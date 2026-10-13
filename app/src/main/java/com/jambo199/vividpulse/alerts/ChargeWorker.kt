package com.jambo199.vividpulse.alerts

import android.content.Context
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jambo199.vividpulse.Graph
import com.jambo199.vividpulse.system.BatteryReader
import com.jambo199.vividpulse.system.BatterySample
import com.jambo199.vividpulse.util.Format
import com.jambo199.vividpulse.widget.Widgets
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Runs while the phone charges (Android starts it when charging begins and stops it when the
 * charger comes out): records the charge, keeps a live notification up to date and alerts once at
 * the charge limit. Each run lasts a few minutes, then hands over to the next while still charging.
 */
class ChargeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!Graph.settings.current.chargeCompanion) {
            Notify.cancel(context, Notify.ID_CHARGING)
            return Result.success()
        }
        val reader = BatteryReader(context)
        val until = SystemClock.elapsedRealtime() + RUN_MS
        var plugged = true
        try {
            while (SystemClock.elapsedRealtime() < until) {
                val b = reader.sample()
                plugged = b.plug != null || b.charging
                if (!plugged) break
                val session = Graph.chargeHistory.record(
                    ChargePoint(System.currentTimeMillis(), b.level, b.watts?.takeIf { it > 0f }, b.temperatureC),
                )
                val limit = Graph.settings.current.chargeLimit
                if (ChargeMath.shouldAlert(session, limit, b.level)) {
                    limitReached(context, b)
                    Graph.chargeHistory.markAlerted(session.startedAt)
                }
                charging(context, b, session, limit)
                Widgets.updateVitals(context, b)
                delay(if (b.full || b.level >= 100f) SLOW_MS else EVERY_MS)
            }
        } finally {
            if (!reader.sample().let { it.plug != null || it.charging }) {
                Notify.cancel(context, Notify.ID_CHARGING)
                Widgets.updateVitals(context)
            }
        }
        // The next run carries on (still plugged in) or waits for the next charge.
        if (Graph.settings.current.chargeCompanion) ChargeWatch.next(context)
        return Result.success()
    }

    private fun charging(context: Context, b: BatterySample, session: ChargeSession, limit: Int) {
        val level = b.level.roundToInt()
        val watts = b.watts?.takeIf { it > 0f }
        val toLimit = if (limit in 1..99 && level < limit) ChargeMath.minutesTo(limit, session.points) else null
        val toFull = b.timeToFullMs?.let { (it / 60_000).toInt() } ?: ChargeMath.minutesTo(100, session.points)
        val text = listOfNotNull(
            toLimit?.let { "$limit% in about ${Format.minutes(it)}" },
            toFull?.takeIf { level < 100 }?.let { "full in about ${Format.minutes(it)}" },
            b.temperatureC?.let { Format.temperature(it, Graph.settings.current.fahrenheit) },
        ).joinToString(" · ")
        Notify.post(
            context,
            Notify.ID_CHARGING,
            Notify.builder(context, Notify.CHARGING)
                .setContentTitle(listOfNotNull(if (b.full) "Full" else "Charging", "$level%", watts?.let { Format.watts(it) }).joinToString(" · "))
                .setContentText(text.ifBlank { "Since ${Format.clock(session.startedAt)} · +${session.gained.roundToInt()}%" })
                .setProgress(100, level, false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setContentIntent(Notify.openApp(context, "battery", 1)),
        )
    }

    private fun limitReached(context: Context, b: BatterySample) {
        Notify.post(
            context,
            Notify.ID_LIMIT,
            Notify.builder(context, Notify.BATTERY)
                .setContentTitle("Battery at ${b.level.roundToInt()}%")
                .setContentText("You can unplug now. Stopping around here is kinder to the battery over time.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(Notify.openApp(context, "battery", 2)),
        )
    }

    private companion object {
        /** A little under the 10 minutes Android allows each run. */
        const val RUN_MS = 9 * 60_000L
        const val EVERY_MS = 30_000L
        /** Once full, a reading every couple of minutes is plenty. */
        const val SLOW_MS = 120_000L
    }
}

/** Starts and stops the charge watcher. */
object ChargeWatch {
    private const val NAME = "charge-watch"

    private fun request() = OneTimeWorkRequestBuilder<ChargeWorker>()
        .setConstraints(Constraints.Builder().setRequiresCharging(true).build())
        .build()

    /** Waits for the next charge (or watches this one), unless it already is. */
    fun start(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request())
    }

    /** Carries on after the current run: straight away while charging, otherwise at the next charge. */
    fun next(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request())
    }

    /**
     * Plugged in just now (seen while the app is running): starts watching at once rather than
     * waiting for Android to decide the phone is charging, which can take a minute.
     */
    fun now(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ChargeWorker>().build())
    }

    fun stop(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(NAME)
        Notify.cancel(context, Notify.ID_CHARGING)
    }
}
