package com.jambo199.vividpulse

import android.app.Application
import com.jambo199.vividpulse.data.SettingsStore
import com.jambo199.vividpulse.games.GamesRepository
import com.jambo199.vividpulse.games.WatchStore
import com.jambo199.vividpulse.system.DeviceSpecs
import com.jambo199.vividpulse.system.LiveMonitor
import com.jambo199.vividpulse.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class PulseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
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
    val updates by lazy { UpdateManager(app, scope) }

    private val specsLazy = lazy { DeviceSpecs(app, monitor.cpu) }

    /** The phone's fixed specs. The first read is slow (files, OpenGL): do it off the main thread. */
    val specs: DeviceSpecs by specsLazy

    /** The specs if they've been read already. */
    val specsIfReady: DeviceSpecs? get() = if (specsLazy.isInitialized()) specs else null

    fun init(app: Application) {
        this.app = app
    }
}
