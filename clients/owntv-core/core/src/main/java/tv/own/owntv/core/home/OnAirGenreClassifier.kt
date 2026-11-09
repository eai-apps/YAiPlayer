package tv.own.owntv.core.home

import tv.own.owntv.core.content.ProgrammeSearchMatcher
import tv.own.owntv.core.parser.EpgDetails

/**
 * Maps XMLTV `<category>` values to the 7 [OnAirGenre] chips.
 * Supports both Russian and English names, case-insensitive, with NFKC and ё/е normalization.
 */
object OnAirGenreClassifier {

    private val separatorRegex = Regex("[${EpgDetails.CATEGORY_SEPARATOR},;/|]")

    private val movieKeywords = listOf(
        "фильм", "кино", "боевик", "комеди", "триллер", "драм", "мелодрам",
        "детектив", "фантастик", "ужас", "вестерн", "приключен", "кинофильм",
        "криминал", "х/ф", "х.ф", "хф", "худ.фильм", "художественный фильм",
        "movie", "film", "cinema", "feature film", "action", "comedy",
        "thriller", "drama", "horror", "sci-fi", "science fiction", "western",
        "adventure", "crime", "romance",
    )

    private val seriesKeywords = listOf(
        "сериал", "т/с", "т.с", "тс", "телесериал", "многосерийный",
        "мини-сериал", "ситком", "мыльная опера", "новелла",
        "series", "tv series", "tv show", "soap", "sitcom", "mini-series",
        "miniseries", "episode", "serial", "telenovela",
    )

    private val sportsKeywords = listOf(
        "спорт", "футбол", "хоккей", "баскетбол", "теннис", "бокс", "мма",
        "единоборств", "биатлон", "волейбол", "автоспорт", "гонк", "формула-1",
        "формула 1", "олимпиад", "чемпионат", "атлетик", "фигурное катание",
        "регби", "гандбол", "плавание", "дзюдо", "борьба",
        "sport", "football", "soccer", "hockey", "basketball", "tennis",
        "boxing", "mma", "martial arts", "biathlon", "volleyball", "motorsport",
        "racing", "formula 1", "olympics", "athletics", "rugby", "cricket",
    )

    private val newsKeywords = listOf(
        "новост", "вести", "время", "событи", "политик", "информационн",
        "информаци", "факты", "актуально", "репортаж", "интервью",
        "ток-шоу", "аналитик", "пресс", "известия",
        "news", "information", "bulletin", "current affairs", "politics",
        "report", "breaking news", "weather", "talk show",
    )

    private val kidsKeywords = listOf(
        "детям", "детск", "мультфильм", "мультсериал", "мультик", "анимаци",
        "сказк", "для детей", "м/ф", "м.ф", "мф", "спокойной ночи",
        "kids", "children", "child", "cartoon", "animation", "animated",
        "family", "for children", "anime", "youth",
    )

    private val musicKeywords = listOf(
        "музык", "концерт", "клип", "хит-парад", "чарт", "песн", "рок",
        "поп", "эстрад", "джаз", "классическая музык", "опера", "балет",
        "music", "musical", "concert", "music video", "chart", "pop",
        "rock", "jazz", "opera", "classical music",
    )

    private val educationalKeywords = listOf(
        "познавательн", "документальн", "д/ф", "д.ф", "дф", "наук", "истори",
        "природ", "животн", "путешестви", "культур", "технологи", "образовани",
        "научно-популярн", "исследовани", "дискавери", "географи", "планета",
        "educational", "education", "documentary", "doc", "science", "history",
        "nature", "wildlife", "animals", "travel", "culture", "technology",
        "discovery", "investigation", "geography",
    )

    /**
     * Classifies raw category string from XMLTV into a set of [OnAirGenre]s.
     * Returns empty set if no matching genres are found or if [rawCategories] is null/empty.
     */
    fun classify(rawCategories: String?): Set<OnAirGenre> {
        if (rawCategories.isNullOrBlank()) return emptySet()

        val parts = rawCategories.split(separatorRegex)
        val matched = LinkedHashSet<OnAirGenre>()

        for (part in parts) {
            val norm = ProgrammeSearchMatcher.normalize(part)
            if (norm.isEmpty()) continue

            if (matchesAny(norm, movieKeywords)) matched.add(OnAirGenre.MOVIES)
            if (matchesAny(norm, seriesKeywords)) matched.add(OnAirGenre.SERIES)
            if (matchesAny(norm, sportsKeywords)) matched.add(OnAirGenre.SPORTS)
            if (matchesAny(norm, newsKeywords)) matched.add(OnAirGenre.NEWS)
            if (matchesAny(norm, kidsKeywords)) matched.add(OnAirGenre.KIDS)
            if (matchesAny(norm, musicKeywords)) matched.add(OnAirGenre.MUSIC)
            if (matchesAny(norm, educationalKeywords)) matched.add(OnAirGenre.EDUCATIONAL)
        }

        return matched
    }

    private fun matchesAny(normalizedText: String, keywords: List<String>): Boolean {
        for (kw in keywords) {
            val normKw = ProgrammeSearchMatcher.normalize(kw)
            if (normalizedText.contains(normKw)) return true
        }
        return false
    }

    /**
     * Checks if [itemGenres] satisfies the selected [filterGenre].
     * [OnAirGenre.ALL] matches everything.
     */
    fun matchesFilter(itemGenres: Set<OnAirGenre>, filterGenre: OnAirGenre): Boolean {
        return filterGenre == OnAirGenre.ALL || filterGenre in itemGenres
    }
}
