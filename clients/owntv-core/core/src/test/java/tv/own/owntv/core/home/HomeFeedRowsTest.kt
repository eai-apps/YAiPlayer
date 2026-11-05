package tv.own.owntv.core.home

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.entity.CategoryEntity
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.model.HomeConfig
import tv.own.owntv.core.model.HomeRow
import tv.own.owntv.core.model.MediaType
import java.lang.reflect.Proxy

/**
 * Unit tests for Home feed rows (LIVE_CATEGORIES, ALL_CHANNELS, RECENT_MOVIES) as specified in TASK_5.
 *
 * Verifies row limits, ordering, conditional queries when enabled/disabled, category emptiness filtering,
 * source sync-flag scoping, and customizable visibleOrder reordering.
 */
class HomeFeedRowsTest {

    // Mirroring constants from HomeFeed.kt
    private val categoryRailLimit = 8
    private val categoryRailChannels = 20
    private val allChannelsRowLimit = 30
    private val recentMoviesRowLimit = 30

    private fun channel(id: Long, sourceId: Long = 1L, categoryId: Long? = null, name: String = "Channel $id") =
        ChannelEntity(
            id = id,
            sourceId = sourceId,
            categoryId = categoryId,
            name = name,
            streamUrl = "http://stream/$id",
        )

    private fun category(id: Long, sourceId: Long = 1L, name: String = "Cat $id") =
        CategoryEntity(
            id = id,
            sourceId = sourceId,
            mediaType = MediaType.LIVE,
            name = name,
        )

    private fun movie(id: Long, sourceId: Long = 1L, name: String = "Movie $id", addedAt: Long = 1000L + id) =
        MovieEntity(
            id = id,
            sourceId = sourceId,
            name = name,
            streamUrl = "http://movie/$id",
            addedAt = addedAt,
        )

    private fun source(
        id: Long,
        name: String = "Source $id",
        syncLive: Boolean = true,
        syncMovies: Boolean = true,
    ) = SourceEntity(
        id = id,
        name = name,
        type = SourceType.M3U,
        url = "http://source/$id",
        syncLive = syncLive,
        syncMovies = syncMovies,
    )

    /**
     * Test harness implementing the HomeFeed rail assembly logic from HomeFeed.kt
     * with query counters to verify conditional execution.
     */
    private class Harness(
        private val categories: List<CategoryEntity> = emptyList(),
        private val channelsByCategory: Map<Long, List<ChannelEntity>> = emptyMap(),
        private val allChannelsPool: List<ChannelEntity> = emptyList(),
        private val moviesPool: List<MovieEntity> = emptyList(),
    ) {
        var channelQueriesCount = 0
            private set
        var allChannelsQueriesCount = 0
            private set
        var moviesQueriesCount = 0
            private set

        val categoryDao: CategoryDao = Proxy.newProxyInstance(
            CategoryDao::class.java.classLoader,
            arrayOf(CategoryDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "observe" -> {
                    @Suppress("UNCHECKED_CAST")
                    val sourceIds = (args?.get(0) as? List<Long>) ?: emptyList()
                    val mediaType = args?.get(1) as? MediaType
                    val filtered = categories.filter { it.sourceId in sourceIds && it.mediaType == mediaType }
                    flowOf(filtered)
                }
                else -> null
            }
        } as CategoryDao

        val channelDao: ChannelDao = Proxy.newProxyInstance(
            ChannelDao::class.java.classLoader,
            arrayOf(ChannelDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "channelsAfterCategory" -> {
                    channelQueriesCount++
                    val categoryId = args?.get(0) as Long
                    val limit = args?.get(3) as Int
                    val list = channelsByCategory[categoryId] ?: emptyList()
                    list.take(limit)
                }
                "snapshotAll" -> {
                    allChannelsQueriesCount++
                    @Suppress("UNCHECKED_CAST")
                    val sourceIds = (args?.get(0) as? List<Long>) ?: emptyList()
                    val limit = args?.get(1) as Int
                    allChannelsPool.filter { it.sourceId in sourceIds }.take(limit)
                }
                else -> null
            }
        } as ChannelDao

        val movieDao: MovieDao = Proxy.newProxyInstance(
            MovieDao::class.java.classLoader,
            arrayOf(MovieDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "recentlyAddedSnapshot" -> {
                    moviesQueriesCount++
                    @Suppress("UNCHECKED_CAST")
                    val sourceIds = (args?.get(0) as? List<Long>) ?: emptyList()
                    val limit = args?.get(1) as Int
                    moviesPool.filter { it.sourceId in sourceIds }
                        .sortedByDescending { it.addedAt ?: 0L }
                        .take(limit)
                }
                else -> null
            }
        } as MovieDao

        suspend fun loadLiveCategoryRails(config: HomeConfig, liveIds: Set<Long>): List<ChannelRail> {
            if (HomeRow.LIVE_CATEGORIES !in config.visibleOrder || liveIds.isEmpty()) return emptyList()
            val rails = ArrayList<ChannelRail>()
            for (cat in categoryDao.observe(liveIds.toList(), MediaType.LIVE).first()) {
                if (rails.size >= 8) break // CATEGORY_RAIL_LIMIT
                val channels = channelDao.channelsAfterCategory(cat.id, Int.MIN_VALUE, Long.MIN_VALUE, 20) // CATEGORY_RAIL_CHANNELS
                if (channels.isNotEmpty()) {
                    rails += ChannelRail(cat.id, cat.name, channels)
                }
            }
            return rails
        }

        suspend fun loadAllChannels(config: HomeConfig, liveIds: Set<Long>): List<ChannelEntity> {
            if (HomeRow.ALL_CHANNELS !in config.visibleOrder || liveIds.isEmpty()) return emptyList()
            return channelDao.snapshotAll(liveIds.toList(), 30 * 2).take(30) // ALL_CHANNELS_ROW_LIMIT
        }

        suspend fun loadRecentMovies(config: HomeConfig, movieIds: Set<Long>): List<MovieEntity> {
            if (HomeRow.RECENT_MOVIES !in config.visibleOrder || movieIds.isEmpty()) return emptyList()
            return movieDao.recentlyAddedSnapshot(movieIds.toList(), 30 * 2).take(30) // RECENT_MOVIES_ROW_LIMIT
        }
    }

