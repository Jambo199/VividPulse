package com.jambo199.vividpulse.update

import android.content.Context
import org.json.JSONObject

/** How big an update was. */
enum class UpdateSize(val label: String) {
    MAJOR("Major update"),
    MINOR("Minor update"),
    SMALL("Small update"),
    ;

    companion object {
        fun of(name: String?): UpdateSize = entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: SMALL
    }
}

/** One version in the changelog. */
data class ChangelogEntry(
    val code: Int,
    val version: String,
    /** "2026-10-13" */
    val date: String?,
    val title: String,
    val size: UpdateSize,
    val changes: List<String>,
)

/**
 * Every version of the app and what it changed. Each build carries the list up to itself (made
 * from the project's history when it's built); newer ones come from the release page.
 */
object Changelog {
    const val FILE = "changelog.json"

    fun parse(json: String): List<ChangelogEntry> {
        val list = JSONObject(json).optJSONArray("entries") ?: return emptyList()
        return (0 until list.length()).mapNotNull { i ->
            val o = list.optJSONObject(i) ?: return@mapNotNull null
            val code = o.optInt("code", -1).takeIf { it > 0 } ?: return@mapNotNull null
            val changes = o.optJSONArray("changes")
            ChangelogEntry(
                code = code,
                version = o.optString("version").ifBlank { "1.0.$code" },
                date = o.optString("date").ifBlank { null },
                title = o.optString("title"),
                size = UpdateSize.of(o.optString("size")),
                changes = changes?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }.orEmpty(),
            )
        }.sortedByDescending { it.code }
    }

    /** The list that came with this build (empty for builds made without it). */
    fun bundled(context: Context): List<ChangelogEntry> =
        runCatching { context.assets.open(FILE).bufferedReader().use { parse(it.readText()) } }.getOrDefault(emptyList())

    /** Both lists together, newest first; where both have a version, [newer]'s entry wins. */
    fun merge(older: List<ChangelogEntry>, newer: List<ChangelogEntry>): List<ChangelogEntry> =
        (older + newer).associateBy { it.code }.values.sortedByDescending { it.code }

    /** How many of each size, biggest first. */
    fun counts(entries: List<ChangelogEntry>): Map<UpdateSize, Int> =
        UpdateSize.entries.associateWith { s -> entries.count { it.size == s } }
}
