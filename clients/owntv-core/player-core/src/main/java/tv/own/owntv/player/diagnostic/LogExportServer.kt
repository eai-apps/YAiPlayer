package tv.own.owntv.player.diagnostic

import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.own.owntv.core.companion.CompanionLink
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Temporary local HTTP server that serves the diagnostic log to devices on the same Wi-Fi network.
 * Automatically stops after [timeoutMs] (default 5 minutes), upon manual stop, or after download.
 * Requires a single-use random token in the URL.
 */
class LogExportServer {

    sealed interface State {
        data object Idle : State
        data class Running(
            val url: String,
            val qrBitmap: Bitmap?,
            val expiresAtMs: Long,
            val downloaded: Boolean = false,
        ) : State
        data object Stopped : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile
    private var serverSocket: ServerSocket? = null
    private var loopJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val tokenConsumed = AtomicBoolean(false)

    fun start(
        reportContent: String,
        filename: String = DiagnosticLogExporter.formatFilename(),
        timeoutMs: Long = 5 * 60 * 1000L,
    ): State.Running? {
        stop()
        tokenConsumed.set(false)

        val socket = runCatching { ServerSocket(0) }.getOrNull() ?: return null
        serverSocket = socket
        val port = socket.localPort

        val token = UUID.randomUUID().toString().replace("-", "").take(12)
        val lanHost = CompanionLink.lanHosts().firstOrNull(CompanionLink::isPrivateLan)
            ?: CompanionLink.lanHosts().firstOrNull()
            ?: "127.0.0.1"
        val url = "http://$lanHost:$port/log.txt?token=$token"
        val qr = CompanionLink.renderQr(url, 360)
        val expiresAt = System.currentTimeMillis() + timeoutMs

        val running = State.Running(
            url = url,
            qrBitmap = qr,
            expiresAtMs = expiresAt,
            downloaded = false,
        )
        _state.value = running

        loopJob = scope.launch {
            val timeoutJob = launch {
                delay(timeoutMs)
                stop()
            }

            try {
                while (!socket.isClosed) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    launch {
                        handleClient(client, token, reportContent, filename)
                    }
                }
            } finally {
                timeoutJob.cancel()
            }
        }

        return running
    }

    private fun handleClient(
        client: Socket,
        validToken: String,
        content: String,
        filename: String,
    ) {
        client.use { s ->
            s.soTimeout = 5000
            val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.US_ASCII))
            val line = runCatching { reader.readLine() }.getOrNull() ?: return
            val parts = line.split(" ")
            if (parts.size < 2 || parts[0] != "GET") {
                sendResponse(s, 400, "Bad Request", "text/plain", "Bad Request\n".toByteArray(Charsets.UTF_8))
                return
            }

            val rawUri = parts[1]
            val query = rawUri.substringAfter("?", "")
            val params = query.split("&").associate { param ->
                val kv = param.split("=", limit = 2)
                kv[0] to (kv.getOrNull(1) ?: "")
            }
            val reqToken = params["token"].orEmpty()

            if (reqToken.isBlank() || reqToken != validToken || tokenConsumed.get()) {
                val body = "401 Unauthorized - invalid or expired token\n".toByteArray(Charsets.UTF_8)
                sendResponse(s, 401, "Unauthorized", "text/plain; charset=utf-8", body)
                return
            }

            // Valid token: mark single-use token consumed
            tokenConsumed.set(true)
            val bytes = content.toByteArray(Charsets.UTF_8)
            val headers = listOf(
                "Content-Disposition: attachment; filename=\"$filename\"",
                "Cache-Control: no-cache, no-store, must-revalidate",
            )
            sendResponse(s, 200, "OK", "text/plain; charset=utf-8", bytes, headers)

            val current = _state.value
            if (current is State.Running) {
                _state.value = current.copy(downloaded = true)
            }

            // Stop server shortly after successful download
            scope.launch {
                delay(2000)
                stop()
            }
        }
    }

    private fun sendResponse(
        socket: Socket,
        code: Int,
        status: String,
        contentType: String,
        body: ByteArray,
        extraHeaders: List<String> = emptyList(),
    ) {
        runCatching {
            val out = socket.getOutputStream()
            val headerBuilder = StringBuilder()
            headerBuilder.append("HTTP/1.1 $code $status\r\n")
            headerBuilder.append("Content-Type: $contentType\r\n")
            headerBuilder.append("Content-Length: ${body.size}\r\n")
            headerBuilder.append("Connection: close\r\n")
            for (h in extraHeaders) {
                headerBuilder.append("$h\r\n")
            }
            headerBuilder.append("\r\n")
            out.write(headerBuilder.toString().toByteArray(Charsets.US_ASCII))
            out.write(body)
            out.flush()
        }
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        loopJob?.cancel()
        loopJob = null
        if (_state.value is State.Running) {
            _state.value = State.Stopped
        }
    }
}
