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
        /** Looking for the TV box's network debugging on the Wi-Fi (no address typed). */
        val searching: Boolean = false,
        /** Why it failed, in words for the owner: not found, not allowed on the TV, no picture. */
        val problem: Problem? = null,
        /** The technical reason, small under the message. */
        val detail: String? = null,
        /** The TV box's crash log (YAiPlayer TV does not start, owner 12.11), or null when not read. */
        val crashLog: String? = null,
        val installing: Boolean = false,
        /** null: nothing done; "" installed; otherwise why it failed. */
        val installResult: String? = null,
    )

    enum class Problem { NOT_FOUND, NOT_ALLOWED, UNREACHABLE, NO_PICTURE }

    private val transport: AdbTransport = DadbTransport(app)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var liveJob: Job? = null

    fun setHost(v: String) = _state.update { it.copy(host = v.filter { c -> c.isDigit() || c == '.' }.take(15), failed = false) }

    fun connect() {
        val host = _state.value.host.trim()
        if (host.isEmpty() || _state.value.connecting) return
        _state.update { it.copy(connecting = true, failed = false, problem = null, detail = null) }
        viewModelScope.launch { connectTo(host) }
    }

    /**
     * Opens by itself (owner 12.11: no address, no port): the address the remote already uses, the remembered TV
     * boxes, then the whole Wi-Fi is searched for network debugging (port 5555).
     */
    fun autoConnect(preferred: String?) {
        if (_state.value.connecting || _state.value.connected || _state.value.searching) return
        _state.update { it.copy(searching = true, failed = false, problem = null, detail = null) }
        viewModelScope.launch {
            val host = AdbFinder.find(getApplication(), listOf(preferred, _state.value.host.takeIf { it.isNotBlank() }))
            if (host == null) {
                _state.update { it.copy(searching = false, failed = true, problem = Problem.NOT_FOUND) }
                return@launch
            }
            _state.update { it.copy(searching = false, host = host, connecting = true) }
            connectTo(host)
        }
    }

    private suspend fun connectTo(host: String) {
        val r = transport.connect(host)
        val e = r.exceptionOrNull()
        val problem = when {
            e == null -> null
            e is dadb.AdbAuthException || e is java.net.SocketTimeoutException -> Problem.NOT_ALLOWED
            else -> Problem.UNREACHABLE
        }
        _state.update {
            it.copy(connecting = false, connected = e == null, failed = e != null, problem = problem,
                detail = e?.let { t -> "${t.javaClass.simpleName}: ${t.message?.take(80).orEmpty()}" })
        }
        if (e == null) shoot()
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
        // Raw PNG first (`exec:screencap -p`, no base64 on the TV); the older text way as a fallback.
        val raw = transport.execBytes("screencap -p").getOrNull()
        val bitmap = raw?.let { decodeBytes(it) } ?: transport.runShell(SCREENCAP).getOrNull()?.let { decode(it) }
        _state.update {
            it.copy(busy = false, image = bitmap ?: it.image, failed = bitmap == null,
                problem = if (bitmap == null) Problem.NO_PICTURE else null)
        }
    }

    /** The TV box's recent crashes (`logcat -b crash`), so a TV app that will not start can be diagnosed. */
    fun readCrashLog() {
        if (!_state.value.connected || _state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val crash = transport.runShell("logcat -d -b crash -t 400").getOrNull().orEmpty()
            val text = crash.ifBlank {
                transport.runShell("logcat -d -t 800 AndroidRuntime:E ActivityManager:W *:S").getOrNull().orEmpty()
            }
            _state.update { it.copy(busy = false, crashLog = text.trim()) }
        }
    }

    /**
     * Downloads the newest TV APK (the release's permanent link) and installs it over the app on the TV box through
     * the debugging connection: the way to update a TV app that crashes on start and cannot update itself.
     */
    fun installLatestOnTv() = installOnTv(TV_APK_URL)

    /** The beta TV build (installs next to the normal app), only offered in the phone's beta build. */
    fun installBetaOnTv() = installOnTv(BETA_TV_APK_URL)

    private fun installOnTv(apkUrl: String) {
        if (!_state.value.connected || _state.value.installing) return
        _state.update { it.copy(installing = true, installResult = null) }
        viewModelScope.launch {
            val result = runCatching {
                val file = java.io.File(getApplication<Application>().cacheDir, "YAiPlayer-TV.apk")
                withContext(Dispatchers.IO) {
                    var url = java.net.URL(apkUrl)
                    var conn: java.net.HttpURLConnection
                    var hops = 0
                    while (true) {
                        conn = url.openConnection() as java.net.HttpURLConnection
                        conn.instanceFollowRedirects = false
                        conn.connectTimeout = 15_000
                        conn.readTimeout = 60_000
                        val code = conn.responseCode
                        if (code in 300..399 && hops++ < 6) {
                            url = java.net.URL(url, conn.getHeaderField("Location"))
                            conn.disconnect()
                            continue
                        }
                        check(code == 200) { "HTTP $code" }
                        break
                    }
                    conn.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
                    check(file.length() > 1_000_000) { "file too small" }
                }
                transport.install(file).getOrThrow()
            }
            _state.update {
                it.copy(installing = false, installResult = result.exceptionOrNull()?.let { e -> "${e.javaClass.simpleName}: ${e.message?.take(100).orEmpty()}" } ?: "")
            }
        }
    }

    private suspend fun decodeBytes(bytes: ByteArray): Bitmap? = withContext(Dispatchers.Default) {
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
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
        const val TV_APK_URL = "https://github.com/eai-apps/YAiPlayer/releases/latest/download/YAiPlayer-TV.apk"
        const val BETA_TV_APK_URL = "https://github.com/eai-apps/YAiPlayer/releases/download/beta/YAiPlayer-TV-beta.apk"
        /** A PNG of the screen as base64 on stdout; `toybox base64` is on Android 6+. */
        const val SCREENCAP = "screencap -p | base64"
    }
}

/** Pure part, tested: the shell's output reduced to the base64 alphabet (line breaks and CRs dropped). */
object ScreenPeekText {
    fun clean(output: String): String = output.filter { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '+' || it == '/' || it == '=' }
}
