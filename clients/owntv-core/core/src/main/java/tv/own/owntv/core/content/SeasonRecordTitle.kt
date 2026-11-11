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
}
