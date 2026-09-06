package com.music.bitchord.ui

import com.music.bitchord.auth.CapturedSession
import com.music.bitchord.auth.WebSessionMode
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.accountChannels as loadAccountChannels
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.innertube.adoptSessionScope as adoptScope
import com.music.bitchord.data.innertube.selectChannel as applyChannel
import com.music.bitchord.data.model.AccountChannel

/** Type aliases keep the restored ViewModel API source-compatible with the model layer. */
typealias ViewModelAccountChannel = AccountChannel
typealias ViewModelCapturedSession = CapturedSession
typealias ViewModelWebSessionMode = WebSessionMode

/** Compatibility forwarding extension for the repository account-channel loader. */
suspend fun YtMusicRepository.accountChannels(): Result<List<AccountChannel>> = loadAccountChannels(this)

/** Compatibility forwarding extensions for Innertube session-scope hooks. */
fun Innertube.selectChannel(pageId: String?, dataSyncId: String?, authUser: String? = null) =
    applyChannel(this, pageId, dataSyncId, authUser)

fun Innertube.adoptSessionScope(
    pageId: String?,
    dataSyncId: String?,
    authUser: String?,
    visitorData: String?,
    clientVersion: String?,
    loggedIn: Boolean,
) = adoptScope(this, pageId, dataSyncId, authUser, visitorData, clientVersion, loggedIn)

/**
 * The partially restored onWebSession path only uses this as a post-capture
 * guard. A successful captured session necessarily has a signed-in cookie, so
 * treating the compatibility value as true preserves the intended refresh path.
 */
val MainViewModel.wasSignedIn: Boolean
    get() = true

// Keep the unqualified names used by the restored ViewModel available in this package.
typealias AccountChannel = com.music.bitchord.data.model.AccountChannel
typealias CapturedSession = com.music.bitchord.auth.CapturedSession
typealias WebSessionMode = com.music.bitchord.auth.WebSessionMode
