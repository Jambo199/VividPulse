package com.jambo199.vividpulse.games

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** A game Epic is giving away, now or soon. */
data class EpicGame(
    val title: String,
    val description: String,
    val imageUrl: String?,
    val url: String,
    val freeNow: Boolean,
    /** Epoch ms the giveaway starts / ends. */
    val startsAt: Long?,
    val endsAt: Long?,
    val originalPrice: String?,
)

object Epic {
    fun freeGamesUrl(country: String) =
        "https://store-site-backend-static-ipv4.ak.epicgames.com/freeGamesPromotions?locale=en-US&country=$country&allowCountries=$country"

    /** Free now first, then the ones coming next. */
    fun parseFreeGames(json: String, nowMs: Long = System.currentTimeMillis()): List<EpicGame> {
        val elements = JSONObject(json).optJSONObject("data")?.optJSONObject("Catalog")
            ?.optJSONObject("searchStore")?.optJSONArray("elements") ?: return emptyList()
        val games = (0 until elements.length()).mapNotNull { i -> parse(elements.getJSONObject(i), nowMs) }
        return games.sortedWith(compareBy({ !it.freeNow }, { it.startsAt ?: 0L }))
    }

    private fun parse(o: JSONObject, nowMs: Long): EpicGame? {
        val promotions = o.optJSONObject("promotions") ?: return null
        val current = freeOffers(promotions.optJSONArray("promotionalOffers"))
            .firstOrNull { (start, end) -> (start == null || start <= nowMs) && (end == null || end > nowMs) }
        val upcoming = freeOffers(promotions.optJSONArray("upcomingPromotionalOffers"))
            .firstOrNull { (start, _) -> start == null || start > nowMs }
        val offer = current ?: upcoming ?: return null
        val title = o.optString("title").ifBlank { return null }
        return EpicGame(
            title = title,
            description = o.optString("description").takeIf { it != title }.orEmpty(),
            imageUrl = image(o.optJSONArray("keyImages")),
            url = "https://store.epicgames.com/p/" + (slug(o) ?: return null),
            freeNow = current != null,
            startsAt = offer.first,
            endsAt = offer.second,
            originalPrice = o.optJSONObject("price")?.optJSONObject("totalPrice")?.optJSONObject("fmtPrice")
                ?.optString("originalPrice")?.takeIf { it.isNotBlank() && it != "0" },
        )
    }

    /** Start and end (epoch ms) of each 100%-off offer. */
    private fun freeOffers(groups: JSONArray?): List<Pair<Long?, Long?>> {
        groups ?: return emptyList()
        val result = mutableListOf<Pair<Long?, Long?>>()
        for (g in 0 until groups.length()) {
            val offers = groups.optJSONObject(g)?.optJSONArray("promotionalOffers") ?: continue
            for (k in 0 until offers.length()) {
                val offer = offers.getJSONObject(k)
                val percent = offer.optJSONObject("discountSetting")?.optInt("discountPercentage", -1) ?: -1
                if (percent == 0) result += time(offer.optString("startDate")) to time(offer.optString("endDate"))
            }
        }
        return result
    }

    private fun slug(o: JSONObject): String? {
        fun fromMappings(a: JSONArray?): String? {
            a ?: return null
            for (i in 0 until a.length()) {
                val m = a.optJSONObject(i) ?: continue
                val s = m.optString("pageSlug")
                if (s.isNotBlank() && m.optString("pageType", "productHome") == "productHome") return s
            }
            return null
        }
        return fromMappings(o.optJSONObject("catalogNs")?.optJSONArray("mappings"))
            ?: fromMappings(o.optJSONArray("offerMappings"))
            ?: o.optString("productSlug").takeIf { it.isNotBlank() && it != "[]" && it != "null" }?.substringBefore('/')
            ?: o.optString("urlSlug").takeIf { it.isNotBlank() && !it.matches(Regex("[0-9a-f]{32}")) }
    }

    private fun image(a: JSONArray?): String? {
        a ?: return null
        val byType = (0 until a.length()).mapNotNull { a.optJSONObject(it) }.associate { it.optString("type") to it.optString("url") }
        return listOf("OfferImageWide", "DieselStoreFrontWide", "featuredMedia", "Thumbnail", "OfferImageTall")
            .firstNotNullOfOrNull { byType[it]?.ifBlank { null } }
            ?: byType.values.firstOrNull { it.isNotBlank() }
    }

    private fun time(iso: String): Long? = if (iso.isBlank() || iso == "null") null else runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull()
}
