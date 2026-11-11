// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.player

import java.util.Locale
import tv.own.owntv.core.player.TrackLanguages

data class TrackLocalizedStrings(
    val dubbing: String,
    val original: String,
    val forced: String,
    val full: String,
    val multiVoice: String? = null,
    val twoVoice: String? = null,
    val singleVoice: String? = null,
    val commentary: String? = null,
    val sdh: String? = null,
    val fallbackNumber: (Int) -> String,
)

object TrackFormatter {

    fun format(
        track: TrackOption,
        displayLocale: Locale,
        strings: TrackLocalizedStrings,
    ): String {
        val raw = track.label.trim()

        // External subs keep their raw prefix if they come from OpenSubtitles/local file
        if (raw.startsWith("OS_") || raw.startsWith("LOCAL_")) {
            val language = resolveLanguage(track.lang, raw, displayLocale)
            return if (language != null) "$language ($raw)" else raw
        }

        val language = resolveLanguage(track.lang, raw, displayLocale)
        val type = resolveType(track, raw, strings)
        val customLabel = extractCustomLabel(raw, language)

        return when {
            language != null && type != null -> "$language ($type)"
            language != null && customLabel != null -> "$language ($customLabel)"
            language != null -> language
            type != null -> type.replaceFirstChar { it.titlecase(displayLocale) }
            customLabel != null -> customLabel
            else -> strings.fallbackNumber(track.displayNumber())
        }
    }

    private fun TrackOption.displayNumber(): Int =
        (typeIndex.takeIf { it >= 0 } ?: mpvId) + 1

    internal fun resolveLanguage(
        lang: String?,
        rawLabel: String,
        displayLocale: Locale,
    ): String? {
        val code = lang?.trim()?.lowercase(Locale.ROOT)?.takeIf {
            it.isNotBlank() && it != "und" && it != "unknown" && it != "null"
        }
        if (code != null) {
            val name = TrackLanguages.displayName(code, displayLocale)
            if (name.isNotBlank() && !name.equals(code, ignoreCase = true)) {
                return name
            }
            return code.uppercase(displayLocale)
        }
        val fromLabel = detectLanguageCodeFromLabel(rawLabel)
        if (fromLabel != null) {
            val name = TrackLanguages.displayName(fromLabel, displayLocale)
            if (name.isNotBlank() && !name.equals(fromLabel, ignoreCase = true)) {
                return name
            }
            return fromLabel.uppercase(displayLocale)
        }
        return null
    }

    private fun detectLanguageCodeFromLabel(label: String): String? {
        val l = label.lowercase(Locale.ROOT)
        return when {
            Regex("""\b(rus|ru|russian|рус|русск\w*)\b""", RegexOption.IGNORE_CASE).containsMatchIn(l) -> "rus"
            Regex("""\b(eng|en|english|англ|английск\w*)\b""", RegexOption.IGNORE_CASE).containsMatchIn(l) -> "eng"
            Regex("""\b(ukr|uk|ua|ukrainian|укр|украинск\w*)\b""", RegexOption.IGNORE_CASE).containsMatchIn(l) -> "ukr"
            Regex("""\b(fra|fre|fr|french|français|франц\w*)\b""", RegexOption.IGNORE_CASE).containsMatchIn(l) -> "fra"
            Regex("""\b(deu|ger|de|german|deutsch|нем|немецк\w*)\b""", RegexOption.IGNORE_CASE).containsMatchIn(l) -> "deu"
            Regex("""\b(ita|it|italian|italiano|итал\w*)\b""", RegexOption.IGNORE_CASE).containsMatchIn(l) -> "ita"
            Regex("""\b(spa|es|spanish|español|исп\w*)\b""", RegexOption.IGNORE_CASE).containsMatchIn(l) -> "spa"
            Regex("""\b(jpn|ja|japanese|япон\w*)\b""", RegexOption.IGNORE_CASE).containsMatchIn(l) -> "jpn"
            Regex("""\b(kor|ko|korean|корей\w*)\b""", RegexOption.IGNORE_CASE).containsMatchIn(l) -> "kor"
            Regex("""\b(zho|chi|zh|chinese|китай\w*)\b""", RegexOption.IGNORE_CASE).containsMatchIn(l) -> "zho"
            Regex("""\b(pol|pl|polish|polski|польск\w*)\b""", RegexOption.IGNORE_CASE).containsMatchIn(l) -> "pol"
            Regex("""\b(tur|tr|turkish|турецк\w*)\b""", RegexOption.IGNORE_CASE).containsMatchIn(l) -> "tur"
            Regex("""\b(por|pt|portuguese|португал\w*)\b""", RegexOption.IGNORE_CASE).containsMatchIn(l) -> "por"
            else -> null
        }
    }

