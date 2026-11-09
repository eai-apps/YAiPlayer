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

class RemoteClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .build(),
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun pair(
        host: String,
        port: Int,
        code: String,
        clientName: String,
        deviceId: String,
    ): RemotePairResult = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("code", code)
            put("name", clientName)
            put("deviceId", deviceId)
        }.toString()

        val url = "http://$host:$port/pair"
        val request = Request.Builder()
            .url(url)
            .post(payload.toRequestBody(jsonMediaType))
            .build()

        runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                when (response.code) {
                    200 -> {
                        val obj = JSONObject(bodyStr)
                        val token = obj.getString("token")
                        val tvName = obj.optString("tvName", "YAiPlayer")
                        RemotePairResult.Success(token, tvName)
                    }
                    429 -> {
                        val obj = runCatching { JSONObject(bodyStr) }.getOrNull()
                        val retry = obj?.optLong("retryAfterSeconds", 300L) ?: 300L
                        RemotePairResult.RateLimited(retry)
                    }
                    403 -> RemotePairResult.InvalidCode
                    else -> RemotePairResult.Failure("HTTP ${response.code}: $bodyStr")
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
            val url = "http://$host:$port/cmd?token=$token"
            val request = Request.Builder()
                .url(url)
                .post(command.toJson().toRequestBody(jsonMediaType))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("HTTP ${response.code}"))
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
            val url = "http://$host:$port/state?token=$token"
            val request = Request.Builder().url(url).get().build()

            okHttpClient.newCall(request).execute().use { response ->
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
            val url = "http://$host:$port/logs?token=$token"
            val request = Request.Builder().url(url).get().build()

            okHttpClient.newCall(request).execute().use { response ->
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
            val url = "http://$host:$port/favorites?token=$token"
            val request = Request.Builder().url(url).get().build()

            okHttpClient.newCall(request).execute().use { response ->
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
