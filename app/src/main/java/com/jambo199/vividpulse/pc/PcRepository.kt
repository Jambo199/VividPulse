package com.jambo199.vividpulse.pc

import android.content.Context
import com.jambo199.vividpulse.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** The PC this phone is paired with. */
data class PcPairing(val host: String, val port: Int, val name: String, val code: String)

/** A game in one of the Steam collections shown on the phone. */
data class LibraryEntry(
    val appId: Int,
    val name: String,
    val coverUrl: String,
    val installed: Boolean,
    val sizeBytes: Long,
    val minutes: Long,
)

/** Purchased and Installed, the two collections the PC tab shows. */
data class PcLibrary(val purchased: List<LibraryEntry>, val installed: List<LibraryEntry>, val fromCollection: String?)

/**
 * The paired PC: pairing, its specs and Steam library, and live readings. Everything the PC sends
 * is kept on the phone, so it can still be shown (as it last was) while the PC is off.
 */
class PcRepository(context: Context) {
    private val prefs = context.getSharedPreferences("pc", Context.MODE_PRIVATE)
    private val dir = File(context.filesDir, "pc").apply { mkdirs() }
    private val _pairing = MutableStateFlow(readPairing())
    val pairing: StateFlow<PcPairing?> = _pairing.asStateFlow()
    private var library: Pair<PcSteam, PcLibrary>? = null

    suspend fun discover(): List<PcFound> = withContext(Dispatchers.IO) { PcClient.discover() }

    /** Checks [code] with the PC at [host], and remembers it. Throws [WrongCodeException] for a wrong code. */
    suspend fun pair(host: String, port: Int, code: String): PcSpecs = withContext(Dispatchers.IO) {
        val hello = runCatching { PcParse.found(PcClient.request(host, port, "/hello", null), host) }.getOrNull()
        val json = PcClient.request(host, port, "/specs", code.trim())
        val specs = PcParse.specs(json)
        File(dir, "specs.json").writeText(json)
        val p = PcPairing(host, port, hello?.name ?: specs.name, code.trim())
        prefs.edit().putString("host", p.host).putInt("port", p.port).putString("name", p.name).putString("code", p.code).apply()
        _pairing.value = p
        specs
    }

    /** When the PC last answered (0 for never). */
    fun lastSeen(): Long = prefs.getLong("seen", 0)

    fun forget() {
        prefs.edit().clear().apply()
        dir.listFiles()?.forEach { it.delete() }
        library = null
        _pairing.value = null
    }

    fun cachedSpecs(): PcSpecs? = runCatching { PcParse.specs(File(dir, "specs.json").readText()) }.getOrNull()

    fun cachedSteam(): PcSteam? = runCatching { PcParse.steam(File(dir, "steam.json").readText()) }.getOrNull()

    /** The last live readings the PC sent. */
    fun cachedLive(): PcLive? = runCatching { PcParse.live(File(dir, "live.json").readText()) }.getOrNull()

    /** Purchased and Installed as last shown, names and covers included. */
    fun cachedLibrary(): PcLibrary? = runCatching { LibraryJson.read(File(dir, "library.json").readText()) }.getOrNull()

    suspend fun specs(): PcSpecs = withContext(Dispatchers.IO) {
        val json = get("/specs")
        File(dir, "specs.json").writeText(json)
        PcParse.specs(json)
    }

    suspend fun live(): PcLive = withContext(Dispatchers.IO) {
        val json = get("/live", timeoutMs = 5_000)
        val live = PcParse.live(json)
        File(dir, "live.json").writeText(json)
        live
    }

    suspend fun steam(refresh: Boolean = false): PcSteam = withContext(Dispatchers.IO) {
        val json = get(if (refresh) "/steam?refresh=1" else "/steam", timeoutMs = 20_000)
        File(dir, "steam.json").writeText(json)
        PcParse.steam(json)
    }

    /** Starts a Steam game on the PC. */
    suspend fun launch(appId: Int) = withContext(Dispatchers.IO) { get("/launch?appid=$appId", post = true) }

