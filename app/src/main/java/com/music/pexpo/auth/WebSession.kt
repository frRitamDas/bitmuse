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
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** What the in-app browser is being opened for. */
enum class WebSessionMode {
    /** No usable Pexpo session yet: start a completely fresh Google sign-in. */
    SIGN_IN,

    /**
     * Already signed in, but on the wrong channel. Opens YouTube Music itself
     * so its own Accounts switcher can be used, and takes the session from
     * whatever page the listener ends up on.
     */
    SWITCH_CHANNEL,
}

/** A session lifted out of the in-app browser. */
data class CapturedSession(
    val cookie: String,
    /** `DELEGATED_SESSION_ID` — set only while a brand channel is selected. */
    val pageId: String?,
    /** `DATASYNC_ID`, account half only. */
    val dataSyncId: String?,
    /** `SESSION_INDEX` — which Google account in the cookie jar. */
    val authUser: String?,
    val visitorData: String?,
    val clientVersion: String?,
    /** Whether the page reported signed in. */
    val loggedIn: Boolean,
)

/**
 * Controls the WebView's temporary Google cookie state.
 *
 * Pexpo's durable multi-account sessions live in [AuthStore]. This browser jar
 * is only an authentication surface. In particular, clearing it for Add
 * Account must never remove an account already stored by Pexpo.
 */
object BrowserSession {

    /**
     * Clears Google/YouTube cookies and invokes [onComplete] only after every
     * asynchronous cookie-expiration operation has completed and the jar has
     * been flushed.
     *
     * The previous implementation called `setCookie()` and immediately
     * navigated to Google. `setCookie()` is asynchronous, so the old Google
     * session could still be present when Google processed the navigation and
     * redirected straight back to YouTube Music. That race is the main cause of
     * the reported "signed out but Home Sign in opens YouTube Music" bug.
     *
     * We deliberately do not call `removeAllCookies()`, because the WebView
     * cookie jar is shared and Pexpo must not sign the user out of Discord or
     * Last.fm when refreshing only Google authentication.
     */
    fun clearGoogleCookies(onComplete: () -> Unit = {}) {
        val manager = runCatching { CookieManager.getInstance() }.getOrElse {
            Log.w("Pexpo", "no cookie manager to clear: ${it.message}")
            onComplete()
            return
        }

        val expirations = buildList {
            GOOGLE_ORIGINS.forEach { origin ->
                val jar = manager.getCookie(origin) ?: return@forEach
                val host = origin.removePrefix("https://").substringBefore('/')
                val parent = host.substringAfter('.', "").takeIf { it.contains('.') }
                jar.split(';').forEach { entry ->
                    val name = entry.substringBefore('=').trim()
                    if (name.isEmpty()) return@forEach
                    // Host-only cookie.
                    add(origin to "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/")
                    // Host-scoped domain form.
                    add(origin to "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Domain=$host")
                    // Parent domain form, e.g. .youtube.com for
                    // music.youtube.com and .google.com for accounts.google.com.
                    if (parent != null) {
                        add(origin to "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Domain=.$parent")
                    }
                }
            }
        }

        if (expirations.isEmpty()) {
            runCatching { manager.flush() }
            onComplete()
            return
        }

        val remaining = AtomicInteger(expirations.size)
        expirations.forEach { (origin, cookie) ->
            manager.setCookie(origin, cookie) {
                if (remaining.decrementAndGet() == 0) {
                    runCatching { manager.flush() }
                    onComplete()
                }
            }
        }
    }

    private val GOOGLE_ORIGINS = listOf(
        "https://music.youtube.com",
        "https://www.youtube.com",
        "https://youtube.com",
        "https://accounts.google.com",
        "https://www.google.com",
        "https://google.com",
    )
}

/**
 * In-app Google sign-in for YouTube Music, and the way to change which channel
 * it listens as.
 *
 * SIGN_IN always waits for the Google browser cleanup to finish before the
 * first navigation. Therefore Home → Sign in and Account → Add account both
 * begin at Google's real "Sign in to YouTube Music" form instead of reusing a
 * previous Google account from the WebView.
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
                        if (mode != WebSessionMode.SIGN_IN) return
                        if (captured || url?.startsWith(MUSIC_ORIGIN) != true) return
                        if (view != null && captureFrom(view, currentOnCaptured)) captured = true
                    }
                }

                webView = this
                if (mode == WebSessionMode.SIGN_IN) {
                    // Critical: don't call loadUrl until the asynchronous
                    // CookieManager operations have completed.
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

/** Takes the authenticated session from [view], if one is present. */
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

private const val MUSIC_ORIGIN = "https://music.youtube.com"
private const val LOGIN_URL = "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&passive=true&continue=https%3A%2F%2Fmusic.youtube.com%2F"
private const val TAG = "Pexpo"

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
