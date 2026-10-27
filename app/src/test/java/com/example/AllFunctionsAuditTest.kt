package com.example

import com.example.domain.CandleAndStructureEngines
import com.example.domain.IndicatorAndConfluenceEngines
import com.example.domain.Mql5ConfigParams
import com.example.domain.Mql5IndicatorCodeGenerator
import com.example.domain.SystemDiagnosticGuard
import com.example.model.AnalyticalVerdict
import com.example.model.MacroSentiment
import com.example.model.MarketStructureClass
import com.example.model.MarketSymbol
import com.example.model.Mt5RateBar
import com.example.model.MacroNewsArticle
import com.example.model.NewsImpactLevel
import com.example.model.OnlineDataProvider
import com.example.model.SrInteractionType
import com.example.model.SwingLabel
import com.example.model.Timeframe
import com.example.data.remote.LiveMarketAndNewsService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

/**
 * Audit Menyeluruh untuk Semua Fungsi dan Modul Inti Aplikasi:
 * 1. Seluruh 10 Mesin Analisis (Data, Candle, Structure, Trend, Momentum, Volatility, Volume, S/R, News, Confluence)
 * 2. Penjelasan Skenario Grafik & Formating Salin Level MT5 (Support, Resistance, SL, TP)
 * 3. Logika Deteksi Struktur Pasar (HH, HL, LH, LL & BOS/CHoCH)
 * 4. Generator Kode MQL5 Indikator MT5
 * 5. Diagnostic Guard & Validasi Anomali Data
 * 6. Model Simbol & Timeframe Multi-Instrumen (XAUUSD & EURUSD)
 */
class AllFunctionsAuditTest {

    private fun generateBullishSeries(count: Int = 300): List<Mt5RateBar> {
        val chron = (0 until count).map { cIdx ->
            val wave = sin(cIdx * 0.25) * 3.0
            val trend = 2600.0 + cIdx * 0.5 + wave
            Mt5RateBar(
                barIndex = 0,
                timeEpochSec = 1727000000L + cIdx * 900L,
                open = trend - 0.5,
                high = trend + 2.0,
                low = trend - 1.5,
                close = trend + 0.8,
                tickVolume = 1000L + (cIdx % 20) * 50L,
                realVolume = 10000L,
                spread = 18
            )
        }
        return chron.asReversed().mapIndexed { idx, b -> b.copy(barIndex = idx) }
    }

    private fun generateBearishSeries(count: Int = 300): List<Mt5RateBar> {
        val chron = (0 until count).map { cIdx ->
            val wave = sin(cIdx * 0.25) * 3.0
            val trend = 2800.0 - cIdx * 0.5 + wave
            Mt5RateBar(
                barIndex = 0,
                timeEpochSec = 1727000000L + cIdx * 900L,
                open = trend + 0.5,
                high = trend + 1.5,
                low = trend - 2.0,
                close = trend - 0.8,
                tickVolume = 1000L + (cIdx % 20) * 50L,
                realVolume = 10000L,
                spread = 18
            )
        }
        return chron.asReversed().mapIndexed { idx, b -> b.copy(barIndex = idx) }
    }

