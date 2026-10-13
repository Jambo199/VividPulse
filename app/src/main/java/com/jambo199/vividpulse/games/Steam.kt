package com.jambo199.vividpulse.games

import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

/** A game in one of the charts. */
data class Game(
    val appId: Int,
    val name: String,
    val imageUrl: String,
    val rank: Int,
    /** Last week's place; null when it's new to the chart. */
    val lastWeekRank: Int? = null,
    val playersNow: Long? = null,
    val peakToday: Long? = null,
    val price: String? = null,
    val originalPrice: String? = null,
    val discountPercent: Int = 0,
    val weeksOnChart: Int? = null,
    /** When a deal ends (epoch seconds). */
    val dealEnds: Long? = null,
    /** The price in cents, for sorting by price. */
    val priceCents: Long? = null,
    /** "Coming 2026", "12 Mar 2026" for games not out yet. */
    val release: String? = null,
    /** Steam reviews: "Very Positive", how many and % positive. */
    val reviewLabel: String? = null,
    val reviewCount: Long? = null,
    val reviewPercent: Int? = null,
    /** The cheapest prices at other stores and key sellers (with a GG.deals key). */
    val elsewhere: BestPrices? = null,
) {
    /** The cheapest price somewhere other than Steam, when it beats Steam's. */
    val cheaperElsewhere: Long?
        get() {
            val best = elsewhere?.bestCents ?: return null
            val steam = priceCents ?: return null
            return best.takeIf { it < steam }
        }

    /** Places climbed (+) or dropped (−) since last week; null when new. */
    val rankChange: Int? get() = lastWeekRank?.takeIf { it > 0 }?.let { it - rank }

    val storeUrl: String get() = "https://store.steampowered.com/app/$appId"
}

/** What the store says about one game. */
data class GameDetail(
    val appId: Int,
    val name: String,
    val description: String,
    val imageUrl: String,
    val backgroundUrl: String?,
    val developers: List<String>,
    val publishers: List<String>,
    val releaseDate: String?,
    val genres: List<String>,
    val price: String?,
    val originalPrice: String?,
    val discountPercent: Int,
    val free: Boolean,
    val windows: Boolean,
    val mac: Boolean,
    val linux: Boolean,
    val metacritic: Int?,
    val reviews: Long?,
    val screenshots: List<String>,
    val controller: String?,
    /** The price now and before any discount, in cents, and the currency (e.g. "GBP"). */
    val priceCents: Long? = null,
    val regularCents: Long? = null,
    val currency: String? = null,
)

/** A store listing's name, picture and price. */
data class StoreItem(
    val appId: Int,
    val name: String,
    val imageUrl: String,
    val price: String?,
    val originalPrice: String?,
    val discountPercent: Int,
    val priceCents: Long? = null,
    val dealEnds: Long? = null,
    val release: String? = null,
    val reviewLabel: String? = null,
    val reviewCount: Long? = null,
    val reviewPercent: Int? = null,
    /** Free to play. */
    val isFree: Boolean = false,
    /** Steam's kind of app: 0 is a game (others: DLC, software, soundtracks…). */
    val type: Int = 0,
    /** The tall library cover (600×900). */
    val coverUrl: String? = null,
) {
    /** A game from this listing, in place [rank]. */
    fun toGame(rank: Int) = Game(
        appId = appId,
        name = name,
        imageUrl = imageUrl,
        rank = rank,
        price = price,
        originalPrice = originalPrice,
        discountPercent = discountPercent,
        dealEnds = dealEnds,
        priceCents = priceCents,
        release = release,
        reviewLabel = reviewLabel,
        reviewCount = reviewCount,
        reviewPercent = reviewPercent,
    )

    /** [g] with this listing's name, picture, price and reviews filled in. */
    fun fill(g: Game) = g.copy(
        name = name,
        imageUrl = imageUrl,
        price = price,
        originalPrice = originalPrice,
        discountPercent = discountPercent,
        dealEnds = dealEnds ?: g.dealEnds,
        priceCents = priceCents,
        release = release ?: g.release,
        reviewLabel = reviewLabel,
        reviewCount = reviewCount,
        reviewPercent = reviewPercent,
    )
}

