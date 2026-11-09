// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.

package tv.own.owntv.extras.favoriteshub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity

class FavoritesHubGroupingTest {

    private fun testChannel(
        id: Long,
        name: String,
        categoryId: Long? = 10L,
        sourceId: Long = 1L,
        number: Int? = null,
        sortOrder: Int = 0,
        catchup: Boolean = false,
        catchupDays: Int = 0,
    ) = ChannelEntity(
        id = id,
        sourceId = sourceId,
        categoryId = categoryId,
        name = name,
        streamUrl = "http://test.stream/$id",
        number = number,
        sortOrder = sortOrder,
        catchup = catchup,
        catchupDays = catchupDays,
    )

    @Test
    fun `empty favorites yields empty rows`() {
        val (all, rows) = FavoritesHubGrouping.process(
            channels = emptyList(),
            categoriesById = emptyMap(),
            sourceNamesById = emptyMap(),
        )
        assertTrue(all.isEmpty())
        assertTrue(rows.isEmpty())
    }

    @Test
    fun `hidden channels, hidden categories, and sealed categories are excluded`() {
        val ch1 = testChannel(1, "Channel 1", categoryId = 10)
        val ch2 = testChannel(2, "Channel 2 (hidden)", categoryId = 10)
        val ch3 = testChannel(3, "Channel 3 in hidden cat", categoryId = 20)
        val ch4 = testChannel(4, "Channel 4 in sealed cat", categoryId = 30)
        val ch5 = testChannel(5, "Channel 5 visible", categoryId = 40)

        val categories = mapOf(10L to "News", 20L to "HiddenCat", 30L to "Adult", 40L to "Movies")

        val (all, rows) = FavoritesHubGrouping.process(
            channels = listOf(ch1, ch2, ch3, ch4, ch5),
            categoriesById = categories,
            sourceNamesById = mapOf(1L to "Source 1"),
            hiddenCategoryIds = setOf(20L),
            hiddenChannelIds = setOf(2L),
            sealedCategoryIds = setOf(30L),
        )

        assertEquals(listOf(1L, 5L), all.map { it.channel.id })
        assertEquals(2, rows.size)
        assertEquals(listOf("News", "Movies"), rows.map { it.categoryName })
    }

    @Test
    fun `channel is present in both top row and category row`() {
        val ch1 = testChannel(1, "Discovery", categoryId = 10)
        val ch2 = testChannel(2, "National Geographic", categoryId = 10)
        val categories = mapOf(10L to "Documentary")

        val (all, rows) = FavoritesHubGrouping.process(
            channels = listOf(ch1, ch2),
            categoriesById = categories,
            sourceNamesById = mapOf(1L to "Playlist 1"),
        )

        assertEquals(2, all.size)
        assertEquals(1, rows.size)
        assertEquals(2, rows[0].channels.size)
        assertEquals(listOf(1L, 2L), all.map { it.channel.id })
        assertEquals(listOf(1L, 2L), rows[0].channels.map { it.channel.id })
    }

    @Test
    fun `same named categories from multiple playlists are merged into one row`() {
        // Channel 1 from Source 1 in "Cinema"
        val ch1 = testChannel(1, "Cinema HD", categoryId = 101, sourceId = 1)
        // Channel 2 from Source 2 in "cinema" (different case)
        val ch2 = testChannel(2, "Kino Plus", categoryId = 201, sourceId = 2)
        // Channel 3 from Source 1 in "Sports"
        val ch3 = testChannel(3, "Sport 1", categoryId = 102, sourceId = 1)

        val categories = mapOf(
            101L to "Cinema",
            201L to "cinema",
            102L to "Sports",
        )
        val sources = mapOf(
            1L to "M3U Provider",
            2L to "Stalker Portal",
        )

        val (all, rows) = FavoritesHubGrouping.process(
            channels = listOf(ch1, ch2, ch3),
            categoriesById = categories,
            sourceNamesById = sources,
            categoryOrder = listOf("Cinema", "Sports"),
        )

        assertEquals(3, all.size)
        // Cinema merged into 1 row!
        assertEquals(2, rows.size)
        assertEquals("Cinema", rows[0].categoryName)
        assertEquals(listOf(1L, 2L), rows[0].channels.map { it.channel.id })
        assertEquals("Sports", rows[1].categoryName)
        assertEquals(listOf(3L), rows[1].channels.map { it.channel.id })

        // Check multi-source indicator and source names
        assertTrue(all[0].hasMultipleSources)
        assertEquals("M3U Provider", all[0].sourceName)
        assertEquals("Stalker Portal", all[1].sourceName)
    }

