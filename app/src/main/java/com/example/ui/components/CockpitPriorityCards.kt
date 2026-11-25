package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.data.local.SavedSignalSnapshotEntity
import com.example.domain.IndicatorAndConfluenceEngines
import com.example.model.AnalyticalVerdict
import com.example.model.CompleteHubAnalysis
import com.example.model.GlobalMarketSession
import com.example.model.PlainLanguageBriefing
import com.example.model.SignalOutcomeStatus
import com.example.ui.HubUiState
import com.example.ui.screens.formatPrice
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
fun CockpitModeToggleBar(
    isSimpleMode: Boolean,
    onToggleSimpleMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SlateSurface)
            .border(1.dp, SlateCardBorder, RoundedCornerShape(14.dp))
            .padding(4.dp)
            .testTag("cockpit_mode_toggle"),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isSimpleMode) GoldPrimary else ObsidianBg)
                .clickable { onToggleSimpleMode(true) }
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .testTag("cockpit_mode_simple_btn"),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Mode Sederhana",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSimpleMode) ObsidianBg else TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Keputusan Cepat & Risiko 1-Tap",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = if (isSimpleMode) ObsidianBg.copy(alpha = 0.85f) else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (!isSimpleMode) CyanSecondary else ObsidianBg)
                .clickable { onToggleSimpleMode(false) }
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .testTag("cockpit_mode_pro_btn"),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Mode Pro",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (!isSimpleMode) ObsidianBg else TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "14 Mesin, SMC & Diagnostik",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = if (!isSimpleMode) ObsidianBg.copy(alpha = 0.85f) else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SimpleCockpitDecisionCard(
    uiState: HubUiState,
    analysis: CompleteHubAnalysis,
    onUpdateRiskPreferences: (Double, Double) -> Unit,
    onNavigateToTrade: () -> Unit,
    onSaveSnapshot: () -> Unit,
    onSwitchToProMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val digits = analysis.symbol.digits
    val vol = analysis.volatilityEngine
    val bar1 = analysis.dataEngine.closedCandle1
    val entryPrice = bar1.close

    val isBuy = analysis.verdict in listOf(
        AnalyticalVerdict.STRONG_BULLISH_CONFLUENCE,
        AnalyticalVerdict.MODERATE_BULLISH_BIAS
    )
    val isSell = analysis.verdict in listOf(
        AnalyticalVerdict.STRONG_BEARISH_CONFLUENCE,
        AnalyticalVerdict.MODERATE_BEARISH_BIAS
    )
    val isActionable = isBuy || isSell

    val accentColor = when {
        isBuy -> BullishEmerald
        isSell -> BearishCrimson
        else -> NeutralAmber
    }
    val cardBg = when {
        isBuy -> BullishBg
        isSell -> BearishBg
        else -> NeutralBg
    }
    val actionBadge = when {
        isBuy -> "BELI (BUY)"
        isSell -> "JUAL (SELL)"
        else -> "TUNGGU (STANDBY)"
    }

    val calibration = analysis.calibrationReport ?: IndicatorAndConfluenceEngines.buildInstrumentCalibrationReport(
        bars = analysis.bars,
        symbol = analysis.symbol,
        momentumReport = analysis.momentumEngine,
        volatilityReport = analysis.volatilityEngine,
        sessionReport = analysis.sessionEngine,
        highImpactNewsCount = analysis.newsEngine.highImpactCount
    )
    val briefing: PlainLanguageBriefing = analysis.plainBriefing ?: IndicatorAndConfluenceEngines.buildPlainLanguageBriefing(
        symbol = analysis.symbol,
        timeframe = analysis.activeTimeframe,
        verdict = analysis.verdict,
        trend = analysis.trendEngine,
        momentum = analysis.momentumEngine,
        mtf = analysis.mtfEngine,
        news = analysis.newsEngine,
        session = analysis.sessionEngine,
        calibration = calibration
    )

    val slPrice = if (isBuy) vol.longStopReferencePrice else vol.shortStopReferencePrice
    val tp1Price = if (isBuy) vol.longTarget1RPrice else vol.shortTarget1RPrice
    val tp2Price = if (isBuy) vol.longTarget2RPrice else vol.shortTarget2RPrice
    val slPoints = (abs(entryPrice - slPrice) / analysis.symbol.pointSize).toInt()
    val lotCalc = IndicatorAndConfluenceEngines.calculatePositionRisk(
        symbol = analysis.symbol,
        provider = uiState.selectedProvider,
        balanceDollars = uiState.accountBalanceDollars,
        riskPercentage = uiState.riskPercentage,
        entryPrice = entryPrice,
        stopLossPrice = slPrice,
        target1Price = tp1Price,
        target2Price = tp2Price,
        brokerSpec = analysis.brokerContractSpec
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, accentColor, RoundedCornerShape(18.dp))
            .testTag("simple_decision_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = accentColor.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.border(1.dp, accentColor, RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = actionBadge,
                        style = MaterialTheme.typography.labelLarge,
                        color = accentColor,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                Text(
                    text = "Skor ${maxOf(analysis.bullishConfluenceScore, analysis.bearishConfluenceScore)}% • TF ${analysis.activeTimeframe.label}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = briefing.headline,
                style = MaterialTheme.typography.titleLarge,
                color = accentColor,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = briefing.beginnerSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )

            briefing.cautionBadge?.let { caution ->
                Surface(
                    color = BearishBg.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, NeutralAmber.copy(alpha = 0.7f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = "Peringatan kondisi pasar",
                            tint = NeutralAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = caution,
                            style = MaterialTheme.typography.labelMedium,
                            color = NeutralAmber,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Surface(
                color = SlateSurfaceElevated,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, SlateCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Panduan Cepat: ${briefing.actionHint}",
                    style = MaterialTheme.typography.labelMedium,
                    color = CyanSecondary,
                    modifier = Modifier.padding(10.dp)
                )
            }

            HorizontalDivider(color = SlateCardBorder)

            // 1-Tap Risk & Balance Presets
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Batas Risiko 1-Tap (Modal $${uiState.accountBalanceDollars.toInt()}):",
                        style = MaterialTheme.typography.labelMedium,
                        color = GoldBright,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Maks -$${String.format(Locale.US, "%.2f", lotCalc.riskAmountDollars)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = BearishCrimson,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val riskPresets = listOf(
                        Triple(0.5, "0.5% Aman", "simple_risk_chip_0_5"),
                        Triple(1.0, "1% Standar", "simple_risk_chip_1"),
                        Triple(2.0, "2% Agresif", "simple_risk_chip_2")
                    )
                    riskPresets.forEach { (pct, label, tag) ->
                        val selected = abs(uiState.riskPercentage - pct) < 0.05
                        FilterChip(
                            selected = selected,
                            onClick = { onUpdateRiskPreferences(uiState.accountBalanceDollars, pct) },
                            label = {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GoldPrimary,
                                selectedLabelColor = ObsidianBg
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag(tag)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Modal:",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    listOf(500.0, 1000.0, 2500.0, 5000.0).forEach { bal ->
                        val selected = abs(uiState.accountBalanceDollars - bal) < 1.0
                        FilterChip(
                            selected = selected,
                            onClick = { onUpdateRiskPreferences(bal, uiState.riskPercentage) },
                            label = { Text("$${bal.toInt()}", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanSecondary.copy(alpha = 0.25f),
                                selectedLabelColor = CyanSecondary
                            ),
                            modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                        )
                    }
                }
            }

            if (isActionable) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SimpleMetricBox(
                        title = "ENTRY",
                        value = formatPrice(entryPrice, digits),
                        color = CyanSecondary,
                        tag = "simple_entry_value",
                        modifier = Modifier.weight(1f)
                    )
                    SimpleMetricBox(
                        title = "STOP LOSS",
                        value = formatPrice(slPrice, digits),
                        color = BearishCrimson,
                        tag = "simple_sl_value",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SimpleMetricBox(
                        title = "TARGET TP1 (1.5R)",
                        value = formatPrice(tp1Price, digits),
                        color = BullishEmerald,
                        tag = "simple_tp1_value",
                        modifier = Modifier.weight(1f)
                    )
                    SimpleMetricBox(
                        title = if (lotCalc.isProxyIndicativeOnly) "SIZE INDIKATIF" else "UKURAN LOT",
                        value = lotCalc.formattedSize,
                        color = GoldBright,
                        tag = "simple_lot_value",
                        modifier = Modifier.weight(1f)
                    )
                }

                Button(
                    onClick = {
                        val orderText = buildString {
                            appendLine("${analysis.dataEngine.symbolWithSuffix} (${analysis.activeTimeframe.label}) • ${if (isBuy) "BUY" else "SELL"}")
                            appendLine("Entry: ${formatPrice(entryPrice, digits)}")
                            appendLine("Stop Loss: ${formatPrice(slPrice, digits)} ($slPoints pt)")
                            appendLine("Target TP1: ${formatPrice(tp1Price, digits)}")
                            appendLine("Ukuran Posisi: ${lotCalc.formattedSize} (Risiko ${uiState.riskPercentage}%)")
                        }
                        clipboard.setText(AnnotatedString(orderText))
                        Toast.makeText(context, "Setup order disalin ke clipboard.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBg),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("simple_copy_order_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Salin setup order",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SALIN SETUP ORDER",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Surface(
                    color = SlateSurfaceElevated,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, NeutralAmber.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("simple_standby_notice")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "BELUM ADA ENTRY AMAN (STANDBY)",
                            style = MaterialTheme.typography.labelMedium,
                            color = NeutralAmber,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Harga Entry, SL, dan TP tidak diaktifkan saat kondisi netral/choppy agar Anda tidak masuk posisi berisiko.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSaveSnapshot,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("save_signal_snapshot_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.BookmarkAdd,
                        contentDescription = "Simpan sinyal ke riwayat",
                        tint = GoldBright,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Simpan Riwayat", style = MaterialTheme.typography.labelSmall, color = GoldBright)
                }

                OutlinedButton(
                    onClick = onNavigateToTrade,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("cockpit_jump_to_trade_card")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = "Buka kalkulator dan safety gate",
                        tint = CyanSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tiket & Gate", style = MaterialTheme.typography.labelSmall, color = CyanSecondary)
                }
            }

            OutlinedButton(
                onClick = onSwitchToProMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("switch_to_pro_mode_button")
            ) {
                Text(
                    text = "Buka Mode Pro (14 Mesin, Kalibrasi & Diagnostik)",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary
                )
            }
        }
    }
}

@Composable
private fun SimpleMetricBox(
    title: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    tag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SlateSurfaceElevated,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
        modifier = modifier.testTag(tag)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun BacktestAndCalibrationCard(
    analysis: CompleteHubAnalysis,
    savedLogs: List<SavedSignalSnapshotEntity> = emptyList(),
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val calibration = analysis.calibrationReport ?: IndicatorAndConfluenceEngines.buildInstrumentCalibrationReport(
        bars = analysis.bars,
        symbol = analysis.symbol,
        momentumReport = analysis.momentumEngine,
        volatilityReport = analysis.volatilityEngine,
        sessionReport = analysis.sessionEngine,
        highImpactNewsCount = analysis.newsEngine.highImpactCount
    )
    val backtest = analysis.backtestSummary ?: IndicatorAndConfluenceEngines.runHistoricalBacktest(
        bars = analysis.bars,
        symbol = analysis.symbol,
        calibrationProfile = calibration.profile
    )

    val resolvedLogs = savedLogs.filter {
        it.outcomeStatus == SignalOutcomeStatus.WIN_TP.code ||
            it.outcomeStatus == SignalOutcomeStatus.LOSS_SL.code
    }
    val winLogs = resolvedLogs.count { it.outcomeStatus == SignalOutcomeStatus.WIN_TP.code }
    val lossLogs = resolvedLogs.count { it.outcomeStatus == SignalOutcomeStatus.LOSS_SL.code }
    val pendingLogs = savedLogs.count { it.outcomeStatus == SignalOutcomeStatus.PENDING.code }
    val liveWinRatePct = if (resolvedLogs.isNotEmpty()) (winLogs.toDouble() / resolvedLogs.size) * 100.0 else 0.0

    Card(
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, CyanSecondary.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .testTag("backtest_summary_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = "Statistik mini-backtest historis",
                        tint = CyanSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "BUKTI HISTORIS & KALIBRASI ${analysis.symbol.code}",
                            style = MaterialTheme.typography.titleSmall,
                            color = GoldBright,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${backtest.sampleBarsCount} candle tutup (${analysis.activeTimeframe.label}) • Nol look-ahead",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                val gateColor = if (calibration.qualityGatePassed) BullishEmerald else NeutralAmber
                Surface(
                    color = gateColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, gateColor.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = if (calibration.qualityGatePassed) "GATE LOLOS" else "FILTER WASPADA",
                        style = MaterialTheme.typography.labelSmall,
                        color = gateColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Calibration Status Summary
            Surface(
                color = SlateSurfaceElevated,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, SlateCardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("calibration_quality_box")
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (calibration.qualityGatePassed) Icons.Default.CheckCircle else Icons.Default.Shield,
                            contentDescription = "Status kalibrasi instrumen",
                            tint = if (calibration.qualityGatePassed) BullishEmerald else NeutralAmber,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = calibration.profile.profileName,
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = calibration.qualityGateSummary,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (calibration.qualityGatePassed) BullishEmerald else NeutralAmber
                    )
                }
            }

            // Mini-Backtest Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BacktestStatChip(
                    label = "Win-Rate",
                    value = if (backtest.wins + backtest.losses > 0) "${String.format(Locale.US, "%.1f", backtest.winRatePct)}%" else "-",
                    accent = if (backtest.winRatePct >= 45.0) BullishEmerald else NeutralAmber,
                    modifier = Modifier.weight(1f)
                )
                BacktestStatChip(
                    label = "Profit Factor",
                    value = if (backtest.wins + backtest.losses > 0) String.format(Locale.US, "%.2f", backtest.profitFactor) else "-",
                    accent = if (backtest.profitFactor >= 1.2) BullishEmerald else TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                BacktestStatChip(
                    label = "Expectancy",
                    value = if (backtest.wins + backtest.losses > 0) "${String.format(Locale.US, "%+.2f", backtest.netExpectancyR)}R" else "-",
                    accent = if (backtest.netExpectancyR >= 0.0) CyanSecondary else BearishCrimson,
                    modifier = Modifier.weight(1f)
                )
                BacktestStatChip(
                    label = "Max DD",
                    value = "${String.format(Locale.US, "%.1f", backtest.maxDrawdownR)}R",
                    accent = BearishCrimson,
                    modifier = Modifier.weight(1f)
                )
            }

            if (!compact) {
                Text(
                    text = "Total Setup: ${backtest.totalTrades} (Win ${backtest.wins} • Loss ${backtest.losses} • Aktif/Expired ${backtest.breakevenOrOpen}). ${backtest.honestNote}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )

                if (savedLogs.isNotEmpty()) {
                    HorizontalDivider(color = SlateCardBorder)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("live_track_record_row"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Track Record Sinyal Tersimpan:",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldBright,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (resolvedLogs.isNotEmpty()) {
                                "Win $winLogs | Loss $lossLogs (${String.format(Locale.US, "%.0f", liveWinRatePct)}%) • Pending $pendingLogs"
                            } else {
                                "Menunggu hasil $pendingLogs sinyal"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BacktestStatChip(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SlateSurfaceElevated,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, SlateCardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SignalOutcomeBadgePill(
    logItem: SavedSignalSnapshotEntity,
    modifier: Modifier = Modifier
) {
    val outcome = SignalOutcomeStatus.fromCode(logItem.outcomeStatus)
    val badgeColor = when (outcome) {
        SignalOutcomeStatus.WIN_TP -> BullishEmerald
        SignalOutcomeStatus.LOSS_SL -> BearishCrimson
        SignalOutcomeStatus.PENDING -> NeutralAmber
        SignalOutcomeStatus.EXPIRED_NEUTRAL -> TextSecondary
    }
    Surface(
        color = badgeColor.copy(alpha = 0.16f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.6f)),
        modifier = modifier.testTag("log_outcome_badge_${logItem.id}")
    ) {
        Text(
            text = outcome.label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = badgeColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
