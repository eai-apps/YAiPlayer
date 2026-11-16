// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NameSearchTest {
    private fun matches(pattern: String, name: String): Boolean {
        // GLOB semantics enough for these patterns: '*' prefix/suffix and [..] classes.
        val regex = Regex(pattern.trim('*').let { inner ->
            val sb = StringBuilder()
            var i = 0
            while (i < inner.length) {
                val c = inner[i]
                if (c == '[') { val end = inner.indexOf(']', i + 2); sb.append(inner, i, end + 1); i = end + 1 }
                else { sb.append(Regex.escape(c.toString())); i++ }
            }
            sb.toString()
        })
        return regex.containsMatchIn(name)
    }

    @Test fun lowercaseCyrillicFindsCapitalised() {
        val p = NameSearch.glob("кино")
        assertEquals("*[кК][иИ][нН][оО]*", p)
        assertTrue(matches(p, "КиноКнига"))
        assertTrue(matches(p, "Кинозал | Советское Кино"))
        assertTrue(matches(p, "КИНОМИКС"))
    }

    @Test fun midWordCapitalAndYo() {
        assertTrue(matches(NameSearch.glob("кинокнига"), "КиноКнига"))
        assertTrue(matches(NameSearch.glob("елка"), "Ёлка ТВ"))
    }

    @Test fun blankMatchesAll() = assertEquals("*", NameSearch.glob("  "))
}
