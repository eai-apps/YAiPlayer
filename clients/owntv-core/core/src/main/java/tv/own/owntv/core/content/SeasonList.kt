// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.content

/** The season choice on a show's page (TV and phone): which seasons, in which order, and when episodes get a picker. */
object SeasonList {
    /** From this many episodes in a season the page offers an «Episodes ▾» picker that jumps to one. */
    const val EPISODE_PICKER_FROM = 30

    /**
     * Distinct seasons in the chosen order; specials (season 0) always after the numbered seasons.
     * The count shown in the header is [numberedCount] of the same list, so «13 seasons» and the picker agree.
     */
    fun order(seasonNumbers: Iterable<Int>, descending: Boolean): List<Int> =
        seasonNumbers.distinct().filter { it >= 0 }
            .let { if (descending) it.sortedDescending() else it.sorted() }
            .sortedBy { it == 0 }

    fun numberedCount(seasons: List<Int>): Int = seasons.count { it > 0 }
}
