package com.music.pexpo.auth

import android.webkit.CookieManager
import com.music.pexpo.data.DebugLog as Log
import java.util.concurrent.atomic.AtomicInteger

enum class WebSessionMode {
    SIGN_IN,
    SWITCH_CHANNEL,
}

data class CapturedSession(
    val cookie: String,
    val pageId: String?,
    val dataSyncId: String?,
    val authUser: String?,
    val visitorData: String?,
    val clientVersion: String?,
    val loggedIn: Boolean,
)

object BrowserSession {
    /** Expire Google/YouTube identity cookies before a fresh login transaction. */
    fun clearGoogleCookies(onComplete: () -> Unit = {}) {
        val manager = runCatching { CookieManager.getInstance() }.getOrElse {
            Log.w(TAG, "unable to obtain WebView CookieManager: ${it.message}")
            onComplete()
            return
        }

        val mutations = LinkedHashSet<String>()
        GOOGLE_ORIGINS.forEach { origin ->
            val host = origin.removePrefix("https://").substringBefore('/')
            val parent = host.substringAfter('.', "").takeIf { it.contains('.') }
            val discovered = manager.getCookie(origin).orEmpty()
                .split(';')
                .map { it.substringBefore('=').trim() }
                .filter { it.isNotEmpty() }

            (discovered + GOOGLE_AUTH_COOKIE_NAMES).distinct().forEach { name ->
                GOOGLE_PATHS.forEach { path ->
                    mutations += "$origin|$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=$path"
                    mutations += "$origin|$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=$path; Domain=$host"
                    if (parent != null) {
                        mutations += "$origin|$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=$path; Domain=.$parent"
                    }
                }
            }
        }

        if (mutations.isEmpty()) {
            runCatching { manager.flush() }
            onComplete()
            return
        }

        val remaining = AtomicInteger(mutations.size)
        mutations.forEach { mutation ->
            val origin = mutation.substringBefore('|')
            val cookie = mutation.substringAfter('|')
            manager.setCookie(origin, cookie) {
                if (remaining.decrementAndGet() == 0) {
                    runCatching { manager.flush() }
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        runCatching { manager.flush() }
                        onComplete()
                    }
                }
            }
        }
    }

    private const val TAG = "Pexpo"

    private val GOOGLE_ORIGINS = listOf(
        "https://music.youtube.com",
        "https://www.youtube.com",
        "https://youtube.com",
        "https://accounts.google.com",
        "https://www.google.com",
        "https://google.com",
    )

    private val GOOGLE_AUTH_COOKIE_NAMES = setOf(
        "SID", "SSID", "APISID", "SAPISID", "HSID", "LSID", "OSID",
        "__Secure-1PSID", "__Secure-3PSID", "__Secure-1PAPISID", "__Secure-3PAPISID",
        "__Secure-1PSIDTS", "__Secure-3PSIDTS", "__Secure-1PSIDCC", "__Secure-3PSIDCC",
        "__Secure-1PSIDUC", "__Secure-3PSIDUC", "__Secure-YEC", "__Host-GAPS", "GAPS",
        "AEC", "SOCS", "SIDCC", "LOGIN_INFO", "PREF", "YSC", "VISITOR_INFO1_LIVE",
        "NID", "1P_JAR", "OTZ", "ACCOUNT_CHOOSER", "GALX", "SMSV",
    )

    private val GOOGLE_PATHS = listOf(
        "/", "/ServiceLogin", "/ServiceLogin/", "/ServiceLoginAuth", "/ServiceLoginAuth/",
        "/signin", "/signin/", "/accounts", "/accounts/", "/youtubei/v1",
    )
}
