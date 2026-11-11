// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.epg

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity

@OptIn(ExperimentalCoroutinesApi::class)
class VisibleEpgPrefetcherTest {

    @Test
    fun `visibleWindow expands range by buffer and clamps to bounds`() {
        assertEquals(IntRange.EMPTY, VisibleEpgPrefetcher.visibleWindow(emptyList(), totalCount = 100))
        assertEquals(IntRange.EMPTY, VisibleEpgPrefetcher.visibleWindow(listOf(0, 1), totalCount = 0))

        // Visible 5..10 in 100 channels with buffer 3 -> 2..13
        val window = VisibleEpgPrefetcher.visibleWindow(listOf(5, 6, 7, 8, 9, 10), totalCount = 100, buffer = 3)
        assertEquals(2..13, window)

        // Near start: 0..2 in 50 -> 0..5
        val startWindow = VisibleEpgPrefetcher.visibleWindow(listOf(0, 1, 2), totalCount = 50, buffer = 3)
        assertEquals(0..5, startWindow)

        // Near end: 47..49 in 50 -> 44..49
        val endWindow = VisibleEpgPrefetcher.visibleWindow(listOf(47, 48, 49), totalCount = 50, buffer = 3)
        assertEquals(44..49, endWindow)
    }

    @Test
    fun `empty results TTL works correctly`() {
        val now = 1000_000L
        val ttl = 10 * 60 * 1000L // 10 minutes

        val mockReader = object {
            val emptyMap = java.util.concurrent.ConcurrentHashMap<Long, Long>()
            fun markEmpty(id: Long, t: Long) { emptyMap[id] = t + ttl }
            fun isEmpty(id: Long, t: Long): Boolean {
                val until = emptyMap[id] ?: return false
                return t < until
            }
            fun clear() { emptyMap.clear() }
        }

        mockReader.markEmpty(42L, now)
        assertTrue(mockReader.isEmpty(42L, now + 5 * 60 * 1000L)) // 5 min later -> still empty
        assertFalse(mockReader.isEmpty(42L, now + 11 * 60 * 1000L)) // 11 min later -> expired

        mockReader.markEmpty(42L, now)
        mockReader.clear()
        assertFalse(mockReader.isEmpty(42L, now + 1_000L)) // cleared -> not empty
    }
}
