package com.jambo199.vividpulse.ui.device

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jambo199.vividpulse.system.CameraMath
import com.jambo199.vividpulse.system.CameraReader
import com.jambo199.vividpulse.system.CameraSpec
import com.jambo199.vividpulse.system.DisplayReader
import com.jambo199.vividpulse.system.DisplaySpec
import com.jambo199.vividpulse.system.SensorCatalog
import com.jambo199.vividpulse.system.SensorEntry
import com.jambo199.vividpulse.ui.Routes
import com.jambo199.vividpulse.ui.components.AnimatedNumber
import com.jambo199.vividpulse.ui.components.CardTitle
import com.jambo199.vividpulse.ui.components.ChartLine
import com.jambo199.vividpulse.ui.components.DetailPage
import com.jambo199.vividpulse.ui.components.GlassCard
import com.jambo199.vividpulse.ui.components.InfoRow
import com.jambo199.vividpulse.ui.components.LiveChart
import com.jambo199.vividpulse.ui.components.Pill
import com.jambo199.vividpulse.ui.components.RingGauge
import com.jambo199.vividpulse.ui.components.ShimmerBox
import com.jambo199.vividpulse.ui.components.Stat
import com.jambo199.vividpulse.ui.components.bouncyClick
import com.jambo199.vividpulse.ui.components.entrance
import com.jambo199.vividpulse.ui.components.sharedCard
import com.jambo199.vividpulse.ui.theme.Accents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisplayScreen(back: () -> Unit, open: (String) -> Unit) {
    val context = LocalContext.current
    val spec by produceState<DisplaySpec?>(null) { value = withContext(Dispatchers.IO) { DisplayReader.read(context) } }
    // The refresh rate changes on its own on adaptive (LTPO) screens; follow it live.
    var hz by remember { mutableFloatStateOf(DisplayReader.currentHz(context)) }
    DisposableEffect(Unit) {
        val dm = context.getSystemService(DisplayManager::class.java)
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                if (displayId == Display.DEFAULT_DISPLAY) hz = DisplayReader.currentHz(context)
            }
        }
        dm?.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        onDispose { dm?.unregisterDisplayListener(listener) }
    }
    val fps = rememberMeasuredFps()
    val top = spec?.refreshRates?.maxOrNull() ?: 60f

    DetailPage("Display", back, subtitle = spec?.let { "${it.maxWidth} × ${it.maxHeight}" }) {
        item {
            GlassCard(Modifier.fillMaxWidth().sharedCard("card-display"), accent = Accents.cpu) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RingGauge(hz / top, Accents.cpu, Modifier.size(150.dp), thickness = 13.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AnimatedNumber(hz, { "${it.roundToInt()}" }, MaterialTheme.typography.displaySmall)
                            Text("Hz now", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Stat("Frames drawn", "${fps.roundToInt()} fps")
                        Stat("Fastest", "${top.roundToInt()} Hz")
                        spec?.inches?.let { Stat("Size", String.format(Locale.US, "%.2f inches", it)) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Adaptive screens change speed by themselves: fast while things move, slow when still to save battery.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        spec?.let { s ->
            item {
                GlassCard(Modifier.fillMaxWidth().entrance(1)) {
                    Text("Refresh rates", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        s.refreshRates.forEach { r ->
                            Pill("${r.roundToInt()} Hz", Accents.cpu, filled = (r - hz) * (r - hz) < 1f)
                        }
                    }
                    if (s.hdr.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text("HDR video", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            s.hdr.forEach { Pill(it, Accents.storage) }
                        }
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth().entrance(2)) {
                    s.rows().forEach { InfoRow(it.label, it.value) }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().entrance(3), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassCard(Modifier.weight(1f), accent = Accents.games, onClick = { open(Routes.SCREEN) }) {
                    CardTitle(Icons.Rounded.GridOn, "Dead pixels", Accents.games)
                }
                GlassCard(Modifier.weight(1f), accent = Accents.download, onClick = { open(Routes.FPS) }) {
                    CardTitle(Icons.Rounded.Speed, "Smoothness", Accents.download)
                }
            }
        }
    }
}

/** How many frames a second the app is drawing (it keeps animating, so: the screen's top speed). */
@Composable
fun rememberMeasuredFps(): Float {
    var fps by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var windowStart = withFrameNanos { it }
        var frames = 0
        while (true) {
            val now = withFrameNanos { it }
            frames++
            if (now - windowStart >= 500_000_000L) {
                fps = frames * 1e9f / (now - windowStart)
                frames = 0
                windowStart = now
            }
        }
    }
    return fps
}

@Composable
fun CamerasScreen(back: () -> Unit) {
    val context = LocalContext.current
    val cameras by produceState<List<CameraSpec>?>(null) { value = withContext(Dispatchers.IO) { CameraReader.read(context) } }
    DetailPage("Cameras", back, subtitle = cameras?.let { "${it.size} cameras apps can use" }) {
        val list = cameras
        if (list == null) {
            items(2) { ShimmerBox(Modifier.fillMaxWidth().height(200.dp)) }
        } else {
            if (list.isEmpty()) {
                item {
                    GlassCard(Modifier.fillMaxWidth(), accent = Accents.upload) {
                        Text("No cameras are shared with apps on this device.", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            list.forEachIndexed { i, cam ->
                item {
                    CameraCard(cam, Modifier.then(if (i == 0) Modifier.sharedCard("card-cameras") else Modifier).entrance(i))
                }
            }
            item {
                Text(
                    "Phones often join several lenses into one camera for apps and switch between them as you zoom, so the camera app can show more than appears here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CameraCard(cam: CameraSpec, modifier: Modifier) {
    val front = cam.facing == "Front"
    val accent = if (front) Accents.upload else Accents.cpu
    val kind = cam.equivalentMm.firstOrNull()?.let { CameraMath.lensKind(it, front) } ?: cam.facing
    GlassCard(modifier.fillMaxWidth(), accent = accent) {
        CardTitle(Icons.Rounded.CameraAlt, "${cam.facing} · $kind", accent, trailing = "id ${cam.id}")
        Spacer(Modifier.height(10.dp))
        Row {
            Stat("Resolution", cam.megapixels?.let { "${it.roundToInt()} MP" } ?: "—", Modifier.weight(1f))
            Stat("Lens", cam.equivalentMm.firstOrNull()?.let { "$it mm" } ?: "—", Modifier.weight(1f))
            Stat("Aperture", cam.apertures.firstOrNull()?.let { "f/$it" } ?: "—", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            cam.bestVideo?.let { Pill("$it video", accent) }
            if (cam.ois) Pill("Stabilised", Accents.battery)
            if (cam.tenBit) Pill("10-bit HDR", Accents.storage)
            if (cam.raw) Pill("RAW", Accents.memory)
            if (cam.flash) Pill("Flash", Accents.gpu, filled = false)
            if (cam.lenses > 1) Pill("${cam.lenses} lenses in one", Accents.games, filled = false)
        }
        Spacer(Modifier.height(6.dp))
        cam.sensorMm?.let { InfoRow("Sensor size", it) }
        if (cam.equivalentMm.size > 1) InfoRow("Focal lengths", cam.equivalentMm.joinToString(" / ") { "$it mm" })
        cam.maxZoom?.let { InfoRow("Digital zoom", String.format(Locale.US, "up to %.0f×", it)) }
    }
}

@Composable
fun SensorsScreen(back: () -> Unit) {
    val context = LocalContext.current
    val sensors by produceState<List<SensorEntry>?>(null) { value = withContext(Dispatchers.IO) { SensorCatalog.list(context) } }
    DetailPage("Sensors", back, subtitle = sensors?.let { "${it.size} sensors · tap one to watch it" }) {
        val list = sensors
        if (list == null) {
            items(4) { ShimmerBox(Modifier.fillMaxWidth().height(72.dp)) }
        } else {
            list.forEachIndexed { i, s ->
                item { SensorCard(s, Modifier.then(if (i == 0) Modifier.sharedCard("card-sensors") else Modifier).entrance(i)) }
            }
        }
    }
}

@Composable
private fun SensorCard(s: SensorEntry, modifier: Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 180f else 0f, spring(), label = "chevron")
    GlassCard(modifier.fillMaxWidth(), accent = if (open) Accents.battery else null, padding = 16.dp) {
        Row(Modifier.fillMaxWidth().bouncyClick { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.kind, style = MaterialTheme.typography.titleMedium)
                Text("${s.name} · ${s.vendor}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Icon(Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.graphicsLayer { rotationZ = turn })
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(top = 12.dp)) {
                SensorLive(s)
                InfoRow("Range", "${trim(s.maxRange)} ${s.unit}".trim())
                InfoRow("Power", "${trim(s.powerMa)} mA")
                if (s.wakeUp) InfoRow("Wakes the phone", "Yes")
            }
        }
    }
}

private val AXIS = listOf(Accents.download, Accents.upload, Accents.battery)

/** Live readings of one sensor while its card is open. */
@Composable
private fun SensorLive(s: SensorEntry) {
    val context = LocalContext.current
    val histories = remember { List(3) { mutableStateListOf<Float>() } }
    var latest by remember { mutableStateOf(FloatArray(0)) }
    var sampledAt by remember { mutableStateOf(0L) }
    DisposableEffect(s.type, s.name) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getSensorList(Sensor.TYPE_ALL)?.firstOrNull { it.type == s.type && it.name == s.name }
        var last = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val now = android.os.SystemClock.uptimeMillis()
                if (now - last < 100) return
                last = now
                latest = event.values.copyOf()
                sampledAt = now
                for (axis in 0 until s.axes.coerceAtMost(event.values.size)) {
                    val h = histories[axis]
                    h += event.values[axis]
                    if (h.size > 80) h.removeAt(0)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) sm?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm?.unregisterListener(listener) }
    }
    if (latest.isEmpty()) {
        Text(
            if (s.type == Sensor.TYPE_SIGNIFICANT_MOTION || s.type == Sensor.TYPE_STEP_DETECTOR) "Reports only when it's triggered." else "Waiting for a reading…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val axes = s.axes.coerceAtMost(latest.size)
    Row(Modifier.fillMaxWidth()) {
        val names = if (axes == 3) listOf("X", "Y", "Z") else listOf("Value")
        for (a in 0 until axes) {
            Column(Modifier.weight(1f)) {
                Text(names[a], style = MaterialTheme.typography.labelMedium, color = AXIS[a].start)
                Text("${trim(latest[a])} ${s.unit}".trim(), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    // Shift readings up so negative values (e.g. tilting) still fit the chart.
    val all = (0 until axes).flatMap { histories[it] }
    val low = (all.minOrNull() ?: 0f).coerceAtMost(0f)
    LiveChart(
        (0 until axes).map { a -> ChartLine(histories[a].map { it - low }, AXIS[a], fill = axes == 1) },
        sampledAt,
        100,
        Modifier.fillMaxWidth().height(110.dp),
        visible = 80,
    )
    Spacer(Modifier.height(8.dp))
}

private fun trim(v: Float): String = when {
    v == v.roundToInt().toFloat() && kotlin.math.abs(v) < 1e7 -> v.roundToInt().toString()
    kotlin.math.abs(v) >= 100 -> String.format(Locale.US, "%.1f", v)
    else -> String.format(Locale.US, "%.2f", v)
}
