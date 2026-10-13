package com.jambo199.vividpulse.alerts

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jambo199.vividpulse.Graph
import com.jambo199.vividpulse.system.BatteryReader
import com.jambo199.vividpulse.widget.Widgets
import java.time.Instant
import java.time.ZoneId

/** About once an hour: notes the battery level, sends the morning report when due, and refreshes the vitals widget. */
class PulseWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val b = BatteryReader(context).sample()
        val now = System.currentTimeMillis()
        Graph.batteryLog.add(LevelSample(now, b.level, b.charging || b.plug != null))
        Widgets.updateVitals(context, b)

        val prefs = context.getSharedPreferences("alerts", Context.MODE_PRIVATE)
        val zone = ZoneId.systemDefault()
        if (Graph.settings.current.overnightReport && Overnight.due(now, zone, prefs.getString("nightSent", null))) {
            Overnight.report(Graph.batteryLog.samples(), now, zone)?.let { r ->
                Notify.post(
                    context,
                    Notify.ID_OVERNIGHT,
                    Notify.builder(context, Notify.REPORTS)
                        .setContentTitle(r.title)
                        .setContentText(r.text)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(r.text))
                        .setAutoCancel(true)
                        .setContentIntent(Notify.openApp(context, "battery", 3)),
                )
                prefs.edit().putString("nightSent", Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString()).apply()
            }
        }
        // Make sure the charge watcher is waiting for the next charge (it's restarted after updates).
        if (Graph.settings.current.chargeCompanion) ChargeWatch.start(context)
        return Result.success()
    }
}
