package com.jambo199.vividpulse.ui.games

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jambo199.vividpulse.Graph
import com.jambo199.vividpulse.games.Chart
import com.jambo199.vividpulse.games.EpicGame
import com.jambo199.vividpulse.games.Game
import com.jambo199.vividpulse.games.friendlyMessage
import kotlinx.coroutines.CancellationException
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
    EPIC("Free on Epic", null),
}

/** A list that may be loading, may have failed, and may still show an older copy meanwhile. */
data class ListState<T>(
    val items: List<T>? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val fetchedAt: Long? = null,
)

class GamesViewModel : ViewModel() {
    private val repo = Graph.games

    private val _tab = MutableStateFlow(GamesTab.MOST_PLAYED)
    val tab: StateFlow<GamesTab> = _tab.asStateFlow()

    private val charts = Chart.entries.associateWith { c ->
        MutableStateFlow(repo.cached(c)?.let { ListState(it.games, fetchedAt = it.fetchedAt) } ?: ListState<Game>())
    }
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

    fun refresh() {
        load(_tab.value, force = true)
        viewModelScope.launch { refreshWatched() }
        viewModelScope.launch { repo.steamOnline()?.let { _online.value = it } }
    }

    private fun load(t: GamesTab, force: Boolean) {
        val chart = t.chart
        if (chart == null) {
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
        val flow = charts.getValue(chart)
        if (flow.value.loading) return
        flow.value = flow.value.copy(loading = true, error = null)
        viewModelScope.launch {
            flow.value = try {
                val list = repo.chart(chart, force)
                ListState(list.games, fetchedAt = list.fetchedAt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                flow.value.copy(loading = false, error = e.friendlyMessage())
            }
        }
    }
}
