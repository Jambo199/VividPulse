package com.jambo199.vividpulse.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.jambo199.vividpulse.ui.theme.Accent
import com.jambo199.vividpulse.ui.theme.LocalPulseColors

@Immutable
data class ChartLine(val values: List<Float>, val accent: Accent, val fill: Boolean = true)

/**
 * A live line chart that scrolls smoothly every frame, gliding between readings instead of
 * jumping once a second. It runs one reading behind so the newest point slides in from the edge.
 *
 * [sampledAt] is when the newest reading was taken (SystemClock.uptimeMillis) and [intervalMs]
 * how far apart readings are. With [max] null the scale follows the data.
 */
@Composable
fun LiveChart(
    lines: List<ChartLine>,
    sampledAt: Long,
    intervalMs: Long,
    modifier: Modifier = Modifier,
    max: Float? = null,
    visible: Int = 60,
    grid: Boolean = true,
) {
    val now = remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) withFrameMillis { now.longValue = SystemClock.uptimeMillis() }
    }
    val dataMax = lines.maxOfOrNull { l -> l.values.takeLast(visible + 2).maxOrNull() ?: 0f } ?: 0f
    val target = max ?: (dataMax * 1.2f).coerceAtLeast(0.0001f)
    val scale by animateFloatAsState(target, spring(stiffness = Spring.StiffnessVeryLow), label = "scale")
    val colors = LocalPulseColors.current

    Canvas(modifier.clipToBounds()) {
        if (grid) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 6.dp.toPx()))
            for (i in 1..3) {
                val y = size.height * i / 4f
                drawLine(colors.grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = dash)
            }
        }
        val progress = ((now.longValue - sampledAt).toFloat() / intervalMs.coerceAtLeast(1)).coerceIn(0f, 1f)
        lines.forEach { line -> plotLine(line, progress, visible, scale) }
    }
}

private fun DrawScope.plotLine(line: ChartLine, progress: Float, visible: Int, max: Float) {
    val values = line.values
    if (values.size < 2) return
    val step = size.width / (visible - 1).coerceAtLeast(1)
    val n = values.size
    val top = size.height * 0.06f
    val usable = size.height - top - 2.dp.toPx()
    fun x(k: Int) = size.width + step * (1f - progress) - (n - 1 - k) * step
    fun y(v: Float) = top + usable * (1f - (v / max).coerceIn(0f, 1.05f))

    val first = (n - visible - 2).coerceAtLeast(0)
    val path = Path()
    path.moveTo(x(first), y(values[first]))
    for (k in first + 1 until n) {
        val x0 = x(k - 1)
        val x1 = x(k)
        val mid = (x0 + x1) / 2
        path.cubicTo(mid, y(values[k - 1]), mid, y(values[k]), x1, y(values[k]))
    }
    if (line.fill) {
        val area = Path().apply {
            addPath(path)
            lineTo(x(n - 1), size.height)
            lineTo(x(first), size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(line.accent.start.copy(alpha = 0.32f), Color.Transparent), startY = top, endY = size.height))
    }
    val brush = Brush.horizontalGradient(listOf(line.accent.end, line.accent.start), startX = 0f, endX = size.width)
    drawPath(path, brush, alpha = 0.18f, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, brush, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

    // A glowing dot where the line meets the right edge.
    val xa = x(n - 2)
    val xb = x(n - 1)
    val t = if (xb > xa) ((size.width - xa) / (xb - xa)).coerceIn(0f, 1f) else 1f
    val va = values[n - 2]
    val vb = values[n - 1]
    // Same easing as the curve between the two points.
    val eased = t * t * (3 - 2 * t)
    val dot = Offset(minOf(size.width, xb), y(va + (vb - va) * eased))
    drawCircle(line.accent.start.copy(alpha = 0.3f), 7.dp.toPx(), dot)
    drawCircle(line.accent.start, 3.5.dp.toPx(), dot)
}

/** A small still chart of recent values. */
@Composable
fun Sparkline(values: List<Float>, accent: Accent, modifier: Modifier = Modifier, max: Float? = null) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val top = max ?: (values.maxOrNull() ?: 1f).coerceAtLeast(0.0001f)
        val step = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val px = i * step
            val py = size.height * (1f - (v / top).coerceIn(0f, 1f))
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, Brush.horizontalGradient(listOf(accent.end, accent.start)), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
