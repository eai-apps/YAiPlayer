package tv.own.owntv.core.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity

class OnAirFeedTest {

    private fun channel(
        id: Long,
        name: String,
        sourceId: Long = 1L,
        catchup: Boolean = true,
        sortOrder: Int = 1,
    ) = ChannelEntity(
        id = id,
        sourceId = sourceId,
        categoryId = 10L,
        name = name,
        streamUrl = "http://test/$id",
        catchup = catchup,
        sortOrder = sortOrder,
    )

    private fun programme(
        id: Long,
        epgChannelId: String,
        title: String,
        startMs: Long,
        stopMs: Long,
        categories: String? = null,
    ) = EpgProgrammeEntity(
        id = id,
        sourceId = 1L,
        epgChannelId = epgChannelId,
        startMs = startMs,
        stopMs = stopMs,
        title = title,
        categories = categories,
    )

    @Test
    fun testGenreMapping_ruAndEn_caseInsensitive() {
        // Movies
        assertTrue(OnAirGenre.MOVIES in OnAirGenreClassifier.classify("Фильмы"))
        assertTrue(OnAirGenre.MOVIES in OnAirGenreClassifier.classify("кино"))
        assertTrue(OnAirGenre.MOVIES in OnAirGenreClassifier.classify("Художественный фильм"))
        assertTrue(OnAirGenre.MOVIES in OnAirGenreClassifier.classify("Action Movie"))
        assertTrue(OnAirGenre.MOVIES in OnAirGenreClassifier.classify("FEATURE FILM"))

        // Series
        assertTrue(OnAirGenre.SERIES in OnAirGenreClassifier.classify("Сериалы"))
        assertTrue(OnAirGenre.SERIES in OnAirGenreClassifier.classify("т/с"))
        assertTrue(OnAirGenre.SERIES in OnAirGenreClassifier.classify("Телесериал"))
        assertTrue(OnAirGenre.SERIES in OnAirGenreClassifier.classify("TV Series"))
        assertTrue(OnAirGenre.SERIES in OnAirGenreClassifier.classify("Sitcom"))

        // Sports
        assertTrue(OnAirGenre.SPORTS in OnAirGenreClassifier.classify("Спорт"))
        assertTrue(OnAirGenre.SPORTS in OnAirGenreClassifier.classify("Футбол. Лига Чемпионов"))
        assertTrue(OnAirGenre.SPORTS in OnAirGenreClassifier.classify("sports"))
        assertTrue(OnAirGenre.SPORTS in OnAirGenreClassifier.classify("Formula 1"))

        // News
        assertTrue(OnAirGenre.NEWS in OnAirGenreClassifier.classify("Новости"))
        assertTrue(OnAirGenre.NEWS in OnAirGenreClassifier.classify("Вести в 20:00"))
        assertTrue(OnAirGenre.NEWS in OnAirGenreClassifier.classify("Breaking News"))
        assertTrue(OnAirGenre.NEWS in OnAirGenreClassifier.classify("Current Affairs"))

        // Kids
        assertTrue(OnAirGenre.KIDS in OnAirGenreClassifier.classify("Детям"))
        assertTrue(OnAirGenre.KIDS in OnAirGenreClassifier.classify("Мультфильм"))
        assertTrue(OnAirGenre.KIDS in OnAirGenreClassifier.classify("Cartoons"))
        assertTrue(OnAirGenre.KIDS in OnAirGenreClassifier.classify("Animation"))

        // Music
        assertTrue(OnAirGenre.MUSIC in OnAirGenreClassifier.classify("Музыка"))
        assertTrue(OnAirGenre.MUSIC in OnAirGenreClassifier.classify("Концерт"))
        assertTrue(OnAirGenre.MUSIC in OnAirGenreClassifier.classify("Music Video"))
        assertTrue(OnAirGenre.MUSIC in OnAirGenreClassifier.classify("Rock"))

        // Educational
        assertTrue(OnAirGenre.EDUCATIONAL in OnAirGenreClassifier.classify("Познавательное"))
        assertTrue(OnAirGenre.EDUCATIONAL in OnAirGenreClassifier.classify("Документальный"))
        assertTrue(OnAirGenre.EDUCATIONAL in OnAirGenreClassifier.classify("Science Documentary"))
        assertTrue(OnAirGenre.EDUCATIONAL in OnAirGenreClassifier.classify("Discovery"))
    }

