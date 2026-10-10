package com.musichub.app.domain.recommendation

import android.content.Context
import com.musichub.app.domain.downloader.LocalMediaExtractor
import com.musichub.app.presentation.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID

data class RecommendedTrack(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val artist: String,
    val artworkUrl: String,
    val duration: String,
    val previewUrl: String? = null,
    val downloadQuery: String
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
            // Put latest search at the front
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

object RecommendationEngine {

    suspend fun getPersonalizedRecommendations(
        existingSongs: List<SongItem>,
        client: OkHttpClient,
        searchQueries: List<String> = emptyList()
    ): List<RecommendedTrack> = withContext(Dispatchers.IO) {
        val resultList = mutableListOf<RecommendedTrack>()
        val existingNormalizedTitles = existingSongs.map {
            it.title.lowercase().replace(Regex("[^\\p{L}\\p{Nd}]"), "")
        }.toMutableSet()

        // Extract top artists from user's current library
        val topArtists = existingSongs
            .filter { it.artist.isNotBlank() && it.artist != "MusicHub" && it.artist != "<unknown>" }
            .groupBy { it.artist.trim() }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(4)
            .map { it.first }

        val queries = mutableListOf<String>()

        // 1. Prioritize user's recent search interests (up to 3 distinct search terms)
        val validSearches = searchQueries
            .filter { it.isNotBlank() && it.length >= 2 }
            .distinctBy { it.lowercase() }
            .take(3)
        queries.addAll(validSearches)

        // 2. Add library top artists
        for (artist in topArtists) {
            if (!queries.any { it.equals(artist, ignoreCase = true) }) {
                queries.add(artist)
            }
        }

        // Always add popular/trending query categories if queries list is small
        if (queries.isEmpty()) {
            queries.add("Top Hits")
            queries.add("Acoustic Chill")
            queries.add("Pop Music")
        } else if (queries.size < 3) {
            queries.add("Popular Hits")
        }

        for (query in queries.take(6)) {
            try {
                val itunesUrl = "https://itunes.apple.com/search?term=${URLEncoder.encode(query, "UTF-8")}&entity=song&limit=8"
                val req = Request.Builder()
                    .url(itunesUrl)
                    .header("User-Agent", LocalMediaExtractor.USER_AGENT)
                    .build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val json = JSONObject(body)
                    val array = json.optJSONArray("results") ?: JSONArray()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val title = obj.optString("trackName", "")
                        val artist = obj.optString("artistName", "")
                        val preview = obj.optString("previewUrl", "")
                        val art100 = obj.optString("artworkUrl100", "")
                        val artHd = art100.replace("100x100bb.jpg", "600x600bb.jpg")
                        val durMs = obj.optLong("trackTimeMillis", 0L)
                        val durSec = durMs / 1000
                        val durStr = if (durSec > 0) "%d:%02d".format(durSec / 60, durSec % 60) else "3:30"

                        val normTitle = title.lowercase().replace(Regex("[^\\p{L}\\p{Nd}]"), "")
                        if (title.isNotBlank() && !existingNormalizedTitles.contains(normTitle)) {
                            existingNormalizedTitles.add(normTitle)
                            resultList.add(
                                RecommendedTrack(
                                    title = title,
                                    artist = artist,
                                    artworkUrl = artHd,
                                    duration = durStr,
                                    previewUrl = preview.ifBlank { null },
                                    downloadQuery = "$artist - $title"
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        resultList.shuffled().take(12)
    }
}
