package com.jambo199.vividpulse.system

import android.content.Context
import com.jambo199.vividpulse.games.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import java.util.Locale

/** One camera lens as GSMArena lists it. */
data class SheetLens(
    val megapixels: Float?,
    val mm: Int?,
    val kind: String,
    /** Sensor size, e.g. 1/1.28". */
    val sensor: String?,
    val aperture: String?,
    val ois: Boolean,
    /** "3.7× optical" for zoom lenses. */
    val zoom: String?,
)

/** The back or front cameras. */
data class SheetCameras(
    val lenses: List<SheetLens>,
    /** "8K 30 fps · 4K 120 fps · 1080p 240 fps". */
    val video: String?,
    /** HDR video formats (Dolby Vision, HDR10+…); empty when it has none. */
    val hdrVideo: List<String>,
    /** Takes HDR photos; null when the specs don't say. */
    val hdrPhotos: Boolean?,
)

/** A phone's camera and screen specs from its GSMArena page. */
data class PhoneSheet(
    val name: String,
    val url: String,
    val back: SheetCameras?,
    val front: SheetCameras?,
    /** HDR formats the screen shows (from GSMArena's display line); empty when it has none. */
    val displayHdr: List<String>,
)

/** Reads GSMArena's search results and phone pages. */
object GsmArena {
    const val BASE = "https://www.gsmarena.com/"

    fun searchUrl(phone: String): String = BASE + "results.php3?sQuickSearch=yes&sName=" + URLEncoder.encode(phone, "UTF-8")

