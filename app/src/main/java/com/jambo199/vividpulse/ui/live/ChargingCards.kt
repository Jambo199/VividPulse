package com.jambo199.vividpulse.ui.live

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jambo199.vividpulse.Graph
import com.jambo199.vividpulse.alerts.Background
import com.jambo199.vividpulse.alerts.ChargeSession
import com.jambo199.vividpulse.ui.components.CardTitle
import com.jambo199.vividpulse.ui.components.GlassCard
import com.jambo199.vividpulse.ui.components.Pill
import com.jambo199.vividpulse.ui.components.Sparkline
import com.jambo199.vividpulse.ui.components.Stat
import com.jambo199.vividpulse.ui.components.bouncyClick
import com.jambo199.vividpulse.ui.theme.Accents
import com.jambo199.vividpulse.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

private val LIMITS = listOf(80, 85, 90, 95)

/** Turns the charging companion on and off, with the charge limit and the overnight report. */
@OptIn(ExperimentalLayoutApi::class)
@SuppressLint("BatteryLife")
@Composable
fun ChargingCompanionCard(cycles: Int?, modifier: Modifier = Modifier, turnOn: Boolean = false) {
    val context = LocalContext.current
    val settings by Graph.settings.state.collectAsStateWithLifecycle()
    var exempt by remember { mutableStateOf(isExempt(context)) }
    // Turning a feature on asks for permission to notify (Android 13+); it's turned on either way.
    var pendingTurnOn by remember { mutableStateOf<(() -> Unit)?>(null) }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingTurnOn?.invoke()
        pendingTurnOn = null
    }
    fun withNotifications(turnOn: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            pendingTurnOn = turnOn
            askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            turnOn()
        }
    }
    // Opened to turn it on (from a link, or the automated test).
    LaunchedEffect(turnOn) {
        if (turnOn && !Graph.settings.current.chargeCompanion) {
            withNotifications {
                Graph.settings.update { it.copy(chargeCompanion = true) }
                Background.schedule(context)
            }
        }
    }
    GlassCard(modifier.fillMaxWidth(), accent = Accents.charging) {
        CardTitle(Icons.Rounded.BatteryChargingFull, "Charging companion", Accents.charging)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Every charge: a live notification with the level and power, an alert at your limit, and a history of your charges.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Switch(settings.chargeCompanion, { on ->
                val apply = {
                    Graph.settings.update { it.copy(chargeCompanion = on) }
                    Background.schedule(context)
                }
                if (on) withNotifications(apply) else apply()
            })
        }
        AnimatedVisibility(settings.chargeCompanion, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column {
                Spacer(Modifier.height(10.dp))
                Text("Alert me at", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LIMITS.forEach { l ->
                        Pill("$l%", Accents.charging, Modifier.clip(CircleShape).bouncyClick { Graph.settings.update { it.copy(chargeLimit = l) } }, filled = settings.chargeLimit == l)
                    }
                    Pill("No alert", Accents.charging, Modifier.clip(CircleShape).bouncyClick { Graph.settings.update { it.copy(chargeLimit = 100) } }, filled = settings.chargeLimit >= 100)
                }
                Text(
                    "Stopping at 80–90% most of the time puts less wear on a battery than charging to 100% every time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                if (!exempt) {
                    Text(
                        "Allow unrestricted battery use so vivo doesn't hold the companion back",
                        style = MaterialTheme.typography.labelLarge,
                        color = Accents.charging.start,
                        modifier = Modifier.padding(top = 6.dp).clip(CircleShape).bouncyClick {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                            exempt = isExempt(context)
                        }.padding(vertical = 8.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Overnight report", style = MaterialTheme.typography.bodyMedium)
                Text("In the morning: how much battery the night used", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(settings.overnightReport, { on ->
                val apply = { Graph.settings.update { it.copy(overnightReport = on) } }
                if (on) withNotifications(apply) else apply()
            })
        }
        cycles?.let {
            Spacer(Modifier.height(8.dp))
            Text("Charge cycles so far: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun isExempt(context: android.content.Context): Boolean =
    context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

/** The last few charges, each with its curve. */
@Composable
fun RecentChargesCard(modifier: Modifier = Modifier) {
    val sessions by produceState<List<ChargeSession>?>(null) {
        while (true) {
            value = withContext(Dispatchers.IO) { Graph.chargeHistory.sessions() }
            delay(30_000)
        }
    }
    val list = sessions?.filter { it.points.size >= 2 }.orEmpty()
    if (list.isEmpty()) return
    GlassCard(modifier.fillMaxWidth(), accent = Accents.battery) {
        CardTitle(Icons.Rounded.History, "Recent charges", Accents.battery, trailing = "${list.size}")
        list.take(6).forEach { s -> ChargeRow(s) }
    }
}

@Composable
private fun ChargeRow(s: ChargeSession) {
    var open by rememberSaveable(s.startedAt) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 10.dp).clip(MaterialTheme.shapes.medium).bouncyClick { open = !open }) {
        val day = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(s.startedAt))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${s.startLevel.roundToInt()}% → ${s.endLevel.roundToInt()}%", style = MaterialTheme.typography.titleMedium)
                Text(
                    "$day · ${Format.duration(s.durationMs)}" + (if (s.peakWatts > 0f) " · peak ${Format.watts(s.peakWatts)}" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (s.limitAlerted) Pill("Limit reached", Accents.charging, filled = false)
        }
        Spacer(Modifier.height(6.dp))
        Sparkline(s.points.map { it.level }, Accents.battery, Modifier.fillMaxWidth().height(36.dp), max = 100f)
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column {
                val watts = s.points.mapNotNull { it.watts }
                if (watts.size >= 2) {
                    Text("Charging power", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    Sparkline(watts, Accents.charging, Modifier.fillMaxWidth().height(44.dp))
                }
                Row(Modifier.padding(top = 8.dp)) {
                    Stat("Gained", "+${s.gained.roundToInt()}%", Modifier.weight(1f))
                    Stat("Average", s.averageWatts?.let { Format.watts(it) } ?: "—", Modifier.weight(1f))
                    Stat("Hottest", s.peakTempC?.let { Format.temperature(it, Graph.settings.current.fahrenheit) } ?: "—", Modifier.weight(1f))
                }
            }
        }
    }
}
