package com.jambo199.vividpulse.games

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Games you've starred, so their live player counts sit at the top of the Games page. */
class WatchStore(context: Context) {
    private val prefs = context.getSharedPreferences("watching", Context.MODE_PRIVATE)
    private val _games = MutableStateFlow(read())
    val games: StateFlow<List<Game>> = _games.asStateFlow()

    fun isWatched(appId: Int): Boolean = _games.value.any { it.appId == appId }

    fun toggle(game: Game) {
        val now = _games.value
        _games.value = if (now.any { it.appId == game.appId }) now.filter { it.appId != game.appId } else listOf(game) + now
        save()
    }

    private fun save() {
        val a = JSONArray(_games.value.map { JSONObject().put("appId", it.appId).put("name", it.name).put("imageUrl", it.imageUrl) })
        prefs.edit().putString("games", a.toString()).apply()
    }

    private fun read(): List<Game> = runCatching {
        val a = JSONArray(prefs.getString("games", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Game(appId = o.getInt("appId"), name = o.getString("name"), imageUrl = o.getString("imageUrl"), rank = i + 1)
        }
    }.getOrDefault(emptyList())
}
