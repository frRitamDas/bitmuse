package com.music.pexpo.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.music.pexpo.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/** Checks GitHub Releases for a newer Pexpo build and can install its APK. */
object AppUpdateChecker {
    data class UpdateInfo(val version: String, val releaseUrl: String, val apkUrl: String?, val notes: String?)

    private const val CACHE_SUBDIR = "updates"
    private const val LATEST_RELEASE_URL = "https://api.github.com/repos/frRitamDas/bitmuse/releases/latest"
    private val json = Json { ignoreUnknownKeys = true }

    private val _available = MutableStateFlow<UpdateInfo?>(null)
    val available = _available.asStateFlow()

    sealed interface DownloadState {
        data object Idle : DownloadState
        data class Downloading(val fraction: Float) : DownloadState
        data class Ready(val file: File) : DownloadState
        data class Failed(val message: String) : DownloadState
    }
    private val _download = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val download = _download.asStateFlow()
    @Volatile private var downloadCancelled = false

    suspend fun check(context: Context) = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .build()
            val body = Http.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            } ?: return@runCatching
            val release = json.parseToJsonElement(body) as? JsonObject ?: return@runCatching
            val tag = release["tag_name"]?.jsonPrimitive?.contentOrNull ?: return@runCatching
            val url = release["html_url"]?.jsonPrimitive?.contentOrNull ?: return@runCatching
            val apkUrl = apkAssetUrl(release, installedVariant(context))
            val notes = release["body"]?.jsonPrimitive?.contentOrNull
            val latest = tag.removePrefix("v")
            if (isNewer(latest, BuildConfig.VERSION_NAME)) {
                _available.value = UpdateInfo(latest, url, apkUrl, notes)
            }
        }
    }

    suspend fun clearCache(context: Context) = withContext(Dispatchers.IO) {
        File(context.cacheDir, CACHE_SUBDIR).listFiles()?.forEach { it.delete() }
    }

    private fun apkAssetUrl(release: JsonObject, variant: String): String? = runCatching {
        val apkAssets = release["assets"]?.jsonArray
            ?.mapNotNull { it as? JsonObject }
            ?.filter { asset ->
                asset["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk", ignoreCase = true) == true &&
                    asset["state"]?.jsonPrimitive?.contentOrNull == "uploaded"
            }
            .orEmpty()

        // Prefer the exact ABI currently installed. Universal is only the
        // fallback when a matching split is unavailable.
        apkAssets.firstOrNull { asset ->
            asset["name"]?.jsonPrimitive?.contentOrNull?.lowercase() == "pexpo-${release["tag_name"]?.jsonPrimitive?.contentOrNull?.removePrefix("v")?.lowercase()}-$variant.apk"
        }?.get("browser_download_url")?.jsonPrimitive?.contentOrNull
            ?: apkAssets.firstOrNull { asset ->
                asset["name"]?.jsonPrimitive?.contentOrNull?.lowercase() == "pexpo-${release["tag_name"]?.jsonPrimitive?.contentOrNull?.removePrefix("v")?.lowercase()}-universal.apk"
            }?.get("browser_download_url")?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    suspend fun downloadApk(context: Context) = withContext(Dispatchers.IO) {
        val url = _available.value?.apkUrl
        if (url.isNullOrBlank()) {
            _download.value = DownloadState.Failed("No compatible Pexpo APK was found in the release.")
            return@withContext
        }

        downloadCancelled = false
        _download.value = DownloadState.Downloading(0f)

        runCatching {
            val dir = File(context.cacheDir, CACHE_SUBDIR).apply { mkdirs() }
            val target = File(dir, "pexpo-update.apk")
            val temp = File(dir, "pexpo-update.apk.part")
            temp.delete()

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/octet-stream")
                .build()
            Http.client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Download failed: HTTP ${response.code}" }
                val body = response.body ?: error("The update response was empty.")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    FileOutputStream(temp).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = 0L
                        while (true) {
                            if (downloadCancelled) {
                                temp.delete()
                                _download.value = DownloadState.Idle
                                return@withContext
                            }
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (total > 0) {
                                _download.value = DownloadState.Downloading(
                                    (downloaded.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
                                )
                            }
                        }
                    }
                }
            }

            check(temp.exists() && temp.length() > 0L) { "The downloaded APK is empty." }
            validateApk(context, temp)
            if (target.exists()) target.delete()
            check(temp.renameTo(target)) { "Could not finalize the downloaded APK." }
            _download.value = DownloadState.Ready(target)
        }.onFailure { error ->
            _download.value = DownloadState.Failed(error.message ?: "Could not download the Pexpo update.")
        }
    }

    fun cancelDownload() { downloadCancelled = true }
    fun resetDownload() { _download.value = DownloadState.Idle }

    fun installApk(context: Context, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(
            Intent(Intent.ACTION_INSTALL_PACKAGE)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                .putExtra(Intent.EXTRA_RETURN_RESULT, true)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun validateApk(context: Context, file: File) {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(
                file.absolutePath,
                PackageManager.PackageInfoFlags.of(0L),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        } ?: error("The downloaded file is not a valid Android APK.")

        check(info.packageName == BuildConfig.APPLICATION_ID) {
            "The downloaded APK belongs to ${info.packageName}, not ${BuildConfig.APPLICATION_ID}."
        }
    }

    private fun installedVariant(context: Context): String {
        val splits = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).splitNames.orEmpty()
        }.getOrDefault(emptyArray())
        val joined = splits.joinToString(" ").lowercase()
        return when {
            joined.contains("arm64") || joined.contains("arm64_v8a") -> "arm64-v8a"
            joined.contains("armeabi") || joined.contains("armeabi_v7a") -> "armeabi-v7a"
            joined.contains("x86_64") -> "x86_64"
            else -> "universal"
        }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        fun key(version: String): List<Int> {
            val parts = version.split(".").map { it.toIntOrNull() ?: 0 }
            if (parts.size == 4 && parts[2] == 1) {
                return listOf(parts[0], parts[1], parts[3], 1)
            }
            return listOf(
                parts.getOrElse(0) { 0 },
                parts.getOrElse(1) { 0 },
                parts.getOrElse(2) { 0 },
                0,
            )
        }
        val l = key(latest)
        val c = key(current)
        for (i in l.indices) if (l[i] != c[i]) return l[i] > c[i]
        return false
    }
}
