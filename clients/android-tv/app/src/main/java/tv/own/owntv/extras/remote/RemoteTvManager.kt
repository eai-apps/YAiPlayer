// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.remote

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RemoteTvManager(private val context: Context) : RemoteCommandHandler, RemoteStateProvider {

    private val prefs = context.applicationContext.getSharedPreferences("remote_tv_prefs", Context.MODE_PRIVATE)
    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    val pairingManager = RemotePairingManager(SharedPrefsRemotePairingStore(context))
    private val discovery = RemoteDiscoveryManager(context)

    private val _enabled = MutableStateFlow(prefs.getBoolean("remote_enabled", false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _serverPort = MutableStateFlow<Int?>(null)
    val serverPort: StateFlow<Int?> = _serverPort.asStateFlow()

    private val _currentPin = MutableStateFlow<String?>(null)
    val currentPin: StateFlow<String?> = _currentPin.asStateFlow()

    private val _pinExpiresAtMs = MutableStateFlow(0L)
    val pinExpiresAtMs: StateFlow<Long> = _pinExpiresAtMs.asStateFlow()

    private val _pairedDevices = MutableStateFlow(pairingManager.getPairedDevices())
    val pairedDevices: StateFlow<List<PairedDevice>> = _pairedDevices.asStateFlow()

    // Callbacks to UI / Activity
    var keyEventDispatcher: ((Int) -> Boolean)? = null
    var togglePlayPause: (() -> Unit)? = null
    var seekBy: ((Long) -> Unit)? = null
    var zap: ((Int) -> Unit)? = null
    var toLive: (() -> Unit)? = null
    var tuneByNumber: ((Int) -> Unit)? = null
    var onSearchText: ((String) -> Boolean)? = null
    var onTune: ((channelId: Long?, streamUrl: String?, title: String?) -> Unit)? = null
    var stateSupplier: (() -> RemoteState)? = null
    var favoritesSupplier: (() -> List<RemoteFavoriteItem>)? = null
    var logsSupplier: (() -> String)? = null

    private val tvName: String
        get() = "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "YAiPlayer TV" }

    private val server = RemoteTvServer(
        pairingManager = pairingManager,
        commandHandler = this,
        stateProvider = this,
        tvName = tvName,
    )

    init {
        if (_enabled.value) {
            startServer()
        }
    }

    fun setEnabled(value: Boolean) {
        if (_enabled.value == value) return
        _enabled.value = value
        prefs.edit().putBoolean("remote_enabled", value).apply()
        if (value) {
            startServer()
        } else {
            stopServer()
        }
    }

    fun generateNewPin(): String {
        val pin = pairingManager.generateNewCode()
        _currentPin.value = pin
        _pinExpiresAtMs.value = pairingManager.pinExpiresAtMs
        return pin
    }

    fun forgetDevice(deviceId: String) {
        pairingManager.forgetDevice(deviceId)
        _pairedDevices.value = pairingManager.getPairedDevices()
    }

    fun refreshDevices() {
        _currentPin.value = pairingManager.currentPin?.takeIf { pairingManager.pinRemainingSeconds() > 0 }
        _pairedDevices.value = pairingManager.getPairedDevices()
    }

    private fun startServer() {
        val port = server.start(0)
        _serverPort.value = port
        if (port != null) {
            discovery.advertise(tvName, port)
        }
    }

    private fun stopServer() {
        server.stop()
        pairingManager.invalidateCode()
        discovery.stopAdvertising()
        _serverPort.value = null
        _currentPin.value = null
        _pinExpiresAtMs.value = 0L
    }

    override fun handleCommand(command: RemoteCommand): Boolean = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.Main.immediate) {
        if (!_enabled.value) return@runBlocking false
        refreshDevices()
        when (command) {
            is RemoteCommand.Nav -> {
                val keyCode = when (command.key) {
                    RemoteNavKey.UP -> KeyEvent.KEYCODE_DPAD_UP
                    RemoteNavKey.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
                    RemoteNavKey.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
                    RemoteNavKey.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
                    RemoteNavKey.OK -> KeyEvent.KEYCODE_DPAD_CENTER
                    RemoteNavKey.BACK -> KeyEvent.KEYCODE_BACK
                    RemoteNavKey.HOME -> KeyEvent.KEYCODE_HOME
                    RemoteNavKey.MENU -> KeyEvent.KEYCODE_MENU
                }
                keyEventDispatcher?.invoke(keyCode) ?: false
            }
            is RemoteCommand.Media -> {
                when (command.key) {
                    RemoteMediaKey.PLAY_PAUSE -> {
                        togglePlayPause?.invoke()
                        true
                    }
                    RemoteMediaKey.REWIND_10 -> {
                        seekBy?.invoke(-10_000L)
                        true
                    }
                    RemoteMediaKey.FORWARD_10 -> {
                        seekBy?.invoke(10_000L)
                        true
                    }
                    RemoteMediaKey.REWIND_60 -> {
                        seekBy?.invoke(-60_000L)
                        true
                    }
                    RemoteMediaKey.FORWARD_60 -> {
                        seekBy?.invoke(60_000L)
                        true
                    }
                    RemoteMediaKey.VOLUME_UP -> {
                        audioManager?.adjustStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            AudioManager.ADJUST_RAISE,
                            AudioManager.FLAG_SHOW_UI,
                        )
                        true
                    }
                    RemoteMediaKey.VOLUME_DOWN -> {
                        audioManager?.adjustStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            AudioManager.ADJUST_LOWER,
                            AudioManager.FLAG_SHOW_UI,
                        )
                        true
                    }
                    RemoteMediaKey.VOLUME_MUTE -> {
                        audioManager?.adjustStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            AudioManager.ADJUST_TOGGLE_MUTE,
                            AudioManager.FLAG_SHOW_UI,
                        )
                        true
                    }
                    RemoteMediaKey.CHANNEL_UP -> {
                        zap?.invoke(1)
                        true
                    }
                    RemoteMediaKey.CHANNEL_DOWN -> {
                        zap?.invoke(-1)
                        true
                    }
                    RemoteMediaKey.TO_LIVE -> {
                        toLive?.invoke()
                        true
                    }
                    RemoteMediaKey.ARCHIVE -> {
                        seekBy?.invoke(-300_000L)
                        true
                    }
                }
            }
            is RemoteCommand.Number -> {
                if (tuneByNumber != null) {
                    tuneByNumber?.invoke(command.digit)
                    true
                } else {
                    val keyCode = KeyEvent.KEYCODE_0 + command.digit.coerceIn(0, 9)
                    keyEventDispatcher?.invoke(keyCode) ?: false
                }
            }
            is RemoteCommand.Text -> {
                onSearchText?.invoke(command.query) ?: false
            }
            is RemoteCommand.Tune -> {
                val streamUrl = command.streamUrl
                if (streamUrl != null && !RemoteSecurity.shareableLink(streamUrl)) false
                else if (onTune == null || (command.streamUrl == null && command.title.isNullOrBlank())) false
                else { onTune?.invoke(null, command.streamUrl, command.title?.take(256)); true }
            }
        }
    }

    override fun getState(): RemoteState {
        return stateSupplier?.invoke() ?: RemoteState()
    }

    override fun getFavorites(): List<RemoteFavoriteItem> {
        return favoritesSupplier?.invoke() ?: emptyList()
    }

    override fun getDiagnosticLogs(): String {
        return logsSupplier?.invoke() ?: "YAiPlayer log: no report"
    }

    companion object {
        @Volatile
        private var instance: RemoteTvManager? = null

        fun get(context: Context): RemoteTvManager {
            return instance ?: synchronized(this) {
                instance ?: RemoteTvManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
