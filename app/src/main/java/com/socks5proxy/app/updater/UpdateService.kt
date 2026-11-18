package com.socks5proxy.app.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val sha256: String = ""
)

object UpdateService {

    private const val GITHUB_REPO = "kotpers67-spec/socks5-proxy-android"
    private const val API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
    private const val MAX_APK_BYTES = 512L * 1024L * 1024L
    private val DOWNLOAD_HOSTS = setOf(
        "github.com",
        "release-assets.githubusercontent.com",
        "objects.githubusercontent.com"
    )

    suspend fun checkForUpdates(currentVersion: String): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", "Socks5ProxyAndroid")
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 8_000
                readTimeout = 8_000
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext UpdateInfo(false, currentVersion, "", "")
                }

                val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val latestVersion = release.optString("tag_name", "").removePrefix("v")
                val assets = release.optJSONArray("assets")
                val apkAssets = (0 until (assets?.length() ?: 0))
                    .map { assets!!.getJSONObject(it) }
                    .filter { it.optString("name").endsWith(".apk", ignoreCase = true) }
                val selectedAsset = selectApkAsset(apkAssets)
                val downloadUrl = selectedAsset?.optString("browser_download_url", "").orEmpty()
                val digest = selectedAsset?.optString("digest", "")
                    ?.removePrefix("sha256:")
                    .orEmpty()
                val isNewer = isVersionNewer(latestVersion, currentVersion.removePrefix("v"))

                UpdateInfo(
                    hasUpdate = isNewer && downloadUrl.isNotBlank(),
                    latestVersion = latestVersion,
                    downloadUrl = downloadUrl,
                    releaseNotes = release.optString("body", ""),
                    sha256 = digest
                )
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            UpdateInfo(false, currentVersion, "", "")
        }
    }

    suspend fun downloadApk(
        context: Context,
        updateInfo: UpdateInfo,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val source = URL(updateInfo.downloadUrl)
        require(source.protocol == "https" && source.host == "github.com") {
            "Ссылка на APK должна вести на GitHub по HTTPS"
        }
        require(source.path.startsWith("/$GITHUB_REPO/releases/download/")) {
            "Ссылка на APK не относится к релизам приложения"
        }

        val folder = File(context.cacheDir, "updates")
        if (!folder.exists() && !folder.mkdirs()) error("Не удалось подготовить папку обновления")
        val temporaryFile = File(folder, "update.apk.part")
        val apkFile = File(folder, "Socks5Proxy-${updateInfo.latestVersion}.apk")
        val connection = (source.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Socks5ProxyAndroid")
            connectTimeout = 15_000
            readTimeout = 30_000
        }

        try {
            val status = connection.responseCode
            require(status in 200..299) { "GitHub вернул ошибку загрузки: HTTP $status" }
            require(connection.url.protocol == "https" && connection.url.host in DOWNLOAD_HOSTS) {
                "GitHub перенаправил загрузку на недоверенный адрес"
            }
            val contentLength = connection.contentLengthLong
            require(contentLength <= MAX_APK_BYTES) { "Файл обновления превышает допустимый размер" }

            var downloaded = 0L
            val digest = MessageDigest.getInstance("SHA-256")
            connection.inputStream.use { input ->
                FileOutputStream(temporaryFile).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        downloaded += count
                        require(downloaded <= MAX_APK_BYTES) { "Файл обновления превышает допустимый размер" }
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        if (contentLength > 0) {
                            onProgress(((downloaded * 100) / contentLength).toInt().coerceIn(0, 100))
                        }
                    }
                }
            }
            require(downloaded > 0) { "GitHub вернул пустой APK" }
            require(contentLength <= 0 || downloaded == contentLength) { "Загрузка APK завершилась не полностью" }

            if (updateInfo.sha256.isNotBlank()) {
                val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
                require(actualHash.equals(updateInfo.sha256, ignoreCase = true)) {
                    "Контрольная сумма APK не совпала"
                }
            }

            if (apkFile.exists() && !apkFile.delete()) error("Не удалось заменить старый файл обновления")
            if (!temporaryFile.renameTo(apkFile)) error("Не удалось сохранить APK обновления")
            onProgress(100)
            apkFile
        } catch (error: Exception) {
            temporaryFile.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    /** Opens Android's installer, or the per-app unknown-source settings when permission is missing. */
    fun openInstaller(context: Context, apkFile: File): Boolean {
        if (!apkFile.isFile) error("APK обновления не найден")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settingsIntent)
            return false
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
        return true
    }

    private fun selectApkAsset(assets: List<JSONObject>): JSONObject? {
        if (assets.isEmpty()) return null
        val universal = assets.firstOrNull { it.optString("name").contains("universal", ignoreCase = true) }
        if (universal != null) return universal

        val supportedAbis = Build.SUPPORTED_ABIS
        val matchingAbi = supportedAbis.firstNotNullOfOrNull { abi ->
            assets.firstOrNull { it.optString("name").contains(abi, ignoreCase = true) }
        }
        return matchingAbi ?: assets.first()
    }

    private fun isVersionNewer(remote: String, current: String): Boolean {
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val length = maxOf(remoteParts.size, currentParts.size)
        for (index in 0 until length) {
            val remotePart = remoteParts.getOrElse(index) { 0 }
            val currentPart = currentParts.getOrElse(index) { 0 }
            if (remotePart > currentPart) return true
            if (remotePart < currentPart) return false
        }
        return false
    }
}
