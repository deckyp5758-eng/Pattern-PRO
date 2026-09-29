package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.CompleteHubAnalysis
import com.example.model.MacroSentiment
import com.example.model.NewsImpactLevel
import com.example.model.PatternBias
import com.example.model.Timeframe
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

@Composable
fun MtfMatrixScreen(
    analysis: CompleteHubAnalysis,
    onSelectTimeframe: (Timeframe) -> Unit,
    modifier: Modifier = Modifier
) {
    val mtf = analysis.mtfEngine

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .testTag("mtf_matrix_list"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Kartu Ringkasan Prioritas Multi-Timeframe (H1 -> M30 -> M15 -> M5 -> M1)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, GoldPrimary, RoundedCornerShape(16.dp))
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
                        Text(
                            text = "10. MESIN MULTI-TIMEFRAME (H1 ↓ M1)",
                            style = MaterialTheme.typography.titleMedium,
                            color = GoldBright
                        )
                        Surface(
                            color = if (mtf.isPullbackInHtfTrend) NeutralBg else BullishBg,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.border(
                                1.dp,
                                if (mtf.isPullbackInHtfTrend) NeutralAmber else BullishEmerald,
                                RoundedCornerShape(8.dp)
                            )
                        ) {
                            Text(
                                text = if (mtf.isPullbackInHtfTrend) "SEDANG PULLBACK" else "SEARAH TF BESAR",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (mtf.isPullbackInHtfTrend) NeutralAmber else BullishEmerald,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = "Urutan Prioritas: H1 → M30 → M15 (Penentu Arah Utama) → M5 → M1 (Konfirmasi)",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanSecondary
                    )

                    HorizontalDivider(color = SlateCardBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Arah TF Besar (H1/M30/M15)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text(
                                text = mtf.htfBias.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = when (mtf.htfBias) {
                                    PatternBias.BULLISH -> BullishEmerald
                                    PatternBias.BEARISH -> BearishCrimson
                                    PatternBias.NEUTRAL -> GoldBright
                                }
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Kondisi TF Kecil (M5/M1)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text(
                                text = mtf.ltfState,
                                style = MaterialTheme.typography.labelLarge,
                                color = CyanSecondary
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SlateSurfaceElevated)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "Kesimpulan MTF: ${mtf.executiveConclusion}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        // Baris Prioritas H1 -> M30 -> M15 -> M5 -> M1
        items(mtf.rows, key = { it.timeframe.name }) { row ->
            val isCurrentTf = row.timeframe == analysis.activeTimeframe
            val biasColor = when (row.bias) {
                PatternBias.BULLISH -> BullishEmerald
                PatternBias.BEARISH -> BearishCrimson
                PatternBias.NEUTRAL -> NeutralAmber
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCurrentTf) SlateSurfaceElevated else SlateSurface
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (isCurrentTf) 1.5.dp else 1.dp,
                            color = if (isCurrentTf) CyanSecondary else SlateCardBorder,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onSelectTimeframe(row.timeframe) }
                        .testTag("mtf_row_${row.timeframe.label}")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(GoldPrimary)
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${row.timeframe.label} (Prioritas #${row.timeframe.priorityRank})",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = ObsidianBg
                                    )
                                }
                                if (isCurrentTf) {
                                    Text(
                                        text = "• GRAFIK AKTIF",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CyanSecondary
                                    )
                                }
                            }

                            Surface(
                                color = biasColor.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.border(1.dp, biasColor, RoundedCornerShape(8.dp))
                            ) {
                                Text(
                                    text = row.bias.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = biasColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = SlateCardBorder)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Mesin Tren", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text(row.trend.label, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Struktur Pasar", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text(row.structure.label, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Pola Candle Tutup [1]", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text(row.closedPatternName, style = MaterialTheme.typography.labelMedium, color = GoldBright)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Momentum & S/R", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text(
                                    "RSI ${String.format(Locale.US, "%.0f", row.rsi14)} | ADX ${String.format(Locale.US, "%.0f", row.adx14)} | ${row.srStatus}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyanSecondary
                                )
                            }
                        }
                    }
                }

                if (row.timeframe != Timeframe.M1) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = GoldPrimary.copy(alpha = 0.7f),
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(18.dp)
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun NewsEngineScreen(
    analysis: CompleteHubAnalysis,
    onRefreshNews: () -> Unit,
    onAddCustomEvent: (String, String, String, NewsImpactLevel, MacroSentiment, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val newsEngine = analysis.newsEngine
    var showAddDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .testTag("news_engine_list"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // 11. MESIN BERITA (KOMPONEN WAJIB)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, GoldPrimary, RoundedCornerShape(16.dp))
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
                                text = "11. MESIN BERITA EKONOMI (WAJIB)",
                                style = MaterialTheme.typography.titleMedium,
                                color = GoldBright
                            )
                            Text(
                                text = "Fokus ${analysis.symbol.code}: USD, Federal Reserve, FOMC, Suku Bunga, Inflasi CPI, NFP, ECB",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanSecondary
                            )
                        }
                        Surface(
                            color = BearishBg,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.border(1.dp, BearishCrimson, RoundedCornerShape(8.dp))
                        ) {
                            Text(
                                text = "${newsEngine.highImpactCount} DAMPAK TINGGI",
                                style = MaterialTheme.typography.labelSmall,
                                color = BearishCrimson,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = "Sentimen Makro Dominan: ${newsEngine.dominantSentiment.label}",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onRefreshNews,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("sync_live_news_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = CyanSecondary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Perbarui Berita", style = MaterialTheme.typography.labelMedium, color = CyanSecondary)
                        }

                        Button(
                            onClick = { showAddDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GoldPrimary,
                                contentColor = ObsidianBg
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("add_custom_news_button")
                        ) {
                            Icon(Icons.Default.AddAlert, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Catat Jadwal FOMC/CPI", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // Daftar Berita Ekonomi
        items(newsEngine.articles, key = { it.id }) { article ->
            val impactColor = when (article.impact) {
                NewsImpactLevel.HIGH -> BearishCrimson
                NewsImpactLevel.MEDIUM -> NeutralAmber
                NewsImpactLevel.LOW -> CyanSecondary
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceElevated),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SlateCardBorder, RoundedCornerShape(14.dp))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = article.categoryTag,
                            style = MaterialTheme.typography.labelMedium,
                            color = GoldBright
                        )
                        Surface(
                            color = impactColor.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.border(1.dp, impactColor, RoundedCornerShape(6.dp))
                        ) {
                            Text(
                                text = article.impact.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = impactColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = article.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = article.keyTakeaway,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )

                    HorizontalDivider(color = SlateCardBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = article.sentiment.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanSecondary
                        )
                        Text(
                            text = "${article.sourceName} • ${formatEpochSec(article.publishedTimeEpochSec)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    if (showAddDialog) {
        var titleInput by remember { mutableStateOf("") }
        var categoryInput by remember { mutableStateOf("Federal Reserve • Suku Bunga FOMC") }
        var takeawayInput by remember { mutableStateOf("") }
        var selectedImpact by remember { mutableStateOf(NewsImpactLevel.HIGH) }
        var selectedSentiment by remember { mutableStateOf(MacroSentiment.DOVISH_USD_BULLISH_GOLD) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Tambah Jadwal Berita Ekonomi") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("Judul Berita (misal: Pengumuman Suku Bunga FOMC)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("news_title_input")
                    )
                    OutlinedTextField(
                        value = categoryInput,
                        onValueChange = { categoryInput = it },
                        label = { Text("Kategori (USD, FOMC, Suku Bunga, CPI, ECB)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = takeawayInput,
                        onValueChange = { takeawayInput = it },
                        label = { Text("Catatan Dampak ke Pasar") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NewsImpactLevel.entries.forEach { imp ->
                            FilterChip(
                                selected = selectedImpact == imp,
                                onClick = { selectedImpact = imp },
                                label = { Text(imp.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddCustomEvent(
                            titleInput,
                            categoryInput,
                            "BOTH",
                            selectedImpact,
                            selectedSentiment,
                            takeawayInput
                        )
                        showAddDialog = false
                    },
                    modifier = Modifier.testTag("confirm_add_news_button")
                ) {
                    Text("Simpan Berita")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}
