package com.jambo199.vividpulse.ui.games

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jambo199.vividpulse.Graph
import com.jambo199.vividpulse.games.Chart
import com.jambo199.vividpulse.games.EpicGame
import com.jambo199.vividpulse.games.Game
import com.jambo199.vividpulse.games.friendlyMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class GamesTab(val label: String, val chart: Chart?) {
    MOST_PLAYED("Most played", Chart.MOST_PLAYED),
    TOP_SELLERS("Top sellers", Chart.TOP_SELLERS),
    DEALS("Deals", Chart.DEALS),
    UPCOMING("Most anticipated", Chart.UPCOMING),
    /** Epic's giveaways and anything free to keep on Steam. */
    FREE("Free games", null),
}

/** Ways to order the Deals list. */
enum class DealSort(val label: String) {
    POPULAR("Popular"),
    PLAYERS("Most played"),
    DISCOUNT("Biggest discount"),
    RATED("Best reviewed"),
    ;

    /** [games] in this order; ties keep the store's own order. */
    fun apply(games: List<Game>): List<Game> = when (this) {
        POPULAR -> games.sortedBy { it.rank }
        PLAYERS -> games.sortedWith(compareByDescending<Game> { it.playersNow ?: -1 }.thenBy { it.rank })
        DISCOUNT -> games.sortedWith(compareByDescending<Game> { it.discountPercent }.thenBy { it.rank })
        RATED -> games.sortedWith(compareByDescending<Game> { reviewScore(it) }.thenBy { it.rank })
    }

    companion object {
        /**
         * % positive, pulled towards 70% for games with few reviews, so 98% from 40 people doesn't
         * beat 95% from 200,000. Games without reviews go last.
         */
        fun reviewScore(g: Game): Double {
            val percent = g.reviewPercent ?: return -1.0
            val count = (g.reviewCount ?: 0L).toDouble()
            return (percent * count + 70.0 * 200) / (count + 200)
        }
    }
}

/** A list that may be loading, may have failed, and may still show an older copy meanwhile. */
data class ListState<T>(
    val items: List<T>? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val fetchedAt: Long? = null,
    /** There's another page to load as you scroll. */
    val canLoadMore: Boolean = false,
    val loadingMore: Boolean = false,
)

class GamesViewModel : ViewModel() {
    private val repo = Graph.games

    private val _tab = MutableStateFlow(GamesTab.MOST_PLAYED)
    val tab: StateFlow<GamesTab> = _tab.asStateFlow()

    private val charts = Chart.entries.associateWith { c ->
        MutableStateFlow(repo.cached(c)?.let { ListState(it.games, fetchedAt = it.fetchedAt, canLoadMore = it.nextStart != null) } ?: ListState<Game>())
    }

    private val _dealSort = MutableStateFlow(DealSort.POPULAR)
    val dealSort: StateFlow<DealSort> = _dealSort.asStateFlow()
    private val filling = mutableMapOf<Chart, Job>()
    private val _epic = MutableStateFlow(ListState(repo.cachedEpic()))
    val epic: StateFlow<ListState<EpicGame>> = _epic.asStateFlow()

    private val _online = MutableStateFlow<Pair<Long, Long>?>(null)
    val online: StateFlow<Pair<Long, Long>?> = _online.asStateFlow()

    fun chart(c: Chart): StateFlow<ListState<Game>> = charts.getValue(c)

    /** Starred games, and how many people are playing each right now. */
    val watching: StateFlow<List<Game>> = Graph.watching.games
    private val _watchPlayers = MutableStateFlow<Map<Int, Long>>(emptyMap())
    val watchPlayers: StateFlow<Map<Int, Long>> = _watchPlayers.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _results = MutableStateFlow(ListState<Game>())
    val results: StateFlow<ListState<Game>> = _results.asStateFlow()

    init {
        load(GamesTab.MOST_PLAYED, force = false)
        viewModelScope.launch { _online.value = repo.steamOnline() }
        viewModelScope.launch { watching.collectLatest { refreshWatched() } }
        viewModelScope.launch {
            // Search a moment after typing stops (a newer query cancels the wait).
            _query.collectLatest { q ->
                if (q.isBlank()) {
                    _results.value = ListState()
                    return@collectLatest
                }
                delay(350)
                _results.value = _results.value.copy(loading = true, error = null)
                _results.value = try {
                    ListState(repo.search(q), fetchedAt = System.currentTimeMillis())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ListState(error = e.friendlyMessage())
                }
            }
        }
    }

