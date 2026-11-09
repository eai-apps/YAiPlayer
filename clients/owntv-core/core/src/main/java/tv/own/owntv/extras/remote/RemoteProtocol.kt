// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.remote

import org.json.JSONObject

object RemoteProtocol {

    const val HEADER_TOKEN = "X-Remote-Token"
    const val PATH_PAIR = "/pair"
    const val PATH_COMMAND = "/command"
    const val PATH_STATE = "/state"
    const val PATH_LOG = "/log"

    fun serializeCommand(cmd: RemoteCommand): String {
        val json = JSONObject()
        when (cmd) {
            RemoteCommand.Up -> json.put("action", "UP")
            RemoteCommand.Down -> json.put("action", "DOWN")
            RemoteCommand.Left -> json.put("action", "LEFT")
            RemoteCommand.Right -> json.put("action", "RIGHT")
            RemoteCommand.Ok -> json.put("action", "OK")
            RemoteCommand.Back -> json.put("action", "BACK")
            RemoteCommand.Home -> json.put("action", "HOME")
            RemoteCommand.Menu -> json.put("action", "MENU")

            RemoteCommand.PlayPause -> json.put("action", "PLAY_PAUSE")
            RemoteCommand.SeekBack10 -> json.put("action", "SEEK_BACK_10")
            RemoteCommand.SeekFwd10 -> json.put("action", "SEEK_FWD_10")
            RemoteCommand.SeekBack60 -> json.put("action", "SEEK_BACK_60")
            RemoteCommand.SeekFwd60 -> json.put("action", "SEEK_FWD_60")
            RemoteCommand.VolumeUp -> json.put("action", "VOLUME_UP")
            RemoteCommand.VolumeDown -> json.put("action", "VOLUME_DOWN")
            RemoteCommand.VolumeMute -> json.put("action", "VOLUME_MUTE")
            RemoteCommand.ChannelNext -> json.put("action", "CHANNEL_NEXT")
            RemoteCommand.ChannelPrev -> json.put("action", "CHANNEL_PREV")
            is RemoteCommand.Digit -> {
                json.put("action", "DIGIT")
                json.put("digit", cmd.digit)
            }
            RemoteCommand.Live -> json.put("action", "LIVE")
            RemoteCommand.Archive -> json.put("action", "ARCHIVE")

            is RemoteCommand.InsertText -> {
                json.put("action", "INSERT_TEXT")
                json.put("text", cmd.text)
            }
            is RemoteCommand.SendToTv -> {
                json.put("action", "SEND_TO_TV")
                json.put("streamUrl", cmd.streamUrl)
                json.put("title", cmd.title)
                cmd.channelId?.let { json.put("channelId", it) }
                cmd.catchupUrl?.let { json.put("catchupUrl", it) }
            }
            RemoteCommand.PullFromTv -> json.put("action", "PULL_FROM_TV")
            RemoteCommand.ExportLog -> json.put("action", "EXPORT_LOG")
        }
        return json.toString()
    }

    fun parseCommand(raw: String): RemoteCommand? {
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        return when (json.optString("action")) {
            "UP" -> RemoteCommand.Up
            "DOWN" -> RemoteCommand.Down
            "LEFT" -> RemoteCommand.Left
            "RIGHT" -> RemoteCommand.Right
            "OK" -> RemoteCommand.Ok
            "BACK" -> RemoteCommand.Back
            "HOME" -> RemoteCommand.Home
            "MENU" -> RemoteCommand.Menu

            "PLAY_PAUSE" -> RemoteCommand.PlayPause
            "SEEK_BACK_10" -> RemoteCommand.SeekBack10
            "SEEK_FWD_10" -> RemoteCommand.SeekFwd10
            "SEEK_BACK_60" -> RemoteCommand.SeekBack60
            "SEEK_FWD_60" -> RemoteCommand.SeekFwd60
            "VOLUME_UP" -> RemoteCommand.VolumeUp
            "VOLUME_DOWN" -> RemoteCommand.VolumeDown
            "VOLUME_MUTE" -> RemoteCommand.VolumeMute
            "CHANNEL_NEXT" -> RemoteCommand.ChannelNext
            "CHANNEL_PREV" -> RemoteCommand.ChannelPrev
            "DIGIT" -> RemoteCommand.Digit(json.optInt("digit", 0))
            "LIVE" -> RemoteCommand.Live
            "ARCHIVE" -> RemoteCommand.Archive

            "INSERT_TEXT" -> RemoteCommand.InsertText(json.optString("text", ""))
            "SEND_TO_TV" -> RemoteCommand.SendToTv(
                streamUrl = json.optString("streamUrl", ""),
                title = json.optString("title", ""),
                channelId = if (json.has("channelId")) json.optLong("channelId") else null,
                catchupUrl = json.optString("catchupUrl").takeIf { it.isNotBlank() }
            )
            "PULL_FROM_TV" -> RemoteCommand.PullFromTv
            "EXPORT_LOG" -> RemoteCommand.ExportLog
            else -> null
        }
    }

    fun serializeState(state: RemoteState): String {
        val json = JSONObject()
        json.put("isPlaying", state.isPlaying)
        json.put("title", state.title)
        json.put("channelOrMovieName", state.channelOrMovieName)
        json.put("positionMs", state.positionMs)
        json.put("durationMs", state.durationMs)
        json.put("volume", state.volume)
        json.put("isMuted", state.isMuted)
        state.streamUrl?.let { json.put("streamUrl", it) }
        state.catchupUrl?.let { json.put("catchupUrl", it) }
        return json.toString()
    }

    fun parseState(raw: String): RemoteState {
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return RemoteState()
        return RemoteState(
            isPlaying = json.optBoolean("isPlaying", false),
            title = json.optString("title", ""),
            channelOrMovieName = json.optString("channelOrMovieName", ""),
            positionMs = json.optLong("positionMs", 0L),
            durationMs = json.optLong("durationMs", 0L),
            volume = json.optInt("volume", 50),
            isMuted = json.optBoolean("isMuted", false),
            streamUrl = json.optString("streamUrl").takeIf { it.isNotBlank() },
            catchupUrl = json.optString("catchupUrl").takeIf { it.isNotBlank() }
        )
    }
}
