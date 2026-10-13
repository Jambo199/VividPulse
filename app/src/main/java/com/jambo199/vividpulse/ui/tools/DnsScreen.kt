package com.jambo199.vividpulse.ui.tools

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.jambo199.vividpulse.tools.DnsProvider
import com.jambo199.vividpulse.tools.DnsState
import com.jambo199.vividpulse.tools.PrivateDns
import com.jambo199.vividpulse.ui.components.CardTitle
import com.jambo199.vividpulse.ui.components.DetailPage
import com.jambo199.vividpulse.ui.components.GlassCard
import com.jambo199.vividpulse.ui.components.Pill
import com.jambo199.vividpulse.ui.components.PillButton
import com.jambo199.vividpulse.ui.components.entrance
import com.jambo199.vividpulse.ui.components.sharedCard
import com.jambo199.vividpulse.ui.theme.Accents
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Private DNS: pick a DNS server for the whole phone, with how quickly each answers. */
@Composable
fun DnsScreen(back: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(PrivateDns.current(context)) }
    var canApply by remember { mutableStateOf(PrivateDns.canApply(context)) }
    var times by remember { mutableStateOf<Map<String, Long?>>(emptyMap()) }
    var testing by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    // Coming back from Android's settings (or from granting the permission): read it again.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, refresh) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            state = PrivateDns.current(context)
            canApply = PrivateDns.canApply(context)
        }
    }
    fun test() {
        if (testing) return
        testing = true
        scope.launch {
            val hosts = PrivateDns.PROVIDERS.mapNotNull { it.host }
            val results = coroutineScope { hosts.map { h -> async { h to PrivateDns.responseMs(h) } }.awaitAll() }
            times = results.toMap()
            testing = false
        }
    }
    LaunchedEffect(Unit) { test() }
    fun choose(p: DnsProvider) {
        if (PrivateDns.apply(context, p)) {
            state = PrivateDns.current(context)
            Toast.makeText(context, "Private DNS: ${p.name}", Toast.LENGTH_SHORT).show()
        } else if (p.host == null) {
            Toast.makeText(context, "Choose \"Automatic\" under Private DNS", Toast.LENGTH_LONG).show()
            PrivateDns.openSettings(context)
        } else {
            PrivateDns.copy(context, p.host)
            Toast.makeText(context, "Copied ${p.host}. Under Private DNS, choose the hostname option and paste it", Toast.LENGTH_LONG).show()
            PrivateDns.openSettings(context)
        }
    }
    val fastest = times.filterValues { it != null }.minByOrNull { it.value ?: Long.MAX_VALUE }?.key

    DetailPage("Private DNS", back, subtitle = "In use: ${state?.label ?: "unknown"}") {
        item {
            GlassCard(Modifier.fillMaxWidth().sharedCard("tool-dns"), accent = Accents.cpu) {
                CardTitle(Icons.Rounded.Dns, "Faster, private lookups", Accents.cpu)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Every app looks up addresses through DNS. A good DNS server answers faster, keeps it encrypted, and some block ads and trackers in apps and games. It applies to the whole phone, on Wi-Fi and mobile data.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                PillButton(if (testing) "Testing…" else "Test response times again", Accents.cpu, { test() })
            }
        }
        if (!canApply) {
            item { OneTapSetup(Modifier.entrance(1)) { refresh++ } }
        }
        PrivateDns.PROVIDERS.forEachIndexed { i, p ->
            item(key = p.name) {
                val inUse = when {
                    state == null -> false
                    p.host == null -> state?.mode == DnsState.MODE_AUTO
                    else -> state?.mode == DnsState.MODE_HOST && state?.host.equals(p.host, ignoreCase = true)
                }
                ProviderCard(p, inUse, p.host?.let { times[it] }, p.host != null && p.host == fastest, tested = p.host in times, Modifier.entrance(i + 2)) { choose(p) }
            }
        }
        item {
            Text(
                "Response time is how long it takes to reach each server and set up the encrypted connection from where you are now, so it changes between Wi-Fi and mobile data. Under 40 ms is great.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
    }
}

@Composable
private fun ProviderCard(p: DnsProvider, inUse: Boolean, ms: Long?, fastest: Boolean, tested: Boolean, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier.fillMaxWidth().animateContentSize(), accent = if (inUse) Accents.battery else null, onClick = onClick, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium)
                p.host?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Accents.cpu.start, fontFamily = FontFamily.Monospace) }
            }
            Spacer(Modifier.width(8.dp))
            when {
                p.host == null -> Unit
                ms != null -> Pill("$ms ms", if (fastest) Accents.battery else Accents.download, filled = fastest)
                tested -> Pill("No answer", Accents.thermal, filled = false)
                else -> Pill("…", Accents.download, filled = false)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(p.about, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (inUse || fastest || p.blocksAds) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (inUse) Pill("In use", Accents.battery)
                if (fastest) Pill("Fastest here", Accents.battery, filled = false)
                if (p.blocksAds) Pill("Blocks ads", Accents.upload, filled = false)
            }
        }
    }
}

/** How to let the app switch DNS itself: one command from a computer. */
@Composable
private fun OneTapSetup(modifier: Modifier, onCheck: () -> Unit) {
    val context = LocalContext.current
    val command = PrivateDns.grantCommand(context)
    GlassCard(modifier.fillMaxWidth(), accent = Accents.storage) {
        CardTitle(Icons.Rounded.Computer, "Switch with one tap", Accents.storage)
        Spacer(Modifier.height(6.dp))
        Text(
            "For now, tapping a server copies its name and opens Android's Private DNS setting for you to paste it. To let Vivid Pulse switch it straight away, run this once from a computer:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        listOf(
            "Settings › About phone › tap Software version 7 times to turn on Developer options",
            "Settings › Developer options › turn on USB debugging",
            "Connect the phone to your computer, allow it, and run:",
        ).forEachIndexed { i, s ->
            Text("${i + 1}. $s", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(command, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = Accents.storage.start)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Copy command", Accents.storage, {
                PrivateDns.copy(context, command)
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
            })
            PillButton("Check again", Accents.cpu, onCheck)
        }
    }
}
