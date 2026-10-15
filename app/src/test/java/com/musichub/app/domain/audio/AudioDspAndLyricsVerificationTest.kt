package com.musichub.app.domain.audio

import com.musichub.app.data.database.LyricsEntity
import com.musichub.app.data.database.MoodPredictionEntity
import com.musichub.app.domain.lyrics.KhmerTextSegmenter
import com.musichub.app.domain.lyrics.LocalLyricsScanner
import com.musichub.app.domain.lyrics.LyricsSentimentEngine
import com.musichub.app.domain.mood.HybridMoodFusionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class AudioDspAndLyricsVerificationTest {

    // =========================================================================
    // 1. Audio DSP Feature Extractor Verification
    // =========================================================================

    @Test
    fun testAudioDspFeatureExtractionOnSynthetic120BpmTrack() {
        val sampleRate = 24000
        val durationSeconds = 15
        val totalSamples = sampleRate * durationSeconds
        val waveform = FloatArray(totalSamples)

        // Generate synthetic rhythmic pulses at 120 BPM (2 beats per second = pulse every 12000 samples)
        val samplesPerBeat = sampleRate / 2
        for (i in 0 until totalSamples) {
            val beatPhase = (i % samplesPerBeat).toFloat() / samplesPerBeat
            val envelope = if (beatPhase < 0.25f) 1.0f - (beatPhase / 0.25f) else 0.05f
            val carrier = sin(2.0 * Math.PI * 220.0 * i / sampleRate).toFloat()
            waveform[i] = envelope * carrier * 0.75f
        }

        val features = AudioDspFeatureExtractor.extractFeatures(
            waveform = waveform,
            sampleRate = sampleRate,
            valence = 0.70f,
            arousal = 0.80f
        )

        // Verify BPM detected in musical vicinity of 120 BPM
        assertTrue("Estimated BPM should be in rhythmic tempo range [110..130]", features.bpm in 110..130)
        assertTrue("Energy should be positive", features.energy > 0.15f)
        assertTrue("Danceability should reflect rhythmic pulse", features.danceability > 0.35f)
        assertEquals(AudioDspFeatureExtractor.EMBEDDING_DIM, features.audioEmbedding.size)

        // Verify L2 normalization of 64-D embedding
        var normSum = 0.0
        for (v in features.audioEmbedding) normSum += (v * v).toDouble()
        val norm = sqrt(normSum).toFloat()
        assertEquals("Audio embedding must be L2 normalized to 1.0", 1.0f, norm, 0.01f)
    }

    @Test
    fun testAudioDspGenreClassification() {
        val sampleRate = 24000
        val nSamples = sampleRate * 5

        // Quiet acoustic gentle tone
        val acousticWave = FloatArray(nSamples) { i ->
            (sin(2.0 * Math.PI * 440.0 * i / sampleRate) * 0.08f).toFloat()
        }
        val acousticFeatures = AudioDspFeatureExtractor.extractFeatures(
            waveform = acousticWave,
            sampleRate = sampleRate,
            valence = 0.30f,
            arousal = 0.15f
        )
        assertEquals("Acoustic", acousticFeatures.genre)

        // High energy percussive noisy waveform
        val rockWave = FloatArray(nSamples) { i ->
            val noise = ((i * 1103515245 + 12345) and 0x7FFFFFFF) / 2147483648f - 0.5f
            noise * 0.90f
        }
        val rockFeatures = AudioDspFeatureExtractor.extractFeatures(
            waveform = rockWave,
            sampleRate = sampleRate,
            valence = 0.50f,
            arousal = 0.90f
        )
        assertEquals("Rock", rockFeatures.genre)
    }

    @Test
    fun testAudioEmbeddingSerializationAndCosineSimilarity() {
        val sampleRate = 24000
        val waveA = FloatArray(sampleRate * 2) { i -> (sin(i.toDouble() * 0.05) * 0.5).toFloat() }
        val waveB = FloatArray(sampleRate * 2) { i -> (sin(i.toDouble() * 0.05) * 0.5).toFloat() }

        val featA = AudioDspFeatureExtractor.extractFeatures(waveA, sampleRate, 0.6f, 0.6f)
        val featB = AudioDspFeatureExtractor.extractFeatures(waveB, sampleRate, 0.6f, 0.6f)

        val simSame = AudioDspFeatureExtractor.cosineSimilarity(featA.audioEmbedding, featB.audioEmbedding)
        assertTrue("Identical waveforms should yield cosine similarity close to 1.0", simSame > 0.98f)

        val serialized = AudioDspFeatureExtractor.serializeEmbedding(featA.audioEmbedding)
        val deserialized = AudioDspFeatureExtractor.deserializeEmbedding(serialized)
        assertNotNull(deserialized)
        assertEquals(64, deserialized!!.size)
        val simRoundTrip = AudioDspFeatureExtractor.cosineSimilarity(featA.audioEmbedding, deserialized)
        assertEquals(1.0f, simRoundTrip, 0.001f)
    }

    // =========================================================================
    // 2. Lyrics Understanding & Semantic Themes (English & Khmer)
    // =========================================================================

    @Test
    fun testEnglishLyricsThemesAndEmbedding() {
        val heartbreakLyrics = "I cry alone with tears and broken heart, saying goodbye in deep sorrow and pain"
        val analysis = LyricsSentimentEngine.analyze(heartbreakLyrics)

        assertEquals("en", analysis.language)
        assertFalse(analysis.isInstrumental)
        assertTrue("Should detect negative valence", analysis.valence < -0.40f)
        assertTrue("Themes should include HEARTBREAK", analysis.themes.contains("HEARTBREAK"))
        assertNotNull(analysis.lyricEmbedding)
        assertEquals(LyricsSentimentEngine.LYRIC_EMBEDDING_DIM, analysis.lyricEmbedding!!.size)

        var normSum = 0.0
        for (v in analysis.lyricEmbedding!!) normSum += (v * v).toDouble()
        assertEquals("Lyric embedding must be L2 normalized to 1.0", 1.0f, sqrt(normSum).toFloat(), 0.01f)
    }

    @Test
    fun testKhmerLyricsThemesAndSegmentation() {
        val partyKhmerLyrics = "យប់នេះយើងនាំគ្នារាំសប្បាយកន្ត្រាក់អារម្មណ៍រីករាយសើចសប្បាយទាំងអស់គ្នា"
        val analysis = LyricsSentimentEngine.analyze(partyKhmerLyrics)

        assertEquals("km", analysis.language)
        assertFalse(analysis.isInstrumental)
        assertTrue("Should detect positive valence", analysis.valence > 0.40f)
        assertTrue("Themes should detect CELEBRATION", analysis.themes.contains("CELEBRATION"))

        val romanceKhmer = "បងស្រឡាញ់អូនខ្លាំងណាស់ផ្អែមល្ហែមថ្នាក់ថ្នមកក់ក្តៅក្នុងបេះដូង"
        val romanceAnalysis = LyricsSentimentEngine.analyze(romanceKhmer)
        assertTrue("Themes should detect ROMANCE", romanceAnalysis.themes.contains("ROMANCE"))
    }

    // =========================================================================
    // 3. Separation of Sonic Mood vs Lyrical Emotion & Missing Lyrics Fallback
    // =========================================================================

    @Test
    fun testSeparationOfSonicMoodVsLyricalEmotionDissonance() {
        // Upbeat high-energy dance track
        val sampleRate = 24000
        val danceWave = FloatArray(sampleRate * 2) { i -> (sin(i.toDouble() * 0.1) * 0.8).toFloat() }
        val dsp = AudioDspFeatureExtractor.extractFeatures(danceWave, sampleRate, 0.85f, 0.90f)

        val audioPred = MoodPredictionEntity(
            songId = "track-upbeat-sad",
            modelVersion = "test",
            valence = 0.90f, // va = +0.80
            arousal = 0.85f, // aa = +0.70
            calm = 0.05f,
            energetic = 0.90f,
            sad = 0.05f,
            romantic = 0.05f,
            confidence = 0.88f,
            analyzedAt = 1000L
        )

        val sadLyrics = LyricsEntity(
            songId = "track-upbeat-sad",
            plainLyrics = "tears falling down in pain alone crying heartbroken",
            syncedLyrics = null,
            source = "LOCAL_LRC",
            language = "en",
            isInstrumental = false,
            lyricsValence = -0.80f,
            lyricsArousal = -0.40f,
            lyricsConfidence = 0.85f,
            dissonanceScore = null,
            updatedAt = 1000L
        )

        val profile = HybridMoodFusionEngine.fuseMultiModal(
            songId = "track-upbeat-sad",
            audioFeatures = dsp,
            audioPrediction = audioPred,
            lyrics = sadLyrics
        )

        // Sonic Mood must remain ENERGETIC/HAPPY while Lyrical Emotion is HEARTBREAK
        assertTrue(profile.sonicMood in listOf("ENERGETIC", "HAPPY"))
        assertEquals("HEARTBREAK", profile.lyricalEmotion)
        assertTrue("Dissonance should be accurately detected for upbeat music with sad lyrics", profile.isDissonant)
        assertTrue("Dissonance score should exceed threshold", profile.dissonanceScore > 0.40f)
        assertTrue(profile.hasLyrics)
    }

    @Test
    fun testMissingLyricsFallbackCapsLyricDependentConfidence() {
        // Melancholic acoustic track with NO lyrics
        val sampleRate = 24000
        val slowWave = FloatArray(sampleRate * 2) { i -> (sin(i.toDouble() * 0.01) * 0.1).toFloat() }
        val dsp = AudioDspFeatureExtractor.extractFeatures(slowWave, sampleRate, 0.20f, 0.20f)

        val audioPred = MoodPredictionEntity(
            songId = "track-instrumental-sad",
            modelVersion = "test",
            valence = 0.25f, // va = -0.50 (melancholic territory)
            arousal = 0.25f, // aa = -0.50
            calm = 0.30f,
            energetic = 0.05f,
            sad = 0.65f,
            romantic = 0.10f,
            confidence = 0.85f,
            analyzedAt = 1000L
        )

        val profile = HybridMoodFusionEngine.fuseMultiModal(
            songId = "track-instrumental-sad",
            audioFeatures = dsp,
            audioPrediction = audioPred,
            lyrics = null // Missing lyrics!
        )

        assertFalse(profile.hasLyrics)
        assertNull(profile.lyricalEmotion)
        assertNull(profile.lyricalValence)
        assertEquals("SAD", profile.sonicMood)
        assertTrue("Should identify as lyric-dependent mood context", profile.isLyricDependentMood)
        assertTrue("Confidence must be capped at 0.20f to avoid guessing blindly without text", profile.confidence <= 0.20f)
        assertTrue(profile.lyricConfidenceCapped)
    }
}
