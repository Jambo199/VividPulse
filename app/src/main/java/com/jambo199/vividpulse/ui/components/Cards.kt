package com.jambo199.vividpulse.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jambo199.vividpulse.ui.theme.Accent
import com.jambo199.vividpulse.ui.theme.LocalPulseColors
import com.jambo199.vividpulse.ui.theme.Numbers

/**
 * The app's card: a softly lit panel with a thin border, tinted by [accent] when given. With
 * [onClick] it springs under the finger.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accent: Accent? = null,
    onClick: (() -> Unit)? = null,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalPulseColors.current
    val shape = RoundedCornerShape(28.dp)
    val tint = accent?.start ?: Color.Transparent
    var m = modifier
    if (onClick != null) m = m.bouncyClick(onClick = onClick)
    Column(
        m
            .clip(shape)
            .background(colors.glass)
            .background(
                Brush.linearGradient(
                    listOf(tint.copy(alpha = if (colors.isDark) 0.13f else 0.08f), Color.Transparent),
                ),
            )
            .border(1.dp, Brush.linearGradient(listOf(tint.copy(alpha = 0.35f).takeIf { accent != null } ?: colors.glassBorder, colors.glassBorder)), shape)
            .padding(padding),
        content = content,
    )
}

/** A rounded square with a gradient behind an icon. */
@Composable
fun IconBadge(icon: ImageVector, accent: Accent, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(accent.brush),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}

/** Icon badge, title and an optional note on the right: the top line of most cards. */
@Composable
fun CardTitle(icon: ImageVector, title: String, accent: Accent, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, accent, size = 34.dp)
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A number that counts smoothly to each new value instead of jumping. */
@Composable
fun AnimatedNumber(
    value: Float,
    format: (Float) -> String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val shown by animateFloatAsState(value, spring(dampingRatio = 1f, stiffness = Spring.StiffnessLow), label = "number")
    Text(format(shown), style = style.merge(Numbers), color = color, modifier = modifier, maxLines = 1)
}

/** A label above a big value. */
@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleMedium.merge(Numbers), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A row in a list of specs: label on the left, value on the right. */
@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.merge(Numbers).copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.weight(0.58f),
        )
    }
}

/** A small rounded label, e.g. "LIVE", "-50%". */
@Composable
fun Pill(text: String, accent: Accent, modifier: Modifier = Modifier, filled: Boolean = true) {
    Box(
        modifier
            .clip(CircleShape)
            .then(if (filled) Modifier.background(accent.brush) else Modifier.border(1.dp, accent.start, CircleShape))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
            color = if (filled) Color.White else accent.start,
            maxLines = 1,
        )
    }
}

/** A pulsing dot for things that are live. */
@Composable
fun LiveDot(color: Color, modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "live").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "pulse",
    )
    Canvas(modifier.size(14.dp)) {
        drawCircle(color.copy(alpha = (1f - pulse) * 0.5f), radius = size.minDimension / 2 * (0.4f + pulse * 0.6f))
        drawCircle(color, radius = size.minDimension / 2 * 0.38f)
    }
}

/** Section heading above a group of cards. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier.padding(start = 6.dp, top = 18.dp, bottom = 8.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.5.sp), color = MaterialTheme.colorScheme.primary)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * A detail page: back button and title on top, the page's items in a scrolling list. Leaves room
 * at the bottom for the navigation bar.
 */
@Composable
fun DetailPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            actions()
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
            item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }
}

/** A round icon button on a soft background. */
@Composable
fun RoundButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(LocalPulseColors.current.glass)
            .border(1.dp, LocalPulseColors.current.glassBorder, CircleShape)
            .bouncyClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(22.dp))
    }
}