/** One page of a store search. */
data class SearchPage(val appIds: List<Int>, val rows: Int, val total: Int)

/** Steam store searches the app pages through. */
enum class StoreSearch(val query: String) {
    /** Games on sale, in the order the store ranks them (best sellers first). */
    DEALS("specials=1&filter=topsellers"),
    /** The most wishlisted games: the store's list of the most anticipated. */
    WISHLISTED("filter=popularwishlist"),
    /** Popular games coming out soon, for when the wishlist chart isn't available. */
    UPCOMING("filter=popularcomingsoon"),
    /** Paid games that are free to keep for now. */
    FREE_TO_KEEP("maxprice=free&specials=1"),
}

/** Steam's public web API and store endpoints, and parsers for what they return. */
object Steam {
    private const val API = "https://api.steampowered.com"
    private const val ASSETS = "https://shared.akamai.steamstatic.com/store_item_assets/"

    fun mostPlayedUrl() = "$API/ISteamChartsService/GetMostPlayedGames/v1/"

    fun playersUrl(appId: Int) = "$API/ISteamUserStats/GetNumberOfCurrentPlayers/v1/?appid=$appId"

    /** Steam's chart of games by how many are playing right now (what the store's charts page shows). */
    fun concurrentUrl() = "$API/ISteamChartsService/GetGamesByConcurrentPlayers/v1/"

    fun itemsUrl(appIds: List<Int>, country: String): String {
        val input = JSONObject()
            .put("ids", JSONArray(appIds.map { JSONObject().put("appid", it) }))
            .put("context", context(country))
            .put("data_request", DATA_REQUEST())
        return "$API/IStoreBrowseService/GetItems/v1/?input_json=" + Http.encode(input.toString())
    }

    fun topSellersUrl(country: String, count: Int = 100): String {
        val input = JSONObject()
            .put("country_code", country)
            .put("context", context(country))
            .put("data_request", DATA_REQUEST())
            .put("page_start", 0)
            .put("page_count", count)
        return "$API/IStoreTopSellersService/GetWeeklyTopSellers/v1/?input_json=" + Http.encode(input.toString())
    }

    @Suppress("FunctionName")
    private fun DATA_REQUEST() = JSONObject()
        .put("include_assets", true)
        .put("include_basic_info", true)
        .put("include_release", true)
        .put("include_reviews", true)

    /** A page of store search results ([StoreSearch]), games only. */
    fun storeSearchUrl(kind: StoreSearch, start: Int, count: Int, country: String) =
        "https://store.steampowered.com/search/results/?${kind.query}&category1=998&start=$start&count=$count&infinite=1&cc=$country&l=english"

    /**
     * A store search page: its games in order (bundles, which list several apps, are left out), how
     * many rows the page had, bundles included, so the next page starts after them, and how many
     * results there are in all.
     */
    fun parseSearchPage(json: String): SearchPage {
        val o = JSONObject(json)
        val html = o.optString("results_html")
        val ids = Regex("data-ds-appid=\"(\\d+)\"").findAll(html).map { it.groupValues[1].toInt() }.distinct().toList()
        val rows = Regex("class=\"search_result_row").findAll(html).count().coerceAtLeast(ids.size)
        return SearchPage(ids, rows, o.optInt("total_count", rows))
    }

    fun featuredUrl(country: String) = "https://store.steampowered.com/api/featuredcategories?cc=$country&l=english"

    fun detailsUrl(appId: Int, country: String) = "https://store.steampowered.com/api/appdetails?appids=$appId&cc=$country&l=english"

    fun headerFallback(appId: Int) = "${ASSETS}steam/apps/$appId/header.jpg"

    fun searchUrl(term: String, country: String) =
        "https://store.steampowered.com/api/storesearch/?term=${Http.encode(term)}&l=english&cc=$country"