    // ==========================================
    // AUDIT 1: Seluruh 10 Mesin Analisis Pasar
    // ==========================================
    @Test
    fun audit_allTenEngines_executeFlawlessly() {
        val bars = generateBullishSeries(300)
        val tfMap = Timeframe.entries.associateWith { bars }

        val news = listOf(
            com.example.model.MacroNewsArticle(
                id = "n1",
                title = "US Core CPI Steady at Expected Rate",
                categoryTag = "CPI",
                affectedSymbols = listOf(MarketSymbol.XAUUSD),
                impact = NewsImpactLevel.HIGH,
                sentiment = MacroSentiment.HAWKISH_USD_BEARISH_GOLD,
                publishedTimeEpochSec = 1727000000L,
                sourceName = "ForexFactory",
                keyTakeaway = "Inflation shows stability."
            )
        )

        val analysis = IndicatorAndConfluenceEngines.buildCompleteAnalysis(
            symbol = MarketSymbol.XAUUSD,
            brokerSuffix = "",
            activeTimeframe = Timeframe.M15,
            activeBars = bars,
            allTimeframeBars = tfMap,
            isLiveFeed = true,
            feedSourceName = "Comprehensive Audit Feed",
            newsArticles = news,
            isLiveRss = true
        )

        // 1. Data Engine
        assertNotNull(analysis.dataEngine)
        assertEquals("XAUUSD", analysis.dataEngine.symbolWithSuffix)
        assertEquals(Timeframe.M15, analysis.dataEngine.timeframe)
        assertEquals(300, analysis.dataEngine.totalBarsLoaded)
        assertTrue(analysis.dataEngine.isLiveFeedConnected)

        // 2. Candle Engine (Wajib Bar 1 terisolasi)
        assertNotNull(analysis.candleEngine)
        assertEquals(1, analysis.candleEngine.closedBar1Metrics.barIndex)
        assertTrue(analysis.candleEngine.recentHistoricalPatterns.all { it.closedBarIndex >= 1 })

        // 3. Structure Engine (Swings HH, HL, LH, LL)
        assertNotNull(analysis.structureEngine)
        assertTrue(analysis.structureEngine.confirmedSwings.isNotEmpty())
        assertTrue(
            analysis.structureEngine.classification == MarketStructureClass.BULLISH_STRUCTURE ||
                analysis.structureEngine.classification == MarketStructureClass.RANGE
        )

        // 4. Trend Engine
        assertNotNull(analysis.trendEngine)
        assertTrue(analysis.trendEngine.ema20 > 0.0)
        assertTrue(analysis.trendEngine.ema50 > 0.0)
        assertTrue(analysis.trendEngine.ema200 > 0.0)

        // 5. Momentum Engine
        assertNotNull(analysis.momentumEngine)
        assertTrue(analysis.momentumEngine.rsi14 in 0.0..100.0)
        assertTrue(analysis.momentumEngine.adx14 >= 0.0)

        // 6. Volatility Engine
        assertNotNull(analysis.volatilityEngine)
        assertTrue(analysis.volatilityEngine.atr14 > 0.0)
        assertTrue(analysis.volatilityEngine.longStopReferencePrice < bars[1].close)
        assertTrue(analysis.volatilityEngine.longTarget1RPrice > bars[1].close)

        // 7. Volume Engine
        assertNotNull(analysis.volumeEngine)
        assertTrue(analysis.volumeEngine.smaVolume20 > 0.0)

        // 8. S/R Engine
        assertNotNull(analysis.srEngine)
        assertTrue(analysis.srEngine.zones.isNotEmpty())

        // 9. News Engine
        assertNotNull(analysis.newsEngine)
        assertTrue(analysis.newsEngine.highImpactCount >= 1)

        // 10. Confluence Engine
        assertNotNull(analysis.verdict)
        assertTrue(analysis.bullishConfluenceScore >= 0)
        assertTrue(analysis.bearishConfluenceScore >= 0)

        // 11. Smart Money Concepts (FVG & Order Block)
        assertNotNull(analysis.smartMoneyEngine)
        assertTrue(analysis.smartMoneyEngine.fvgConfluenceNote.isNotBlank())

        // 12. Global Trading Session Engine
        assertNotNull(analysis.sessionEngine)
        assertTrue(analysis.sessionEngine.utcHour in 0..23)
        assertNotNull(analysis.sessionEngine.activeSession)

        // 13. HTF Trend Alignment Guard
        assertNotNull(analysis.htfAlignmentGuard)
        assertNotNull(analysis.htfAlignmentGuard.status)
        assertTrue(analysis.htfAlignmentGuard.advice.isNotBlank())

        // 14. Historical Volatility & Breakout Probability Zone
        assertNotNull(analysis.breakoutProbability)
        assertTrue(analysis.breakoutProbability.bullishBreakoutProbPct in 0..100)
        assertTrue(analysis.breakoutProbability.bearishBreakdownProbPct in 0..100)
        assertTrue(analysis.breakoutProbability.rangeBoundHoldProbPct in 0..100)
        assertTrue(analysis.breakoutProbability.recentVolatilityPoints.isNotEmpty())
    }

    // ==========================================
    // AUDIT 2: Deteksi Pola & Struktur (HH, HL, LH, LL)
    // ==========================================
    @Test
    fun audit_marketStructure_detectsSwingsCorrectly() {
        val bullBars = generateBullishSeries(200)
        val volBull = IndicatorAndConfluenceEngines.runVolatilityEngine(bullBars, MarketSymbol.XAUUSD)
        val structBull = CandleAndStructureEngines.runMarketStructureEngine(bullBars, volBull.atr14)

        assertTrue(structBull.confirmedSwings.isNotEmpty())
        val swingTypes = structBull.confirmedSwings.map { it.label }
        // Dalam tren naik harus terbentuk ayunan Higher High atau Higher Low
        assertTrue(swingTypes.contains(SwingLabel.HH) || swingTypes.contains(SwingLabel.HL))

        val bearBars = generateBearishSeries(200)
        val volBear = IndicatorAndConfluenceEngines.runVolatilityEngine(bearBars, MarketSymbol.XAUUSD)
        val structBear = CandleAndStructureEngines.runMarketStructureEngine(bearBars, volBear.atr14)

        assertTrue(structBear.confirmedSwings.isNotEmpty())
        val bearSwingTypes = structBear.confirmedSwings.map { it.label }
        // Dalam tren turun harus terbentuk ayunan Lower High atau Lower Low
        assertTrue(bearSwingTypes.contains(SwingLabel.LH) || bearSwingTypes.contains(SwingLabel.LL))
        assertEquals(MarketStructureClass.BEARISH_STRUCTURE, structBear.classification)
    }

