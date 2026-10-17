package com.example.domain

import com.example.model.CandleAnatomyType
import com.example.model.CandleEngineReport
import com.example.model.CandlestickPatternType
import com.example.model.DetectedPattern
import com.example.model.MarketStructureClass
import com.example.model.MarketStructureReport
import com.example.model.MarketSymbol
import com.example.model.Mt5RateBar
import com.example.model.SrEngineReport
import com.example.model.SrInteractionType
import com.example.model.SrZone
import com.example.model.StructureBreakEvent
import com.example.model.SwingLabel
import com.example.model.SwingPoint
import kotlin.math.abs

object CandleAndStructureEngines {

    /**
     * 3. MESIN CANDLE (CANDLE ENGINE)
     * Hanya mengevaluasi candle yang SUDAH TUTUP / CLOSED (barIndex >= 1).
     * Candle 0 diabaikan untuk sinyal final agar 100% NON-REPAINT.
     */
    fun runCandleEngine(
        bars: List<Mt5RateBar>,
        symbol: MarketSymbol,
        atr14: Double
    ): CandleEngineReport {
        val bar1 = bars.getOrNull(1) ?: bars.first()
        val point = symbol.pointSize

        val bodyPts = bar1.bodySize / point
        val upperWickPts = bar1.upperWick / point
        val lowerWickPts = bar1.lowerWick / point
        val rangePts = bar1.totalRange / point
        val bodyRatioPct = bar1.bodyToRangeRatio * 100.0
        val wickToBody = bar1.wickToBodyRatio

        val anatomy = when {
            bar1.bodyToRangeRatio <= 0.12 -> CandleAnatomyType.DOJI
            bar1.isBullish && bar1.bodyToRangeRatio >= 0.65 && bar1.totalRange >= atr14 * 0.70 ->
                CandleAnatomyType.STRONG_BULLISH
            bar1.isBearish && bar1.bodyToRangeRatio >= 0.65 && bar1.totalRange >= atr14 * 0.70 ->
                CandleAnatomyType.STRONG_BEARISH
            bar1.isBullish -> CandleAnatomyType.BULLISH
            else -> CandleAnatomyType.BEARISH
        }

        val allClosedDetections = mutableListOf<DetectedPattern>()
        val maxIndex = (bars.size - 8).coerceAtMost(32)
        for (idx in 1..maxIndex) {
            val detectedAtIdx = detect15PatternsAtClosedIndex(bars, idx, atr14)
            allClosedDetections.addAll(detectedAtIdx)
        }

        val activeBar1Patterns = allClosedDetections.filter { it.closedBarIndex == 1 }

        return CandleEngineReport(
            closedBar1Metrics = bar1,
            anatomyType = anatomy,
            bodyPoints = bodyPts,
            upperWickPoints = upperWickPts,
            lowerWickPoints = lowerWickPts,
            rangePoints = rangePts,
            bodyToRangePct = bodyRatioPct,
            wickToBodyRatio = wickToBody,
            activeClosedPatterns = activeBar1Patterns,
            recentHistoricalPatterns = allClosedDetections.take(18)
        )
    }