    /** Store search results: games only, with their price. */
    fun parseSearch(json: String): List<Game> {
        val items = JSONObject(json).optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { i ->
            val o = items.getJSONObject(i)
            val appId = o.optInt("id", 0)
            if (appId <= 0 || o.optString("type", "app") != "app") return@mapNotNull null
            val price = o.optJSONObject("price")
            val currency = price?.optString("currency")?.ifBlank { null }
            val final = price?.optLong("final", -1) ?: -1
            val initial = price?.optLong("initial", -1) ?: -1
            Game(
                appId = appId,
                name = o.optString("name"),
                imageUrl = o.optString("tiny_image").ifBlank { headerFallback(appId) },
                rank = i + 1,
                price = when {
                    price == null -> null
                    final == 0L -> "Free"
                    else -> money(final, currency)
                },
                originalPrice = if (initial > final && final >= 0) money(initial, currency) else null,
                discountPercent = if (initial > 0 && final in 0 until initial) ((initial - final) * 100 / initial).toInt() else 0,
            )
        }
    }

    private fun context(country: String) = JSONObject()
        .put("language", "english")
        .put("country_code", country)
        .put("steam_realm", 1)

    /** Most played right now: place, app, last week's place and today's peak players. */
    fun parseMostPlayed(json: String): List<Game> {
        val ranks = JSONObject(json).getJSONObject("response").getJSONArray("ranks")
        return (0 until ranks.length()).map { i ->
            val r = ranks.getJSONObject(i)
            val appId = r.getInt("appid")
            Game(
                appId = appId,
                name = "",
                imageUrl = headerFallback(appId),
                rank = r.optInt("rank", i + 1),
                lastWeekRank = r.optInt("last_week_rank", 0).takeIf { it > 0 },
                peakToday = r.optLong("peak_in_game", 0).takeIf { it > 0 },
            )
        }
    }

    /**
     * GetNumberOfCurrentPlayers. Steam answers 0 for some games even while thousands are playing,
     * so 0 counts as "not known" rather than "nobody".
     */
    fun parsePlayers(json: String): Long? {
        val r = JSONObject(json).optJSONObject("response") ?: return null
        return if (r.optInt("result", 1) == 1 && r.has("player_count")) r.getLong("player_count").takeIf { it > 0 } else null
    }

    /** GetGamesByConcurrentPlayers: players right now for each game on Steam's chart, by app. */
    fun parseConcurrent(json: String): Map<Int, Long> {
        val ranks = JSONObject(json).optJSONObject("response")?.optJSONArray("ranks") ?: return emptyMap()
        return (0 until ranks.length()).mapNotNull { i ->
            val r = ranks.optJSONObject(i) ?: return@mapNotNull null
            val appId = r.optInt("appid", 0)
            val players = r.optLong("concurrent_in_game", 0)
            if (appId > 0 && players > 0) appId to players else null
        }.toMap()
    }

    /** IStoreBrowseService/GetItems: names, pictures and prices by app. */
    fun parseItems(json: String): Map<Int, StoreItem> {
        val items = JSONObject(json).optJSONObject("response")?.optJSONArray("store_items") ?: return emptyMap()
        return (0 until items.length()).mapNotNull { i -> parseItem(items.getJSONObject(i)) }.associateBy { it.appId }
    }

