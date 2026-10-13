package com.jambo199.vividpulse.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jambo199.vividpulse.Graph
import com.jambo199.vividpulse.alerts.Background
import com.jambo199.vividpulse.games.GgDeals
import com.jambo199.vividpulse.games.friendlyMessage
import com.jambo199.vividpulse.ui.Routes
import com.jambo199.vividpulse.ui.components.CardTitle
import com.jambo199.vividpulse.ui.components.GlassCard
import com.jambo199.vividpulse.ui.components.PillButton
import com.jambo199.vividpulse.ui.theme.Accents
import kotlinx.coroutines.launch

/** Sale alerts for starred games, Steam's big sales, and a way to the charging companion. */
@Composable
fun AlertsCard(open: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings by Graph.settings.state.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pending?.invoke()
        pending = null
    }
    fun turnOn(apply: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            pending = apply
            askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            apply()
        }
    }
    GlassCard(modifier.fillMaxWidth(), accent = Accents.storage) {
        CardTitle(Icons.Rounded.NotificationsActive, "Alerts", Accents.storage)
        Spacer(Modifier.height(8.dp))
        Toggle("Starred games on sale", "Star a game and pick when to hear: any sale, or below a price", settings.saleAlerts) { on ->
            val apply = { Graph.settings.update { it.copy(saleAlerts = on) } }
            if (on) turnOn(apply) else apply()
        }
        Toggle("Steam's big sales", "When the Winter, Spring, Summer or Autumn Sale starts", settings.bigSaleAlerts) { on ->
            val apply = { Graph.settings.update { it.copy(bigSaleAlerts = on) } }
            if (on) turnOn(apply) else apply()
        }
        Text(
            "Prices are checked every few hours.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Check now", Accents.storage, { Background.checkDealsNow(context) })
            PillButton("Charging alerts", Accents.charging, { open(Routes.BATTERY) })
        }
    }
}

@Composable
private fun Toggle(title: String, note: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(on, onChange)
    }
}

/** The GG.deals key, for prices at other stores and key sellers. */
@Composable
fun PriceComparisonCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by Graph.settings.state.collectAsStateWithLifecycle()
    var key by remember { mutableStateOf(settings.ggDealsKey) }
    var status by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    fun save() {
        Graph.settings.update { it.copy(ggDealsKey = key.trim()) }
        if (key.isBlank()) {
            status = null
            return
        }
        testing = true
        status = "Checking the key…"
        scope.launch {
            // Counter-Strike 2 and Baldur's Gate 3: any working key gets prices for these.
            status = runCatching { Graph.games.bestPrices(listOf(730, 1086940), force = true) }.fold(
                onSuccess = { if (it.isNotEmpty()) "Working: prices will show on deals and game pages" else "The key works, but no prices came back" },
                onFailure = { "Didn't work: ${it.friendlyMessage()}" },
            )
            testing = false
        }
    }
    GlassCard(modifier.fillMaxWidth(), accent = Accents.battery) {
        CardTitle(Icons.Rounded.Sell, "Compare prices", Accents.battery, trailing = "GG.deals")
        Text(
            "See the cheapest price at other stores and key sellers (CDKeys, Eneba, Kinguin and more) on every deal and game page. It needs a free key from GG.deals: sign in there, open the API page and copy your key.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
        )
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text("GG.deals API key") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        status?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (it.startsWith("Didn't")) MaterialTheme.colorScheme.error else Accents.battery.start,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(if (testing) "Checking…" else "Save key", Accents.battery, { if (!testing) save() })
            PillButton("Get a free key", Accents.download, {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GgDeals.KEY_PAGE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            })
        }
    }
}