    /**
     * Purchased and Installed, with names and covers from the Steam store. Purchased is your own
     * "Purchased" collection when you made one by adding games; otherwise every paid game in your
     * library (free-to-play games, tools and soundtracks left out).
     */
    suspend fun library(steam: PcSteam): PcLibrary = withContext(Dispatchers.IO) {
        library?.takeIf { it.first == steam }?.let { return@withContext it.second }
        val collection = steam.purchasedCollection
        val owned = collection?.appIds ?: (steam.libraryAppIds + steam.games.map { it.appId }).distinct()
        val items = runCatching { Graph.games.storeItems((owned + steam.installedGames.map { it.appId }).distinct()) }.getOrDefault(emptyMap())
        // Steam's Installed collection is games and software: not soundtracks, DLC or videos.
        val installed = steam.installedGames.filter { g -> items[g.appId]?.type.let { it == null || it == 0 || it == 1 } }
        val games = steam.games.associateBy { it.appId }
        fun entry(id: Int): LibraryEntry {
            val item = items[id]
            val g = games[id]
            return LibraryEntry(
                appId = id,
                name = item?.name ?: g?.name ?: "App $id",
                coverUrl = item?.coverUrl ?: "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/$id/library_600x900.jpg",
                installed = g?.installed == true,
                sizeBytes = g?.sizeBytes ?: 0,
                minutes = g?.minutes ?: 0,
            )
        }
        val purchased = if (collection != null) {
            owned.map(::entry)
        } else {
            owned.filter { id -> items[id]?.let { it.type == 0 && !it.isFree } == true }.map(::entry)
        }
        PcLibrary(
            purchased = purchased.sortedBy { it.name.lowercase() },
            installed = installed.map { entry(it.appId) }.sortedBy { it.name.lowercase() },
            fromCollection = collection?.name,
        ).also {
            library = steam to it
            runCatching { File(dir, "library.json").writeText(LibraryJson.write(it)) }
        }
    }

    /** The PC's address may have changed (routers hand them out): looks for it by name. */
    suspend fun relocate(): Boolean = withContext(Dispatchers.IO) {
        val p = _pairing.value ?: return@withContext false
        val match = runCatching { PcClient.discover() }.getOrDefault(emptyList()).firstOrNull { it.name.equals(p.name, ignoreCase = true) }
            ?: return@withContext false
        if (match.host == p.host && match.port == p.port) return@withContext false
        val moved = p.copy(host = match.host, port = match.port)
        prefs.edit().putString("host", moved.host).putInt("port", moved.port).apply()
        _pairing.value = moved
        true
    }

    private fun get(path: String, post: Boolean = false, timeoutMs: Int = 6_000): String {
        val p = _pairing.value ?: throw java.io.IOException("Not paired with a PC")
        return PcClient.request(p.host, p.port, path, p.code, post, timeoutMs).also {
            prefs.edit().putLong("seen", System.currentTimeMillis()).apply()
        }
    }

    private fun readPairing(): PcPairing? {
        val host = prefs.getString("host", null) ?: return null
        val code = prefs.getString("code", null) ?: return null
        return PcPairing(host, prefs.getInt("port", PcClient.PORT), prefs.getString("name", null) ?: host, code)
    }

    companion object {
        /** Where the PC program is published, next to the app. */
        fun downloadUrl(): String = com.jambo199.vividpulse.BuildConfig.UPDATE_BASE_URL + "VividPulsePC.zip"
    }
}

/** Saves Purchased and Installed for showing while the PC is off. */
internal object LibraryJson {
    fun write(l: PcLibrary): String = JSONObject()
        .put("purchased", entries(l.purchased))
        .put("installed", entries(l.installed))
        .put("fromCollection", l.fromCollection ?: JSONObject.NULL)
        .toString()

    fun read(json: String): PcLibrary {
        val o = JSONObject(json)
        val from = if (o.isNull("fromCollection")) null else o.optString("fromCollection").ifBlank { null }
        return PcLibrary(entries(o.optJSONArray("purchased")), entries(o.optJSONArray("installed")), from)
    }

    private fun entries(list: List<LibraryEntry>) = JSONArray(
        list.map { e ->
            JSONObject().put("appId", e.appId).put("name", e.name).put("coverUrl", e.coverUrl)
                .put("installed", e.installed).put("sizeBytes", e.sizeBytes).put("minutes", e.minutes)
        },
    )

    private fun entries(a: JSONArray?): List<LibraryEntry> = if (a == null) {
        emptyList()
    } else {
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            LibraryEntry(o.optInt("appId"), o.optString("name"), o.optString("coverUrl"), o.optBoolean("installed"), o.optLong("sizeBytes"), o.optLong("minutes"))
        }
    }
}
