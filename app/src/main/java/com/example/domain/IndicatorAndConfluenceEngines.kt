package com.example.domain

import com.example.model.AnalyticalVerdict
import com.example.model.BacktestSummaryReport
import com.example.model.BrokerContractSpec
import com.example.model.CompleteHubAnalysis
import com.example.model.DataEngineReport
import com.example.model.GlobalMarketSession
import com.example.model.HtfAlignmentReport
import com.example.model.HtfAlignmentStatus
import com.example.model.InstrumentCalibrationProfile
import com.example.model.InstrumentCalibrationReport
import com.example.model.LotRiskCalculation
import com.example.model.MacdState
import com.example.model.MacroNewsArticle
import com.example.model.MacroSentiment
import com.example.model.MarketStructureClass
import com.example.model.MarketSymbol
import com.example.model.MomentumEngineReport
import com.example.model.Mt5RateBar
import com.example.model.MtfEngineReport
import com.example.model.NewsDataStatus
import com.example.model.NewsEngineReport
import com.example.model.NewsImpactLevel
import com.example.model.OnlineDataProvider
import com.example.model.PatternBias
import com.example.model.PlainLanguageBriefing
import com.example.model.RsiRegime
import com.example.model.SignalOutcomeStatus
import com.example.model.SmartMoneyReport
import com.example.model.SrInteractionType
import com.example.model.StructureBreakEvent
import com.example.model.Timeframe
import com.example.model.TimeframeMatrixRow
import com.example.model.TradingSessionReport
import com.example.model.TrendClassification
import com.example.model.TrendEngineReport
import com.example.model.VolatilityBreakoutPoint
import com.example.model.VolatilityBreakoutProbabilityReport
import com.example.model.VolatilityEngineReport
import com.example.model.VolatilityState
import com.example.model.VolumeClassification
import com.example.model.VolumeEngineReport
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

object IndicatorAndConfluenceEngines {

    /**
     * 5. MESIN TREN (EMA 20, EMA 50, EMA 200 pada Candle Tutup [1])
     * Perhitungan EMA diselaraskan dengan standar iMA(..., MODE_EMA, PRICE_CLOSE) di MetaTrader 5:
     * dihitung secara kronologis dari candle paling lama ke candle terbaru.
     */
    fun runTrendEngine(
        bars: List<Mt5RateBar>,
        symbol: MarketSymbol
    ): TrendEngineReport {
        val chronologicalCloses = bars.asReversed().map { it.close }
        val ema20Chron = computeEmaSeries(chronologicalCloses, 20)
        val ema50Chron = computeEmaSeries(chronologicalCloses, 50)
        val ema200Chron = computeEmaSeries(chronologicalCloses, 200)

        val ema20Series = ema20Chron.asReversed()
        val ema50Series = ema50Chron.asReversed()
        val ema200Series = ema200Chron.asReversed()

        val e20 = ema20Series.getOrElse(1) { ema20Series.first() }
        val e50 = ema50Series.getOrElse(1) { ema50Series.first() }
        val e200 = ema200Series.getOrElse(1) { ema200Series.first() }
        val close1 = bars.getOrElse(1) { bars.first() }.close

        // Kemiringan (slope) EMA20 selama 5 candle tutup terakhir (Candle [6] ke Candle [1])
        val e20Prev5 = ema20Series.getOrElse(6) { e20 }
        val ema20SlopeUp = e20 > e20Prev5
        val ema20SlopeDown = e20 < e20Prev5

        val isBullishStack = e20 > e50 && e50 > e200
        val isBearishStack = e20 < e50 && e50 < e200

        val classification = when {
            isBullishStack && close1 >= e20 -> TrendClassification.STRONG_BULLISH
            isBullishStack || (e20 > e50 && close1 >= e20 && ema20SlopeUp) || (close1 > e20 && e20 > e200) ->
                TrendClassification.BULLISH
            isBearishStack && close1 <= e20 -> TrendClassification.STRONG_BEARISH
            isBearishStack || (e20 < e50 && close1 <= e20 && ema20SlopeDown) || (close1 < e20 && e20 < e200) ->
                TrendClassification.BEARISH
            else -> TrendClassification.NEUTRAL
        }

        val distPts = (close1 - e200) / symbol.pointSize
        val summary = when {
            isBullishStack -> "EMA20 > EMA50 > EMA200 Selaras Naik (${classification.label})"
            isBearishStack -> "EMA20 < EMA50 < EMA200 Selaras Turun (${classification.label})"
            e20 > e50 && close1 >= e20 -> "EMA20 > EMA50 & Harga di Atas EMA20 (${classification.label})"
            e20 < e50 && close1 <= e20 -> "EMA20 < EMA50 & Harga di Bawah EMA20 (${classification.label})"
            else -> "Susunan EMA Sedang Transisi (${classification.label})"
        }

        return TrendEngineReport(
            ema20 = e20,
            ema50 = e50,
            ema200 = e200,
            ema20Series = ema20Series,
            ema50Series = ema50Series,
            ema200Series = ema200Series,
            classification = classification,
            isPerfectBullishStack = isBullishStack,
            isPerfectBearishStack = isBearishStack,
            priceToEma200DistancePoints = distPts,
            summary = summary
        )
    }

    /**
     * 6. MESIN MOMENTUM (RSI 14, MACD 12/26/9, ADX 14 pada Candle Tutup [1])
     */
    fun runMomentumEngine(bars: List<Mt5RateBar>): MomentumEngineReport {
        val chron = bars.asReversed()
        val closes = chron.map { it.close }

        val rsiChron = computeRsiSeries(closes, 14)
        val rsiSeries = rsiChron.asReversed()
        val rsi1 = rsiSeries.getOrElse(1) { 50.0 }
        val rsiRegime = when {
            rsi1 > 55.0 -> RsiRegime.BULLISH
            rsi1 < 45.0 -> RsiRegime.BEARISH
            else -> RsiRegime.NEUTRAL
        }

        val ema12 = computeEmaSeries(closes, 12)
        val ema26 = computeEmaSeries(closes, 26)
        val macdLineChron = ema12.indices.map { ema12[it] - ema26[it] }
        val signalLineChron = computeEmaSeries(macdLineChron, 9)
        val histChron = macdLineChron.indices.map { macdLineChron[it] - signalLineChron[it] }

        val macdMainSeries = macdLineChron.asReversed()
        val macdSigSeries = signalLineChron.asReversed()
        val histSeries = histChron.asReversed()

        val macd1 = macdMainSeries.getOrElse(1) { 0.0 }
        val sig1 = macdSigSeries.getOrElse(1) { 0.0 }
        val hist1 = histSeries.getOrElse(1) { 0.0 }
        val hist2 = histSeries.getOrElse(2) { 0.0 }

        val macdState = when {
            hist1 > 0.0 && hist2 <= 0.0 -> MacdState.BULLISH_CROSS
            hist1 < 0.0 && hist2 >= 0.0 -> MacdState.BEARISH_CROSS
            hist1 > 0.0 && hist1 >= hist2 -> MacdState.BULLISH_STRENGTHENING
            hist1 > 0.0 -> MacdState.BULLISH_WEAKENING
            hist1 < 0.0 && hist1 <= hist2 -> MacdState.BEARISH_STRENGTHENING
            else -> MacdState.BEARISH_WEAKENING
        }

        val (adx1, plusDi1, minusDi1) = computeAdx14AtClosedBar1(chron)
        val isAdxStrong = adx1 > 25.0

        val summary = "RSI(14): ${String.format(java.util.Locale.US, "%.1f", rsi1)} • " +
            "ADX(14): ${String.format(java.util.Locale.US, "%.1f", adx1)} (${if (isAdxStrong) "Tren Kuat >25" else "Lemah/Sideways <=25"}) • " +
            macdState.label

        return MomentumEngineReport(
            rsi14 = rsi1,
            rsiRegime = rsiRegime,
            adx14 = adx1,
            plusDi14 = plusDi1,
            minusDi14 = minusDi1,
            isTrendStrengthConfirmed = isAdxStrong,
            macdMain = macd1,
            macdSignal = sig1,
            macdHistogram = hist1,
            prevMacdHistogram = hist2,
            macdState = macdState,
            summary = summary
        )
    }

