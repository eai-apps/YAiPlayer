// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
// Same data as eai-apps/YAiPlay-Radio (src/services/radioApi.ts, src/constants/genres.ts).
package tv.own.owntv.radio

import org.json.JSONArray
import org.json.JSONObject

data class RadioStation(
    val id: String,
    val name: String,
    val url: String,
    val homepage: String?,
    val favicon: String?,
    val tags: List<String>,
    val country: String?,
    val codec: String?,
    val bitrate: Int,
    val clicks: Int,
    /** ISO 3166 code («DE»), for the flag beside the country (owner 12.11: «страна с флагом», as on the site). */
    val countryCode: String? = null,
)

/** A genre of the sample's list: [tag] is what Radio Browser searches by; empty = the world top. */
data class RadioGenre(val id: String, val tag: String)

/** A hand-picked collection from the radio site's catalogue («Доброе утро», …). */
data class RadioCollection(val id: String, val name: String, val emoji: String?, val note: String?, val stations: List<RadioStation>)

object RadioGenres {
    val all: List<RadioGenre> = listOf(
        "popular" to "", "pop" to "pop", "rock" to "rock", "80s" to "80s",
        "90s" to "90s", "2000s" to "2000s", "70s" to "70s", "dance" to "dance", "house" to "house", "techno" to "techno",
        "dnb" to "drum and bass", "synthwave" to "synthwave", "electronic" to "electronic", "hiphop" to "hiphop",
        "rnb" to "rnb", "chanson" to "chanson", "jazz" to "jazz", "blues" to "blues", "classical" to "classical",
        "chillout" to "lounge", "lofi" to "lofi", "ambient" to "ambient", "metal" to "metal", "indie" to "indie",
        "latino" to "latino", "reggae" to "reggae", "soundtracks" to "soundtrack", "disco" to "disco", "trance" to "trance",
        "country" to "country", "funk" to "funk", "oldies" to "oldies", "folk" to "folk", "kpop" to "kpop",
        "kids" to "children", "sport" to "sport", "christmas" to "christmas", "comedy" to "comedy", "news" to "news",
    ).map { (id, tag) -> RadioGenre(id, tag) }
}

object RadioParser {
    /** Radio Browser `/json/stations/search`: HTTP(S) streams only, duplicates by name folded to the best bitrate. */
    fun stations(json: String, limit: Int = 120): List<RadioStation> {
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        val list = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::station) }
        return dedupe(list).take(limit)
    }

    fun station(o: JSONObject): RadioStation? {
        val url = o.optString("url_resolved").ifBlank { o.optString("url") }.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        val name = o.optString("name").trim().ifEmpty { return null }
        return RadioStation(
            id = o.optString("stationuuid").ifBlank { url },
            name = name,
            url = url,
            homepage = o.optString("homepage").takeIf { it.startsWith("http") },
            favicon = o.optString("favicon").takeIf { it.startsWith("http") },
            tags = o.optString("tags").split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(6),
            country = o.optString("country").takeIf { it.isNotBlank() },
            codec = o.optString("codec").takeIf { it.isNotBlank() },
            bitrate = o.optInt("bitrate", 0),
            clicks = o.optInt("clickcount", 0),
            countryCode = o.optString("countrycode").trim().uppercase().takeIf { it.length == 2 && it.all { c -> c in 'A'..'Z' } },
        )
    }

    /** One entry per station name: the higher bitrate, then the more popular one (as the sample does). */
    fun dedupe(list: List<RadioStation>): List<RadioStation> {
        val map = LinkedHashMap<String, RadioStation>()
        for (s in list) {
            val key = s.name.lowercase().replace(Regex("\\s+"), " ")
            val existing = map[key]
            if (existing == null || s.bitrate > existing.bitrate || (s.bitrate == existing.bitrate && s.clicks > existing.clicks)) {
                map[key] = s
            }
        }
        return map.values.toList()
    }

    /** `curated` of the site's catalog.json. */
    fun collections(catalogJson: String): List<RadioCollection> {
        val root = runCatching { JSONObject(catalogJson) }.getOrNull() ?: return emptyList()
        val arr = root.optJSONArray("curated") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val c = arr.optJSONObject(i) ?: return@mapNotNull null
            val st = c.optJSONArray("stations") ?: JSONArray()
            val stations = (0 until st.length()).mapNotNull { st.optJSONObject(it)?.let(::station) }
            if (stations.isEmpty()) null
            else RadioCollection(c.optString("id"), c.optString("name"), c.optString("emoji").ifBlank { null }, c.optString("note").ifBlank { null }, stations)
        }
    }

    fun toJson(s: RadioStation): JSONObject = JSONObject()
        .put("stationuuid", s.id).put("name", s.name).put("url_resolved", s.url).put("homepage", s.homepage ?: "")
        .put("favicon", s.favicon ?: "").put("tags", s.tags.joinToString(",")).put("country", s.country ?: "")
        .put("codec", s.codec ?: "").put("bitrate", s.bitrate).put("clickcount", s.clicks).put("countrycode", s.countryCode ?: "")
}

object RadioUrls {
    /** Radio Browser mirrors, tried in this order (the sample's list). */
    val mirrors = listOf(
        "https://de1.api.radio-browser.info",
        "https://de2.api.radio-browser.info",
        "https://fi1.api.radio-browser.info",
        "https://nl1.api.radio-browser.info",
        "https://at1.api.radio-browser.info",
    )
    const val CATALOG = "https://yaiplay-radio.pages.dev/catalog.json"

    /** Same query as the sample's fetchStations: working stations, most listened first, a margin for duplicates. */
    fun searchPath(tag: String?, name: String?, limit: Int = 120, countryCode: String? = null): String {
        val q = linkedMapOf(
            "hidebroken" to "true",
            "order" to "clickcount",
            "reverse" to "true",
            "limit" to minOf(Math.round(limit * 1.6f), 400).toString(),
        )
        tag?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { q["tag"] = it }
        name?.trim()?.takeIf { it.isNotEmpty() }?.let { q["name"] = it }
        countryCode?.trim()?.uppercase()?.takeIf { it.length == 2 }?.let { q["countrycode"] = it }
        return "/json/stations/search?" + q.entries.joinToString("&") { (k, v) -> k + "=" + java.net.URLEncoder.encode(v, "UTF-8") }
    }
}
