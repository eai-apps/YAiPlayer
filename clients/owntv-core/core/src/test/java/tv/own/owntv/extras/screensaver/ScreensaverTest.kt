// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.screensaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreensaverTest {

    @Test
    fun `idle detector activates only after timeout in idle state`() {
        val detector = ScreensaverIdleDetector(lastUserActivityMs = 1000L)
        val config = ScreensaverConfig(enabled = true, timeoutMinutes = 5)

        // 4 minutes elapsed -> should not activate
        assertFalse(detector.checkShouldActivate(now = 1000L + 4 * 60 * 1000L, config = config))

        // 5 minutes elapsed -> should activate
        assertTrue(detector.checkShouldActivate(now = 1000L + 5 * 60 * 1000L, config = config))
    }

    @Test
    fun `idle detector ignores active video playback`() {
        val detector = ScreensaverIdleDetector(lastUserActivityMs = 1000L)
        detector.isPlaybackActive = true
        detector.isPlaybackPaused = false
        detector.isPlaybackStalled = false

        val config = ScreensaverConfig(enabled = true, timeoutMinutes = 5)
        val now = 1000L + 10 * 60 * 1000L

        assertFalse(detector.checkShouldActivate(now = now, config = config))
    }

    @Test
    fun `idle detector triggers when video is paused`() {
        val detector = ScreensaverIdleDetector(lastUserActivityMs = 1000L)
        detector.isPlaybackActive = true
        detector.isPlaybackPaused = true

        val config = ScreensaverConfig(enabled = true, timeoutMinutes = 5)
        val now = 1000L + 5 * 60 * 1000L

        assertTrue(detector.checkShouldActivate(now = now, config = config))
    }

    @Test
    fun `idle detector triggers when video is stalled without progress`() {
        val detector = ScreensaverIdleDetector(lastUserActivityMs = 1000L)
        detector.isPlaybackActive = true
        detector.isPlaybackPaused = false
        detector.isPlaybackStalled = true

        val config = ScreensaverConfig(enabled = true, timeoutMinutes = 5)
        val now = 1000L + 5 * 60 * 1000L

        assertTrue(detector.checkShouldActivate(now = now, config = config))
    }

    @Test
    fun `idle detector is suppressed during text input or PIN dialog`() {
        val detector = ScreensaverIdleDetector(lastUserActivityMs = 1000L)
        val config = ScreensaverConfig(enabled = true, timeoutMinutes = 5)
        val now = 1000L + 10 * 60 * 1000L

        detector.isInputActive = true
        assertFalse(detector.checkShouldActivate(now = now, config = config))

        detector.isInputActive = false
        detector.isPinDialogActive = true
        assertFalse(detector.checkShouldActivate(now = now, config = config))
    }

    @Test
    fun `key press closes screensaver and play key resumes paused video`() {
        val detector = ScreensaverIdleDetector(lastUserActivityMs = 1000L)
        detector.isScreensaverActive = true
        detector.isPlaybackPaused = true

        // Normal OK button dismisses without resume
        val (consumed1, resume1) = detector.onKeyEvent(keyCode = 23, isPlayKey = false)
        assertTrue(consumed1)
        assertFalse(resume1)
        assertFalse(detector.isScreensaverActive)

        // Reactivate screensaver
        detector.isScreensaverActive = true
        // Play button dismisses and requests resume
        val (consumed2, resume2) = detector.onKeyEvent(keyCode = 126, isPlayKey = true)
        assertTrue(consumed2)
        assertTrue(resume2)
        assertFalse(detector.isScreensaverActive)
    }

    @Test
    fun `manifest parser extracts items and uses fallback on invalid json`() {
        val fallback = ScreensaverManifest.parseManifestJson("invalid-json")
        assertTrue(fallback.isNotEmpty())
        assertEquals("nasa_carina", fallback.first().id)

        val json = """
        {
          "version": 1,
          "items": [
            {
              "id": "custom_1",
              "title": "Custom Lake",
              "subtitle": "Photographer",
              "url": "https://example.com/lake.jpg",
              "license": "CC0",
              "source": "Unsplash"
            }
          ]
        }
        """.trimIndent()

        val parsed = ScreensaverManifest.parseManifestJson(json)
        assertEquals(1, parsed.size)
        assertEquals("custom_1", parsed[0].id)
        assertEquals("Custom Lake", parsed[0].title)
        assertEquals("https://example.com/lake.jpg", parsed[0].imageUrl)
    }
}
