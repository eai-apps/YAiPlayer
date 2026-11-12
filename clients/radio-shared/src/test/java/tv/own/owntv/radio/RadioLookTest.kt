// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioLookTest {
    @Test fun `every genre shown has the site's colours`() {
        RadioGenres.all.forEach { g ->
            assertTrue(g.id, RadioLook.genreColors.containsKey(g.id))
            assertEquals(g.id, 3, RadioLook.colorsOf(g.id).size)
        }
        assertTrue(!RadioLook.genreColors.containsKey("russian_rock"))
    }

    @Test fun `placeholder letter and steady colours`() {
        assertEquals("Е", RadioLook.initial("  Европа Плюс"))
        assertEquals("1", RadioLook.initial("101.ru"))
        assertEquals("♪", RadioLook.initial("..."))
        assertTrue(RadioLook.placeholderColors("Radio X") === RadioLook.placeholderColors("radio x "))
    }
}
