package com.jambo199.vividpulse.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jambo199.vividpulse.MainActivity
import com.jambo199.vividpulse.R

/** The app's notifications: what they're for, and posting them safely. */
object Notify {
    /** While charging: level, power and when the limit's reached. Quiet. */
    const val CHARGING = "charging"
    /** The charge limit alert. */
    const val BATTERY = "battery_alerts"
    /** The overnight battery report. */
    const val REPORTS = "reports"
    /** Starred games on sale, and Steam's big sales. */
    const val DEALS = "deals"
    /** The paired PC: Steam downloads finishing, running hot. */
    const val PC = "pc"

    const val ID_CHARGING = 100
    const val ID_LIMIT = 101
    const val ID_OVERNIGHT = 102
    const val ID_BIG_SALE = 103
    const val ID_PC_HEAT = 104
    private const val ID_GAME_BASE = 10_000
    private const val ID_PC_BASE = 2_000_000

    fun channels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CHARGING, "Charging", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Battery level and charging power while the phone charges"
                    setShowBadge(false)
                },
                NotificationChannel(BATTERY, "Charge limit", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "When the battery reaches the level you chose"
                },
                NotificationChannel(REPORTS, "Overnight report", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "How much battery the phone used overnight"
                },
                NotificationChannel(DEALS, "Game deals", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Starred games on sale, and Steam's big sales"
                },
                NotificationChannel(PC, "PC alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Steam downloads finishing on your PC, and your PC running hot"
                },
            ),
        )
    }

    /** Whether the app may show notifications at all. */
    fun allowed(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    /** Opens the app on [route] (a page like "battery" or "game/730"). */
    fun openApp(context: Context, route: String?, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_ROUTE, route)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun builder(context: Context, channel: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_pulse)
            .setColor(0xFF22D3EE.toInt())

    fun post(context: Context, id: Int, builder: NotificationCompat.Builder) {
        if (!allowed(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (ignored: SecurityException) {
        }
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)

    /** One notification per starred game. */
    fun gameId(appId: Int): Int = ID_GAME_BASE + (appId % 1_000_000)

    /** One notification per game finishing on the PC. */
    fun pcId(appId: Int): Int = ID_PC_BASE + (appId % 1_000_000)
}
