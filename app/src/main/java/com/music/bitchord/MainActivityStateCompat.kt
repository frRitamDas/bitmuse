package com.music.bitchord

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.music.bitchord.auth.WebSessionMode

/**
 * Compatibility state for the partially restored account-session UI in MainActivity.
 *
 * The current MainActivity still contains the older `showLogin` call sites while
 * the WebView block uses the newer WebSessionMode API. Keeping this bridge here
 * avoids duplicating two competing login states until that screen is consolidated.
 */
private val webSessionState = mutableStateOf<WebSessionMode?>(null)

var webSession: WebSessionMode?
    get() = webSessionState.value
    set(value) {
        webSessionState.value = value
        if (value == null) {
            captureRequest = 0
            captureFailed = false
        }
    }

/** Legacy alias used by the remaining sign-in call sites. */
var showLogin: Boolean
    get() = webSession != null
    set(value) {
        webSession = if (value) WebSessionMode.SIGN_IN else null
    }

/** Derived mode used by the restored WebView header. */
val mode: WebSessionMode
    get() = webSession ?: WebSessionMode.SIGN_IN

var captureRequest by mutableIntStateOf(0)
var captureFailed by mutableStateOf(false)
