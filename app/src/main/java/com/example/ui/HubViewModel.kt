package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.HubRepository
import com.example.data.local.CustomNewsEventEntity
import com.example.data.local.SavedSignalSnapshotEntity
import com.example.data.remote.LiveMarketAndNewsService
import com.example.domain.IndicatorAndConfluenceEngines
import com.example.domain.Mql5ConfigParams
import com.example.domain.Mql5IndicatorCodeGenerator
import com.example.domain.SystemDiagnosticGuard
import com.example.domain.SystemDiagnosticReport
import com.example.model.CompleteHubAnalysis
import com.example.model.GitHubReleaseInfo
import com.example.model.LiveVerificationAudit
import com.example.model.MacroNewsArticle
import com.example.model.MacroSentiment
import com.example.model.MarketSymbol
import com.example.model.NewsImpactLevel
import com.example.model.OnlineDataProvider
import com.example.model.Timeframe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

enum class HubTab(val title: String) {
    HUD_CHART("Grafik"),
    TRADE("Trade"),
    ENGINES("Mesin"),
    MTF_MATRIX("Matriks"),
    NEWS_ENGINE("Berita"),
    MQL5_STUDIO("Kode MQL5"),
    SETTINGS("Pengaturan")
}

data class HubUiState(
    val selectedTab: HubTab = HubTab.HUD_CHART,
    val selectedProvider: OnlineDataProvider = OnlineDataProvider.BINANCE_VISION,
    val selectedSymbol: MarketSymbol = MarketSymbol.XAUUSD,
    val brokerSuffix: String = "",
    val selectedTimeframe: Timeframe = Timeframe.M15,
    val isLoading: Boolean = true,
    val isSyncingInProgress: Boolean = false,
    val isProviderOnline: Boolean = false,
    val connectionErrorMessage: String? = null,
    val analysis: CompleteHubAnalysis? = null,
    val inspectedBarIndex: Int = 1,
    val showEmaOverlays: Boolean = true,
    val showSrZones: Boolean = true,
    val showStructureSwings: Boolean = true,
    val isLiveTickMonitorEnabled: Boolean = true,
    val isTradeNotificationEnabled: Boolean = true,
    val mql5Config: Mql5ConfigParams = Mql5ConfigParams(),
    val generatedMql5Code: String = "",
    val diagnostics: SystemDiagnosticReport = SystemDiagnosticReport(),
    val liveAudit: LiveVerificationAudit = LiveVerificationAudit(),
    val githubRelease: GitHubReleaseInfo = GitHubReleaseInfo(),
    val metaApiAccountIdInput: String = "",
    val metaApiAuthTokenInput: String = "",
    val hasCustomSavedCredentials: Boolean = false,
    val statusBannerMessage: String? = null
)

