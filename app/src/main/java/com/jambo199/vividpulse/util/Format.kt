package com.jambo199.vividpulse.util

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Turning numbers into short, friendly text. */
object Format {
    /** 1536 → "1.5 KB"; decimal units, as phones and stores use. */
    fun bytes(b: Long, decimals: Int = 1): String {
        val v = abs(b.toDouble())
        val (n, unit) = when {
            v >= 1e12 -> v / 1e12 to "TB"
            v >= 1e9 -> v / 1e9 to "GB"
            v >= 1e6 -> v / 1e6 to "MB"
            v >= 1e3 -> v / 1e3 to "KB"
            else -> return "${b} B"
        }
        val sign = if (b < 0) "-" else ""
        return sign + trim(String.format(Locale.US, "%.${decimals}f", n)) + " " + unit
    }

    /** Bytes per second as a network speed: "12.4 Mbps", "830 Kbps". */
    fun bitsPerSec(bytesPerSec: Long): String {
        val bits = bytesPerSec * 8.0
        return when {
            bits >= 1e9 -> trim(String.format(Locale.US, "%.2f", bits / 1e9)) + " Gbps"
            bits >= 1e6 -> trim(String.format(Locale.US, if (bits >= 1e8) "%.0f" else "%.1f", bits / 1e6)) + " Mbps"
            bits >= 1e3 -> String.format(Locale.US, "%.0f", bits / 1e3) + " Kbps"
            else -> String.format(Locale.US, "%.0f", bits) + " bps"
        }
    }

    /** Bytes per second as a transfer rate: "1.2 MB/s". */
    fun rate(bytesPerSec: Long): String = bytes(bytesPerSec) + "/s"

    fun kbps(kbps: Int): String = if (kbps >= 1000) trim(String.format(Locale.US, "%.1f", kbps / 1000f)) + " Mbps" else "$kbps Kbps"

    fun mhz(mhz: Long): String = if (mhz >= 1000) String.format(Locale.US, "%.2f GHz", mhz / 1000f) else "$mhz MHz"

    fun percent(fraction: Float): String = "${(fraction * 100).roundToInt()}%"

    /** 1234567 → "1.2M", 45210 → "45.2K". */
    fun compact(n: Long): String = when {
        n >= 1_000_000_000 -> trim(String.format(Locale.US, "%.1f", n / 1e9)) + "B"
        n >= 1_000_000 -> trim(String.format(Locale.US, if (n >= 10_000_000) "%.1f" else "%.2f", n / 1e6)) + "M"
        n >= 1_000 -> trim(String.format(Locale.US, "%.1f", n / 1e3)) + "K"
        else -> n.toString()
    }

    /** 1234567 → "1,234,567". */
    fun grouped(n: Long): String = String.format(Locale.US, "%,d", n)

    fun temperature(celsius: Float, fahrenheit: Boolean): String =
        if (fahrenheit) String.format(Locale.US, "%.1f°F", celsius * 9 / 5 + 32) else String.format(Locale.US, "%.1f°C", celsius)

    /** 3h 12m, 4d 2h, 45s. */
    fun duration(ms: Long): String {
        val s = ms / 1000
        val d = s / 86_400
        val h = s % 86_400 / 3600
        val m = s % 3600 / 60
        return when {
            d > 0 -> "${d}d ${h}h"
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m ${s % 60}s"
            else -> "${s}s"
        }
    }

    fun watts(w: Float): String = String.format(Locale.US, if (abs(w) >= 10) "%.1f W" else "%.2f W", w)

    private fun trim(s: String): String = if (s.contains('.')) s.trimEnd('0').trimEnd('.') else s
}
