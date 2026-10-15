package com.musichub.app.domain.mood

import com.musichub.app.data.database.LyricsEntity
import com.musichub.app.data.database.MoodPredictionEntity
import com.musichub.app.domain.audio.AudioDspFeatureExtractor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Uncertainty-weighted Bayesian late-fusion engine.
 * Combines continuous audio valence/arousal with lyrics sentiment.
 * Explicitly separates Sonic Mood (Audio) from Lyrical Emotion (Text).
 */
object HybridMoodFusionEngine {

    data class FusedMoodResult(
        val valence: Float,       // [-1.0 .. 1.0]
        val arousal: Float,       // [-1.0 .. 1.0]
        val primaryMood: String,   // CALM, ENERGETIC, SAD, HAPPY, ROMANTIC
        val confidence: Float,    // [0.0 .. 1.0]
        val dissonanceScore: Float, // [0.0 .. 1.0]
        val isDissonant: Boolean
    )

    data class MultiModalMusicProfile(
        val songId: String,
        // Sonic (Audio) Modality
        val sonicMood: String,
        val sonicValence: Float,
        val sonicArousal: Float,
        val bpm: Int,
        val energy: Float,
        val danceability: Float,
        val genre: String,
        val audioEmbedding: FloatArray, // 64-D
        // Lyrical (Text) Modality
        val hasLyrics: Boolean,
        val lyricalEmotion: String?,    // "HEARTBREAK", "ROMANCE", "CELEBRATION", "PEACE", "ANGER", "MOTIVATION"
        val lyricalValence: Float?,
        val lyricalArousal: Float?,
        val lyricalThemes: List<String>,
        val lyricEmbedding: FloatArray?, // 32-D
        // Combined & Dissonance Signals
        val overallMood: String,
        val confidence: Float,
        val isDissonant: Boolean,       // e.g. Upbeat music with heartbroken lyrics
        val dissonanceScore: Float,
        val isLyricDependentMood: Boolean,
        val lyricConfidenceCapped: Boolean
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is MultiModalMusicProfile) return false
            return songId == other.songId &&
                    sonicMood == other.sonicMood &&
                    bpm == other.bpm &&
                    genre == other.genre &&
                    hasLyrics == other.hasLyrics &&
                    overallMood == other.overallMood &&
                    isDissonant == other.isDissonant
        }

        override fun hashCode(): Int {
            var result = songId.hashCode()
            result = 31 * result + sonicMood.hashCode()
            result = 31 * result + bpm
            result = 31 * result + genre.hashCode()
            result = 31 * result + hasLyrics.hashCode()
            result = 31 * result + overallMood.hashCode()
            result = 31 * result + isDissonant.hashCode()
            return result
        }
    }

    // Prototype centroids in continuous [-1.0 .. 1.0] affect space
    private val prototypes = mapOf(
        "HAPPY" to Pair(0.65f, 0.60f),
        "ENERGETIC" to Pair(-0.10f, 0.75f),
        "CALM" to Pair(0.55f, -0.50f),
        "SAD" to Pair(-0.60f, -0.45f),
        "ROMANTIC" to Pair(0.60f, 0.10f)
    )

    fun fuse(
        audioPrediction: MoodPredictionEntity,
        lyrics: LyricsEntity?
    ): FusedMoodResult {
        // Normalize audio valence/arousal from [0..1] to [-1..1]
        val va = (audioPrediction.valence * 2f - 1f).coerceIn(-1f, 1f)
        val aa = (audioPrediction.arousal * 2f - 1f).coerceIn(-1f, 1f)
        val ca = audioPrediction.confidence.coerceIn(0.1f, 1.0f)

        // Check lyrics availability
        val vl = lyrics?.lyricsValence
        val al = lyrics?.lyricsArousal
        val cl = lyrics?.lyricsConfidence ?: 0f
        val isInstrumental = lyrics?.isInstrumental == true

        if (lyrics == null || vl == null || al == null || cl < 0.15f || isInstrumental) {
            // Audio-only modality
            val primaryMood = classifyPrototype(va, aa)
            return FusedMoodResult(
                valence = va,
                arousal = aa,
                primaryMood = primaryMood,
                confidence = ca,
                dissonanceScore = 0f,
                isDissonant = false
            )
        }

        // Both modalities present: Compute Dissonance
        val distance = sqrt((va - vl) * (va - vl) + (aa - al) * (aa - al))
        val maxDist = 2.828427f // 2 * sqrt(2)
        val dissonance = (distance / maxDist).coerceIn(0f, 1f)
        val isDissonant = dissonance > 0.45f && ca > 0.50f && cl > 0.40f

        // Audio Prior (70%), Lyrics Prior (30%)
        val wa = 0.70f * ca
        val wl = 0.30f * cl
        val totalWeight = wa + wl

        val fusedValence = if (totalWeight > 0.001f) ((wa * va + wl * vl) / totalWeight).coerceIn(-1f, 1f) else va
        val fusedArousal = if (totalWeight > 0.001f) ((wa * aa + wl * al) / totalWeight).coerceIn(-1f, 1f) else aa

        val fusedConfidence = min(1.0f, sqrt(wa * wa + wl * wl) * (1.0f - 0.35f * dissonance))
        val primaryMood = classifyPrototype(fusedValence, fusedArousal)

        return FusedMoodResult(
            valence = fusedValence,
            arousal = fusedArousal,
            primaryMood = primaryMood,
            confidence = fusedConfidence,
            dissonanceScore = dissonance,
            isDissonant = isDissonant
        )
    }

    /**
     * Complete multi-modal music understanding separating sonic mood from lyrical emotion,
     * with confidence capping on lyric-dependent moods when lyrics are absent.
     */
    fun fuseMultiModal(
        songId: String,
        audioFeatures: AudioDspFeatureExtractor.DspFeatures,
        audioPrediction: MoodPredictionEntity?,
        lyrics: LyricsEntity?
    ): MultiModalMusicProfile {
        val va = audioPrediction?.let { (it.valence * 2f - 1f).coerceIn(-1f, 1f) }
            ?: ((audioFeatures.energy * 2f - 1f) * 0.5f)
        val aa = audioPrediction?.let { (it.arousal * 2f - 1f).coerceIn(-1f, 1f) }
            ?: ((audioFeatures.danceability * 2f - 1f) * 0.7f)
        val ca = audioPrediction?.confidence ?: 0.65f

        val sonicMood = classifyPrototype(va, aa)

        val hasLyrics = lyrics != null &&
                !lyrics.plainLyrics.isNullOrBlank() &&
                !lyrics.isInstrumental &&
                lyrics.lyricsValence != null &&
                lyrics.lyricsArousal != null

        if (lyrics == null || !hasLyrics) {
            // Missing Lyrics Fallback: Smoothly rely on audio analysis.
            // If the acoustic profile resembles a soft, slow ballad (low energy, low valence),
            // cap confidence on lyric-dependent moods (e.g. Heartbroken, Melancholy) at 0.20f.
            val isLyricDependent = va < -0.30f || (audioFeatures.energy < 0.35f && va < 0.0f)
            val finalConfidence = if (isLyricDependent) min(ca, 0.20f) else ca

            return MultiModalMusicProfile(
                songId = songId,
                sonicMood = sonicMood,
                sonicValence = va,
                sonicArousal = aa,
                bpm = audioFeatures.bpm,
                energy = audioFeatures.energy,
                danceability = audioFeatures.danceability,
                genre = audioFeatures.genre,
                audioEmbedding = audioFeatures.audioEmbedding,
                hasLyrics = false,
                lyricalEmotion = null,
                lyricalValence = null,
                lyricalArousal = null,
                lyricalThemes = emptyList(),
                lyricEmbedding = null,
                overallMood = sonicMood,
                confidence = finalConfidence,
                isDissonant = false,
                dissonanceScore = 0f,
                isLyricDependentMood = isLyricDependent,
                lyricConfidenceCapped = isLyricDependent
            )
        }

        // Both Audio and Lyrics Available
        val vl = lyrics.lyricsValence!!
        val al = lyrics.lyricsArousal!!
        val cl = lyrics.lyricsConfidence ?: 0.5f

        val distance = sqrt((va - vl) * (va - vl) + (aa - al) * (aa - al))
        val maxDist = 2.828427f
        val dissonance = (distance / maxDist).coerceIn(0f, 1f)
        val isDissonant = dissonance > 0.42f && ca > 0.45f && cl > 0.35f

        // Map lyrical emotion
        val lyricalEmotion = when {
            vl < -0.40f -> "HEARTBREAK"
            vl > 0.45f && al > 0.30f -> "CELEBRATION"
            vl > 0.40f && al <= 0.30f -> "ROMANCE"
            vl in -0.30f..0.30f && al < -0.20f -> "PEACE"
            vl < -0.30f && al > 0.40f -> "ANGER"
            else -> "NEUTRAL"
        }

        // Extract semantic themes and lyric embedding from lyrics content
        val analysis = if (!lyrics.plainLyrics.isNullOrBlank()) {
            com.musichub.app.domain.lyrics.LyricsSentimentEngine.analyze(lyrics.plainLyrics)
        } else null
        val themes = analysis?.themes ?: emptyList()
        val lyricEmbedding = analysis?.lyricEmbedding

        val fused = fuse(
            audioPrediction = audioPrediction ?: MoodPredictionEntity(
                songId = songId,
                modelVersion = "dsp",
                valence = (va + 1f) / 2f,
                arousal = (aa + 1f) / 2f,
                calm = 0.25f,
                energetic = 0.25f,
                sad = 0.25f,
                romantic = 0.25f,
                confidence = ca,
                analyzedAt = System.currentTimeMillis()
            ),
            lyrics = lyrics
        )

        return MultiModalMusicProfile(
            songId = songId,
            sonicMood = sonicMood,
            sonicValence = va,
            sonicArousal = aa,
            bpm = audioFeatures.bpm,
            energy = audioFeatures.energy,
            danceability = audioFeatures.danceability,
            genre = audioFeatures.genre,
            audioEmbedding = audioFeatures.audioEmbedding,
            hasLyrics = true,
            lyricalEmotion = lyricalEmotion,
            lyricalValence = vl,
            lyricalArousal = al,
            lyricalThemes = themes,
            lyricEmbedding = lyricEmbedding,
            overallMood = fused.primaryMood,
            confidence = fused.confidence,
            isDissonant = isDissonant,
            dissonanceScore = dissonance,
            isLyricDependentMood = false,
            lyricConfidenceCapped = false
        )
    }

    private fun classifyPrototype(valence: Float, arousal: Float): String {
        var minDistance = Float.MAX_VALUE
        var closestMood = "CALM"

        for ((mood, point) in prototypes) {
            val dv = valence - point.first
            val da = arousal - point.second
            val dist = dv * dv + da * da
            if (dist < minDistance) {
                minDistance = dist
                closestMood = mood
            }
        }

        return closestMood
    }
}
