package com.socks5proxy.app.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import java.util.zip.ZipFile

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val sha256: String = "",
    val errorMessage: String? = null
)

object UpdateService {

    private const val GITHUB_REPO = "kotpers67-spec/socks5-proxy-android"
    private const val API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
    private const val MAX_APK_BYTES = 512L * 1024L * 1024L
    private const val MAX_REDIRECTS = 5
    private val DOWNLOAD_HOSTS = setOf(
        "github.com",
        "release-assets.githubusercontent.com",
        "objects.githubusercontent.com"
    )

    fun getUpdatesFolder(context: Context): File {
        val folder = File(context.cacheDir, "updates")
        if (!folder.exists()) folder.mkdirs()
        return folder
    }

    fun getPartFile(context: Context): File {
        return File(getUpdatesFolder(context), "update.apk.part")
    }

    fun getExistingPartBytes(context: Context): Long {
        val part = getPartFile(context)
        return if (part.isFile) part.length() else 0L
    }

    fun getTargetApkFile(context: Context, version: String): File {
        return File(getUpdatesFolder(context), "Socks5Proxy-$version.apk")
    }

    suspend fun checkForUpdates(currentVersion: String): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", "Socks5ProxyAndroid")
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext UpdateInfo(
                        hasUpdate = false,
                        latestVersion = currentVersion,
                        downloadUrl = "",
                        releaseNotes = "",
                        errorMessage = "GitHub API вернул HTTP ${connection.responseCode}"
                    )
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
        } catch (e: Exception) {
            UpdateInfo(
                hasUpdate = false,
                latestVersion = currentVersion,
                downloadUrl = "",
                releaseNotes = "",
                errorMessage = e.localizedMessage ?: "Не удалось связаться с GitHub"
            )
        }
    }

    suspend fun downloadApk(
        context: Context,
        updateInfo: UpdateInfo,
        onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        var currentUrl = URL(updateInfo.downloadUrl)
        require(currentUrl.protocol == "https" && currentUrl.host in DOWNLOAD_HOSTS) {
            "Ссылка на APK должна вести на проверенные адреса GitHub по HTTPS"
        }

        val temporaryFile = getPartFile(context)
        val apkFile = getTargetApkFile(context, updateInfo.latestVersion)

        // Если готовый валидный APK уже скачан целиком
        if (apkFile.isFile && isApkValid(apkFile)) {
            onProgress(100, apkFile.length(), apkFile.length())
            return@withContext apkFile
        }

        val existingBytes = if (temporaryFile.isFile) temporaryFile.length() else 0L
        var connection: HttpURLConnection? = null
        var redirectCount = 0

        try {
            while (true) {
                val conn = (currentUrl.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "Socks5ProxyAndroid")
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    if (existingBytes > 0) {
                        setRequestProperty("Range", "bytes=$existingBytes-")
                    }
                }
                connection = conn

                val status = conn.responseCode
                if (status in 300..399) {
                    val location = conn.getHeaderField("Location")
                        ?: error("GitHub вернул пустой адрес перенаправления")
                    currentUrl = URL(currentUrl, location)
                    conn.disconnect()
                    redirectCount++
                    require(redirectCount <= MAX_REDIRECTS) { "Слишком много перенаправлений" }
                    require(currentUrl.protocol == "https" && currentUrl.host in DOWNLOAD_HOSTS) {
                        "Недоверенный адрес перенаправления: ${currentUrl.host}"
                    }
                    continue
                }

                require(status == HttpURLConnection.HTTP_OK || status == HttpURLConnection.HTTP_PARTIAL) {
                    "Ошибка загрузки с GitHub: HTTP $status"
                }
                break
            }

            val finalConn = connection ?: error("Не удалось установить сетевое соединение")
            val isPartial = finalConn.responseCode == HttpURLConnection.HTTP_PARTIAL
            val contentLength = finalConn.contentLengthLong

            val totalBytes: Long
            val appendMode: Boolean
            var downloaded: Long

            if (isPartial && existingBytes > 0) {
                totalBytes = if (contentLength > 0) existingBytes + contentLength else -1L
                appendMode = true
                downloaded = existingBytes
            } else {
                totalBytes = contentLength
                appendMode = false
                downloaded = 0L
            }

            require(totalBytes <= MAX_APK_BYTES) { "Файл обновления превышает допустимый размер" }

            finalConn.inputStream.use { input ->
                FileOutputStream(temporaryFile, appendMode).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        require(downloaded <= MAX_APK_BYTES) { "Файл обновления превысил лимит" }

                        if (totalBytes > 0) {
                            val percent = ((downloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
                            onProgress(percent, downloaded, totalBytes)
                        } else {
                            onProgress(-1, downloaded, -1L)
                        }
                    }
                    output.flush()
                }
            }

            require(downloaded > 1024 * 1024) { "Скачанный файл слишком мал для APK приложения" }
            require(totalBytes <= 0 || downloaded >= totalBytes) { "Загрузка прервана, скачано $downloaded из $totalBytes байт" }

            // Проверяем валидность APK (должен быть валидным ZIP с AndroidManifest.xml)
            if (!isApkValid(temporaryFile)) {
                temporaryFile.delete()
                error("Скачанный файл поврежден или не является корректным Android APK")
            }

            if (updateInfo.sha256.isNotBlank()) {
                val digest = MessageDigest.getInstance("SHA-256")
                temporaryFile.inputStream().use { stream ->
                    val buf = ByteArray(32 * 1024)
                    var read: Int
                    while (stream.read(buf).also { read = it } != -1) {
                        digest.update(buf, 0, read)
                    }
                }
                val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actualHash.equals(updateInfo.sha256, ignoreCase = true)) {
                    temporaryFile.delete()
                    error("Контрольная сумма SHA-256 не совпала")
                }
            }

            if (apkFile.exists()) apkFile.delete()
            if (!temporaryFile.renameTo(apkFile)) {
                error("Не удалось переименовать файл обновления в ${apkFile.name}")
            }

            apkFile.setReadable(true, false)
            onProgress(100, downloaded, totalBytes)
            apkFile

        } catch (e: Exception) {
            // При ошибке сети НЕ удаляем part файл, сохраняем для докачки!
            throw e
        } finally {
            connection?.disconnect()
        }
    }

    fun isApkValid(file: File): Boolean {
        if (!file.isFile || file.length() < 1024 * 1024) return false
        return try {
            ZipFile(file).use { zip ->
                zip.getEntry("AndroidManifest.xml") != null &&
                zip.getEntry("classes.dex") != null
            }
        } catch (_: Exception) {
            false
        }
    }

    fun cleanupOldUpdates(context: Context, keepFileName: String? = null) {
        runCatching {
            getUpdatesFolder(context).listFiles()?.forEach { file ->
                if (file.isFile && file.name != keepFileName && file.name != "update.apk.part") {
                    file.delete()
                }
            }
        }
    }

    /** Opens Android's installer, or the per-app unknown-source settings when permission is missing. */
    fun openInstaller(context: Context, apkFile: File): Boolean {
        if (!apkFile.isFile || !isApkValid(apkFile)) error("Файл APK не найден или поврежден")

        apkFile.setReadable(true, false)

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

        val resInfoList = context.packageManager.queryIntentActivities(installIntent, PackageManager.MATCH_DEFAULT_ONLY)
        for (resolveInfo in resInfoList) {
            val packageName = resolveInfo.activityInfo.packageName
            context.grantUriPermission(packageName, apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(installIntent)
        return true
    }

    private fun selectApkAsset(assets: List<JSONObject>): JSONObject? {
        if (assets.isEmpty()) return null
        val signedAsset = assets.firstOrNull { it.optString("name").contains("signed", ignoreCase = true) }
        if (signedAsset != null) return signedAsset

        val universal = assets.firstOrNull { it.optString("name").contains("universal", ignoreCase = true) }
        if (universal != null) return universal

        val supportedAbis = Build.SUPPORTED_ABIS
        val matchingAbi = supportedAbis.firstNotNullOfOrNull { abi ->
            assets.firstOrNull { it.optString("name").contains(abi, ignoreCase = true) }
        }
        return matchingAbi ?: assets.first()
    }

    fun isVersionNewer(remote: String, current: String): Boolean {
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
