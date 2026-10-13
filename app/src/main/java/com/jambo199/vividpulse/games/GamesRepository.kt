package com.jambo199.vividpulse.games

import android.content.Context
import android.util.Log
import com.jambo199.vividpulse.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** The charts the Games page shows. [search] is the store search that pages through it, if any. */
enum class Chart(val label: String, val search: StoreSearch? = null) {
    MOST_PLAYED("Most played"),
    TOP_SELLERS("Top sellers"),
    DEALS("Deals", StoreSearch.DEALS),
    UPCOMING("Most anticipated", StoreSearch.WISHLISTED),
    FREE_TO_KEEP("Free to keep on Steam", StoreSearch.FREE_TO_KEEP),
}

/**
 * A chart as far as it's loaded; [nextStart] is where the next page starts, null at the end, and
 * [source] the store search it pages through.
 */
data class GameList(val games: List<Game>, val fetchedAt: Long, val nextStart: Int? = null, val source: StoreSearch? = null)

/**
 * Fetches Steam's charts, keeping the last good copy of each on disk so
 * the page has something to show offline.
 */
class GamesRepository(context: Context) {
    private val dir = File(context.cacheDir, "games").apply { mkdirs() }
    private val memory = java.util.concurrent.ConcurrentHashMap<Chart, GameList>()
    private val lock = Mutex()
    private val known = java.util.concurrent.ConcurrentHashMap<Int, Game>()
    /** Games Steam has no player count for (not out yet, or not a game). */
    private val noPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()

    /** A game we've listed already, so its page can show the name and picture straight away. */
    fun known(appId: Int): Game? = known[appId]

    private val bestCache = java.util.concurrent.ConcurrentHashMap<Int, Pair<BestPrices, Long>>()

    /** Whether a GG.deals key has been entered, so other stores' prices can be shown. */
    val canCompare: Boolean get() = Graph.settings.current.ggDealsKey.isNotBlank()

    /** Store listings (price, discount) for any games, in this country. */
    suspend fun storeItems(ids: List<Int>): Map<Int, StoreItem> = withContext(Dispatchers.IO) { itemsFor(ids) }

    /**
     * The cheapest prices at other stores and key sellers, from GG.deals (empty without a key).
     * Kept for half an hour; [force] fetches again. Throws when GG.deals refuses, e.g. a wrong key.
     */
    suspend fun bestPrices(ids: List<Int>, force: Boolean = false): Map<Int, BestPrices> = withContext(Dispatchers.IO) {
        val key = Graph.settings.current.ggDealsKey.trim()
        if (key.isEmpty() || ids.isEmpty()) return@withContext emptyMap()
        val now = System.currentTimeMillis()
        val fresh: Map<Int, BestPrices> = if (force) emptyMap() else ids.mapNotNull { id -> bestCache[id]?.takeIf { now - it.second < BEST_FRESH_MS }?.let { id to it.first } }.toMap()
        val missing = ids.filter { it !in fresh }.distinct()
        val fetched = missing.chunked(GgDeals.MAX_IDS).flatMap { chunk ->
            Log.i(TAG, "GG.deals prices for ${chunk.size} games")
            GgDeals.parse(Http.get(GgDeals.pricesUrl(chunk, key, GgDeals.region(country)))).entries
        }.associate { it.key to it.value }
        fetched.forEach { (id, p) -> bestCache[id] = p to now }
        fresh + fetched
    }

