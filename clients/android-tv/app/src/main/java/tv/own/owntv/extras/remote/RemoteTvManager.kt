// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.remote

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.own.owntv.player.diagnostic.DiagnosticLogExporter

object RemoteTvManager {
    private const val PREF_REMOTE_ENABLED = "tv_phone_remote_enabled"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var server: RemoteTvServer? = null
    private var currentActivity: Activity? = null
    private var prefs: SharedPreferences? = null

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _serverState = MutableStateFlow<RemoteTvServer.ServerState>(RemoteTvServer.ServerState.Stopped)
    val serverState: StateFlow<RemoteTvServer.ServerState> = _serverState.asStateFlow()

    fun init(context: Context) {
        val sp = context.getSharedPreferences("tv_phone_remote", Context.MODE_PRIVATE)
        prefs = sp
        val enabled = sp.getBoolean(PREF_REMOTE_ENABLED, false)
        _isEnabled.value = enabled
        if (enabled) {
            startServer(context)
        }
    }

    fun attachActivity(activity: Activity) {
        currentActivity = activity
    }

    fun detachActivity(activity: Activity) {
        if (currentActivity === activity) {
            currentActivity = null
        }
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs?.edit()?.putBoolean(PREF_REMOTE_ENABLED, enabled)?.apply()
        _isEnabled.value = enabled
        if (enabled) {
            startServer(context)
        } else {
            stopServer()
        }
    }

    fun pairingManager(): RemotePairingManager? = server?.pairingManager

    private fun startServer(context: Context) {
        if (server != null) return
        val srv = RemoteTvServer(context.applicationContext)
        srv.onCommandReceived = { cmd ->
            dispatchCommand(context, cmd)
        }
        srv.logReportProvider = {
            DiagnosticLogExporter.buildReport(context.applicationContext)
        }
        server = srv
        srv.start()
        scope.launch {
            srv.serverState.collect { state ->
                _serverState.value = state
            }
        }
    }

    private fun stopServer() {
        server?.stop()
        server = null
        _serverState.value = RemoteTvServer.ServerState.Stopped
    }

    private fun dispatchCommand(context: Context, cmd: RemoteCommand) {
        when (cmd) {
            RemoteCommand.VolumeUp -> adjustVolume(context, AudioManager.ADJUST_RAISE)
            RemoteCommand.VolumeDown -> adjustVolume(context, AudioManager.ADJUST_LOWER)
            RemoteCommand.VolumeMute -> adjustVolume(context, AudioManager.ADJUST_TOGGLE_MUTE)
            is RemoteCommand.Up -> injectKey(KeyEvent.KEYCODE_DPAD_UP)
            is RemoteCommand.Down -> injectKey(KeyEvent.KEYCODE_DPAD_DOWN)
            is RemoteCommand.Left -> injectKey(KeyEvent.KEYCODE_DPAD_LEFT)
            is RemoteCommand.Right -> injectKey(KeyEvent.KEYCODE_DPAD_RIGHT)
            is RemoteCommand.Ok -> injectKey(KeyEvent.KEYCODE_DPAD_CENTER)
            is RemoteCommand.Back -> injectKey(KeyEvent.KEYCODE_BACK)
            is RemoteCommand.Home -> injectKey(KeyEvent.KEYCODE_HOME)
            is RemoteCommand.Menu -> injectKey(KeyEvent.KEYCODE_MENU)
            is RemoteCommand.PlayPause -> injectKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            is RemoteCommand.Rewind10, is RemoteCommand.Rewind60 -> injectKey(KeyEvent.KEYCODE_MEDIA_REWIND)
            is RemoteCommand.Forward10, is RemoteCommand.Forward60 -> injectKey(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
            is RemoteCommand.ChannelNext -> injectKey(KeyEvent.KEYCODE_CHANNEL_UP)
            is RemoteCommand.ChannelPrev -> injectKey(KeyEvent.KEYCODE_CHANNEL_DOWN)
            is RemoteCommand.Digit -> {
                val code = when (cmd.digit) {
                    0 -> KeyEvent.KEYCODE_0
                    1 -> KeyEvent.KEYCODE_1
                    2 -> KeyEvent.KEYCODE_2
                    3 -> KeyEvent.KEYCODE_3
                    4 -> KeyEvent.KEYCODE_4
                    5 -> KeyEvent.KEYCODE_5
                    6 -> KeyEvent.KEYCODE_6
                    7 -> KeyEvent.KEYCODE_7
                    8 -> KeyEvent.KEYCODE_8
                    9 -> KeyEvent.KEYCODE_9
                    else -> null
                }
                code?.let { injectKey(it) }
            }
            else -> {}
        }
    }

    private fun adjustVolume(context: Context, direction: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        am?.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }

    private fun injectKey(keyCode: Int) {
        val act = currentActivity ?: return
        act.runOnUiThread {
            act.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            act.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
    }
}
