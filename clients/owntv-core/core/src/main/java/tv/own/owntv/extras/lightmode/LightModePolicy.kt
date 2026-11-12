// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.lightmode

import tv.own.owntv.core.theme.AnimationLevel

/**
 * Pure policy rules for Light Mode (low-end device optimization).
 */
object LightModePolicy {

    /**
     * Owner 12.11: Light Mode is about animations only; the previews have their own switches next to it
     * (Settings → Video player: «Лёгкий режим» and «Превью каналов»). The saved preference always wins.
     */
    @Suppress("UNUSED_PARAMETER")
    fun isEffectiveHeroPreview(savedHeroPreview: Boolean, lightMode: Boolean): Boolean = savedHeroPreview

    /** The channel list's live preview follows its own switch, whatever Light Mode says (owner 12.11). */
    @Suppress("UNUSED_PARAMETER")
    fun isEffectiveLivePreview(savedLivePreview: Boolean, lightMode: Boolean): Boolean = savedLivePreview

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
