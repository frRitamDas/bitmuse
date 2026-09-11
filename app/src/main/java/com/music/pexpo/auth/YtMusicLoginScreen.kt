package com.music.pexpo.auth

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.music.pexpo.data.DebugLog as Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

private const val MUSIC_ORIGIN = "https://music.youtube.com"
private const val LOGIN_URL = "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&passive=true&continue=https%3A%2F%2Fmusic.youtube.com%2F"
private const val TAG = "Pexpo"

/**
 * In-app Google sign-in for YouTube Music, and the way to change which channel
 * it listens as.
 *
 * SIGN_IN is a fresh authentication flow: Google/YouTube WebView cookies are
 * cleared completely for the relevant Google hosts before LOGIN_URL is loaded.
 * SWITCH_CHANNEL intentionally keeps the current browser session and opens
 * YouTube Music so its own Accounts switcher can be used.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YtMusicLoginScreen(
    mode: WebSessionMode,
    onCaptured: (CapturedSession) -> Unit,
    modifier: Modifier = Modifier,
    /** Raise to take the session from the page as it stands. */
    captureRequest: Int = 0,
    /** Told when a capture was asked for and there was no session to take. */
    onCaptureUnavailable: () -> Unit = {},
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    val currentOnCaptured by rememberUpdatedState(onCaptured)
    val currentOnUnavailable by rememberUpdatedState(onCaptureUnavailable)

    LaunchedEffect(captureRequest) {
        if (captureRequest == 0) return@LaunchedEffect
        val view = webView
        if (view == null || !captureFrom(view, currentOnCaptured)) currentOnUnavailable()
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    private var captured = false

                    override fun onPageFinished(view: WebView?, url: String?) {
                        // Only SIGN_IN finishes by itself. SWITCH_CHANNEL starts
                        // on YouTube Music and must never auto-capture its first
                        // page, because that would defeat channel switching.
                        if (mode != WebSessionMode.SIGN_IN) return
                        if (captured || url?.startsWith(MUSIC_ORIGIN) != true) return
                        if (view != null && captureFrom(view, currentOnCaptured)) captured = true
                    }
                }

                webView = this
                if (mode == WebSessionMode.SIGN_IN) {
                    // CookieManager.setCookie() is asynchronous. Wait for its
                    // completion before the first Google navigation; otherwise
                    // the previous Google account can win the redirect race.
                    BrowserSession.clearGoogleCookies {
                        post {
                            stopLoading()
                            clearHistory()
                            loadUrl(LOGIN_URL)
                        }
                    }
                } else {
                    loadUrl("$MUSIC_ORIGIN/")
                }
            }
        },
    )
}

/** Takes the session from [view], if it is holding one. */
private fun captureFrom(view: WebView, onCaptured: (CapturedSession) -> Unit): Boolean {
    val cookies = CookieManager.getInstance().getCookie(MUSIC_ORIGIN)
    if (cookies == null || !AuthStore.hasApiSid(cookies)) return false
    CookieManager.getInstance().flush()

    view.evaluateJavascript(YTCFG_PROBE) { raw ->
        val config = raw.parseConfig()
        if (config == null) {
            Log.w(TAG, "no ytcfg on the page; falling back to the shell for identity")
        }
        onCaptured(
            CapturedSession(
                cookie = cookies,
                dataSyncId = config?.string("dataSyncId")?.substringBefore("||"),
                pageId = config?.string("pageId"),
                authUser = config?.string("authUser"),
                visitorData = config?.string("visitorData"),
                clientVersion = config?.string("clientVersion"),
                loggedIn = config?.get("loggedIn").let { it is JsonPrimitive && it.content == "true" },
            ),
        )
    }
    return true
}

/** Probe the live YouTube page for the identity it is currently serving. */
private const val YTCFG_PROBE = """
(function () {
  try {
    if (!window.ytcfg || !window.ytcfg.get) return null;
    var get = function (key) {
      var value = window.ytcfg.get(key);
      return (value === undefined || value === null || value === '') ? null : String(value);
    };
    return {
      loggedIn: String(!!window.ytcfg.get('LOGGED_IN')),
      pageId: get('DELEGATED_SESSION_ID'),
      dataSyncId: get('DATASYNC_ID'),
      authUser: get('SESSION_INDEX'),
      visitorData: get('VISITOR_DATA'),
      clientVersion: get('INNERTUBE_CLIENT_VERSION')
    };
  } catch (e) {
    return null;
  }
})()
"""

private val json = Json { ignoreUnknownKeys = true }

private fun String?.parseConfig(): JsonObject? =
    this?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
