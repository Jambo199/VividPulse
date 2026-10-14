package com.jambo199.vividpulse.ui.pc

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Monitor
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jambo199.vividpulse.Graph
import com.jambo199.vividpulse.games.Steam
import com.jambo199.vividpulse.pc.LibraryEntry
import com.jambo199.vividpulse.pc.PcDownload
import com.jambo199.vividpulse.pc.PcDownloads
import com.jambo199.vividpulse.pc.PcClient
import com.jambo199.vividpulse.pc.PcLibrary
import com.jambo199.vividpulse.pc.PcFound
import com.jambo199.vividpulse.pc.PcLive
import com.jambo199.vividpulse.pc.PcPairing
import com.jambo199.vividpulse.pc.PcRepository
import com.jambo199.vividpulse.pc.PcSpecs
import com.jambo199.vividpulse.ui.Routes
import com.jambo199.vividpulse.ui.TabBottomPadding
import com.jambo199.vividpulse.ui.components.AmbientGlow
import com.jambo199.vividpulse.ui.components.AnimatedNumber
import com.jambo199.vividpulse.ui.components.BarMeter
import com.jambo199.vividpulse.ui.components.CardTitle
import com.jambo199.vividpulse.ui.components.GlassCard
import com.jambo199.vividpulse.ui.components.IconBadge
import com.jambo199.vividpulse.ui.components.InfoRow
import com.jambo199.vividpulse.ui.components.LiveDot
import com.jambo199.vividpulse.ui.components.PillButton
import com.jambo199.vividpulse.ui.components.RingGauge
import com.jambo199.vividpulse.ui.components.SectionHeader
import com.jambo199.vividpulse.ui.components.ShimmerBox
import com.jambo199.vividpulse.ui.components.Sparkline
import com.jambo199.vividpulse.ui.components.Stat
import com.jambo199.vividpulse.ui.components.VerticalMeter
import com.jambo199.vividpulse.ui.components.bouncyClick
import com.jambo199.vividpulse.ui.components.entrance
import com.jambo199.vividpulse.ui.theme.Accent
import com.jambo199.vividpulse.ui.theme.Accents
import com.jambo199.vividpulse.ui.theme.LocalPulseColors
import com.jambo199.vividpulse.ui.theme.Numbers
import com.jambo199.vividpulse.util.Format
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

private enum class Shelf(val label: String) { PURCHASED("Purchased"), INSTALLED("Installed") }

/** What [CodeDialog] is asking for: a found PC's code, or an address and code typed in. */
private data class Asking(val name: String?, val host: String)

/**
 * The PC tab: your computer's specs, how hard it's working, and your Steam library, from Vivid
 * Pulse PC running on it. [pairHost] and [pairCode] pair straight away (used by the automated test).
 */
@Composable
fun PcScreen(open: (String) -> Unit, pairHost: String? = null, pairCode: String? = null) {
    val vm: PcViewModel = viewModel()
    val pairing by vm.pairing.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val setup by vm.setup.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(pairHost, pairCode) {
        if (pairHost != null && pairCode != null) vm.pair(pairHost, pairCode)
    }
    LaunchedEffect(pairing?.host, pairing?.code) {
        if (pairing != null) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.watch() }
    }
    var asking by remember { mutableStateOf<Asking?>(null) }
    var shelf by rememberSaveable { mutableStateOf(Shelf.PURCHASED) }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize()) {
        AmbientGlow(listOf(Accents.cpu, Accents.gpu, Accents.games), Modifier.fillMaxWidth().height(380.dp))
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 8.dp, bottom = TabBottomPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.padding(start = 4.dp, bottom = 4.dp).entrance(0)) {
                    Text("PC", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        if (pairing == null) "Your computer, on your phone" else "Specs, live usage and your Steam library",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val p = pairing
            if (p == null) {
                item { SetupCard(Modifier.entrance(1)) }
                item {
                    FindCard(
                        setup,
                        onSearch = vm::search,
                        onPick = { asking = Asking(it.name, address(it.host, it.port)) },
                        onManual = { asking = Asking(null, "") },
                        modifier = Modifier.entrance(2),
                    )
                }
            } else {
                pcPage(p, state, vm, open, shelf, { shelf = it }, onNewCode = { asking = Asking(p.name, address(p.host, p.port)) })
            }
        }
    }

    asking?.let { a ->
        CodeDialog(
            asking = a,
            busy = setup.pairing,
            error = setup.error,
            onPair = { host, code -> vm.pair(host, code) { ok -> if (ok) asking = null } },
            onDismiss = {
                asking = null
                vm.clearError()
            },
        )
    }
}

