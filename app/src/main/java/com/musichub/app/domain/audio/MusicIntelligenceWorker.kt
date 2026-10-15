package com.musichub.app.domain.audio

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.musichub.app.data.database.AppDatabase
import com.musichub.app.domain.lyrics.LyricsRepository
import com.musichub.app.domain.mood.OnDeviceMusicMoodClassifier
import com.musichub.app.presentation.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * On-Device Background Worker for Sequential Music Intelligence Analysis.
 * Runs in low priority on Dispatchers.IO, analyzing songs one-by-one with
 * safe ONNX memory management, caching in Room DB, and robust error handling.
 */
class MusicIntelligenceWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "MusicIntelligenceSequentialWorker"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = OneTimeWorkRequestBuilder<MusicIntelligenceWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(applicationContext)
        val songDao = db.songDao()
        val moodDao = db.moodPredictionDao()
        val lyricsRepo = LyricsRepository(applicationContext)

        try {
            val allSongEntities = songDao.getAllSongs().first()
            if (allSongEntities.isEmpty()) return@withContext Result.success()

            val analyzedIds = moodDao.getAnalyzedSongIds(OnDeviceMusicMoodClassifier.MODEL_VERSION).toSet()
            val unanalyzed = allSongEntities.filter { it.id.isNotBlank() && !analyzedIds.contains(it.id) }

            if (unanalyzed.isEmpty()) {
                return@withContext Result.success()
            }

            for (entity in unanalyzed) {
                if (isStopped) break

                val songItem = SongItem(
                    id = entity.id,
                    title = entity.title,
                    artist = entity.artist,
                    album = entity.album,
                    duration = formatDuration(entity.duration),
                    durationSec = (entity.duration / 1000).toInt(),
                    artworkUrl = entity.artworkUri ?: "",
                    uriString = entity.path,
                    format = entity.format,
                    isFavorite = entity.isFavorite
                )

                // 1. Analyze Audio (Neural Mood + DSP Features: BPM, Energy, 64-D Embedding, Genre)
                runCatching {
                    OnDeviceMusicMoodClassifier.classify(applicationContext, songItem)
                }

                // 2. Scan Local / Embedded Lyrics
                runCatching {
                    lyricsRepo.getOrFetchLyrics(songItem, allowOnline = false)
                }

                // Low-priority pacing delay between tracks to prevent thermal throttling
                delay(120)
            }

            Result.success()
        } catch (_: Throwable) {
            Result.failure()
        } finally {
            // Guarantee ONNX session memory release on completion or cancellation
            OnDeviceMusicMoodClassifier.closeSession()
        }
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSec = durationMs / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(min, sec)
    }
}
