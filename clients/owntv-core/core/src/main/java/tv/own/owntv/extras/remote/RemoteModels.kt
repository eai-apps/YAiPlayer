// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.remote

/**
 * Commands that can be sent from the Phone Remote to the TV.
 */
sealed interface RemoteCommand {
    data object Up : RemoteCommand
    data object Down : RemoteCommand
    data object Left : RemoteCommand
    data object Right : RemoteCommand
    data object Ok : RemoteCommand
    data object Back : RemoteCommand
    data object Home : RemoteCommand
    data object Menu : RemoteCommand

    data object PlayPause : RemoteCommand
    data object SeekBack10 : RemoteCommand
    data object SeekFwd10 : RemoteCommand
    data object SeekBack60 : RemoteCommand
    data object SeekFwd60 : RemoteCommand
    data object VolumeUp : RemoteCommand
    data object VolumeDown : RemoteCommand
    data object VolumeMute : RemoteCommand
    data object ChannelNext : RemoteCommand
    data object ChannelPrev : RemoteCommand
    data class Digit(val digit: Int) : RemoteCommand
    data object Live : RemoteCommand
    data object Archive : RemoteCommand

    data class InsertText(val text: String) : RemoteCommand
    data class SendToTv(
        val streamUrl: String,
        val title: String,
        val channelId: Long? = null,
        val catchupUrl: String? = null
    ) : RemoteCommand
    data object PullFromTv : RemoteCommand
    data object ExportLog : RemoteCommand
}

/**
 * Live playback state broadcasted from TV to Phone.
 */
data class RemoteState(
    val isPlaying: Boolean = false,
    val title: String = "",
    val channelOrMovieName: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Int = 50,
    val isMuted: Boolean = false,
    val streamUrl: String? = null,
    val catchupUrl: String? = null,
)

/**
 * Discovered TV device on the local network.
 */
data class RemoteDevice(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val lastSeenMs: Long = System.currentTimeMillis()
)

/**
 * Entitlement gate for Remote functionality.
 * Designed for future monetization while keeping all features 100% free now.
 */
interface RemoteEntitlement {
    fun canUseRemote(): Boolean
}

object DefaultRemoteEntitlement : RemoteEntitlement {
    override fun canUseRemote(): Boolean = true
}

/**
 * Result of a PIN verification or pairing attempt.
 */
sealed interface PairingResult {
    data class Success(val token: String, val tvName: String) : PairingResult
    data object InvalidPin : PairingResult
    data class Blocked(val waitSeconds: Long) : PairingResult
    data object Expired : PairingResult
}
