package com.jambo199.vividpulse.games

import org.json.JSONObject
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode

/** The cheapest prices for a game across the stores GG.deals compares. */
data class BestPrices(
    val appId: Int,
    val title: String?,
    /** The game's GG.deals page, listing every store. */
    val url: String?,
    val currency: String?,
    /** The cheapest official store (Steam, Fanatical, Humble, GMG and so on). */
    val retailCents: Long?,
    /** The cheapest key seller (CDKeys, Eneba, Kinguin, Instant Gaming and so on). */
    val keyshopCents: Long?,
    val lowestRetailCents: Long?,
    val lowestKeyshopCents: Long?,
) {
    val bestCents: Long? get() = listOfNotNull(retailCents, keyshopCents).minOrNull()
    val bestIsKeyshop: Boolean get() = keyshopCents != null && (retailCents == null || keyshopCents < retailCents)
    val lowestCents: Long? get() = listOfNotNull(lowestRetailCents, lowestKeyshopCents).minOrNull()
}

/** GG.deals' price API (needs your free key from gg.deals/api). */
object GgDeals {
    const val KEY_PAGE = "https://gg.deals/api/"
    const val MAX_IDS = 100

    fun pricesUrl(appIds: List<Int>, key: String, region: String?): String =
        "https://api.gg.deals/v1/prices/by-steam-app-id/?ids=${appIds.joinToString(",")}&key=${Http.encode(key.trim())}" +
            (region?.let { "&region=$it" } ?: "")

    /** GG.deals' region for a country: its own code, which it knows for most. */
    fun region(country: String): String = country.lowercase()

    /** The prices for each game it knows. Throws with GG.deals' own message when it refuses. */
    fun parse(json: String): Map<Int, BestPrices> {
        val o = JSONObject(json)
        val data = o.opt("data")
        if (!o.optBoolean("success", true) || data !is JSONObject) {
            val message = (data as? JSONObject)?.optString("message")?.ifBlank { null } ?: o.optString("message").ifBlank { "GG.deals didn't send prices" }
            throw IOException(message)
        }
        val result = mutableMapOf<Int, BestPrices>()
        for (id in data.keys()) {
            val appId = id.toIntOrNull() ?: continue
            val game = data.optJSONObject(id) ?: continue
            val prices = game.optJSONObject("prices") ?: continue
            result[appId] = BestPrices(
                appId = appId,
                title = game.optString("title").ifBlank { null },
                url = game.optString("url").ifBlank { null },
                currency = prices.optString("currency").ifBlank { null },
                retailCents = cents(prices.opt("currentRetail")),
                keyshopCents = cents(prices.opt("currentKeyshops")),
                lowestRetailCents = cents(prices.opt("historicalRetail")),
                lowestKeyshopCents = cents(prices.opt("historicalKeyshops")),
            )
        }
        return result
    }

    /** "24.99" or 24.99 → 2499. */
    fun cents(value: Any?): Long? = when (value) {
        null, JSONObject.NULL -> null
        is Number -> BigDecimal(value.toString()).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
        is String -> value.trim().replace(",", "").toBigDecimalOrNull()?.movePointRight(2)?.setScale(0, RoundingMode.HALF_UP)?.toLong()
        else -> null
    }?.takeIf { it >= 0 }
}

/** Store searches for a game, for comparing by hand. Key sellers are marked as such. */
data class StoreLink(val name: String, val keySeller: Boolean, val url: String)

object StoreLinks {
    fun forGame(name: String): List<StoreLink> {
        val q = Http.encode(name)
        return listOf(
            StoreLink("AllKeyShop", true, "https://www.allkeyshop.com/blog/catalogue/search-$q/"),
            StoreLink("CDKeys", true, "https://www.cdkeys.com/catalogsearch/result/?q=$q"),
            StoreLink("Eneba", true, "https://www.eneba.com/store?text=$q"),
            StoreLink("Kinguin", true, "https://www.kinguin.net/listing?phrase=$q"),
            StoreLink("Instant Gaming", true, "https://www.instant-gaming.com/en/search/?q=$q"),
            StoreLink("G2A", true, "https://www.g2a.com/search?query=$q"),
            StoreLink("Fanatical", false, "https://www.fanatical.com/en/search?search=$q"),
            StoreLink("Humble", false, "https://www.humblebundle.com/store/search?search=$q"),
            StoreLink("Green Man Gaming", false, "https://www.greenmangaming.com/search/?query=$q"),
        )
    }
}
