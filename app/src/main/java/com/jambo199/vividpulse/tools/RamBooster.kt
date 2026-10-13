package com.jambo199.vividpulse.tools

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.SharedMemory
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresApi
import coil.imageLoader
import com.jambo199.vividpulse.system.MemInfo
import com.jambo199.vividpulse.system.SysFs
import com.jambo199.vividpulse.util.Format
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How memory stands at one moment, in bytes. */
data class MemSnapshot(
    val total: Long,
    val available: Long,
    val cached: Long,
    val swapTotal: Long,
    val swapUsed: Long,
    val lowMemory: Boolean,
    val threshold: Long,
)

enum class StepStatus { WAITING, RUNNING, DONE, SKIPPED }

data class BoostStep(val title: String, val explain: String, val status: StepStatus = StepStatus.WAITING, val detail: String? = null)

/** What the result was made of, for the summary. */
data class BoostResult(
    val before: MemSnapshot,
    val after: MemSnapshot,
    val ownBeforeKb: Long,
    val ownAfterKb: Long,
    val peakReserved: Long,
) {
    val gained: Long get() = after.available - before.available
    val compressed: Long get() = (after.swapUsed - before.swapUsed).coerceAtLeast(0)
    val cacheCleared: Long get() = (before.cached - after.cached).coerceAtLeast(0)
}

data class BoostState(
    val running: Boolean = false,
    val steps: List<BoostStep> = RamBooster.STEPS,
    /** Available memory in GB, a reading every [RamBooster.SAMPLE_MS] while it runs. */
    val availableHistory: List<Float> = emptyList(),
    val sampledAt: Long = 0,
    val reserved: Long = 0,
    val result: BoostResult? = null,
    val error: String? = null,
)

/**
 * Frees up RAM the only way an app can on today's Android (since Android 14 apps can't close other
 * apps): it briefly reserves a big block of memory. Android reacts as it always does when memory
 * gets tight: it drops cached files, compresses idle memory (zram) and closes apps that
 * haven't been used for a while. Then the block is handed straight back, leaving that room free.
 *
 * It never goes below a safety floor ([floorFor]) and stops early if Android signals low memory, so
 * the app you're in, music and anything with a notification keep running.
 */
