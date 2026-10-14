package com.jambo199.vividpulse.pc

import org.json.JSONArray
import org.json.JSONObject

/** A PC running Vivid Pulse PC, as it answers on the network. */
data class PcFound(val name: String, val host: String, val port: Int, val version: String?)

data class PcOs(val name: String?, val version: String?, val build: String?, val installed: String?)
data class PcCpu(val name: String?, val cores: Int?, val threads: Int?, val maxMhz: Int?, val l2Kb: Int?, val l3Kb: Int?, val socket: String?)
data class PcGpu(val name: String, val vramBytes: Long?, val driver: String?, val width: Int?, val height: Int?, val refreshHz: Int?)
data class PcStick(val bytes: Long?, val speedMts: Int?, val configuredMts: Int?, val type: String?, val maker: String?, val part: String?, val slot: String?)
data class PcDrive(val model: String?, val bytes: Long?, val kind: String?, val bus: String?)
data class PcVolume(val letter: String, val label: String?, val bytes: Long?, val freeBytes: Long?)
data class PcMonitor(val name: String?, val maker: String?)

/** The PC's fixed specs. */
data class PcSpecs(
    val name: String,
    val maker: String?,
    val model: String?,
    val os: PcOs,
    val cpu: PcCpu,
    val gpus: List<PcGpu>,
    val memoryBytes: Long?,
    val sticks: List<PcStick>,
    val boardMaker: String?,
    val boardModel: String?,
    val bios: String?,
    val drives: List<PcDrive>,
    val volumes: List<PcVolume>,
    val monitors: List<PcMonitor>,
)

data class PcGpuLive(
    val name: String?,
    val tempC: Float?,
    val loadPercent: Float?,
    val vramUsedMb: Float?,
    val vramTotalMb: Float?,
    val powerW: Float?,
    val clockMhz: Float?,
    val fanPercent: Float?,
)

/** How the PC is doing right now. */
data class PcLive(
    val at: Long,
    val cpuPercent: Float?,
    val cpuCores: List<Float>,
    val cpuMhz: Int?,
    val memoryUsedBytes: Long?,
    val memoryTotalBytes: Long?,
    val gpu: PcGpuLive?,
    val downBytesPerSec: Long?,
    val upBytesPerSec: Long?,
    val uptimeSec: Long?,
    /** The Steam game being played, 0 for none. */
    val playingAppId: Int,
)

/** A game in the PC's Steam library. */
data class PcGame(val appId: Int, val name: String?, val installed: Boolean, val sizeBytes: Long, val minutes: Long, val lastPlayed: Long)

/** One of your Steam collections. A dynamic one (built from filters) has no list of games. */
data class PcCollection(val id: String?, val name: String, val appIds: List<Int>, val dynamic: Boolean)

data class PcSteam(
    val installed: Boolean,
    val userName: String?,
    val games: List<PcGame>,
    val collections: List<PcCollection>,
    /** Every app in the Steam library on this PC. */
    val libraryAppIds: List<Int>,
) {
    val installedGames: List<PcGame> get() = games.filter { it.installed }

    /** The collection named "Purchased" (or similar) when you made one by adding games to it. */
    val purchasedCollection: PcCollection?
        get() = collections.firstOrNull { !it.dynamic && it.appIds.isNotEmpty() && it.name.contains("purchas", ignoreCase = true) }
}

/** A Steam download or update on the PC. [state] is "downloading", "installing", "paused" or "queued". */
data class PcDownload(
    val appId: Int,
    val name: String?,
    val state: String,
    val bytesDone: Long,
    val bytesTotal: Long,
    val stagedDone: Long,
    val stagedTotal: Long,
    val bytesPerSec: Long,
) {
    /** How far along (0..1), when Steam says. */
    val fraction: Float?
        get() = when {
            state == "installing" && stagedTotal > 0 -> stagedDone.toFloat() / stagedTotal
            bytesTotal > 0 -> bytesDone.toFloat() / bytesTotal
            else -> null
        }?.coerceIn(0f, 1f)

    /** Seconds left at the current speed. */
    val secondsLeft: Long?
        get() = if (state == "downloading" && bytesPerSec > 0 && bytesTotal > bytesDone) (bytesTotal - bytesDone) / bytesPerSec else null
}

/** A download that finished (on the PC's clock). */
data class PcFinished(val appId: Int, val name: String?, val at: Long)

data class PcDownloads(val at: Long, val downloads: List<PcDownload>, val finished: List<PcFinished>)

/** Reads what Vivid Pulse PC sends. PowerShell writes numbers as floats and lists of one as lists. */
object PcParse {
    fun found(json: String, host: String): PcFound? = runCatching {
        val o = JSONObject(json)
        if (o.optString("app") != "VividPulsePC") return null
        PcFound(o.optString("name").ifBlank { host }, host, o.optInt("port", PcClient.PORT), o.optString("version").ifBlank { null })
    }.getOrNull()