    private fun detect15PatternsAtClosedIndex(
        bars: List<Mt5RateBar>,
        i: Int,
        atr: Double
    ): List<DetectedPattern> {
        if (i < 1 || i + 7 >= bars.size) return emptyList()
        val c1 = bars[i]
        val c2 = bars[i + 1]
        val c3 = bars[i + 2]

        // Konteks ayunan harga sebelum candle i (dari candle i+6 menuju candle i+1, searah kiri-ke-kanan di MT5)
        val lookbackRef = bars[i + 6]
        val priorSwingDelta = c2.close - lookbackRef.close
        val recentLowWindow = (i + 1..i + 5).minOf { bars[it].low }
        val recentHighWindow = (i + 1..i + 5).maxOf { bars[it].high }

        val isPriorDowntrend = priorSwingDelta < -atr * 0.25 || c1.low <= recentLowWindow
        val isPriorUptrend = priorSwingDelta > atr * 0.25 || c1.high >= recentHighWindow
        val tolerance = (atr * 0.08).coerceAtLeast(c1.totalRange * 0.06)

        val list = mutableListOf<DetectedPattern>()

        // 1. Doji
        if (c1.bodyToRangeRatio <= 0.11 && c1.totalRange >= atr * 0.25) {
            list.add(
                DetectedPattern(
                    pattern = CandlestickPatternType.DOJI,
                    closedBarIndex = i,
                    barTimeEpochSec = c1.timeEpochSec,
                    priceAtDetection = c1.close,
                    rationale = "Bodi candle tutup [$i] hanya ${(c1.bodyToRangeRatio * 100).toInt()}% dari total rentang (pasar seimbang/ragu)."
                )
            )
        }

        // 2. Hammer (Pantulan Bawah)
        if (c1.lowerWick >= c1.bodySize * 1.8 &&
            c1.lowerWick >= c1.totalRange * 0.52 &&
            c1.upperWick <= c1.totalRange * 0.25 &&
            c1.bodyToRangeRatio in 0.05..0.48 &&
            isPriorDowntrend && c1.low <= minOf(c2.low, c3.low)
        ) {
            list.add(
                DetectedPattern(
                    pattern = CandlestickPatternType.HAMMER,
                    closedBarIndex = i,
                    barTimeEpochSec = c1.timeEpochSec,
                    priceAtDetection = c1.close,
                    rationale = "Candle tutup [$i] menolak harga bawah dengan ekor bawah ${(c1.lowerWick / c1.bodySize.coerceAtLeast(1e-6)).format1()}x ukuran bodi."
                )
            )
        }

        // 3. Inverted Hammer
        if (c1.upperWick >= c1.bodySize * 1.8 &&
            c1.upperWick >= c1.totalRange * 0.52 &&
            c1.lowerWick <= c1.totalRange * 0.22 &&
            c1.bodyToRangeRatio in 0.05..0.45 &&
            priorSwingDelta < -atr * 0.35 && c1.low <= recentLowWindow
        ) {
            list.add(
                DetectedPattern(
                    pattern = CandlestickPatternType.INVERTED_HAMMER,
                    closedBarIndex = i,
                    barTimeEpochSec = c1.timeEpochSec,
                    priceAtDetection = c1.close,
                    rationale = "Candle tutup [$i] menguji likuiditas atas di dasar penurunan dengan ekor atas > 1.8x bodi."
                )
            )
        }

        // 4. Shooting Star (Tolakan Atas)
        if (c1.upperWick >= c1.bodySize * 1.8 &&
            c1.upperWick >= c1.totalRange * 0.52 &&
            c1.lowerWick <= c1.totalRange * 0.25 &&
            c1.bodyToRangeRatio in 0.05..0.46 &&
            isPriorUptrend && c1.high >= maxOf(c2.high, c3.high)
        ) {
            list.add(
                DetectedPattern(
                    pattern = CandlestickPatternType.SHOOTING_STAR,
                    closedBarIndex = i,
                    barTimeEpochSec = c1.timeEpochSec,
                    priceAtDetection = c1.close,
                    rationale = "Candle tutup [$i] terkena tolakan di puncak dengan ekor atas ${(c1.upperWick / c1.bodySize.coerceAtLeast(1e-6)).format1()}x bodi."
                )
            )
        }

        // 5. Hanging Man
        if (c1.lowerWick >= c1.bodySize * 1.8 &&
            c1.lowerWick >= c1.totalRange * 0.52 &&
            c1.upperWick <= c1.totalRange * 0.22 &&
            c1.bodyToRangeRatio in 0.08..0.45 &&
            priorSwingDelta > atr * 0.35 && c1.high >= recentHighWindow && c1.isBearish
        ) {
            list.add(
                DetectedPattern(
                    pattern = CandlestickPatternType.HANGING_MAN,
                    closedBarIndex = i,
                    barTimeEpochSec = c1.timeEpochSec,
                    priceAtDetection = c1.close,
                    rationale = "Candle tutup [$i] membentuk ekor bawah panjang di area puncak setelah kenaikan."
                )
            )
        }

        // 6. Bullish Engulfing
        if (c1.isBullish && c2.isBearish &&
            c1.close >= c2.open &&
            c1.open <= c2.close + tolerance * 0.5 &&
            c1.bodySize > c2.bodySize * 1.05
        ) {
            list.add(
                DetectedPattern(
                    pattern = CandlestickPatternType.BULLISH_ENGULFING,
                    closedBarIndex = i,
                    barTimeEpochSec = c1.timeEpochSec,
                    priceAtDetection = c1.close,
                    rationale = "Bodi candle naik [$i] yang sudah tutup menelan penuh bodi candle turun sebelumnya [${i + 1}]."
                )
            )
        }

        // 7. Bearish Engulfing
        if (c1.isBearish && c2.isBullish &&
            c1.close <= c2.open &&
            c1.open >= c2.close - tolerance * 0.5 &&
            c1.bodySize > c2.bodySize * 1.05
        ) {
            list.add(
                DetectedPattern(
                    pattern = CandlestickPatternType.BEARISH_ENGULFING,
                    closedBarIndex = i,
                    barTimeEpochSec = c1.timeEpochSec,
                    priceAtDetection = c1.close,
                    rationale = "Bodi candle turun [$i] yang sudah tutup menelan penuh bodi candle naik sebelumnya [${i + 1}]."
                )
            )
        }

        // 8. Bullish Harami
        if (c2.isBearish && c1.isBullish &&
            c2.bodyToRangeRatio >= 0.50 &&
            c1.open > c2.close && c1.close < c2.open &&
            c1.bodySize <= c2.bodySize * 0.65
        ) {
            list.add(
                DetectedPattern(
                    pattern = CandlestickPatternType.BULLISH_HARAMI,
                    closedBarIndex = i,
                    barTimeEpochSec = c1.timeEpochSec,
                    priceAtDetection = c1.close,
                    rationale = "Bodi candle naik [$i] berada di dalam rentang bodi candle turun besar [${i + 1}]."
                )
            )
        }

        // 9. Bearish Harami
        if (c2.isBullish && c1.isBearish &&
            c2.bodyToRangeRatio >= 0.50 &&
            c1.open < c2.close && c1.close > c2.open &&
            c1.bodySize <= c2.bodySize * 0.65
        ) {
            list.add(
                DetectedPattern(
                    pattern = CandlestickPatternType.BEARISH_HARAMI,
                    closedBarIndex = i,
                    barTimeEpochSec = c1.timeEpochSec,
                    priceAtDetection = c1.close,
                    rationale = "Bodi candle turun [$i] berada di dalam rentang bodi candle naik besar [${i + 1}]."
                )
            )
        }

        // 10. Tweezer Top
        if (c2.isBullish && c1.isBearish &&
            abs(c1.high - c2.high) <= tolerance &&
            c1.high >= c3.high
        ) {
            list.add(
                DetectedPattern(
                    pattern = CandlestickPatternType.TWEEZER_TOP,
                    closedBarIndex = i,
                    barTimeEpochSec = c1.timeEpochSec,
                    priceAtDetection = c1.close,
                    rationale = "Tolakan ganda di titik tertinggi yang sama (${c1.high}) pada candle tutup [${i + 1}] dan [$i]."
                )
            )
        }

        // 11. Tweezer Bottom
        if (c2.isBearish && c1.isBullish &&
            abs(c1.low - c2.low) <= tolerance &&
            c1.low <= c3.low
        ) {
            list.add(
                DetectedPattern(
                    pattern = CandlestickPatternType.TWEEZER_BOTTOM,
                    closedBarIndex = i,
                    barTimeEpochSec = c1.timeEpochSec,
                    priceAtDetection = c1.close,
                    rationale = "Pertahanan ganda di titik terendah yang sama (${c1.low}) pada candle tutup [${i + 1}] dan [$i]."
                )
            )
        }

        // 12. Morning Star
        val c3Midpoint = (c3.open + c3.close) / 2.0
        if (c3.isBearish && c3.bodyToRangeRatio >= 0.45 &&
            c2.bodySize <= c3.bodySize * 0.45 &&
            c1.isBullish && c1.close >= c3Midpoint
        ) {
            list.add(
                DetectedPattern(
                    pattern = CandlestickPatternType.MORNING_STAR,
                    closedBarIndex = i,
                    barTimeEpochSec = c1.timeEpochSec,
                    priceAtDetection = c1.close,
                    rationale = "Pola 3 candle Morning Star tuntas pada candle tutup [$i], melewati titik tengah candle [${i + 2}]."
                )
            )
        }

        // 13. Evening Star
        if (c3.isBullish && c3.bodyToRangeRatio >= 0.45 &&
            c2.bodySize <= c3.bodySize * 0.45 &&
            c1.isBearish && c1.close <= c3Midpoint
        ) {
            list.add(
                DetectedPattern(
                    pattern = CandlestickPatternType.EVENING_STAR,
                    closedBarIndex = i,
                    barTimeEpochSec = c1.timeEpochSec,
                    priceAtDetection = c1.close,
                    rationale = "Pola 3 candle Evening Star tuntas pada candle tutup [$i], menembus bawah titik tengah candle [${i + 2}]."
                )
            )
        }

        // 14. Three White Soldiers
        if (c3.isBullish && c2.isBullish && c1.isBullish &&
            c1.close > c2.close && c2.close > c3.close &&
            c1.bodyToRangeRatio >= 0.50 && c2.bodyToRangeRatio >= 0.50 && c3.bodyToRangeRatio >= 0.45
        ) {
            list.add(
                DetectedPattern(
                    pattern = CandlestickPatternType.THREE_WHITE_SOLDIERS,
                    closedBarIndex = i,
                    barTimeEpochSec = c1.timeEpochSec,
                    priceAtDetection = c1.close,
                    rationale = "3 candle bullish kuat berturut-turut [${i + 2} -> $i] ditutup makin tinggi."
                )
            )
        }

        // 15. Three Black Crows
        if (c3.isBearish && c2.isBearish && c1.isBearish &&
            c1.close < c2.close && c2.close < c3.close &&
            c1.bodyToRangeRatio >= 0.50 && c2.bodyToRangeRatio >= 0.50 && c3.bodyToRangeRatio >= 0.45
        ) {
            list.add(
                DetectedPattern(
                    pattern = CandlestickPatternType.THREE_BLACK_CROWS,
                    closedBarIndex = i,
                    barTimeEpochSec = c1.timeEpochSec,
                    priceAtDetection = c1.close,
                    rationale = "3 candle bearish kuat berturut-turut [${i + 2} -> $i] ditutup makin rendah."
                )
            )
        }

        return list
    }

