package com.musichub.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val hasUpdate: Boolean,
    val currentVersion: String,
    val newVersion: String,
    val releaseNotes: String,
    val apkUrl: String,
    val apkSize: Long
)

class GitHubUpdateChecker(
    private val repoOwner: String = "SithpongRin",
    private val repoName: String = "MusicHubbb"
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun checkLatestRelease(currentVersion: String): UpdateInfo = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"
        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/vnd.github.v3+json")
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) MusicHub-Android-App")
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext UpdateInfo(
                    hasUpdate = false,
                    currentVersion = currentVersion,
                    newVersion = currentVersion,
                    releaseNotes = "",
                    apkUrl = "",
                    apkSize = 0L
                )
            }

            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            val tagName = json.optString("tag_name", "").removePrefix("v")
            val notes = json.optString("body", "Bug fixes and performance improvements.")

            var apkUrl = ""
            var apkSize = 0L

            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url", "")
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            val hasUpdate = isNewerVersion(currentVersion, tagName)
            UpdateInfo(
                hasUpdate = hasUpdate,
                currentVersion = currentVersion,
                newVersion = tagName,
                releaseNotes = notes,
                apkUrl = apkUrl,
                apkSize = apkSize
            )
        } catch (e: Exception) {
            UpdateInfo(
                hasUpdate = false,
                currentVersion = currentVersion,
                newVersion = currentVersion,
                releaseNotes = "",
                apkUrl = "",
                apkSize = 0L
            )
        }
    }

    private fun isNewerVersion(current: String, candidate: String): Boolean {
        val currParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val candParts = candidate.split(".").mapNotNull { it.toIntOrNull() }

        val length = maxOf(currParts.size, candParts.size)
        for (i in 0 until length) {
            val currVal = currParts.getOrElse(i) { 0 }
            val candVal = candParts.getOrElse(i) { 0 }
            if (candVal > currVal) return true
            if (candVal < currVal) return false
        }
        return false
    }

    suspend fun downloadApk(
        context: Context,
        apkUrl: String,
        onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val cacheFolder = context.externalCacheDir ?: context.cacheDir
        val destFile = File(cacheFolder, "MusicHub-update.apk")
        if (destFile.exists()) destFile.delete()

        try {
            var currentUrl = apkUrl
            var redirectCount = 0
            var finalResp: okhttp3.Response? = null

            while (redirectCount < 6) {
                val req = Request.Builder()
                    .url(currentUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                    .header("Accept", "*/*")
                    .build()
                val resp = client.newCall(req).execute()
                if (resp.isRedirect || resp.code in 300..399) {
                    val loc = resp.header("Location")
                    resp.close()
                    if (!loc.isNullOrBlank()) {
                        currentUrl = loc
                        redirectCount++
                        continue
                    }
                }
                finalResp = resp
                break
            }

            if (finalResp == null || !finalResp.isSuccessful) {
                val code = finalResp?.code ?: 0
                finalResp?.close()
                return@withContext Result.failure(Exception("HTTP Error: $code"))
            }

            val body = finalResp.body ?: run {
                finalResp.close()
                return@withContext Result.failure(Exception("Empty response body"))
            }

            val total = body.contentLength()
            val input = body.byteStream()
            val output = FileOutputStream(destFile)
            val buffer = ByteArray(32768)
            var downloaded = 0L
            var read: Int
            var lastUpdateMs = 0L

            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
                downloaded += read
                val now = System.currentTimeMillis()
                if (now - lastUpdateMs > 150 || (total > 0 && downloaded == total)) {
                    lastUpdateMs = now
                    val p = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else 0
                    withContext(Dispatchers.Main) {
                        onProgress(p, downloaded, total)
                    }
                }
            }

            output.flush()
            output.close()
            input.close()
            finalResp.close()

            if (!destFile.exists() || destFile.length() < 500000L) {
                return@withContext Result.failure(Exception("Incomplete download (${destFile.length()} bytes)"))
            }

            Result.success(destFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

object ApkInstaller {
    /**
     * Triggers Android package installer for the downloaded APK using modern FileProvider.
     * Preserves all SQLite Room database tables, DataStore preferences, and offline files.
     */
    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(settingsIntent)
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            // Safety
        }
    }
}
