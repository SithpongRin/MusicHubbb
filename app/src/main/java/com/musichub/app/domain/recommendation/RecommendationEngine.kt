package com.musichub.app.domain.recommendation

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

object RecommendationEngine {

    suspend fun getPersonalizedRecommendations(
        existingSongs: List<SongItem>,
        client: OkHttpClient
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
        queries.addAll(topArtists)

        // Always add popular/trending query categories if library is small
        if (queries.isEmpty()) {
            queries.add("Top Hits")
            queries.add("Acoustic Chill")
            queries.add("Pop Music")
        } else if (queries.size < 3) {
            queries.add("Popular Hits")
        }

        for (query in queries) {
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
