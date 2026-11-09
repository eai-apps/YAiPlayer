package tv.own.owntv.core.backup

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
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Temporary local HTTP server that transfers settings backups across the home Wi-Fi network.
 * Supports:
 * - EXPORT: serves `yaiplayer-backup-YYYYMMDD.json` to a browser on phone/PC.
 * - RESTORE: serves an HTML upload form, receives the JSON file, and delivers it to the app.
 *
 * Automatically stops after [timeoutMs] (default 5 minutes) or on manual stop.
 * Secured by an ephemeral random 12-character token. Max upload size limited to 5 MB.
 */
class SettingsTransferServer {

    sealed interface State {
        data object Idle : State
        data class Running(
            val mode: Mode,
            val url: String,
            val qrBitmap: Bitmap?,
            val expiresAtMs: Long,
            val transferred: Boolean = false,
        ) : State
        data object Stopped : State
    }

    enum class Mode { EXPORT, RESTORE }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes
        const val MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024L // 5 MB
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile
    private var serverSocket: ServerSocket? = null
    private var loopJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val tokenConsumed = AtomicBoolean(false)

    fun startExport(
        jsonContent: String,
        filename: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
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
        val url = "http://$lanHost:$port/backup?token=$token"
        val qr = CompanionLink.renderQr(url, 360)
        val expiresAt = System.currentTimeMillis() + timeoutMs

        val running = State.Running(
            mode = Mode.EXPORT,
            url = url,
            qrBitmap = qr,
            expiresAtMs = expiresAt,
            transferred = false,
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
                        handleExportClient(client, token, jsonContent, filename)
                    }
                }
            } finally {
                timeoutJob.cancel()
            }
        }

        return running
    }

    fun startRestore(
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        onBackupReceived: (String) -> Unit,
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
        val url = "http://$lanHost:$port/restore?token=$token"
        val qr = CompanionLink.renderQr(url, 360)
        val expiresAt = System.currentTimeMillis() + timeoutMs

        val running = State.Running(
            mode = Mode.RESTORE,
            url = url,
            qrBitmap = qr,
            expiresAtMs = expiresAt,
            transferred = false,
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
                        handleRestoreClient(client, token, onBackupReceived)
                    }
                }
            } finally {
                timeoutJob.cancel()
            }
        }

        return running
    }

    private fun handleExportClient(
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

            if (reqToken.isBlank() || reqToken != validToken) {
                val body = "401 Unauthorized - invalid or expired token\n".toByteArray(Charsets.UTF_8)
                sendResponse(s, 401, "Unauthorized", "text/plain; charset=utf-8", body)
                return
            }

            val bytes = content.toByteArray(Charsets.UTF_8)
            val headers = listOf(
                "Content-Disposition: attachment; filename=\"$filename\"",
                "Cache-Control: no-cache, no-store, must-revalidate",
            )
            sendResponse(s, 200, "OK", "application/json; charset=utf-8", bytes, headers)

            val current = _state.value
            if (current is State.Running) {
                _state.value = current.copy(transferred = true)
            }

            scope.launch {
                delay(2000)
                stop()
            }
        }
    }

    private fun handleRestoreClient(
        client: Socket,
        validToken: String,
        onBackupReceived: (String) -> Unit,
    ) {
        client.use { s ->
            s.soTimeout = 10000
            val input = s.getInputStream()
            val reader = BufferedReader(InputStreamReader(input, Charsets.US_ASCII))
            val requestLine = runCatching { reader.readLine() }.getOrNull() ?: return
            val requestParts = requestLine.split(" ")
            if (requestParts.size < 2) {
                sendResponse(s, 400, "Bad Request", "text/plain", "Bad Request\n".toByteArray(Charsets.UTF_8))
                return
            }

            val method = requestParts[0]
            val rawUri = requestParts[1]
            val path = rawUri.substringBefore("?")
            val query = rawUri.substringAfter("?", "")
            val params = query.split("&").associate { param ->
                val kv = param.split("=", limit = 2)
                kv[0] to (kv.getOrNull(1) ?: "")
            }
            val reqToken = params["token"].orEmpty()

            if (reqToken.isBlank() || reqToken != validToken) {
                val body = "401 Unauthorized - invalid or expired token\n".toByteArray(Charsets.UTF_8)
                sendResponse(s, 401, "Unauthorized", "text/plain; charset=utf-8", body)
                return
            }

            // Read request headers
            var contentLength = -1L
            var contentType = ""
            while (true) {
                val headerLine = runCatching { reader.readLine() }.getOrNull() ?: break
                if (headerLine.isEmpty()) break
                val lower = headerLine.lowercase()
                if (lower.startsWith("content-length:")) {
                    contentLength = headerLine.substringAfter(":").trim().toLongOrNull() ?: -1L
                } else if (lower.startsWith("content-type:")) {
                    contentType = headerLine.substringAfter(":").trim()
                }
            }

            if (method == "GET") {
                val html = renderUploadHtml(validToken)
                sendResponse(s, 200, "OK", "text/html; charset=utf-8", html.toByteArray(Charsets.UTF_8))
                return
            }

            if (method == "POST") {
                if (contentLength > MAX_FILE_SIZE_BYTES) {
                    val body = "413 Payload Too Large - file exceeds 5MB limit\n".toByteArray(Charsets.UTF_8)
                    sendResponse(s, 413, "Payload Too Large", "text/plain; charset=utf-8", body)
                    return
                }

                val bodyBytes = if (contentLength > 0) {
                    val buffer = ByteArray(contentLength.toInt())
                    var totalRead = 0
                    while (totalRead < buffer.size) {
                        val read = input.read(buffer, totalRead, buffer.size - totalRead)
                        if (read < 0) break
                        totalRead += read
                    }
                    buffer.copyOf(totalRead)
                } else {
                    val baos = ByteArrayOutputStream()
                    val temp = ByteArray(4096)
                    while (true) {
                        val read = input.read(temp)
                        if (read < 0) break
                        baos.write(temp, 0, read)
                        if (baos.size() > MAX_FILE_SIZE_BYTES) {
                            val body = "413 Payload Too Large\n".toByteArray(Charsets.UTF_8)
                            sendResponse(s, 413, "Payload Too Large", "text/plain; charset=utf-8", body)
                            return
                        }
                    }
                    baos.toByteArray()
                }

                val rawString = String(bodyBytes, Charsets.UTF_8)
                val jsonPayload = extractJsonPayload(rawString)

                if (jsonPayload.isNullOrBlank()) {
                    val body = "400 Bad Request - no JSON content found\n".toByteArray(Charsets.UTF_8)
                    sendResponse(s, 400, "Bad Request", "text/plain; charset=utf-8", body)
                    return
                }

                // Success response
                val successHtml = renderSuccessHtml()
                sendResponse(s, 200, "OK", "text/html; charset=utf-8", successHtml.toByteArray(Charsets.UTF_8))

                val current = _state.value
                if (current is State.Running) {
                    _state.value = current.copy(transferred = true)
                }

                scope.launch {
                    onBackupReceived(jsonPayload)
                    delay(2000)
                    stop()
                }
            }
        }
    }

    private fun extractJsonPayload(raw: String): String? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start in 0 until end) {
            return raw.substring(start, end + 1)
        }
        return null
    }

    private fun renderUploadHtml(token: String): String =
        """<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>YAiPlayer Settings Restore</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #121212; color: #fff; display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; padding: 20px; box-sizing: border-box; }
    .card { background: #1e1e1e; border: 1px solid #333; border-radius: 16px; padding: 32px; max-width: 440px; width: 100%; box-shadow: 0 8px 24px rgba(0,0,0,0.5); text-align: center; }
    h1 { font-size: 20px; margin-top: 0; margin-bottom: 8px; }
    p { font-size: 14px; color: #aaa; margin-bottom: 24px; line-height: 1.5; }
    .file-input { margin-bottom: 24px; }
    input[type="file"] { display: none; }
    .file-label { display: block; background: #2a2a2a; border: 2px dashed #444; border-radius: 12px; padding: 24px 16px; cursor: pointer; color: #ccc; transition: all 0.2s; word-break: break-all; }
    .file-label:hover { border-color: #6366f1; color: #fff; }
    button { width: 100%; background: #4f46e5; color: #fff; border: none; border-radius: 10px; padding: 14px; font-size: 16px; font-weight: 600; cursor: pointer; transition: background 0.2s; }
    button:hover { background: #4338ca; }
  </style>
</head>
<body>
  <div class="card">
    <h1>Загрузить резервную копию</h1>
    <p>Выберите файл резервной копии (.json) для передачи на ТВ (максимум 5 МБ)</p>
    <form method="POST" action="/restore?token=$token" enctype="multipart/form-data">
      <div class="file-input">
        <label class="file-label" id="file-label">
          <input type="file" name="backup" accept=".json,application/json" required onchange="document.getElementById('file-label').textContent = this.files[0] ? this.files[0].name : 'Выберите файл';">
          <span>Нажмите, чтобы выбрать .json файл</span>
        </label>
      </div>
      <button type="submit">Загрузить на ТВ</button>
    </form>
  </div>
</body>
</html>"""

    private fun renderSuccessHtml(): String =
        """<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>YAiPlayer Settings Restore</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #121212; color: #fff; display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; padding: 20px; box-sizing: border-box; text-align: center; }
    .card { background: #1e1e1e; border: 1px solid #333; border-radius: 16px; padding: 32px; max-width: 440px; width: 100%; box-shadow: 0 8px 24px rgba(0,0,0,0.5); }
    h1 { color: #10b981; font-size: 22px; margin-top: 0; }
    p { font-size: 15px; color: #ccc; line-height: 1.5; margin-bottom: 0; }
  </style>
</head>
<body>
  <div class="card">
    <h1>Файл успешно передан!</h1>
    <p>Проверьте экран телевизора и подтвердите восстановление настроек.</p>
  </div>
</body>
</html>"""

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
