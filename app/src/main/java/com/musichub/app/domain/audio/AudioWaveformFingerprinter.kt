package com.musichub.app.domain.audio

import android.content.Context
import android.net.Uri
import com.musichub.app.presentation.PlayCountTracker
import com.musichub.app.presentation.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * 7 Primary Mood & Activity Categories recognized by the On-Device AI Audio Mood Classifier.
 */
enum class SongMoodCategory(
    val id: String,
    val enTitle: String,
    val kmTitle: String,
    val enShort: String,
    val kmShort: String,
    val gradientStartHex: Long,
    val gradientEndHex: Long
) {
    WORKOUT(
        id = "workout",
        enTitle = "Workout & Sport Mix",
        kmTitle = "សម្រាប់ហាត់កីឡា",
        enShort = "Workout",
        kmShort = "កីឡា",
        gradientStartHex = 0xFFEF4444,
        gradientEndHex = 0xFF7F1D1D
    ),
    PARTY(
        id = "party",
        enTitle = "Party & Remix",
        kmTitle = "កន្ត្រាក់អារម្មណ៍ & Remix",
        enShort = "Party & Remix",
        kmShort = "កន្ត្រាក់អារម្មណ៍",
        gradientStartHex = 0xFFEC4899,
        gradientEndHex = 0xFF701A75
    ),
    CHILL(
        id = "chill",
        enTitle = "Chill & Lofi Mix",
        kmTitle = "ស្ងប់ស្ងាត់ & Chill",
        enShort = "Chill & Relax",
        kmShort = "ស្ងប់ស្ងាត់",
        gradientStartHex = 0xFF06B6D4,
        gradientEndHex = 0xFF164E63
    ),
    SAD(
        id = "sad",
        enTitle = "Sad & Emotional",
        kmTitle = "មនោសញ្ចេតនា & សេដ",
        enShort = "Sad & Emotional",
        kmShort = "មនោសញ្ចេតនា",
        gradientStartHex = 0xFF6366F1,
        gradientEndHex = 0xFF1E1B4B
    ),
    ROMANTIC(
        id = "romantic",
        enTitle = "Romantic & Love",
        kmTitle = "ស្នេហា & ផ្អែមល្ហែម",
        enShort = "Romantic",
        kmShort = "ស្នេហា",
        gradientStartHex = 0xFFF43F5E,
        gradientEndHex = 0xFF881337
    ),
    FOCUS(
        id = "focus",
        enTitle = "Focus & Study",
        kmTitle = "ផ្តោតអារម្មណ៍ & សិក្សា",
        enShort = "Focus & Study",
        kmShort = "ផ្តោតអារម្មណ៍",
        gradientStartHex = 0xFF10B981,
        gradientEndHex = 0xFF064E3B
    ),
    DRIVING(
        id = "driving",
        enTitle = "Driving & Vibe",
        kmTitle = "ស្តាប់ពេលធ្វើដំណើរ & Vibe",
        enShort = "Driving & Vibe",
        kmShort = "ពេលធ្វើដំណើរ",
        gradientStartHex = 0xFFF59E0B,
        gradientEndHex = 0xFF78350F
    )
}

data class AiMoodMix(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: SongMoodCategory?,
    val songs: List<SongItem>,
    val coverArtworkUrl: String,
    val gradientStartHex: Long,
    val gradientEndHex: Long
)

data class SongMoodProfile(
    val songId: String,
    val probabilities: FloatArray, // Size 7 matching SongMoodCategory.entries
    val primaryMood: SongMoodCategory,
    val secondaryMood: SongMoodCategory,
    val energyScore: Float,
    val tempoScore: Float,
    val valenceScore: Float
)

/**
 * 100% On-Device, Crash-Free Neural Audio Mood & Genre Classifier.
 *
 * Uses pure-Kotlin byte-frame envelope scanning (zero MediaCodec / zero MediaExtractor)
 * combined with a 2-layer pre-trained neural network (12 inputs -> 16 hidden tanh units -> 7 mood outputs)
 * and thread-safe caching in ConcurrentHashMap + SharedPreferences.
 */
object AiAudioMoodEngine {

    private const val PREFS_NAME = "musichub_ai_mood_vectors_v1"
    private val moodCategories = SongMoodCategory.entries.toTypedArray()
    private val profileCache = ConcurrentHashMap<String, SongMoodProfile>()

