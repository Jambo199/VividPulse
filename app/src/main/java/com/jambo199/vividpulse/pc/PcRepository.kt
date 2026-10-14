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
    /** A non-Steam game added to Steam: no store page, and started its own way. */
    val shortcut: Boolean = false,
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
        dir.listFiles()?.forEach { it.deleteRecursively() }
        library = null
        _pairing.value = null
    }

    fun cachedSpecs(): PcSpecs? = runCatching { PcParse.specs(File(dir, "specs.json").readText()) }.getOrNull()

    fun cachedSteam(): PcSteam? = runCatching { PcParse.steam(File(dir, "steam.json").readText()) }.getOrNull()

    /** Steam's downloads on the PC as last seen. */
    fun cachedDownloads(): PcDownloads? = runCatching { PcParse.downloads(File(dir, "downloads.json").readText()) }.getOrNull()

    /** Steam's downloads and updates on the PC (and those that just finished). The first version of Vivid Pulse PC has none: an error. */
    suspend fun downloads(): PcDownloads = withContext(Dispatchers.IO) {
        val json = get("/downloads", timeoutMs = 8_000)
        val d = PcParse.downloads(json)
        File(dir, "downloads.json").writeText(json)
        d
    }

    // ---- PC alerts: settings and what's been said already

    /** Tell me when a Steam download finishes on the PC. */
    var alertDownloads: Boolean
        get() = prefs.getBoolean("alertDownloads", true)
        set(v) { prefs.edit().putBoolean("alertDownloads", v).apply() }

    /** Tell me when the PC's graphics card passes [heatLimit]. */
    var alertHeat: Boolean
        get() = prefs.getBoolean("alertHeat", true)
        set(v) { prefs.edit().putBoolean("alertHeat", v).apply() }

    var heatLimit: Int
        get() = prefs.getInt("heatLimit", 85)
        set(v) { prefs.edit().putInt("heatLimit", v).apply() }

    var lastHeatAlertAt: Long
        get() = prefs.getLong("lastHeatAlert", 0)
        set(v) { prefs.edit().putLong("lastHeatAlert", v).apply() }

    /** Finished downloads already told about ("appId@time"). */
    var toldFinished: Set<String>
        get() = prefs.getStringSet("toldFinished", emptySet()).orEmpty()
        set(v) { prefs.edit().putStringSet("toldFinished", v.toList().takeLast(60).toSet()).apply() }

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

    /** Starts a game on the PC through Steam. */
    suspend fun launch(game: LibraryEntry) = withContext(Dispatchers.IO) {
        get(if (game.shortcut) "/launch?shortcut=${game.appId}" else "/launch?appid=${game.appId}", post = true)
    }

    /**
     * The picture a non-Steam game has in Steam on the PC, kept on the phone (a day for none), as
     * a file the image loader can read; "" when it has none.
     */
    private fun shortcutArt(id: Int): String {
        val folder = File(dir, "art").apply { mkdirs() }
        val file = File(folder, "$id.img")
        val none = File(folder, "$id.none")
        if (file.length() > 0) return "file://" + file.absolutePath
        if (System.currentTimeMillis() - none.lastModified() < DAY_MS) return ""
        val p = _pairing.value ?: return ""
        return runCatching {
            file.writeBytes(PcClient.requestBytes(p.host, p.port, "/art?shortcut=$id", p.code, timeoutMs = 8_000))
            "file://" + file.absolutePath
        }.getOrElse {
            file.delete()
            none.writeText("")
            none.setLastModified(System.currentTimeMillis())
            ""
        }
    }

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
        val installed = steam.installedGames.filter { g -> PcLibraryMath.countsAsInstalled(items[g.appId]?.type) }
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
            installed = (
                installed.map { entry(it.appId) } +
                    steam.shortcuts.map { LibraryEntry(it.appId, it.name, shortcutArt(it.appId), installed = true, sizeBytes = 0, minutes = 0, shortcut = true) }
                ).sortedBy { it.name.lowercase() },
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

        private const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}

object PcLibraryMath {
    /**
     * Whether an installed app belongs in Installed, by its kind in the Steam store (EStoreAppType):
     * games, demos, mods, software and playtests do, as in Steam's library; DLC, soundtracks,
     * videos and Steam's tools don't. One the store doesn't know (delisted) counts.
     */
    fun countsAsInstalled(type: Int?): Boolean = type == null || type in INSTALLED_KINDS

    private val INSTALLED_KINDS = setOf(0, 1, 2, 6, 12)
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
                .put("installed", e.installed).put("sizeBytes", e.sizeBytes).put("minutes", e.minutes).put("shortcut", e.shortcut)
        },
    )

    private fun entries(a: JSONArray?): List<LibraryEntry> = if (a == null) {
        emptyList()
    } else {
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            LibraryEntry(
                o.optInt("appId"), o.optString("name"), o.optString("coverUrl"), o.optBoolean("installed"),
                o.optLong("sizeBytes"), o.optLong("minutes"), o.optBoolean("shortcut"),
            )
        }
    }
}
