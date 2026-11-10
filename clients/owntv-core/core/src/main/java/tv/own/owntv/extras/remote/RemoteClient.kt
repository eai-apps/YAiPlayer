// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface RemotePairResult {
    data class Success(val token: String, val tvName: String) : RemotePairResult
    data object InvalidCode : RemotePairResult
    data class RateLimited(val retryAfterSeconds: Long) : RemotePairResult
    data class Failure(val errorMessage: String) : RemotePairResult
}

class RemoteClient(private val pins: android.content.SharedPreferences? = null) {
    private val memoryPins = java.util.concurrent.ConcurrentHashMap<String, String>()
    private fun client(host: String, port: Int, fingerprint: String? = null): OkHttpClient {
        RemoteSecurity.localAddress(host)
        require(port in 1..65535)
        return RemoteTls.client(fingerprint ?: (pins?.getString("$host:$port", null) ?: memoryPins["$host:$port"]) ?: error("Pair first"))
    }
    private fun signed(host: String, port: Int, token: String, path: String, body: String? = null): Request {
        val method = if (body == null) "GET" else "POST"
        val nonce = "${System.currentTimeMillis()}:${java.util.UUID.randomUUID()}"
        return Request.Builder().url("https://$host:$port$path")
            .header("X-Remote-Nonce", nonce)
            .header("X-Remote-Signature", RemoteSecurity.signature(token, method, path, nonce, body.orEmpty()))
            .apply { if (body != null) post(body.toRequestBody(jsonMediaType)) else get() }.build()
    }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun pair(
        host: String,
        port: Int,
        code: String,
        clientName: String,
        deviceId: String,
        fingerprint: String,
    ): RemotePairResult = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("code", code)
            put("name", clientName)
            put("deviceId", deviceId)
        }.toString()

        runCatching {
        RemoteSecurity.localAddress(host)
        val url = "https://$host:$port/pair"
        val request = Request.Builder()
            .url(url)
            .post(payload.toRequestBody(jsonMediaType))
            .build()

            client(host, port, fingerprint).newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                when (response.code) {
                    200 -> {
                        val obj = JSONObject(bodyStr)
                        val token = obj.getString("token")
                        val tvName = obj.optString("tvName", "YAiPlayer")
                        memoryPins["$host:$port"] = fingerprint
                        pins?.edit()?.putString("$host:$port", fingerprint)?.apply()
                        RemotePairResult.Success(token, tvName)
                    }
                    429 -> {
                        val obj = runCatching { JSONObject(bodyStr) }.getOrNull()
                        val retry = obj?.optLong("retryAfterSeconds", 300L) ?: 300L
                        RemotePairResult.RateLimited(retry)
                    }
                    403 -> RemotePairResult.InvalidCode
                    else -> RemotePairResult.Failure("HTTP ${response.code}")
                }
            }
        }.getOrElse { e ->
            RemotePairResult.Failure(e.message ?: "error")
        }
    }

    suspend fun sendCommand(
        host: String,
        port: Int,
        token: String,
        command: RemoteCommand,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = signed(host, port, token, "/cmd", command.toJson())

            client(host, port).newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}")
                }
            }
        }
    }

    suspend fun fetchState(
        host: String,
        port: Int,
        token: String,
    ): Result<RemoteState> = withContext(Dispatchers.IO) {
        runCatching {
            val request = signed(host, port, token, "/state")

            client(host, port).newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    RemoteState.fromJson(body)
                } else {
                    throw IOException("HTTP ${response.code}")
                }
            }
        }
    }

    suspend fun fetchLogs(
        host: String,
        port: Int,
        token: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val request = signed(host, port, token, "/logs")

            client(host, port).newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string().orEmpty()
                } else {
                    throw IOException("HTTP ${response.code}")
                }
            }
        }
    }

    suspend fun fetchFavorites(
        host: String,
        port: Int,
        token: String,
    ): Result<List<RemoteFavoriteItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = signed(host, port, token, "/favorites")

            client(host, port).newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    RemoteFavoriteItem.listFromJson(body)
                } else {
                    throw IOException("HTTP ${response.code}")
                }
            }
        }
    }
}
