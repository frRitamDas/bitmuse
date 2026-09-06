package com.music.bitchord.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Backward-compatible overload for callers that still provide only a cookie
 * callback. New code should use the CapturedSession overload in YtMusicLoginScreen.
 */
@Composable
fun YtMusicLoginScreen(
    onCookiesCaptured: (cookieHeader: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    YtMusicLoginScreen(
        mode = WebSessionMode.SIGN_IN,
        onCaptured = { session -> onCookiesCaptured(session.cookie) },
        modifier = modifier,
    )
}
