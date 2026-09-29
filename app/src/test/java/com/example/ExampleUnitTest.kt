package com.example

import com.example.domain.CandleAndStructureEngines
import com.example.domain.IndicatorAndConfluenceEngines
import com.example.domain.Mql5ConfigParams
import com.example.domain.Mql5IndicatorCodeGenerator
import com.example.domain.SystemDiagnosticGuard
import com.example.model.MarketStructureClass
import com.example.model.MarketSymbol
import com.example.model.Mt5RateBar
import com.example.model.Timeframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    private fun sampleBars(count: Int = 300): List<Mt5RateBar> {
        return (0 until count).map { idx ->
            val base = 2650.0 + (count - idx) * 0.45
            Mt5RateBar(
                barIndex = idx,
                timeEpochSec = 1727500000L - idx * 900L,
                open = base,
                high = base + 1.8,
                low = base - 1.2,
                close = base + 0.9,
                tickVolume = if (idx == 1) 1500L else 600L,
                realVolume = 12000L,
                spread = 18
            )
        }
    }

    @Test
    fun closedCandleEngine_neverUsesFormingBar0ForFinalSignals() {
        val bars = sampleBars()
        val vol = IndicatorAndConfluenceEngines.runVolatilityEngine(bars, MarketSymbol.XAUUSD)
        val candleReport = CandleAndStructureEngines.runCandleEngine(bars, MarketSymbol.XAUUSD, vol.atr14)

        assertTrue(candleReport.recentHistoricalPatterns.all { it.closedBarIndex >= 1 })
        assertEquals(1, candleReport.closedBar1Metrics.barIndex)
    }

    @Test
    fun trendMomentumVolumeAndMql5Generator_produceValidOutput() {
        val bars = sampleBars()
        val map = Timeframe.entries.associateWith { bars }
        val analysis = IndicatorAndConfluenceEngines.buildCompleteAnalysis(
            symbol = MarketSymbol.XAUUSD,
            brokerSuffix = ".sml",
            activeTimeframe = Timeframe.M15,
            activeBars = bars,
            allTimeframeBars = map,
            isLiveFeed = true,
            feedSourceName = "UnitTest Feed",
            newsArticles = emptyList(),
            isLiveRss = true
        )

        assertEquals("XAUUSD.sml", analysis.dataEngine.symbolWithSuffix)
        assertTrue(analysis.volumeEngine.isVolumeExpansion)
        assertTrue(analysis.trendEngine.isPerfectBullishStack)

        val mq5 = Mql5IndicatorCodeGenerator.generateMq5Source(Mql5ConfigParams(), analysis)
        assertNotNull(mq5)
        assertTrue(mq5.contains("OnCalculate"))
        assertTrue(mq5.contains("FORMING_BAR_0_IGNORED"))

        val (isValid, notes) = SystemDiagnosticGuard.validateRateSeries(
            MarketSymbol.XAUUSD,
            Timeframe.M15,
            bars
        )
        assertTrue(isValid)
        assertTrue(notes.isNotEmpty())
        val sig1 = SystemDiagnosticGuard.computeClosedBar1Signature(MarketSymbol.XAUUSD, Timeframe.M15, bars[1])
        val sig2 = SystemDiagnosticGuard.computeClosedBar1Signature(MarketSymbol.XAUUSD, Timeframe.M15, bars[1])
        assertEquals(sig1, sig2)
    }

    @Test
    fun structureAndTrendEngine_neverInvertBullishUptrendWithMinorPause() {
        // Buat 300 candle tren naik dengan gelombang HH -> HL -> HH -> koreksi kecil di dekat puncak
        val chron = (0 until 300).map { cIdx ->
            val wave = kotlin.math.sin(cIdx * 0.35) * 4.5
            val trend = 2600.0 + cIdx * 0.65 + wave
            Mt5RateBar(
                barIndex = 0,
                timeEpochSec = 1727000000L + cIdx * 900L,
                open = trend - 0.8,
                high = trend + 2.2,
                low = trend - 2.0,
                close = trend + 0.8,
                tickVolume = 800L,
                realVolume = 8000L,
                spread = 18
            )
        }
        val mt5Series = chron.asReversed().mapIndexed { idx, b -> b.copy(barIndex = idx) }
        val vol = IndicatorAndConfluenceEngines.runVolatilityEngine(mt5Series, MarketSymbol.XAUUSD)
        val struct = CandleAndStructureEngines.runMarketStructureEngine(mt5Series, vol.atr14)
        val trend = IndicatorAndConfluenceEngines.runTrendEngine(mt5Series, MarketSymbol.XAUUSD)

        assertTrue("Tren harus Bullish, bukan terbalik", trend.isPerfectBullishStack)
        assertEquals(
            "Struktur pasar pada tren naik tidak boleh terbaca terbalik sebagai Bearish",
            MarketStructureClass.BULLISH_STRUCTURE,
            struct.classification
        )
    }

    @Test
    fun diagnosticGuard_blocksSymbolCrossContaminationAndTimestampOverlap() {
        val goldBars = sampleBars(100)
        // Harga Emas (~2690) harus ditolak jika diklaim sebagai EURUSD (~1.10)
        val (isValidWrongSymbol, wrongSymbolNotes) = SystemDiagnosticGuard.validateRateSeries(
            symbol = MarketSymbol.EURUSD,
            timeframe = Timeframe.M15,
            bars = goldBars
        )
        assertTrue("Data XAUUSD yang masuk ke EURUSD wajib ditolak", !isValidWrongSymbol)
        assertTrue(wrongSymbolNotes.any { it.contains("tidak sesuai dengan rentang wajar EURUSD") })

        // Tumpang tindih timestamp antara Candle [0] dan Candle [1] wajib terdeteksi
        val overlappingBars = goldBars.toMutableList().apply {
            this[0] = this[0].copy(timeEpochSec = this[1].timeEpochSec)
        }
        val (isValidOverlap, overlapNotes) = SystemDiagnosticGuard.validateRateSeries(
            symbol = MarketSymbol.XAUUSD,
            timeframe = Timeframe.M15,
            bars = overlappingBars
        )
        assertTrue("Tumpang tindih timestamp wajib terdeteksi", !isValidOverlap)
        assertTrue(overlapNotes.any { it.contains("Urutan waktu terbalik/tumpang tindih") })
    }

    @Test
    fun gitHubReleaseUpdaterAndMetaApiGuard_validateInputsHonestAndSafe() = kotlinx.coroutines.runBlocking {
        val service = com.example.data.remote.LiveMarketAndNewsService()
        val ghEmpty = service.checkLatestGitHubRelease("YOUR_GITHUB_USER/YOUR_REPO_NAME")
        assertEquals(false, ghEmpty.isUpdateAvailable)
        assertTrue(ghEmpty.statusMessage.contains("username/nama-repo"))
        assertTrue(service.getInstalledReleaseTag().startsWith("v1."))
    }
}

