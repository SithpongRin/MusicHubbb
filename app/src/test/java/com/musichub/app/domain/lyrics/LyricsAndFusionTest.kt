package com.musichub.app.domain.lyrics

import com.musichub.app.data.database.LyricsEntity
import com.musichub.app.data.database.MoodPredictionEntity
import com.musichub.app.domain.mood.HybridMoodFusionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsAndFusionTest {

    @Test
    fun testKhmerTextSegmentation() {
        val khmerSentence = "ខ្ញុំស្រឡាញ់តន្ត្រីនិងស្នេហា"
        assertTrue(KhmerTextSegmenter.isKhmerText(khmerSentence))

        val words = KhmerTextSegmenter.segment(khmerSentence)
        assertTrue("Words list should not be empty", words.isNotEmpty())
        assertTrue("Should identify Khmer emotional lemma", words.any { it.contains("ស្រឡាញ់") || it.contains("ស្នេហា") })
    }

    @Test
    fun testEnglishLyricsSentimentAnalysis() {
        val happyLyrics = "I love this happy day, dancing in the party with joy and smile"
        val analysis = LyricsSentimentEngine.analyze(happyLyrics)

        assertEquals("en", analysis.language)
        assertFalse(analysis.isInstrumental)
        assertTrue("Happy lyrics should have positive valence", analysis.valence > 0.4f)
        assertTrue("Dance lyrics should have positive arousal", analysis.arousal > 0.3f)
        assertTrue("Confidence should be high for multiple lemmas", analysis.confidence > 0.4f)
    }

    @Test
    fun testKhmerLyricsSentimentAnalysis() {
        val sadKhmerLyrics = "ខ្ញុំយំម្នាក់ឯងឈឺចាប់ខ្លោចផ្សាទឹកភ្នែកហូរព្រាត់ប្រាសស្នេហា"
        val analysis = LyricsSentimentEngine.analyze(sadKhmerLyrics)

        assertEquals("km", analysis.language)
        assertFalse(analysis.isInstrumental)
        assertTrue("Sad Khmer lyrics should have negative valence", analysis.valence < -0.3f)
    }

    @Test
    fun testInstrumentalDetection() {
        val instrumentalText = "[Instrumental Solo - Guitar]"
        val analysis = LyricsSentimentEngine.analyze(instrumentalText)

        assertTrue("Should detect instrumental track", analysis.isInstrumental)
        assertEquals("instrumental", analysis.language)
    }

    @Test
    fun testHybridMoodFusionWhenLyricsMissing() {
        val audioPred = MoodPredictionEntity(
            songId = "song-1",
            modelVersion = "v1",
            valence = 0.85f,
            arousal = 0.90f,
            calm = 0.1f,
            energetic = 0.9f,
            sad = 0.05f,
            romantic = 0.2f,
            confidence = 0.85f,
            analyzedAt = 1000L
        )

        val fused = HybridMoodFusionEngine.fuse(audioPred, null)

        assertEquals("HAPPY", fused.primaryMood)
        assertFalse(fused.isDissonant)
        assertEquals(0f, fused.dissonanceScore, 0.001f)
    }

    @Test
    fun testHybridMoodFusionDissonanceDetection() {
        // High valence, high arousal audio (Upbeat EDM)
        val audioPred = MoodPredictionEntity(
            songId = "song-2",
            modelVersion = "v1",
            valence = 0.90f, // va = +0.80
            arousal = 0.90f, // aa = +0.80
            calm = 0.05f,
            energetic = 0.95f,
            sad = 0.05f,
            romantic = 0.1f,
            confidence = 0.90f,
            analyzedAt = 1000L
        )

        // Melancholic tragic lyrics
        val sadLyrics = LyricsEntity(
            songId = "song-2",
            plainLyrics = "tears and crying in pain alone",
            syncedLyrics = null,
            source = "LRCLIB",
            language = "en",
            isInstrumental = false,
            lyricsValence = -0.80f,
            lyricsArousal = -0.40f,
            lyricsConfidence = 0.80f,
            dissonanceScore = null,
            updatedAt = 1000L
        )

        val fused = HybridMoodFusionEngine.fuse(audioPred, sadLyrics)

        assertTrue("Dissonance score should be significant", fused.dissonanceScore > 0.40f)
        assertTrue("Should be marked as emotionally dissonant", fused.isDissonant)
    }
}
