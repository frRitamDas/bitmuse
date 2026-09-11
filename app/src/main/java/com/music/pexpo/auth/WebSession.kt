package com.music.pexpo.auth

import android.webkit.CookieManager
import com.music.pexpo.data.DebugLog as Log
import java.util.concurrent.atomic.AtomicInteger

/** What the in-app browser is being opened for. */
enum class WebSessionMode {
    /** No usable session yet: sign in to Google. */
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
    /** Whether the page reported itself signed in at all. */
    val loggedIn: Boolean,
)

/**
 * The WebView's own cookie jar, which is not Pexpo's persisted account store.
 *
 * Google authentication is the only state cleared here. Pexpo's durable
 * multi-account sessions remain in AuthStore, so Add Account can replace the
 * temporary browser identity without deleting any stored Pexpo account.
 */
object BrowserSession {

    /**
     * Clears Google/YouTube cookies and invokes [onComplete] only after every
     * asynchronous cookie-expiration operation has completed and the jar has
     * been flushed.
     *
     * The previous implementation started navigation immediately after
     * `setCookie()`. Cookie writes are asynchronous, so Google could process
     * the login URL while the previous account was still present and redirect
     * directly to YouTube Music. Waiting for all callbacks removes that race.
     *
     * We intentionally do not call `removeAllCookies()`: the WebView cookie jar
     * is shared and clearing it wholesale would also affect Discord/Last.fm.
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
                    add(origin to "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/")
                    add(origin to "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Domain=$host")
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