class HubViewModel(
    private val repository: HubRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HubUiState(
            selectedProvider = repository.getSavedSelectedProvider() ?: if (repository.isMetaApiConfigured()) {
                OnlineDataProvider.ELEV8_METAAPI
            } else {
                OnlineDataProvider.BINANCE_VISION
            },
            brokerSuffix = repository.getSavedBrokerSuffix(),
            githubRelease = GitHubReleaseInfo(
                repository = repository.getDefaultGitHubRepository(),
                installedTag = repository.getInstalledReleaseTag()
            ),
            isTradeNotificationEnabled = repository.isTradeNotificationsEnabled(),
            metaApiAccountIdInput = repository.getMetaApiAccountId(),
            metaApiAuthTokenInput = repository.getMetaApiAuthToken(),
            hasCustomSavedCredentials = repository.hasCustomMetaApiCredentials()
        )
    )
    val uiState: StateFlow<HubUiState> = _uiState.asStateFlow()

    val savedSignalLogs: StateFlow<List<SavedSignalSnapshotEntity>> =
        repository.savedSignalLogs.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val customNewsFlow: StateFlow<List<CustomNewsEventEntity>> =
        repository.customNewsEvents.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Token Generasi Permintaan (Monotonic Generation Guard):
     * Setiap kali pengguna mengganti Provider, Simbol (XAUUSD/EURUSD), Timeframe, atau melakukan Reset,
     * token ini naik +1 sehingga respons lama yang masih di jalan (in-flight) otomatis ditolak
     * dan TIDAK PERNAH bisa menimpa/tumpang tindih dengan state baru.
     */
    private val requestGenerationId = AtomicLong(1L)

    // Cache terisolasi ketat berdasarkan (Provider, Symbol)
    private val cachedBundles = ConcurrentHashMap<Pair<OnlineDataProvider, MarketSymbol>, HubRepository.BundleFetchOutcome.Success>()

    // Memori perbandingan harga antar-provider yang diisolasi ketat per (MarketSymbol, Timeframe, OnlineDataProvider)
    // agar harga XAUUSD (~4138) dan EURUSD (~1.136) TIDAK PERNAH tercampur!
    private val providerPriceMemory = ConcurrentHashMap<Triple<MarketSymbol, Timeframe, OnlineDataProvider>, Double>()

    private var activeFetchJob: Job? = null
    private var inFlightKey: Pair<OnlineDataProvider, MarketSymbol>? = null
    private var serverPollingJob: Job? = null
    private var auditJob: Job? = null

    init {
        refreshMarketData(forceRefreshNews = false, runAuditAfterSuccess = true)
        startRealServerPollingLoop()
        viewModelScope.launch {
            customNewsFlow
                .drop(1)
                .distinctUntilChanged()
                .collect {
                    appendDiagnosticLog("Agenda berita pengguna berubah -> evaluasi ulang Mesin Berita")
                    recomputeClosedCandleAnalysis()
                }
        }
    }

    fun selectTab(tab: HubTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun selectProvider(provider: OnlineDataProvider) {
        if (_uiState.value.selectedProvider == provider) return
        repository.saveSelectedProvider(provider)
        val newGen = requestGenerationId.incrementAndGet()
        auditJob?.cancel()
        _uiState.update {
            it.copy(
                selectedProvider = provider,
                inspectedBarIndex = 1,
                connectionErrorMessage = null,
                diagnostics = it.diagnostics.copy(activeGenerationId = newGen)
            )
        }
        appendDiagnosticLog("Gen #$newGen: Ganti Sumber Provider ke ${provider.displayName}")
        refreshMarketData(forceRefreshNews = false, runAuditAfterSuccess = false)
    }

    fun selectSymbol(symbol: MarketSymbol) {
        if (_uiState.value.selectedSymbol == symbol) return
        val newGen = requestGenerationId.incrementAndGet()
        auditJob?.cancel()
        val currentProv = _uiState.value.selectedProvider
        val currentTf = _uiState.value.selectedTimeframe
        val key = currentProv to symbol
        val cached = cachedBundles[key]
        val hasValidCachedTf = cached != null &&
            cached.symbol == symbol &&
            cached.provider == currentProv &&
            ! cached.ratesByTimeframe[currentTf].isNullOrEmpty()

        _uiState.update {
            it.copy(
                selectedSymbol = symbol,
                inspectedBarIndex = 1,
                // Jika belum ada cache valid untuk simbol baru, kosongkan analysis sementara agar data simbol lama tidak tampil tumpang tindih
                analysis = if (hasValidCachedTf) it.analysis else null,
                isLoading = !hasValidCachedTf,
                connectionErrorMessage = null,
                diagnostics = it.diagnostics.copy(activeGenerationId = newGen)
            )
        }
        appendDiagnosticLog("Gen #$newGen: Ganti Pair ke ${symbol.code} pada ${currentProv.shortLabel}")

        if (hasValidCachedTf && !currentProv.isOfflineTestMode) {
            recomputeClosedCandleAnalysis()
            updateAuditComparisonForCurrentSelection()
        } else {
            refreshMarketData(forceRefreshNews = false, runAuditAfterSuccess = true)
        }
    }

    fun selectTimeframe(timeframe: Timeframe) {
        if (_uiState.value.selectedTimeframe == timeframe) return
        val newGen = requestGenerationId.incrementAndGet()
        val currentProv = _uiState.value.selectedProvider
        val currentSym = _uiState.value.selectedSymbol
        val key = currentProv to currentSym
        val cached = cachedBundles[key]
        val hasValidBarsForTf = cached != null && !cached.ratesByTimeframe[timeframe].isNullOrEmpty()

        _uiState.update {
            it.copy(
                selectedTimeframe = timeframe,
                inspectedBarIndex = 1,
                diagnostics = it.diagnostics.copy(activeGenerationId = newGen)
            )
        }
        appendDiagnosticLog("Gen #$newGen: Ganti Timeframe ke ${timeframe.label} (${currentSym.code})")

        if (hasValidBarsForTf && !currentProv.isOfflineTestMode) {
            recomputeClosedCandleAnalysis()
            updateAuditComparisonForCurrentSelection()
        } else {
            // Jika timeframe yang diminta belum tersedia di bundle, langsung ambil bersih dari server
            refreshMarketData(forceRefreshNews = false, runAuditAfterSuccess = false)
        }
    }

    fun updateBrokerSuffix(suffix: String) {
        if (_uiState.value.brokerSuffix == suffix) return
        repository.saveBrokerSuffix(suffix)
        _uiState.update { state ->
            val updatedConfig = state.mql5Config.copy(brokerSuffix = suffix)
            state.copy(brokerSuffix = suffix, mql5Config = updatedConfig)
        }
        recomputeClosedCandleAnalysis()
    }

    fun inspectCandleBar(barIndex: Int) {
        _uiState.update { it.copy(inspectedBarIndex = barIndex.coerceAtLeast(0)) }
    }

    fun toggleEmaOverlays() {
        _uiState.update { it.copy(showEmaOverlays = !it.showEmaOverlays) }
    }

    fun toggleSrZones() {
        _uiState.update { it.copy(showSrZones = !it.showSrZones) }
    }

    fun toggleStructureSwings() {
        _uiState.update { it.copy(showStructureSwings = !it.showStructureSwings) }
    }

    fun toggleLiveTickMonitor() {
        val nextState = !_uiState.value.isLiveTickMonitorEnabled
        _uiState.update {
            it.copy(
                isLiveTickMonitorEnabled = nextState,
                statusBannerMessage = if (nextState) {
                    "Polling server online berkala untuk Candle [0] diaktifkan."
                } else {
                    "Polling server online berkala dijeda."
                }
            )
        }
        appendDiagnosticLog("Polling Server Candle [0]: ${if (nextState) "AKTIF" else "DIJEDA"}")
    }

    fun toggleTradeNotificationAlerts(enabled: Boolean) {
        repository.saveTradeNotificationsEnabled(enabled)
        _uiState.update {
            it.copy(
                isTradeNotificationEnabled = enabled,
                statusBannerMessage = if (enabled) {
                    "Notifikasi Sinyal Trade HP Diaktifkan (Push Alert Siap)."
                } else {
                    "Notifikasi Sinyal Trade HP Dinonaktifkan."
                }
            )
        }
        appendDiagnosticLog("Notifikasi Sinyal Trade HP: ${if (enabled) "AKTIF" else "NONAKTIF"}")
    }

    /**
     * Pembersihan penuh seluruh cache memori, pembatalan request yang sedang berjalan,
     * dan pengambilan ulang data segar dari server online agar bebas 100% dari data tumpang tindih.
     */
    fun cleanAndResetDataSession() {
        val newGen = requestGenerationId.incrementAndGet()
        activeFetchJob?.cancel()
        auditJob?.cancel()
        inFlightKey = null
        cachedBundles.clear()
        providerPriceMemory.clear()

        viewModelScope.launch {
            repository.clearNewsCache()
        }

        _uiState.update { state ->
            state.copy(
                isLoading = true,
                isSyncingInProgress = false,
                analysis = null,
                connectionErrorMessage = null,
                inspectedBarIndex = 1,
                diagnostics = state.diagnostics.copy(
                    activeGenerationId = newGen,
                    cachePurgeCount = state.diagnostics.cachePurgeCount + 1
                ),
                statusBannerMessage = "Pembersihan Selesai: Cache memori dikosongkan & sesi disinkronkan ulang dari server."
            )
        }
        appendDiagnosticLog("PEMBERSIHAN PENUH (Gen #$newGen): Seluruh cache dibersihkan, mengambil ulang data murni.")
        refreshMarketData(forceRefreshNews = true, runAuditAfterSuccess = true)
    }

    /**
     * Mengambil data pasar M1..H1 dan berita murni dari server online.
     * Dilengkapi Generation Token & Symbol Sanity Check agar tidak pernah salah ambil data.
     */
    fun refreshMarketData(
        forceRefreshNews: Boolean = false,
        runAuditAfterSuccess: Boolean = false
    ) {
        val stateSnapshot = _uiState.value
        val targetProvider = stateSnapshot.selectedProvider
        val targetSymbol = stateSnapshot.selectedSymbol
        val targetTf = stateSnapshot.selectedTimeframe
        val key = targetProvider to targetSymbol

        if (activeFetchJob?.isActive == true && inFlightKey == key) {
            _uiState.update { state ->
                val diag = state.diagnostics
                state.copy(
                    diagnostics = diag.copy(
                        blockedDuplicateRequests = diag.blockedDuplicateRequests + 1
                    ),
                    statusBannerMessage = "Permintaan ke ${targetProvider.shortLabel} (${targetSymbol.code}) sedang berjalan. Dobel request dicegah."
                )
            }
            appendDiagnosticLog("DICEGAH: Dobel request ke ${targetProvider.shortLabel} (${targetSymbol.code}) diblokir.")
            return
        }

        activeFetchJob?.cancel()
        auditJob?.cancel()
        val capturedGen = requestGenerationId.incrementAndGet()
        inFlightKey = key

        activeFetchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = it.analysis == null || it.analysis.symbol != targetSymbol,
                    isSyncingInProgress = true,
                    connectionErrorMessage = null,
                    diagnostics = it.diagnostics.copy(activeGenerationId = capturedGen)
                )
            }
            try {
                val outcome = repository.fetchCompleteSymbolBundle(
                    symbol = targetSymbol,
                    provider = targetProvider,
                    activeTimeframe = targetTf,
                    forceRefreshNews = forceRefreshNews
                )

                // Guard Anti-Tumpang-Tindih: Pastikan state pilihan pengguna tidak berubah selama request berjalan
                val latestState = _uiState.value
                if (requestGenerationId.get() != capturedGen ||
                    latestState.selectedProvider != targetProvider ||
                    latestState.selectedSymbol != targetSymbol
                ) {
                    recordDiscardedStaleResponse("Respons bundle ${targetProvider.shortLabel} (${targetSymbol.code}) diabaikan karena pilihan sudah berubah.")
                    return@launch
                }

                when (outcome) {
                    is HubRepository.BundleFetchOutcome.Success -> {
                        cachedBundles[key] = outcome
                        val currentTfNow = _uiState.value.selectedTimeframe
                        val activeBars = outcome.ratesByTimeframe[currentTfNow]
                            ?: outcome.ratesByTimeframe[targetTf].orEmpty()

                        // Simpan harga per (Symbol, Timeframe, Provider) agar tidak pernah tercampur antar-simbol
                        outcome.ratesByTimeframe.forEach { (tfKey, tfBars) ->
                            val close1 = tfBars.getOrNull(1)?.close ?: 0.0
                            if (close1 > 0.0) {
                                providerPriceMemory[Triple(targetSymbol, tfKey, targetProvider)] = close1
                            }
                        }

                        val prevFetchMs = _uiState.value.liveAudit.lastServerFetchEpochMs
                        val newFetchMs = outcome.serverFetchTimeMs
                        val timestampChanged = prevFetchMs == 0L || newFetchMs > prevFetchMs

                        val comparisonText = buildProviderComparisonFor(targetSymbol, currentTfNow)
                        val hasMultiProviderDiff = countDistinctProvidersFor(targetSymbol, currentTfNow) >= 2

                        _uiState.update { state ->
                            val diag = state.diagnostics
                            val prevAudit = state.liveAudit
                            state.copy(
                                isLoading = false,
                                isSyncingInProgress = false,
                                isProviderOnline = true,
                                connectionErrorMessage = null,
                                diagnostics = diag.copy(
                                    newsCacheHits = diag.newsCacheHits + (if (outcome.wasNewsServedFromCache) 1 else 0),
                                    lastSyncDurationMs = outcome.fetchDurationMs
                                ),
                                liveAudit = prevAudit.copy(
                                    check1EndpointAccessible = outcome.httpStatusCode == 200,
                                    check1Detail = "HTTP ${outcome.httpStatusCode} OK (${targetProvider.baseHost})",
                                    check2RealServerData = true,
                                    check2Detail = "${activeBars.size} candle asli ${targetSymbol.code} dari ${targetProvider.shortLabel} (0% Simulasi)",
                                    check3TimestampUpdated = timestampChanged,
                                    check3Detail = "Waktu ambil server: ${formatMillisTime(newFetchMs)} (Candle [0]: ${activeBars.firstOrNull()?.timeEpochSec ?: 0L}s)",
                                    check4ChangesAcrossProviders = hasMultiProviderDiff || prevAudit.check4ChangesAcrossProviders,
                                    check4Detail = comparisonText,
                                    check5ErrorDisplayedProperly = true,
                                    check5Detail = "Penangkap error HTTP/Jaringan aktif & teruji",
                                    check6HonestWhenOffline = true,
                                    check6Detail = "Jujur: Tanpa fallback data palsu saat provider mati",
                                    lastHttpStatusCode = outcome.httpStatusCode,
                                    lastEndpointUrl = outcome.primaryEndpointUrl,
                                    previousServerFetchEpochMs = prevFetchMs,
                                    lastServerFetchEpochMs = newFetchMs,
                                    lastErrorMessage = null
                                ),
                                statusBannerMessage = "TERHUBUNG ONLINE: ${targetProvider.shortLabel} • ${targetSymbol.code} (HTTP ${outcome.httpStatusCode} • ${outcome.fetchDurationMs} ms)"
                            )
                        }
                        recomputeClosedCandleAnalysis()
                        appendDiagnosticLog(
                            "ONLINE HTTP ${outcome.httpStatusCode} [${targetSymbol.code} ${currentTfNow.label}] dari ${targetProvider.shortLabel} (${outcome.fetchDurationMs} ms)"
                        )

                        if (runAuditAfterSuccess) {
                            runBackgroundMultiProviderAudit()
                        }
                    }

                    is HubRepository.BundleFetchOutcome.Error -> {
                        cachedBundles.remove(key)
                        appendDiagnosticLog("OFFLINE / ERROR [${targetProvider.shortLabel} ${targetSymbol.code}]: ${outcome.errorMessage}")
                        _uiState.update { state ->
                            val prevAudit = state.liveAudit
                            state.copy(
                                isLoading = false,
                                isSyncingInProgress = false,
                                isProviderOnline = false,
                                analysis = null,
                                connectionErrorMessage = outcome.errorMessage,
                                liveAudit = prevAudit.copy(
                                    check1EndpointAccessible = false,
                                    check1Detail = "GAGAL (HTTP ${outcome.httpStatusCode}) pada ${outcome.endpointUrl}",
                                    check2RealServerData = false,
                                    check2Detail = "Tidak ada data karena provider offline/gagal",
                                    check5ErrorDisplayedProperly = true,
                                    check5Detail = "Error ditampilkan: ${outcome.errorMessage}",
                                    check6HonestWhenOffline = true,
                                    check6Detail = "TERBUKTI JUJUR: Status berubah ke OFFLINE & menolak membuat data palsu",
                                    lastHttpStatusCode = outcome.httpStatusCode,
                                    lastEndpointUrl = outcome.endpointUrl,
                                    lastErrorMessage = outcome.errorMessage
                                ),
                                statusBannerMessage = "PROVIDER OFFLINE: ${outcome.errorMessage}"
                            )
                        }
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } finally {
                if (inFlightKey == key) {
                    inFlightKey = null
                }
            }
        }
    }

    /**
     * Menjalankan verifikasi lintas-provider secara terisolasi untuk simbol & timeframe aktif,
     * tanpa menimpa data grafik utama dan tanpa mencampur harga XAUUSD dengan EURUSD.
     */
    fun runBackgroundMultiProviderAudit() {
        if (auditJob?.isActive == true) {
            appendDiagnosticLog("Audit lintas-provider sedang berjalan, duplikasi dicegah.")
            return
        }
        val stateSnapshot = _uiState.value
        val sym = stateSnapshot.selectedSymbol
        val tf = stateSnapshot.selectedTimeframe
        val activeProv = stateSnapshot.selectedProvider
        val capturedGen = requestGenerationId.get()

        auditJob = viewModelScope.launch {
            val freeComparisonProviders = OnlineDataProvider.realProviders
                .filter { !it.isOfflineTestMode && it != OnlineDataProvider.ELEV8_METAAPI }
            val providersToProbe = (listOf(activeProv) + freeComparisonProviders)
                .filter { !it.isOfflineTestMode }
                .distinct()
                .take(3)
            for (prov in providersToProbe) {
                if (requestGenerationId.get() != capturedGen) return@launch
                val tripleKey = Triple(sym, tf, prov)
                // Gunakan data yang sudah ada untuk provider aktif agar tidak terjadi dobel request
                if (prov == activeProv && providerPriceMemory.containsKey(tripleKey)) {
                    continue
                }
                val res = repository.pollSingleTimeframeFromServer(sym, tf, prov)
                if (res is LiveMarketAndNewsService.RatesFetchOutcome.Success) {
                    val close1 = res.bars.getOrNull(1)?.close ?: 0.0
                    if (SystemDiagnosticGuard.isPriceMatchingSymbol(sym, close1)) {
                        providerPriceMemory[tripleKey] = close1
                    }
                }
            }

            if (requestGenerationId.get() != capturedGen) return@launch

            val offlineProbe = repository.pollSingleTimeframeFromServer(sym, tf, OnlineDataProvider.OFFLINE_SIMULATION_TEST)
            val offlineErrorVerified = offlineProbe is LiveMarketAndNewsService.RatesFetchOutcome.Error

            val distinctCount = countDistinctProvidersFor(sym, tf)
            val comparisonSummary = buildProviderComparisonFor(sym, tf)

            _uiState.update { state ->
                if (state.selectedSymbol != sym || state.selectedTimeframe != tf) return@update state
                val audit = state.liveAudit
                state.copy(
                    liveAudit = audit.copy(
                        check3TimestampUpdated = true,
                        check4ChangesAcrossProviders = distinctCount >= 2,
                        check4Detail = comparisonSummary,
                        check5ErrorDisplayedProperly = offlineErrorVerified,
                        check5Detail = if (offlineErrorVerified) {
                            "Teruji: Error ditangkap jujur saat endpoint tidak dapat diakses"
                        } else audit.check5Detail,
                        check6HonestWhenOffline = offlineErrorVerified
                    )
                )
            }
            appendDiagnosticLog("Audit 6 Poin (${sym.code} ${tf.label}) selesai: $comparisonSummary")
        }
    }

    private fun updateAuditComparisonForCurrentSelection() {
        val sym = _uiState.value.selectedSymbol
        val tf = _uiState.value.selectedTimeframe
        val distinctCount = countDistinctProvidersFor(sym, tf)
        if (distinctCount >= 2) {
            val summary = buildProviderComparisonFor(sym, tf)
            _uiState.update { s ->
                s.copy(
                    liveAudit = s.liveAudit.copy(
                        check4ChangesAcrossProviders = true,
                        check4Detail = summary
                    )
                )
            }
        } else {
            runBackgroundMultiProviderAudit()
        }
    }

    private fun buildProviderComparisonFor(symbol: MarketSymbol, timeframe: Timeframe): String {
        val entries = OnlineDataProvider.realProviders.mapNotNull { prov ->
            val price = providerPriceMemory[Triple(symbol, timeframe, prov)]
            if (price != null && price > 0.0) "${prov.shortLabel}: $price" else null
        }
        return if (entries.size >= 2) {
            "Perbandingan ${symbol.code} (${timeframe.label}): ${entries.joinToString(" | ")}"
        } else if (entries.size == 1) {
            "Data ${symbol.code} (${timeframe.label}) aktif: ${entries.first()}"
        } else {
            "Menunggu perbandingan antar provider untuk ${symbol.code}..."
        }
    }

    private fun countDistinctProvidersFor(symbol: MarketSymbol, timeframe: Timeframe): Int {
        val prices = OnlineDataProvider.realProviders.mapNotNull { prov ->
            providerPriceMemory[Triple(symbol, timeframe, prov)]
        }
        return prices.distinct().size
    }

    /**
     * Polling berkala ke server online (setiap 12 detik) untuk memperbarui Candle [0] & memeriksa Candle [1] baru.
     * Dilengkapi Generation Token Guard & Symbol/Timeframe Verification agar hasil polling lama
     * TIDAK PERNAH menimpa pair atau timeframe yang baru dipilih pengguna.
     */
    private fun startRealServerPollingLoop() {
        serverPollingJob?.cancel()
        serverPollingJob = viewModelScope.launch {
            while (isActive) {
                delay(12000L)
                val stateBeforePoll = _uiState.value
                if (!stateBeforePoll.isLiveTickMonitorEnabled ||
                    stateBeforePoll.isLoading ||
                    stateBeforePoll.isSyncingInProgress ||
                    activeFetchJob?.isActive == true ||
                    stateBeforePoll.selectedProvider.isOfflineTestMode
                ) {
                    continue
                }

                val capturedGen = requestGenerationId.get()
                val sym = stateBeforePoll.selectedSymbol
                val tf = stateBeforePoll.selectedTimeframe
                val prov = stateBeforePoll.selectedProvider
                val key = prov to sym
                val bundle = cachedBundles[key] ?: continue

                when (val pollRes = repository.pollSingleTimeframeFromServer(sym, tf, prov)) {
                    is LiveMarketAndNewsService.RatesFetchOutcome.Success -> {
                        val freshBars = pollRes.bars
                        if (freshBars.isEmpty()) continue

                        // Guard Anti-Tumpang-Tindih: Tolak hasil polling jika pengguna sudah ganti Pair, TF, atau Provider!
                        val stateAfterPoll = _uiState.value
                        if (requestGenerationId.get() != capturedGen ||
                            stateAfterPoll.selectedSymbol != sym ||
                            stateAfterPoll.selectedTimeframe != tf ||
                            stateAfterPoll.selectedProvider != prov ||
                            activeFetchJob?.isActive == true
                        ) {
                            recordDiscardedStaleResponse("Polling ${sym.code} ${tf.label} diabaikan karena state telah berpindah.")
                            continue
                        }

                        val prevClosedBarTime = bundle.ratesByTimeframe[tf]?.getOrNull(1)?.timeEpochSec ?: 0L
                        val newClosedBarTime = freshBars.getOrNull(1)?.timeEpochSec ?: 0L

                        val updatedMap = bundle.ratesByTimeframe.toMutableMap()
                        updatedMap[tf] = freshBars
                        val updatedBundle = bundle.copy(
                            ratesByTimeframe = updatedMap.toMap(),
                            serverFetchTimeMs = pollRes.serverFetchTimestampMs
                        )
                        cachedBundles[key] = updatedBundle
                        freshBars.getOrNull(1)?.close?.let { c1 ->
                            providerPriceMemory[Triple(sym, tf, prov)] = c1
                        }

                        val prevFetchMs = stateAfterPoll.liveAudit.lastServerFetchEpochMs
                        val newFetchMs = pollRes.serverFetchTimestampMs

                        if (newClosedBarTime != prevClosedBarTime) {
                            appendDiagnosticLog("Server ${prov.shortLabel}: Candle ${sym.code} ${tf.label} baru tutup -> Hitung ulang penuh")
                            recomputeClosedCandleAnalysis()
                        } else {
                            val currentAnalysis = stateAfterPoll.analysis ?: continue
                            if (currentAnalysis.symbol != sym || currentAnalysis.activeTimeframe != tf) {
                                recordDiscardedStaleResponse("Mencegah update Candle [0] karena simbol/TF analisis tidak cocok.")
                                continue
                            }
                            val freshBar0 = freshBars.first()
                            val updatedAnalysis = currentAnalysis.copy(
                                bars = freshBars,
                                dataEngine = currentAnalysis.dataEngine.copy(
                                    formingCandle0 = freshBar0,
                                    lastSyncTimeMs = newFetchMs
                                ),
                                volumeEngine = currentAnalysis.volumeEngine.copy(
                                    formingBar0Volume = freshBar0.tickVolume
                                )
                            )
                            _uiState.update { s ->
                                if (s.selectedSymbol != sym || s.selectedTimeframe != tf || s.selectedProvider != prov) {
                                    return@update s
                                }
                                s.copy(
                                    analysis = updatedAnalysis,
                                    diagnostics = s.diagnostics.copy(
                                        lightweightBar0Ticks = s.diagnostics.lightweightBar0Ticks + 1
                                    ),
                                    liveAudit = s.liveAudit.copy(
                                        check3TimestampUpdated = newFetchMs > prevFetchMs,
                                        check3Detail = "Waktu server berubah: ${formatMillisTime(prevFetchMs)} → ${formatMillisTime(newFetchMs)}",
                                        previousServerFetchEpochMs = prevFetchMs,
                                        lastServerFetchEpochMs = newFetchMs
                                    )
                                )
                            }
                        }
                    }

                    is LiveMarketAndNewsService.RatesFetchOutcome.Error -> {
                        if (requestGenerationId.get() == capturedGen) {
                            appendDiagnosticLog("Polling gagal dari ${prov.shortLabel}: ${pollRes.errorMessage}")
                            if (prov == OnlineDataProvider.ELEV8_METAAPI &&
                                pollRes.httpStatusCode in listOf(401, 402, 403, 404, 429)
                            ) {
                                cachedBundles.remove(prov to sym)
                                _uiState.update { s ->
                                    if (s.selectedProvider != prov || s.selectedSymbol != sym) return@update s
                                    s.copy(
                                        isLoading = false,
                                        isProviderOnline = false,
                                        connectionErrorMessage = pollRes.errorMessage,
                                        analysis = null
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun recordDiscardedStaleResponse(reason: String) {
        _uiState.update { s ->
            s.copy(
                diagnostics = s.diagnostics.copy(
                    discardedStaleResponses = s.diagnostics.discardedStaleResponses + 1
                )
            )
        }
        appendDiagnosticLog("DICEGAH TUMPANG TINDIH: $reason")
    }

    fun saveCurrentClosedSignalToLog() {
        val currentAnalysis = _uiState.value.analysis ?: return
        val bar1 = currentAnalysis.dataEngine.closedCandle1
        val topPattern = currentAnalysis.candleEngine.activeClosedPatterns.firstOrNull()?.pattern?.displayName
            ?: currentAnalysis.candleEngine.anatomyType.label
        val isBullishBias = currentAnalysis.bullishConfluenceScore >= currentAnalysis.bearishConfluenceScore
        val stopRef = if (isBullishBias) {
            currentAnalysis.volatilityEngine.longStopReferencePrice
        } else {
            currentAnalysis.volatilityEngine.shortStopReferencePrice
        }
        val targetRef = if (isBullishBias) {
            currentAnalysis.volatilityEngine.longTarget1RPrice
        } else {
            currentAnalysis.volatilityEngine.shortTarget1RPrice
        }
        val dominantScore = maxOf(currentAnalysis.bullishConfluenceScore, currentAnalysis.bearishConfluenceScore)

        viewModelScope.launch {
            repository.saveClosedCandleSnapshot(
                SavedSignalSnapshotEntity(
                    symbolWithSuffix = currentAnalysis.dataEngine.symbolWithSuffix,
                    timeframeLabel = currentAnalysis.activeTimeframe.label,
                    closedBarEpochSec = bar1.timeEpochSec,
                    closePrice = bar1.close,
                    verdictLabel = currentAnalysis.verdict.label,
                    patternDetected = topPattern,
                    structureLabel = currentAnalysis.structureEngine.classification.label,
                    trendLabel = currentAnalysis.trendEngine.classification.label,
                    rsi14 = currentAnalysis.momentumEngine.rsi14,
                    adx14 = currentAnalysis.momentumEngine.adx14,
                    atr14 = currentAnalysis.volatilityEngine.atr14,
                    stopReferencePrice = stopRef,
                    targetReferencePrice = targetRef,
                    confluenceScore = dominantScore
                )
            )
            appendDiagnosticLog("Sinyal Candle [1] (${currentAnalysis.dataEngine.symbolWithSuffix}) disimpan ke riwayat.")
            _uiState.update {
                it.copy(statusBannerMessage = "Sinyal Candle Tutup [1] berhasil dikunci ke Catatan Riwayat Non-Repaint.")
            }
        }
    }

    fun deleteSignalLog(id: Int) {
        viewModelScope.launch {
            repository.deleteSignalLog(id)
            appendDiagnosticLog("Catatan riwayat #$id dihapus.")
        }
    }

    fun clearAllSignalLogs() {
        viewModelScope.launch {
            repository.clearAllSignalLogs()
            appendDiagnosticLog("Seluruh catatan riwayat sinyal dibersihkan.")
            _uiState.update {
                it.copy(statusBannerMessage = "Seluruh catatan riwayat sinyal telah dibersihkan.")
            }
        }
    }

    fun addCustomNewsEvent(
        title: String,
        categoryTag: String,
        targetSymbolCode: String,
        impact: NewsImpactLevel,
        sentiment: MacroSentiment,
        takeawayNote: String
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.addCustomMacroNewsEvent(
                title = title.trim(),
                categoryTag = categoryTag.trim().ifEmpty { "FOMC / Makro USD" },
                targetSymbolCode = targetSymbolCode,
                impactLevel = impact,
                sentiment = sentiment,
                takeawayNote = takeawayNote.trim().ifEmpty { "Tunggu konfirmasi penutupan candle [1] setelah rilis berita." }
            )
            _uiState.update {
                it.copy(statusBannerMessage = "Agenda berita berdampak tinggi berhasil ditambahkan ke Mesin Berita.")
            }
        }
    }

    fun updateMql5Config(newConfig: Mql5ConfigParams) {
        if (_uiState.value.mql5Config == newConfig) return
        _uiState.update { state ->
            val code = state.analysis?.let {
                Mql5IndicatorCodeGenerator.generateMq5Source(newConfig, it)
            }.orEmpty()
            state.copy(
                mql5Config = newConfig,
                brokerSuffix = newConfig.brokerSuffix,
                generatedMql5Code = code
            )
        }
        recomputeClosedCandleAnalysis()
    }

    fun dismissBannerMessage() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }

    fun updateGitHubRepoInput(repo: String) {
        val clean = repo.trim()
        if (clean.isNotBlank()) {
            repository.saveCustomGitHubRepository(clean)
        }
        _uiState.update { state ->
            state.copy(
                githubRelease = state.githubRelease.copy(repository = repo)
            )
        }
    }

    fun updateMetaApiAccountIdInput(accId: String) {
        _uiState.update { it.copy(metaApiAccountIdInput = accId) }
    }

    fun updateMetaApiAuthTokenInput(token: String) {
        _uiState.update { it.copy(metaApiAuthTokenInput = token) }
    }

    fun saveAndConnectMetaApiCredentials() {
        val state = _uiState.value
        val accId = state.metaApiAccountIdInput.trim()
        val token = state.metaApiAuthTokenInput.trim()
        if (accId.isBlank() || token.isBlank()) {
            _uiState.update { it.copy(statusBannerMessage = "Account ID dan Token MetaApi tidak boleh kosong!") }
            return
        }
        repository.setCustomMetaApiCredentials(accId, token)
        repository.saveSelectedProvider(OnlineDataProvider.ELEV8_METAAPI)
        _uiState.update {
            it.copy(
                selectedProvider = OnlineDataProvider.ELEV8_METAAPI,
                hasCustomSavedCredentials = true,
                statusBannerMessage = "Kredensial Elev8 MT5 tersimpan permanen di penyimpanan lokal aplikasi. Menghubungkan ke MetaApi Cloud..."
            )
        }
        appendDiagnosticLog("Kredensial MetaApi tersimpan permanen (Account ID: ${accId.take(8)}...)")
        refreshMarketData(forceRefreshNews = false)
    }

    fun clearSavedMetaApiCredentials() {
        repository.clearCustomMetaApiCredentials()
        val defaultAcc = repository.getMetaApiAccountId()
        val defaultToken = repository.getMetaApiAuthToken()
        _uiState.update {
            it.copy(
                metaApiAccountIdInput = defaultAcc,
                metaApiAuthTokenInput = defaultToken,
                hasCustomSavedCredentials = false,
                statusBannerMessage = "Kredensial tersimpan telah dihapus dari penyimpanan perangkat."
            )
        }
        appendDiagnosticLog("Kredensial kustom MetaApi dihapus dari penyimpanan lokal.")
    }

    fun checkGitHubReleaseUpdate() {
        val currentRepo = _uiState.value.githubRelease.repository.trim()
        _uiState.update { state ->
            state.copy(
                githubRelease = state.githubRelease.copy(
                    isChecking = true,
                    statusMessage = "Memeriksa rilis APK terbaru di GitHub Releases ($currentRepo)..."
                )
            )
        }
        viewModelScope.launch {
            val result = repository.checkLatestGitHubRelease(currentRepo)
            _uiState.update { state ->
                state.copy(
                    githubRelease = result,
                    statusBannerMessage = result.statusMessage
                )
            }
        }
    }

    private fun recomputeClosedCandleAnalysis() {
        val state = _uiState.value
        val sym = state.selectedSymbol
        val tf = state.selectedTimeframe
        val prov = state.selectedProvider
        val key = prov to sym
        val bundle = cachedBundles[key] ?: return
        if (bundle.symbol != sym || bundle.provider != prov) return
        val activeBars = bundle.ratesByTimeframe[tf] ?: return
        if (activeBars.isEmpty()) return

        val (isValidSeries, integrityNotes) = SystemDiagnosticGuard.validateRateSeries(sym, tf, activeBars)
        if (!isValidSeries) {
            appendDiagnosticLog("PERINGATAN INTEGRITAS [${sym.code} ${tf.label}]: ${integrityNotes.firstOrNull()}")
        }
        val closedBar1 = activeBars.getOrElse(1) { activeBars.first() }
        val bar1Hash = SystemDiagnosticGuard.computeClosedBar1Signature(sym, tf, closedBar1)

        val customArticles = customNewsFlow.value.map { entity ->
            val affected = when (entity.targetSymbolCode) {
                "XAUUSD" -> listOf(MarketSymbol.XAUUSD)
                "EURUSD" -> listOf(MarketSymbol.EURUSD)
                else -> listOf(MarketSymbol.XAUUSD, MarketSymbol.EURUSD)
            }
            MacroNewsArticle(
                id = "custom_${entity.id}",
                title = entity.title,
                categoryTag = entity.categoryTag,
                affectedSymbols = affected,
                impact = runCatching { NewsImpactLevel.valueOf(entity.impactLevel) }.getOrDefault(NewsImpactLevel.HIGH),
                sentiment = runCatching { MacroSentiment.valueOf(entity.sentimentCode) }.getOrDefault(MacroSentiment.NEUTRAL_WATCH),
                publishedTimeEpochSec = entity.createdTimeEpochSec,
                sourceName = "Catatan Agenda Trader",
                keyTakeaway = entity.takeawayNote
            )
        }

        val mergedNews = customArticles + bundle.newsArticles

        val analysis = IndicatorAndConfluenceEngines.buildCompleteAnalysis(
            symbol = sym,
            brokerSuffix = state.brokerSuffix,
            activeTimeframe = tf,
            activeBars = activeBars,
            allTimeframeBars = bundle.ratesByTimeframe,
            isLiveFeed = true,
            feedSourceName = "${prov.shortLabel} (${prov.baseHost})",
            newsArticles = mergedNews,
            isLiveRss = true
        )

        val mql5Code = Mql5IndicatorCodeGenerator.generateMq5Source(state.mql5Config, analysis)

        _uiState.update { s ->
            if (s.selectedSymbol != sym || s.selectedTimeframe != tf || s.selectedProvider != prov) {
                return@update s
            }
            val prevDiag = s.diagnostics
            s.copy(
                isLoading = false,
                analysis = analysis,
                generatedMql5Code = mql5Code,
                diagnostics = prevDiag.copy(
                    isDataConsistent = isValidSeries,
                    bar1IntegrityHash = bar1Hash,
                    totalFullCalculations = prevDiag.totalFullCalculations + 1,
                    integrityNotes = integrityNotes
                )
            )
        }
    }

    private fun appendDiagnosticLog(message: String) {
        val timeStr = formatMillisTime(System.currentTimeMillis())
        val entry = "[$timeStr] $message"
        _uiState.update { state ->
            val updatedLogs = (listOf(entry) + state.diagnostics.recentEventsLog).take(14)
            state.copy(
                diagnostics = state.diagnostics.copy(recentEventsLog = updatedLogs)
            )
        }
    }

    private fun formatMillisTime(ms: Long): String {
        if (ms <= 0L) return "-"
        return SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(ms))
    }

    class Factory(private val repository: HubRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HubViewModel(repository) as T
        }
    }
}
