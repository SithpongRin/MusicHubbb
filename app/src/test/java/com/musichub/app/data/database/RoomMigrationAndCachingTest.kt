package com.musichub.app.data.database

import com.musichub.app.domain.audio.AudioDspFeatureExtractor
import com.musichub.app.domain.lyrics.LyricsSentimentEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomMigrationAndCachingTest {

    @Test
    fun testMoodPredictionEntityWithEnrichedDspAndEmbeddingFields() {
        val dummyEmbedding = FloatArray(64) { 0.125f }
        val serializedEmbedding = AudioDspFeatureExtractor.serializeEmbedding(dummyEmbedding)

        val entity = MoodPredictionEntity(
            songId = "test-song-123",
            modelVersion = "mert-v1-95m-deam-pmemo-int8-2",
            valence = 0.72f,
            arousal = 0.65f,
            calm = 0.15f,
            energetic = 0.60f,
            sad = 0.05f,
            romantic = 0.20f,
            confidence = 0.85f,
            analyzedAt = 1700000000000L,
            genre = "Rock",
            bpm = 128,
            energy = 0.82f,
            danceability = 0.75f,
            audioEmbedding = serializedEmbedding,
            secondaryMood = "ENERGETIC"
        )

        assertEquals("test-song-123", entity.songId)
        assertEquals("Rock", entity.genre)
        assertEquals(128, entity.bpm)
        assertEquals(0.82f, entity.energy, 0.001f)
        assertEquals(0.75f, entity.danceability, 0.001f)
        assertEquals("ENERGETIC", entity.secondaryMood)

        val deserialized = AudioDspFeatureExtractor.deserializeEmbedding(entity.audioEmbedding!!)
        assertNotNull(deserialized)
        assertEquals(64, deserialized!!.size)
        assertEquals(0.125f, deserialized[0], 0.01f)
    }

    @Test
    fun testLyricsEntityWithThemesAndLyricEmbeddingFields() {
        val dummyLyricEmbedding = FloatArray(32) { 0.176f }
        val serialized = LyricsSentimentEngine.serializeEmbedding(dummyLyricEmbedding)

        val entity = LyricsEntity(
            songId = "test-song-lyrics",
            plainLyrics = "Love is in the air",
            syncedLyrics = "[00:10.00] Love is in the air",
            source = "LOCAL_LRC",
            language = "en",
            isInstrumental = false,
            lyricsValence = 0.80f,
            lyricsArousal = 0.50f,
            lyricsConfidence = 0.90f,
            dissonanceScore = 0.10f,
            updatedAt = 1700000000000L,
            themes = "ROMANCE,CELEBRATION",
            lyricEmbedding = serialized
        )

        assertEquals("LOCAL_LRC", entity.source)
        assertEquals("ROMANCE,CELEBRATION", entity.themes)

        val deserialized = LyricsSentimentEngine.deserializeEmbedding(entity.lyricEmbedding!!)
        assertNotNull(deserialized)
        assertEquals(32, deserialized!!.size)
        assertEquals(0.176f, deserialized[0], 0.01f)
    }

    @Test
    fun testDefaultValuesPreserveBackwardsCompatibilityForUnmigratedRows() {
        val legacyEntity = MoodPredictionEntity(
            songId = "legacy-song",
            modelVersion = "v1",
            valence = 0.5f,
            arousal = 0.5f,
            calm = 0.25f,
            energetic = 0.25f,
            sad = 0.25f,
            romantic = 0.25f,
            confidence = 0.5f,
            analyzedAt = 1000L
        )

        // When reading rows inserted in v2/v3 without new columns, defaults apply safely
        assertEquals("Unknown", legacyEntity.genre)
        assertEquals(0, legacyEntity.bpm)
        assertEquals(0.5f, legacyEntity.energy, 0.001f)
        assertEquals(0.5f, legacyEntity.danceability, 0.001f)
        assertEquals(null, legacyEntity.audioEmbedding)
        assertEquals(null, legacyEntity.secondaryMood)

        val legacyLyrics = LyricsEntity(
            songId = "legacy-lyrics",
            plainLyrics = null,
            syncedLyrics = null,
            source = "NOT_FOUND",
            language = "unknown"
        )
        assertEquals(null, legacyLyrics.themes)
        assertEquals(null, legacyLyrics.lyricEmbedding)
    }
}
