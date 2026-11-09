// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.lightmode

import tv.own.owntv.core.theme.AnimationLevel

/**
 * Pure policy rules for Light Mode (low-end device optimization).
 */
object LightModePolicy {

    /**
     * When Light Mode is active, hero preview streaming in the main row is disabled.
     * When Light Mode is inactive, the user's saved preference is respected.
     */
    fun isEffectiveHeroPreview(savedHeroPreview: Boolean, lightMode: Boolean): Boolean {
        return if (lightMode) false else savedHeroPreview
    }

    /**
     * When Light Mode is active, channel list live video preview is disabled.
     * When Light Mode is inactive, the user's saved preference is respected.
     */
    fun isEffectiveLivePreview(savedLivePreview: Boolean, lightMode: Boolean): Boolean {
        return if (lightMode) false else savedLivePreview
    }

    /**
     * When Light Mode is active, animations are set to OFF to maximize responsiveness on budget chipsets.
     * When Light Mode is inactive, the user's saved animation level is respected.
     */
    fun isEffectiveAnimationLevel(savedLevel: AnimationLevel, lightMode: Boolean): AnimationLevel {
        return if (lightMode) AnimationLevel.OFF else savedLevel
    }

    /**
     * Prompt is shown once on first launch only when the hardware is low-RAM,
     * the prompt has not been dismissed yet, and Light Mode is not already active.
     */
    fun shouldPromptLightMode(
        isLowRam: Boolean,
        promptDismissed: Boolean,
        lightModeEnabled: Boolean,
    ): Boolean {
        return isLowRam && !promptDismissed && !lightModeEnabled
    }
}
