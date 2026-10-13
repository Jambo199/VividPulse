package com.jambo199.vividpulse.tools

import com.jambo199.vividpulse.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

enum class SpeedPhase(val label: String) {
    IDLE("Ready"),
    PING("Checking response time"),
    DOWNLOAD("Download"),
    UPLOAD("Upload"),
    DONE("Done"),
    FAILED("Couldn't finish"),
}

data class SpeedState(
    val phase: SpeedPhase = SpeedPhase.IDLE,
    val pingMs: Float? = null,
    val jitterMs: Float? = null,
    val downMbps: Float? = null,
    val upMbps: Float? = null,
    /** The speed being measured right now. */
    val liveMbps: Float = 0f,
    /** How far through the current phase, 0..1. */
    val progress: Float = 0f,
    val downSamples: List<Float> = emptyList(),
    val upSamples: List<Float> = emptyList(),
    val error: String? = null,
)

/**
 * An internet speed test against Cloudflare's speed test servers (speed.cloudflare.com): response
 * time first, then download and upload over several connections at once, as speed test sites do.
 */
class SpeedTest {
    private val _state = MutableStateFlow(SpeedState())
    val state: StateFlow<SpeedState> = _state.asStateFlow()

    suspend fun run() = withContext(Dispatchers.IO) {
        _state.value = SpeedState(phase = SpeedPhase.PING)
        try {
            ping()
            transfer(upload = false)
            transfer(upload = true)
            _state.value = _state.value.copy(phase = SpeedPhase.DONE, liveMbps = 0f, progress = 1f)
        } catch (e: CancellationException) {
            _state.value = SpeedState()
            throw e
        } catch (e: Exception) {
            _state.value = _state.value.copy(phase = SpeedPhase.FAILED, liveMbps = 0f, error = e.message ?: e.javaClass.simpleName)
        }
    }

    private fun ping() {
        val times = mutableListOf<Float>()
        repeat(PINGS + 1) { i ->
            val started = System.nanoTime()
            val conn = open("$BASE/__down?bytes=0")
            conn.inputStream.use { it.readBytes() }
            val ms = (System.nanoTime() - started) / 1e6f
            // The first one also sets up the secure connection, so it's slower; skip it.
            if (i > 0) times += ms
            _state.value = _state.value.copy(progress = (i + 1f) / (PINGS + 1), pingMs = times.takeIf { it.isNotEmpty() }?.let(SpeedMath::median))
        }
        _state.value = _state.value.copy(pingMs = SpeedMath.median(times), jitterMs = SpeedMath.jitter(times))
    }

    private suspend fun transfer(upload: Boolean) = coroutineScope {
        val phase = if (upload) SpeedPhase.UPLOAD else SpeedPhase.DOWNLOAD
        _state.value = _state.value.copy(phase = phase, liveMbps = 0f, progress = 0f)
        val bytes = AtomicLong(0)
        val started = System.nanoTime()
        val deadline = started + PHASE_NS
        val workers = (1..CONNECTIONS).map {
            async {
                val alive = { isActive }
                while (isActive && System.nanoTime() < deadline) {
                    if (upload) uploadOnce(bytes, deadline, alive) else downloadOnce(bytes, deadline, alive)
                }
            }
        }
        // Report the speed over the last second, five times a second.
        val reporter = launch {
            val window = ArrayDeque<Pair<Long, Long>>()
            val samples = mutableListOf<Float>()
            while (isActive) {
                delay(200)
                val now = System.nanoTime()
                window.addLast(now to bytes.get())
                while (window.size > 1 && now - window.first().first > 1_000_000_000L) window.removeFirst()
                val (t0, b0) = window.first()
                val mbps = if (now > t0) SpeedMath.mbps(bytes.get() - b0, now - t0) else 0f
                samples += mbps
                val progress = ((now - started).toFloat() / PHASE_NS).coerceIn(0f, 1f)
                _state.value = if (upload) {
                    _state.value.copy(liveMbps = mbps, progress = progress, upSamples = samples.toList())
                } else {
                    _state.value.copy(liveMbps = mbps, progress = progress, downSamples = samples.toList())
                }
            }
        }
        workers.awaitAll()
        reporter.cancel()
        // Leave out the first moments, while connections are still speeding up.
        val all = if (upload) _state.value.upSamples else _state.value.downSamples
        val result = SpeedMath.result(all)
        _state.value = if (upload) _state.value.copy(upMbps = result, liveMbps = result) else _state.value.copy(downMbps = result, liveMbps = result)
    }

    private fun downloadOnce(counter: AtomicLong, deadline: Long, alive: () -> Boolean) {
        val conn = open("$BASE/__down?bytes=$DOWNLOAD_BYTES")
        try {
            conn.inputStream.use { input ->
                val buffer = ByteArray(64 * 1024)
                while (System.nanoTime() < deadline && alive()) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    counter.addAndGet(n.toLong())
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun uploadOnce(counter: AtomicLong, deadline: Long, alive: () -> Boolean) {
        val conn = URL("$BASE/__up").openConnection() as HttpURLConnection
        var stopped = false
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", AGENT)
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            conn.setFixedLengthStreamingMode(UPLOAD_BYTES)
            val chunk = Random.nextBytes(64 * 1024)
            var sent = 0L
            conn.outputStream.use { out ->
                while (sent < UPLOAD_BYTES) {
                    if (System.nanoTime() >= deadline || !alive()) {
                        stopped = true
                        break
                    }
                    val n = minOf(chunk.size.toLong(), UPLOAD_BYTES - sent).toInt()
                    out.write(chunk, 0, n)
                    sent += n
                    counter.addAndGet(n.toLong())
                }
            }
            conn.responseCode
        } catch (e: java.io.IOException) {
            // Stopping part-way through an upload (time's up) makes the connection complain.
            if (!stopped) throw e
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.useCaches = false
        conn.setRequestProperty("User-Agent", AGENT)
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            throw java.io.IOException("The test server answered $code")
        }
        return conn
    }

    companion object {
        private const val BASE = "https://speed.cloudflare.com"
        private val AGENT = "VividPulse/${BuildConfig.VERSION_NAME} (Android)"
        private const val PINGS = 10
        private const val CONNECTIONS = 4
        private const val PHASE_NS = 10_000_000_000L
        private const val DOWNLOAD_BYTES = 25_000_000L
        private const val UPLOAD_BYTES = 8_000_000L
    }
}

object SpeedMath {
    fun mbps(bytes: Long, nanos: Long): Float = if (nanos <= 0) 0f else (bytes * 8 / (nanos / 1e9) / 1e6).toFloat()

    fun median(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val s = values.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    /** Average change between one response time and the next. */
    fun jitter(values: List<Float>): Float =
        if (values.size < 2) 0f else values.zipWithNext { a, b -> kotlin.math.abs(a - b) }.average().toFloat()

    /**
     * The speed to report from the per-moment readings: skip the ramp-up (first quarter), then
     * take the 90th percentile of the rest, which matches how fast the connection can go without
     * being fooled by one lucky burst.
     */
    fun result(samples: List<Float>): Float {
        if (samples.isEmpty()) return 0f
        val settled = samples.drop(samples.size / 4).ifEmpty { samples }.sorted()
        return settled[((settled.size - 1) * 0.9f).toInt()]
    }
}
