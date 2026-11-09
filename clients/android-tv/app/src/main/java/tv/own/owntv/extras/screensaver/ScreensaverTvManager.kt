// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.screensaver

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object ScreensaverTvManager {
    private const val PREFS_NAME = "tv_screensaver_prefs"
    private const val KEY_ENABLED = "screensaver_enabled"
    private const val KEY_TIMEOUT = "screensaver_timeout_minutes"
    private const val KEY_MODE = "screensaver_mode"
    private const val KEY_CORNER_CLOCK = "screensaver_corner_clock"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var prefs: SharedPreferences? = null
    private var idleCheckJob: Job? = null

    val detector = ScreensaverIdleDetector()

    private val _config = MutableStateFlow(ScreensaverConfig())
    val config: StateFlow<ScreensaverConfig> = _config.asStateFlow()

    private val _isScreensaverVisible = MutableStateFlow(false)
    val isScreensaverVisible: StateFlow<Boolean> = _isScreensaverVisible.asStateFlow()

    fun init(context: Context) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp
        val enabled = sp.getBoolean(KEY_ENABLED, true)
        val timeout = sp.getInt(KEY_TIMEOUT, 5)
        val modeStr = sp.getString(KEY_MODE, ScreensaverMode.MIX.name) ?: ScreensaverMode.MIX.name
        val cornerClock = sp.getBoolean(KEY_CORNER_CLOCK, true)
        val cfg = ScreensaverConfig(
            enabled = enabled,
            timeoutMinutes = timeout,
            mode = ScreensaverMode.fromString(modeStr),
            showCornerClock = cornerClock,
        )
        _config.value = cfg
        startIdleMonitoring()
    }

    fun setEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_ENABLED, enabled)?.apply()
        _config.value = _config.value.copy(enabled = enabled)
        if (!enabled) {
            dismiss()
        }
    }

    fun setTimeoutMinutes(minutes: Int) {
        prefs?.edit()?.putInt(KEY_TIMEOUT, minutes)?.apply()
        _config.value = _config.value.copy(timeoutMinutes = minutes)
    }

    fun setMode(mode: ScreensaverMode) {
        prefs?.edit()?.putString(KEY_MODE, mode.name)?.apply()
        _config.value = _config.value.copy(mode = mode)
    }

    fun setShowCornerClock(show: Boolean) {
        prefs?.edit()?.putBoolean(KEY_CORNER_CLOCK, show)?.apply()
        _config.value = _config.value.copy(showCornerClock = show)
    }

    fun onUserActivity() {
        detector.onUserActivity()
        if (_isScreensaverVisible.value) {
            _isScreensaverVisible.value = false
        }
    }

    fun updatePlaybackState(isActive: Boolean, isPaused: Boolean, isStalled: Boolean) {
        detector.isPlaybackActive = isActive
        detector.isPlaybackPaused = isPaused
        detector.isPlaybackStalled = isStalled
    }

    fun onKeyEvent(keyCode: Int, isPlayKey: Boolean): Pair<Boolean, Boolean> {
        val (consumed, resume) = detector.onKeyEvent(keyCode, isPlayKey)
        if (consumed) {
            _isScreensaverVisible.value = false
        }
        return consumed to resume
    }

    fun preview() {
        detector.isScreensaverActive = true
        _isScreensaverVisible.value = true
    }

    fun dismiss() {
        detector.isScreensaverActive = false
        _isScreensaverVisible.value = false
        detector.onUserActivity()
    }

    private fun startIdleMonitoring() {
        idleCheckJob?.cancel()
        idleCheckJob = scope.launch {
            while (isActive) {
                delay(1000)
                val now = System.currentTimeMillis()
                if (detector.checkShouldActivate(now, _config.value)) {
                    detector.isScreensaverActive = true
                    _isScreensaverVisible.value = true
                }
            }
        }
    }
}
