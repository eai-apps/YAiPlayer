// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.stalker

import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.epg.EpgGapPlan
import tv.own.owntv.core.sync.work.nextStalkerCatchup

class EpgCorePlanTest {
    @Test fun userOrderStaysAheadOfAllBuiltins() {
        assertEquals(listOf(3L, 1L, 2L, 4L), EpgGapPlan.ordered(listOf(2L to true, 3L to false, 1L to false, 4L to true)))
    }
    @Test fun onlyMissingChannelsReachLaterFeeds() = runBlocking {
        val have = hashSetOf("own")
        val visited = ArrayList<Pair<Int, List<String>>>()
        EpgGapPlan.visitFeeds(listOf(1, 2, 3),
            { listOf("own", "a", "b").filter { EpgGapPlan.needsGuide(it, have) } }) { feed, gaps ->
            visited.add(feed to gaps)
            have.add(if (feed == 1) "a" else "b")
        }
        assertEquals(listOf(1 to listOf("a", "b"), 2 to listOf("b")), visited)
    }
    @Test fun emptyFeedIsNotRepeatedWithinPass() = runBlocking {
        val visited = ArrayList<Int>()
        EpgGapPlan.visitFeeds(listOf(1, 2, 3), { listOf("empty") }) { feed, _ -> visited.add(feed) }
        assertEquals(listOf(1, 2, 3), visited)
    }
    @Test fun noDownloadsWhenAllChannelsHaveGuide() = runBlocking {
        EpgGapPlan.visitFeeds(listOf(1, 2), { emptyList<String>() }) { _, _ -> fail("Unexpected download") }
        assertFalse(EpgGapPlan.needsGuide("manual", setOf("manual")))
        assertTrue(EpgGapPlan.needsGuide(null, setOf("manual")))
    }
    @Test fun archiveGapDoesNotSelectCoveredFutureAndUsesExclusiveEnd() {
        val archive = listOf(0L to 100L)
        assertTrue(EpgGapPlan.inWindow(10, 20, archive))
        assertFalse(EpgGapPlan.inWindow(100, 120, archive))
        assertFalse(EpgGapPlan.inWindow(-20, 0, archive))
        assertFalse(EpgGapPlan.inWindow(10, 20, emptyList()))
    }
    private fun at(value: String) = ZonedDateTime.parse(value + "[Europe/Berlin]")
    @Test fun threeWeeklySlotsAndNextWeek() {
        var time = at("2026-11-09T08:59:00+01:00")
        val expected = listOf("2026-11-09T09:00:00+01:00", "2026-11-09T15:00:00+01:00",
            "2026-11-10T09:00:00+01:00", "2026-11-16T09:00:00+01:00")
        for (slot in expected) {
            time = nextStalkerCatchup(time)
            assertEquals(at(slot), time)
        }
    }
    @Test fun respectsLocalTimeAcrossDaylightSaving() {
        assertEquals(at("2027-03-29T09:00:00+02:00"), nextStalkerCatchup(at("2027-03-27T23:30:00+01:00")))
        assertEquals(at("2027-11-01T09:00:00+01:00"), nextStalkerCatchup(at("2027-10-30T23:30:00+02:00")))
    }
}