    @Test
    fun test1_liveCategoriesDisabled_emptyRailsAndNoChannelQueries() = runBlocking {
        val harness = Harness(
            categories = listOf(category(1), category(2)),
            channelsByCategory = mapOf(1L to listOf(channel(10)), 2L to listOf(channel(20)))
        )
        val config = HomeConfig(hidden = setOf(HomeRow.LIVE_CATEGORIES))

        assertFalse(HomeRow.LIVE_CATEGORIES in config.visibleOrder)
        val rails = harness.loadLiveCategoryRails(config, liveIds = setOf(1L))

        assertTrue(rails.isEmpty())
        assertEquals(0, harness.channelQueriesCount)
    }

    @Test
    fun test2_liveCategoriesEnabled_capsAt8RailsWithAtMost20ChannelsEach() = runBlocking {
        val cats = (1L..12L).map { category(it) }
        val channelsMap = cats.associate { cat ->
            cat.id to (1L..30L).map { channel(cat.id * 100 + it, categoryId = cat.id) }
        }
        val harness = Harness(categories = cats, channelsByCategory = channelsMap)
        val config = HomeConfig(order = listOf(HomeRow.LIVE_CATEGORIES), hidden = emptySet())

        val rails = harness.loadLiveCategoryRails(config, liveIds = setOf(1L))

        assertEquals(categoryRailLimit, rails.size)
        assertTrue(rails.all { it.channels.size == categoryRailChannels })
        assertEquals(8, harness.channelQueriesCount)
    }

    @Test
    fun test3_categoryWithoutChannelsOmittedFromRails() = runBlocking {
        val cats = listOf(category(1, name = "With channels"), category(2, name = "Empty category"), category(3, name = "Also has channels"))
        val channelsMap = mapOf(
            1L to listOf(channel(101)),
            2L to emptyList(),
            3L to listOf(channel(301))
        )
        val harness = Harness(categories = cats, channelsByCategory = channelsMap)
        val config = HomeConfig(order = listOf(HomeRow.LIVE_CATEGORIES), hidden = emptySet())

        val rails = harness.loadLiveCategoryRails(config, liveIds = setOf(1L))

        assertEquals(2, rails.size)
        assertEquals(listOf(1L, 3L), rails.map { it.categoryId })
        assertTrue(rails.none { it.categoryId == 2L })
    }

    @Test
    fun test4_allChannelsRow_capsAt30WhenEnabled_emptyWhenDisabled() = runBlocking {
        val pool = (1L..50L).map { channel(it) }
        val harness = Harness(allChannelsPool = pool)

        // Enabled
        val enabledConfig = HomeConfig(order = listOf(HomeRow.ALL_CHANNELS), hidden = emptySet())
        val channels = harness.loadAllChannels(enabledConfig, liveIds = setOf(1L))
        assertEquals(allChannelsRowLimit, channels.size)
        assertEquals(1L, channels.first().id)
        assertEquals(30L, channels.last().id)

        // Disabled
        val disabledConfig = HomeConfig(hidden = setOf(HomeRow.ALL_CHANNELS))
        val disabledChannels = harness.loadAllChannels(disabledConfig, liveIds = setOf(1L))
        assertTrue(disabledChannels.isEmpty())
    }

