// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.radio

/**
 * How the radio looks, shared by TV and phone (owner 12.11: "like the site"): each genre's three-colour gradient,
 * taken from the YAiPlay Radio site's genre tiles (Tailwind from/via/to colours written out as ARGB), and a
 * placeholder for a station without a logo — its first letter on a gradient picked from its name.
 */
object RadioLook {
    /** Genre id → three ARGB colours (from, via, to). */
    val genreColors: Map<String, LongArray> = mapOf(
        "popular" to longArrayOf(0xFF06B6D4, 0xFF8B5CF6, 0xFF22D3EE),
        "pop" to longArrayOf(0xFF2563EB, 0xFF0891B2, 0xFF14B8A6),
        "rock" to longArrayOf(0xFF0F172A, 0xFF083344, 0xFF4C1D95),
        "80s" to longArrayOf(0xFF1D4ED8, 0xFF0891B2, 0xFF7C3AED),
        "90s" to longArrayOf(0xFF0F766E, 0xFF7C3AED, 0xFF22D3EE),
        "2000s" to longArrayOf(0xFF312E81, 0xFF581C87, 0xFF831843),
        "70s" to longArrayOf(0xFF78350F, 0xFF7C2D12, 0xFF854D0E),
        "dance" to longArrayOf(0xFF06B6D4, 0xFF2563EB, 0xFF4338CA),
        "house" to longArrayOf(0xFF4C1D95, 0xFF6B21A8, 0xFF0E7490),
        "techno" to longArrayOf(0xFF09090B, 0xFF171717, 0xFF2E1065),
        "dnb" to longArrayOf(0xFF422006, 0xFF78350F, 0xFF450A0A),
        "synthwave" to longArrayOf(0xFF1E1B4B, 0xFF581C87, 0xFF0891B2),
        "electronic" to longArrayOf(0xFF164E63, 0xFF134E4A, 0xFF4C1D95),
        "hiphop" to longArrayOf(0xFF6D28D9, 0xFF115E59, 0xFF164E63),
        "rnb" to longArrayOf(0xFF083344, 0xFF831843, 0xFF581C87),
        "chanson" to longArrayOf(0xFF451A03, 0xFF18181B, 0xFF422006),
        "jazz" to longArrayOf(0xFF1E293B, 0xFF1E3A8A, 0xFF155E75),
        "blues" to longArrayOf(0xFF172554, 0xFF1E1B4B, 0xFF0F172A),
        "classical" to longArrayOf(0xFF082F49, 0xFF172554, 0xFF0F172A),
        "chillout" to longArrayOf(0xFF0D9488, 0xFF7C3AED, 0xFF06B6D4),
        "lofi" to longArrayOf(0xFF2E1065, 0xFF134E4A, 0xFF083344),
        "ambient" to longArrayOf(0xFF020617, 0xFF083344, 0xFF2E1065),
        "metal" to longArrayOf(0xFF09090B, 0xFF450A0A, 0xFF000000),
        "indie" to longArrayOf(0xFF3B0764, 0xFF1E1B4B, 0xFF1E3A8A),
        "latino" to longArrayOf(0xFF9A3412, 0xFFB45309, 0xFFCA8A04),
        "reggae" to longArrayOf(0xFF6D28D9, 0xFFCA8A04, 0xFFB91C1C),
        "soundtracks" to longArrayOf(0xFF0F172A, 0xFF083344, 0xFF1E1B4B),
        "disco" to longArrayOf(0xFFC026D3, 0xFFDB2777, 0xFF06B6D4),
        "trance" to longArrayOf(0xFF6D28D9, 0xFF4F46E5, 0xFF06B6D4),
        "country" to longArrayOf(0xFFB45309, 0xFFC2410C, 0xFFCA8A04),
        "funk" to longArrayOf(0xFFEA580C, 0xFFDC2626, 0xFFDB2777),
        "oldies" to longArrayOf(0xFF854D0E, 0xFFB45309, 0xFF44403C),
        "folk" to longArrayOf(0xFF5B21B6, 0xFF15803D, 0xFF0E7490),
        "kpop" to longArrayOf(0xFFEC4899, 0xFF06B6D4, 0xFF9333EA),
        "kids" to longArrayOf(0xFF0EA5E9, 0xFF22D3EE, 0xFF22D3EE),
        "sport" to longArrayOf(0xFF15803D, 0xFF6D28D9, 0xFF115E59),
        "christmas" to longArrayOf(0xFFB91C1C, 0xFF0E7490, 0xFF5B21B6),
        "comedy" to longArrayOf(0xFFA16207, 0xFFD97706, 0xFFC2410C),
        "news" to longArrayOf(0xFF1E3A8A, 0xFF1E293B, 0xFF18181B),
    )

    private val fallback = longArrayOf(0xFF0E7490, 0xFF6D28D9, 0xFF0891B2)

    fun colorsOf(genreId: String): LongArray = genreColors[genreId] ?: fallback

    /** Gradients for station placeholders: the genre palette, one picked steadily by the station's name. */
    fun placeholderColors(name: String): LongArray {
        val all = genreColors.values.toList()
        return all[Math.floorMod(name.trim().lowercase().hashCode(), all.size)]
    }

    /** The first letter or digit of the name, upper case; "♪" when there is none. */
    fun initial(name: String): String = name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "\u266A"

    /** What the genre tile shows as its glyph (as on the site); each app draws it with its own icon set. */
    enum class Glyph { TOP, SPARKLE, GUITAR, RETRO, BEAT, MIC, NOTE, CALM, SUN, FILM, HEART, SPORT, TALK, NEWS }

    fun glyphOf(genreId: String): Glyph = when (genreId) {
        "popular", "kpop" -> Glyph.TOP
        "pop", "indie", "christmas" -> Glyph.SPARKLE
        "rock", "metal", "country", "folk", "blues" -> Glyph.GUITAR
        "80s", "90s", "2000s", "70s", "oldies", "synthwave" -> Glyph.RETRO
        "dance", "house", "techno", "dnb", "electronic", "trance", "disco", "funk" -> Glyph.BEAT
        "hiphop", "rnb" -> Glyph.MIC
        "chillout", "lofi", "ambient" -> Glyph.CALM
        "latino", "reggae" -> Glyph.SUN
        "soundtracks" -> Glyph.FILM
        "kids" -> Glyph.HEART
        "sport" -> Glyph.SPORT
        "comedy" -> Glyph.TALK
        "news" -> Glyph.NEWS
        else -> Glyph.NOTE
    }

    /** The flag emoji of an ISO 3166 code («DE» → 🇩🇪); null for anything else. */
    fun flag(countryCode: String?): String? {
        val c = countryCode?.trim()?.uppercase() ?: return null
        if (c.length != 2 || !c.all { it in 'A'..'Z' }) return null
        return String(Character.toChars(0x1F1E6 + (c[0] - 'A'))) + String(Character.toChars(0x1F1E6 + (c[1] - 'A')))
    }
}
