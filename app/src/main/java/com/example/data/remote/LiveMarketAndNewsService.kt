package com.example.data.remote

import com.example.BuildConfig
import com.example.model.MacroNewsArticle
import com.example.model.MacroSentiment
import com.example.model.MarketSymbol
import com.example.model.Mt5RateBar
import com.example.model.NewsImpactLevel
import com.example.model.OnlineDataProvider
import com.example.model.Timeframe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class LiveMarketAndNewsService {

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    sealed class RatesFetchOutcome {
        data class Success(
            val bars: List<Mt5RateBar>,
            val provider: OnlineDataProvider,
            val endpointUrl: String,
            val httpStatusCode: Int,
            val serverFetchTimestampMs: Long,
            val latestBarTimestampSec: Long
        ) : RatesFetchOutcome()

        data class Error(
            val provider: OnlineDataProvider,
            val endpointUrl: String,
            val httpStatusCode: Int,
            val errorMessage: String,
            val failedAtMs: Long
        ) : RatesFetchOutcome()
    }

    sealed class NewsFetchOutcome {
        data class Success(
            val articles: List<MacroNewsArticle>,
            val endpointUrl: String,
            val httpStatusCode: Int,
            val fetchedAtMs: Long
        ) : NewsFetchOutcome()

        data class Error(
            val endpointUrl: String,
            val httpStatusCode: Int,
            val errorMessage: String
        ) : NewsFetchOutcome()
    }

    /**
     * Mengambil data OHLCV asli secara online dari provider yang dipilih.
     * Urutan diselaraskan 100% dengan standar ArraySetAsSeries(true) di MetaTrader 5:
     * - barIndex = 0 : Candle sedang berjalan (Forming Bar di ujung PALING KANAN grafik MT5)
     * - barIndex = 1 : Candle terakhir yang SUDAH TUTUP (di sebelah kiri Candle [0])
     * - barIndex = 2..N : Candle historis yang sudah tutup (semakin besar indeks = semakin ke KIRI di grafik MT5)
     */
    suspend fun fetchSymbolRates(
        symbol: MarketSymbol,
        timeframe: Timeframe,
        provider: OnlineDataProvider
    ): RatesFetchOutcome = withContext(Dispatchers.IO) {
        val fetchTimeMs = System.currentTimeMillis()
        if (provider == OnlineDataProvider.ELEV8_METAAPI) {
            return@withContext fetchElev8MetaApiRates(symbol, timeframe, fetchTimeMs)
        }

        val url = buildProviderUrl(symbol, timeframe, provider)

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; HUB-SmartPatternEnginePro/1.0)")
                .header("Accept", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val code = response.code
                val bodyStr = response.body?.string().orEmpty()

                if (!response.isSuccessful || bodyStr.isBlank()) {
                    return@withContext RatesFetchOutcome.Error(
                        provider = provider,
                        endpointUrl = url,
                        httpStatusCode = code,
                        errorMessage = "HTTP $code dari ${provider.shortLabel}: Server menolak atau respons kosong.",
                        failedAtMs = fetchTimeMs
                    )
                }

                val parsedBars = when (provider) {
                    OnlineDataProvider.ELEV8_METAAPI -> emptyList()
                    OnlineDataProvider.BINANCE_VISION -> parseBinanceKlines(bodyStr, symbol, timeframe)
                    OnlineDataProvider.YAHOO_FINANCE -> parseYahooChartJson(bodyStr, symbol, timeframe)
                    OnlineDataProvider.KRAKEN_SPOT -> parseKrakenOhlcJson(bodyStr, symbol, timeframe)
                    OnlineDataProvider.BITFINEX_PUBLIC -> parseBitfinexCandlesJson(bodyStr, symbol, timeframe)
                    OnlineDataProvider.OKX_GLOBAL -> parseOkxCandlesJson(bodyStr, symbol, timeframe)
                    OnlineDataProvider.KUCOIN_SPOT -> parseKucoinCandlesJson(bodyStr, symbol, timeframe)
                    OnlineDataProvider.OFFLINE_SIMULATION_TEST -> emptyList()
                }

                if (parsedBars.size < 30) {
                    return@withContext RatesFetchOutcome.Error(
                        provider = provider,
                        endpointUrl = url,
                        httpStatusCode = code,
                        errorMessage = "Data dari ${provider.shortLabel} hanya berisi ${parsedBars.size} candle (minimal 30 candle diperlukan).",
                        failedAtMs = fetchTimeMs
                    )
                }

                return@withContext RatesFetchOutcome.Success(
                    bars = parsedBars,
                    provider = provider,
                    endpointUrl = url,
                    httpStatusCode = code,
                    serverFetchTimestampMs = fetchTimeMs,
                    latestBarTimestampSec = parsedBars.first().timeEpochSec
                )
            }
        } catch (e: Exception) {
            return@withContext RatesFetchOutcome.Error(
                provider = provider,
                endpointUrl = url,
                httpStatusCode = 0,
                errorMessage = "${e.javaClass.simpleName}: ${e.localizedMessage ?: "Tidak dapat menjangkau server ${provider.baseHost}"}",
                failedAtMs = fetchTimeMs
            )
        }
    }

    fun buildProviderUrl(
        symbol: MarketSymbol,
        timeframe: Timeframe,
        provider: OnlineDataProvider
    ): String {
        return when (provider) {
            OnlineDataProvider.ELEV8_METAAPI -> {
                val region = discoveredMetaApiRegion ?: getMetaApiRegion()
                val accId = getMetaApiAccountId().ifEmpty { "YOUR_METAAPI_ACCOUNT_ID" }
                val symWithSuffix = discoveredElev8Symbol[symbol] ?: getElev8SymbolWithSuffix(symbol)
                "https://mt-market-data-client-api-v1.$region.agiliumtrade.ai/users/current/accounts/$accId/historical-market-data/symbols/$symWithSuffix/timeframes/${timeframe.binanceInterval}/candles?limit=500"
            }
            OnlineDataProvider.BINANCE_VISION -> {
                val ticker = if (symbol == MarketSymbol.XAUUSD) "PAXGUSDT" else "EURUSDT"
                // Ambil 500 candle agar pemanasan EMA 200, RSI 14, MACD, dan ADX 14 akurat seperti MT5 Web
                "https://data-api.binance.vision/api/v3/klines?symbol=$ticker&interval=${timeframe.binanceInterval}&limit=500"
            }
            OnlineDataProvider.YAHOO_FINANCE -> {
                val ticker = if (symbol == MarketSymbol.XAUUSD) "GC=F" else "EURUSD=X"
                "https://query1.finance.yahoo.com/v8/finance/chart/$ticker?interval=${timeframe.yahooInterval}&range=${timeframe.yahooRange}"
            }
            OnlineDataProvider.KRAKEN_SPOT -> {
                val pair = if (symbol == MarketSymbol.XAUUSD) "PAXGUSD" else "EURUSD"
                "https://api.kraken.com/0/public/OHLC?pair=$pair&interval=${timeframe.krakenInterval}"
            }
            OnlineDataProvider.BITFINEX_PUBLIC -> {
                val pair = if (symbol == MarketSymbol.XAUUSD) "tXAUT:UST" else "tEURUST"
                "https://api-pub.bitfinex.com/v2/candles/trade:${timeframe.binanceInterval}:$pair/hist?limit=500"
            }
            OnlineDataProvider.OKX_GLOBAL -> {
                val instId = if (symbol == MarketSymbol.XAUUSD) "PAXG-USDT" else "USDT-EUR"
                val okxBar = if (timeframe == Timeframe.H1) "1H" else timeframe.binanceInterval
                "https://www.okx.com/api/v5/market/candles?instId=$instId&bar=$okxBar&limit=300"
            }
            OnlineDataProvider.KUCOIN_SPOT -> {
                val pair = if (symbol == MarketSymbol.XAUUSD) "PAXG-USDT" else "USDT-EUR"
                val kcType = when (timeframe) {
                    Timeframe.M1 -> "1min"
                    Timeframe.M5 -> "5min"
                    Timeframe.M15 -> "15min"
                    Timeframe.M30 -> "30min"
                    Timeframe.H1 -> "1hour"
                }
                "https://api.kucoin.com/api/v1/market/candles?type=$kcType&symbol=$pair"
            }
            OnlineDataProvider.OFFLINE_SIMULATION_TEST -> {
                "https://offline-provider.invalid/api/v1/ohlc?symbol=${symbol.code}&tf=${timeframe.label}"
            }
        }
    }

    // Pembatas request paralel MetaApi (maksimal 3 bersamaan agar tidak melanggar batas 5 request paralel MetaApi)
    private val metaApiSemaphore = Semaphore(3)

    @Volatile
    private var discoveredMetaApiRegion: String? = null

    private val discoveredElev8Symbol = java.util.concurrent.ConcurrentHashMap<MarketSymbol, String>()

    @Volatile
    private var customMetaApiAccountId: String? = null

    @Volatile
    private var customMetaApiAuthToken: String? = null

    @Volatile
    private var customMetaApiRegion: String? = null

    fun setCustomMetaApiCredentials(accountId: String, authToken: String, region: String? = null) {
        customMetaApiAccountId = accountId.trim()
        customMetaApiAuthToken = authToken.trim()
        if (!region.isNullOrBlank()) {
            customMetaApiRegion = region.trim()
            discoveredMetaApiRegion = region.trim()
        }
    }

    fun getMetaApiAccountId(): String {
        customMetaApiAccountId?.let { if (it.isNotBlank()) return it }
        return runCatching { BuildConfig.METAAPI_ACCOUNT_ID.trim() }.getOrDefault("")
    }

    fun getMetaApiAuthToken(): String {
        customMetaApiAuthToken?.let { if (it.isNotBlank()) return it }
        return runCatching { BuildConfig.METAAPI_AUTH_TOKEN.trim() }.getOrDefault("")
    }

    fun getMetaApiRegion(): String {
        customMetaApiRegion?.let { if (it.isNotBlank()) return it }
        return discoveredMetaApiRegion ?: "new-york"
    }

    private fun getElev8SymbolWithSuffix(symbol: MarketSymbol): String {
        return discoveredElev8Symbol[symbol] ?: symbol.code
    }

    fun isMetaApiConfigured(): Boolean {
        val accId = getMetaApiAccountId()
        val token = getMetaApiAuthToken()
        return accId.isNotEmpty() &&
            accId != "YOUR_METAAPI_ACCOUNT_ID" &&
            token.isNotEmpty() &&
            token != "YOUR_METAAPI_AUTH_TOKEN"
    }

    /**
     * Mendeteksi otomatis region akun MetaApi dari Provisioning API
     * dan mendeteksi nama simbol persis pada broker MT5 Elev8 (XAUUSD, XAUUSD.sml, XAUUSDm, dll.)
     */
    private fun discoverMetaApiRegionIfNeeded(accountId: String, authToken: String): String? {
        discoveredMetaApiRegion?.let { return it }
        val provHosts = listOf(
            "https://mt-provisioning-api-v1.agiliumtrade.agiliumtrade.ai",
            "https://mt-provisioning-api-v1.agiliumtrade.ai",
            "https://mt-provisioning-api-v1.new-york.agiliumtrade.ai",
            "https://mt-provisioning-api-v1.london.agiliumtrade.ai",
            "https://mt-provisioning-api-v1.singapore.agiliumtrade.ai"
        )
        for (host in provHosts) {
            val res = runCatching {
                val provUrl = "$host/users/current/accounts/$accountId"
                val req = Request.Builder()
                    .url(provUrl)
                    .header("auth-token", authToken)
                    .header("Accept", "application/json")
                    .build()
                okHttpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        if (body.isNotBlank()) {
                            val json = JSONObject(body)
                            val region = json.optString("region", "").trim()
                            if (region.isNotEmpty()) {
                                discoveredMetaApiRegion = region
                                return@runCatching region
                            }
                        }
                    }
                    null
                }
            }.getOrNull()
            if (!res.isNullOrBlank()) return res
        }
        return null
    }

    private fun discoverBrokerSymbolIfNeeded(
        region: String,
        accountId: String,
        authToken: String,
        symbol: MarketSymbol
    ): String? {
        discoveredElev8Symbol[symbol]?.let { return it }
        val symHosts = listOf(
            "https://mt-client-api-v1.$region.agiliumtrade.ai",
            "https://mt-client-api-v1.$region.agiliumtrade.agiliumtrade.ai"
        )
        for (host in symHosts) {
            val res = runCatching {
                val symUrl = "$host/users/current/accounts/$accountId/symbols"
                val req = Request.Builder()
                    .url(symUrl)
                    .header("auth-token", authToken)
                    .header("Accept", "application/json")
                    .build()
                okHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val body = resp.body?.string().orEmpty()
                    if (body.isBlank()) return@use null
                    val arr = JSONArray(body)
                    val allSymbols = mutableListOf<String>()
                    for (i in 0 until arr.length()) {
                        val item = arr.optString(i, "").trim()
                        if (item.isNotEmpty()) {
                            allSymbols.add(item)
                        } else {
                            val objSym = arr.optJSONObject(i)?.optString("symbol", "")?.trim().orEmpty()
                            if (objSym.isNotEmpty()) allSymbols.add(objSym)
                        }
                    }
                    val configured = getElev8SymbolWithSuffix(symbol)
                    val match = allSymbols.firstOrNull { it.equals(configured, ignoreCase = true) }
                        ?: allSymbols.firstOrNull { it.equals(symbol.code, ignoreCase = true) }
                        ?: allSymbols.firstOrNull { it.equals("${symbol.code}.sml", ignoreCase = true) }
                        ?: allSymbols.firstOrNull { it.startsWith(symbol.code, ignoreCase = true) }
                    if (!match.isNullOrEmpty()) {
                        discoveredElev8Symbol[symbol] = match
                    }
                    match
                }
            }.getOrNull()
            if (!res.isNullOrBlank()) return res
        }
        return null
    }

    /**
     * Konektor Resmi Elev8 MT5 Cloud via MetaApi:
     * - Historical Candles: mt-market-data-client-api-v1.<region>.agiliumtrade.ai (dan fallback agiliumtrade.agiliumtrade.ai)
     * - Current Candle [0] & Symbols: mt-client-api-v1.<region>.agiliumtrade.ai (dan fallback agiliumtrade.agiliumtrade.ai)
     * - Jika kredensial belum diisi atau masa uji coba habis (HTTP 401/402/403/404/429),
     *   mengembalikan status OFFLINE secara jujur dengan pesan edukatif.
     */
    private suspend fun fetchElev8MetaApiRates(
        symbol: MarketSymbol,
        timeframe: Timeframe,
        fetchTimeMs: Long
    ): RatesFetchOutcome = metaApiSemaphore.withPermit {
        val defaultUrl = buildProviderUrl(symbol, timeframe, OnlineDataProvider.ELEV8_METAAPI)
        if (!isMetaApiConfigured()) {
            return@withPermit RatesFetchOutcome.Error(
                provider = OnlineDataProvider.ELEV8_METAAPI,
                endpointUrl = defaultUrl,
                httpStatusCode = 401,
                errorMessage = "OFFLINE — Kredensial Elev8 MT5 (MetaApi) belum diisi. Anda dapat memasukkan Account ID & Token langsung di tombol 'Konfigurasi Akun Elev8' di bawah ini.",
                failedAtMs = fetchTimeMs
            )
        }

        val accountId = getMetaApiAccountId()
        val authToken = getMetaApiAuthToken()
        val autoRegion = discoverMetaApiRegionIfNeeded(accountId, authToken)
        val regionsToTry = listOfNotNull(autoRegion, getMetaApiRegion(), "new-york", "london", "singapore", "vint-hill").distinct()
        val tfParam = timeframe.binanceInterval

        var lastHttpCode = 0
        var lastErrorMsg = ""
        var lastUrl = defaultUrl

        for (region in regionsToTry) {
            val autoSym = discoverBrokerSymbolIfNeeded(region, accountId, authToken, symbol)
            val symbolCandidates = listOfNotNull(
                autoSym,
                getElev8SymbolWithSuffix(symbol),
                symbol.code,
                "${symbol.code}.sml",
                "${symbol.code}m"
            ).distinct()

            for (rawSym in symbolCandidates) {
                val encodedSym = URLEncoder.encode(rawSym, "UTF-8").replace("+", "%20")
                val domainCandidates = listOf(
                    "https://mt-market-data-client-api-v1.$region.agiliumtrade.ai" to "https://mt-client-api-v1.$region.agiliumtrade.ai",
                    "https://mt-market-data-client-api-v1.$region.agiliumtrade.agiliumtrade.ai" to "https://mt-client-api-v1.$region.agiliumtrade.agiliumtrade.ai"
                )

                for ((marketDataDomain, clientDomain) in domainCandidates) {
                    val histUrl = "$marketDataDomain/users/current/accounts/$accountId/historical-market-data/symbols/$encodedSym/timeframes/$tfParam/candles?limit=500"
                    val currentUrl = "$clientDomain/users/current/accounts/$accountId/symbols/$encodedSym/current-candles/$tfParam?keepSubscription=true"
                    lastUrl = histUrl

                    try {
                        val histReq = Request.Builder()
                            .url(histUrl)
                            .header("auth-token", authToken)
                            .header("Accept", "application/json")
                            .build()

                        okHttpClient.newCall(histReq).execute().use { response ->
                            val code = response.code
                            val bodyStr = response.body?.string().orEmpty()
                            lastHttpCode = code

                            if (code in 200..299 && bodyStr.isNotBlank()) {
                                discoveredMetaApiRegion = region
                                discoveredElev8Symbol[symbol] = rawSym

                                // Ambil juga current-candle [0] yang sedang berjalan di MT5 Elev8 (best-effort)
                                val currentCandleJson = runCatching {
                                    val curReq = Request.Builder()
                                        .url(currentUrl)
                                        .header("auth-token", authToken)
                                        .header("Accept", "application/json")
                                        .build()
                                    okHttpClient.newCall(curReq).execute().use { curResp ->
                                        if (curResp.isSuccessful) curResp.body?.string() else null
                                    }
                                }.getOrNull()

                                val parsedBars = parseMetaApiCandlesJson(bodyStr, currentCandleJson, symbol, timeframe)
                                if (parsedBars.size >= 30) {
                                    return@withPermit RatesFetchOutcome.Success(
                                        bars = parsedBars,
                                        provider = OnlineDataProvider.ELEV8_METAAPI,
                                        endpointUrl = histUrl,
                                        httpStatusCode = code,
                                        serverFetchTimestampMs = fetchTimeMs,
                                        latestBarTimestampSec = parsedBars.first().timeEpochSec
                                    )
                                } else {
                                    lastErrorMsg = "Server MT5 Elev8 ($region) hanya mengembalikan ${parsedBars.size} candle untuk $rawSym."
                                }
                            } else {
                                val snippet = bodyStr.take(180)
                                lastErrorMsg = when (code) {
                                    401 -> "OFFLINE (HTTP 401) — Token MetaApi tidak valid atau kedaluwarsa. Periksa token di akun app.metaapi.cloud."
                                    402, 403, 429 -> "OFFLINE (HTTP $code) — Kuota Gratis 17 Hari / Kredit CPU MetaApi Habis. Silakan Top-Up saldo di https://app.metaapi.cloud agar server MT5 Elev8 kembali Online."
                                    404 -> "OFFLINE (HTTP 404) — Akun MT5 Elev8 berstatus UNDEPLOYED di app.metaapi.cloud atau simbol '$rawSym' tidak ditemukan. Pastikan akun berstatus DEPLOYED & CONNECTED."
                                    else -> "OFFLINE (HTTP $code) dari Elev8 MT5 ($region): $snippet"
                                }
                                if (code == 401 || code == 402 || code == 429) {
                                    break
                                }
                            }
                        }
                    } catch (e: Exception) {
                        lastErrorMsg = "${e.javaClass.simpleName}: ${e.localizedMessage ?: "Gagal menghubungi server MetaApi ($region)"}"
                    }
                    if (lastHttpCode == 401 || lastHttpCode == 402 || lastHttpCode == 429) {
                        break
                    }
                }
                if (lastHttpCode == 401 || lastHttpCode == 402 || lastHttpCode == 429) {
                    break
                }
            }
            if (lastHttpCode == 401 || lastHttpCode == 402 || lastHttpCode == 429) {
                break
            }
        }

        return@withPermit RatesFetchOutcome.Error(
            provider = OnlineDataProvider.ELEV8_METAAPI,
            endpointUrl = lastUrl,
            httpStatusCode = lastHttpCode,
            errorMessage = lastErrorMsg.ifEmpty {
                "OFFLINE — Tidak dapat menjangkau server Elev8 MT5 (MetaApi). Periksa status DEPLOYED atau Top-Up kuota di app.metaapi.cloud."
            },
            failedAtMs = fetchTimeMs
        )
    }

    private fun parseMetaApiCandlesJson(
        historicalJsonStr: String,
        currentCandleJsonStr: String?,
        symbol: MarketSymbol,
        timeframe: Timeframe
    ): List<Mt5RateBar> {
        val rawChronological = mutableListOf<Mt5RateBar>()
        val array = JSONArray(historicalJsonStr)
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            parseSingleMetaApiCandleObject(obj, symbol)?.let { rawChronological.add(it) }
        }

        if (!currentCandleJsonStr.isNullOrBlank()) {
            runCatching {
                val curObj = JSONObject(currentCandleJsonStr)
                parseSingleMetaApiCandleObject(curObj, symbol)?.let { rawChronological.add(it) }
            }
        }

        return normalizeToMt5SeriesIndexing(rawChronological, timeframe, symbol)
    }

    private fun parseSingleMetaApiCandleObject(
        obj: JSONObject,
        symbol: MarketSymbol
    ): Mt5RateBar? {
        val timeIso = obj.optString("time", "")
        val timeSec = parseIsoTimestampToSec(timeIso)
        if (timeSec <= 0L) return null
        val open = obj.optDouble("open", -1.0)
        val rawHigh = obj.optDouble("high", -1.0)
        val rawLow = obj.optDouble("low", -1.0)
        val close = obj.optDouble("close", -1.0)
        if (open <= 0.0 || close <= 0.0 || rawHigh <= 0.0 || rawLow <= 0.0) return null

        val high = maxOf(rawHigh, open, close)
        val low = minOf(rawLow, open, close)
        val tickVol = obj.optLong("tickVolume", 15L).coerceAtLeast(1L)
        val realVol = obj.optLong("volume", tickVol).coerceAtLeast(1L)
        val spreadPts = obj.optInt("spread", computeRealSpreadEstimate(high, low, symbol)).coerceAtLeast(1)

        return Mt5RateBar(
            barIndex = 0,
            timeEpochSec = timeSec,
            open = roundToDigits(open, symbol.digits),
            high = roundToDigits(high, symbol.digits),
            low = roundToDigits(low, symbol.digits),
            close = roundToDigits(close, symbol.digits),
            tickVolume = tickVol,
            realVolume = realVol,
            spread = spreadPts
        )
    }

    private fun parseIsoTimestampToSec(isoStr: String): Long {
        if (isoStr.isBlank()) return 0L
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val parsed = sdf.parse(isoStr)
                if (parsed != null) return parsed.time / 1000L
            } catch (_: Exception) {
            }
        }
        return 0L
    }

    private fun parseBinanceKlines(
        jsonStr: String,
        symbol: MarketSymbol,
        timeframe: Timeframe
    ): List<Mt5RateBar> {
        val array = JSONArray(jsonStr)
        val rawChronological = mutableListOf<Mt5RateBar>()
        val total = array.length()
        for (i in 0 until total) {
            val item = array.getJSONArray(i)
            val openTimeMs = item.getLong(0)
            val open = item.getString(1).toDoubleOrNull() ?: continue
            val high = item.getString(2).toDoubleOrNull() ?: continue
            val low = item.getString(3).toDoubleOrNull() ?: continue
            val close = item.getString(4).toDoubleOrNull() ?: continue
            val volDouble = item.getString(5).toDoubleOrNull() ?: 1.0
            val tradesCount = item.optLong(8, (volDouble * 10).toLong()).coerceAtLeast(1L)
            val spreadPts = computeRealSpreadEstimate(high, low, symbol)

            rawChronological.add(
                Mt5RateBar(
                    barIndex = 0,
                    timeEpochSec = openTimeMs / 1000L,
                    open = roundToDigits(open, symbol.digits),
                    high = roundToDigits(high, symbol.digits),
                    low = roundToDigits(low, symbol.digits),
                    close = roundToDigits(close, symbol.digits),
                    tickVolume = tradesCount,
                    realVolume = (volDouble * 100).toLong().coerceAtLeast(1L),
                    spread = spreadPts
                )
            )
        }
        return normalizeToMt5SeriesIndexing(rawChronological, timeframe, symbol)
    }

    private fun parseYahooChartJson(
        jsonStr: String,
        symbol: MarketSymbol,
        timeframe: Timeframe
    ): List<Mt5RateBar> {
        val root = JSONObject(jsonStr)
        val chart = root.getJSONObject("chart")
        val results = chart.optJSONArray("result") ?: return emptyList()
        if (results.length() == 0) return emptyList()

        val firstResult = results.getJSONObject(0)
        val timestamps = firstResult.optJSONArray("timestamp") ?: return emptyList()
        val quoteArr = firstResult
            .getJSONObject("indicators")
            .getJSONArray("quote")
        if (quoteArr.length() == 0) return emptyList()

        val quote = quoteArr.getJSONObject(0)
        val opens = quote.getJSONArray("open")
        val highs = quote.getJSONArray("high")
        val lows = quote.getJSONArray("low")
        val closes = quote.getJSONArray("close")
        val volumes = quote.optJSONArray("volume")

        val rawChronological = mutableListOf<Mt5RateBar>()
        val count = timestamps.length()
        val startIdx = (count - 520).coerceAtLeast(0)

        for (i in startIdx until count) {
            if (opens.isNull(i) || highs.isNull(i) || lows.isNull(i) || closes.isNull(i)) continue
            val timeSec = timestamps.getLong(i)
            val open = opens.getDouble(i)
            val high = highs.getDouble(i)
            val low = lows.getDouble(i)
            val close = closes.getDouble(i)
            if (open <= 0.0 || close <= 0.0) continue

            val rawVol = if (volumes != null && !volumes.isNull(i)) volumes.getLong(i) else 0L
            val rangeTicks = ((high - low) / symbol.pointSize).roundToInt().toLong().coerceAtLeast(15L)
            val tickVol = if (rawVol > 0L) rawVol else rangeTicks
            val spreadPts = computeRealSpreadEstimate(high, low, symbol)

            rawChronological.add(
                Mt5RateBar(
                    barIndex = 0,
                    timeEpochSec = timeSec,
                    open = roundToDigits(open, symbol.digits),
                    high = roundToDigits(high, symbol.digits),
                    low = roundToDigits(low, symbol.digits),
                    close = roundToDigits(close, symbol.digits),
                    tickVolume = tickVol,
                    realVolume = rawVol,
                    spread = spreadPts
                )
            )
        }

        return normalizeToMt5SeriesIndexing(rawChronological, timeframe, symbol)
    }

    private fun parseKrakenOhlcJson(
        jsonStr: String,
        symbol: MarketSymbol,
        timeframe: Timeframe
    ): List<Mt5RateBar> {
        val root = JSONObject(jsonStr)
        val errors = root.optJSONArray("error")
        if (errors != null && errors.length() > 0) return emptyList()

        val resultObj = root.optJSONObject("result") ?: return emptyList()
        var candleArray: JSONArray? = null
        val keys = resultObj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k != "last") {
                candleArray = resultObj.optJSONArray(k)
                if (candleArray != null) break
            }
        }
        val array = candleArray ?: return emptyList()
        val rawChronological = mutableListOf<Mt5RateBar>()
        val count = array.length()
        val startIdx = (count - 500).coerceAtLeast(0)

        for (i in startIdx until count) {
            val item = array.getJSONArray(i)
            val timeSec = item.getLong(0)
            val open = item.getString(1).toDoubleOrNull() ?: continue
            val high = item.getString(2).toDoubleOrNull() ?: continue
            val low = item.getString(3).toDoubleOrNull() ?: continue
            val close = item.getString(4).toDoubleOrNull() ?: continue
            val volDouble = item.getString(6).toDoubleOrNull() ?: 1.0
            val tradeCount = item.optLong(7, 15L).coerceAtLeast(1L)
            val spreadPts = computeRealSpreadEstimate(high, low, symbol)

            rawChronological.add(
                Mt5RateBar(
                    barIndex = 0,
                    timeEpochSec = timeSec,
                    open = roundToDigits(open, symbol.digits),
                    high = roundToDigits(high, symbol.digits),
                    low = roundToDigits(low, symbol.digits),
                    close = roundToDigits(close, symbol.digits),
                    tickVolume = tradeCount,
                    realVolume = (volDouble * 100).toLong().coerceAtLeast(1L),
                    spread = spreadPts
                )
            )
        }

        return normalizeToMt5SeriesIndexing(rawChronological, timeframe, symbol)
    }

    /**
     * Parser resmi untuk Bitfinex Public v2 API (`api-pub.bitfinex.com`):
     * Format array: [MTS, OPEN, CLOSE, HIGH, LOW, VOLUME]
     */
    private fun parseBitfinexCandlesJson(
        jsonStr: String,
        symbol: MarketSymbol,
        timeframe: Timeframe
    ): List<Mt5RateBar> {
        val array = JSONArray(jsonStr)
        val rawChronological = mutableListOf<Mt5RateBar>()
        val count = array.length()
        for (i in 0 until count) {
            val item = array.optJSONArray(i) ?: continue
            if (item.length() < 6) continue
            val timeSec = item.getLong(0) / 1000L
            val open = item.getDouble(1)
            val close = item.getDouble(2)
            val rawHigh = item.getDouble(3)
            val rawLow = item.getDouble(4)
            val volDouble = item.getDouble(5)
            if (open <= 0.0 || close <= 0.0) continue

            val high = maxOf(rawHigh, open, close)
            val low = minOf(rawLow, open, close)
            val tickVol = (volDouble * 25.0).toLong().coerceAtLeast(12L)
            val spreadPts = computeRealSpreadEstimate(high, low, symbol)

            rawChronological.add(
                Mt5RateBar(
                    barIndex = 0,
                    timeEpochSec = timeSec,
                    open = roundToDigits(open, symbol.digits),
                    high = roundToDigits(high, symbol.digits),
                    low = roundToDigits(low, symbol.digits),
                    close = roundToDigits(close, symbol.digits),
                    tickVolume = tickVol,
                    realVolume = (volDouble * 100.0).toLong().coerceAtLeast(1L),
                    spread = spreadPts
                )
            )
        }
        return normalizeToMt5SeriesIndexing(rawChronological, timeframe, symbol)
    }

    /**
     * Parser resmi untuk OKX Global v5 API (`www.okx.com`):
     * Format data: [ts_ms, open, high, low, close, vol, ...]
     * Untuk EURUSD (`USDT-EUR`), kuotasi adalah EUR per 1 USD (~0.8815), sehingga dikonversi presisi `1.0 / harga` menjadi EURUSD (~1.1344).
     */
    private fun parseOkxCandlesJson(
        jsonStr: String,
        symbol: MarketSymbol,
        timeframe: Timeframe
    ): List<Mt5RateBar> {
        val root = JSONObject(jsonStr)
        if (root.optString("code") != "0") return emptyList()
        val dataArr = root.optJSONArray("data") ?: return emptyList()
        val rawChronological = mutableListOf<Mt5RateBar>()
        val isInvertedPair = (symbol == MarketSymbol.EURUSD)

        for (i in 0 until dataArr.length()) {
            val item = dataArr.optJSONArray(i) ?: continue
            if (item.length() < 6) continue
            val timeSec = item.getString(0).toLongOrNull()?.div(1000L) ?: continue
            val rOpen = item.getString(1).toDoubleOrNull() ?: continue
            val rHigh = item.getString(2).toDoubleOrNull() ?: continue
            val rLow = item.getString(3).toDoubleOrNull() ?: continue
            val rClose = item.getString(4).toDoubleOrNull() ?: continue
            val rVol = item.getString(5).toDoubleOrNull() ?: 1.0
            if (rOpen <= 0.0 || rHigh <= 0.0 || rLow <= 0.0 || rClose <= 0.0) continue

            val open = if (isInvertedPair) 1.0 / rOpen else rOpen
            val close = if (isInvertedPair) 1.0 / rClose else rClose
            val high = if (isInvertedPair) maxOf(1.0 / rLow, open, close) else maxOf(rHigh, open, close)
            val low = if (isInvertedPair) minOf(1.0 / rHigh, open, close) else minOf(rLow, open, close)
            val tickVol = (rVol * 15.0).toLong().coerceAtLeast(10L)
            val spreadPts = computeRealSpreadEstimate(high, low, symbol)

            rawChronological.add(
                Mt5RateBar(
                    barIndex = 0,
                    timeEpochSec = timeSec,
                    open = roundToDigits(open, symbol.digits),
                    high = roundToDigits(high, symbol.digits),
                    low = roundToDigits(low, symbol.digits),
                    close = roundToDigits(close, symbol.digits),
                    tickVolume = tickVol,
                    realVolume = (rVol * 100.0).toLong().coerceAtLeast(1L),
                    spread = spreadPts
                )
            )
        }
        return normalizeToMt5SeriesIndexing(rawChronological, timeframe, symbol)
    }

    /**
     * Parser resmi untuk KuCoin Spot v1 API (`api.kucoin.com`):
     * Format data: [time_sec, open, close, high, low, volume, turnover]
     */
    private fun parseKucoinCandlesJson(
        jsonStr: String,
        symbol: MarketSymbol,
        timeframe: Timeframe
    ): List<Mt5RateBar> {
        val root = JSONObject(jsonStr)
        if (root.optString("code") != "200000") return emptyList()
        val dataArr = root.optJSONArray("data") ?: return emptyList()
        val rawChronological = mutableListOf<Mt5RateBar>()
        val isInvertedPair = (symbol == MarketSymbol.EURUSD)

        for (i in 0 until dataArr.length()) {
            val item = dataArr.optJSONArray(i) ?: continue
            if (item.length() < 6) continue
            val timeSec = item.getString(0).toLongOrNull() ?: continue
            val rOpen = item.getString(1).toDoubleOrNull() ?: continue
            val rClose = item.getString(2).toDoubleOrNull() ?: continue
            val rHigh = item.getString(3).toDoubleOrNull() ?: continue
            val rLow = item.getString(4).toDoubleOrNull() ?: continue
            val rVol = item.getString(5).toDoubleOrNull() ?: 1.0
            if (rOpen <= 0.0 || rHigh <= 0.0 || rLow <= 0.0 || rClose <= 0.0) continue

            val open = if (isInvertedPair) 1.0 / rOpen else rOpen
            val close = if (isInvertedPair) 1.0 / rClose else rClose
            val high = if (isInvertedPair) maxOf(1.0 / rLow, open, close) else maxOf(rHigh, open, close)
            val low = if (isInvertedPair) minOf(1.0 / rHigh, open, close) else minOf(rLow, open, close)
            val tickVol = (rVol * 20.0).toLong().coerceAtLeast(10L)
            val spreadPts = computeRealSpreadEstimate(high, low, symbol)

            rawChronological.add(
                Mt5RateBar(
                    barIndex = 0,
                    timeEpochSec = timeSec,
                    open = roundToDigits(open, symbol.digits),
                    high = roundToDigits(high, symbol.digits),
                    low = roundToDigits(low, symbol.digits),
                    close = roundToDigits(close, symbol.digits),
                    tickVolume = tickVol,
                    realVolume = (rVol * 100.0).toLong().coerceAtLeast(1L),
                    spread = spreadPts
                )
            )
        }
        return normalizeToMt5SeriesIndexing(rawChronological, timeframe, symbol)
    }

    /**
     * Menormalisasi deret candle dari provider online agar 100% sesuai dengan standar MetaTrader 5:
     * 1. Mengurutkan berdasarkan waktu dari terlama ke terbaru (kronologis).
     * 2. Menggabungkan tick ekstra yang jatuh pada slot timeframe yang sama (contoh: Yahoo Finance
     *    sering menyisipkan tick kuotasi detik berjalan di akhir array pada periode candle yang sama,
     *    yang jika tidak digabung akan menggeser Candle [0] menjadi Candle [1]).
     * 3. Membalik urutan ke standar MT5 ArraySetAsSeries(true) di mana:
     *    - barIndex 0 = Candle paling kanan (sedang berjalan / forming)
     *    - barIndex 1 = Candle tutup terakhir (tepat di kiri barIndex 0)
     *    - barIndex 2..N = Candle historis ke arah kiri
     */
    private fun normalizeToMt5SeriesIndexing(
        rawBars: List<Mt5RateBar>,
        timeframe: Timeframe,
        symbol: MarketSymbol
    ): List<Mt5RateBar> {
        if (rawBars.isEmpty()) return emptyList()
        val periodSec = (timeframe.minutes * 60L).coerceAtLeast(60L)
        val sortedChronological = rawBars.sortedBy { it.timeEpochSec }

        val mergedByBucket = ArrayList<Mt5RateBar>(sortedChronological.size)
        for (bar in sortedChronological) {
            val bucketStartSec = (bar.timeEpochSec / periodSec) * periodSec
            val prev = mergedByBucket.lastOrNull()
            val prevBucketSec = prev?.let { (it.timeEpochSec / periodSec) * periodSec }

            if (prev != null && prevBucketSec == bucketStartSec) {
                // Gabungkan tick ke dalam candle periode yang sama (Open tetap dari awal candle, Close dari tick terbaru)
                val mergedHigh = maxOf(prev.high, bar.high)
                val mergedLow = minOf(prev.low, bar.low)
                mergedByBucket[mergedByBucket.lastIndex] = prev.copy(
                    timeEpochSec = bucketStartSec,
                    high = roundToDigits(mergedHigh, symbol.digits),
                    low = roundToDigits(mergedLow, symbol.digits),
                    close = roundToDigits(bar.close, symbol.digits),
                    tickVolume = maxOf(prev.tickVolume, bar.tickVolume),
                    realVolume = maxOf(prev.realVolume, bar.realVolume),
                    spread = computeRealSpreadEstimate(mergedHigh, mergedLow, symbol)
                )
            } else {
                mergedByBucket.add(bar.copy(timeEpochSec = bucketStartSec))
            }
        }

        return mergedByBucket
            .takeLast(500)
            .asReversed()
            .mapIndexed { idx, bar -> bar.copy(barIndex = idx) }
    }

    /**
     * 11. NEWS ENGINE ONLINE - Mengambil kalender ekonomi langsung dari ForexFactory JSON API
     * dan berita ekonomi langsung dari CNBC RSS. Tanpa artikel palsu/lokal!
     */
    suspend fun fetchLiveMacroNews(isOfflineTestMode: Boolean = false): NewsFetchOutcome = withContext(Dispatchers.IO) {
        if (isOfflineTestMode) {
            return@withContext NewsFetchOutcome.Error(
                endpointUrl = "https://offline-provider.invalid/news",
                httpStatusCode = 0,
                errorMessage = "Mode Uji Offline Aktif: Provider berita tidak dapat dijangkau."
            )
        }

        val ffUrl = "https://nfs.faireconomy.media/ff_calendar_thisweek.json"
        val cnbcUrl = "https://www.cnbc.com/id/20910258/device/rss/rss.html"
        val collected = mutableListOf<MacroNewsArticle>()
        var lastHttpCode = 0
        var primaryEndpoint = ffUrl

        try {
            val req = Request.Builder()
                .url(ffUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                lastHttpCode = resp.code
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    if (body.isNotBlank()) {
                        collected.addAll(parseForexFactoryCalendarJson(body))
                    }
                }
            }
        } catch (_: Exception) {
        }

        try {
            val req = Request.Builder()
                .url(cnbcUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (lastHttpCode == 0) {
                    lastHttpCode = resp.code
                    primaryEndpoint = cnbcUrl
                }
                if (resp.isSuccessful) {
                    val xml = resp.body?.string().orEmpty()
                    if (xml.isNotBlank()) {
                        collected.addAll(parseRssXmlForMacroNews(xml))
                    }
                }
            }
        } catch (e: Exception) {
            if (collected.isEmpty()) {
                return@withContext NewsFetchOutcome.Error(
                    endpointUrl = cnbcUrl,
                    httpStatusCode = lastHttpCode,
                    errorMessage = "Gagal memuat berita online: ${e.localizedMessage ?: "Jaringan terputus"}"
                )
            }
        }

        if (collected.isEmpty()) {
            return@withContext NewsFetchOutcome.Error(
                endpointUrl = primaryEndpoint,
                httpStatusCode = lastHttpCode,
                errorMessage = "Server berita tidak mengembalikan artikel (HTTP $lastHttpCode)."
            )
        }

        val sorted = collected
            .distinctBy { it.title }
            .sortedWith(
                compareByDescending<MacroNewsArticle> { it.impact == NewsImpactLevel.HIGH }
                    .thenByDescending { it.publishedTimeEpochSec }
            )
            .take(25)

        NewsFetchOutcome.Success(
            articles = sorted,
            endpointUrl = primaryEndpoint,
            httpStatusCode = if (lastHttpCode == 0) 200 else lastHttpCode,
            fetchedAtMs = System.currentTimeMillis()
        )
    }

    private fun parseForexFactoryCalendarJson(jsonStr: String): List<MacroNewsArticle> {
        val array = JSONArray(jsonStr)
        val out = mutableListOf<MacroNewsArticle>()
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val country = obj.optString("country", "")
            if (country !in listOf("USD", "EUR", "ALL")) continue

            val title = obj.optString("title", "").trim()
            if (title.isEmpty()) continue
            val impactStr = obj.optString("impact", "Low")
            val dateStr = obj.optString("date", "")
            val forecast = obj.optString("forecast", "").ifEmpty { "-" }
            val previous = obj.optString("previous", "").ifEmpty { "-" }

            val epochSec = runCatching {
                isoFormat.parse(dateStr)?.time?.div(1000L)
            }.getOrNull() ?: (System.currentTimeMillis() / 1000L)

            val impact = when (impactStr.lowercase(Locale.US)) {
                "high" -> NewsImpactLevel.HIGH
                "medium" -> NewsImpactLevel.MEDIUM
                else -> NewsImpactLevel.LOW
            }

            val lowerTitle = title.lowercase(Locale.US)
            val categoryTag = when {
                "fomc" in lowerTitle || "fed" in lowerTitle || "powell" in lowerTitle ->
                    "$country • Federal Reserve / FOMC"
                "rate" in lowerTitle || "yield" in lowerTitle || "monetary" in lowerTitle ->
                    "$country • Suku Bunga (Interest Rate)"
                "cpi" in lowerTitle || "pce" in lowerTitle || "inflation" in lowerTitle || "ppi" in lowerTitle ->
                    "$country • Inflasi (CPI/PCE)"
                "employment" in lowerTitle || "payroll" in lowerTitle || "jobless" in lowerTitle || "claims" in lowerTitle ->
                    "$country • Tenaga Kerja (NFP/Jobs)"
                "gdp" in lowerTitle || "pmi" in lowerTitle ->
                    "$country • Pertumbuhan & PMI"
                else -> "Kalender Ekonomi $country"
            }

            val sentiment = when {
                impact == NewsImpactLevel.HIGH && country == "USD" -> MacroSentiment.HAWKISH_USD_BEARISH_GOLD
                impact == NewsImpactLevel.HIGH && country == "EUR" -> MacroSentiment.ECB_HAWKISH_EUR_BID
                else -> MacroSentiment.NEUTRAL_WATCH
            }

            val symbols = if (country == "EUR") {
                listOf(MarketSymbol.EURUSD, MarketSymbol.XAUUSD)
            } else {
                listOf(MarketSymbol.XAUUSD, MarketSymbol.EURUSD)
            }

            out.add(
                MacroNewsArticle(
                    id = "ff_${country}_${i}_$epochSec",
                    title = "[$country] $title",
                    categoryTag = categoryTag,
                    affectedSymbols = symbols,
                    impact = impact,
                    sentiment = sentiment,
                    publishedTimeEpochSec = epochSec,
                    sourceName = "ForexFactory Live Calendar",
                    keyTakeaway = "Jadwal Rilis: $dateStr | Proyeksi (Forecast): $forecast | Sebelumnya (Previous): $previous"
                )
            )
        }
        return out
    }

    private fun parseRssXmlForMacroNews(xml: String): List<MacroNewsArticle> {
        val results = mutableListOf<MacroNewsArticle>()
        val itemRegex = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)
        val titleRegex = Regex("<title><!\\[CDATA\\[(.*?)]]></title>|<title>(.*?)</title>")
        val descRegex = Regex("<description><!\\[CDATA\\[(.*?)]]></description>|<description>(.*?)</description>")
        val pubDateRegex = Regex("<pubDate>(.*?)</pubDate>")
        val rssDateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)

        val nowSec = System.currentTimeMillis() / 1000L
        val matches = itemRegex.findAll(xml).take(10)

        for ((idx, match) in matches.withIndex()) {
            val block = match.groupValues[1]
            val titleMatch = titleRegex.find(block)
            val rawTitle = (titleMatch?.groupValues?.get(1)?.ifEmpty { titleMatch.groupValues[2] } ?: "")
                .replace("&amp;", "&")
                .replace("&#039;", "'")
                .replace("&quot;", "\"")
                .trim()
            if (rawTitle.length < 12) continue

            val descMatch = descRegex.find(block)
            val rawDesc = (descMatch?.groupValues?.get(1)?.ifEmpty { descMatch.groupValues[2] } ?: "")
                .replace(Regex("<.*?>"), "")
                .trim()

            val pubDateStr = pubDateRegex.find(block)?.groupValues?.get(1)?.trim().orEmpty()
            val pubEpochSec = runCatching {
                rssDateFormat.parse(pubDateStr)?.time?.div(1000L)
            }.getOrNull() ?: (nowSec - idx * 300L)

            val lower = "$rawTitle $rawDesc".lowercase(Locale.US)
            val categoryTag = when {
                "fomc" in lower || "powell" in lower || "fed" in lower -> "Federal Reserve / FOMC"
                "rate" in lower || "yield" in lower || "treasury" in lower -> "Suku Bunga & Imbal Hasil (Yields)"
                "inflation" in lower || "cpi" in lower || "pce" in lower -> "Inflasi CPI AS"
                "jobs" in lower || "payroll" in lower || "unemployment" in lower -> "Tenaga Kerja AS (NFP)"
                "gold" in lower || "bullion" in lower || "precious" in lower -> "Arus Emas XAUUSD"
                "ecb" in lower || "euro" in lower || "lagarde" in lower -> "ECB / Zona Euro"
                else -> "Makro USD & Pasar Global"
            }

            val impact = when {
                "fomc" in lower || "fed" in lower || "rate" in lower || "cpi" in lower || "payroll" in lower -> NewsImpactLevel.HIGH
                "gold" in lower || "ecb" in lower || "yield" in lower || "dollar" in lower -> NewsImpactLevel.HIGH
                else -> NewsImpactLevel.MEDIUM
            }

            val sentiment = when {
                "cut" in lower || "dovish" in lower || "lower rate" in lower || "cool" in lower -> MacroSentiment.DOVISH_USD_BULLISH_GOLD
                "hike" in lower || "hawkish" in lower || "higher for longer" in lower || "surge" in lower -> MacroSentiment.HAWKISH_USD_BEARISH_GOLD
                "safe haven" in lower || "gold" in lower || "geopolit" in lower -> MacroSentiment.SAFE_HAVEN_GOLD_BID
                "ecb" in lower -> MacroSentiment.ECB_HAWKISH_EUR_BID
                else -> MacroSentiment.NEUTRAL_WATCH
            }

            val symbols = if ("ecb" in lower || "euro" in lower) {
                listOf(MarketSymbol.EURUSD, MarketSymbol.XAUUSD)
            } else {
                listOf(MarketSymbol.XAUUSD, MarketSymbol.EURUSD)
            }

            results.add(
                MacroNewsArticle(
                    id = "cnbc_rss_${pubEpochSec}_$idx",
                    title = rawTitle,
                    categoryTag = categoryTag,
                    affectedSymbols = symbols,
                    impact = impact,
                    sentiment = sentiment,
                    publishedTimeEpochSec = pubEpochSec,
                    sourceName = "CNBC Economy Live RSS",
                    keyTakeaway = if (rawDesc.length > 15) rawDesc.take(180) else "Berita pasar langsung dari RSS CNBC Economy."
                )
            )
        }
        return results
    }

    private fun computeRealSpreadEstimate(high: Double, low: Double, symbol: MarketSymbol): Int {
        val barPts = ((high - low) / symbol.pointSize).roundToInt().coerceAtLeast(1)
        val est = (barPts * 0.06).roundToInt()
        return est.coerceIn(symbol.defaultSpreadPoints / 2, symbol.defaultSpreadPoints * 3)
    }

    private fun roundToDigits(value: Double, digits: Int): Double {
        var factor = 1.0
        repeat(digits) { factor *= 10.0 }
        return (value * factor).roundToInt() / factor
    }

    fun getDefaultGitHubRepository(): String {
        val raw = runCatching { BuildConfig.GITHUB_REPOSITORY }.getOrDefault("").trim()
        return if (raw.isEmpty() || raw.startsWith("YOUR_GITHUB_USER") || raw == "null") {
            ""
        } else {
            raw
        }
    }

    fun getInstalledReleaseTag(): String {
        val raw = runCatching { BuildConfig.APP_RELEASE_TAG }.getOrDefault("v1.0.0").trim()
        return if (raw.isEmpty() || raw == "null") "v1.0.0" else raw
    }

    suspend fun checkLatestGitHubRelease(repoInput: String): com.example.model.GitHubReleaseInfo =
        withContext(Dispatchers.IO) {
            val installedTag = getInstalledReleaseTag()
            val cleanRepo = repoInput.trim()
                .removePrefix("https://github.com/")
                .removePrefix("http://github.com/")
                .removeSuffix(".git")
                .trim('/')

            if (!cleanRepo.contains("/") || cleanRepo.startsWith("YOUR_GITHUB_USER")) {
                return@withContext com.example.model.GitHubReleaseInfo(
                    repository = cleanRepo,
                    installedTag = installedTag,
                    isChecking = false,
                    statusMessage = "Masukkan nama repositori GitHub Anda dengan format: username/nama-repo"
                )
            }

            val apiUrl = "https://api.github.com/repos/$cleanRepo/releases/latest"
            val request = Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "HUB-SmartPatternEnginePro/1.0 (Android OTA Updater)")
                .get()
                .build()

            try {
                okHttpClient.newCall(request).execute().use { response ->
                    val code = response.code
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val msg = if (code == 404) {
                            "Belum ada Release di GitHub ($cleanRepo) atau repositori bersifat Private. Push kode ke branch main untuk memicu GitHub Actions Release otomatis."
                        } else {
                            "Gagal memeriksa GitHub Releases (HTTP $code)."
                        }
                        return@withContext com.example.model.GitHubReleaseInfo(
                            repository = cleanRepo,
                            installedTag = installedTag,
                            isChecking = false,
                            statusMessage = msg
                        )
                    }

                    val json = JSONObject(body)
                    val latestTag = json.optString("tag_name", "").trim()
                    val title = json.optString("name", latestTag).trim()
                    val notes = json.optString("body", "").trim()
                    val publishedAt = json.optString("published_at", "").replace("T", " ").removeSuffix("Z")
                    val htmlUrl = json.optString("html_url", "https://github.com/$cleanRepo/releases")

                    var apkUrl = ""
                    var apkName = ""
                    var apkSizeMb = 0.0
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            val downloadUrl = asset.optString("browser_download_url", "")
                            if (name.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()) {
                                apkUrl = downloadUrl
                                apkName = name
                                apkSizeMb = roundToDigits(asset.optLong("size", 0L) / (1024.0 * 1024.0), 2)
                                if (!name.contains("latest", ignoreCase = true)) {
                                    break
                                }
                            }
                        }
                    }

                    val isNewer = latestTag.isNotBlank() && latestTag != installedTag
                    val statusMsg = when {
                        apkUrl.isBlank() -> "Release $latestTag ditemukan, namun belum ada file .apk terlampir."
                        isNewer -> "Tersedia pembaruan APK versi $latestTag di GitHub Releases! Ketuk Unduh APK untuk memperbarui."
                        else -> "Aplikasi sudah berada di versi terbaru ($installedTag). Anda tetap dapat mengunduh ulang APK."
                    }

                    com.example.model.GitHubReleaseInfo(
                        repository = cleanRepo,
                        installedTag = installedTag,
                        latestTag = latestTag,
                        releaseTitle = title,
                        releaseNotes = notes,
                        publishedAt = publishedAt,
                        apkDownloadUrl = apkUrl,
                        apkFileName = apkName,
                        apkSizeMb = apkSizeMb,
                        htmlReleaseUrl = htmlUrl,
                        isUpdateAvailable = isNewer,
                        isChecking = false,
                        statusMessage = statusMsg
                    )
                }
            } catch (e: Exception) {
                com.example.model.GitHubReleaseInfo(
                    repository = cleanRepo,
                    installedTag = installedTag,
                    isChecking = false,
                    statusMessage = "Gagal terhubung ke GitHub API: ${e.message ?: "Periksa koneksi internet"}"
                )
            }
        }
}

