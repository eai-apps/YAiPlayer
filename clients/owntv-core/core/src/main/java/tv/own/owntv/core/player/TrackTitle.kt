// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.player

import java.util.Locale

/** What a track's provider title says about the track, beyond its language. */
enum class TrackTitleKind { FORCED, FULL, SDH, DUB, MVO, DVO, AVO, ORIGINAL, COMMENTARY }

/**
 * Audio and subtitle rows used to show the provider's title as is — «forced», «full», «Subtitle 4» —
 * and drop the language the stream does declare (П9, 10.11.2026). The row is now the language first,
 * then what kind of track it is, in the viewer's words: «Русский · Форсированные», «English · Оригинал».
 * A title that adds something else (a studio, «Пифагор») is kept after the language, never instead of it.
 */
object TrackTitle {
    /** A whole word starting with one of [stems] (Latin or Cyrillic; `\b` is unreliable for Cyrillic). */
    private fun words(vararg stems: String) =
        Regex("(?<!\\p{L})(?:" + stems.joinToString("|") + ")\\p{L}*(?!\\p{L})", RegexOption.IGNORE_CASE)

    private val KIND_WORDS: List<Pair<TrackTitleKind, Regex>> = listOf(
        TrackTitleKind.FORCED to words("forced", "форсир"),
        TrackTitleKind.SDH to words("sdh", "hearing", "cc", "слабослыш"),
        TrackTitleKind.FULL to words("full", "полн"),
        TrackTitleKind.COMMENTARY to words("comment", "коммент"),
        TrackTitleKind.DUB to words("dub", "дубл"),
        TrackTitleKind.MVO to words("mvo", "multivoice", "многоголос", "закадров"),
        TrackTitleKind.DVO to words("dvo", "двухголос"),
        TrackTitleKind.AVO to words("avo", "авторск"),
        TrackTitleKind.ORIGINAL to words("orig", "оригинал"),
    )

    /** Title words that name a language, for the tracks whose stream carries no language code. */
    private val LANGUAGE_WORDS: List<Pair<String, Regex>> = listOf(
        "ru" to words("rus", "рус"),
        "uk" to words("ukr", "укр"),
        "en" to words("eng", "англ"),
        "de" to words("ger", "deu", "deutsch", "немец"),
        "fr" to words("fre", "fra", "french", "français", "француз"),
        "es" to words("spa", "español", "испан"),
        "it" to words("ita", "итальян"),
    )

    /** Leftover noise once the kind and language words are taken out. */
    private val NOISE = Regex("(?i)(?<!\\p{L})(subtitles?|subs?|audio|track|субтитр\\p{L}*|дорожк\\p{L}*|звук|stereo)(?!\\p{L})|\\d+(\\.\\d+)?|[\\[\\](){}|,._\\-/:]+")

    /** `rus` → `ru`, `ger`/`deu` → `de`: streams carry ISO 639-2 codes, display names want 639-1. */
    private val ISO3_TO_2: Map<String, String> by lazy {
        val map = HashMap<String, String>()
        for (two in Locale.getISOLanguages()) {
            runCatching { Locale.forLanguageTag(two).isO3Language }.getOrNull()?.let { map[it] = two }
        }
        // Bibliographic codes some muxers still write.
        map["ger"] = "de"; map["fre"] = "fr"; map["chi"] = "zh"; map["cze"] = "cs"; map["dut"] = "nl"; map["gre"] = "el"
        map
    }

    internal fun normalizeCode(code: String): String {
        val c = code.trim().lowercase(Locale.ROOT).substringBefore('-').substringBefore('_')
        return if (c.length == 3) ISO3_TO_2[c] ?: c else c
    }

    /** An external subtitle's raw label carries its source (`OS_…`, `LOCAL_…`); it is shown as is. */
    fun isExternalLabel(raw: String?): Boolean =
        raw != null && (raw.startsWith(EXTERNAL_OS) || raw.startsWith(EXTERNAL_LOCAL))
    private const val EXTERNAL_OS = "OS_"
    private const val EXTERNAL_LOCAL = "LOCAL_"

    fun kinds(title: String?): List<TrackTitleKind> {
        if (title.isNullOrBlank()) return emptyList()
        return KIND_WORDS.filter { (_, re) -> re.containsMatchIn(title) }.map { it.first }
    }

    fun languageCode(title: String?): String? {
        if (title.isNullOrBlank()) return null
        return LANGUAGE_WORDS.firstOrNull { (_, re) -> re.containsMatchIn(title) }?.first
    }

    /** What remains of [title] after its language and kind words — a studio or a dub name, or blank. */
    fun remainder(title: String?): String {
        if (title.isNullOrBlank()) return ""
        var rest: String = title
        (KIND_WORDS.map { it.second } + LANGUAGE_WORDS.map { it.second }).forEach { rest = it.replace(rest, " ") }
        // A kind word's tail («форсированные» after «форсир») stays behind: drop any word it started.
        return NOISE.replace(rest, " ").replace(Regex("\\s+"), " ").trim()
    }

    /**
     * The row text. [kindText] localises a kind, [fallback] is «Аудио 2» / «Субтитры 2» for a track
     * with nothing to go on, [separator] is the app's « · ». [forcedFlag] is the stream's forced flag.
     */
    fun display(
        title: String?, langCode: String?, locale: Locale,
        kindText: (TrackTitleKind) -> String, fallback: String, separator: String,
        forcedFlag: Boolean = false,
    ): String {
        val code = langCode?.takeIf { it.isNotBlank() && !it.equals("und", true) } ?: languageCode(title)
        val language = code?.let { normalizeCode(it) }?.let {
            runCatching { Locale.forLanguageTag(it).getDisplayLanguage(locale) }.getOrNull()
                ?.takeIf { name -> name.isNotBlank() && !name.equals(it, true) }
                ?.replaceFirstChar { c -> c.titlecase(locale) }
                ?: it.uppercase(locale)
        }
        // The stream's own «forced» flag counts even when the title never says so.
        val kinds = kinds(title).let { if (forcedFlag && TrackTitleKind.FORCED !in it) listOf(TrackTitleKind.FORCED) + it else it }
        val extra = remainder(title).takeIf { it.length >= 2 && (language == null || !it.equals(language, true)) }
        // No language anywhere: the number still says which track it is, the kind says what it is.
        val head = language ?: fallback.takeIf { kinds.isNotEmpty() }
        val parts = listOfNotNull(head) + kinds.map(kindText) + listOfNotNull(extra)
        return parts.takeIf { it.isNotEmpty() }?.joinToString(separator)
            ?: title?.takeIf { it.isNotBlank() }
            ?: fallback
    }
}
