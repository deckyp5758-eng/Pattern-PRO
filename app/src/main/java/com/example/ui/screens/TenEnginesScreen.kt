package com.example.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.SystemDiagnosticReport
import com.example.model.AnalyticalVerdict
import com.example.model.CandlestickPatternType
import com.example.model.CompleteHubAnalysis
import com.example.model.PatternBias
import com.example.ui.theme.BearishCrimson
import com.example.ui.theme.BullishEmerald
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.GoldBright
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NeutralAmber
import com.example.ui.theme.SlateCardBorder
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun TenEnginesScreen(
    analysis: CompleteHubAnalysis,
    diagnostics: SystemDiagnosticReport,
    onCleanAndResetSession: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val digits = analysis.symbol.digits
    val dEngine = analysis.dataEngine
    val cEngine = analysis.candleEngine
    val sEngine = analysis.structureEngine
    val tEngine = analysis.trendEngine
    val mEngine = analysis.momentumEngine
    val vEngine = analysis.volatilityEngine
    val srEngine = analysis.srEngine
    val volEngine = analysis.volumeEngine
    val mtfEngine = analysis.mtfEngine
    val newsEngine = analysis.newsEngine

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .testTag("ten_engines_list"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // HEADER RINGKASAN AUDIT MESIN
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GoldPrimary.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .testTag("engines_audit_header_card")
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
                            text = "AUDIT 10 MESIN KUANTITATIF",
                            style = MaterialTheme.typography.titleMedium,
                            color = GoldBright,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            color = BullishEmerald.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.border(1.dp, BullishEmerald.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        ) {
                            Text(
                                text = "100% NON-REPAINT",
                                style = MaterialTheme.typography.labelSmall,
                                color = BullishEmerald,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = "Instrumen: ${analysis.symbol.code} (${analysis.symbol.displayName}) • Timeframe: ${analysis.activeTimeframe.label} (${analysis.activeTimeframe.mt5EnumName})",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Text(
                        text = "Semua perhitungan sinyal dikunci mutlak pada Candle Tutup Indeks [1]. Candle [0] hanya sebagai pemantau harga berjalan tanpa mengubah status sinyal historis.",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanSecondary
                    )
                }
            }
        }

        // 0. PANEL DIAGNOSTIK & PENGAMAN ANTI-TUMPANG-TINDIH / ANTI-DOBEL REQUEST
        item {
            EngineSectionCard(
                engineNumber = "DIAG",
                title = "PENGAMAN DATA & DIAGNOSTIK SISTEM",
                badgeText = if (diagnostics.isDataConsistent) "BEBAS TUMPANG TINDIH" else "PERIKSA DATA",
                badgeColor = if (diagnostics.isDataConsistent) BullishEmerald else BearishCrimson
            ) {
                EngineRow("Token Generasi Sesi Aktif", "Gen #${diagnostics.activeGenerationId} (${analysis.symbol.code} • ${analysis.activeTimeframe.label})")
                EngineRow("Kunci Integritas Candle [1]", diagnostics.bar1IntegrityHash)
                EngineRow("Kalkulasi Penuh Candle [1]", "${diagnostics.totalFullCalculations} kali (Hanya saat candle tutup/ganti TF)")
                EngineRow("Update Ringan Candle [0]", "${diagnostics.lightweightBar0Ticks} tick (Tanpa hitung ulang sinyal [1])")
                EngineRow("Dobel Request Diblokir", "${diagnostics.blockedDuplicateRequests} permintaan ganda dicegah")
                EngineRow("Respons Usang Dibuang", "${diagnostics.discardedStaleResponses} paket usang diabaikan (Anti-Tumpang-Tindih)")
                EngineRow("Pembersihan Cache & Sinkron", "${diagnostics.cachePurgeCount}x dibersihkan • Durasi: ${diagnostics.lastSyncDurationMs} ms")

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(BullishEmerald.copy(alpha = 0.12f))
                        .border(1.dp, BullishEmerald.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "✓ Self-Healing & Pembersihan Cache Otomatis Aktif: Sesi & memori dibersihkan otomatis saat ganti pair/provider tanpa risiko tumpang tindih.",
                        style = MaterialTheme.typography.labelSmall,
                        color = BullishEmerald
                    )
                }

                HorizontalDivider(color = SlateCardBorder, modifier = Modifier.padding(vertical = 4.dp))
                diagnostics.integrityNotes.forEach { note ->
                    Text(
                        text = "✓ $note",
                        style = MaterialTheme.typography.labelSmall,
                        color = BullishEmerald
                    )
                }

                if (diagnostics.recentEventsLog.isNotEmpty()) {
                    HorizontalDivider(color = SlateCardBorder, modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "Log Aktivitas & Pelacak Kesalahan Real-time:",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanSecondary
                    )
                    diagnostics.recentEventsLog.take(5).forEach { logLine ->
                        Text(
                            text = logLine,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // 1. PRINSIP UTAMA (NON-REPAINT & HANYA CANDLE TUTUP)
        item {
            EngineSectionCard(
                engineNumber = "01",
                title = "PRINSIP UTAMA (SISTEM NON-REPAINT)",
                badgeText = "HANYA CANDLE TUTUP",
                badgeColor = BullishEmerald
            ) {
                EngineRow("Dasar Sinyal Final", "Candle Indeks [1] (Candle yang Sudah Close)")
                EngineRow("Fungsi Candle [0]", "Hanya Memantau Harga Berjalan (Bukan Sinyal)")
                EngineRow("Data Masa Depan (Look-Ahead)", "0% (Murni Data Historis yang Sudah Tutup)")
                EngineRow("ZigZag Repaint", "Tidak Digunakan (Pakai Titik Swing Terkonfirmasi)")
                EngineRow("Jenis Sistem", "Indikator Analitik V1 (Tanpa Martingale/Grid/Auto-Trade)")
            }
        }

        // 2. DATA ENGINE (MT5 CopyRates / CopyBuffer Indexing)
        item {
            EngineSectionCard(
                engineNumber = "02",
                title = "MESIN DATA (DATA ENGINE MT5 • KIRI KE KANAN)",
                badgeText = "${dEngine.totalBarsLoaded} CANDLE",
                badgeColor = CyanSecondary
            ) {
                EngineRow("Simbol & Timeframe", "${dEngine.symbolWithSuffix} • ${dEngine.timeframe.mt5EnumName}")
                EngineRow(
                    "Candle [2] (Kiri / Sebelumnya)",
                    "Buka:${formatPrice(dEngine.previousClosedCandle2.open, digits)} Tutup:${formatPrice(dEngine.previousClosedCandle2.close, digits)}"
                )
                EngineRow(
                    "Candle [1] (Tutup Terakhir • Sinyal)",
                    "O:${formatPrice(dEngine.closedCandle1.open, digits)} H:${formatPrice(dEngine.closedCandle1.high, digits)} L:${formatPrice(dEngine.closedCandle1.low, digits)} C:${formatPrice(dEngine.closedCandle1.close, digits)}"
                )
                EngineRow(
                    "Candle [0] (Ujung Kanan • Berjalan)",
                    "O:${formatPrice(dEngine.formingCandle0.open, digits)} H:${formatPrice(dEngine.formingCandle0.high, digits)} L:${formatPrice(dEngine.formingCandle0.low, digits)} C:${formatPrice(dEngine.formingCandle0.close, digits)}"
                )
                EngineRow(
                    "Volume & Spread [Candle 1]",
                    "TickVol: ${dEngine.closedCandle1.tickVolume} | RealVol: ${dEngine.closedCandle1.realVolume} | Spread: ${dEngine.closedCandle1.spread} poin"
                )
            }
        }

        // 3. CANDLE ENGINE (ANATOMI + 15 POLA CANDLESTICK)
        item {
            EngineSectionCard(
                engineNumber = "03",
                title = "MESIN CANDLE & 15 POLA [CANDLE 1]",
                badgeText = cEngine.anatomyType.label,
                badgeColor = GoldBright
            ) {
                EngineRow("Ukuran Bodi [Candle 1]", "${cEngine.bodyPoints.toInt()} poin (${String.format(Locale.US, "%.1f", cEngine.bodyToRangePct)}% dari total rentang)")
                EngineRow("Ekor Atas / Ekor Bawah [1]", "Atas: ${cEngine.upperWickPoints.toInt()} poin | Bawah: ${cEngine.lowerWickPoints.toInt()} poin")
                EngineRow("Total Rentang & Rasio Ekor", "Rentang: ${cEngine.rangePoints.toInt()} poin | Ekor/Bodi: ${String.format(Locale.US, "%.2f", cEngine.wickToBodyRatio)}x")

                HorizontalDivider(color = SlateCardBorder, modifier = Modifier.padding(vertical = 6.dp))

                Text(
                    text = "DETEKSI 15 POLA CANDLESTICK (HANYA CANDLE YANG SUDAH TUTUP)",
                    style = MaterialTheme.typography.labelMedium,
                    color = CyanSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))

                val detectedByType = cEngine.recentHistoricalPatterns.groupBy { it.pattern }
                CandlestickPatternType.entries.forEach { patType ->
                    val hits = detectedByType[patType].orEmpty()
                    val latestHit = hits.minByOrNull { it.closedBarIndex }
                    val isBar1Hit = latestHit?.closedBarIndex == 1

                    val statusLabel = when {
                        isBar1Hit -> "AKTIF DI CANDLE TUTUP [1]"
                        latestHit != null -> "Muncul di Candle Tutup [${latestHit.closedBarIndex}]"
                        else -> "Belum Muncul"
                    }
                    val statusColor = when {
                        isBar1Hit && patType.defaultBias == PatternBias.BULLISH -> BullishEmerald
                        isBar1Hit && patType.defaultBias == PatternBias.BEARISH -> BearishCrimson
                        isBar1Hit -> GoldBright
                        latestHit != null -> CyanSecondary
                        else -> TextSecondary.copy(alpha = 0.6f)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${patType.id}. ${patType.displayName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (latestHit != null) TextPrimary else TextSecondary
                        )
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = if (isBar1Hit) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // 4. MARKET STRUCTURE ENGINE
        item {
            EngineSectionCard(
                engineNumber = "04",
                title = "MESIN STRUKTUR PASAR (HH/HL/BOS/CHOCH)",
                badgeText = sEngine.classification.label,
                badgeColor = CyanSecondary
            ) {
                EngineRow("Klasifikasi Struktur", sEngine.classification.label)
                EngineRow("Status BOS / CHOCH", sEngine.latestBreakEvent.label)
                EngineRow(
                    "Swing High Terkonfirmasi",
                    sEngine.lastSwingHigh?.let { "${it.label.fullName} @ ${formatPrice(it.price, digits)} [Candle ${it.closedBarIndex}]" } ?: "Memindai..."
                )
                EngineRow(
                    "Swing Low Terkonfirmasi",
                    sEngine.lastSwingLow?.let { "${it.label.fullName} @ ${formatPrice(it.price, digits)} [Candle ${it.closedBarIndex}]" } ?: "Memindai..."
                )

                if (sEngine.confirmedSwings.isNotEmpty()) {
                    HorizontalDivider(color = SlateCardBorder, modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "Alur Struktur Kiri → Kanan (Lama → Baru): " +
                            sEngine.confirmedSwings.take(6).asReversed().joinToString(" → ") {
                                "${it.label.shortCode}@${formatPrice(it.price, digits)}[c${it.closedBarIndex}]"
                            },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }

        // 5. TREND ENGINE (EMA 20 / 50 / 200)
        item {
            EngineSectionCard(
                engineNumber = "05",
                title = "MESIN TREN (EMA 20 • EMA 50 • EMA 200)",
                badgeText = tEngine.classification.label,
                badgeColor = GoldPrimary
            ) {
                EngineRow("EMA 20 [Candle Tutup 1]", formatPrice(tEngine.ema20, digits))
                EngineRow("EMA 50 [Candle Tutup 1]", formatPrice(tEngine.ema50, digits))
                EngineRow("EMA 200 [Candle Tutup 1]", formatPrice(tEngine.ema200, digits))
                EngineRow(
                    "Susunan Garis EMA",
                    when {
                        tEngine.isPerfectBullishStack -> "EMA20 > EMA50 > EMA200 (Konteks Tren Naik/Buy)"
                        tEngine.isPerfectBearishStack -> "EMA20 < EMA50 < EMA200 (Konteks Tren Turun/Sell)"
                        else -> "Garis EMA Menyempit / Transisi"
                    }
                )
                EngineRow("Jarak Harga ke EMA 200", "${tEngine.priceToEma200DistancePoints.toInt()} poin")
            }
        }

        // 6. MOMENTUM ENGINE (RSI 14, MACD, ADX 14)
        item {
            EngineSectionCard(
                engineNumber = "06",
                title = "MESIN MOMENTUM (RSI 14 • MACD • ADX 14)",
                badgeText = mEngine.rsiRegime.label,
                badgeColor = if (mEngine.isTrendStrengthConfirmed) BullishEmerald else NeutralAmber
            ) {
                EngineRow("RSI (14) [Candle Tutup 1]", "${String.format(Locale.US, "%.2f", mEngine.rsi14)} → ${mEngine.rsiRegime.label}")
                EngineRow(
                    "Kekuatan Tren ADX (14)",
                    "${String.format(Locale.US, "%.2f", mEngine.adx14)} (${if (mEngine.isTrendStrengthConfirmed) "> 25 Tren Kuat Terkonfirmasi" else "<= 25 Tren Lemah/Sideways"})"
                )
                EngineRow("Arah +DI / -DI", "+DI: ${String.format(Locale.US, "%.1f", mEngine.plusDi14)} | -DI: ${String.format(Locale.US, "%.1f", mEngine.minusDi14)}")
                EngineRow(
                    "MACD (12, 26, 9)",
                    "Utama: ${String.format(Locale.US, "%.4f", mEngine.macdMain)} | Sinyal: ${String.format(Locale.US, "%.4f", mEngine.macdSignal)}"
                )
                EngineRow("Histogram & Status MACD", "${String.format(Locale.US, "%.4f", mEngine.macdHistogram)} (${mEngine.macdState.label})")
            }
        }

        // 7. VOLATILITY ENGINE (ATR 14)
        item {
            EngineSectionCard(
                engineNumber = "07",
                title = "MESIN VOLATILITAS (ATR 14 & ACUAN STOP/TARGET)",
                badgeText = vEngine.state.label,
                badgeColor = GoldBright
            ) {
                EngineRow("Nilai ATR (14)", "${formatPrice(vEngine.atr14, digits)} (${vEngine.atrPoints.toInt()} poin)")
                EngineRow("Persentase ATR terhadap Harga", "${String.format(Locale.US, "%.3f", vEngine.atrPercentageOfPrice)}%")
                EngineRow("Kondisi Volatilitas", vEngine.state.label)
                EngineRow("Jarak Acuan Stop (1.5x ATR)", formatPrice(vEngine.analyticalStopDistance, digits))
                EngineRow(
                    "Acuan Skenario Naik (Buy)",
                    "Stop: ${formatPrice(vEngine.longStopReferencePrice, digits)} | Target 1: ${formatPrice(vEngine.longTarget1RPrice, digits)} | Target 2: ${formatPrice(vEngine.longTarget2RPrice, digits)}"
                )
                EngineRow(
                    "Acuan Skenario Turun (Sell)",
                    "Stop: ${formatPrice(vEngine.shortStopReferencePrice, digits)} | Target 1: ${formatPrice(vEngine.shortTarget1RPrice, digits)} | Target 2: ${formatPrice(vEngine.shortTarget2RPrice, digits)}"
                )
                if (vEngine.warningMessage != null) {
                    HorizontalDivider(color = SlateCardBorder, modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = vEngine.warningMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = NeutralAmber
                    )
                }
            }
        }

        // 8. SUPPORT & RESISTANCE ENGINE
        item {
            EngineSectionCard(
                engineNumber = "08",
                title = "MESIN SUPPORT & RESISTANCE (ZONA KLASTER)",
                badgeText = srEngine.closedBar1Interaction.label,
                badgeColor = CyanSecondary
            ) {
                EngineRow("Reaksi Harga di Candle [1]", srEngine.closedBar1Interaction.label)
                srEngine.zones.forEachIndexed { idx, zone ->
                    val role = if (zone.isSupport) "ZONA SUPPORT" else "ZONA RESISTANCE"
                    EngineRow(
                        "#${idx + 1} $role (${zone.touchCount}x disentuh)",
                        "${formatPrice(zone.lowerBound, digits)} – ${formatPrice(zone.upperBound, digits)} (Jarak ${String.format(Locale.US, "%.1f", zone.distanceInAtr)} ATR)"
                    )
                }
            }
        }

        // 9. VOLUME ENGINE (Volume vs SMA 20)
        item {
            EngineSectionCard(
                engineNumber = "09",
                title = "MESIN VOLUME (KONFLUENSI SMA VOLUME 20)",
                badgeText = if (volEngine.isVolumeExpansion) "EKSPANSI VOLUME" else "VOLUME BIASA",
                badgeColor = if (volEngine.isVolumeExpansion) BullishEmerald else NeutralAmber
            ) {
                EngineRow("Volume Candle Tutup [1]", "${volEngine.closedBar1Volume}")
                EngineRow("Rata-rata Volume SMA (20)", "${volEngine.smaVolume20.toInt()}")
                EngineRow("Rasio Volume / SMA20", "${String.format(Locale.US, "%.2f", volEngine.volumeToSmaRatio)}x (${volEngine.classification.label})")
                EngineRow("Catatan Konfluensi", volEngine.confluenceNote)
            }
        }

        // 10. CONFLUENCE & SCORING MATRIX ENGINE (MESIN 10)
        item {
            val verdictColor = when (analysis.verdict) {
                AnalyticalVerdict.STRONG_BULLISH_CONFLUENCE,
                AnalyticalVerdict.MODERATE_BULLISH_BIAS -> BullishEmerald
                AnalyticalVerdict.STRONG_BEARISH_CONFLUENCE,
                AnalyticalVerdict.MODERATE_BEARISH_BIAS -> BearishCrimson
                AnalyticalVerdict.HTF_BULLISH_LTF_PULLBACK,
                AnalyticalVerdict.HTF_BEARISH_LTF_PULLBACK,
                AnalyticalVerdict.NEUTRAL_CONSOLIDATION -> NeutralAmber
            }

            EngineSectionCard(
                engineNumber = "10",
                title = "MESIN SKOR KONFLUENSI & VERDIK FINAL",
                badgeText = analysis.verdict.label,
                badgeColor = verdictColor
            ) {
                // Bar Skor Naik
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Skor Konfluensi Naik (Bullish)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = BullishEmerald
                        )
                        Text(
                            text = "${analysis.bullishConfluenceScore}% / 100%",
                            style = MaterialTheme.typography.labelLarge,
                            color = BullishEmerald,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (analysis.bullishConfluenceScore / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = BullishEmerald,
                        trackColor = SlateCardBorder
                    )
                }

                // Bar Skor Turun
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Skor Konfluensi Turun (Bearish)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = BearishCrimson
                        )
                        Text(
                            text = "${analysis.bearishConfluenceScore}% / 100%",
                            style = MaterialTheme.typography.labelLarge,
                            color = BearishCrimson,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (analysis.bearishConfluenceScore / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = BearishCrimson,
                        trackColor = SlateCardBorder
                    )
                }

                HorizontalDivider(color = SlateCardBorder, modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "CHECKLIST 7 KRITERIA KONFLUENSI TERKUNCI [CANDLE 1]:",
                    style = MaterialTheme.typography.labelMedium,
                    color = GoldBright
                )

                analysis.confluenceChecklist.forEach { (criterion, passed) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
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
                                contentDescription = if (passed) "Terpenuhi" else "Belum",
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

        // 11. MULTI-TIMEFRAME ENGINE
        item {
            EngineSectionCard(
                engineNumber = "11",
                title = "MESIN MULTI-TIMEFRAME (H1 • M30 • M15 • M5 • M1)",
                badgeText = mtfEngine.htfBias.label,
                badgeColor = when (mtfEngine.htfBias) {
                    PatternBias.BULLISH -> BullishEmerald
                    PatternBias.BEARISH -> BearishCrimson
                    PatternBias.NEUTRAL -> NeutralAmber
                }
            ) {
                EngineRow("Bias Timeframe Besar (H1/M30/M15)", mtfEngine.htfBias.label)
                EngineRow("Kondisi Timeframe Kecil (M5/M1)", mtfEngine.ltfState)
                EngineRow(
                    "Status Koreksi (Pullback)",
                    if (mtfEngine.isPullbackInHtfTrend) "PULLBACK AKTIF (Waspada Reversal Prematur)" else "SEARAH TREN UTAMA"
                )
                EngineRow("Kesimpulan Eksekutif", mtfEngine.executiveConclusion)

                HorizontalDivider(color = SlateCardBorder, modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = "STATUS TIAP TIMEFRAME:",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanSecondary
                )
                mtfEngine.rows.forEach { row ->
                    val rowColor = when (row.bias) {
                        PatternBias.BULLISH -> BullishEmerald
                        PatternBias.BEARISH -> BearishCrimson
                        PatternBias.NEUTRAL -> NeutralAmber
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${row.timeframe.label}: ${row.trend.label} • ${row.structure.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary
                        )
                        Text(
                            text = row.bias.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = rowColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 12. NEWS & FUNDAMENTAL ENGINE
        item {
            EngineSectionCard(
                engineNumber = "12",
                title = "MESIN MAKRO FUNDAMENTAL & BERITA EKONOMI",
                badgeText = newsEngine.dominantSentiment.label,
                badgeColor = CyanSecondary
            ) {
                EngineRow("Sentimen Dominan", newsEngine.dominantSentiment.label)
                EngineRow("Berita Dampak Tinggi Aktif", "${newsEngine.highImpactCount} Agenda Terkait ${analysis.symbol.code}")
                EngineRow("Status Sinkronisasi RSS", if (newsEngine.isLiveRssSynced) "LIVE ONLINE RSS TERVERIFIKASI" else "OFFLINE / CADANGAN")
                if (newsEngine.newsRiskWarning != null) {
                    HorizontalDivider(color = SlateCardBorder, modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = newsEngine.newsRiskWarning,
                        style = MaterialTheme.typography.labelSmall,
                        color = NeutralAmber
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun EngineSectionCard(
    engineNumber: String,
    title: String,
    badgeText: String,
    badgeColor: Color,
    content: @Composable () -> Unit
) {
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
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(GoldPrimary.copy(alpha = 0.2f))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = engineNumber,
                            style = MaterialTheme.typography.labelMedium,
                            color = GoldBright
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }

                Surface(
                    color = badgeColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.border(1.dp, badgeColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(color = SlateCardBorder)
            content()
        }
    }
}

@Composable
private fun EngineRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary,
            modifier = Modifier.weight(0.58f)
        )
    }
}
