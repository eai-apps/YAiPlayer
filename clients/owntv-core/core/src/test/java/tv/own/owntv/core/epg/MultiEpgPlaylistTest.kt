package tv.own.owntv.core.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.repository.isValidEpgUrl
import tv.own.owntv.core.repository.joinGuideUrls
import tv.own.owntv.core.repository.splitGuideUrls

class MultiEpgPlaylistTest {

    private val hour = 3600_000L

    @Test
    fun legacyPlaylistWithSingleEpgMigratesAsFirstItem() {
        val legacySource = SourceEntity(
            id = 101L,
            name = "My Playlist",
            type = SourceType.M3U,
            url = "https://provider.example/playlist.m3u",
            epgUrl = "https://epg.example/primary.xml.gz",
        )

        val epgList = splitGuideUrls(legacySource.epgUrl)
        assertEquals(1, epgList.size)
        assertEquals("https://epg.example/primary.xml.gz", epgList[0])
    }

    @Test
    fun multiEpgUrlsJoinedAndSplitRoundtrip() {
        val urls = listOf(
            "https://epg1.example/guide.xml",
            "https://epg2.example/guide.xml",
            "https://epg3.example/guide.xml",
        )

        val joined = joinGuideUrls(urls)
        val parsed = splitGuideUrls(joined)

        assertEquals(urls, parsed)
        assertEquals(urls[0], parsed.first())
    }

    @Test
    fun emptyAndDuplicateUrlsAreDiscardedWhilePreservingOrder() {
        val input = """
            https://epg1.example/guide.xml
            
            https://epg2.example/guide.xml
            https://epg1.example/guide.xml
               
            https://epg3.example/guide.xml
        """.trimIndent()

        val parsed = splitGuideUrls(input)
        assertEquals(
            listOf(
                "https://epg1.example/guide.xml",
                "https://epg2.example/guide.xml",
                "https://epg3.example/guide.xml",
            ),
            parsed,
        )
    }

    @Test
    fun urlValidationRequirements() {
        assertTrue(isValidEpgUrl("http://epg.org/guide.xml"))
        assertTrue(isValidEpgUrl("https://epg.org/guide.xml.gz"))
        assertFalse(isValidEpgUrl("ftp://epg.org/guide.xml"))
        assertFalse(isValidEpgUrl("epg.org/guide.xml"))
        assertFalse(isValidEpgUrl(""))
    }

    @Test
    fun multiEpgPriorityResolvesOverlappingProgrammes() {
        val feed1Id = -10L
        val feed2Id = -11L

        val p1 = EpgProgrammeEntity(
            id = 1L,
            sourceId = feed1Id,
            epgChannelId = "channel.one",
            startMs = 10 * hour,
            stopMs = 12 * hour,
            title = "Primary News",
        )
        // Feed 2 overlaps with Feed 1 (11:00 - 13:00)
        val p2Overlap = EpgProgrammeEntity(
            id = 2L,
            sourceId = feed2Id,
            epgChannelId = "channel.one",
            startMs = 11 * hour,
            stopMs = 13 * hour,
            title = "Secondary News (Overlapping)",
        )
        // Feed 2 non-overlapping later program (13:00 - 14:00)
        val p2Gap = EpgProgrammeEntity(
            id = 3L,
            sourceId = feed2Id,
            epgChannelId = "channel.one",
            startMs = 13 * hour,
            stopMs = 14 * hour,
            title = "Secondary Afternoon Show",
        )

        val priority = GuidePriority.of(listOf(feed1Id, feed2Id))
        val resolved = priority.resolve(listOf(p1, p2Overlap, p2Gap))

        val keptIds = resolved.map { it.id }
        assertEquals(listOf(1L, 3L), keptIds)
        assertEquals("Primary News", resolved[0].title)
        assertEquals("Secondary Afternoon Show", resolved[1].title)
    }
}