    private fun parseItem(o: JSONObject): StoreItem? {
        val appId = o.optInt("appid", o.optInt("id", 0)).takeIf { it > 0 } ?: return null
        val name = o.optString("name").ifBlank { return null }
        val purchase = o.optJSONObject("best_purchase_option")
        val free = o.optBoolean("is_free", false)
        val release = o.optJSONObject("release")
        val reviews = o.optJSONObject("reviews")?.let { it.optJSONObject("summary_filtered") ?: it.optJSONObject("summary_unfiltered") }
        val comingSoon = release?.optBoolean("is_coming_soon") == true
        return StoreItem(
            appId = appId,
            name = name,
            imageUrl = assetUrl(o.optJSONObject("assets"), "header") ?: headerFallback(appId),
            price = if (free) "Free" else purchase?.optString("formatted_final_price")?.ifBlank { null },
            originalPrice = purchase?.optString("formatted_original_price")?.ifBlank { null },
            discountPercent = purchase?.optInt("discount_pct", 0) ?: 0,
            priceCents = if (free) 0L else purchase?.optString("final_price_in_cents")?.toLongOrNull(),
            dealEnds = purchase?.optJSONArray("active_discounts")?.optJSONObject(0)?.optLong("discount_end_date", 0)?.takeIf { it > 0 },
            release = if (comingSoon) releaseText(release) else null,
            reviewLabel = reviews?.optString("review_score_label")?.ifBlank { null },
            reviewCount = reviews?.optLong("review_count", 0)?.takeIf { it > 0 },
            reviewPercent = reviews?.optInt("percent_positive", -1)?.takeIf { it >= 0 },
            isFree = free,
            type = o.optInt("type", 0),
            coverUrl = assetUrl(o.optJSONObject("assets"), "library_capsule"),
        )
    }

