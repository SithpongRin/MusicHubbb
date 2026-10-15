package com.musichub.app.domain.lyrics

import kotlin.math.max
import kotlin.math.min

/**
 * High-performance on-device multilingual sentiment & valence-arousal analyzer.
 * Processes English and Khmer lyrics with zero model bloat.
 */
object LyricsSentimentEngine {

    data class LyricsAnalysis(
        val valence: Float,       // [-1.0f .. 1.0f]
        val arousal: Float,       // [-1.0f .. 1.0f]
        val confidence: Float,    // [0.0f .. 1.0f]
        val language: String,     // "km", "en", "instrumental", "unknown"
        val isInstrumental: Boolean
    )

    // Valence (pleasantness) and Arousal (energy) word mappings [-1.0 .. 1.0]
    private val englishLexicon = mapOf(
        // High Valence, High Arousal (Happy / Energetic)
        "love" to Pair(0.85f, 0.60f), "happy" to Pair(0.90f, 0.70f), "joy" to Pair(0.85f, 0.65f),
        "dance" to Pair(0.70f, 0.85f), "party" to Pair(0.75f, 0.90f), "celebrate" to Pair(0.80f, 0.80f),
        "energy" to Pair(0.60f, 0.90f), "fire" to Pair(0.50f, 0.85f), "power" to Pair(0.60f, 0.80f),
        "shine" to Pair(0.75f, 0.65f), "beautiful" to Pair(0.80f, 0.40f), "smile" to Pair(0.85f, 0.50f),
        "freedom" to Pair(0.80f, 0.70f), "alive" to Pair(0.75f, 0.75f), "fly" to Pair(0.70f, 0.70f),

        // High Valence, Low Arousal (Calm / Romantic / Relaxing)
        "peace" to Pair(0.75f, -0.70f), "calm" to Pair(0.70f, -0.80f), "quiet" to Pair(0.50f, -0.75f),
        "soft" to Pair(0.60f, -0.65f), "sleep" to Pair(0.50f, -0.85f), "dream" to Pair(0.65f, -0.40f),
        "gentle" to Pair(0.70f, -0.60f), "breeze" to Pair(0.60f, -0.50f), "relax" to Pair(0.75f, -0.75f),
        "sweet" to Pair(0.75f, -0.30f), "warm" to Pair(0.70f, -0.35f), "whisper" to Pair(0.55f, -0.60f),

        // Low Valence, Low Arousal (Sad / Melancholic)
        "sad" to Pair(-0.85f, -0.50f), "cry" to Pair(-0.80f, -0.30f), "tears" to Pair(-0.75f, -0.40f),
        "pain" to Pair(-0.85f, 0.20f), "alone" to Pair(-0.70f, -0.60f), "lonely" to Pair(-0.80f, -0.65f),
        "broken" to Pair(-0.85f, -0.20f), "sorrow" to Pair(-0.80f, -0.55f), "grief" to Pair(-0.90f, -0.60f),
        "lost" to Pair(-0.65f, -0.40f), "dark" to Pair(-0.60f, -0.30f), "goodbye" to Pair(-0.70f, -0.40f),
        "miss" to Pair(-0.55f, -0.35f), "hurt" to Pair(-0.80f, 0.10f), "bleeding" to Pair(-0.85f, 0.30f),

        // Low Valence, High Arousal (Anger / Frustration / Aggressive)
        "hate" to Pair(-0.85f, 0.85f), "angry" to Pair(-0.80f, 0.80f), "rage" to Pair(-0.90f, 0.95f),
        "fight" to Pair(-0.60f, 0.90f), "war" to Pair(-0.80f, 0.90f), "kill" to Pair(-0.95f, 0.90f),
        "scream" to Pair(-0.70f, 0.90f), "break" to Pair(-0.60f, 0.70f), "danger" to Pair(-0.65f, 0.85f)
    )

