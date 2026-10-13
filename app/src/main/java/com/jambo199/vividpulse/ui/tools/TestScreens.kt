package com.jambo199.vividpulse.ui.tools

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.jambo199.vividpulse.system.DisplayReader
import com.jambo199.vividpulse.ui.components.AnimatedNumber
import com.jambo199.vividpulse.ui.components.DetailPage
import com.jambo199.vividpulse.ui.components.GlassCard
import com.jambo199.vividpulse.ui.components.RoundButton
import com.jambo199.vividpulse.ui.components.Stat
import com.jambo199.vividpulse.ui.components.sharedCard
import com.jambo199.vividpulse.ui.theme.Accents
import com.jambo199.vividpulse.ui.theme.LocalPulseColors
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** Hides the status and navigation bars (and turns the brightness up) while shown. */
@Composable
private fun Immersive(brightest: Boolean = false) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        val before = window?.attributes?.screenBrightness
        if (brightest && window != null) {
            window.attributes = window.attributes.apply { screenBrightness = 1f }
        }
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (brightest && window != null && before != null) {
                window.attributes = window.attributes.apply { screenBrightness = before }
            }
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

private val TEST_COLOURS = listOf(
    "Red" to Color(0xFFFF0000),
    "Green" to Color(0xFF00FF00),
    "Blue" to Color(0xFF0000FF),
    "White" to Color.White,
    "Black" to Color.Black,
    "Grey" to Color(0xFF808080),
    "Gradient" to Color.Unspecified,
)