    // ==========================================
    // AUDIT 3: Skenario Grafik & Format Salin Level MT5
    // ==========================================
    @Test
    fun audit_scenarioAndLevelClipboardFormatting() {
        val bars = generateBullishSeries(100)
        val bar1 = bars[1]
        val atr = 15.0 // misal ATR Emas 15.0 poin ($15.00)
        val digits = MarketSymbol.XAUUSD.digits // 2 digit

        val supp = bar1.close - atr * 1.5
        val res = bar1.close + atr * 1.5
        val sl = bar1.close - atr * 1.5
        val tp = bar1.close + atr * 2.25

        val formatSupp = String.format(java.util.Locale.US, "%.${digits}f", supp)
        val formatRes = String.format(java.util.Locale.US, "%.${digits}f", res)
        val formatSl = String.format(java.util.Locale.US, "%.${digits}f", sl)
        val formatTp = String.format(java.util.Locale.US, "%.${digits}f", tp)

        // Pastikan format harga bersih 2 desimal untuk XAUUSD tanpa karakter aneh
        assertTrue(formatSupp.matches(Regex("""\d+\.\d{2}""")))
        assertTrue(formatRes.matches(Regex("""\d+\.\d{2}""")))
        assertTrue(formatSl.matches(Regex("""\d+\.\d{2}""")))
        assertTrue(formatTp.matches(Regex("""\d+\.\d{2}""")))

        // Uji untuk EURUSD (5 desimal)
        val eurusdDigits = MarketSymbol.EURUSD.digits
        val eurusdPrice = 1.08452
        val formatEur = String.format(java.util.Locale.US, "%.${eurusdDigits}f", eurusdPrice)
        assertEquals("1.08452", formatEur)
    }

    // ==========================================
    // AUDIT 4: Generator Kode Indikator MQL5
    // ==========================================
    @Test
    fun audit_mql5Generator_producesCompleteValidSource() {
        val bars = generateBullishSeries(150)
        val tfMap = Timeframe.entries.associateWith { bars }
        val analysis = IndicatorAndConfluenceEngines.buildCompleteAnalysis(
            symbol = MarketSymbol.XAUUSD,
            brokerSuffix = "",
            activeTimeframe = Timeframe.M15,
            activeBars = bars,
            allTimeframeBars = tfMap,
            isLiveFeed = true,
            feedSourceName = "MQL5 Generator Audit",
            newsArticles = emptyList(),
            isLiveRss = false
        )

        val config = Mql5ConfigParams(
            brokerSuffix = "",
            emaFast = 20,
            emaMid = 50,
            emaSlow = 200,
            rsiPeriod = 14,
            adxPeriod = 14,
            atrPeriod = 14,
            swingStrength = 3
        )
        val mq5Source = Mql5IndicatorCodeGenerator.generateMq5Source(config, analysis)

        assertNotNull(mq5Source)
        assertTrue("Wajib mengandung properti indikator", mq5Source.contains("#property indicator_chart_window"))
        assertTrue("Wajib mendefinisikan OnInit", mq5Source.contains("int OnInit()"))
        assertTrue("Wajib mendefinisikan OnCalculate", mq5Source.contains("int OnCalculate("))
        assertTrue("Wajib mendefinisikan OnDeinit", mq5Source.contains("void OnDeinit("))
        assertTrue("Wajib mengisolasi candle tutup 1 (anti-repaint)", mq5Source.contains("FORMING_BAR_0_IGNORED"))
        assertTrue("Wajib memuat parameter InpEmaFast", mq5Source.contains("InpEmaFast") && mq5Source.contains("20"))
    }

