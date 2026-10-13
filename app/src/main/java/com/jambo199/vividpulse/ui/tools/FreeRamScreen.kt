package com.jambo199.vividpulse.ui.tools

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jambo199.vividpulse.Graph
import com.jambo199.vividpulse.tools.BoostResult
import com.jambo199.vividpulse.tools.BoostStep
import com.jambo199.vividpulse.tools.StepStatus
import com.jambo199.vividpulse.ui.components.AnimatedNumber
import com.jambo199.vividpulse.ui.components.CardTitle
import com.jambo199.vividpulse.ui.components.ChartLine
import com.jambo199.vividpulse.ui.components.DetailPage
import com.jambo199.vividpulse.ui.components.GlassCard
import com.jambo199.vividpulse.ui.components.InfoRow
import com.jambo199.vividpulse.ui.components.LiveChart
import com.jambo199.vividpulse.ui.components.RingGauge
import com.jambo199.vividpulse.ui.components.Stat
import com.jambo199.vividpulse.ui.components.bouncyClick
import com.jambo199.vividpulse.ui.components.entrance
import com.jambo199.vividpulse.ui.components.sharedCard
import com.jambo199.vividpulse.ui.theme.Accents
import com.jambo199.vividpulse.ui.theme.LocalPulseColors
import com.jambo199.vividpulse.ui.theme.Numbers
import com.jambo199.vividpulse.util.Format
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun FreeRamScreen(back: () -> Unit, autoStart: Boolean = false) {
    val booster = Graph.booster
    val state by booster.state.collectAsStateWithLifecycle()
    val live by Graph.monitor.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) { if (autoStart) booster.start() }
    LaunchedEffect(state.result) { if (state.result != null) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }

    val m = live?.memory
    val total = m?.total ?: 0L
    // While it runs, the gauge follows the run's own fast readings.
    val availableGb = if (state.running) state.availableHistory.lastOrNull() else m?.available?.let { it / 1e9f }
    val usedFraction = if (total > 0 && availableGb != null) (1f - availableGb * 1e9f / total).coerceIn(0f, 1f) else 0f

    DetailPage("Free up RAM", back, subtitle = "See Android make room, step by step") {
        item {
            GlassCard(Modifier.fillMaxWidth().sharedCard("tool-freeram"), accent = Accents.memory) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RingGauge(usedFraction, Accents.memory, Modifier.size(150.dp), thickness = 13.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AnimatedNumber(usedFraction * 100, { "${it.roundToInt()}%" }, MaterialTheme.typography.displaySmall)
                            Text("in use", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column {
                            Text("Available", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            AnimatedNumber(availableGb ?: 0f, { String.format(Locale.US, "%.2f GB", it) }, MaterialTheme.typography.titleLarge, color = Accents.battery.start)
                        }
                        Stat("RAM", if (total > 0) "${com.jambo199.vividpulse.system.MemInfo.marketedRamGb(total)} GB" else "—")
                        if ((m?.swapTotal ?: 0) > 0) Stat("RAM+ in use", Format.bytes(m!!.swapUsed))
                    }
                }
                AnimatedVisibility(state.availableHistory.size > 1, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        Text("Available memory, live", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LiveChart(
                            listOf(ChartLine(state.availableHistory, Accents.battery)),
                            state.sampledAt,
                            com.jambo199.vividpulse.tools.RamBooster.SAMPLE_MS,
                            Modifier.fillMaxWidth().height(110.dp),
                            max = (total / 1e9f).takeIf { it > 0 },
                            visible = 60,
                        )
                        if (state.reserved > 0) {
                            Text(
                                "Holding ${Format.bytes(state.reserved)} for a moment",
                                style = MaterialTheme.typography.bodySmall.merge(Numbers),
                                color = Accents.memory.start,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(if (state.running) Accents.memory.brush else Accents.brand.brush, alpha = if (state.running) 0.6f else 1f)
                        .bouncyClick(enabled = !state.running) { booster.start() }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        when {
                            state.running -> "Working…"
                            state.result != null -> "Free up again"
                            else -> "Free up RAM"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                val last = booster.lastRun
                if (!state.running && state.result == null && last != null) {
                    Text(
                        "Last time: " + (if (last.second > 0) "+${Format.bytes(last.second)}" else "no change") + ", " +
                            DateUtils.getRelativeTimeSpanString(last.first, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                state.error?.let {
                    Text("Stopped: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        state.result?.let { r -> item { ResultCard(r, Modifier.entrance(0)) } }
        item {
            GlassCard(Modifier.fillMaxWidth().entrance(1)) {
                Text(if (state.running || state.result != null) "What it's doing" else "What it will do", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                state.steps.forEachIndexed { i, s -> StepRow(i + 1, s) }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth().entrance(2), accent = Accents.storage) {
                CardTitle(Icons.Rounded.Memory, "Good to know", Accents.storage)
                Spacer(Modifier.height(6.dp))
                listOf(
                    "Android keeps apps you've used in memory so they open instantly, so RAM looking full is normal and isn't slowing your phone down.",
                    "Freeing RAM closes apps you haven't used in a while. They open from scratch next time, a little slower, and that uses a bit of battery.",
                    "Music, navigation and anything showing a notification keep running, and so does the app you're in.",
                    "Best used just before a big game, or when the phone feels sluggish after a lot of app switching.",
                    "Since Android 14 no app can close other apps directly, so this asks Android to do it, the same way it does when memory runs short.",
                ).forEach {
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text("•", color = Accents.storage.start, modifier = Modifier.padding(end = 10.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepRow(number: Int, step: BoostStep) {
    val tint by animateColorAsState(
        when (step.status) {
            StepStatus.DONE -> Accents.battery.start
            StepStatus.RUNNING -> Accents.memory.start
            else -> LocalPulseColors.current.faint
        },
        label = "step",
    )
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        StatusMark(step.status, number, tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                step.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (step.status == StepStatus.WAITING) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                step.detail ?: step.explain,
                style = MaterialTheme.typography.bodySmall.merge(Numbers),
                color = if (step.detail != null && step.status != StepStatus.WAITING) tint else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (step.detail != null && step.status == StepStatus.RUNNING) {
                Text(step.explain, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A number while waiting, a spinning arc while running, a tick when done. */
@Composable
private fun StatusMark(status: StepStatus, number: Int, tint: Color) {
    val spin by rememberInfiniteTransition(label = "spin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "angle",
    )
    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        when (status) {
            StepStatus.RUNNING -> Canvas(Modifier.size(26.dp)) {
                val stroke = 3.dp.toPx()
                drawArc(tint.copy(alpha = 0.25f), 0f, 360f, false, Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
                drawArc(tint, spin, 100f, false, Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
            }
            StepStatus.DONE -> Box(Modifier.size(26.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            StepStatus.SKIPPED -> Icon(Icons.Rounded.Remove, contentDescription = "Skipped", tint = tint)
            StepStatus.WAITING -> Box(
                Modifier.size(26.dp).clip(CircleShape).background(LocalPulseColors.current.track),
                contentAlignment = Alignment.Center,
            ) {
                Text("$number", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ResultCard(r: BoostResult, modifier: Modifier) {
    val good = r.gained >= 50_000_000
    GlassCard(modifier.fillMaxWidth(), accent = if (good) Accents.battery else Accents.storage) {
        Text(if (good) "Freed up" else "All good", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AnimatedNumber(
            (r.gained.coerceAtLeast(0) / 1e9f),
            { if (good) "+" + String.format(Locale.US, "%.2f GB", it) else "No change needed" },
            MaterialTheme.typography.displaySmall,
            color = if (good) Accents.battery.start else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        InfoRow("Available before", Format.bytes(r.before.available))
        InfoRow("Available now", Format.bytes(r.after.available))
        if (r.compressed > 10_000_000) InfoRow("Squeezed into RAM+", Format.bytes(r.compressed))
        if (r.cacheCleared > 10_000_000) InfoRow("Cached files dropped", Format.bytes(r.cacheCleared))
        InfoRow("Vivid Pulse itself", "${Format.bytes(r.ownBeforeKb * 1024)} → ${Format.bytes(r.ownAfterKb * 1024)}")
        Text(
            if (good) {
                "The rest came from apps Android closed in the background; they'll open fresh next time."
            } else {
                "Android had already kept memory in good shape, so there was little to free."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
