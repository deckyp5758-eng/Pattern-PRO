package com.example

import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.HubRepository
import com.example.data.local.HubDatabase
import com.example.data.local.SavedSignalSnapshotEntity
import com.example.data.local.UserPreferencesManager
import com.example.domain.IndicatorAndConfluenceEngines
import com.example.model.AnalyticalVerdict
import com.example.model.GlobalMarketSession
import com.example.model.MarketSymbol
import com.example.model.Mt5RateBar
import com.example.model.NewsDataStatus
import com.example.model.OnlineDataProvider
import com.example.model.SignalOutcomeStatus
import com.example.model.Timeframe
import com.example.ui.HubUiState
import com.example.ui.HubViewModel
import com.example.ui.screens.HudTerminalScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Priority1And2FeatureTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun buildTrendingBars(
        count: Int = 120,
        bullish: Boolean = true,
        startEpochSec: Long = 1727000000L,
        stepSec: Long = 900L,
        basePrice: Double = 2650.0
    ): List<Mt5RateBar> {
        val chron = (0 until count).map { idx ->
            val slope = if (bullish) 0.70 else -0.70
            val drift = basePrice + idx * slope + sin(idx * 0.25) * 2.2
            val open = if (bullish) drift - 0.6 else drift + 0.6
            val close = if (bullish) drift + 0.9 else drift - 0.9
            Mt5RateBar(
                barIndex = 0,
                timeEpochSec = startEpochSec + idx * stepSec,
                open = open,
                high = maxOf(open, close) + 1.0,
                low = minOf(open, close) - 1.0,
                close = close,
                tickVolume = 1200L + (idx % 10) * 60L,
                realVolume = 12000L,
                spread = 15
            )
        }
        return chron.asReversed().mapIndexed { i, bar -> bar.copy(barIndex = i) }
    }

    private fun buildChoppyAlternatingBars(count: Int = 90): List<Mt5RateBar> {
        val startEpochSec = 1727000000L
        val chron = (0 until count).map { idx ->
            val isBull = idx % 2 == 0
            val open = if (isBull) 2650.0 else 2650.4
            val close = if (isBull) 2650.4 else 2650.0
            Mt5RateBar(
                barIndex = 0,
                timeEpochSec = startEpochSec + idx * 900L,
                open = open,
                high = 2652.0,
                low = 2648.0,
                close = close,
                tickVolume = 900L,
                realVolume = 9000L,
                spread = 20
            )
        }
        return chron.asReversed().mapIndexed { i, bar -> bar.copy(barIndex = i) }
    }

    @Test
    fun test01_calibrationProfiles_differentiateXauAndEurAndDetectChoppyMarket() {
        val xauProfile = IndicatorAndConfluenceEngines.getInstrumentCalibrationProfile(
            MarketSymbol.XAUUSD,
            GlobalMarketSession.ASIA_PACIFIC
        )
        val eurProfile = IndicatorAndConfluenceEngines.getInstrumentCalibrationProfile(
            MarketSymbol.EURUSD,
            GlobalMarketSession.LONDON
        )
        assertTrue(xauProfile.atrStopMultiplier > eurProfile.atrStopMultiplier)
        assertTrue(xauProfile.sessionGuidance.contains("Asia"))
        assertFalse(eurProfile.profileName == xauProfile.profileName)

        val choppyBars = buildChoppyAlternatingBars(90)
        val analysis = IndicatorAndConfluenceEngines.buildCompleteAnalysis(
            symbol = MarketSymbol.XAUUSD,
            brokerSuffix = ".sml",
            activeTimeframe = Timeframe.M15,
            activeBars = choppyBars,
            allTimeframeBars = Timeframe.entries.associateWith { choppyBars },
            isLiveFeed = true,
            feedSourceName = "Test",
            newsArticles = emptyList(),
            isLiveRss = false
        )
        val cal = analysis.calibrationReport
        assertNotNull(cal)
        assertTrue(cal!!.isChoppyMarketDetected)
        assertFalse(cal.qualityGatePassed)
        assertFalse(
            analysis.verdict == AnalyticalVerdict.MODERATE_BULLISH_BIAS ||
                analysis.verdict == AnalyticalVerdict.MODERATE_BEARISH_BIAS
        )
    }

    @Test
    fun test02_backtest_isNonRepaintAndGeneratesObjectiveMetrics() {
        val bars = buildTrendingBars(count = 140, bullish = true)
        val mutatedBar0 = bars.toMutableList().apply {
            this[0] = this[0].copy(
                high = this[0].high + 200.0,
                low = this[0].low - 200.0,
                close = this[0].close + 150.0
            )
        }

        val bt1 = IndicatorAndConfluenceEngines.runHistoricalBacktest(bars, MarketSymbol.XAUUSD)
        val bt2 = IndicatorAndConfluenceEngines.runHistoricalBacktest(mutatedBar0, MarketSymbol.XAUUSD)

        assertEquals(139, bt1.sampleBarsCount)
        assertEquals(bt1, bt2)
        assertTrue(bt1.honestNote.isNotBlank())
    }

    @Test
    fun test03_outcomeEvaluator_enforcesConservativeSlPriorityAndIgnoresBar0() {
        val entryEpoch = 1727000000L
        // barIndex 0 touches TP, barIndex 1 did not hit SL or TP -> must stay null (PENDING)
        val bar0OnlyBars = listOf(
            Mt5RateBar(0, entryEpoch + 1800L, 2650.0, 2700.0, 2648.0, 2690.0, 100, 1000, 15),
            Mt5RateBar(1, entryEpoch + 900L, 2650.0, 2655.0, 2648.0, 2653.0, 100, 1000, 15),
            Mt5RateBar(2, entryEpoch, 2649.0, 2651.0, 2648.0, 2650.0, 100, 1000, 15)
        )
        assertNull(
            IndicatorAndConfluenceEngines.evaluatePendingSignalOutcome(
                directionBias = "BUY",
                verdictLabel = "BUY",
                closedBarEpochSec = entryEpoch,
                stopReferencePrice = 2640.0,
                targetReferencePrice = 2670.0,
                bars = bar0OnlyBars
            )
        )

        // Same closed bar touches BOTH SL (2640) and TP (2670) -> conservative LOSS_SL wins
        val sameBarSlTp = listOf(
            Mt5RateBar(0, entryEpoch + 1800L, 2650.0, 2652.0, 2649.0, 2651.0, 100, 1000, 15),
            Mt5RateBar(1, entryEpoch + 900L, 2650.0, 2675.0, 2638.0, 2665.0, 100, 1000, 15),
            Mt5RateBar(2, entryEpoch, 2649.0, 2651.0, 2648.0, 2650.0, 100, 1000, 15)
        )
        val slResult = IndicatorAndConfluenceEngines.evaluatePendingSignalOutcome(
            directionBias = "BUY",
            verdictLabel = "BUY",
            closedBarEpochSec = entryEpoch,
            stopReferencePrice = 2640.0,
            targetReferencePrice = 2670.0,
            bars = sameBarSlTp
        )
        assertNotNull(slResult)
        assertEquals(SignalOutcomeStatus.LOSS_SL, slResult!!.first)

        // Clean WIN_TP on closed bar 1
        val cleanTp = listOf(
            Mt5RateBar(0, entryEpoch + 1800L, 2670.0, 2672.0, 2668.0, 2671.0, 100, 1000, 15),
            Mt5RateBar(1, entryEpoch + 900L, 2650.0, 2674.0, 2645.0, 2672.0, 100, 1000, 15),
            Mt5RateBar(2, entryEpoch, 2649.0, 2651.0, 2648.0, 2650.0, 100, 1000, 15)
        )
        val winResult = IndicatorAndConfluenceEngines.evaluatePendingSignalOutcome(
            directionBias = "BUY",
            verdictLabel = "BUY",
            closedBarEpochSec = entryEpoch,
            stopReferencePrice = 2640.0,
            targetReferencePrice = 2670.0,
            bars = cleanTp
        )
        assertNotNull(winResult)
        assertEquals(SignalOutcomeStatus.WIN_TP, winResult!!.first)
    }

    @Test
    fun test04_roomMigration1To2_preservesLegacyRowsAndAppliesDefaults() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_1_2_test_${System.nanoTime()}.db"
        val factory = FrameworkSQLiteOpenHelperFactory()
        val configV1 = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `closed_candle_signal_logs` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `symbolWithSuffix` TEXT NOT NULL,
                            `timeframeLabel` TEXT NOT NULL,
                            `closedBarEpochSec` INTEGER NOT NULL,
                            `closePrice` REAL NOT NULL,
                            `verdictLabel` TEXT NOT NULL,
                            `patternDetected` TEXT NOT NULL,
                            `structureLabel` TEXT NOT NULL,
                            `trendLabel` TEXT NOT NULL,
                            `rsi14` REAL NOT NULL,
                            `adx14` REAL NOT NULL,
                            `atr14` REAL NOT NULL,
                            `stopReferencePrice` REAL NOT NULL,
                            `targetReferencePrice` REAL NOT NULL,
                            `confluenceScore` INTEGER NOT NULL,
                            `savedTimestampMs` INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        INSERT INTO closed_candle_signal_logs (
                            symbolWithSuffix, timeframeLabel, closedBarEpochSec, closePrice,
                            verdictLabel, patternDetected, structureLabel, trendLabel,
                            rsi14, adx14, atr14, stopReferencePrice, targetReferencePrice,
                            confluenceScore, savedTimestampMs
                        ) VALUES (
                            'XAUUSD.sml', 'M15', 1727000000, 2655.5,
                            'KONFLUENSI NAIK KUAT (BULLISH) [CANDLE 1]', 'Bullish Engulfing',
                            'Uptrend HH/HL', 'Strong Bullish', 61.2, 28.4, 4.5,
                            2648.0, 2666.75, 80, 1727000100000
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        val helperV1 = factory.create(configV1)
        try {
            val db = helperV1.writableDatabase
            HubDatabase.MIGRATION_1_2.migrate(db)

            db.query(
                "SELECT symbolWithSuffix, directionBias, outcomeStatus, outcomePrice, outcomeEvaluatedAtMs, timeframeMinutes FROM closed_candle_signal_logs"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("XAUUSD.sml", cursor.getString(0))
                assertEquals("NEUTRAL", cursor.getString(1))
                assertEquals("PENDING", cursor.getString(2))
                assertEquals(0.0, cursor.getDouble(3), 0.001)
                assertEquals(0L, cursor.getLong(4))
                assertEquals(15, cursor.getInt(5))
            }
        } finally {
            helperV1.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun test05_viewModel_persistsSimpleModeRiskPresetsAndEvaluatesOutcomes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, HubDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val prefs = UserPreferencesManager.getInstance(context)
            prefs.saveSimpleCockpitMode(true)
            prefs.savePreferredAccountBalance(1000.0)
            prefs.savePreferredRiskPercent(1.0)

            val repo = HubRepository(db.hubDao(), prefs)
            val vm1 = HubViewModel(repo)
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            assertTrue(vm1.uiState.value.isSimpleCockpitMode)
            vm1.toggleSimpleCockpitMode(false)
            vm1.updateRiskPreferences(2500.0, 2.0)

            // Re-create ViewModel to verify persistence
            val vm2 = HubViewModel(repo)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertFalse(vm2.uiState.value.isSimpleCockpitMode)
            assertEquals(2500.0, vm2.uiState.value.accountBalanceDollars, 0.01)
            assertEquals(2.0, vm2.uiState.value.riskPercentage, 0.01)

            // Outcome evaluator updates PENDING saved log in Room
            val entryEpoch = 1727000000L
            db.hubDao().insertSignalLog(
                SavedSignalSnapshotEntity(
                    id = 99,
                    symbolWithSuffix = "XAUUSD.sml",
                    timeframeLabel = "M15",
                    closedBarEpochSec = entryEpoch,
                    closePrice = 2650.0,
                    verdictLabel = "KONFLUENSI NAIK KUAT (BULLISH) [CANDLE 1]",
                    patternDetected = "Bullish Engulfing",
                    structureLabel = "Uptrend",
                    trendLabel = "Bullish",
                    rsi14 = 60.0,
                    adx14 = 30.0,
                    atr14 = 4.0,
                    stopReferencePrice = 2640.0,
                    targetReferencePrice = 2670.0,
                    confluenceScore = 80,
                    directionBias = "BUY",
                    outcomeStatus = SignalOutcomeStatus.PENDING.code,
                    timeframeMinutes = 15
                )
            )
            // Collect once to ensure savedSignalLogs StateFlow receives the row
            val initialLogs = repo.savedSignalLogs.first()
            assertEquals(1, initialLogs.size)

            val bars = listOf(
                Mt5RateBar(0, entryEpoch + 1800L, 2671.0, 2673.0, 2669.0, 2672.0, 100, 1000, 15),
                Mt5RateBar(1, entryEpoch + 900L, 2650.0, 2675.0, 2646.0, 2671.0, 100, 1000, 15),
                Mt5RateBar(2, entryEpoch, 2649.0, 2651.0, 2648.0, 2650.0, 100, 1000, 15)
            )
            val evaluated = IndicatorAndConfluenceEngines.evaluatePendingSignalOutcome(
                directionBias = initialLogs.first().directionBias,
                verdictLabel = initialLogs.first().verdictLabel,
                closedBarEpochSec = initialLogs.first().closedBarEpochSec,
                stopReferencePrice = initialLogs.first().stopReferencePrice,
                targetReferencePrice = initialLogs.first().targetReferencePrice,
                bars = bars
            )!!
            repo.updateSignalOutcome(initialLogs.first().id, evaluated.first, evaluated.second, evaluated.third)
            val updatedLogs = repo.savedSignalLogs.first()
            assertEquals(SignalOutcomeStatus.WIN_TP.code, updatedLogs.first().outcomeStatus)
        } finally {
            db.close()
        }
    }

    @Test
    fun test06_composeHudTerminal_simpleModeVsProModeAndStandbySuppression() {
        val bullBars = buildTrendingBars(120, bullish = true)
        val actionableAnalysis = IndicatorAndConfluenceEngines.buildCompleteAnalysis(
            symbol = MarketSymbol.XAUUSD,
            brokerSuffix = ".sml",
            activeTimeframe = Timeframe.M15,
            activeBars = bullBars,
            allTimeframeBars = Timeframe.entries.associateWith { bullBars },
            isLiveFeed = true,
            feedSourceName = "Test",
            newsArticles = emptyList(),
            isLiveRss = false,
            newsStatus = NewsDataStatus.LIVE_FRESH
        ).copy(verdict = AnalyticalVerdict.STRONG_BULLISH_CONFLUENCE)

        var lastRiskChoice = 1.0
        composeRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                HudTerminalScreen(
                    uiState = HubUiState(
                        isSimpleCockpitMode = true,
                        selectedProvider = OnlineDataProvider.ELEV8_METAAPI,
                        accountBalanceDollars = 1000.0,
                        riskPercentage = 1.0
                    ),
                    analysis = actionableAnalysis,
                    savedLogs = emptyList(),
                    onSelectProvider = {},
                    onSelectSymbol = {},
                    onSelectTimeframe = {},
                    onInspectBar = {},
                    onToggleEma = {},
                    onToggleSr = {},
                    onToggleSwings = {},
                    onToggleLiveTick = {},
                    onRunMultiProviderAudit = {},
                    onCleanAndResetSession = {},
                    onRefreshData = {},
                    onSaveSnapshot = {},
                    onDeleteSnapshot = {},
                    onClearAllSnapshots = {},
                    onToggleSimpleMode = {},
                    onUpdateRiskPreferences = { _, r -> lastRiskChoice = r }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hud_terminal_list").performScrollToNode(hasTestTag("cockpit_mode_toggle"))
        composeRule.onNodeWithTag("cockpit_mode_toggle").assertIsDisplayed()
        composeRule.onNodeWithTag("hud_terminal_list").performScrollToNode(hasTestTag("simple_decision_card"))
        composeRule.onNodeWithTag("simple_decision_card").assertIsDisplayed()
        composeRule.onNodeWithTag("simple_risk_chip_2").performScrollTo().performClick()
        assertEquals(2.0, lastRiskChoice, 0.001)
        composeRule.onNodeWithTag("simple_entry_value").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("simple_copy_order_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("hud_terminal_list").performScrollToNode(hasTestTag("backtest_summary_card"))
        composeRule.onNodeWithTag("backtest_summary_card").assertIsDisplayed()
    }

    @Test
    fun test07_composeHudTerminal_simpleModeStandbySuppressesOrderCopy() {
        val bars = buildChoppyAlternatingBars(90)
        val standbyAnalysis = IndicatorAndConfluenceEngines.buildCompleteAnalysis(
            symbol = MarketSymbol.EURUSD,
            brokerSuffix = "",
            activeTimeframe = Timeframe.M15,
            activeBars = bars,
            allTimeframeBars = Timeframe.entries.associateWith { bars },
            isLiveFeed = true,
            feedSourceName = "Test",
            newsArticles = emptyList(),
            isLiveRss = false
        ).copy(verdict = AnalyticalVerdict.NEUTRAL_CONSOLIDATION)

        composeRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                HudTerminalScreen(
                    uiState = HubUiState(isSimpleCockpitMode = true),
                    analysis = standbyAnalysis,
                    savedLogs = emptyList(),
                    onSelectProvider = {},
                    onSelectSymbol = {},
                    onSelectTimeframe = {},
                    onInspectBar = {},
                    onToggleEma = {},
                    onToggleSr = {},
                    onToggleSwings = {},
                    onToggleLiveTick = {},
                    onRunMultiProviderAudit = {},
                    onCleanAndResetSession = {},
                    onRefreshData = {},
                    onSaveSnapshot = {},
                    onDeleteSnapshot = {},
                    onClearAllSnapshots = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hud_terminal_list").performScrollToNode(hasTestTag("simple_decision_card"))
        composeRule.onNodeWithTag("simple_standby_notice").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("simple_copy_order_button").assertDoesNotExist()
    }
}
