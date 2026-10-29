package com.example.model

enum class OnlineDataProvider(
    val id: String,
    val displayName: String,
    val shortLabel: String,
    val baseHost: String,
    val isOfflineTestMode: Boolean = false
) {
    ELEV8_METAAPI(
        id = "elev8_metaapi",
        displayName = "Elev8 MT5 Cloud (MetaApi Official)",
        shortLabel = "Elev8 MT5",
        baseHost = "mt-market-data-client-api-v1.agiliumtrade.ai"
    ),
    BINANCE_VISION(
        id = "binance_vision",
        displayName = "Binance Vision Spot API (PAXG/EUR)",
        shortLabel = "Binance API",
        baseHost = "data-api.binance.vision"
    ),
    YAHOO_FINANCE(
        id = "yahoo_finance",
        displayName = "Yahoo Finance Global (GC=F / EURUSD=X)",
        shortLabel = "Yahoo Finance",
        baseHost = "query1.finance.yahoo.com"
    ),
    KRAKEN_SPOT(
        id = "kraken_spot",
        displayName = "Kraken Institutional OHLC API",
        shortLabel = "Kraken API",
        baseHost = "api.kraken.com"
    ),
    BITFINEX_PUBLIC(
        id = "bitfinex_public",
        displayName = "Bitfinex Institutional Public API (XAUT/EUR)",
        shortLabel = "Bitfinex API",
        baseHost = "api-pub.bitfinex.com"
    ),
    OKX_GLOBAL(
        id = "okx_global",
        displayName = "OKX Global Market v5 API (PAXG/EUR)",
        shortLabel = "OKX API",
        baseHost = "www.okx.com"
    ),
    KUCOIN_SPOT(
        id = "kucoin_spot",
        displayName = "KuCoin Global Spot API (PAXG/EUR)",
        shortLabel = "KuCoin API",
        baseHost = "api.kucoin.com"
    ),
    OFFLINE_SIMULATION_TEST(
        id = "offline_test",
        displayName = "Mode Uji Provider Offline (Endpoint Mati)",
        shortLabel = "Uji Offline",
        baseHost = "offline-provider.invalid",
        isOfflineTestMode = true
    );

    val isBrokerNativeFeed: Boolean
        get() = this == ELEV8_METAAPI

    val feedQualityLabel: String
        get() = if (this == ELEV8_METAAPI) {
            "Feed Broker MT5 (Real/Demo)"
        } else {
            "Feed Proxy Pasar (Spot/Futures)"
        }

    companion object {
        val realProviders = listOf(
            ELEV8_METAAPI,
            BINANCE_VISION,
            YAHOO_FINANCE,
            KRAKEN_SPOT,
            BITFINEX_PUBLIC,
            OKX_GLOBAL,
            KUCOIN_SPOT
        )
    }
}

data class LiveVerificationAudit(
    val check1EndpointAccessible: Boolean = false,
    val check1Detail: String = "Belum diuji",
    val check2RealServerData: Boolean = false,
    val check2Detail: String = "Belum diuji",
    val check3TimestampUpdated: Boolean = false,
    val check3Detail: String = "Menunggu pembaruan waktu server...",
    val check4ChangesAcrossProviders: Boolean = false,
    val check4Detail: String = "Menunggu perbandingan antar provider...",
    val check5ErrorDisplayedProperly: Boolean = true,
    val check5Detail: String = "Penangkap error jaringan & HTTP siap",
    val check6HonestWhenOffline: Boolean = true,
    val check6Detail: String = "Tanpa data palsu/simulasi lokal (0% Mock)",
    val lastHttpStatusCode: Int = 0,
    val lastEndpointUrl: String = "-",
    val lastServerFetchEpochMs: Long = 0L,
    val previousServerFetchEpochMs: Long = 0L,
    val lastErrorMessage: String? = null
) {
    val isAllSixVerifiedAndLive: Boolean
        get() = check1EndpointAccessible &&
            check2RealServerData &&
            check3TimestampUpdated &&
            check4ChangesAcrossProviders &&
            check5ErrorDisplayedProperly &&
            check6HonestWhenOffline &&
            lastErrorMessage == null
}

