package com.example.domain

import com.example.model.AnalyticalVerdict
import com.example.model.CompleteHubAnalysis
import com.example.model.DataEngineReport
import com.example.model.MacdState
import com.example.model.MacroNewsArticle
import com.example.model.MacroSentiment
import com.example.model.MarketStructureClass
import com.example.model.MarketSymbol
import com.example.model.MomentumEngineReport
import com.example.model.Mt5RateBar
import com.example.model.MtfEngineReport
import com.example.model.NewsEngineReport
import com.example.model.NewsImpactLevel
import com.example.model.PatternBias
import com.example.model.RsiRegime
import com.example.model.SrInteractionType
import com.example.model.StructureBreakEvent
import com.example.model.Timeframe
import com.example.model.TimeframeMatrixRow
import com.example.model.TrendClassification
import com.example.model.TrendEngineReport
import com.example.model.VolatilityEngineReport
import com.example.model.VolatilityState
import com.example.model.VolumeClassification
import com.example.model.VolumeEngineReport
import kotlin.math.abs

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

        val htfBullCount = htfRows.count { it.bias == PatternBias.BULLISH }
        val htfBearCount = htfRows.count { it.bias == PatternBias.BEARISH }

        val htfBias = when {
            htfBullCount >= 2 -> PatternBias.BULLISH
            htfBearCount >= 2 -> PatternBias.BEARISH
            htfBullCount == 1 && htfBearCount == 0 -> PatternBias.BULLISH
            htfBearCount == 1 && htfBullCount == 0 -> PatternBias.BEARISH
            else -> PatternBias.NEUTRAL
        }

        val ltfBullCount = ltfRows.count { it.bias == PatternBias.BULLISH }
        val ltfBearCount = ltfRows.count { it.bias == PatternBias.BEARISH }

        val isPullback = (htfBias == PatternBias.BULLISH && ltfBearCount >= 1 && ltfBullCount == 0) ||
            (htfBias == PatternBias.BEARISH && ltfBullCount >= 1 && ltfBearCount == 0)

        val ltfState = when {
            htfBias == PatternBias.BULLISH && isPullback -> "TF Kecil (M5/M1) Sedang Koreksi (Pullback)"
            htfBias == PatternBias.BEARISH && isPullback -> "TF Kecil (M5/M1) Sedang Koreksi Naik"
            htfBias == PatternBias.BULLISH && ltfBullCount >= 1 -> "Searah Naik (Bullish) dengan TF Besar"
            htfBias == PatternBias.BEARISH && ltfBearCount >= 1 -> "Searah Turun (Bearish) dengan TF Besar"
            else -> "TF Kecil Campuran / Sideways"
        }

        val conclusion = when {
            htfBias == PatternBias.BULLISH && isPullback ->
                "Timeframe Besar (H1/M30/M15) Bullish (Naik), sedangkan Timeframe Kecil (M5/M1) sedang koreksi (Pullback). JANGAN langsung menyebut pembalikan arah (Reversal); tunggu konfirmasi candle tutup Higher Low (HL) di area Support."
            htfBias == PatternBias.BEARISH && isPullback ->
                "Timeframe Besar (H1/M30/M15) Bearish (Turun), sedangkan Timeframe Kecil (M5/M1) sedang koreksi naik (Pullback). JANGAN langsung menyebut Reversal; tunggu konfirmasi candle tutup Lower High (LH) di area Resistance."
            htfBias == PatternBias.BULLISH ->
                "Konfluensi Naik (Bullish) Selaras dari Timeframe Besar (H1 -> M30 -> M15) hingga Timeframe Kecil."
            htfBias == PatternBias.BEARISH ->
                "Konfluensi Turun (Bearish) Selaras dari Timeframe Besar (H1 -> M30 -> M15) hingga Timeframe Kecil."
            else ->
                "Struktur Multi-Timeframe sedang konsolidasi/transisi. Tunggu penembusan struktur yang sah di H1/M30."
        }

        return MtfEngineReport(
            rows = rows,
            htfBias = htfBias,
            ltfState = ltfState,
            isPullbackInHtfTrend = isPullback,
            executiveConclusion = conclusion
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
        isLiveRss: Boolean
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
            lastSyncTimeMs = System.currentTimeMillis()
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
            lastUpdatedMs = System.currentTimeMillis(),
            isLiveRssSynced = isLiveRss
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

        // 7. Bobot Multi-Timeframe (6 poin)
        if (mtfReport.htfBias == PatternBias.BULLISH) bullPoints += 6
        if (mtfReport.htfBias == PatternBias.BEARISH) bearPoints += 6

        val finalBullScore = bullPoints.coerceIn(0, 100)
        val finalBearScore = bearPoints.coerceIn(0, 100)

        val verdict = when {
            mtfReport.isPullbackInHtfTrend && mtfReport.htfBias == PatternBias.BULLISH &&
                activeTimeframe in listOf(Timeframe.M1, Timeframe.M5) ->
                AnalyticalVerdict.HTF_BULLISH_LTF_PULLBACK
            mtfReport.isPullbackInHtfTrend && mtfReport.htfBias == PatternBias.BEARISH &&
                activeTimeframe in listOf(Timeframe.M1, Timeframe.M5) ->
                AnalyticalVerdict.HTF_BEARISH_LTF_PULLBACK
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

        val checklist = listOf(
            "Candle Tutup [1] Terkunci (Bebas Look-Ahead / Non-Repaint)" to true,
            "Susunan Tren EMA 20/50/200 Selaras (${trendReport.classification.label})" to
                (trendReport.isPerfectBullishStack || trendReport.isPerfectBearishStack),
            "Struktur Pasar Terkonfirmasi (${structureReport.classification.label})" to
                (structureReport.classification != MarketStructureClass.RANGE),
            "Pola Candlestick di Candle Tutup [1] (${candleReport.activeClosedPatterns.firstOrNull()?.pattern?.displayName ?: "Tidak Ada"})" to
                candleReport.activeClosedPatterns.isNotEmpty(),
            "Kekuatan Tren ADX(14) > 25 (${String.format(java.util.Locale.US, "%.1f", momentumReport.adx14)})" to
                momentumReport.isTrendStrengthConfirmed,
            "Ekspansi Volume > SMA 20 (${String.format(java.util.Locale.US, "%.2f", volumeReport.volumeToSmaRatio)}x)" to
                volumeReport.isVolumeExpansion,
            "Konfluensi Multi-Timeframe H1/M30/M15 (${mtfReport.htfBias.label})" to
                (mtfReport.htfBias != PatternBias.NEUTRAL)
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
            verdict = verdict,
            bullishConfluenceScore = finalBullScore,
            bearishConfluenceScore = finalBearScore,
            confluenceChecklist = checklist
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
            if (l <= 1e-10) 100.0
            else {
                val rs = g / l
                100.0 - (100.0 / (1.0 + rs))
            }
        }
    }

    private fun computeAdx14AtClosedBar1(chron: List<Mt5RateBar>): Triple<Double, Double, Double> {
        if (chron.size < 20) return Triple(25.0, 20.0, 20.0)
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
}
