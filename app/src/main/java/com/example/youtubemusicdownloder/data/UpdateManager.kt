package com.example.youtubemusicdownloder.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tagName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String
)

object UpdateManager {

    const val CURRENT_VERSION = "v2.4.0"
    private const val GITHUB_LATEST_RELEASE_URL = "https://api.github.com/repos/Ikurq/YoutubeMusicDownloader/releases/latest"

    suspend fun checkForUpdates(): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_LATEST_RELEASE_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.setRequestProperty("User-Agent", "YoutubeMusicDownloaderApp")
            connection.connectTimeout = 8000
            connection.readTimeout = 8000

            if (connection.responseCode == 200) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)

                val tagName = json.optString("tag_name", "")
                val releaseNotes = json.optString("body", "バグ修正とパフォーマンス改善")
                val assets = json.optJSONArray("assets")

                var apkDownloadUrl = ""
                if (assets != null && assets.length() > 0) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk")) {
                            apkDownloadUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                    if (apkDownloadUrl.isBlank() && assets.length() > 0) {
                        apkDownloadUrl = assets.getJSONObject(0).optString("browser_download_url", "")
                    }
                }

                if (tagName.isNotBlank() && isNewerVersion(tagName, CURRENT_VERSION) && apkDownloadUrl.isNotBlank()) {
                    return@withContext ReleaseInfo(
                        tagName = tagName,
                        releaseNotes = releaseNotes,
                        apkDownloadUrl = apkDownloadUrl
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestClean = latest.removePrefix("v").trim()
        val currentClean = current.removePrefix("v").trim()

        if (latestClean == currentClean) return false

        val latestParts = latestClean.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = currentClean.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    suspend fun downloadAndInstallApk(
        context: Context,
        downloadUrl: String,
        onProgress: (percent: Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val destinationFile = File(context.getExternalFilesDir(null), "update.apk")
            if (destinationFile.exists()) {
                destinationFile.delete()
            }

            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.requestMethod = "GET"
            connection.connect()

            val fileLength = connection.contentLength

            connection.inputStream.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    val data = ByteArray(4096)
                    var total: Long = 0
                    var count: Int
                    while (input.read(data).also { count = it } != -1) {
                        total += count
                        if (fileLength > 0) {
                            val percent = (total * 100 / fileLength).toInt()
                            onProgress(percent)
                        }
                        output.write(data, 0, count)
                    }
                }
            }

            // Trigger Installation
            withContext(Dispatchers.Main) {
                installApk(context, destinationFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    private fun installApk(context: Context, apkFile: File) {
        val apkUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
