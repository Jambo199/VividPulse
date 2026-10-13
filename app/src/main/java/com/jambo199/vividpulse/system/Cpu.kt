package com.jambo199.vividpulse.system

/** One CPU core as built: its clock range and design. */
data class CoreSpec(val index: Int, val minKHz: Long, val maxKHz: Long, val part: String?, val cluster: Int)

/** Cores that share a design and top speed, e.g. "Prime · 2 × Oryon · 4.32 GHz". */
data class Cluster(
    val index: Int,
    val name: String,
    val cores: List<Int>,
    val part: String?,
    val minKHz: Long,
    val maxKHz: Long,
    val governor: String?,
)

/** One reading of the cores. Loads are 0..1; null where the phone doesn't say. */
data class CpuSample(
    val curKHz: List<Long?>,
    val coreLoad: List<Float?>,
    val load: Float?,
    /** True when [load] is real usage time; false when it's estimated from clock speeds. */
    val measured: Boolean,
) {
    companion object {
        val EMPTY = CpuSample(emptyList(), emptyList(), null, false)
    }
}

/**
 * Reads the CPU cores: their design (from /proc/cpuinfo), clock ranges and live clock speeds.
 *
 * Android doesn't let apps read how busy the CPU is (/proc/stat) any more, so unless the phone
 * allows it, the load is estimated from how fast each core is clocked relative to its range: the
 * kernel raises the clock as work arrives, so it follows real load closely.
 */
class CpuReader {
    val cores: List<CoreSpec>
    val clusters: List<Cluster>
    val hardware: String?

    private var lastStat: Map<String, LongArray>? = null
    private var statWorks = true

    init {
        val count = SysFs.read("/sys/devices/system/cpu/possible")?.let { SysFs.parseRange(it).size }
            ?.takeIf { it > 0 } ?: Runtime.getRuntime().availableProcessors()
        val cpuinfo = SysFs.read("/proc/cpuinfo").orEmpty()
        val parts = CpuParsing.partsByCore(cpuinfo)
        hardware = CpuParsing.hardware(cpuinfo)
        val ranges = (0 until count).map { i ->
            val base = "/sys/devices/system/cpu/cpu$i/cpufreq/"
            val min = SysFs.readLong(base + "cpuinfo_min_freq") ?: SysFs.readLong(base + "scaling_min_freq") ?: 0L
            val max = SysFs.readLong(base + "cpuinfo_max_freq") ?: SysFs.readLong(base + "scaling_max_freq") ?: 0L
            min to max
        }
        val groups = CpuParsing.clusterOf(ranges.map { it.second })
        cores = (0 until count).map { i -> CoreSpec(i, ranges[i].first, ranges[i].second, parts[i], groups[i]) }
        val clusterCount = (groups.maxOrNull() ?: -1) + 1
        val names = CpuParsing.clusterNames(
            (0 until clusterCount).map { c -> cores.count { it.cluster == c } },
        )
        clusters = (0 until clusterCount).map { c ->
            val members = cores.filter { it.cluster == c }
            val first = members.first()
            Cluster(
                index = c,
                name = names[c],
                cores = members.map { it.index },
                part = members.mapNotNull { it.part }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key,
                minKHz = members.minOf { it.minKHz },
                maxKHz = members.maxOf { it.maxKHz },
                governor = SysFs.read("/sys/devices/system/cpu/cpu${first.index}/cpufreq/scaling_governor"),
            )
        }
    }

    val maxKHz: Long get() = cores.maxOfOrNull { it.maxKHz }?.takeIf { it >= MIN_REAL_KHZ } ?: 0L

    /** True when at least one core's clock can be read. */
    val clocksReadable: Boolean by lazy { cores.any { curFreq(it.index) != null } }

    fun sample(): CpuSample {
        val cur = cores.map { if (online(it.index)) curFreq(it.index) else null }
        val stat = readStatUsage()
        val freqLoads = cores.mapIndexed { i, spec -> cur[i]?.let { CpuParsing.loadOf(it, spec.minKHz, spec.maxKHz) } }
        val coreLoad = if (stat != null) cores.map { stat.second["cpu${it.index}"] } else freqLoads
        val load = stat?.first ?: CpuParsing.weightedLoad(cores.map { it.maxKHz }, freqLoads)
        return CpuSample(cur, coreLoad, load, measured = stat != null)
    }

    private fun online(i: Int): Boolean = i == 0 || SysFs.read("/sys/devices/system/cpu/cpu$i/online", remember = false) != "0"

    private fun curFreq(i: Int): Long? =
        (
            SysFs.readLong("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq", remember = false)
                ?: SysFs.readLong("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_cur_freq", remember = false)
            )?.takeIf { it >= MIN_REAL_KHZ && (cores.getOrNull(i)?.maxKHz ?: MIN_REAL_KHZ) >= MIN_REAL_KHZ }

    private companion object {
        /** Below 10 MHz a "clock speed" is a placeholder (emulators, hidden values), not a reading. */
        const val MIN_REAL_KHZ = 10_000L
    }

    /** Real usage from /proc/stat, on the rare phones that still allow it. */
    private fun readStatUsage(): Pair<Float, Map<String, Float>>? {
        if (!statWorks) return null
        val text = SysFs.read("/proc/stat", remember = false)
        if (text == null) {
            statWorks = false
            return null
        }
        val now = CpuParsing.parseStat(text)
        val before = lastStat
        lastStat = now
        if (before == null) return null
        val usage = now.mapNotNull { (name, t) -> before[name]?.let { name to CpuParsing.usageBetween(it, t) } }.toMap()
        val all = usage["cpu"] ?: return null
        return all to usage
    }
}

