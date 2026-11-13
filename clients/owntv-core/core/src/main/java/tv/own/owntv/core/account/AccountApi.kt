// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.account

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** A server answer that is not 2xx: [code] is the contract's error code (docs/SYNC_API.md §2), "network" when offline. */
class AccountApiException(val status: Int, val code: String, message: String? = null) : Exception(message ?: code)

/**
 * The account server (yai-cloud `worker/`, contract docs/SYNC_API.md). Plain HTTP + JSON; no secret ever leaves the
 * device unencrypted except the account password itself (over HTTPS, stored there only as a hash).
 */
class AccountApi(
    private val http: OkHttpClient,
    private val base: String = AccountConfig.API_BASE,
    private val clientTag: String = "${AccountConfig.TENANT}/app/1",
) {
    private val json = "application/json".toMediaType()

    suspend fun call(method: String, path: String, body: JSONObject? = null, token: String? = null): Any? = withContext(Dispatchers.IO) {
        val rb = Request.Builder().url(base.trimEnd('/') + path).header("X-YAi-Client", clientTag)
        if (token != null) rb.header("Authorization", "Bearer $token")
        val payload = body?.toString()?.toRequestBody(json) ?: if (method == "GET" || method == "DELETE") null else "{}".toRequestBody(json)
        rb.method(method, payload)
        val res = try { http.newCall(rb.build()).execute() } catch (e: java.io.IOException) {
            throw AccountApiException(0, "network", e.message)
        }
        res.use { r ->
            val text = r.body.string()
            val parsed: Any? = when {
                text.isBlank() -> null
                text.trimStart().startsWith("[") -> JSONArray(text)
                else -> runCatching { JSONObject(text) }.getOrNull()
            }
            if (!r.isSuccessful) {
                val err = (parsed as? JSONObject)?.optJSONObject("error")
                throw AccountApiException(r.code, err?.optString("code")?.ifBlank { null } ?: "server_error", err?.optString("message"))
            }
            parsed
        }
    }

    suspend fun obj(method: String, path: String, body: JSONObject? = null, token: String? = null): JSONObject =
        (call(method, path, body, token) as? JSONObject) ?: JSONObject()

    suspend fun arr(path: String, token: String?): JSONArray = (call("GET", path, null, token) as? JSONArray) ?: JSONArray()

    fun device(name: String, kind: String) = JSONObject().put("name", name.take(60)).put("kind", kind)
}
