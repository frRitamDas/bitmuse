package com.music.bitchord

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.data.canvas.CanvasCache
import com.music.bitchord.data.canvas.SpotifyToken
import com.music.bitchord.playback.AudioCache
import com.music.bitchord.playback.LastPlayed
import com.music.bitchord.playback.OriginalVersion
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.scrobbling.LastFM
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.SearchHistory
import com.music.bitchord.data.sources.SourceRegistry
import com.music.bitchord.data.stats.ArtistFacts
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.download.Downloads
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BitChordApplication : Application(), SingletonImageLoader.Factory {

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: android.os.Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) {
                if (isLegacyBuild()) showLegacyBuildGate(activity)
            }
        })
        authStore = AuthStore(this)
        val restoredSession = authStore.activeSession
        if (restoredSession != null && authStore.activeAccountId == null) {
            authStore.select(restoredSession.accountId, restoredSession.activeProfileId)
        }
        authStore.cookie = restoredSession?.cookie
        Innertube.cookie = restoredSession?.cookie
        if (authStore.cookie != null) {
            Innertube.selectChannel(authStore.channelPageId, authStore.channelDataSyncId)
            CoroutineScope(Dispatchers.IO).launch { Innertube.ensureSessionScope() }
        }
        AppSettings.init(this)
        SourceRegistry.init(this)
        SearchHistory.init(this)
        LastPlayed.init(this)
        OriginalVersion.init(this)
        Downloads.init(this)
        ListeningStats.init(this)
        ArtistFacts.init(this)
        AudioCache.init(this)
        CanvasCache.init(this)
        SpotifyToken.init(this)
        if (AppSettings.consumeVersionUpdate(BuildConfig.VERSION_CODE)) {
            AudioCache.clear()
            SingletonImageLoader.get(this).let { loader ->
                loader.memoryCache?.clear()
                loader.diskCache?.clear()
            }
        }
        initLastfm()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .crossfade(200)
            .build()

    /** Versions below 1.5.1.4 are permanently blocked from normal app use. */
    private fun isLegacyBuild(): Boolean {
        fun key(version: String): List<Int> {
            val parts = version.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
            // Pexpo's four-part 1.5.1.4 line is ordered between 1.5.4 and 1.5.5.
            if (parts.size == 4 && parts[2] == 1) {
                return listOf(parts[0], parts[1], parts[3], 1)
            }
            return listOf(parts.getOrElse(0) { 0 }, parts.getOrElse(1) { 0 }, parts.getOrElse(2) { 0 }, 0)
        }
        val current = key(BuildConfig.VERSION_NAME)
        val minimum = key("1.5.1.4")
        return current.zip(minimum).firstOrNull { (a, b) -> a != b }?.let { it.first < it.second } == true
    }

    /** Non-dismissible update gate: unsupported builds cannot continue into the app. */
    private fun showLegacyBuildGate(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (activity is androidx.appcompat.app.AppCompatActivity && activity.isChangingConfigurations) return
        AlertDialog.Builder(activity)
            .setTitle("Pexpo has been shut down")
            .setMessage("This version of Pexpo is no longer supported. Download Pexpo 1.5.1.4 to continue.")
            .setCancelable(false)
            .setPositiveButton("Download Pexpo 1.5.1.4") { _, _ ->
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://pexpoupdates.xo.je")))
                activity.finishAndRemoveTask()
            }
            .show()
    }

    private fun initLastfm() {
        val sessionKey = AppSettings.lastfmSessionKey.value
        if (sessionKey.isBlank()) return
        val endpoint = AppSettings.lastfmEndpoint.value.ifBlank { LastFM.DEFAULT_API_ENDPOINT }
        val apiKey = AppSettings.lastfmApiKey.value.trim()
        val secret = AppSettings.lastfmSecret.value.trim()
        if (apiKey.isBlank() || secret.isBlank()) return
        LastFM.configure(
            endpoint = endpoint,
            apiKey = apiKey,
            secret = secret,
            sessionKey = sessionKey,
        )
    }

    companion object {
        lateinit var authStore: AuthStore
            private set
    }
}
