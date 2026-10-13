package com.jambo199.vividpulse.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.jambo199.vividpulse.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val notes: String,
    /** The build's list of changes, one per line. */
    val changes: List<String> = emptyList(),
    /** Major, minor or small, when the release says. */
    val updateSize: UpdateSize? = null,
)

/** What changed in the version that's installed now, shown once after an update. */
data class WhatsNew(
    val versionName: String,
    val title: String,
    val changes: List<String>,
    val size: UpdateSize? = null,
    /** Earlier updates included, when versions were skipped. */
    val alsoIncludes: Int = 0,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Ready(val info: UpdateInfo, val file: File) : UpdateState
    data class Failed(val message: String, val info: UpdateInfo?) : UpdateState
}

/**
 * Checks the app's GitHub release for a newer build, downloads it, verifies it and hands it to
 * Android's installer. Android always asks the user to confirm the install.
 */
class UpdateManager(private val context: Context, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val dir = File(context.cacheDir, "updates")
    private var job: Job? = null
    private var checkedThisLaunch = false
    private var installWhenAllowed = false
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)

    /** The version whose banner was put off with "Later" (until the app is next opened). */
    private val _snoozed = MutableStateFlow<Int?>(null)
    val snoozed: StateFlow<Int?> = _snoozed.asStateFlow()

    /** When the last check finished, or 0. */
    val lastCheckedAt: Long get() = prefs.getLong("lastCheck", 0L)

    init {
        // Downloads for builds that are now installed (or older) are no longer needed.
        dir.listFiles()?.forEach { f ->
            val code = f.name.removePrefix("VividPulse-").removeSuffix(".apk").toIntOrNull()
            if (code == null || code <= BuildConfig.VERSION_CODE) f.delete()
        }
    }

    /** Called when the app opens; checks once per launch. */
    fun checkOnLaunch(enabled: Boolean) {
        if (checkedThisLaunch || !enabled) return
        checkedThisLaunch = true
        check(manual = false)
    }

    /** Called when the app comes back to the screen: checks again if it's been a while. */
    fun checkIfStale(enabled: Boolean) {
        if (!enabled || !checkedThisLaunch) return
        val s = _state.value
        if (s is UpdateState.Downloading || s is UpdateState.Ready || s is UpdateState.Checking) return
        if (System.currentTimeMillis() - lastCheckedAt > RECHECK_MS) check(manual = false)
    }

    fun snooze() {
        (_state.value as? UpdateState.Available)?.let { _snoozed.value = it.info.versionCode }
    }

    /** What changed in the version that's installed now: from the changelog it came with, or the notes it was downloaded with. */
    fun whatsNew(): WhatsNew? {
        val all = Changelog.bundled(context)
        all.firstOrNull { it.code == BuildConfig.VERSION_CODE }?.let { mine ->
            // Everything since the version this phone had before, when it skipped some.
            val from = prefs.getInt("updatedFrom", -1)
            val covered = all.filter { it.code in (from + 1)..BuildConfig.VERSION_CODE }.takeIf { from > 0 }.orEmpty().ifEmpty { listOf(mine) }
            return WhatsNew(
                BuildConfig.VERSION_NAME,
                mine.title,
                covered.flatMap { it.changes }.distinct(),
                covered.minOf { it.size },
                covered.size - 1,
            )
        }
        if (prefs.getInt("notesFor", -1) != BuildConfig.VERSION_CODE) return null
        val title = prefs.getString("notesTitle", null) ?: return null
        val changes = prefs.getString("notesChanges", "").orEmpty().lines().filter { it.isNotBlank() }
        return WhatsNew(BuildConfig.VERSION_NAME, title, changes)
    }

    /** [whatsNew], but only the first time the app opens after an update (however it was installed). */
    fun takeWhatsNew(): WhatsNew? {
        val lastRun = prefs.getInt("lastRunCode", -1)
        prefs.edit().putInt("lastRunCode", BuildConfig.VERSION_CODE).apply()
        if (prefs.getInt("notesShown", -1) == BuildConfig.VERSION_CODE) return null
        // Updated through the app, or opened for the first time since an update from elsewhere.
        val updated = prefs.getInt("notesFor", -1) == BuildConfig.VERSION_CODE || lastRun in 1 until BuildConfig.VERSION_CODE
        if (!updated) return null
        if (lastRun in 1 until BuildConfig.VERSION_CODE) prefs.edit().putInt("updatedFrom", lastRun).apply()
        val notes = whatsNew() ?: return null
        prefs.edit().putInt("notesShown", BuildConfig.VERSION_CODE).apply()
        return notes
    }

    /**
     * Every version and what it changed: the list this build came with, plus any newer versions
     * from the release page (kept for when you're offline).
     */
    suspend fun changelog(refresh: Boolean): List<ChangelogEntry> = withContext(Dispatchers.IO) {
        val bundled = Changelog.bundled(context)
        val saved = File(context.filesDir, Changelog.FILE)
        if (refresh) {
            runCatching { httpGetText(BuildConfig.UPDATE_BASE_URL + Changelog.FILE) }
                .onSuccess { text -> if (Changelog.parse(text).isNotEmpty()) saved.writeText(text) }
                .onFailure { Log.w(TAG, "Couldn't fetch the changelog", it) }
        }
        val fetched = runCatching { if (saved.isFile) Changelog.parse(saved.readText()) else emptyList() }.getOrDefault(emptyList())
        Changelog.merge(bundled, fetched)
    }

    fun check(manual: Boolean) {
        val current = _state.value
        if (current is UpdateState.Downloading || current is UpdateState.Checking) return
        job?.cancel()
        job = scope.launch {
            if (manual) _state.value = UpdateState.Checking
            val result = withContext(Dispatchers.IO) { runCatching { fetchInfo() } }
            _state.value = result.fold(
                onSuccess = { info ->
                    Log.i(TAG, "Latest build ${info.versionCode}, installed ${BuildConfig.VERSION_CODE}")
                    prefs.edit().putLong("lastCheck", System.currentTimeMillis()).apply()
                    if (info.versionCode > BuildConfig.VERSION_CODE) {
                        val downloaded = apkFile(info)
                        if (downloaded.isFile && downloaded.length() > 0) {
                            UpdateState.Ready(info, downloaded)
                        } else {
                            UpdateState.Available(info)
                        }
                    } else {
                        UpdateState.UpToDate
                    }
                },
                onFailure = { e ->
                    Log.w(TAG, "Update check failed", e)
                    if (manual) UpdateState.Failed("Couldn't check for updates. Are you online?", null) else UpdateState.Idle
                },
            )
        }
    }

    /** Downloads the new build, then opens the installer. */
    fun download() {
        val info = when (val s = _state.value) {
            is UpdateState.Available -> s.info
            is UpdateState.Failed -> s.info ?: return check(manual = true)
            is UpdateState.Ready -> {
                install()
                return
            }
            else -> return
        }
        job?.cancel()
        job = scope.launch {
            _state.value = UpdateState.Downloading(info, 0f)
            try {
                val file = withContext(Dispatchers.IO) { downloadAndVerify(info) }
                // Kept so the new version can say what's new the first time it opens.
                prefs.edit()
                    .putInt("notesFor", info.versionCode)
                    .putString("notesTitle", info.notes)
                    .putString("notesChanges", info.changes.joinToString("\n"))
                    .apply()
                _state.value = UpdateState.Ready(info, file)
                install()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Update download failed", e)
                _state.value = UpdateState.Failed(
                    if (e is SecurityException) e.message ?: "The download didn't check out" else "Download failed. Check your connection and try again.",
                    info,
                )
            }
        }
    }

    /** Opens Android's install screen for the downloaded build. Returns false if it couldn't yet. */
    fun install(): Boolean {
        val ready = _state.value as? UpdateState.Ready ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            // Android needs "Install unknown apps" switched on for us first; continue on return.
            installWhenAllowed = true
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", ready.file)
        return startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }

    /** The app came back to the foreground (e.g. from the "Install unknown apps" screen). */
    fun onResume() {
        if (!installWhenAllowed) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) return
        installWhenAllowed = false
        install()
    }

    private fun startActivity(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't open ${intent.action}", e)
        false
    }

    private fun apkFile(info: UpdateInfo) = File(dir, "VividPulse-${info.versionCode}.apk")

    private fun fetchInfo(): UpdateInfo {
        val base = BuildConfig.UPDATE_BASE_URL
        val o = JSONObject(httpGetText(base + "version.json"))
        val code = o.getInt("versionCode")
        return UpdateInfo(
            versionCode = code,
            versionName = o.optString("versionName").ifBlank { "1.0.$code" },
            apkUrl = base + o.optString("apk").ifBlank { "VividPulse.apk" },
            sha256 = o.optString("sha256").lowercase(),
            sizeBytes = o.optLong("size"),
            notes = o.optString("notes"),
            changes = UpdateNotes.changes(o.optString("changes")),
            updateSize = o.optString("updateSize").ifBlank { null }?.let(UpdateSize::of),
        )
    }

    private fun downloadAndVerify(info: UpdateInfo): File {
        dir.mkdirs()
        val target = apkFile(info)
        val tmp = File(dir, target.name + ".part")
        val digest = MessageDigest.getInstance("SHA-256")
        val conn = open(info.apkUrl)
        try {
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
            var read = 0L
            var lastStep = -1
            conn.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        read += n
                        if (total > 0) {
                            val step = (read * 100 / total).toInt()
                            if (step != lastStep) {
                                lastStep = step
                                _state.value = UpdateState.Downloading(info, (read.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }

        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (info.sha256.isNotBlank() && actual != info.sha256) {
            tmp.delete()
            throw SecurityException("The downloaded update was damaged. Please try again.")
        }
        // Make sure it really is a newer Vivid Pulse before offering to install it.
        @Suppress("DEPRECATION")
        val archive = context.packageManager.getPackageArchiveInfo(tmp.path, 0)
        if (archive == null || archive.packageName != context.packageName ||
            PackageInfoCompat.getLongVersionCode(archive) <= BuildConfig.VERSION_CODE
        ) {
            tmp.delete()
            throw SecurityException("The downloaded file isn't a newer Vivid Pulse.")
        }
        if (!tmp.renameTo(target)) throw IOException("Couldn't save the update")
        return target
    }

    private fun httpGetText(url: String): String {
        val conn = open(url)
        try {
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("User-Agent", "VividPulse/${BuildConfig.VERSION_NAME} (Android)")
        conn.setRequestProperty("Cache-Control", "no-cache")
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            throw IOException("HTTP $code for $url")
        }
        return conn
    }

    companion object {
        private const val TAG = "UpdateManager"
        private const val RECHECK_MS = 6 * 60 * 60 * 1000L
    }
}

object UpdateNotes {
    /** A commit message body to a list of changes: "- Fixed x" lines, or wrapped sentences joined up. */
    fun changes(body: String): List<String> {
        val items = mutableListOf<StringBuilder>()
        for (raw in body.lines()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> items += StringBuilder()
                line.startsWith("- ") || line.startsWith("* ") -> items += StringBuilder(line.drop(2))
                items.isEmpty() -> items += StringBuilder(line)
                else -> items.last().let { if (it.isEmpty()) it.append(line) else it.append(' ').append(line) }
            }
        }
        return items.map { it.toString().trim() }.filter { it.isNotEmpty() }
    }
}
