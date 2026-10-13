package com.jambo199.vividpulse.games

import android.content.Context
import android.util.Log
import com.jambo199.vividpulse.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** The charts the Games page shows. */
enum class Chart(val label: String) {
    MOST_PLAYED("Most played"),
    TOP_SELLERS("Top sellers"),
    DEALS("Deals"),
}

data class GameList(val games: List<Game>, val fetchedAt: Long)

/**
 * Fetches Steam's charts and Epic's free games, keeping the last good copy of each on disk so
 * the page has something to show offline.
 */
class GamesRepository(context: Context) {
    private val dir = File(context.cacheDir, "games").apply { mkdirs() }
    private val memory = mutableMapOf<Chart, GameList>()
    private var epicMemory: Pair<List<EpicGame>, Long>? = null
    private val known = java.util.concurrent.ConcurrentHashMap<Int, Game>()

    /** A game we've listed already, so its page can show the name and picture straight away. */
    fun known(appId: Int): Game? = known[appId]

    val country: String
        get() = Graph.settings.current.steamCountry.ifBlank { Locale.getDefault().country }.ifBlank { "US" }.uppercase(Locale.US)

    /** The last copy we have, however old. */
    fun cached(chart: Chart): GameList? = memory[chart] ?: readCache(chart)?.also { list ->
        memory[chart] = list
        list.games.forEach { known.putIfAbsent(it.appId, it) }
    }

    suspend fun chart(chart: Chart, force: Boolean): GameList = withContext(Dispatchers.IO) {
        val have = memory[chart]
        if (!force && have != null && System.currentTimeMillis() - have.fetchedAt < FRESH_MS) return@withContext have
        val games = when (chart) {
            Chart.MOST_PLAYED -> mostPlayed()
            Chart.TOP_SELLERS -> topSellers()
            Chart.DEALS -> Steam.parseFeatured(Http.get(Steam.featuredUrl(country)), "specials").take(LIMIT)
        }
        if (games.isEmpty()) throw java.io.IOException("Steam sent an empty list")
        games.forEach { known[it.appId] = it }
        GameList(games, System.currentTimeMillis()).also {
            memory[chart] = it
            writeCache(chart, it)
        }
    }

    private suspend fun mostPlayed(): List<Game> = coroutineScope {
        val chart = Steam.parseMostPlayed(Http.get(Steam.mostPlayedUrl())).take(LIMIT)
        val items = async { runCatching { Steam.parseItems(Http.get(Steam.itemsUrl(chart.map { it.appId }, country))) }.getOrDefault(emptyMap()) }
        val gate = Semaphore(6)
        val players = chart.map { g -> async { gate.withPermit { playersNow(g.appId) } } }
        val named = items.await()
        val counts = players.awaitAll()
        chart.mapIndexed { i, g ->
            val item = named[g.appId] ?: runCatching { storeItemFromDetails(g.appId) }.getOrNull()
            g.copy(
                name = item?.name ?: "App ${g.appId}",
                imageUrl = item?.imageUrl ?: g.imageUrl,
                price = item?.price,
                originalPrice = item?.originalPrice,
                discountPercent = item?.discountPercent ?: 0,
                playersNow = counts[i],
            )
        }
    }

    private suspend fun topSellers(): List<Game> {
        val ranked = runCatching { Steam.parseTopSellers(Http.get(Steam.topSellersUrl(country))) }
            .onFailure { Log.w(TAG, "Weekly top sellers failed, using the store's list", it) }
            .getOrNull()
        if (ranked.isNullOrEmpty()) {
            return Steam.parseFeatured(Http.get(Steam.featuredUrl(country)), "top_sellers").take(LIMIT)
        }
        val games = ranked.take(LIMIT)
        val missing = games.filter { it.second == null }.map { it.first.appId }
        val named = if (missing.isEmpty()) emptyMap() else runCatching { Steam.parseItems(Http.get(Steam.itemsUrl(missing, country))) }.getOrDefault(emptyMap())
        return games.map { (g, item) ->
            if (item != null) {
                g
            } else {
                val n = named[g.appId]
                g.copy(
                    name = n?.name ?: g.name.ifBlank { "App ${g.appId}" },
                    imageUrl = n?.imageUrl ?: g.imageUrl,
                    price = n?.price,
                    originalPrice = n?.originalPrice,
                    discountPercent = n?.discountPercent ?: 0,
                )
            }
        }
    }

