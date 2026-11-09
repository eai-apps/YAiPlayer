// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class RemoteClient(
    private val context: Context,
    val entitlement: RemoteEntitlement = DefaultRemoteEntitlement
) {
    private val _discoveredDevices = MutableStateFlow<List<RemoteDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<RemoteDevice>> = _discoveredDevices.asStateFlow()

    private val _activeDevice = MutableStateFlow<RemoteDevice?>(null)
    val activeDevice: StateFlow<RemoteDevice?> = _activeDevice.asStateFlow()

    private val _authToken = MutableStateFlow<String?>(null)
    val authToken: StateFlow<String?> = _authToken.asStateFlow()

    private val _tvState = MutableStateFlow(RemoteState())
    val tvState: StateFlow<RemoteState> = _tvState.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    @Volatile private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun startDiscovery() {
        if (!entitlement.canUseRemote()) return
        stopDiscovery()

        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        _isSearching.value = true

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType.contains("_yaiplayer._tcp")) {
                    resolveService(nsdManager, service)
                }
            }
            override fun onServiceLost(service: NsdServiceInfo) {
                _discoveredDevices.value = _discoveredDevices.value.filterNot { it.name == service.serviceName }
            }
            override fun onDiscoveryStopped(serviceType: String) {
                _isSearching.value = false
            }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                _isSearching.value = false
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                _isSearching.value = false
            }
        }
        discoveryListener = listener
        runCatching {
            nsdManager.discoverServices("_yaiplayer._tcp", NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    private fun resolveService(nsdManager: NsdManager, service: NsdServiceInfo) {
        val resolver = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host?.hostAddress ?: return
                val port = serviceInfo.port
                val device = RemoteDevice(
                    id = "$host:$port",
                    name = serviceInfo.serviceName,
                    host = host,
                    port = port
                )
                val current = _discoveredDevices.value.toMutableList()
                current.removeAll { it.id == device.id }
                current.add(device)
                _discoveredDevices.value = current

                if (_activeDevice.value == null) {
                    _activeDevice.value = device
                }
            }
        }
        runCatching { nsdManager.resolveService(service, resolver) }
    }

    fun stopDiscovery() {
        val listener = discoveryListener ?: return
        discoveryListener = null
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        runCatching { nsdManager.stopServiceDiscovery(listener) }
        _isSearching.value = false
    }

    fun selectDevice(device: RemoteDevice, token: String? = null) {
        _activeDevice.value = device
        _authToken.value = token
    }

    suspend fun pairWithPin(pin: String, deviceName: String = "Mobile"): PairingResult = withContext(Dispatchers.IO) {
        val device = _activeDevice.value ?: return@withContext PairingResult.InvalidPin
        val url = URL("http://${device.host}:${device.port}${RemoteProtocol.PATH_PAIR}")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 4000
            readTimeout = 4000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }

        try {
            val body = JSONObject().apply {
                put("pin", pin)
                put("deviceName", deviceName)
            }.toString()

            OutputStreamWriter(conn.outputStream).use { it.write(body) }

            val code = conn.responseCode
            val responseText = if (code in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            } else {
                BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream)).use { it.readText() }
            }

            val json = runCatching { JSONObject(responseText) }.getOrNull()
            when (code) {
                200 -> {
                    val token = json?.optString("token") ?: ""
                    _authToken.value = token
                    PairingResult.Success(token, json?.optString("tvName") ?: device.name)
                }
                429 -> PairingResult.Blocked(json?.optLong("waitSeconds") ?: 300L)
                410 -> PairingResult.Expired
                else -> PairingResult.InvalidPin
            }
        } catch (e: Exception) {
            PairingResult.InvalidPin
        } finally {
            conn.disconnect()
        }
    }

    suspend fun sendCommand(cmd: RemoteCommand): Boolean = withContext(Dispatchers.IO) {
        val device = _activeDevice.value ?: return@withContext false
        val token = _authToken.value ?: return@withContext false

        val url = URL("http://${device.host}:${device.port}${RemoteProtocol.PATH_COMMAND}")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 3000
            readTimeout = 3000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty(RemoteProtocol.HEADER_TOKEN, token)
        }

        try {
            val body = RemoteProtocol.serializeCommand(cmd)
            OutputStreamWriter(conn.outputStream).use { it.write(body) }
            conn.responseCode in 200..299
        } catch (e: Exception) {
            false
        } finally {
            conn.disconnect()
        }
    }

    suspend fun fetchState(): RemoteState = withContext(Dispatchers.IO) {
        val device = _activeDevice.value ?: return@withContext RemoteState()
        val url = URL("http://${device.host}:${device.port}${RemoteProtocol.PATH_STATE}")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 3000
            readTimeout = 3000
        }

        try {
            if (conn.responseCode in 200..299) {
                val text = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val state = RemoteProtocol.parseState(text)
                _tvState.value = state
                state
            } else {
                _tvState.value
            }
        } catch (e: Exception) {
            _tvState.value
        } finally {
            conn.disconnect()
        }
    }

    suspend fun exportLogFromTv(): String? = withContext(Dispatchers.IO) {
        val device = _activeDevice.value ?: return@withContext null
        val token = _authToken.value ?: return@withContext null

        val url = URL("http://${device.host}:${device.port}${RemoteProtocol.PATH_LOG}")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6000
            readTimeout = 6000
            setRequestProperty(RemoteProtocol.HEADER_TOKEN, token)
        }

        try {
            if (conn.responseCode in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            } else null
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