enum class MarketSymbol(
    val code: String,
    val mt5DefaultSuffix: String,
    val displayName: String,
    val digits: Int,
    val pointSize: Double,
    val defaultSpreadPoints: Int
) {
    XAUUSD(
        code = "XAUUSD",
        mt5DefaultSuffix = "XAUUSD.sml",
        displayName = "Emas Spot / Dolar AS",
        digits = 2,
        pointSize = 0.01,
        defaultSpreadPoints = 18
    ),
    EURUSD(
        code = "EURUSD",
        mt5DefaultSuffix = "EURUSD.sml",
        displayName = "Euro / Dolar AS",
        digits = 5,
        pointSize = 0.00001,
        defaultSpreadPoints = 8
    );

    fun withSuffix(suffix: String): String {
        val clean = suffix.trim()
        return if (clean.isEmpty()) code else "$code$clean"
    }
}

enum class Timeframe(
    val label: String,
    val mt5EnumName: String,
    val binanceInterval: String,
    val krakenInterval: Int,
    val yahooInterval: String,
    val yahooRange: String,
    val minutes: Int,
    val priorityRank: Int
) {
    H1("H1", "PERIOD_H1", "1h", 60, "1h", "1mo", 60, 1),
    M30("M30", "PERIOD_M30", "30m", 30, "30m", "1mo", 30, 2),
    M15("M15", "PERIOD_M15", "15m", 15, "15m", "5d", 15, 3),
    M5("M5", "PERIOD_M5", "5m", 5, "5m", "5d", 5, 4),
    M1("M1", "PERIOD_M1", "1m", 1, "1m", "1d", 1, 5);

    companion object {
        val orderedByChart = listOf(M1, M5, M15, M30, H1)
        val orderedByPriority = listOf(H1, M30, M15, M5, M1)
    }
}

/**
 * Struktur data harga MT5 (MqlRates) yang diisi murni dari respons server online.
 * barIndex == 0 -> Candle sedang berjalan/forming dari server (HANYA untuk pemantauan)
 * barIndex == 1 -> Candle terakhir yang SUDAH CLOSE dari server (sinyal final NON-REPAINT)
 * barIndex >= 2 -> Candle historis yang sudah close dari server
 */
data class Mt5RateBar(
    val barIndex: Int,
    val timeEpochSec: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val tickVolume: Long,
    val realVolume: Long,
    val spread: Int
) {
    val isClosed: Boolean get() = barIndex >= 1
    val isBullish: Boolean get() = close > open
    val isBearish: Boolean get() = close < open
    val bodySize: Double get() = kotlin.math.abs(close - open)
    val totalRange: Double get() = (high - low).coerceAtLeast(1e-9)
    val upperWick: Double get() = high - maxOf(open, close)
    val lowerWick: Double get() = minOf(open, close) - low
    val bodyToRangeRatio: Double get() = (bodySize / totalRange).coerceIn(0.0, 1.0)
    val wickToBodyRatio: Double
        get() = if (bodySize > 1e-9) (upperWick + lowerWick) / bodySize else 99.9
}

// 2. DATA ENGINE
data class DataEngineReport(
    val symbolWithSuffix: String,
    val timeframe: Timeframe,
    val totalBarsLoaded: Int,
    val formingCandle0: Mt5RateBar,
    val closedCandle1: Mt5RateBar,
    val previousClosedCandle2: Mt5RateBar,
    val isLiveFeedConnected: Boolean,
    val feedSourceLabel: String,
    val lastSyncTimeMs: Long
)

// 3. CANDLE ENGINE
enum class CandleAnatomyType(val label: String) {
    STRONG_BULLISH("Bullish Kuat (Strong Bullish)"),
    BULLISH("Candle Naik (Bullish)"),
    DOJI("Doji (Pasar Ragu/Seimbang)"),
    BEARISH("Candle Turun (Bearish)"),
    STRONG_BEARISH("Bearish Kuat (Strong Bearish)")
}

enum class PatternBias(val label: String) {
    BULLISH("NAIK (BULLISH)"),
    BEARISH("TURUN (BEARISH)"),
    NEUTRAL("NETRAL / TRANSISI")
}

