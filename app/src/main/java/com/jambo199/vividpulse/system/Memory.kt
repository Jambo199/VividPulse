package com.jambo199.vividpulse.system

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.util.Locale

/** RAM right now, in bytes. */
data class MemorySample(
    val total: Long,
    val available: Long,
    val cached: Long,
    val buffers: Long,
    val free: Long,
    /** Swap: compressed memory (zram), plus the extra RAM on storage some phones add ([MemInfo.extraRamName]). */
    val swapTotal: Long,
    val swapFree: Long,
    val lowMemory: Boolean,
    val threshold: Long,
) {
    val used: Long get() = (total - available).coerceAtLeast(0)
    val usedFraction: Float get() = if (total > 0) used.toFloat() / total else 0f
    val swapUsed: Long get() = (swapTotal - swapFree).coerceAtLeast(0)

    companion object {
        val EMPTY = MemorySample(0, 0, 0, 0, 0, 0, 0, false, 0)
    }
}

class MemoryReader(context: Context) {
    private val am = context.getSystemService(ActivityManager::class.java)
    private val info = ActivityManager.MemoryInfo()

    fun sample(): MemorySample {
        am.getMemoryInfo(info)
        val m = SysFs.read("/proc/meminfo", remember = false)?.let(MemInfo::parse).orEmpty()
        fun kb(key: String) = (m[key] ?: 0L) * 1024
        return MemorySample(
            total = info.totalMem.takeIf { it > 0 } ?: kb("MemTotal"),
            available = info.availMem.takeIf { it > 0 } ?: kb("MemAvailable"),
            cached = kb("Cached"),
            buffers = kb("Buffers"),
            free = kb("MemFree"),
            swapTotal = kb("SwapTotal"),
            swapFree = kb("SwapFree"),
            lowMemory = info.lowMemory,
            threshold = info.threshold,
        )
    }
}

object MemInfo {
    /** /proc/meminfo to kB values by name. */
    fun parse(text: String): Map<String, Long> = text.lines().mapNotNull { line ->
        val name = line.substringBefore(':', "").trim()
        val value = line.substringAfter(':', "").trim().split(' ').firstOrNull()?.toLongOrNull()
        if (name.isEmpty() || value == null) null else name to value
    }.toMap()

    /** The size the phone is sold with: 15.3 GB usable reads as 16 GB. */
    fun marketedRamGb(totalBytes: Long): Int {
        val gb = totalBytes / 1e9
        return RAM_SIZES.firstOrNull { it >= gb } ?: kotlin.math.ceil(gb).toInt()
    }

    /** 476 GB usable reads as 512 GB. */
    fun marketedStorageGb(totalBytes: Long): Int {
        val gb = totalBytes / 1e9
        return STORAGE_SIZES.firstOrNull { it >= gb * 0.98 } ?: kotlin.math.ceil(gb).toInt()
    }

    /** What this phone's maker calls the memory it adds on top of RAM: "RAM Plus" on Samsung, "RAM+" on vivo. */
    val extraRamName: String by lazy { extraRamName(Build.MANUFACTURER.orEmpty(), Build.BRAND.orEmpty()) }

    fun extraRamName(maker: String, brand: String): String {
        val m = "$maker $brand".lowercase(Locale.US)
        return when {
            "vivo" in m || "iqoo" in m -> "RAM+"
            "samsung" in m -> "RAM Plus"
            "xiaomi" in m || "redmi" in m || "poco" in m -> "Memory extension"
            "oppo" in m || "realme" in m || "oneplus" in m -> "RAM expansion"
            "honor" in m -> "RAM Turbo"
            "motorola" in m -> "RAM Boost"
            else -> "Virtual RAM"
        }
    }

    private val RAM_SIZES = listOf(1, 2, 3, 4, 6, 8, 10, 12, 16, 18, 20, 24, 32, 48, 64)
    private val STORAGE_SIZES = listOf(8, 16, 32, 64, 128, 256, 512, 1024, 2048)
}
