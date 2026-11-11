// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.player

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackTitleTest {
    private val ru = Locale.forLanguageTag("ru")
    private fun kindRu(k: TrackTitleKind) = when (k) {
        TrackTitleKind.FORCED -> "Форсированные"
        TrackTitleKind.FULL -> "Полные"
        TrackTitleKind.SDH -> "Для слабослышащих"
        TrackTitleKind.DUB -> "Дубляж"
        TrackTitleKind.MVO -> "Многоголосый"
        TrackTitleKind.DVO -> "Двухголосый"
        TrackTitleKind.AVO -> "Авторский"
        TrackTitleKind.ORIGINAL -> "Оригинал"
        TrackTitleKind.COMMENTARY -> "Комментарии"
    }
    private fun show(title: String?, lang: String?) =
        TrackTitle.display(title, lang, ru, ::kindRu, fallback = "Субтитры 4", separator = " · ")

    @Test fun kindWordsNoLongerHideTheLanguage() {
        assertEquals("Русский · Форсированные", show("forced", "rus"))
        assertEquals("Английский · Полные", show("full", "eng"))
        assertEquals("Русский · Дубляж", show("Русский (дублированный)", "rus"))
    }

    @Test fun genericTitlesFallBackToTheLanguage() {
        assertEquals("Английский", show("Subtitle 4", "eng"))
        assertEquals("Русский", show("", "ru"))
    }

    @Test fun languageFromTheTitleWhenTheStreamHasNone() {
        assertEquals("Русский · Многоголосый", show("RUS MVO", null))
        assertEquals("Украинский · Дубляж", show("Ukr Dub", "und"))
    }

    @Test fun anythingElseInTheTitleIsKeptAfterTheLanguage() {
        assertEquals("Русский · Многоголосый · Пифагор", show("Многоголосый, Пифагор", "rus"))
        assertEquals("Русский · Пифагор", show("Пифагор", "rus"))
    }

    @Test fun nothingToGoOn() {
        assertEquals("Субтитры 4", show(null, null))
        assertEquals("Субтитры 4 · Форсированные", show("forced", null))
        assertEquals("Something", show("Something", null))
    }

    @Test fun streamForcedFlagCountsWhenTheTitleDoesNotSayIt() {
        val flagged = TrackTitle.display("Subtitle 4", "rus", ru, ::kindRu, fallback = "Субтитры 4", separator = " · ", forcedFlag = true)
        assertEquals("Русский · Форсированные", flagged)
        // Already said by the title: not doubled.
        val named = TrackTitle.display("forced", "rus", ru, ::kindRu, fallback = "Субтитры 4", separator = " · ", forcedFlag = true)
        assertEquals("Русский · Форсированные", named)
    }
}