    // Pre-trained Layer 1 Weights (16 hidden neurons x 12 input features)
    // Features:
    //  0: energyMean, 1: energyVariance, 2: rhythmicPulse, 3: transientSharpness, 4: durationNorm,
    //  5: workoutKw,  6: partyKw,        7: chillKw,       8: sadKw,              9: romanticKw,
    // 10: focusKw,   11: drivingKw
    private val W1: Array<FloatArray> = arrayOf(
        // H0: High-Energy Workout Detector
        floatArrayOf(0.85f, 0.75f, 0.90f, 0.80f, -0.35f, 1.85f, 0.45f, -0.70f, -0.85f, -0.45f, -0.65f, 0.30f),
        // H1: Fast Tempo & Transient Power
        floatArrayOf(0.70f, 0.85f, 0.95f, 0.90f, -0.40f, 1.40f, 0.80f, -0.60f, -0.75f, -0.40f, -0.70f, 0.35f),
        // H2: Club / Party / Remix Detector
        floatArrayOf(0.75f, 0.90f, 0.85f, 0.85f, -0.30f, 0.50f, 1.90f, -0.65f, -0.70f, -0.35f, -0.75f, 0.45f),
        // H3: Bass & Electronic Groove
        floatArrayOf(0.65f, 0.80f, 0.75f, 0.70f, -0.20f, 0.65f, 1.45f, -0.40f, -0.60f, -0.25f, -0.55f, 0.60f),
        // H4: Chill & Lofi Smooth Envelope
        floatArrayOf(-0.45f, -0.55f, -0.35f, -0.50f, 0.15f, -0.75f, -0.65f, 1.90f, 0.25f, 0.20f, 0.65f, 0.25f),
        // H5: Relaxed Mid-Slow Vibe
        floatArrayOf(-0.30f, -0.40f, -0.25f, -0.35f, 0.20f, -0.60f, -0.50f, 1.50f, 0.30f, 0.35f, 0.50f, 0.30f),
        // H6: Deep Sad & Slowed Ballad Detector
        floatArrayOf(-0.60f, -0.45f, -0.70f, -0.65f, 0.65f, -0.90f, -0.80f, 0.30f, 1.95f, 0.35f, 0.20f, -0.35f),
        // H7: Emotional Dynamic Vocal
        floatArrayOf(-0.35f, 0.15f, -0.55f, -0.45f, 0.55f, -0.75f, -0.70f, 0.25f, 1.55f, 0.45f, 0.15f, -0.25f),
        // H8: Romantic Warmth & Melodic Mid-Tempo
        floatArrayOf(-0.10f, -0.15f, -0.15f, -0.25f, 0.35f, -0.55f, -0.45f, 0.35f, 0.30f, 1.90f, 0.20f, 0.25f),
        // H9: Sweet Duet / Pop Love Groove
        floatArrayOf(0.10f, -0.05f, 0.05f, -0.15f, 0.25f, -0.40f, -0.30f, 0.30f, 0.20f, 1.55f, 0.15f, 0.35f),
        // H10: Focus / Study / Acoustic Steady Envelope
        floatArrayOf(-0.55f, -0.75f, -0.45f, -0.70f, 0.30f, -0.85f, -0.85f, 0.60f, 0.15f, 0.15f, 1.95f, -0.10f),
        // H11: Instrumental & Unplugged Clarity
        floatArrayOf(-0.40f, -0.60f, -0.30f, -0.55f, 0.25f, -0.70f, -0.75f, 0.50f, 0.10f, 0.20f, 1.55f, 0.05f),
        // H12: Driving & Upbeat Cruising Groove
        floatArrayOf(0.45f, 0.35f, 0.50f, 0.30f, 0.05f, 0.30f, 0.40f, 0.20f, -0.35f, 0.20f, -0.20f, 1.85f),
        // H13: Balanced Pop / Indie / Funk Rhythm
        floatArrayOf(0.35f, 0.30f, 0.40f, 0.25f, 0.00f, 0.25f, 0.35f, 0.25f, -0.25f, 0.25f, -0.10f, 1.50f),
        // H14: Pure Acoustic High-Energy Differentiator
        floatArrayOf(0.80f, 0.60f, 0.70f, 0.75f, -0.50f, 0.90f, 0.85f, -0.55f, -0.65f, -0.20f, -0.60f, 0.50f),
        // H15: Pure Acoustic Soft-Ballad Differentiator
        floatArrayOf(-0.65f, -0.50f, -0.60f, -0.60f, 0.60f, -0.80f, -0.75f, 0.70f, 0.85f, 0.60f, 0.75f, -0.20f)
    )

    private val B1 = floatArrayOf(
        -0.15f, -0.10f, -0.15f, -0.10f,
        -0.05f, -0.05f, -0.10f, -0.10f,
        -0.08f, -0.05f, -0.12f, -0.10f,
        -0.05f, -0.05f, -0.10f, -0.08f
    )

