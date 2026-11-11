// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonListTest {
    @Test fun `thirteen seasons and specials, both orders`() {
        // «American Horror Story»: 13 seasons, episodes listed out of order, plus specials.
        val numbers = (13 downTo 1).flatMap { s -> List(6) { s } } + listOf(0, 0, 7)
        val asc = SeasonList.order(numbers, descending = false)
        assertEquals((1..13).toList() + 0, asc)
        assertEquals((13 downTo 1).toList() + 0, SeasonList.order(numbers, descending = true))
        assertEquals(13, SeasonList.numberedCount(asc))
    }

    @Test fun `every season stays reachable, however many`() {
        val many = SeasonList.order((1..30).shuffled(java.util.Random(1)), descending = false)
        assertEquals(30, many.size)
        assertEquals((1..30).toList(), many)
    }

    @Test fun `episode picker only for long seasons`() {
        assertFalse(29 >= SeasonList.EPISODE_PICKER_FROM)
        assertTrue(30 >= SeasonList.EPISODE_PICKER_FROM)
    }

    @Test fun `broken season numbers are dropped`() {
        assertEquals(listOf(1, 2), SeasonList.order(listOf(2, -1, 1, 2), descending = false))
    }
}
