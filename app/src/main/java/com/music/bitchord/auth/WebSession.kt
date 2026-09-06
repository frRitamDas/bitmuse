package com.music.bitchord.auth

import android.webkit.CookieManager
import com.music.bitchord.data.DebugLog as Log

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
    fun clearGoogleCookies() {
        val manager = runCatching { CookieManager.getInstance() }.getOrElse {
            Log.w("BitChord", "no cookie manager to clear: ${it.message}")
            return
        }
        var cleared = 0
        GOOGLE_ORIGINS.forEach { origin ->
            val jar = manager.getCookie(origin) ?: return@forEach
            val host = origin.substringAfter("://")
            jar.split(';').forEach { entry ->
                val name = entry.substringBefore('=').trim()
                if (name.isEmpty()) return@forEach
                manager.setCookie(origin, "$name=; Max-Age=0; Path=/")
                manager.setCookie(origin, "$name=; Max-Age=0; Path=/; Domain=$host")
                manager.setCookie(origin, "$name=; Max-Age=0; Path=/; Domain=.$host")
                cleared++
            }
        }
        runCatching { manager.flush() }
        Log.d("BitChord", "cleared $cleared browser cookies for Google")
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
