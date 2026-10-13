package com.jambo199.vividpulse.ui.device

import android.content.Intent
import android.net.Uri
import com.jambo199.vividpulse.Graph
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
import com.jambo199.vividpulse.system.CameraReader
import com.jambo199.vividpulse.system.CameraReport
import com.jambo199.vividpulse.system.CameraMath
import com.jambo199.vividpulse.system.KnownPhones
import com.jambo199.vividpulse.system.PhoneSheet
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
import com.jambo199.vividpulse.ui.rememberSpecs
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
    // HDR formats as Android reports them, plus any more the phone's published specs list.
    val phone = rememberSpecs()?.name
    val sheet by produceState<PhoneSheet?>(null, phone) {
        if (phone != null) value = Graph.phoneSheets.forPhone(phone) ?: KnownPhones.sheet(phone)
    }
    val hdrAll = (spec?.hdr.orEmpty() + sheet?.displayHdr.orEmpty()).distinct()

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
                    val hdr = hdrAll
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("HDR", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Pill(if (hdr.isNotEmpty()) "Supported" else "Not supported", if (hdr.isNotEmpty()) Accents.battery else Accents.thermal, filled = hdr.isNotEmpty())
                    }
                    if (hdr.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            hdr.forEach { Pill(it, Accents.storage, filled = false) }
                        }
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth().entrance(2)) {
                    s.copy(hdr = hdrAll).rows().forEach { InfoRow(it.label, it.value) }
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

@OptIn(ExperimentalLayoutApi::class)
/** One lens on the Cameras page: "50 MP · 35 mm · Main", then sensor, aperture and stabilisation. */
private data class LensLine(val megapixels: String?, val mm: Int?, val kind: String, val details: String)

/** The back or front cameras: their lenses, then a few lines (video, HDR, zoom). */
private data class CameraGroup(val title: String, val front: Boolean, val lenses: List<LensLine>, val rows: List<Pair<String, String>>)

private fun mp(v: Float): String = if (v % 1f == 0f) "${v.toInt()} MP" else String.format(Locale.US, "%.1f MP", v)

private fun hdrRow(label: String, formats: List<String>): Pair<String, String> =
    label to if (formats.isEmpty()) "No" else "Yes · " + formats.joinToString(", ")

private fun groups(sheet: PhoneSheet): List<CameraGroup> = listOfNotNull(
    sheet.back?.let { "Back" to it },
    sheet.front?.let { "Front" to it },
).map { (side, c) ->
    CameraGroup(
        title = "$side camera" + if (c.lenses.size > 1) "s" else "",
        front = side == "Front",
        lenses = c.lenses.map { l ->
            LensLine(l.megapixels?.let(::mp), l.mm, l.kind, listOfNotNull(l.sensor, l.aperture, "OIS".takeIf { l.ois }, l.zoom).joinToString(" · "))
        },
        rows = listOfNotNull(
            c.video?.let { "Video" to it },
            hdrRow("HDR video", c.hdrVideo),
            c.hdrPhotos?.let { "HDR photos" to if (it) "Yes" else "No" },
        ),
    )
}

private fun groups(r: CameraReport): List<CameraGroup> = listOf("Back", "Front", "External").mapNotNull { facing ->
    val lenses = r.lenses.filter { it.facing == facing }.ifEmpty { return@mapNotNull null }
    val cameras = r.cameras.filter { it.facing == facing }
    val rows = listOfNotNull(
        CameraMath.videoSummary(cameras.flatMap { it.videoModes })?.let { "Video" to it },
        hdrRow("HDR video", cameras.flatMap { it.hdrVideo }.distinct()),
        cameras.mapNotNull { it.zoom }.takeIf { facing == "Back" && it.isNotEmpty() }
            ?.let { z -> "Zoom" to zoomText(z.minOf { it.start }) + " to " + zoomText(z.maxOf { it.endInclusive }) },
    )
    val title = "$facing camera" + if (lenses.size > 1) "s" else ""
    CameraGroup(
        title,
        facing == "Front",
        lenses.map { l ->
            LensLine(
                l.megapixels?.let { mp(it.roundToInt().toFloat()) },
                l.equivalentMm,
                l.kind,
                listOfNotNull(l.sensorType, l.aperture?.let { "f/$it" }, "OIS".takeIf { l.ois }).joinToString(" · "),
            )
        },
        rows,
    )
}

/**
 * The phone's cameras: the specs GSMArena lists for it (looked up once, then kept), or built into
 * the app for a few phones, or failing both, what Android shares with apps. [phoneName] looks up
 * another phone (used by the automated test).
 */
@Composable
fun CamerasScreen(back: () -> Unit, phoneName: String? = null) {
    val context = LocalContext.current
    val specs = rememberSpecs()
    val name = phoneName ?: specs?.name
    var looked by remember(name) { mutableStateOf(false) }
    val sheet by produceState<PhoneSheet?>(null, name) {
        if (name == null) return@produceState
        value = Graph.phoneSheets.forPhone(name) ?: KnownPhones.sheet(name)
        looked = true
    }
    val report by produceState<CameraReport?>(null, looked, sheet) {
        if (looked && sheet == null) value = withContext(Dispatchers.IO) { CameraReader.read(context) }
    }
    val s = sheet
    val shown: List<CameraGroup>? = when {
        s != null -> groups(s)
        looked -> report?.let { groups(it) }
        else -> null
    }
    DetailPage(
        "Cameras",
        back,
        subtitle = (s?.name ?: name)?.let { n -> listOfNotNull(n, shown?.takeIf { it.isNotEmpty() }?.joinToString(" · ") { g -> "${g.lenses.size} ${g.title.substringBefore(' ').lowercase(Locale.US)}" }).joinToString(" · ") },
    ) {
        when {
            shown == null -> items(2) { ShimmerBox(Modifier.fillMaxWidth().height(200.dp)) }
            shown.isEmpty() -> item {
                GlassCard(Modifier.fillMaxWidth().sharedCard("card-cameras")) {
                    Text("This device has no cameras that apps can see.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            else -> {
                shown.forEachIndexed { i, g ->
                    item { CameraGroupCard(g, Modifier.then(if (i == 0) Modifier.sharedCard("card-cameras") else Modifier).entrance(i)) }
                }
                item {
                    Text(
                        if (s != null) "Specs from GSMArena · open the page" else "As Android shares them with apps",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (s != null) Accents.games.start else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .then(
                                if (s != null) {
                                    Modifier.bouncyClick {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(s.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                        }
                                    }
                                } else {
                                    Modifier
                                },
                            )
                            .padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraGroupCard(g: CameraGroup, modifier: Modifier) {
    val accent = if (g.front) Accents.upload else Accents.games
    GlassCard(modifier.fillMaxWidth(), accent = accent) {
        CardTitle(Icons.Rounded.CameraAlt, g.title, accent)
        Spacer(Modifier.height(4.dp))
        g.lenses.forEach { l ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(88.dp)) {
                    Text(l.megapixels ?: "—", style = MaterialTheme.typography.titleLarge)
                    l.mm?.let { Text("$it mm", style = MaterialTheme.typography.labelMedium, color = accent.start) }
                }
                Column(Modifier.weight(1f)) {
                    Text(l.kind, style = MaterialTheme.typography.titleSmall)
                    if (l.details.isNotEmpty()) {
                        Text(l.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        g.rows.forEach { (label, value) -> InfoRow(label, value) }
    }
}

private fun zoomText(z: Float): String =
    if (z < 1f) String.format(Locale.US, "%.1f×", z) else String.format(Locale.US, "%.0f×", z)

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
