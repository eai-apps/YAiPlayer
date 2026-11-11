// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.sync

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.sync.work.RefreshQueueScheduler

class RefreshQueueTest {
    // The owner's start-up from 11.11: his portal (active), another profile's portal, the «эдем» feed added by
    // hand, the portal's own guide, the other portal's guide, and the built-in guide — all stale at once.
    private val myPortal = PlaylistInfo(1, active = true, due = true, baseItemCount = 6000, guideUrls = listOf("stalker://1"))
    private val otherPortal = PlaylistInfo(2, active = false, due = true, baseItemCount = 900, guideUrls = listOf("stalker://2"))
    private val feeds = listOf(
        GuideFeedInfo(-1, "http://epg.example/eden.xml", builtin = false, due = true, baseProgrammes = 6500),
        GuideFeedInfo(-2, "stalker://1", builtin = false, due = true, baseProgrammes = 0),
        GuideFeedInfo(-3, "stalker://2", builtin = false, due = true, baseProgrammes = 0),
        GuideFeedInfo(-9, "builtin://1", builtin = true, due = true, baseProgrammes = 0),
    )

    @Test fun `startup runs the active portal, its guide, unclaimed feeds, then the built-in guide - one by one`() {
        val jobs = RefreshQueuePlanner.plan(listOf(myPortal, otherPortal), feeds)
        assertEquals(
            listOf(RefreshJob.Catalog(1, 6000), RefreshJob.Guide(-2, 0), RefreshJob.Guide(-1, 6500), RefreshJob.Guide(-9, 0)),
            jobs,
        )
    }

    @Test fun `other profiles wait for the background refresh, after the active one`() {
        val jobs = RefreshQueuePlanner.plan(listOf(myPortal, otherPortal), feeds, includeOtherProfiles = true)
        assertEquals(listOf<Long>(1, -2, -1, 2, -3, -9), jobs.map { it.id })
        assertTrue(jobs.last() is RefreshJob.Guide)
    }

    @Test fun `nothing due means no queue, and a fresh source is not repeated`() {
        val fresh = myPortal.copy(due = false)
        val jobs = RefreshQueuePlanner.plan(listOf(fresh), feeds.map { it.copy(due = false) })
        assertTrue(jobs.isEmpty())
        val twice = RefreshQueuePlanner.plan(listOf(myPortal, myPortal), feeds.take(2) + feeds[1])
        assertEquals(1, twice.count { it is RefreshJob.Catalog })
        assertEquals(1, twice.count { it.id == -2L })
    }

    @Test fun `queue survives the trip through WorkManager input data`() {
        val jobs = RefreshQueuePlanner.plan(listOf(myPortal), feeds)
        val decoded = RefreshQueueScheduler.decode(
            jobs.map { if (it is RefreshJob.Catalog) 0 else 1 }.toIntArray(),
            jobs.map { it.id }.toLongArray(),
            jobs.map { if (it is RefreshJob.Catalog) it.baseItemCount else (it as RefreshJob.Guide).baseProgrammes }.toIntArray(),
        )
        assertEquals(jobs, decoded)
        assertTrue(RefreshQueueScheduler.decode(intArrayOf(0), longArrayOf(), intArrayOf(0)).isEmpty())
        assertTrue(RefreshQueueScheduler.decode(null, null, null).isEmpty())
    }

    @Test fun `one summary for the pill`() {
        val t = RefreshQueueTracker()
        assertNull(t.progress.value)
        t.update(2, 4)
        assertEquals(RefreshQueueTracker.Progress(2, 4), t.progress.value)
        t.update(9, 4)
        assertEquals(4, t.progress.value!!.current)
        t.finished()
        assertNull(t.progress.value)
        t.update(1, 0)
        assertNull(t.progress.value)
    }

    @Test fun `a source synced yesterday evening is refreshed on the first start today`() {
        val zone = ZoneId.of("Europe/Berlin")
        val yesterday2300 = java.time.ZonedDateTime.of(2026, 11, 10, 23, 0, 0, 0, zone).toInstant().toEpochMilli()
        val today0700 = java.time.ZonedDateTime.of(2026, 11, 11, 7, 0, 0, 0, zone).toInstant().toEpochMilli()
        val today0100 = java.time.ZonedDateTime.of(2026, 11, 11, 1, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertFalse(syncedToday(yesterday2300, today0700, zone))
        assertTrue(syncedToday(today0100, today0700, zone))
        assertFalse(syncedToday(null, today0700, zone))
        // Only 8 hours old, so the 24-hour threshold alone would have skipped it.
        assertFalse(isStale(yesterday2300, today0700, 24))
    }

    @Test fun `guide percentage never claims to be done early`() {
        val t = EpgActivityTracker()
        t.fraction(-2, 0.5f)
        assertTrue(t.active.value.isEmpty()) // not started: ignored
        t.started(-2, "Мой портал")
        t.fraction(-2, 0.6f)
        assertEquals(60, t.active.value[-2L]!!.percent)
        t.fraction(-2, 1.0f)
        assertEquals(99, t.active.value[-2L]!!.percent)
        t.finished(-2)
        assertTrue(t.active.value.isEmpty())
    }
}
