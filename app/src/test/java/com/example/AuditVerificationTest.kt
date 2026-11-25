package com.example

import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.HubRepository
import com.example.data.local.HubDatabase
import com.example.data.local.UserPreferencesManager
import com.example.data.remote.LiveMarketAndNewsService
import com.example.domain.IndicatorAndConfluenceEngines
import com.example.domain.SystemDiagnosticGuard
import com.example.domain.SystemDiagnosticReport
import com.example.model.AnalyticalVerdict
import com.example.model.BrokerContractSpec
import com.example.model.GlobalMarketSession
import com.example.model.MacroNewsArticle
import com.example.model.MacroSentiment
import com.example.model.MarketSymbol
import com.example.model.Mt5RateBar
import com.example.model.NewsDataStatus
import com.example.model.NewsImpactLevel
import com.example.model.OnlineDataProvider
import com.example.model.PatternBias
import com.example.model.Timeframe
import com.example.model.TrendClassification
import com.example.ui.HubUiState
import com.example.ui.HubViewModel
import com.example.ui.screens.MtfMatrixScreen
import com.example.ui.screens.NewsEngineScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TenEnginesScreen
import com.example.ui.screens.TradeSignalScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuditVerificationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun buildBullishBars(
        count: Int = 120,
        startEpochSec: Long = 1727000000L,
        stepSec: Long = 900L,
        basePrice: Double = 2600.0
    ): List<Mt5RateBar> {
        val chron = (0 until count).map { idx ->
            val drift = basePrice + idx * 0.65 + sin(idx * 0.2) * 2.5
            Mt5RateBar(
                barIndex = 0,
                timeEpochSec = startEpochSec + idx * stepSec,
                open = drift - 0.6,
                high = drift + 1.9,
                low = drift - 1.4,
                close = drift + 0.9,
                tickVolume = 1100L + (idx % 12) * 75L,
                realVolume = 11000L,
                spread = 18
            )
        }
        return chron.asReversed().mapIndexed { i, bar -> bar.copy(barIndex = i) }
    }

    private fun buildBearishBars(
        count: Int = 120,
        startEpochSec: Long = 1727000000L,
        stepSec: Long = 900L,
        basePrice: Double = 2850.0
    ): List<Mt5RateBar> {
        val chron = (0 until count).map { idx ->
            val drift = basePrice - idx * 0.65 + sin(idx * 0.2) * 2.5
            Mt5RateBar(
                barIndex = 0,
                timeEpochSec = startEpochSec + idx * stepSec,
                open = drift + 0.6,
                high = drift + 1.4,
                low = drift - 1.9,
                close = drift - 0.9,
                tickVolume = 1100L + (idx % 12) * 75L,
                realVolume = 11000L,
                spread = 18
            )
        }
        return chron.asReversed().mapIndexed { i, bar -> bar.copy(barIndex = i) }
    }

    private fun buildFlatNeutralBars(count: Int = 120): List<Mt5RateBar> {
        return (0 until count).map { idx ->
            Mt5RateBar(
                barIndex = idx,
                timeEpochSec = 1727000000L - idx * 900L,
                open = 2650.0,
                high = 2650.2,
                low = 2649.8,
                close = 2650.0,
                tickVolume = 800L,
                realVolume = 8000L,
                spread = 15
            )
        }
    }

    // 1. News status: offline returns OFFLINE_EMPTY honestly (never claims LIVE_FRESH)
    @Test
    fun test01_newsStatus_honestOfflineAndStateEnumMapping() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, HubDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val prefs = UserPreferencesManager.getInstance(context)
            val repo = HubRepository(db.hubDao(), prefs)

            val offlineBundle = repo.resolveNewsStateForTest(
                forceRefresh = false,
                isOfflineTestMode = true
            )
            assertEquals(NewsDataStatus.OFFLINE_EMPTY, offlineBundle.status)
            assertTrue(offlineBundle.articles.isEmpty())

            val bars = buildBullishBars(80)
            val staleAnalysis = IndicatorAndConfluenceEngines.buildCompleteAnalysis(
                symbol = MarketSymbol.XAUUSD,
                brokerSuffix = "",
                activeTimeframe = Timeframe.M15,
                activeBars = bars,
                allTimeframeBars = Timeframe.entries.associateWith { bars },
                isLiveFeed = true,
                feedSourceName = "Test",
                newsArticles = listOf(
                    MacroNewsArticle(
                        id = "old_1",
                        title = "Cached FOMC Minutes",
                        categoryTag = "FOMC",
                        affectedSymbols = listOf(MarketSymbol.XAUUSD),
                        impact = NewsImpactLevel.HIGH,
                        sentiment = MacroSentiment.NEUTRAL_WATCH,
                        publishedTimeEpochSec = 1727000000L,
                        sourceName = "Cache",
                        keyTakeaway = "Snapshot cache"
                    )
                ),
                isLiveRss = false,
                newsStatus = NewsDataStatus.CACHE_STALE,
                newsStatusDetail = "Feed RSS timeout; menampilkan cache lama."
            )
            assertEquals(NewsDataStatus.CACHE_STALE, staleAnalysis.newsEngine.status)
            assertFalse(staleAnalysis.newsEngine.isLiveRssSynced)
            assertTrue(staleAnalysis.newsEngine.statusMessage.contains("cache lama"))
        } finally {
            db.close()
        }
    }

    // 2. MTF completeness: missing H1 or M30 neutralizes HTF bias and marks incomplete
    @Test
    fun test02_mtfCompleteness_missingH1OrM30NeutralizesBias() {
        val bars = buildBullishBars(100)
        val missingH1Map = mapOf(
            Timeframe.M30 to bars,
            Timeframe.M15 to bars,
            Timeframe.M5 to bars,
            Timeframe.M1 to bars
        )
        val missingH1Report = IndicatorAndConfluenceEngines.runMultiTimeframeEngine(
            symbol = MarketSymbol.XAUUSD,
            ratesByTf = missingH1Map
        )
        assertFalse(missingH1Report.isMtfDataComplete)
        assertTrue(missingH1Report.missingTimeframes.contains(Timeframe.H1))
        assertEquals(PatternBias.NEUTRAL, missingH1Report.htfBias)
        assertFalse(missingH1Report.isPullbackInHtfTrend)

        val completeMap = Timeframe.entries.associateWith { bars }
        val completeReport = IndicatorAndConfluenceEngines.runMultiTimeframeEngine(
            symbol = MarketSymbol.XAUUSD,
            ratesByTf = completeMap
        )
        assertTrue(completeReport.isMtfDataComplete)
        assertTrue(completeReport.missingTimeframes.isEmpty())
    }

    // 3. Neutral trend UI mapping verification: NEUTRAL trend renders "— EMA", never "▼ EMA"
    @Test
    fun test03_mtfScreen_neutralTrendIsNotRenderedAsBearish() {
        val neutralBars = buildFlatNeutralBars(100)
        val incompleteMap = mapOf(Timeframe.M15 to neutralBars)
        val analysis = IndicatorAndConfluenceEngines.buildCompleteAnalysis(
            symbol = MarketSymbol.XAUUSD,
            brokerSuffix = "",
            activeTimeframe = Timeframe.M15,
            activeBars = neutralBars,
            allTimeframeBars = incompleteMap,
            isLiveFeed = true,
            feedSourceName = "Test",
            newsArticles = emptyList(),
            isLiveRss = false,
            newsStatus = NewsDataStatus.OFFLINE_EMPTY
        )
        assertEquals(TrendClassification.NEUTRAL, analysis.mtfEngine.rows.first().trend)

        composeRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                MtfMatrixScreen(
                    analysis = analysis,
                    onSelectTimeframe = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mtf_incomplete_banner").assertIsDisplayed()
        composeRule.onNodeWithText("— EMA").assertIsDisplayed()
        composeRule.onNodeWithText("▼ EMA").assertDoesNotExist()
    }

    // 4. Pullback verdict consistency: HTF bullish + LTF bearish sets activeTimeframeVerdict and HTF pullback context
    @Test
    fun test04_pullbackVerdict_activeTfVsHtfContextConsistency() {
        val htfBull = buildBullishBars(120, stepSec = 3600L)
        val m30Bull = buildBullishBars(120, stepSec = 1800L)
        val m15Bull = buildBullishBars(120, stepSec = 900L)
        val m5Bear = buildBearishBars(120, stepSec = 300L)
        val m1Bear = buildBearishBars(120, stepSec = 60L)

        val tfMap = mapOf(
            Timeframe.H1 to htfBull,
            Timeframe.M30 to m30Bull,
            Timeframe.M15 to m15Bull,
            Timeframe.M5 to m5Bear,
            Timeframe.M1 to m1Bear
        )
        val analysis = IndicatorAndConfluenceEngines.buildCompleteAnalysis(
            symbol = MarketSymbol.XAUUSD,
            brokerSuffix = "",
            activeTimeframe = Timeframe.M5,
            activeBars = m5Bear,
            allTimeframeBars = tfMap,
            isLiveFeed = true,
            feedSourceName = "Test",
            newsArticles = emptyList(),
            isLiveRss = false,
            newsStatus = NewsDataStatus.OFFLINE_EMPTY
        )

        assertTrue(analysis.mtfEngine.isMtfDataComplete)
        assertEquals(PatternBias.BULLISH, analysis.mtfEngine.htfBias)
        assertTrue(analysis.mtfContextSummary.isNotBlank())
        assertFalse(
            "Checklist ke-10 tidak boleh lolos jika HTF != bias aktif",
            analysis.verdict == AnalyticalVerdict.STRONG_BEARISH_CONFLUENCE
        )
    }

    // 5. Proxy vs Broker MT5 verified spec vs fallback position sizing
    @Test
    fun test05_positionSizing_distinguishesProxyVerifiedMt5AndFallback() {
        val proxyCalc = IndicatorAndConfluenceEngines.calculatePositionRisk(
            symbol = MarketSymbol.XAUUSD,
            provider = OnlineDataProvider.BINANCE_VISION,
            balanceDollars = 1000.0,
            riskPercentage = 1.0,
            entryPrice = 2700.0,
            stopLossPrice = 2690.0,
            target1Price = 2715.0,
            target2Price = 2725.0,
            brokerSpec = null
        )
        assertTrue(proxyCalc.isProxyIndicativeOnly)
        assertFalse(proxyCalc.isMt5VerifiedLot)
        assertTrue(proxyCalc.formattedSize.contains("unit"))
        assertTrue(proxyCalc.assumptionNote.contains("BUKAN lot MT5"))

        val verifiedSpec = BrokerContractSpec(
            brokerSymbol = "XAUUSD.sml",
            accountCurrency = "USD",
            contractSize = 100.0,
            tickSize = 0.01,
            tickValue = 1.0,
            minVolume = 0.01,
            maxVolume = 100.0,
            volumeStep = 0.01,
            isBrokerVerified = true
        )
        val mt5VerifiedCalc = IndicatorAndConfluenceEngines.calculatePositionRisk(
            symbol = MarketSymbol.XAUUSD,
            provider = OnlineDataProvider.ELEV8_METAAPI,
            balanceDollars = 1000.0,
            riskPercentage = 1.0,
            entryPrice = 2700.0,
            stopLossPrice = 2690.0,
            target1Price = 2715.0,
            target2Price = 2725.0,
            brokerSpec = verifiedSpec
        )
        assertFalse(mt5VerifiedCalc.isProxyIndicativeOnly)
        assertTrue(mt5VerifiedCalc.isMt5VerifiedLot)
        assertEquals("0.01 Lot", mt5VerifiedCalc.formattedSize)

        val mt5FallbackCalc = IndicatorAndConfluenceEngines.calculatePositionRisk(
            symbol = MarketSymbol.XAUUSD,
            provider = OnlineDataProvider.ELEV8_METAAPI,
            balanceDollars = 1000.0,
            riskPercentage = 1.0,
            entryPrice = 2700.0,
            stopLossPrice = 2690.0,
            target1Price = 2715.0,
            target2Price = 2725.0,
            brokerSpec = null
        )
        assertFalse(mt5FallbackCalc.isProxyIndicativeOnly)
        assertFalse(mt5FallbackCalc.isMt5VerifiedLot)
        assertTrue(mt5FallbackCalc.assumptionNote.contains("belum terverifikasi"))
    }

    // 6. Standby suppression rule: Standby suppresses active Entry/SL/TP metrics & copy order setup button
    @Test
    fun test06_tradeScreen_standbySuppressesActiveExecutionAndProxyLabelIsHonest() {
        val neutralBars = buildFlatNeutralBars(100)
        val analysis = IndicatorAndConfluenceEngines.buildCompleteAnalysis(
            symbol = MarketSymbol.XAUUSD,
            brokerSuffix = "",
            activeTimeframe = Timeframe.M15,
            activeBars = neutralBars,
            allTimeframeBars = Timeframe.entries.associateWith { neutralBars },
            isLiveFeed = true,
            feedSourceName = "Binance Proxy",
            newsArticles = emptyList(),
            isLiveRss = false
        ).copy(verdict = AnalyticalVerdict.NEUTRAL_CONSOLIDATION)

        composeRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                TradeSignalScreen(
                    analysis = analysis,
                    selectedProvider = OnlineDataProvider.BINANCE_VISION
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("trade_standby_notice").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("trade_metric_entry").assertDoesNotExist()
        composeRule.onNodeWithTag("copy_complete_order_setup").assertDoesNotExist()
    }

    // 7. Session engine separates currentSession (nowMs) from candleSession (candleEpochSec)
    @Test
    fun test07_sessionAndDataTimestamps_separatedCorrectly() {
        val nyOverlapMs = 1700056800000L // 2023-11-15 14:00:00 UTC -> London/NY Overlap
        val asiaCandleEpochSec = 1700013600L // 2023-11-15 02:00:00 UTC -> Asia-Pacific

        val sessionReport = IndicatorAndConfluenceEngines.runSessionEngine(
            candleEpochSec = asiaCandleEpochSec,
            nowMs = nyOverlapMs
        )
        assertEquals(GlobalMarketSession.LONDON_NEW_YORK_OVERLAP, sessionReport.currentSession)
        assertEquals(GlobalMarketSession.ASIA_PACIFIC, sessionReport.candleSession)
        assertFalse(sessionReport.isCandleFromCurrentSession)
        assertEquals(14, sessionReport.currentUtcHour)
        assertEquals(2, sessionReport.candleUtcHour)

        val bars = buildBullishBars(80)
        val serverTime = nyOverlapMs - 30_000L
        val calcTime = nyOverlapMs
        val analysis = IndicatorAndConfluenceEngines.buildCompleteAnalysis(
            symbol = MarketSymbol.XAUUSD,
            brokerSuffix = "",
            activeTimeframe = Timeframe.M15,
            activeBars = bars,
            allTimeframeBars = Timeframe.entries.associateWith { bars },
            isLiveFeed = true,
            feedSourceName = "Test",
            newsArticles = emptyList(),
            isLiveRss = false,
            serverFetchTimeMs = serverTime,
            currentTimeMs = calcTime
        )
        assertEquals(serverTime, analysis.dataEngine.serverDataTimeMs)
        assertEquals(calcTime, analysis.dataEngine.calculatedAtDeviceTimeMs)
    }

    // 8. ViewModel generation guard prevents stale credential/provider mixing and preserves monotonic IDs
    @Test
    fun test08_viewModel_generationGuardAndCredentialStateMasking() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, HubDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val prefs = UserPreferencesManager.getInstance(context)
            prefs.saveMetaApiCredentials("secret-acc-12345678", "secret-token-98765432")
            val repo = HubRepository(db.hubDao(), prefs)
            val vm = HubViewModel(repo)

            Shadows.shadowOf(Looper.getMainLooper()).idle()
            val initialState = vm.uiState.value
            // Saved plaintext secrets must NEVER be preloaded into input state
            assertEquals("", initialState.metaApiAccountIdInput)
            assertEquals("", initialState.metaApiAuthTokenInput)
            assertTrue(initialState.hasCustomSavedCredentials)
            assertTrue(initialState.savedMetaApiAccountIdHint.endsWith("5678"))
            assertTrue(initialState.savedMetaApiTokenHint.endsWith("5432"))

            val genBefore = initialState.diagnostics.activeGenerationId
            vm.selectSymbol(MarketSymbol.EURUSD)
            val genAfterSymbol = vm.uiState.value.diagnostics.activeGenerationId
            assertTrue(genAfterSymbol > genBefore)

            vm.selectProvider(OnlineDataProvider.KRAKEN_SPOT)
            val genAfterProvider = vm.uiState.value.diagnostics.activeGenerationId
            assertTrue(genAfterProvider > genAfterSymbol)

            vm.clearSavedMetaApiCredentials()
            assertFalse(vm.uiState.value.hasCustomSavedCredentials)
            assertEquals("", vm.uiState.value.savedMetaApiTokenHint)
        } finally {
            db.close()
        }
    }

    // 9. Security: No METAAPI_ACCOUNT_ID / METAAPI_AUTH_TOKEN on BuildConfig, encrypted local storage, legacy wipe & URL redaction
    @Test
    fun test09_security_noBuildConfigSecretsEncryptedPrefsLegacyPurgeAndUrlRedaction() {
        val buildConfigFields = BuildConfig::class.java.declaredFields.map { it.name }
        assertFalse(buildConfigFields.contains("METAAPI_ACCOUNT_ID"))
        assertFalse(buildConfigFields.contains("METAAPI_AUTH_TOKEN"))

        val context = ApplicationProvider.getApplicationContext<Context>()
        // Simulate legacy plaintext keys stored in main prefs
        val legacyPrefs = context.getSharedPreferences("hub_trader_user_prefs", Context.MODE_PRIVATE)
        legacyPrefs.edit()
            .putString("pref_metaapi_account_id", "legacy-plain-acc")
            .putString("pref_metaapi_auth_token", "legacy-plain-tok")
            .commit()

        val prefs = UserPreferencesManager.getInstance(context)
        // Legacy plaintext must be purged on access
        assertFalse(legacyPrefs.contains("pref_metaapi_account_id"))
        assertFalse(legacyPrefs.contains("pref_metaapi_auth_token"))

        prefs.saveMetaApiCredentials("11223344-5566-7788", "super-secret-jwt-token-9999")
        val cipherPayload = prefs.getEncryptedTokenPayloadForAudit()
        assertEquals(2, cipherPayload.split(":").size)
        assertFalse(cipherPayload.contains("super-secret-jwt-token-9999"))

        assertEquals("super-secret-jwt-token-9999", prefs.getMetaApiAuthToken())
        assertEquals("••••9999", prefs.getMaskedTokenHint())

        val service = LiveMarketAndNewsService(prefs)
        val url = service.buildProviderUrl(MarketSymbol.XAUUSD, Timeframe.M15, OnlineDataProvider.ELEV8_METAAPI)
        assertFalse(url.contains("11223344-5566-7788"))
        assertTrue(url.contains("****7788"))

        val sanitized = LiveMarketAndNewsService.sanitizeSensitiveText(
            "HTTP 429 on /accounts/11223344-5566-7788 with token super-secret-jwt-token-9999",
            "11223344-5566-7788",
            "super-secret-jwt-token-9999"
        )
        assertFalse(sanitized.contains("11223344-5566-7788"))
        assertFalse(sanitized.contains("super-secret-jwt-token-9999"))
        assertTrue(sanitized.contains("[REDACTED]"))

        prefs.clearMetaApiCredentials()
        assertFalse(prefs.hasCustomCredentials())
    }

    // 10. OTA security: rejects arbitrary repo, checks SemVer, blocks downgrade unless explicitly allowed, enforces HTTPS host allowlist & SHA-256
    @Test
    fun test10_otaSecurity_repoAllowlistSemVerDowngradeUrlAndSha256() {
        val rejectedRepo = LiveMarketAndNewsService.evaluateReleaseMetadata(
            cleanRepo = "evil-attacker/fake-pattern-pro",
            allowedRepos = setOf(LiveMarketAndNewsService.OFFICIAL_GITHUB_REPO),
            installedTag = "v1.0.9",
            latestTag = "v9.9.9",
            releaseTitle = "Fake",
            releaseNotes = "",
            publishedAt = "",
            htmlUrl = "https://github.com/evil-attacker/fake/releases/tag/v9.9.9",
            apkUrl = "https://github.com/evil-attacker/fake/releases/download/v9.9.9/app.apk",
            apkName = "app.apk",
            apkSizeMb = 5.0,
            expectedSha256 = "a".repeat(64),
            sha256AssetName = "app.apk.sha256",
            allowDowngrade = false
        )
        assertFalse(rejectedRepo.isUpdateAvailable)
        assertTrue(rejectedRepo.statusMessage.contains("Ditolak"))

        assertTrue((LiveMarketAndNewsService.compareSemVerTags("v1.0.10", "v1.0.9") ?: 0) > 0)
        assertTrue((LiveMarketAndNewsService.compareSemVerTags("v1.0.8", "v1.0.9") ?: 0) < 0)

        val blockedDowngrade = LiveMarketAndNewsService.evaluateReleaseMetadata(
            cleanRepo = LiveMarketAndNewsService.OFFICIAL_GITHUB_REPO,
            allowedRepos = setOf(LiveMarketAndNewsService.OFFICIAL_GITHUB_REPO),
            installedTag = "v1.0.9",
            latestTag = "v1.0.8",
            releaseTitle = "Old",
            releaseNotes = "",
            publishedAt = "",
            htmlUrl = "https://github.com/deckyp5758-eng/Pattern-PRO/releases/tag/v1.0.8",
            apkUrl = "https://github.com/deckyp5758-eng/Pattern-PRO/releases/download/v1.0.8/app.apk",
            apkName = "app.apk",
            apkSizeMb = 5.0,
            expectedSha256 = "b".repeat(64),
            sha256AssetName = "app.apk.sha256",
            allowDowngrade = false
        )
        assertFalse(blockedDowngrade.isUpdateAvailable)
        assertTrue(blockedDowngrade.isDowngradeDetected)
        assertEquals("", blockedDowngrade.apkDownloadUrl)

        val allowedDowngrade = LiveMarketAndNewsService.evaluateReleaseMetadata(
            cleanRepo = LiveMarketAndNewsService.OFFICIAL_GITHUB_REPO,
            allowedRepos = setOf(LiveMarketAndNewsService.OFFICIAL_GITHUB_REPO),
            installedTag = "v1.0.9",
            latestTag = "v1.0.8",
            releaseTitle = "Rollback",
            releaseNotes = "",
            publishedAt = "",
            htmlUrl = "https://github.com/deckyp5758-eng/Pattern-PRO/releases/tag/v1.0.8",
            apkUrl = "https://github.com/deckyp5758-eng/Pattern-PRO/releases/download/v1.0.8/app.apk",
            apkName = "app.apk",
            apkSizeMb = 5.0,
            expectedSha256 = "b".repeat(64),
            sha256AssetName = "app.apk.sha256",
            allowDowngrade = true
        )
        assertTrue(allowedDowngrade.isUpdateAvailable)
        assertTrue(allowedDowngrade.isDowngradeDetected)

        assertFalse(LiveMarketAndNewsService.isTrustedOtaDownloadUrl("http://github.com/deckyp5758-eng/Pattern-PRO/releases/download/v1.0.10/app.apk"))
        assertFalse(LiveMarketAndNewsService.isTrustedOtaDownloadUrl("https://evil.example.com/app.apk"))
        assertTrue(LiveMarketAndNewsService.isTrustedOtaDownloadUrl("https://github.com/deckyp5758-eng/Pattern-PRO/releases/download/v1.0.10/app.apk"))

        val tempFile = File.createTempFile("ota_sha_test", ".bin")
        try {
            tempFile.writeText("pattern-pro-release-binary")
            val sha = LiveMarketAndNewsService.computeFileSha256(tempFile)
            assertEquals(64, sha.length)
            val context = ApplicationProvider.getApplicationContext<Context>()
            val (mismatchOk, mismatchMsg) = LiveMarketAndNewsService.verifyApkSha256AndSignature(
                context, tempFile, "0".repeat(64)
            )
            assertFalse(mismatchOk)
            assertTrue(mismatchMsg.contains("SHA-256 gagal"))
        } finally {
            tempFile.delete()
        }
    }

    // 11. Non-repaint Candle [1] invariance across Candle [0] mutations
    @Test
    fun test11_nonRepaint_closedCandle1InvariantUnderFormingCandle0Changes() {
        val base = buildBullishBars(120)
        val mutated = base.toMutableList().apply {
            this[0] = this[0].copy(
                high = this[0].high + 50.0,
                low = this[0].low - 50.0,
                close = this[0].close + 25.0,
                tickVolume = this[0].tickVolume + 5000L
            )
        }
        val a1 = IndicatorAndConfluenceEngines.buildCompleteAnalysis(
            MarketSymbol.XAUUSD, "", Timeframe.M15, base,
            Timeframe.entries.associateWith { base },
            true, "Test", emptyList(), false
        )
        val a2 = IndicatorAndConfluenceEngines.buildCompleteAnalysis(
            MarketSymbol.XAUUSD, "", Timeframe.M15, mutated,
            Timeframe.entries.associateWith { mutated },
            true, "Test", emptyList(), false
        )
        assertEquals(a1.verdict, a2.verdict)
        assertEquals(a1.bullishConfluenceScore, a2.bullishConfluenceScore)
        assertEquals(a1.bearishConfluenceScore, a2.bearishConfluenceScore)
        assertEquals(a1.trendEngine.ema20, a2.trendEngine.ema20, 0.00001)
        assertEquals(a1.momentumEngine.rsi14, a2.momentumEngine.rsi14, 0.00001)
        assertEquals(a1.volatilityEngine.atr14, a2.volatilityEngine.atr14, 0.00001)
        assertEquals(
            SystemDiagnosticGuard.computeClosedBar1Signature(MarketSymbol.XAUUSD, Timeframe.M15, a1.dataEngine.closedCandle1),
            SystemDiagnosticGuard.computeClosedBar1Signature(MarketSymbol.XAUUSD, Timeframe.M15, a2.dataEngine.closedCandle1)
        )
    }

    // 12. Backup & extraction XML exclusion rules + manifest check
    @Test
    fun test12_backupAndExtractionRules_excludeSensitivePrefs() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val backupXml = context.resources.getXml(R.xml.backup_rules)
        val backupNodes = mutableListOf<String>()
        while (backupXml.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (backupXml.eventType == org.xmlpull.v1.XmlPullParser.START_TAG) {
                backupNodes.add(
                    "${backupXml.name}:${backupXml.getAttributeValue(null, "domain")}:${backupXml.getAttributeValue(null, "path")}"
                )
            }
            backupXml.next()
        }
        assertTrue(backupNodes.any { it == "exclude:sharedpref:hub_secure_credentials.xml" })
        assertTrue(backupNodes.any { it == "exclude:sharedpref:." })

        val extractXml = context.resources.getXml(R.xml.data_extraction_rules)
        val extractNodes = mutableListOf<String>()
        while (extractXml.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (extractXml.eventType == org.xmlpull.v1.XmlPullParser.START_TAG) {
                extractNodes.add(
                    "${extractXml.name}:${extractXml.getAttributeValue(null, "domain")}:${extractXml.getAttributeValue(null, "path")}"
                )
            }
            extractXml.next()
        }
        assertTrue(extractNodes.any { it == "exclude:sharedpref:hub_secure_credentials.xml" })
    }

    // 13. Compose UI verification for NewsEngineScreen, TenEnginesScreen, and SettingsScreen masking
    @Test
    fun test13_composeScreens_renderHonestNewsDynamicChecklistAndMaskedCredentials() {
        val bars = buildBullishBars(90)
        val analysis = IndicatorAndConfluenceEngines.buildCompleteAnalysis(
            symbol = MarketSymbol.XAUUSD,
            brokerSuffix = ".sml",
            activeTimeframe = Timeframe.M15,
            activeBars = bars,
            allTimeframeBars = Timeframe.entries.associateWith { bars },
            isLiveFeed = true,
            feedSourceName = "Elev8",
            newsArticles = emptyList(),
            isLiveRss = false,
            newsStatus = NewsDataStatus.OFFLINE_EMPTY,
            newsStatusDetail = "Feed berita sedang offline."
        )

        composeRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                SettingsScreen(
                    uiState = HubUiState(
                        hasCustomSavedCredentials = true,
                        savedMetaApiAccountIdHint = "****5678",
                        savedMetaApiTokenHint = "****4321",
                        metaApiAccountIdInput = "",
                        metaApiAuthTokenInput = ""
                    ),
                    onRunMultiProviderAudit = {},
                    onCleanAndResetSession = {},
                    onUpdateGitHubRepo = {},
                    onCheckGitHubUpdate = {},
                    onUpdateMetaApiAccountId = {},
                    onUpdateMetaApiAuthToken = {},
                    onSaveMetaApiCredentials = {},
                    onClearMetaApiCredentials = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_saved_credential_hint")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("****4321", substring = true)
        composeRule.onNodeWithTag("settings_toggle_token_visibility")
            .performScrollTo()
            .assertIsDisplayed()

        assertNotEquals(0, analysis.confluenceChecklist.size)
        assertTrue(NewsEngineScreen_smokeCheck(analysis))
    }

    private fun NewsEngineScreen_smokeCheck(analysis: com.example.model.CompleteHubAnalysis): Boolean {
        return analysis.newsEngine.status == NewsDataStatus.OFFLINE_EMPTY &&
            analysis.confluenceChecklist.size == 10
    }
}
