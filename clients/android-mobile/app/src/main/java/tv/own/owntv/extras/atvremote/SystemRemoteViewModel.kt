// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.atvremote

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** Why the remote is not connected; the screen turns each into a localized message. */
enum class SystemRemoteProblem { OFFLINE, NEEDS_PAIRING, CERT_CHANGED, NOT_LOCAL, DISCONNECTED }

sealed interface SystemRemoteStatus {
    data object NoTv : SystemRemoteStatus
    data class Connecting(val name: String) : SystemRemoteStatus
    data class Connected(val name: String, val model: String) : SystemRemoteStatus
    data class Failed(val name: String, val problem: SystemRemoteProblem, val detail: String? = null) : SystemRemoteStatus
}

/** Pairing with a TV that is not remembered yet. */
data class SystemRemotePairing(
    val name: String,
    val host: String,
    val remotePort: Int,
    val step: Step,
    val error: Error? = null,
    /** The technical reason behind [error] (timeout, refused, handshake), shown small so a failure can be diagnosed. */
    val detail: String? = null,
) {
    enum class Step { OPENING, ENTER_CODE, CHECKING }
    enum class Error { CANNOT_REACH, TYPO, REJECTED, FAILED }
}

class SystemRemoteViewModel(app: Application) : AndroidViewModel(app) {
    init {
        AtvLocalNetwork.install(app)
    }

    private val store = AtvDeviceStore(app)
    private val discovery = AtvDiscovery(app)
    private val identity: AtvIdentity = AtvKeystoreIdentity

    val savedTvs: StateFlow<List<AtvSavedTv>> = store.devices
    val foundTvs: StateFlow<List<AtvFoundTv>> = discovery.found

    private val _status = MutableStateFlow<SystemRemoteStatus>(SystemRemoteStatus.NoTv)
    val status: StateFlow<SystemRemoteStatus> = _status.asStateFlow()

    private val _pairing = MutableStateFlow<SystemRemotePairing?>(null)
    val pairing: StateFlow<SystemRemotePairing?> = _pairing.asStateFlow()

    private val _volume = MutableStateFlow<Triple<Int, Int, Boolean>?>(null)
    val volume: StateFlow<Triple<Int, Int, Boolean>?> = _volume.asStateFlow()

    private val _currentId = MutableStateFlow<String?>(null)
    val currentId: StateFlow<String?> = _currentId.asStateFlow()

    @Volatile private var session: AtvRemoteSession? = null
    private var pairingConn: AtvPairing? = null
    private var connectJob: Job? = null
    private var autoConnected = false

    fun onScreenStart() {
        discovery.start()
        if (!autoConnected) {
            autoConnected = true
            savedTvs.value.firstOrNull()?.let { connect(it) }
        }
    }

    fun onScreenStop() = discovery.stop()

    private var autoPairTried = false

    /** Nothing remembered and exactly one TV on the Wi-Fi: start pairing at once, the code appears on that TV. */
    fun maybeAutoPair() {
        if (autoPairTried || _pairing.value != null || savedTvs.value.isNotEmpty()) return
        val only = foundTvs.value.singleOrNull() ?: return
        autoPairTried = true
        startPairing(only.serviceName, only.host, only.port)
    }

    /** Connects to a remembered TV; uses the address from discovery when the TV has moved to a new IP. */
    fun connect(tv: AtvSavedTv) {
        connectJob?.cancel()
        closeSession()
        _currentId.value = tv.id
        _status.value = SystemRemoteStatus.Connecting(tv.name)
        connectJob = viewModelScope.launch {
            val hosts = (listOfNotNull(foundTvs.value.firstOrNull { it.serviceName == tv.name }?.host) + tv.host).distinct()
            var problem = SystemRemoteProblem.OFFLINE
            var detail: String? = null
            for (host in hosts) {
                val result = withContext(Dispatchers.IO) {
                    runCatching { AtvRemoteSession.connect(identity, host, tv.certSha256, listener(tv), tv.remotePort) }
                }
                result.onSuccess {
                    session = it
                    store.touch(tv.id, host)
                    return@launch
                }
                problem = problemOf(result.exceptionOrNull())
                detail = reasonOf(result.exceptionOrNull(), host)
                if (problem != SystemRemoteProblem.OFFLINE) break
            }
            _status.value = SystemRemoteStatus.Failed(tv.name, problem, detail)
        }
    }

    /** Exception class, its message and the address, one per line, for the small text under the error. */
    private fun reasonOf(t: Throwable?, host: String): String? {
        t ?: return null
        val root = generateSequence(t) { it.cause }.last()
        return listOfNotNull(root.javaClass.simpleName, root.message?.take(70), host).joinToString(System.lineSeparator())
    }

    private fun problemOf(t: Throwable?): SystemRemoteProblem = when (t) {
        is AtvNeedsPairingException -> SystemRemoteProblem.NEEDS_PAIRING
        is AtvCertificateMismatchException -> SystemRemoteProblem.CERT_CHANGED
        is AtvAddressException -> SystemRemoteProblem.NOT_LOCAL
        else -> SystemRemoteProblem.OFFLINE
    }

    private fun listener(tv: AtvSavedTv) = object : AtvSessionListener {
        override fun onReady(info: AtvTvInfo) {
            _status.value = SystemRemoteStatus.Connected(tv.name, listOf(info.vendor, info.model).filter { it.isNotBlank() }.joinToString(" "))
        }
        override fun onVolume(level: Int, max: Int, muted: Boolean) {
            _volume.value = Triple(level, max, muted)
        }
        override fun onKeyboardRequested() {
            _keyboard.value = true
        }
        override fun onClosed(error: Throwable?) {
            if (error == null) return
            val wasReady = _status.value is SystemRemoteStatus.Connected
            // A TV that forgot the phone accepts TLS 1.3 and drops the line right after; ask for pairing then.
            val problem = if (!wasReady && error is javax.net.ssl.SSLException) SystemRemoteProblem.NEEDS_PAIRING
            else SystemRemoteProblem.DISCONNECTED
            if (_currentId.value == tv.id) _status.value = SystemRemoteStatus.Failed(tv.name, problem)
        }
    }