    /** A list (and order) asked for from outside, picked up when the page shows. */
    fun takeAsked() {
        val (t, sort) = asked.value ?: return
        asked.value = null
        sort?.let { _dealSort.value = it }
        select(t)
    }

    fun search(q: String) {
        _query.value = q
    }

    private suspend fun refreshWatched() = coroutineScope {
        val ids = watching.value.map { it.appId }
        if (ids.isEmpty()) return@coroutineScope
        val counts = ids.map { id -> async { id to repo.playersNow(id) } }.awaitAll()
        _watchPlayers.value = counts.mapNotNull { (id, n) -> n?.let { id to it } }.toMap()
    }

    fun select(t: GamesTab) {
        _tab.value = t
        load(t, force = false)
    }

    fun sortDeals(sort: DealSort) {
        _dealSort.value = sort
    }

    /** The next page of [chart], when you scroll near the end of it. */
    fun loadMore(chart: Chart) {
        val flow = charts.getValue(chart)
        val s = flow.value
        if (!s.canLoadMore || s.loadingMore || s.loading) return
        flow.value = s.copy(loadingMore = true)
        viewModelScope.launch {
            try {
                val list = repo.more(chart)
                flow.value = flow.value.copy(
                    items = list?.games ?: flow.value.items,
                    loadingMore = false,
                    canLoadMore = list?.nextStart != null,
                    error = null,
                )
                fillPlayers(chart)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Leave it there; scrolling back down tries again.
                flow.value = flow.value.copy(loadingMore = false, error = e.friendlyMessage())
            }
        }
    }

    /** Live player counts for the charts that show them, filling in a batch at a time. */
    private fun fillPlayers(chart: Chart) {
        if (chart != Chart.MOST_PLAYED && chart != Chart.DEALS) return
        val flow = charts.getValue(chart)
        filling[chart]?.cancel()
        filling[chart] = viewModelScope.launch {
            runCatching {
                repo.fillPlayers(chart) { list -> flow.value = flow.value.copy(items = list.games) }
            }
        }
    }

    fun refresh() {
        load(_tab.value, force = true)
        viewModelScope.launch { refreshWatched() }
        viewModelScope.launch { repo.steamOnline()?.let { _online.value = it } }
    }

    private fun load(t: GamesTab, force: Boolean) {
        val chart = t.chart
        if (chart == null) {
            // Free games: Epic's giveaways, plus anything free to keep on Steam.
            loadChart(Chart.FREE_TO_KEEP, force)
            val flow = _epic
            if (flow.value.loading) return
            flow.value = flow.value.copy(loading = true, error = null)
            viewModelScope.launch {
                flow.value = try {
                    ListState(repo.epicFree(force), fetchedAt = System.currentTimeMillis())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    flow.value.copy(loading = false, error = e.friendlyMessage())
                }
            }
            return
        }
        loadChart(chart, force)
    }

    private fun loadChart(chart: Chart, force: Boolean) {
        val flow = charts.getValue(chart)
        if (flow.value.loading) return
        flow.value = flow.value.copy(loading = true, error = null)
        viewModelScope.launch {
            flow.value = try {
                val list = repo.chart(chart, force)
                ListState(list.games, fetchedAt = list.fetchedAt, canLoadMore = list.nextStart != null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                flow.value.copy(loading = false, error = e.friendlyMessage())
            }
            if (flow.value.error == null) fillPlayers(chart)
        }
    }

    companion object {
        val asked = MutableStateFlow<Pair<GamesTab, DealSort?>?>(null)

        /** "deals", "deals/discount", "upcoming"…: true if that names a list. */
        fun ask(path: String): Boolean {
            val parts = path.lowercase().split('/')
            fun <E : Enum<E>> find(all: List<E>, name: String?) = all.firstOrNull { it.name.equals(name?.replace('-', '_'), ignoreCase = true) }
            val tab = find(GamesTab.entries, parts.getOrNull(0)) ?: return false
            asked.value = tab to find(DealSort.entries, parts.getOrNull(1))
            return true
        }
    }
}
