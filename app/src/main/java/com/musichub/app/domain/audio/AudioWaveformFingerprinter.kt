package com.musichub.app.domain.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Audio Waveform Fingerprint Engine for MusicHub.
 * Extracts a normalized dynamic energy envelope (PCM amplitude vector)
 * from an audio segment using Android's hardware-accelerated MediaCodec,
 * and performs cross-correlation to detect duplicate audio tracks with acoustic precision.
 */
object AudioWaveformFingerprinter {

    private const val BUCKET_COUNT = 36
    private const val SAMPLE_WINDOW_US = 12_000_000L // 12 seconds sample window
    private const val TIMEOUT_US = 5000L

    // In-memory cache for speed
    private val memoryCache = mutableMapOf<String, FloatArray>()

    /**
     * Extracts or retrieves from cache the normalized waveform fingerprint (energy vector).
     */
    suspend fun getWaveformFingerprint(
        context: Context,
        uriString: String,
        durationSec: Int = 0
    ): FloatArray? = withContext(Dispatchers.IO) {
        if (uriString.isBlank()) return@withContext null

        val cacheKey = "fp_" + uriString.hashCode()
        memoryCache[cacheKey]?.let { return@withContext it }

        val prefs = context.getSharedPreferences("musichub_audio_fingerprints", Context.MODE_PRIVATE)
        val cachedStr = prefs.getString(cacheKey, null)
        if (!cachedStr.isNullOrBlank()) {
            val parsed = parseFingerprint(cachedStr)
            if (parsed != null && parsed.size == BUCKET_COUNT) {
                memoryCache[cacheKey] = parsed
                return@withContext parsed
            }
        }

        val fingerprint = extractPcmWaveform(context, uriString, durationSec)
        if (fingerprint != null) {
            memoryCache[cacheKey] = fingerprint
            prefs.edit().putString(cacheKey, serializeFingerprint(fingerprint)).apply()
        }
        return@withContext fingerprint
    }

    /**
     * Synchronous version for background tasks.
     */
    fun getWaveformFingerprintSync(
        context: Context,
        uriString: String,
        durationSec: Int = 0
    ): FloatArray? {
        if (uriString.isBlank()) return null
        val cacheKey = "fp_" + uriString.hashCode()
        memoryCache[cacheKey]?.let { return it }

        val prefs = context.getSharedPreferences("musichub_audio_fingerprints", Context.MODE_PRIVATE)
        val cachedStr = prefs.getString(cacheKey, null)
        if (!cachedStr.isNullOrBlank()) {
            val parsed = parseFingerprint(cachedStr)
            if (parsed != null && parsed.size == BUCKET_COUNT) {
                memoryCache[cacheKey] = parsed
                return parsed
            }
        }

        val fingerprint = extractPcmWaveform(context, uriString, durationSec)
        if (fingerprint != null) {
            memoryCache[cacheKey] = fingerprint
            prefs.edit().putString(cacheKey, serializeFingerprint(fingerprint)).apply()
        }
        return fingerprint
    }

