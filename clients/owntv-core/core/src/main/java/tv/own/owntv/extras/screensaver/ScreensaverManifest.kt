// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.screensaver

import org.json.JSONObject

object ScreensaverManifest {

    val FALLBACK_ITEMS: List<ScreensaverItem> = listOf(
        ScreensaverItem(
            id = "nasa_carina",
            title = "Carina Nebula Cosmic Cliffs",
            subtitle = "NASA / ESA / CSA / STScI",
            imageUrl = "https://images-assets.nasa.gov/image/PIA15415/PIA15415~orig.jpg",
            license = "Public Domain",
            source = "NASA",
        ),
        ScreensaverItem(
            id = "nasa_pillars",
            title = "Pillars of Creation",
            subtitle = "NASA / ESA / Hubble Heritage",
            imageUrl = "https://images-assets.nasa.gov/image/PIA18224/PIA18224~orig.jpg",
            license = "Public Domain",
            source = "NASA",
        ),
        ScreensaverItem(
            id = "nasa_jupiter",
            title = "Jupiter in True Color",
            subtitle = "NASA / JPL-Caltech / SwRI / MSSS",
            imageUrl = "https://images-assets.nasa.gov/image/PIA21985/PIA21985~orig.jpg",
            license = "Public Domain",
            source = "NASA",
        ),
        ScreensaverItem(
            id = "nature_aurora",
            title = "Aurora Borealis over Tromsø",
            subtitle = "Wikimedia Commons",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/a/aa/Northern_Lights_Troms%C3%B8_Norway.jpg",
            license = "CC0 / Public Domain",
            source = "Wikimedia Commons",
        ),
        ScreensaverItem(
            id = "nature_mountains",
            title = "Matterhorn at Sunrise",
            subtitle = "Wikimedia Commons",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/6/66/Matterhorn_sunrise_2019.jpg",
            license = "CC0 / Public Domain",
            source = "Wikimedia Commons",
        ),
    )

    fun parseManifestJson(jsonString: String): List<ScreensaverItem> {
        val root = runCatching { JSONObject(jsonString) }.getOrNull() ?: return FALLBACK_ITEMS
        val array = root.optJSONArray("items") ?: return FALLBACK_ITEMS
        val list = mutableListOf<ScreensaverItem>()

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("id").ifBlank { "item_$i" }
            val title = obj.optString("title").ifBlank { "Nature" }
            val subtitle = obj.optString("subtitle").takeIf { it.isNotBlank() }
            val url = obj.optString("url")
            val license = obj.optString("license")
            val source = obj.optString("source")

            if (url.isNotBlank()) {
                list.add(
                    ScreensaverItem(
                        id = id,
                        title = title,
                        subtitle = subtitle,
                        imageUrl = url,
                        license = license,
                        source = source,
                    )
                )
            }
        }

        return if (list.isNotEmpty()) list else FALLBACK_ITEMS
    }
}