    /**
     * 7. MESIN VOLATILITAS (ATR 14 pada Candle Tutup [1])
     */
    fun runVolatilityEngine(
        bars: List<Mt5RateBar>,
        symbol: MarketSymbol
    ): VolatilityEngineReport {
        val chron = bars.asReversed()
        val trList = ArrayList<Double>(chron.size)
        for (i in chron.indices) {
            val cur = chron[i]
            val tr = if (i == 0) {
                cur.high - cur.low
            } else {
                val prevClose = chron[i - 1].close
                maxOf(
                    cur.high - cur.low,
                    abs(cur.high - prevClose),
                    abs(cur.low - prevClose)
                )
            }
            trList.add(tr)
        }
        val atrSeries = computeWilderRmaSeries(trList, 14).asReversed()
        val atr1 = atrSeries.getOrElse(1) { trList.lastOrNull() ?: symbol.pointSize * 50 }
        val avgAtr50 = atrSeries.drop(1).take(50).average().coerceAtLeast(1e-8)
        val ratio = atr1 / avgAtr50

        val close1 = bars.getOrElse(1) { bars.first() }.close
        val atrPct = (atr1 / close1.coerceAtLeast(1e-6)) * 100.0
        val atrPts = atr1 / symbol.pointSize

        val state = when {
            ratio < 0.78 -> VolatilityState.LOW
            ratio <= 1.28 -> VolatilityState.NORMAL
            ratio <= 1.68 -> VolatilityState.HIGH
            else -> VolatilityState.EXTREME
        }

        val stopDist = atr1 * 1.5
        val warning = when (state) {
            VolatilityState.EXTREME -> "PERINGATAN VOLATILITAS EKSTREM: ATR saat ini ${String.format(java.util.Locale.US, "%.2f", ratio)}x di atas rata-rata 50 candle. Perlebar jarak referensi batas risiko (Stop)."
            VolatilityState.HIGH -> "VOLATILITAS TINGGI: Rentang candle melebar. Tunggu konfirmasi penutupan candle di area Support/Resistance."
            VolatilityState.LOW -> "VOLATILITAS RENDAH (SEPI): Pasar sedang menyempit; hati-hati potensi ledakan arah baru."
            VolatilityState.NORMAL -> null
        }

        return VolatilityEngineReport(
            atr14 = atr1,
            atrPoints = atrPts,
            atrPercentageOfPrice = atrPct,
            state = state,
            analyticalStopDistance = stopDist,
            longStopReferencePrice = close1 - stopDist,
            shortStopReferencePrice = close1 + stopDist,
            longTarget1RPrice = close1 + stopDist * 1.5,
            longTarget2RPrice = close1 + stopDist * 2.5,
            shortTarget1RPrice = close1 - stopDist * 1.5,
            shortTarget2RPrice = close1 - stopDist * 2.5,
            warningMessage = warning
        )
    }

    /**
     * 9. MESIN VOLUME (Volume Candle Tutup [1] vs SMA Volume 20)
     */
    fun runVolumeEngine(bars: List<Mt5RateBar>): VolumeEngineReport {
        val bar0 = bars.first()
        val bar1 = bars.getOrElse(1) { bar0 }
        val closed20 = bars.drop(1).take(20)
        val sma20 = closed20.map { it.tickVolume }.average().coerceAtLeast(1.0)
        val ratio = bar1.tickVolume.toDouble() / sma20
        val isExpansion = bar1.tickVolume.toDouble() > sma20

        val classification = when {
            ratio < 0.75 -> VolumeClassification.LOW_VOLUME
            ratio <= 1.20 -> VolumeClassification.NORMAL_VOLUME
            ratio <= 1.45 -> VolumeClassification.HIGH_VOLUME
            else -> VolumeClassification.VOLUME_EXPANSION
        }

        val note = if (isExpansion) {
            "Konfluensi Volume Aktif: Volume candle tutup [1] (${bar1.tickVolume}) > Rata-rata SMA20 (${sma20.toInt()})"
        } else {
            "Volume Di Bawah Rata-rata: Volume candle tutup [1] (${bar1.tickVolume}) < SMA20 (${sma20.toInt()})"
        }

        return VolumeEngineReport(
            closedBar1Volume = bar1.tickVolume,
            formingBar0Volume = bar0.tickVolume,
            smaVolume20 = sma20,
            volumeToSmaRatio = ratio,
            classification = classification,
            isVolumeExpansion = isExpansion,
            confluenceNote = note
        )
    }

    /**
     * 10. MESIN MULTI-TIMEFRAME (Prioritas H1 -> M30 -> M15 -> M5 -> M1)
     */
    fun runMultiTimeframeEngine(
        symbol: MarketSymbol,
        ratesByTf: Map<Timeframe, List<Mt5RateBar>>
    ): MtfEngineReport {
        val rows = Timeframe.orderedByPriority.mapNotNull { tf ->
            val tfBars = ratesByTf[tf] ?: return@mapNotNull null
            val vol = runVolatilityEngine(tfBars, symbol)
            val trend = runTrendEngine(tfBars, symbol)
            val mom = runMomentumEngine(tfBars)
            val struct = CandleAndStructureEngines.runMarketStructureEngine(tfBars, vol.atr14)
            val candle = CandleAndStructureEngines.runCandleEngine(tfBars, symbol, vol.atr14)
            val sr = CandleAndStructureEngines.runSupportResistanceEngine(
                tfBars,
                struct.confirmedSwings,
                vol.atr14
            )

            val topPattern = candle.activeClosedPatterns.firstOrNull()?.pattern?.displayName
                ?: candle.recentHistoricalPatterns.firstOrNull { it.closedBarIndex <= 2 }?.let {
                    "${it.pattern.displayName} [${it.closedBarIndex}]"
                }
                ?: candle.anatomyType.label

            val rowScore = trend.classification.scoreWeight +
                (if (struct.classification == MarketStructureClass.BULLISH_STRUCTURE) 2 else if (struct.classification == MarketStructureClass.BEARISH_STRUCTURE) -2 else 0) +
                (if (mom.rsiRegime == RsiRegime.BULLISH) 1 else if (mom.rsiRegime == RsiRegime.BEARISH) -1 else 0)

            val rowBias = when {
                rowScore >= 2 -> PatternBias.BULLISH
                rowScore <= -2 -> PatternBias.BEARISH
                else -> PatternBias.NEUTRAL
            }

            TimeframeMatrixRow(
                timeframe = tf,
                trend = trend.classification,
                structure = struct.classification,
                structureEvent = struct.latestBreakEvent,
                rsi14 = mom.rsi14,
                adx14 = mom.adx14,
                closedPatternName = topPattern,
                srStatus = sr.closedBar1Interaction.label,
                bias = rowBias
            )
        }

        val htfRows = rows.filter { it.timeframe in listOf(Timeframe.H1, Timeframe.M30, Timeframe.M15) }
        val ltfRows = rows.filter { it.timeframe in listOf(Timeframe.M5, Timeframe.M1) }

        val availableTfs = rows.map { it.timeframe }
        val missingTfs = Timeframe.orderedByPriority.filter { tf ->
            val tfBars = ratesByTf[tf]
            tfBars == null || tfBars.size < 30
        }
        val hasH1 = availableTfs.contains(Timeframe.H1) && (ratesByTf[Timeframe.H1]?.size ?: 0) >= 30
        val hasM30 = availableTfs.contains(Timeframe.M30) && (ratesByTf[Timeframe.M30]?.size ?: 0) >= 30
        val hasM15 = availableTfs.contains(Timeframe.M15) && (ratesByTf[Timeframe.M15]?.size ?: 0) >= 30
        val isMtfDataComplete = hasH1 && hasM30 && hasM15

        val htfBullCount = htfRows.count { it.bias == PatternBias.BULLISH }
        val htfBearCount = htfRows.count { it.bias == PatternBias.BEARISH }

        val htfBias = when {
            !isMtfDataComplete -> PatternBias.NEUTRAL // Wajib H1, M30 & M15 lengkap untuk konfluensi HTF
            htfBullCount >= 2 -> PatternBias.BULLISH
            htfBearCount >= 2 -> PatternBias.BEARISH
            else -> PatternBias.NEUTRAL
        }

        val ltfBullCount = ltfRows.count { it.bias == PatternBias.BULLISH }
        val ltfBearCount = ltfRows.count { it.bias == PatternBias.BEARISH }

        val isPullback = isMtfDataComplete && (
            (htfBias == PatternBias.BULLISH && ltfBearCount >= 1 && ltfBullCount == 0) ||
                (htfBias == PatternBias.BEARISH && ltfBullCount >= 1 && ltfBearCount == 0)
            )

        val missingCoreLabels = listOf(Timeframe.H1, Timeframe.M30, Timeframe.M15)
            .filter { it in missingTfs }
            .joinToString(", ") { it.label }

        val ltfState = when {
            !isMtfDataComplete -> "Data MTF Belum Lengkap (Kurang: $missingCoreLabels)"
            htfBias == PatternBias.BULLISH && isPullback -> "TF Kecil (M5/M1) Sedang Koreksi (Pullback)"
            htfBias == PatternBias.BEARISH && isPullback -> "TF Kecil (M5/M1) Sedang Koreksi Naik"
            htfBias == PatternBias.BULLISH && ltfBullCount >= 1 -> "Searah Naik (Bullish) dengan TF Besar"
            htfBias == PatternBias.BEARISH && ltfBearCount >= 1 -> "Searah Turun (Bearish) dengan TF Besar"
            else -> "TF Kecil Campuran / Sideways"
        }

        val conclusion = when {
            !isMtfDataComplete ->
                "Data MTF belum lengkap (membutuhkan H1, M30, dan M15 yang valid; kurang: $missingCoreLabels). Konfluensi tren HTF ditahan (Netral) demi mencegah sinyal palsu."
            htfBias == PatternBias.BULLISH && isPullback ->
                "Timeframe Besar (H1/M30/M15) Bullish (Naik), sedangkan Timeframe Kecil (M5/M1) sedang koreksi (Pullback). JANGAN langsung menyebut pembalikan arah (Reversal); tunggu konfirmasi candle tutup Higher Low (HL) di area Support."
            htfBias == PatternBias.BEARISH && isPullback ->
                "Timeframe Besar (H1/M30/M15) Bearish (Turun), sedangkan Timeframe Kecil (M5/M1) sedang koreksi naik (Pullback). JANGAN langsung menyebut Reversal; tunggu konfirmasi candle tutup Lower High (LH) di area Resistance."
            htfBias == PatternBias.BULLISH ->
                "Konfluensi Naik (Bullish) Selaras pada Timeframe Besar (H1 -> M30 -> M15) hingga Timeframe Kecil."
            htfBias == PatternBias.BEARISH ->
                "Konfluensi Turun (Bearish) Selaras pada Timeframe Besar (H1 -> M30 -> M15) hingga Timeframe Kecil."
            else ->
                "Struktur Multi-Timeframe sedang konsolidasi/transisi. Tunggu penembusan struktur yang sah di H1/M30."
        }

        return MtfEngineReport(
            rows = rows,
            htfBias = htfBias,
            ltfState = ltfState,
            isPullbackInHtfTrend = isPullback,
            executiveConclusion = conclusion,
            availableTimeframes = availableTfs,
            missingTimeframes = missingTfs,
            isMtfDataComplete = isMtfDataComplete
        )
    }

