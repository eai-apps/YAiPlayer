// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.radio

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Radio in the background (owner 12.11): a Media3 MediaSessionService around the one [RadioPlayer]. Media3 posts
 * the media notification and keeps the service in the foreground while the radio plays; the lock screen,
 * headphones, Bluetooth and the system pause act on the same player. Started by [RadioPlayer.play]; stops itself
 * when the radio is stopped. Declared in both apps' manifests, enabled only in the build with the radio.
 */
class RadioPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    private val stopWhenIdle = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val p = session?.player ?: return
            if (playbackState == Player.STATE_IDLE && p.mediaItemCount == 0) stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val player = RadioPlayer.get(this).exoPlayer
        val launch = packageManager.getLaunchIntentForPackage(packageName)
            ?: packageManager.getLeanbackLaunchIntentForPackage(packageName)
        val builder = MediaSession.Builder(this, player).setId(SESSION_ID)
        launch?.let {
            it.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            builder.setSessionActivity(PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        session = builder.build().also { addSession(it) }
        player.addListener(stopWhenIdle)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Swiping the app away: keep playing only while the radio actually plays. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.let {
            it.player.removeListener(stopWhenIdle)
            // The player is shared and lives as long as the app; only the session goes.
            it.release()
        }
        session = null
        super.onDestroy()
    }

    private companion object {
        const val SESSION_ID = "yaiplayer-radio"
    }
}
