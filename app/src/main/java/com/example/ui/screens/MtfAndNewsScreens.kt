package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.sp
import com.example.model.CompleteHubAnalysis
import com.example.model.MarketSymbol
import com.example.model.NewsImpactLevel
import com.example.model.PatternBias
import com.example.model.Timeframe
import com.example.ui.theme.BearishBg
import com.example.ui.theme.BearishCrimson
import com.example.ui.theme.BullishEmerald
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.GoldBright
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NeutralAmber
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
    val bullCount = mtf.rows.count { it.bias == PatternBias.BULLISH }
    val bearCount = mtf.rows.count { it.bias == PatternBias.BEARISH }
    val neutralCount = mtf.rows.count { it.bias == PatternBias.NEUTRAL }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .testTag("mtf_matrix_list"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // 1. KARTU RINGKASAN EKSEKUTIF MULTI-TIMEFRAME (H1 -> M30 -> M15 -> M5 -> M1)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, GoldPrimary, RoundedCornerShape(16.dp))
                    .testTag("mtf_executive_card")
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
                                imageVector = Icons.Default.Timeline,
                                contentDescription = null,
                                tint = GoldBright,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "MATRIKS MULTI-TIMEFRAME (H1 ↓ M1)",
                                style = MaterialTheme.typography.titleMedium,
                                color = GoldBright,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        val badgeColor = when {
                            mtf.isPullbackInHtfTrend -> NeutralAmber
                            mtf.htfBias == PatternBias.BULLISH -> BullishEmerald
                            mtf.htfBias == PatternBias.BEARISH -> BearishCrimson
                            else -> GoldBright
                        }
                        val badgeLabel = when {
                            mtf.isPullbackInHtfTrend -> "SEDANG PULLBACK"
                            mtf.htfBias == PatternBias.BULLISH -> "HTF BULLISH NAIK"
                            mtf.htfBias == PatternBias.BEARISH -> "HTF BEARISH TURUN"
                            else -> "KONSOLIDASI"
                        }

                        Surface(
                            color = badgeColor.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.border(1.dp, badgeColor, RoundedCornerShape(8.dp))
                        ) {
                            Text(
                                text = badgeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "Instrumen: ${analysis.symbol.code} • Timeframe Aktif: ${analysis.activeTimeframe.label} • Urutan Prioritas: H1 → M30 → M15 → M5 → M1",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanSecondary
                    )

                    HorizontalDivider(color = SlateCardBorder)

                    // Keselarasan 5 Timeframe
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                color = BullishEmerald.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.border(1.dp, BullishEmerald.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            ) {
                                Text(
                                    text = "$bullCount/5 NAIK",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BullishEmerald,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Surface(
                                color = BearishCrimson.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.border(1.dp, BearishCrimson.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            ) {
                                Text(
                                    text = "$bearCount/5 TURUN",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BearishCrimson,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            if (neutralCount > 0) {
                                Surface(
                                    color = NeutralAmber.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.border(1.dp, NeutralAmber.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                ) {
                                    Text(
                                        text = "$neutralCount/5 NETRAL",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NeutralAmber,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = mtf.ltfState,
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SlateSurfaceElevated)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "Kesimpulan Eksekutif MTF: ${mtf.executiveConclusion}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        // 2. RADAR HEATMAP 5-TIMEFRAME (COMPACT NO-SCROLL GRID)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateSurface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SlateCardBorder, RoundedCornerShape(14.dp))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RADAR HEATMAP 5-TIMEFRAME",
                            style = MaterialTheme.typography.labelMedium,
                            color = GoldBright,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Ketuk TF untuk Beralih",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanSecondary
                        )
                    }

                    // Grid 5 Kolom pas 100% lebar layar HP tanpa geser kiri-kanan
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        mtf.rows.forEach { row ->
                            val isCurrentTf = row.timeframe == analysis.activeTimeframe
                            val biasColor = when (row.bias) {
                                PatternBias.BULLISH -> BullishEmerald
                                PatternBias.BEARISH -> BearishCrimson
                                PatternBias.NEUTRAL -> NeutralAmber
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isCurrentTf) SlateSurfaceElevated else ObsidianBg)
                                    .border(
                                        width = if (isCurrentTf) 1.5.dp else 1.dp,
                                        color = if (isCurrentTf) GoldPrimary else SlateCardBorder,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onSelectTimeframe(row.timeframe) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // TF Header
                                Text(
                                    text = row.timeframe.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isCurrentTf) GoldBright else TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )

                                // Bias Block
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(biasColor.copy(alpha = 0.2f))
                                        .border(0.5.dp, biasColor, RoundedCornerShape(4.dp))
                                        .padding(vertical = 3.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = when (row.bias) {
                                            PatternBias.BULLISH -> "BULL"
                                            PatternBias.BEARISH -> "BEAR"
                                            PatternBias.NEUTRAL -> "NEU"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = biasColor,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 9.sp
                                    )
                                }

                                // Tren EMA Block
                                val isTrendUp = row.trend.name.contains("BULL", ignoreCase = true) || row.trend.name.contains("UP", ignoreCase = true)
                                Text(
                                    text = if (isTrendUp) "▲ EMA" else "▼ EMA",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isTrendUp) BullishEmerald else BearishCrimson,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                // RSI Value
                                Text(
                                    text = "RSI ${row.rsi14.toInt()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (row.rsi14 >= 70) BearishCrimson else if (row.rsi14 <= 30) BullishEmerald else CyanSecondary,
                                    fontSize = 9.sp
                                )

                                if (isCurrentTf) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(GoldPrimary)
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "💡 Pandangan Cepat 1 Detik: Hijau seragam = Konfluensi Bullish Penuh | Merah seragam = Konfluensi Bearish Penuh.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // 3. KARTU DETAIL TIAP TIMEFRAME (HIERARKI H1 -> M30 -> M15 -> M5 -> M1)
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
                                        color = ObsidianBg,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (isCurrentTf) {
                                    Text(
                                        text = "• GRAFIK AKTIF",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CyanSecondary,
                                        fontWeight = FontWeight.Bold
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
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontWeight = FontWeight.Bold
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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CandlestickChart,
                                contentDescription = null,
                                tint = CyanSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Ketuk untuk buka grafik ${row.timeframe.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanSecondary
                            )
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

private enum class NewsFilterOption(val label: String) {
    ALL("Semua"),
    HIGH_IMPACT("Dampak Tinggi"),
    XAUUSD("Emas (XAUUSD)"),
    EURUSD("Euro (EURUSD)")
}

@Composable
fun NewsEngineScreen(
    analysis: CompleteHubAnalysis,
    modifier: Modifier = Modifier
) {
    val newsEngine = analysis.newsEngine
    var selectedFilter by remember { mutableStateOf(NewsFilterOption.ALL) }

    val filteredArticles = when (selectedFilter) {
        NewsFilterOption.ALL -> newsEngine.articles
        NewsFilterOption.HIGH_IMPACT -> newsEngine.articles.filter { it.impact == NewsImpactLevel.HIGH }
        NewsFilterOption.XAUUSD -> newsEngine.articles.filter { MarketSymbol.XAUUSD in it.affectedSymbols }
        NewsFilterOption.EURUSD -> newsEngine.articles.filter { MarketSymbol.EURUSD in it.affectedSymbols }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .testTag("news_engine_list"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // 1. KARTU UTAMA MESIN BERITA MAKRO & KALENDER OTOMATIS
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, GoldPrimary, RoundedCornerShape(16.dp))
                    .testTag("news_header_card")
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
                                text = "11. KALENDER & BERITA MAKRO OTOMATIS",
                                style = MaterialTheme.typography.titleMedium,
                                color = GoldBright,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Fokus ${analysis.symbol.code}: Federal Reserve, FOMC, Suku Bunga, CPI, NFP, ECB & Geopolitik",
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
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "Sentimen Makro Dominan: ${newsEngine.dominantSentiment.label}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )

                    HorizontalDivider(color = SlateCardBorder)

                    // Indikator Sinkronisasi Otomatis & Pembersihan Usang Aktif
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
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                tint = if (newsEngine.isLiveRssSynced) BullishEmerald else NeutralAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (newsEngine.isLiveRssSynced) "Smart Auto-Sync Aktif • Kalender & Berita Riil Terisi Otomatis" else "Mode Cadangan Offline",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (newsEngine.isLiveRssSynced) BullishEmerald else NeutralAmber,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = formatEpochSec(newsEngine.lastUpdatedMs / 1000L),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }

                    Text(
                        text = "✓ Berita & jadwal yang telah usang (>24 jam) otomatis dibersihkan. Kalender ekonomi mingguan selalu segar.",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanSecondary
                    )
                }
            }
        }

        // 2. PERINGATAN RISIKO VOLATILITAS MAKRO & SPREAD (JIKA ADA 2+ BERITA HIGH IMPACT)
        if (newsEngine.newsRiskWarning != null || newsEngine.highImpactCount >= 2) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateSurface),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, NeutralAmber, RoundedCornerShape(14.dp))
                        .testTag("news_risk_warning_card")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = NeutralAmber,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "PELINDUNG RISIKO MAKRO & PELEBARAN SPREAD",
                                style = MaterialTheme.typography.labelMedium,
                                color = NeutralAmber,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = newsEngine.newsRiskWarning
                                    ?: "Terdapat ${newsEngine.highImpactCount} agenda berita berdampak tinggi aktif. Perlebar jarak batas risiko (Stop Loss berbasis ATR) dan jangan mengeksekusi saat candle masih berjalan [0].",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }

        // 3. FILTER CHIP KATEGORI & DAMPAK
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NewsFilterOption.entries.forEach { opt ->
                    FilterChip(
                        selected = selectedFilter == opt,
                        onClick = { selectedFilter = opt },
                        label = {
                            val count = when (opt) {
                                NewsFilterOption.ALL -> newsEngine.articles.size
                                NewsFilterOption.HIGH_IMPACT -> newsEngine.articles.count { it.impact == NewsImpactLevel.HIGH }
                                NewsFilterOption.XAUUSD -> newsEngine.articles.count { MarketSymbol.XAUUSD in it.affectedSymbols }
                                NewsFilterOption.EURUSD -> newsEngine.articles.count { MarketSymbol.EURUSD in it.affectedSymbols }
                            }
                            Text("${opt.label} ($count)", style = MaterialTheme.typography.labelSmall)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GoldPrimary,
                            selectedLabelColor = ObsidianBg
                        ),
                        modifier = Modifier.testTag("filter_chip_${opt.name.lowercase()}")
                    )
                }
            }
        }

        // 4. DAFTAR BERITA MAKRO & KALENDER EKONOMI TERSINKRONISASI
        if (filteredArticles.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Tidak ada berita aktif yang cocok dengan filter yang dipilih.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        } else {
            items(filteredArticles, key = { it.id }) { article ->
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
                        .testTag("news_item_${article.id}")
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
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontWeight = FontWeight.Bold
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = article.sentiment.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanSecondary,
                                modifier = Modifier.weight(1f)
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
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}