    fun buildCompleteAnalysis(
        symbol: MarketSymbol,
        brokerSuffix: String,
        activeTimeframe: Timeframe,
        activeBars: List<Mt5RateBar>,
        allTimeframeBars: Map<Timeframe, List<Mt5RateBar>>,
        isLiveFeed: Boolean,
        feedSourceName: String,
        newsArticles: List<MacroNewsArticle>,
        isLiveRss: Boolean,
        newsStatus: NewsDataStatus = if (isLiveRss) NewsDataStatus.LIVE_FRESH else if (newsArticles.isNotEmpty()) NewsDataStatus.CACHE_STALE else NewsDataStatus.OFFLINE_EMPTY,
        newsStatusDetail: String = "",
        serverFetchTimeMs: Long = System.currentTimeMillis(),
        currentTimeMs: Long = System.currentTimeMillis(),
        brokerContractSpec: BrokerContractSpec? = null
    ): CompleteHubAnalysis {
        val bar0 = activeBars.first()
        val bar1 = activeBars.getOrElse(1) { bar0 }
        val bar2 = activeBars.getOrElse(2) { bar1 }

        val dataReport = DataEngineReport(
            symbolWithSuffix = symbol.withSuffix(brokerSuffix),
            timeframe = activeTimeframe,
            totalBarsLoaded = activeBars.size,
            formingCandle0 = bar0,
            closedCandle1 = bar1,
            previousClosedCandle2 = bar2,
            isLiveFeedConnected = isLiveFeed,
            feedSourceLabel = feedSourceName,
            lastSyncTimeMs = serverFetchTimeMs,
            serverDataTimeMs = serverFetchTimeMs,
            calculatedAtDeviceTimeMs = currentTimeMs
        )

        val volatilityReport = runVolatilityEngine(activeBars, symbol)
        val candleReport = CandleAndStructureEngines.runCandleEngine(activeBars, symbol, volatilityReport.atr14)
        val structureReport = CandleAndStructureEngines.runMarketStructureEngine(activeBars, volatilityReport.atr14)
        val trendReport = runTrendEngine(activeBars, symbol)
        val momentumReport = runMomentumEngine(activeBars)
        val srReport = CandleAndStructureEngines.runSupportResistanceEngine(
            activeBars,
            structureReport.confirmedSwings,
            volatilityReport.atr14
        )
        val volumeReport = runVolumeEngine(activeBars)
        val mtfReport = runMultiTimeframeEngine(symbol, allTimeframeBars)

        val filteredNews = newsArticles.filter { symbol in it.affectedSymbols }
        val highImpactNews = filteredNews.count { it.impact == NewsImpactLevel.HIGH }
        val dominantSentiment = filteredNews.firstOrNull()?.sentiment ?: MacroSentiment.NEUTRAL_WATCH
        val newsWarning = if (highImpactNews >= 2) {
            "PERINGATAN BERITA DAMPAK TINGGI ($highImpactNews agenda ${symbol.code}): Waspadai volatilitas FOMC/Suku Bunga/CPI & pelebaran spread. Gunakan hanya sinyal candle yang sudah TUTUP [1]."
        } else null

        val newsReport = NewsEngineReport(
            articles = filteredNews,
            highImpactCount = highImpactNews,
            dominantSentiment = dominantSentiment,
            newsRiskWarning = newsWarning,
            lastUpdatedMs = currentTimeMs,
            isLiveRssSynced = newsStatus == NewsDataStatus.LIVE_FRESH,
            status = newsStatus,
            statusMessage = newsStatusDetail
        )

        // 12. SMART MONEY CONCEPTS (FVG & ORDER BLOCKS)
        val smartMoneyReport = CandleAndStructureEngines.runSmartMoneyEngine(activeBars, volatilityReport.atr14)

        // 13. GLOBAL TRADING SESSION ENGINE (Sesi Saat Ini vs Sesi Candle Tutup [1])
        val sessionReport = runSessionEngine(candleEpochSec = bar1.timeEpochSec, nowMs = currentTimeMs)

        // 14. HTF TREND ALIGNMENT GUARD
        val htfAlignmentReport = runHtfAlignmentGuard(activeTimeframe, allTimeframeBars, symbol)

        // 15. HISTORICAL VOLATILITY & BREAKOUT PROBABILITY ZONE
        val suppPrice = srReport.nearestSupport?.centerPrice ?: (bar1.close - volatilityReport.atr14 * 1.5)
        val resPrice = srReport.nearestResistance?.centerPrice ?: (bar1.close + volatilityReport.atr14 * 1.5)
        val breakoutProbReport = computeBreakoutProbability(
            bars = activeBars,
            atr14 = volatilityReport.atr14,
            nearestSupport = suppPrice,
            nearestResistance = resPrice
        )

        var bullPoints = 0
        var bearPoints = 0

        // 1. Bobot Tren EMA (24 poin)
        when (trendReport.classification) {
            TrendClassification.STRONG_BULLISH -> bullPoints += 24
            TrendClassification.BULLISH -> bullPoints += 17
            TrendClassification.STRONG_BEARISH -> bearPoints += 24
            TrendClassification.BEARISH -> bearPoints += 17
            TrendClassification.NEUTRAL -> {}
        }

        // 2. Bobot Struktur Pasar HH/HL vs LH/LL & BOS/CHOCH (24 poin)
        if (structureReport.classification == MarketStructureClass.BULLISH_STRUCTURE) bullPoints += 17
        if (structureReport.classification == MarketStructureClass.BEARISH_STRUCTURE) bearPoints += 17
        if (structureReport.latestBreakEvent == StructureBreakEvent.BOS_BULLISH ||
            structureReport.latestBreakEvent == StructureBreakEvent.CHOCH_BULLISH
        ) bullPoints += 7
        if (structureReport.latestBreakEvent == StructureBreakEvent.BOS_BEARISH ||
            structureReport.latestBreakEvent == StructureBreakEvent.CHOCH_BEARISH
        ) bearPoints += 7

        // 3. Bobot Pola Candlestick Tutup [1] (16 poin)
        val hasBullPattern = candleReport.activeClosedPatterns.any { it.pattern.defaultBias == PatternBias.BULLISH }
        val hasBearPattern = candleReport.activeClosedPatterns.any { it.pattern.defaultBias == PatternBias.BEARISH }
        if (hasBullPattern) bullPoints += 16 else if (bar1.isBullish) bullPoints += 5
        if (hasBearPattern) bearPoints += 16 else if (bar1.isBearish) bearPoints += 5

        // 4. Bobot Momentum RSI, MACD, ADX (18 poin)
        if (momentumReport.rsiRegime == RsiRegime.BULLISH) bullPoints += 7
        if (momentumReport.rsiRegime == RsiRegime.BEARISH) bearPoints += 7
        if (momentumReport.macdState in listOf(MacdState.BULLISH_CROSS, MacdState.BULLISH_STRENGTHENING)) bullPoints += 6
        if (momentumReport.macdState in listOf(MacdState.BEARISH_CROSS, MacdState.BEARISH_STRENGTHENING)) bearPoints += 6
        if (momentumReport.isTrendStrengthConfirmed) {
            if (momentumReport.plusDi14 >= momentumReport.minusDi14) bullPoints += 5 else bearPoints += 5
        }

        // 5. Bobot Support & Resistance (8 poin)
        if (srReport.closedBar1Interaction in listOf(SrInteractionType.BULLISH_REJECTION, SrInteractionType.BULLISH_BREAKOUT)) {
            bullPoints += 8
        }
        if (srReport.closedBar1Interaction in listOf(SrInteractionType.BEARISH_REJECTION, SrInteractionType.BEARISH_BREAKOUT)) {
            bearPoints += 8
        }

        // 6. Bobot Volume (4 poin)
        if (volumeReport.isVolumeExpansion) {
            if (bar1.isBullish) bullPoints += 4 else if (bar1.isBearish) bearPoints += 4
        }

        // 7. Bobot Multi-Timeframe (6 poin, hanya jika H1/M30/M15 lengkap)
        if (mtfReport.isMtfDataComplete) {
            if (mtfReport.htfBias == PatternBias.BULLISH) bullPoints += 6
            if (mtfReport.htfBias == PatternBias.BEARISH) bearPoints += 6
        }

        // 8. Bobot Smart Money FVG & Order Block (6 poin)
        if (smartMoneyReport.closedBar1InFvg) {
            if (bar1.isBullish || smartMoneyReport.nearestBullishFvg != null) bullPoints += 6
            if (bar1.isBearish || smartMoneyReport.nearestBearishFvg != null) bearPoints += 6
        }

        // 9. Bobot Keselarasan HTF Trend Alignment Guard (5 poin, memakai enum, bukan string)
        if (htfAlignmentReport.status == HtfAlignmentStatus.PERFECT_ALIGNMENT) {
            when (htfAlignmentReport.htfTrendClassification) {
                TrendClassification.STRONG_BULLISH, TrendClassification.BULLISH -> bullPoints += 5
                TrendClassification.STRONG_BEARISH, TrendClassification.BEARISH -> bearPoints += 5
                TrendClassification.NEUTRAL -> {}
            }
        }

        val finalBullScore = bullPoints.coerceIn(0, 100)
        val finalBearScore = bearPoints.coerceIn(0, 100)

        val calibrationReport = buildInstrumentCalibrationReport(
            bars = activeBars,
            symbol = symbol,
            momentumReport = momentumReport,
            volatilityReport = volatilityReport,
            sessionReport = sessionReport,
            highImpactNewsCount = highImpactNews
        )

        val rawPreGateVerdict = when {
            finalBullScore >= 65 && finalBullScore > finalBearScore + 15 ->
                AnalyticalVerdict.STRONG_BULLISH_CONFLUENCE
            finalBearScore >= 65 && finalBearScore > finalBullScore + 15 ->
                AnalyticalVerdict.STRONG_BEARISH_CONFLUENCE
            finalBullScore >= 46 && finalBullScore > finalBearScore + 8 ->
                AnalyticalVerdict.MODERATE_BULLISH_BIAS
            finalBearScore >= 46 && finalBearScore > finalBullScore + 8 ->
                AnalyticalVerdict.MODERATE_BEARISH_BIAS
            else -> AnalyticalVerdict.NEUTRAL_CONSOLIDATION
        }

        // Filter Anti-Choppy & Spread: menahan bias moderat ketika pasar benar-benar choppy & ADX di bawah ambang instrumen
        val activeTfRawVerdict = if (
            calibrationReport.isChoppyMarketDetected &&
            momentumReport.adx14 < calibrationReport.profile.adxTrendThreshold &&
            rawPreGateVerdict in listOf(
                AnalyticalVerdict.MODERATE_BULLISH_BIAS,
                AnalyticalVerdict.MODERATE_BEARISH_BIAS
            )
        ) {
            AnalyticalVerdict.NEUTRAL_CONSOLIDATION
        } else {
            rawPreGateVerdict
        }

        val verdict = when {
            mtfReport.isMtfDataComplete && mtfReport.isPullbackInHtfTrend &&
                mtfReport.htfBias == PatternBias.BULLISH &&
                activeTimeframe in listOf(Timeframe.M1, Timeframe.M5) ->
                AnalyticalVerdict.HTF_BULLISH_LTF_PULLBACK
            mtfReport.isMtfDataComplete && mtfReport.isPullbackInHtfTrend &&
                mtfReport.htfBias == PatternBias.BEARISH &&
                activeTimeframe in listOf(Timeframe.M1, Timeframe.M5) ->
                AnalyticalVerdict.HTF_BEARISH_LTF_PULLBACK
            else -> activeTfRawVerdict
        }

        val mtfContextSummary = when {
            !mtfReport.isMtfDataComplete ->
                "Verdict ${activeTimeframe.label}: ${activeTfRawVerdict.label} • Konteks MTF belum lengkap (${mtfReport.ltfState})."
            mtfReport.isPullbackInHtfTrend && activeTimeframe !in listOf(Timeframe.M1, Timeframe.M5) ->
                "Verdict ${activeTimeframe.label}: ${verdict.label} • Catatan MTF: TF besar (${mtfReport.htfBias.label}) & TF kecil M5/M1 sedang koreksi (pullback)."
            else ->
                mtfReport.executiveConclusion
        }

        val checklist = listOf(
            "Candle Tutup [1] Terkunci (Bebas Look-Ahead / Non-Repaint)" to true,
            "Susunan Tren EMA 20/50/200 Selaras (${trendReport.classification.label})" to
                (trendReport.isPerfectBullishStack || trendReport.isPerfectBearishStack),
            "Struktur Pasar Terkonfirmasi (${structureReport.classification.label})" to
                (structureReport.classification != MarketStructureClass.RANGE),
            "Pola Candlestick di Candle Tutup [1] (${candleReport.activeClosedPatterns.firstOrNull()?.pattern?.displayName ?: "Tidak Ada"})" to
                candleReport.activeClosedPatterns.isNotEmpty(),
            "Smart Money FVG & Order Block (${smartMoneyReport.activeFvgs.size} gap aktif)" to
                (smartMoneyReport.activeFvgs.isNotEmpty() || smartMoneyReport.activeOrderBlocks.isNotEmpty()),
            "Sesi Pasar Aktif: ${sessionReport.currentSession.sessionName} (${sessionReport.currentSession.liquidityRating})" to
                sessionReport.isHighLiquidityWindow,
            "Keselarasan HTF H1 (${htfAlignmentReport.status.label})" to
                htfAlignmentReport.status.isAligned,
            "Kekuatan Tren ADX(14) > 25 (${String.format(Locale.US, "%.1f", momentumReport.adx14)})" to
                momentumReport.isTrendStrengthConfirmed,
            "Ekspansi Volume > SMA 20 (${String.format(Locale.US, "%.2f", volumeReport.volumeToSmaRatio)}x)" to
                volumeReport.isVolumeExpansion,
            "Konfluensi Multi-Timeframe H1/M30/M15 (${if (mtfReport.isMtfDataComplete) mtfReport.htfBias.label else "Belum Lengkap"})" to
                (mtfReport.isMtfDataComplete && mtfReport.htfBias != PatternBias.NEUTRAL)
        )

        val backtestSummary = runHistoricalBacktest(
            bars = activeBars,
            symbol = symbol,
            calibrationProfile = calibrationReport.profile
        )

        val plainBriefing = buildPlainLanguageBriefing(
            symbol = symbol,
            timeframe = activeTimeframe,
            verdict = verdict,
            trend = trendReport,
            momentum = momentumReport,
            mtf = mtfReport,
            news = newsReport,
            session = sessionReport,
            calibration = calibrationReport
        )

        return CompleteHubAnalysis(
            symbol = symbol,
            brokerSuffix = brokerSuffix,
            activeTimeframe = activeTimeframe,
            bars = activeBars,
            dataEngine = dataReport,
            candleEngine = candleReport,
            structureEngine = structureReport,
            trendEngine = trendReport,
            momentumEngine = momentumReport,
            volatilityEngine = volatilityReport,
            srEngine = srReport,
            volumeEngine = volumeReport,
            mtfEngine = mtfReport,
            newsEngine = newsReport,
            smartMoneyEngine = smartMoneyReport,
            sessionEngine = sessionReport,
            htfAlignmentGuard = htfAlignmentReport,
            breakoutProbability = breakoutProbReport,
            verdict = verdict,
            bullishConfluenceScore = finalBullScore,
            bearishConfluenceScore = finalBearScore,
            confluenceChecklist = checklist,
            activeTimeframeVerdict = activeTfRawVerdict,
            mtfContextSummary = mtfContextSummary,
            brokerContractSpec = brokerContractSpec,
            calibrationReport = calibrationReport,
            backtestSummary = backtestSummary,
            plainBriefing = plainBriefing
        )
    }

