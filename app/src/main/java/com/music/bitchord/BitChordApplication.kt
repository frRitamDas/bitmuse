package com.music.bitchord

import android.app.Application
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
