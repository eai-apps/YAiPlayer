// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.sync

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.core.database.entity.CategoryEntity
import tv.own.owntv.core.model.MediaType

class HiddenCategorySyncTest {
    @Test fun `frozen only when every profile of the source hides it`() {
        val a = setOf("7:10", "7:11", "8:10", "custom:x")
        val b = setOf("7:10", "7:12")
        assertEquals(setOf("10"), HiddenCategorySync.frozenTails(7, listOf(a, b)))
        assertEquals(setOf("10", "11"), HiddenCategorySync.frozenTails(7, listOf(a)))
        assertEquals(emptySet<String>(), HiddenCategorySync.frozenTails(7, emptyList()))
        assertEquals(emptySet<String>(), HiddenCategorySync.frozenTails(9, listOf(a)))
    }

    @Test fun `tails map to category rows by remote id, or by name without one`() {
        val cats = listOf(
            CategoryEntity(id = 1, sourceId = 7, mediaType = MediaType.LIVE, name = "News", remoteId = "10"),
            CategoryEntity(id = 2, sourceId = 7, mediaType = MediaType.LIVE, name = "Sport", remoteId = "11"),
            CategoryEntity(id = 3, sourceId = 7, mediaType = MediaType.LIVE, name = "Kids", remoteId = null),
        )
        assertEquals(setOf(1L, 3L), HiddenCategorySync.frozenIds(cats, setOf("10", "Kids")))
        assertEquals(emptySet<Long>(), HiddenCategorySync.frozenIds(cats, emptySet()))
    }
}
