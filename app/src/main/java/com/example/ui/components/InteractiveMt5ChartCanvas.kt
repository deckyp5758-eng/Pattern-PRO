package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CompleteHubAnalysis
import com.example.ui.theme.BearishCrimson
import com.example.ui.theme.BullishEmerald
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.GoldBright
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.NeutralAmber
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateCardBorder
import com.example.ui.theme.SlateSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun InteractiveMt5ChartCanvas(
    analysis: CompleteHubAnalysis,
    inspectedBarIndex: Int,
    showEmaOverlays: Boolean,
    showSrZones: Boolean,
    showStructureSwings: Boolean,
    onBarSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    var zoomCandleCount by remember { mutableIntStateOf(36) }
    val visibleCount = zoomCandleCount.coerceAtMost(analysis.bars.size).coerceAtLeast(10)

    // Standar MT5 Web:
    // analysis.bars[0] = Candle [0] terbaru (sedang berjalan)
    // analysis.bars[1] = Candle [1] tutup terakhir
    // analysis.bars[visibleCount - 1] = Candle paling lama di sisi KIRI layar
    // Maka visibleBars (kiri-ke-kanan di layar): index 0 = paling KIRI (lama) -> index terakhir = paling KANAN (Candle [0])
    val visibleBars = analysis.bars.take(visibleCount).asReversed()

    val ema20Visible = analysis.trendEngine.ema20Series.take(visibleCount).asReversed()
    val ema50Visible = analysis.trendEngine.ema50Series.take(visibleCount).asReversed()
    val ema200Visible = analysis.trendEngine.ema200Series.take(visibleCount).asReversed()

    val oldestBar = visibleBars.first()
    val newestBar0 = visibleBars.last()
    val closedBar1 = visibleBars.getOrElse(visibleBars.size - 2) { newestBar0 }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianBg)
            .border(1.dp, SlateCardBorder, RoundedCornerShape(16.dp))
            .padding(10.dp)
    ) {
        // Baris Kontrol Orientasi MT5 Web & Zoom Candle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LegendDot(color = GoldPrimary, label = "EMA20")
                LegendDot(color = CyanSecondary, label = "EMA50")
                LegendDot(color = BearishCrimson, label = "EMA200")
                LegendDot(color = BullishEmerald, label = "[1] Tutup")
                LegendDot(color = NeutralAmber, label = "[0] Live")
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(24, 36, 54).forEach { countOption ->
                    val isSelected = zoomCandleCount == countOption
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) CyanSecondary else SlateSurfaceElevated)
                            .clickable { zoomCandleCount = countOption }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${countOption}c",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) ObsidianBg else TextSecondary
                        )
                    }
                }
            }
        }

        // Petunjuk Arah Kiri-ke-Kanan Standar MT5 Web
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "◄ KIRI: Historis [${oldestBar.barIndex}] (${formatUtcTimeShort(oldestBar.timeEpochSec)})",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Text(
                text = "KANAN: [1] Tutup & [0] Live (${formatUtcTimeShort(newestBar0.timeEpochSec)}) ►",
                style = MaterialTheme.typography.labelSmall,
                color = GoldBright
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(315.dp)
                .testTag("mt5_interactive_chart_canvas")
                .pointerInput(visibleCount, visibleBars) {
                    detectTapGestures { offset ->
                        val chartWidth = size.width.toFloat()
                        val rightAxisSpace = 64f * density
                        val plotWidth = (chartWidth - rightAxisSpace).coerceAtLeast(1f)
                        val slotWidth = plotWidth / visibleCount.toFloat()
                        val tappedSlot = (offset.x / slotWidth).toInt().coerceIn(0, visibleCount - 1)
                        val selectedBar = visibleBars[tappedSlot]
                        onBarSelected(selectedBar.barIndex)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val rightAxisWidth = 62.dp.toPx()
                val plotW = (w - rightAxisWidth).coerceAtLeast(10f)
                val priceChartTop = 8.dp.toPx()
                val priceChartBottom = h * 0.73f
                val priceChartH = (priceChartBottom - priceChartTop).coerceAtLeast(20f)
                val volChartTop = h * 0.76f
                val volChartH = h * 0.14f
                val timeAxisTop = h * 0.91f

                // Standar MT5 Web: Skala vertikal (Y-Axis) mengikuti High & Low dari candle yang tampil di layar
                // sehingga candle tidak gepeng atau terdistorsi oleh EMA 200 yang jauh.
                val minPrice = visibleBars.minOf { it.low }
                val maxPrice = visibleBars.maxOf { it.high }
                val pad = ((maxPrice - minPrice) * 0.10).coerceAtLeast(analysis.symbol.pointSize * 10)
                val lowBound = minPrice - pad
                val highBound = maxPrice + pad
                val priceSpan = (highBound - lowBound).coerceAtLeast(1e-8)

                // Konversi harga ke koordinat Y:
                // Harga Tinggi (highBound) -> di ATAS (priceChartTop)
                // Harga Rendah (lowBound)  -> di BAWAH (priceChartBottom)
                fun priceToYUnclamped(p: Double): Float {
                    val norm = ((highBound - p) / priceSpan).toFloat()
                    return priceChartTop + norm * priceChartH
                }

                fun priceToYClamped(p: Double): Float {
                    return priceToYUnclamped(p).coerceIn(priceChartTop, priceChartBottom)
                }

                val slotW = plotW / visibleCount.toFloat()

                // 1. Garis Grid Horizontal & Vertikal + Label Harga Sumbu Kanan (Gaya MT5 Web)
                val gridSteps = 4
                for (g in 0..gridSteps) {
                    val frac = g.toFloat() / gridSteps
                    val y = priceChartTop + frac * priceChartH
                    val levelPrice = highBound - (frac * priceSpan)
                    drawLine(
                        color = Color(0xFF162238),
                        start = Offset(0f, y),
                        end = Offset(plotW, y),
                        strokeWidth = 1f
                    )
                    val formatted = String.format(
                        Locale.US,
                        "%.${analysis.symbol.digits}f",
                        levelPrice
                    )
                    drawText(
                        textMeasurer = textMeasurer,
                        text = formatted,
                        topLeft = Offset(plotW + 4.dp.toPx(), (y - 6.dp.toPx()).coerceAtLeast(0f)),
                        style = TextStyle(
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 9.sp,
                            color = Color(0xFF7C8DA6)
                        )
                    )
                }

                // Garis Grid Waktu Vertikal + Label Sumbu Waktu (X-Axis) dari Kiri (Lama) ke Kanan (Baru)
                val timeStepIndices = listOf(
                    0,
                    visibleCount / 3,
                    (visibleCount * 2) / 3,
                    (visibleCount - 2).coerceAtLeast(0)
                ).distinct()

                for (slotIdx in timeStepIndices) {
                    val bar = visibleBars.getOrNull(slotIdx) ?: continue
                    val x = (slotIdx + 0.5f) * slotW
                    drawLine(
                        color = Color(0xFF141E30),
                        start = Offset(x, priceChartTop),
                        end = Offset(x, volChartTop + volChartH),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f), 0f)
                    )
                    val timeLabel = if (bar.barIndex == 1) {
                        "${formatUtcTimeShort(bar.timeEpochSec)}[1]"
                    } else {
                        formatUtcTimeShort(bar.timeEpochSec)
                    }
                    drawText(
                        textMeasurer = textMeasurer,
                        text = timeLabel,
                        topLeft = Offset((x - 18.dp.toPx()).coerceIn(0f, plotW - 46.dp.toPx()), timeAxisTop),
                        style = TextStyle(
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 8.sp,
                            color = if (bar.barIndex == 1) BullishEmerald else Color(0xFF7C8DA6)
                        )
                    )
                }

                // 2. Zona Support & Resistance (Dipotong rapi di dalam area grafik harga)
                if (showSrZones) {
                    clipRect(left = 0f, top = priceChartTop, right = plotW, bottom = priceChartBottom) {
                        for (zone in analysis.srEngine.zones.take(4)) {
                            if (zone.upperBound < lowBound || zone.lowerBound > highBound) continue
                            val topY = priceToYClamped(zone.upperBound)
                            val botY = priceToYClamped(zone.lowerBound)
                            val zoneH = (botY - topY).coerceAtLeast(4.dp.toPx())
                            val zoneColor = if (zone.isSupport) {
                                BullishEmerald.copy(alpha = 0.13f)
                            } else {
                                BearishCrimson.copy(alpha = 0.13f)
                            }
                            val strokeColor = if (zone.isSupport) {
                                BullishEmerald.copy(alpha = 0.45f)
                            } else {
                                BearishCrimson.copy(alpha = 0.45f)
                            }
                            drawRect(
                                color = zoneColor,
                                topLeft = Offset(0f, topY),
                                size = Size(plotW, zoneH)
                            )
                            drawLine(
                                color = strokeColor,
                                start = Offset(0f, priceToYUnclamped(zone.centerPrice)),
                                end = Offset(plotW, priceToYUnclamped(zone.centerPrice)),
                                strokeWidth = 1.2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                            )
                        }
                    }
                }

                // 3. Kolom Sorotan untuk Candle Tutup [1] (Nomor 2 dari Kanan) & Candle yang Dipilih
                visibleBars.forEachIndexed { idx, bar ->
                    val centerX = (idx + 0.5f) * slotW
                    if (bar.barIndex == 1) {
                        drawRect(
                            color = BullishEmerald.copy(alpha = 0.09f),
                            topLeft = Offset(idx * slotW, priceChartTop),
                            size = Size(slotW, volChartTop + volChartH - priceChartTop)
                        )
                    }
                    if (bar.barIndex == inspectedBarIndex) {
                        drawLine(
                            color = CyanSecondary.copy(alpha = 0.75f),
                            start = Offset(centerX, priceChartTop),
                            end = Offset(centerX, volChartTop + volChartH),
                            strokeWidth = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )
                    }
                }

                // 4. Garis EMA 20, EMA 50, EMA 200 (Menggunakan priceToYUnclamped + clipRect seperti MT5 Web)
                if (showEmaOverlays) {
                    clipRect(left = 0f, top = priceChartTop, right = plotW, bottom = priceChartBottom) {
                        fun drawEmaPath(series: List<Double>, color: Color, dashed: Boolean = false) {
                            if (series.size < 2) return
                            val path = Path()
                            var started = false
                            series.forEachIndexed { idx, value ->
                                val x = (idx + 0.5f) * slotW
                                val y = priceToYUnclamped(value)
                                if (!started) {
                                    path.moveTo(x, y)
                                    started = true
                                } else {
                                    path.lineTo(x, y)
                                }
                            }
                            drawPath(
                                path = path,
                                color = color,
                                style = Stroke(
                                    width = 2.dp.toPx(),
                                    pathEffect = if (dashed) {
                                        PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                                    } else null
                                )
                            )
                        }
                        drawEmaPath(ema200Visible, BearishCrimson.copy(alpha = 0.85f), dashed = true)
                        drawEmaPath(ema50Visible, CyanSecondary.copy(alpha = 0.9f))
                        drawEmaPath(ema20Visible, GoldPrimary)
                    }
                }

                // 5. Candlestick (Kiri = Lama -> Kanan = Candle [1] Tutup -> Paling Kanan = Candle [0] Berjalan)
                val candleBodyW = (slotW * 0.66f).coerceAtLeast(3f)
                visibleBars.forEachIndexed { idx, bar ->
                    val centerX = (idx + 0.5f) * slotW
                    val highY = priceToYClamped(bar.high)
                    val lowY = priceToYClamped(bar.low)
                    val openY = priceToYClamped(bar.open)
                    val closeY = priceToYClamped(bar.close)
                    val bodyTop = minOf(openY, closeY)
                    val bodyH = kotlin.math.abs(closeY - openY).coerceAtLeast(2.dp.toPx())

                    val candleColor = when {
                        bar.barIndex == 0 -> NeutralAmber
                        bar.isBullish -> BullishEmerald
                        else -> BearishCrimson
                    }

                    // Ekor Atas & Ekor Bawah (High ke Low)
                    drawLine(
                        color = candleColor,
                        start = Offset(centerX, highY),
                        end = Offset(centerX, lowY),
                        strokeWidth = 1.4.dp.toPx()
                    )

                    if (bar.barIndex == 0) {
                        // Candle [0] (Paling Kanan) digambar berongga (hollow) menandakan sedang berjalan / belum close
                        drawRect(
                            color = ObsidianBg,
                            topLeft = Offset(centerX - candleBodyW / 2f, bodyTop),
                            size = Size(candleBodyW, bodyH)
                        )
                        drawRect(
                            color = NeutralAmber,
                            topLeft = Offset(centerX - candleBodyW / 2f, bodyTop),
                            size = Size(candleBodyW, bodyH),
                            style = Stroke(width = 1.6.dp.toPx())
                        )
                    } else {
                        // Candle [1..N] (Sudah Tutup) digambar penuh (solid)
                        drawRect(
                            color = candleColor,
                            topLeft = Offset(centerX - candleBodyW / 2f, bodyTop),
                            size = Size(candleBodyW, bodyH)
                        )
                    }
                }

                // 6. Garis Harga Penutupan Candle [1] & Harga Live [0] di Sumbu Kanan (Persis MT5 Web)
                val livePriceY = priceToYClamped(newestBar0.close)
                val liveLineColor = if (newestBar0.isBullish) BullishEmerald else BearishCrimson
                drawLine(
                    color = liveLineColor.copy(alpha = 0.85f),
                    start = Offset(0f, livePriceY),
                    end = Offset(plotW, livePriceY),
                    strokeWidth = 1.2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f), 0f)
                )
                drawRoundRect(
                    color = liveLineColor,
                    topLeft = Offset(plotW + 2.dp.toPx(), (livePriceY - 8.dp.toPx()).coerceIn(0f, priceChartBottom - 14.dp.toPx())),
                    size = Size(rightAxisWidth - 4.dp.toPx(), 16.dp.toPx()),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
                val livePriceFormatted = String.format(
                    Locale.US,
                    "%.${analysis.symbol.digits}f",
                    newestBar0.close
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = livePriceFormatted,
                    topLeft = Offset(plotW + 5.dp.toPx(), (livePriceY - 6.dp.toPx()).coerceIn(1f, priceChartBottom - 13.dp.toPx())),
                    style = TextStyle(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.5.sp,
                        color = ObsidianBg
                    )
                )

                // 7. Label Titik Swing Struktur Pasar (HH, HL, LH, LL)
                if (showStructureSwings) {
                    val swingByIndex = analysis.structureEngine.confirmedSwings.associateBy { it.closedBarIndex }
                    visibleBars.forEachIndexed { idx, bar ->
                        val swing = swingByIndex[bar.barIndex] ?: return@forEachIndexed
                        val x = (idx + 0.5f) * slotW
                        val y = if (swing.label.isHigh) {
                            (priceToYClamped(swing.price) - 13.dp.toPx()).coerceAtLeast(1f)
                        } else {
                            (priceToYClamped(swing.price) + 2.dp.toPx()).coerceAtMost(priceChartBottom - 11.dp.toPx())
                        }
                        val lblColor = if (swing.label.isHigh) GoldPrimary else CyanSecondary
                        drawText(
                            textMeasurer = textMeasurer,
                            text = swing.label.shortCode,
                            topLeft = Offset((x - 8.dp.toPx()).coerceAtLeast(0f), y),
                            style = TextStyle(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                color = lblColor
                            )
                        )
                    }
                }

                // 8. Sub-Grafik Volume & Garis Rata-rata SMA 20
                val maxVol = visibleBars.maxOf { it.tickVolume }.coerceAtLeast(1L).toFloat()
                val smaVol = analysis.volumeEngine.smaVolume20.toFloat()

                drawLine(
                    color = SlateCardBorder,
                    start = Offset(0f, volChartTop - 2.dp.toPx()),
                    end = Offset(plotW, volChartTop - 2.dp.toPx()),
                    strokeWidth = 1f
                )

                visibleBars.forEachIndexed { idx, bar ->
                    val centerX = (idx + 0.5f) * slotW
                    val barRatio = (bar.tickVolume.toFloat() / maxVol).coerceIn(0.05f, 1f)
                    val barH = barRatio * volChartH
                    val volColor = when {
                        bar.barIndex == 0 -> NeutralAmber.copy(alpha = 0.55f)
                        bar.tickVolume > smaVol -> GoldPrimary.copy(alpha = 0.85f)
                        bar.isBullish -> BullishEmerald.copy(alpha = 0.55f)
                        else -> BearishCrimson.copy(alpha = 0.55f)
                    }
                    drawRect(
                        color = volColor,
                        topLeft = Offset(centerX - candleBodyW / 2f, volChartTop + (volChartH - barH)),
                        size = Size(candleBodyW, barH)
                    )
                }

                val smaY = volChartTop + volChartH * (1f - (smaVol / maxVol).coerceIn(0f, 1f))
                drawLine(
                    color = CyanSecondary.copy(alpha = 0.8f),
                    start = Offset(0f, smaY),
                    end = Offset(plotW, smaY),
                    strokeWidth = 1.2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                )
            }
        }
    }
}

private fun formatUtcTimeShort(epochSec: Long): String {
    val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date(epochSec * 1000L))
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .height(8.dp)
                .fillMaxWidth(0.022f)
                .background(color, RoundedCornerShape(2.dp))
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}
