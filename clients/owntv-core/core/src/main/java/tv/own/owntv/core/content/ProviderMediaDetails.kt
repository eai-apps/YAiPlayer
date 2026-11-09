// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.content

import java.util.Locale

data class ProviderMediaDetails(
    val originalTitle: String? = null,
    val genres: String? = null,
    val country: String? = null,
    val director: String? = null,
    val actors: String? = null,
    val year: Int? = null,
    val durationSecs: Int? = null,
) {
    companion object {
        fun from(fields: Map<String, String?>): ProviderMediaDetails {
            fun value(vararg keys: String) = keys.firstNotNullOfOrNull { key ->
                fields[key]?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
            }
            val duration = value("duration_secs", "duration_seconds")?.toIntOrNull()
                ?: value("duration")?.let(::durationSeconds)
                ?: value("time")?.toIntOrNull()?.takeIf { it > 0 && it <= Int.MAX_VALUE / 60 }?.times(60)
            return ProviderMediaDetails(
                originalTitle = value("o_name", "original_title", "original_name"),
                genres = value("genres_str", "genre", "genres"),
                country = value("country"), director = value("director"),
                actors = value("actors", "cast"),
                year = value("year", "releasedate", "release_date")?.take(4)?.toIntOrNull()?.takeIf { it in 1800..3000 },
                durationSecs = duration?.takeIf { it > 0 },
            )
        }

        private fun durationSeconds(raw: String): Int? {
            val parts = raw.split(':').map { it.toLongOrNull()?.takeIf { n -> n in 0..Int.MAX_VALUE.toLong() } ?: return null }
            if (parts.size > 1 && parts.last() >= 60) return null
            if (parts.size == 3 && parts[1] >= 60) return null
            val seconds = when (parts.size) {
                1 -> parts[0]
                2 -> parts[0] * 60 + parts[1]
                3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                else -> return null
            }
            return seconds.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt()
        }
    }
}

fun providerSearchText(vararg fields: Any?): String = fields.filterNotNull()
    .joinToString(" ").lowercase(Locale.ROOT).replace('ё', 'е').trim()