    private val khmerLexicon = mapOf(
        // High Valence, High Arousal (សប្បាយ / ញាក់ / លើកទឹកចិត្ត)
        "សប្បាយ" to Pair(0.85f, 0.70f), "រីករាយ" to Pair(0.90f, 0.65f), "ញញឹម" to Pair(0.80f, 0.50f),
        "សើច" to Pair(0.80f, 0.70f), "រាំ" to Pair(0.70f, 0.85f), "កន្ត្រាក់" to Pair(0.60f, 0.90f),
        "ជោគជ័យ" to Pair(0.85f, 0.75f), "ស្រស់ស្រាយ" to Pair(0.80f, 0.60f), "ពន្លឺ" to Pair(0.75f, 0.50f),
        "រំភើប" to Pair(0.85f, 0.80f), "អស្ចារ្យ" to Pair(0.85f, 0.75f), "ជ័យជម្នះ" to Pair(0.80f, 0.80f),

        // High Valence, Low Arousal (ស្នេហា / ស្រទន់ / ស្ងប់ស្ងាត់)
        "ស្រឡាញ់" to Pair(0.85f, 0.40f), "ស្នេហា" to Pair(0.85f, 0.35f), "ថ្នាក់ថ្នម" to Pair(0.80f, -0.50f),
        "កក់ក្តៅ" to Pair(0.75f, -0.40f), "ស្ងប់" to Pair(0.75f, -0.80f), "ស្ងាត់" to Pair(0.50f, -0.70f),
        "សន្តិភាព" to Pair(0.80f, -0.70f), "ផ្អែមល្ហែម" to Pair(0.80f, -0.30f), "ត្រជាក់" to Pair(0.60f, -0.50f),
        "ដេក" to Pair(0.50f, -0.85f), "សុបិន" to Pair(0.65f, -0.40f), "ក្តីសុខ" to Pair(0.85f, -0.30f),

        // Low Valence, Low Arousal (កម្សត់ / ឈឺចាប់ / ឯកា)
        "យំ" to Pair(-0.85f, -0.30f), "ទឹកភ្នែក" to Pair(-0.85f, -0.40f), "ឈឺចាប់" to Pair(-0.90f, 0.10f),
        "កម្សត់" to Pair(-0.85f, -0.50f), "ឯកា" to Pair(-0.80f, -0.65f), "ព្រាត់" to Pair(-0.85f, -0.45f),
        "ព្រាត់ប្រាស" to Pair(-0.90f, -0.50f), "បែកបាក់" to Pair(-0.85f, -0.20f), "ក្បត់" to Pair(-0.90f, 0.40f),
        "នឹក" to Pair(-0.45f, -0.30f), "សោកសៅ" to Pair(-0.85f, -0.60f), "អស់សង្ឃឹម" to Pair(-0.95f, -0.70f),
        "ទុក្ខ" to Pair(-0.85f, -0.50f), "ខ្លោចផ្សា" to Pair(-0.90f, -0.30f), "ស្ដាយ" to Pair(-0.65f, -0.40f),

        // Low Valence, High Arousal (ខឹង / ស្អប់ / ច្រណែន)
        "ខឹង" to Pair(-0.80f, 0.85f), "ស្អប់" to Pair(-0.85f, 0.85f), "គំនុំ" to Pair(-0.90f, 0.80f),
        "ឈ្លោះ" to Pair(-0.75f, 0.85f), "បំផ្លាញ" to Pair(-0.85f, 0.90f)
    )

    fun analyze(lyricsText: String): LyricsAnalysis {
        val trimmed = lyricsText.trim()
        if (trimmed.isBlank() || isExplicitInstrumental(trimmed)) {
            return LyricsAnalysis(
                valence = 0f,
                arousal = 0f,
                confidence = 0f,
                language = "instrumental",
                isInstrumental = true
            )
        }

        val isKhmer = KhmerTextSegmenter.isKhmerText(trimmed)
        val language = if (isKhmer) "km" else "en"

        val tokens = if (isKhmer) {
            KhmerTextSegmenter.segment(trimmed)
        } else {
            trimmed.lowercase()
                .split("[^a-zA-Z']+".toRegex())
                .filter { it.length >= 2 }
        }

        if (tokens.size < 3) {
            return LyricsAnalysis(
                valence = 0f,
                arousal = 0f,
                confidence = 0f,
                language = language,
                isInstrumental = false
            )
        }

        val lexicon = if (isKhmer) khmerLexicon else englishLexicon

        var totalValence = 0f
        var totalArousal = 0f
        var matchCount = 0

        for (token in tokens) {
            val score = lexicon[token] ?: continue
            totalValence += score.first
            totalArousal += score.second
            matchCount++
        }

        if (matchCount == 0) {
            return LyricsAnalysis(
                valence = 0f,
                arousal = 0f,
                confidence = 0.05f,
                language = language,
                isInstrumental = false
            )
        }

        val avgValence = (totalValence / matchCount).coerceIn(-1.0f, 1.0f)
        val avgArousal = (totalArousal / matchCount).coerceIn(-1.0f, 1.0f)

        // Confidence scales with number of matched emotional lemmas (maxes at 12 words)
        val confidence = min(1.0f, (matchCount.toFloat() / 12.0f)).coerceAtLeast(0.20f)

        return LyricsAnalysis(
            valence = avgValence,
            arousal = avgArousal,
            confidence = confidence,
            language = language,
            isInstrumental = false
        )
    }

    private fun isExplicitInstrumental(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("[instrumental]") ||
                lower.contains("instrumental") && text.length < 50 ||
                lower.contains("[solo]") && text.length < 50 ||
                lower.contains("ភ្លេងសុទ្ធ")
    }
}
