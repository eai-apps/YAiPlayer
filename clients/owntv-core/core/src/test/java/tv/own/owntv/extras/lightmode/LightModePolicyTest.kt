// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.lightmode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.theme.AnimationLevel

class LightModePolicyTest {

    @Test
    fun `effective hero preview respects saved value when light mode is off`() {
        assertTrue(LightModePolicy.isEffectiveHeroPreview(savedHeroPreview = true, lightMode = false))
        assertFalse(LightModePolicy.isEffectiveHeroPreview(savedHeroPreview = false, lightMode = false))
    }

    @Test
    fun `effective hero preview is forced false when light mode is on`() {
        assertFalse(LightModePolicy.isEffectiveHeroPreview(savedHeroPreview = true, lightMode = true))
        assertFalse(LightModePolicy.isEffectiveHeroPreview(savedHeroPreview = false, lightMode = true))
    }

    @Test
    fun `effective live preview respects saved value when light mode is off`() {
        assertTrue(LightModePolicy.isEffectiveLivePreview(savedLivePreview = true, lightMode = false))
        assertFalse(LightModePolicy.isEffectiveLivePreview(savedLivePreview = false, lightMode = false))
    }

    @Test
    fun `effective live preview is forced false when light mode is on`() {
        assertFalse(LightModePolicy.isEffectiveLivePreview(savedLivePreview = true, lightMode = true))
        assertFalse(LightModePolicy.isEffectiveLivePreview(savedLivePreview = false, lightMode = true))
    }

    @Test
    fun `effective animation level respects saved level when light mode is off`() {
        assertEquals(AnimationLevel.FULL, LightModePolicy.isEffectiveAnimationLevel(AnimationLevel.FULL, lightMode = false))
        assertEquals(AnimationLevel.OFF, LightModePolicy.isEffectiveAnimationLevel(AnimationLevel.OFF, lightMode = false))
    }

    @Test
    fun `effective animation level is forced OFF when light mode is on`() {
        assertEquals(AnimationLevel.OFF, LightModePolicy.isEffectiveAnimationLevel(AnimationLevel.FULL, lightMode = true))
        assertEquals(AnimationLevel.OFF, LightModePolicy.isEffectiveAnimationLevel(AnimationLevel.OFF, lightMode = true))
    }

    @Test
    fun `restoration of previous values on toggling light mode off`() {
        val originalHero = true
        val originalLive = true
        val originalAnim = AnimationLevel.FULL

        // 1. Initially light mode is off
        var lightMode = false
        assertEquals(originalHero, LightModePolicy.isEffectiveHeroPreview(originalHero, lightMode))
        assertEquals(originalLive, LightModePolicy.isEffectiveLivePreview(originalLive, lightMode))
        assertEquals(originalAnim, LightModePolicy.isEffectiveAnimationLevel(originalAnim, lightMode))

        // 2. Light mode turned on -> previews disabled, animations off
        lightMode = true
        assertFalse(LightModePolicy.isEffectiveHeroPreview(originalHero, lightMode))
        assertFalse(LightModePolicy.isEffectiveLivePreview(originalLive, lightMode))
        assertEquals(AnimationLevel.OFF, LightModePolicy.isEffectiveAnimationLevel(originalAnim, lightMode))

        // 3. Light mode turned off -> previous saved values return untouched
        lightMode = false
        assertEquals(originalHero, LightModePolicy.isEffectiveHeroPreview(originalHero, lightMode))
        assertEquals(originalLive, LightModePolicy.isEffectiveLivePreview(originalLive, lightMode))
        assertEquals(originalAnim, LightModePolicy.isEffectiveAnimationLevel(originalAnim, lightMode))
    }

    @Test
    fun `prompt logic correctly targets low-RAM devices only once`() {
        // Low RAM, not yet prompted, light mode off -> show prompt
        assertTrue(
            LightModePolicy.shouldPromptLightMode(
                isLowRam = true,
                promptDismissed = false,
                lightModeEnabled = false,
            )
        )

        // Device has plenty of RAM -> do not prompt
        assertFalse(
            LightModePolicy.shouldPromptLightMode(
                isLowRam = false,
                promptDismissed = false,
                lightModeEnabled = false,
            )
        )

        // Already dismissed previously -> do not prompt again
        assertFalse(
            LightModePolicy.shouldPromptLightMode(
                isLowRam = true,
                promptDismissed = true,
                lightModeEnabled = false,
            )
        )

        // Already enabled -> no need to prompt
        assertFalse(
            LightModePolicy.shouldPromptLightMode(
                isLowRam = true,
                promptDismissed = false,
                lightModeEnabled = true,
            )
        )
    }
}
