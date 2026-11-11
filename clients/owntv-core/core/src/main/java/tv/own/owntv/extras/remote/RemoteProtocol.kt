// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.remote

import org.json.JSONArray
import org.json.JSONObject

enum class RemoteNavKey {
    UP, DOWN, LEFT, RIGHT, OK, BACK, HOME, MENU
}

enum class RemoteMediaKey {
    PLAY_PAUSE,
    REWIND_10, FORWARD_10,
    REWIND_60, FORWARD_60,
    VOLUME_UP, VOLUME_DOWN, VOLUME_MUTE,
    CHANNEL_UP, CHANNEL_DOWN,
    TO_LIVE, ARCHIVE
}

sealed class RemoteCommand {
    data class Nav(val key: RemoteNavKey) : RemoteCommand()
    data class Media(val key: RemoteMediaKey) : RemoteCommand()
    data class Number(val digit: Int) : RemoteCommand()
    data class Text(val query: String) : RemoteCommand()
    data class Tune(val channelId: Long? = null, val streamUrl: String? = null, val title: String? = null) : RemoteCommand()

    fun toJson(): String {
        val obj = JSONObject()
        when (this) {
            is Nav -> {
                obj.put("type", "nav")
                obj.put("key", key.name)
            }
            is Media -> {
                obj.put("type", "media")
                obj.put("key", key.name)
            }
            is Number -> {
                obj.put("type", "number")
                obj.put("digit", digit)
            }
            is Text -> {
                obj.put("type", "text")
                obj.put("query", query)
            }
            is Tune -> {
                obj.put("type", "tune")
                channelId?.let { obj.put("channelId", it) }
                streamUrl?.let { obj.put("streamUrl", it) }
                title?.let { obj.put("title", it) }
            }
        }
        return obj.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): RemoteCommand? = runCatching {
            val obj = JSONObject(jsonStr)
            when (obj.optString("type")) {
                "nav" -> Nav(RemoteNavKey.valueOf(obj.getString("key")))
                "media" -> Media(RemoteMediaKey.valueOf(obj.getString("key")))
                "number" -> Number(obj.getInt("digit"))
                "text" -> Text(obj.getString("query"))
                "tune" -> Tune(
                    channelId = if (obj.has("channelId")) obj.getLong("channelId") else null,
                    streamUrl = obj.optString("streamUrl").takeIf { it.isNotBlank() },
                    title = obj.optString("title").takeIf { it.isNotBlank() },
                )
                else -> null
            }
        }.getOrNull()
    }
}

data class RemoteState(
    val title: String? = null,
    val channelName: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volumePct: Int = 100,
    val isMuted: Boolean = false,
) {
    fun toJson(): String {
        val obj = JSONObject()
        title?.let { obj.put("title", it) }
        channelName?.let { obj.put("channelName", it) }
        obj.put("isPlaying", isPlaying)
        obj.put("positionMs", positionMs)
        obj.put("durationMs", durationMs)
        obj.put("volumePct", volumePct)
        obj.put("isMuted", isMuted)
        return obj.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): RemoteState = runCatching {
            val obj = JSONObject(jsonStr)
            RemoteState(
                title = obj.optString("title").takeIf { it.isNotBlank() },
                channelName = obj.optString("channelName").takeIf { it.isNotBlank() },
                isPlaying = obj.optBoolean("isPlaying", false),
                positionMs = obj.optLong("positionMs", 0L),
                durationMs = obj.optLong("durationMs", 0L),
                volumePct = obj.optInt("volumePct", 100),
                isMuted = obj.optBoolean("isMuted", false),
            )
        }.getOrDefault(RemoteState())
    }
}

data class PairedDevice(
    val id: String,
    val name: String,
    val token: String,
    val pairedAtMs: Long,
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("token", token)
        put("pairedAtMs", pairedAtMs)
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): PairedDevice = PairedDevice(
            id = obj.getString("id"),
            name = obj.getString("name"),
            token = obj.getString("token"),
            pairedAtMs = obj.optLong("pairedAtMs", 0L),
        )
    }
}

data class RemoteDiscoveryInfo(
    val name: String,
    val host: String,
    val port: Int,
    val deviceId: String = "",
)

data class RemoteFavoriteItem(
    val channelId: Long,
    val name: String,
    val logoUrl: String? = null,
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("channelId", channelId)
        put("name", name)
        logoUrl?.let { put("logoUrl", it) }
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): RemoteFavoriteItem = RemoteFavoriteItem(
            channelId = obj.getLong("channelId"),
            name = obj.getString("name"),
            logoUrl = obj.optString("logoUrl").takeIf { it.isNotBlank() },
        )

        fun listToJson(list: List<RemoteFavoriteItem>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJsonObject()) }
            return arr.toString()
        }

        fun listFromJson(jsonStr: String): List<RemoteFavoriteItem> = runCatching {
            val arr = JSONArray(jsonStr)
            List(arr.length()) { i -> fromJsonObject(arr.getJSONObject(i)) }
        }.getOrDefault(emptyList())
    }
}
