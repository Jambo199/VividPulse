package com.jambo199.vividpulse.ui.live

import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jambo199.vividpulse.Graph
import com.jambo199.vividpulse.system.BatterySample
import com.jambo199.vividpulse.system.LiveState
import com.jambo199.vividpulse.system.MemInfo
import com.jambo199.vividpulse.ui.Routes
import com.jambo199.vividpulse.ui.TabBottomPadding
import com.jambo199.vividpulse.ui.components.AmbientGlow
import com.jambo199.vividpulse.ui.components.AnimatedNumber
import com.jambo199.vividpulse.ui.components.BarMeter
import com.jambo199.vividpulse.ui.components.CardTitle
import com.jambo199.vividpulse.ui.components.ChartLine
import com.jambo199.vividpulse.ui.components.GlassCard
import com.jambo199.vividpulse.ui.components.LiquidBattery
import com.jambo199.vividpulse.ui.components.LiveChart
import com.jambo199.vividpulse.ui.components.LiveDot
import com.jambo199.vividpulse.ui.components.RingGauge
import com.jambo199.vividpulse.ui.components.RoundButton
import com.jambo199.vividpulse.ui.components.Sparkline
import com.jambo199.vividpulse.ui.components.VerticalMeter
import com.jambo199.vividpulse.ui.components.entrance
import com.jambo199.vividpulse.ui.components.sharedCard
import com.jambo199.vividpulse.ui.rememberSpecs
import com.jambo199.vividpulse.ui.theme.Accents
import com.jambo199.vividpulse.ui.theme.Numbers
import com.jambo199.vividpulse.util.Format
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun LiveScreen(open: (String) -> Unit) {
    val live by Graph.monitor.state.collectAsStateWithLifecycle()
    val settings by Graph.settings.state.collectAsStateWithLifecycle()
    val specs = rememberSpecs()
    val context = LocalContext.current
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // How long since the phone was switched on, and how much of that it spent asleep.
    val uptime by produceState(uptimeText()) {
        while (true) {
            delay(30_000)
            value = uptimeText()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AmbientGlow(listOf(Accents.cpu, Accents.memory, Accents.upload), Modifier.fillMaxWidth().height(420.dp))
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 8.dp, bottom = TabBottomPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 6.dp).entrance(0), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LiveDot(Accents.battery.start)
                            Spacer(Modifier.width(6.dp))
                            Text("LIVE", style = MaterialTheme.typography.labelLarge, color = Accents.battery.start)
                        }
                        Text(
                            specs?.name ?: "Your phone",
                            style = MaterialTheme.typography.headlineLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            specs?.summary ?: " ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                        Text(
                            uptime,
                            style = MaterialTheme.typography.labelMedium.merge(Numbers),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    RoundButton(Icons.Rounded.Settings, "Settings", { open(Routes.SETTINGS) })
                }
            }
            item { CpuHero(live, Modifier.entrance(1)) { open(Routes.CPU) } }
            item {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max).entrance(2), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MemoryCard(live, Modifier.weight(1f).fillMaxHeight()) { open(Routes.MEMORY) }
                    BatteryCard(live?.battery ?: BatterySample.EMPTY, settings.fahrenheit, Modifier.weight(1f).fillMaxHeight()) { open(Routes.BATTERY) }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max).entrance(3), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GpuCard(live, specs?.gpu?.renderer, Modifier.weight(1f).fillMaxHeight()) { open(Routes.CPU) }
                    ThermalCard(live, settings.fahrenheit, Modifier.weight(1f).fillMaxHeight()) { open(Routes.THERMAL) }
                }
            }
            item { NetworkCard(live, Modifier.entrance(4)) { open(Routes.NETWORK) } }
            item {
                StorageCard(live, Modifier.entrance(5)) {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
        }
    }
}

