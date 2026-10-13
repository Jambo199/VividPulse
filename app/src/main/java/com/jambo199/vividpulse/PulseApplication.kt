package com.jambo199.vividpulse

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.jambo199.vividpulse.alerts.ChargeWatch
import com.jambo199.vividpulse.alerts.Background
import com.jambo199.vividpulse.alerts.BatteryLog
import com.jambo199.vividpulse.alerts.ChargeHistory
import com.jambo199.vividpulse.alerts.Notify
import com.jambo199.vividpulse.data.SettingsStore
import com.jambo199.vividpulse.games.GamesRepository
import com.jambo199.vividpulse.games.WatchStore
import com.jambo199.vividpulse.system.DeviceSpecs
import com.jambo199.vividpulse.system.LiveMonitor
import com.jambo199.vividpulse.tools.RamBooster
import com.jambo199.vividpulse.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class PulseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        Notify.channels(this)
        // Background checks: hourly battery notes, deal alerts, and the charge watcher if it's on.
        runCatching { Background.schedule(this) }
        // While the app's process is running, plugging in starts the charge watcher straight away.
        ContextCompat.registerReceiver(
            this,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (Graph.settings.current.chargeCompanion) runCatching { ChargeWatch.now(context) }
                }
            },
            IntentFilter(Intent.ACTION_POWER_CONNECTED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }
}

/** The app's long-lived pieces. */
object Graph {
    lateinit var app: Application
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings by lazy { SettingsStore(app) }
    val monitor by lazy { LiveMonitor(app) }
    val games by lazy { GamesRepository(app) }
    val watching by lazy { WatchStore(app) }
    val booster by lazy { RamBooster(app, scope) }
    val updates by lazy { UpdateManager(app, scope) }
    val chargeHistory by lazy { ChargeHistory(app) }
    val batteryLog by lazy { BatteryLog(app) }

    private val specsLazy = lazy { DeviceSpecs(app, monitor.cpu) }

    /** The phone's fixed specs. The first read is slow (files, OpenGL): do it off the main thread. */
    val specs: DeviceSpecs by specsLazy

    /** The specs if they've been read already. */
    val specsIfReady: DeviceSpecs? get() = if (specsLazy.isInitialized()) specs else null

    fun init(app: Application) {
        this.app = app
    }
}