    internal fun resolveType(
        track: TrackOption,
        rawLabel: String,
        strings: TrackLocalizedStrings,
    ): String? {
        if (track.forced) return strings.forced
        val l = rawLabel.lowercase(Locale.ROOT)
        return when {
            Regex("""\b(forced|force|форсирован\w*|форс\w*)\b""", RegexOption.IGNORE_CASE).containsMatchIn(l) -> strings.forced
            Regex("""\b(full|полн\w*|complete)\b""", RegexOption.IGNORE_CASE).containsMatchIn(l) -> strings.full
            Regex("""\b(dub|dubbed|dubbing|дубл\w*|дубляж\w*)\b""", RegexOption.IGNORE_CASE).containsMatchIn(l) -> strings.dubbing
            Regex("""\b(orig|original|оригинал\w*)\b""", RegexOption.IGNORE_CASE).containsMatchIn(l) -> strings.original
            Regex("""\b(mvo|многоголос\w*)\b""", RegexOption.IGNORE_CASE).containsMatchIn(l) -> strings.multiVoice ?: strings.dubbing
            Regex("""\b(dvo|двухголос\w*)\b""", RegexOption.IGNORE_CASE).containsMatchIn(l) -> strings.twoVoice ?: strings.dubbing
            Regex("""\b(avo|одноголос\w*|авторск\w*)\b""", RegexOption.IGNORE_CASE).containsMatchIn(l) -> strings.singleVoice ?: strings.dubbing
            Regex("""\b(sdh|глухих|слабослышащ\w*)\b""", RegexOption.IGNORE_CASE).containsMatchIn(l) -> strings.sdh ?: strings.full
            Regex("""\b(commentary|комментар\w*)\b""", RegexOption.IGNORE_CASE).containsMatchIn(l) -> strings.commentary
            else -> null
        }
    }

    private fun extractCustomLabel(rawLabel: String, resolvedLanguage: String?): String? {
        if (rawLabel.isBlank()) return null
        val l = rawLabel.lowercase(Locale.ROOT)
        // If the label is just a type keyword or generic placeholder, ignore it
        val isGenericOrType = Regex(
            """^(forced|force|full|dub|dubbed|dubbing|orig|original|audio|track|subtitle|subtitles|default|none|\d+|русский|русская|russian|rus|ru|english|eng|en|ukrainian|ukr|ua|french|fra|fre|fr|german|deu|ger|de)$""",
            RegexOption.IGNORE_CASE,
        ).matches(l.trim())
        if (isGenericOrType) return null

        // If the label is like "Russian (Dub)" or "Русский (дублированный)", it was already handled by language + type
        if (detectLanguageCodeFromLabel(rawLabel) != null && Regex("""\b(dub|orig|forced|full|дубл|дубляж|оригинал|форсир|полн)\b""", RegexOption.IGNORE_CASE).containsMatchIn(l)) {
            return null
        }

        // Clean up brackets or redundant language name from the label if needed
        var cleaned = rawLabel
        if (resolvedLanguage != null) {
            cleaned = cleaned.replace(resolvedLanguage, "", ignoreCase = true)
        }
        cleaned = cleaned.trim().trim('(', ')', '[', ']', '-', ':', ' ')
        return cleaned.takeIf { it.isNotBlank() }
    }
}
