package com.jambo199.vividpulse.games

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** When to hear about a starred game's price. */
enum class AlertRule(val label: String) {
    SALE("Any sale"),
    PRICE("Below a price"),
    OFF("No alerts"),
}

/** A starred game's price alert. [anywhere] counts other stores and key sellers too (with a GG.deals key). */
data class PriceAlert(val rule: AlertRule = AlertRule.SALE, val targetCents: Long? = null, val anywhere: Boolean = true)

/** A starred game's price the last time it was checked. */
data class SeenPrice(val discount: Int, val cents: Long?, val bestCents: Long?)

/**
 * Games you've starred, so their live player counts sit at the top of the Games page, with the
 * price alert for each.
 */
class WatchStore(context: Context) {
    private val prefs = context.getSharedPreferences("watching", Context.MODE_PRIVATE)
    private val _games = MutableStateFlow(read())
    val games: StateFlow<List<Game>> = _games.asStateFlow()
    private val _alerts = MutableStateFlow(readAlerts())
    val alerts: StateFlow<Map<Int, PriceAlert>> = _alerts.asStateFlow()

    fun isWatched(appId: Int): Boolean = _games.value.any { it.appId == appId }

    fun toggle(game: Game) {
        val now = _games.value
        _games.value = if (now.any { it.appId == game.appId }) now.filter { it.appId != game.appId } else listOf(game) + now
        if (!isWatched(game.appId)) {
            _alerts.value = _alerts.value - game.appId
            prefs.edit().remove("seen-${game.appId}").apply()
        }
        save()
    }

    fun alert(appId: Int): PriceAlert = _alerts.value[appId] ?: PriceAlert()

    fun setAlert(appId: Int, alert: PriceAlert) {
        _alerts.value = _alerts.value + (appId to alert)
        save()
    }

    fun seen(appId: Int): SeenPrice? = prefs.getString("seen-$appId", null)?.let { s ->
        runCatching {
            val o = JSONObject(s)
            SeenPrice(o.optInt("discount"), o.optLong("cents", -1).takeIf { it >= 0 }, o.optLong("best", -1).takeIf { it >= 0 })
        }.getOrNull()
    }

    fun saveSeen(appId: Int, seen: SeenPrice) {
        val o = JSONObject().put("discount", seen.discount).put("cents", seen.cents ?: -1).put("best", seen.bestCents ?: -1)
        prefs.edit().putString("seen-$appId", o.toString()).apply()
    }

    private fun save() {
        val a = JSONArray(_games.value.map { JSONObject().put("appId", it.appId).put("name", it.name).put("imageUrl", it.imageUrl) })
        val alerts = JSONObject()
        _alerts.value.forEach { (id, al) ->
            alerts.put(id.toString(), JSONObject().put("rule", al.rule.name).put("target", al.targetCents ?: -1).put("anywhere", al.anywhere))
        }
        prefs.edit().putString("games", a.toString()).putString("alerts", alerts.toString()).apply()
    }

    private fun read(): List<Game> = runCatching {
        val a = JSONArray(prefs.getString("games", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Game(appId = o.getInt("appId"), name = o.getString("name"), imageUrl = o.getString("imageUrl"), rank = i + 1)
        }
    }.getOrDefault(emptyList())

    private fun readAlerts(): Map<Int, PriceAlert> = runCatching {
        val o = JSONObject(prefs.getString("alerts", "{}") ?: "{}")
        o.keys().asSequence().mapNotNull { k ->
            val id = k.toIntOrNull() ?: return@mapNotNull null
            val a = o.getJSONObject(k)
            id to PriceAlert(
                rule = AlertRule.entries.firstOrNull { it.name == a.optString("rule") } ?: AlertRule.SALE,
                targetCents = a.optLong("target", -1).takeIf { it >= 0 },
                anywhere = a.optBoolean("anywhere", true),
            )
        }.toMap()
    }.getOrDefault(emptyMap())
}

/** Whether a starred game's price is news. */
object SaleAlerts {
    enum class Why { SALE, DEEPER_SALE, TARGET }

    /**
     * Compares the price now with the last check. The first check only notes the price: a game
     * that was already on sale when you starred it isn't news.
     */
    fun decide(alert: PriceAlert, before: SeenPrice?, now: SeenPrice): Why? {
        if (before == null) return null
        return when (alert.rule) {
            AlertRule.OFF -> null
            AlertRule.SALE -> when {
                now.discount > 0 && before.discount == 0 -> Why.SALE
                before.discount > 0 && now.discount >= before.discount + 10 -> Why.DEEPER_SALE
                else -> null
            }
            AlertRule.PRICE -> {
                val target = alert.targetCents ?: return null
                val priceNow = cheapest(now, alert.anywhere) ?: return null
                val priceBefore = cheapest(before, alert.anywhere)
                if (priceNow <= target && (priceBefore == null || priceBefore > target)) Why.TARGET else null
            }
        }
    }

    fun cheapest(p: SeenPrice, anywhere: Boolean): Long? =
        if (anywhere) listOfNotNull(p.cents, p.bestCents).minOrNull() else p.cents
}
