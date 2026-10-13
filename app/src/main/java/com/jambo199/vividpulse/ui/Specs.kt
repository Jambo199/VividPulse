package com.jambo199.vividpulse.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.jambo199.vividpulse.Graph
import com.jambo199.vividpulse.system.DeviceSpecs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The phone's fixed specs, once they've been read (null for a moment on first launch). */
@Composable
fun rememberSpecs(): DeviceSpecs? {
    val specs by produceState(Graph.specsIfReady) {
        if (value == null) value = withContext(Dispatchers.IO) { Graph.specs }
    }
    return specs
}