enum class CandlestickPatternType(
    val id: Int,
    val displayName: String,
    val candleCount: Int,
    val defaultBias: PatternBias
) {
    DOJI(1, "Doji (Keseimbangan)", 1, PatternBias.NEUTRAL),
    HAMMER(2, "Hammer (Palu Pantulan Naik)", 1, PatternBias.BULLISH),
    INVERTED_HAMMER(3, "Inverted Hammer (Palu Terbalik)", 1, PatternBias.BULLISH),
    SHOOTING_STAR(4, "Shooting Star (Tolakan Atas)", 1, PatternBias.BEARISH),
    HANGING_MAN(5, "Hanging Man (Peringatan Puncak)", 1, PatternBias.BEARISH),
    BULLISH_ENGULFING(6, "Bullish Engulfing (Telan Naik)", 2, PatternBias.BULLISH),
    BEARISH_ENGULFING(7, "Bearish Engulfing (Telan Turun)", 2, PatternBias.BEARISH),
    BULLISH_HARAMI(8, "Bullish Harami (Ibu-Anak Naik)", 2, PatternBias.BULLISH),
    BEARISH_HARAMI(9, "Bearish Harami (Ibu-Anak Turun)", 2, PatternBias.BEARISH),
    TWEEZER_TOP(10, "Tweezer Top (Atap Kembar Turun)", 2, PatternBias.BEARISH),
    TWEEZER_BOTTOM(11, "Tweezer Bottom (Dasar Kembar Naik)", 2, PatternBias.BULLISH),
    MORNING_STAR(12, "Morning Star (Bintang Fajar Naik)", 3, PatternBias.BULLISH),
    EVENING_STAR(13, "Evening Star (Bintang Senja Turun)", 3, PatternBias.BEARISH),
    THREE_WHITE_SOLDIERS(14, "Three White Soldiers (3 Prajurit Naik)", 3, PatternBias.BULLISH),
    THREE_BLACK_CROWS(15, "Three Black Crows (3 Gagak Turun)", 3, PatternBias.BEARISH)
}

data class DetectedPattern(
    val pattern: CandlestickPatternType,
    val closedBarIndex: Int,
    val barTimeEpochSec: Long,
    val priceAtDetection: Double,
    val rationale: String
)

data class CandleEngineReport(
    val closedBar1Metrics: Mt5RateBar,
    val anatomyType: CandleAnatomyType,
    val bodyPoints: Double,
    val upperWickPoints: Double,
    val lowerWickPoints: Double,
    val rangePoints: Double,
    val bodyToRangePct: Double,
    val wickToBodyRatio: Double,
    val activeClosedPatterns: List<DetectedPattern>,
    val recentHistoricalPatterns: List<DetectedPattern>
)

// 4. MARKET STRUCTURE ENGINE
enum class SwingLabel(val shortCode: String, val fullName: String, val isHigh: Boolean) {
    HH("HH", "Higher High (Puncak Lebih Tinggi)", true),
    LH("LH", "Lower High (Puncak Lebih Rendah)", true),
    SH("SH", "Swing High (Titik Puncak)", true),
    HL("HL", "Higher Low (Lembah Lebih Tinggi)", false),
    LL("LL", "Lower Low (Lembah Lebih Rendah)", false),
    SL("SL", "Swing Low (Titik Lembah)", false)
}

data class SwingPoint(
    val closedBarIndex: Int,
    val timeEpochSec: Long,
    val price: Double,
    val label: SwingLabel
)

enum class MarketStructureClass(val label: String) {
    BULLISH_STRUCTURE("Struktur Bullish (HH + HL)"),
    BEARISH_STRUCTURE("Struktur Bearish (LH + LL)"),
    RANGE("Sideways / Konsolidasi (Range)"),
    TRANSITION("Transisi Struktur Harga")
}

enum class StructureBreakEvent(val label: String, val bias: PatternBias) {
    BOS_BULLISH("BOS Bullish (Tembus Struktur Naik)", PatternBias.BULLISH),
    BOS_BEARISH("BOS Bearish (Tembus Struktur Turun)", PatternBias.BEARISH),
    CHOCH_BULLISH("CHOCH Bullish (Perubahan Karakter Naik)", PatternBias.BULLISH),
    CHOCH_BEARISH("CHOCH Bearish (Perubahan Karakter Turun)", PatternBias.BEARISH),
    NONE("Belum Ada Tembusan di Candle [1]", PatternBias.NEUTRAL)
}

data class MarketStructureReport(
    val classification: MarketStructureClass,
    val latestBreakEvent: StructureBreakEvent,
    val breakLevelPrice: Double?,
    val lastSwingHigh: SwingPoint?,
    val lastSwingLow: SwingPoint?,
    val confirmedSwings: List<SwingPoint>,
    val structureSummary: String
)

// 5. TREND ENGINE
enum class TrendClassification(val label: String, val scoreWeight: Int) {
    STRONG_BULLISH("Tren Naik Kuat (Strong Bullish)", 2),
    BULLISH("Tren Naik (Bullish)", 1),
    NEUTRAL("Netral / EMA Menyempit", 0),
    BEARISH("Tren Turun (Bearish)", -1),
    STRONG_BEARISH("Tren Turun Kuat (Strong Bearish)", -2)
}

