package com.jambo199.vividpulse.alerts

import android.content.Context
import org.json.JSONArray
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** A battery reading taken in the background, about once an hour. */
data class LevelSample(val at: Long, val level: Float, val charging: Boolean)

/** The last few days of hourly battery readings. */
class BatteryLog(context: Context) {
    private val file = File(context.filesDir, "battery_log.json")

    @Synchronized
    fun add(sample: LevelSample) {
        val keep = read().filter { sample.at - it.at < KEEP_MS } + sample
        runCatching { file.writeText(JSONArray(keep.map { JSONArray().put(it.at).put(it.level.toDouble()).put(it.charging) }).toString()) }
    }

    @Synchronized
    fun samples(): List<LevelSample> = read()

    private fun read(): List<LevelSample> = runCatching {
        if (!file.isFile) return emptyList()
        val a = JSONArray(file.readText())
        (0 until a.length()).map { i ->
            val r = a.getJSONArray(i)
            LevelSample(r.getLong(0), r.getDouble(1).toFloat(), r.getBoolean(2))
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val KEEP_MS = 3 * 24 * 60 * 60_000L
    }
}

/** What the battery did overnight. */
data class NightReport(
    val fromLevel: Int,
    val toLevel: Int,
    val hours: Float,
    /** It was charging at some point in the night. */
    val charged: Boolean,
) {
    val used: Int get() = (fromLevel - toLevel).coerceAtLeast(0)
    val perHour: Float get() = if (hours > 0f) used / hours else 0f

    val title: String
        get() = when {
            charged -> "Charged overnight: now $toLevel%"
            else -> "Overnight: used $used% in ${hours.roundToInt()} h"
        }

    val text: String
        get() = when {
            charged -> "The battery went from $fromLevel% to $toLevel% while you slept."
            perHour < 0.8f -> "From $fromLevel% to $toLevel%. A quiet night: barely anything ran in the background."
            perHour < 2f -> "From $fromLevel% to $toLevel%, about ${"%.1f".format(perHour)}% an hour. About normal."
            else -> "From $fromLevel% to $toLevel%, about ${"%.1f".format(perHour)}% an hour. More than usual: something was busy in the background."
        }
}

object Overnight {
    /** The report is sent once a day, the first check after this hour. */
    const val MORNING_HOUR = 7

    /**
     * The night before [now]: from the reading closest to 23:30 the evening before (within a couple
     * of hours) to the latest reading. Null when there aren't readings to tell.
     */
    fun report(samples: List<LevelSample>, now: Long, zone: ZoneId): NightReport? {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val bedtime = LocalDate.from(today).minusDays(1).atTime(23, 30).atZone(zone).toInstant().toEpochMilli()
        val start = samples.filter { kotlin.math.abs(it.at - bedtime) <= 150 * 60_000L }.minByOrNull { kotlin.math.abs(it.at - bedtime) } ?: return null
        val end = samples.filter { it.at > start.at && it.at <= now }.maxByOrNull { it.at } ?: return null
        val hours = (end.at - start.at) / 3_600_000f
        if (hours < 4f) return null
        val night = samples.filter { it.at in start.at..end.at }
        return NightReport(
            fromLevel = start.level.roundToInt(),
            toLevel = end.level.roundToInt(),
            hours = hours,
            charged = night.any { it.charging } || end.level > start.level + 1f,
        )
    }

    /** Whether it's time to send this morning's report. */
    fun due(now: Long, zone: ZoneId, lastSentDay: String?): Boolean {
        val t = Instant.ofEpochMilli(now).atZone(zone)
        return t.hour in MORNING_HOUR..12 && t.toLocalDate().toString() != lastSentDay
    }
}
