// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.tvoptimizer

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The TV box's screen on the phone (owner 12.11), from the remote. The Android TV remote protocol has no picture, so
 * this uses network debugging (the same connection as «Ускорь телевизор»): `screencap` on the TV, sent back as
 * base64 text over the ADB shell and decoded here. One picture on demand, or one every [LIVE_INTERVAL_MS].
 */
class TvScreenPeekViewModel(app: Application) : AndroidViewModel(app) {
    data class State(
        val host: String = "",
        val connecting: Boolean = false,
        val connected: Boolean = false,
        val busy: Boolean = false,
        val live: Boolean = false,
        val image: Bitmap? = null,
        val failed: Boolean = false,
    )

    private val transport: AdbTransport = DadbTransport(app)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var liveJob: Job? = null

    fun setHost(v: String) = _state.update { it.copy(host = v.filter { c -> c.isDigit() || c == '.' }.take(15), failed = false) }

    fun connect() {
        val host = _state.value.host.trim()
        if (host.isEmpty() || _state.value.connecting) return
        _state.update { it.copy(connecting = true, failed = false) }
        viewModelScope.launch {
            val ok = transport.connect(host).isSuccess
            _state.update { it.copy(connecting = false, connected = ok, failed = !ok) }
            if (ok) shoot()
        }
    }

    fun shoot() {
        if (!_state.value.connected || _state.value.busy) return
        viewModelScope.launch { capture() }
    }

    fun setLive(on: Boolean) {
        _state.update { it.copy(live = on) }
        liveJob?.cancel()
        if (on) liveJob = viewModelScope.launch {
            while (isActive && _state.value.live && _state.value.connected) {
                capture()
                delay(LIVE_INTERVAL_MS)
            }
        }
    }

    private suspend fun capture() {
        _state.update { it.copy(busy = true) }
        val bitmap = transport.runShell(SCREENCAP).getOrNull()?.let { decode(it) }
        _state.update { it.copy(busy = false, image = bitmap ?: it.image, failed = bitmap == null) }
    }

    private suspend fun decode(text: String): Bitmap? = withContext(Dispatchers.Default) {
        runCatching {
            val bytes = Base64.decode(ScreenPeekText.clean(text), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    override fun onCleared() {
        liveJob?.cancel()
        // The connection closes with the screen; the TV keeps nothing running.
        kotlinx.coroutines.runBlocking { runCatching { transport.disconnect() } }
    }

    companion object {
        const val LIVE_INTERVAL_MS = 2_000L
        /** A PNG of the screen as base64 on stdout; `toybox base64` is on Android 6+. */
        const val SCREENCAP = "screencap -p | base64"
    }
}

/** Pure part, tested: the shell's output reduced to the base64 alphabet (line breaks and CRs dropped). */
object ScreenPeekText {
    fun clean(output: String): String = output.filter { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '+' || it == '/' || it == '=' }
}
