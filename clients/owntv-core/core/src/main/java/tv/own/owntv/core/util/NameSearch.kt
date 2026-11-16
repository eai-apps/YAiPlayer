// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.util

/**
 * Channel-name search that ignores letter case in every alphabet.
 *
 * Owner 16.11: «кино» found nothing while «Кинозал…», «КиноКнига» were in the list. SQLite's LIKE folds
 * case only for Latin letters, so a lowercase Cyrillic query never met a capitalised name. GLOB is
 * case-sensitive but takes character classes, so each letter becomes «[кК]»: «кино» → «*[кК][иИ][нН][оО]*»,
 * which meets «Кинозал», «КиноКнига» and «КИНО». «е» and «ё» count as the same letter.
 */
object NameSearch {
    fun glob(query: String): String {
        val q = query.trim()
        if (q.isEmpty()) return "*"
        val sb = StringBuilder("*")
        for (ch in q) {
            when {
                ch == 'е' || ch == 'ё' || ch == 'Е' || ch == 'Ё' -> sb.append("[еЕёЁ]")
                ch == '*' || ch == '?' || ch == '[' -> sb.append('[').append(ch).append(']')
                ch.isLetter() && ch.lowercaseChar() != ch.uppercaseChar() ->
                    sb.append('[').append(ch.lowercaseChar()).append(ch.uppercaseChar()).append(']')
                else -> sb.append(ch)
            }
        }
        return sb.append('*').toString()
    }
}
