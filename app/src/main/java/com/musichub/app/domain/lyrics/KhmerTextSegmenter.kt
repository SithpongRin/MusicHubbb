package com.musichub.app.domain.lyrics

import android.icu.text.BreakIterator
import android.icu.util.ULocale

/**
 * On-device Khmer word segmenter leveraging Android's built-in ICU BreakIterator.
 * Requires 0 MB external models or C++ libraries.
 */
object KhmerTextSegmenter {

    private val khmerDictionary = listOf(
        // Sentiment & emotional lemmas
        "ព្រាត់ប្រាស", "អស់សង្ឃឹម", "ស្រស់ស្រាយ", "ជ័យជម្នះ", "ផ្អែមល្ហែម", "ថ្នាក់ថ្នម", "កក់ក្តៅ", "ឈឺចាប់", "ខ្លោចផ្សា",
        "បែកបាក់", "សន្តិភាព", "ទឹកភ្នែក", "កន្ត្រាក់", "ជោគជ័យ", "ស្រឡាញ់", "រីករាយ", "សប្បាយ", "អស្ចារ្យ", "ម្នាក់ឯង",
        "សោកសៅ", "បំផ្លាញ", "ក្តីសុខ", "ត្រជាក់", "ញញឹម", "រំភើប", "ស្នេហា", "កម្សត់", "ស្ងប់", "ស្ងាត់", "សុបិន", "ព្រាត់",
        "ក្បត់", "ឈ្លោះ", "គំនុំ", "ទុក្ខ", "ស្ដាយ", "សើច", "ពន្លឺ", "ឯកា", "ខឹង", "ស្អប់", "ដេក", "នឹក", "រាំ", "យំ",
        // Common grammatical particles / pronouns / verbs / nouns
        "ខ្ញុំ", "យើង", "អ្នក", "គេ", "គាត់", "មិន", "ឥត", "និង", "ជា", "នៅ", "មាន", "បាន", "ហូរ", "ទៅ", "មក", "ដែល", "នេះ", "នោះ", "មួយ", "តន្ត្រី"
    ).sortedByDescending { it.length }

    fun isKhmerText(text: String): Boolean {
        var khmerCount = 0
        for (ch in text) {
            if (ch in '\u1780'..'\u17FF') {
                khmerCount++
                if (khmerCount >= 5) return true
            }
        }
        return false
    }

    fun segment(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        val words = mutableListOf<String>()
        try {
            val iterator = BreakIterator.getWordInstance(ULocale("km"))
            iterator.setText(text)

            var start = iterator.first()
            var end = iterator.next()

            while (end != BreakIterator.DONE) {
                val word = text.substring(start, end).trim()
                // Keep only meaningful tokens (exclude whitespace and pure punctuation)
                if (word.isNotBlank() && word.any { it.isLetterOrDigit() || it in '\u1780'..'\u17FF' }) {
                    words.add(word)
                }
                start = end
                end = iterator.next()
            }
        } catch (_: Throwable) {
            // Fallback for JVM unit test environment where android.icu is stubbed
        }

        // If ICU returned a single combined token or failed, use dictionary-based greedy segmenter
        if (words.size <= 1 && text.any { it in '\u1780'..'\u17FF' }) {
            return dictionarySegment(text)
        }

        return words
    }

    private fun dictionarySegment(text: String): List<String> {
        val result = mutableListOf<String>()
        var i = 0
        val len = text.length

        while (i < len) {
            if (text[i].isWhitespace()) {
                i++
                continue
            }

            var matched = false
            for (word in khmerDictionary) {
                if (text.startsWith(word, i)) {
                    result.add(word)
                    i += word.length
                    matched = true
                    break
                }
            }

            if (!matched) {
                val start = i
                i++
                while (i < len && text[i] in '\u17B6'..'\u17D3') {
                    i++
                }
                val token = text.substring(start, i).trim()
                if (token.isNotBlank()) {
                    result.add(token)
                }
            }
        }

        return result
    }
}
