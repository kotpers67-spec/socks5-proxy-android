package com.socks5proxy.app.updater

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String
)

object UpdateService {

    private const val GITHUB_REPO = "kotpers67-spec/socks5-proxy-android"
    private const val API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

    suspend fun checkForUpdates(currentVersion: String): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            val url = URL(API_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Socks5ProxyAndroid")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            if (conn.responseCode != 200) {
                return@withContext UpdateInfo(false, currentVersion, "", "")
            }

            val json = conn.inputStream.bufferedReader().use { it.readText() }
            val obj = JSONObject(json)
            val tagName = obj.optString("tag_name", "").removePrefix("v")
            val body = obj.optString("body", "")

            var apkUrl = ""
            val assets = obj.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url", "")
                        break
                    }
                }
            }

            val isNewer = isVersionNewer(tagName, currentVersion.removePrefix("v"))
            UpdateInfo(
                hasUpdate = isNewer && apkUrl.isNotEmpty(),
                latestVersion = tagName,
                downloadUrl = apkUrl,
                releaseNotes = body
            )
        } catch (_: Exception) {
            UpdateInfo(false, currentVersion, "", "")
        }
    }

    suspend fun downloadAndInstallApk(context: Context, downloadUrl: String, onProgress: (Int) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL(downloadUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Socks5ProxyAndroid")
            conn.connectTimeout = 10000
            conn.readTimeout = 15000

            val totalSize = conn.contentLength
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(updatesDir, "update.apk")

            conn.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var downloaded = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        if (totalSize > 0) {
                            val progress = ((downloaded * 100) / totalSize).toInt()
                            withContext(Dispatchers.Main) { onProgress(progress) }
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                installApk(context, apkFile)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    private fun isVersionNewer(remote: String, current: String): Boolean {
        val rParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val cParts = current.split(".").mapNotNull { it.toIntOrNull() }

        val len = maxOf(rParts.size, cParts.size)
        for (i in 0 until len) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