    private fun storeItemFromDetails(appId: Int): StoreItem? =
        Steam.parseDetails(Http.get(Steam.detailsUrl(appId, country)), appId)?.let {
            StoreItem(appId, it.name, it.imageUrl, it.price, it.originalPrice, it.discountPercent)
        }

    suspend fun playersNow(appId: Int): Long? = withContext(Dispatchers.IO) {
        runCatching { Steam.parsePlayers(Http.get(Steam.playersUrl(appId))) }.getOrNull()
    }

    suspend fun details(appId: Int): GameDetail? = withContext(Dispatchers.IO) {
        Steam.parseDetails(Http.get(Steam.detailsUrl(appId, country)), appId)
    }

    /** Any game on the Steam store by name. */
    suspend fun search(term: String): List<Game> = withContext(Dispatchers.IO) {
        Steam.parseSearch(Http.get(Steam.searchUrl(term.trim(), country))).also { list ->
            list.forEach { known.putIfAbsent(it.appId, it) }
        }
    }

    /** People on Steam right now, and how many of them are playing. */
    suspend fun steamOnline(): Pair<Long, Long>? = withContext(Dispatchers.IO) {
        runCatching { Steam.parseOnline(Http.get("https://www.valvesoftware.com/about/stats")) }.getOrNull()
    }

    fun cachedEpic(): List<EpicGame>? = epicMemory?.first

    suspend fun epicFree(force: Boolean): List<EpicGame> = withContext(Dispatchers.IO) {
        val have = epicMemory
        if (!force && have != null && System.currentTimeMillis() - have.second < FRESH_MS) return@withContext have.first
        Epic.parseFreeGames(Http.get(Epic.freeGamesUrl(country))).also { epicMemory = it to System.currentTimeMillis() }
    }

    private fun cacheFile(chart: Chart) = File(dir, "${chart.name.lowercase(Locale.US)}.json")

    private fun writeCache(chart: Chart, list: GameList) {
        runCatching {
            val a = JSONArray(list.games.map { g ->
                JSONObject()
                    .put("appId", g.appId).put("name", g.name).put("imageUrl", g.imageUrl).put("rank", g.rank)
                    .put("lastWeekRank", g.lastWeekRank ?: 0).put("playersNow", g.playersNow ?: -1).put("peakToday", g.peakToday ?: -1)
                    .put("price", g.price ?: "").put("originalPrice", g.originalPrice ?: "").put("discountPercent", g.discountPercent)
                    .put("weeksOnChart", g.weeksOnChart ?: 0).put("dealEnds", g.dealEnds ?: 0)
            })
            cacheFile(chart).writeText(JSONObject().put("fetchedAt", list.fetchedAt).put("games", a).toString())
        }
    }

    private fun readCache(chart: Chart): GameList? = runCatching {
        val o = JSONObject(cacheFile(chart).readText())
        val a = o.getJSONArray("games")
        GameList(
            (0 until a.length()).map { i ->
                val g = a.getJSONObject(i)
                Game(
                    appId = g.getInt("appId"),
                    name = g.getString("name"),
                    imageUrl = g.getString("imageUrl"),
                    rank = g.getInt("rank"),
                    lastWeekRank = g.optInt("lastWeekRank").takeIf { it > 0 },
                    playersNow = g.optLong("playersNow", -1).takeIf { it >= 0 },
                    peakToday = g.optLong("peakToday", -1).takeIf { it >= 0 },
                    price = g.optString("price").ifBlank { null },
                    originalPrice = g.optString("originalPrice").ifBlank { null },
                    discountPercent = g.optInt("discountPercent"),
                    weeksOnChart = g.optInt("weeksOnChart").takeIf { it > 0 },
                    dealEnds = g.optLong("dealEnds").takeIf { it > 0 },
                )
            },
            o.getLong("fetchedAt"),
        )
    }.getOrNull()

    companion object {
        private const val TAG = "Games"
        const val LIMIT = 20
        private const val FRESH_MS = 5 * 60_000L
    }
}