@Composable
private fun CpuHero(live: LiveState?, modifier: Modifier, onClick: () -> Unit) {
    val cpu = live?.cpu
    val load = cpu?.load ?: 0f
    val cores = Graph.monitor.cpu.cores
    val fastest = cpu?.curKHz?.filterNotNull()?.maxOrNull()
    GlassCard(modifier.fillMaxWidth().sharedCard("card-cpu"), accent = Accents.cpu, onClick = onClick) {
        CardTitle(Icons.Rounded.DeveloperBoard, "Processor", Accents.cpu, trailing = "${cores.size} cores")
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RingGauge(load, Accents.cpu, Modifier.size(128.dp), thickness = 11.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedNumber(load * 100, { "${it.roundToInt()}%" }, MaterialTheme.typography.headlineMedium)
                    Text(if (cpu?.measured == true) "usage" else "activity", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                LiveChart(
                    listOf(ChartLine(live?.cpuHistory.orEmpty(), Accents.cpu)),
                    live?.sampledAt ?: 0L,
                    live?.intervalMs ?: 1000L,
                    Modifier.fillMaxWidth().height(64.dp),
                    max = 1f,
                    visible = 40,
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().height(34.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    val loads = cpu?.coreLoad.orEmpty()
                    cores.forEachIndexed { i, _ ->
                        val l = loads.getOrNull(i) ?: 0f
                        VerticalMeter(l, Accents.load(l), Modifier.width(10.dp).fillMaxHeight())
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (fastest != null) "Fastest core now ${Format.mhz(fastest / 1000)} of ${Format.mhz(Graph.monitor.cpu.maxKHz / 1000)}" else "Clock speeds are hidden on this phone",
            style = MaterialTheme.typography.bodySmall.merge(Numbers),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MemoryCard(live: LiveState?, modifier: Modifier, onClick: () -> Unit) {
    val m = live?.memory
    GlassCard(modifier.sharedCard("card-memory"), accent = Accents.memory, onClick = onClick) {
        CardTitle(Icons.Rounded.Memory, "RAM", Accents.memory)
        Spacer(Modifier.height(10.dp))
        RingGauge(m?.usedFraction ?: 0f, Accents.memory, Modifier.size(104.dp).align(Alignment.CenterHorizontally), thickness = 9.dp) {
            AnimatedNumber((m?.usedFraction ?: 0f) * 100, { "${it.roundToInt()}%" }, MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (m != null && m.total > 0) "${Format.bytes(m.used)} of ${MemInfo.marketedRamGb(m.total)} GB" else "—",
            style = MaterialTheme.typography.bodyMedium.merge(Numbers),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(6.dp))
        Sparkline(live?.memoryHistory.orEmpty().takeLast(40), Accents.memory, Modifier.fillMaxWidth().height(22.dp), max = 1f)
    }
}

@Composable
private fun BatteryCard(b: BatterySample, fahrenheit: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val accent = if (b.charging) Accents.charging else Accents.battery
    GlassCard(modifier.sharedCard("card-battery"), accent = accent, onClick = onClick) {
        CardTitle(Icons.Rounded.BatteryChargingFull, "Battery", accent)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiquidBattery(b.level, b.charging, Modifier.width(46.dp).height(84.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                AnimatedNumber(b.level, { "${it.roundToInt()}%" }, MaterialTheme.typography.headlineMedium)
                Text(b.statusLabel.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Spacer(Modifier.height(10.dp))
        val w = b.watts
        Text(
            listOfNotNull(
                w?.let { (if (it >= 0) "+" else "−") + Format.watts(abs(it)) },
                b.temperatureC?.let { Format.temperature(it, fahrenheit) },
            ).joinToString(" · ").ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium.merge(Numbers),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun GpuCard(live: LiveState?, renderer: String?, modifier: Modifier, onClick: () -> Unit) {
    val gpu = live?.gpu
    GlassCard(modifier.sharedCard("card-gpu"), accent = Accents.gpu, onClick = onClick) {
        CardTitle(Icons.Rounded.ViewInAr, "Graphics", Accents.gpu)
        Spacer(Modifier.height(10.dp))
        if (gpu?.load != null) {
            RingGauge(gpu.load, Accents.gpu, Modifier.size(96.dp).align(Alignment.CenterHorizontally), thickness = 9.dp) {
                AnimatedNumber(gpu.load * 100, { "${it.roundToInt()}%" }, MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(8.dp))
            Text(gpu.clockMHz?.let { "$it MHz" } ?: renderer.orEmpty(), style = MaterialTheme.typography.bodyMedium.merge(Numbers), fontWeight = FontWeight.SemiBold)
        } else {
            Text(renderer ?: "GPU", style = MaterialTheme.typography.titleLarge, maxLines = 2)
            Spacer(Modifier.height(6.dp))
            Text(
                gpu?.clockMHz?.let { "Running at $it MHz" } ?: "This phone keeps GPU load private, so it's shown when Android allows.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ThermalCard(live: LiveState?, fahrenheit: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val t = live?.thermal
    val headroom = t?.headroom
    GlassCard(modifier.sharedCard("card-thermal"), accent = Accents.thermal, onClick = onClick) {
        CardTitle(Icons.Rounded.Thermostat, "Heat", Accents.thermal)
        Spacer(Modifier.height(10.dp))
        Text(
            t?.takeIf { it.status >= 0 }?.statusLabel ?: (if (headroom != null) heatWord(headroom) else "—"),
            style = MaterialTheme.typography.headlineMedium,
            color = Accents.load(headroom ?: (t?.status ?: 0) / 4f),
        )
        Text("before slowing down", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        BarMeter(headroom ?: 0f, Accents.thermal, Modifier.fillMaxWidth().height(10.dp))
        Spacer(Modifier.height(10.dp))
        Text(
            live?.battery?.temperatureC?.let { "Battery ${Format.temperature(it, fahrenheit)}" } ?: "—",
            style = MaterialTheme.typography.bodyMedium.merge(Numbers),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun NetworkCard(live: LiveState?, modifier: Modifier, onClick: () -> Unit) {
    val n = live?.net
    GlassCard(modifier.fillMaxWidth().sharedCard("card-network"), accent = Accents.download, onClick = onClick) {
        CardTitle(Icons.Rounded.Wifi, "Network", Accents.download, trailing = "live")
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("↓ Download", style = MaterialTheme.typography.labelMedium, color = Accents.download.start)
                AnimatedNumber((n?.rxPerSec ?: 0L).toFloat(), { Format.bitsPerSec(it.toLong()) }, MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.weight(1f)) {
                Text("↑ Upload", style = MaterialTheme.typography.labelMedium, color = Accents.upload.start)
                AnimatedNumber((n?.txPerSec ?: 0L).toFloat(), { Format.bitsPerSec(it.toLong()) }, MaterialTheme.typography.titleLarge)
            }
        }
        Spacer(Modifier.height(8.dp))
        LiveChart(
            listOf(ChartLine(live?.rxHistory.orEmpty(), Accents.download), ChartLine(live?.txHistory.orEmpty(), Accents.upload, fill = false)),
            live?.sampledAt ?: 0L,
            live?.intervalMs ?: 1000L,
            Modifier.fillMaxWidth().height(72.dp),
            visible = 45,
        )
    }
}

@Composable
private fun StorageCard(live: LiveState?, modifier: Modifier, onClick: () -> Unit) {
    val s = live?.storage
    GlassCard(modifier.fillMaxWidth(), accent = Accents.storage, onClick = onClick) {
        CardTitle(
            Icons.Rounded.SdStorage,
            "Storage",
            Accents.storage,
            trailing = if (s != null && s.total > 0) "${MemInfo.marketedStorageGb(s.total)} GB" else null,
        )
        Spacer(Modifier.height(12.dp))
        BarMeter(s?.usedFraction ?: 0f, Accents.storage, Modifier.fillMaxWidth().height(14.dp), height = 12.dp)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(
                if (s != null && s.total > 0) "${Format.bytes(s.used)} used" else "—",
                style = MaterialTheme.typography.bodyMedium.merge(Numbers),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (s != null && s.total > 0) "${Format.bytes(s.free)} free" else "",
                style = MaterialTheme.typography.bodyMedium.merge(Numbers),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun uptimeText(): String {
    val on = SystemClock.elapsedRealtime()
    val awake = SystemClock.uptimeMillis()
    val asleep = ((on - awake) * 100 / on.coerceAtLeast(1)).toInt()
    return "On for ${Format.duration(on)} · asleep $asleep% of it"
}

fun heatWord(headroom: Float): String = when {
    headroom < 0.4f -> "Cool"
    headroom < 0.7f -> "Warm"
    headroom < 1f -> "Hot"
    else -> "Throttling"
}