    /** When a game that isn't out yet arrives: the store's own words if it has them, else the date. */
    private fun releaseText(r: JSONObject?): String? {
        r ?: return null
        r.optString("custom_release_date_message").ifBlank { null }?.let { return it }
        val at = r.optLong("steam_release_date", 0).takeIf { it > 0 } ?: return "Coming soon"
        // Release dates are days, stored as midnight UTC.
        val d = Instant.ofEpochSecond(at).atZone(ZoneOffset.UTC).toLocalDate()
        return when (r.optString("coming_soon_display")) {
            "date_year" -> d.year.toString()
            "date_quarter" -> "Q${(d.monthValue - 1) / 3 + 1} ${d.year}"
            "date_month" -> DateTimeFormatter.ofPattern("MMM yyyy", Locale.UK).format(d)
            else -> DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK).format(d)
        }
    }

    /** "steam/apps/730/${FILENAME}?t=1" with "header.jpg" → the picture's full address. */
    fun assetUrl(assets: JSONObject?, key: String): String? {
        assets ?: return null
        val format = assets.optString("asset_url_format").ifBlank { return null }
        val file = assets.optString(key).ifBlank { return null }
        return ASSETS + format.replace("\${FILENAME}", file)
    }

    /** This week's top sellers, with their store listing when Steam includes it. */
    fun parseTopSellers(json: String): List<Pair<Game, StoreItem?>> {
        val ranks = JSONObject(json).getJSONObject("response").getJSONArray("ranks")
        return (0 until ranks.length()).map { i ->
            val r = ranks.getJSONObject(i)
            val item = r.optJSONObject("item")?.let(::parseItem)
            val appId = r.optInt("appid", item?.appId ?: 0)
            val g = Game(
                appId = appId,
                name = "",
                imageUrl = headerFallback(appId),
                rank = r.optInt("rank", i + 1),
                lastWeekRank = r.optInt("last_week_rank", 0).takeIf { it > 0 },
                weeksOnChart = r.optInt("consecutive_weeks", 0).takeIf { it > 0 },
            )
            (item?.fill(g) ?: g) to item
        }.filter { it.first.appId > 0 }
    }

    /** A list from the store's featured categories ("specials", "top_sellers", "new_releases"). */
    fun parseFeatured(json: String, category: String): List<Game> {
        val items = JSONObject(json).optJSONObject(category)?.optJSONArray("items") ?: return emptyList()
        val seen = mutableSetOf<Int>()
        return (0 until items.length()).mapNotNull { i ->
            val o = items.getJSONObject(i)
            val appId = o.optInt("id", 0)
            // Skip bundles, hardware and anything listed twice.
            if (appId <= 0 || o.optInt("type", 0) != 0 || !seen.add(appId)) return@mapNotNull null
            val currency = o.optString("currency").ifBlank { null }
            val final = o.optLong("final_price", -1)
            val original = o.optLong("original_price", -1)
            Game(
                appId = appId,
                name = o.optString("name"),
                imageUrl = o.optString("header_image").ifBlank { o.optString("large_capsule_image") }.ifBlank { headerFallback(appId) },
                rank = seen.size,
                price = if (final == 0L) "Free" else money(final, currency),
                originalPrice = if (o.optBoolean("discounted") && original > 0) money(original, currency) else null,
                discountPercent = o.optInt("discount_percent", 0),
                dealEnds = o.optLong("discount_expiration", 0).takeIf { it > 0 },
                priceCents = final.takeIf { it >= 0 },
            )
        }.mapIndexed { i, g -> g.copy(rank = i + 1) }
    }

    fun parseDetails(json: String, appId: Int): GameDetail? {
        val wrapper = JSONObject(json).optJSONObject(appId.toString()) ?: return null
        if (!wrapper.optBoolean("success")) return null
        val d = wrapper.getJSONObject("data")
        val price = d.optJSONObject("price_overview")
        val platforms = d.optJSONObject("platforms")
        val free = d.optBoolean("is_free")
        return GameDetail(
            appId = appId,
            name = d.optString("name"),
            description = Html.toText(d.optString("short_description")),
            imageUrl = d.optString("header_image").ifBlank { headerFallback(appId) },
            backgroundUrl = d.optString("background_raw").ifBlank { d.optString("background") }.ifBlank { null },
            developers = strings(d.optJSONArray("developers")),
            publishers = strings(d.optJSONArray("publishers")),
            releaseDate = d.optJSONObject("release_date")?.optString("date")?.ifBlank { null },
            genres = d.optJSONArray("genres")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("description") } }.orEmpty(),
            price = if (free) "Free to play" else price?.optString("final_formatted")?.ifBlank { null },
            originalPrice = price?.optString("initial_formatted")?.ifBlank { null },
            discountPercent = price?.optInt("discount_percent", 0) ?: 0,
            free = free,
            windows = platforms?.optBoolean("windows") == true,
            mac = platforms?.optBoolean("mac") == true,
            linux = platforms?.optBoolean("linux") == true,
            metacritic = d.optJSONObject("metacritic")?.optInt("score", 0)?.takeIf { it > 0 },
            reviews = d.optJSONObject("recommendations")?.optLong("total", 0)?.takeIf { it > 0 },
            screenshots = d.optJSONArray("screenshots")?.let { a -> (0 until a.length()).mapNotNull { a.getJSONObject(it).optString("path_full").ifBlank { null } } }.orEmpty(),
            controller = d.optString("controller_support").ifBlank { null },
            priceCents = if (free) 0L else price?.optLong("final", -1)?.takeIf { it >= 0 },
            regularCents = if (free) 0L else price?.optLong("initial", -1)?.takeIf { it >= 0 },
            currency = price?.optString("currency")?.ifBlank { null },
        )
    }

    /** Steam's own count of people online and in a game. */
    fun parseOnline(json: String): Pair<Long, Long>? {
        val o = JSONObject(json)
        fun num(key: String) = o.optString(key).filter { it.isDigit() }.toLongOrNull()
        val online = num("users_online") ?: return null
        return online to (num("users_ingame") ?: 0L)
    }

    /** Cents in [currency] as the phone would write money, e.g. 2499 GBP → "£24.99". */
    fun money(cents: Long, currency: String?): String? {
        if (cents < 0) return null
        return try {
            val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
            if (currency != null) format.currency = Currency.getInstance(currency)
            format.format(cents / 100.0)
        } catch (ignored: Exception) {
            String.format(Locale.US, "%.2f %s", cents / 100.0, currency.orEmpty()).trim()
        }
    }

    private fun strings(a: JSONArray?): List<String> =
        a?.let { (0 until it.length()).map { i -> it.optString(i) }.filter { s -> s.isNotBlank() } }.orEmpty()
}

/** Store descriptions carry a little HTML and entities. */
object Html {
    fun toText(html: String): String = html
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").replace("&amp;", "&")
        .trim()
}
