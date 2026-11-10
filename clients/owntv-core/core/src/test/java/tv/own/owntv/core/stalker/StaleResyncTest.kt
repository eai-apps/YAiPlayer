// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.

package tv.own.owntv.core.stalker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.database.entity.CategoryEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.sync.isStale

class StaleResyncTest {

    private val hourMs = 3_600_000L
    private val nowMs = 1_700_000_000_000L

    @Test
    fun isStaleReturnsTrueWhenNoData() {
        // null or non-positive timestamps mean no sync has succeeded yet
        assertTrue(isStale(lastSyncAt = null, nowMs = nowMs, thresholdHours = 24))
        assertTrue(isStale(lastSyncAt = 0L, nowMs = nowMs, thresholdHours = 24))
        assertTrue(isStale(lastSyncAt = -1L, nowMs = nowMs, thresholdHours = 24))
    }

    @Test
    fun isStaleReturnsFalseWhenFresh() {
        // Recent sync well within threshold
        val recent1Hour = nowMs - (1 * hourMs)
        assertFalse(isStale(lastSyncAt = recent1Hour, nowMs = nowMs, thresholdHours = 24))

        val recent23Hours = nowMs - (23 * hourMs)
        assertFalse(isStale(lastSyncAt = recent23Hours, nowMs = nowMs, thresholdHours = 24))
    }

    @Test
    fun isStaleReturnsTrueWhenExactlyAtThreshold() {
        val exact24Hours = nowMs - (24 * hourMs)
        assertTrue(isStale(lastSyncAt = exact24Hours, nowMs = nowMs, thresholdHours = 24))

        val exact12Hours = nowMs - (12 * hourMs)
        assertTrue(isStale(lastSyncAt = exact12Hours, nowMs = nowMs, thresholdHours = 12))
    }

    @Test
    fun isStaleReturnsTrueWhenOlderThanThreshold() {
        val old48Hours = nowMs - (48 * hourMs)
        assertTrue(isStale(lastSyncAt = old48Hours, nowMs = nowMs, thresholdHours = 24))

        val old72Hours = nowMs - (72 * hourMs)
        assertTrue(isStale(lastSyncAt = old72Hours, nowMs = nowMs, thresholdHours = 24))
    }

    @Test
    fun isStaleThresholdVariants() {
        // 12 hours option
        assertFalse(isStale(lastSyncAt = nowMs - (11 * hourMs), nowMs = nowMs, thresholdHours = 12))
        assertTrue(isStale(lastSyncAt = nowMs - (12 * hourMs), nowMs = nowMs, thresholdHours = 12))

        // 48 hours option
        assertFalse(isStale(lastSyncAt = nowMs - (47 * hourMs), nowMs = nowMs, thresholdHours = 48))
        assertTrue(isStale(lastSyncAt = nowMs - (48 * hourMs), nowMs = nowMs, thresholdHours = 48))

        // 72 hours option
        assertFalse(isStale(lastSyncAt = nowMs - (71 * hourMs), nowMs = nowMs, thresholdHours = 72))
        assertTrue(isStale(lastSyncAt = nowMs - (72 * hourMs), nowMs = nowMs, thresholdHours = 72))
    }

    @Test
    fun settingsDefaultsAreCorrect() {
        assertTrue(SettingsRepository.DEFAULT_BACKGROUND_RESYNC_ENABLED)
        assertEquals(24, SettingsRepository.DEFAULT_BACKGROUND_RESYNC_HOURS)
        assertEquals(listOf(12, 24, 48, 72), SettingsRepository.BACKGROUND_RESYNC_HOUR_OPTIONS)
    }

    @Test
    fun hiddenCategoriesArePreservedByCustomizationKey() {
        val cat = CategoryEntity(
            id = 42L,
            sourceId = 1L,
            mediaType = MediaType.LIVE,
            name = "Cinema",
            remoteId = "cat_cinema",
        )
        val key = CustomizeKeys.category(cat)
        assertEquals("1:cat_cinema", key)

        val custom = SectionCustomizations(hiddenCategories = setOf(key))
        assertTrue(key in custom.hiddenCategories)

        // Simulating a re-sync with a new database row id:
        val resyncedCat = cat.copy(id = 999L)
        val resyncedKey = CustomizeKeys.category(resyncedCat)
        assertEquals("1:cat_cinema", resyncedKey)
        assertTrue("Category remains hidden after resync because key depends on sourceId and remoteId, not Room row id",
            resyncedKey in custom.hiddenCategories)
    }
}