class RamBooster(private val context: Context, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(BoostState())
    val state: StateFlow<BoostState> = _state.asStateFlow()
    private var job: Job? = null
    private val prefs = context.getSharedPreferences("ram", Context.MODE_PRIVATE)

    /** The last run's gain and when it was, if any. */
    val lastRun: Pair<Long, Long>?
        get() = prefs.getLong("at", 0L).takeIf { it > 0 }?.let { it to prefs.getLong("gained", 0L) }

    fun start() {
        if (_state.value.running) return
        // Runs in the app's scope: once started it always finishes and hands the memory back,
        // even if you leave the page.
        job = scope.launch(Dispatchers.Default) { run() }
    }

    private suspend fun run() {
        _state.value = BoostState(running = true)
        val held = ArrayList<AutoCloseable>()
        try {
            // 1. Measure.
            step(0, StepStatus.RUNNING)
            val before = snapshot()
            sample(before)
            step(0, StepStatus.DONE, "${Format.bytes(before.total - before.available)} in use · ${Format.bytes(before.available)} available" +
                if (before.swapTotal > 0) " · ${MemInfo.extraRamName} ${Format.bytes(before.swapUsed)} used" else "")

            // 2. This app's own spare memory.
            step(1, StepStatus.RUNNING)
            val ownBefore = Debug.getPss()
            withContext(Dispatchers.Main) { context.imageLoader.memoryCache?.clear() }
            Runtime.getRuntime().gc()
            delay(300)
            val ownAfter = Debug.getPss()
            step(1, StepStatus.DONE, "Vivid Pulse: ${Format.bytes(ownBefore * 1024)} → ${Format.bytes(ownAfter * 1024)}")

            // 3. Make room.
            var peak = 0L
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
                step(2, StepStatus.SKIPPED, "Needs Android 8.1 or newer")
            } else {
                step(2, StepStatus.RUNNING, "Starting…")
                val floor = floorFor(before.total, before.threshold)
                val cap = (before.total * MAX_SHARE).toLong()
                val started = SystemClock.elapsedRealtime()
                var reserved = 0L
                while (scope.isActive && reserved + CHUNK <= cap && SystemClock.elapsedRealtime() - started < MAX_PRESS_MS) {
                    val now = snapshot()
                    sample(now, reserved)
                    if (now.lowMemory || now.available - CHUNK < floor) break
                    held += reserveChunk() ?: break
                    reserved += CHUNK
                    peak = reserved
                    step(2, StepStatus.RUNNING, "Holding ${Format.bytes(reserved)} · ${Format.bytes(now.available)} still available")
                    delay(60)
                }
                // Give Android a moment to finish tidying before letting go.
                repeat(4) {
                    delay(SAMPLE_MS)
                    sample(snapshot(), reserved)
                }
                step(2, StepStatus.DONE, if (peak > 0) "Held up to ${Format.bytes(peak)} while Android made room" else "Memory was already short, so it held back")
            }

            // 4. Hand it back.
            step(3, StepStatus.RUNNING)
            held.forEach { runCatching { it.close() } }
            held.clear()
            sample(snapshot(), 0)
            step(3, StepStatus.DONE, if (peak > 0) "Released ${Format.bytes(peak)}" else "Nothing to release")

            // 5. Compare, once things settle.
            step(4, StepStatus.RUNNING)
            repeat(8) {
                delay(SAMPLE_MS)
                sample(snapshot(), 0)
            }
            val after = snapshot()
            val result = BoostResult(before, after, ownBefore, ownAfter, peak)
            Log.i(TAG, "Available ${before.available} -> ${after.available}, held up to $peak, swap ${before.swapUsed} -> ${after.swapUsed}")
            step(4, StepStatus.DONE, describe(result))
            prefs.edit().putLong("at", System.currentTimeMillis()).putLong("gained", result.gained).apply()
            _state.value = _state.value.copy(running = false, result = result, reserved = 0)
        } catch (e: Exception) {
            _state.value = _state.value.copy(running = false, error = e.message ?: e.javaClass.simpleName, reserved = 0)
        } finally {
            held.forEach { runCatching { it.close() } }
        }
    }

    /** A block of memory that really takes up RAM: every page is written to. */
    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private fun reserveChunk(): AutoCloseable? = try {
        val sm = SharedMemory.create("vivid-pulse-room", CHUNK.toInt())
        val buffer = sm.mapReadWrite()
        var i = 0
        while (i < CHUNK) {
            buffer.put(i, 1)
            i += PAGE
        }
        SharedMemory.unmap(buffer)
        sm
    } catch (e: Exception) {
        null
    }

    private fun sample(s: MemSnapshot, reserved: Long = _state.value.reserved) {
        val h = (_state.value.availableHistory + s.available / 1e9f).takeLast(120)
        _state.value = _state.value.copy(availableHistory = h, sampledAt = SystemClock.uptimeMillis(), reserved = reserved)
    }

    private fun step(index: Int, status: StepStatus, detail: String? = null) {
        val steps = _state.value.steps.toMutableList()
        steps[index] = steps[index].copy(status = status, detail = detail ?: steps[index].detail)
        _state.value = _state.value.copy(steps = steps)
    }

    private suspend fun snapshot(): MemSnapshot = withContext(Dispatchers.IO) {
        val am = context.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val m = SysFs.read("/proc/meminfo", remember = false)?.let(MemInfo::parse).orEmpty()
        fun kb(k: String) = (m[k] ?: 0L) * 1024
        MemSnapshot(
            total = info.totalMem,
            available = info.availMem,
            cached = kb("Cached"),
            swapTotal = kb("SwapTotal"),
            swapUsed = (kb("SwapTotal") - kb("SwapFree")).coerceAtLeast(0),
            lowMemory = info.lowMemory,
            threshold = info.threshold,
        )
    }

    companion object {
        private const val TAG = "RamBooster"
        const val SAMPLE_MS = 250L
        private const val PAGE = 4096
        private const val CHUNK = 128L * 1024 * 1024
        /** Never hold more than half the phone's RAM… */
        private const val MAX_SHARE = 0.5
        /** …nor for longer than this. */
        private const val MAX_PRESS_MS = 9_000L

        /** How much must always stay available: 15% of RAM, and well above Android's low-memory line. */
        fun floorFor(total: Long, threshold: Long): Long = maxOf((total * 0.15).toLong(), threshold * 3 / 2)

        fun describe(r: BoostResult): String = when {
            r.gained >= 50_000_000 -> "Available ${Format.bytes(r.before.available)} → ${Format.bytes(r.after.available)} (+${Format.bytes(r.gained)})"
            else -> "Available ${Format.bytes(r.after.available)}: Android didn't need to free much, memory was already in good shape"
        }

        val STEPS = listOf(
            BoostStep("Check memory", "Reads how much RAM is in use, cached, and compressed."),
            BoostStep("Tidy Vivid Pulse itself", "Clears this app's own picture cache and spare memory."),
            BoostStep(
                "Ask Android to make room",
                "Briefly holds a large block of memory. Android does what it always does when memory runs short: drops cached files, compresses idle memory and closes apps you haven't used in a while.",
            ),
            BoostStep("Give it all back", "Releases the block at once, so that space is left free."),
            BoostStep("Compare", "Measures again and shows what changed."),
        )
    }
}
