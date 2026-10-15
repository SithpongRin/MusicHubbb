package com.musichub.app.domain.mood

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.musichub.app.data.database.AppDatabase
import com.musichub.app.data.database.MoodPredictionEntity
import com.musichub.app.domain.audio.AiAudioMoodEngine
import com.musichub.app.domain.audio.SongMoodCategory
import com.musichub.app.domain.audio.SongMoodProfile
import com.musichub.app.presentation.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Runs the bundled MERT-v1-95M music-emotion regressor entirely on-device.
 *
 * The model emits valence/arousal. MusicHub's four labels are scored as smooth
 * distances to documented points in that learned affect space; the model itself
 * is never replaced by title, artist, genre, or waveform heuristics.
 */
object OnDeviceMusicMoodClassifier {
    const val MODEL_VERSION = "mert-v1-95m-deam-pmemo-int8-2"
    private const val MODEL_ASSET = "mood/mert_emotion_int8.onnx"
    private const val EXPECTED_MODEL_BYTES = 95_552_890L
    private const val SAMPLE_RATE = 24_000
    private const val WINDOW_SECONDS = 15
    private const val SAMPLE_COUNT = SAMPLE_RATE * WINDOW_SECONDS

    @Volatile
    private var ortEnv: OrtEnvironment? = null
    @Volatile
    private var ortSession: OrtSession? = null
    @Volatile
    private var isBatchRunning = false

    data class Prediction(
        val valence: Float,
        val arousal: Float,
        val moodScores: FloatArray,
        val confidence: Float
    )

    data class FullAudioAnalysis(
        val prediction: Prediction,
        val dspFeatures: com.musichub.app.domain.audio.AudioDspFeatureExtractor.DspFeatures
    )

    @Synchronized
    fun closeSession() {
        try {
            ortSession?.close()
            ortSession = null
        } catch (_: Throwable) {}
    }

    fun asSongMoodProfile(entity: MoodPredictionEntity, songId: String): SongMoodProfile {
        val scores = entity.scores()
        val primaryIndex = scores.indices.maxByOrNull { scores[it] } ?: 0
        val secondaryIndex = scores.indices.filter { it != primaryIndex }
            .maxByOrNull { scores[it] } ?: primaryIndex
        return SongMoodProfile(
            songId = songId,
            probabilities = scores.copyOf(),
            primaryMood = SongMoodCategory.entries[primaryIndex],
            secondaryMood = SongMoodCategory.entries[secondaryIndex],
            energyScore = entity.arousal,
            tempoScore = entity.arousal,
            valenceScore = entity.valence * 2f - 1f,
            confidenceScore = entity.confidence
        )
    }