/** The parsing and arithmetic behind [CpuReader], kept apart so it can be unit tested. */
object CpuParsing {
    /** "CPU part" per core from /proc/cpuinfo, as a design name (e.g. "Cortex-X925", "Oryon"). */
    fun partsByCore(cpuinfo: String): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        var core: Int? = null
        var implementer: String? = null
        for (raw in cpuinfo.lines()) {
            val key = raw.substringBefore(':').trim().lowercase()
            val value = raw.substringAfter(':', "").trim()
            when (key) {
                "processor" -> {
                    core = value.toIntOrNull()
                    implementer = null
                }
                "cpu implementer" -> implementer = value.lowercase()
                "cpu part" -> {
                    val c = core ?: continue
                    partName(implementer, value.lowercase())?.let { result[c] = it }
                }
            }
        }
        return result
    }

    fun hardware(cpuinfo: String): String? = cpuinfo.lines()
        .firstOrNull { it.substringBefore(':').trim().equals("Hardware", ignoreCase = true) }
        ?.substringAfter(':')?.trim()?.ifEmpty { null }

    fun partName(implementer: String?, part: String): String? {
        val p = part.removePrefix("0x").trimStart('0').ifEmpty { "0" }
        return when (implementer?.removePrefix("0x")) {
            "41" -> ARM[p]
            "51" -> QUALCOMM[p]
            "53" -> SAMSUNG[p]
            else -> ARM[p]
        }
    }

    private val ARM = mapOf(
        "d03" to "Cortex-A53", "d04" to "Cortex-A35", "d05" to "Cortex-A55", "d07" to "Cortex-A57",
        "d08" to "Cortex-A72", "d09" to "Cortex-A73", "d0a" to "Cortex-A75", "d0b" to "Cortex-A76",
        "d0d" to "Cortex-A77", "d41" to "Cortex-A78", "d44" to "Cortex-X1", "d46" to "Cortex-A510",
        "d47" to "Cortex-A710", "d48" to "Cortex-X2", "d4d" to "Cortex-A715", "d4e" to "Cortex-X3",
        "d80" to "Cortex-A520", "d81" to "Cortex-A720", "d82" to "Cortex-X4", "d85" to "Cortex-X925",
        "d87" to "Cortex-A725",
    )
    private val QUALCOMM = mapOf(
        "1" to "Oryon", "2" to "Oryon",
        "800" to "Kryo Gold", "801" to "Kryo Silver", "802" to "Kryo Gold", "803" to "Kryo Silver",
        "804" to "Kryo Gold", "805" to "Kryo Silver",
    )
    private val SAMSUNG = mapOf("1" to "Mongoose M1", "2" to "Mongoose M2", "3" to "Mongoose M3", "4" to "Mongoose M4", "5" to "Mongoose M5")

    /** Groups cores by top speed; cluster 0 is the fastest. */
    fun clusterOf(maxKHz: List<Long>): List<Int> {
        val distinct = maxKHz.distinct().sortedDescending()
        return maxKHz.map { distinct.indexOf(it) }
    }

    /** Names for clusters (fastest first) from how many cores each has. */
    fun clusterNames(sizes: List<Int>): List<String> = when (sizes.size) {
        0 -> emptyList()
        1 -> listOf("Cores")
        2 -> if (sizes[0] <= 2) listOf("Prime", "Performance") else listOf("Performance", "Efficiency")
        else -> sizes.indices.map { i ->
            when (i) {
                0 -> "Prime"
                sizes.lastIndex -> "Efficiency"
                else -> if (sizes.size > 3) "Performance ${i}" else "Performance"
            }
        }
    }

    /** Where [cur] sits between [min] and [max], 0..1. */
    fun loadOf(cur: Long, min: Long, max: Long): Float {
        if (max <= 0) return 0f
        if (max <= min) return (cur.toFloat() / max).coerceIn(0f, 1f)
        return ((cur - min).toFloat() / (max - min)).coerceIn(0f, 1f)
    }

    /** Overall load, with faster cores counting for more. Switched-off cores count as idle. */
    fun weightedLoad(maxKHz: List<Long>, loads: List<Float?>): Float? {
        if (loads.all { it == null }) return null
        val total = maxKHz.sum().toFloat()
        if (total <= 0f) return loads.filterNotNull().average().toFloat()
        return (maxKHz.indices.sumOf { (maxKHz[it] * (loads[it] ?: 0f)).toDouble() } / total).toFloat().coerceIn(0f, 1f)
    }

    /** /proc/stat lines ("cpu", "cpu0"…) to their time counters. */
    fun parseStat(text: String): Map<String, LongArray> = text.lines()
        .filter { it.startsWith("cpu") }
        .associate { line ->
            val parts = line.trim().split(Regex("\\s+"))
            parts[0] to parts.drop(1).mapNotNull { it.toLongOrNull() }.toLongArray()
        }

    /** Busy share of the time between two /proc/stat readings. */
    fun usageBetween(before: LongArray, after: LongArray): Float {
        fun idle(t: LongArray) = t.getOrElse(3) { 0 } + t.getOrElse(4) { 0 }
        val total = after.sum() - before.sum()
        if (total <= 0) return 0f
        val idle = idle(after) - idle(before)
        return (1f - idle.toFloat() / total).coerceIn(0f, 1f)
    }
}
