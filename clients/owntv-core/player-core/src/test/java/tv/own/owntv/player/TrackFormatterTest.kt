package tv.own.owntv.player

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class TrackFormatterTest {

    private val ruStrings = TrackLocalizedStrings(
        dubbing = "дубляж",
        original = "оригинал",
        forced = "форсированные",
        full = "полные",
        multiVoice = "многоголосый",
        twoVoice = "двухголосый",
        singleVoice = "одноголосый",
        commentary = "комментарии",
        sdh = "для слабослышащих",
        fallbackNumber = { "Субтитры $it" },
    )

    private val ruAudioStrings = ruStrings.copy(
        fallbackNumber = { "Аудио $it" },
    )

    @Test
    fun `formats forced subtitle with language`() {
        val track = TrackOption(
            label = "forced",
            mpvId = 0,
            selected = false,
            lang = "rus",
            labelKind = TrackLabelKind.SUBTITLE,
        )
        val result = TrackFormatter.format(track, Locale("ru"), ruStrings)
        assertEquals("Русский (форсированные)", result)
    }

    @Test
    fun `formats full subtitle with language`() {
        val track = TrackOption(
            label = "full",
            mpvId = 1,
            selected = false,
            lang = "rus",
            labelKind = TrackLabelKind.SUBTITLE,
        )
        val result = TrackFormatter.format(track, Locale("ru"), ruStrings)
        assertEquals("Русский (полные)", result)
    }

    @Test
    fun `formats subtitle using forced flag without label`() {
        val track = TrackOption(
            label = "",
            mpvId = 2,
            selected = false,
            lang = "rus",
            forced = true,
            labelKind = TrackLabelKind.SUBTITLE,
        )
        val result = TrackFormatter.format(track, Locale("ru"), ruStrings)
        assertEquals("Русский (форсированные)", result)
    }

    @Test
    fun `formats standalone forced subtitle when language is unknown`() {
        val track = TrackOption(
            label = "forced",
            mpvId = 0,
            selected = false,
            lang = null,
            labelKind = TrackLabelKind.SUBTITLE,
        )
        val result = TrackFormatter.format(track, Locale("ru"), ruStrings)
        assertEquals("Форсированные", result)
    }

    @Test
    fun `formats standalone full subtitle when language is unknown`() {
        val track = TrackOption(
            label = "full",
            mpvId = 1,
            selected = false,
            lang = null,
            labelKind = TrackLabelKind.SUBTITLE,
        )
        val result = TrackFormatter.format(track, Locale("ru"), ruStrings)
        assertEquals("Полные", result)
    }

    @Test
    fun `formats audio dubbing track`() {
        val track = TrackOption(
            label = "DUB",
            mpvId = 0,
            selected = true,
            lang = "rus",
            labelKind = TrackLabelKind.AUDIO,
        )
        val result = TrackFormatter.format(track, Locale("ru"), ruAudioStrings)
        assertEquals("Русский (дубляж)", result)
    }

    @Test
    fun `formats audio original track`() {
        val track = TrackOption(
            label = "original",
            mpvId = 1,
            selected = false,
            lang = "eng",
            labelKind = TrackLabelKind.AUDIO,
        )
        val result = TrackFormatter.format(track, Locale("ru"), ruAudioStrings)
        assertEquals("Английский (оригинал)", result)
    }

    @Test
    fun `detects language from label when lang is missing`() {
        val track = TrackOption(
            label = "Russian forced",
            mpvId = 0,
            selected = false,
            lang = null,
            labelKind = TrackLabelKind.SUBTITLE,
        )
        val result = TrackFormatter.format(track, Locale("ru"), ruStrings)
        assertEquals("Русский (форсированные)", result)
    }

    @Test
    fun `preserves custom studio name`() {
        val track = TrackOption(
            label = "LostFilm",
            mpvId = 0,
            selected = false,
            lang = "rus",
            labelKind = TrackLabelKind.AUDIO,
        )
        val result = TrackFormatter.format(track, Locale("ru"), ruAudioStrings)
        assertEquals("Русский (LostFilm)", result)
    }

    @Test
    fun `uses fallback track number when nothing is known`() {
        val track = TrackOption(
            label = "",
            mpvId = 3,
            selected = false,
            lang = null,
            typeIndex = 3,
            labelKind = TrackLabelKind.SUBTITLE,
        )
        val result = TrackFormatter.format(track, Locale("ru"), ruStrings)
        assertEquals("Субтитры 4", result)
    }
}