/** Full-screen colours: stuck or dead pixels, tint and uneven brightness stand out on them. */
@Composable
fun ScreenTestScreen(back: () -> Unit) {
    Immersive(brightest = true)
    var index by remember { mutableIntStateOf(0) }
    var hint by remember { mutableStateOf(true) }
    LaunchedEffect(index) {
        hint = true
        delay(1800)
        hint = false
    }
    BackHandler(onBack = back)
    val (name, colour) = TEST_COLOURS[index]
    Box(
        Modifier
            .fillMaxSize()
            .then(
                if (colour == Color.Unspecified) {
                    Modifier.background(Brush.horizontalGradient(listOf(Color.Black, Color.White)))
                } else {
                    Modifier.background(colour)
                },
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { index = (index + 1) % TEST_COLOURS.size },
                    onLongPress = { back() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(hint, enter = fadeIn(), exit = fadeOut()) {
            Column(
                Modifier.clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("$name · ${index + 1} of ${TEST_COLOURS.size}", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text("Tap for the next colour · hold or go back to finish", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

private class Stroke2(val color: Color) {
    val points = ArrayList<Offset>()
}

private val FINGER_COLOURS = listOf(
    Accents.cpu.start, Accents.upload.start, Accents.battery.start, Accents.storage.start, Accents.memory.start,
    Accents.gpu.start, Accents.download.start, Color.White, Accents.thermal.start, Accents.charging.start,
)

/** Draw with as many fingers as you like; shows how many touches the screen tracks at once. */
@Composable
fun TouchTestScreen(back: () -> Unit) {
    Immersive()
    BackHandler(onBack = back)
    val active = remember { HashMap<Long, Stroke2>() }
    val done = remember { ArrayList<Stroke2>() }
    var version by remember { mutableIntStateOf(0) }
    var fingers by remember { mutableIntStateOf(0) }
    var most by remember { mutableIntStateOf(0) }
    var touches by remember { mutableIntStateOf(0) }
    val colors = LocalPulseColors.current
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { c ->
                                val id = c.id.value
                                if (c.pressed) {
                                    val stroke = active.getOrPut(id) {
                                        touches++
                                        Stroke2(FINGER_COLOURS[(touches - 1) % FINGER_COLOURS.size])
                                    }
                                    stroke.points += c.position
                                } else {
                                    active.remove(id)?.let {
                                        done += it
                                        if (done.size > 60) done.removeAt(0)
                                    }
                                }
                                c.consume()
                            }
                            fingers = active.size
                            most = max(most, fingers)
                            version++
                        }
                    }
                },
        ) {
            if (version < 0) return@Canvas
            // A faint grid helps spot dead zones.
            val step = 48.dp.toPx()
            var x = step
            while (x < size.width) {
                drawLine(colors.grid, Offset(x, 0f), Offset(x, size.height))
                x += step
            }
            var y = step
            while (y < size.height) {
                drawLine(colors.grid, Offset(0f, y), Offset(size.width, y))
                y += step
            }
            fun drawStroke(s: Stroke2, alpha: Float) {
                if (s.points.size < 2) return
                val p = Path()
                p.moveTo(s.points[0].x, s.points[0].y)
                for (i in 1 until s.points.size) p.lineTo(s.points[i].x, s.points[i].y)
                drawPath(p, s.color.copy(alpha = alpha), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            done.forEach { drawStroke(it, 0.45f) }
            active.values.forEach { s ->
                drawStroke(s, 1f)
                s.points.lastOrNull()?.let { at ->
                    drawCircle(s.color.copy(alpha = 0.25f), 44.dp.toPx(), at)
                    drawCircle(s.color, 44.dp.toPx(), at, style = Stroke(2.dp.toPx()))
                }
            }
        }
        Row(
            Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", back)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("$fingers ${if (fingers == 1) "finger" else "fingers"}", style = MaterialTheme.typography.titleLarge)
                Text("Most at once: $most", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RoundButton(Icons.Rounded.Delete, "Clear", {
                done.clear()
                most = fingers
                version++
            })
        }
    }
}

/**
 * Keeps the screen busy with moving things and measures every frame: how many come each second,
 * and how many arrive late (a stutter you'd notice in a game).
 */
@Composable
fun FpsScreen(back: () -> Unit) {
    val context = LocalContext.current
    val times = remember { FloatArray(FRAMES) }
    var head by remember { mutableIntStateOf(0) }
    var fps by remember { mutableFloatStateOf(0f) }
    var late by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var hz by remember { mutableFloatStateOf(DisplayReader.currentHz(context)) }
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        var windowStart = last
        var frames = 0
        while (true) {
            val now = withFrameNanos { it }
            val ms = (now - last) / 1e6f
            last = now
            times[head % FRAMES] = ms
            head++
            frames++
            total++
            val budget = 1000f / hz
            if (ms > budget * 1.5f) late++
            if (now - windowStart >= 500_000_000L) {
                fps = frames * 1e9f / (now - windowStart)
                frames = 0
                windowStart = now
                hz = DisplayReader.currentHz(context)
            }
        }
    }
    val spin by rememberInfiniteTransition(label = "spin").animateFloat(0f, 360f, infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "angle")
    val colors = LocalPulseColors.current

    DetailPage("Smoothness", back, subtitle = "The screen is running at ${hz.roundToInt()} Hz") {
        item {
            GlassCard(Modifier.fillMaxWidth().sharedCard("tool-fps"), accent = Accents.download) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        AnimatedNumber(fps, { "${it.roundToInt()}" }, MaterialTheme.typography.displayLarge)
                        Text("frames a second", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat("Late frames", if (total > 0) String.format(java.util.Locale.US, "%.1f%%", late * 100f / total) else "—")
                        Stat("Frame budget", String.format(java.util.Locale.US, "%.1f ms", 1000f / hz))
                    }
                }
                Spacer(Modifier.height(14.dp))
                // Frame times: each bar is one frame; red ones came late.
                Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                    if (head < 0) return@Canvas
                    val budget = 1000f / hz
                    val w = size.width / FRAMES
                    val scale = size.height / (budget * 3f)
                    drawLine(colors.grid, Offset(0f, size.height - budget * scale), Offset(size.width, size.height - budget * scale), 1.dp.toPx())
                    for (i in 0 until FRAMES) {
                        val ms = times[(head + i) % FRAMES]
                        if (ms <= 0f) continue
                        val h = (ms * scale).coerceAtMost(size.height)
                        val c = if (ms > budget * 1.5f) Accents.thermal.end else Accents.battery.start
                        drawRoundRect(c, Offset(i * w + w * 0.15f, size.height - h), Size(w * 0.7f, h), CornerRadius(w * 0.3f))
                    }
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Motion check", style = MaterialTheme.typography.titleMedium)
                Text(
                    "At higher refresh rates the ball and stripes move more smoothly and blur less.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Canvas(Modifier.fillMaxWidth().height(160.dp)) {
                    if (head < 0) return@Canvas
                    val t = System.nanoTime() / 1e9
                    // A ball sweeping side to side.
                    val bx = (size.width * (0.5 + 0.44 * sin(t * 2.2))).toFloat()
                    drawCircle(Brush.radialGradient(listOf(Accents.brand.start, Accents.brand.end), center = Offset(bx, 40.dp.toPx()), radius = 22.dp.toPx()), 22.dp.toPx(), Offset(bx, 40.dp.toPx()))
                    // Stripes scrolling past.
                    val stripe = 28.dp.toPx()
                    val shift = ((t * 480.0) % (stripe * 2)).toFloat()
                    var x = -stripe * 2 + shift
                    while (x < size.width) {
                        drawRect(colors.track, Offset(x, 86.dp.toPx()), Size(stripe, 24.dp.toPx()))
                        x += stripe * 2
                    }
                    // A spinning arc.
                    rotate(spin, Offset(size.width - 34.dp.toPx(), 140.dp.toPx())) {
                        drawArc(
                            Accents.download.brush, 0f, 270f, false,
                            Offset(size.width - 52.dp.toPx(), 122.dp.toPx()), Size(36.dp.toPx(), 36.dp.toPx()),
                            style = Stroke(4.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                }
            }
        }
        item {
            Text(
                "Late means a frame took over one and a half times its slot, which shows as a stutter. A few during page changes are normal.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
    }
}

private const val FRAMES = 90