    fun specs(json: String): PcSpecs {
        val o = JSONObject(json)
        val os = o.optJSONObject("os") ?: JSONObject()
        val cpu = o.optJSONObject("cpu") ?: JSONObject()
        val memory = o.optJSONObject("memory") ?: JSONObject()
        val board = o.optJSONObject("board") ?: JSONObject()
        return PcSpecs(
            name = o.optString("name").ifBlank { "PC" },
            maker = o.str("maker"),
            model = o.str("model"),
            os = PcOs(os.str("name"), os.str("version"), os.str("build"), os.str("installed")),
            cpu = PcCpu(cpu.str("name"), cpu.int("cores"), cpu.int("threads"), cpu.int("maxMhz"), cpu.int("l2Kb"), cpu.int("l3Kb"), cpu.str("socket")),
            gpus = o.objects("gpus").mapNotNull { g ->
                val name = g.str("name") ?: return@mapNotNull null
                PcGpu(name, g.long("vramBytes"), g.str("driver"), g.int("width"), g.int("height"), g.int("refreshHz"))
            },
            memoryBytes = memory.long("totalBytes"),
            sticks = memory.objects("sticks").map { s ->
                PcStick(s.long("bytes"), s.int("speedMts"), s.int("configuredMts"), s.str("type"), s.str("maker"), s.str("part"), s.str("slot"))
            },
            boardMaker = board.str("maker"),
            boardModel = board.str("model"),
            bios = board.str("bios"),
            drives = o.objects("drives").map { d -> PcDrive(d.str("model"), d.long("bytes"), d.str("kind"), d.str("bus")) },
            volumes = o.objects("volumes").mapNotNull { v -> v.str("letter")?.let { PcVolume(it, v.str("label"), v.long("bytes"), v.long("freeBytes")) } },
            monitors = o.objects("monitors").map { m -> PcMonitor(m.str("name"), m.str("maker")) }.filter { !it.name.isNullOrBlank() },
        )
    }

    fun live(json: String): PcLive {
        val o = JSONObject(json)
        val g = o.optJSONObject("gpu")
        return PcLive(
            at = o.long("at") ?: System.currentTimeMillis(),
            cpuPercent = o.float("cpuPercent"),
            cpuCores = o.floats("cpuCores"),
            cpuMhz = o.int("cpuMhz"),
            memoryUsedBytes = o.long("memoryUsedBytes"),
            memoryTotalBytes = o.long("memoryTotalBytes"),
            gpu = g?.let {
                PcGpuLive(it.str("name"), it.float("tempC"), it.float("loadPercent"), it.float("vramUsedMb"), it.float("vramTotalMb"), it.float("powerW"), it.float("clockMhz"), it.float("fanPercent"))
            },
            downBytesPerSec = o.long("downBytesPerSec"),
            upBytesPerSec = o.long("upBytesPerSec"),
            uptimeSec = o.long("uptimeSec"),
            playingAppId = o.int("playingAppId") ?: 0,
        )
    }

    fun steam(json: String): PcSteam {
        val o = JSONObject(json)
        return PcSteam(
            installed = o.optBoolean("installed"),
            userName = o.optJSONObject("user")?.str("name"),
            games = o.objects("games").mapNotNull { g ->
                val id = g.int("appId") ?: return@mapNotNull null
                PcGame(id, g.str("name"), g.optBoolean("installed"), g.long("sizeBytes") ?: 0, g.long("minutes") ?: 0, g.long("lastPlayed") ?: 0)
            },
            collections = o.objects("collections").mapNotNull { c ->
                val name = c.str("name") ?: return@mapNotNull null
                PcCollection(c.str("id"), name, c.ints("appIds"), c.optBoolean("dynamic"))
            },
            libraryAppIds = o.ints("libraryAppIds"),
        )
    }

    fun downloads(json: String): PcDownloads {
        val o = JSONObject(json)
        return PcDownloads(
            at = o.long("at") ?: System.currentTimeMillis(),
            downloads = o.objects("downloads").mapNotNull { d ->
                val id = d.int("appId") ?: return@mapNotNull null
                PcDownload(
                    appId = id,
                    name = d.str("name"),
                    state = d.str("state") ?: "queued",
                    bytesDone = d.long("bytesDone") ?: 0,
                    bytesTotal = d.long("bytesTotal") ?: 0,
                    stagedDone = d.long("stagedDone") ?: 0,
                    stagedTotal = d.long("stagedTotal") ?: 0,
                    bytesPerSec = d.long("bytesPerSec") ?: 0,
                )
            },
            finished = o.objects("finished").mapNotNull { f ->
                val id = f.int("appId") ?: return@mapNotNull null
                PcFinished(id, f.str("name"), f.long("at") ?: 0)
            },
        )
    }

    private fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key).trim().ifBlank { null }
    private fun JSONObject.num(key: String): Double? = if (isNull(key) || !has(key)) null else optDouble(key).takeUnless { it.isNaN() }
    private fun JSONObject.long(key: String): Long? = num(key)?.toLong()
    private fun JSONObject.int(key: String): Int? = num(key)?.toInt()
    private fun JSONObject.float(key: String): Float? = num(key)?.toFloat()

    /**
     * A list. Windows PowerShell sometimes sends a single item where a list was meant, or wraps a
     * list as {"value": [...], "Count": n}.
     */
    private fun JSONObject.list(key: String): JSONArray? = when (val v = opt(key)) {
        is JSONArray -> v
        is JSONObject -> v.optJSONArray("value")?.takeIf { v.has("Count") } ?: JSONArray().put(v)
        null, JSONObject.NULL -> null
        else -> JSONArray().put(v)
    }

    private fun JSONObject.objects(key: String): List<JSONObject> =
        list(key)?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }.orEmpty()

    private fun JSONObject.ints(key: String): List<Int> =
        list(key)?.let { a -> (0 until a.length()).mapNotNull { i -> a.optDouble(i).takeUnless { it.isNaN() }?.toInt() } }.orEmpty()

    private fun JSONObject.floats(key: String): List<Float> =
        list(key)?.let { a -> (0 until a.length()).map { a.optDouble(it, 0.0).toFloat() } }.orEmpty()
}
