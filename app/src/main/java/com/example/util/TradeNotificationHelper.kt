package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.model.AnalyticalVerdict
import com.example.model.MarketSymbol
import com.example.model.OnlineDataProvider
import com.example.model.Timeframe
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object TradeNotificationHelper {

    const val CHANNEL_ID = "hub_trade_signal_channel"
    private const val CHANNEL_NAME = "Sinyal Trade Terkonfirmasi"
    private const val CHANNEL_DESC = "Notifikasi sinyal BUY dan SELL terukur pada penutupan candle [1] per provider."

    // Set untuk mencegah pengiriman notifikasi ganda pada candle yang sama
    private val notifiedSignalKeys = ConcurrentHashMap.newKeySet<String>()

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun sendTradeSignalNotification(
        context: Context,
        provider: OnlineDataProvider,
        symbol: MarketSymbol,
        timeframe: Timeframe,
        bar1EpochSec: Long,
        verdict: AnalyticalVerdict,
        entryPrice: Double,
        stopLossPrice: Double,
        target1Price: Double,
        target2Price: Double,
        isTestAlert: Boolean = false
    ): Boolean {
        createNotificationChannel(context)

        val isBuy = verdict in listOf(
            AnalyticalVerdict.STRONG_BULLISH_CONFLUENCE,
            AnalyticalVerdict.MODERATE_BULLISH_BIAS
        )
        val isSell = verdict in listOf(
            AnalyticalVerdict.STRONG_BEARISH_CONFLUENCE,
            AnalyticalVerdict.MODERATE_BEARISH_BIAS
        )

        if (!isBuy && !isSell && !isTestAlert) {
            return false // Jangan kirim notifikasi jika pasar sedang netral/pullback
        }

        val actionText = if (isBuy) "BUY" else if (isSell) "SELL" else "UJI COBA"
        val signalKey = "${provider.id}_${symbol.code}_${timeframe.name}_${bar1EpochSec}_${verdict.name}"

        if (!isTestAlert && notifiedSignalKeys.contains(signalKey)) {
            return false // Sudah pernah dikirim untuk candle ini
        }

        notifiedSignalKeys.add(signalKey)

        val digits = symbol.digits
        val entryFormatted = String.format(Locale.US, "%.${digits}f", entryPrice)
        val slFormatted = String.format(Locale.US, "%.${digits}f", stopLossPrice)
        val tp1Formatted = String.format(Locale.US, "%.${digits}f", target1Price)
        val tp2Formatted = String.format(Locale.US, "%.${digits}f", target2Price)

        val title = if (isTestAlert) {
            "🔔 [UJI] Notifikasi Sinyal Trade Aktif: ${symbol.code} (${provider.shortLabel})"
        } else {
            "⚡ [${provider.shortLabel}] SINYAL $actionText ${symbol.code} (${timeframe.label})"
        }

        val contentText = "Entry: $entryFormatted | SL: $slFormatted | TP1: $tp1Formatted | TP2: $tp2Formatted"
        val bigText = buildString {
            append("Instrumen: ${symbol.code} (${symbol.displayName})\n")
            append("Provider Feed: ${provider.displayName}\n")
            append("Timeframe: ${timeframe.label} (Candle Tutup [1])\n")
            append("Arah Sinyal: $actionText Terkonfirmasi (Non-Repaint)\n")
            append("━━━━━━━━━━━━━━━━━━━━\n")
            append("• Entry Acuan: $entryFormatted\n")
            append("• Stop Loss (1.5x ATR): $slFormatted\n")
            append("• Target 1 (1.5R): $tp1Formatted\n")
            append("• Target 2 (2.5R): $tp2Formatted")
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            (System.currentTimeMillis() % 10000).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notificationId = (provider.hashCode() * 31 + symbol.hashCode() * 17 + timeframe.hashCode() + if (isTestAlert) 9999 else 0) and 0x7FFFFFFF

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        return try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
            true
        } catch (e: SecurityException) {
            // Izin POST_NOTIFICATIONS belum diberikan oleh pengguna
            false
        }
    }
}