    /**
     * 4. MESIN STRUKTUR PASAR (MARKET STRUCTURE ENGINE)
     * Menggunakan kaki struktur bergantian (Alternating High-Low Structural Legs) dengan filter
     * minimum deviasi ATR agar riak koreksi kecil (pullback mikro) di puncak kenaikan tidak
     * terbaca terbalik sebagai Struktur Bearish, dan pantulan kecil di dasar penurunan tidak
     * terbaca terbalik sebagai Struktur Bullish.
     * Tetap 100% NON-REPAINT (hanya membaca candle yang sudah tutup: barIndex >= 1).
     */
    fun runMarketStructureEngine(
        bars: List<Mt5RateBar>,
        atr14: Double
    ): MarketStructureReport {
        val pivotSpan = 2
        val minConfirmedBarIndex = pivotSpan + 1 // minimal barIndex = 3 (punya 2 candle tutup di kanan: bar 2 dan bar 1)
        val maxSearchIndex = (bars.size - pivotSpan - 1).coerceAtMost(180)

        data class RawCandidate(
            val bar: Mt5RateBar,
            val isHigh: Boolean,
            val price: Double
        )

        val candidatesChronological = mutableListOf<RawCandidate>()
        for (idx in maxSearchIndex downTo minConfirmedBarIndex) {
            val candidate = bars[idx]
            var isSwingHigh = true
            var isSwingLow = true
            for (offset in 1..pivotSpan) {
                if (bars[idx - offset].high > candidate.high || bars[idx + offset].high >= candidate.high) {
                    isSwingHigh = false
                }
                if (bars[idx - offset].low < candidate.low || bars[idx + offset].low <= candidate.low) {
                    isSwingLow = false
                }
            }
            if (isSwingHigh && isSwingLow) {
                if (candidate.isBullish) {
                    candidatesChronological.add(RawCandidate(candidate, true, candidate.high))
                } else {
                    candidatesChronological.add(RawCandidate(candidate, false, candidate.low))
                }
            } else if (isSwingHigh) {
                candidatesChronological.add(RawCandidate(candidate, true, candidate.high))
            } else if (isSwingLow) {
                candidatesChronological.add(RawCandidate(candidate, false, candidate.low))
            }
        }

        // Filter kaki struktur bergantian (High <-> Low) dengan deviasi minimal 0.60 * ATR14
        // agar koreksi kecil di dalam tren kuat tidak membalik pembacaan HH/HL vs LH/LL
        val minSwingLeg = (atr14 * 0.60).coerceAtLeast(1e-6)
        val alternatingSwings = mutableListOf<RawCandidate>()

        for (cand in candidatesChronological) {
            val last = alternatingSwings.lastOrNull()
            if (last == null) {
                alternatingSwings.add(cand)
            } else if (last.isHigh == cand.isHigh) {
                // Jika bertipe sama (High setelah High, atau Low setelah Low), simpan titik yang lebih ekstrem
                if (cand.isHigh && cand.price >= last.price) {
                    alternatingSwings[alternatingSwings.lastIndex] = cand
                } else if (!cand.isHigh && cand.price <= last.price) {
                    alternatingSwings[alternatingSwings.lastIndex] = cand
                }
            } else {
                // Jika berbeda tipe (High <-> Low), pastikan jarak ayunannya signifikan (>= 0.60 ATR)
                val legMove = abs(cand.price - last.price)
                if (legMove >= minSwingLeg) {
                    alternatingSwings.add(cand)
                }
            }
        }

        val labeledSwingsChron = mutableListOf<SwingPoint>()
        var prevHighPrice: Double? = null
        var prevLowPrice: Double? = null

        for (item in alternatingSwings) {
            if (item.isHigh) {
                val lbl = when {
                    prevHighPrice == null -> SwingLabel.SH
                    item.price >= prevHighPrice!! - (atr14 * 0.04) -> SwingLabel.HH
                    else -> SwingLabel.LH
                }
                prevHighPrice = item.price
                labeledSwingsChron.add(
                    SwingPoint(
                        closedBarIndex = item.bar.barIndex,
                        timeEpochSec = item.bar.timeEpochSec,
                        price = item.price,
                        label = lbl
                    )
                )
            } else {
                val lbl = when {
                    prevLowPrice == null -> SwingLabel.SL
                    item.price >= prevLowPrice!! - (atr14 * 0.04) -> SwingLabel.HL
                    else -> SwingLabel.LL
                }
                prevLowPrice = item.price
                labeledSwingsChron.add(
                    SwingPoint(
                        closedBarIndex = item.bar.barIndex,
                        timeEpochSec = item.bar.timeEpochSec,
                        price = item.price,
                        label = lbl
                    )
                )
            }
        }

        // Urutkan dari yang paling dekat dengan Candle [1] (closedBarIndex terkecil = terbaru)
        val sortedSwingsNewestFirst = labeledSwingsChron.sortedBy { it.closedBarIndex }
        val lastHigh = sortedSwingsNewestFirst.firstOrNull { it.label.isHigh }
        val prevHighPoint = sortedSwingsNewestFirst.filter { it.label.isHigh }.getOrNull(1)
        val lastLow = sortedSwingsNewestFirst.firstOrNull { !it.label.isHigh }
        val prevLowPoint = sortedSwingsNewestFirst.filter { !it.label.isHigh }.getOrNull(1)

        val closedBar1 = bars.getOrNull(1) ?: bars.first()
        val closedBar2 = bars.getOrNull(2) ?: closedBar1
        val closedBar3 = bars.getOrNull(3) ?: closedBar2

        // Evaluasi posisi harga penutupan Candle [1] terhadap rentang grafik yang tampil di layar (34 candle terakhir)
        val visibleClosedSlice = bars.drop(1).take(34)
        val visibleOldestClose = visibleClosedSlice.lastOrNull()?.close ?: closedBar1.close
        val visibleHigh = visibleClosedSlice.maxOfOrNull { it.high } ?: closedBar1.high
        val visibleLow = visibleClosedSlice.minOfOrNull { it.low } ?: closedBar1.low
        val visibleMid = (visibleHigh + visibleLow) * 0.5
        val visibleNetChange = closedBar1.close - visibleOldestClose

        val baseClass = when {
            lastHigh?.label == SwingLabel.HH && lastLow?.label == SwingLabel.HL ->
                MarketStructureClass.BULLISH_STRUCTURE
            lastHigh?.label == SwingLabel.LH && lastLow?.label == SwingLabel.LL ->
                MarketStructureClass.BEARISH_STRUCTURE
            lastHigh != null && lastLow != null &&
                abs(lastHigh.price - (prevHighPoint?.price ?: lastHigh.price)) <= atr14 * 0.40 &&
                abs(lastLow.price - (prevLowPoint?.price ?: lastLow.price)) <= atr14 * 0.40 ->
                MarketStructureClass.RANGE
            // Jika struktur sedang di antara HH + LL atau LH + HL, baca dari kaki terbaru dan arah harga di grafik
            lastHigh != null && lastLow != null -> {
                val structMid = (lastHigh.price + lastLow.price) * 0.5
                val isRecentLegUp = lastLow.closedBarIndex > lastHigh.closedBarIndex && closedBar1.close >= structMid
                val isRecentLegDown = lastHigh.closedBarIndex > lastLow.closedBarIndex && closedBar1.close <= structMid
                when {
                    (lastHigh.label == SwingLabel.HH || lastLow.label == SwingLabel.HL) &&
                        (isRecentLegUp || (closedBar1.close > visibleMid && visibleNetChange > atr14 * 0.8)) ->
                        MarketStructureClass.BULLISH_STRUCTURE
                    (lastHigh.label == SwingLabel.LH || lastLow.label == SwingLabel.LL) &&
                        (isRecentLegDown || (closedBar1.close < visibleMid && visibleNetChange < -atr14 * 0.8)) ->
                        MarketStructureClass.BEARISH_STRUCTURE
                    else -> MarketStructureClass.TRANSITION
                }
            }
            visibleNetChange > atr14 * 1.2 -> MarketStructureClass.BULLISH_STRUCTURE
            visibleNetChange < -atr14 * 1.2 -> MarketStructureClass.BEARISH_STRUCTURE
            else -> MarketStructureClass.TRANSITION
        }

        // Deteksi BOS / CHOCH yang sudah sah tutup pada Candle [1..3]
        var breakEvent = StructureBreakEvent.NONE
        var brokenPrice: Double? = null
        val recentClosedMaxClose = maxOf(closedBar1.close, closedBar2.close, closedBar3.close)
        val recentClosedMinClose = minOf(closedBar1.close, closedBar2.close, closedBar3.close)

        if (lastHigh != null && recentClosedMaxClose > lastHigh.price && closedBar1.close >= lastHigh.price - atr14 * 0.15) {
            brokenPrice = lastHigh.price
            breakEvent = if (lastHigh.label == SwingLabel.LH) {
                StructureBreakEvent.CHOCH_BULLISH
            } else {
                StructureBreakEvent.BOS_BULLISH
            }
        } else if (lastLow != null && recentClosedMinClose < lastLow.price && closedBar1.close <= lastLow.price + atr14 * 0.15) {
            brokenPrice = lastLow.price
            breakEvent = if (lastLow.label == SwingLabel.HL) {
                StructureBreakEvent.CHOCH_BEARISH
            } else {
                StructureBreakEvent.BOS_BEARISH
            }
        }

        val finalClass = when (breakEvent) {
            StructureBreakEvent.BOS_BULLISH,
            StructureBreakEvent.CHOCH_BULLISH -> MarketStructureClass.BULLISH_STRUCTURE
            StructureBreakEvent.BOS_BEARISH,
            StructureBreakEvent.CHOCH_BEARISH -> MarketStructureClass.BEARISH_STRUCTURE
            StructureBreakEvent.NONE -> baseClass
        }

        val summaryText = buildString {
            append(finalClass.label)
            if (lastHigh != null && lastLow != null) {
                append(" • Puncak ${lastHigh.label.shortCode}: ${lastHigh.price} | Lembah ${lastLow.label.shortCode}: ${lastLow.price}")
            }
            if (breakEvent != StructureBreakEvent.NONE) {
                append(" • ${breakEvent.label}")
            }
        }

        return MarketStructureReport(
            classification = finalClass,
            latestBreakEvent = breakEvent,
            breakLevelPrice = brokenPrice,
            lastSwingHigh = lastHigh,
            lastSwingLow = lastLow,
            confirmedSwings = sortedSwingsNewestFirst.take(14),
            structureSummary = summaryText
        )
    }