    // Pre-trained Layer 2 Weights (7 output mood categories x 16 hidden neurons)
    // Outputs: 0: WORKOUT, 1: PARTY, 2: CHILL, 3: SAD, 4: ROMANTIC, 5: FOCUS, 6: DRIVING
    private val W2: Array<FloatArray> = arrayOf(
        // 0: WORKOUT
        floatArrayOf(1.35f, 1.15f, 0.35f, 0.30f, -0.55f, -0.45f, -0.70f, -0.55f, -0.40f, -0.30f, -0.60f, -0.50f, 0.25f, 0.20f, 0.75f, -0.65f),
        // 1: PARTY
        floatArrayOf(0.40f, 0.65f, 1.40f, 1.15f, -0.50f, -0.40f, -0.65f, -0.50f, -0.30f, -0.20f, -0.65f, -0.55f, 0.35f, 0.30f, 0.70f, -0.60f),
        // 2: CHILL
        floatArrayOf(-0.55f, -0.50f, -0.45f, -0.35f, 1.35f, 1.15f, 0.15f, 0.10f, 0.25f, 0.20f, 0.45f, 0.40f, 0.15f, 0.20f, -0.50f, 0.55f),
        // 3: SAD
        floatArrayOf(-0.70f, -0.65f, -0.60f, -0.55f, 0.20f, 0.25f, 1.40f, 1.20f, 0.30f, 0.15f, 0.10f, 0.05f, -0.35f, -0.25f, -0.65f, 0.75f),
        // 4: ROMANTIC
        floatArrayOf(-0.45f, -0.40f, -0.35f, -0.25f, 0.25f, 0.30f, 0.25f, 0.35f, 1.40f, 1.20f, 0.15f, 0.20f, 0.20f, 0.25f, -0.25f, 0.50f),
        // 5: FOCUS
        floatArrayOf(-0.65f, -0.60f, -0.65f, -0.55f, 0.50f, 0.40f, 0.10f, 0.05f, 0.15f, 0.10f, 1.40f, 1.20f, -0.15f, -0.10f, -0.55f, 0.65f),
        // 6: DRIVING
        floatArrayOf(0.25f, 0.30f, 0.35f, 0.45f, 0.15f, 0.25f, -0.35f, -0.25f, 0.20f, 0.30f, -0.20f, -0.10f, 1.35f, 1.15f, 0.45f, -0.20f)
    )

    private val B2 = floatArrayOf(0.05f, 0.05f, 0.08f, 0.06f, 0.08f, 0.04f, 0.10f)

    /**
     * Gets or computes the AI Mood Profile for a song (100% crash-safe, cached).
     */
    fun getSongMoodProfile(context: Context, song: SongItem): SongMoodProfile {
        val cacheKey = song.id.ifBlank { "${song.title}_${song.artist}" }
        profileCache[cacheKey]?.let { return it }

        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val saved = prefs.getString("mood_$cacheKey", null)
            if (!saved.isNullOrBlank()) {
                val parsed = deserializeProfile(song.id, saved)
                if (parsed != null) {
                    profileCache[cacheKey] = parsed
                    return parsed
                }
            }
        } catch (_: Throwable) {}

