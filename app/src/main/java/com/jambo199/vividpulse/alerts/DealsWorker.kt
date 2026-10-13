package com.jambo199.vividpulse.alerts

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jambo199.vividpulse.Graph
import com.jambo199.vividpulse.games.BestPrices
import com.jambo199.vividpulse.games.Chart
import com.jambo199.vividpulse.games.Game
import com.jambo199.vividpulse.games.SaleAlerts
import com.jambo199.vividpulse.games.SeenPrice
import com.jambo199.vividpulse.games.Steam
import com.jambo199.vividpulse.games.SteamSales
import com.jambo199.vividpulse.games.StoreItem
import com.jambo199.vividpulse.widget.Widgets
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Every few hours: checks starred games' prices for the alerts you set, says when one of Steam's
 * big sales starts, and refreshes the deals widget.
 */
class DealsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val settings = Graph.settings.current
        val prefs = context.getSharedPreferences("alerts", Context.MODE_PRIVATE)

        if (settings.bigSaleAlerts) {
            SteamSales.current(Instant.now())?.let { sale ->
                val id = sale.name + sale.start
                if (prefs.getString("bigSale", null) != id) {
                    val ends = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()).format(sale.end.atZone(ZoneId.systemDefault()))
                    Notify.post(
                        context,
                        Notify.ID_BIG_SALE,
                        Notify.builder(context, Notify.DEALS)
                            .setContentTitle("The ${sale.name} is on")
                            .setContentText("Until $ends. Your starred games and the best deals are on the Games tab.")
                            .setAutoCancel(true)
                            .setContentIntent(Notify.openApp(context, "games/deals", 4)),
                    )
                    prefs.edit().putString("bigSale", id).apply()
                }
            }
        }

        val watched = Graph.watching.games.value
        if (settings.saleAlerts && watched.isNotEmpty()) {
            val ids = watched.map { it.appId }
            val items = runCatching { Graph.games.storeItems(ids) }.getOrDefault(emptyMap())
            val best = runCatching { Graph.games.bestPrices(ids, force = true) }
                .onFailure { Log.w(TAG, "GG.deals prices failed", it) }
                .getOrDefault(emptyMap())
            var told = 0
            for (g in watched) {
                val item = items[g.appId] ?: continue
                val now = SeenPrice(item.discountPercent, item.priceCents, best[g.appId]?.bestCents)
                val why = SaleAlerts.decide(Graph.watching.alert(g.appId), Graph.watching.seen(g.appId), now)
                if (why != null) {
                    tell(context, g, item, best[g.appId], why)
                    told++
                }
                Graph.watching.saveSeen(g.appId, now)
            }
            Log.i(TAG, "Checked ${watched.size} starred games, $told alerts")
        }

        // The deals widget shows the latest list.
        runCatching { Graph.games.chart(Chart.DEALS, force = false) }
        Widgets.updateDeals(context)
        return Result.success()
    }

    private fun tell(context: Context, g: Game, item: StoreItem, best: BestPrices?, why: SaleAlerts.Why) {
        val name = item.name.ifBlank { g.name }
        val steam = item.price
        val was = item.originalPrice?.let { " (was $it)" }.orEmpty()
        val elsewhere = best?.bestCents?.takeIf { b -> item.priceCents == null || b < item.priceCents }?.let { b ->
            " Cheapest anywhere: ${Steam.money(b, best.currency)}" + if (best.bestIsKeyshop) " at a key seller." else "."
        }.orEmpty()
        val alert = Graph.watching.alert(g.appId)
        val (title, text) = when (why) {
            SaleAlerts.Why.SALE -> "$name is ${item.discountPercent}% off" to "$steam on Steam$was.$elsewhere"
            SaleAlerts.Why.DEEPER_SALE -> "$name is now ${item.discountPercent}% off" to "$steam on Steam$was.$elsewhere"
            SaleAlerts.Why.TARGET -> {
                val target = alert.targetCents?.let { Steam.money(it, best?.currency) } ?: ""
                val cheapest = SaleAlerts.cheapest(SeenPrice(item.discountPercent, item.priceCents, best?.bestCents), alert.anywhere)
                "$name is down to ${cheapest?.let { Steam.money(it, best?.currency) } ?: steam}" to
                    "Your alert was for $target or less. Steam: $steam.$elsewhere"
            }
        }
        Notify.post(
            context,
            Notify.gameId(g.appId),
            Notify.builder(context, Notify.DEALS)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(Notify.openApp(context, "game/${g.appId}", Notify.gameId(g.appId))),
        )
    }

    private companion object {
        const val TAG = "DealsWorker"
    }
}
