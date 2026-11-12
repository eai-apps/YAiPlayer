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

    @Test fun subtitledSeasonsBelongToTheShow() {
        assertTrue(SeasonRecordTitle.sameShowLoose("Американская история ужасов [S13]", "Американская история ужасов: Отель [S05] [ru]"))
        assertTrue(SeasonRecordTitle.sameShowLoose("Американская история ужасов — Культ [S07]", "Американская история ужасов [S01]"))
        assertFalse(SeasonRecordTitle.sameShowLoose("Баба [S01]", "Баба-яга / Яга [S01]"))
        assertFalse(SeasonRecordTitle.sameShowLoose("Король гольфа [S01]", "Король гольфа 2 [S01]"))
    }

    @Test fun bilingualAndPlainTitlesAreOneShow() {
        // Owner 12.11: seasons 1–4, 9, 12, 13 had a bilingual title, 5–8, 10, 11 the plain one, so there were two cards.
        val bi = "Американская история ужасов / Американська історія жаху"
        assertTrue(SeasonRecordTitle.sameShowLoose("$bi [S13]", "Американская история ужасов [S05]"))
        assertTrue(SeasonRecordTitle.sameShowLoose("Американская история ужасов [S05]", "$bi [S01]"))
        assertFalse(SeasonRecordTitle.sameShowLoose("Мужчина / Женщина [S01]", "Мужик [S01]"))
        val cards = SeasonRecordTitle.cards(
            listOf(1L to "$bi [S13]", 2L to "$bi [S01]", 3L to "Американская история ужасов [S05]", 4L to "Американская история ужасов [S06]"),
        )
        assertEquals(setOf(2L, 3L, 4L), cards.hidden)
        assertEquals("Американская история ужасов / Американська історія жаху", cards.names[1L])
        // The plain-title record comes first in the playlist: still one card.
        val plainFirst = SeasonRecordTitle.cards(listOf(1L to "Американская история ужасов [S05]", 2L to "$bi [S13]"))
        assertEquals(setOf(2L), plainFirst.hidden)
    }

    @Test fun hugeCatalogIsFast() {
        // 40 000 season records of 8 000 shows: the old pairwise grouping froze the TV (owner 12.11).
        val rows = (0 until 40_000).map { n -> n.toLong() to "Сериал номер ${n / 5} [S0${n % 5 + 1}] [ua] [ru]" }
        val t0 = System.nanoTime()
        val cards = SeasonRecordTitle.cards(rows)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertEquals(32_000, cards.hidden.size)
        assertTrue("took $ms ms", ms < 3_000)
    }

    @Test fun oneCardPerShow() {
        val cards = SeasonRecordTitle.cards(
            listOf(
                10L to "Извне [S02] [ua] [ru]", 7L to "Извне [S01]", 12L to "Извне [S04]",
                20L to "Американская история ужасов [S13]", 21L to "Американская история ужасов: Отель [S05]",
                30L to "Остров сокровищ", 31L to "Баба-яга / Яга [S01]",
            ),
        )
        assertEquals(setOf(10L, 12L, 21L), cards.hidden)
        assertEquals("Извне", cards.names[7L])
        assertEquals("Американская история ужасов", cards.names[20L])
        assertEquals("Баба-яга / Яга", cards.names[31L])
        assertFalse(30L in cards.names)
    }
}
