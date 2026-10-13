package com.jambo199.vividpulse.ui.tools

import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.jambo199.vividpulse.tools.ServerInfo
import com.jambo199.vividpulse.tools.SpeedServer
import com.jambo199.vividpulse.ui.components.RoundButton
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jambo199.vividpulse.tools.SpeedPhase
import com.jambo199.vividpulse.tools.SpeedState
import com.jambo199.vividpulse.tools.SpeedTest
import com.jambo199.vividpulse.ui.components.AnimatedNumber
import com.jambo199.vividpulse.ui.components.ChartLine
import com.jambo199.vividpulse.ui.components.DetailPage
import com.jambo199.vividpulse.ui.components.GlassCard
import com.jambo199.vividpulse.ui.components.InfoRow
import com.jambo199.vividpulse.ui.components.LiveChart
import com.jambo199.vividpulse.ui.components.Speedometer
import com.jambo199.vividpulse.ui.components.Stat
import com.jambo199.vividpulse.ui.components.bouncyClick
import com.jambo199.vividpulse.ui.components.entrance
import com.jambo199.vividpulse.ui.components.sharedCard
import com.jambo199.vividpulse.ui.theme.Accents
import com.jambo199.vividpulse.ui.theme.Numbers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SpeedTestScreen(back: () -> Unit) {
    val context = LocalContext.current
    val test = remember { SpeedTest() }
    val state by test.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    var history by remember { mutableStateOf(SpeedHistory.read(context)) }
    val running = state.phase != SpeedPhase.IDLE && state.phase != SpeedPhase.DONE && state.phase != SpeedPhase.FAILED
    val mobile = remember { onMobileData(context) }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }
    val server by produceState<ServerInfo?>(null) { value = SpeedServer.lookup() }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state.phase) {
        if (state.phase == SpeedPhase.DONE) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun start() {
        job?.cancel()
        job = scope.launch {
            test.run()
            val s = test.state.value
            if (s.phase == SpeedPhase.DONE) {
                SpeedHistory.add(context, s)
                history = SpeedHistory.read(context)
            }
        }
    }

    val accent = if (state.phase == SpeedPhase.UPLOAD) Accents.upload else Accents.download
    DetailPage(
        "Speed test",
        back,
        subtitle = server?.place?.let { "Cloudflare server in $it" } ?: "Using Cloudflare's speed test servers",
        actions = {
            if (state.phase == SpeedPhase.DONE) {
                RoundButton(Icons.Rounded.Share, "Share result", { shareResult(context, state, server) })
            }
        },
    ) {
        item {
            GlassCard(Modifier.fillMaxWidth().sharedCard("tool-speedtest"), accent = accent) {
                Speedometer(
                    if (running) state.liveMbps else state.downMbps ?: 0f,
                    accent,
                    Modifier.fillMaxWidth().aspectRatio(1.15f),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 70.dp)) {
                        AnimatedNumber(
                            if (running) state.liveMbps else state.downMbps ?: 0f,
                            { if (it >= 100) "${it.roundToInt()}" else String.format(Locale.US, "%.1f", it) },
                            MaterialTheme.typography.displayMedium,
                        )
                        Text("Mbps", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        AnimatedContent(
                            state.phase,
                            transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
                            label = "phase",
                        ) { phase ->
                            Text(
                                if (phase == SpeedPhase.FAILED) state.error ?: phase.label else phase.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = accent.start,
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    Stat("Response", state.pingMs?.let { "${it.roundToInt()} ms" } ?: "—", Modifier.weight(1f))
                    Stat("Download", state.downMbps?.let { mbps(it) } ?: "—", Modifier.weight(1f), color = Accents.download.start)
                    Stat("Upload", state.upMbps?.let { mbps(it) } ?: "—", Modifier.weight(1f), color = Accents.upload.start)
                }
                if (state.downSamples.size > 1) {
                    Spacer(Modifier.height(12.dp))
                    LiveChart(
                        listOfNotNull(
                            ChartLine(state.downSamples, Accents.download),
                            state.upSamples.takeIf { it.size > 1 }?.let { ChartLine(it, Accents.upload) },
                        ),
                        android.os.SystemClock.uptimeMillis(),
                        200,
                        Modifier.fillMaxWidth().height(90.dp),
                        visible = 50,
                        grid = false,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(if (running) Accents.thermal.brush else Accents.games.brush)
                        .bouncyClick { if (running) job?.cancel() else start() }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        when {
                            running -> "Stop"
                            state.phase == SpeedPhase.IDLE -> "Start"
                            else -> "Test again"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                state.jitterMs?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Jitter ${it.roundToInt()} ms · " + quality(state),
                        style = MaterialTheme.typography.bodySmall.merge(Numbers),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                server?.provider?.let {
                    Spacer(Modifier.height(4.dp))
                    Text("Your provider: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (mobile) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "You're on mobile data: a test on a fast 5G connection can use several hundred MB.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Accents.storage.start,
                    )
                }
            }
        }
        if (history.isNotEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth().entrance(1)) {
                    Text("Earlier tests", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    history.forEach { h ->
                        InfoRow(
                            if (System.currentTimeMillis() - h.at < 60_000) "Just now" else DateUtils.getRelativeTimeSpanString(h.at).toString(),
                            "↓ ${mbps(h.down)} · ↑ ${mbps(h.up)} · ${h.ping.roundToInt()} ms",
                        )
                    }
                }
            }
        }
    }
}

private fun shareResult(context: Context, s: SpeedState, server: ServerInfo?) {
    val text = buildString {
        append("My internet speed: ↓ ${mbps(s.downMbps ?: 0f)} · ↑ ${mbps(s.upMbps ?: 0f)}")
        s.pingMs?.let { append(" · ${it.roundToInt()} ms") }
        server?.place?.let { append(" (Cloudflare $it)") }
        append(" – tested with Vivid Pulse")
    }
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(Intent.createChooser(send, "Share result").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun mbps(v: Float): String = if (v >= 100) "${v.roundToInt()} Mbps" else String.format(Locale.US, "%.1f Mbps", v)

/** What the result is good for, in plain words. */
private fun quality(s: SpeedState): String {
    val down = s.downMbps ?: return ""
    val ping = s.pingMs ?: 999f
    return when {
        down >= 100 && ping < 30 -> "great for cloud gaming and 4K streaming"
        down >= 25 && ping < 50 -> "good for online games and HD streaming"
        down >= 10 -> "fine for streaming; online games may lag"
        else -> "slow: expect buffering"
    }
}

private fun onMobileData(context: Context): Boolean {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
}

data class SpeedResult(val at: Long, val down: Float, val up: Float, val ping: Float)

/** The last few results, kept on the phone. */
object SpeedHistory {
    private const val KEY = "history"

    fun read(context: Context): List<SpeedResult> = runCatching {
        val a = JSONArray(context.getSharedPreferences("speedtest", Context.MODE_PRIVATE).getString(KEY, "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            SpeedResult(o.getLong("at"), o.getDouble("down").toFloat(), o.getDouble("up").toFloat(), o.getDouble("ping").toFloat())
        }
    }.getOrDefault(emptyList())

    fun add(context: Context, s: SpeedState) {
        val r = SpeedResult(System.currentTimeMillis(), s.downMbps ?: 0f, s.upMbps ?: 0f, s.pingMs ?: 0f)
        val all = (listOf(r) + read(context)).take(8)
        val a = JSONArray(all.map { JSONObject().put("at", it.at).put("down", it.down.toDouble()).put("up", it.up.toDouble()).put("ping", it.ping.toDouble()) })
        context.getSharedPreferences("speedtest", Context.MODE_PRIVATE).edit().putString(KEY, a.toString()).apply()
    }
}
