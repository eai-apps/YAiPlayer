package tv.own.owntv.core.player

import android.content.Context

/**
 * "FFmpeg audio decoder": whether ExoPlayer may decode sound with the engine's FFmpeg, for formats the
 * device has no decoder for (DTS, TrueHD, MP2 on many boxes). On by default since beta-12 (owner 14.11,
 * VS KM6: many channels silent or failing in ExoPlayer — their first track is MP2, the box has no MP2
 * decoder, and ExoPlayer then fails with DECODER_INIT_FAILED for audio/mpeg-L2). The device's own decoders
 * and passthrough still win; FFmpeg takes only what they cannot. Off: the audio path from before FFmpeg audio.
 *
 * Read once per process, at app start ([load]), and fixed for the life of the process: a change only
 * takes effect after a restart, so no player ever runs half on one audio path and half on the other.
 * Kept per device in its own preferences file — it is about what this device's decoders do, so it is not
 * part of backups or local sync.
 */
object FfmpegAudioSetting {

    private const val PREFS_NAME = "owntv_audio_engine"
    private const val KEY_FFMPEG_AUDIO = "ffmpeg_audio"
    private const val KEY_RESET_V4 = "ffmpeg_audio_on_v4"
    /** Also for installs that never touched the switch: they had no MP2 sound, not a choice. */
    private const val DEFAULT_ON = true

    /** What this process plays with. False until [load] runs. */
    @Volatile
    var active: Boolean = false
        private set

    /** Called by each app in `Application.onCreate`, before Koin starts and before any player exists. */
    fun load(context: Context) {
        resetOnceForV4(context)
        active = saved(context)
    }

    /**
     * v4.0.2 (owner 17.11): on the same VS KM6 the beta played every channel in ExoPlayer in ~1 s, while the
     * release (updated from 3.x) showed MP2 sound as NO_UNSUPPORTED_SUBTYPE — FFmpeg audio was off in that
     * install's saved preferences (left from before it became the default) — so each channel showed a picture
     * for a second, went dark and fell back to mpv. The beta-20 speed reference needs it ON: switch it on
     * once for every install; a later choice in Settings still sticks.
     */
    private fun resetOnceForV4(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_RESET_V4, false)) return
        prefs.edit().putBoolean(KEY_FFMPEG_AUDIO, true).putBoolean(KEY_RESET_V4, true).commit()
    }

    /** The saved choice — what the next start of the app will use. */
    fun saved(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_FFMPEG_AUDIO, DEFAULT_ON)

    /** Saves the choice for the next start. Written synchronously: the caller restarts the app right after. */
    fun save(context: Context, on: Boolean): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_FFMPEG_AUDIO, on).commit()
}
