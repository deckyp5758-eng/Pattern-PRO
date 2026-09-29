package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.Mql5ConfigParams
import com.example.ui.theme.BullishEmerald
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.GoldBright
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateCardBorder
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun Mql5StudioScreen(
    config: Mql5ConfigParams,
    generatedCode: String,
    onUpdateConfig: (Mql5ConfigParams) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var copiedFeedback by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .testTag("mql5_studio_list"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

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
                    Text(
                        text = "GENERATOR KODE INDIKATOR MT5 (.MQ5)",
                        style = MaterialTheme.typography.titleMedium,
                        color = GoldBright
                    )
                    Text(
                        text = "Nama File: HUB_Smart_Pattern_Engine_Pro.mq5 • Murni Non-Repaint Candle Tutup [Indeks 1] • Mendukung XAUUSD.sml & EURUSD.sml",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )

                    HorizontalDivider(color = SlateCardBorder)

                    Text(
                        text = "Atur Parameter Input Indikator MQL5:",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyanSecondary
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(".sml", ".pro", ".raw", "").forEach { sfx ->
                            val label = if (sfx.isEmpty()) "Standar (Tanpa Akhiran)" else "Akhiran: $sfx"
                            FilterChip(
                                selected = config.brokerSuffix == sfx,
                                onClick = { onUpdateConfig(config.copy(brokerSuffix = sfx)) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(2, 3, 5).forEach { pivot ->
                            FilterChip(
                                selected = config.swingStrength == pivot,
                                onClick = { onUpdateConfig(config.copy(swingStrength = pivot)) },
                                label = { Text("Swing Pivot: $pivot Candle", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                        FilterChip(
                            selected = config.enableNewsWebRequest,
                            onClick = { onUpdateConfig(config.copy(enableNewsWebRequest = !config.enableNewsWebRequest)) },
                            label = { Text("WebRequest Berita MT5", style = MaterialTheme.typography.labelSmall) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText("HUB_Smart_Pattern_Engine_Pro.mq5", generatedCode)
                                )
                                copiedFeedback = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GoldPrimary,
                                contentColor = ObsidianBg
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("copy_mql5_code_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (copiedFeedback) "Kode Tersalin!" else "Salin Kode .mq5",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "HUB_Smart_Pattern_Engine_Pro.mq5")
                                    putExtra(Intent.EXTRA_TEXT, generatedCode)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Bagikan File Indikator .mq5"))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("share_mql5_code_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = CyanSecondary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Kirim / Ekspor .mq5", style = MaterialTheme.typography.labelMedium, color = CyanSecondary)
                        }
                    }

                    if (copiedFeedback) {
                        Text(
                            text = "✓ Kode HUB_Smart_Pattern_Engine_Pro.mq5 berhasil disalin! Tempel (Paste) ke MetaEditor 5 lalu tekan F7 (Compile).",
                            style = MaterialTheme.typography.labelSmall,
                            color = BullishEmerald
                        )
                    }
                }
            }
        }

        // Kotak Tampilan Kode Sumber MQL5
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ObsidianBg)
                    .border(1.dp, SlateCardBorder, RoundedCornerShape(14.dp))
                    .padding(12.dp)
                    .horizontalScroll(rememberScrollState())
            ) {
                Text(
                    text = generatedCode,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary
                )
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}