    /**
     * 8. MESIN SUPPORT & RESISTANCE (CLUSTERED S/R ENGINE)
     */
    fun runSupportResistanceEngine(
        bars: List<Mt5RateBar>,
        confirmedSwings: List<SwingPoint>,
        atr14: Double
    ): SrEngineReport {
        val bar1 = bars.getOrNull(1) ?: bars.first()
        val currentClose = bar1.close
        val clusterTolerance = (atr14 * 0.55).coerceAtLeast(1e-5)
        val zoneHalfWidth = (atr14 * 0.22).coerceAtLeast(1e-5)

        data class MutableCluster(
            var sumPrice: Double,
            var count: Int,
            var minBarIndex: Int,
            var rejectionSum: Double
        )

        val clusters = mutableListOf<MutableCluster>()
        for (swing in confirmedSwings) {
            val swingBar = bars.getOrNull(swing.closedBarIndex)
            val wickRatio = if (swingBar != null) {
                if (swing.label.isHigh) swingBar.upperWick / swingBar.totalRange
                else swingBar.lowerWick / swingBar.totalRange
            } else 0.35

            val existing = clusters.firstOrNull {
                abs((it.sumPrice / it.count) - swing.price) <= clusterTolerance
            }
            if (existing != null) {
                existing.sumPrice += swing.price
                existing.count += 1
                existing.minBarIndex = minOf(existing.minBarIndex, swing.closedBarIndex)
                existing.rejectionSum += wickRatio
            } else {
                clusters.add(
                    MutableCluster(
                        sumPrice = swing.price,
                        count = 1,
                        minBarIndex = swing.closedBarIndex,
                        rejectionSum = wickRatio
                    )
                )
            }
        }

        val srZones = clusters.map { cluster ->
            val center = cluster.sumPrice / cluster.count
            val lower = center - zoneHalfWidth
            val upper = center + zoneHalfWidth
            val isSupport = center <= currentClose
            val distAtr = abs(currentClose - center) / atr14.coerceAtLeast(1e-6)
            val avgRejection = (cluster.rejectionSum / cluster.count).coerceIn(0.1, 1.0)

            val bar2 = bars.getOrNull(2) ?: bar1
            val interaction = when {
                !isSupport && bar1.high > upper && bar1.close < lower ->
                    SrInteractionType.FAILED_BREAKOUT
                isSupport && bar1.low < lower && bar1.close > upper ->
                    SrInteractionType.FAILED_BREAKOUT
                isSupport && bar1.low <= upper && bar1.close > center && bar1.lowerWick > bar1.bodySize * 0.6 ->
                    SrInteractionType.BULLISH_REJECTION
                !isSupport && bar1.high >= lower && bar1.close < center && bar1.upperWick > bar1.bodySize * 0.6 ->
                    SrInteractionType.BEARISH_REJECTION
                bar2.close <= upper && bar1.close > upper && bar1.isBullish ->
                    SrInteractionType.BULLISH_BREAKOUT
                bar2.close >= lower && bar1.close < lower && bar1.isBearish ->
                    SrInteractionType.BEARISH_BREAKOUT
                isSupport -> SrInteractionType.SUPPORT_HOLD
                else -> SrInteractionType.RESISTANCE_HOLD
            }

            val recencyBonus = (100.0 / (cluster.minBarIndex + 5)).coerceAtMost(15.0)
            val proximityBonus = (10.0 / (distAtr + 0.5)).coerceAtMost(20.0)
            val priority = (cluster.count * 8.0) + recencyBonus + (avgRejection * 12.0) + proximityBonus

            SrZone(
                centerPrice = center,
                lowerBound = lower,
                upperBound = upper,
                isSupport = isSupport,
                touchCount = cluster.count,
                lastTouchBarsAgo = cluster.minBarIndex,
                rejectionStrengthScore = avgRejection * 100.0,
                distanceInAtr = distAtr,
                interaction = interaction,
                priorityScore = priority
            )
        }.sortedByDescending { it.priorityScore }.take(6)

        val nearestSupport = srZones.filter { it.isSupport }.minByOrNull { it.distanceInAtr }
        val nearestResistance = srZones.filter { !it.isSupport }.minByOrNull { it.distanceInAtr }
        val activeInteraction = srZones.firstOrNull {
            it.interaction != SrInteractionType.SUPPORT_HOLD &&
                it.interaction != SrInteractionType.RESISTANCE_HOLD
        }?.interaction ?: if ((nearestSupport?.distanceInAtr ?: 99.0) < (nearestResistance?.distanceInAtr ?: 99.0)) {
            SrInteractionType.SUPPORT_HOLD
        } else {
            SrInteractionType.RESISTANCE_HOLD
        }

        val summary = buildString {
            append("${srZones.size} Area S/R Terklaster • ")
            if (nearestSupport != null) {
                append("Support: ${nearestSupport.centerPrice.formatPrice(bar1.close)} (${nearestSupport.touchCount}x sentuh) | ")
            }
            if (nearestResistance != null) {
                append("Resistance: ${nearestResistance.centerPrice.formatPrice(bar1.close)} (${nearestResistance.touchCount}x sentuh)")
            }
        }

        return SrEngineReport(
            zones = srZones,
            nearestSupport = nearestSupport,
            nearestResistance = nearestResistance,
            closedBar1Interaction = activeInteraction,
            summary = summary
        )
    }

