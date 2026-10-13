package com.jambo199.vividpulse.alerts

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One reading while charging. */
data class ChargePoint(val at: Long, val level: Float, val watts: Float?, val tempC: Float?)

/** One time the phone was charged, from plugging in to unplugging. */
data class ChargeSession(val startedAt: Long, val points: List<ChargePoint>, val limitAlerted: Boolean = false) {
    val startLevel: Float get() = points.firstOrNull()?.level ?: 0f
    val endLevel: Float get() = points.lastOrNull()?.level ?: 0f
    val endedAt: Long get() = points.lastOrNull()?.at ?: startedAt
    val durationMs: Long get() = endedAt - startedAt
    val gained: Float get() = endLevel - startLevel
    val peakWatts: Float get() = points.maxOfOrNull { it.watts ?: 0f } ?: 0f
    val averageWatts: Float? get() = points.mapNotNull { it.watts }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
    val peakTempC: Float? get() = points.mapNotNull { it.tempC }.maxOrNull()
}

/** Pure decisions about a charge, kept apart so they can be tested. */
object ChargeMath {
    /** Readings this close together belong to the same charge. */
    const val CONTINUE_MS = 3 * 60_000L

    /** Same charge: a recent reading, and the battery hasn't run down since (it was unplugged and used). */
    fun continues(last: ChargePoint?, now: Long, level: Float): Boolean =
        last != null && now - last.at in 0..CONTINUE_MS && level >= last.level - 1f

    /** Alert once per charge, when it climbs past the limit (not when plugged in above it already). */
    fun shouldAlert(session: ChargeSession, limit: Int, level: Float): Boolean =
        limit in 1..99 && !session.limitAlerted && level >= limit && session.startLevel < limit

    /** Minutes until [target]%, from how fast the last few minutes went; null when it can't tell. */
    fun minutesTo(target: Int, points: List<ChargePoint>): Int? {
        val last = points.lastOrNull() ?: return null
        if (last.level >= target) return 0
        val from = points.lastOrNull { last.at - it.at >= 4 * 60_000L } ?: points.firstOrNull() ?: return null
        val minutes = (last.at - from.at) / 60_000f
        val rate = (last.level - from.level) / minutes
        if (minutes < 1f || rate <= 0.05f) return null
        return ((target - last.level) / rate).toInt().coerceAtLeast(1)
    }

    /** Keeps a long charge's readings to about [max], dropping every other one from the middle. */
    fun thin(points: List<ChargePoint>, max: Int): List<ChargePoint> {
        if (points.size <= max) return points
        val keepEnds = 20
        val middle = points.subList(keepEnds, points.size - keepEnds).filterIndexed { i, _ -> i % 2 == 0 }
        return points.take(keepEnds) + middle + points.takeLast(keepEnds)
    }
}

/** The last charges, kept on the phone. */
class ChargeHistory(context: Context) {
    private val file = File(context.filesDir, "charges.json")

    @Synchronized
    fun sessions(): List<ChargeSession> = read()

    /** Adds a reading to the charge going on now, or starts a new one. */
    @Synchronized
    fun record(point: ChargePoint): ChargeSession {
        val all = read().toMutableList()
        val current = all.firstOrNull()
        val session = if (current != null && ChargeMath.continues(current.points.lastOrNull(), point.at, point.level)) {
            all.removeAt(0)
            current.copy(points = ChargeMath.thin(current.points + point, MAX_POINTS))
        } else {
            ChargeSession(point.at, listOf(point))
        }
        all.add(0, session)
        write(all.take(MAX_SESSIONS))
        return session
    }

    @Synchronized
    fun markAlerted(startedAt: Long) {
        write(read().map { if (it.startedAt == startedAt) it.copy(limitAlerted = true) else it })
    }

    private fun read(): List<ChargeSession> = runCatching {
        if (!file.isFile) return emptyList()
        val a = JSONArray(file.readText())
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val p = o.getJSONArray("points")
            ChargeSession(
                startedAt = o.getLong("startedAt"),
                points = (0 until p.length()).map { j ->
                    val r = p.getJSONArray(j)
                    ChargePoint(
                        at = r.getLong(0),
                        level = r.getDouble(1).toFloat(),
                        watts = r.optDouble(2, Double.NaN).takeUnless { it.isNaN() }?.toFloat(),
                        tempC = r.optDouble(3, Double.NaN).takeUnless { it.isNaN() }?.toFloat(),
                    )
                },
                limitAlerted = o.optBoolean("limitAlerted"),
            )
        }
    }.getOrDefault(emptyList())

    private fun write(sessions: List<ChargeSession>) {
        runCatching {
            val a = JSONArray(sessions.map { s ->
                JSONObject()
                    .put("startedAt", s.startedAt)
                    .put("limitAlerted", s.limitAlerted)
                    .put("points", JSONArray(s.points.map { p ->
                        JSONArray().put(p.at).put(p.level.toDouble())
                            .put(p.watts?.toDouble() ?: JSONObject.NULL).put(p.tempC?.toDouble() ?: JSONObject.NULL)
                    }))
            })
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(a.toString())
            tmp.renameTo(file)
        }
    }

    private companion object {
        const val MAX_SESSIONS = 30
        const val MAX_POINTS = 300
    }
}
