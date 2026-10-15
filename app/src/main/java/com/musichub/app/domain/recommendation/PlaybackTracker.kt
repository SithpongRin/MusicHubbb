package com.musichub.app.domain.recommendation

import android.content.Context
import com.musichub.app.data.database.AppDatabase
import com.musichub.app.data.database.PlaybackEventEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-device implicit behavioral feedback tracker.
 * Silently records playback interactions to learn user music preferences without cloud telemetry.
 */
object PlaybackTracker {

    suspend fun recordPlaybackEvent(
        context: Context,
        songId: String,
        durationListenedMs: Long,
        totalDurationMs: Long,
        isFavorite: Boolean
    ) = withContext(Dispatchers.IO) {
        if (songId.isBlank() || totalDurationMs <= 0L) return@withContext

        val ratio = (durationListenedMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
        val wasSkippedEarly = durationListenedMs < 10_000L && ratio < 0.25f

        val event = PlaybackEventEntity(
            songId = songId,
            timestamp = System.currentTimeMillis(),
            durationListenedMs = durationListenedMs,
            completionRatio = ratio,
            wasSkippedEarly = wasSkippedEarly,
            isFavorite = isFavorite
        )

        val db = AppDatabase.getDatabase(context)
        db.playbackEventDao().insertEvent(event)
    }

    /**
     * Computes the learned user affinity score for a given song [-3.0 .. 10.0].
     */
    suspend fun computeSongAffinity(
        context: Context,
        songId: String,
        isFavorite: Boolean
    ): Float = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val dao = db.playbackEventDao()

        val completedPlays = dao.getCompletedPlayCount(songId)
        val earlySkips = dao.getEarlySkipCount(songId)

        var affinity = 0.0f
        affinity += (completedPlays * 1.2f).coerceAtMost(6.0f)
        affinity -= (earlySkips * 1.5f).coerceAtMost(4.5f)
        if (isFavorite) {
            affinity += 3.0f
        }

        affinity
    }
}
