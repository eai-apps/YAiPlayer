// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.stalker

import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.content.ProviderMediaDetails
import tv.own.owntv.core.content.providerSearchText
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.live.previewGuideWindow
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.sync.QuickSyncAction
import tv.own.owntv.core.sync.SyncContentTypes
import tv.own.owntv.core.sync.selectiveSyncPlan
import tv.own.owntv.core.sync.dispatchSelectiveSync

class OperatorFixesTest {
    @Test fun eachSyncActionDispatchesOnlyItsRequestedOperations() = runBlocking {
        val source = SourceEntity(name = "Portal", type = SourceType.STALKER, url = "http://portal/c/")
        val expected = mapOf(
            QuickSyncAction.CHANNELS to listOf("live"), QuickSyncAction.EPG to listOf("epg"),
            QuickSyncAction.CHANNELS_AND_EPG to listOf("live", "epg"),
            QuickSyncAction.VOD to listOf("movies", "series"),
            QuickSyncAction.ALL to listOf("live", "movies", "series", "epg"),
        )
        for ((action, operations) in expected) {
            val calls = mutableListOf<String>()
            dispatchSelectiveSync(selectiveSyncPlan(action, source), catalog = { requested ->
                val effective = requested.effectiveFor(source)
                if (effective.live) calls += "live"
                if (effective.movies) calls += "movies"
                if (effective.series) calls += "series"
            }, epg = { calls += "epg" })
            assertEquals(action.name, operations, calls)
        }
    }

    @Test fun stalkerMetadataKeepsOriginalTitleAndAllProviderFields() {
        val details = ProviderMediaDetails.from(mapOf("o_name" to "Original", "genres_str" to "Драма",
            "country" to "Германия", "director" to "Режиссёр", "actors" to "Актёр",
            "year" to "2024", "time" to "95"))
        assertEquals(ProviderMediaDetails("Original", "Драма", "Германия", "Режиссёр", "Актёр", 2024, 5700), details)
    }

    @Test fun xtreamAliasesAndDurationAreParsedWithoutInventingMissingFields() {
        val details = ProviderMediaDetails.from(mapOf("original_title" to "Original", "genre" to "Comedy",
            "cast" to "Actor", "releasedate" to "2025-01-02", "duration" to "01:32:05"))
        assertEquals(5525, details.durationSecs)
        assertEquals(2025, details.year)
        assertNull(details.country)
        assertNull(details.director)
        assertEquals(ProviderMediaDetails(), ProviderMediaDetails.from(emptyMap()))
    }

    @Test fun catalogueSearchNormalizesRussianLatinAndYo() {
        val text = providerSearchText("Фильм", "Original", "Драма", 2024, "Германия", "РЕЖИССЁР", "Актёр")
        for (query in listOf("фильм", "ORIGINAL", "ДРАМА", "2024", "ГЕРМАНИЯ", "Режиссер", "АКТЕР")) {
            assertTrue(query, text.contains(providerSearchText(query)))
        }
    }

    @Test fun invalidProviderValuesDoNotHideValidFallbackFields() {
        val details = ProviderMediaDetails.from(mapOf("duration_secs" to "0", "time" to "01:30:00",
            "year" to "0", "releasedate" to "2024-05-01"))
        assertEquals(5400, details.durationSecs)
        assertEquals(2024, details.year)
        assertNull(ProviderMediaDetails.from(mapOf("duration" to "-1:61:99")).durationSecs)
    }

    @Test fun previewIncludesExactlyTheNext24HoursAndStoredSourceWins() {
        val hour = 3_600_000L
        val now = 1_800_000_000_000L
        fun row(index: Int, title: String) = EpgProgrammeEntity(sourceId = 1, epgChannelId = "42",
            title = title, startMs = now + index * hour, stopMs = now + (index + 1) * hour)
        val provider = (0 until 30).map { row(it, "Provider $it") }
        val stored = listOf(row(0, "User source"))
        val window = previewGuideWindow(stored, provider, now, now + 24 * hour)
        assertEquals(24, window.size)
        assertEquals("User source", window.first().title)
        assertTrue(window.all { it.startMs < now + 24 * hour })
    }

    @Test fun portalDayRequestsStartInThePastAndIncludeYesterday() = runBlocking {
        val requested = java.util.Collections.synchronizedList(mutableListOf<String>())
        val client = object : StalkerClient(OkHttpClient()) {
            override suspend fun getArchiveDay(apiBase: String, mac: String, token: String, userAgent: String?,
                channelId: String, date: String, page: Int): Page<ArchiveEntry> {
                requested += date
                val at = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)!!.time + 12 * 3_600_000L
                return Page(1, 1, listOf(ArchiveEntry("$channelId-$date", at, at + 3_600_000, true, "Programme")))
            }
        }
        val auth = object : StalkerAuthManager(client) {
            override suspend fun <T> withAuthRetry(creds: StalkerCredentials,
                block: suspend (StalkerSession) -> T): T = block(StalkerSession(
                    "http://portal/portal.php", "test-token", Long.MAX_VALUE, emptyMap()))
        }
        val source = SourceEntity(name = "Portal", type = SourceType.STALKER,
            url = "http://portal/c/", mac = "00:1A:79:AA:BB:CC")
        val now = System.currentTimeMillis()
        val rows = mutableListOf<StalkerEpgLoader.Row>()
        StalkerEpgLoader(auth, client).loadDayTables(source, listOf("42" to 3),
            now - 4 * 86_400_000L, now + 2 * 86_400_000L) { rows += it }
        assertEquals(5, requested.distinct().size)
        assertTrue(rows.any { it.startMs < now - 86_400_000L })
        assertTrue(rows.all { it.epgChannelId == "42" })
    }
}
