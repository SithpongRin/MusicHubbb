package com.musichub.app.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.musichub.app.presentation.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MediaPlaybackService : Service {

    constructor() : super()

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastTitle: String = ""
    private var lastArtist: String = ""
    private var lastArtworkUrl: String = ""
    private var lastIsPlaying: Boolean = false
    private var currentArtworkBitmap: Bitmap? = null

    companion object {
        const val CHANNEL_ID = "musichub_playback_channel"
        const val CHANNEL_NAME = "Music Playback"
        const val NOTIFICATION_ID = 2001

        const val ACTION_PLAY = "com.musichub.app.ACTION_PLAY"
        const val ACTION_PAUSE = "com.musichub.app.ACTION_PAUSE"
        const val ACTION_PREV = "com.musichub.app.ACTION_PREV"
        const val ACTION_NEXT = "com.musichub.app.ACTION_NEXT"
        const val ACTION_STOP = "com.musichub.app.ACTION_STOP"
        const val ACTION_UPDATE = "com.musichub.app.ACTION_UPDATE"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_ARTWORK_URL = "extra_artwork_url"
        const val EXTRA_IS_PLAYING = "extra_is_playing"

        var onActionReceived: ((String) -> Unit)? = null

        fun updateNotification(
            context: Context,
            title: String,
            artist: String,
            artworkUrl: String,
            isPlaying: Boolean
        ) {
            val intent = Intent(context, MediaPlaybackService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTIST, artist)
                putExtra(EXTRA_ARTWORK_URL, artworkUrl)
                putExtra(EXTRA_IS_PLAYING, isPlaying)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Background start restriction safety
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MediaPlaybackService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                // Safety
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_UPDATE -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: lastTitle
                val artist = intent.getStringExtra(EXTRA_ARTIST) ?: lastArtist
                val artworkUrl = intent.getStringExtra(EXTRA_ARTWORK_URL) ?: lastArtworkUrl
                val isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, lastIsPlaying)

                val artworkChanged = artworkUrl != lastArtworkUrl || currentArtworkBitmap == null
                lastTitle = title
                lastArtist = artist
                lastArtworkUrl = artworkUrl
                lastIsPlaying = isPlaying

                if (artworkChanged && artworkUrl.isNotBlank()) {
                    serviceScope.launch {
                        val loadedBitmap = loadArtworkBitmap(artworkUrl)
                        currentArtworkBitmap = loadedBitmap
                        postForegroundNotification(lastTitle, lastArtist, currentArtworkBitmap, lastIsPlaying)
                    }
                } else {
                    postForegroundNotification(lastTitle, lastArtist, currentArtworkBitmap, lastIsPlaying)
                }
            }
            ACTION_PLAY, ACTION_PAUSE, ACTION_PREV, ACTION_NEXT -> {
                onActionReceived?.invoke(action)
            }
            ACTION_STOP -> {
                onActionReceived?.invoke(action)
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun postForegroundNotification(
        title: String,
        artist: String,
        artwork: Bitmap?,
        isPlaying: Boolean
    ) {
        val notification = buildNotification(title, artist, artwork, isPlaying)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Safety against background service start exception
        }
    }

    private fun buildNotification(
        title: String,
        artist: String,
        artwork: Bitmap?,
        isPlaying: Boolean
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Prev Action
        val prevIntent = Intent(this, MediaPlaybackService::class.java).apply { action = ACTION_PREV }
        val prevPending = PendingIntent.getService(this, 1, prevIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        // Play/Pause Action
        val playPauseAction = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        val playPauseIntent = Intent(this, MediaPlaybackService::class.java).apply { action = playPauseAction }
        val playPausePending = PendingIntent.getService(this, 2, playPauseIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        // Next Action
        val nextIntent = Intent(this, MediaPlaybackService::class.java).apply { action = ACTION_NEXT }
        val nextPending = PendingIntent.getService(this, 3, nextIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        // Stop Action
        val stopIntent = Intent(this, MediaPlaybackService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(this, 4, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (isPlaying) "Pause" else "Play"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title.ifBlank { "MusicHub" })
            .setContentText(artist.ifBlank { "Playing" })
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPending)
            .addAction(playPauseIcon, playPauseTitle, playPausePending)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", stopPending)

        if (artwork != null) {
            builder.setLargeIcon(artwork)
        }

        return builder.build()
    }

    private suspend fun loadArtworkBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val loader = ImageLoader(this@MediaPlaybackService)
            val request = ImageRequest.Builder(this@MediaPlaybackService)
                .data(url)
                .allowHardware(false)
                .build()
            val result = loader.execute(request)
            if (result is SuccessResult) {
                (result.drawable as? BitmapDrawable)?.bitmap
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "MusicHub foreground playback controls"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