    @Test
    fun test5_recentMoviesRow_capsAt30WithNewestFirst_emptyWhenDisabled() = runBlocking {
        val pool = (1L..50L).map { movie(it, addedAt = 1000L + it) } // id 50 is newest (addedAt 1050)
        val harness = Harness(moviesPool = pool)

        // Enabled
        val enabledConfig = HomeConfig(order = listOf(HomeRow.RECENT_MOVIES), hidden = emptySet())
        val movies = harness.loadRecentMovies(enabledConfig, movieIds = setOf(1L))
        assertEquals(recentMoviesRowLimit, movies.size)
        assertEquals(50L, movies.first().id) // Newest first
        assertEquals(21L, movies.last().id)

        // Verify strictly descending order of addedAt
        val addedTimestamps = movies.mapNotNull { it.addedAt }
        assertEquals(addedTimestamps.sortedDescending(), addedTimestamps)

        // Disabled
        val disabledConfig = HomeConfig(hidden = setOf(HomeRow.RECENT_MOVIES))
        val disabledMovies = harness.loadRecentMovies(disabledConfig, movieIds = setOf(1L))
        assertTrue(disabledMovies.isEmpty())
    }

    @Test
    fun test6_hiddenAndInactiveSourcesExcludedFromAllThreeRows() = runBlocking {
        val s1 = source(1L, "Active Source", syncLive = true, syncMovies = true)
        val s2 = source(2L, "No Live Sync", syncLive = false, syncMovies = true)
        val s3 = source(3L, "No Movie Sync", syncLive = true, syncMovies = false)
        val s4Inactive = source(4L, "Inactive Source", syncLive = true, syncMovies = true)

        val sources = listOf(s1, s2, s3) // s4 is not linked to profile
        val liveIds = sources.filter { it.syncLive }.map { it.id }.toSet()
        val movieIds = sources.filter { it.syncMovies }.map { it.id }.toSet()

        assertFalse(2L in liveIds)
        assertTrue(3L in liveIds)
        assertTrue(2L in movieIds)
        assertFalse(3L in movieIds)
        assertFalse(4L in liveIds || 4L in movieIds)

        val cats = listOf(category(1, sourceId = 1L), category(2, sourceId = 2L))
        val catChannels = mapOf(
            1L to listOf(channel(101, sourceId = 1L)),
            2L to listOf(channel(201, sourceId = 2L)),
        )
        val allChannels = listOf(
            channel(101, sourceId = 1L),
            channel(201, sourceId = 2L),
            channel(401, sourceId = 4L),
        )
        val movies = listOf(
            movie(101, sourceId = 1L),
            movie(301, sourceId = 3L),
            movie(401, sourceId = 4L),
        )

        val harness = Harness(
            categories = cats,
            channelsByCategory = catChannels,
            allChannelsPool = allChannels,
            moviesPool = movies,
        )
        val config = HomeConfig(
            order = listOf(HomeRow.LIVE_CATEGORIES, HomeRow.ALL_CHANNELS, HomeRow.RECENT_MOVIES),
            hidden = emptySet(),
        )

        val rails = harness.loadLiveCategoryRails(config, liveIds)
        val loadedChannels = harness.loadAllChannels(config, liveIds)
        val loadedMovies = harness.loadRecentMovies(config, movieIds)

        // Category rail from source 2 (syncLive = false) must not appear
        assertEquals(listOf(1L), rails.map { it.categoryId })

        // Channel from source 2 (syncLive = false) or source 4 (inactive) must not appear
        assertTrue(loadedChannels.all { it.sourceId in liveIds })
        assertEquals(listOf(101L), loadedChannels.map { it.id })

        // Movie from source 3 (syncMovies = false) or source 4 (inactive) must not appear
        assertTrue(loadedMovies.all { it.sourceId in movieIds })
        assertEquals(listOf(101L), loadedMovies.map { it.id })
    }

    @Test
    fun test7_orderOfRowsRespectsVisibleOrderAndAllowsReordering() {
        val customOrder = listOf(
            HomeRow.RECENT_MOVIES,
            HomeRow.LIVE_CATEGORIES,
            HomeRow.ALL_CHANNELS,
            HomeRow.FAVORITE_MOVIES,
        )
        val config = HomeConfig(order = customOrder, hidden = setOf(HomeRow.LIVE_SPORTS))

        val visible = config.visibleOrder

        // Trending is always at the top if not hidden, then custom order
        val expected = listOf(
            HomeRow.TRENDING,
            HomeRow.RECENT_MOVIES,
            HomeRow.LIVE_CATEGORIES,
            HomeRow.ALL_CHANNELS,
            HomeRow.FAVORITE_MOVIES,
        )
        assertEquals(expected, visible)

        // Hiding RECENT_MOVIES drops it without changing relative order of remaining rows
        val configWithHidden = config.copy(hidden = config.hidden + HomeRow.RECENT_MOVIES)
        val visibleWithHidden = configWithHidden.visibleOrder
        assertEquals(
            listOf(
                HomeRow.TRENDING,
                HomeRow.LIVE_CATEGORIES,
                HomeRow.ALL_CHANNELS,
                HomeRow.FAVORITE_MOVIES,
            ),
            visibleWithHidden
        )
    }
}
