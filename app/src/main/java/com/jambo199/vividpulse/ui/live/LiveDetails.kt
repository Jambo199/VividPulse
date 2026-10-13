package com.jambo199.vividpulse.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import android.os.SystemClock
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jambo199.vividpulse.Graph
import com.jambo199.vividpulse.system.BatterySample
import com.jambo199.vividpulse.system.Cluster
import com.jambo199.vividpulse.system.Connection
import com.jambo199.vividpulse.system.LiveState
import com.jambo199.vividpulse.system.MemInfo
import com.jambo199.vividpulse.system.NetworkReader
import com.jambo199.vividpulse.system.PowerSession
import com.jambo199.vividpulse.ui.Routes
import com.jambo199.vividpulse.ui.components.AnimatedNumber
import com.jambo199.vividpulse.ui.components.BarMeter
import com.jambo199.vividpulse.ui.components.CardTitle
import com.jambo199.vividpulse.ui.components.ChartLine
import com.jambo199.vividpulse.ui.components.DetailPage
import com.jambo199.vividpulse.ui.components.GlassCard
import com.jambo199.vividpulse.ui.components.InfoRow
import com.jambo199.vividpulse.ui.components.LiquidBattery
import com.jambo199.vividpulse.ui.components.LiveChart
import com.jambo199.vividpulse.ui.components.Pill
import com.jambo199.vividpulse.ui.components.RingGauge
import com.jambo199.vividpulse.ui.components.Sparkline
import com.jambo199.vividpulse.ui.components.Stat
import com.jambo199.vividpulse.ui.components.entrance
import com.jambo199.vividpulse.ui.components.sharedCard
import com.jambo199.vividpulse.ui.rememberSpecs
import com.jambo199.vividpulse.ui.theme.Accent
import com.jambo199.vividpulse.ui.theme.Accents
import com.jambo199.vividpulse.ui.theme.LocalPulseColors
import com.jambo199.vividpulse.ui.theme.Numbers
import com.jambo199.vividpulse.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun CpuScreen(back: () -> Unit) {
    val live by Graph.monitor.state.collectAsStateWithLifecycle()
    val specs = rememberSpecs()
    val reader = Graph.monitor.cpu
    val cpu = live?.cpu
    DetailPage("Processor", back, subtitle = specs?.socName ?: "CPU and GPU") {
        item {
            GlassCard(Modifier.fillMaxWidth().sharedCard("card-cpu"), accent = Accents.cpu) {
                CardTitle(Icons.Rounded.DeveloperBoard, specs?.socName ?: "CPU", Accents.cpu, trailing = "${reader.cores.size} cores")
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RingGauge(cpu?.load ?: 0f, Accents.cpu, Modifier.size(150.dp), thickness = 13.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AnimatedNumber((cpu?.load ?: 0f) * 100, { "${it.roundToInt()}%" }, MaterialTheme.typography.displaySmall)
                            Text(if (cpu?.measured == true) "usage" else "activity", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val fastest = cpu?.curKHz?.filterNotNull()?.maxOrNull()
                        Stat("Fastest core", fastest?.let { Format.mhz(it / 1000) } ?: "—")
                        Stat("Top speed", reader.maxKHz.takeIf { it > 0 }?.let { Format.mhz(it / 1000) } ?: "—")
                        Stat("Cores awake", "${cpu?.curKHz?.count { it != null } ?: 0} of ${reader.cores.size}")
                    }
                }
                Spacer(Modifier.height(14.dp))
                LiveChart(listOf(ChartLine(live?.cpuHistory.orEmpty(), Accents.cpu)), live?.sampledAt ?: 0, live?.intervalMs ?: 1000, Modifier.fillMaxWidth().height(130.dp), max = 1f, visible = 90)
                if (cpu?.measured != true) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Android doesn't let apps see true CPU usage, so activity is worked out from how fast each core is running: cores speed up the moment there's work to do.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        reader.clusters.forEachIndexed { i, cluster ->
            item { ClusterCard(cluster, live, Modifier.entrance(i + 1)) }
        }
        item {
            val gpu = live?.gpu
            GlassCard(Modifier.fillMaxWidth().sharedCard("card-gpu").entrance(4), accent = Accents.gpu) {
                CardTitle(Icons.Rounded.ViewInAr, specs?.gpu?.renderer ?: "Graphics", Accents.gpu)
                if (gpu?.load != null) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RingGauge(gpu.load, Accents.gpu, Modifier.size(96.dp), thickness = 9.dp) {
                            AnimatedNumber(gpu.load * 100, { "${it.roundToInt()}%" }, MaterialTheme.typography.titleLarge)
                        }
                        Spacer(Modifier.width(12.dp))
                        LiveChart(listOf(ChartLine(live?.gpuHistory.orEmpty(), Accents.gpu)), live?.sampledAt ?: 0, live?.intervalMs ?: 1000, Modifier.weight(1f).height(90.dp), max = 1f)
                    }
                }
                Spacer(Modifier.height(8.dp))
                gpu?.clockMHz?.let { InfoRow("Clock now", "$it MHz" + (Graph.monitor.gpu.maxClockMHz?.let { m -> " of $m MHz" } ?: "")) }
                specs?.gpu?.let {
                    InfoRow("Maker", it.vendor)
                    InfoRow("OpenGL", it.glVersion.substringBefore(" (").take(40))
                }
                specs?.vulkan?.let { InfoRow("Vulkan", it) }
                if (gpu?.load == null) {
                    Text(
                        "This phone keeps GPU load private from apps.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth().entrance(5)) {
                specs?.socModel?.let { InfoRow("Chip model", it) }
                reader.hardware?.let { InfoRow("Hardware", it) }
                InfoRow("Instruction sets", android.os.Build.SUPPORTED_ABIS.joinToString())
                reader.clusters.firstOrNull()?.governor?.let { InfoRow("Speed governor", it) }
            }
        }
    }
}

@Composable
private fun ClusterCard(cluster: Cluster, live: LiveState?, modifier: Modifier) {
    val accent = when (cluster.index) {
        0 -> Accents.gpu
        1 -> Accents.cpu
        else -> Accents.battery
    }
    GlassCard(modifier.fillMaxWidth(), accent = accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(cluster.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    "${cluster.cores.size} × ${cluster.part ?: "core"}" + (cluster.maxKHz.takeIf { it > 0 }?.let { " · up to ${Format.mhz(it / 1000)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            cluster.governor?.let { Pill(it, accent, filled = false) }
        }
        Spacer(Modifier.height(12.dp))
        val perRow = if (cluster.cores.size % 3 == 0 && cluster.cores.size > 2) 3 else 2
        cluster.cores.chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { core -> CoreTile(core, live, accent, Modifier.weight(1f)) }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CoreTile(core: Int, live: LiveState?, accent: Accent, modifier: Modifier) {
    val cur = live?.cpu?.curKHz?.getOrNull(core)
    val load = live?.cpu?.coreLoad?.getOrNull(core) ?: 0f
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(LocalPulseColors.current.track.copy(alpha = 0.35f))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("CPU $core", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(if (cur == null) LocalPulseColors.current.faint else Accents.load(load)))
        }
        if (cur != null) {
            AnimatedNumber(cur / 1000f, { "${it.roundToInt()}" }, MaterialTheme.typography.titleLarge)
            Text("MHz", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("Asleep", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(" ", style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(6.dp))
        Sparkline(live?.coreHistory?.getOrNull(core).orEmpty().takeLast(30), accent, Modifier.fillMaxWidth().height(20.dp), max = 1f)
    }
}

@Composable
fun MemoryScreen(back: () -> Unit, open: (String) -> Unit) {
    val live by Graph.monitor.state.collectAsStateWithLifecycle()
    val m = live?.memory
    DetailPage("RAM", back, subtitle = m?.takeIf { it.total > 0 }?.let { "${MemInfo.marketedRamGb(it.total)} GB memory" }) {
        item {
            GlassCard(Modifier.fillMaxWidth().sharedCard("card-memory"), accent = Accents.memory) {
                CardTitle(Icons.Rounded.Memory, "In use", Accents.memory)
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RingGauge(m?.usedFraction ?: 0f, Accents.memory, Modifier.size(150.dp), thickness = 13.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AnimatedNumber((m?.usedFraction ?: 0f) * 100, { "${it.roundToInt()}%" }, MaterialTheme.typography.displaySmall)
                            Text("used", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Stat("Used", m?.let { Format.bytes(it.used) } ?: "—")
                        Stat("Available", m?.let { Format.bytes(it.available) } ?: "—", color = Accents.battery.start)
                        Stat("Usable total", m?.let { Format.bytes(it.total) } ?: "—")
                    }
                }
                Spacer(Modifier.height(14.dp))
                LiveChart(listOf(ChartLine(live?.memoryHistory.orEmpty(), Accents.memory)), live?.sampledAt ?: 0, live?.intervalMs ?: 1000, Modifier.fillMaxWidth().height(120.dp), max = 1f, visible = 90)
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth().entrance(1), accent = Accents.memory, onClick = { open(Routes.FREE_RAM) }) {
                CardTitle(Icons.Rounded.CleaningServices, "Free up RAM", Accents.memory, trailing = "›")
                Text(
                    "Ask Android to close apps you haven't used in a while and make room, and watch what it does, step by step.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        if (m != null && m.total > 0) {
            item {
                GlassCard(Modifier.fillMaxWidth().entrance(1)) {
                    Text("Where it goes", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    MemoryBar("Apps and system", (m.used).toFloat() / m.total, Format.bytes(m.used), Accents.memory)
                    MemoryBar("Cached (freed when needed)", (m.cached + m.buffers).toFloat() / m.total, Format.bytes(m.cached + m.buffers), Accents.cpu)
                    MemoryBar("Free", m.free.toFloat() / m.total, Format.bytes(m.free), Accents.battery)
                    if (m.lowMemory) {
                        Spacer(Modifier.height(6.dp))
                        Pill("Running low: Android is closing background apps", Accents.thermal)
                    }
                }
            }
            if (m.swapTotal > 0) {
                item {
                    GlassCard(Modifier.fillMaxWidth().entrance(2), accent = Accents.memory) {
                        Text("Extended RAM", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Compressed and on-storage memory the phone adds on top of its RAM (vivo calls it RAM+).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        MemoryBar("In use", m.swapUsed.toFloat() / m.swapTotal, "${Format.bytes(m.swapUsed)} of ${Format.bytes(m.swapTotal)}", Accents.upload)
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth().entrance(3)) {
                    Text(
                        "Android keeps apps you've used in memory so they open instantly, so seeing RAM mostly used is normal. \"Available\" is what can be handed to a new app straight away.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    InfoRow("Low-memory line", Format.bytes(m.threshold))
                }
            }
        }
    }
}

@Composable
private fun MemoryBar(label: String, fraction: Float, value: String, accent: Accent) {
    Column(Modifier.padding(bottom = 12.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium.merge(Numbers), fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        BarMeter(fraction, accent, Modifier.fillMaxWidth().height(10.dp))
    }
}

@Composable
fun BatteryScreen(back: () -> Unit, turnOnCompanion: Boolean = false) {
    val live by Graph.monitor.state.collectAsStateWithLifecycle()
    val settings by Graph.settings.state.collectAsStateWithLifecycle()
    val b = live?.battery ?: BatterySample.EMPTY
    val design = Graph.monitor.battery.designCapacityMah
    val accent = if (b.charging) Accents.charging else Accents.battery
    DetailPage("Battery", back, subtitle = b.statusLabel) {
        item {
            GlassCard(Modifier.fillMaxWidth().sharedCard("card-battery"), accent = accent) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiquidBattery(b.level, b.charging, Modifier.width(80.dp).height(150.dp))
                    Spacer(Modifier.width(20.dp))
                    Column {
                        AnimatedNumber(b.level, { "${it.roundToInt()}%" }, MaterialTheme.typography.displayMedium)
                        Text(
                            listOfNotNull(b.statusLabel, b.plug).distinct().joinToString(" · "),
                            style = MaterialTheme.typography.titleMedium,
                            color = accent.start,
                        )
                        b.timeToFullMs?.let {
                            Text("Full in about ${Format.duration(it)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item {
            val w = b.watts
            GlassCard(Modifier.fillMaxWidth().entrance(1), accent = accent) {
                CardTitle(Icons.Rounded.Bolt, if (b.charging) "Charging power" else "Power use", accent, trailing = "live")
                Spacer(Modifier.height(8.dp))
                AnimatedNumber(abs(w ?: 0f), { if (w == null) "—" else Format.watts(it) }, MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(8.dp))
                LiveChart(
                    listOf(ChartLine(live?.powerHistory.orEmpty().map { abs(it) }, accent)),
                    live?.sampledAt ?: 0, live?.intervalMs ?: 1000,
                    Modifier.fillMaxWidth().height(120.dp),
                    visible = 90,
                )
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth().entrance(2), accent = Accents.thermal) {
                CardTitle(Icons.Rounded.Thermostat, "Temperature", Accents.thermal)
                Spacer(Modifier.height(8.dp))
                AnimatedNumber(b.temperatureC ?: 0f, { if (b.temperatureC == null) "—" else Format.temperature(it, settings.fahrenheit) }, MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(8.dp))
                LiveChart(
                    listOf(ChartLine(live?.batteryTempHistory.orEmpty(), Accents.thermal)),
                    live?.sampledAt ?: 0, live?.intervalMs ?: 1000,
                    Modifier.fillMaxWidth().height(100.dp),
                    max = 55f,
                    visible = 90,
                )
            }
        }
        live?.session?.let { s ->
            item { SessionCard(s, Modifier.entrance(3)) }
        }
        item { ChargingCompanionCard(b.cycles, Modifier.entrance(3), turnOn = turnOnCompanion) }
        item { RecentChargesCard(Modifier.entrance(3)) }
        item {
            GlassCard(Modifier.fillMaxWidth().entrance(4)) {
                Text("Details", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                InfoRow("Current", b.currentMa?.let { "${it.roundToInt()} mA" } ?: "Not shared")
                InfoRow("Voltage", b.voltageMv?.let { String.format(java.util.Locale.US, "%.2f V", it / 1000f) } ?: "—")
                InfoRow("Health", b.healthLabel)
                b.cycles?.let { InfoRow("Charge cycles", it.toString()) }
                b.technology?.let { InfoRow("Type", it) }
                design?.let { InfoRow("Design capacity", "${Format.grouped(it.toLong())} mAh") }
                b.estimatedFullMah?.let { full ->
                    val health = design?.let { d -> " (${(full * 100f / d).roundToInt().coerceAtMost(110)}%)" }.orEmpty()
                    InfoRow("Holds now (estimate)", "${Format.grouped(full.toLong())} mAh$health")
                }
                b.chargeCounterMah?.let { InfoRow("Charge left", "${Format.grouped(it.toLong())} mAh") }
            }
        }
    }
}

/** How this charge (or this stretch on battery) is going. */
@Composable
private fun SessionCard(s: PowerSession, modifier: Modifier) {
    val now by produceState(SystemClock.elapsedRealtime()) {
        while (true) {
            delay(1000)
            value = SystemClock.elapsedRealtime()
        }
    }
    val accent = if (s.charging) Accents.charging else Accents.battery
    val change = s.levelChange.roundToInt()
    GlassCard(modifier.fillMaxWidth(), accent = accent) {
        CardTitle(
            if (s.charging) Icons.Rounded.Bolt else Icons.Rounded.BatteryStd,
            if (s.charging) "This charge" else "Since unplugged",
            accent,
            trailing = if (s.seenStart) null else "since the app opened",
        )
        Spacer(Modifier.height(10.dp))
        Row {
            Stat("Time", Format.duration(now - s.startedAt), Modifier.weight(1f))
            Stat(
                if (s.charging) "Gained" else "Used",
                "${if (s.charging) "+" else "−"}${kotlin.math.abs(change)}%",
                Modifier.weight(1f),
                color = accent.start,
            )
            Stat(
                if (s.charging) "Speed" else "Drain",
                s.percentPerHour(now)?.let { "${it.roundToInt()}%/h" } ?: "—",
                Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row {
            Stat("Peak power", if (s.peakWatts > 0) Format.watts(s.peakWatts) else "—", Modifier.weight(1f))
            Stat(if (s.charging) "Energy in" else "Energy used", String.format(java.util.Locale.US, "%.1f Wh", s.energyWh), Modifier.weight(1f))
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
fun NetworkScreen(back: () -> Unit, open: (String) -> Unit) {
    val live by Graph.monitor.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val connection by produceState<Connection?>(null) {
        val reader = NetworkReader(context.applicationContext)
        while (true) {
            value = withContext(Dispatchers.IO) { runCatching { reader.connection() }.getOrNull() }
            delay(3000)
        }
    }
    val n = live?.net
    DetailPage("Network", back, subtitle = connection?.let { listOfNotNull(it.kind, it.name).joinToString(" · ") }) {
        item {
            GlassCard(Modifier.fillMaxWidth().sharedCard("card-network"), accent = Accents.download) {
                CardTitle(Icons.Rounded.Wifi, "Right now", Accents.download, trailing = "live")
                Spacer(Modifier.height(10.dp))
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("↓ Download", style = MaterialTheme.typography.labelMedium, color = Accents.download.start)
                        AnimatedNumber((n?.rxPerSec ?: 0L).toFloat(), { Format.bitsPerSec(it.toLong()) }, MaterialTheme.typography.headlineSmall)
                        Text(Format.rate(n?.rxPerSec ?: 0), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("↑ Upload", style = MaterialTheme.typography.labelMedium, color = Accents.upload.start)
                        AnimatedNumber((n?.txPerSec ?: 0L).toFloat(), { Format.bitsPerSec(it.toLong()) }, MaterialTheme.typography.headlineSmall)
                        Text(Format.rate(n?.txPerSec ?: 0), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(10.dp))
                LiveChart(
                    listOf(ChartLine(live?.rxHistory.orEmpty(), Accents.download), ChartLine(live?.txHistory.orEmpty(), Accents.upload, fill = false)),
                    live?.sampledAt ?: 0, live?.intervalMs ?: 1000,
                    Modifier.fillMaxWidth().height(150.dp),
                    visible = 90,
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    Stat("Downloaded since start", Format.bytes(n?.totalRx ?: 0), Modifier.weight(1f))
                    Stat("Uploaded", Format.bytes(n?.totalTx ?: 0), Modifier.weight(1f))
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth().entrance(1), accent = Accents.games, onClick = { open(Routes.SPEED) }) {
                CardTitle(Icons.Rounded.Speed, "Test my internet speed", Accents.games, trailing = "›")
                Text(
                    "Measures download, upload and response time in about 25 seconds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        connection?.let { c ->
            item {
                GlassCard(Modifier.fillMaxWidth().entrance(2)) {
                    CardTitle(Icons.Rounded.NetworkCheck, c.kind, Accents.download, trailing = c.bars?.let { "▂▄▆█".take(it.coerceIn(0, 4)).ifEmpty { "▁" } })
                    Spacer(Modifier.height(4.dp))
                    c.name?.let { InfoRow("Name", it) }
                    c.details.forEach { (k, v) -> InfoRow(k, v) }
                    if (c.kind == "Wi-Fi" && c.name == null) {
                        Text(
                            "Android only shares the Wi-Fi network's name with apps that have location access, which this app doesn't ask for.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThermalScreen(back: () -> Unit) {
    val live by Graph.monitor.state.collectAsStateWithLifecycle()
    val settings by Graph.settings.state.collectAsStateWithLifecycle()
    val t = live?.thermal
    val headroom = t?.headroom
    DetailPage("Heat", back, subtitle = "How close the phone is to slowing down") {
        item {
            GlassCard(Modifier.fillMaxWidth().sharedCard("card-thermal"), accent = Accents.thermal) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RingGauge(headroom ?: 0f, Accents.thermal, Modifier.size(150.dp), thickness = 13.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                t?.takeIf { it.status >= 0 }?.statusLabel ?: headroom?.let { heatWord(it) } ?: "—",
                                style = MaterialTheme.typography.headlineSmall,
                                color = Accents.load(headroom ?: 0f),
                            )
                            headroom?.let {
                                Text("${(it * 100).roundToInt()}% of limit", style = MaterialTheme.typography.labelSmall.merge(Numbers), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Stat("Battery", live?.battery?.temperatureC?.let { Format.temperature(it, settings.fahrenheit) } ?: "—")
                        Stat("Android says", t?.statusLabel ?: "—")
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (headroom != null) {
                    LiveChart(listOf(ChartLine(live?.headroomHistory.orEmpty(), Accents.thermal)), live?.sampledAt ?: 0, live?.intervalMs ?: 1000, Modifier.fillMaxWidth().height(110.dp), max = 1f, visible = 90)
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    "At 100% the phone starts lowering its speed to cool down, which is when games drop frames. Android forecasts this from all the phone's temperature sensors.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val zones = t?.zones.orEmpty()
        if (zones.isNotEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth().entrance(1)) {
                    Text("Sensors", style = MaterialTheme.typography.titleMedium)
                    zones.forEach { (name, c) -> InfoRow(name, Format.temperature(c, settings.fahrenheit)) }
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth().entrance(2)) {
                Text("Keeping cool while gaming", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                listOf(
                    "Take off a thick case for long sessions.",
                    "Fast charging while playing heats the battery the most; play unplugged or use a slower charger.",
                    "Lower the game's frame rate cap a step if the phone gets hot: it often holds steadier.",
                    "Bright sun and screen brightness at full both add heat.",
                ).forEach {
                    Text("•  $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp))
                }
            }
        }
    }
}
