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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.ui.screens.SettingsScreen
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
import com.example.ui.theme.SlateSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

enum class AppPillar(
    val title: String,
    val subtitle: String,
    val defaultTab: HubTab
) {
    COCKPIT("Cockpit", "Grafik & Eksekusi", HubTab.HUD_CHART),
    RADAR("Radar", "MTF & Berita", HubTab.MTF_MATRIX),
    STUDIO("Studio", "Mesin & Sistem", HubTab.ENGINES)
}

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
            val currentPillar = when (uiState.selectedTab) {
                HubTab.HUD_CHART, HubTab.TRADE -> AppPillar.COCKPIT
                HubTab.MTF_MATRIX, HubTab.NEWS_ENGINE -> AppPillar.RADAR
                HubTab.ENGINES, HubTab.MQL5_STUDIO, HubTab.SETTINGS -> AppPillar.STUDIO
            }

            Surface(
                color = SlateSurface,
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = SlateCardBorder.copy(alpha = 0.8f)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppPillar.entries.forEach { pillar ->
                        val isSelected = currentPillar == pillar
                        val icon = when (pillar) {
                            AppPillar.COCKPIT -> Icons.Default.CandlestickChart
                            AppPillar.RADAR -> Icons.Default.Layers
                            AppPillar.STUDIO -> Icons.Default.Analytics
                        }

                        Surface(
                            color = if (isSelected) GoldPrimary else SlateSurfaceElevated.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { viewModel.selectTab(pillar.defaultTab) }
                                .testTag("nav_pillar_${pillar.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = pillar.title,
                                    tint = if (isSelected) ObsidianBg else TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = pillar.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isSelected) ObsidianBg else TextPrimary,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                                    )
                                    Text(
                                        text = pillar.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) ObsidianBg.copy(alpha = 0.8f) else TextSecondary,
                                        fontSize = 9.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
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

            // Pilar Sub-Tabs Workspace Switcher (Hanya ditampilkan bila data online aktif)
            val currentPillar = when (uiState.selectedTab) {
                HubTab.HUD_CHART, HubTab.TRADE -> AppPillar.COCKPIT
                HubTab.MTF_MATRIX, HubTab.NEWS_ENGINE -> AppPillar.RADAR
                HubTab.ENGINES, HubTab.MQL5_STUDIO, HubTab.SETTINGS -> AppPillar.STUDIO
            }

            if (analysis != null && uiState.isProviderOnline && !uiState.isLoading) {
                val subTabs = when (currentPillar) {
                    AppPillar.COCKPIT -> listOf(
                        HubTab.HUD_CHART to "📈 Grafik & Sinyal",
                        HubTab.TRADE to "🎯 Tiket Eksekusi MT5"
                    )
                    AppPillar.RADAR -> listOf(
                        HubTab.MTF_MATRIX to "🧭 Matriks 5-Timeframe",
                        HubTab.NEWS_ENGINE to "📰 Berita Makro & Kalender"
                    )
                    AppPillar.STUDIO -> listOf(
                        HubTab.ENGINES to "⚙️ 10 Mesin Analisis",
                        HubTab.MQL5_STUDIO to "💻 Generator MQL5 MT5",
                        HubTab.SETTINGS to "🛡️ Server & Pengaturan"
                    )
                }

                Surface(
                    color = SlateSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateCardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        subTabs.forEach { (tab, label) ->
                            val isTabActive = uiState.selectedTab == tab
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isTabActive) GoldPrimary else SlateSurfaceElevated.copy(alpha = 0.5f))
                                    .clickable { viewModel.selectTab(tab) }
                                    .padding(vertical = 8.dp)
                                    .testTag("nav_tab_${tab.name.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isTabActive) ObsidianBg else TextSecondary,
                                    fontWeight = if (isTabActive) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

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

                // 1. Settings dan MQL5 Studio selalu dapat diakses pengguna kapanpun (bahkan saat feed pasar offline)
                uiState.selectedTab == HubTab.SETTINGS -> {
                    SettingsScreen(
                        uiState = uiState,
                        onSelectProvider = viewModel::selectProvider,
                        onRunMultiProviderAudit = viewModel::runBackgroundMultiProviderAudit,
                        onCleanAndResetSession = viewModel::cleanAndResetDataSession,
                        onUpdateGitHubRepo = viewModel::updateGitHubRepoInput,
                        onCheckGitHubUpdate = viewModel::checkGitHubReleaseUpdate,
                        onToggleAllowOtaDowngrade = viewModel::toggleAllowOtaDowngrade,
                        onUpdateMetaApiAccountId = viewModel::updateMetaApiAccountIdInput,
                        onUpdateMetaApiAuthToken = viewModel::updateMetaApiAuthTokenInput,
                        onSaveMetaApiCredentials = viewModel::saveAndConnectMetaApiCredentials,
                        onClearMetaApiCredentials = viewModel::clearSavedMetaApiCredentials
                    )
                }

                uiState.selectedTab == HubTab.MQL5_STUDIO -> {
                    Mql5StudioScreen(
                        config = uiState.mql5Config,
                        generatedCode = uiState.generatedMql5Code,
                        onUpdateConfig = viewModel::updateMql5Config
                    )
                }

                // 2. PEMERIKSAAN JUJUR: Tampilan Saat Provider Offline / Kuota Habis (Untuk Tab Analisis Pasar)
                analysis == null || !uiState.isProviderOnline -> {
                    HonestOfflineErrorState(
                        provider = uiState.selectedProvider,
                        endpointUrl = uiState.liveAudit.lastEndpointUrl,
                        errorMessage = uiState.connectionErrorMessage
                            ?: "Provider tidak dapat dijangkau atau mengembalikan respons kosong.",
                        metaApiAccountId = uiState.metaApiAccountIdInput,
                        metaApiAuthToken = uiState.metaApiAuthTokenInput,
                        savedAccountIdHint = uiState.savedMetaApiAccountIdHint,
                        savedTokenHint = uiState.savedMetaApiTokenHint,
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
                            onClearMetaApiCredentials = viewModel::clearSavedMetaApiCredentials,
                            onNavigateToTrade = { viewModel.selectTab(HubTab.TRADE) }
                        )

                        HubTab.TRADE -> TradeSignalScreen(
                            analysis = analysis,
                            selectedProvider = uiState.selectedProvider,
                            onNavigateToChart = { viewModel.selectTab(HubTab.HUD_CHART) }
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

                        HubTab.SETTINGS -> SettingsScreen(
                            uiState = uiState,
                            onSelectProvider = viewModel::selectProvider,
                            onRunMultiProviderAudit = viewModel::runBackgroundMultiProviderAudit,
                            onCleanAndResetSession = viewModel::cleanAndResetDataSession,
                            onUpdateGitHubRepo = viewModel::updateGitHubRepoInput,
                            onCheckGitHubUpdate = viewModel::checkGitHubReleaseUpdate,
                            onToggleAllowOtaDowngrade = viewModel::toggleAllowOtaDowngrade,
                            onUpdateMetaApiAccountId = viewModel::updateMetaApiAccountIdInput,
                            onUpdateMetaApiAuthToken = viewModel::updateMetaApiAuthTokenInput,
                            onSaveMetaApiCredentials = viewModel::saveAndConnectMetaApiCredentials,
                            onClearMetaApiCredentials = viewModel::clearSavedMetaApiCredentials
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
    savedAccountIdHint: String,
    savedTokenHint: String,
    hasCustomSavedCredentials: Boolean,
    onUpdateAccountId: (String) -> Unit,
    onUpdateAuthToken: (String) -> Unit,
    onSaveMetaApi: () -> Unit,
    onClearMetaApi: () -> Unit,
    onRetry: () -> Unit,
    onSwitchProvider: (OnlineDataProvider) -> Unit
) {
    var isOfflineTokenVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("honest_offline_error_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // CARD 1: STATUS ERROR & DETAIL KUOTA (Ringkas agar Solusi Cepat langsung terlihat)
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, BearishCrimson, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = "Provider Offline",
                            tint = BearishCrimson,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "STATUS: ${provider.displayName.uppercase()} OFFLINE",
                            style = MaterialTheme.typography.titleSmall,
                            color = BearishCrimson,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Aplikasi beroperasi jujur (0% data palsu). Data pasar hanya ditampilkan jika server online aktif.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(BearishBg.copy(alpha = 0.6f))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (endpointUrl.isNotBlank()) {
                                Text(
                                    text = "Endpoint: $endpointUrl",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GoldBright,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "Detail Error: $errorMessage",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            // CARD 2: SOLUSI CEPAT: BERALIH KE PROVIDER PUBLIK GRATIS (LANGSUNG DI ATAS)
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateSurface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, BullishEmerald, RoundedCornerShape(18.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = GoldBright,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "SOLUSI CEPAT: BERALIH KE SERVER AKTIF",
                            style = MaterialTheme.typography.titleSmall,
                            color = GoldBright,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Server MetaApi Elev8 memiliki kuota CPU terbatas (HTTP 429). Anda dapat langsung beralih ke Server Publik Gratis tanpa batas kuota:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary
                    )

                    // Tombol Utama: Langsung Beralih ke Binance Vision (1 Tap)
                    Button(
                        onClick = { onSwitchProvider(OnlineDataProvider.BINANCE_VISION) },
                        colors = ButtonDefaults.buttonColors(containerColor = BullishEmerald, contentColor = ObsidianBg),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("switch_to_binance_vision_fast")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "⚡ AKTIFKAN BINANCE VISION SEKARANG",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "Feed Proxy Real-Time • PAXG Spot (Gold) & EUR",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    Text(
                        text = "Atau Pilih Server Publik Lainnya:",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyanSecondary,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Daftar Provider Publik Alternatif
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OnlineDataProvider.realProviders.forEach { realProv ->
                            val isSelected = realProv == provider
                            OutlinedButton(
                                onClick = { onSwitchProvider(realProv) },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) GoldPrimary.copy(alpha = 0.15f) else androidx.compose.ui.graphics.Color.Transparent
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) GoldPrimary else CyanSecondary.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier.testTag("switch_to_${realProv.id}")
                            ) {
                                Text(
                                    text = realProv.shortLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) GoldBright else CyanSecondary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = CyanSecondary, contentColor = ObsidianBg),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("retry_online_button")
                    ) {
                        Text(
                            text = "COBA SAMBUNGKAN KEMBALI",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // CARD 3: KREDENSIAL AKUN ELEV8 MT5 (TERENKRIPSI LOKAL)
            if (provider == OnlineDataProvider.ELEV8_METAAPI) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianBg),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GoldPrimary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
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
                            Text(
                                text = "GANTI KREDENSIAL ELEV8 MT5",
                                style = MaterialTheme.typography.labelMedium,
                                color = GoldBright,
                                fontWeight = FontWeight.Bold
                            )
                            if (hasCustomSavedCredentials) {
                                Text(
                                    text = "TERSIMPAN (TERENKRIPSI)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BullishEmerald,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = "Kredensial dienkripsi via Android Keystore (AES-256-GCM) secara lokal. Token tersimpan tidak ditampilkan ulang demi keamanan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        if (hasCustomSavedCredentials) {
                            Text(
                                text = "Tersimpan: Account $savedAccountIdHint • Token $savedTokenHint",
                                style = MaterialTheme.typography.labelSmall,
                                color = BullishEmerald,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.testTag("offline_saved_credential_hint")
                            )
                        }
                        OutlinedTextField(
                            value = metaApiAccountId,
                            onValueChange = onUpdateAccountId,
                            label = { Text("Account ID MetaApi Baru (Elev8)", style = MaterialTheme.typography.labelSmall) },
                            placeholder = {
                                Text(
                                    text = if (hasCustomSavedCredentials) "Tersimpan: $savedAccountIdHint" else "Contoh: 2f0cba6a-da2e-415f-...",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("offline_account_id_input")
                        )
                        OutlinedTextField(
                            value = metaApiAuthToken,
                            onValueChange = onUpdateAuthToken,
                            label = { Text("API Access Token MetaApi Baru", style = MaterialTheme.typography.labelSmall) },
                            placeholder = {
                                Text(
                                    text = if (hasCustomSavedCredentials) "Token tersimpan: $savedTokenHint" else "Token dari app.metaapi.cloud/token",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            visualTransformation = if (isOfflineTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(
                                    onClick = { isOfflineTokenVisible = !isOfflineTokenVisible },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("offline_toggle_token_visibility")
                                ) {
                                    Icon(
                                        imageVector = if (isOfflineTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (isOfflineTokenVisible) "Sembunyikan Token" else "Tampilkan Token Sementara",
                                        tint = CyanSecondary
                                    )
                                }
                            },
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
        }
    }
}