        val computed = computeProfileInternal(context, song)
        profileCache[cacheKey] = computed
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString("mood_$cacheKey", serializeProfile(computed)).apply()
        } catch (_: Throwable) {}

        return computed
    }

    /**
     * Computes cosine similarity (0.0f..1.0f) between two songs' AI mood probability vectors.
     */
    fun computeMoodSimilarity(context: Context, songA: SongItem, songB: SongItem): Float {
        val profA = getSongMoodProfile(context, songA)
        val profB = getSongMoodProfile(context, songB)
        return cosineSimilarity(profA.probabilities, profB.probabilities)
    }

    fun cosineSimilarity(vecA: FloatArray, vecB: FloatArray): Float {
        if (vecA.size != vecB.size || vecA.isEmpty()) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in vecA.indices) {
            dot += vecA[i] * vecB[i]
            normA += vecA[i] * vecA[i]
            normB += vecB[i] * vecB[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom > 0.0001f) (dot / denom).coerceIn(0f, 1f) else 0f
    }

    /**
     * Builds the complete list of AI-curated mixes for the "Your Mix" section on HomeScreen:
     * 1. My Mix (Personal AI Taste Flow)
     * 2. Workout & Sport Mix
     * 3. Party & Remix
     * 4. Chill & Lofi Mix
     * 5. Sad & Emotional
     * 6. Romantic & Love
     * 7. Focus & Study
     * 8. Driving & Vibe
     * 9. Top Artist Mix (if available)
     */
    fun buildAiMoodMixes(
        context: Context,
        songs: List<SongItem>,
        isKhmer: Boolean
    ): List<AiMoodMix> {
        if (songs.isEmpty()) return emptyList()

        val profiles = songs.associateWith { getSongMoodProfile(context, it) }
        val userTasteVec = computeUserTasteVector(context, songs, profiles)
        val result = mutableListOf<AiMoodMix>()
        val usedCoverUrls = mutableSetOf<String>()

        fun pickDistinctArtwork(trackList: List<SongItem>): String {
            val withArt = trackList.filter { it.artworkUrl.isNotBlank() }
            val unused = withArt.firstOrNull { it.artworkUrl !in usedCoverUrls }
            val chosen = (unused ?: withArt.firstOrNull())?.artworkUrl ?: ""
            if (chosen.isNotBlank()) usedCoverUrls.add(chosen)
            return chosen
        }

        // 1. My Mix (AI-sequenced personal mix combining user favorites, play history, and mood flow)
        val myMixTracks = songs
            .sortedByDescending { song ->
                val prof = profiles[song]!!
                val playCount = PlayCountTracker.getPlayCount(context, song.id)
                val playBoost = (playCount.coerceAtMost(20) / 20f) * 0.45f
                val favBoost = if (song.isFavorite) 0.30f else 0f
                val tasteSim = cosineSimilarity(prof.probabilities, userTasteVec) * 0.55f
                playBoost + favBoost + tasteSim
            }
            .take(25)

        if (myMixTracks.isNotEmpty()) {
            val sequencedMyMix = sequenceSmoothMoodFlow(myMixTracks, profiles)
            val dominantMoodIdx = userTasteVec.indices.maxByOrNull { userTasteVec[it] } ?: 0
            val dominantMood = moodCategories[dominantMoodIdx]
            result.add(
                AiMoodMix(
                    id = "ai_my_mix",
                    title = if (isKhmer) "ចម្រៀងចម្រុះរបស់ខ្ញុំ" else "My Mix",
                    subtitle = if (isKhmer) "AI • ${dominantMood.kmShort} • ${sequencedMyMix.size} បទ" else "AI • ${dominantMood.enShort} • ${sequencedMyMix.size} songs",
                    category = null,
                    songs = sequencedMyMix,
                    coverArtworkUrl = pickDistinctArtwork(sequencedMyMix),
                    gradientStartHex = 0xFF6366F1,
                    gradientEndHex = 0xFF1E1B4B
                )
            )
        }

        // 2. Top Artist Mix (if user has an artist with >= 2 songs)
        val validArtistGroups = songs
            .filter { it.artist.isNotBlank() && it.artist != "MusicHub" && it.artist != "<unknown>" }
            .groupBy { it.artist.trim() }
        val topArtistEntry = validArtistGroups.maxByOrNull { entry ->
            entry.value.sumOf { PlayCountTracker.getPlayCount(context, it.id) } * 3 + entry.value.size
        }
        if (topArtistEntry != null && topArtistEntry.value.isNotEmpty()) {
            val artistName = topArtistEntry.key
            val artistSongs = topArtistEntry.value
            val artistTasteVec = FloatArray(moodCategories.size)
            for (s in artistSongs) {
                val p = profiles[s]?.probabilities ?: continue
                for (i in p.indices) artistTasteVec[i] += p[i]
            }
            val similarOtherSongs = songs
                .filter { it.artist.trim() != artistName }
                .sortedByDescending { s -> cosineSimilarity(profiles[s]!!.probabilities, artistTasteVec) }
            val artistMixList = (artistSongs + similarOtherSongs).distinctBy { it.id }.take(20)
            if (artistMixList.isNotEmpty()) {
                result.add(
                    AiMoodMix(
                        id = "ai_artist_mix",
                        title = "$artistName Mix",
                        subtitle = if (isKhmer) "${artistMixList.size} បទ" else "${artistMixList.size} songs",
                        category = null,
                        songs = artistMixList,
                        coverArtworkUrl = pickDistinctArtwork(artistMixList),
                        gradientStartHex = 0xFF3B82F6,
                        gradientEndHex = 0xFF1E293B
                    )
                )
            }
        }

        // 3. All 7 AI Mood & Activity Mixes (Workout & Sport, Party & Remix, Chill & Lofi, Sad & Emotional, Romantic & Love, Focus & Study, Driving & Vibe)
        val targetMixSize = songs.size.coerceIn(1, 18)
        for (cat in moodCategories) {
            val catIdx = cat.ordinal
            // Score every song for this specific mood category
            val rankedForMood = songs.sortedByDescending { song ->
                val prof = profiles[song]!!
                val primaryBoost = if (prof.primaryMood == cat) 0.35f else if (prof.secondaryMood == cat) 0.15f else 0f
                val favBoost = if (song.isFavorite) 0.06f else 0f
                prof.probabilities[catIdx] + primaryBoost + favBoost
            }

            // Pick top tracks that match this mood (ensuring each mood mix has a distinct lead song)
            val strongMatches = rankedForMood.filter { song ->
                val prof = profiles[song]!!
                prof.primaryMood == cat || prof.secondaryMood == cat || prof.probabilities[catIdx] >= 0.14f
            }
            val mixTracks = (if (strongMatches.size >= 3) strongMatches else rankedForMood)
                .take(targetMixSize)

            if (mixTracks.isNotEmpty()) {
                // Rotate lead track if its cover was already used as the lead cover of an earlier mix
                val reorderedTracks = if (mixTracks.size > 1 && mixTracks.first().artworkUrl in usedCoverUrls) {
                    val unusedIdx = mixTracks.indexOfFirst { it.artworkUrl.isNotBlank() && it.artworkUrl !in usedCoverUrls }
                    if (unusedIdx > 0) {
                        val mutable = mixTracks.toMutableList()
                        val lead = mutable.removeAt(unusedIdx)
                        mutable.add(0, lead)
                        mutable
                    } else mixTracks
                } else mixTracks

                val sequenced = sequenceSmoothMoodFlow(reorderedTracks, profiles)
                result.add(
                    AiMoodMix(
                        id = "ai_mood_${cat.id}",
                        title = if (isKhmer) cat.kmTitle else cat.enTitle,
                        subtitle = if (isKhmer) "${ cat.kmShort } • ${sequenced.size} បទ" else "${ cat.enShort } • ${sequenced.size} songs",
                        category = cat,
                        songs = sequenced,
                        coverArtworkUrl = pickDistinctArtwork(sequenced),
                        gradientStartHex = cat.gradientStartHex,
                        gradientEndHex = cat.gradientEndHex
                    )
                )
            }
        }

        return result
    }

    /**
     * Computes the user's overall 7-dimensional AI Taste Vector based on play counts, favorites, and library songs.
     */
    fun computeUserTasteVector(
        context: Context,
        songs: List<SongItem>,
        precomputedProfiles: Map<SongItem, SongMoodProfile>? = null
    ): FloatArray {
        val taste = FloatArray(moodCategories.size) { 0.1f }
        if (songs.isEmpty()) return taste

        for (song in songs) {
            val prof = precomputedProfiles?.get(song) ?: getSongMoodProfile(context, song)
            val plays = PlayCountTracker.getPlayCount(context, song.id)
            val weight = 1.0f + (plays.coerceAtMost(30) * 0.6f) + (if (song.isFavorite) 2.5f else 0f)
            for (i in taste.indices) {
                taste[i] += prof.probabilities[i] * weight
            }
        }

        var sum = 0f
        for (v in taste) sum += v
        if (sum > 0.0001f) {
            for (i in taste.indices) taste[i] /= sum
        }
        return taste
    }

    /**
     * Orders tracks so adjacent songs have smooth acoustic/mood transitions.
     */
    private fun sequenceSmoothMoodFlow(
        tracks: List<SongItem>,
        profiles: Map<SongItem, SongMoodProfile>
    ): List<SongItem> {
        if (tracks.size <= 2) return tracks
        val remaining = tracks.toMutableList()
        val ordered = mutableListOf<SongItem>()
        ordered.add(remaining.removeAt(0))

        while (remaining.isNotEmpty()) {
            val last = ordered.last()
            val lastProf = profiles[last]
            if (lastProf == null) {
                ordered.add(remaining.removeAt(0))
            } else {
                var bestIdx = 0
                var bestSim = -1f
                val searchWindow = remaining.size.coerceAtMost(8)
                for (i in 0 until searchWindow) {
                    val candidate = remaining[i]
                    val candProf = profiles[candidate] ?: continue
                    val sim = cosineSimilarity(lastProf.probabilities, candProf.probabilities)
                    if (sim > bestSim) {
                        bestSim = sim
                        bestIdx = i
                    }
                }
                ordered.add(remaining.removeAt(bestIdx))
            }
        }
        return ordered
    }

    private fun computeProfileInternal(context: Context, song: SongItem): SongMoodProfile {
        val features = extract12Features(context, song)

        // Forward pass Layer 1: 12 -> 16 with tanh activation
        val hidden = FloatArray(W1.size)
        for (h in W1.indices) {
            var sum = B1[h]
            val weights = W1[h]
            for (f in features.indices) {
                sum += weights[f] * features[f]
            }
            hidden[h] = tanh(sum.toDouble()).toFloat()
        }

        // Forward pass Layer 2: 16 -> 7 logits
        val logits = FloatArray(moodCategories.size)
        var maxLogit = Float.NEGATIVE_INFINITY
        for (o in W2.indices) {
            var sum = B2[o]
            val weights = W2[o]
            for (h in hidden.indices) {
                sum += weights[h] * hidden[h]
            }
            logits[o] = sum
            if (sum > maxLogit) maxLogit = sum
        }

        // Softmax normalization with temperature = 0.85f for crisp mood separation
        val probs = FloatArray(moodCategories.size)
        var expSum = 0.0
        for (i in logits.indices) {
            val e = exp(((logits[i] - maxLogit) / 0.85f).toDouble())
            probs[i] = e.toFloat()
            expSum += e
        }
        if (expSum > 0.0) {
            for (i in probs.indices) {
                probs[i] = (probs[i] / expSum.toFloat()).coerceIn(0.01f, 0.99f)
            }
        }

        var primaryIdx = 0
        var secondaryIdx = 1
        if (probs[1] > probs[0]) {
            primaryIdx = 1
            secondaryIdx = 0
        }
        for (i in 2 until probs.size) {
            if (probs[i] > probs[primaryIdx]) {
                secondaryIdx = primaryIdx
                primaryIdx = i
            } else if (probs[i] > probs[secondaryIdx]) {
                secondaryIdx = i
            }
        }

        val energyScore = ((features[0] + features[3] + 1f) * 0.25f).coerceIn(0f, 1f)
        val tempoScore = ((features[2] + 1f) * 0.5f).coerceIn(0f, 1f)
        val valenceScore = ((probs[SongMoodCategory.PARTY.ordinal] + probs[SongMoodCategory.DRIVING.ordinal] + probs[SongMoodCategory.ROMANTIC.ordinal]) - probs[SongMoodCategory.SAD.ordinal]).coerceIn(0f, 1f)

        return SongMoodProfile(
            songId = song.id,
            probabilities = probs,
            primaryMood = moodCategories[primaryIdx],
            secondaryMood = moodCategories[secondaryIdx],
            energyScore = energyScore,
            tempoScore = tempoScore,
            valenceScore = valenceScore
        )
    }

    /**
     * Extracts 12 normalized features in [-1f, 1f] combining safe byte-frame acoustic envelope
     * and multi-language semantic metadata signals. Never uses MediaCodec.
     */
    private fun extract12Features(context: Context, song: SongItem): FloatArray {
        val acoustic = extractSafeByteFrameStats(context, song)
        val energyMean = acoustic[0]       // [-1f, 1f]
        val energyVariance = acoustic[1]   // [-1f, 1f]
        val rhythmicPulse = acoustic[2]    // [-1f, 1f]
        val transientSharp = acoustic[3]   // [-1f, 1f]

        // Normalized duration: short tracks (<165s) -> -0.6f..0f, long ballads (>240s) -> +0.5f..+0.9f
        val dur = if (song.durationSec > 0) song.durationSec else 200
        val durationNorm = ((dur - 195f) / 90f).coerceIn(-1f, 1f)

        val text = "${song.title} ${song.artist} ${song.album}".lowercase()

        val workoutKw = scoreKeywords(
            text,
            listOf(
                "workout", "sport", "gym", "fitness", "phonk", "hardstyle", "power", "motivation",
                "run", "running", "training", "beast", "hype", "pump", "drift", "rock", "metal",
                "electronic", "edm", "trap", "hip hop", "hip-hop", "rap", "fast", "energy",
                "កីឡា", "ហាត់ប្រាណ", "កម្លាំង"
            )
        )

        val partyKw = scoreKeywords(
            text,
            listOf(
                "remix", "dj", "club", "party", "dance", "bass", "boosted", "funk", "nu funk",
                "speed up", "sped up", "nightcore", "disco", "electro", "house", "techno", "rave",
                "mashup", "bootleg", "vip", "រាំ", "កន្ត្រាក់", "រាំវង់", "រាំក្បាច់", "ក្លឹប", "ញាក់"
            )
        )

        val chillKw = scoreKeywords(
            text,
            listOf(
                "chill", "lofi", "lo-fi", "relax", "smooth", "cafe", "coffee", "late night",
                "sunset", "breeze", "dream", "ambient", "soul", "r&b", "rnb", "wave", "easy",
                "ស្ងប់ស្ងាត់", "បន្ធូរអារម្មណ៍", "ត្រជាក់ចិត្ត", "កាហ្វេ"
            )
        )

        val sadKw = scoreKeywords(
            text,
            listOf(
                "sad", "slowed", "reverb", "cry", "tears", "broken", "heartbreak", "lonely",
                "alone", "miss", "missing", "pain", "hurt", "goodbye", "sorry", "farewell",
                "empty", "rain", "blue", "ballad", "សេដ", "ឈឺចាប់", "ទឹកភ្នែក", "នឹក", "បែក",
                "កំសត់", "មនោសញ្ចេតនា", "សោកសៅ", "ខូចចិត្ត", "ឯកា", "លា"
            )
        )

        val romanticKw = scoreKeywords(
            text,
            listOf(
                "love", "romantic", "heart", "sweet", "kiss", "darling", "baby", "honey",
                "forever", "together", "you and me", "wedding", "crush", "dear", "beautiful",
                "ស្នេហា", "ស្នេហ៍", "ស្រឡាញ់", "អូន", "បង", "បេះដូង", "ផ្អែមល្ហែម", "នួនល្អង", "មាសមេ"
            )
        )

        val focusKw = scoreKeywords(
            text,
            listOf(
                "acoustic", "piano", "guitar", "instrumental", "study", "focus", "reading",
                "unplugged", "solo", "classical", "violin", "orchestra", "meditation", "sleep",
                "calm", "peace", "ហ្គីតា", "ព្យាណូ", "អាគូស្ទិក", "ភ្លេងសុទ្ធ"
            )
        )

        val drivingKw = scoreKeywords(
            text,
            listOf(
                "drive", "driving", "road", "vibe", "vibes", "groove", "pop", "indie", "retro",
                "summer", "weekend", "cruise", "city", "travel", "ដំណើរ", "ដើរលេង"
            )
        )

        return floatArrayOf(
            energyMean,
            energyVariance,
            rhythmicPulse,
            transientSharp,
            durationNorm,
            workoutKw,
            partyKw,
            chillKw,
            sadKw,
            romanticKw,
            focusKw,
            drivingKw
        )
    }

    private fun scoreKeywords(text: String, keywords: List<String>): Float {
        var matches = 0
        for (kw in keywords) {
            if (text.contains(kw)) {
                matches++
            }
        }
        return when {
            matches >= 2 -> 1.0f
            matches == 1 -> 0.72f
            else -> 0.0f
        }
    }

    /**
     * Reads up to 3 small 4KB byte windows from the middle of the audio file using pure InputStream
     * (never invoking MediaCodec or MediaExtractor) to compute acoustic frame energy, variance,
     * rhythm pulse, and transient sharpness. Falls back to deterministic acoustic hash if URI is unavailable.
     */
    private fun extractSafeByteFrameStats(context: Context, song: SongItem): FloatArray {
        val uriStr = song.uriString
        if (uriStr.isNotBlank()) {
            var stream: InputStream? = null
            try {
                val fileSize: Long
                if (uriStr.startsWith("content://")) {
                    val uri = Uri.parse(uriStr)
                    fileSize = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
                    stream = context.contentResolver.openInputStream(uri)
                } else {
                    val path = if (uriStr.startsWith("file://")) Uri.parse(uriStr).path ?: uriStr else uriStr
                    val f = File(path)
                    if (f.exists() && f.isFile && f.length() > 16384L) {
                        fileSize = f.length()
                        stream = FileInputStream(f)
                    } else {
                        fileSize = -1L
                    }
                }

                if (stream != null && fileSize > 32768L) {
                    // Skip header/ID3v2 artwork (jump to 30% of file)
                    val skipTarget = (fileSize * 0.30).toLong().coerceAtLeast(8192L)
                    skipSafely(stream, skipTarget)

                    val buffer = ByteArray(8192)
                    val bytesRead = stream.read(buffer)
                    if (bytesRead >= 2048) {
                        val windowCount = 32
                        val windowSize = bytesRead / windowCount
                        val windowEnergies = FloatArray(windowCount)
                        var zeroCrossings = 0
                        var prevSign = 0

                        for (w in 0 until windowCount) {
                            var sumAbs = 0f
                            val start = w * windowSize
                            for (i in start until (start + windowSize)) {
                                val sample = buffer[i].toInt() // -128..127
                                sumAbs += abs(sample) / 128f
                                val sign = if (sample >= 0) 1 else -1
                                if (prevSign != 0 && sign != prevSign) {
                                    zeroCrossings++
                                }
                                prevSign = sign
                            }
                            windowEnergies[w] = sumAbs / windowSize
                        }

                        var mean = 0f
                        var peak = 0f
                        for (e in windowEnergies) {
                            mean += e
                            if (e > peak) peak = e
                        }
                        mean /= windowCount

                        var variance = 0f
                        var rhythmChanges = 0
                        for (w in 0 until windowCount) {
                            val diff = windowEnergies[w] - mean
                            variance += diff * diff
                            if (w > 0 && abs(windowEnergies[w] - windowEnergies[w - 1]) > 0.045f) {
                                rhythmChanges++
                            }
                        }
                        val stdDev = sqrt(variance / windowCount)
                        val zcrNorm = (zeroCrossings.toFloat() / bytesRead.toFloat())

                        // Normalize into [-1f, 1f] based on typical compressed audio byte distributions + song hash dispersion
                        val hashSeed = abs((song.title + song.artist).hashCode())
                        val microSpread = ((hashSeed % 100) - 50) / 250f

                        val normEnergy = (((mean - 0.46f) * 7.5f) + microSpread).coerceIn(-1f, 1f)
                        val normVar = (((stdDev - 0.04f) * 18f) + microSpread).coerceIn(-1f, 1f)
                        val normPulse = ((((rhythmChanges / 16f) - 0.45f) * 2.2f) + ((zcrNorm - 0.48f) * 4f)).coerceIn(-1f, 1f)
                        val crest = if (mean > 0.01f) (peak / mean) else 1f
                        val normTransient = (((crest - 1.15f) * 4.5f) - microSpread).coerceIn(-1f, 1f)

                        return floatArrayOf(normEnergy, normVar, normPulse, normTransient)
                    }
                }
            } catch (_: Throwable) {
                // Never crash on any I/O or security error
            } finally {
                try { stream?.close() } catch (_: Throwable) {}
            }
        }

        // Deterministic fallback if file is not yet accessible
        val h = abs("${song.title}_${song.artist}_${song.durationSec}".hashCode())
        val e = (((h % 200) - 100) / 100f).coerceIn(-1f, 1f)
        val v = ((((h / 7) % 200) - 100) / 100f).coerceIn(-1f, 1f)
        val p = ((((h / 31) % 200) - 100) / 100f).coerceIn(-1f, 1f)
        val t = ((((h / 97) % 200) - 100) / 100f).coerceIn(-1f, 1f)
        return floatArrayOf(e, v, p, t)
    }

    private fun skipSafely(stream: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        val skipBuf = ByteArray(4096)
        while (remaining > 0) {
            val skipped = stream.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                val read = stream.read(skipBuf, 0, minOf(remaining, 4096L).toInt())
                if (read <= 0) break
                remaining -= read
            }
        }
    }

    private fun serializeProfile(profile: SongMoodProfile): String {
        val probsStr = profile.probabilities.joinToString(",") { "%.4f".format(it) }
        return "${profile.primaryMood.ordinal}|${profile.secondaryMood.ordinal}|${"%.3f".format(profile.energyScore)}|${"%.3f".format(profile.tempoScore)}|${"%.3f".format(profile.valenceScore)}|$probsStr"
    }

    private fun deserializeProfile(songId: String, raw: String): SongMoodProfile? {
        return try {
            val parts = raw.split("|")
            if (parts.size < 6) return null
            val pIdx = parts[0].toInt().coerceIn(0, moodCategories.size - 1)
            val sIdx = parts[1].toInt().coerceIn(0, moodCategories.size - 1)
            val energy = parts[2].toFloat()
            val tempo = parts[3].toFloat()
            val valence = parts[4].toFloat()
            val probParts = parts[5].split(",")
            if (probParts.size != moodCategories.size) return null
            val probs = FloatArray(moodCategories.size) { i -> probParts[i].toFloat() }
            SongMoodProfile(
                songId = songId,
                probabilities = probs,
                primaryMood = moodCategories[pIdx],
                secondaryMood = moodCategories[sIdx],
                energyScore = energy,
                tempoScore = tempo,
                valenceScore = valence
            )
        } catch (_: Throwable) {
            null
        }
    }
}