    /**
     * The phones on a search page: name and page address. Every link to a phone page counts
     * (phone pages are "maker_model-1234.php"; news and reviews have more to their names), so it
     * reads the desktop and the mobile site alike (the mobile site leaves out the quotes);
     * [bestMatch] then keeps only this phone.
     */
    fun parseResults(html: String): List<Pair<String, String>> =
        Regex("""<a href=["']?(?:https://(?:www|m)\.gsmarena\.com/)?([a-z0-9_]+-\d+\.php)["']?[\s>][^>]*>?(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(html)
            .mapNotNull { m ->
                val inner = m.groupValues[2]
                val name = text(inner).replace('\n', ' ').replace(Regex("\\s+"), " ").trim().ifBlank {
                    // A picture-only link: its title starts with the phone's name.
                    Regex("""title="([^"]+)"""").find(inner)?.groupValues?.get(1)?.substringBefore(" Android")?.substringBefore(". ")?.trim()
                }
                if (name.isNullOrBlank()) null else name to absolute(m.groupValues[1])
            }
            .distinctBy { it.second }
            .toList()

    /**
     * The result that is this phone: the same name, or failing that the closest one holding every
     * word of it ("Galaxy S24" must not turn into "Galaxy S24 Ultra" when the S24 itself is there).
     */
    fun bestMatch(results: List<Pair<String, String>>, phone: String): String? {
        val want = words(phone)
        if (want.isEmpty()) return null
        results.firstOrNull { words(it.first) == want }?.let { return it.second }
        return results.filter { words(it.first).containsAll(want) }
            .minByOrNull { words(it.first).size }
            ?.second
    }

    fun parsePhone(html: String, url: String): PhoneSheet? {
        val specs = Regex("""data-spec="([a-z0-9]+)"[^>]*>(.*?)</(?:td|h1|span)>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(html)
            .associate { it.groupValues[1] to text(it.groupValues[2]) }
        // The name: tagged on the desktop site; the mobile site only has it as the heading and title.
        val name = specs["modelname"]?.ifBlank { null }
            ?: Regex("""<h1[^>]*>(.*?)</h1>""", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1)?.let(::text)?.ifBlank { null }
            ?: Regex("""<title>(.*?)</title>""", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1)?.let(::text)?.substringBefore(" - ")?.ifBlank { null }
            ?: return null
        val back = cameras(specs["cam1modules"], specs["cam1features"], specs["cam1video"], front = false)
        val front = cameras(specs["cam2modules"], specs["cam2features"], specs["cam2video"], front = true)
        if (back == null && front == null) return null
        return PhoneSheet(name, url, back, front, hdrFormats(specs["displaytype"].orEmpty()))
    }

    private fun cameras(modules: String?, features: String?, video: String?, front: Boolean): SheetCameras? {
        // Widest first: ultra-wide, main, then zoom.
        val lenses = modules?.lines()?.mapNotNull { lens(it, front) }.orEmpty()
            .sortedBy { it.mm ?: if (it.kind.startsWith("Ultra-wide")) 13 else 999 }
        if (lenses.isEmpty()) return null
        return SheetCameras(
            lenses = lenses,
            video = video?.let(::videoSummary),
            hdrVideo = hdrFormats(video.orEmpty()),
            hdrPhotos = features?.let { Regex("""\bHDR\b""").containsMatchIn(it) },
        )
    }

    /** `50 MP, f/1.7, 35mm (wide), 1/1.28", 1.22µm, dual pixel PDAF, gimbal OIS`. */
    fun lens(line: String, front: Boolean): SheetLens? {
        val l = line.trim()
        if (l.isEmpty()) return null
        val mp = Regex("""(\d+(?:\.\d+)?)\s*MP""").find(l)?.groupValues?.get(1)?.toFloatOrNull()
        val mm = Regex("""(\d+)\s*mm\b""").find(l)?.groupValues?.get(1)?.toIntOrNull()
        val label = Regex("""\(([^)]*)\)""").find(l)?.groupValues?.get(1)?.lowercase(Locale.US).orEmpty()
        val kind = when {
            "periscope" in label -> "Periscope telephoto"
            "telephoto" in label -> "Telephoto"
            "ultrawide" in label || "ultra wide" in label -> if (front) "Ultra-wide selfie" else "Ultra-wide"
            "macro" in label -> "Macro"
            "depth" in label || "tof" in label -> "Depth"
            "wide" in label -> if (front) "Selfie" else "Main"
            label.isNotBlank() -> label.replaceFirstChar { it.titlecase(Locale.US) }
            front -> "Selfie"
            mm != null -> CameraMath.lensKind(mm, front = false)
            else -> "Camera"
        }
        // A line without megapixels is a depth sensor, or an accessory (vivo's add-on zoom lens): not a camera of its own.
        if (mp == null && kind != "Depth") return null
        return SheetLens(
            megapixels = mp,
            mm = mm,
            kind = kind,
            sensor = Regex("""(1/\d+(?:\.\d+)?"|\b1(?:\.\d+)?")""").find(l)?.value,
            aperture = Regex("""f/(\d+(?:\.\d+)?)""").find(l)?.value,
            ois = Regex("""\bOIS\b""").containsMatchIn(l),
            zoom = Regex("""(\d+(?:\.\d+)?)x optical zoom""", RegexOption.IGNORE_CASE).find(l)?.groupValues?.get(1)?.let { "$it× optical" },
        )
    }

    /** `8K@24/30fps, 4K@24/30/60/120fps, 1080p@30/60/240fps, gyro-EIS` → "8K 30 fps · 4K 120 fps · 1080p 240 fps". */
    fun videoSummary(video: String): String? {
        val order = listOf("8K", "6K", "4K", "1440p", "1080p", "720p")
        val best = Regex("""(8K|6K|4K|1440p|1080p|720p)@([\d/]+)\s*fps""", RegexOption.IGNORE_CASE).findAll(video)
            .mapNotNull { m -> m.groupValues[2].split('/').mapNotNull { it.toIntOrNull() }.maxOrNull()?.let { m.groupValues[1].uppercase(Locale.US).replace("P", "p") to it } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, fps) -> fps.max() }
        val shown = order.filter { it in best }.take(3)
        if (shown.isEmpty()) return null
        return shown.joinToString(" · ") { "$it ${best.getValue(it)} fps" }
    }

    /** HDR formats named in a line of specs; "HDR" alone when it only says HDR. */
    fun hdrFormats(line: String): List<String> {
        val found = buildList {
            if (line.contains("Dolby Vision", ignoreCase = true)) add("Dolby Vision")
            if (line.contains("HDR10+")) add("HDR10+")
            if (Regex("""HDR10(?!\+)""").containsMatchIn(line)) add("HDR10")
            if (line.contains("HDR Vivid", ignoreCase = true)) add("HDR Vivid")
            if (Regex("""\bHLG\b""").containsMatchIn(line)) add("HLG")
        }
        return found.ifEmpty { if (Regex("""\bHDR\b""").containsMatchIn(line)) listOf("HDR") else emptyList() }
    }

    /** Words of a phone's name for matching: "Galaxy S24+" and "galaxy s24 plus" are the same. */
    private fun words(name: String): Set<String> = name.lowercase(Locale.US)
        .replace("+", " plus ")
        .replace(Regex("[^a-z0-9 ]"), " ")
        .split(' ')
        .filter { it.isNotBlank() && it != "5g" && it != "4g" }
        .toSet()

    private fun absolute(href: String) = if (href.startsWith("http")) href else BASE + href.removePrefix("/")

    /** HTML to plain text: one line per <br>, tags gone, entities read. */
    fun text(html: String): String = html
        .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&quot;", "\"").replace("&#34;", "\"").replace("&#039;", "'").replace("&#39;", "'")
        .replace("&deg;", "°").replace("&micro;", "µ").replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&amp;", "&")
        .lines().joinToString("\n") { it.trim() }
        .trim()
}

