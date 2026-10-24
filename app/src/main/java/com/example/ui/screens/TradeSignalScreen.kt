package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AnalyticalVerdict
import com.example.model.CompleteHubAnalysis
import com.example.model.OnlineDataProvider
import com.example.ui.theme.BearishBg
import com.example.ui.theme.BearishCrimson
import com.example.ui.theme.BullishBg
import com.example.ui.theme.BullishEmerald
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.GoldBright
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NeutralAmber
import com.example.ui.theme.NeutralBg
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateCardBorder
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.abs

@Composable
fun TradeSignalScreen(
    analysis: CompleteHubAnalysis,
    selectedProvider: OnlineDataProvider,
    modifier: Modifier = Modifier
) {
    val symbol = analysis.symbol
    val digits = symbol.digits
    val vEngine = analysis.volatilityEngine
    val bar1 = analysis.dataEngine.closedCandle1
    val entryPrice = bar1.close

    // State Kalkulator Risiko
    var balanceInput by remember { mutableStateOf("1000") }
    var selectedRiskPct by remember { mutableStateOf(1.0) } // 1%

    val balanceNum = balanceInput.toDoubleOrNull() ?: 1000.0
    val riskAmountDollars = balanceNum * (selectedRiskPct / 100.0)

    val isBuy = analysis.verdict in listOf(
        AnalyticalVerdict.STRONG_BULLISH_CONFLUENCE,
        AnalyticalVerdict.MODERATE_BULLISH_BIAS
    )
    val isSell = analysis.verdict in listOf(
        AnalyticalVerdict.STRONG_BEARISH_CONFLUENCE,
        AnalyticalVerdict.MODERATE_BEARISH_BIAS
    )

    val slPrice = if (isBuy) vEngine.longStopReferencePrice else if (isSell) vEngine.shortStopReferencePrice else entryPrice - vEngine.analyticalStopDistance
    val tp1Price = if (isBuy) vEngine.longTarget1RPrice else if (isSell) vEngine.shortTarget1RPrice else entryPrice + (vEngine.analyticalStopDistance * 1.5)
    val tp2Price = if (isBuy) vEngine.longTarget2RPrice else if (isSell) vEngine.shortTarget2RPrice else entryPrice + (vEngine.analyticalStopDistance * 2.5)

    val slDistancePrice = abs(entryPrice - slPrice)
    val slDistancePoints = slDistancePrice / symbol.pointSize

    // Kalkulasi Rekomendasi Lot Sesuai Ukuran Kontrak Standar Forex/Emas
    // XAUUSD: 1 lot = 100 oz (1 poin = $1), EURUSD: 1 lot = 100,000 unit (1 pip = $10)
    val pipValuePerLot = if (symbol == com.example.model.MarketSymbol.XAUUSD) 1.0 else 10.0
    val pointDivisor = if (symbol == com.example.model.MarketSymbol.XAUUSD) 1.0 else 10.0
    val pipsAtRisk = (slDistancePoints / pointDivisor).coerceAtLeast(1.0)
    val calculatedLotSize = ((riskAmountDollars / (pipsAtRisk * pipValuePerLot))).coerceIn(0.01, 100.0)
    val lotFormatted = String.format(Locale.US, "%.2f", calculatedLotSize)

    val estProfitTp1 = riskAmountDollars * 1.5
    val estProfitTp2 = riskAmountDollars * 2.5

    val signalBadgeColor = when {
        isBuy -> BullishEmerald
        isSell -> BearishCrimson
        else -> NeutralAmber
    }
    val signalActionTitle = when {
        analysis.verdict == AnalyticalVerdict.STRONG_BULLISH_CONFLUENCE -> "BUY SEKARANG (KONFLUENSI KUAT)"
        analysis.verdict == AnalyticalVerdict.MODERATE_BULLISH_BIAS -> "BUY (BIAS NAIK TERKONFIRMASI)"
        analysis.verdict == AnalyticalVerdict.STRONG_BEARISH_CONFLUENCE -> "SELL SEKARANG (KONFLUENSI KUAT)"
        analysis.verdict == AnalyticalVerdict.MODERATE_BEARISH_BIAS -> "SELL (BIAS TURUN TERKONFIRMASI)"
        analysis.verdict == AnalyticalVerdict.HTF_BULLISH_LTF_PULLBACK -> "TUNGGU (TF KECIL SEDANG PULLBACK NAIK)"
        analysis.verdict == AnalyticalVerdict.HTF_BEARISH_LTF_PULLBACK -> "TUNGGU (TF KECIL SEDANG PULLBACK TURUN)"
        else -> "STANDBY / WAIT (PASAR SIDEWAYS)"
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .testTag("trade_signal_list"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // 1. HEADER SINKRONISASI FEED & KEJURUAN DATA PROVIDER
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, GoldPrimary, RoundedCornerShape(16.dp))
                    .testTag("trade_provider_sync_header")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = null,
                                tint = GoldBright,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "PUSAT EKSEKUSI TRADE & ALARM",
                                style = MaterialTheme.typography.titleMedium,
                                color = GoldBright,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            color = BullishEmerald.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.border(1.dp, BullishEmerald.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        ) {
                            Text(
                                text = "TERHUBUNG DENGAN GRAFIK",
                                style = MaterialTheme.typography.labelSmall,
                                color = BullishEmerald,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    HorizontalDivider(color = SlateCardBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Sumber Feed Terpilih:",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            Text(
                                text = "${selectedProvider.displayName} (${symbol.code})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CyanSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Timeframe Analisis:",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            Text(
                                text = "${analysis.activeTimeframe.label} (Candle Tutup [1])",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "🔒 Garansi Isolasi Mesin: Sinyal, Stop Loss, dan Target di bawah ini dihitung MURNI 100% dari feed ${selectedProvider.shortLabel} tanpa tercampur provider lain untuk mencegah salah entri/loss.",
                        style = MaterialTheme.typography.labelSmall,
                        color = GoldBright
                    )
                }
            }
        }

        // 2. KARTU KEPUTUSAN SINYAL UTAMA (ACTIONABLE DECISION CARD)
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        isBuy -> BullishBg
                        isSell -> BearishBg
                        else -> NeutralBg
                    }
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.5.dp,
                        color = signalBadgeColor,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .testTag("trade_verdict_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "STATUS KEPUTUSAN SINYAL:",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        Surface(
                            color = signalBadgeColor.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.border(1.dp, signalBadgeColor, RoundedCornerShape(8.dp))
                        ) {
                            Text(
                                text = if (isBuy) "SINYAL BUY" else if (isSell) "SINYAL SELL" else "STANDBY",
                                style = MaterialTheme.typography.labelSmall,
                                color = signalBadgeColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = signalActionTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = signalBadgeColor,
                        fontWeight = FontWeight.Bold
                    )

                    // Konfirmasi Arah dari Matriks Multi-Timeframe (H1/M30/M15/M5/M1)
                    val mtfBiasText = when (analysis.mtfEngine.htfBias) {
                        com.example.model.PatternBias.BULLISH -> "Bias Matriks MTF: BULLISH SELARAS (Naik)"
                        com.example.model.PatternBias.BEARISH -> "Bias Matriks MTF: BEARISH SELARAS (Turun)"
                        else -> "Bias Matriks MTF: NEUTRAL / SIDEWAYS"
                    }
                    val mtfBadgeColor = when (analysis.mtfEngine.htfBias) {
                        com.example.model.PatternBias.BULLISH -> BullishEmerald
                        com.example.model.PatternBias.BEARISH -> BearishCrimson
                        else -> GoldBright
                    }
                    Surface(
                        color = mtfBadgeColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.border(1.dp, mtfBadgeColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = mtfBadgeColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = mtfBiasText,
                                style = MaterialTheme.typography.labelSmall,
                                color = mtfBadgeColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Skor Konfluensi Naik vs Turun
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Skor Bullish", style = MaterialTheme.typography.labelSmall, color = BullishEmerald)
                                Text("${analysis.bullishConfluenceScore}%", style = MaterialTheme.typography.labelSmall, color = BullishEmerald, fontWeight = FontWeight.Bold)
                            }
                            LinearProgressIndicator(
                                progress = { (analysis.bullishConfluenceScore / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = BullishEmerald,
                                trackColor = SlateCardBorder
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Skor Bearish", style = MaterialTheme.typography.labelSmall, color = BearishCrimson)
                                Text("${analysis.bearishConfluenceScore}%", style = MaterialTheme.typography.labelSmall, color = BearishCrimson, fontWeight = FontWeight.Bold)
                            }
                            LinearProgressIndicator(
                                progress = { (analysis.bearishConfluenceScore / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = BearishCrimson,
                                trackColor = SlateCardBorder
                            )
                        }
                    }

                    HorizontalDivider(color = SlateCardBorder)

                    // Petunjuk Salin Cepat untuk MetaTrader 5
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PARAMETER EKSEKUSI MT5:",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Surface(
                            color = CyanSecondary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    tint = CyanSecondary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Ketuk untuk Salin",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyanSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // GRID 4 PARAMETER UTAMA EKSEKUSI
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExecutionMetricBox(
                            title = "HARGA ENTRY",
                            value = formatPrice(entryPrice, digits),
                            subText = "Candle [1] Close",
                            borderColor = CyanSecondary,
                            testTag = "trade_metric_entry",
                            modifier = Modifier.weight(1f)
                        )
                        ExecutionMetricBox(
                            title = "STOP LOSS (SL)",
                            value = formatPrice(slPrice, digits),
                            subText = "${slDistancePoints.toInt()} Poin ATR",
                            borderColor = BearishCrimson,
                            testTag = "trade_metric_sl",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExecutionMetricBox(
                            title = "TARGET 1 (TP1)",
                            value = formatPrice(tp1Price, digits),
                            subText = "Rasio 1 : 1.5 (Aman)",
                            borderColor = BullishEmerald,
                            testTag = "trade_metric_tp1",
                            modifier = Modifier.weight(1f)
                        )
                        ExecutionMetricBox(
                            title = "TARGET 2 (TP2)",
                            value = formatPrice(tp2Price, digits),
                            subText = "Rasio 1 : 2.5 (Tren)",
                            borderColor = GoldBright,
                            testTag = "trade_metric_tp2",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 3. KALKULATOR LOT & MANAJEMEN RISIKO (SMART POSITION CALCULATOR)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SlateCardBorder, RoundedCornerShape(16.dp))
                    .testTag("trade_lot_calculator_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "KALKULATOR UKURAN LOT & RISIKO AKUN",
                        style = MaterialTheme.typography.titleMedium,
                        color = GoldBright,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Menghitung ukuran lot aman berdasarkan jarak Stop Loss (${slDistancePoints.toInt()} Poin ATR) dari provider ${selectedProvider.shortLabel}.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )

                    // Presets Modal Cepat
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("500", "1000", "2500", "5000", "10000").forEach { presetBal ->
                            val isSel = balanceInput == presetBal
                            FilterChip(
                                selected = isSel,
                                onClick = { balanceInput = presetBal },
                                label = { Text("$$presetBal", style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GoldPrimary.copy(alpha = 0.25f),
                                    selectedLabelColor = GoldBright
                                )
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = balanceInput,
                            onValueChange = { balanceInput = it },
                            label = { Text("Modal Akun ($)") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("account_balance_input")
                        )

                        Column(modifier = Modifier.weight(1.1f)) {
                            Text("Toleransi Risiko:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(0.5, 1.0, 2.0, 3.0).forEach { rPct ->
                                    FilterChip(
                                        selected = selectedRiskPct == rPct,
                                        onClick = { selectedRiskPct = rPct },
                                        label = { Text("${if (rPct == 0.5) "0.5" else rPct.toInt().toString()}%", style = MaterialTheme.typography.labelSmall) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = GoldPrimary,
                                            selectedLabelColor = ObsidianBg
                                        )
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = SlateCardBorder)

                    // Hasil Rekomendasi Lot
                    val clipboard = LocalClipboardManager.current
                    val ctx = LocalContext.current
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SlateSurfaceElevated)
                            .border(1.dp, SlateCardBorder, RoundedCornerShape(12.dp))
                            .clickable {
                                clipboard.setText(AnnotatedString(lotFormatted))
                                Toast.makeText(
                                    ctx,
                                    "✓ Ukuran Lot ($lotFormatted) tersalin - Siap di-paste ke MetaTrader 5",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .padding(12.dp)
                            .testTag("trade_lot_result_box")
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Rekomendasi Ukuran Lot:", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "$lotFormatted Lot",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = GoldBright,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Surface(
                                        color = GoldPrimary.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Salin Lot",
                                                tint = GoldBright,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = "SALIN",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = GoldBright,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Maksimal Risiko (Stop Loss):", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text(
                                    text = "-$${String.format(Locale.US, "%.2f", riskAmountDollars)} (${selectedRiskPct}%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BearishCrimson,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Proyeksi Profit TP1 (1.5R):", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text(
                                    text = "+$${String.format(Locale.US, "%.2f", estProfitTp1)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BullishEmerald,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Proyeksi Profit TP2 (2.5R):", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text(
                                    text = "+$${String.format(Locale.US, "%.2f", estProfitTp2)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BullishEmerald,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Tombol 1-Klik Salin Rencana Setup Lengkap
                    Button(
                        onClick = {
                            val orderPlan = buildString {
                                appendLine("🚀 SETUP EKSEKUSI MT5: ${analysis.dataEngine.symbolWithSuffix} (${analysis.activeTimeframe.label})")
                                appendLine("• Aksi: ${if (isBuy) "BUY" else if (isSell) "SELL" else "STANDBY"}")
                                appendLine("• Entry: ${formatPrice(entryPrice, digits)}")
                                appendLine("• Lot Rekomendasi: $lotFormatted")
                                appendLine("• Stop Loss: ${formatPrice(slPrice, digits)} (${slDistancePoints.toInt()} pt)")
                                appendLine("• Target TP1 (1.5R): ${formatPrice(tp1Price, digits)}")
                                appendLine("• Target TP2 (2.5R): ${formatPrice(tp2Price, digits)}")
                                appendLine("• Risiko Dolar: -$${String.format(Locale.US, "%.2f", riskAmountDollars)}")
                                appendLine("• Estimasi Reward TP1: +$${String.format(Locale.US, "%.2f", estProfitTp1)}")
                                appendLine("• Feed: ${selectedProvider.shortLabel}")
                            }
                            clipboard.setText(AnnotatedString(orderPlan))
                            Toast.makeText(
                                ctx,
                                "✓ Seluruh setup order MT5 disalin ke clipboard!",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanSecondary,
                            contentColor = ObsidianBg
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("copy_complete_order_setup")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "SALIN SELURUH SETUP ORDER MT5",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 4. CHECKLIST KONFLUENSI MESIN PENDUKUNG
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SlateCardBorder, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "AUDIT 7 KRITERIA KONFLUENSI EKSEKUSI [CANDLE 1]:",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyanSecondary,
                        fontWeight = FontWeight.Bold
                    )

                    analysis.confluenceChecklist.forEach { (criterion, passed) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(if (passed) BullishEmerald.copy(alpha = 0.2f) else TextSecondary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (passed) Icons.Default.CheckCircle else Icons.Default.Close,
                                    contentDescription = if (passed) "Lolos" else "Belum",
                                    tint = if (passed) BullishEmerald else TextSecondary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = criterion,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (passed) TextPrimary else TextSecondary
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun ExecutionMetricBox(
    title: String,
    value: String,
    subText: String,
    borderColor: androidx.compose.ui.graphics.Color,
    testTag: String = "",
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Surface(
        color = SlateSurfaceElevated,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.2.dp, borderColor.copy(alpha = 0.75f)),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                clipboardManager.setText(AnnotatedString(value))
                Toast.makeText(
                    context,
                    "✓ $title tersalin ($value)",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .then(if (testTag.isNotBlank()) Modifier.testTag(testTag) else Modifier)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = borderColor,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Surface(
                    color = borderColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Salin $title",
                            tint = borderColor,
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = "SALIN",
                            style = MaterialTheme.typography.labelSmall,
                            color = borderColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = subText,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