    /**
     * 12. MESIN SMART MONEY CONCEPTS (FVG & ORDER BLOCKS)
     * Mendeteksi Fair Value Gap (ketidakseimbangan 3-candle) dan Order Blocks
     * secara murni pada candle yang sudah tutup (barIndex >= 1) non-repaint.
     */
    fun runSmartMoneyEngine(
        bars: List<Mt5RateBar>,
        atr14: Double
    ): com.example.model.SmartMoneyReport {
        if (bars.size < 10) {
            return com.example.model.SmartMoneyReport(
                activeFvgs = emptyList(),
                nearestBullishFvg = null,
                nearestBearishFvg = null,
                activeOrderBlocks = emptyList(),
                closedBar1InFvg = false,
                fvgConfluenceNote = "Data candle belum mencukupi untuk pemindaian FVG."
            )
        }

        val bar1 = bars[1]
        val minGapThreshold = atr14 * 0.15 // Filter gap mikro/noise
        val detectedFvgs = mutableListOf<com.example.model.FairValueGap>()
        val detectedObs = mutableListOf<com.example.model.OrderBlock>()

        // Periksa 35 candle tutup terbaru (barIndex 1 sampai 35)
        val limit = (bars.size - 3).coerceAtMost(35)
        for (i in 1..limit) {
            val candleCurrent = bars[i]
            val candleMiddle = bars[i + 1]
            val candlePrior = bars[i + 2]

            // Bullish FVG: Low candle i > High candle i+2 (candle middle naik impulsif)
            if (candleCurrent.low > candlePrior.high + minGapThreshold && candleMiddle.isBullish) {
                val top = candleCurrent.low
                val bottom = candlePrior.high
                val isMitigated = (1 until i).any { idx ->
                    bars[idx].low <= bottom
                }
                detectedFvgs.add(
                    com.example.model.FairValueGap(
                        barIndex = i,
                        type = com.example.model.FvgType.BULLISH_FVG,
                        topPrice = top,
                        bottomPrice = bottom,
                        isMitigated = isMitigated,
                        ageBars = i - 1
                    )
                )
            }

            // Bearish FVG: High candle i < Low candle i+2 (candle middle turun impulsif)
            if (candleCurrent.high < candlePrior.low - minGapThreshold && candleMiddle.isBearish) {
                val top = candlePrior.low
                val bottom = candleCurrent.high
                val isMitigated = (1 until i).any { idx ->
                    bars[idx].high >= top
                }
                detectedFvgs.add(
                    com.example.model.FairValueGap(
                        barIndex = i,
                        type = com.example.model.FvgType.BEARISH_FVG,
                        topPrice = top,
                        bottomPrice = bottom,
                        isMitigated = isMitigated,
                        ageBars = i - 1
                    )
                )
            }

            // Order Block Detection:
            // Bullish OB: candle bearish terakhir sebelum ledakan candle bullish
            if (candlePrior.isBearish && candleMiddle.isBullish && candleCurrent.isBullish &&
                candleCurrent.close > candlePrior.high && detectedObs.size < 3
            ) {
                detectedObs.add(
                    com.example.model.OrderBlock(
                        barIndex = i + 2,
                        isBullish = true,
                        topPrice = candlePrior.high,
                        bottomPrice = candlePrior.low,
                        label = "Bullish Order Block (Candle [${i + 2}])"
                    )
                )
            } else if (candlePrior.isBullish && candleMiddle.isBearish && candleCurrent.isBearish &&
                candleCurrent.close < candlePrior.low && detectedObs.size < 3
            ) {
                detectedObs.add(
                    com.example.model.OrderBlock(
                        barIndex = i + 2,
                        isBullish = false,
                        topPrice = candlePrior.high,
                        bottomPrice = candlePrior.low,
                        label = "Bearish Order Block (Candle [${i + 2}])"
                    )
                )
            }
        }

        val unmitigatedFvgs = detectedFvgs.filter { !it.isMitigated }
        val nearestBullFvg = unmitigatedFvgs.filter { it.type.isBullish }
            .minByOrNull { kotlin.math.abs(bar1.close - it.topPrice) }
        val nearestBearFvg = unmitigatedFvgs.filter { !it.type.isBullish }
            .minByOrNull { kotlin.math.abs(bar1.close - it.bottomPrice) }

        // Periksa apakah candle 1 sedang berada di dalam FVG aktif
        val bar1InFvg = unmitigatedFvgs.any { fvg ->
            bar1.close in fvg.bottomPrice..fvg.topPrice
        }

        val note = when {
            bar1InFvg && nearestBullFvg != null && bar1.close in nearestBullFvg.bottomPrice..nearestBullFvg.topPrice ->
                "Candle [1] Re-test di area Bullish FVG (${nearestBullFvg.bottomPrice.formatPrice(bar1.close)} – ${nearestBullFvg.topPrice.formatPrice(bar1.close)}). Area diskon Smart Money."
            bar1InFvg && nearestBearFvg != null && bar1.close in nearestBearFvg.bottomPrice..nearestBearFvg.topPrice ->
                "Candle [1] Re-test di area Bearish FVG (${nearestBearFvg.bottomPrice.formatPrice(bar1.close)} – ${nearestBearFvg.topPrice.formatPrice(bar1.close)}). Area premium Smart Money."
            nearestBullFvg != null ->
                "FVG Bullish belum termitigasi di ${nearestBullFvg.bottomPrice.formatPrice(bar1.close)} – ${nearestBullFvg.topPrice.formatPrice(bar1.close)} (${nearestBullFvg.ageBars} bar lalu)."
            nearestBearFvg != null ->
                "FVG Bearish belum termitigasi di ${nearestBearFvg.bottomPrice.formatPrice(bar1.close)} – ${nearestBearFvg.topPrice.formatPrice(bar1.close)} (${nearestBearFvg.ageBars} bar lalu)."
            else -> "Tidak ada Fair Value Gap (FVG) signifikan yang terbuka."
        }

        return com.example.model.SmartMoneyReport(
            activeFvgs = unmitigatedFvgs.take(5),
            nearestBullishFvg = nearestBullFvg,
            nearestBearishFvg = nearestBearFvg,
            activeOrderBlocks = detectedObs,
            closedBar1InFvg = bar1InFvg,
            fvgConfluenceNote = note
        )
    }

    private fun Double.format1(): String = String.format(java.util.Locale.US, "%.1f", this)
    private fun Double.formatPrice(ref: Double): String {
        val decimals = if (ref > 100.0) 2 else 5
        return String.format(java.util.Locale.US, "%.${decimals}f", this)
    }
}