    @Test
    fun `channels without category are grouped into uncategorized row at the end`() {
        val ch1 = testChannel(1, "Orphan Channel", categoryId = null)
        val ch2 = testChannel(2, "News Channel", categoryId = 10)
        val categories = mapOf(10L to "News")

        val (all, rows) = FavoritesHubGrouping.process(
            channels = listOf(ch1, ch2),
            categoriesById = categories,
            sourceNamesById = mapOf(1L to "P1"),
            categoryOrder = listOf("News"),
            uncategorizedLabel = "Без категории",
        )

        assertEquals(2, all.size)
        assertEquals(2, rows.size)
        assertEquals("News", rows[0].categoryName)
        assertEquals("Без категории", rows[1].categoryName)
        assertEquals(1L, rows[1].channels[0].channel.id)
    }

    @Test
    fun `manual order outranks channel numbers and names in top row`() {
        val ch1 = testChannel(1, "Alpha", number = 1)
        val ch2 = testChannel(2, "Beta", number = 2)
        val ch3 = testChannel(3, "Gamma", number = 3)

        // User manually moved Gamma (3) to first place, Alpha (1) to second, Beta (2) to third
        val manual = mapOf(3L to 0, 1L to 1, 2L to 2)

        val (all, _) = FavoritesHubGrouping.process(
            channels = listOf(ch1, ch2, ch3),
            categoriesById = mapOf(10L to "General"),
            sourceNamesById = mapOf(1L to "Source"),
            manualPositions = manual,
        )

        assertEquals(listOf(3L, 1L, 2L), all.map { it.channel.id })
    }

    @Test
    fun `archive badge is active only when catchup is enabled and preserves days`() {
        val chNoCatchup = testChannel(1, "Live Only", catchup = false, catchupDays = 0)
        val chCatchupIndefinite = testChannel(2, "Archive Portal", catchup = true, catchupDays = 0)
        val chCatchup7Days = testChannel(3, "Archive 7d", catchup = true, catchupDays = 7)

        val (all, _) = FavoritesHubGrouping.process(
            channels = listOf(chNoCatchup, chCatchupIndefinite, chCatchup7Days),
            categoriesById = mapOf(10L to "General"),
            sourceNamesById = mapOf(1L to "Source"),
        )

        assertFalse(all[0].hasCatchup)
        assertEquals(0, all[0].catchupDays)

        assertTrue(all[1].hasCatchup)
        assertEquals(0, all[1].catchupDays)

        assertTrue(all[2].hasCatchup)
        assertEquals(7, all[2].catchupDays)
    }

    @Test
    fun `programme progress and time left calculation`() {
        val ch = testChannel(1, "Movie Channel")
        val now = 1_000_000L
        val prog = EpgProgrammeEntity(
            sourceId = 1L,
            epgChannelId = "movie",
            title = "Inception",
            startMs = 900_000L,
            stopMs = 1_100_000L,
        )

        val (all, _) = FavoritesHubGrouping.process(
            channels = listOf(ch),
            categoriesById = mapOf(10L to "Movies"),
            sourceNamesById = mapOf(1L to "Source"),
            programmesByChannelId = mapOf(1L to prog),
            nowMs = now,
        )

        assertEquals("Inception", all[0].programme?.title)
        assertEquals(0.5f, all[0].progress, 0.01f) // exactly half-way
        assertEquals(2L, all[0].timeLeftMinutes) // (100_000 ms + 59_999) / 60_000 = 2 min
    }
}
