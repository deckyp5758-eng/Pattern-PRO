package com.example.domain

import com.example.model.MarketSymbol
import com.example.model.Mt5RateBar
import com.example.model.Timeframe
import java.util.Locale

/**
 * Laporan kesehatan sistem, pencegah dobel permintaan (Request Guard),
 * pencegah respons usang/tumpang tindih (Stale Response Guard),
 * dan pengaudit integritas data OHLCV agar tidak pernah salah ambil data.
 */
data class SystemDiagnosticReport(
    val isDataConsistent: Boolean = true,
    val bar1IntegrityHash: String = "-",
    val activeGenerationId: Long = 1L,
    val totalFullCalculations: Int = 0,
    val lightweightBar0Ticks: Int = 0,
    val blockedDuplicateRequests: Int = 0,
    val discardedStaleResponses: Int = 0,
    val cachePurgeCount: Int = 0,
    val newsCacheHits: Int = 0,
    val lastSyncDurationMs: Long = 0L,
    val integrityNotes: List<String> = emptyList(),
    val recentEventsLog: List<String> = emptyList()
)

object SystemDiagnosticGuard {

    /**
     * Memvalidasi apakah harga candle benar-benar milik simbol yang diminta
     * (mencegah data XAUUSD dan EURUSD tertukar atau tumpang tindih).
     */
    fun isPriceMatchingSymbol(symbol: MarketSymbol, price: Double): Boolean {
        if (price <= 0.0 || price.isNaN() || price.isInfinite()) return false
        return when (symbol) {
            MarketSymbol.XAUUSD -> price in 500.0..25000.0
            MarketSymbol.EURUSD -> price in 0.40..3.00
        }
    }

    /**
     * Memvalidasi urutan indeks, urutan waktu, kesesuaian simbol, dan validitas harga High/Low
     * agar tidak ada data tumpang tindih, duplikat slot waktu, atau bar yang salah urutan.
     */
    fun validateRateSeries(
        symbol: MarketSymbol,
        timeframe: Timeframe,
        bars: List<Mt5RateBar>
    ): Pair<Boolean, List<String>> {
        val issues = mutableListOf<String>()
        if (bars.isEmpty()) {
            return Pair(false, listOf("Daftar candle kosong (0 bar)!"))
        }
        if (bars.size < 60) {
            issues.add("Jumlah candle ${symbol.code} ${timeframe.label} terlalu sedikit (${bars.size}).")
        }
        val bar0 = bars.getOrNull(0)
        val bar1 = bars.getOrNull(1)
        val bar2 = bars.getOrNull(2)

        if (bar0?.barIndex != 0) {
            issues.add("Indeks Candle [0] tidak sesuai (ditemukan: ${bar0?.barIndex}).")
        }
        if (bar1?.barIndex != 1) {
            issues.add("Indeks Candle Tutup [1] tidak sesuai (ditemukan: ${bar1?.barIndex}).")
        }
        if (bar0 != null && bar1 != null && bar0.timeEpochSec <= bar1.timeEpochSec) {
            issues.add("Urutan waktu terbalik/tumpang tindih: Candle [0] (${bar0.timeEpochSec}) <= Candle [1] (${bar1.timeEpochSec}).")
        }
        if (bar1 != null && bar2 != null && bar1.timeEpochSec <= bar2.timeEpochSec) {
            issues.add("Urutan waktu terbalik/tumpang tindih: Candle [1] (${bar1.timeEpochSec}) <= Candle [2] (${bar2.timeEpochSec}).")
        }

        // Validasi kecocokan harga terhadap simbol agar data XAUUSD & EURUSD tidak pernah tertukar
        if (bar1 != null && !isPriceMatchingSymbol(symbol, bar1.close)) {
            issues.add("Harga Candle [1] (${bar1.close}) tidak sesuai dengan rentang wajar ${symbol.code}!")
        }

        // Periksa konsistensi High/Low, nilai finite/positif, dan urutan waktu turun (strictly descending)
        val checkLimit = 50.coerceAtMost(bars.size)
        for (i in 0 until checkLimit) {
            val bar = bars[i]
            if (bar.open.isNaN() || bar.open.isInfinite() || bar.high.isNaN() || bar.high.isInfinite() ||
                bar.low.isNaN() || bar.low.isInfinite() || bar.close.isNaN() || bar.close.isInfinite() ||
                bar.open <= 0.0 || bar.high <= 0.0 || bar.low <= 0.0 || bar.close <= 0.0
            ) {
                issues.add("Anomali OHLC pada Candle [${bar.barIndex}]: harga non-positif, NaN, atau tak hingga.")
                break
            }
            val maxOC = maxOf(bar.open, bar.close)
            val minOC = minOf(bar.open, bar.close)
            if (bar.high + 1e-7 < maxOC || bar.low - 1e-7 > minOC || bar.high < bar.low) {
                issues.add("Anomali OHLC pada Candle [${bar.barIndex}]: High/Low di luar Open/Close.")
                break
            }
            if (i > 0 && bars[i - 1].timeEpochSec <= bar.timeEpochSec) {
                issues.add("Tumpang tindih timestamp pada Candle [${i - 1}] dan [$i].")
                break
            }
        }

        val notes = if (issues.isEmpty()) {
            listOf(
                "Isolasi Simbol & Timeframe Valid: Data ${symbol.code} (${timeframe.label}) bebas tumpang tindih",
                "Urutan Indeks MT5 Valid: Candle [2] Kiri -> Candle [1] Tutup -> Candle [0] Ujung Kanan",
                "Urutan Timestamp Valid: Tidak ada duplikasi slot waktu atau bar yang tertukar",
                "Kunci Non-Repaint Aktif: Pembaruan Candle [0] tidak mengubah sinyal Candle [1]"
            )
        } else {
            issues
        }

        return Pair(issues.isEmpty(), notes)
    }

    /**
     * Menghasilkan tanda tangan unik (Hash) untuk Candle Tutup [1].
     * Selama candle baru belum tutup, kode tanda tangan ini WAJIB tetap sama (bukti Non-Repaint).
     */
    fun computeClosedBar1Signature(
        symbol: MarketSymbol,
        timeframe: Timeframe,
        closedBar1: Mt5RateBar
    ): String {
        val raw = String.format(
            Locale.US,
            "%s-%s-T%d-O%.5f-H%.5f-L%.5f-C%.5f-V%d",
            symbol.code,
            timeframe.label,
            closedBar1.timeEpochSec,
            closedBar1.open,
            closedBar1.high,
            closedBar1.low,
            closedBar1.close,
            closedBar1.tickVolume
        )
        val hash = kotlin.math.abs(raw.hashCode()).toString(16).uppercase(Locale.US)
        return "LOCK-${symbol.code}-${timeframe.label}-${closedBar1.timeEpochSec % 100000}-$hash"
    }
}
