package com.jambo199.vividpulse.alerts

import android.content.Context
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.jambo199.vividpulse.Graph
import com.jambo199.vividpulse.pc.PcDownloads
import com.jambo199.vividpulse.pc.PcFinished
import com.jambo199.vividpulse.pc.PcLive
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** When to tell you about the paired PC. */
object PcAlertMath {
    /** One "running hot" alert per half hour at most. */
    const val HEAT_EVERY_MS = 30 * 60_000L

    /** Finished downloads more than this long before the PC's answer are old news. */
    const val FINISHED_RECENT_MS = 2 * 3600_000L

    fun key(f: PcFinished) = "${f.appId}@${f.at}"

    /** Downloads that finished recently and haven't been told about. */
    fun newlyFinished(d: PcDownloads, told: Set<String>): List<PcFinished> =
        d.finished.filter { key(it) !in told && d.at - it.at in 0..FINISHED_RECENT_MS }

    fun heatDue(tempC: Float?, limit: Int, lastAlertAt: Long, now: Long): Boolean =
        tempC != null && tempC >= limit && now - lastAlertAt >= HEAT_EVERY_MS

    /** Worth watching closely: something's downloading, or a game is running. */
    fun busy(d: PcDownloads?, live: PcLive?): Boolean =
        d?.downloads?.any { it.state != "paused" } == true || (live?.playingAppId ?: 0) != 0
}

/**
 * Looks at the paired PC in the background: tells you when a Steam download finishes and when the
 * graphics card runs hot. A quick look every 15 minutes; while something's downloading or a game
 * is running, it keeps watching (a look every 20 seconds, a run of a few minutes at a time).
 */
class PcWatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = Graph.pc
        if (repo.pairing.value == null || !(repo.alertDownloads || repo.alertHeat)) return Result.success()
        val watching = inputData.getBoolean(WATCH, false)
        val until = SystemClock.elapsedRealtime() + if (watching) RUN_MS else 0
        var busy: Boolean
        while (true) {
            val downloads = if (repo.alertDownloads) runCatching { repo.downloads() }.getOrNull() else null
            val live = runCatching { repo.live() }.getOrNull()
            // The PC is off (or Vivid Pulse PC isn't running): nothing to watch until it's back.
            if (downloads == null && live == null) {
                busy = false
                break
            }
            tell(applicationContext, repo.pairing.value?.name ?: "your PC", downloads, live)
            busy = PcAlertMath.busy(downloads, live)
            if (!busy || SystemClock.elapsedRealtime() >= until) break
            delay(EVERY_MS)
        }
        if (busy) PcWatch.next(applicationContext)
        return Result.success()
    }

    private fun tell(context: Context, pc: String, downloads: PcDownloads?, live: PcLive?) {
        val repo = Graph.pc
        if (downloads != null && repo.alertDownloads) {
            val told = repo.toldFinished
            val fresh = PcAlertMath.newlyFinished(downloads, told)
            fresh.forEach { f ->
                Notify.post(
                    context,
                    Notify.pcId(f.appId),
                    Notify.builder(context, Notify.PC)
                        .setContentTitle("${f.name ?: "A game"} is ready to play")
                        .setContentText("Finished downloading on $pc")
                        .setContentIntent(Notify.openApp(context, "pc", Notify.pcId(f.appId)))
                        .setAutoCancel(true),
                )
            }
            // Everything listed now counts as told, so old ones don't come up later.
            repo.toldFinished = told + downloads.finished.map(PcAlertMath::key)
        }
        val temp = live?.gpu?.tempC
        val now = System.currentTimeMillis()
        if (repo.alertHeat && PcAlertMath.heatDue(temp, repo.heatLimit, repo.lastHeatAlertAt, now)) {
            repo.lastHeatAlertAt = now
            Notify.post(
                context,
                Notify.ID_PC_HEAT,
                Notify.builder(context, Notify.PC)
                    .setContentTitle("$pc is running hot")
                    .setContentText("Graphics card at ${temp!!.roundToInt()}°C (your alert is at ${repo.heatLimit}°C)")
                    .setStyle(
                        NotificationCompat.BigTextStyle().bigText(
                            "Graphics card at ${temp.roundToInt()}°C (your alert is at ${repo.heatLimit}°C). Check the PC's fans and airflow, or ease off the settings.",
                        ),
                    )
                    .setContentIntent(Notify.openApp(context, "pc", Notify.ID_PC_HEAT))
                    .setAutoCancel(true),
            )
        }
    }

    companion object {
        const val WATCH = "watch"
        private const val EVERY_MS = 20_000L
        private const val RUN_MS = 9 * 60_000L
    }
}

/** Schedules the PC watcher: a quick look every 15 minutes, and close watching while it's busy. */
object PcWatch {
    private const val CHECK = "pc-check"
    private const val WATCH = "pc-watch"

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    private fun watchRequest() = OneTimeWorkRequestBuilder<PcWatchWorker>()
        .setInputData(workDataOf(PcWatchWorker.WATCH to true))
        .setConstraints(online)
        .build()

    /** Turns the quick look on or off to match the pairing and the alert settings. */
    fun schedule(context: Context) {
        val repo = Graph.pc
        val wm = WorkManager.getInstance(context)
        if (repo.pairing.value != null && (repo.alertDownloads || repo.alertHeat)) {
            wm.enqueueUniquePeriodicWork(
                CHECK,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PcWatchWorker>(15, TimeUnit.MINUTES).setConstraints(online).build(),
            )
        } else {
            stop(context)
        }
    }

    /** Something's going on (seen in the PC tab): watch closely, unless already. */
    fun start(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(WATCH, ExistingWorkPolicy.KEEP, watchRequest())
    }

    fun next(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(WATCH, ExistingWorkPolicy.APPEND_OR_REPLACE, watchRequest())
    }

    fun stop(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(CHECK)
        wm.cancelUniqueWork(WATCH)
    }
}
