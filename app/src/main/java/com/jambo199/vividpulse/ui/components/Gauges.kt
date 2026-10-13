package com.jambo199.vividpulse.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jambo199.vividpulse.ui.theme.Accent
import com.jambo199.vividpulse.ui.theme.LocalPulseColors
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

/**
 * A ring that fills to [value] (0..1) with a gradient, springing to each new value. Whatever is
 * in [content] sits in the middle.
 */
@Composable
fun RingGauge(
    value: Float,
    accent: Accent,
    modifier: Modifier = Modifier,
    thickness: Dp = 12.dp,
    sweep: Float = 280f,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val shown by animateFloatAsState(
        value.coerceIn(0f, 1f),
        spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessVeryLow),
        label = "ring",
    )
    val track = LocalPulseColors.current.track
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = thickness.toPx()
            val inset = stroke / 2 + stroke * 0.6f
            val arcSize = Size(size.minDimension - inset * 2, size.minDimension - inset * 2)
            val topLeft = Offset((size.width - arcSize.width) / 2, (size.height - arcSize.height) / 2)
            val start = 90f + (360f - sweep) / 2f
            rotate(start) {
                drawArc(track, 0f, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                if (shown > 0.002f) {
                    val brush = Brush.sweepGradient(
                        0f to accent.start,
                        sweep / 360f to accent.end,
                        1f to accent.start,
                        center = center,
                    )
                    // A soft glow under the bar, then the bar itself.
                    drawArc(brush, 0f, sweep * shown, false, topLeft, arcSize, alpha = 0.22f, style = Stroke(stroke * 2.2f, cap = StrokeCap.Round))
                    drawArc(brush, 0f, sweep * shown, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    val angle = Math.toRadians((sweep * shown).toDouble())
                    val r = arcSize.width / 2
                    val tip = Offset(center.x + (r * cos(angle)).toFloat(), center.y + (r * sin(angle)).toFloat())
                    drawCircle(Color.White, radius = stroke * 0.32f, center = tip)
                }
            }
        }
        content()
    }
}

/** A thin horizontal bar that fills to [value] with the accent gradient. */
@Composable
fun BarMeter(value: Float, accent: Accent, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val shown by animateFloatAsState(value.coerceIn(0f, 1f), spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow), label = "bar")
    val track = LocalPulseColors.current.track
    Canvas(modifier) {
        val h = height.toPx()
        val y = size.height / 2
        drawLine(track, Offset(h / 2, y), Offset(size.width - h / 2, y), h, StrokeCap.Round)
        if (shown > 0.002f) {
            val end = h / 2 + (size.width - h) * shown
            drawLine(
                Brush.horizontalGradient(listOf(accent.start, accent.end), startX = 0f, endX = size.width),
                Offset(h / 2, y), Offset(end, y), h, StrokeCap.Round,
            )
        }
    }
}

/** A tall pill that fills from the bottom: one per CPU core. */
@Composable
fun VerticalMeter(value: Float, color: Color, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(value.coerceIn(0f, 1f), spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow), label = "meter")
    val track = LocalPulseColors.current.track
    Canvas(modifier) {
        val w = size.width
        val r = androidx.compose.ui.geometry.CornerRadius(w / 2, w / 2)
        drawRoundRect(track, cornerRadius = r)
        val h = (size.height * shown).coerceAtLeast(if (shown > 0.005f) w else 0f)
        if (h > 0f) {
            drawRoundRect(
                Brush.verticalGradient(listOf(color, color.copy(alpha = 0.55f)), startY = size.height - h, endY = size.height),
                topLeft = Offset(0f, size.height - h),
                size = Size(w, h),
                cornerRadius = r,
            )
        }
    }
}

/**
 * A speedometer for the speed test: a log scale from 0 to 1 Gbps (2.5 Gbps beyond that), a
 * gradient arc up to the needle and a springy needle.
 */
@Composable
fun Speedometer(mbps: Float, accent: Accent, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    val fraction by animateFloatAsState(
        SpeedScale.fraction(mbps),
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
        label = "needle",
    )
    val colors = LocalPulseColors.current
    val measurer = rememberTextMeasurer()
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sweep = 260f
            val start = 90f + (360f - sweep) / 2f
            val stroke = 16.dp.toPx()
            val inset = stroke * 1.4f
            val arcSize = Size(size.minDimension - inset * 2, size.minDimension - inset * 2)
            val topLeft = Offset((size.width - arcSize.width) / 2, (size.height - arcSize.height) / 2)
            rotate(start) {
                drawArc(colors.track, 0f, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                val brush = Brush.sweepGradient(0f to accent.start, sweep / 360f to accent.end, 1f to accent.start, center = center)
                if (fraction > 0.002f) {
                    drawArc(brush, 0f, sweep * fraction, false, topLeft, arcSize, alpha = 0.25f, style = Stroke(stroke * 2f, cap = StrokeCap.Round))
                    drawArc(brush, 0f, sweep * fraction, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
            ticks(measurer, start, sweep, arcSize.width / 2 - stroke * 1.6f, colors.faint)
            // The needle.
            val angle = Math.toRadians((start + sweep * fraction).toDouble())
            val len = arcSize.width / 2 - stroke * 0.2f
            val tip = Offset(center.x + (len * cos(angle)).toFloat(), center.y + (len * sin(angle)).toFloat())
            val back = Offset(center.x + (len * 0.58f * cos(angle)).toFloat(), center.y + (len * 0.58f * sin(angle)).toFloat())
            drawLine(accent.end.copy(alpha = 0.35f), back, tip, 10.dp.toPx(), StrokeCap.Round)
            drawLine(Color.White, back, tip, 3.5.dp.toPx(), StrokeCap.Round)
        }
        content()
    }
}

private fun DrawScope.ticks(measurer: TextMeasurer, start: Float, sweep: Float, radius: Float, color: Color) {
    SpeedScale.LABELS.forEach { mark ->
        val a = Math.toRadians((start + sweep * SpeedScale.fraction(mark.toFloat())).toDouble())
        val label = if (mark >= 1000) "${mark / 1000}G" else mark.toString()
        val layout = measurer.measure(label, TextStyle(color = color, fontSize = 11.sp))
        val c = Offset(center.x + (radius * cos(a)).toFloat(), center.y + (radius * sin(a)).toFloat())
        drawText(layout, topLeft = Offset(c.x - layout.size.width / 2, c.y - layout.size.height / 2))
    }
}

/** Where a speed sits on the speedometer: more room for everyday speeds, less for the top. */
object SpeedScale {
    val LABELS = listOf(0, 5, 10, 25, 50, 100, 250, 500, 1000)
    private const val TOP = 2500f

    fun fraction(mbps: Float): Float {
        if (mbps <= 0f) return 0f
        return (ln(1f + mbps) / ln(1f + TOP)).coerceIn(0f, 1f)
    }
}
