// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.radio

import org.json.JSONArray
import org.json.JSONObject

/**
 * The account sync of the YAiPlay Radio site (worker endpoints under /sync, client syncService.ts), so the TV, the phone
 * and the site share one list of favourites. The rule is the site's: the newer side wins (so removals travel too);
 * the first sign-in on a device merges both sides and loses nothing.
 */
object RadioSync {
    /** The radio site's API (the same address the site's light version calls). */
    const val API = "https://yaiplay-radio-api.yai-media-player.workers.dev"

    enum class Action { PULL, PUSH, MERGE }

    fun decide(mergeFirst: Boolean, serverUpdated: Long, localUpdated: Long): Action = when {
        mergeFirst -> Action.MERGE
        serverUpdated > localUpdated -> Action.PULL
        else -> Action.PUSH
    }

    /** A favourite's identity on the site: its stationuuid (our id falls back to the url when a station has none). */
    fun key(o: JSONObject): String = o.optString("stationuuid").ifBlank { o.optString("url_resolved").ifBlank { o.optString("url") } }

    /** Server first, then local ones the server lacks; each station once. */
    fun mergeFavorites(server: JSONArray, local: JSONArray): JSONArray {
        val seen = HashSet<String>()
        val out = JSONArray()
        for (arr in listOf(server, local)) {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val k = key(o)
                if (k.isNotEmpty() && seen.add(k)) out.put(o)
            }
        }
        return out
    }

    /**
     * The body of `PUT /sync/data`. The site also keeps recognised tracks and playlists there and a PUT replaces
     * the whole record, so what the server had of those goes back untouched.
     */
    fun body(favorites: JSONArray, server: JSONObject): JSONObject = JSONObject()
        .put("favorites", favorites)
        .put("recognizedTracks", server.optJSONArray("recognizedTracks") ?: JSONArray())
        .put("playlists", server.optJSONArray("playlists") ?: JSONArray())

    /** Why a sign-in failed, from the worker's status code (its messages are Russian only, so we use our own). */
    enum class Failure { WRONG_CREDENTIALS, LOGIN_TAKEN, INVALID, TOO_MANY, UNAVAILABLE, NETWORK }

    fun failureFor(status: Int): Failure = when (status) {
        401 -> Failure.WRONG_CREDENTIALS
        409 -> Failure.LOGIN_TAKEN
        400 -> Failure.INVALID
        429 -> Failure.TOO_MANY
        else -> Failure.UNAVAILABLE
    }

    /** The worker's cleanLogin: trimmed, lower case, 3–32 of latin or Russian letters, digits, _ . - */
    fun cleanLogin(login: String): String? = login.trim().lowercase().takeIf { Regex("^[a-z0-9а-яё_.\\-]{3,32}$").matches(it) }

    fun validPassword(password: String): Boolean = password.length >= 6
}
