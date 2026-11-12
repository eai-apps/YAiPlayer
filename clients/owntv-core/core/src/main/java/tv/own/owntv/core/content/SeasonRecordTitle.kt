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
        if (looseKeys(x, y)) return true
        // Bilingual titles: «Американская история ужасов / Американська історія жаху» is the show «Американская
        // история ужасов» (owner 12.11: seasons 5–8, 10, 11 sat in a second card). Compare the left parts too.
        val lx = leftPart(x)
        val ly = leftPart(y)
        return (lx != x || ly != y) && lx.length >= MIN_BASE && ly.length >= MIN_BASE && looseKeys(lx, ly)
    }

    /** The title before « / » (the second language of a bilingual title), or the whole title. */
    private fun leftPart(key: String): String = key.substringBefore(ALT_TITLE).trim()

    private fun looseKeys(x: String, y: String): Boolean {
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
        // Linear, not pairwise: a panel with tens of thousands of season records froze the series tab and then
        // crashed the app (owner 12.11). Each base title is computed once; a record joins the group of its exact
        // base, or of the shortest known base it extends with a subtitle separator (same rule as sameShowLoose).
        class Rec(val id: Long, val shown: String, val key: String)
        val records = rows.filter { isSeasonRecord(it.second) }
            .map { val shown = base(it.second); Rec(it.first, shown, shown.lowercase()) }
            .sortedBy { it.key.length }
        val groups = ArrayList<MutableList<Rec>>()
        val byKey = HashMap<String, MutableList<Rec>>()
        for (r in records) {
            var g = byKey[r.key]
            val left = leftPart(r.key)
            if (g == null && left != r.key && left.length >= MIN_BASE) g = byKey[left]
            if (g == null) {
                var idx = MIN_BASE
                while (idx < r.key.length && g == null) {
                    if (SUBTITLE_STARTS.any { r.key.startsWith(it, idx) }) g = byKey[r.key.substring(0, idx)]
                    idx++
                }
            }
            if (g == null) {
                g = mutableListOf()
                groups.add(g)
                byKey[r.key] = g
                // A later record named only by the first-language part («Американская история ужасов [S05]») joins this group.
                if (left != r.key && left.length >= MIN_BASE) byKey.putIfAbsent(left, g)
            }
            g.add(r)
        }
        val hidden = HashSet<Long>()
        val names = HashMap<Long, String>()
        for (g in groups) {
            val keep = g.minBy { it.id }
            g.forEach { if (it.id != keep.id) hidden.add(it.id) }
            names[keep.id] = g.first().shown
        }
        return Cards(hidden, names)
    }

    data class Cards(val hidden: Set<Long>, val names: Map<Long, String>)

    private const val MIN_BASE = 3
    private val ALT_TITLE = String(charArrayOf(' ', '/', ' '))
    /** How a season subtitle follows the title. A bare «-» or «/» is part of titles («Баба-яга / Яга»), not one. */
    private val SUBTITLE_STARTS = listOf(":", " :", ".", " - ", " — ", " – ", "—", "–", " (")
}
