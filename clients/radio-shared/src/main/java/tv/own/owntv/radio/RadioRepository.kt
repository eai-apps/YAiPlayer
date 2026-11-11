// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.radio

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** Stations from Radio Browser and the radio site's collections; favourites kept on this device. */
class RadioRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("radio_v1", Context.MODE_PRIVATE)
    private val cache = HashMap<String, List<RadioStation>>()
    private val _favorites = MutableStateFlow(loadFavorites())
    val favorites: StateFlow<List<RadioStation>> = _favorites.asStateFlow()

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8_000
        c.readTimeout = 12_000
        c.setRequestProperty("User-Agent", "YAiPlayer-Radio")
        try {
            if (c.responseCode !in 200..299) throw java.io.IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** Tries the mirrors in turn; the first that answers wins. */
    suspend fun search(tag: String?, name: String? = null): List<RadioStation> = withContext(Dispatchers.IO) {
        val path = RadioUrls.searchPath(tag, name)
        cache[path]?.let { return@withContext it }
        var last: Exception? = null
        for (m in RadioUrls.mirrors) {
            try {
                val list = RadioParser.stations(get(m + path))
                cache[path] = list
                return@withContext list
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: java.io.IOException("no mirror")
    }

    suspend fun collections(): List<RadioCollection> = withContext(Dispatchers.IO) {
        runCatching { RadioParser.collections(get(RadioUrls.CATALOG)) }.getOrDefault(emptyList())
    }

    fun isFavorite(id: String) = _favorites.value.any { it.id == id }

    fun toggleFavorite(s: RadioStation) {
        val list = if (isFavorite(s.id)) _favorites.value.filterNot { it.id == s.id } else listOf(s) + _favorites.value
        prefs.edit().putString(KEY_FAV, JSONArray(list.map(RadioParser::toJson)).toString()).apply()
        _favorites.value = list
    }

    private fun loadFavorites(): List<RadioStation> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_FAV, "[]"))
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(RadioParser::station) }
    }.getOrDefault(emptyList())

    companion object {
        private const val KEY_FAV = "favorites"
    }
}
