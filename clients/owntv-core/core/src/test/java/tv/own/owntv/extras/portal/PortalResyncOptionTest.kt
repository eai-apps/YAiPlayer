// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.portal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType

class PortalResyncOptionTest {

    @Test
    fun m3uHidesVodOnly() {
        val source = SourceEntity(
            id = 1L,
            name = "Test M3U",
            url = "http://example.com/test.m3u",
            type = SourceType.M3U,
            syncMovies = true,
            syncSeries = true,
        )
        val options = PortalResyncOption.availableFor(source)
        assertEquals(4, options.size)
        assertFalse(options.contains(PortalResyncOption.VOD_ONLY))
        assertTrue(options.contains(PortalResyncOption.ALL))
        assertTrue(options.contains(PortalResyncOption.CHANNELS_ONLY))
        assertTrue(options.contains(PortalResyncOption.EPG_ONLY))
        assertTrue(options.contains(PortalResyncOption.CHANNELS_AND_EPG))
    }

    @Test
    fun xtreamWithVodShowsAllFive() {
        val source = SourceEntity(
            id = 2L,
            name = "Test Xtream",
            url = "http://example.com",
            type = SourceType.XTREAM,
            syncMovies = true,
            syncSeries = false,
        )
        val options = PortalResyncOption.availableFor(source)
        assertEquals(5, options.size)
        assertTrue(options.contains(PortalResyncOption.VOD_ONLY))
    }

    @Test
    fun stalkerWithVodShowsAllFive() {
        val source = SourceEntity(
            id = 3L,
            name = "Test Stalker",
            url = "http://example.com/c",
            type = SourceType.STALKER,
            syncMovies = false,
            syncSeries = true,
        )
        val options = PortalResyncOption.availableFor(source)
        assertEquals(5, options.size)
        assertTrue(options.contains(PortalResyncOption.VOD_ONLY))
    }

    @Test
    fun stalkerWithoutVodHidesVodOnly() {
        val source = SourceEntity(
            id = 4L,
            name = "Test Stalker Live Only",
            url = "http://example.com/c",
            type = SourceType.STALKER,
            syncMovies = false,
            syncSeries = false,
        )
        val options = PortalResyncOption.availableFor(source)
        assertEquals(4, options.size)
        assertFalse(options.contains(PortalResyncOption.VOD_ONLY))
    }

    @Test
    fun sessionFirstEntryStateDefaults() {
        assertTrue(SessionFirstEntryState.isFirstLiveEntry)
        assertTrue(SessionFirstEntryState.isFirstMoviesEntry)
        assertTrue(SessionFirstEntryState.isFirstSeriesEntry)
    }
}