data class TrendEngineReport(
    val ema20: Double,
    val ema50: Double,
    val ema200: Double,
    val ema20Series: List<Double>,
    val ema50Series: List<Double>,
    val ema200Series: List<Double>,
    val classification: TrendClassification,
    val isPerfectBullishStack: Boolean,
    val isPerfectBearishStack: Boolean,
    val priceToEma200DistancePoints: Double,
    val summary: String
)

// 6. MOMENTUM ENGINE
enum class RsiRegime(val label: String) {
    BULLISH("Momentum Bullish (RSI > 55)"),
    NEUTRAL("Momentum Netral (RSI 45 – 55)"),
    BEARISH("Momentum Bearish (RSI < 45)")
}

enum class MacdState(val label: String) {
    BULLISH_CROSS("Potongan Naik (Bullish Cross) [Candle 1]"),
    BEARISH_CROSS("Potongan Turun (Bearish Cross) [Candle 1]"),
    BULLISH_STRENGTHENING("Histogram Naik Menguat"),
    BULLISH_WEAKENING("Histogram Naik Melemah"),
    BEARISH_STRENGTHENING("Histogram Turun Menguat"),
    BEARISH_WEAKENING("Histogram Turun Melemah")
}

data class MomentumEngineReport(
    val rsi14: Double,
    val rsiRegime: RsiRegime,
    val adx14: Double,
    val plusDi14: Double,
    val minusDi14: Double,
    val isTrendStrengthConfirmed: Boolean,
    val macdMain: Double,
    val macdSignal: Double,
    val macdHistogram: Double,
    val prevMacdHistogram: Double,
    val macdState: MacdState,
    val summary: String
)

// 7. VOLATILITY ENGINE
enum class VolatilityState(val label: String) {
    LOW("Volatilitas Rendah (Sepi)"),
    NORMAL("Volatilitas Normal"),
    HIGH("Volatilitas Tinggi (Aktif)"),
    EXTREME("Volatilitas Ekstrem (Rawan Lompatan)")
}

data class VolatilityEngineReport(
    val atr14: Double,
    val atrPoints: Double,
    val atrPercentageOfPrice: Double,
    val state: VolatilityState,
    val analyticalStopDistance: Double,
    val longStopReferencePrice: Double,
    val shortStopReferencePrice: Double,
    val longTarget1RPrice: Double,
    val longTarget2RPrice: Double,
    val shortTarget1RPrice: Double,
    val shortTarget2RPrice: Double,
    val warningMessage: String?
)

// 8. SUPPORT & RESISTANCE ENGINE
enum class SrInteractionType(val label: String) {
    SUPPORT_HOLD("Bertahan di Area Support"),
    RESISTANCE_HOLD("Tertahan di Area Resistance"),
    BULLISH_REJECTION("Pantulan Naik dari Support [Candle 1]"),
    BEARISH_REJECTION("Tolakan Turun dari Resistance [Candle 1]"),
    BULLISH_BREAKOUT("Breakout Tembus Resistance [Candle 1]"),
    BEARISH_BREAKOUT("Breakdown Tembus Support [Candle 1]"),
    FAILED_BREAKOUT("Failed Breakout (Tembusan Palsu/Trap)")
}

data class SrZone(
    val centerPrice: Double,
    val lowerBound: Double,
    val upperBound: Double,
    val isSupport: Boolean,
    val touchCount: Int,
    val lastTouchBarsAgo: Int,
    val rejectionStrengthScore: Double,
    val distanceInAtr: Double,
    val interaction: SrInteractionType,
    val priorityScore: Double
)

data class SrEngineReport(
    val zones: List<SrZone>,
    val nearestSupport: SrZone?,
    val nearestResistance: SrZone?,
    val closedBar1Interaction: SrInteractionType,
    val summary: String
)

// 9. VOLUME ENGINE
enum class VolumeClassification(val label: String) {
    LOW_VOLUME("Volume Rendah (< 0.75x Rata-rata 20)"),
    NORMAL_VOLUME("Volume Normal (0.75x – 1.2x SMA20)"),
    HIGH_VOLUME("Volume Tinggi (1.2x – 1.5x SMA20)"),
    VOLUME_EXPANSION("Ekspansi Volume (> 1.5x SMA20)")
}

data class VolumeEngineReport(
    val closedBar1Volume: Long,
    val formingBar0Volume: Long,
    val smaVolume20: Double,
    val volumeToSmaRatio: Double,
    val classification: VolumeClassification,
    val isVolumeExpansion: Boolean,
    val confluenceNote: String
)

