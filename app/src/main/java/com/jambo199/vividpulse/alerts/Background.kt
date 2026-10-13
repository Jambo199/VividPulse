package com.jambo199.vividpulse.alerts

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.jambo199.vividpulse.Graph
import java.util.concurrent.TimeUnit

/** Everything the app does while it's closed, scheduled with Android's job scheduler. */
object Background {
    private const val HOURLY = "pulse-hourly"
    private const val DEALS = "deals"
    private const val DEALS_NOW = "deals-now"

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            HOURLY,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PulseWorker>(1, TimeUnit.HOURS).build(),
        )
        wm.enqueueUniquePeriodicWork(
            DEALS,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DealsWorker>(6, TimeUnit.HOURS).setConstraints(online).build(),
        )
        if (Graph.settings.current.chargeCompanion) ChargeWatch.start(context) else ChargeWatch.stop(context)
    }

    /** Checks starred games' prices (and refreshes the deals widget) now. */
    fun checkDealsNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            DEALS_NOW,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<DealsWorker>().setConstraints(online).build(),
        )
    }
}
