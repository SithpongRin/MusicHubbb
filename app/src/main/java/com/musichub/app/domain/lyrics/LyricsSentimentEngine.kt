package com.musichub.app.domain.lyrics

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * High-performance on-device multilingual sentiment & valence-arousal analyzer.
 * Processes English and Khmer lyrics with zero model bloat, extracting emotional valence,
 * arousal, semantic themes, and compact 32-dimensional text embeddings.
 */
object LyricsSentimentEngine {

    const val LYRIC_EMBEDDING_DIM = 32

    data class LyricsAnalysis(
        val valence: Float,             // [-1.0f .. 1.0f]
        val arousal: Float,             // [-1.0f .. 1.0f]
        val confidence: Float,          // [0.0f .. 1.0f]
        val language: String,           // "km", "en", "instrumental", "unknown"
        val isInstrumental: Boolean,
        val themes: List<String> = emptyList(), // "ROMANCE", "HEARTBREAK", "CELEBRATION", "PEACE", "ANGER", "MOTIVATION"
        val lyricEmbedding: FloatArray? = null  // 32-D L2 normalized embedding
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is LyricsAnalysis) return false
            return valence == other.valence &&
                    arousal == other.arousal &&
                    confidence == other.confidence &&
                    language == other.language &&
                    isInstrumental == other.isInstrumental &&
                    themes == other.themes &&
                    ((lyricEmbedding == null && other.lyricEmbedding == null) ||
                            (lyricEmbedding != null && other.lyricEmbedding != null && lyricEmbedding.contentEquals(other.lyricEmbedding)))
        }

        override fun hashCode(): Int {
            var result = valence.hashCode()
            result = 31 * result + arousal.hashCode()
            result = 31 * result + confidence.hashCode()
            result = 31 * result + language.hashCode()
            result = 31 * result + isInstrumental.hashCode()
            result = 31 * result + themes.hashCode()
            result = 31 * result + (lyricEmbedding?.contentHashCode() ?: 0)
            return result
        }
    }

    // Valence (pleasantness) and Arousal (energy) word mappings [-1.0 .. 1.0]
    private val englishLexicon = mapOf(
        // High Valence, High Arousal (Happy / Energetic)
        "love" to Pair(0.85f, 0.60f), "happy" to Pair(0.90f, 0.70f), "joy" to Pair(0.85f, 0.65f),
        "dance" to Pair(0.70f, 0.85f), "party" to Pair(0.75f, 0.90f), "celebrate" to Pair(0.80f, 0.80f),
        "energy" to Pair(0.60f, 0.90f), "fire" to Pair(0.50f, 0.85f), "power" to Pair(0.60f, 0.80f),
        "shine" to Pair(0.75f, 0.65f), "beautiful" to Pair(0.80f, 0.40f), "smile" to Pair(0.85f, 0.50f),
        "freedom" to Pair(0.80f, 0.70f), "alive" to Pair(0.75f, 0.75f), "fly" to Pair(0.70f, 0.70f),
        "kiss" to Pair(0.80f, 0.50f), "heart" to Pair(0.70f, 0.40f), "darling" to Pair(0.80f, 0.35f),

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
        "សង្សារ" to Pair(0.75f, 0.30f), "បេះដូង" to Pair(0.70f, 0.20f),

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

    private val themeDictionary = mapOf(
        "ROMANCE" to setOf("love", "heart", "darling", "baby", "kiss", "sweet", "hug", "forever", "romantic", "ស្រឡាញ់", "ស្នេហា", "សង្សារ", "ថ្នាក់ថ្នម", "ផ្អែមល្ហែម", "កក់ក្តៅ", "បេះដូង"),
        "HEARTBREAK" to setOf("sad", "cry", "tears", "pain", "broken", "alone", "lonely", "goodbye", "hurt", "grief", "bleeding", "miss", "sorrow", "យំ", "ទឹកភ្នែក", "ឈឺចាប់", "ព្រាត់", "ព្រាត់ប្រាស", "បែកបាក់", "កម្សត់", "ឯកា", "សោកសៅ", "ខ្លោចផ្សា", "ក្បត់", "ស្ដាយ"),
        "CELEBRATION" to setOf("dance", "party", "celebrate", "happy", "joy", "smile", "cheer", "jump", "fun", "សប្បាយ", "រីករាយ", "រាំ", "កន្ត្រាក់", "សើច", "ញញឹម", "រំភើប"),
        "PEACE" to setOf("peace", "calm", "quiet", "soft", "sleep", "dream", "relax", "breeze", "whisper", "ស្ងប់", "ស្ងាត់", "សន្តិភាព", "ក្តីសុខ", "ត្រជាក់", "ដេក", "សុបិន"),
        "ANGER" to setOf("hate", "angry", "rage", "fight", "war", "kill", "scream", "danger", "break", "ខឹង", "ស្អប់", "គំនុំ", "ឈ្លោះ", "បំផ្លាញ"),
        "MOTIVATION" to setOf("fire", "power", "alive", "freedom", "fly", "shine", "energy", "champion", "strong", "win", "ជោគជ័យ", "ជ័យជម្នះ", "ពន្លឺ", "អស្ចារ្យ")
    )

    fun analyze(lyricsText: String): LyricsAnalysis {
        val trimmed = lyricsText.trim()
        if (trimmed.isBlank() || isExplicitInstrumental(trimmed)) {
            val emptyEmbedding = FloatArray(LYRIC_EMBEDDING_DIM)
            return LyricsAnalysis(
                valence = 0f,
                arousal = 0f,
                confidence = 0f,
                language = "instrumental",
                isInstrumental = true,
                themes = emptyList(),
                lyricEmbedding = emptyEmbedding
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
            val emptyEmbedding = FloatArray(LYRIC_EMBEDDING_DIM)
            return LyricsAnalysis(
                valence = 0f,
                arousal = 0f,
                confidence = 0f,
                language = language,
                isInstrumental = false,
                themes = emptyList(),
                lyricEmbedding = emptyEmbedding
            )
        }

        val lexicon = if (isKhmer) khmerLexicon else englishLexicon

        var totalValence = 0f
        var totalArousal = 0f
        var matchCount = 0
        var positiveCount = 0
        var negativeCount = 0

        // Theme counters
        val themeCounts = mutableMapOf<String, Int>()
        for (theme in themeDictionary.keys) {
            themeCounts[theme] = 0
        }

        for (token in tokens) {
            val score = lexicon[token]
            if (score != null) {
                totalValence += score.first
                totalArousal += score.second
                matchCount++
                if (score.first > 0.1f) positiveCount++
                if (score.first < -0.1f) negativeCount++
            }

            for ((theme, keywords) in themeDictionary) {
                if (keywords.contains(token)) {
                    themeCounts[theme] = (themeCounts[theme] ?: 0) + 1
                }
            }
        }

        val matchedThemes = themeCounts.filter { it.value > 0 }
            .toList()
            .sortedByDescending { it.second }
            .take(3)
            .map { it.first }

        if (matchCount == 0) {
            val emptyEmbedding = FloatArray(LYRIC_EMBEDDING_DIM)
            return LyricsAnalysis(
                valence = 0f,
                arousal = 0f,
                confidence = 0.05f,
                language = language,
                isInstrumental = false,
                themes = matchedThemes,
                lyricEmbedding = emptyEmbedding
            )
        }

        val avgValence = (totalValence / matchCount).coerceIn(-1.0f, 1.0f)
        val avgArousal = (totalArousal / matchCount).coerceIn(-1.0f, 1.0f)

        // Confidence scales with number of matched emotional lemmas (maxes at 12 words)
        val confidence = min(1.0f, (matchCount.toFloat() / 12.0f)).coerceAtLeast(0.20f)

        // Generate 32-D Lyrics Semantic Embedding
        val embedding = buildLyricEmbedding(
            valence = avgValence,
            arousal = avgArousal,
            confidence = confidence,
            positiveRatio = positiveCount.toFloat() / matchCount.coerceAtLeast(1),
            negativeRatio = negativeCount.toFloat() / matchCount.coerceAtLeast(1),
            themeCounts = themeCounts,
            totalTokens = tokens.size,
            isKhmer = isKhmer
        )

        return LyricsAnalysis(
            valence = avgValence,
            arousal = avgArousal,
            confidence = confidence,
            language = language,
            isInstrumental = false,
            themes = matchedThemes,
            lyricEmbedding = embedding
        )
    }

    private fun buildLyricEmbedding(
        valence: Float,
        arousal: Float,
        confidence: Float,
        positiveRatio: Float,
        negativeRatio: Float,
        themeCounts: Map<String, Int>,
        totalTokens: Int,
        isKhmer: Boolean
    ): FloatArray {
        val vec = FloatArray(LYRIC_EMBEDDING_DIM)

        // Dims 0..5: Normalized theme presence
        val themeOrder = listOf("ROMANCE", "HEARTBREAK", "CELEBRATION", "PEACE", "ANGER", "MOTIVATION")
        val maxThemeHits = themeCounts.values.maxOrNull()?.coerceAtLeast(1) ?: 1
        for (i in themeOrder.indices) {
            vec[i] = ((themeCounts[themeOrder[i]] ?: 0).toFloat() / maxThemeHits).coerceIn(0f, 1f)
        }

        // Dims 6..11: Affect coordinates & sentiment polarity
        vec[6] = (valence + 1f) * 0.5f // [0..1]
        vec[7] = (arousal + 1f) * 0.5f // [0..1]
        vec[8] = confidence
        vec[9] = positiveRatio.coerceIn(0f, 1f)
        vec[10] = negativeRatio.coerceIn(0f, 1f)
        vec[11] = abs(positiveRatio - negativeRatio).coerceIn(0f, 1f) // emotional bias

        // Dims 12..21: Lexical and structural characteristics
        val normTokens = (totalTokens.toFloat() / 250f).coerceIn(0f, 1f)
        for (i in 0..9) {
            vec[12 + i] = (normTokens * ((i + 1) * 0.1f)).coerceIn(0f, 1f)
        }

        // Dims 22..31: Language and distribution projections
        vec[22] = if (isKhmer) 1.0f else 0.0f
        vec[23] = if (!isKhmer) 1.0f else 0.0f
        for (i in 24 until LYRIC_EMBEDDING_DIM) {
            vec[i] = (vec[i - 24] * 0.65f + vec[6] * 0.35f).coerceIn(0f, 1f)
        }

        // L2 Normalize
        var normSum = 0.0
        for (v in vec) normSum += (v * v).toDouble()
        val norm = sqrt(normSum).toFloat()
        if (norm > 0.0001f) {
            for (i in vec.indices) vec[i] /= norm
        }

        return vec
    }

    /**
     * Computes Cosine Similarity between two 32-D lyrics embeddings in [0.0 .. 1.0].
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
            if (parts.size != LYRIC_EMBEDDING_DIM) return null
            FloatArray(LYRIC_EMBEDDING_DIM) { parts[it].toFloat() }
        } catch (_: Throwable) {
            null
        }
    }

    private fun isExplicitInstrumental(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("[instrumental]") ||
                lower.contains("instrumental") && text.length < 50 ||
                lower.contains("[solo]") && text.length < 50 ||
                lower.contains("ភ្លេងសុទ្ធ")
    }
}
