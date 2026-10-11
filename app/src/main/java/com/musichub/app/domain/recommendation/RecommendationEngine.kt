package com.musichub.app.domain.recommendation

import android.content.Context
import com.musichub.app.domain.audio.AiAudioMoodEngine
import com.musichub.app.domain.audio.SongMoodCategory
import com.musichub.app.domain.downloader.LocalMediaExtractor
import com.musichub.app.domain.downloader.OnlineSearchResult
import com.musichub.app.presentation.PlayCountTracker
import com.musichub.app.presentation.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.abs

data class RecommendedTrack(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String,
    val duration: String,
    val previewUrl: String,
    val downloadQuery: String,
    val localSongId: String? = null,
    val moodLabelEn: String = "",
    val moodLabelKm: String = ""
)

/**
 * AI-Powered Hybrid Internet Recommendation Engine ("Recommended for You").
 *
 * Combines all 3 core signals:
 * 1. Pure Audio Signal Taste (from local songs on the device via `AiAudioMoodEngine`):
 *    - 7-dimensional learned audio mood vector (`userTasteVec`)
 *    - Target acoustic Energy & Tempo profile from the user's most-played and favorited local tracks
 *    - Per-artist real acoustic signatures (`artistAudioProfiles`)
 * 2. Artist Name & Learned Artist Affinity:
 *    - Ranks the user's favorite and most-listened artists (`rankedArtists`)
 *    - Prioritizes new tracks from favorite artists and acoustically similar artists
 * 3. Track Title, Genre & Search Intent:
 *    - Uses whole-word boundary title/genre classification (`classifyMetadataProfile`) and recent search queries
 *    - Strictly filters out any track already in `existingSongs`.
 */
object RecommendationEngine {
    private const val CACHE_PREFS = "musichub_ai_internet_recs_v3"

