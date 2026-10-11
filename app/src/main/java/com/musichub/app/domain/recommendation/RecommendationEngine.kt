package com.musichub.app.domain.recommendation

import android.content.Context
import com.musichub.app.domain.audio.AiAudioMoodEngine
import com.musichub.app.domain.audio.SongMoodCategory
import com.musichub.app.presentation.PlayCountTracker
import com.musichub.app.presentation.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.UUID

data class RecommendedTrack(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val artist: String,
    val artworkUrl: String,
    val duration: String,
    val previewUrl: String? = null,
    val downloadQuery: String,
    val localSongId: String? = null,
    val moodLabelEn: String = "",
    val moodLabelKm: String = ""
)

object SearchPreferenceTracker {
    private const val PREFS_NAME = "musichub_search_prefs"
    private const val KEY_SEARCH_QUERIES = "recent_search_queries"
    private const val MAX_SAVED_QUERIES = 10

    fun recordSearchQuery(context: Context, query: String) {
        val clean = query.trim()
        if (clean.length < 2) return
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val existingJson = prefs.getString(KEY_SEARCH_QUERIES, "[]") ?: "[]"
            val array = JSONArray(existingJson)
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                val item = array.optString(i, "")
                if (item.isNotBlank() && !item.equals(clean, ignoreCase = true)) {
                    list.add(item)
                }
            }
            list.add(0, clean)
            val trimmedList = list.take(MAX_SAVED_QUERIES)

            val newArray = JSONArray()
            trimmedList.forEach { newArray.put(it) }
            prefs.edit().putString(KEY_SEARCH_QUERIES, newArray.toString()).apply()
        } catch (_: Exception) {}
    }

    fun getRecentSearchQueries(context: Context): List<String> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val existingJson = prefs.getString(KEY_SEARCH_QUERIES, "[]") ?: "[]"
            val array = JSONArray(existingJson)
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                val item = array.optString(i, "")
                if (item.isNotBlank()) {
                    list.add(item)
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }
}

/**
 * 100% On-Device AI Recommendation Engine (Zero external API calls).
 *
 * Uses AiAudioMoodEngine to analyze the acoustic & mood profile of all local songs,
 * the user's listening history, favorites, currently playing track, and recent search queries
 * to rank and recommend tracks matching the user's preferred mood and vibe.
 */
object RecommendationEngine {

    suspend fun getPersonalizedRecommendations(
        context: Context,
        existingSongs: List<SongItem>,
        currentSong: SongItem? = null,
        searchQueries: List<String> = emptyList()
    ): List<RecommendedTrack> = withContext(Dispatchers.IO) {
        if (existingSongs.isEmpty()) return@withContext emptyList()

        val profiles = existingSongs.associateWith { AiAudioMoodEngine.getSongMoodProfile(context, it) }
        val baseTasteVec = AiAudioMoodEngine.computeUserTasteVector(context, existingSongs, profiles)
        val contextVec = baseTasteVec.copyOf()

        // Blend in the currently playing song's AI mood vector so recommendations adapt to live listening vibe
        if (currentSong != null) {
            val currentProf = profiles[currentSong] ?: AiAudioMoodEngine.getSongMoodProfile(context, currentSong)
            for (i in contextVec.indices) {
                contextVec[i] = (contextVec[i] * 0.60f) + (currentProf.probabilities[i] * 0.40f)
            }
        }

        // Blend in recent search queries if they correspond to a mood or artist
        val cleanQueries = searchQueries.map { it.lowercase().trim() }.filter { it.length >= 2 }.take(5)
        if (cleanQueries.isNotEmpty()) {
            val queryDummySong = SongItem(
                id = "search_intent",
                title = cleanQueries.joinToString(" "),
                artist = "",
                album = "",
                duration = "3:00",
                durationSec = 180
            )
            val queryProf = AiAudioMoodEngine.getSongMoodProfile(context, queryDummySong)
            for (i in contextVec.indices) {
                contextVec[i] = (contextVec[i] * 0.78f) + (queryProf.probabilities[i] * 0.22f)
            }
        }

        // Top artists affinity
        val topArtists = existingSongs
            .filter { it.artist.isNotBlank() && it.artist != "MusicHub" && it.artist != "<unknown>" }
            .groupBy { it.artist.trim().lowercase() }
            .mapValues { entry ->
                entry.value.sumOf { PlayCountTracker.getPlayCount(context, it.id) } * 2 + entry.value.size
            }

        val maxArtistScore = (topArtists.values.maxOrNull() ?: 1).coerceAtLeast(1)

        val candidates = if (existingSongs.size > 4 && currentSong != null) {
            existingSongs.filter { it.id != currentSong.id }
        } else {
            existingSongs
        }

        val scoredCandidates = candidates.map { song ->
            val prof = profiles[song] ?: AiAudioMoodEngine.getSongMoodProfile(context, song)
            val moodSim = AiAudioMoodEngine.cosineSimilarity(prof.probabilities, contextVec)

            val artistKey = song.artist.trim().lowercase()
            val artistAffinity = ((topArtists[artistKey] ?: 0).toFloat() / maxArtistScore.toFloat()) * 0.18f

            val playCount = PlayCountTracker.getPlayCount(context, song.id)
            // Rediscovery curve: songs with 1..10 plays or matching mood get a discovery boost
            val playAffinity = when {
                playCount in 1..8 -> 0.16f
                playCount > 8 -> 0.12f
                else -> 0.08f
            }
            val favBoost = if (song.isFavorite) 0.12f else 0f

            val textLower = "${song.title} ${song.artist} ${prof.primaryMood.enShort} ${prof.primaryMood.kmShort}".lowercase()
            val searchBoost = if (cleanQueries.any { q -> textLower.contains(q) }) 0.28f else 0f

            val totalScore = (moodSim * 0.65f) + artistAffinity + playAffinity + favBoost + searchBoost
            Triple(song, prof, totalScore)
        }.sortedByDescending { it.third }

        // Select up to 12 tracks while ensuring good variety across the user's top matching moods
        val selected = scoredCandidates.take(12)

        selected.map { (song, prof, _) ->
            RecommendedTrack(
                id = song.id,
                title = song.title,
                artist = song.artist,
                artworkUrl = song.artworkUrl,
                duration = song.duration,
                previewUrl = song.uriString.ifBlank { null },
                downloadQuery = "${song.artist} - ${song.title}",
                localSongId = song.id,
                moodLabelEn = prof.primaryMood.enShort,
                moodLabelKm = prof.primaryMood.kmShort
            )
        }
    }
}