    /**
     * 13. MESIN SESI PERDAGANGAN GLOBAL (GLOBAL TRADING SESSION ENGINE)
     * Memisahkan Sesi Pasar SAAT INI (nowMs) dengan Sesi saat Candle [1] tutup (candleEpochSec).
     */
    fun runSessionEngine(
        candleEpochSec: Long,
        nowMs: Long = System.currentTimeMillis()
    ): TradingSessionReport {
        val currentEpochSec = (nowMs / 1000L).coerceAtLeast(1L)
        val safeCandleEpoch = if (candleEpochSec > 0L) candleEpochSec else currentEpochSec

        val currentUtcHour = (((currentEpochSec % 86400L) + 86400L) % 86400L / 3600L).toInt()
        val candleUtcHour = (((safeCandleEpoch % 86400L) + 86400L) % 86400L / 3600L).toInt()

        val currentSession = classifyUtcHourSession(currentUtcHour)
        val candleSession = classifyUtcHourSession(candleUtcHour)
        val sameSession = currentSession == candleSession

        val isOverlap = currentSession == GlobalMarketSession.LONDON_NEW_YORK_OVERLAP
        val isHighLiquidity = currentSession == GlobalMarketSession.LONDON_NEW_YORK_OVERLAP ||
            currentSession == GlobalMarketSession.NEW_YORK ||
            currentSession == GlobalMarketSession.LONDON

        val baseAdvice = when (currentSession) {
            GlobalMarketSession.LONDON_NEW_YORK_OVERLAP ->
                "Sesi Emas London-NY Overlap: Likuiditas tertinggi hari ini. Ideal untuk memvalidasi sinyal tren Candle [1]."
            GlobalMarketSession.NEW_YORK ->
                "Sesi New York: Momentum tinggi pasca rilis data AS. Perhatikan reaksi harga pada level S1/R1."
            GlobalMarketSession.LONDON ->
                "Sesi London: Pembukaan pasar Eropa. Volume meningkat mengikuti arah tren akumulasi."
            GlobalMarketSession.ASIA_PACIFIC ->
                "Sesi Asia: Rentang harga cenderung sempit (akumulasi). Waspadai sapuan likuiditas sebelum sesi London."
            GlobalMarketSession.PACIFIC_OFFPEAK ->
                "Sesi Off-Peak Pasifik: Volume rendah dan spread berpotensi melebar. Disarankan wait and see."
        }
        val advice = if (sameSession) {
            baseAdvice
        } else {
            "$baseAdvice (Catatan: Candle tutup [1] terbentuk di ${candleSession.sessionName}, sedangkan sesi saat ini ${currentSession.sessionName})."
        }

        return TradingSessionReport(
            activeSession = currentSession,
            utcHour = currentUtcHour,
            isLondonNyOverlap = isOverlap,
            isHighLiquidityWindow = isHighLiquidity,
            sessionAdvice = advice,
            currentSession = currentSession,
            currentUtcHour = currentUtcHour,
            currentTimeMs = nowMs,
            candleSession = candleSession,
            candleUtcHour = candleUtcHour,
            candleTimeEpochSec = safeCandleEpoch,
            isCandleFromCurrentSession = sameSession
        )
    }

