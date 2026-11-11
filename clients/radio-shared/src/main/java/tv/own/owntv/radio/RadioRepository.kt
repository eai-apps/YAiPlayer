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
import org.json.JSONObject

/**
 * Stations from Radio Browser and the radio site's collections. Favourites are kept on this device and, once the
 * user signs in with a YAiPlay Radio account, synced with the site and their other devices ([RadioSync]).
 */
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
        val raw = rawFavorites()
        val out = JSONArray()
        if (isFavorite(s.id)) {
            for (i in 0 until raw.length()) raw.optJSONObject(i)?.takeIf { RadioSync.key(it) != s.id }?.let(out::put)
        } else {
            out.put(RadioParser.toJson(s))
            for (i in 0 until raw.length()) raw.optJSONObject(i)?.let(out::put)
        }
        saveFavorites(out)
        prefs.edit().putLong(KEY_LOCAL_UPDATED, System.currentTimeMillis()).apply()
    }

    // Raw site objects are kept as they came, so a sync never strips fields the site uses and we do not.
    private fun rawFavorites(): JSONArray = runCatching { JSONArray(prefs.getString(KEY_FAV, "[]")) }.getOrDefault(JSONArray())

    private fun saveFavorites(arr: JSONArray) {
        prefs.edit().putString(KEY_FAV, arr.toString()).apply()
        _favorites.value = parse(arr)
    }

    private fun parse(arr: JSONArray): List<RadioStation> =
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(RadioParser::station) }.distinctBy { it.id }

    private fun loadFavorites(): List<RadioStation> = parse(rawFavorites())

    // ---- Account (the radio site's /sync API) ----

    private val _account = MutableStateFlow(prefs.getString(KEY_LOGIN, null)?.takeIf { prefs.getString(KEY_TOKEN, null) != null })
    /** The signed-in login, or null. */
    val account: StateFlow<String?> = _account.asStateFlow()

    /** When the last sync finished (0 = never). */
    val lastSync: Long get() = prefs.getLong(KEY_LAST_SYNC, 0)

    private class HttpFailure(val status: Int) : java.io.IOException("HTTP $status")

    private fun call(path: String, method: String, body: JSONObject?, token: String?): JSONObject {
        val c = URL(RadioSync.API + path).openConnection() as HttpURLConnection
        c.connectTimeout = 8_000
        c.readTimeout = 15_000
        c.requestMethod = method
        c.setRequestProperty("Accept", "application/json")
        token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
        try {
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            if (code !in 200..299) throw HttpFailure(code)
            val text = c.inputStream.bufferedReader().use { it.readText() }
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        } finally {
            c.disconnect()
        }
    }

    /** Signs in (or creates the account), then merges this device's favourites with the account's. Null = success. */
    suspend fun signIn(login: String, password: String, create: Boolean): RadioSync.Failure? = withContext(Dispatchers.IO) {
        val l = RadioSync.cleanLogin(login) ?: return@withContext RadioSync.Failure.INVALID
        if (!RadioSync.validPassword(password)) return@withContext RadioSync.Failure.INVALID
        val answer = try {
            call(if (create) "/sync/register" else "/sync/login", "POST", JSONObject().put("login", l).put("password", password), null)
        } catch (e: HttpFailure) {
            return@withContext RadioSync.failureFor(e.status)
        } catch (e: Exception) {
            return@withContext RadioSync.Failure.NETWORK
        }
        val token = answer.optString("token").ifBlank { return@withContext RadioSync.Failure.UNAVAILABLE }
        prefs.edit().putString(KEY_TOKEN, token).putString(KEY_LOGIN, answer.optString("login").ifBlank { l }).apply()
        _account.value = prefs.getString(KEY_LOGIN, l)
        syncNow(mergeFirst = true)
        null
    }

    fun signOut() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_LOGIN).remove(KEY_LAST_SYNC).apply()
        _account.value = null
    }

    /** One sync round; false when not signed in or it failed. A rejected token signs the device out. */
    suspend fun syncNow(mergeFirst: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val token = prefs.getString(KEY_TOKEN, null) ?: return@withContext false
        try {
            val server = call("/sync/data", "GET", null, token)
            val local = rawFavorites()
            val localUpdated = prefs.getLong(KEY_LOCAL_UPDATED, 0)
            when (RadioSync.decide(mergeFirst, server.optLong("updatedAt"), localUpdated)) {
                RadioSync.Action.MERGE -> {
                    val merged = RadioSync.mergeFavorites(server.optJSONArray("favorites") ?: JSONArray(), local)
                    saveFavorites(merged)
                    push(merged, server, token)
                }
                RadioSync.Action.PULL -> {
                    saveFavorites(server.optJSONArray("favorites") ?: JSONArray())
                    prefs.edit().putLong(KEY_LOCAL_UPDATED, server.optLong("updatedAt")).apply()
                }
                RadioSync.Action.PUSH -> push(local, server, token)
            }
            prefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply()
            true
        } catch (e: HttpFailure) {
            if (e.status == 401) signOut()
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun push(favorites: JSONArray, server: JSONObject, token: String) {
        val r = call("/sync/data", "PUT", RadioSync.body(favorites, server), token)
        prefs.edit().putLong(KEY_LOCAL_UPDATED, r.optLong("updatedAt", System.currentTimeMillis())).apply()
    }

    companion object {
        private const val KEY_FAV = "favorites"
        private const val KEY_TOKEN = "sync_token"
        private const val KEY_LOGIN = "sync_login"
        private const val KEY_LOCAL_UPDATED = "sync_local_updated"
        private const val KEY_LAST_SYNC = "sync_last"
    }
}