    // ==========================================
    // AUDIT 5: Diagnostic Guard & Integritas Data
    // ==========================================
    @Test
    fun audit_systemDiagnosticGuard_catchesAllCorruptions() {
        val validBars = generateBullishSeries(100)

        // 1. Data Valid harus lolos
        val (isValid, notes) = SystemDiagnosticGuard.validateRateSeries(
            MarketSymbol.XAUUSD,
            Timeframe.M15,
            validBars
        )
        assertTrue(isValid)

        // 2. Anomali Harga Rendah > Tinggi (Inversi Bar)
        val invertedHighLowBars = validBars.toMutableList().apply {
            this[5] = this[5].copy(high = 2500.0, low = 2700.0)
        }
        val (isInvValid, invNotes) = SystemDiagnosticGuard.validateRateSeries(
            MarketSymbol.XAUUSD,
            Timeframe.M15,
            invertedHighLowBars
        )
        assertFalse(isInvValid)
        assertTrue(invNotes.any { it.contains("Anomali OHLC") })

        // 3. Anomali Harga Nol atau Negatif
        val zeroPriceBars = validBars.toMutableList().apply {
            this[1] = this[1].copy(close = 0.0)
        }
        val (isZeroValid, zeroNotes) = SystemDiagnosticGuard.validateRateSeries(
            MarketSymbol.XAUUSD,
            Timeframe.M15,
            zeroPriceBars
        )
        assertFalse(isZeroValid)
        assertTrue(zeroNotes.any { it.contains("tidak sesuai dengan rentang wajar") || it.contains("Anomali OHLC") })
    }

    // ==========================================
    // AUDIT 6: Provider & Simbol Enum Integrity
    // ==========================================
    @Test
    fun audit_symbolAndProviderEnums_matchSpecs() {
        // Simbol XAUUSD & EURUSD
        assertEquals("XAUUSD", MarketSymbol.XAUUSD.code)
        assertEquals(2, MarketSymbol.XAUUSD.digits)
        assertEquals(0.01, MarketSymbol.XAUUSD.pointSize, 0.000001)

        assertEquals("EURUSD", MarketSymbol.EURUSD.code)
        assertEquals(5, MarketSymbol.EURUSD.digits)
        assertEquals(0.00001, MarketSymbol.EURUSD.pointSize, 0.0000001)

        // Pastikan provider online memiliki display name dan endpoint URL
        val providers = OnlineDataProvider.entries
        assertTrue(providers.contains(OnlineDataProvider.BINANCE_VISION))
        assertTrue(providers.contains(OnlineDataProvider.ELEV8_METAAPI))
        assertTrue(providers.contains(OnlineDataProvider.KRAKEN_SPOT))
        assertTrue(providers.contains(OnlineDataProvider.YAHOO_FINANCE))

        // Seluruh timeframe MT5 terdaftar lengkap
        val timeframes = Timeframe.entries
        assertEquals(5, timeframes.size)
        assertTrue(timeframes.contains(Timeframe.M1))
        assertTrue(timeframes.contains(Timeframe.M5))
        assertTrue(timeframes.contains(Timeframe.M15))
        assertTrue(timeframes.contains(Timeframe.M30))
        assertTrue(timeframes.contains(Timeframe.H1))
    }

    // ==========================================
    // AUDIT 7: Risk vs Reward & Proyeksi Target
    // ==========================================
    @Test
    fun audit_riskRewardCalculations_bullishAndBearish() {
        val bullBars = generateBullishSeries(100)
        val tfMap = Timeframe.entries.associateWith { bullBars }
        val bullAnalysis = IndicatorAndConfluenceEngines.buildCompleteAnalysis(
            symbol = MarketSymbol.XAUUSD,
            brokerSuffix = "",
            activeTimeframe = Timeframe.M15,
            activeBars = bullBars,
            allTimeframeBars = tfMap,
            isLiveFeed = true,
            feedSourceName = "RR Bullish Audit",
            newsArticles = emptyList(),
            isLiveRss = false
        )

        val vol = bullAnalysis.volatilityEngine
        val bar1Close = bullAnalysis.bars[1].close

        // Proyeksi Bullish (Long): TP > Entry > SL
        val bullEntry = bar1Close
        val bullSl = vol.longStopReferencePrice
        val bullTp1 = vol.longTarget1RPrice
        val bullTp2 = vol.longTarget2RPrice

        assertTrue("Bullish entry harus positif", bullEntry > 0.0)
        assertTrue("Bullish SL wajib di bawah entry", bullSl < bullEntry)
        assertTrue("Bullish TP 1R wajib di atas entry", bullTp1 > bullEntry)
        assertTrue("Bullish TP 2R wajib di atas TP 1R", bullTp2 > bullTp1)

        val bullRisk = bullEntry - bullSl
        val bullReward = bullTp1 - bullEntry
        val bullRatio = bullReward / bullRisk
        assertEquals(1.5, bullRatio, 0.05)

        // Proyeksi Bearish (Short): SL > Entry > TP
        val bearSl = vol.shortStopReferencePrice
        val bearTp1 = vol.shortTarget1RPrice
        val bearTp2 = vol.shortTarget2RPrice

        assertTrue("Bearish SL wajib di atas entry", bearSl > bullEntry)
        assertTrue("Bearish TP 1R wajib di bawah entry", bearTp1 < bullEntry)
        assertTrue("Bearish TP 2R wajib di bawah TP 1R", bearTp2 < bearTp1)

        val bearRisk = bearSl - bullEntry
        val bearReward = bullEntry - bearTp1
        val bearRatio = bearReward / bearRisk
        assertEquals(1.5, bearRatio, 0.05)
    }