    private fun classifyUtcHourSession(utcHour: Int): GlobalMarketSession {
        return when (utcHour) {
            in 12..15 -> GlobalMarketSession.LONDON_NEW_YORK_OVERLAP
            in 16..20 -> GlobalMarketSession.NEW_YORK
            in 7..11 -> GlobalMarketSession.LONDON
            in 0..6 -> GlobalMarketSession.ASIA_PACIFIC
            else -> GlobalMarketSession.PACIFIC_OFFPEAK
        }
    }

    /**
     * 14. PENGAWAL KESELARASAN TREN HTF (HTF TREND ALIGNMENT GUARD)
     * Membandingkan arah tren timeframe aktif terhadap timeframe utama H1/M30.
     */
    fun runHtfAlignmentGuard(
        activeTimeframe: Timeframe,
        allTimeframeBars: Map<Timeframe, List<Mt5RateBar>>,
        symbol: MarketSymbol
    ): HtfAlignmentReport {
        val htfBars = allTimeframeBars[Timeframe.H1] ?: allTimeframeBars[Timeframe.M30]
        val activeBars = allTimeframeBars[activeTimeframe]

        if (htfBars == null || htfBars.size < 30 || activeBars == null || activeBars.size < 30) {
            return HtfAlignmentReport(
                status = HtfAlignmentStatus.HTF_RANGE_NEUTRAL,
                htfTrendLabel = "Tren H1 Netral",
                ltfTrendLabel = "Timeframe Aktif (${activeTimeframe.label})",
                advice = "Data timeframe tinggi sedang disinkronkan.",
                riskModifierNote = "Standar 1 : 1.5"
            )
        }

        val htfTrend = runTrendEngine(htfBars, symbol)
        val ltfTrend = runTrendEngine(activeBars, symbol)

        val isHtfBull = htfTrend.classification in listOf(TrendClassification.STRONG_BULLISH, TrendClassification.BULLISH)
        val isHtfBear = htfTrend.classification in listOf(TrendClassification.STRONG_BEARISH, TrendClassification.BEARISH)
        val isLtfBull = ltfTrend.classification in listOf(TrendClassification.STRONG_BULLISH, TrendClassification.BULLISH)
        val isLtfBear = ltfTrend.classification in listOf(TrendClassification.STRONG_BEARISH, TrendClassification.BEARISH)

        val status = when {
            (isHtfBull && isLtfBull) || (isHtfBear && isLtfBear) -> HtfAlignmentStatus.PERFECT_ALIGNMENT
            (isHtfBull && isLtfBear) || (isHtfBear && isLtfBull) -> HtfAlignmentStatus.COUNTER_TREND_WARNING
            else -> HtfAlignmentStatus.HTF_RANGE_NEUTRAL
        }

        val advice = when (status) {
            HtfAlignmentStatus.PERFECT_ALIGNMENT ->
                "Sinyal ${activeTimeframe.label} SEARAH dengan tren besar H1 (${htfTrend.classification.label}). Probabilitas tinggi: Target profit 1.5R - 2.0R direkomendasikan."
            HtfAlignmentStatus.COUNTER_TREND_WARNING ->
                "PERINGATAN: Sinyal ${activeTimeframe.label} MELAWAN tren utama H1 (${htfTrend.classification.label}). Ini adalah transaksi Counter-Trend: Pasang TP lebih konservatif (1.0R) dan segera geser SL ke BEP (impas)."
            HtfAlignmentStatus.HTF_RANGE_NEUTRAL ->
                "Tren besar H1 saat ini berkonsolidasi. Jalankan strategi swing normal dengan batas SL/TP proporsional."
        }

        val riskNote = when (status) {
            HtfAlignmentStatus.PERFECT_ALIGNMENT -> "Rasio RR Penuh 1 : 1.5 – 1 : 2.0 (High Win-Rate)"
            HtfAlignmentStatus.COUNTER_TREND_WARNING -> "Konservatif: Target 1.0R (Cepat Amankan Profit)"
            HtfAlignmentStatus.HTF_RANGE_NEUTRAL -> "Rasio Standar 1 : 1.5"
        }

        return HtfAlignmentReport(
            status = status,
            htfTrendLabel = htfTrend.classification.label,
            ltfTrendLabel = ltfTrend.classification.label,
            advice = advice,
            riskModifierNote = riskNote,
            htfTrendClassification = htfTrend.classification,
            ltfTrendClassification = ltfTrend.classification
        )
    }

    /**
     * Menghitung Position Sizing & Risiko Akun:
     * - Broker-native (Elev8 MT5) dengan spesifikasi kontrak broker terverifikasi -> Lot MT5 akurat.
     * - Broker-native tanpa spesifikasi kontrak -> Fallback asumsi standar akun USD dengan disclaimer eksplisit.
     * - Proxy feed (Binance/Yahoo/Kraken/Bitfinex/OKX/KuCoin) -> Position size indikatif (BUKAN lot MT5).
     */
    fun calculatePositionRisk(
        symbol: MarketSymbol,
        provider: OnlineDataProvider,
        balanceDollars: Double,
        riskPercentage: Double,
        entryPrice: Double,
        stopLossPrice: Double,
        target1Price: Double,
        target2Price: Double,
        brokerSpec: BrokerContractSpec? = null
    ): LotRiskCalculation {
        val safeBalance = balanceDollars.coerceAtLeast(0.0)
        val safeRiskPct = riskPercentage.coerceIn(0.1, 100.0)
        val targetRiskDollars = safeBalance * (safeRiskPct / 100.0)
        val slDistPrice = abs(entryPrice - stopLossPrice).coerceAtLeast(symbol.pointSize)
        val tp1Ratio = (abs(target1Price - entryPrice) / slDistPrice).coerceAtLeast(0.0)
        val tp2Ratio = (abs(target2Price - entryPrice) / slDistPrice).coerceAtLeast(0.0)

        if (!provider.isBrokerNativeFeed) {
            val indicativeUnits = (targetRiskDollars / slDistPrice).coerceAtLeast(0.0)
            val actualRisk = indicativeUnits * slDistPrice
            return LotRiskCalculation(
                calculatedSize = indicativeUnits,
                formattedSize = "${String.format(Locale.US, "%.2f", indicativeUnits)} unit",
                isMt5VerifiedLot = false,
                isProxyIndicativeOnly = true,
                riskAmountDollars = targetRiskDollars,
                actualSlLossDollars = actualRisk,
                estimatedProfitTp1 = actualRisk * tp1Ratio,
                estimatedProfitTp2 = actualRisk * tp2Ratio,
                assumptionNote = "Position size indikatif (${provider.shortLabel}) — BUKAN lot MT5. Feed proxy spot/futures berbeda kuotasi & ukuran kontrak dengan broker MT5."
            )
        }

        if (brokerSpec != null && brokerSpec.isBrokerVerified && brokerSpec.tickSize > 0.0 && brokerSpec.tickValue > 0.0) {
            val lossPerLot = ((slDistPrice / brokerSpec.tickSize) * brokerSpec.tickValue).coerceAtLeast(0.01)
            val step = brokerSpec.volumeStep.coerceAtLeast(0.01)
            val rawLots = targetRiskDollars / lossPerLot
            val steppedLots = (floor(rawLots / step) * step)
                .coerceIn(brokerSpec.minVolume, brokerSpec.maxVolume)
            val actualLoss = steppedLots * lossPerLot
            return LotRiskCalculation(
                calculatedSize = steppedLots,
                formattedSize = String.format(Locale.US, "%.2f Lot", steppedLots),
                isMt5VerifiedLot = true,
                isProxyIndicativeOnly = false,
                riskAmountDollars = targetRiskDollars,
                actualSlLossDollars = actualLoss,
                estimatedProfitTp1 = actualLoss * tp1Ratio,
                estimatedProfitTp2 = actualLoss * tp2Ratio,
                assumptionNote = "Spesifikasi broker ${brokerSpec.brokerSymbol} (${brokerSpec.accountCurrency}): ContractSize=${brokerSpec.contractSize}, TickSize=${brokerSpec.tickSize}, TickValue=${brokerSpec.tickValue}."
            )
        }

        // Fallback broker-native standar akun USD (misal XAUUSD 100 oz -> $100 per $1.00 per 1 lot; EURUSD 100k -> $100,000 per 1.0000 per 1 lot)
        val contractUnitsPerLot = if (symbol == MarketSymbol.XAUUSD) 100.0 else 100_000.0
        val lossPerLotStandard = (slDistPrice * contractUnitsPerLot).coerceAtLeast(0.01)
        val rawLots = targetRiskDollars / lossPerLotStandard
        val steppedLots = (floor(rawLots / 0.01) * 0.01).coerceIn(0.01, 100.0)
        val actualLoss = steppedLots * lossPerLotStandard

        return LotRiskCalculation(
            calculatedSize = steppedLots,
            formattedSize = String.format(Locale.US, "%.2f Lot", steppedLots),
            isMt5VerifiedLot = false,
            isProxyIndicativeOnly = false,
            riskAmountDollars = targetRiskDollars,
            actualSlLossDollars = actualLoss,
            estimatedProfitTp1 = actualLoss * tp1Ratio,
            estimatedProfitTp2 = actualLoss * tp2Ratio,
            assumptionNote = "Estimasi asumsi standar akun USD (ContractSize=${contractUnitsPerLot.toInt()}). Spesifikasi kontrak broker belum terverifikasi — cek Contract Size & akun mata uang di MT5 sebelum order."
        )
    }

