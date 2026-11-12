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

    @Test fun flagsAndGlyphs() {
        org.junit.Assert.assertEquals("\uD83C\uDDE9\uD83C\uDDEA", RadioLook.flag("de"))
        org.junit.Assert.assertNull(RadioLook.flag("Germany"))
        org.junit.Assert.assertNull(RadioLook.flag(null))
        org.junit.Assert.assertEquals(RadioLook.Glyph.RETRO, RadioLook.glyphOf("80s"))
        org.junit.Assert.assertEquals(RadioLook.Glyph.NOTE, RadioLook.glyphOf("unknown"))
        // Every genre of the list has a glyph other than the fallback, except the plain-note ones by design.
        RadioGenres.all.forEach { RadioLook.glyphOf(it.id) }
    }

    @Test fun myCountrySearchesByCode() {
        val path = RadioUrls.searchPath(null, null, countryCode = "ru")
        org.junit.Assert.assertTrue(path, path.contains("countrycode=RU"))
        val s = RadioParser.station(org.json.JSONObject().put("url_resolved", "https://x/y").put("name", "N").put("countrycode", "ua"))
        org.junit.Assert.assertEquals("UA", s?.countryCode)
    }
}
