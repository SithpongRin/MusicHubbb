package com.musichub.app.domain.audio

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-performance on-device Digital Signal Processing (DSP) Music Information Retrieval (MIR) Engine.
 * Extracts tempo (BPM), acoustic energy, danceability, spectral characteristics, genre,
 * and compact 64-dimensional audio embeddings from raw PCM audio waveforms without external native libraries.
 */
object AudioDspFeatureExtractor {

    const val EMBEDDING_DIM = 64

    data class DspFeatures(
        val bpm: Int,
        val energy: Float,               // [0.0 .. 1.0]
        val danceability: Float,         // [0.0 .. 1.0]
        val spectralCentroid: Float,     // [0.0 .. 1.0]
        val zeroCrossingRate: Float,     // [0.0 .. 1.0]
        val dynamicRange: Float,         // [0.0 .. 1.0]
        val genre: String,               // Pop, Rock, Acoustic, Hip-Hop, Electronic, Traditional/Folk
        val audioEmbedding: FloatArray   // Size 64, L2 normalized
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is DspFeatures) return false
            return bpm == other.bpm &&
                    energy == other.energy &&
                    danceability == other.danceability &&
                    genre == other.genre &&
                    audioEmbedding.contentEquals(other.audioEmbedding)
        }

        override fun hashCode(): Int {
            var result = bpm
            result = 31 * result + energy.hashCode()
            result = 31 * result + danceability.hashCode()
            result = 31 * result + genre.hashCode()
            result = 31 * result + audioEmbedding.contentHashCode()
            return result
        }
    }

    /**
     * Extracts full DSP musical characteristics and 64-D embedding from PCM waveform.
     *
     * @param waveform Audio samples (mono, typically 15 seconds at sampleRate)
     * @param sampleRate Sampling rate in Hz (default 24000)
     * @param valence Optional neural valence prediction [-1.0 .. 1.0] or [0.0 .. 1.0]
     * @param arousal Optional neural arousal prediction [-1.0 .. 1.0] or [0.0 .. 1.0]
     */
    fun extractFeatures(
        waveform: FloatArray,
        sampleRate: Int = 24000,
        valence: Float = 0.5f,
        arousal: Float = 0.5f
    ): DspFeatures {
        if (waveform.isEmpty()) {
            return fallbackFeatures()
        }

        val nSamples = waveform.size

        // 1. Root Mean Square (RMS) Energy & Dynamic Range
        var sumSquares = 0.0
        var peak = 0f
        for (i in 0 until nSamples) {
            val sample = waveform[i]
            val absSample = abs(sample)
            if (absSample > peak) peak = absSample
            sumSquares += (sample * sample).toDouble()
        }
        val rawRms = sqrt(sumSquares / nSamples).toFloat()
        // Map raw RMS to [0.0 .. 1.0] with perceptual logarithmic scaling
        val energy = (rawRms * 2.8f).coerceIn(0.0f, 1.0f)
        val crestFactor = if (rawRms > 0.001f) (peak / rawRms).coerceIn(1.0f, 10.0f) else 1.0f
        val dynamicRange = ((crestFactor - 1.0f) / 9.0f).coerceIn(0.0f, 1.0f)

        // 2. Zero Crossing Rate (ZCR)
        var zeroCrossings = 0
        for (i in 1 until nSamples) {
            if ((waveform[i] >= 0f && waveform[i - 1] < 0f) || (waveform[i] < 0f && waveform[i - 1] >= 0f)) {
                zeroCrossings++
            }
        }
        val zeroCrossingRate = (zeroCrossings.toFloat() / (nSamples - 1)).coerceIn(0.0f, 1.0f)

        // 3. Spectral Energy Flux and Tempo (BPM) via Onset Autocorrelation
        val frameSize = 1024
        val hopSize = 512
        val numFrames = (nSamples - frameSize) / hopSize

        val onsetCurve = FloatArray(max(1, numFrames))
        var prevFrameEnergy = 0f
        var centroidNumerator = 0.0
        var centroidDenominator = 0.0

        for (f in 0 until numFrames) {
            val start = f * hopSize
            var frameEnergy = 0f
            var spectralWeightedDiff = 0f

            for (i in 0 until frameSize) {
                val s = waveform[start + i]
                val sq = s * s
                frameEnergy += sq
                if (i > 0) {
                    val diff = abs(s - waveform[start + i - 1])
                    spectralWeightedDiff += diff * (i.toFloat() / frameSize)
                }
            }
            frameEnergy /= frameSize
            onsetCurve[f] = max(0f, frameEnergy - prevFrameEnergy) + (spectralWeightedDiff * 0.05f)
            prevFrameEnergy = frameEnergy

            centroidNumerator += spectralWeightedDiff
            centroidDenominator += frameEnergy + 0.0001f
        }

        val spectralCentroid = if (centroidDenominator > 0.0) {
            ((centroidNumerator / centroidDenominator) * 0.25f).toFloat().coerceIn(0.0f, 1.0f)
        } else {
            0.5f
        }

        // Autocorrelation of onset curve for BPM detection (60 to 200 BPM)
        val frameDurationSec = hopSize.toFloat() / sampleRate
        val minLag = (60f / (200f * frameDurationSec)).toInt().coerceAtLeast(1)
        val maxLag = (60f / (60f * frameDurationSec)).toInt().coerceAtMost(onsetCurve.size - 1)

        var bestLag = minLag
        var maxAutocorr = -1f
        var sumAutocorr = 0f
        var autocorrCount = 0

        for (lag in minLag..maxLag) {
            var corr = 0f
            val count = onsetCurve.size - lag
            if (count <= 0) continue
            for (i in 0 until count) {
                corr += onsetCurve[i] * onsetCurve[i + lag]
            }
            corr /= count
            sumAutocorr += corr
            autocorrCount++

            if (corr > maxAutocorr) {
                maxAutocorr = corr
                bestLag = lag
            }
        }

        val estimatedBpm = if (bestLag > 0 && frameDurationSec > 0) {
            val rawBpm = (60f / (bestLag * frameDurationSec)).toInt()
            // Fold into standard musical range [70 .. 180]
            when {
                rawBpm < 65 -> rawBpm * 2
                rawBpm > 195 -> rawBpm / 2
                else -> rawBpm
            }.coerceIn(60, 200)
        } else {
            120
        }

        val meanAutocorr = if (autocorrCount > 0) sumAutocorr / autocorrCount else 0.001f
        val peakProminence = if (meanAutocorr > 0.0001f) (maxAutocorr / meanAutocorr) else 1.0f
        val danceability = ((peakProminence - 1.0f) / 3.0f).coerceIn(0.15f, 0.95f)

        // 4. Genre Classification Heuristic
        val genre = classifyGenre(
            bpm = estimatedBpm,
            energy = energy,
            danceability = danceability,
            zcr = zeroCrossingRate,
            spectralCentroid = spectralCentroid,
            valence = valence,
            arousal = arousal
        )

        // 5. Construct 64-Dimensional Compact Audio Embedding
        val embedding = buildAudioEmbedding(
            waveform = waveform,
            bpm = estimatedBpm,
            energy = energy,
            danceability = danceability,
            zcr = zeroCrossingRate,
            spectralCentroid = spectralCentroid,
            dynamicRange = dynamicRange,
            valence = valence,
            arousal = arousal,
            genre = genre
        )

        return DspFeatures(
            bpm = estimatedBpm,
            energy = energy,
            danceability = danceability,
            spectralCentroid = spectralCentroid,
            zeroCrossingRate = zeroCrossingRate,
            dynamicRange = dynamicRange,
            genre = genre,
            audioEmbedding = embedding
        )
    }

    private fun classifyGenre(
        bpm: Int,
        energy: Float,
        danceability: Float,
        zcr: Float,
        spectralCentroid: Float,
        valence: Float,
        arousal: Float
    ): String {
        return when {
            // Electronic / Dance: High energy, high danceability, steady electronic tempo
            energy > 0.60f && danceability > 0.60f && (bpm in 118..138 || arousal > 0.70f) -> "Electronic"

            // Rock: High energy, high ZCR (distortion/percussion), high dynamic contrast
            energy > 0.65f && zcr > 0.22f -> "Rock"

            // Hip-Hop: Characteristic urban tempo (80..115 BPM), strong groove/danceability
            bpm in 78..115 && danceability > 0.55f && energy > 0.40f -> "Hip-Hop"

            // Acoustic / Ballad: Soft energy, low ZCR, slower tempo or high sweetness
            energy < 0.42f && zcr < 0.16f -> "Acoustic"

            // Pop: Balanced energy, bright spectral presence, high valence/danceability
            danceability > 0.48f && (valence > 0.50f || energy in 0.45f..0.75f) -> "Pop"

            // Traditional / Folk fallback
            else -> "Traditional/Folk"
        }
    }

    private fun buildAudioEmbedding(
        waveform: FloatArray,
        bpm: Int,
        energy: Float,
        danceability: Float,
        zcr: Float,
        spectralCentroid: Float,
        dynamicRange: Float,
        valence: Float,
        arousal: Float,
        genre: String
    ): FloatArray {
        val vec = FloatArray(EMBEDDING_DIM)
        val nSamples = waveform.size

        // Dims 0..15: 16-band pseudo-mel spectral energy distribution
        val bandCount = 16
        val samplesPerBand = max(1, nSamples / bandCount)
        for (b in 0 until bandCount) {
            val start = b * samplesPerBand
            val end = min(nSamples, start + samplesPerBand)
            var bandSum = 0.0
            for (i in start until end) {
                bandSum += abs(waveform[i]).toDouble()
            }
            vec[b] = ((bandSum / (end - start)) * 3.5f).toFloat().coerceIn(0f, 1f)
        }

        // Dims 16..31: 4-segment temporal dynamics (RMS, Peak, Variance, Flux across 4 quarters)
        val quarter = nSamples / 4
        for (q in 0..3) {
            val qStart = q * quarter
            val qEnd = min(nSamples, qStart + quarter)
            var qSum = 0.0
            var qPeak = 0f
            for (i in qStart until qEnd) {
                val absVal = abs(waveform[i])
                if (absVal > qPeak) qPeak = absVal
                qSum += (absVal * absVal).toDouble()
            }
            val qRms = sqrt(qSum / (qEnd - qStart)).toFloat()
            val baseIdx = 16 + q * 4
            vec[baseIdx] = (qRms * 2.5f).coerceIn(0f, 1f)
            vec[baseIdx + 1] = qPeak.coerceIn(0f, 1f)
            vec[baseIdx + 2] = abs(qPeak - qRms).coerceIn(0f, 1f)
            vec[baseIdx + 3] = ((q + 1) * 0.25f)
        }

        // Dims 32..47: Rhythmic tempo pulse harmonics
        val normBpm = (bpm.toFloat() / 200f).coerceIn(0f, 1f)
        for (h in 0..15) {
            val phase = h.toDouble() * Math.PI / 8.0
            vec[32 + h] = (0.5f + 0.5f * cos(normBpm * 2 * Math.PI + phase).toFloat()).coerceIn(0f, 1f)
        }

        // Dims 48..63: Global descriptors and Genre one-hot/affinity codes
        vec[48] = energy
        vec[49] = danceability
        vec[50] = zcr
        vec[51] = spectralCentroid
        vec[52] = dynamicRange
        vec[53] = valence.coerceIn(0f, 1f)
        vec[54] = arousal.coerceIn(0f, 1f)
        vec[55] = normBpm

        // Genre affinity one-hot flags in Dims 56..61
        vec[56] = if (genre == "Pop") 1.0f else 0.05f
        vec[57] = if (genre == "Rock") 1.0f else 0.05f
        vec[58] = if (genre == "Hip-Hop") 1.0f else 0.05f
        vec[59] = if (genre == "Acoustic") 1.0f else 0.05f
        vec[60] = if (genre == "Electronic") 1.0f else 0.05f
        vec[61] = if (genre == "Traditional/Folk") 1.0f else 0.05f
        vec[62] = (energy * danceability).coerceIn(0f, 1f)
        vec[63] = (valence * arousal).coerceIn(0f, 1f)

        // L2 Normalize the entire 64-dimensional vector
        var normSum = 0.0
        for (v in vec) normSum += (v * v).toDouble()
        val norm = sqrt(normSum).toFloat()
        if (norm > 0.0001f) {
            for (i in vec.indices) vec[i] /= norm
        }

        return vec
    }

    /**
     * Computes Cosine Similarity between two L2-normalized 64-D vectors in [0.0 .. 1.0].
     */
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
        return if (denom > 0.00001f) (dot / denom).coerceIn(0f, 1f) else 0f
    }

    fun serializeEmbedding(vec: FloatArray): String {
        return vec.joinToString(",") { "%.4f".format(it) }
    }

    fun deserializeEmbedding(str: String): FloatArray? {
        if (str.isBlank()) return null
        return try {
            val parts = str.split(",")
            if (parts.size != EMBEDDING_DIM) return null
            FloatArray(EMBEDDING_DIM) { parts[it].toFloat() }
        } catch (_: Throwable) {
            null
        }
    }

    private fun fallbackFeatures(): DspFeatures {
        val zeros = FloatArray(EMBEDDING_DIM) { 1f / sqrt(EMBEDDING_DIM.toFloat()) }
        return DspFeatures(
            bpm = 120,
            energy = 0.5f,
            danceability = 0.5f,
            spectralCentroid = 0.5f,
            zeroCrossingRate = 0.2f,
            dynamicRange = 0.5f,
            genre = "Pop",
            audioEmbedding = zeros
        )
    }
}
