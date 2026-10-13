package com.jambo199.vividpulse.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import com.jambo199.vividpulse.ui.theme.Accent
import com.jambo199.vividpulse.ui.theme.LocalPulseColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Slowly drifting coloured light behind the top of a page. */
@Composable
fun AmbientGlow(accents: List<Accent>, modifier: Modifier = Modifier) {
    val dark = LocalPulseColors.current.isDark
    val t by rememberInfiniteTransition(label = "ambient").animateFloat(
        0f, 1f, infiniteRepeatable(tween(18_000, easing = LinearEasing)), label = "drift",
    )
    Canvas(modifier) {
        val a = (2 * PI * t).toFloat()
        val strength = if (dark) 0.30f else 0.16f
        accents.take(3).forEachIndexed { i, accent ->
            val phase = a + i * 2.1f
            val c = Offset(
                size.width * (0.2f + 0.6f * ((i + 0.5f) / accents.size.coerceAtMost(3)) + 0.12f * cos(phase)),
                size.height * (0.25f + 0.15f * sin(phase * 0.8f)),
            )
            val r = size.width * (0.55f + 0.08f * sin(phase))
            drawCircle(Brush.radialGradient(listOf(accent.start.copy(alpha = strength), Color.Transparent), center = c, radius = r), r, c)
        }
    }
}

/**
 * A battery drawn as a tank of liquid: the level sloshes gently, bubbles rise while charging,
 * and the colour turns amber then red as it runs low.
 */
@Composable
fun LiquidBattery(level: Float, charging: Boolean, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(level.coerceIn(0f, 100f) / 100f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessVeryLow), label = "level")
    val transition = rememberInfiniteTransition(label = "liquid")
    val wave by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(if (charging) 1600 else 3200, easing = LinearEasing)), label = "wave")
    val bubbles by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "bubbles")
    val colors = LocalPulseColors.current
    val liquid = when {
        charging -> listOf(Color(0xFFD9F99D), Color(0xFF22C55E))
        shown < 0.15f -> listOf(Color(0xFFFCA5A5), Color(0xFFDC2626))
        shown < 0.30f -> listOf(Color(0xFFFDE68A), Color(0xFFF59E0B))
        else -> listOf(Color(0xFF86EFAC), Color(0xFF16A34A))
    }
    Canvas(modifier) {
        val capH = size.height * 0.06f
        val body = Size(size.width, size.height - capH)
        val radius = CornerRadius(size.width * 0.22f)
        val stroke = 3.dp.toPx()
        // The terminal on top.
        drawRoundRect(colors.track, Offset(size.width * 0.32f, 0f), Size(size.width * 0.36f, capH + radius.x * 0.3f), CornerRadius(capH))
        val shell = Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, capH, body.width, size.height, radius))
        }
        drawPath(shell, colors.glass)
        clipPath(shell) {
            val inner = size.height - capH
            val surface = size.height - inner * shown
            val amp = if (charging) 5.dp.toPx() else 3.dp.toPx()
            val path = Path().apply {
                moveTo(0f, size.height)
                var x = 0f
                while (x <= size.width) {
                    val y = surface + amp * sin((x / size.width * 2 * PI + wave * 2 * PI).toFloat())
                    lineTo(x, y)
                    x += 4f
                }
                lineTo(size.width, size.height)
                close()
            }
            drawPath(path, Brush.verticalGradient(liquid, startY = surface, endY = size.height))
            if (charging) {
                for (i in 0 until 6) {
                    val p = (bubbles + i / 6f) % 1f
                    val bx = size.width * (0.15f + 0.7f * ((i * 37) % 10) / 10f)
                    val by = size.height - (size.height - surface) * p
                    drawCircle(Color.White.copy(alpha = 0.35f * (1f - p)), (2.5f + i % 3).dp.toPx(), Offset(bx, by))
                }
            }
        }
        drawPath(shell, colors.glassBorder, style = Stroke(stroke, join = StrokeJoin.Round))
    }
}

/** A shimmering placeholder while something loads. */
fun Modifier.shimmer(): Modifier = composed {
    val colors = LocalPulseColors.current
    val x by rememberInfiniteTransition(label = "shimmer").animateFloat(
        -1f, 2f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "x",
    )
    clip(RoundedCornerShape(16.dp))
        .background(colors.track)
        .drawBehind {
            val w = size.width
            drawRect(
                Brush.linearGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = if (colors.isDark) 0.08f else 0.5f), Color.Transparent),
                    start = Offset(w * x - w * 0.3f, 0f),
                    end = Offset(w * x + w * 0.3f, size.height),
                ),
            )
        }
}

/** Placeholder block. */
@Composable
fun ShimmerBox(modifier: Modifier) {
    Box(modifier.shimmer())
}
