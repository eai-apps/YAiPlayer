// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.radio

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

/**
 * The cover of the song on the air, like the YAiPlay Radio site: the station's ICY title («Artist - Song») is
 * looked up in the iTunes Search API, and a cover is taken only when the artist matches — otherwise the
 * station's logo stays (a wrong cover is worse than none).
 */
object RadioCover {
    private val cache = HashMap<String, String>()

    private fun norm(s: String) = s.lowercase().replace('ё', 'е')
        .replace(Regex("\\b(feat|ft|featuring)\\b.*$"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    /** «Double Key - We Love To Love - 1990» → artist, title; null when the title has no «Artist - Song» form. */
    fun split(raw: String): Pair<String, String>? {
        val cleaned = raw.replace(Regex("\\s*[-–—]\\s*(19|20)\\d{2}\\s*$"), "")
            .replace(Regex("\\s*[\\[(](19|20)\\d{2}[\\])]\\s*$"), "").trim()
        val parts = cleaned.split(Regex("\\s+[-–—]\\s+"))
        if (parts.size < 2) return null
        val artist = parts[0].trim()
        val title = parts.drop(1).joinToString(" - ").trim()
        return if (artist.isNotEmpty() && title.isNotEmpty()) artist to title else null
    }

    /** Cover URL (600 px) or null. Blocking: call off the main thread. */
    fun fetch(raw: String): String? {
        val (artist, title) = split(raw) ?: return null
        val key = "${norm(artist)}|${norm(title)}"
        synchronized(cache) { cache[key]?.let { return it.ifEmpty { null } } }
        return try {
            val term = "$artist ${title.replace(Regex("[\\[(].*?[\\])]"), "")}"
            val url = URL("https://itunes.apple.com/search?term=${URLEncoder.encode(term, "UTF-8")}&media=music&entity=song&limit=10")
            val conn = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 5000; readTimeout = 5000 }
            val body = try { conn.inputStream.bufferedReader().use { it.readText() } } finally { conn.disconnect() }
            val results = JSONObject(body).optJSONArray("results")
            val want = norm(artist)
            var art = ""
            if (results != null) for (i in 0 until results.length()) {
                val r = results.optJSONObject(i) ?: continue
                val a = norm(r.optString("artistName"))
                if (a.isNotEmpty() && (a.contains(want) || want.contains(a))) {
                    art = r.optString("artworkUrl100").replace("100x100bb", "600x600bb")
                    break
                }
            }
            synchronized(cache) { cache[key] = art }
            art.ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }
}
