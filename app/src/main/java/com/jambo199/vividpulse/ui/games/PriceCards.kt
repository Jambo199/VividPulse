package com.jambo199.vividpulse.ui.games

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jambo199.vividpulse.Graph
import com.jambo199.vividpulse.games.AlertRule
import com.jambo199.vividpulse.games.BestPrices
import com.jambo199.vividpulse.games.PriceAlert
import com.jambo199.vividpulse.games.Steam
import com.jambo199.vividpulse.games.StoreLinks
import com.jambo199.vividpulse.games.friendlyMessage
import com.jambo199.vividpulse.ui.Routes
import com.jambo199.vividpulse.ui.components.CardTitle
import com.jambo199.vividpulse.ui.components.GlassCard
import com.jambo199.vividpulse.ui.components.Pill
import com.jambo199.vividpulse.ui.components.ShimmerBox
import com.jambo199.vividpulse.ui.components.bouncyClick
import com.jambo199.vividpulse.ui.theme.Accent
import com.jambo199.vividpulse.ui.theme.Accents
import com.jambo199.vividpulse.ui.theme.Numbers

/**
 * Where else to buy a game, and for how much: the cheapest official store and key seller from
 * GG.deals (with your key), and searches on the usual stores.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PriceCompareCard(appId: Int, name: String, steamCents: Long?, steamPrice: String?, open: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    fun openUrl(url: String) = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    val settings by Graph.settings.state.collectAsStateWithLifecycle()
    val hasKey = settings.ggDealsKey.isNotBlank()
    val prices by produceState<Result<BestPrices?>?>(null, appId, settings.ggDealsKey) {
        value = if (hasKey) runCatching { Graph.games.bestPrices(listOf(appId))[appId] } else Result.success(null)
    }
    GlassCard(modifier.fillMaxWidth(), accent = Accents.battery) {
        CardTitle(Icons.Rounded.Sell, "Cheaper elsewhere?", Accents.battery, trailing = if (hasKey) "GG.deals" else null)
        Spacer(Modifier.height(8.dp))
        val result = prices
        when {
            !hasKey -> {
                Text(
                    "Add your free GG.deals key to see the cheapest official store and key seller right here, for every deal.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Add a key in Settings",
                    style = MaterialTheme.typography.labelLarge,
                    color = Accents.battery.start,
                    modifier = Modifier.clip(CircleShape).bouncyClick { open(Routes.SETTINGS) }.padding(vertical = 8.dp),
                )
            }
            result == null -> ShimmerBox(Modifier.fillMaxWidth().height(96.dp))
            result.isFailure -> Text(
                "Couldn't get prices: ${result.exceptionOrNull()?.friendlyMessage() ?: "unknown error"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            result.getOrNull() == null -> Text(
                "GG.deals doesn't list this game.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> {
                val p = result.getOrNull()!!
                fun money(cents: Long?) = cents?.let { Steam.money(it, p.currency) } ?: "—"
                PriceRow("Steam", steamPrice ?: "—", null, Accents.games)
                PriceRow("Cheapest official store", money(p.retailCents), saving(p.retailCents, steamCents), Accents.download)
                PriceRow("Cheapest key seller", money(p.keyshopCents), saving(p.keyshopCents, steamCents), Accents.battery)
                if (p.lowestCents != null) {
                    Text(
                        "All-time low: ${money(p.lowestRetailCents)} official · ${money(p.lowestKeyshopCents)} key sellers",
                        style = MaterialTheme.typography.bodySmall.merge(Numbers),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                p.url?.let { url ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Compare every store on GG.deals",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(CircleShape).background(Accents.battery.brush).bouncyClick { openUrl(url) }.padding(horizontal = 18.dp, vertical = 11.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Search for it on", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StoreLinks.forGame(name).forEach { s ->
                Pill(
                    s.name,
                    if (s.keySeller) Accents.storage else Accents.download,
                    Modifier.clip(CircleShape).bouncyClick { openUrl(s.url) },
                    filled = false,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Yellow: key sellers, which resell game keys. Usually the cheapest, but check the key's region and the seller's rating. Blue: official stores.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PriceRow(label: String, price: String, saving: String?, accent: Accent) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        saving?.let {
            Pill(it, accent)
            Spacer(Modifier.width(8.dp))
        }
        Text(price, style = MaterialTheme.typography.titleMedium.merge(Numbers), color = accent.start)
    }
}

/** "save 35%" when [cents] beats Steam's price. */
private fun saving(cents: Long?, steamCents: Long?): String? {
    if (cents == null || steamCents == null || steamCents <= 0 || cents >= steamCents) return null
    return "save ${((steamCents - cents) * 100 / steamCents)}%"
}

/** A starred game's price alert: any sale, or below a price you pick. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PriceAlertCard(appId: Int, regularCents: Long?, currencyHint: String?, modifier: Modifier = Modifier) {
    val alerts by Graph.watching.alerts.collectAsStateWithLifecycle()
    val alert = alerts[appId] ?: PriceAlert()
    val settings by Graph.settings.state.collectAsStateWithLifecycle()
    GlassCard(modifier.fillMaxWidth(), accent = Accents.storage) {
        CardTitle(Icons.Rounded.NotificationsActive, "Price alert", Accents.storage)
        Spacer(Modifier.height(6.dp))
        Text(
            when (alert.rule) {
                AlertRule.SALE -> "You'll get a notification when it goes on sale, or the sale gets bigger."
                AlertRule.PRICE -> "You'll get a notification when it's ${alert.targetCents?.let { Steam.money(it, currencyHint) } ?: "at your price"} or less" +
                    (if (alert.anywhere && settings.ggDealsKey.isNotBlank()) " anywhere, key sellers included." else " on Steam.")
                AlertRule.OFF -> "Starred, without price alerts."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!settings.saleAlerts) {
            Text("Price alerts are turned off in Settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Any sale", alert.rule == AlertRule.SALE) { Graph.watching.setAlert(appId, alert.copy(rule = AlertRule.SALE)) }
            // Targets at a quarter, half and three quarters off the regular price.
            regularCents?.takeIf { it > 0 }?.let { full ->
                listOf(75, 50, 25).forEach { pct ->
                    val target = full * pct / 100
                    Choice(
                        "${Steam.money(target, currencyHint)} or less",
                        alert.rule == AlertRule.PRICE && alert.targetCents == target,
                    ) { Graph.watching.setAlert(appId, alert.copy(rule = AlertRule.PRICE, targetCents = target)) }
                }
            }
            Choice("Off", alert.rule == AlertRule.OFF) { Graph.watching.setAlert(appId, alert.copy(rule = AlertRule.OFF)) }
        }
        if (alert.rule == AlertRule.PRICE && settings.ggDealsKey.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("Anywhere", alert.anywhere) { Graph.watching.setAlert(appId, alert.copy(anywhere = true)) }
                Choice("Steam only", !alert.anywhere) { Graph.watching.setAlert(appId, alert.copy(anywhere = false)) }
            }
        }
    }
}

@Composable
private fun Choice(text: String, on: Boolean, onClick: () -> Unit) {
    Pill(text, Accents.storage, Modifier.clip(CircleShape).bouncyClick(onClick = onClick), filled = on)
}
