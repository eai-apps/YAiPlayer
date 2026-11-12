// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.radio

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The radio's own small player (Media3). Plays while the radio screen is open; leaving the screen stops it.
 * Separate from the TV player on purpose: radio must not touch the channel playback or its history.
 */
class RadioPlayer(context: Context) {
    enum class Status { IDLE, BUFFERING, PLAYING, PAUSED, ERROR }

    // Music audio focus: another app's sound pauses the radio, and unplugging headphones does too.
    private val player = ExoPlayer.Builder(context.applicationContext).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            true,
        )
        setHandleAudioBecomingNoisy(true)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var coverJob: Job? = null
    private val _cover = MutableStateFlow<String?>(null)
    /** Cover of the song on the air (iTunes, like the site); null = show the station's logo. */
    val cover: StateFlow<String?> = _cover.asStateFlow()
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
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = update()
            override fun onPlayerError(error: PlaybackException) {
                _status.value = Status.ERROR
            }
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                val t = mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() }
                if (t == _title.value) return
                _title.value = t
                loadCover(t)
            }
        })
    }

    private fun loadCover(title: String?) {
        coverJob?.cancel()
        _cover.value = null
        if (title == null) return
        coverJob = scope.launch { _cover.value = RadioCover.fetch(title) }
    }

    /**
     * «Play wish» ([ExoPlayer.getPlayWhenReady]) decides play or pause, not «is audible right now»: a live
     * stream re-buffers often, and during that [ExoPlayer.isPlaying] is false although the user is playing.
     */
    private fun update() {
        _status.value = when {
            _status.value == Status.ERROR && player.playbackState == Player.STATE_IDLE -> Status.ERROR
            player.playbackState == Player.STATE_IDLE -> Status.IDLE
            !player.playWhenReady -> Status.PAUSED
            player.playbackState == Player.STATE_BUFFERING -> Status.BUFFERING
            else -> Status.PLAYING
        }
    }

    fun play(station: RadioStation) {
        _current.value = station
        _title.value = null
        loadCover(null)
        _status.value = Status.BUFFERING
        player.setMediaItem(MediaItem.fromUri(station.url))
        player.prepare()
        player.play()
    }

    fun toggle() {
        val s = _current.value ?: return
        when {
            _status.value == Status.ERROR || player.playbackState == Player.STATE_IDLE -> play(s)
            player.playWhenReady -> player.pause() // also while buffering
            else -> player.play()
        }
        update()
    }

    fun stop() {
        player.stop()
        _current.value = null
        loadCover(null)
        _status.value = Status.IDLE
    }

    fun release() {
        scope.cancel()
        player.release()
    }
}
