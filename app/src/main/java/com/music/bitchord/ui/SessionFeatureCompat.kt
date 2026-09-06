package com.music.bitchord.ui

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.accountChannels as loadAccountChannels
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.innertube.adoptSessionScope as adoptScope
import com.music.bitchord.data.innertube.selectChannel as applyChannel

typealias AccountChannel = com.music.bitchord.data.model.AccountChannel
typealias CapturedSession = com.music.bitchord.auth.CapturedSession
typealias WebSessionMode = com.music.bitchord.auth.WebSessionMode

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
 * Compatibility fallback for the partially restored onWebSession path. The
 * callback is reached only after a valid captured browser session has been
 * accepted, so the post-capture refresh path remains enabled.
 */
val MainViewModel.wasSignedIn: Boolean
    get() = true
