package com.jambo199.vividpulse.system

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Reading the small text files the kernel exposes, and system properties. */
object SysFs {
    /** Paths that couldn't be read once: the phone blocks them, so don't keep trying. */
    private val blocked = ConcurrentHashMap.newKeySet<String>()

    /**
     * The file's text, or null. With [remember] a file that can't be read is skipped from then on;
     * leave it off for files that come and go (a CPU core's clock while the core is switched off).
     */
    fun read(path: String, remember: Boolean = true): String? {
        if (path in blocked) return null
        return try {
            File(path).readText().trim().ifEmpty { null }
        } catch (ignored: Exception) {
            if (remember) blocked += path
            null
        }
    }

    fun readLong(path: String, remember: Boolean = true): Long? = read(path, remember)?.trim()?.toLongOrNull()

    fun lines(path: String): List<String>? = read(path)?.lines()

    fun canRead(path: String): Boolean = read(path) != null

    private val props = ConcurrentHashMap<String, String>()

    /** A system property, e.g. `ro.vivo.os.version`; "" when it isn't set or can't be read. */
    fun prop(key: String): String = props.getOrPut(key) { readProp(key) }

    private fun readProp(key: String): String {
        try {
            val c = Class.forName("android.os.SystemProperties")
            val value = c.getMethod("get", String::class.java).invoke(null, key) as? String
            if (!value.isNullOrBlank()) return value.trim()
        } catch (ignored: Throwable) {
            // Not reachable on this phone; ask the getprop tool instead.
        }
        return try {
            val p = ProcessBuilder("getprop", key).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().use { it.readText() }.trim()
            p.waitFor(2, TimeUnit.SECONDS)
            out
        } catch (ignored: Exception) {
            ""
        }
    }

    /** "0-7" or "0-3,4-7" (as in /sys/devices/system/cpu/possible) to the CPU numbers. */
    fun parseRange(text: String): List<Int> = text.trim().split(',').flatMap { part ->
        val bounds = part.trim().split('-')
        val a = bounds.getOrNull(0)?.trim()?.toIntOrNull() ?: return@flatMap emptyList()
        val b = bounds.getOrNull(1)?.trim()?.toIntOrNull() ?: a
        (a..b).toList()
    }
}
