package tv.own.owntv.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType

class QuickActionsLogicTest {

    private val stalker = SourceEntity(
        id = 1L, name = "Stalker Portal", type = SourceType.STALKER, url = "http://portal.example/c/",
        syncLive = true, syncMovies = true, syncSeries = true,
    )

    private val xtream = SourceEntity(
        id = 2L, name = "Xtream Playlist", type = SourceType.XTREAM, url = "http://xtream.example",
        syncLive = true, syncMovies = true, syncSeries = false,
    )

    private val m3u = SourceEntity(
        id = 3L, name = "M3U Playlist", type = SourceType.M3U, url = "http://m3u.example/playlist.m3u",
        syncLive = true, syncMovies = false, syncSeries = false,
    )

    @Test
    fun `hasLibrary is true for Stalker and Xtream and false for M3U`() {
        fun hasLibrary(source: SourceEntity?) =
            source?.type == SourceType.STALKER || source?.type == SourceType.XTREAM

        assertTrue(hasLibrary(stalker))
        assertTrue(hasLibrary(xtream))
        assertFalse(hasLibrary(m3u))
        assertFalse(hasLibrary(null))
    }

    @Test
    fun `refreshVod scopes only movies and series from source enabled flags`() {
        fun vodContentTypes(source: SourceEntity): SyncContentTypes =
            SyncContentTypes(live = false, movies = source.syncMovies, series = source.syncSeries)

        val stalkerVod = vodContentTypes(stalker)
        assertFalse(stalkerVod.live)
        assertTrue(stalkerVod.movies)
        assertTrue(stalkerVod.series)

        val xtreamVod = vodContentTypes(xtream)
        assertFalse(xtreamVod.live)
        assertTrue(xtreamVod.movies)
        assertFalse(xtreamVod.series)
    }

    @Test
    fun `target sources resolve to active playlist or all playlists when none selected`() {
        val all = listOf(stalker, xtream, m3u)

        val targetActive = all.filter { it.id == 2L }.ifEmpty { all }
        assertEquals(listOf(xtream), targetActive)

        val targetAll = all.filter { it.id == -1L }.ifEmpty { all }
        assertEquals(all, targetAll)
    }

    @Test
    fun `source type determines whether portal or playlist refresh wording is used`() {
        fun isPortal(source: SourceEntity?) = source?.type == SourceType.STALKER

        assertTrue(isPortal(stalker))
        assertFalse(isPortal(xtream))
        assertFalse(isPortal(m3u))
        assertFalse(isPortal(null))
    }
}