    /**
     * 15. MESIN PROBABILITAS BREAKOUT VOLATILITAS HISTORIS
     * Memetakan volatilitas historis (ATR & standard deviation envelope) terhadap level Support/Resistance
     * untuk menghasilkan 'Probability Zone' (Peluang Breakout Naik, Breakdown Turun, atau Range-Bound).
     */
    fun computeBreakoutProbability(
        bars: List<Mt5RateBar>,
        atr14: Double,
        nearestSupport: Double,
        nearestResistance: Double
    ): VolatilityBreakoutProbabilityReport {
        val bar1 = bars.getOrNull(1) ?: bars.firstOrNull() ?: Mt5RateBar(
            barIndex = 0, timeEpochSec = 0, open = 2600.0, high = 2605.0, low = 2595.0, close = 2600.0,
            tickVolume = 100, realVolume = 1000, spread = 20
        )
        val currentPrice = bar1.close
        val safeAtr = atr14.coerceAtLeast(0.01)

        val distToRes = (nearestResistance - currentPrice).coerceAtLeast(0.001)
        val distToSupp = (currentPrice - nearestSupport).coerceAtLeast(0.001)

        val distToResAtr = distToRes / safeAtr
        val distToSuppAtr = distToSupp / safeAtr

        // Titik volatilitas historis 25 candle terakhir
        val pointCount = 25.coerceAtMost(bars.size)
        val recentPoints = (0 until pointCount).map { i ->
            val b = bars[i]
            val envelope = safeAtr * 1.25
            VolatilityBreakoutPoint(
                barIndex = b.barIndex,
                closePrice = b.close,
                upperVolBand = b.close + envelope,
                lowerVolBand = b.close - envelope,
                historicalVolatility = envelope
            )
        }.reversed()

        // Bobot probabilitas breakout berbasis jarak ATR dan posisi harga
        val bullRaw = (48.0 / (distToResAtr + 0.65)).coerceIn(10.0, 75.0)
        val bearRaw = (48.0 / (distToSuppAtr + 0.65)).coerceIn(10.0, 75.0)
        val rangeRaw = (50.0 * (distToResAtr + distToSuppAtr) / 2.0).coerceIn(20.0, 60.0)

        val total = bullRaw + bearRaw + rangeRaw
        val bullPct = ((bullRaw / total) * 100).toInt().coerceIn(5, 80)
        val bearPct = ((bearRaw / total) * 100).toInt().coerceIn(5, 80)
        val rangePct = (100 - bullPct - bearPct).coerceAtLeast(10)

        val riskLabel = when {
            bullPct >= 45 -> "Tinggi: Uji Breakout Resistance ($nearestResistance)"
            bearPct >= 45 -> "Tinggi: Uji Breakdown Support ($nearestSupport)"
            distToResAtr < 0.7 -> "Sedang: Harga Mendekati Resistance R1"
            distToSuppAtr < 0.7 -> "Sedang: Harga Mendekati Support S1"
            else -> "Rendah: Harga di Tengah Zona Konsolidasi"
        }

        return VolatilityBreakoutProbabilityReport(
            bullishBreakoutProbPct = bullPct,
            bearishBreakdownProbPct = bearPct,
            rangeBoundHoldProbPct = rangePct,
            resistancePrice = nearestResistance,
            supportPrice = nearestSupport,
            currentPrice = currentPrice,
            historicalAtr = safeAtr,
            distanceToResistanceAtr = distToResAtr,
            distanceToSupportAtr = distToSuppAtr,
            primaryBreakoutRisk = riskLabel,
            recentVolatilityPoints = recentPoints
        )
    }

    /**
     * Menghitung deret EMA sesuai standar MetaTrader 5 (iMA MODE_EMA):
     * Diinisialisasi dengan rata-rata SMA pada 'period' candle pertama, lalu dihaluskan secara eksponensial.
     */
    private fun computeEmaSeries(values: List<Double>, period: Int): List<Double> {
        if (values.isEmpty()) return emptyList()
        val k = 2.0 / (period + 1.0)
        val out = ArrayList<Double>(values.size)
        val seedCount = period.coerceAtMost(values.size)
        val initialSma = values.take(seedCount).average()

        var runningSum = 0.0
        var ema = initialSma
        for (i in values.indices) {
            val v = values[i]
            if (i < seedCount - 1) {
                runningSum += v
                out.add(runningSum / (i + 1))
            } else if (i == seedCount - 1) {
                ema = initialSma
                out.add(ema)
            } else {
                ema = (v - ema) * k + ema
                out.add(ema)
            }
        }
        return out
    }

    /**
     * Menghitung Wilder's Smoothing (RMA) sesuai standar MetaTrader 5 untuk RSI, ATR, dan ADX:
     * Diinisialisasi dengan SMA dari 'period' candle pertama.
     */
    private fun computeWilderRmaSeries(values: List<Double>, period: Int): List<Double> {
        if (values.isEmpty()) return emptyList()
        val out = ArrayList<Double>(values.size)
        val seedCount = period.coerceAtMost(values.size)
        val initialSma = values.take(seedCount).average()

        var runningSum = 0.0
        var rma = initialSma
        for (i in values.indices) {
            val v = values[i]
            if (i < seedCount - 1) {
                runningSum += v
                out.add(runningSum / (i + 1))
            } else if (i == seedCount - 1) {
                rma = initialSma
                out.add(rma)
            } else {
                rma = (rma * (period - 1) + v) / period.toDouble()
                out.add(rma)
            }
        }
        return out
    }

    private fun computeRsiSeries(closes: List<Double>, period: Int): List<Double> {
        if (closes.size < 2) return List(closes.size) { 50.0 }
        val gains = ArrayList<Double>(closes.size)
        val losses = ArrayList<Double>(closes.size)
        gains.add(0.0)
        losses.add(0.0)
        for (i in 1 until closes.size) {
            val diff = closes[i] - closes[i - 1]
            gains.add(if (diff > 0) diff else 0.0)
            losses.add(if (diff < 0) -diff else 0.0)
        }
        val avgGains = computeWilderRmaSeries(gains, period)
        val avgLosses = computeWilderRmaSeries(losses, period)
        return closes.indices.map { i ->
            val g = avgGains[i]
            val l = avgLosses[i]
            when {
                l <= 1e-10 && g <= 1e-10 -> 50.0 // Harga datar (flat line / zero gain & loss): RSI seimbang di 50.0
                l <= 1e-10 -> 100.0 // Seluruh pergerakan naik (zero loss): RSI maksimal 100.0
                g <= 1e-10 -> 0.0 // Seluruh pergerakan turun (zero gain): RSI minimal 0.0
                else -> {
                    val rs = g / l
                    100.0 - (100.0 / (1.0 + rs))
                }
            }
        }
    }

    private fun computeAdx14AtClosedBar1(chron: List<Mt5RateBar>): Triple<Double, Double, Double> {
        // Wilder RMA ADX membutuhkan minimal 14 bar untuk TR/+DM/-DM dan 14 bar untuk DX smoothing = 28 bar
        if (chron.size < 28) return Triple(0.0, 0.0, 0.0)
        val trList = ArrayList<Double>(chron.size)
        val plusDmList = ArrayList<Double>(chron.size)
        val minusDmList = ArrayList<Double>(chron.size)
        trList.add(chron[0].high - chron[0].low)
        plusDmList.add(0.0)
        minusDmList.add(0.0)

        for (i in 1 until chron.size) {
            val cur = chron[i]
            val prev = chron[i - 1]
            val upMove = cur.high - prev.high
            val downMove = prev.low - cur.low
            val plusDm = if (upMove > downMove && upMove > 0) upMove else 0.0
            val minusDm = if (downMove > upMove && downMove > 0) downMove else 0.0
            val tr = maxOf(
                cur.high - cur.low,
                abs(cur.high - prev.close),
                abs(cur.low - prev.close)
            )
            trList.add(tr)
            plusDmList.add(plusDm)
            minusDmList.add(minusDm)
        }

        val atr = computeWilderRmaSeries(trList, 14)
        val smoothPlus = computeWilderRmaSeries(plusDmList, 14)
        val smoothMinus = computeWilderRmaSeries(minusDmList, 14)

        val plusDiSeries = chron.indices.map { i ->
            100.0 * (smoothPlus[i] / atr[i].coerceAtLeast(1e-9))
        }
        val minusDiSeries = chron.indices.map { i ->
            100.0 * (smoothMinus[i] / atr[i].coerceAtLeast(1e-9))
        }
        val dxSeries = chron.indices.map { i ->
            val sum = (plusDiSeries[i] + minusDiSeries[i]).coerceAtLeast(1e-9)
            100.0 * (abs(plusDiSeries[i] - minusDiSeries[i]) / sum)
        }
        val adxSeries = computeWilderRmaSeries(dxSeries, 14)

        val idx1 = (chron.size - 2).coerceAtLeast(0)
        return Triple(adxSeries[idx1], plusDiSeries[idx1], minusDiSeries[idx1])
    }

