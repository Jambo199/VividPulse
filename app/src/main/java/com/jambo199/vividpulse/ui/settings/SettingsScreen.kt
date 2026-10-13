package com.jambo199.vividpulse.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jambo199.vividpulse.BuildConfig
import com.jambo199.vividpulse.Graph
import com.jambo199.vividpulse.data.RefreshRate
import com.jambo199.vividpulse.data.ThemeMode
import com.jambo199.vividpulse.ui.Routes
import com.jambo199.vividpulse.ui.components.CardTitle
import com.jambo199.vividpulse.ui.components.DetailPage
import com.jambo199.vividpulse.ui.components.GlassCard
import com.jambo199.vividpulse.ui.components.WhatsNewSheet
import com.jambo199.vividpulse.ui.components.bouncyClick
import com.jambo199.vividpulse.ui.components.entrance
import com.jambo199.vividpulse.ui.theme.Accent
import com.jambo199.vividpulse.ui.theme.Accents
import com.jambo199.vividpulse.ui.theme.LocalPulseColors
import com.jambo199.vividpulse.update.UpdateState
import android.text.format.DateUtils
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(back: () -> Unit, open: (String) -> Unit = {}) {
    val settings by Graph.settings.state.collectAsStateWithLifecycle()
    val update by Graph.updates.state.collectAsStateWithLifecycle()
    var showNotes by remember { mutableStateOf(false) }
    DetailPage("Settings", back, subtitle = "Vivid Pulse ${BuildConfig.VERSION_NAME}") {
        item {
            GlassCard(Modifier.fillMaxWidth().entrance(0), accent = Accents.memory) {
                CardTitle(Icons.Rounded.Palette, "Look", Accents.memory)
                Spacer(Modifier.height(12.dp))
                Choices(ThemeMode.entries, settings.theme, { it.label }, Accents.memory) { t -> Graph.settings.update { it.copy(theme = t) } }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth().entrance(1), accent = Accents.cpu) {
                CardTitle(Icons.Rounded.Speed, "Live readings", Accents.cpu)
                Text(
                    "How often the live pages update. Faster looks smoother; slower saves a little battery.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                )
                Choices(RefreshRate.entries, settings.refresh, { it.label }, Accents.cpu) { r -> Graph.settings.update { it.copy(refresh = r) } }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth().entrance(2), accent = Accents.thermal) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        CardTitle(Icons.Rounded.Thermostat, "Fahrenheit", Accents.thermal)
                    }
                    Switch(settings.fahrenheit, { v -> Graph.settings.update { it.copy(fahrenheit = v) } })
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        CardTitle(Icons.Rounded.LightMode, "Keep the screen on", Accents.storage)
                        Text(
                            "While Vivid Pulse is open: handy for watching a charge or temperatures while you play",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, end = 8.dp),
                        )
                    }
                    Switch(settings.keepScreenOn, { v -> Graph.settings.update { it.copy(keepScreenOn = v) } })
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth().entrance(3), accent = Accents.games) {
                CardTitle(Icons.Rounded.Public, "Store region", Accents.games)
                Text(
                    "Which country's Steam and Epic prices to show.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                )
                val phone = Locale.getDefault().country.ifBlank { "US" }
                val regions = listOf("") + listOf("GB", "US", "DE", "CN", "HK", "IN", "AU", "CA", "JP").filter { it != phone }
                Choices(regions, settings.steamCountry, { if (it.isEmpty()) "This phone ($phone)" else it }, Accents.games) { c ->
                    Graph.settings.update { it.copy(steamCountry = c) }
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth().entrance(4), accent = Accents.battery) {
                CardTitle(Icons.Rounded.SystemUpdate, "Updates", Accents.battery)
                Spacer(Modifier.height(8.dp))
                val (text, action) = when (val u = update) {
                    UpdateState.Checking -> "Checking…" to null
                    UpdateState.UpToDate -> "You have the latest version" to { Graph.updates.check(manual = true) }
                    is UpdateState.Available -> "Version ${u.info.versionName} is ready – tap to update" to { Graph.updates.download() }
                    is UpdateState.Downloading -> "Downloading… ${(u.progress * 100).toInt()}%" to null
                    is UpdateState.Ready -> "Downloaded – tap to install" to { Graph.updates.download() }
                    is UpdateState.Failed -> u.message to { if (u.info != null) Graph.updates.download() else Graph.updates.check(manual = true) }
                    UpdateState.Idle -> "Check for a new version" to { Graph.updates.check(manual = true) }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(Accents.battery.brush)
                        .bouncyClick(enabled = action != null) { action?.invoke() }
                        .padding(vertical = 14.dp, horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White)
                }
                val checked = Graph.updates.lastCheckedAt
                if (checked > 0) {
                    Text(
                        "Last checked " + DateUtils.getRelativeTimeSpanString(checked, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Check automatically", style = MaterialTheme.typography.bodyMedium)
                        Text("When the app opens, and every few hours while it's open", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(settings.autoCheckUpdates, { v -> Graph.settings.update { it.copy(autoCheckUpdates = v) } })
                }
                val notes = remember { Graph.updates.whatsNew() }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    if (notes != null) {
                        Text(
                            "What's new in ${notes.versionName}",
                            style = MaterialTheme.typography.labelLarge,
                            color = Accents.battery.start,
                            modifier = Modifier.clip(CircleShape).bouncyClick { showNotes = true }.padding(vertical = 8.dp),
                        )
                    }
                    Text(
                        "Changelog",
                        style = MaterialTheme.typography.labelLarge,
                        color = Accents.brand.start,
                        modifier = Modifier.clip(CircleShape).bouncyClick { open(Routes.CHANGELOG) }.padding(vertical = 8.dp),
                    )
                }
                if (notes != null && showNotes) {
                    WhatsNewSheet(notes, onSeeAll = {
                        showNotes = false
                        open(Routes.CHANGELOG)
                    }) { showNotes = false }
                }
            }
        }
        item {
            Text(
                "Vivid Pulse reads your phone's sensors and system files on the phone itself; nothing about your phone leaves it. Game charts come from Steam and the Epic Games Store; the speed test uses Cloudflare.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp).entrance(5),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Choices(options: List<T>, selected: T, label: (T) -> String, accent: Accent, onPick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            val on = o == selected
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (on) Color.Transparent else LocalPulseColors.current.track.copy(alpha = 0.5f))
                    .background(accent.brush, alpha = if (on) 1f else 0f)
                    .bouncyClick { onPick(o) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(label(o), style = MaterialTheme.typography.labelLarge, color = if (on) Color.White else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
