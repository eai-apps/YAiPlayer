// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import tv.own.owntv.core.companion.CompanionLink
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class RemoteTvServer(
    private val context: Context,
    val pairingManager: RemotePairingManager = RemotePairingManager()
) {
    sealed interface ServerState {
        data object Stopped : ServerState
        data class Running(val port: Int, val host: String, val pin: String?) : ServerState
    }

    private val _serverState = MutableStateFlow<ServerState>(ServerState.Stopped)
    val serverState: StateFlow<ServerState> = _serverState.asStateFlow()

    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var nsdRegistrationListener: NsdManager.RegistrationListener? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var listenJob: Job? = null
    private val isRunning = AtomicBoolean(false)

    var onCommandReceived: ((RemoteCommand) -> Unit)? = null
    var currentStateProvider: (() -> RemoteState)? = null
    var logReportProvider: (() -> String)? = null

    fun start(tvName: String = defaultTvName()): Boolean {
        if (isRunning.get()) return true

        val socket = runCatching { ServerSocket(0) }.getOrNull() ?: return false
        serverSocket = socket
        val port = socket.localPort
        isRunning.set(true)

        val lanHost = CompanionLink.lanHosts().firstOrNull(CompanionLink::isPrivateLan)
            ?: CompanionLink.lanHosts().firstOrNull()
            ?: "127.0.0.1"

        _serverState.value = ServerState.Running(
            port = port,
            host = lanHost,
            pin = pairingManager.getCurrentPin()
        )

        registerNsd(tvName, port)

        listenJob = scope.launch {
            try {
                while (isRunning.get() && !socket.isClosed) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    launch { handleClient(client, tvName) }
                }
            } finally {
                stop()
            }
        }

        return true
    }

    fun requestPairingPin(): String {
        val pin = pairingManager.generateNewPin()
        val current = _serverState.value
        if (current is ServerState.Running) {
            _serverState.value = current.copy(pin = pin)
        }
        return pin
    }

    fun stop() {
        if (!isRunning.compareAndSet(true, false)) return
        listenJob?.cancel()
        listenJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        unregisterNsd()
        _serverState.value = ServerState.Stopped
    }

    private fun handleClient(client: Socket, tvName: String) {
        client.soTimeout = 10_000
        client.use { s ->
            val reader = BufferedReader(InputStreamReader(s.getInputStream()))
            val firstLine = reader.readLine() ?: return
            val parts = firstLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val path = parts[1].split("?")[0]

            var token: String? = null
            var contentLength = 0

            var header = reader.readLine()
            while (!header.isNullOrBlank()) {
                val colon = header.indexOf(':')
                if (colon != -1) {
                    val key = header.substring(0, colon).trim()
                    val value = header.substring(colon + 1).trim()
                    if (key.equals(RemoteProtocol.HEADER_TOKEN, ignoreCase = true)) {
                        token = value
                    } else if (key.equals("Content-Length", ignoreCase = true)) {
                        contentLength = value.toIntOrNull() ?: 0
                    }
                }
                header = reader.readLine()
            }

            val body = if (contentLength > 0) {
                val buf = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val r = reader.read(buf, read, contentLength - read)
                    if (r == -1) break
                    read += r
                }
                String(buf, 0, read)
            } else ""

            when (path) {
                RemoteProtocol.PATH_PAIR -> {
                    if (method == "POST") {
                        val json = runCatching { JSONObject(body) }.getOrNull()
                        val pin = json?.optString("pin") ?: ""
                        val deviceName = json?.optString("deviceName") ?: "Mobile"

                        when (val result = pairingManager.verifyPin(pin, deviceName, tvName)) {
                            is PairingResult.Success -> {
                                sendJsonResponse(s, 200, JSONObject().apply {
                                    put("token", result.token)
                                    put("tvName", result.tvName)
                                }.toString())
                            }
                            is PairingResult.Blocked -> {
                                sendJsonResponse(s, 429, JSONObject().apply {
                                    put("error", "blocked")
                                    put("waitSeconds", result.waitSeconds)
                                }.toString())
                            }
                            PairingResult.Expired -> {
                                sendJsonResponse(s, 410, JSONObject().apply {
                                    put("error", "expired")
                                }.toString())
                            }
                            PairingResult.InvalidPin -> {
                                sendJsonResponse(s, 401, JSONObject().apply {
                                    put("error", "invalid_pin")
                                }.toString())
                            }
                        }
                    } else {
                        sendResponse(s, 405, "Method Not Allowed")
                    }
                }
                RemoteProtocol.PATH_COMMAND -> {
                    if (token == null || !pairingManager.isTokenValid(token)) {
                        sendResponse(s, 401, "Unauthorized")
                        return
                    }
                    val cmd = RemoteProtocol.parseCommand(body)
                    if (cmd != null) {
                        onCommandReceived?.invoke(cmd)
                        sendJsonResponse(s, 200, """{"status":"ok"}""")
                    } else {
                        sendResponse(s, 400, "Bad Request")
                    }
                }
                RemoteProtocol.PATH_STATE -> {
                    val state = currentStateProvider?.invoke() ?: RemoteState()
                    sendJsonResponse(s, 200, RemoteProtocol.serializeState(state))
                }
                RemoteProtocol.PATH_LOG -> {
                    if (token == null || !pairingManager.isTokenValid(token)) {
                        sendResponse(s, 401, "Unauthorized")
                        return
                    }
                    val log = logReportProvider?.invoke() ?: "Diagnostic report not available."
                    sendTextResponse(s, 200, log)
                }
                else -> sendResponse(s, 404, "Not Found")
            }
        }
    }

    private fun sendJsonResponse(socket: Socket, code: Int, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val out = socket.getOutputStream()
        val header = "HTTP/1.1 $code OK\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        out.write(header.toByteArray(Charsets.US_ASCII))
        out.write(bytes)
        out.flush()
    }

    private fun sendTextResponse(socket: Socket, code: Int, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val out = socket.getOutputStream()
        val header = "HTTP/1.1 $code OK\r\n" +
            "Content-Type: text/plain; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        out.write(header.toByteArray(Charsets.US_ASCII))
        out.write(bytes)
        out.flush()
    }

    private fun sendResponse(socket: Socket, code: Int, text: String) {
        val out = socket.getOutputStream()
        val header = "HTTP/1.1 $code $text\r\n" +
            "Content-Type: text/plain; charset=utf-8\r\n" +
            "Content-Length: 0\r\n" +
            "Connection: close\r\n\r\n"
        out.write(header.toByteArray(Charsets.US_ASCII))
        out.flush()
    }

    private fun registerNsd(name: String, port: Int) {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = name
            serviceType = "_yaiplayer._tcp"
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo?) {}
            override fun onRegistrationFailed(info: NsdServiceInfo?, errorCode: Int) {}
            override fun onServiceUnregistered(info: NsdServiceInfo?) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo?, errorCode: Int) {}
        }
        nsdRegistrationListener = listener
        runCatching { nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    private fun unregisterNsd() {
        val listener = nsdRegistrationListener ?: return
        nsdRegistrationListener = null
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        runCatching { nsdManager.unregisterService(listener) }
    }

    private fun defaultTvName(): String {
        val model = Build.MODEL ?: "Android TV"
        return "YAiPlayer ($model)"
    }
}
