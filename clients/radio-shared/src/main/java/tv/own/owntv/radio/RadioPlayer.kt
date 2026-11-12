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
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The radio's own small player (Media3), one per app process ([get]). Keeps playing after the radio screen is
 * left (owner 12.11): [RadioPlaybackService] publishes it as a MediaSession, so the notification, the lock
 * screen, headphones / Bluetooth keys and the system pause control the same player as the on-screen button.
 * Separate from the TV player on purpose: radio must not touch the channel playback or its history; when a
 * channel starts, the TV player takes audio focus and the radio pauses by itself.
 */
class RadioPlayer private constructor(context: Context) {
    private val app = context.applicationContext
    enum class Status { IDLE, BUFFERING, PLAYING, PAUSED, ERROR }

    // Music audio focus: another app's sound pauses the radio, and unplugging headphones does too.
    private val player = ExoPlayer.Builder(app).build().apply {
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

    /** The Media3 player itself, for the MediaSession of [RadioPlaybackService]. */
    internal val exoPlayer: Player get() = player

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
        _status.value = RadioPlayback.status(_status.value == Status.ERROR, player.playbackState, player.playWhenReady)
    }

    fun play(station: RadioStation) {
        _current.value = station
        _title.value = null
        loadCover(null)
        _status.value = Status.BUFFERING
        val meta = MediaMetadata.Builder()
            .setStation(station.name)
            .setTitle(station.name)
            .setArtist(listOfNotNull(station.country, station.bitrate.takeIf { it > 0 }?.let { "$it kbps" }).joinToString(" · "))
            .apply { station.favicon?.takeIf { it.startsWith("http") }?.let { setArtworkUri(android.net.Uri.parse(it)) } }
            .build()
        player.setMediaItem(MediaItem.Builder().setUri(station.url).setMediaId(station.id).setMediaMetadata(meta).build())
        player.prepare()
        player.play()
        // The service keeps the process alive and shows the notification while the radio plays.
        runCatching { app.startService(android.content.Intent(app, RadioPlaybackService::class.java)) }
    }

    fun toggle() {
        val s = _current.value ?: return
        when (RadioPlayback.toggle(_status.value == Status.ERROR, player.playbackState, player.playWhenReady)) {
            RadioPlayback.Action.RESTART -> play(s)
            RadioPlayback.Action.PAUSE -> player.pause() // also while buffering
            RadioPlayback.Action.RESUME -> player.play()
        }
        update()
    }

    /** The remote's separate Play / Pause keys: [on] plays or resumes, otherwise pauses; no-op when nothing is chosen. */
    fun setPlaying(on: Boolean) {
        val s = _current.value ?: return
        if (on) {
            if (_status.value == Status.ERROR || player.playbackState == Player.STATE_IDLE) play(s) else player.play()
        } else {
            player.pause()
        }
        update()
    }

    fun stop() {
        player.stop()
        player.clearMediaItems()
        _current.value = null
        loadCover(null)
        _status.value = Status.IDLE
    }

    companion object {
        @Volatile private var instance: RadioPlayer? = null

        /** The one radio player of this app; must be first called on the main thread (Media3 binds to it). */
        fun get(context: Context): RadioPlayer =
            instance ?: synchronized(this) { instance ?: RadioPlayer(context).also { instance = it } }
    }
}

/**
 * What the play / pause button does and what it shows, as plain functions (tested in RadioPlaybackTest).
 * «Play wish» (playWhenReady) decides, not «audible right now»: a live stream re-buffers often, and during that
 * isPlaying is false although the user is playing; a pause pressed then must still pause.
 */
object RadioPlayback {
    enum class Action { RESTART, PAUSE, RESUME }

    fun toggle(error: Boolean, playbackState: Int, playWhenReady: Boolean): Action = when {
        error || playbackState == Player.STATE_IDLE -> Action.RESTART
        playWhenReady -> Action.PAUSE
        else -> Action.RESUME
    }

    fun status(error: Boolean, playbackState: Int, playWhenReady: Boolean): RadioPlayer.Status = when {
        error && playbackState == Player.STATE_IDLE -> RadioPlayer.Status.ERROR
        playbackState == Player.STATE_IDLE -> RadioPlayer.Status.IDLE
        !playWhenReady -> RadioPlayer.Status.PAUSED
        playbackState == Player.STATE_BUFFERING -> RadioPlayer.Status.BUFFERING
        else -> RadioPlayer.Status.PLAYING
    }

    /** The button shows «pause» while the user wants sound (playing or buffering), «play» otherwise. */
    fun showsPause(status: RadioPlayer.Status): Boolean =
        status == RadioPlayer.Status.PLAYING || status == RadioPlayer.Status.BUFFERING
}
