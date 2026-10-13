package com.jambo199.vividpulse

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.jambo199.vividpulse.ui.PulseRoot
import com.jambo199.vividpulse.ui.theme.PulseTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** A page to open, from a launcher shortcut or the automated test ("route" extra). */
    private val route = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) route.value = intent?.getStringExtra(EXTRA_ROUTE)
        setContent {
            val settings by Graph.settings.state.collectAsStateWithLifecycle()
            PulseTheme(settings.theme) {
                PulseRoot(route)
            }
        }
        lifecycleScope.launch {
            Graph.settings.state.map { it.keepScreenOn }.distinctUntilChanged().collect { on ->
                if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        // Read the phone's vitals only while the app is on screen.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Graph.monitor.run { Graph.settings.current.refresh.ms }
            }
        }
        Graph.updates.checkOnLaunch(Graph.settings.current.autoCheckUpdates)
        // Read the fixed specs in the background so the pages that show them open instantly.
        lifecycleScope.launch(Dispatchers.IO) { Graph.specs }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_ROUTE)?.let { route.value = it }
    }

    override fun onResume() {
        super.onResume()
        Graph.updates.onResume()
        Graph.updates.checkIfStale(Graph.settings.current.autoCheckUpdates)
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}