    /** Adds other stores' prices to a chart's games, for comparing with Steam's. */
    suspend fun fillBestPrices(chart: Chart, onUpdate: (GameList) -> Unit): Unit = withContext(Dispatchers.IO) {
        if (!canCompare) return@withContext
        val ids = memory[chart]?.games.orEmpty().filter { it.elsewhere == null }.map { it.appId }
        if (ids.isEmpty()) return@withContext
        val best = runCatching { bestPrices(ids) }.onFailure { Log.w(TAG, "GG.deals prices failed", it) }.getOrNull() ?: return@withContext
        if (best.isEmpty()) return@withContext
        val updated = lock.withLock {
            val current = memory[chart] ?: return@withContext
            current.copy(games = current.games.map { g -> best[g.appId]?.let { g.copy(elsewhere = it) } ?: g }).also { memory[chart] = it }
        }
        onUpdate(updated)
    }

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
        val now = System.currentTimeMillis()
        val list = when (chart) {
            Chart.MOST_PLAYED -> GameList(mostPlayed(), now)
            Chart.TOP_SELLERS -> GameList(topSellers(), now)
            Chart.DEALS -> runCatching { storePage(Chart.DEALS, StoreSearch.DEALS, 0, 0) }
                .onFailure { Log.w(TAG, "Store search failed, using the featured specials", it) }
                .getOrNull()?.takeIf { it.games.isNotEmpty() }
                ?: GameList(Steam.parseFeatured(Http.get(Steam.featuredUrl(country)), "specials"), now)
            // The most wishlisted games; if the store won't give that list, the popular ones coming soon.
            Chart.UPCOMING -> runCatching { storePage(chart, StoreSearch.WISHLISTED, 0, 0) }
                .onFailure { Log.w(TAG, "Most wishlisted failed, using popular upcoming", it) }
                .getOrNull()?.takeIf { it.games.isNotEmpty() }
                ?: storePage(chart, StoreSearch.UPCOMING, 0, 0)
            Chart.FREE_TO_KEEP -> storePage(chart, StoreSearch.FREE_TO_KEEP, 0, 0)
        }
        // Nothing free to keep is normal; an empty chart isn't.
        if (list.games.isEmpty() && chart != Chart.FREE_TO_KEEP) throw java.io.IOException("Steam sent an empty list")
        list.games.forEach { known[it.appId] = it }
        lock.withLock {
            // Keep live player counts we already had until fresh ones arrive.
            val old = memory[chart]?.games?.associate { it.appId to it.playersNow }.orEmpty()
            list.copy(games = list.games.map { g -> if (g.playersNow == null) g.copy(playersNow = old[g.appId]) else g })
                .also { memory[chart] = it }
        }.also { writeCache(chart, it) }
    }

    /** The next page of a chart that has one ([GameList.nextStart]), added to what's loaded. */
    suspend fun more(chart: Chart): GameList? = withContext(Dispatchers.IO) {
        val have = memory[chart] ?: return@withContext null
        val start = have.nextStart ?: return@withContext have
        val source = have.source ?: chart.search
        if (source == null || have.games.size >= MAX_LISTED) return@withContext have.copy(nextStart = null)
        val page = storePage(chart, source, start, have.games.size)
        page.games.forEach { known.putIfAbsent(it.appId, it) }
        lock.withLock {
            val current = memory[chart] ?: have
            val seen = current.games.mapTo(HashSet()) { it.appId }
            val added = page.games.filter { seen.add(it.appId) }
            val games = current.games + added.mapIndexed { i, g -> g.copy(rank = current.games.size + i + 1) }
            current.copy(games = games, nextStart = page.nextStart?.takeIf { games.size < MAX_LISTED }).also { memory[chart] = it }
        }.also { writeCache(chart, it) }
    }

    /**
     * Fills in how many are playing each game on [chart] that doesn't have a count yet, a batch at
     * a time, handing over the list after each batch so the page fills in as it goes.
     */
    suspend fun fillPlayers(chart: Chart, onUpdate: (GameList) -> Unit): Unit = withContext(Dispatchers.IO) {
        val todo = memory[chart]?.games.orEmpty()
            .filter { it.playersNow == null && it.release == null && it.appId !in noPlayers }
            .map { it.appId }
        if (todo.isEmpty()) return@withContext
        val gate = Semaphore(8)
        for (batch in todo.chunked(16)) {
            val counts = coroutineScope { batch.map { id -> async { gate.withPermit { id to playersNow(id) } } }.awaitAll() }
            counts.filter { it.second == null }.forEach { noPlayers += it.first }
            val found = counts.mapNotNull { (id, n) -> n?.let { id to it } }.toMap()
            if (found.isEmpty()) continue
            val updated = lock.withLock {
                val current = memory[chart] ?: return@withContext
                current.copy(games = current.games.map { g -> found[g.appId]?.let { g.copy(playersNow = it) } ?: g })
                    .also { memory[chart] = it }
            }
            onUpdate(updated)
        }
        memory[chart]?.let { writeCache(chart, it) }
    }

    /** One page of the store search [kind] for [chart], ranked on from [after]. */
    private suspend fun storePage(chart: Chart, kind: StoreSearch, start: Int, after: Int): GameList {
        val page = Steam.parseSearchPage(Http.get(Steam.storeSearchUrl(kind, start, PAGE, country)))
        val items = itemsFor(page.appIds)
        // Keep the store's order; anything it can't describe in this country is left out, and so
        // is anything already out on the list of games still to come.
        val games = page.appIds.mapNotNull { items[it] }
            .filter { chart != Chart.UPCOMING || it.release != null }
            .mapIndexed { i, item -> item.toGame(after + i + 1) }
        val next = start + page.rows
        Log.i(TAG, "$chart ($kind) from $start: ${games.size} games of ${page.appIds.size} (${page.rows} rows, ${page.total} in all)")
        return GameList(games, System.currentTimeMillis(), next.takeIf { page.rows > 0 && it < page.total }, kind)
    }

    /** Store listings for any number of games, a few requests at once. */
    private suspend fun itemsFor(ids: List<Int>): Map<Int, StoreItem> = coroutineScope {
        ids.chunked(ITEMS_PER_CALL)
            .map { chunk -> async { runCatching { Steam.parseItems(Http.get(Steam.itemsUrl(chunk, country))) }.getOrDefault(emptyMap()) } }
            .awaitAll()
            .fold(emptyMap()) { all, part -> all + part }
    }

    private suspend fun mostPlayed(): List<Game> = coroutineScope {
        val chart = Steam.parseMostPlayed(Http.get(Steam.mostPlayedUrl()))
        val items = async { itemsFor(chart.map { it.appId }) }
        val gate = Semaphore(6)
        // Counts for the top of the chart now; the rest fill in while you look ([fillPlayers]).
        val players = chart.take(PLAYERS_FIRST).map { g -> async { gate.withPermit { playersNow(g.appId) } } }
        val named = items.await()
        // A few apps aren't in the store's listing (tools, betas): look those up one at a time.
        val extra = chart.filter { it.appId !in named }.take(10)
            .map { g -> async { gate.withPermit { g.appId to runCatching { storeItemFromDetails(g.appId) }.getOrNull() } } }
            .awaitAll().toMap()
        val counts = players.awaitAll()
        chart.mapIndexed { i, g ->
            val item = named[g.appId] ?: extra[g.appId]
            (item?.fill(g) ?: g.copy(name = "App ${g.appId}")).copy(playersNow = counts.getOrNull(i))
        }
    }

    private suspend fun topSellers(): List<Game> {
        val ranked = runCatching { Steam.parseTopSellers(Http.get(Steam.topSellersUrl(country))) }
            .onFailure { Log.w(TAG, "Weekly top sellers failed, using the store's list", it) }
            .getOrNull()
        if (ranked.isNullOrEmpty()) {
            return Steam.parseFeatured(Http.get(Steam.featuredUrl(country)), "top_sellers")
        }
        val missing = ranked.filter { it.second == null }.map { it.first.appId }
        val named = if (missing.isEmpty()) emptyMap() else itemsFor(missing)
        return ranked.map { (g, item) ->
            if (item != null) g else named[g.appId]?.fill(g) ?: g.copy(name = g.name.ifBlank { "App ${g.appId}" })
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

    private fun cacheFile(chart: Chart) = File(dir, "${chart.name.lowercase(Locale.US)}.json")

    private fun writeCache(chart: Chart, list: GameList) {
        runCatching {
            val a = JSONArray(list.games.map { g ->
                JSONObject()
                    .put("appId", g.appId).put("name", g.name).put("imageUrl", g.imageUrl).put("rank", g.rank)
                    .put("lastWeekRank", g.lastWeekRank ?: 0).put("playersNow", g.playersNow ?: -1).put("peakToday", g.peakToday ?: -1)
                    .put("price", g.price ?: "").put("originalPrice", g.originalPrice ?: "").put("discountPercent", g.discountPercent)
                    .put("weeksOnChart", g.weeksOnChart ?: 0).put("dealEnds", g.dealEnds ?: 0)
                    .put("priceCents", g.priceCents ?: -1).put("release", g.release ?: "")
                    .put("reviewLabel", g.reviewLabel ?: "").put("reviewCount", g.reviewCount ?: 0).put("reviewPercent", g.reviewPercent ?: -1)
            })
            cacheFile(chart).writeText(
                JSONObject().put("fetchedAt", list.fetchedAt).put("nextStart", list.nextStart ?: -1)
                    .put("source", list.source?.name ?: "").put("games", a).toString(),
            )
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
                    priceCents = g.optLong("priceCents", -1).takeIf { it >= 0 },
                    release = g.optString("release").ifBlank { null },
                    reviewLabel = g.optString("reviewLabel").ifBlank { null },
                    reviewCount = g.optLong("reviewCount").takeIf { it > 0 },
                    reviewPercent = g.optInt("reviewPercent", -1).takeIf { it >= 0 },
                )
            },
            o.getLong("fetchedAt"),
            o.optInt("nextStart", -1).takeIf { it >= 0 },
            StoreSearch.entries.firstOrNull { it.name == o.optString("source") },
        )
    }.getOrNull()

    companion object {
        private const val TAG = "Games"
        private const val FRESH_MS = 5 * 60_000L
        private const val BEST_FRESH_MS = 30 * 60_000L
        /** Store search results per page. */
        private const val PAGE = 50
        /** Games per IStoreBrowseService/GetItems request. */
        private const val ITEMS_PER_CALL = 50
        /** Live player counts fetched with the chart itself; the rest follow. */
        private const val PLAYERS_FIRST = 20
        /** How far a store search chart scrolls. */
        const val MAX_LISTED = 500
    }
}
