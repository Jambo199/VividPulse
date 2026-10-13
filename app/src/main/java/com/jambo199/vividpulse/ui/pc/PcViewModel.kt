package com.jambo199.vividpulse.ui.pc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jambo199.vividpulse.Graph
import com.jambo199.vividpulse.pc.PcAddress
import com.jambo199.vividpulse.pc.PcFound
import com.jambo199.vividpulse.pc.PcLibrary
import com.jambo199.vividpulse.pc.PcLive
import com.jambo199.vividpulse.pc.PcSpecs
import com.jambo199.vividpulse.pc.PcSteam
import com.jambo199.vividpulse.pc.WrongCodeException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Link { CONNECTING, ONLINE, OFFLINE, WRONG_CODE }

data class PcState(
    val link: Link = Link.CONNECTING,
    /** When the PC last answered; what's on screen is from then while it's offline. */
    val lastSeen: Long = 0,
    val specs: PcSpecs? = null,
    val live: PcLive? = null,
    val steam: PcSteam? = null,
    val library: PcLibrary? = null,
    val libraryLoading: Boolean = false,
    /** Recent CPU and GPU load (0..1), oldest first, for the little charts. */
    val cpuHistory: List<Float> = emptyList(),
    val gpuHistory: List<Float> = emptyList(),
)

data class PairingState(
    val searching: Boolean = false,
    val searched: Boolean = false,
    val found: List<PcFound> = emptyList(),
    val pairing: Boolean = false,
    val error: String? = null,
)

/**
 * The PC tab. Shows what the PC last sent straight away (kept on the phone), then keeps it up to
 * date while the PC answers. When it stops answering the page stays as it was, marked offline, and
 * picks up again by itself once Vivid Pulse PC is running.
 */
class PcViewModel : ViewModel() {
    private val repo = Graph.pc
    val pairing = repo.pairing

    private val _state = MutableStateFlow(cached())
    val state: StateFlow<PcState> = _state.asStateFlow()

    private val _setup = MutableStateFlow(PairingState())
    val setup: StateFlow<PairingState> = _setup.asStateFlow()

    private var refreshing: Job? = null

    private fun cached() = PcState(
        lastSeen = repo.lastSeen(),
        specs = repo.cachedSpecs(),
        live = repo.cachedLive(),
        steam = repo.cachedSteam(),
        library = repo.cachedLibrary(),
    )

    /** Keeps the page up to date while it's on screen; returns when it leaves. */
    suspend fun watch() {
        if (pairing.value == null) return
        var misses = 0
        while (true) {
            val result = runCatching { repo.live() }
            val live = result.getOrNull()
            if (live != null) {
                val wasOnline = _state.value.link == Link.ONLINE
                misses = 0
                _state.update { s ->
                    s.copy(
                        link = Link.ONLINE,
                        lastSeen = System.currentTimeMillis(),
                        live = live,
                        cpuHistory = (s.cpuHistory + ((live.cpuPercent ?: 0f) / 100f)).takeLast(HISTORY),
                        gpuHistory = (s.gpuHistory + ((live.gpu?.loadPercent ?: 0f) / 100f)).takeLast(HISTORY),
                    )
                }
                // Just (re)connected: fetch the specs and the Steam library again too.
                if (!wasOnline) refreshAll()
                delay(LIVE_EVERY_MS)
            } else {
                val e = result.exceptionOrNull()
                if (e is CancellationException) throw e
                misses++
                if (e is WrongCodeException) {
                    _state.update { it.copy(link = Link.WRONG_CODE) }
                    delay(RETRY_SLOW_MS)
                    continue
                }
                // One miss can be a hiccup; after two the PC is offline, and what's shown stays as it was.
                if (misses >= 2) _state.update { it.copy(link = Link.OFFLINE, lastSeen = repo.lastSeen()) }
                // Its address may have changed (a router hands out new ones): look for it by name.
                if (misses % 3 == 2) runCatching { repo.relocate() }
                delay(if (misses < 3) RETRY_FAST_MS else RETRY_SLOW_MS)
            }
        }
    }

    /** Specs and the Steam library, fresh from the PC. */
    fun refreshAll(steamRefresh: Boolean = false) {
        if (refreshing?.isActive == true) return
        refreshing = viewModelScope.launch {
            runCatching { repo.specs() }.onSuccess { s -> _state.update { it.copy(specs = s) } }
            _state.update { it.copy(libraryLoading = true) }
            runCatching { repo.steam(steamRefresh) }.onSuccess { steam ->
                _state.update { it.copy(steam = steam) }
                runCatching { repo.library(steam) }.onSuccess { l -> _state.update { it.copy(library = l) } }
            }
            _state.update { it.copy(libraryLoading = false) }
        }
    }

    fun search() {
        if (_setup.value.searching) return
        _setup.update { it.copy(searching = true, error = null) }
        viewModelScope.launch {
            val found = runCatching { repo.discover() }.getOrDefault(emptyList())
            _setup.update { it.copy(searching = false, searched = true, found = found) }
        }
    }

    /** Pairs with the PC at [address] (its port too, when given); [done] gets whether it worked. */
    fun pair(address: String, code: String, done: (Boolean) -> Unit = {}) {
        val (host, port) = PcAddress.parse(address) ?: run {
            _setup.update { it.copy(error = "That doesn't look like an address. It's like 192.168.1.20") }
            return
        }
        if (code.isBlank()) return
        _setup.update { it.copy(pairing = true, error = null) }
        viewModelScope.launch {
            val result = runCatching { repo.pair(host, port, code) }
            val e = result.exceptionOrNull()
            if (e is CancellationException) throw e
            _setup.update {
                it.copy(
                    pairing = false,
                    error = when {
                        e == null -> null
                        e is WrongCodeException -> "That code isn't right. Check the code in the Vivid Pulse PC window."
                        else -> "Couldn't reach $host. Is Vivid Pulse PC running, and is the phone on the same Wi-Fi?"
                    },
                )
            }
            if (e == null) {
                _state.value = cached().copy(link = Link.ONLINE, lastSeen = System.currentTimeMillis())
                refreshAll()
            }
            done(e == null)
        }
    }

    fun clearError() = _setup.update { it.copy(error = null) }

    /** Starts a Steam game on the PC; [done] gets an error message, or null when it started. */
    fun play(appId: Int, done: (String?) -> Unit) {
        viewModelScope.launch {
            val e = runCatching { repo.launch(appId) }.exceptionOrNull()
            if (e is CancellationException) throw e
            done(
                when {
                    e == null -> null
                    e.message?.contains("500") == true -> "Steam on your PC couldn't start it"
                    else -> "Couldn't reach your PC"
                },
            )
        }
    }

    fun forget() {
        repo.forget()
        _state.value = PcState()
        _setup.value = PairingState()
    }

    private companion object {
        const val LIVE_EVERY_MS = 2_000L
        const val RETRY_FAST_MS = 4_000L
        const val RETRY_SLOW_MS = 12_000L
        const val HISTORY = 60
    }
}
