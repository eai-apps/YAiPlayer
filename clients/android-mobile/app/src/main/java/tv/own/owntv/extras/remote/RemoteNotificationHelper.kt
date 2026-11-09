// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.remote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tv.own.owntv.mobile.MainActivity

class RemoteActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val remoteClient = RemoteClient()
        val prefs = context.getSharedPreferences("mobile_remote_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("saved_tvs", null) ?: return
        val saved = runCatching {
            val arr = org.json.JSONArray(raw)
            if (arr.length() > 0) RemoteSavedTv.fromJsonObject(arr.getJSONObject(0)) else null
        }.getOrNull() ?: return

        val pendingResult = goAsync()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_PLAY_PAUSE -> remoteClient.sendCommand(saved.host, saved.port, saved.token, RemoteCommand.Media(RemoteMediaKey.PLAY_PAUSE))
                    ACTION_CHANNEL_UP -> remoteClient.sendCommand(saved.host, saved.port, saved.token, RemoteCommand.Media(RemoteMediaKey.CHANNEL_UP))
                    ACTION_CHANNEL_DOWN -> remoteClient.sendCommand(saved.host, saved.port, saved.token, RemoteCommand.Media(RemoteMediaKey.CHANNEL_DOWN))
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "tv.own.owntv.extras.remote.ACTION_PLAY_PAUSE"
        const val ACTION_CHANNEL_UP = "tv.own.owntv.extras.remote.ACTION_CHANNEL_UP"
        const val ACTION_CHANNEL_DOWN = "tv.own.owntv.extras.remote.ACTION_CHANNEL_DOWN"
    }
}

object RemoteNotificationHelper {
    private const val CHANNEL_ID = "phone_remote_notification"
    private const val NOTIFICATION_ID = 4280

    fun showNotification(
        context: Context,
        tvName: String,
        isPlaying: Boolean,
        contentTitle: String?,
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "YAiPlayer Remote",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Persistent remote control notification"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPi = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        fun createActionPi(action: String, reqCode: Int): PendingIntent {
            val intent = Intent(context, RemoteActionReceiver::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(
                context,
                reqCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseText = if (isPlaying) "Pause" else "Play"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(tvName)
            .setContentText(contentTitle ?: "Connected to TV")
            .setContentIntent(openPi)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_previous, "CH -", createActionPi(RemoteActionReceiver.ACTION_CHANNEL_DOWN, 1))
            .addAction(playPauseIcon, playPauseText, createActionPi(RemoteActionReceiver.ACTION_PLAY_PAUSE, 2))
            .addAction(android.R.drawable.ic_media_next, "CH +", createActionPi(RemoteActionReceiver.ACTION_CHANNEL_UP, 3))
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }

    fun dismissNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.cancel(NOTIFICATION_ID)
    }
}