    /**
     * Profil kalibrasi khusus instrumen (XAUUSD vs EURUSD) agar batas Stop/ATR,
     * ambang ADX, dan deteksi choppiness menyesuaikan volatilitas alami aset.
     */
    fun getInstrumentCalibrationProfile(
        symbol: MarketSymbol,
        session: GlobalMarketSession
    ): InstrumentCalibrationProfile {
        return when (symbol) {
            MarketSymbol.XAUUSD -> {
                val asiaCaution = if (session == GlobalMarketSession.ASIA_PACIFIC) {
                    "Sesi Asia pada Emas rawan sweep likuiditas palsu; prioritaskan sinyal saat London/NY aktif."
                } else {
                    "Kalibrasi XAUUSD aktif (ekor wick lebih lebar diperhitungkan pada ATR)."
                }
                InstrumentCalibrationProfile(
                    symbol = symbol,
                    profileName = "Emas Spot (XAUUSD High-Volatility)",
                    adxTrendThreshold = 23.0,
                    atrStopMultiplier = 1.65,
                    choppyWickThreshold = 0.62,
                    maxSpreadToSlRatioPct = 20.0,
                    sessionGuidance = asiaCaution
                )
            }
            MarketSymbol.EURUSD -> {
                val sessionNote = if (session == GlobalMarketSession.ASIA_PACIFIC) {
                    "EURUSD sesi Asia cenderung sempit (ranging); gunakan konfirmasi breakout London."
                } else {
                    "Kalibrasi EURUSD aktif (filter konsolidasi ketat)."
                }
                InstrumentCalibrationProfile(
                    symbol = symbol,
                    profileName = "Forex Major (EURUSD Tight-Range)",
                    adxTrendThreshold = 25.0,
                    atrStopMultiplier = 1.35,
                    choppyWickThreshold = 0.56,
                    maxSpreadToSlRatioPct = 18.0,
                    sessionGuidance = sessionNote
                )
            }
        }
    }

    fun buildInstrumentCalibrationReport(
        bars: List<Mt5RateBar>,
        symbol: MarketSymbol,
        momentumReport: MomentumEngineReport,
        volatilityReport: VolatilityEngineReport,
        sessionReport: TradingSessionReport,
        highImpactNewsCount: Int
    ): InstrumentCalibrationReport {
        val profile = getInstrumentCalibrationProfile(symbol, sessionReport.currentSession)
        val closedWindow = bars.drop(1).take(8)
        var flips = 0
        var prevDir = 0
        for (bar in closedWindow) {
            val dir = when {
                bar.close > bar.open -> 1
                bar.close < bar.open -> -1
                else -> 0
            }
            if (dir != 0 && prevDir != 0 && dir != prevDir) {
                flips++
            }
            if (dir != 0) prevDir = dir
        }

        val avgWickRatio = if (closedWindow.isNotEmpty()) {
            closedWindow.map { b ->
                val range = (b.high - b.low).coerceAtLeast(symbol.pointSize)
                ((b.upperWick + b.lowerWick) / range).coerceIn(0.0, 1.0)
            }.average()
        } else 0.0

        val isChoppy = (flips >= 5 && momentumReport.adx14 < profile.adxTrendThreshold) ||
            (avgWickRatio >= profile.choppyWickThreshold && momentumReport.adx14 < profile.adxTrendThreshold)

        val bar1 = bars.getOrNull(1) ?: bars.firstOrNull()
        val spreadPts = (bar1?.spread ?: symbol.defaultSpreadPoints).coerceAtLeast(0)
        val spreadPrice = spreadPts * symbol.pointSize
        val slDist = volatilityReport.analyticalStopDistance.coerceAtLeast(symbol.pointSize)
        val spreadToSlPct = ((spreadPrice / slDist) * 100.0).coerceIn(0.0, 999.0)
        val isSpreadSafe = spreadToSlPct <= profile.maxSpreadToSlRatioPct
        val isNewsBlocked = highImpactNewsCount >= 2
        val qualityPassed = !isChoppy && isSpreadSafe && !isNewsBlocked

        val summary = when {
            !isSpreadSafe -> "Spread terlalu lebar (${String.format(Locale.US, "%.1f", spreadToSlPct)}% dari jarak SL). Biaya entry mahal."
            isChoppy -> "Pasar terdeteksi Sideways/Choppy ($flips pergantian arah, ADX ${String.format(Locale.US, "%.1f", momentumReport.adx14)}). Risiko false signal tinggi."
            isNewsBlocked -> "Waspada $highImpactNewsCount agenda berita Dampak Tinggi. Tunggu volatilitas stabil."
            else -> "Quality Gate Lolos (${profile.profileName}): Spread ${String.format(Locale.US, "%.1f", spreadToSlPct)}% dari SL, struktur bersih."
        }

        return InstrumentCalibrationReport(
            profile = profile,
            isChoppyMarketDetected = isChoppy,
            consecutiveFlipCount = flips,
            averageWickRatio = avgWickRatio,
            spreadPoints = spreadPts,
            spreadToSlRatioPct = spreadToSlPct,
            isSpreadSafe = isSpreadSafe,
            isHighImpactNewsBlocked = isNewsBlocked,
            qualityGatePassed = qualityPassed,
            qualityGateSummary = summary
        )
    }

    /**
     * Mini-Backtest Historis Non-Repaint berbasis Closed Candle saja (kronologis, nol look-ahead).
     * Menguji strategi konfluensi tren EMA(20/50) + RSI(14) dengan Target 1.5R dan SL berbasis ATR.
     * Konservatif: bila SL & TP1 tersentuh di candle yang sama, SL dihitung lebih dulu.
     */
    fun runHistoricalBacktest(
        bars: List<Mt5RateBar>,
        symbol: MarketSymbol,
        calibrationProfile: InstrumentCalibrationProfile = getInstrumentCalibrationProfile(symbol, GlobalMarketSession.LONDON)
    ): BacktestSummaryReport {
        val closedChron = if (bars.size > 1) bars.drop(1).asReversed() else emptyList()
        if (closedChron.size < 70) {
            return BacktestSummaryReport(
                sampleBarsCount = closedChron.size,
                totalTrades = 0,
                wins = 0,
                losses = 0,
                breakevenOrOpen = 0,
                winRatePct = 0.0,
                profitFactor = 0.0,
                netExpectancyR = 0.0,
                maxDrawdownR = 0.0,
                honestNote = "Data historis kurang dari 70 candle tutup; mini-backtest belum dapat dihitung."
            )
        }

        val closes = closedChron.map { it.close }
        val ema20 = computeEmaSeries(closes, 20)
        val ema50 = computeEmaSeries(closes, 50)
        val rsi14 = computeRsiSeries(closes, 14)

        val trList = ArrayList<Double>(closedChron.size)
        for (i in closedChron.indices) {
            val cur = closedChron[i]
            val tr = if (i == 0) {
                cur.high - cur.low
            } else {
                val prevClose = closedChron[i - 1].close
                maxOf(cur.high - cur.low, abs(cur.high - prevClose), abs(cur.low - prevClose))
            }
            trList.add(tr)
        }
        val atrSeries = computeWilderRmaSeries(trList, 14)

        var wins = 0
        var losses = 0
        var openOrExpired = 0
        var grossWinR = 0.0
        var grossLossR = 0.0
        var equityR = 0.0
        var peakR = 0.0
        var maxDdR = 0.0

        var idx = 55
        while (idx < closedChron.size - 2) {
            val bar = closedChron[idx]
            val e20 = ema20[idx]
            val e50 = ema50[idx]
            val rsi = rsi14[idx]
            val atr = atrSeries[idx].coerceAtLeast(symbol.pointSize * 10.0)
            val stopDist = atr * calibrationProfile.atrStopMultiplier
            val tpDist = stopDist * 1.5

            val isBuySetup = e20 > e50 && bar.close > e20 && bar.close > bar.open && rsi in 52.0..72.0
            val isSellSetup = e20 < e50 && bar.close < e20 && bar.close < bar.open && rsi in 28.0..48.0

            if (!isBuySetup && !isSellSetup) {
                idx++
                continue
            }

            val entry = bar.close
            val sl = if (isBuySetup) entry - stopDist else entry + stopDist
            val tp = if (isBuySetup) entry + tpDist else entry - tpDist

            var resolved = false
            var exitIndex = idx + 1
            val maxLookahead = minOf(closedChron.size - 1, idx + 36)
            for (fwd in (idx + 1)..maxLookahead) {
                val fBar = closedChron[fwd]
                if (isBuySetup) {
                    if (fBar.low <= sl) {
                        losses++
                        grossLossR += 1.0
                        equityR -= 1.0
                        exitIndex = fwd
                        resolved = true
                        break
                    } else if (fBar.high >= tp) {
                        wins++
                        grossWinR += 1.5
                        equityR += 1.5
                        exitIndex = fwd
                        resolved = true
                        break
                    }
                } else {
                    if (fBar.high >= sl) {
                        losses++
                        grossLossR += 1.0
                        equityR -= 1.0
                        exitIndex = fwd
                        resolved = true
                        break
                    } else if (fBar.low <= tp) {
                        wins++
                        grossWinR += 1.5
                        equityR += 1.5
                        exitIndex = fwd
                        resolved = true
                        break
                    }
                }
                exitIndex = fwd
            }

            if (!resolved) {
                openOrExpired++
            } else {
                if (equityR > peakR) peakR = equityR
                maxDdR = max(maxDdR, peakR - equityR)
            }

            idx = exitIndex + 1
        }

        val resolvedTrades = wins + losses
        val totalTrades = resolvedTrades + openOrExpired
        val winRate = if (resolvedTrades > 0) (wins.toDouble() / resolvedTrades) * 100.0 else 0.0
        val pf = when {
            grossLossR > 0.0 -> grossWinR / grossLossR
            grossWinR > 0.0 -> 9.99
            else -> 0.0
        }
        val expectancy = if (resolvedTrades > 0) (grossWinR - grossLossR) / resolvedTrades else 0.0

        val note = when {
            resolvedTrades == 0 -> "Belum ada setup selesai pada ${closedChron.size} candle tutup terakhir."
            resolvedTrades < 5 -> "Sampel masih kecil ($resolvedTrades trade selesai dari ${closedChron.size} bar). Jadikan referensi awal saja."
            else -> "Simulasi objektif non-repaint pada ${closedChron.size} candle tutup (Target 1.5R, tie-break SL konservatif)."
        }

        return BacktestSummaryReport(
            sampleBarsCount = closedChron.size,
            totalTrades = totalTrades,
            wins = wins,
            losses = losses,
            breakevenOrOpen = openOrExpired,
            winRatePct = winRate,
            profitFactor = pf,
            netExpectancyR = expectancy,
            maxDrawdownR = maxDdR,
            honestNote = note
        )
    }

