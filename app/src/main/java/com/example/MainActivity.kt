package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.HubRepository
import com.example.data.local.HubDatabase
import com.example.model.OnlineDataProvider
import com.example.ui.HubTab
import com.example.ui.HubViewModel
import com.example.ui.screens.HudTerminalScreen
import com.example.ui.screens.Mql5StudioScreen
import com.example.ui.screens.MtfMatrixScreen
import com.example.ui.screens.NewsEngineScreen
import com.example.ui.screens.TenEnginesScreen
import com.example.ui.theme.BearishBg
import com.example.ui.theme.BearishCrimson
import com.example.ui.theme.BullishBg
import com.example.ui.theme.BullishEmerald
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.GoldBright
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateCardBorder
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                HubSmartPatternApp()
            }
        }
    }
}

@Composable
fun HubSmartPatternApp() {
    val context = LocalContext.current
    val repository = remember {
        val db = HubDatabase.getInstance(context)
        HubRepository(db.hubDao())
    }
    val viewModel: HubViewModel = viewModel(factory = HubViewModel.Factory(repository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val savedLogs by viewModel.savedSignalLogs.collectAsStateWithLifecycle()

    if (uiState.selectedTab != HubTab.HUD_CHART) {
        BackHandler {
            viewModel.selectTab(HubTab.HUD_CHART)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ObsidianBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar(
                containerColor = SlateSurface,
                tonalElevation = 8.dp
            ) {
                HubTab.entries.forEach { tab ->
                    val isSelected = uiState.selectedTab == tab
                    val icon = when (tab) {
                        HubTab.HUD_CHART -> Icons.Default.CandlestickChart
                        HubTab.ENGINES -> Icons.Default.Analytics
                        HubTab.MTF_MATRIX -> Icons.Default.Layers
                        HubTab.NEWS_ENGINE -> Icons.Default.Newspaper
                        HubTab.MQL5_STUDIO -> Icons.Default.Code
                    }
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { viewModel.selectTab(tab) },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = tab.title
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ObsidianBg,
                            selectedTextColor = GoldBright,
                            indicatorColor = GoldPrimary,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Banner Status / Peringatan Koneksi
            AnimatedVisibility(visible = uiState.statusBannerMessage != null) {
                uiState.statusBannerMessage?.let { msg ->
                    val isError = uiState.connectionErrorMessage != null || !uiState.isProviderOnline
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isError) BearishBg else BullishBg)
                            .border(
                                1.dp,
                                if (isError) BearishCrimson else BullishEmerald,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { viewModel.dismissBannerMessage() }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isError) BearishCrimson else BullishEmerald,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "TUTUP",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanSecondary
                        )
                    }
                }
            }

            val analysis = uiState.analysis
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = GoldPrimary)
                            Text(
                                text = "Menghubungi Server Online ${uiState.selectedProvider.displayName}...",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary
                            )
                        }
                    }
                }

                // PEMERIKSAAN #5 & #6: Tampilan Jujur Saat Provider Offline / Error (Tanpa Data Simulasi!)
                analysis == null || !uiState.isProviderOnline -> {
                    HonestOfflineErrorState(
                        provider = uiState.selectedProvider,
                        endpointUrl = uiState.liveAudit.lastEndpointUrl,
                        errorMessage = uiState.connectionErrorMessage
                            ?: "Provider tidak dapat dijangkau atau mengembalikan respons kosong.",
                        onRetry = { viewModel.refreshMarketData(forceRefreshNews = true) },
                        onSwitchProvider = viewModel::selectProvider
                    )
                }

                else -> {
                    when (uiState.selectedTab) {
                        HubTab.HUD_CHART -> HudTerminalScreen(
                            uiState = uiState,
                            analysis = analysis,
                            savedLogs = savedLogs,
                            onSelectProvider = viewModel::selectProvider,
                            onSelectSymbol = viewModel::selectSymbol,
                            onSelectTimeframe = viewModel::selectTimeframe,
                            onSelectSuffix = viewModel::updateBrokerSuffix,
                            onInspectBar = viewModel::inspectCandleBar,
                            onToggleEma = viewModel::toggleEmaOverlays,
                            onToggleSr = viewModel::toggleSrZones,
                            onToggleSwings = viewModel::toggleStructureSwings,
                            onToggleLiveTick = viewModel::toggleLiveTickMonitor,
                            onRunMultiProviderAudit = viewModel::runBackgroundMultiProviderAudit,
                            onCleanAndResetSession = viewModel::cleanAndResetDataSession,
                            onRefreshData = { viewModel.refreshMarketData(forceRefreshNews = false) },
                            onSaveSnapshot = viewModel::saveCurrentClosedSignalToLog,
                            onDeleteSnapshot = viewModel::deleteSignalLog,
                            onClearAllSnapshots = viewModel::clearAllSignalLogs,
                            onUpdateGitHubRepo = viewModel::updateGitHubRepoInput,
                            onCheckGitHubUpdate = viewModel::checkGitHubReleaseUpdate
                        )

                        HubTab.ENGINES -> TenEnginesScreen(
                            analysis = analysis,
                            diagnostics = uiState.diagnostics,
                            onCleanAndResetSession = viewModel::cleanAndResetDataSession
                        )

                        HubTab.MTF_MATRIX -> MtfMatrixScreen(
                            analysis = analysis,
                            onSelectTimeframe = { tf ->
                                viewModel.selectTimeframe(tf)
                                viewModel.selectTab(HubTab.HUD_CHART)
                            }
                        )

                        HubTab.NEWS_ENGINE -> NewsEngineScreen(
                            analysis = analysis,
                            onRefreshNews = { viewModel.refreshMarketData(forceRefreshNews = true) },
                            onAddCustomEvent = viewModel::addCustomNewsEvent
                        )

                        HubTab.MQL5_STUDIO -> Mql5StudioScreen(
                            config = uiState.mql5Config,
                            generatedCode = uiState.generatedMql5Code,
                            onUpdateConfig = viewModel::updateMql5Config
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HonestOfflineErrorState(
    provider: OnlineDataProvider,
    endpointUrl: String,
    errorMessage: String,
    onRetry: () -> Unit,
    onSwitchProvider: (OnlineDataProvider) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp)
            .testTag("honest_offline_error_screen"),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateSurface),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, BearishCrimson, RoundedCornerShape(18.dp))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = "Provider Offline",
                    tint = BearishCrimson,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "STATUS: PROVIDER OFFLINE / GAGAL TERHUBUNG",
                    style = MaterialTheme.typography.titleMedium,
                    color = BearishCrimson,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Aplikasi tetap jujur: TIDAK menampilkan grafik/data simulasi lokal ketika sumber online (${provider.displayName}) tidak dapat diakses.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(BearishBg.copy(alpha = 0.6f))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Endpoint: $endpointUrl",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldBright
                        )
                        Text(
                            text = "Detail Error: $errorMessage",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Pilih Sumber Server Online Aktif atau Coba Lagi:",
                    style = MaterialTheme.typography.labelMedium,
                    color = CyanSecondary
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OnlineDataProvider.realProviders.forEach { realProv ->
                        OutlinedButton(
                            onClick = { onSwitchProvider(realProv) },
                            modifier = Modifier.testTag("switch_to_${realProv.id}")
                        ) {
                            Text(
                                text = realProv.shortLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanSecondary
                            )
                        }
                    }
                }

                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldPrimary,
                        contentColor = ObsidianBg
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("retry_online_connection_button")
                ) {
                    Text("Coba Hubungkan Ulang ke ${provider.shortLabel}", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
