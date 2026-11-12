// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.content

/**
 * Some Stalker panels keep every season as its own record — «Король гольфа [S01]», «Король гольфа [S02]
 * [05 из 06] [ua] [ru]» — so the app showed one season per card and a show whose first season the
 * operator had not uploaded yet looked like it began at season two (П8, 10.11.2026). The base title is
 * the name without any bracketed tag; records sharing it in one playlist are one show.
 */
object SeasonRecordTitle {
    private val TAG = Regex("""\s*\[[^\]]*]\s*""")
    private val SEASON = Regex("""\[S(\d{1,3})(?:E\d{1,4})?]""", RegexOption.IGNORE_CASE)

    fun isSeasonRecord(name: String?): Boolean = name != null && SEASON.containsMatchIn(name)

    fun base(name: String): String = TAG.replace(name, " ").replace(Regex("\\s+"), " ").trim()

    fun season(name: String?): Int? = name?.let { SEASON.find(it)?.groupValues?.get(1)?.toIntOrNull() }

    fun sameShow(a: String, b: String): Boolean = base(a).equals(base(b), ignoreCase = true)

    /**
     * [sameShow], or one title is the other plus a season subtitle after a separator: «Американская история
     * ужасов: Отель [S05]» belongs with «Американская история ужасов [S13]». Without this, the seasons with a
     * subtitle (5–8, 10, 11 of that show) were missing from the season list (owner 12.11).
     */
    fun sameShowLoose(a: String, b: String): Boolean {
        val x = base(a).lowercase()
        val y = base(b).lowercase()
        if (x == y) return true
        val (short, long) = if (x.length <= y.length) x to y else y to x
        if (short.length < MIN_BASE || !long.startsWith(short)) return false
        val rest = long.substring(short.length)
        return SUBTITLE_STARTS.any { rest.startsWith(it) }
    }

    /**
     * One card per show in the series list: of the season records (`[Sxx]`) in [rows] (id to name), every record but
     * the lowest id of its show is hidden, and the shown one is named without the tags. Plain titles are untouched.
     */
    fun cards(rows: List<Pair<Long, String>>): Cards {
        val records = rows.filter { isSeasonRecord(it.second) }.sortedBy { base(it.second).length }
        val groups = ArrayList<MutableList<Pair<Long, String>>>()
        for (r in records) {
            val g = groups.firstOrNull { sameShowLoose(it.first().second, r.second) }
            if (g != null) g.add(r) else groups.add(mutableListOf(r))
        }
        val hidden = HashSet<Long>()
        val names = HashMap<Long, String>()
        for (g in groups) {
            val keep = g.minBy { it.first }
            g.forEach { if (it.first != keep.first) hidden.add(it.first) }
            names[keep.first] = base(g.first().second)
        }
        return Cards(hidden, names)
    }

    data class Cards(val hidden: Set<Long>, val names: Map<Long, String>)

    private const val MIN_BASE = 3
    /** How a season subtitle follows the title. A bare «-» or «/» is part of titles («Баба-яга / Яга»), not one. */
    private val SUBTITLE_STARTS = listOf(":", " :", ".", " - ", " — ", " – ", "—", "–", " (")
}