// 10. MULTI-TIMEFRAME ENGINE
data class TimeframeMatrixRow(
    val timeframe: Timeframe,
    val trend: TrendClassification,
    val structure: MarketStructureClass,
    val structureEvent: StructureBreakEvent,
    val rsi14: Double,
    val adx14: Double,
    val closedPatternName: String,
    val srStatus: String,
    val bias: PatternBias
)

data class MtfEngineReport(
    val rows: List<TimeframeMatrixRow>,
    val htfBias: PatternBias,
    val ltfState: String,
    val isPullbackInHtfTrend: Boolean,
    val executiveConclusion: String
)

// 11. NEWS ENGINE (KOMPONEN WAJIB)
enum class NewsImpactLevel(val label: String) {
    HIGH("DAMPAK TINGGI"),
    MEDIUM("DAMPAK SEDANG"),
    LOW("DAMPAK RENDAH")
}

enum class MacroSentiment(val label: String) {
    HAWKISH_USD_BEARISH_GOLD("USD Menguat (Hawkish) • Tekanan Turun XAU/EUR"),
    DOVISH_USD_BULLISH_GOLD("USD Melemah (Dovish) • Dorongan Naik XAU/EUR"),
    SAFE_HAVEN_GOLD_BID("Aset Aman (Safe-Haven) • Dorongan Naik XAUUSD"),
    ECB_HAWKISH_EUR_BID("ECB Hawkish • Dorongan Naik EURUSD"),
    NEUTRAL_WATCH("Netral • Waspada Jelang Rilis Data")
}

data class MacroNewsArticle(
    val id: String,
    val title: String,
    val categoryTag: String,
    val affectedSymbols: List<MarketSymbol>,
    val impact: NewsImpactLevel,
    val sentiment: MacroSentiment,
    val publishedTimeEpochSec: Long,
    val sourceName: String,
    val keyTakeaway: String
)

data class NewsEngineReport(
    val articles: List<MacroNewsArticle>,
    val highImpactCount: Int,
    val dominantSentiment: MacroSentiment,
    val newsRiskWarning: String?,
    val lastUpdatedMs: Long,
    val isLiveRssSynced: Boolean
)

enum class AnalyticalVerdict(val label: String) {
    STRONG_BULLISH_CONFLUENCE("KONFLUENSI NAIK KUAT (BULLISH) [CANDLE 1]"),
    MODERATE_BULLISH_BIAS("BIAS NAIK MODERAT (BULLISH)"),
    HTF_BULLISH_LTF_PULLBACK("TF BESAR NAIK • TF KECIL KOREKSI (TUNGGU)"),
    HTF_BEARISH_LTF_PULLBACK("TF BESAR TURUN • TF KECIL KOREKSI (TUNGGU)"),
    NEUTRAL_CONSOLIDATION("NETRAL / SIDEWAYS (TUNGGU KONFIRMASI)"),
    MODERATE_BEARISH_BIAS("BIAS TURUN MODERAT (BEARISH)"),
    STRONG_BEARISH_CONFLUENCE("KONFLUENSI TURUN KUAT (BEARISH) [CANDLE 1]")
}

// 12. SMART MONEY CONCEPTS (FVG & ORDER BLOCKS)
enum class FvgType(val label: String, val isBullish: Boolean) {
    BULLISH_FVG("Bullish FVG (Celah Diskon Beli)", true),
    BEARISH_FVG("Bearish FVG (Celah Premium Jual)", false)
}

data class FairValueGap(
    val barIndex: Int,
    val type: FvgType,
    val topPrice: Double,
    val bottomPrice: Double,
    val isMitigated: Boolean,
    val ageBars: Int
)

data class OrderBlock(
    val barIndex: Int,
    val isBullish: Boolean,
    val topPrice: Double,
    val bottomPrice: Double,
    val label: String
)

data class SmartMoneyReport(
    val activeFvgs: List<FairValueGap>,
    val nearestBullishFvg: FairValueGap?,
    val nearestBearishFvg: FairValueGap?,
    val activeOrderBlocks: List<OrderBlock>,
    val closedBar1InFvg: Boolean,
    val fvgConfluenceNote: String
)