    /** Opens pairing with a TV found on the Wi-Fi or typed by IP; the TV then shows a code. */
    fun startPairing(name: String, host: String, remotePort: Int = AtvTls.REMOTE_PORT) {
        cancelPairing()
        _pairing.value = SystemRemotePairing(name, host, remotePort, SystemRemotePairing.Step.OPENING)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val conn = AtvPairing.open(identity, host)
                    try {
                        conn.also { it.start(clientName()) }
                    } catch (e: Throwable) {
                        conn.close()
                        throw e
                    }
                }
            }
            result.onSuccess { conn ->
                if (_pairing.value?.host != host) {
                    conn.close(); return@launch
                }
                pairingConn = conn
                _pairing.value = _pairing.value?.copy(step = SystemRemotePairing.Step.ENTER_CODE, error = null)
            }.onFailure { e ->
                _pairing.value = _pairing.value?.copy(
                    step = SystemRemotePairing.Step.OPENING,
                    error = if (e is AtvAddressException || e is IOException) SystemRemotePairing.Error.CANNOT_REACH
                    else SystemRemotePairing.Error.FAILED,
                    detail = reasonOf(e, host),
                )
            }
        }
    }

    fun submitCode(code: String) {
        val p = _pairing.value ?: return
        val conn = pairingConn ?: return
        _pairing.value = p.copy(step = SystemRemotePairing.Step.CHECKING, error = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { conn.finish(code) } }
            result.onSuccess {
                val cert = conn.tvCertificateSha256
                conn.close()
                pairingConn = null
                val tv = AtvSavedTv(cert, p.name.ifBlank { p.host }, p.host, AtvTls.PAIRING_PORT, p.remotePort, cert, System.currentTimeMillis())
                store.save(tv)
                _pairing.value = null
                connect(tv)
            }.onFailure { e ->
                val typo = e is AtvPairingException && e.status == AtvPairingException.LOCAL_CODE_MISMATCH
                if (!typo) {
                    conn.close()
                    pairingConn = null
                }
                _pairing.value = p.copy(
                    step = if (typo) SystemRemotePairing.Step.ENTER_CODE else SystemRemotePairing.Step.OPENING,
                    error = when {
                        typo -> SystemRemotePairing.Error.TYPO
                        e is AtvPairingException && e.wrongCode -> SystemRemotePairing.Error.REJECTED
                        else -> SystemRemotePairing.Error.FAILED
                    },
                )
            }
        }
    }

    fun cancelPairing() {
        val conn = pairingConn
        pairingConn = null
        _pairing.value = null
        if (conn != null) viewModelScope.launch(Dispatchers.IO) { conn.close() }
    }

    /** Pairs again with a remembered TV (after a reset of the TV, or when it was replaced). */
    fun repair(tv: AtvSavedTv) {
        store.remove(tv.id)
        startPairing(tv.name, tv.host, tv.remotePort)
    }

    fun forget(tv: AtvSavedTv) {
        if (_currentId.value == tv.id) {
            closeSession()
            _currentId.value = null
            _status.value = SystemRemoteStatus.NoTv
        }
        store.remove(tv.id)
    }

    fun rename(tv: AtvSavedTv, name: String) {
        if (name.isNotBlank()) store.rename(tv.id, name.trim())
    }

    fun key(code: Int, direction: AtvDirection = AtvDirection.SHORT) = send { it.key(code, direction) }

    fun text(value: String) {
        if (value.isNotEmpty()) send { it.text(value) }
    }

    private val _keyboard = MutableStateFlow(false)
    /** The TV opened its keyboard (a text field got focus): the phone shows its own and types live (owner 12.11). */
    val keyboard: StateFlow<Boolean> = _keyboard.asStateFlow()

    fun closeKeyboard() {
        _keyboard.value = false
    }

    /** Live typing: only what changed goes to the TV — backspaces, then the new characters. */
    fun type(old: String, new: String) {
        val edit = tv.own.owntv.extras.atvremote.AtvTyping.diff(old, new)
        repeat(edit.deletes) { send { s -> s.key(AtvKeys.DEL) } }
        if (edit.insert.isNotEmpty()) send { it.text(edit.insert) }
    }

    /** Returns false when [value] is neither a link nor a package name. */
    fun launch(value: String): Boolean {
        val link = runCatching { AtvAppLinks.normalize(value) }.getOrNull() ?: return false
        send { it.launch(link) }
        return true
    }

    // One thread, first in first out: keystrokes reach the TV in the order they were typed.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val sendQueue = Dispatchers.IO.limitedParallelism(1)

    private fun send(action: (AtvRemoteSession) -> Unit) {
        val s = session ?: return
        viewModelScope.launch(sendQueue) {
            runCatching { action(s) }
        }
    }

    private fun clientName(): String = listOfNotNull("YAiPlayer", Build.MODEL?.takeIf { it.isNotBlank() }).joinToString(" · ")

    private fun closeSession() {
        val s = session
        session = null
        _volume.value = null
        if (s != null) viewModelScope.launch(Dispatchers.IO) { s.close() }
    }

    override fun onCleared() {
        discovery.stop()
        val s = session
        val p = pairingConn
        session = null
        pairingConn = null
        // Closing a TLS socket writes close_notify: never on the main thread.
        Thread { s?.close(); p?.close() }.start()
        super.onCleared()
    }
}