private fun LazyListScope.pcPage(
    p: PcPairing,
    state: PcState,
    vm: PcViewModel,
    open: (String) -> Unit,
    shelf: Shelf,
    onShelf: (Shelf) -> Unit,
    onNewCode: () -> Unit,
) {
    item { StatusCard(p, state, onNewCode, Modifier.entrance(1)) }
    state.live?.let { live -> item { LiveCard(live, state, Modifier.entrance(2)) } }
    state.downloads?.takeIf { it.downloads.isNotEmpty() }?.let { d -> item(key = "downloads") { DownloadsCard(d, state, open) } }
    libraryItems(state, vm, p, open, shelf, onShelf)
    state.specs?.let { specs ->
        item { SectionHeader("Inside your PC", subtitle = listOfNotNull(specs.name, model(specs)).joinToString(" · ")) }
        specCards(specs)
    }
    item(key = "alerts") { PcAlertsCard(vm, gpuTempKnown = state.live?.gpu?.tempC != null, oldProgram = state.link == Link.ONLINE && state.downloads == null) }
    item {
        Text(
            "Forget this PC",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp).clip(CircleShape).bouncyClick { vm.forget() }.padding(vertical = 10.dp, horizontal = 6.dp),
        )
    }
}

// ---------------------------------------------------------------- setting up