    suspend fun classify(context: Context, song: SongItem): Prediction? = withContext(Dispatchers.IO) {
        return@withContext try {
            val songId = song.id.takeIf { it.isNotBlank() } ?: return@withContext null
            val dao = AppDatabase.getDatabase(context).moodPredictionDao()
            dao.get(songId, MODEL_VERSION)?.let { cached ->
                return@withContext Prediction(cached.valence, cached.arousal, cached.scores(), cached.confidence)
            }

            val waveform = decodeCenterWindow(context, song)
            if (waveform == null) {
                // Persist neutral fallback so unreadable files are not repeatedly re-analyzed on every app start
                dao.upsert(
                    MoodPredictionEntity(
                        songId = songId,
                        modelVersion = MODEL_VERSION,
                        valence = 0.5f,
                        arousal = 0.5f,
                        calm = 0.25f,
                        energetic = 0.25f,
                        sad = 0.25f,
                        romantic = 0.25f,
                        confidence = 0.15f,
                        analyzedAt = System.currentTimeMillis()
                    )
                )
                return@withContext null
            }

            val raw = runCatching { infer(context, waveform) }.getOrNull()
            if (raw == null) {
                dao.upsert(
                    MoodPredictionEntity(
                        songId = songId,
                        modelVersion = MODEL_VERSION,
                        valence = 0.5f,
                        arousal = 0.5f,
                        calm = 0.25f,
                        energetic = 0.25f,
                        sad = 0.25f,
                        romantic = 0.25f,
                        confidence = 0.15f,
                        analyzedAt = System.currentTimeMillis()
                    )
                )
                return@withContext null
            }

            val prediction = fromModelOutput(raw[0], raw[1])
            val dsp = com.musichub.app.domain.audio.AudioDspFeatureExtractor.extractFeatures(
                waveform = waveform,
                sampleRate = SAMPLE_RATE,
                valence = prediction.valence,
                arousal = prediction.arousal
            )
            val topIndex = prediction.moodScores.indices.maxByOrNull { prediction.moodScores[it] } ?: 0
            val secondIndex = prediction.moodScores.indices.filter { it != topIndex }
                .maxByOrNull { prediction.moodScores[it] } ?: topIndex
            val secondaryMoodStr = SongMoodCategory.entries[secondIndex].name

            dao.upsert(
                MoodPredictionEntity(
                    songId = songId,
                    modelVersion = MODEL_VERSION,
                    valence = prediction.valence,
                    arousal = prediction.arousal,
                    calm = prediction.moodScores[0],
                    energetic = prediction.moodScores[1],
                    sad = prediction.moodScores[2],
                    romantic = prediction.moodScores[3],
                    confidence = prediction.confidence,
                    analyzedAt = System.currentTimeMillis(),
                    genre = dsp.genre,
                    bpm = dsp.bpm,
                    energy = dsp.energy,
                    danceability = dsp.danceability,
                    audioEmbedding = com.musichub.app.domain.audio.AudioDspFeatureExtractor.serializeEmbedding(dsp.audioEmbedding),
                    secondaryMood = secondaryMoodStr
                )
            )
            prediction
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun analyzeFull(context: Context, song: SongItem): FullAudioAnalysis? = withContext(Dispatchers.IO) {
        val songId = song.id.takeIf { it.isNotBlank() } ?: return@withContext null
        val waveform = decodeCenterWindow(context, song)
        val prediction = classify(context, song) ?: Prediction(
            valence = 0.5f,
            arousal = 0.5f,
            moodScores = floatArrayOf(0.25f, 0.25f, 0.25f, 0.25f),
            confidence = 0.20f
        )
        val dspFeatures = if (waveform != null) {
            com.musichub.app.domain.audio.AudioDspFeatureExtractor.extractFeatures(
                waveform = waveform,
                sampleRate = SAMPLE_RATE,
                valence = prediction.valence,
                arousal = prediction.arousal
            )
        } else {
            com.musichub.app.domain.audio.AudioDspFeatureExtractor.extractFeatures(
                waveform = FloatArray(0),
                sampleRate = SAMPLE_RATE,
                valence = prediction.valence,
                arousal = prediction.arousal
            )
        }
        FullAudioAnalysis(prediction, dspFeatures)
    }

    suspend fun processUnanalyzedSongs(
        context: Context,
        songs: List<SongItem>,
        onSongAnalyzed: (suspend (SongItem) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        if (isBatchRunning || songs.isEmpty()) return@withContext
        isBatchRunning = true
        try {
            val dao = AppDatabase.getDatabase(context).moodPredictionDao()
            val cachedMap = dao.getAllForModel(MODEL_VERSION).associateBy { it.songId }
            val unanalyzed = songs.filter { it.id.isNotBlank() && !cachedMap.containsKey(it.id) }
            if (unanalyzed.isEmpty()) {
                // All songs already analyzed and persisted! Zero work needed on launch.
                return@withContext
            }

            for (song in unanalyzed) {
                val prediction = classify(context, song)
                if (prediction != null) {
                    onSongAnalyzed?.invoke(song)
                }
                delay(120)
            }
        } catch (_: Throwable) {
        } finally {
            isBatchRunning = false
            closeSession()
        }
    }

    internal fun fromModelOutput(rawValence: Float, rawArousal: Float): Prediction {
        val valence = rawValence.coerceIn(0f, 1f)
        val arousal = rawArousal.coerceIn(0f, 1f)
        // Empirically calibrated circumplex centroids corresponding to MERT sigmoid output distribution:
        // 0: Calm (High V, Low A), 1: Energetic (High V, High A), 2: Sad (Low V, Low A), 3: Romantic (High V, Moderate A)
        val prototypes = arrayOf(
            floatArrayOf(0.58f, 0.38f), // CALM
            floatArrayOf(0.62f, 0.62f), // ENERGETIC
            floatArrayOf(0.38f, 0.38f), // SAD
            floatArrayOf(0.60f, 0.48f)  // ROMANTIC
        )
        val logits = FloatArray(4) { index ->
            val dv = valence - prototypes[index][0]
            val da = arousal - prototypes[index][1]
            -(dv * dv + da * da) / 0.04f
        }
        val peak = logits.maxOrNull() ?: 0f
        val expScores = FloatArray(4) { exp((logits[it] - peak).toDouble()).toFloat() }
        val total = expScores.sum().coerceAtLeast(0.0001f)
        for (index in expScores.indices) expScores[index] /= total
        val sorted = expScores.sortedDescending()
        val margin = (sorted[0] - sorted.getOrElse(1) { 0f }).coerceIn(0f, 1f)
        return Prediction(valence, arousal, expScores, (0.5f + margin * 0.5f).coerceIn(0f, 1f))
    }

    @Synchronized
    private fun getOrCreateSession(context: Context): Pair<OrtEnvironment, OrtSession>? {
        return try {
            val env = ortEnv ?: OrtEnvironment.getEnvironment().also { ortEnv = it }
            val session = ortSession ?: run {
                val modelFile = materializeModel(context)
                val options = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(2)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                }
                env.createSession(modelFile.absolutePath, options).also { ortSession = it }
            }
            Pair(env, session)
        } catch (_: Throwable) {
            null
        }
    }

    private fun infer(context: Context, waveform: FloatArray): FloatArray? {
        val (environment, session) = getOrCreateSession(context) ?: return null
        return synchronized(session) {
            OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(waveform),
                longArrayOf(1, SAMPLE_COUNT.toLong())
            ).use { input ->
                session.run(mapOf("audio_waveform" to input)).use { result ->
                    @Suppress("UNCHECKED_CAST")
                    ((result[0].value as Array<FloatArray>)[0]).copyOf()
                }
            }
        }
    }

    private fun materializeModel(context: Context): File {
        val directory = File(context.filesDir, "mood-model").also { it.mkdirs() }
        val output = File(directory, "mert_emotion_int8.onnx")
        if (output.exists() && output.length() == EXPECTED_MODEL_BYTES) return output

        val tempFile = File(directory, "mert_emotion_int8.onnx.tmp")
        try {
            context.assets.open(MODEL_ASSET).use { input ->
                FileOutputStream(tempFile).use { outputStream ->
                    val buffer = ByteArray(65536)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                }
            }
            if (tempFile.length() == EXPECTED_MODEL_BYTES) {
                if (output.exists()) output.delete()
                tempFile.renameTo(output)
            }
        } catch (_: Throwable) {
            tempFile.delete()
        }
        return output
    }

    internal fun decodeCenterWindow(context: Context, song: SongItem): FloatArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            val resolvedFile = AiAudioMoodEngine.resolveLocalAudioFile(song)
            if (resolvedFile != null && resolvedFile.exists()) {
                extractor.setDataSource(resolvedFile.absolutePath)
            } else if (song.uriString.startsWith("content://")) {
                extractor.setDataSource(context, Uri.parse(song.uriString), null)
            } else if (song.uriString.isNotBlank()) {
                extractor.setDataSource(song.uriString)
            } else {
                return null
            }

            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            val format = extractor.getTrackFormat(track)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE).takeIf { it > 0 } ?: 44100
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                runCatching { format.getLong(MediaFormat.KEY_DURATION) }.getOrDefault(0L)
            } else 0L

            val windowUs = WINDOW_SECONDS * 1_000_000L
            extractor.selectTrack(track)
            if (durationUs > windowUs) {
                extractor.seekTo(max(0L, durationUs / 2 - windowUs / 2), MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }

            val mime = requireNotNull(format.getString(MediaFormat.KEY_MIME))
            codec = MediaCodec.createDecoderByType(mime).apply { configure(format, null, null, 0); start() }

            val maxPcmCapacity = sampleRate * WINDOW_SECONDS
            val pcm = FloatArray(maxPcmCapacity)
            var pcmCount = 0

            val info = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            while (!outputEnded && pcmCount < maxPcmCapacity) {
                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val buffer = codec.getInputBuffer(inputIndex) ?: continue
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                when (val outputIndex = codec.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                    in 0..Int.MAX_VALUE -> {
                        if (info.size > 0) {
                            val output = codec.getOutputBuffer(outputIndex) ?: ByteBuffer.allocate(0)
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            output.order(ByteOrder.LITTLE_ENDIAN)
                            while (output.remaining() >= 2 * channels && pcmCount < maxPcmCapacity) {
                                var mixed = 0f
                                repeat(channels) { mixed += output.short / 32768f }
                                pcm[pcmCount++] = mixed / channels
                            }
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    }
                }
            }
            if (pcmCount == 0) return null
            return resampleAndPad(pcm, pcmCount, sampleRate)
        } catch (_: Throwable) {
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun resampleAndPad(source: FloatArray, sourceCount: Int, sourceRate: Int): FloatArray {
        val result = FloatArray(SAMPLE_COUNT)
        val available = min(SAMPLE_COUNT, (sourceCount.toLong() * SAMPLE_RATE / sourceRate).toInt())
        val lastIdx = sourceCount - 1
        for (i in 0 until available) {
            val position = i * sourceRate.toFloat() / SAMPLE_RATE
            val left = position.toInt().coerceIn(0, lastIdx)
            val right = min(left + 1, lastIdx)
            result[i] = source[left] + (source[right] - source[left]) * (position - left)
        }

        // Standard Wav2Vec2/MERT Waveform Normalization ((x - mean) / sqrt(var + 1e-7))
        // Ensures proper distribution alignment with DEAM/PMEmo training data
        var sum = 0.0
        for (i in 0 until available) sum += result[i]
        val mean = (if (available > 0) sum / available else 0.0).toFloat()
        var varSum = 0.0
        for (i in 0 until available) {
            val diff = result[i] - mean
            varSum += diff * diff
        }
        val variance = (if (available > 0) varSum / available else 0.0).toFloat()
        val std = kotlin.math.sqrt(variance + 1e-7f)
        for (i in result.indices) {
            result[i] = ((result[i] - mean) / std).coerceIn(-6f, 6f)
        }
        return result
    }
}
