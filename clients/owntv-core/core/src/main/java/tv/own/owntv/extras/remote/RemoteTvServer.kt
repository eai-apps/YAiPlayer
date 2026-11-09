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

    val isRunning: Boolean get() = _state.value is ServerState.Running
    val port: Int? get() = (_state.value as? ServerState.Running)?.port

    @Synchronized
    fun start(preferredPort: Int = 0): Int? {
        stop()
        val socket = runCatching { ServerSocket(preferredPort) }.getOrNull() ?: return null
        serverSocket = socket
        val localPort = socket.localPort
        _state.value = ServerState.Running(localPort)

        loopJob = scope.launch {
            try {
                while (!socket.isClosed) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    launch {
                        handleClient(client)
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
        runCatching { serverSocket?.close() }
        serverSocket = null
        loopJob?.cancel()
        loopJob = null
        _state.value = ServerState.Stopped
    }

    private fun handleClient(client: Socket) {
        client.use { s ->
            s.soTimeout = 10_000
            val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            val reqLine = reader.readLine() ?: return
            val parts = reqLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val rawPath = parts[1]
            val path = rawPath.substringBefore('?')
            val queryParams = parseQueryParams(rawPath.substringAfter('?', ""))

            var contentLength = 0
            while (true) {
                val header = reader.readLine() ?: break
                if (header.isEmpty()) break
                val lower = header.lowercase()
                if (lower.startsWith("content-length:")) {
                    contentLength = header.substringAfter(':').trim().toIntOrNull() ?: 0
                }
            }

            val body = if (contentLength > 0) {
                val buf = CharArray(contentLength)
                var readTotal = 0
                while (readTotal < contentLength) {
                    val count = reader.read(buf, readTotal, contentLength - readTotal)
                    if (count < 0) break
                    readTotal += count
                }
                String(buf, 0, readTotal)
            } else ""

            val out = s.getOutputStream()
            val token = queryParams["token"]

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
                    if (!pairingManager.isValidToken(token)) {
                        writeJsonResponse(out, 401, "{\"status\":\"unauthorized\"}")
                        return
                    }
                    val command = RemoteCommand.fromJson(body)
                    if (command != null && commandHandler.handleCommand(command)) {
                        writeJsonResponse(out, 200, "{\"status\":\"ok\"}")
                    } else {
                        writeJsonResponse(out, 400, "{\"status\":\"bad_command\"}")
                    }
                }
                method == "GET" && path == "/state" -> {
                    if (!pairingManager.isValidToken(token)) {
                        writeJsonResponse(out, 401, "{\"status\":\"unauthorized\"}")
                        return
                    }
                    val st = stateProvider.getState()
                    writeJsonResponse(out, 200, st.toJson())
                }
                method == "GET" && path == "/logs" -> {
                    if (!pairingManager.isValidToken(token)) {
                        writeJsonResponse(out, 401, "{\"status\":\"unauthorized\"}")
                        return
                    }
                    val logs = stateProvider.getDiagnosticLogs()
                    writePlainTextResponse(out, 200, logs)
                }
                method == "GET" && path == "/favorites" -> {
                    if (!pairingManager.isValidToken(token)) {
                        writeJsonResponse(out, 401, "{\"status\":\"unauthorized\"}")
                        return
                    }
                    val favs = stateProvider.getFavorites()
                    writeJsonResponse(out, 200, RemoteFavoriteItem.listToJson(favs))
                }
                else -> {
                    writeJsonResponse(out, 404, "{\"status\":\"not_found\"}")
                }
            }
        }
    }

    private fun parseQueryParams(queryString: String): Map<String, String> {
        if (queryString.isBlank()) return emptyMap()
        return queryString.split('&').mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                key to value
            } else null
        }.toMap()
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
