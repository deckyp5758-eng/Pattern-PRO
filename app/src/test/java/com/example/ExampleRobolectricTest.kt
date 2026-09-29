package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.LiveMarketAndNewsService
import com.example.model.MarketSymbol
import com.example.model.OnlineDataProvider
import com.example.model.Timeframe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("HUB Pattern Engine", appName)
    }

    @Test
    fun sixPointOnlineVerification_allChecksPassAgainstLiveServers() = runBlocking {
        val service = LiveMarketAndNewsService()

        // Pemeriksaan 1 & 2: Apakah endpoint dapat diakses & apakah data benar-benar berasal dari server?
        val binanceOutcome1 = service.fetchSymbolRates(
            symbol = MarketSymbol.XAUUSD,
            timeframe = Timeframe.M15,
            provider = OnlineDataProvider.BINANCE_VISION
        )
        assertTrue(
            "Binance Vision endpoint harus dapat diakses (HTTP 200)",
            binanceOutcome1 is LiveMarketAndNewsService.RatesFetchOutcome.Success
        )
        val binanceSuccess1 = binanceOutcome1 as LiveMarketAndNewsService.RatesFetchOutcome.Success
        assertEquals(200, binanceSuccess1.httpStatusCode)
        assertTrue("Data harus memiliki minimal 100 candle asli dari server", binanceSuccess1.bars.size >= 100)
        assertTrue("Harga XAUUSD dari server harus riil (> 1000)", binanceSuccess1.bars[1].close > 1000.0)

        // Pemeriksaan 3: Apakah timestamp berubah pada pengambilan berikutnya?
        delay(25)
        val binanceOutcome2 = service.fetchSymbolRates(
            symbol = MarketSymbol.XAUUSD,
            timeframe = Timeframe.M15,
            provider = OnlineDataProvider.BINANCE_VISION
        )
        assertTrue(binanceOutcome2 is LiveMarketAndNewsService.RatesFetchOutcome.Success)
        val binanceSuccess2 = binanceOutcome2 as LiveMarketAndNewsService.RatesFetchOutcome.Success
        assertTrue(
            "Timestamp pengambilan server harus berubah/bertambah",
            binanceSuccess2.serverFetchTimestampMs > binanceSuccess1.serverFetchTimestampMs
        )

        // Pemeriksaan 4: Apakah data berubah ketika sumber provider berubah? (Binance vs Kraken vs Yahoo)
        val krakenOutcome = service.fetchSymbolRates(
            symbol = MarketSymbol.XAUUSD,
            timeframe = Timeframe.M15,
            provider = OnlineDataProvider.KRAKEN_SPOT
        )
        val yahooOutcome = service.fetchSymbolRates(
            symbol = MarketSymbol.XAUUSD,
            timeframe = Timeframe.M15,
            provider = OnlineDataProvider.YAHOO_FINANCE
        )
        assertTrue("Kraken endpoint harus dapat diakses", krakenOutcome is LiveMarketAndNewsService.RatesFetchOutcome.Success)
        assertTrue("Yahoo endpoint harus dapat diakses", yahooOutcome is LiveMarketAndNewsService.RatesFetchOutcome.Success)

        val krakenSuccess = krakenOutcome as LiveMarketAndNewsService.RatesFetchOutcome.Success
        val yahooSuccess = yahooOutcome as LiveMarketAndNewsService.RatesFetchOutcome.Success
        assertNotEquals(
            "Endpoint URL harus berbeda saat ganti sumber provider",
            binanceSuccess1.endpointUrl,
            krakenSuccess.endpointUrl
        )
        val distinctCloseOrVol = setOf(
            "${binanceSuccess1.bars[1].close}_${binanceSuccess1.bars[1].tickVolume}",
            "${krakenSuccess.bars[1].close}_${krakenSuccess.bars[1].tickVolume}",
            "${yahooSuccess.bars[1].close}_${yahooSuccess.bars[1].tickVolume}"
        )
        assertTrue("Data harus terbukti berubah saat sumber provider diganti", distinctCloseOrVol.size >= 2)

        // Pemeriksaan 5 & 6: Apakah error koneksi ditampilkan & apakah aplikasi tetap jujur ketika provider offline?
        val offlineOutcome = service.fetchSymbolRates(
            symbol = MarketSymbol.XAUUSD,
            timeframe = Timeframe.M15,
            provider = OnlineDataProvider.OFFLINE_SIMULATION_TEST
        )
        assertTrue(
            "Ketika provider offline, service WAJIB mengembalikan Error jujur (bukan data palsu)",
            offlineOutcome is LiveMarketAndNewsService.RatesFetchOutcome.Error
        )
        val offlineErr = offlineOutcome as LiveMarketAndNewsService.RatesFetchOutcome.Error
        assertTrue("Pesan error harus tidak kosong dan informatif", offlineErr.errorMessage.isNotBlank())

        // Verifikasi juga Berita Online (ForexFactory / CNBC RSS) & Kejujuran Berita Offline
        val liveNews = service.fetchLiveMacroNews(isOfflineTestMode = false)
        assertTrue("Berita online harus dapat diakses dari server", liveNews is LiveMarketAndNewsService.NewsFetchOutcome.Success)
        val liveNewsSuccess = liveNews as LiveMarketAndNewsService.NewsFetchOutcome.Success
        assertTrue("Artikel berita online tidak boleh kosong", liveNewsSuccess.articles.isNotEmpty())

        val offlineNews = service.fetchLiveMacroNews(isOfflineTestMode = true)
        assertTrue("Berita harus jujur mengembalikan Error saat offline", offlineNews is LiveMarketAndNewsService.NewsFetchOutcome.Error)
    }
}