    /**
     * Mengevaluasi outcome riwayat sinyal (WIN_TP / LOSS_SL) terhadap candle tutup terbaru.
     * Konservatif: bila SL & TP tersentuh dalam candle yang sama, SL dihitung lebih dahulu.
     */
    fun evaluatePendingSignalOutcome(
        directionBias: String,
        verdictLabel: String,
        closedBarEpochSec: Long,
        stopReferencePrice: Double,
        targetReferencePrice: Double,
        bars: List<Mt5RateBar>,
        evaluatedAtMs: Long = System.currentTimeMillis()
    ): Triple<SignalOutcomeStatus, Double, Long>? {
        val inferredBias = when {
            directionBias.equals("BUY", ignoreCase = true) -> "BUY"
            directionBias.equals("SELL", ignoreCase = true) -> "SELL"
            verdictLabel.contains("BULLISH", ignoreCase = true) || verdictLabel.contains("NAIK BIAS", ignoreCase = true) -> "BUY"
            verdictLabel.contains("BEARISH", ignoreCase = true) || verdictLabel.contains("TURUN BIAS", ignoreCase = true) -> "SELL"
            else -> "NEUTRAL"
        }
        if (inferredBias == "NEUTRAL") {
            return Triple(SignalOutcomeStatus.EXPIRED_NEUTRAL, 0.0, evaluatedAtMs)
        }
        if (stopReferencePrice <= 0.0 || targetReferencePrice <= 0.0) return null

        val futureClosedChron = bars
            .filter { it.barIndex >= 1 && it.timeEpochSec > closedBarEpochSec }
            .sortedBy { it.timeEpochSec }

        for (bar in futureClosedChron) {
            if (inferredBias == "BUY") {
                if (bar.low <= stopReferencePrice) {
                    return Triple(SignalOutcomeStatus.LOSS_SL, stopReferencePrice, evaluatedAtMs)
                }
                if (bar.high >= targetReferencePrice) {
                    return Triple(SignalOutcomeStatus.WIN_TP, targetReferencePrice, evaluatedAtMs)
                }
            } else {
                if (bar.high >= stopReferencePrice) {
                    return Triple(SignalOutcomeStatus.LOSS_SL, stopReferencePrice, evaluatedAtMs)
                }
                if (bar.low <= targetReferencePrice) {
                    return Triple(SignalOutcomeStatus.WIN_TP, targetReferencePrice, evaluatedAtMs)
                }
            }
        }
        return null
    }

    /**
     * Menghasilkan ringkasan bahasa awam yang mudah dipahami pengguna non-teknis.
     */
    fun buildPlainLanguageBriefing(
        symbol: MarketSymbol,
        timeframe: Timeframe,
        verdict: AnalyticalVerdict,
        trend: TrendEngineReport,
        momentum: MomentumEngineReport,
        mtf: MtfEngineReport,
        news: NewsEngineReport,
        session: TradingSessionReport,
        calibration: InstrumentCalibrationReport
    ): PlainLanguageBriefing {
        val headline = when (verdict) {
            AnalyticalVerdict.STRONG_BULLISH_CONFLUENCE,
            AnalyticalVerdict.MODERATE_BULLISH_BIAS -> "PELUANG BELI (BUY)"
            AnalyticalVerdict.STRONG_BEARISH_CONFLUENCE,
            AnalyticalVerdict.MODERATE_BEARISH_BIAS -> "PELUANG JUAL (SELL)"
            AnalyticalVerdict.HTF_BULLISH_LTF_PULLBACK -> "TUNGGU KOREKSI SELESAI (ARAH UTAMA NAIK)"
            AnalyticalVerdict.HTF_BEARISH_LTF_PULLBACK -> "TUNGGU KOREKSI SELESAI (ARAH UTAMA TURUN)"
            AnalyticalVerdict.NEUTRAL_CONSOLIDATION -> "TUNGGU DULU (PASAR BELUM JELAS)"
        }

        val trendPlain = when (trend.classification) {
            TrendClassification.STRONG_BULLISH, TrendClassification.BULLISH -> "harga sedang dalam tren naik"
            TrendClassification.STRONG_BEARISH, TrendClassification.BEARISH -> "harga sedang dalam tren turun"
            TrendClassification.NEUTRAL -> "harga masih bergerak mendatar (sideways)"
        }
        val mtfPlain = if (!mtf.isMtfDataComplete) {
            "data beberapa timeframe masih melengkapi sinkronisasi"
        } else {
            when (mtf.htfBias) {
                PatternBias.BULLISH -> "arah grafik besar (H1/M30) mendukung kenaikan"
                PatternBias.BEARISH -> "arah grafik besar (H1/M30) mendukung penurunan"
                PatternBias.NEUTRAL -> "grafik besar masih netral"
            }
        }
        val momentumPlain = if (momentum.isTrendStrengthConfirmed) {
            "dorongan harga cukup kuat"
        } else {
            "tenaga pergerakan masih lemah"
        }

        val beginnerSummary = "${symbol.code} (${timeframe.label}) saat ini menunjukkan bahwa $trendPlain, $mtfPlain, dan $momentumPlain. " +
            if (verdict in listOf(
                    AnalyticalVerdict.STRONG_BULLISH_CONFLUENCE,
                    AnalyticalVerdict.MODERATE_BULLISH_BIAS,
                    AnalyticalVerdict.STRONG_BEARISH_CONFLUENCE,
                    AnalyticalVerdict.MODERATE_BEARISH_BIAS
                )
            ) {
                "Candle penutupan terakhir sudah memberi konfirmasi arah yang lebih terukur."
            } else {
                "Sebaiknya tahan transaksi terlebih dahulu agar tidak terjebak pergerakan bolak-balik."
            }

        val actionHint = when {
            calibration.isHighImpactNewsBlocked ->
                "Ada ${news.highImpactCount} berita berdampak besar. Kurangi risiko atau tunggu 15–30 menit setelah rilis."
            !calibration.isSpreadSafe ->
                "Spread sedang lebar (${calibration.spreadPoints} pt). Pertimbangkan tunggu sesi lebih likuid sebelum masuk."
            verdict in listOf(
                AnalyticalVerdict.STRONG_BULLISH_CONFLUENCE,
                AnalyticalVerdict.MODERATE_BULLISH_BIAS,
                AnalyticalVerdict.STRONG_BEARISH_CONFLUENCE,
                AnalyticalVerdict.MODERATE_BEARISH_BIAS
            ) ->
                "Pasang Entry, Stop Loss, dan Target Profit sekaligus sesuai pilihan risiko Anda. ${session.currentSession.sessionName} aktif."
            else ->
                "Tunggu sampai candle tutup berikutnya memberi arah BUY/SELL yang bersih. ${calibration.profile.sessionGuidance}"
        }

        val caution = when {
            calibration.isHighImpactNewsBlocked -> "Berita Besar Aktif (${news.highImpactCount})"
            calibration.isChoppyMarketDetected -> "Pasar Rawan Sideways / Choppy"
            !calibration.isSpreadSafe -> "Spread Broker Sedang Lebar"
            else -> null
        }

        return PlainLanguageBriefing(
            headline = headline,
            beginnerSummary = beginnerSummary,
            actionHint = actionHint,
            cautionBadge = caution
        )
    }
}
