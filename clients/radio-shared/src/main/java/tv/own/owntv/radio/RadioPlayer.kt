// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.radio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The radio's own small player (Media3). Plays while the radio screen is open; leaving the screen stops it.
 * Separate from the TV player on purpose: radio must not touch the channel playback or its history.
 */
class RadioPlayer(context: Context) {
    enum class Status { IDLE, BUFFERING, PLAYING, PAUSED, ERROR }

    private val player = ExoPlayer.Builder(context.applicationContext).build()
    private val _current = MutableStateFlow<RadioStation?>(null)
    val current: StateFlow<RadioStation?> = _current.asStateFlow()
    private val _status = MutableStateFlow(Status.IDLE)
    val status: StateFlow<Status> = _status.asStateFlow()
    private val _title = MutableStateFlow<String?>(null)
    /** What the station says is on now (ICY stream title), when it says anything. */
    val title: StateFlow<String?> = _title.asStateFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) = update()
            override fun onIsPlayingChanged(isPlaying: Boolean) = update()
            override fun onPlayerError(error: PlaybackException) {
                _status.value = Status.ERROR
            }
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                _title.value = mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() }
            }
        })
    }

    private fun update() {
        _status.value = when {
            player.isPlaying -> Status.PLAYING
            player.playbackState == Player.STATE_BUFFERING -> Status.BUFFERING
            player.playbackState == Player.STATE_READY -> Status.PAUSED
            _status.value == Status.ERROR -> Status.ERROR
            else -> Status.IDLE
        }
    }

    fun play(station: RadioStation) {
        _current.value = station
        _title.value = null
        _status.value = Status.BUFFERING
        player.setMediaItem(MediaItem.fromUri(station.url))
        player.prepare()
        player.play()
    }

    fun toggle() {
        val s = _current.value ?: return
        when {
            player.isPlaying -> player.pause()
            _status.value == Status.ERROR || player.playbackState == Player.STATE_IDLE -> play(s)
            else -> player.play()
        }
    }

    fun stop() {
        player.stop()
        _current.value = null
        _status.value = Status.IDLE
    }

    fun release() = player.release()
}