    suspend fun getPersonalizedRecommendations(
        context: Context,
        existingSongs: List<SongItem>,
        client: OkHttpClient,
        currentSong: SongItem? = null,
        searchQueries: List<String> = emptyList()
    ): List<RecommendedTrack> = withContext(Dispatchers.IO) {
        try {
            // 1. Analyze user's Pure Audio Taste Vector, Target Energy/Tempo, and Local Artist Audio Profiles
            val localProfiles = existingSongs.associateWith { AiAudioMoodEngine.getSongMoodProfile(context, it) }
            val userTasteVec = AiAudioMoodEngine.computeUserTasteVector(context, existingSongs, localProfiles)
            val currentMoodProfile = currentSong?.let {
                localProfiles[it] ?: AiAudioMoodEngine.getSongMoodProfile(context, it)
            }

            // Compute weighted target acoustic Energy & Tempo from local songs
            var weightedEnergySum = 0f
            var weightedTempoSum = 0f
            var totalAcousticWeight = 0f
            for (song in existingSongs) {
                val prof = localProfiles[song] ?: continue
                val plays = PlayCountTracker.getPlayCount(context, song.id)
                val w = (1.0f + plays.coerceAtMost(30) * 0.6f + (if (song.isFavorite) 2.5f else 0f)).coerceAtLeast(0.2f)
                weightedEnergySum += prof.energyScore * w
                weightedTempoSum += prof.tempoScore * w
                totalAcousticWeight += w
            }
            val userTargetEnergy = if (totalAcousticWeight > 0f) (weightedEnergySum / totalAcousticWeight) else 0.52f
            val userTargetTempo = if (totalAcousticWeight > 0f) (weightedTempoSum / totalAcousticWeight) else 0.52f

            // Build per-artist real audio mood centroids from local library songs
            val artistAudioProfiles = mutableMapOf<String, FloatArray>()
            val validArtistGroups = existingSongs
                .filter {
                    val a = it.artist.trim()
                    a.isNotBlank() &&
                        !a.equals("MusicHub", ignoreCase = true) &&
                        !a.equals("<unknown>", ignoreCase = true) &&
                        !a.equals("Unknown Artist", ignoreCase = true) &&
                        !a.equals("YouTube", ignoreCase = true)
                }
                .groupBy { it.artist.trim() }

            for ((artistName, tracks) in validArtistGroups) {
                val centroid = FloatArray(SongMoodCategory.entries.size)
                var count = 0
                for (t in tracks) {
                    val p = localProfiles[t] ?: continue
                    for (i in centroid.indices) centroid[i] += p.probabilities[i]
                    count++
                }
                if (count > 0) {
                    for (i in centroid.indices) centroid[i] /= count.toFloat()
                    artistAudioProfiles[artistName.lowercase()] = centroid
                }
            }

            // Detect whether user listens to Khmer music
            val khmerSongCount = existingSongs.count { containsKhmerScript(it.title) || containsKhmerScript(it.artist) }
            val khmerQueryCount = searchQueries.count { containsKhmerScript(it) }
            val prefersKhmer = khmerSongCount > 0 || khmerQueryCount > 0 || existingSongs.isEmpty()

            // Rank user's favorite & most-listened artists using learned AI affinity + play count + favorites
            val rankedArtists = validArtistGroups
                .entries
                .sortedByDescending { (artistName, tracks) ->
                    val plays = tracks.sumOf { PlayCountTracker.getPlayCount(context, it.id) }
                    val favs = tracks.count { it.isFavorite }
                    val learnedRewards = tracks.sumOf { AiAudioMoodEngine.getSongLearnedReward(context, it.id).toDouble() }.toFloat()
                    val artistAff = AiAudioMoodEngine.getArtistLearnedAffinity(context, artistName)
                    (plays * 3.5f) + (favs * 4.5f) + (tracks.size * 1.5f) + (learnedRewards * 2.0f) + (artistAff * 2.5f)
                }
                .map { it.key }

            // Find user's #1 and #2 learned AI Mood Categories from pure audio taste
            val sortedMoodCategories = SongMoodCategory.entries
                .sortedByDescending { cat -> userTasteVec[cat.ordinal] }
            val topMood1 = sortedMoodCategories.getOrNull(0) ?: SongMoodCategory.DRIVING
            val topMood2 = sortedMoodCategories.getOrNull(1) ?: SongMoodCategory.ROMANTIC

            // 2. Synthesize Smart Hybrid Discovery Queries (Search + Artist + Audio Mood + Title Vibe)
            val aiQueries = mutableListOf<String>()

            // A. User's recent search queries (top 2)
            for (q in searchQueries.take(2)) {
                val clean = q.trim()
                if (clean.length >= 2 && aiQueries.none { it.equals(clean, ignoreCase = true) }) {
                    aiQueries.add(clean)
                }
            }

            // B. Currently playing artist + song vibe (if valid)
            currentSong?.artist?.trim()?.takeIf {
                it.isNotBlank() && !it.equals("MusicHub", ignoreCase = true) && !it.equals("<unknown>", ignoreCase = true)
            }?.let { currArtist ->
                if (aiQueries.none { it.contains(currArtist, ignoreCase = true) }) {
                    aiQueries.add("$currArtist official audio")
                }
            }

            // C. User's top learned artists (top 3)
            for (artist in rankedArtists.take(3)) {
                if (aiQueries.size < 5 && aiQueries.none { it.contains(artist, ignoreCase = true) }) {
                    aiQueries.add(artist)
                }
            }

            // D. AI Audio-Mood discovery queries tailored to the user's dominant acoustic moods
            val moodQuery1 = buildMoodDiscoveryQuery(topMood1, prefersKhmer)
            if (aiQueries.size < 6 && aiQueries.none { it.equals(moodQuery1, ignoreCase = true) }) {
                aiQueries.add(moodQuery1)
            }
            val moodQuery2 = buildMoodDiscoveryQuery(topMood2, prefersKhmer)
            if (aiQueries.size < 6 && aiQueries.none { it.equals(moodQuery2, ignoreCase = true) }) {
                aiQueries.add(moodQuery2)
            }

            val finalQueries = aiQueries.take(5)

            // 3. Fetch candidate tracks from the Internet in parallel (YouTube + iTunes)
            val rawOnlineCandidates = coroutineScope {
                val ytJobs = finalQueries.map { query ->
                    async(Dispatchers.IO) {
                        try {
                            LocalMediaExtractor.searchOnlineTracks(query, client, limit = 8)
                        } catch (_: Throwable) {
                            emptyList()
                        }
                    }
                }
                val itunesJob = async(Dispatchers.IO) {
                    val itunesList = mutableListOf<OnlineSearchResult>()
                    for (query in finalQueries.take(3)) {
                        try {
                            itunesList.addAll(fetchItunesCandidates(query, client, limit = 6))
                        } catch (_: Throwable) {}
                    }
                    itunesList
                }
                (ytJobs.awaitAll().flatten() + itunesJob.await())
            }

            // 4. Strictly filter out any track that already exists in `existingSongs`
            val existingIds = existingSongs.map { it.id.lowercase() }.toSet()
            val existingNormTitles = existingSongs.map { normalizeForComparison(it.title) }.filter { it.length >= 3 }.toSet()

            val seenCandidateKeys = mutableSetOf<String>()
            val uniqueInternetCandidates = rawOnlineCandidates.filter { cand ->
                val normTitle = normalizeForComparison(cand.title)
                val normArtist = normalizeForComparison(cand.artist)
                if (normTitle.length < 2) return@filter false

                // Reject if duration is a 1-hour compilation (> 11 mins) or ultra-short (< 50s)
                val durSec = parseDurationToSeconds(cand.duration)
                if (durSec in 1..49 || durSec > 660) return@filter false

                // Reject if already in user's local library
                val candIdLower = cand.id.lowercase()
                val alreadyInLibrary = existingIds.any { it.contains(candIdLower) } ||
                    existingNormTitles.any { localTitle ->
                        localTitle == normTitle ||
                            (localTitle.length >= 6 && normTitle.length >= 6 &&
                                (localTitle.contains(normTitle) || normTitle.contains(localTitle)))
                    }
                if (alreadyInLibrary) return@filter false

                val dedupKey = "${normTitle}_$normArtist"
                seenCandidateKeys.add(dedupKey)
            }

            if (uniqueInternetCandidates.isEmpty()) {
                return@withContext loadCachedRecommendations(context, existingNormTitles)
            }

            // 5. Score and rank every Internet candidate using Hybrid:
            //    (1) Audio Taste & Energy/Tempo Fit + (2) Artist Name & Affinity + (3) Title & Search Alignment
            val topArtistList = rankedArtists.take(10).map { it.lowercase() }
            val searchKeywords = searchQueries.take(6).map { it.lowercase().trim() }.filter { it.length >= 2 }

            data class ScoredCandidate(
                val track: RecommendedTrack,
                val artistKey: String,
                val score: Float
            )

            val scored = uniqueInternetCandidates.map { cand ->
                val durSec = parseDurationToSeconds(cand.duration).let { if (it > 0) it else 205 }
                val cleanTitle = cleanOnlineTitle(cand.title)
                val cleanArtist = cleanOnlineArtist(cand.artist)
                val artistLower = cleanArtist.lowercase()
                val titleLower = cleanTitle.lowercase()

                // Look up if we have a real audio profile for this artist in the local library
                val matchedLocalArtistProfile = artistAudioProfiles[artistLower]
                    ?: artistAudioProfiles.entries.firstOrNull { (k, _) ->
                        k.length >= 3 && (artistLower.contains(k) || k.contains(artistLower))
                    }?.value

                val aiProfile = AiAudioMoodEngine.classifyMetadataProfile(
                    context = context,
                    title = cleanTitle,
                    artist = cleanArtist,
                    durationSec = durSec,
                    localArtistProfileHint = matchedLocalArtistProfile
                )

                // Signal 1: Audio Taste Vector & Energy/Tempo Fit (40% weight)
                val tasteSimilarity = AiAudioMoodEngine.cosineSimilarity(aiProfile.probabilities, userTasteVec)
                val currentMoodSim = if (currentMoodProfile != null) {
                    AiAudioMoodEngine.cosineSimilarity(aiProfile.probabilities, currentMoodProfile.probabilities)
                } else {
                    tasteSimilarity
                }
                val energyTempoFit = (1.0f - (abs(aiProfile.energyScore - userTargetEnergy) * 0.45f + abs(aiProfile.tempoScore - userTargetTempo) * 0.45f))
                    .coerceIn(0f, 1f)
                val audioScore = (tasteSimilarity * 0.22f) + (currentMoodSim * 0.10f) + (energyTempoFit * 0.10f)

                // Signal 2: Artist Name & Learned Artist Affinity (32% weight)
                val artistRankIdx = topArtistList.indexOfFirst { topA ->
                    topA == artistLower || (topA.length >= 3 && (artistLower.contains(topA) || topA.contains(artistLower)))
                }
                val artistRankBonus = if (artistRankIdx != -1) {
                    (0.26f - (artistRankIdx * 0.02f)).coerceAtLeast(0.10f)
                } else 0f
                val learnedArtistAff = (AiAudioMoodEngine.getArtistLearnedAffinity(context, cleanArtist) * 0.08f)
                    .coerceIn(-0.18f, 0.25f)
                val artistScore = artistRankBonus + learnedArtistAff

                // Signal 3: Title, Mood Keyword & Search Intent Alignment (28% weight)
                val matchesSearch = searchKeywords.any { kw ->
                    titleLower.contains(kw) || artistLower.contains(kw)
                }
                val titleMoodMatchBonus = if (aiProfile.primaryMood == topMood1) 0.10f else if (aiProfile.primaryMood == topMood2) 0.06f else 0f
                val titleSearchScore = (if (matchesSearch) 0.20f else 0f) + titleMoodMatchBonus

                val totalAiScore = audioScore + artistScore + titleSearchScore

                val webDownloadUrl = cand.webUrl ?: if (cand.id.length == 11 && !cand.id.all { it.isDigit() }) {
                    "https://www.youtube.com/watch?v=${cand.id}"
                } else {
                    "$cleanTitle $cleanArtist audio"
                }

                val recTrack = RecommendedTrack(
                    id = cand.id,
                    title = cleanTitle,
                    artist = cleanArtist,
                    artworkUrl = cand.artworkUrl,
                    duration = cand.duration.ifBlank { "3:30" },
                    previewUrl = cand.previewUrl.orEmpty(),
                    downloadQuery = webDownloadUrl,
                    localSongId = null,
                    moodLabelEn = aiProfile.primaryMood.enShort,
                    moodLabelKm = aiProfile.primaryMood.kmShort
                )

                ScoredCandidate(
                    track = recTrack,
                    artistKey = artistLower,
                    score = totalAiScore
                )
            }.sortedByDescending { it.score }

            // 6. Apply Artist Diversity (max 3 tracks per artist)
            val diversified = mutableListOf<RecommendedTrack>()
            val artistCounts = mutableMapOf<String, Int>()
            for (item in scored) {
                if (diversified.size >= 14) break
                val count = artistCounts.getOrDefault(item.artistKey, 0)
                if (count < 3) {
                    diversified.add(item.track)
                    artistCounts[item.artistKey] = count + 1
                }
            }

            if (diversified.isNotEmpty()) {
                saveCachedRecommendations(context, diversified)
            }

            return@withContext diversified
        } catch (_: Throwable) {
            return@withContext loadCachedRecommendations(context, emptySet())
        }
    }

