package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.OnlineDataProvider
import com.example.ui.HubUiState
import com.example.ui.theme.BearishCrimson
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
fun SettingsScreen(
    uiState: HubUiState,
    onRunMultiProviderAudit: () -> Unit,
    onCleanAndResetSession: () -> Unit,
    onUpdateGitHubRepo: (String) -> Unit,
    onCheckGitHubUpdate: () -> Unit,
    onUpdateMetaApiAccountId: (String) -> Unit,
    onUpdateMetaApiAuthToken: (String) -> Unit,
    onSaveMetaApiCredentials: () -> Unit,
    onClearMetaApiCredentials: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val audit = uiState.liveAudit
    val gh = uiState.githubRelease

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Pengaturan
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = GoldBright,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "PENGATURAN SISTEM & KREDENSIAL",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 1. Kredensial Elev8 MT5 (MetaApi)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (uiState.selectedProvider == OnlineDataProvider.ELEV8_METAAPI) GoldPrimary else SlateCardBorder,
                        RoundedCornerShape(16.dp)
                    )
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = GoldBright,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "KREDENSIAL ELEV8 MT5 (DISIMPAN PERMANEN)",
                                style = MaterialTheme.typography.titleMedium,
                                color = GoldBright,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (uiState.hasCustomSavedCredentials) {
                            Text(
                                text = "TERSIMPAN DI HP",
                                style = MaterialTheme.typography.labelSmall,
                                color = BullishEmerald,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "Kredensial Anda disimpan langsung ke penyimpanan internal privat HP (SharedPreferences). Data tetap aman tersimpan saat aplikasi ditutup atau HP di-restart tanpa perlu re-build APK.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    OutlinedTextField(
                        value = uiState.metaApiAccountIdInput,
                        onValueChange = onUpdateMetaApiAccountId,
                        label = { Text("Account ID MetaApi (Elev8)", style = MaterialTheme.typography.labelSmall) },
                        placeholder = { Text("Contoh: 2f0cba6a-da2e-415f-...", style = MaterialTheme.typography.labelSmall) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_metaapi_account_id_input")
                    )

                    OutlinedTextField(
                        value = uiState.metaApiAuthTokenInput,
                        onValueChange = onUpdateMetaApiAuthToken,
                        label = { Text("API Access Token MetaApi", style = MaterialTheme.typography.labelSmall) },
                        placeholder = { Text("Token dari app.metaapi.cloud/token", style = MaterialTheme.typography.labelSmall) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_metaapi_auth_token_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onSaveMetaApiCredentials,
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBg),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_save_metaapi_button")
                        ) {
                            Text(
                                text = "SIMPAN & SAMBUNGKAN",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (uiState.hasCustomSavedCredentials || uiState.metaApiAccountIdInput.isNotBlank()) {
                            OutlinedButton(
                                onClick = onClearMetaApiCredentials,
                                modifier = Modifier.testTag("settings_clear_metaapi_button")
                            ) {
                                Text(
                                    text = "Hapus",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = BearishCrimson
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Panel Update APK Otomatis via GitHub Releases
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (gh.isUpdateAvailable) GoldBright else SlateCardBorder,
                        RoundedCornerShape(16.dp)
                    )
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "UPDATE APK VIA GITHUB RELEASES (CI/CD)",
                                style = MaterialTheme.typography.titleMedium,
                                color = GoldBright,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Versi Terpasang: ${gh.installedTag}" +
                                    if (gh.latestTag.isNotBlank()) " • Rilis GitHub Terbaru: ${gh.latestTag}" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (gh.isUpdateAvailable) BullishEmerald else TextSecondary
                            )
                        }
                        OutlinedButton(
                            onClick = onCheckGitHubUpdate,
                            enabled = !gh.isChecking,
                            modifier = Modifier.testTag("settings_check_github_update_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Cek Update GitHub",
                                tint = CyanSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (gh.isChecking) "Mengecek..." else "Cek Update",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanSecondary
                            )
                        }
                    }

                    OutlinedTextField(
                        value = gh.repository,
                        onValueChange = onUpdateGitHubRepo,
                        label = {
                            Text(
                                text = "Repositori GitHub (username/nama-repo)",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        placeholder = {
                            Text(
                                text = "Contoh: username/hub-smart-pattern-pro",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_github_repo_input")
                    )

                    Text(
                        text = gh.statusMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (gh.isUpdateAvailable) BullishEmerald else TextSecondary
                    )

                    if (gh.apkDownloadUrl.isNotBlank()) {
                        Button(
                            onClick = {
                                runCatching {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(gh.apkDownloadUrl))
                                    context.startActivity(intent)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (gh.isUpdateAvailable) BullishEmerald else GoldPrimary,
                                contentColor = ObsidianBg
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_download_github_apk_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = "Unduh APK Terbaru",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "UNDUH & UPDATE APK (${gh.apkFileName.ifEmpty { gh.latestTag }})" +
                                    if (gh.apkSizeMb > 0.0) " • ${gh.apkSizeMb} MB" else "",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 3. Kartu Verifikasi Audit 6 Pemeriksaan Koneksi Online
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.5.dp,
                        if (audit.isAllSixVerifiedAndLive) BullishEmerald else GoldPrimary,
                        RoundedCornerShape(16.dp)
                    )
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = if (audit.isAllSixVerifiedAndLive) BullishEmerald else GoldBright,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "AUDIT 6 PEMERIKSAAN KONEKSI ONLINE",
                                style = MaterialTheme.typography.titleMedium,
                                color = GoldBright,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = onCleanAndResetSession,
                                modifier = Modifier.testTag("settings_clean_reset_session_button")
                            ) {
                                Text("Bersihkan Cache", style = MaterialTheme.typography.labelSmall, color = GoldBright)
                            }
                            OutlinedButton(
                                onClick = onRunMultiProviderAudit,
                                modifier = Modifier.testTag("settings_run_six_point_audit_button")
                            ) {
                                Text("Uji 6 Poin", style = MaterialTheme.typography.labelSmall, color = CyanSecondary)
                            }
                        }
                    }

                    Text(
                        text = "Endpoint Aktif: ${audit.lastEndpointUrl}",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanSecondary
                    )

                    AuditCheckRowSettings("1. Endpoint Dapat Diakses", audit.check1EndpointAccessible, audit.check1Detail)
                    AuditCheckRowSettings("2. Data Asli dari Server/Provider", audit.check2RealServerData, audit.check2Detail)
                    AuditCheckRowSettings("3. Timestamp Server Berubah", audit.check3TimestampUpdated, audit.check3Detail)
                    AuditCheckRowSettings("4. Data Berubah Saat Ganti Sumber", audit.check4ChangesAcrossProviders, audit.check4Detail)
                    AuditCheckRowSettings("5. Error Koneksi Ditampilkan", audit.check5ErrorDisplayedProperly, audit.check5Detail)
                    AuditCheckRowSettings("6. Jujur Ketika Provider Offline", audit.check6HonestWhenOffline, audit.check6Detail)
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun AuditCheckRowSettings(
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
