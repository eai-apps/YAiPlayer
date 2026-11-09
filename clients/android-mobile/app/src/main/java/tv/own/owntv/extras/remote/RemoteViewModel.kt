// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.remote

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class RemoteSavedTv(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val token: String,
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("host", host)
        put("port", port)
        put("token", token)
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): RemoteSavedTv = RemoteSavedTv(
            id = obj.getString("id"),
            name = obj.getString("name"),
            host = obj.getString("host"),
            port = obj.getInt("port"),
            token = obj.getString("token"),
        )
    }
}

sealed interface ConnectionStatus {
    data object Disconnected : ConnectionStatus
    data object Connecting : ConnectionStatus
    data class Connected(val tvName: String) : ConnectionStatus
    data class Error(val message: String) : ConnectionStatus
}

class RemoteViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("mobile_remote_prefs", Context.MODE_PRIVATE)
    private val client = RemoteClient()
    private val discoveryManager = RemoteDiscoveryManager(application)

    val discoveredTvs: StateFlow<List<RemoteDiscoveryInfo>> = discoveryManager.discover()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _savedTvs = MutableStateFlow<List<RemoteSavedTv>>(loadSavedTvs())
    val savedTvs: StateFlow<List<RemoteSavedTv>> = _savedTvs.asStateFlow()

    private val _selectedTv = MutableStateFlow<RemoteSavedTv?>(_savedTvs.value.firstOrNull())
    val selectedTv: StateFlow<RemoteSavedTv?> = _selectedTv.asStateFlow()

    private val _status = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _tvState = MutableStateFlow(RemoteState())
    val tvState: StateFlow<RemoteState> = _tvState.asStateFlow()

    private val _tvFavorites = MutableStateFlow<List<RemoteFavoriteItem>>(emptyList())
    val tvFavorites: StateFlow<List<RemoteFavoriteItem>> = _tvFavorites.asStateFlow()

    private val _hapticEnabled = MutableStateFlow(prefs.getBoolean("haptic_feedback", true))
    val hapticEnabled: StateFlow<Boolean> = _hapticEnabled.asStateFlow()

    private val _hardwareVolumeEnabled = MutableStateFlow(prefs.getBoolean("hardware_volume", true))
    val hardwareVolumeEnabled: StateFlow<Boolean> = _hardwareVolumeEnabled.asStateFlow()

    private val _pairingTarget = MutableStateFlow<RemoteDiscoveryInfo?>(null)
    val pairingTarget: StateFlow<RemoteDiscoveryInfo?> = _pairingTarget.asStateFlow()

    private val _pairingError = MutableStateFlow<String?>(null)
    val pairingError: StateFlow<String?> = _pairingError.asStateFlow()

    private var pollJob: Job? = null

    val clientDeviceId: String
        get() = prefs.getString("client_device_id", null) ?: run {
            val id = UUID.randomUUID().toString().replace("-", "").take(16)
            prefs.edit().putString("client_device_id", id).apply()
            id
        }

    init {
        _selectedTv.value?.let { connectTo(it) }
    }

    fun selectTv(tv: RemoteSavedTv) {
        _selectedTv.value = tv
        connectTo(tv)
    }

    fun setHapticEnabled(enabled: Boolean) {
        _hapticEnabled.value = enabled
        prefs.edit().putBoolean("haptic_feedback", enabled).apply()
    }

    fun setHardwareVolumeEnabled(enabled: Boolean) {
        _hardwareVolumeEnabled.value = enabled
        prefs.edit().putBoolean("hardware_volume", enabled).apply()
    }

    fun showPairingDialog(info: RemoteDiscoveryInfo) {
        _pairingTarget.value = info
        _pairingError.value = null
    }

    fun dismissPairingDialog() {
        _pairingTarget.value = null
        _pairingError.value = null
    }

    fun submitPairing(pin: String) {
        val target = _pairingTarget.value ?: return
        viewModelScope.launch {
            _status.value = ConnectionStatus.Connecting
            val phoneModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "Phone" }
            when (val res = client.pair(target.host, target.port, pin, phoneModel, clientDeviceId)) {
                is RemotePairResult.Success -> {
                    val saved = RemoteSavedTv(
                        id = target.deviceId.ifBlank { UUID.randomUUID().toString() },
                        name = res.tvName,
                        host = target.host,
                        port = target.port,
                        token = res.token,
                    )
                    saveTv(saved)
                    _selectedTv.value = saved
                    _pairingTarget.value = null
                    _pairingError.value = null
                    connectTo(saved)
                }
                is RemotePairResult.RateLimited -> {
                    _pairingError.value = "LOCKED"
                    _status.value = ConnectionStatus.Error("Rate limited")
                }
                is RemotePairResult.InvalidCode -> {
                    _pairingError.value = "INVALID"
                    _status.value = ConnectionStatus.Error("Invalid PIN")
                }
                is RemotePairResult.Failure -> {
                    _pairingError.value = res.errorMessage
                    _status.value = ConnectionStatus.Error(res.errorMessage)
                }
            }
        }
    }

    fun connectTo(tv: RemoteSavedTv) {
        pollJob?.cancel()
        _status.value = ConnectionStatus.Connecting
        pollJob = viewModelScope.launch {
            var consecutiveFailures = 0
            while (isActive) {
                val stateRes = client.fetchState(tv.host, tv.port, tv.token)
                if (stateRes.isSuccess) {
                    consecutiveFailures = 0
                    _status.value = ConnectionStatus.Connected(tv.name)
                    val st = stateRes.getOrThrow()
                    _tvState.value = st
                    if (st.isPlaying) {
                        RemoteNotificationHelper.showNotification(
                            getApplication(),
                            tv.name,
                            true,
                            st.title ?: st.channelName,
                        )
                    } else {
                        RemoteNotificationHelper.dismissNotification(getApplication())
                    }

                    // Fetch favorites occasionally
                    if (_tvFavorites.value.isEmpty()) {
                        client.fetchFavorites(tv.host, tv.port, tv.token).onSuccess { favs ->
                            _tvFavorites.value = favs
                        }
                    }
                } else {
                    consecutiveFailures++
                    if (consecutiveFailures >= 3) {
                        _status.value = ConnectionStatus.Disconnected
                    }
                }
                delay(1_800L)
            }
        }
    }

    fun retryConnection() {
        _selectedTv.value?.let { connectTo(it) }
    }

    fun sendNav(key: RemoteNavKey) = sendCommand(RemoteCommand.Nav(key))

    fun sendMedia(key: RemoteMediaKey) = sendCommand(RemoteCommand.Media(key))

    fun sendNumber(digit: Int) = sendCommand(RemoteCommand.Number(digit))

    fun sendText(query: String) = sendCommand(RemoteCommand.Text(query))

    fun sendTune(channelId: Long? = null, streamUrl: String? = null, title: String? = null) =
        sendCommand(RemoteCommand.Tune(channelId, streamUrl, title))

    private fun sendCommand(command: RemoteCommand) {
        val tv = _selectedTv.value ?: return
        viewModelScope.launch {
            client.sendCommand(tv.host, tv.port, tv.token, command)
        }
    }

    fun forgetDevice(id: String) {
        val current = _savedTvs.value.toMutableList()
        current.removeAll { it.id == id }
        _savedTvs.value = current
        saveSavedTvs(current)
        if (_selectedTv.value?.id == id) {
            pollJob?.cancel()
            _selectedTv.value = current.firstOrNull()
            _status.value = ConnectionStatus.Disconnected
            _selectedTv.value?.let { connectTo(it) }
        }
    }

    fun downloadLogsAndShare(context: Context, onReady: () -> Unit) {
        val tv = _selectedTv.value ?: return
        viewModelScope.launch {
            val logsRes = client.fetchLogs(tv.host, tv.port, tv.token)
            if (logsRes.isSuccess) {
                val text = logsRes.getOrThrow()
                withContext(Dispatchers.IO) {
                    val cacheDir = File(context.cacheDir, "tv_logs").apply { mkdirs() }
                    val file = File(cacheDir, "yaiplayer_tv_log_${System.currentTimeMillis()}.txt")
                    file.writeText(text, Charsets.UTF_8)
                    val uri = FileProvider.getUriForFile(context, context.packageName + ".share", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "YAiPlayer TV Diagnostic Report")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share TV Log").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
                onReady()
            }
        }
    }

    private fun saveTv(tv: RemoteSavedTv) {
        val list = _savedTvs.value.filterNot { it.id == tv.id } + tv
        _savedTvs.value = list
        saveSavedTvs(list)
    }

    private fun loadSavedTvs(): List<RemoteSavedTv> {
        val raw = prefs.getString("saved_tvs", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i -> RemoteSavedTv.fromJsonObject(arr.getJSONObject(i)) }
        }.getOrDefault(emptyList())
    }

    private fun saveSavedTvs(list: List<RemoteSavedTv>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJsonObject()) }
        prefs.edit().putString("saved_tvs", arr.toString()).apply()
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
        RemoteNotificationHelper.dismissNotification(getApplication())
    }
}