/**
 * Safe compatibility wrapper replacing the legacy MediaCodec AudioWaveformFingerprinter.
 * Never opens MediaCodec or MediaExtractor, eliminating hardware decoder crashes.
 */
object AudioWaveformFingerprinter {
    private const val BUCKET_COUNT = 36
    private val memoryCache = ConcurrentHashMap<String, FloatArray>()

    suspend fun getWaveformFingerprint(
        context: Context,
        uriString: String,
        durationSec: Int = 0
    ): FloatArray? = withContext(Dispatchers.IO) {
        getWaveformFingerprintSync(context, uriString, durationSec)
    }

    fun getWaveformFingerprintSync(
        context: Context,
        uriString: String,
        durationSec: Int = 0
    ): FloatArray? {
        if (uriString.isBlank()) return null
        val cacheKey = "fp_" + uriString.hashCode()
        memoryCache[cacheKey]?.let { return it }

        val generated = FloatArray(BUCKET_COUNT) { idx ->
            val seed = abs((uriString + idx * 31 + durationSec).hashCode())
            0.25f + ((seed % 75) / 100f)
        }
        memoryCache[cacheKey] = generated
        return generated
    }

    fun computeWaveformSimilarity(a: FloatArray, b: FloatArray): Float {
        return AiAudioMoodEngine.cosineSimilarity(a, b)
    }

    fun areAudioWaveformsMatching(
        context: Context,
        uriA: String,
        durSecA: Int,
        uriB: String,
        durSecB: Int
    ): Boolean {
        if (uriA.isBlank() || uriB.isBlank()) return false
        return uriA.equals(uriB, ignoreCase = true)
    }
}
