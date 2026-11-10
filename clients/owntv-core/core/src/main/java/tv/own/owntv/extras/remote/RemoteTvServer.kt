// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.NetworkInterface
import java.util.Collections
import javax.net.ssl.SSLServerSocketFactory

interface RemoteCommandHandler {
    fun handleCommand(command: RemoteCommand): Boolean
}

interface RemoteStateProvider {
    fun getState(): RemoteState
    fun getFavorites(): List<RemoteFavoriteItem>
    fun getDiagnosticLogs(): String
}

class RemoteTvServer(
    val pairingManager: RemotePairingManager,
    private val commandHandler: RemoteCommandHandler,
    private val stateProvider: RemoteStateProvider,
    private val tvName: String = "YAiPlayer TV",
    private val socketFactory: () -> javax.net.ServerSocketFactory = { RemoteTls.serverFactory() },
) {
    sealed interface ServerState {
        data object Stopped : ServerState
        data class Running(val port: Int) : ServerState
    }

    private val _state = MutableStateFlow<ServerState>(ServerState.Stopped)
    val state: StateFlow<ServerState> = _state.asStateFlow()

    @Volatile
    private var serverSocket: ServerSocket? = null
    private var loopJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val activeSockets = java.util.concurrent.ConcurrentHashMap.newKeySet<Socket>()
    private val slots = java.util.concurrent.Semaphore(8)

    val host: String? get() = serverSocket?.inetAddress?.hostAddress
    val isRunning: Boolean get() = _state.value is ServerState.Running
    val port: Int? get() = (_state.value as? ServerState.Running)?.port

    @Synchronized
    fun start(preferredPort: Int = 0): Int? {
        stop()
        val address = Collections.list(NetworkInterface.getNetworkInterfaces()).asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { Collections.list(it.inetAddresses).asSequence() }.firstOrNull { RemoteSecurity.isLocal(it) } ?: return null
        val socket = runCatching { socketFactory().createServerSocket(preferredPort, 8, address) }.getOrNull() ?: return null
        serverSocket = socket
        val localPort = socket.localPort
        _state.value = ServerState.Running(localPort)

        loopJob = scope.launch {
            try {
                while (!socket.isClosed) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    if (!RemoteSecurity.isLocal(client.inetAddress) || !slots.tryAcquire()) { client.close(); continue }
                    activeSockets.add(client)
                    launch {
                        try { runCatching { handleClient(client) } } finally { activeSockets.remove(client); slots.release() }
                    }
                }
            } finally {
                _state.value = ServerState.Stopped
            }
        }
        return localPort
    }

    @Synchronized
    fun stop() {
        pairingManager.invalidateCode()
        activeSockets.forEach { runCatching { it.close() } }
        activeSockets.clear()
        runCatching { serverSocket?.close() }
        serverSocket = null
        loopJob?.cancel()
        loopJob = null
        _state.value = ServerState.Stopped
    }

    private fun handleClient(client: Socket) {
        client.use { s ->
            s.soTimeout = 10_000
            val input = s.getInputStream()
            fun line(): String {
                val bytes = java.io.ByteArrayOutputStream()
                while (true) {
                    val byte = input.read()
                    require(byte >= 0)
                    if (byte == 10) break
                    require(bytes.size() < 4096)
                    if (byte != 13) bytes.write(byte)
                }
                return bytes.toString("UTF-8")
            }
            val parts = line().split(" ")
            require(parts.size == 3)
            val method = parts[0]
            val path = parts[1]
            val headers = mutableMapOf<String, String>()
            var headerBytes = 0
            while (true) {
                val header = line()
                if (header.isEmpty()) break
                headerBytes += header.length
                require(headerBytes <= 8192 && header.contains(':'))
                val key = header.substringBefore(':').lowercase()
                require(!headers.containsKey(key))
                headers[key] = header.substringAfter(':').trim()
            }
            require(!headers.containsKey("transfer-encoding"))
            val contentLength = headers["content-length"]?.toInt() ?: 0
            require(contentLength in 0..16384)
            val bytes = ByteArray(contentLength)
            var offset = 0
            while (offset < bytes.size) {
                val count = input.read(bytes, offset, bytes.size - offset)
                require(count > 0)
                offset += count
            }
            val body = bytes.toString(Charsets.UTF_8)
            val out = s.getOutputStream()
            if (!isRunning) return
            if (path != "/pair" && !pairingManager.authorize(method, path,
                    headers["x-remote-nonce"].orEmpty(), headers["x-remote-signature"].orEmpty(), body)) {
                writeJsonResponse(out, 401, "{\"status\":\"unauthorized\"}")
                return
            }
            when {
                method == "POST" && path == "/pair" -> {
                    val obj = runCatching { JSONObject(body) }.getOrDefault(JSONObject())
                    val code = obj.optString("code")
                    val clientName = obj.optString("name", "Phone")
                    val deviceId = obj.optString("deviceId", "device")

                    when (val result = pairingManager.attemptPair(code, clientName, deviceId)) {
                        is RemotePairingManager.PairAttemptResult.Success -> {
                            val res = JSONObject().apply {
                                put("status", "ok")
                                put("token", result.token)
                                put("tvName", tvName)
                            }
                            writeJsonResponse(out, 200, res.toString())
                        }
                        is RemotePairingManager.PairAttemptResult.RateLimited -> {
                            val res = JSONObject().apply {
                                put("status", "rate_limited")
                                put("retryAfterSeconds", result.remainingSeconds)
                            }
                            writeJsonResponse(out, 429, res.toString())
                        }
                        is RemotePairingManager.PairAttemptResult.InvalidCode -> {
                            val res = JSONObject().apply {
                                put("status", "invalid_code")
                            }
                            writeJsonResponse(out, 403, res.toString())
                        }
                    }
                }
                method == "POST" && path == "/cmd" -> {
                    val command = RemoteCommand.fromJson(body)
                    if (command != null && commandHandler.handleCommand(command)) {
                        writeJsonResponse(out, 200, "{\"status\":\"ok\"}")
                    } else {
                        writeJsonResponse(out, 400, "{\"status\":\"bad_command\"}")
                    }
                }
                method == "GET" && path == "/state" -> {
                    val st = stateProvider.getState()
                    writeJsonResponse(out, 200, st.toJson())
                }
                method == "GET" && path == "/logs" -> {
                    val logs = ""
                    writePlainTextResponse(out, 200, logs)
                }
                method == "GET" && path == "/favorites" -> {
                    val favs = emptyList<RemoteFavoriteItem>()
                    writeJsonResponse(out, 200, RemoteFavoriteItem.listToJson(favs))
                }
                else -> {
                    writeJsonResponse(out, 404, "{\"status\":\"not_found\"}")
                }
            }
        }
    }

    private fun writeJsonResponse(out: java.io.OutputStream, code: Int, json: String) {
        val statusText = when (code) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            429 -> "Too Many Requests"
            else -> "Error"
        }
        val bytes = json.toByteArray(Charsets.UTF_8)
        val headers = "HTTP/1.1 $code $statusText\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        out.write(headers.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    private fun writePlainTextResponse(out: java.io.OutputStream, code: Int, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val headers = "HTTP/1.1 $code OK\r\n" +
            "Content-Type: text/plain; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        out.write(headers.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }
}
