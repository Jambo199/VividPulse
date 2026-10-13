package com.jambo199.vividpulse.ui.tools

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jambo199.vividpulse.ui.Routes
import com.jambo199.vividpulse.ui.TabBottomPadding
import com.jambo199.vividpulse.ui.components.AmbientGlow
import com.jambo199.vividpulse.ui.components.GlassCard
import com.jambo199.vividpulse.ui.components.IconBadge
import com.jambo199.vividpulse.ui.components.entrance
import com.jambo199.vividpulse.ui.components.sharedCard
import com.jambo199.vividpulse.ui.theme.Accent
import com.jambo199.vividpulse.ui.theme.Accents

private data class Tool(val title: String, val note: String, val icon: ImageVector, val accent: Accent, val route: String?)

@Composable
fun ToolsScreen(open: (String) -> Unit) {
    val context = LocalContext.current
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val tools = listOf(
        Tool("Speed test", "Download, upload and response time", Icons.Rounded.Speed, Accents.games, Routes.SPEED),
        Tool("Free up RAM", "Watch Android make room, step by step", Icons.Rounded.CleaningServices, Accents.memory, Routes.FREE_RAM),
        Tool("Smoothness", "Frame rate and dropped frames", Icons.Rounded.Animation, Accents.download, Routes.FPS),
        Tool("Dead pixels", "Full-screen colours to spot faults", Icons.Rounded.GridOn, Accents.upload, Routes.SCREEN),
        Tool("Touch test", "Draw with every finger at once", Icons.Rounded.TouchApp, Accents.cpu, Routes.TOUCH),
        Tool("Sensors", "Watch each sensor live", Icons.Rounded.Sensors, Accents.battery, Routes.SENSORS),
        Tool("Vibration", "Tap to feel the motor", Icons.Rounded.Vibration, Accents.memory, null),
    )
    Box(Modifier.fillMaxSize()) {
        AmbientGlow(listOf(Accents.download, Accents.games, Accents.battery), Modifier.fillMaxWidth().height(380.dp))
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 8.dp, bottom = TabBottomPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.padding(start = 4.dp, bottom = 4.dp).entrance(0)) {
                    Text("Tools", style = MaterialTheme.typography.headlineLarge)
                    Text("Check your phone and connection", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            tools.chunked(2).forEachIndexed { row, pair ->
                item {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max).entrance(row + 1), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { t ->
                            val m = Modifier.weight(1f).fillMaxHeight().then(if (t.route != null) Modifier.sharedCard("tool-${t.route}") else Modifier)
                            GlassCard(m, accent = t.accent, onClick = { if (t.route != null) open(t.route) else buzz(context) }) {
                                IconBadge(t.icon, t.accent, size = 44.dp)
                                Spacer(Modifier.height(14.dp))
                                Text(t.title, style = MaterialTheme.typography.titleMedium)
                                Text(t.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A short rising pattern so you can feel the motor's range. */
private fun buzz(context: Context) {
    val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }
    vibrator ?: return
    val timings = longArrayOf(0, 40, 80, 60, 80, 90, 80, 160)
    val amps = intArrayOf(0, 60, 0, 120, 0, 190, 0, 255)
    if (vibrator.hasAmplitudeControl()) {
        vibrator.vibrate(VibrationEffect.createWaveform(timings, amps, -1))
    } else {
        vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
    }
}
