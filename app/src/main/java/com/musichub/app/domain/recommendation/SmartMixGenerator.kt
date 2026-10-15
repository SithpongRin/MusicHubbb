package com.musichub.app.domain.recommendation

import android.content.Context
import com.musichub.app.data.database.AppDatabase
import com.musichub.app.domain.mood.HybridMoodFusionEngine
import com.musichub.app.domain.mood.OnDeviceMusicMoodClassifier
import com.musichub.app.presentation.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-device Smart Playlist & Mix Generator.
 * Curates mood playlists with energy pacing and learned user preferences.
 */
object SmartMixGenerator {

    enum class MixType {
        CALM, ENERGETIC, HAPPY, SAD, ROMANTIC, YOUR_FLOW
    }

    data class SmartMix(
        val title: String,
        val description: String,
        val mixType: MixType,
        val tracks: List<SongItem>
    )

    suspend fun generateMixes(
        context: Context,
        allSongs: List<SongItem>,
        isKhmer: Boolean = false
    ): List<SmartMix> = withContext(Dispatchers.IO) {
        if (allSongs.isEmpty()) return@withContext emptyList()

        val db = AppDatabase.getDatabase(context)
        val moodDao = db.moodPredictionDao()
        val lyricsDao = db.lyricsDao()

        // 1. Fetch mood data for available songs
        val songMoodMap = mutableMapOf<String, HybridMoodFusionEngine.FusedMoodResult>()
        for (song in allSongs) {
            val audioPred = moodDao.get(song.id, OnDeviceMusicMoodClassifier.MODEL_VERSION)
            if (audioPred != null) {
                val lyrics = lyricsDao.getLyrics(song.id)
                val fused = HybridMoodFusionEngine.fuse(audioPred, lyrics)
                songMoodMap[song.id] = fused
            }
        }

        val mixes = mutableListOf<SmartMix>()

        // 2. Calm Mix
        val calmTracks = allSongs.filter { song ->
            val mood = songMoodMap[song.id]
            mood?.primaryMood == "CALM" || (mood != null && mood.arousal < 0.05f && mood.valence > -0.2f)
        }.sortedBy { songMoodMap[it.id]?.arousal ?: 0f }

        if (calmTracks.size >= 3) {
            mixes.add(
                SmartMix(
                    title = if (isKhmer) "Calm Mix (អារម្មណ៍ស្ងប់ស្ងាត់)" else "Calm Mix",
                    description = if (isKhmer) "បទចម្រៀងស្រទន់ ស្ងប់ស្ងាត់ សម្រាប់គេង ឬផ្តោតអារម្មណ៍ធ្វើការ" else "Peaceful, acoustic and relaxing tracks to unwind",
                    mixType = MixType.CALM,
                    tracks = calmTracks.take(30)
                )
            )
        }

        // 3. Energetic Mix
        val energeticTracks = allSongs.filter { song ->
            val mood = songMoodMap[song.id]
            mood?.primaryMood == "ENERGETIC" || (mood != null && mood.arousal > 0.35f)
        }.sortedByDescending { songMoodMap[it.id]?.arousal ?: 0f }

        if (energeticTracks.size >= 3) {
            mixes.add(
                SmartMix(
                    title = if (isKhmer) "Energetic Mix (កក្រើកថាមពល)" else "Energetic Mix",
                    description = if (isKhmer) "បទញាក់ កន្ត្រាក់អារម្មណ៍ សម្រាប់ហាត់ប្រាណ ឬបើកបរ" else "High-tempo, driving tracks for workouts and focus",
                    mixType = MixType.ENERGETIC,
                    tracks = energeticTracks.take(30)
                )
            )
        }

        // 4. Happy Mix
        val happyTracks = allSongs.filter { song ->
            val mood = songMoodMap[song.id]
            mood?.primaryMood == "HAPPY" || (mood != null && mood.valence > 0.30f && mood.arousal > -0.1f)
        }.sortedByDescending { songMoodMap[it.id]?.valence ?: 0f }

        if (happyTracks.size >= 3) {
            mixes.add(
                SmartMix(
                    title = if (isKhmer) "Happy Mix (រីករាយស្រស់ស្រាយ)" else "Happy Mix",
                    description = if (isKhmer) "បទចម្រៀងបង្កើនភាពរីករាយ និងស្នាមញញឹម" else "Uplifting, feel-good music to brighten your day",
                    mixType = MixType.HAPPY,
                    tracks = happyTracks.take(30)
                )
            )
        }

        // 5. Sad Mix
        val sadTracks = allSongs.filter { song ->
            val mood = songMoodMap[song.id]
            mood?.primaryMood == "SAD" || (mood != null && mood.valence < -0.15f && mood.arousal < 0.15f)
        }.sortedBy { songMoodMap[it.id]?.valence ?: 0f }

        if (sadTracks.size >= 3) {
            mixes.add(
                SmartMix(
                    title = if (isKhmer) "Sad Mix (កម្សត់លួងលោមចិត្ត)" else "Sad Mix",
                    description = if (isKhmer) "បទកម្សត់ ឯកា លួងលោមបេះដូងពេលពិបាកចិត្ត" else "Melancholic, deep and emotional songs when you feel down",
                    mixType = MixType.SAD,
                    tracks = sadTracks.take(30)
                )
            )
        }

        // 6. Your Daily Mix (Blends favorites + most played with smooth energy pacing)
        val scoredTracks = allSongs.map { song ->
            var score = 0f
            if (song.isFavorite) score += 3.5f
            val affinity = PlaybackTracker.computeSongAffinity(context, song.id, song.isFavorite)
            score += affinity
            Pair(song, score)
        }.sortedByDescending { it.second }.map { it.first }

        val flowTracks = applyEnergyPacing(scoredTracks.take(35), songMoodMap)
        if (flowTracks.isNotEmpty()) {
            mixes.add(
                0, // Place Your Daily Mix at the top!
                SmartMix(
                    title = if (isKhmer) "Your Mix (លំហូរតន្ត្រីផ្ទាល់ខ្លួន)" else "Your Mix",
                    description = if (isKhmer) "លាយបទដែលអ្នកចូលចិត្ត និងបទចម្រៀងតាមទម្លាប់ស្តាប់របស់អ្នក" else "A personalized playlist crafted from your listening habits",
                    mixType = MixType.YOUR_FLOW,
                    tracks = flowTracks
                )
            )
        }

        mixes
    }

    /**
     * Orders tracks into a smooth arc: Warmup -> High Peak -> Cool Down.
     */
    private fun applyEnergyPacing(
        tracks: List<SongItem>,
        moodMap: Map<String, HybridMoodFusionEngine.FusedMoodResult>
    ): List<SongItem> {
        if (tracks.size <= 4) return tracks
        val sortedByEnergy = tracks.sortedBy { moodMap[it.id]?.arousal ?: 0f }

        val mid = sortedByEnergy.size / 2
        val lowerEnergy = sortedByEnergy.take(mid)
        val higherEnergy = sortedByEnergy.drop(mid)

        // Interleave to create an emotional journey
        val paced = mutableListOf<SongItem>()
        for (i in lowerEnergy.indices) {
            paced.add(lowerEnergy[i])
            if (i < higherEnergy.size) {
                paced.add(higherEnergy[higherEnergy.size - 1 - i])
            }
        }
        return paced
    }
}
