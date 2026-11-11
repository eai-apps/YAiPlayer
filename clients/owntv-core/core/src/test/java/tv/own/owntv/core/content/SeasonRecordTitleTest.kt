// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonRecordTitleTest {
    @Test fun operatorTitlesShareOneBase() {
        assertEquals("Остров сокровищ", SeasonRecordTitle.base("Остров сокровищ [S01] [05 из 06]"))
        assertEquals("Баба-яга / Яга", SeasonRecordTitle.base("Баба-яга / Яга [S01] [04 из 08] [ua] [ru]"))
        assertTrue(SeasonRecordTitle.sameShow("Король гольфа [S01]", "Король гольфа [S02] [ua] [ru]"))
        assertFalse(SeasonRecordTitle.sameShow("Король гольфа [S01]", "Король гольфа 2 [S01]"))
    }

    @Test fun seasonNumberAndRecordKind() {
        assertEquals(2, SeasonRecordTitle.season("Король гольфа [S02] [05 из 06]"))
        assertEquals(12, SeasonRecordTitle.season("Show [s12e03]"))
        assertTrue(SeasonRecordTitle.isSeasonRecord("Show [S01]"))
        assertFalse(SeasonRecordTitle.isSeasonRecord("Show (2024)"))
    }
}