    /**
     * Decodes a 12-second audio segment into a normalized amplitude bucket array.
     */
    private fun extractPcmWaveform(
        context: Context,
        uriString: String,
        durationSec: Int
    ): FloatArray? {
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        try {
            extractor = MediaExtractor()
            if (uriString.startsWith("content://")) {
                extractor.setDataSource(context, Uri.parse(uriString), null)
            } else if (uriString.startsWith("file://")) {
                val path = Uri.parse(uriString).path ?: return null
                val f = File(path)
                if (!f.exists() || f.length() < 1024) return null
                extractor.setDataSource(path)
            } else {
                val f = File(uriString)
                if (!f.exists() || f.length() < 1024) return null
                extractor.setDataSource(f.absolutePath)
            }

            var audioTrackIndex = -1
            var trackFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    trackFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || trackFormat == null) return null

            val totalDurationUs = if (trackFormat.containsKey(MediaFormat.KEY_DURATION)) {
                trackFormat.getLong(MediaFormat.KEY_DURATION)
            } else {
                durationSec * 1_000_000L
            }

            // Seek to ~30% into the song (chorus / main groove)
            val seekTargetUs = if (totalDurationUs > 20_000_000L) {
                (totalDurationUs * 0.30).toLong()
            } else {
                0L
            }

            extractor.selectTrack(audioTrackIndex)
            extractor.seekTo(seekTargetUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: return null
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(trackFormat, null, null, 0)
            codec.start()

            val bucketSums = FloatArray(BUCKET_COUNT) { 0f }
            val bucketCounts = IntArray(BUCKET_COUNT) { 0 }

            val startSampleTimeUs = extractor.sampleTime
            val endTargetTimeUs = startSampleTimeUs + SAMPLE_WINDOW_US
            val bucketDurationUs = SAMPLE_WINDOW_US / BUCKET_COUNT

            val bufferInfo = MediaCodec.BufferInfo()
            var isEos = false
            var decodedTimeUs = 0L

            val maxIterations = 500
            var iterations = 0

            while (!isEos && iterations++ < maxIterations && decodedTimeUs < SAMPLE_WINDOW_US) {
                val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEos = true
                        } else {
                            val sampleTime = extractor.sampleTime
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTime, 0)
                            extractor.advance()
                            if (sampleTime > endTargetTimeUs) {
                                isEos = true
                            }
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outputIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        val currentUs = bufferInfo.presentationTimeUs - startSampleTimeUs
                        if (currentUs in 0..SAMPLE_WINDOW_US) {
                            decodedTimeUs = currentUs
                            val bucketIdx = (currentUs / bucketDurationUs).toInt().coerceIn(0, BUCKET_COUNT - 1)

                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

                            val shortBuffer = outputBuffer.asShortBuffer()
                            var sumSquares = 0.0
                            var sampleCount = 0
                            val step = 4

                            for (s in 0 until shortBuffer.remaining() step step) {
                                val sample = shortBuffer.get(s) / 32768.0f
                                sumSquares += (sample * sample)
                                sampleCount++
                            }

                            if (sampleCount > 0) {
                                val rms = sqrt(sumSquares / sampleCount).toFloat()
                                bucketSums[bucketIdx] += rms
                                bucketCounts[bucketIdx]++
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                }
            }

            val result = FloatArray(BUCKET_COUNT)
            var maxVal = 0.001f
            for (i in 0 until BUCKET_COUNT) {
                val avg = if (bucketCounts[i] > 0) bucketSums[i] / bucketCounts[i] else 0f
                result[i] = avg
                if (avg > maxVal) maxVal = avg
            }

            for (i in 0 until BUCKET_COUNT) {
                result[i] = (result[i] / maxVal).coerceIn(0f, 1f)
            }

            return result
        } catch (e: Exception) {
            return null
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            try { extractor?.release() } catch (_: Exception) {}
        }
    }

    /**
     * Computes the maximum cross-correlation similarity between two waveform vectors,
     * testing time shifts to handle introductory silence or slight offset differences.
     * Returns a score between 0.0f (no match) and 1.0f (identical waveform).
     */
    fun computeWaveformSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f

        var maxSimilarity = 0f
        val maxShift = 4 // Shift window of ±4 buckets (~1.3 seconds)

        for (shift in -maxShift..maxShift) {
            var dot = 0f
            var normA = 0f
            var normB = 0f
            var count = 0

            for (i in a.indices) {
                val j = i + shift
                if (j in b.indices) {
                    val valA = a[i]
                    val valB = b[j]
                    dot += (valA * valB)
                    normA += (valA * valA)
                    normB += (valB * valB)
                    count++
                }
            }

            if (count > BUCKET_COUNT / 2 && normA > 0.0001f && normB > 0.0001f) {
                val sim = dot / (sqrt(normA) * sqrt(normB))
                if (sim > maxSimilarity) {
                    maxSimilarity = sim
                }
            }
        }

        return maxSimilarity.coerceIn(0f, 1f)
    }

    /**
     * Checks if two audio tracks are an acoustic match based on their waveforms.
     * Threshold is 0.88 (88% wave shape match).
     */
    fun areAudioWaveformsMatching(
        context: Context,
        uriA: String,
        durSecA: Int,
        uriB: String,
        durSecB: Int
    ): Boolean {
        if (uriA.isBlank() || uriB.isBlank()) return false
        if (uriA.equals(uriB, ignoreCase = true)) return true

        // If durations differ by more than 28 seconds, definitely not the same track
        if (durSecA > 0 && durSecB > 0 && abs(durSecA - durSecB) > 28) {
            return false
        }

        val fpA = getWaveformFingerprintSync(context, uriA, durSecA) ?: return false
        val fpB = getWaveformFingerprintSync(context, uriB, durSecB) ?: return false

        val similarity = computeWaveformSimilarity(fpA, fpB)
        return similarity >= 0.88f
    }

    private fun serializeFingerprint(fp: FloatArray): String {
        return fp.joinToString(",") { "%.4f".format(it) }
    }

    private fun parseFingerprint(str: String): FloatArray? {
        return try {
            val parts = str.split(",")
            val arr = FloatArray(parts.size)
            for (i in parts.indices) {
                arr[i] = parts[i].toFloat()
            }
            arr
        } catch (e: Exception) {
            null
        }
    }
}