    private fun buildMoodDiscoveryQuery(mood: SongMoodCategory, prefersKhmer: Boolean): String {
        return if (prefersKhmer) {
            when (mood) {
                SongMoodCategory.WORKOUT -> "Khmer workout gym motivation music"
                SongMoodCategory.PARTY -> "បទញាក់ Remix ថ្មីៗ ពិរោះៗ"
                SongMoodCategory.CHILL -> "បទខ្មែរ Chill ពិរោះៗ ស្ងប់ស្ងាត់"
                SongMoodCategory.SAD -> "បទសេដ មនោសញ្ចេតនា ពិរោះៗ"
                SongMoodCategory.ROMANTIC -> "បទស្នេហា ផ្អែមល្ហែម ពិរោះៗ"
                SongMoodCategory.FOCUS -> "ភ្លេងសុទ្ធ Acoustic Piano ពិរោះៗ"
                SongMoodCategory.DRIVING -> "បទខ្មែរពិរោះៗ ស្តាប់ពេលធ្វើដំណើរ"
            }
        } else {
            when (mood) {
                SongMoodCategory.WORKOUT -> "workout gym motivation music hits"
                SongMoodCategory.PARTY -> "party club dance remix hits"
                SongMoodCategory.CHILL -> "chill lofi relax vibes music"
                SongMoodCategory.SAD -> "sad emotional ballad songs"
                SongMoodCategory.ROMANTIC -> "romantic love songs hits"
                SongMoodCategory.FOCUS -> "acoustic piano study focus music"
                SongMoodCategory.DRIVING -> "night drive pop vibe songs"
            }
        }
    }