/**
 * This phone's GSMArena specs: looked up once by its name and kept (a month), so the Cameras and
 * Display pages show the specs the maker publishes, not just what Android shares with apps.
 */
class PhoneSheets(context: Context) {
    private val dir = File(context.filesDir, "phonesheet").apply { mkdirs() }

    /** The specs for [phone], from the phone when kept, else from GSMArena; null when GSMArena hasn't got it. */
    suspend fun forPhone(phone: String): PhoneSheet? = withContext(Dispatchers.IO) {
        val key = phone.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { return@withContext null }
        val page = File(dir, "$key.html")
        val url = File(dir, "$key.url")
        val none = File(dir, "$key.none")
        val now = System.currentTimeMillis()
        val kept = if (page.exists() && url.exists()) runCatching { GsmArena.parsePhone(page.readText(), url.readText()) }.getOrNull() else null
        if (kept != null && now - page.lastModified() < KEEP_MS) return@withContext kept
        if (kept == null && none.exists() && now - none.lastModified() < RETRY_NONE_MS) return@withContext null
        val fresh = runCatching {
            val search = Http.get(GsmArena.searchUrl(phone), accept = "text/html", userAgent = Http.BROWSER)
            val results = GsmArena.parseResults(search)
            val match = GsmArena.bestMatch(results, phone)
            // A search with one answer can land straight on the phone's page.
            val direct = if (results.isEmpty()) GsmArena.parsePhone(search, GsmArena.searchUrl(phone)) else null
            if (direct != null) {
                direct.also {
                    page.writeText(search)
                    url.writeText(GsmArena.searchUrl(phone))
                    none.delete()
                }
            } else if (match == null) {
                none.writeText(results.joinToString("\n") { it.first })
                null
            } else {
                val html = Http.get(match, accept = "text/html", userAgent = Http.BROWSER)
                GsmArena.parsePhone(html, match)?.also {
                    page.writeText(html)
                    url.writeText(match)
                    none.delete()
                }
            }
        }.getOrNull()
        fresh ?: kept
    }

    private companion object {
        const val KEEP_MS = 30L * 24 * 3600 * 1000
        const val RETRY_NONE_MS = 3L * 24 * 3600 * 1000
    }
}
