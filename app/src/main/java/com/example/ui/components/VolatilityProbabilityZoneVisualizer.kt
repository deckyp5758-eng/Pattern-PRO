package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CompleteHubAnalysis
import com.example.model.VolatilityBreakoutProbabilityReport
import com.example.ui.theme.BearishCrimson
import com.example.ui.theme.BullishEmerald
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.GoldBright
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.NeutralAmber
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateCardBorder
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

/**
 * Komponen Visualisasi Data Probabilitas Breakout Volatilitas Historis (Gaya Recharts Modern di Jetpack Compose).
 * Memetakan amplop volatilitas historis (ATR & simpangan baku) terhadap batas Support/Resistance
 * dan mengarsir 'Probability Zone' potensi tembusan harga secara visual.
 */
@Composable
fun VolatilityProbabilityZoneVisualizer(
    analysis: CompleteHubAnalysis,
    modifier: Modifier = Modifier
) {
    val report = analysis.breakoutProbability
    val digits = analysis.symbol.digits
    val textMeasurer = rememberTextMeasurer()

    Card(
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, SlateCardBorder, RoundedCornerShape(16.dp))
            .testTag("volatility_probability_zone_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ZONA PROBABILITAS BREAKOUT VOLATILITAS",
                        style = MaterialTheme.typography.titleMedium,
                        color = GoldBright,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Pemetaan Volatilitas Historis (ATR) ke Level Support & Resistance",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
                Surface(
                    color = CyanSecondary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "PROBABILITY ZONE",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanSecondary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(color = SlateCardBorder)

            // 3 Kartu Statistik Probabilitas (Gaya Recharts Summary Badge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProbabilityBadge(
                    title = "BREAKOUT NAIK (R1)",
                    pctValue = report.bullishBreakoutProbPct,
                    targetPrice = formatPrice(report.resistancePrice, digits),
                    accentColor = GoldBright,
                    modifier = Modifier.weight(1f)
                )
                ProbabilityBadge(
                    title = "RANGE-BOUND",
                    pctValue = report.rangeBoundHoldProbPct,
                    targetPrice = "Bertahan di S/R",
                    accentColor = BullishEmerald,
                    modifier = Modifier.weight(1f)
                )
                ProbabilityBadge(
                    title = "BREAKDOWN TURUN (S1)",
                    pctValue = report.bearishBreakdownProbPct,
                    targetPrice = formatPrice(report.supportPrice, digits),
                    accentColor = BearishCrimson,
                    modifier = Modifier.weight(1f)
                )
            }

            // Canvas Grafik Area & Line Volatilitas Historis ke Level S/R
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ObsidianBg)
                    .border(1.dp, SlateCardBorder, RoundedCornerShape(12.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    val points = report.recentVolatilityPoints
                    if (points.size < 2) return@Canvas

                    val r1 = report.resistancePrice
                    val s1 = report.supportPrice
                    val currentPrice = report.currentPrice
                    val atr = report.historicalAtr

                    // Tentukan rentang sumbu Y dengan buffer atas dan bawah
                    val minY = minOf(s1 - atr * 1.0, points.minOf { it.lowerVolBand }, currentPrice - atr * 1.5)
                    val maxY = maxOf(r1 + atr * 1.0, points.maxOf { it.upperVolBand }, currentPrice + atr * 1.5)
                    val priceRange = (maxY - minY).coerceAtLeast(1e-4)

                    fun priceToY(price: Double): Float {
                        val normalized = (price - minY) / priceRange
                        return (h - (normalized * h)).toFloat().coerceIn(0f, h)
                    }

                    fun indexToX(index: Int): Float {
                        val paddingStart = 40f
                        val paddingEnd = 60f
                        val usableW = w - paddingStart - paddingEnd
                        return paddingStart + (index.toFloat() / (points.size - 1)) * usableW
                    }

                    val yR1 = priceToY(r1)
                    val yS1 = priceToY(s1)
                    val yCurrent = priceToY(currentPrice)

                    // 1. Gambar Arsiran Zona Breakout Naik (Di atas R1)
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(
                                GoldPrimary.copy(alpha = 0.20f),
                                GoldPrimary.copy(alpha = 0.05f)
                            ),
                            startY = 0f,
                            endY = yR1
                        ),
                        topLeft = Offset(0f, 0f),
                        size = Size(w, yR1)
                    )

                    // 2. Gambar Arsiran Zona Breakdown Turun (Di bawah S1)
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(
                                BearishCrimson.copy(alpha = 0.05f),
                                BearishCrimson.copy(alpha = 0.20f)
                            ),
                            startY = yS1,
                            endY = h
                        ),
                        topLeft = Offset(0f, yS1),
                        size = Size(w, h - yS1)
                    )

                    // 3. Gambar Arsiran Zona Bertahan S/R (Antara S1 dan R1)
                    drawRect(
                        color = SlateSurfaceElevated.copy(alpha = 0.35f),
                        topLeft = Offset(0f, yR1),
                        size = Size(w, (yS1 - yR1).coerceAtLeast(0f))
                    )

                    // 4. Garis Batas Resistance R1 (Dotted Gold / Red)
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                    drawLine(
                        color = GoldBright.copy(alpha = 0.85f),
                        start = Offset(0f, yR1),
                        end = Offset(w, yR1),
                        strokeWidth = 2f,
                        pathEffect = dashEffect
                    )

                    // 5. Garis Batas Support S1 (Dotted Green)
                    drawLine(
                        color = BullishEmerald.copy(alpha = 0.85f),
                        start = Offset(0f, yS1),
                        end = Offset(w, yS1),
                        strokeWidth = 2f,
                        pathEffect = dashEffect
                    )

                    // 6. Amplop Area Volatilitas Historis (Gaya Recharts Area Chart)
                    val upperPath = Path()
                    val lowerPath = Path()
                    val areaPath = Path()

                    points.forEachIndexed { i, pt ->
                        val x = indexToX(i)
                        val yUp = priceToY(pt.upperVolBand)
                        val yLow = priceToY(pt.lowerVolBand)

                        if (i == 0) {
                            upperPath.moveTo(x, yUp)
                            areaPath.moveTo(x, yUp)
                        } else {
                            upperPath.lineTo(x, yUp)
                            areaPath.lineTo(x, yUp)
                        }
                    }

                    // Sambungkan area bawah secara berbalik
                    for (i in points.indices.reversed()) {
                        val x = indexToX(i)
                        val yLow = priceToY(points[i].lowerVolBand)
                        areaPath.lineTo(x, yLow)
                    }
                    areaPath.close()

                    // Gambar arsiran amplop volatilitas historis
                    drawPath(
                        path = areaPath,
                        brush = Brush.verticalGradient(
                            listOf(
                                CyanSecondary.copy(alpha = 0.22f),
                                CyanSecondary.copy(alpha = 0.08f)
                            )
                        )
                    )

                    // 7. Garis Harga Historis (Historical Close Line)
                    val linePath = Path()
                    points.forEachIndexed { i, pt ->
                        val x = indexToX(i)
                        val yClose = priceToY(pt.closePrice)
                        if (i == 0) linePath.moveTo(x, yClose) else linePath.lineTo(x, yClose)
                    }
                    drawPath(
                        path = linePath,
                        color = TextPrimary.copy(alpha = 0.9f),
                        style = Stroke(width = 2.5f)
                    )

                    // 8. Titik Harga Saat Ini (Current Price Marker)
                    val lastX = indexToX(points.lastIndex)
                    drawCircle(
                        color = CyanSecondary,
                        radius = 5f,
                        center = Offset(lastX, yCurrent)
                    )
                    drawCircle(
                        color = CyanSecondary.copy(alpha = 0.4f),
                        radius = 9f,
                        center = Offset(lastX, yCurrent)
                    )

                    // Garis horizontal tipis harga saat ini
                    drawLine(
                        color = CyanSecondary.copy(alpha = 0.5f),
                        start = Offset(lastX, yCurrent),
                        end = Offset(w, yCurrent),
                        strokeWidth = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )

                    // Teks Label Level S/R dan Harga
                    val fontStyle = TextStyle(
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    // Label R1
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "R1: ${formatPrice(r1, digits)}",
                        topLeft = Offset(w - 75f, (yR1 - 18f).coerceAtLeast(4f)),
                        style = fontStyle.copy(color = GoldBright)
                    )

                    // Label S1
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "S1: ${formatPrice(s1, digits)}",
                        topLeft = Offset(w - 75f, (yS1 + 4f).coerceAtMost(h - 18f)),
                        style = fontStyle.copy(color = BullishEmerald)
                    )

                    // Label Harga Saat Ini
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "KINI: ${formatPrice(currentPrice, digits)}",
                        topLeft = Offset(w - 85f, (yCurrent - 7f).coerceIn(4f, h - 18f)),
                        style = fontStyle.copy(color = CyanSecondary)
                    )

                    // Label Probability Zone Watermark
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "▲ ZONA BREAKOUT (${report.bullishBreakoutProbPct}%)",
                        topLeft = Offset(14f, 8f),
                        style = fontStyle.copy(color = GoldBright.copy(alpha = 0.8f))
                    )
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "▼ ZONA BREAKDOWN (${report.bearishBreakdownProbPct}%)",
                        topLeft = Offset(14f, h - 18f),
                        style = fontStyle.copy(color = BearishCrimson.copy(alpha = 0.8f))
                    )
                }
            }

            // Catatan Penjelasan Probabilitas & Jarak ATR
            Surface(
                color = SlateSurfaceElevated,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateCardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "💡 Analisis Probabilitas Volatilitas:",
                        style = MaterialTheme.typography.labelSmall,
                        color = GoldBright,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "• Potensi Breakout: ${report.primaryBreakoutRisk}.\n" +
                            "• Jarak Harga ke Resistance (R1): ${String.format(Locale.US, "%.2f", report.distanceToResistanceAtr)}× ATR (${formatPrice(report.resistancePrice - report.currentPrice, digits)} pt).\n" +
                            "• Jarak Harga ke Support (S1): ${String.format(Locale.US, "%.2f", report.distanceToSupportAtr)}× ATR (${formatPrice(report.currentPrice - report.supportPrice, digits)} pt).\n" +
                            "• Volatilitas Historis (ATR 14): ${formatPrice(report.historicalAtr, digits)} pt.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ProbabilityBadge(
    title: String,
    pctValue: Int,
    targetPrice: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SlateSurfaceElevated,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = "$pctValue%",
                style = MaterialTheme.typography.titleMedium,
                color = accentColor,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = targetPrice,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontSize = 9.sp,
                maxLines = 1
            )
        }
    }
}

private fun formatPrice(value: Double, digits: Int): String =
    String.format(Locale.US, "%.${digits}f", value)
