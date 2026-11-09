// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.screensaver

class ScreensaverIdleDetector(
    var lastUserActivityMs: Long = System.currentTimeMillis()
) {
    var isPlaybackActive: Boolean = false
    var isPlaybackPaused: Boolean = false
    var isPlaybackStalled: Boolean = false
    var isInputActive: Boolean = false
    var isPinDialogActive: Boolean = false
    var isScreensaverActive: Boolean = false

    fun onUserActivity(now: Long = System.currentTimeMillis()) {
        lastUserActivityMs = now
        if (isScreensaverActive) {
            isScreensaverActive = false
        }
    }

    fun checkShouldActivate(now: Long = System.currentTimeMillis(), config: ScreensaverConfig): Boolean {
        if (!config.enabled) return false
        if (isScreensaverActive) return false
        if (isInputActive || isPinDialogActive) return false

        // Playing actively with progress -> do not activate screensaver
        if (isPlaybackActive && !isPlaybackPaused && !isPlaybackStalled) {
            return false
        }

        val elapsed = now - lastUserActivityMs
        return elapsed >= config.timeoutMs
    }

    /**
     * Intercepts a key press when the screensaver is active.
     * Returns: (consumed, shouldResumePlayback)
     */
    fun onKeyEvent(keyCode: Int, isPlayKey: Boolean, now: Long = System.currentTimeMillis()): Pair<Boolean, Boolean> {
        if (!isScreensaverActive) {
            lastUserActivityMs = now
            return false to false
        }

        isScreensaverActive = false
        lastUserActivityMs = now
        val shouldResume = isPlayKey && isPlaybackPaused
        return true to shouldResume
    }
}