@Composable
private fun SetupCard(modifier: Modifier) {
    val context = LocalContext.current
    val url = remember { PcRepository.downloadUrl() }
    GlassCard(modifier.fillMaxWidth(), accent = Accents.brand) {
        CardTitle(Icons.Rounded.Computer, "Connect your PC", Accents.brand)
        Text(
            "See your Windows PC's specs, how hard it's working right now, and your Steam library: the games you've bought and the ones installed. It works over your home Wi-Fi, and nothing leaves your network.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
        )
        Step(1, "On your PC, download Vivid Pulse PC", "Send yourself this link (or type it into the PC's browser):")
        Text(
            url,
            style = MaterialTheme.typography.bodySmall.merge(Numbers),
            color = Accents.brand.start,
            modifier = Modifier.padding(start = 38.dp, top = 4.dp),
        )
        Row(Modifier.padding(start = 38.dp, top = 10.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Share link", Accents.brand, { share(context, url) })
            PillButton("Copy", Accents.download, { copy(context, url) })
        }
        Step(2, "Unzip it and open VividPulsePC", "If Windows asks, choose More info › Run anyway, and allow it on private networks. It shows a 6-digit code, and stays by the clock.")
        Step(3, "Find it below", "Phone and PC need to be on the same Wi-Fi.")
    }
}

@Composable
private fun Step(n: Int, title: String, note: String) {
    Row(Modifier.padding(top = 12.dp)) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(Accents.brand.brush),
            contentAlignment = Alignment.Center,
        ) {
            Text("$n", style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FindCard(setup: PairingState, onSearch: () -> Unit, onPick: (PcFound) -> Unit, onManual: () -> Unit, modifier: Modifier) {
    GlassCard(modifier.fillMaxWidth().animateContentSize(), accent = Accents.cpu) {
        CardTitle(Icons.Rounded.Search, "Find my PC", Accents.cpu)
        Spacer(Modifier.height(12.dp))
        PillButton(if (setup.searching) "Looking…" else "Find my PC", Accents.cpu, { onSearch() }, Modifier.fillMaxWidth())
        setup.found.forEach { pc ->
            Row(
                Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(LocalPulseColors.current.track.copy(alpha = 0.5f))
                    .bouncyClick { onPick(pc) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(Icons.Rounded.Computer, Accents.cpu, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(pc.name, style = MaterialTheme.typography.titleSmall)
                    Text(pc.host, style = MaterialTheme.typography.bodySmall.merge(Numbers), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Pair", style = MaterialTheme.typography.labelLarge, color = Accents.cpu.start)
            }
        }
        if (setup.searched && !setup.searching && setup.found.isEmpty()) {
            Text(
                "No PC answered. Check Vivid Pulse PC is running and both are on the same Wi-Fi, or type in the address its window shows.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        Text(
            "Type the address instead",
            style = MaterialTheme.typography.labelLarge,
            color = Accents.cpu.start,
            modifier = Modifier.padding(top = 8.dp).clip(CircleShape).bouncyClick { onManual() }.padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun CodeDialog(asking: Asking, busy: Boolean, error: String?, onPair: (String, String) -> Unit, onDismiss: () -> Unit) {
    var host by rememberSaveable { mutableStateOf(asking.host) }
    var code by rememberSaveable { mutableStateOf("") }
    val typed = asking.name == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (typed) "Your PC's address" else "Pair with ${asking.name}") },
        text = {
            Column {
                Text(
                    if (typed) "Both are in the Vivid Pulse PC window on your PC." else "Type the 6-digit code shown in the Vivid Pulse PC window.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (typed) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it.trim() },
                        label = { Text("Address, e.g. 192.168.1.20") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }
                OutlinedTextField(
                    value = code,
                    onValueChange = { v -> code = v.filter { it.isDigit() }.take(6) },
                    label = { Text("Code") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                if (error != null) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (!busy) onPair(host, code) }, enabled = code.length == 6 && host.isNotBlank()) {
                Text(if (busy) "Checking…" else "Pair")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------- the paired PC

@Composable
private fun StatusCard(p: PcPairing, state: PcState, onNewCode: () -> Unit, modifier: Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            now = System.currentTimeMillis()
        }
    }
    val specs = state.specs
    val playing = state.live?.playingAppId?.takeIf { it > 0 && state.link == Link.ONLINE }
    GlassCard(modifier.fillMaxWidth().animateContentSize(), accent = Accents.brand) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Computer, Accents.brand, size = 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val line = specs?.let { listOfNotNull(cpuName(it.cpu.name), realGpus(it).firstOrNull()?.name).joinToString(" · ") }
                if (!line.isNullOrBlank()) {
                    Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        val (dot, text) = when (state.link) {
            Link.ONLINE -> Accents.battery.start to "Connected · updating live"
            Link.CONNECTING -> Accents.storage.start to "Connecting to ${p.host}…"
            Link.OFFLINE -> MaterialTheme.colorScheme.onSurfaceVariant to "Offline · last updated ${ago(state.lastSeen, now)}"
            Link.WRONG_CODE -> MaterialTheme.colorScheme.error to "Your PC has a new code"
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveDot(dot)
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, color = if (state.link == Link.ONLINE) Accents.battery.start else MaterialTheme.colorScheme.onSurface)
        }
        when (state.link) {
            Link.OFFLINE -> Text(
                "Vivid Pulse PC isn't answering, so this is how your PC was when it last did. It'll update by itself as soon as Vivid Pulse PC is running again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            Link.WRONG_CODE -> {
                Text(
                    "Vivid Pulse PC on ${p.name} shows a different code now. Type the new one to carry on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
                )
                PillButton("Enter the new code", Accents.brand, onNewCode)
            }
            else -> {}
        }
        if (playing != null) {
            val name = state.library?.let { (it.installed + it.purchased).firstOrNull { e -> e.appId == playing }?.name }
                ?: state.steam?.games?.firstOrNull { it.appId == playing }?.name
                ?: "a Steam game"
            Row(
                Modifier.padding(top = 12.dp).clip(RoundedCornerShape(16.dp)).background(Accents.games.start.copy(alpha = 0.14f)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.SportsEsports, contentDescription = null, tint = Accents.games.start, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Playing $name", style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiveCard(live: PcLive, state: PcState, modifier: Modifier) {
    val settings by Graph.settings.state.collectAsStateWithLifecycle()
    val fresh = state.link == Link.ONLINE
    val cpu = (live.cpuPercent ?: 0f) / 100f
    val gpu = live.gpu
    GlassCard(modifier.fillMaxWidth(), accent = Accents.cpu) {
        CardTitle(Icons.Rounded.Speed, "Right now", Accents.cpu, trailing = if (fresh) "live" else "as last seen")
        Spacer(Modifier.height(12.dp))
        Column(Modifier.alpha(if (fresh) 1f else 0.6f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                LoadRing("CPU", cpu, Accents.cpu)
                if (gpu?.loadPercent != null) LoadRing("GPU", gpu.loadPercent / 100f, Accents.gpu)
                val total = live.memoryTotalBytes
                val used = live.memoryUsedBytes
                if (total != null && used != null && total > 0) LoadRing("RAM", used.toFloat() / total, Accents.memory)
            }
            if (state.cpuHistory.size > 2) {
                Spacer(Modifier.height(10.dp))
                Sparkline(state.cpuHistory, Accents.cpu, Modifier.fillMaxWidth().height(36.dp), max = 1f)
            }
            if (live.cpuCores.size > 1) {
                Text(
                    "${live.cpuCores.size} threads",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                )
                // Slim bars however many threads there are, centred when there are few.
                BoxWithConstraints(Modifier.fillMaxWidth().height(44.dp)) {
                    val n = live.cpuCores.size
                    val gap = if (n <= 8) 8.dp else 3.dp
                    val width = ((maxWidth - gap * (n - 1)) / n).coerceAtMost(12.dp)
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally)) {
                        live.cpuCores.forEach { c -> VerticalMeter(c / 100f, Accents.cpu.start, Modifier.width(width).fillMaxHeight()) }
                    }
                }
            }
            val total = live.memoryTotalBytes
            val used = live.memoryUsedBytes
            if (total != null && used != null && total > 0) {
                Text(
                    "Memory: ${Format.bytes(used)} of ${ramGb(total)} in use",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                )
                BarMeter(used.toFloat() / total, Accents.memory, Modifier.fillMaxWidth().height(8.dp))
            }
            val stats = buildList<Pair<String, String>> {
                live.cpuMhz?.takeIf { it > 0 }?.let { add("CPU clock" to Format.mhz(it.toLong())) }
                if (gpu != null) {
                    gpu.tempC?.let { add("GPU temp" to Format.temperature(it, settings.fahrenheit)) }
                    gpu.vramUsedMb?.let { u -> add("Video memory" to (gpu.vramTotalMb?.takeIf { it >= 1024 }?.let { t -> vram(u, t) } ?: gb(u))) }
                    gpu.powerW?.let { add("GPU power" to "${it.roundToInt()} W") }
                    gpu.clockMhz?.let { add("GPU clock" to Format.mhz(it.toLong())) }
                    gpu.fanPercent?.let { add("GPU fan" to "${it.roundToInt()}%") }
                }
                live.downBytesPerSec?.let { add("Download" to Format.rate(it)) }
                live.upBytesPerSec?.let { add("Upload" to Format.rate(it)) }
                live.uptimeSec?.let { add("On for" to Format.duration(it * 1000)) }
            }
            if (stats.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = 3, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    stats.forEach { (label, value) -> Stat(label, value, Modifier.weight(1f)) }
                    repeat((3 - stats.size % 3) % 3) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun LoadRing(label: String, value: Float, accent: Accent) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RingGauge(value, accent, Modifier.size(92.dp), thickness = 8.dp) {
            AnimatedNumber(value * 100, { "${it.roundToInt()}%" }, MaterialTheme.typography.titleMedium)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------- Steam downloads and alerts

@Composable
private fun DownloadsCard(d: PcDownloads, state: PcState, open: (String) -> Unit) {
    val fresh = state.link == Link.ONLINE
    val context = LocalContext.current
    GlassCard(Modifier.fillMaxWidth(), accent = Accents.download) {
        CardTitle(Icons.Rounded.Download, "Steam downloads", Accents.download, trailing = if (fresh) "live" else "as last seen")
        Column(Modifier.alpha(if (fresh) 1f else 0.6f)) {
            d.downloads.forEach { item ->
                Row(
                    Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).bouncyClick { open(Routes.game(item.appId)) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(Steam.headerFallback(item.appId)).crossfade(200).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.width(86.dp).aspectRatio(460f / 215f).clip(RoundedCornerShape(10.dp)).background(LocalPulseColors.current.track),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.name ?: "App ${item.appId}", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            downloadLine(item),
                            style = MaterialTheme.typography.bodySmall.merge(Numbers),
                            color = if (item.state == "downloading") Accents.download.start else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                }
                item.fraction?.let { BarMeter(it, Accents.download, Modifier.fillMaxWidth().padding(top = 8.dp).height(6.dp)) }
            }
        }
    }
}

/** "Downloading · 25% · 9 of 36 GB · 12.4 MB/s · about 35 min left". */
private fun downloadLine(d: PcDownload): String {
    val pct = d.fraction?.let { "${(it * 100).roundToInt()}%" }
    return when (d.state) {
        "downloading" -> listOfNotNull(
            "Downloading",
            pct,
            if (d.bytesTotal > 0) "${Format.bytes(d.bytesDone)} of ${Format.bytes(d.bytesTotal)}" else null,
            d.bytesPerSec.takeIf { it > 0 }?.let { Format.rate(it) },
            d.secondsLeft?.let { "about ${Format.minutes(((it + 59) / 60).toInt().coerceAtLeast(1))} left" },
        ).joinToString(" · ")
        "installing" -> listOfNotNull("Installing", pct).joinToString(" · ")
        "paused" -> listOfNotNull("Paused", pct).joinToString(" · ")
        else -> listOfNotNull("Queued", d.bytesTotal.takeIf { it > 0 }?.let { Format.bytes(it) + " to download" }).joinToString(" · ")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PcAlertsCard(vm: PcViewModel, gpuTempKnown: Boolean, oldProgram: Boolean) {
    val alerts by vm.alerts.collectAsStateWithLifecycle()
    val settings by Graph.settings.state.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pending?.invoke()
        pending = null
    }
    fun turnOn(apply: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            pending = apply
            askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            apply()
        }
    }
    GlassCard(Modifier.fillMaxWidth(), accent = Accents.storage) {
        CardTitle(Icons.Rounded.NotificationsActive, "PC alerts", Accents.storage)
        AlertToggle(
            "When a download finishes",
            if (oldProgram) "Needs the new Vivid Pulse PC (the one with the icon by the clock)" else "When a Steam game finishes downloading or updating on your PC",
            alerts.downloads,
        ) { on -> if (on) turnOn { vm.setAlerts(downloads = true) } else vm.setAlerts(downloads = false) }
        AlertToggle(
            "When the graphics card runs hot",
            if (gpuTempKnown) "When it passes the temperature below" else "Works with NVIDIA graphics cards (others don't share their temperature)",
            alerts.heat,
        ) { on -> if (on) turnOn { vm.setAlerts(heat = true) } else vm.setAlerts(heat = false) }
        if (alerts.heat) {
            FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(80, 85, 90).forEach { c ->
                    val on = c == alerts.limit
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(if (on) Color.Transparent else LocalPulseColors.current.track.copy(alpha = 0.5f))
                            .background(Accents.storage.brush, alpha = if (on) 1f else 0f)
                            .bouncyClick { vm.setAlerts(limit = c) }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                    ) {
                        Text(Format.temperature(c.toFloat(), settings.fahrenheit).replace(".0", ""), style = MaterialTheme.typography.labelLarge, color = if (on) Color.White else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        Text(
            "Your phone looks every 15 minutes, and every 20 seconds while something's downloading or you're playing.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun AlertToggle(title: String, note: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(on, onChange)
    }
}

// ---------------------------------------------------------------- Steam library

private fun LazyListScope.libraryItems(state: PcState, vm: PcViewModel, p: PcPairing, open: (String) -> Unit, shelf: Shelf, onShelf: (Shelf) -> Unit) {
    val steam = state.steam
    val library = state.library
    item(key = "steam-header") {
        Row(verticalAlignment = Alignment.Bottom) {
            SectionHeader(
                "Steam library",
                Modifier.weight(1f),
                subtitle = steam?.userName?.let { "Signed in as $it" },
            )
            if (state.link == Link.ONLINE && steam?.installed == true) {
                Text(
                    if (state.libraryLoading) "Updating…" else "Refresh",
                    style = MaterialTheme.typography.labelLarge,
                    color = Accents.games.start,
                    modifier = Modifier.padding(bottom = 8.dp).clip(CircleShape).bouncyClick { vm.refreshAll(steamRefresh = true) }.padding(8.dp),
                )
            }
        }
    }
    when {
        steam?.installed == false -> item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Steam isn't installed on ${p.name}, or nobody has signed in to it yet.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        library == null -> item {
            if (state.libraryLoading || state.link != Link.OFFLINE) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(3) { ShimmerBox(Modifier.weight(1f).aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp))) }
                }
            } else {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Your library shows here once your PC is back online.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        else -> {
            item(key = "steam-shelves") { ShelfTabs(library, shelf, onShelf, p) }
            val games = if (shelf == Shelf.PURCHASED) library.purchased else library.installed
            if (games.isEmpty()) {
                item { Text("No games here yet.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 4.dp)) }
            }
            games.chunked(3).forEach { row ->
                item(key = "game-${shelf.name}-${row.first().appId}") { TileRow(row, state.link == Link.ONLINE, vm, p, open) }
            }
        }
    }
}

/** Purchased and Installed, the two Steam collections shown. */
@Composable
private fun ShelfTabs(library: PcLibrary, shelf: Shelf, onShelf: (Shelf) -> Unit, p: PcPairing) {
    Column(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Shelf.entries.forEach { s ->
                val n = if (s == Shelf.PURCHASED) library.purchased.size else library.installed.size
                val on = s == shelf
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(if (on) Color.Transparent else LocalPulseColors.current.track.copy(alpha = 0.5f))
                        .background(Accents.games.brush, alpha = if (on) 1f else 0f)
                        .bouncyClick { onShelf(s) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text("${s.label} ($n)", style = MaterialTheme.typography.labelLarge, color = if (on) Color.White else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Text(
            when (shelf) {
                Shelf.PURCHASED -> library.fromCollection?.let { "Your “$it” collection in Steam" } ?: "The paid games in your Steam library"
                Shelf.INSTALLED -> "On ${p.name} now" + (library.installed.sumOf { it.sizeBytes }.takeIf { it > 0 }?.let { " · ${Format.bytes(it)}" } ?: "")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )
    }
}

@Composable
private fun TileRow(row: List<LibraryEntry>, online: Boolean, vm: PcViewModel, p: PcPairing, open: (String) -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        row.forEach { g ->
            GameTile(
                g,
                canPlay = online && g.installed,
                // A non-Steam game has no store page.
                onOpen = { if (!g.shortcut) open(Routes.game(g.appId)) },
                onPlay = {
                    Toast.makeText(context, "Starting ${g.name} on ${p.name}…", Toast.LENGTH_SHORT).show()
                    vm.play(g) { error -> if (error != null) Toast.makeText(context, error, Toast.LENGTH_SHORT).show() }
                },
                modifier = Modifier.weight(1f),
            )
        }
        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun GameTile(g: LibraryEntry, canPlay: Boolean, onOpen: () -> Unit, onPlay: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    var failed by remember(g.coverUrl) { mutableStateOf(false) }
    Column(modifier.bouncyClick(onClick = onOpen)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)).background(LocalPulseColors.current.track)) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(g.coverUrl).crossfade(250).build(),
                contentDescription = g.name,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )
            if (failed) {
                Text(
                    g.name,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(8.dp),
                )
            }
            if (canPlay) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Accents.battery.brush)
                        .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                        .bouncyClick(onClick = onPlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Play ${g.name} on your PC", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
        Text(g.name, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        val note = when {
            g.shortcut -> "Non-Steam game"
            g.installed && g.sizeBytes > 0 -> Format.bytes(g.sizeBytes)
            g.minutes > 0 -> hours(g.minutes)
            else -> null
        }
        if (note != null) {
            Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------- specs

private fun LazyListScope.specCards(s: PcSpecs) {
    item {
        GlassCard(Modifier.fillMaxWidth(), accent = Accents.cpu) {
            CardTitle(Icons.Rounded.DeveloperBoard, "Processor", Accents.cpu)
            Spacer(Modifier.height(6.dp))
            InfoRow("Model", cpuName(s.cpu.name) ?: "Unknown")
            if (s.cpu.cores != null) InfoRow("Cores", listOfNotNull("${s.cpu.cores} cores", s.cpu.threads?.let { "$it threads" }).joinToString(" · "))
            s.cpu.maxMhz?.let { InfoRow("Base clock", Format.mhz(it.toLong())) }
            val cache = listOfNotNull(s.cpu.l2Kb?.takeIf { it > 0 }?.let { "L2 ${cacheSize(it)}" }, s.cpu.l3Kb?.takeIf { it > 0 }?.let { "L3 ${cacheSize(it)}" })
            if (cache.isNotEmpty()) InfoRow("Cache", cache.joinToString(" · "))
            s.cpu.socket?.takeUnless { it.equals("None", ignoreCase = true) }?.let { InfoRow("Socket", it) }
        }
    }
    val gpus = realGpus(s)
    if (gpus.isNotEmpty()) {
        item {
            GlassCard(Modifier.fillMaxWidth(), accent = Accents.gpu) {
                CardTitle(Icons.Rounded.ViewInAr, if (gpus.size > 1) "Graphics cards" else "Graphics", Accents.gpu)
                gpus.forEachIndexed { i, g ->
                    Text(g.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = if (i == 0) 12.dp else 16.dp))
                    g.vramBytes?.takeIf { it > 0 }?.let { InfoRow("Video memory", ramGb(it)) }
                    if (g.width != null && g.height != null && g.width > 0) {
                        InfoRow("Showing", "${g.width} × ${g.height}" + (g.refreshHz?.takeIf { it > 1 }?.let { " at $it Hz" } ?: ""))
                    }
                    g.driver?.let { InfoRow("Driver", it) }
                }
            }
        }
    }
    item {
        GlassCard(Modifier.fillMaxWidth(), accent = Accents.memory) {
            CardTitle(Icons.Rounded.Memory, "Memory", Accents.memory, trailing = s.memoryBytes?.let { ramGb(it) })
            Spacer(Modifier.height(6.dp))
            val sticks = s.sticks.filter { (it.bytes ?: 0) > 0 }
            if (sticks.isNotEmpty()) {
                val first = sticks.first()
                InfoRow("Sticks", "${sticks.size} × ${ramGb(first.bytes ?: 0)}" + (first.type?.let { " $it" } ?: ""))
                val speed = first.configuredMts?.takeIf { it > 0 } ?: first.speedMts
                speed?.let { InfoRow("Speed", "$it MT/s" + (first.speedMts?.takeIf { r -> r != speed && r > 0 }?.let { r -> " (rated $r)" } ?: "")) }
                sticks.forEach { st ->
                    InfoRow(st.slot ?: "Slot", listOfNotNull(st.bytes?.let { ramGb(it) }, st.maker?.takeIf { it.isNotBlank() && !it.startsWith("Unknown") }, st.part).joinToString(" · "))
                }
            } else {
                InfoRow("Installed", s.memoryBytes?.let { ramGb(it) } ?: "Unknown")
            }
        }
    }
    if (s.drives.isNotEmpty() || s.volumes.isNotEmpty()) {
        item {
            GlassCard(Modifier.fillMaxWidth(), accent = Accents.storage) {
                CardTitle(Icons.Rounded.Storage, "Storage", Accents.storage)
                s.drives.forEach { d ->
                    InfoRow(
                        d.model ?: "Drive",
                        listOfNotNull(d.bytes?.takeIf { it > 0 }?.let { Format.bytes(it, 0) }, d.kind?.takeIf { it.isNotBlank() && it != "Unspecified" }, d.bus?.takeIf { it.isNotBlank() }).joinToString(" · "),
                    )
                }
                s.volumes.forEach { v ->
                    val size = v.bytes ?: return@forEach
                    val free = v.freeBytes ?: 0
                    Text(
                        "${v.letter} ${v.label.orEmpty()}".trim() + " · ${Format.bytes(free)} free of ${Format.bytes(size, 0)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                    )
                    BarMeter(if (size > 0) (size - free).toFloat() / size else 0f, Accents.storage, Modifier.fillMaxWidth().height(8.dp))
                }
            }
        }
    }
    item {
        GlassCard(Modifier.fillMaxWidth(), accent = Accents.download) {
            CardTitle(Icons.Rounded.DeveloperBoard, "Motherboard", Accents.download)
            Spacer(Modifier.height(6.dp))
            InfoRow("Board", listOfNotNull(s.boardMaker, s.boardModel).joinToString(" ").ifBlank { "Unknown" })
            s.bios?.let { InfoRow("BIOS", it) }
            model(s)?.let { InfoRow("Computer", it) }
        }
    }
    if (s.monitors.isNotEmpty()) {
        item {
            GlassCard(Modifier.fillMaxWidth(), accent = Accents.upload) {
                CardTitle(Icons.Rounded.Monitor, if (s.monitors.size > 1) "Displays" else "Display", Accents.upload)
                Spacer(Modifier.height(6.dp))
                s.monitors.forEachIndexed { i, m -> InfoRow("Screen ${i + 1}", listOfNotNull(m.name, m.maker?.takeIf { it.isNotBlank() }?.let { "($it)" }).joinToString(" ")) }
            }
        }
    }
    item {
        GlassCard(Modifier.fillMaxWidth(), accent = Accents.games) {
            CardTitle(Icons.Rounded.DesktopWindows, "Windows", Accents.games)
            Spacer(Modifier.height(6.dp))
            InfoRow("Edition", s.os.name?.removePrefix("Microsoft ") ?: "Windows")
            s.os.version?.let { InfoRow("Version", it) }
            s.os.build?.let { InfoRow("Build", it) }
            s.os.installed?.let { InfoRow("Installed", it) }
        }
    }
}

// ---------------------------------------------------------------- wording

private fun address(host: String, port: Int) = if (port == PcClient.PORT) host else "$host:$port"

/** The maker and model, unless the maker never filled them in (common on self-built PCs). */
private fun model(s: PcSpecs): String? {
    val blank = Regex("system (product name|manufacturer)|to be filled|default string|^o\\.e\\.m\\.?$", RegexOption.IGNORE_CASE)
    return listOfNotNull(s.maker, s.model).filter { !blank.containsMatchIn(it) }.joinToString(" ").ifBlank { null }
}

/** "AMD Ryzen 7 7800X3D 8-Core Processor" → "AMD Ryzen 7 7800X3D". */
private fun cpuName(name: String?): String? = name
    ?.replace(Regex("\\((R|TM|tm)\\)"), "")
    ?.replace(Regex("\\s+\\d+-Core Processor|\\s+CPU\\s+@.*$|\\s+Processor$"), "")
    ?.replace(Regex("\\s+"), " ")
    ?.trim()
    ?.ifBlank { null }

/** Graphics cards, leaving out remote-desktop and virtual screens. */
private fun realGpus(s: PcSpecs) = s.gpus.filterNot { Regex("basic display|virtual|remote display|parsec|meta|spacedesk|indirect", RegexOption.IGNORE_CASE).containsMatchIn(it.name) }
    .ifEmpty { s.gpus }

/** Memory sizes as they're sold: 34,359,738,368 bytes is "32 GB". */
private fun ramGb(bytes: Long): String {
    val gib = bytes / (1024.0 * 1024 * 1024)
    return if (gib >= 1) "${gib.roundToInt()} GB" else "${(bytes / (1024 * 1024))} MB"
}

/** "8.9 / 12 GB": short enough for a third of the card. */
private fun vram(usedMb: Float, totalMb: Float): String = String.format(Locale.US, "%.1f / %.0f GB", usedMb / 1024, totalMb / 1024)

private fun gb(mb: Float): String = if (mb >= 1024) String.format(Locale.US, "%.1f GB", mb / 1024) else "${mb.roundToInt()} MB"

private fun cacheSize(kb: Int): String = if (kb >= 1024) "${(kb / 1024f).let { if (it % 1f == 0f) it.toInt().toString() else String.format(Locale.US, "%.1f", it) }} MB" else "$kb KB"

private fun hours(minutes: Long): String = when {
    minutes < 60 -> "$minutes min played"
    else -> "${(minutes / 60.0).let { if (it < 10) String.format(Locale.US, "%.1f", it) else it.roundToInt().toString() }} h played"
}

private fun ago(at: Long, now: Long): String {
    if (at <= 0) return "a while ago"
    val s = (now - at).coerceAtLeast(0) / 1000
    return when {
        s < 60 -> "just now"
        s < 3600 -> "${s / 60} min ago"
        s < 86_400 -> "${s / 3600} h ago" + (if (s < 43_200) " (${Format.clock(at)})" else "")
        else -> "${s / 86_400} days ago"
    }
}

private fun share(context: Context, url: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "Vivid Pulse PC")
        .putExtra(Intent.EXTRA_TEXT, "Vivid Pulse PC for Windows: $url")
    runCatching { context.startActivity(Intent.createChooser(send, "Send the link to your PC").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun copy(context: Context, url: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Vivid Pulse PC", url))
    Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
}
