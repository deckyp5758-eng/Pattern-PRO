package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.SavedSignalSnapshotEntity
import com.example.model.AnalyticalVerdict
import com.example.model.CompleteHubAnalysis
import com.example.model.MarketSymbol
import com.example.model.OnlineDataProvider
import com.example.model.SrInteractionType
import com.example.model.Timeframe
import com.example.ui.HubUiState
import com.example.ui.components.InteractiveMt5ChartCanvas
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HudTerminalScreen(
    uiState: HubUiState,
    analysis: CompleteHubAnalysis,
    savedLogs: List<SavedSignalSnapshotEntity>,
    onSelectProvider: (OnlineDataProvider) -> Unit,
    onSelectSymbol: (MarketSymbol) -> Unit,
    onSelectTimeframe: (Timeframe) -> Unit,
    onInspectBar: (Int) -> Unit,
    onToggleEma: () -> Unit,
    onToggleSr: () -> Unit,
    onToggleSwings: () -> Unit,
    onToggleLiveTick: () -> Unit,
    onRunMultiProviderAudit: () -> Unit,
    onCleanAndResetSession: () -> Unit,
    onRefreshData: () -> Unit,
    onSaveSnapshot: () -> Unit,
    onDeleteSnapshot: (Int) -> Unit,
    onClearAllSnapshots: () -> Unit,
    onUpdateGitHubRepo: (String) -> Unit = {},
    onCheckGitHubUpdate: () -> Unit = {},
    onUpdateMetaApiAccountId: (String) -> Unit = {},
    onUpdateMetaApiAuthToken: (String) -> Unit = {},
    onSaveMetaApiCredentials: () -> Unit = {},
    onClearMetaApiCredentials: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val digits = analysis.symbol.digits
    val bar0 = analysis.dataEngine.formingCandle0
    val bar1 = analysis.dataEngine.closedCandle1
    val inspectedBar = analysis.bars.firstOrNull { it.barIndex == uiState.inspectedBarIndex } ?: bar1
    val audit = uiState.liveAudit

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // 1. Header Banner dengan Status Koneksi Online Jujur (LIVE vs OFFLINE)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(128.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, GoldPrimary.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_quant_hero_banner),
                    contentDescription = "Header HUB Smart Pattern Engine Pro",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    ObsidianBg.copy(alpha = 0.94f),
                                    ObsidianBg.copy(alpha = 0.78f),
                                    ObsidianBg.copy(alpha = 0.55f)
                                )
                            )
                        )
                        .padding(14.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isVerifiedLive = uiState.isProviderOnline && audit.check1EndpointAccessible
                            Surface(
                                color = if (isVerifiedLive) BullishBg else BearishBg,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.border(
                                    1.dp,
                                    if (isVerifiedLive) BullishEmerald else BearishCrimson,
                                    RoundedCornerShape(8.dp)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isVerifiedLive) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                        contentDescription = null,
                                        tint = if (isVerifiedLive) BullishEmerald else BearishCrimson,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (isVerifiedLive) {
                                            "LIVE ONLINE • HTTP ${audit.lastHttpStatusCode} • NON-REPAINT [1]"
                                        } else {
                                            "PROVIDER OFFLINE / GAGAL KONEKSI"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isVerifiedLive) BullishEmerald else BearishCrimson
                                    )
                                }
                            }
                            IconButton(
                                onClick = onRefreshData,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("refresh_market_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Perbarui data pasar dan berita terbaru",
                                    tint = GoldBright
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "HUB SMART PATTERN ENGINE PRO",
                                style = MaterialTheme.typography.titleLarge,
                                color = GoldBright
                            )
                            Text(
                                text = "${analysis.dataEngine.symbolWithSuffix} • TF ${analysis.activeTimeframe.label} • Sumber: ${uiState.selectedProvider.displayName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanSecondary
                            )
                        }
                    }
                }
            }
        }

        // 2. Pilihan Sumber Data Online (Provider API), Pair, Akhiran Broker, dan Timeframe
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SlateCardBorder, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Pilihan Sumber Server Online (Untuk Menguji Perbedaan Sumber & Mode Offline)
                    Text(
                        text = "Sumber Data Server Online (Tanpa Data Lokal/Simulasi):",
                        style = MaterialTheme.typography.labelSmall,
                        color = GoldBright
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OnlineDataProvider.entries.forEach { prov ->
                            val selected = uiState.selectedProvider == prov
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectProvider(prov) },
                                label = {
                                    Text(
                                        text = prov.shortLabel,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = if (prov.isOfflineTestMode) {
                                        BearishCrimson.copy(alpha = 0.28f)
                                    } else {
                                        CyanSecondary.copy(alpha = 0.25f)
                                    },
                                    selectedLabelColor = if (prov.isOfflineTestMode) BearishCrimson else CyanSecondary
                                ),
                                modifier = Modifier.testTag("provider_chip_${prov.id}")
                            )
                        }
                    }

                    // Pilihan Pair XAUUSD vs EURUSD
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MarketSymbol.entries.forEach { sym ->
                            val isSelected = sym == uiState.selectedSymbol
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) GoldPrimary else SlateSurfaceElevated)
                                    .clickable { onSelectSymbol(sym) }
                                    .padding(vertical = 12.dp, horizontal = 10.dp)
                                    .testTag("symbol_btn_${sym.code}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = sym.code,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isSelected) ObsidianBg else TextPrimary
                                    )
                                    Text(
                                        text = sym.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) ObsidianBg.copy(alpha = 0.8f) else TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    // Timeframe Row: M1, M5, M15, M30, H1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Timeframe.orderedByChart.forEach { tf ->
                            val selected = tf == uiState.selectedTimeframe
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) CyanSecondary else SlateSurfaceElevated)
                                    .clickable { onSelectTimeframe(tf) }
                                    .testTag("tf_btn_${tf.label}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tf.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) ObsidianBg else TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Pemisahan Candle [1] (Sudah Tutup / Sinyal Final di Kiri) vs Candle [0] (Sedang Berjalan di Ujung Kanan Grafik MT5)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val topClosedPat = analysis.candleEngine.activeClosedPatterns.firstOrNull()?.pattern?.displayName
                    ?: analysis.candleEngine.anatomyType.label
                Card(
                    colors = CardDefaults.cardColors(containerColor = BullishBg.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, BullishEmerald, RoundedCornerShape(14.dp))
                        .clickable { onInspectBar(1) }
                        .testTag("card_candle_1_closed")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = BullishEmerald,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "CANDLE [1] TUTUP",
                                style = MaterialTheme.typography.labelMedium,
                                color = BullishEmerald
                            )
                        }
                        Text(
                            text = formatPrice(bar1.close, digits),
                            style = MaterialTheme.typography.titleLarge,
                            color = GoldBright
                        )
                        Text(
                            text = "Pola: $topClosedPat",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = "SINYAL FINAL NON-REPAINT",
                            style = MaterialTheme.typography.labelSmall,
                            color = BullishEmerald
                        )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = NeutralBg.copy(alpha = 0.55f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, NeutralAmber.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                        .clickable { onInspectBar(0) }
                        .testTag("card_candle_0_forming")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = NeutralAmber,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "CANDLE [0] UJUNG KANAN",
                                style = MaterialTheme.typography.labelMedium,
                                color = NeutralAmber
                            )
                        }
                        Text(
                            text = formatPrice(bar0.close, digits),
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = "Waktu: ${formatEpochSec(bar0.timeEpochSec)} | Vol: ${bar0.tickVolume}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Text(
                            text = "SEDANG BERJALAN (Pantauan)",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeutralAmber
                        )
                    }
                }
            }
        }

        // 4. Kesimpulan Analitik & Skor Konfluensi (Berdasarkan Candle Tutup [1])
        item {
            val verdict = analysis.verdict
            val (verdictColor, verdictBg) = when (verdict) {
                AnalyticalVerdict.STRONG_BULLISH_CONFLUENCE,
                AnalyticalVerdict.MODERATE_BULLISH_BIAS -> BullishEmerald to BullishBg
                AnalyticalVerdict.STRONG_BEARISH_CONFLUENCE,
                AnalyticalVerdict.MODERATE_BEARISH_BIAS -> BearishCrimson to BearishBg
                AnalyticalVerdict.HTF_BULLISH_LTF_PULLBACK,
                AnalyticalVerdict.HTF_BEARISH_LTF_PULLBACK,
                AnalyticalVerdict.NEUTRAL_CONSOLIDATION -> GoldPrimary to NeutralBg
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = SlateSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, verdictColor.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "KESIMPULAN ANALITIK CANDLE TUTUP [1]",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            Text(
                                text = verdict.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = verdictColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            color = verdictBg,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.border(1.dp, verdictColor, RoundedCornerShape(10.dp))
                        ) {
                            Text(
                                text = "Naik ${analysis.bullishConfluenceScore}% | Turun ${analysis.bearishConfluenceScore}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = verdictColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = { analysis.bullishConfluenceScore / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = BullishEmerald,
                            trackColor = BearishCrimson.copy(alpha = 0.45f)
                        )
                        Text(
                            text = analysis.mtfEngine.executiveConclusion,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }

                    analysis.newsEngine.newsRiskWarning?.let { warn ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(BearishBg.copy(alpha = 0.7f))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = GoldBright,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = warn,
                                style = MaterialTheme.typography.labelSmall,
                                color = GoldBright
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onRefreshData,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("pull_live_server_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = CyanSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Tarik Server Online",
                                style = MaterialTheme.typography.labelMedium,
                                color = CyanSecondary
                            )
                        }

                        Button(
                            onClick = onSaveSnapshot,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GoldPrimary,
                                contentColor = ObsidianBg
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("save_signal_snapshot_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.BookmarkAdd,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Simpan ke Riwayat",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }

        // 5. Grafik Interaktif MT5 & Tombol Lapisan Indikator
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.showEmaOverlays,
                        onClick = onToggleEma,
                        label = { Text("Garis EMA 20/50/200", style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GoldPrimary.copy(alpha = 0.25f),
                            selectedLabelColor = GoldBright
                        ),
                        modifier = Modifier.testTag("toggle_ema_chip")
                    )
                    FilterChip(
                        selected = uiState.showSrZones,
                        onClick = onToggleSr,
                        label = { Text("Zona Support/Resistance", style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BullishEmerald.copy(alpha = 0.25f),
                            selectedLabelColor = BullishEmerald
                        ),
                        modifier = Modifier.testTag("toggle_sr_chip")
                    )
                    FilterChip(
                        selected = uiState.showStructureSwings,
                        onClick = onToggleSwings,
                        label = { Text("Titik Swing (HH/HL/LH/LL)", style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanSecondary.copy(alpha = 0.25f),
                            selectedLabelColor = CyanSecondary
                        ),
                        modifier = Modifier.testTag("toggle_swings_chip")
                    )
                    FilterChip(
                        selected = uiState.isLiveTickMonitorEnabled,
                        onClick = onToggleLiveTick,
                        label = { Text("Polling Server 12d", style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeutralAmber.copy(alpha = 0.25f),
                            selectedLabelColor = NeutralAmber
                        ),
                        modifier = Modifier.testTag("toggle_live_tick_chip")
                    )
                }

                InteractiveMt5ChartCanvas(
                    analysis = analysis,
                    inspectedBarIndex = uiState.inspectedBarIndex,
                    showEmaOverlays = uiState.showEmaOverlays,
                    showSrZones = uiState.showSrZones,
                    showStructureSwings = uiState.showStructureSwings,
                    onBarSelected = onInspectBar
                )
            }
        }

        // 5B. PENJELASAN ALUR SKENARIO GRAFIK & LEVEL HARGA MT5 (BISA DISALIN)
        item {
            val sr = analysis.srEngine
            val vol = analysis.volatilityEngine
            val bar1Close = analysis.bars.getOrNull(1)?.close ?: analysis.bars.first().close
            val suppVal = sr.nearestSupport?.centerPrice ?: (bar1Close - vol.atr14 * 1.5)
            val resVal = sr.nearestResistance?.centerPrice ?: (bar1Close + vol.atr14 * 1.5)
            val formatSupp = formatPrice(suppVal, digits)
            val formatRes = formatPrice(resVal, digits)
            val formatSl = formatPrice(vol.longStopReferencePrice, digits)
            val formatTp = formatPrice(vol.longTarget1RPrice, digits)
            val riskDistance = formatPrice(vol.atr14 * 1.5, digits)
            val rewardDistance = formatPrice(vol.atr14 * 2.25, digits)
            val patternLabel = analysis.candleEngine.activeClosedPatterns.firstOrNull()?.pattern?.displayName ?: "Netral / Tanpa Pola Khusus"

            Card(
                colors = CardDefaults.cardColors(containerColor = SlateSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GoldPrimary.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ANALISIS SKENARIO GRAFIK & LEVEL MT5",
                                style = MaterialTheme.typography.titleMedium,
                                color = GoldBright,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Penjelasan Arah, Toleransi Risiko, & Level Kunci yang Bisa Disalin",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        Surface(
                            color = CyanSecondary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "SIAP LUKIS MT5",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanSecondary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = SlateCardBorder)

                    // Narasi Skenario Arah Pergerakan
                    val scenarioText = when (sr.closedBar1Interaction) {
                        SrInteractionType.SUPPORT_HOLD, SrInteractionType.BULLISH_REJECTION ->
                            "📍 CANDLE MENYENTUH AREA SUPPORT ($formatSupp):\n" +
                            "• Skenario Utama: Berpotensi PANTULAN NAIK (Rebound Bullish) menuju Resistance ($formatRes).\n" +
                            "• Skenario Alternatif: Jika penutupan candle di bawah $formatSupp (Breakdown), harga berpotensi meluncur ke Support bawah berikutnya."
                        SrInteractionType.RESISTANCE_HOLD, SrInteractionType.BEARISH_REJECTION ->
                            "📍 CANDLE MENYENTUH AREA RESISTANCE ($formatRes):\n" +
                            "• Skenario Utama: Berpotensi PENOLAKAN TURUN (Rejection Bearish) menuju Support ($formatSupp).\n" +
                            "• Skenario Alternatif: Jika penutupan candle di atas $formatRes (Breakout), harga berpotensi melanjutkan kenaikan."
                        else ->
                            "📍 HARGA DIPERDAGANGKAN DI ANTARA SUPPORT & RESISTANCE:\n" +
                            "• Rentang Pergerakan: Antara Support $formatSupp hingga Resistance $formatRes.\n" +
                            "• Status Pola: $patternLabel. Menunggu reaksi pada salah satu level."
                    }

                    Surface(
                        color = SlateSurfaceElevated,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SlateCardBorder, RoundedCornerShape(10.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "🔮 PROYEKSI ARAH GRAFIK:",
                                style = MaterialTheme.typography.labelMedium,
                                color = CyanSecondary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = scenarioText,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                            Text(
                                text = "• Estimasi Risiko (Distance SL): $riskDistance point/pip ($${formatPrice(vol.atr14 * 1.5, digits)}).\n" +
                                    "• Proyeksi Target (Reward 1.5R): $rewardDistance point/pip ($${formatPrice(vol.atr14 * 2.25, digits)}).\n" +
                                    "• Rasio Risk-to-Reward: 1 : 1.5 (Sangat Sehat).\n" +
                                    "• Struktur Pasar (HH/HL/LH/LL): ${analysis.structureEngine.classification.label}.\n" +
                                    "• Ayunan Terakhir: ${analysis.structureEngine.confirmedSwings.firstOrNull()?.let { "${it.label.shortCode} (${it.label.fullName}) di ${formatPrice(it.price, digits)}" } ?: "Menunggu titik swing baru"}.",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }

                    Text(
                        text = "LEVEL HARGA MT5 (KETUK BADGE UNTUK SALIN):",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )

                    // Grid 4 Level MT5 dengan Tombol Salin
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LevelCopyBox(
                            title = "SUPPORT (S1)",
                            value = formatSupp,
                            subText = "Batas Pantulan Bawah",
                            borderColor = BullishEmerald,
                            testTag = "chart_copy_support",
                            modifier = Modifier.weight(1f)
                        )
                        LevelCopyBox(
                            title = "RESISTANCE (R1)",
                            value = formatRes,
                            subText = "Batas Penolakan Atas",
                            borderColor = BearishCrimson,
                            testTag = "chart_copy_resistance",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LevelCopyBox(
                            title = "STOP LOSS (SL)",
                            value = formatSl,
                            subText = "Toleransi $riskDistance pt",
                            borderColor = NeutralAmber,
                            testTag = "chart_copy_sl",
                            modifier = Modifier.weight(1f)
                        )
                        LevelCopyBox(
                            title = "TARGET PROFIT (TP)",
                            value = formatTp,
                            subText = "Target $rewardDistance pt",
                            borderColor = CyanSecondary,
                            testTag = "chart_copy_tp",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 7. Detail Anatomi Candle & Referensi Jarak Stop/Target ATR(14)
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val statusTag = if (inspectedBar.barIndex == 0) {
                        "CANDLE [0] SEDANG BERJALAN (BELUM TUTUP)"
                    } else {
                        "CANDLE [${inspectedBar.barIndex}] SUDAH TUTUP (TERKONFIRMASI)"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DETAIL CANDLE: $statusTag",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (inspectedBar.barIndex == 0) NeutralAmber else CyanSecondary
                        )
                        Text(
                            text = formatEpochSec(inspectedBar.timeEpochSec),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricMiniCol("BUKA (OPEN)", formatPrice(inspectedBar.open, digits))
                        MetricMiniCol("TINGGI (HIGH)", formatPrice(inspectedBar.high, digits))
                        MetricMiniCol("RENDAH (LOW)", formatPrice(inspectedBar.low, digits))
                        MetricMiniCol("TUTUP (CLOSE)", formatPrice(inspectedBar.close, digits))
                    }

                    HorizontalDivider(color = SlateCardBorder)

                    val pt = analysis.symbol.pointSize
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricMiniCol("UKURAN BODI", "${(inspectedBar.bodySize / pt).toInt()} pt")
                        MetricMiniCol("EKOR ATAS", "${(inspectedBar.upperWick / pt).toInt()} pt")
                        MetricMiniCol("EKOR BAWAH", "${(inspectedBar.lowerWick / pt).toInt()} pt")
                        MetricMiniCol("BODI/RENTANG", "${(inspectedBar.bodyToRangeRatio * 100).toInt()}%")
                    }

                    HorizontalDivider(color = SlateCardBorder)

                    val vol = analysis.volatilityEngine
                    Text(
                        text = "REFERENSI BATAS RISIKO (STOP) & TARGET ATR(14) (BUKAN AUTO-TRADE)",
                        style = MaterialTheme.typography.labelMedium,
                        color = GoldBright
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricMiniCol("ATR(14)", "${formatPrice(vol.atr14, digits)} (${String.format(Locale.US, "%.2f", vol.atrPercentageOfPrice)}%)")
                        MetricMiniCol("BATAS STOP BUY", formatPrice(vol.longStopReferencePrice, digits))
                        MetricMiniCol("TARGET BUY (1.5R)", formatPrice(vol.longTarget1RPrice, digits))
                        MetricMiniCol("BATAS STOP SELL", formatPrice(vol.shortStopReferencePrice, digits))
                    }

                    HorizontalDivider(color = SlateCardBorder)

                    // KETERANGAN PANDUAN CARA BACA ANATOMI & ATR(14)
                    val bodyRatioPct = (inspectedBar.bodyToRangeRatio * 100).toInt()
                    val candleDominanceNote = when {
                        inspectedBar.isBullish && bodyRatioPct >= 50 ->
                            "🟢 DOMINASI PEMBELI KUAT: Bodi $bodyRatioPct% (Bullish Momentum). Harga tutup dekat puncaknya."
                        inspectedBar.isBullish ->
                            "🟢 BULLISH PENOLAKAN/KOREKSI: Bodi $bodyRatioPct% dengan ekor bawah/atas signifikan. Terdapat perlawanan."
                        inspectedBar.isBearish && bodyRatioPct >= 50 ->
                            "🔴 DOMINASI PENJUAL KUAT: Bodi $bodyRatioPct% (Bearish Momentum). Harga tutup dekat dasarnya."
                        inspectedBar.isBearish ->
                            "🔴 BEARISH PENOLAKAN/KOREKSI: Bodi $bodyRatioPct% dengan ekor atas/bawah signifikan."
                        else ->
                            "🟡 PASAR DOJI/KONSOLIDASI: Bodi hanya $bodyRatioPct%. Pembeli & penjual seimbang (Ragu-ragu)."
                    }

                    Surface(
                        color = SlateSurfaceElevated,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SlateCardBorder, RoundedCornerShape(10.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "💡 PANDUAN BACA ANATOMI CANDLE & ATR(14):",
                                style = MaterialTheme.typography.labelMedium,
                                color = CyanSecondary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = candleDominanceNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                            Text(
                                text = "• Jarak Gerak ATR(14): ${formatPrice(vol.atr14, digits)} point/pip (${String.format(Locale.US, "%.2f", vol.atrPercentageOfPrice)}% dari harga).\n" +
                                    "• Stop Loss Buy Ideal: $${formatPrice(vol.longStopReferencePrice, digits)} (Batas toleransi aman dari ekor keplakan pasar).\n" +
                                    "• Stop Loss Sell Ideal: $${formatPrice(vol.shortStopReferencePrice, digits)} (Batas toleransi aman jika ambil Sell).\n" +
                                    "• Target Profit 1.5R: $${formatPrice(vol.longTarget1RPrice, digits)} (Proyeksi 1.5x dari jarak risiko).",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }

        // 8. Riwayat Sinyal Candle Tutup yang Tersimpan
        if (savedLogs.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CATATAN RIWAYAT SINYAL TERKUNCI (${savedLogs.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = GoldBright
                    )
                    OutlinedButton(
                        onClick = onClearAllSnapshots,
                        modifier = Modifier.testTag("clear_all_logs_button")
                    ) {
                        Text(
                            text = "Bersihkan Semua",
                            style = MaterialTheme.typography.labelSmall,
                            color = BearishCrimson
                        )
                    }
                }
            }
            items(savedLogs, key = { it.id }) { logItem ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateSurfaceElevated),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SlateCardBorder, RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "${logItem.symbolWithSuffix} [${logItem.timeframeLabel}] • ${logItem.verdictLabel}",
                                style = MaterialTheme.typography.labelLarge,
                                color = CyanSecondary
                            )
                            Text(
                                text = "Tutup @ ${logItem.closePrice} | Pola: ${logItem.patternDetected} | Skor: ${logItem.confluenceScore}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Ref Stop: ${String.format(Locale.US, "%.2f", logItem.stopReferencePrice)} | Ref Target: ${String.format(Locale.US, "%.2f", logItem.targetReferencePrice)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        IconButton(
                            onClick = { onDeleteSnapshot(logItem.id) },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("delete_log_${logItem.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Hapus catatan sinyal",
                                tint = BearishCrimson
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
private fun AuditCheckRow(
    title: String,
    passed: Boolean,
    detail: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (passed) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
            contentDescription = null,
            tint = if (passed) BullishEmerald else BearishCrimson,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = if (passed) TextPrimary else BearishCrimson
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun MetricMiniCol(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary
        )
    }
}

@Composable
private fun LevelCopyBox(
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
                    "✓ $title tersalin: $value (Siap di-paste ke MetaTrader 5)",
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

fun formatPrice(value: Double, digits: Int): String =
    String.format(Locale.US, "%.${digits}f", value)

fun formatEpochSec(epochSec: Long): String {
    val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.US)
    return sdf.format(Date(epochSec * 1000L))
}
