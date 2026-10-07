package com.musichub.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.musichub.app.data.database.AppDatabase

class MusicHubApplication : Application() {

    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackChannel = NotificationChannel(
                CHANNEL_PLAYBACK_ID,
                "Playback Controls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Media playback foreground notification controls"
                setShowBadge(false)
            }

            val downloadChannel = NotificationChannel(
                CHANNEL_DOWNLOAD_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progress notifications for audio file downloads"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(playbackChannel)
            notificationManager?.createNotificationChannel(downloadChannel)
        }
    }

    companion object {
        const val CHANNEL_PLAYBACK_ID = "musichub_playback_channel"
        const val CHANNEL_DOWNLOAD_ID = "musichub_download_channel"
    }
}