    private fun fetchItunesCandidates(
        term: String,
        client: OkHttpClient,
        limit: Int
    ): List<OnlineSearchResult> {
        val list = mutableListOf<OnlineSearchResult>()
        try {
            val encoded = URLEncoder.encode(term.trim(), "UTF-8")
            val url = "https://itunes.apple.com/search?term=$encoded&media=music&entity=song&limit=$limit"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", LocalMediaExtractor.USER_AGENT)
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val arr = JSONObject(body).optJSONArray("results") ?: return emptyList()
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        val trackId = item.optLong("trackId", 0L).toString()
                        val title = item.optString("trackName", "").trim()
                        val artist = item.optString("artistName", "").trim()
                        val genre = item.optString("primaryGenreName", "").trim()
                        val previewUrl = item.optString("previewUrl", "").trim()
                        val art100 = item.optString("artworkUrl100", "").trim()
                        val hdArt = if (art100.isNotBlank()) art100.replace("100x100bb", "600x600bb") else ""
                        val timeMs = item.optLong("trackTimeMillis", 210_000L)
                        val totalSec = (timeMs / 1000L).toInt()
                        val durFormatted = String.format("%d:%02d", totalSec / 60, totalSec % 60)

                        if (title.isNotBlank() && artist.isNotBlank()) {
                            val artistWithGenreHint = if (genre.isNotBlank()) artist else artist
                            list.add(
                                OnlineSearchResult(
                                    id = if (trackId != "0") "itunes_$trackId" else "it_${abs((title + artist).hashCode())}",
                                    title = title,
                                    artist = artistWithGenreHint,
                                    duration = durFormatted,
                                    artworkUrl = hdArt,
                                    webUrl = "$title $artist official audio",
                                    previewUrl = previewUrl
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Throwable) {}
        return list
    }

    private fun containsKhmerScript(text: String): Boolean {
        for (ch in text) {
            if (ch in '\u1780'..'\u17FF') return true
        }
        return false
    }

    private fun normalizeForComparison(raw: String): String {
        return raw.lowercase()
            .replace(Regex("\\(.*?\\)|\\[.*?\\]|【.*?】|「.*?」"), " ")
            .replace("official music video", " ")
            .replace("official video", " ")
            .replace("official audio", " ")
            .replace("lyric video", " ")
            .replace("lyrics", " ")
            .replace("mv", " ")
            .replace(Regex("[^a-z0-9\\u1780-\\u17FF\\u3040-\\u30FF\\u4E00-\\u9FFF]+"), " ")
            .trim()
    }

    private fun cleanOnlineTitle(raw: String): String {
        val cleaned = raw
            .replace(Regex("\\((?i)(official\\s*(music\\s*)?(video|audio|mv|lyric.*))\\)"), "")
            .replace(Regex("\\[(?i)(official\\s*(music\\s*)?(video|audio|mv|lyric.*))\\]"), "")
            .trim()
        return cleaned.ifBlank { raw.trim() }
    }

    private fun cleanOnlineArtist(raw: String): String {
        return raw
            .replace(" - Topic", "", ignoreCase = true)
            .replace("VEVO", "", ignoreCase = true)
            .replace("Official", "", ignoreCase = true)
            .trim()
            .ifBlank { raw.trim() }
    }

    private fun parseDurationToSeconds(dur: String): Int {
        val parts = dur.trim().split(":").mapNotNull { it.trim().toIntOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 0
        }
    }

    private fun saveCachedRecommendations(context: Context, tracks: List<RecommendedTrack>) {
        try {
            val arr = JSONArray()
            for (t in tracks) {
                arr.put(
                    JSONObject().apply {
                        put("id", t.id)
                        put("title", t.title)
                        put("artist", t.artist)
                        put("artworkUrl", t.artworkUrl)
                        put("duration", t.duration)
                        put("previewUrl", t.previewUrl)
                        put("downloadQuery", t.downloadQuery)
                        put("moodLabelEn", t.moodLabelEn)
                        put("moodLabelKm", t.moodLabelKm)
                    }
                )
            }
            context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString("cached_recs_json", arr.toString())
                .apply()
        } catch (_: Throwable) {}
    }

    private fun loadCachedRecommendations(
        context: Context,
        existingNormTitles: Set<String>
    ): List<RecommendedTrack> {
        val list = mutableListOf<RecommendedTrack>()
        try {
            val json = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
                .getString("cached_recs_json", null) ?: return emptyList()
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val title = obj.optString("title", "")
                val normTitle = normalizeForComparison(title)
                if (title.isNotBlank() && normTitle !in existingNormTitles) {
                    list.add(
                        RecommendedTrack(
                            id = obj.optString("id", ""),
                            title = title,
                            artist = obj.optString("artist", ""),
                            artworkUrl = obj.optString("artworkUrl", ""),
                            duration = obj.optString("duration", "3:30"),
                            previewUrl = obj.optString("previewUrl", ""),
                            downloadQuery = obj.optString("downloadQuery", ""),
                            localSongId = null,
                            moodLabelEn = obj.optString("moodLabelEn", ""),
                            moodLabelKm = obj.optString("moodLabelKm", "")
                        )
                    )
                }
            }
        } catch (_: Throwable) {}
        return list
    }
}

/**
 * Tracks user search queries (both Offline and Online searches) in SharedPreferences
 * and feeds them directly into the Self-Learning AI (`AiAudioMoodEngine.recordSearchFeedback`).
 */
object SearchPreferenceTracker {
    private const val PREFS_NAME = "musichub_search_prefs"
    private const val KEY_QUERIES = "recent_search_queries"
    private const val MAX_STORED_QUERIES = 15

    fun recordSearchQuery(context: Context, rawQuery: String) {
        val clean = rawQuery.trim()
        if (clean.length < 2) return
        try {
            AiAudioMoodEngine.recordSearchFeedback(context, clean)

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val existing = getRecentSearchQueries(context).toMutableList()
            existing.removeAll { it.equals(clean, ignoreCase = true) }
            existing.add(0, clean)
            val trimmed = existing.take(MAX_STORED_QUERIES)
            val arr = JSONArray()
            trimmed.forEach { arr.put(it) }
            prefs.edit().putString(KEY_QUERIES, arr.toString()).apply()
        } catch (_: Exception) {}
    }

    fun getRecentSearchQueries(context: Context): List<String> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_QUERIES, null) ?: return emptyList()
            val arr = JSONArray(raw)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val q = arr.optString(i, "").trim()
                if (q.isNotBlank()) list.add(q)
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }
}