    @Test
    fun testUnknownCategory_fallsOnlyIntoAll() {
        val emptyGenres = OnAirGenreClassifier.classify(null)
        assertTrue(emptyGenres.isEmpty())
        assertTrue(OnAirGenreClassifier.matchesFilter(emptyGenres, OnAirGenre.ALL))
        assertFalse(OnAirGenreClassifier.matchesFilter(emptyGenres, OnAirGenre.MOVIES))

        val unknownGenres = OnAirGenreClassifier.classify("НеизвестнаяКатегория123")
        assertTrue(unknownGenres.isEmpty())
        assertTrue(OnAirGenreClassifier.matchesFilter(unknownGenres, OnAirGenre.ALL))
        assertFalse(OnAirGenreClassifier.matchesFilter(unknownGenres, OnAirGenre.SPORTS))
    }

    @Test
    fun testMultipleCategories_mapsMultipleGenres() {
        val genres = OnAirGenreClassifier.classify("Документальный фильм")
        assertTrue(OnAirGenre.EDUCATIONAL in genres)
        assertTrue(OnAirGenre.MOVIES in genres)

        val kidsSeries = OnAirGenreClassifier.classify("Мультсериал")
        assertTrue(OnAirGenre.KIDS in kidsSeries)
        assertTrue(OnAirGenre.SERIES in kidsSeries)
    }

    @Test
    fun testBestChannelVersionSelection_collapsesDuplicates() {
        val now = 1_000_000L
        val progSd = programme(1L, "ch1", "Movie Night", now - 600_000L, now + 1_800_000L, "Кино")
        val progHd = programme(2L, "ch1", "Movie Night", now - 600_000L, now + 1_800_000L, "Кино")
        val prog4k = programme(3L, "ch1", "Movie Night", now - 600_000L, now + 1_800_000L, "Кино")

        val itemSd = OnAirItem(
            channel = channel(101L, "Discovery SD"),
            programme = progSd,
            genres = setOf(OnAirGenre.MOVIES),
            progress = 0.25f,
            canWatchFromStart = true,
            startOffsetSec = 600,
        )
        val itemHd = OnAirItem(
            channel = channel(102L, "Discovery HD"),
            programme = progHd,
            genres = setOf(OnAirGenre.MOVIES),
            progress = 0.25f,
            canWatchFromStart = true,
            startOffsetSec = 600,
        )
        val item4k = OnAirItem(
            channel = channel(103L, "Discovery 4K"),
            programme = prog4k,
            genres = setOf(OnAirGenre.MOVIES),
            progress = 0.25f,
            canWatchFromStart = true,
            startOffsetSec = 600,
        )

        val collapsed = OnAirItem.collapseDuplicates(listOf(itemSd, itemHd, item4k), listOf(1L))
        assertEquals(1, collapsed.size)
        assertEquals("Discovery 4K", collapsed[0].channel.name)
    }

    @Test
    fun testCalculateProgress_boundsAndPrecision() {
        val start = 10_000L
        val stop = 20_000L

        // Before start -> 0.0f
        assertEquals(0.0f, OnAirItem.calculateProgress(start, stop, 5_000L), 0.001f)
        // At start -> 0.0f
        assertEquals(0.0f, OnAirItem.calculateProgress(start, stop, 10_000L), 0.001f)
        // At 25% -> 0.25f
        assertEquals(0.25f, OnAirItem.calculateProgress(start, stop, 12_500L), 0.001f)
        // In middle (50%) -> 0.5f
        assertEquals(0.5f, OnAirItem.calculateProgress(start, stop, 15_000L), 0.001f)
        // At stop -> 1.0f
        assertEquals(1.0f, OnAirItem.calculateProgress(start, stop, 20_000L), 0.001f)
        // Past stop -> 1.0f
        assertEquals(1.0f, OnAirItem.calculateProgress(start, stop, 25_000L), 0.001f)
    }

    @Test
    fun testCanWatchFromStart() {
        val start = 1_000_000L
        val stop = 2_000_000L

        // No catchup
        assertFalse(OnAirItem.canWatchFromStart(hasCatchup = false, startMs = start, stopMs = stop, nowMs = start + 300_000L))

        // Running less than 2 minutes (60 seconds)
        assertFalse(OnAirItem.canWatchFromStart(hasCatchup = true, startMs = start, stopMs = stop, nowMs = start + 60_000L))

        // Running exactly 2 minutes (120 seconds)
        assertTrue(OnAirItem.canWatchFromStart(hasCatchup = true, startMs = start, stopMs = stop, nowMs = start + 120_000L))

        // Running 10 minutes
        assertTrue(OnAirItem.canWatchFromStart(hasCatchup = true, startMs = start, stopMs = stop, nowMs = start + 600_000L))

        // Programme finished
        assertFalse(OnAirItem.canWatchFromStart(hasCatchup = true, startMs = start, stopMs = stop, nowMs = stop + 10_000L))
    }
}
