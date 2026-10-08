package com.musichub.app.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

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
    private val repoName: String = "MusicHubbb",
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun checkLatestRelease(currentVersion: String): UpdateInfo = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"
        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/vnd.github.v3+json")
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
}

object ApkInstaller {
    /**
     * Triggers Android package installer for the downloaded APK using modern FileProvider.
     * Preserves all SQLite Room database tables, DataStore preferences, and offline files.
     */
    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) return

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
    }
}
