package com.musichub.app.domain.mood

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnDeviceMusicMoodClassifierTest {
    @Test
    fun trainedValenceArousalOutputProducesFourNormalizedMoodScores() {
        val output = OnDeviceMusicMoodClassifier.fromModelOutput(.74f, .28f)

        assertEquals(4, output.moodScores.size)
        assertEquals(1f, output.moodScores.sum(), .0001f)
        assertTrue(output.confidence in 0f..1f)
    }

    @Test
    fun highValenceHighArousalFavorsEnergetic() {
        val output = OnDeviceMusicMoodClassifier.fromModelOutput(.85f, .85f)
        assertEquals(1, output.moodScores.indices.maxBy { output.moodScores[it] })
    }

    @Test
    fun lowValenceLowArousalFavorsSad() {
        val output = OnDeviceMusicMoodClassifier.fromModelOutput(.15f, .25f)
        assertEquals(2, output.moodScores.indices.maxBy { output.moodScores[it] })
    }
}
