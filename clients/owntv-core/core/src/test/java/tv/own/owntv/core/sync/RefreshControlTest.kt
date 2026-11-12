// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RefreshControlTest {
    @Test fun `nothing running`() {
        assertNull(RefreshControl.describe(null, emptyList(), emptyList()))
    }

    @Test fun `queue step and the source on it`() {
        val r = RefreshControl.describe(RefreshQueueTracker.Progress(2, 3), emptyList(), listOf("эдем"))!!
        assertEquals(2, r.step)
        assertEquals(3, r.total)
        assertEquals("эдем", r.name)
        assertEquals(true, r.guide)
    }

    @Test fun `manual refresh outside the queue counts what runs, catalog first`() {
        val r = RefreshControl.describe(null, listOf("Портал"), listOf("Гид"))!!
        assertEquals(1, r.step)
        assertEquals(2, r.total)
        assertEquals("Портал", r.name)
        assertEquals(false, r.guide)
    }
}
