package com.jambo199.vividpulse.system

import android.content.Context
import android.os.Build
import android.os.PowerManager
import java.io.File

/**
 * GPU load and clock, on phones that let apps read them: Adreno (Snapdragon), Mali (Exynos,
 * Tensor, MediaTek Dimensity) and MediaTek's own GPU counters. Many phones keep them private.
 */
data class GpuSample(val load: Float?, val clockMHz: Int?) {
    companion object {
        val EMPTY = GpuSample(null, null)
    }
}

class GpuReader {
    private val loadPath = LOAD_PATHS.firstOrNull { SysFs.canRead(it) }
    private val clockPath = CLOCK_PATHS.firstOrNull { SysFs.canRead(it) }
    val maxClockMHz: Int? = MAX_CLOCK_PATHS.firstNotNullOfOrNull { SysFs.readLong(it) }?.let(::toMHz)

    val available: Boolean get() = loadPath != null || clockPath != null

    fun sample(): GpuSample {
        val load = loadPath?.let { path -> SysFs.read(path, remember = false)?.let { parseLoad(it, percentFirst = PERCENT_FIRST.any { k -> k in path }) } }
        val clock = clockPath?.let { SysFs.readLong(it, remember = false) }?.let(::toMHz)
        return GpuSample(load, clock)
    }

    private fun parseLoad(text: String, percentFirst: Boolean): Float? {
        // "37 %" (gpu_busy_percentage), "123 456" busy/total (gpubusy), or a plain number.
        // MediaTek's counters start with the load and add more numbers after it.
        val numbers = text.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.mapNotNull { it.toLongOrNull() }
        return when {
            numbers.size >= 2 && !percentFirst && text.contains(' ') && !text.contains('%') ->
                if (numbers[1] > 0) (numbers[0].toFloat() / numbers[1]).coerceIn(0f, 1f) else 0f
            numbers.isNotEmpty() -> (numbers[0] / 100f).coerceIn(0f, 1f)
            else -> null
        }
    }

    private fun toMHz(raw: Long): Int = when {
        raw > 10_000_000 -> (raw / 1_000_000).toInt() // Hz
        raw > 10_000 -> (raw / 1000).toInt() // kHz
        else -> raw.toInt() // MHz
    }

    private companion object {
        val LOAD_PATHS = listOf(
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
            "/sys/class/kgsl/kgsl-3d0/gpubusy",
            "/sys/kernel/gpu/gpu_busy",
            "/sys/devices/platform/gpu/utilization",
            "/sys/class/misc/mali0/device/utilization",
            "/sys/kernel/ged/hal/gpu_utilization",
            "/sys/module/ged/parameters/gpu_loading",
        )
        val PERCENT_FIRST = listOf("/ged/", "mali")
        val CLOCK_PATHS = listOf(
            "/sys/class/kgsl/kgsl-3d0/gpuclk",
            "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
            "/sys/kernel/gpu/gpu_clock",
            "/sys/class/misc/mali0/device/cur_freq",
            "/sys/class/misc/mali0/device/devfreq/cur_freq",
        )
        val MAX_CLOCK_PATHS = listOf(
            "/sys/class/kgsl/kgsl-3d0/max_gpuclk",
            "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq",
            "/sys/kernel/gpu/gpu_max_clock",
            "/sys/class/misc/mali0/device/max_freq",
            "/sys/class/misc/mali0/device/devfreq/max_freq",
        )
    }
}

/** How hot the phone is running. */
data class ThermalSample(
    /** PowerManager.THERMAL_STATUS_*; -1 when unknown. */
    val status: Int,
    /**
     * How close the phone is to slowing itself down to cool off: 0 is cool, 1 is where it
     * starts throttling. Null on phones that don't forecast it.
     */
    val headroom: Float?,
    /** Readable temperature sensors, °C. */
    val zones: List<Pair<String, Float>>,
) {
    val statusLabel: String
        get() = when (status) {
            0 -> "Cool"
            1 -> "Warm"
            2 -> "Hot"
            3 -> "Very hot"
            4 -> "Critical"
            5, 6 -> "Emergency"
            else -> "Unknown"
        }

    companion object {
        val EMPTY = ThermalSample(-1, null, emptyList())
    }
}

class ThermalReader(context: Context) {
    private val pm = context.getSystemService(PowerManager::class.java)
    private var lastHeadroom: Float? = null
    private var lastHeadroomAt = 0L
    private val zones: List<Pair<String, String>> = findZones()

    val zonesReadable: Boolean get() = zones.isNotEmpty()

    fun sample(): ThermalSample {
        val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) pm?.currentThermalStatus ?: -1 else -1
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android answers at most about once a second; asking more often returns NaN.
            val now = System.currentTimeMillis()
            if (now - lastHeadroomAt >= 2_000) {
                lastHeadroomAt = now
                val h = pm?.getThermalHeadroom(10) ?: Float.NaN
                if (!h.isNaN()) lastHeadroom = h.coerceIn(0f, 1.5f)
            }
        }
        val temps = zones.mapNotNull { (name, path) ->
            SysFs.readLong(path, remember = false)?.let { raw -> name to ThermalMath.celsius(raw) }
        }.filter { it.second in -30f..150f }
        return ThermalSample(status, lastHeadroom, temps)
    }

    private fun findZones(): List<Pair<String, String>> {
        val dirs = File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") } ?: return emptyList()
        return dirs.sortedBy { it.name.removePrefix("thermal_zone").toIntOrNull() ?: 0 }.mapNotNull { dir ->
            val type = SysFs.read("${dir.path}/type") ?: return@mapNotNull null
            val path = "${dir.path}/temp"
            if (SysFs.readLong(path) == null) return@mapNotNull null
            ThermalMath.friendlyZone(type)?.let { it to path }
        }.distinctBy { it.first }.take(12)
    }
}

object ThermalMath {
    /** Thermal zones report millidegrees (most) or degrees. */
    fun celsius(raw: Long): Float = if (raw > 1000 || raw < -1000) raw / 1000f else raw.toFloat()

    /** A readable name for the zones worth showing; null to skip the rest. */
    fun friendlyZone(type: String): String? {
        val t = type.lowercase()
        return when {
            t.contains("battery") || t == "bms" -> "Battery"
            t.contains("skin") || t.contains("xo-therm") || t.contains("quiet") -> "Body"
            t.contains("gpu") -> "GPU" + zoneSuffix(t)
            t.contains("cpu") || t.contains("apc") || t.contains("cpuss") -> "CPU" + zoneSuffix(t)
            t.contains("soc") || t.contains("ap_ntc") -> "Chip"
            t.contains("modem") || t.contains("mdm") -> "Modem"
            t.contains("camera") || t.contains("cam") -> "Camera"
            t.contains("charger") || t.contains("chg") -> "Charger"
            t.contains("ddr") -> "Memory"
            t.contains("pa") && t.contains("therm") -> "Radio"
            else -> null
        }
    }

    private fun zoneSuffix(type: String): String =
        Regex("(\\d+)").findAll(type).lastOrNull()?.value?.let { " $it" } ?: ""
}
