package com.music.bitchord.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.music.bitchord.BuildConfig
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
            val request = Request.Builder().url(LATEST_RELEASE_URL).build()
            val body = Http.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            } ?: return@runCatching
            val release = json.parseToJsonElement(body) as? JsonObject ?: return@runCatching
            val tag = release["tag_name"]?.jsonPrimitive?.contentOrNull ?: return@runCatching
            val url = release["html_url"]?.jsonPrimitive?.contentOrNull ?: return@runCatching
            val apkUrl = apkAssetUrl(release, installedVariant(context))
            val notes = release["body"]?.jsonPrimitive?.contentOrNull
            val latest = tag.removePrefix("v")
            if (isNewer(latest, BuildConfig.VERSION_NAME)) _available.value = UpdateInfo(latest, url, apkUrl, notes)
        }
    }

    suspend fun clearCache(context: Context) = withContext(Dispatchers.IO) {
        File(context.cacheDir, CACHE_SUBDIR).listFiles()?.forEach { it.delete() }
    }

    private fun apkAssetUrl(release: JsonObject, variant: String): String? = runCatching {
        release["assets"]?.jsonArray?.mapNotNull { it as? JsonObject }?.filter { asset ->
            asset["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk", ignoreCase = true) == true &&
                asset["state"]?.jsonPrimitive?.contentOrNull == "uploaded"
        }?.sortedBy { asset ->
            val name = asset["name"]?.jsonPrimitive?.contentOrNull.orEmpty().lowercase()
            if (name.contains("-$variant.apk")) 0 else if (name.contains("-universal.apk")) 1 else 2
        }?.firstOrNull()?.get("browser_download_url")?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    suspend fun downloadApk(context: Context): Unit = withContext(Dispatchers.IO) {
        val info = _available.value ?: return@withContext
        val url = info.apkUrl ?: return@withContext
        downloadCancelled = false
        _download.value = DownloadState.Downloading(0f)
        runCatching {
            val dir = File(context.cacheDir, CACHE_SUBDIR).apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val target = File(dir, "pexpo-${info.version}.apk")
            val request = Request.Builder().url(url).build()
            Http.client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Download failed: HTTP ${response.code}" }
                val body = response.body ?: error("Empty download body")
                val total = body.contentLength().takeIf { it > 0 }
                body.byteStream().use { input -> target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var readTotal = 0L
                    while (true) {
                        if (downloadCancelled) { _download.value = DownloadState.Idle; return@withContext }
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        readTotal += read
                        total?.let { _download.value = DownloadState.Downloading((readTotal.toFloat() / it).coerceIn(0f, 1f)) }
                    }
                }}
            }
            _download.value = DownloadState.Ready(target)
        }.onFailure { error -> _download.value = if (downloadCancelled) DownloadState.Idle else DownloadState.Failed(error.message ?: "Download failed") }
    }

    fun cancelDownload() { downloadCancelled = true }
    fun resetDownload() { _download.value = DownloadState.Idle }

    fun installApk(context: Context, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).setData(Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri, "application/vnd.android.package-archive")
            .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true).putExtra(Intent.EXTRA_RETURN_RESULT, true)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun installedVariant(context: Context): String {
        val splits = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).splitNames.orEmpty() }.getOrDefault(emptyArray())
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
            // 1.5.1.4 is the major maintenance patch attached to 1.5.4.
            // Order it between 1.5.4 and 1.5.5.
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
        for (i in l.indices) {
            if (l[i] != c[i]) return l[i] > c[i]
        }
        return false
    }
}