    // ==========================================
    // AUDIT 8: Volatility Breakout & Probability Zone
    // ==========================================
    @Test
    fun audit_volatilityProbabilityZone_computations() {
        val bars = generateBullishSeries(100)
        val atr = 12.5
        val supp = 2650.0
        val res = 2700.0

        val probReport = IndicatorAndConfluenceEngines.computeBreakoutProbability(
            bars = bars,
            atr14 = atr,
            nearestSupport = supp,
            nearestResistance = res
        )

        assertNotNull(probReport)
        assertTrue(probReport.bullishBreakoutProbPct in 5..80)
        assertTrue(probReport.bearishBreakdownProbPct in 5..80)
        assertTrue(probReport.rangeBoundHoldProbPct >= 10)
        assertTrue(probReport.distanceToResistanceAtr > 0.0)
        assertTrue(probReport.distanceToSupportAtr > 0.0)
        assertTrue(probReport.primaryBreakoutRisk.isNotBlank())
        assertEquals(25, probReport.recentVolatilityPoints.size)

        // Pastikan batas pita atas >= harga tutup >= batas pita bawah pada titik volatilitas
        probReport.recentVolatilityPoints.forEach { pt ->
            assertTrue(pt.upperVolBand >= pt.closePrice)
            assertTrue(pt.closePrice >= pt.lowerVolBand)
            assertTrue(pt.historicalVolatility > 0.0)
        }
    }

    // ==========================================
    // AUDIT 9: Multi-Provider & MetaApi Credential Isolation
    // ==========================================
    @Test
    fun audit_metaApiCredentialsAndProviderUrls() {
        val service = LiveMarketAndNewsService()

        // 1. Awalnya harus bersih tanpa token default yang ter-bake
        assertEquals("", service.getMetaApiAccountId())
        assertEquals("", service.getMetaApiAuthToken())
        assertFalse(service.isMetaApiConfigured())

        // 2. Saat kredensial dimasukkan manual
        service.setCustomMetaApiCredentials("user-acc-12345", "user-token-secret-xyz")
        assertEquals("user-acc-12345", service.getMetaApiAccountId())
        assertEquals("user-token-secret-xyz", service.getMetaApiAuthToken())
        assertTrue(service.isMetaApiConfigured())

        // 3. Saat kredensial dihapus
        service.clearCustomMetaApiCredentials()
        assertEquals("", service.getMetaApiAccountId())
        assertEquals("", service.getMetaApiAuthToken())
        assertFalse(service.isMetaApiConfigured())

        // 4. Verifikasi URL provider open online (Binance Vision, Yahoo Finance, Kraken Spot)
        val binanceUrl = service.buildProviderUrl(MarketSymbol.XAUUSD, Timeframe.M15, OnlineDataProvider.BINANCE_VISION)
        assertTrue(binanceUrl.contains("data-api.binance.vision"))
        assertTrue(binanceUrl.contains("PAXGUSDT"))

        val yahooEurUrl = service.buildProviderUrl(MarketSymbol.EURUSD, Timeframe.H1, OnlineDataProvider.YAHOO_FINANCE)
        assertTrue(yahooEurUrl.contains("query1.finance.yahoo.com"))
        assertTrue(yahooEurUrl.contains("EURUSD=X"))

        val krakenUrl = service.buildProviderUrl(MarketSymbol.XAUUSD, Timeframe.M5, OnlineDataProvider.KRAKEN_SPOT)
        assertTrue(krakenUrl.contains("api.kraken.com"))
        assertTrue(krakenUrl.contains("PAXGUSD"))
    }
}
