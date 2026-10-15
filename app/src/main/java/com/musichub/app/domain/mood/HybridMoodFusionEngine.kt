package com.musichub.app.domain.mood

import com.musichub.app.data.database.LyricsEntity
import com.musichub.app.data.database.MoodPredictionEntity
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Uncertainty-weighted Bayesian late-fusion engine.
 * Combines continuous audio valence/arousal with lyrics sentiment.
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
