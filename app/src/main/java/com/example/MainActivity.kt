package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.ui.screens.TradeSignalScreen
import com.example.util.TradeNotificationHelper
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
        val prefs = com.example.data.local.UserPreferencesManager.getInstance(context)
        HubRepository(db.hubDao(), prefs)
    }
    val viewModel: HubViewModel = viewModel(factory = HubViewModel.Factory(repository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val savedLogs by viewModel.savedSignalLogs.collectAsStateWithLifecycle()

    // Permintaan Izin Notifikasi Otomatis saat Aplikasi Dibuka / Di-install
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Disetujui / Ditolak ditangani langsung oleh sistem Android */ }

    LaunchedEffect(Unit) {
        TradeNotificationHelper.createNotificationChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!isGranted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Alarm Notifikasi Sinyal Trade Otomatis di Layar Notifikasi HP
    LaunchedEffect(
        uiState.analysis?.dataEngine?.closedCandle1?.timeEpochSec,
        uiState.analysis?.verdict,
        uiState.isTradeNotificationEnabled
    ) {
        val analysis = uiState.analysis ?: return@LaunchedEffect
        if (uiState.isTradeNotificationEnabled) {
            val bar1 = analysis.dataEngine.closedCandle1
            val vEngine = analysis.volatilityEngine
            val isBuy = analysis.verdict in listOf(
                com.example.model.AnalyticalVerdict.STRONG_BULLISH_CONFLUENCE,
                com.example.model.AnalyticalVerdict.MODERATE_BULLISH_BIAS
            )
            val isSell = analysis.verdict in listOf(
                com.example.model.AnalyticalVerdict.STRONG_BEARISH_CONFLUENCE,
                com.example.model.AnalyticalVerdict.MODERATE_BEARISH_BIAS
            )
            if (isBuy || isSell) {
                val sl = if (isBuy) vEngine.longStopReferencePrice else vEngine.shortStopReferencePrice
                val tp1 = if (isBuy) vEngine.longTarget1RPrice else vEngine.shortTarget1RPrice
                val tp2 = if (isBuy) vEngine.longTarget2RPrice else vEngine.shortTarget2RPrice
                TradeNotificationHelper.sendTradeSignalNotification(
                    context = context,
                    provider = uiState.selectedProvider,
                    symbol = analysis.symbol,
                    timeframe = analysis.activeTimeframe,
                    bar1EpochSec = bar1.timeEpochSec,
                    verdict = analysis.verdict,
                    entryPrice = bar1.close,
                    stopLossPrice = sl,
                    target1Price = tp1,
                    target2Price = tp2,
                    isTestAlert = false
                )
            }
        }
    }

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
                        HubTab.TRADE -> Icons.AutoMirrored.Filled.TrendingUp
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
                        metaApiAccountId = uiState.metaApiAccountIdInput,
                        metaApiAuthToken = uiState.metaApiAuthTokenInput,
                        hasCustomSavedCredentials = uiState.hasCustomSavedCredentials,
                        onUpdateAccountId = viewModel::updateMetaApiAccountIdInput,
                        onUpdateAuthToken = viewModel::updateMetaApiAuthTokenInput,
                        onSaveMetaApi = viewModel::saveAndConnectMetaApiCredentials,
                        onClearMetaApi = viewModel::clearSavedMetaApiCredentials,
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
                            onCheckGitHubUpdate = viewModel::checkGitHubReleaseUpdate,
                            onUpdateMetaApiAccountId = viewModel::updateMetaApiAccountIdInput,
                            onUpdateMetaApiAuthToken = viewModel::updateMetaApiAuthTokenInput,
                            onSaveMetaApiCredentials = viewModel::saveAndConnectMetaApiCredentials,
                            onClearMetaApiCredentials = viewModel::clearSavedMetaApiCredentials
                        )

                        HubTab.TRADE -> TradeSignalScreen(
                            analysis = analysis,
                            selectedProvider = uiState.selectedProvider
                        )

                        HubTab.ENGINES -> TenEnginesScreen(
                            analysis = analysis,
                            diagnostics = uiState.diagnostics
                        )

                        HubTab.MTF_MATRIX -> MtfMatrixScreen(
                            analysis = analysis,
                            onSelectTimeframe = { tf ->
                                viewModel.selectTimeframe(tf)
                                viewModel.selectTab(HubTab.HUD_CHART)
                            }
                        )

                        HubTab.NEWS_ENGINE -> NewsEngineScreen(
                            analysis = analysis
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
    metaApiAccountId: String,
    metaApiAuthToken: String,
    hasCustomSavedCredentials: Boolean,
    onUpdateAccountId: (String) -> Unit,
    onUpdateAuthToken: (String) -> Unit,
    onSaveMetaApi: () -> Unit,
    onClearMetaApi: () -> Unit,
    onRetry: () -> Unit,
    onSwitchProvider: (OnlineDataProvider) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
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
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = "Provider Offline",
                    tint = BearishCrimson,
                    modifier = Modifier.size(42.dp)
                )
                Text(
                    text = "STATUS: ${provider.shortLabel.uppercase()} OFFLINE",
                    style = MaterialTheme.typography.titleMedium,
                    color = BearishCrimson,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Aplikasi beroperasi jujur (0% data palsu). Data pasar hanya ditampilkan jika server online aktif.",
                    style = MaterialTheme.typography.bodySmall,
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

                // Kotak Input Kredensial Langsung jika Provider adalah Elev8 MT5 (Disimpan Permanen di SharedPreferences)
                if (provider == OnlineDataProvider.ELEV8_METAAPI) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ObsidianBg),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, GoldPrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "KREDENSIAL AKUN ELEV8 MT5 (SIMPAN PERMANEN)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = GoldBright,
                                    fontWeight = FontWeight.Bold
                                )
                                if (hasCustomSavedCredentials) {
                                    Text(
                                        text = "TERSIMPAN DI HP",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = BullishEmerald,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = "Kredensial Anda otomatis tersimpan di penyimpanan lokal HP sehingga tidak akan hilang saat aplikasi ditutup.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            OutlinedTextField(
                                value = metaApiAccountId,
                                onValueChange = onUpdateAccountId,
                                label = { Text("Account ID MetaApi (Elev8)", style = MaterialTheme.typography.labelSmall) },
                                placeholder = { Text("Contoh: 2f0cba6a-da2e-415f-...", style = MaterialTheme.typography.labelSmall) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("offline_account_id_input")
                            )
                            OutlinedTextField(
                                value = metaApiAuthToken,
                                onValueChange = onUpdateAuthToken,
                                label = { Text("API Access Token MetaApi", style = MaterialTheme.typography.labelSmall) },
                                placeholder = { Text("Token dari app.metaapi.cloud/token", style = MaterialTheme.typography.labelSmall) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("offline_auth_token_input")
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onSaveMetaApi,
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBg),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("save_connect_metaapi_button")
                                ) {
                                    Text(
                                        text = "SIMPAN & SAMBUNGKAN",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (hasCustomSavedCredentials || metaApiAccountId.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = onClearMetaApi,
                                        modifier = Modifier.testTag("offline_clear_metaapi_button")
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

                Text(
                    text = "Atau Beralih ke Server Publik Gratis (Tanpa Akun):",
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
                                color = if (realProv == provider) GoldBright else CyanSecondary
                            )
                        }
                    }
                }

                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanSecondary, contentColor = ObsidianBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("retry_online_button")
                ) {
                    Text(
                        text = "COBA HUBUNGKAN ULANG",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

