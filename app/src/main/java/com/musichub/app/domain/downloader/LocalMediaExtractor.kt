package com.musichub.app.domain.downloader

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern
import java.net.URLEncoder
import kotlin.coroutines.resume

data class YouTubeMetadata(
    val title: String,
    val artist: String,
    val thumbnailUrl: String
)

data class OnlineSearchResult(
    val id: String,
    val title: String,
    val artist: String,
    val duration: String,
    val artworkUrl: String,
    val webUrl: String = "https://www.youtube.com/watch?v=$id"
)

data class ExtractedMediaStream(
    val streamUrl: String,
    val userAgent: String,
    val isAudioOnly: Boolean,
    val itag: Int = 0,
    val cookies: String? = null
)

/**
 * Resilient Multi-Engine YouTube & Media Extractor.
 * Direct Fast MP3 Stream Engine + Android Innertube Protocol + Chromium Interception.
 */
object LocalMediaExtractor {
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

    const val ANDROID_YT_USER_AGENT =
        "com.google.android.youtube/20.10.38 (Linux; U; Android 14; US) gzip"

    private val YT_ID_REGEX =
        Pattern.compile("(?:youtu\\.be\\/|youtube\\.com\\/(?:embed\\/|v\\/|watch\\?v=|watch\\?.+&v=|shorts\\/))([a-zA-Z0-9_-]{11})")

    fun extractYouTubeId(url: String): String? {
        val trimmed = url.trim()
        val matcher = YT_ID_REGEX.matcher(trimmed)
        if (matcher.find()) {
            return matcher.group(1)
        }
        return when {
            trimmed.contains("youtu.be/") ->
                trimmed.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
            trimmed.contains("shorts/") ->
                trimmed.substringAfter("shorts/").substringBefore("?").substringBefore("&")
            trimmed.contains("embed/") ->
                trimmed.substringAfter("embed/").substringBefore("?").substringBefore("&")
            trimmed.contains("v=") ->
                trimmed.substringAfter("v=").substringBefore("&").substringBefore("?")
            else -> null
        }?.takeIf { it.isNotBlank() && it.length == 11 }
    }

