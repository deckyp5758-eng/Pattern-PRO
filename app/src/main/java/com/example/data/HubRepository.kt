package com.example.data

import com.example.data.local.CustomNewsEventEntity
import com.example.data.local.HubDao
import com.example.data.local.SavedSignalSnapshotEntity
import com.example.data.remote.LiveMarketAndNewsService
import com.example.domain.SystemDiagnosticGuard
import com.example.model.MacroNewsArticle
import com.example.model.MacroSentiment
import com.example.model.MarketSymbol
import com.example.model.Mt5RateBar
import com.example.model.NewsImpactLevel
import com.example.model.OnlineDataProvider
import com.example.model.Timeframe
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class HubRepository(
    private val hubDao: HubDao,
    private val remoteService: LiveMarketAndNewsService = LiveMarketAndNewsService()
) {
    val savedSignalLogs: Flow<List<SavedSignalSnapshotEntity>> = hubDao.observeSignalLogs()
    val customNewsEvents: Flow<List<CustomNewsEventEntity>> = hubDao.observeCustomNewsEvents()

    private val fetchMutex = Mutex()
    private val newsMutex = Mutex()

    private var cachedNews: List<MacroNewsArticle>? = null
    private var lastNewsFetchTimeMs: Long = 0L

    companion object {
        private const val NEWS_CACHE_TTL_MS = 5 * 60 * 1000L
    }

    fun isMetaApiConfigured(): Boolean = remoteService.isMetaApiConfigured()

    fun getDefaultGitHubRepository(): String = remoteService.getDefaultGitHubRepository()

    fun getInstalledReleaseTag(): String = remoteService.getInstalledReleaseTag()

    suspend fun checkLatestGitHubRelease(repoInput: String) =
        remoteService.checkLatestGitHubRelease(repoInput)

    sealed class BundleFetchOutcome {
        data class Success(
            val symbol: MarketSymbol,
            val provider: OnlineDataProvider,
            val ratesByTimeframe: Map<Timeframe, List<Mt5RateBar>>,
            val primaryEndpointUrl: String,
            val httpStatusCode: Int,
            val serverFetchTimeMs: Long,
            val newsArticles: List<MacroNewsArticle>,
            val wasNewsServedFromCache: Boolean,
            val fetchDurationMs: Long
        ) : BundleFetchOutcome()

        data class Error(
            val symbol: MarketSymbol,
            val provider: OnlineDataProvider,
            val endpointUrl: String,
            val httpStatusCode: Int,
            val errorMessage: String,
            val failedAtMs: Long
        ) : BundleFetchOutcome()
    }

    /**
     * Mengambil seluruh 5 timeframe (M1, M5, M15, M30, H1) secara terkoordinasi dengan Mutex
     * agar tidak pernah terjadi tumpang tindih permintaan atau data antar-timeframe yang belang.
     */
    suspend fun fetchCompleteSymbolBundle(
        symbol: MarketSymbol,
        provider: OnlineDataProvider,
        activeTimeframe: Timeframe,
        forceRefreshNews: Boolean = false
    ): BundleFetchOutcome = fetchMutex.withLock {
        val startMs = System.currentTimeMillis()
        coroutineScope {
            val tfJobs = Timeframe.entries.associateWith { tf ->
                async {
                    val firstTry = remoteService.fetchSymbolRates(symbol, tf, provider)
                    if (firstTry is LiveMarketAndNewsService.RatesFetchOutcome.Success || provider.isOfflineTestMode) {
                        firstTry
                    } else {
                        // Satu kali percobaan ulang otomatis jika salah satu timeframe mengalami gangguan sesaat
                        remoteService.fetchSymbolRates(symbol, tf, provider)
                    }
                }
            }
            val newsJob = async { getOrFetchMacroNews(forceRefreshNews, provider.isOfflineTestMode) }

            val ratesMap = mutableMapOf<Timeframe, List<Mt5RateBar>>()
            var firstError: LiveMarketAndNewsService.RatesFetchOutcome.Error? = null
            var activeUrl = remoteService.buildProviderUrl(symbol, activeTimeframe, provider)
            var activeHttpCode = 200
            var serverFetchMs = System.currentTimeMillis()

            for ((tf, deferred) in tfJobs) {
                when (val res = deferred.await()) {
                    is LiveMarketAndNewsService.RatesFetchOutcome.Success -> {
                        val bar1Close = res.bars.getOrNull(1)?.close ?: 0.0
                        if (SystemDiagnosticGuard.isPriceMatchingSymbol(symbol, bar1Close)) {
                            ratesMap[tf] = res.bars
                            if (tf == activeTimeframe) {
                                activeUrl = res.endpointUrl
                                activeHttpCode = res.httpStatusCode
                                serverFetchMs = res.serverFetchTimestampMs
                            }
                        } else {
                            firstError = LiveMarketAndNewsService.RatesFetchOutcome.Error(
                                provider = provider,
                                endpointUrl = res.endpointUrl,
                                httpStatusCode = res.httpStatusCode,
                                errorMessage = "Data harga ($bar1Close) tidak sesuai dengan simbol ${symbol.code} (ditolak agar tidak salah data).",
                                failedAtMs = System.currentTimeMillis()
                            )
                        }
                    }
                    is LiveMarketAndNewsService.RatesFetchOutcome.Error -> {
                        if (firstError == null || tf == activeTimeframe) {
                            firstError = res
                        }
                    }
                }
            }

            // Jujur 100%: Jika timeframe aktif gagal, kembalikan Error jujur tanpa data palsu
            if (!ratesMap.containsKey(activeTimeframe) || ratesMap.isEmpty()) {
                val err = firstError
                return@coroutineScope BundleFetchOutcome.Error(
                    symbol = symbol,
                    provider = provider,
                    endpointUrl = err?.endpointUrl ?: activeUrl,
                    httpStatusCode = err?.httpStatusCode ?: 0,
                    errorMessage = err?.errorMessage ?: "Provider ${provider.displayName} sedang offline atau tidak dapat diakses.",
                    failedAtMs = System.currentTimeMillis()
                )
            }

            val (newsList, fromCache) = newsJob.await()
            val duration = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)

            BundleFetchOutcome.Success(
                symbol = symbol,
                provider = provider,
                ratesByTimeframe = ratesMap.toMap(),
                primaryEndpointUrl = activeUrl,
                httpStatusCode = activeHttpCode,
                serverFetchTimeMs = serverFetchMs,
                newsArticles = newsList,
                wasNewsServedFromCache = fromCache,
                fetchDurationMs = duration
            )
        }
    }

    /**
     * Mengambil pembaruan langsung dari server online untuk 1 timeframe aktif
     * dengan validasi kecocokan harga simbol.
     */
    suspend fun pollSingleTimeframeFromServer(
        symbol: MarketSymbol,
        timeframe: Timeframe,
        provider: OnlineDataProvider
    ): LiveMarketAndNewsService.RatesFetchOutcome {
        val res = remoteService.fetchSymbolRates(symbol, timeframe, provider)
        if (res is LiveMarketAndNewsService.RatesFetchOutcome.Success) {
            val closePrice = res.bars.getOrNull(1)?.close ?: 0.0
            if (!SystemDiagnosticGuard.isPriceMatchingSymbol(symbol, closePrice)) {
                return LiveMarketAndNewsService.RatesFetchOutcome.Error(
                    provider = provider,
                    endpointUrl = res.endpointUrl,
                    httpStatusCode = res.httpStatusCode,
                    errorMessage = "Data polling ($closePrice) ditolak karena tidak cocok dengan simbol ${symbol.code}.",
                    failedAtMs = System.currentTimeMillis()
                )
            }
        }
        return res
    }

    suspend fun clearNewsCache() = newsMutex.withLock {
        cachedNews = null
        lastNewsFetchTimeMs = 0L
    }

    private suspend fun getOrFetchMacroNews(
        forceRefresh: Boolean,
        isOfflineTestMode: Boolean
    ): Pair<List<MacroNewsArticle>, Boolean> = newsMutex.withLock {
        if (isOfflineTestMode) {
            return@withLock Pair(emptyList(), false)
        }
        val now = System.currentTimeMillis()
        val existing = cachedNews
        if (!forceRefresh && existing != null && (now - lastNewsFetchTimeMs) < NEWS_CACHE_TTL_MS) {
            return@withLock Pair(existing, true)
        }
        return@withLock when (val outcome = remoteService.fetchLiveMacroNews(false)) {
            is LiveMarketAndNewsService.NewsFetchOutcome.Success -> {
                cachedNews = outcome.articles
                lastNewsFetchTimeMs = now
                Pair(outcome.articles, false)
            }
            is LiveMarketAndNewsService.NewsFetchOutcome.Error -> {
                Pair(existing.orEmpty(), false)
            }
        }
    }

    suspend fun saveClosedCandleSnapshot(entity: SavedSignalSnapshotEntity) {
        hubDao.insertSignalLog(entity)
    }

    suspend fun deleteSignalLog(id: Int) {
        hubDao.deleteSignalLogById(id)
    }

    suspend fun clearAllSignalLogs() {
        hubDao.clearAllSignalLogs()
    }

    suspend fun addCustomMacroNewsEvent(
        title: String,
        categoryTag: String,
        targetSymbolCode: String,
        impactLevel: NewsImpactLevel,
        sentiment: MacroSentiment,
        takeawayNote: String
    ) {
        hubDao.insertCustomNewsEvent(
            CustomNewsEventEntity(
                title = title,
                categoryTag = categoryTag,
                targetSymbolCode = targetSymbolCode,
                impactLevel = impactLevel.name,
                sentimentCode = sentiment.name,
                takeawayNote = takeawayNote
            )
        )
    }
}
