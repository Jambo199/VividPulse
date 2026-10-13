package com.jambo199.vividpulse.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.jambo199.vividpulse.data.ThemeMode

/** Each vital has its own pair of colours, used for its gauge, chart and icon. */
@Immutable
data class Accent(val start: Color, val end: Color) {
    val brush: Brush get() = Brush.linearGradient(listOf(start, end))
    val solid: Color get() = start
}

object Accents {
    val cpu = Accent(Color(0xFF22D3EE), Color(0xFF3B82F6))
    val memory = Accent(Color(0xFFC084FC), Color(0xFF7C3AED))
    val gpu = Accent(Color(0xFFFB923C), Color(0xFFF43F5E))
    val battery = Accent(Color(0xFF4ADE80), Color(0xFF16A34A))
    val charging = Accent(Color(0xFFD9F99D), Color(0xFF65A30D))
    val thermal = Accent(Color(0xFFFCA5A5), Color(0xFFEF4444))
    val download = Accent(Color(0xFF38BDF8), Color(0xFF6366F1))
    val upload = Accent(Color(0xFFF472B6), Color(0xFFDB2777))
    val storage = Accent(Color(0xFFFDE047), Color(0xFFF59E0B))
    val games = Accent(Color(0xFF60A5FA), Color(0xFFA855F7))
    val brand = Accent(Color(0xFF22D3EE), Color(0xFFFF4FD8))

    /** Green when low, through amber, to red when high. */
    fun load(fraction: Float): Color = when {
        fraction < 0.5f -> lerp(Color(0xFF4ADE80), Color(0xFFFACC15), fraction / 0.5f)
        else -> lerp(Color(0xFFFACC15), Color(0xFFF43F5E), ((fraction - 0.5f) / 0.5f).coerceIn(0f, 1f))
    }

    private fun lerp(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t.coerceIn(0f, 1f))
}

@Immutable
data class PulseColors(
    val isDark: Boolean,
    val glass: Color,
    val glassBorder: Color,
    val track: Color,
    val grid: Color,
    val faint: Color,
)

val LocalPulseColors = staticCompositionLocalOf {
    PulseColors(true, Color(0xFF121726), Color(0x1FFFFFFF), Color(0x26FFFFFF), Color(0x14FFFFFF), Color(0xFF6B7488))
}

private val Dark = darkColorScheme(
    primary = Color(0xFF8B7CFF),
    onPrimary = Color(0xFF0B0820),
    primaryContainer = Color(0xFF2A2260),
    onPrimaryContainer = Color(0xFFE2DDFF),
    secondary = Color(0xFF22D3EE),
    onSecondary = Color(0xFF00222A),
    secondaryContainer = Color(0xFF0E3A45),
    onSecondaryContainer = Color(0xFFCFF7FF),
    tertiary = Color(0xFFFF6FDF),
    background = Color(0xFF07090F),
    onBackground = Color(0xFFE9ECF5),
    surface = Color(0xFF07090F),
    onSurface = Color(0xFFE9ECF5),
    surfaceVariant = Color(0xFF1A2030),
    onSurfaceVariant = Color(0xFF9BA5BA),
    surfaceContainerLowest = Color(0xFF05070C),
    surfaceContainerLow = Color(0xFF0D111B),
    surfaceContainer = Color(0xFF121726),
    surfaceContainerHigh = Color(0xFF181E2F),
    surfaceContainerHighest = Color(0xFF1F2638),
    outline = Color(0xFF353E54),
    outlineVariant = Color(0xFF242B3D),
    error = Color(0xFFFF6B81),
)

private val Light = lightColorScheme(
    primary = Color(0xFF5B4BE0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E0FF),
    onPrimaryContainer = Color(0xFF1A1060),
    secondary = Color(0xFF0891B2),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3F4FB),
    onSecondaryContainer = Color(0xFF00313C),
    tertiary = Color(0xFFC026D3),
    background = Color(0xFFF4F5FA),
    onBackground = Color(0xFF0E1220),
    surface = Color(0xFFF4F5FA),
    onSurface = Color(0xFF0E1220),
    surfaceVariant = Color(0xFFE6E8F0),
    onSurfaceVariant = Color(0xFF5A6377),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFBFE),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF0F1F7),
    surfaceContainerHighest = Color(0xFFE8EAF2),
    outline = Color(0xFFC5CAD8),
    outlineVariant = Color(0xFFE1E4EE),
    error = Color(0xFFD92D50),
)

private val BaseType = Typography()

private val PulseTypography = Typography(
    displayLarge = BaseType.displayLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp),
    displayMedium = BaseType.displayMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
    displaySmall = BaseType.displaySmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineLarge = BaseType.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineMedium = BaseType.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
    headlineSmall = BaseType.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = BaseType.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = BaseType.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = BaseType.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

/** Digits that don't jiggle as live numbers change. */
val Numbers = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun PulseTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val pulse = if (dark) {
        PulseColors(true, Color(0xFF111625), Color(0x1AFFFFFF), Color(0x24FFFFFF), Color(0x12FFFFFF), Color(0xFF6B7488))
    } else {
        PulseColors(false, Color.White, Color(0x140E1220), Color(0x1A0E1220), Color(0x0F0E1220), Color(0xFF8A93A6))
    }
    CompositionLocalProvider(LocalPulseColors provides pulse) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = PulseTypography, content = content)
    }
}
