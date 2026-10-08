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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern
import kotlin.coroutines.resume

data class YouTubeMetadata(
    val title: String,
    val artist: String,
    val thumbnailUrl: String
)

data class ExtractedMediaStream(
    val streamUrl: String,
    val userAgent: String,
    val isAudioOnly: Boolean,
    val cookies: String? = null
)

/**
 * 100% On-Device YouTube and Media Extractor.
 * Runs completely locally inside the Android phone using headless Chromium engine.
 * Does not depend on any third-party scraper servers or APIs.
 * Bypasses botguard challenges naturally because it runs on the device's real browser stack.
 */
object LocalMediaExtractor {
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

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
     * Fetches official metadata directly from YouTube oEmbed on the local device.
     * Guaranteed to work without API keys or external proxy services.
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
                val thumb = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                if (rawTitle.isNotBlank()) {
                    return@withContext YouTubeMetadata(
                        title = rawTitle,
                        artist = author,
                        thumbnailUrl = thumb
                    )
                }
            }
        } catch (e: Exception) {
            // Ignore and return fallback
        }
        null
    }

    /**
     * Intercepts media stream directly on the phone using headless Chromium.
     */
    suspend fun extractStreamUrl(
        context: Context,
        videoId: String,
        timeoutMs: Long = 20000L
    ): ExtractedMediaStream? = withTimeoutOrNull(timeoutMs) {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val isFinished = AtomicBoolean(false)
                var webView: WebView? = null
                val handler = Handler(Looper.getMainLooper())
                val candidateStream = AtomicReference<String?>(null)

                fun finish(result: ExtractedMediaStream?) {
                    if (isFinished.compareAndSet(false, true)) {
                        handler.removeCallbacksAndMessages(null)
                        try {
                            webView?.stopLoading()
                            webView?.destroy()
                        } catch (e: Exception) {
                        }
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
                        } catch (e: Exception) {
                        }
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

                    // Recurring script to trigger playback
                    val playTriggerRunnable = object : Runnable {
                        override fun run() {
                            if (!isFinished.get()) {
                                triggerAutoPlay(wv)
                                handler.postDelayed(this, 1200L)
                            }
                        }
                    }

                    // Fallback to mobile watch page if embed does not start within 4.5 seconds
                    val fallbackRunnable = Runnable {
                        if (!isFinished.get()) {
                            wv.loadUrl("https://m.youtube.com/watch?v=$videoId")
                        }
                    }
                    handler.postDelayed(fallbackRunnable, 4500L)
                    handler.postDelayed(playTriggerRunnable, 1000L)

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
                                } else {
                                    // Save as candidate (video+audio container like itag 18)
                                    if (candidateStream.compareAndSet(null, cleaned)) {
                                        handler.postDelayed({
                                            val c = candidateStream.get()
                                            if (c != null && !isFinished.get()) {
                                                val cookie = CookieManager.getInstance().getCookie(c)
                                                finish(
                                                    ExtractedMediaStream(
                                                        streamUrl = c,
                                                        userAgent = USER_AGENT,
                                                        isAudioOnly = false,
                                                        cookies = cookie
                                                    )
                                                )
                                            }
                                        }, 2500L)
                                    }
                                }
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            triggerAutoPlay(view)
                        }
                    }

                    // First try embed iframe (lightweight and quick)
                    wv.loadUrl("https://www.youtube.com/embed/$videoId?autoplay=1&enablejsapi=1")

                } catch (e: Exception) {
                    finish(null)
                }
            }
        }
    }

    private fun triggerAutoPlay(webView: WebView?) {
        val script = """
            (function() {
                try {
                    var v = document.querySelector('video');
                    if (v) {
                        v.muted = true;
                        v.play().catch(function(e){});
                    }
                    var b = document.querySelector('.ytp-large-play-button') ||
                            document.querySelector('.ytp-play-button') ||
                            document.querySelector('button[aria-label*="Play"]') ||
                            document.querySelector('.player-control-play');
                    if (b) b.click();
                } catch(e) {}
            })();
        """.trimIndent()
        webView?.evaluateJavascript(script, null)
    }

    fun cleanGoogleVideoUrl(rawUrl: String): String {
        return rawUrl
            .replace(Regex("&range=[0-9]+-[0-9]+"), "")
            .replace(Regex("\\?range=[0-9]+-[0-9]+&"), "?")
            .replace(Regex("\\?range=[0-9]+-[0-9]+$"), "")
    }
}