// 13. GLOBAL TRADING SESSION ENGINE
enum class GlobalMarketSession(
    val sessionName: String,
    val localHoursWib: String,
    val liquidityRating: String,
    val recommendation: String
) {
    LONDON_NEW_YORK_OVERLAP("London & NY Overlap (Sesi Emas)", "19:00 - 23:00 WIB", "SANGAT TINGGI", "Sesi paling likuid dengan volume transaksi terbesar."),
    NEW_YORK("Sesi New York (Wall Street)", "19:00 - 04:00 WIB", "TINGGI", "Momentum tinggi pasca rilis berita ekonomi AS."),
    LONDON("Sesi London (Eropa)", "14:00 - 23:00 WIB", "TINGGI", "Awal pergerakan terarah dan ekspansi volume hari ini."),
    ASIA_PACIFIC("Sesi Asia-Pasifik (Tokyo/Sydney)", "06:00 - 14:00 WIB", "SEDANG", "Rentang konsolidasi. Waspada sapuan likuiditas (Asian sweep)."),
    PACIFIC_OFFPEAK("Sesi Off-Peak Pasifik", "04:00 - 06:00 WIB", "RENDAH", "Spread rawan melebar. Disarankan wait and see.")
}

data class TradingSessionReport(
    val activeSession: GlobalMarketSession,
    val utcHour: Int,
    val isLondonNyOverlap: Boolean,
    val isHighLiquidityWindow: Boolean,
    val sessionAdvice: String
)

// 14. HTF TREND ALIGNMENT GUARD
enum class HtfAlignmentStatus(val label: String, val isAligned: Boolean) {
    PERFECT_ALIGNMENT("SEARAH TREN UTAMA H1 (High Probability)", true),
    COUNTER_TREND_WARNING("⚠️ MELAWAN TREN UTAMA H1 (Counter-Trend)", false),
    HTF_RANGE_NEUTRAL("TREN H1 SEDANG SIDEWAYS / KONSOLIDASI", true)
}

data class HtfAlignmentReport(
    val status: HtfAlignmentStatus,
    val htfTrendLabel: String,
    val ltfTrendLabel: String,
    val advice: String,
    val riskModifierNote: String
)

// 15. HISTORICAL VOLATILITY & BREAKOUT PROBABILITY ZONE
data class VolatilityBreakoutPoint(
    val barIndex: Int,
    val closePrice: Double,
    val upperVolBand: Double,
    val lowerVolBand: Double,
    val historicalVolatility: Double
)

data class VolatilityBreakoutProbabilityReport(
    val bullishBreakoutProbPct: Int,
    val bearishBreakdownProbPct: Int,
    val rangeBoundHoldProbPct: Int,
    val resistancePrice: Double,
    val supportPrice: Double,
    val currentPrice: Double,
    val historicalAtr: Double,
    val distanceToResistanceAtr: Double,
    val distanceToSupportAtr: Double,
    val primaryBreakoutRisk: String,
    val recentVolatilityPoints: List<VolatilityBreakoutPoint>
)

data class CompleteHubAnalysis(
    val symbol: MarketSymbol,
    val brokerSuffix: String,
    val activeTimeframe: Timeframe,
    val bars: List<Mt5RateBar>,
    val dataEngine: DataEngineReport,
    val candleEngine: CandleEngineReport,
    val structureEngine: MarketStructureReport,
    val trendEngine: TrendEngineReport,
    val momentumEngine: MomentumEngineReport,
    val volatilityEngine: VolatilityEngineReport,
    val srEngine: SrEngineReport,
    val volumeEngine: VolumeEngineReport,
    val mtfEngine: MtfEngineReport,
    val newsEngine: NewsEngineReport,
    val smartMoneyEngine: SmartMoneyReport,
    val sessionEngine: TradingSessionReport,
    val htfAlignmentGuard: HtfAlignmentReport,
    val breakoutProbability: VolatilityBreakoutProbabilityReport,
    val verdict: AnalyticalVerdict,
    val bullishConfluenceScore: Int,
    val bearishConfluenceScore: Int,
    val confluenceChecklist: List<Pair<String, Boolean>>
)

data class GitHubReleaseInfo(
    val repository: String = "",
    val installedTag: String = "v1.0.0",
    val latestTag: String = "",
    val releaseTitle: String = "",
    val releaseNotes: String = "",
    val publishedAt: String = "",
    val apkDownloadUrl: String = "",
    val apkFileName: String = "",
    val apkSizeMb: Double = 0.0,
    val htmlReleaseUrl: String = "",
    val isUpdateAvailable: Boolean = false,
    val isChecking: Boolean = false,
    val statusMessage: String = "Siap memeriksa pembaruan APK dari GitHub Releases."
)