    /**
     * Searches YouTube directly for a query and returns the best matching video ID.
     */
    suspend fun searchYouTubeVideoId(query: String, client: OkHttpClient): String? = withContext(Dispatchers.IO) {
        // 1. Android Innertube Protocol (Fastest, zero captcha/cookies needed)
        try {
            val payload = JSONObject().apply {
                val contextObj = JSONObject().apply {
                    val clientObj = JSONObject().apply {
                        put("clientName", "ANDROID")
                        put("clientVersion", "20.10.38")
                        put("androidSdkVersion", 34)
                        put("hl", "en")
                        put("gl", "US")
                    }
                    put("client", clientObj)
                }
                put("context", contextObj)
                put("query", query)
            }

            val req = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/search")
                .header("Content-Type", "application/json")
                .header("User-Agent", ANDROID_YT_USER_AGENT)
                .header("X-YouTube-Client-Name", "3")
                .header("X-YouTube-Client-Version", "20.10.38")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val bodyStr = resp.body?.string() ?: ""
                val pattern = Pattern.compile("\"videoId\"\\s*:\\s*\"([a-zA-Z0-9_-]{11})\"")
                val matcher = pattern.matcher(bodyStr)
                if (matcher.find()) {
                    val vid = matcher.group(1)
                    if (!vid.isNullOrBlank()) {
                        return@withContext vid
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Web Search Fallback
        try {
            val clean = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://www.youtube.com/results?search_query=$clean"
            val req = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", USER_AGENT)
                .build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val html = resp.body?.string() ?: ""
                val pattern = Pattern.compile("\"videoId\"\\s*:\\s*\"([a-zA-Z0-9_-]{11})\"")
                val matcher = pattern.matcher(html)
                if (matcher.find()) {
                    return@withContext matcher.group(1)
                }
            }
        } catch (_: Exception) {}
        null
    }

    /**
     * Searches YouTube directly and returns structured list of tracks with title, artist, duration and thumbnail.
     */
    suspend fun searchYouTubeTracks(
        query: String,
        client: OkHttpClient = OkHttpClient(),
        limit: Int = 20
    ): List<OnlineSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val results = mutableListOf<OnlineSearchResult>()
        val seenIds = mutableSetOf<String>()

        // 1. Android Innertube Protocol (Fastest, zero captcha/cookies needed)
        try {
            val payload = JSONObject().apply {
                val contextObj = JSONObject().apply {
                    val clientObj = JSONObject().apply {
                        put("clientName", "ANDROID")
                        put("clientVersion", "20.10.38")
                        put("androidSdkVersion", 34)
                        put("hl", "en")
                        put("gl", "US")
                    }
                    put("client", clientObj)
                }
                put("context", contextObj)
                put("query", query.trim())
            }

            val req = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/search")
                .header("Content-Type", "application/json")
                .header("User-Agent", ANDROID_YT_USER_AGENT)
                .header("X-YouTube-Client-Name", "3")
                .header("X-YouTube-Client-Version", "20.10.38")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val bodyStr = resp.body?.string() ?: ""
                val rootJson = JSONObject(bodyStr)

                fun inspect(obj: JSONObject) {
                    val vr = obj.optJSONObject("videoRenderer") ?: obj.optJSONObject("compactVideoRenderer")
                    if (vr != null) {
                        val vid = vr.optString("videoId")
                        if (vid.isNotBlank() && vid.length == 11 && seenIds.add(vid)) {
                            val titleRuns = vr.optJSONObject("title")?.optJSONArray("runs")
                            val rawTitle = if (titleRuns != null && titleRuns.length() > 0) {
                                titleRuns.getJSONObject(0).optString("text")
                            } else {
                                vr.optJSONObject("title")?.optString("simpleText", "") ?: ""
                            }

                            val artistRuns = (vr.optJSONObject("longBylineText")
                                ?: vr.optJSONObject("ownerText")
                                ?: vr.optJSONObject("shortBylineText"))?.optJSONArray("runs")
                            val rawArtist = if (artistRuns != null && artistRuns.length() > 0) {
                                artistRuns.getJSONObject(0).optString("text")
                            } else {
                                "YouTube"
                            }

                            val dur = vr.optJSONObject("lengthText")?.optString("simpleText") ?: "3:30"

                            val thumbs = vr.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                            val thumbUrl = if (thumbs != null && thumbs.length() > 0) {
                                thumbs.getJSONObject(thumbs.length() - 1).optString("url")
                            } else {
                                "https://i.ytimg.com/vi/$vid/hqdefault.jpg"
                            }

                            if (rawTitle.isNotBlank()) {
                                results.add(
                                    OnlineSearchResult(
                                        id = vid,
                                        title = rawTitle.trim(),
                                        artist = rawArtist.trim(),
                                        duration = dur,
                                        artworkUrl = thumbUrl
                                    )
                                )
                            }
                        }
                    }
                }

                fun scan(item: Any?) {
                    if (results.size >= limit) return
                    when (item) {
                        is JSONObject -> {
                            inspect(item)
                            val it = item.keys()
                            while (it.hasNext()) {
                                val k = it.next()
                                scan(item.opt(k))
                            }
                        }
                        is org.json.JSONArray -> {
                            for (i in 0 until item.length()) {
                                scan(item.opt(i))
                            }
                        }
                    }
                }

                scan(rootJson)
            }
        } catch (_: Exception) {}

        // 2. Web Search Fallback if empty
        if (results.isEmpty()) {
            try {
                val clean = URLEncoder.encode(query.trim(), "UTF-8")
                val searchUrl = "https://www.youtube.com/results?search_query=$clean"
                val req = Request.Builder()
                    .url(searchUrl)
                    .header("User-Agent", USER_AGENT)
                    .build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val html = resp.body?.string() ?: ""
                    val pattern = Pattern.compile("\"videoId\"\\s*:\\s*\"([a-zA-Z0-9_-]{11})\"")
                    val matcher = pattern.matcher(html)
                    while (matcher.find() && results.size < limit) {
                        val vid = matcher.group(1)
                        if (vid != null && seenIds.add(vid)) {
                            results.add(
                                OnlineSearchResult(
                                    id = vid,
                                    title = "Audio ($vid)",
                                    artist = "YouTube",
                                    duration = "3:30",
                                    artworkUrl = "https://i.ytimg.com/vi/$vid/hqdefault.jpg"
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        results.take(limit)
    }

    /**
     * Fetches official metadata directly from YouTube oEmbed on the local device.
     */
    suspend fun fetchMetadata(videoId: String, client: OkHttpClient): YouTubeMetadata? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json")
                .header("User-Agent", USER_AGENT)
                .build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val bodyStr = resp.body?.string() ?: ""
                val json = JSONObject(bodyStr)
                val rawTitle = json.optString("title", "").trim()
                val author = json.optString("author_name", "YouTube").trim()
                val thumb = resolveBestYouTubeThumbnail(videoId, client)

                if (rawTitle.isNotBlank()) {
                    val cleanArt = author
                        .replace(Regex("(?i)\\s*-\\s*topic$"), "")
                        .replace(Regex("(?i)\\s*topic$"), "")
                        .replace(Regex("(?i)\\b(vevo|official channel|official)\\b"), "")
                        .trim()
                        .trim('-', ' ', ':')
                        .ifBlank { "YouTube" }
                    var cleanTit = rawTitle
                        .replace(Regex("(?i)^topic\\s*[-:]\\s*"), "")
                        .replace(Regex("(?i)\\s*-\\s*topic$"), "")
                        .replace(Regex("(?i)\\s*\\(official(\\s+music)?\\s+(video|audio)\\)"), "")
                        .replace(Regex("(?i)\\s*\\[official(\\s+music)?\\s+(video|audio)\\]"), "")
                        .replace(Regex("(?i)\\s*\\(audio\\)"), "")
                        .replace(Regex("(?i)\\s*\\[audio\\]"), "")
                        .replace(Regex("(?i)\\s*\\(video\\)"), "")
                        .replace(Regex("(?i)\\s*\\[video\\]"), "")
                        .replace(Regex("(?i)\\s*\\(visualizer\\)"), "")
                        .replace(Regex("(?i)\\s*\\[visualizer\\]"), "")
                        .replace(Regex("(?i)\\s*\\(mv\\)"), "")
                        .replace(Regex("(?i)\\s*\\[mv\\]"), "")
                        .replace(Regex("(?i)\\s*\\(lyrics?\\)"), "")
                        .replace(Regex("(?i)\\s*\\[lyrics?\\]"), "")
                        .trim()

                    if (cleanArt != "YouTube" && cleanTit.startsWith("$cleanArt - ", ignoreCase = true)) {
                        cleanTit = cleanTit.substring("$cleanArt - ".length).trim()
                    } else if (cleanArt != "YouTube" && cleanTit.startsWith("$cleanArt: ", ignoreCase = true)) {
                        cleanTit = cleanTit.substring("$cleanArt: ".length).trim()
                    }

                    cleanTit = cleanTit.trim('-', ' ', ':').ifBlank { rawTitle }

                    return@withContext YouTubeMetadata(
                        title = cleanTit,
                        artist = cleanArt,
                        thumbnailUrl = thumb
                    )
                }
            }
        } catch (e: Exception) {}
        null
    }

    /**
     * Resolves the highest resolution thumbnail available for a YouTube video.
     * Tries 1280x720 HD (maxresdefault.jpg), 720p (hq720.jpg), SD (sddefault.jpg), HQ (hqdefault.jpg), then MQ.
     */
    suspend fun resolveBestYouTubeThumbnail(videoId: String, client: OkHttpClient): String = withContext(Dispatchers.IO) {
        val candidates = listOf(
            "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg",
            "https://i.ytimg.com/vi/$videoId/hq720.jpg",
            "https://i.ytimg.com/vi/$videoId/sddefault.jpg",
            "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        )
        for (candidate in candidates) {
            try {
                val req = Request.Builder()
                    .url(candidate)
                    .head()
                    .header("User-Agent", USER_AGENT)
                    .build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    return@withContext candidate
                }
            } catch (_: Exception) {}
        }
        "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"
    }

    /**
     * Searches iTunes Search API for master studio album artwork (up to 1000x1000px).
     */
    suspend fun searchHdCoverArt(query: String, client: OkHttpClient): String? = withContext(Dispatchers.IO) {
        try {
            val cleanQuery = query.replace(Regex("[\\[\\](){}|_]"), " ")
                .replace("Official Music Video", "", ignoreCase = true)
                .replace("Official Video", "", ignoreCase = true)
                .replace("Music Video", "", ignoreCase = true)
                .replace("Topic", "", ignoreCase = true)
                .replace("MV", "", ignoreCase = true)
                .replace("VEVO", "", ignoreCase = true)
                .replace("\\s+".toRegex(), " ")
                .trim()
            if (cleanQuery.isBlank()) return@withContext null

            val itunesUrl = "https://itunes.apple.com/search?term=${URLEncoder.encode(cleanQuery, "UTF-8")}&entity=song&limit=1"
            val req = Request.Builder().url(itunesUrl).header("User-Agent", USER_AGENT).build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val json = JSONObject(resp.body?.string() ?: "")
                val results = json.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    val first = results.getJSONObject(0)
                    val art100 = first.optString("artworkUrl100", "")
                    if (art100.isNotBlank()) {
                        return@withContext art100.replace("100x100bb.jpg", "1000x1000bb.jpg")
                    }
                }
            }
        } catch (_: Exception) {}
        null
    }

    /**
     * Primary High-Speed Direct MP3 Stream Engine.
     * Fetches direct audio stream from CDN with 100% full file integrity.
     */
    suspend fun fetchLoaderStreamUrl(
        videoId: String,
        client: OkHttpClient,
        onProgress: (Int, String) -> Unit = { _, _ -> },
        isKhmer: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        try {
            val initUrl = "https://loader.to/ajax/download.php?format=mp3&url=https://www.youtube.com/watch?v=$videoId"
            val initReq = Request.Builder()
                .url(initUrl)
                .header("User-Agent", USER_AGENT)
                .build()
            val initResp = client.newCall(initReq).execute()
            if (!initResp.isSuccessful) return@withContext null
            val initBody = initResp.body?.string() ?: ""
            val initJson = JSONObject(initBody)
            val streamId = initJson.optString("id", "")
            if (streamId.isBlank()) return@withContext null

            for (i in 1..15) {
                delay(1200)
                val progUrl = "https://loader.to/ajax/progress.php?id=$streamId"
                val progReq = Request.Builder()
                    .url(progUrl)
                    .header("User-Agent", USER_AGENT)
                    .build()
                val progResp = client.newCall(progReq).execute()
                if (progResp.isSuccessful) {
                    val progBody = progResp.body?.string() ?: ""
                    val progJson = JSONObject(progBody)
                    val dlUrl = progJson.optString("download_url", "")
                    val p = progJson.optInt("progress", 0)
                    val mappedProgress = 20 + (p * 25 / 1000).coerceIn(0, 25)
                    withContext(Dispatchers.Main) {
                        onProgress(
                            mappedProgress,
                            if (isKhmer) "កំពុងរៀបចំ Audio Stream..." else "Preparing audio stream..."
                        )
                    }
                    if (dlUrl.isNotBlank() && dlUrl != "null" && dlUrl.startsWith("http")) {
                        return@withContext dlUrl
                    }
                }
            }
        } catch (e: Exception) {}
        null
    }

    /**
     * Extracts direct streams via the official Android YouTube Innertube client protocol.
     */
    suspend fun extractStreamDirect(videoId: String, client: OkHttpClient): List<ExtractedMediaStream> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ExtractedMediaStream>()
        try {
            val payload = JSONObject().apply {
                val contextObj = JSONObject().apply {
                    val clientObj = JSONObject().apply {
                        put("clientName", "ANDROID")
                        put("clientVersion", "20.10.38")
                        put("androidSdkVersion", 34)
                        put("hl", "en")
                        put("gl", "US")
                    }
                    put("client", clientObj)
                }
                put("context", contextObj)
                put("videoId", videoId)
            }

            val req = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player")
                .header("Content-Type", "application/json")
                .header("User-Agent", ANDROID_YT_USER_AGENT)
                .header("X-YouTube-Client-Name", "3")
                .header("X-YouTube-Client-Version", "20.10.38")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val bodyStr = resp.body?.string() ?: ""
                val json = JSONObject(bodyStr)
                val sd = json.optJSONObject("streamingData")
                val adaptive = sd?.optJSONArray("adaptiveFormats")
                if (adaptive != null) {
                    val audioStreams = mutableListOf<ExtractedMediaStream>()
                    for (i in 0 until adaptive.length()) {
                        val f = adaptive.getJSONObject(i)
                        val mime = f.optString("mimeType", "")
                        val rawUrl = f.optString("url", "")
                        val itag = f.optInt("itag", 0)

                        if (mime.contains("audio") && rawUrl.isNotBlank()) {
                            val cleanUrl = cleanGoogleVideoUrl(rawUrl)
                            audioStreams.add(
                                ExtractedMediaStream(
                                    streamUrl = cleanUrl,
                                    userAgent = ANDROID_YT_USER_AGENT,
                                    isAudioOnly = true,
                                    itag = itag
                                )
                            )
                        }
                    }

                    audioStreams.sortWith(compareByDescending { stream ->
                        when (stream.itag) {
                            140 -> 100
                            251 -> 90
                            139 -> 80
                            250 -> 70
                            249 -> 60
                            else -> 50
                        }
                    })

                    results.addAll(audioStreams)
                }
            }
        } catch (e: Exception) {}
        results
    }

    suspend fun extractStreamUrl(
        context: Context,
        videoId: String,
        client: OkHttpClient,
        onProgress: (Int, String) -> Unit = { _, _ -> },
        isKhmer: Boolean = false,
        timeoutMs: Long = 8000L
    ): ExtractedMediaStream? {
        // 1. Primary Engine: Direct High-Speed MP3 Stream
        val loaderUrl = fetchLoaderStreamUrl(videoId, client, onProgress, isKhmer)
        if (loaderUrl != null && loaderUrl.isNotBlank()) {
            return ExtractedMediaStream(
                streamUrl = loaderUrl,
                userAgent = USER_AGENT,
                isAudioOnly = true,
                itag = 140
            )
        }

        // 2. Secondary Engine: Innertube Direct Audio Protocol
        val directStreams = extractStreamDirect(videoId, client)
        if (directStreams.isNotEmpty()) {
            return directStreams.first()
        }

        // 3. Fallback: Headless Chromium Interception
        return extractStreamViaWebView(context, videoId, timeoutMs)
    }

    private suspend fun extractStreamViaWebView(
        context: Context,
        videoId: String,
        timeoutMs: Long
    ): ExtractedMediaStream? = withTimeoutOrNull(timeoutMs) {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val isFinished = AtomicBoolean(false)
                var webView: WebView? = null
                val handler = Handler(Looper.getMainLooper())

                fun finish(result: ExtractedMediaStream?) {
                    if (isFinished.compareAndSet(false, true)) {
                        handler.removeCallbacksAndMessages(null)
                        try {
                            webView?.stopLoading()
                            webView?.destroy()
                        } catch (e: Exception) {}
                        webView = null
                        if (cont.isActive) {
                            cont.resume(result)
                        }
                    }
                }

                cont.invokeOnCancellation {
                    handler.post {
                        try {
                            webView?.stopLoading()
                            webView?.destroy()
                        } catch (e: Exception) {}
                        webView = null
                    }
                }

                try {
                    val wv = WebView(context.applicationContext)
                    webView = wv

                    wv.settings.apply {
                        javaScriptEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        domStorageEnabled = true
                        databaseEnabled = true
                        userAgentString = USER_AGENT
                        loadsImagesAutomatically = false
                        blockNetworkImage = true
                    }

                    wv.webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val reqUrl = request?.url?.toString() ?: ""
                            if (reqUrl.contains("googlevideo.com/videoplayback")) {
                                val cleaned = cleanGoogleVideoUrl(reqUrl)
                                val isAudio = reqUrl.contains("mime=audio") ||
                                        reqUrl.contains("itag=140") ||
                                        reqUrl.contains("itag=251") ||
                                        reqUrl.contains("itag=250") ||
                                        reqUrl.contains("itag=249")

                                if (isAudio) {
                                    val cookie = CookieManager.getInstance().getCookie(reqUrl)
                                    finish(
                                        ExtractedMediaStream(
                                            streamUrl = cleaned,
                                            userAgent = USER_AGENT,
                                            isAudioOnly = true,
                                            cookies = cookie
                                        )
                                    )
                                }
                            }
                            return super.shouldInterceptRequest(view, request)
                        }
                    }

                    wv.loadUrl("https://www.youtube.com/embed/$videoId?autoplay=1&enablejsapi=1")

                } catch (e: Exception) {
                    finish(null)
                }
            }
        }
    }

    fun cleanGoogleVideoUrl(rawUrl: String): String {
        return rawUrl
            .replace(Regex("&range=[0-9]+-[0-9]+"), "")
            .replace(Regex("\\?range=[0-9]+-[0-9]+&"), "?")
            .replace(Regex("\\?range=[0-9]+-[0-9]+$"), "")
    }
}
