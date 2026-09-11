package com.music.pexpo.auth

import android.content.Context
import android.content.SharedPreferences
import com.music.pexpo.data.DebugLog as Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class AuthStore(context: Context) {
    private val prefs: SharedPreferences = runCatching {
        EncryptedSharedPreferences.create(
            context,
            "pexpo_auth",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse {
        Log.w("Pexpo", "EncryptedSharedPreferences unavailable, falling back: ${it.message}")
        context.getSharedPreferences("pexpo_auth_plain", Context.MODE_PRIVATE)
    }

    var cookie: String?
        get() = prefs.getString(KEY_COOKIE, null)
        set(value) = prefs.edit().putString(KEY_COOKIE, value).apply()

    var sessions: List<GoogleAccountSession>
        get() {
            val saved = sessionsFromJson(prefs.getString(KEY_SESSIONS, null))
            if (saved.isNotEmpty()) {
                // Pexpo intentionally supports exactly one Google account. If an
                // older multi-account build left several sessions behind, keep
                // only the currently active one so the old state cannot leak
                // back into the account selector.
                val active = saved.firstOrNull { it.accountId == activeAccountId } ?: saved.first()
                if (saved.size != 1 || saved.first().accountId != active.accountId) {
                    replaceSessions(listOf(active))
                    activeAccountId = active.accountId
                    activeProfileId = active.activeProfileId
                }
                return listOf(active)
            }
            val legacy = cookie ?: return emptyList()
            val profile = YouTubeProfile(
                profileId = profileId(channelPageId, channelDataSyncId, channelName ?: "Personal"),
                name = channelName ?: "Personal",
                pageId = channelPageId,
                dataSyncId = channelDataSyncId,
                authUser = channelAuthUser,
                isBrandAccount = channelPageId != null,
            )
            return listOf(GoogleAccountSession(
                accountId = sessionId(legacy, channelDataSyncId),
                cookie = legacy,
                profiles = listOf(profile),
                activeProfileId = profile.profileId,
            )).also { replaceSessions(it) }
        }
        set(value) {
            // Hard single-account invariant. This also makes the limit durable
            // even if a caller from an older multi-account path writes a list.
            val only = value.lastOrNull()
            replaceSessions(only?.let(::listOf) ?: emptyList())
            if (only == null) {
                prefs.edit().remove(KEY_ACTIVE_ACCOUNT).remove(KEY_ACTIVE_PROFILE).apply()
            } else {
                activeAccountId = only.accountId
                activeProfileId = only.activeProfileId
            }
        }

    var activeAccountId: String?
        get() = prefs.getString(KEY_ACTIVE_ACCOUNT, null)
        set(value) = prefs.edit().putString(KEY_ACTIVE_ACCOUNT, value).apply()

    var activeProfileId: String?
        get() = prefs.getString(KEY_ACTIVE_PROFILE, null)
        set(value) = prefs.edit().putString(KEY_ACTIVE_PROFILE, value).apply()

    val activeSession: GoogleAccountSession?
        get() = sessions.firstOrNull { it.accountId == activeAccountId } ?: sessions.firstOrNull()

    fun replaceSessions(value: List<GoogleAccountSession>) =
        prefs.edit().putString(KEY_SESSIONS, value.toJson()).apply()

    fun upsertSession(session: GoogleAccountSession, activate: Boolean = true) {
        // Signing in always establishes the one Pexpo account. A newly captured
        // Google session replaces any stale session instead of creating a second.
        replaceSessions(listOf(session))
        if (activate) select(session.accountId, session.activeProfileId)
    }

    fun select(accountId: String, profileId: String?) {
        if (sessions.none { it.accountId == accountId }) return
        activeAccountId = accountId
        activeProfileId = profileId
    }

    fun removeAccount(accountId: String): GoogleAccountSession? {
        val remaining = sessions.filterNot { it.accountId == accountId }
        replaceSessions(remaining)
        val fallback = remaining.firstOrNull()
        if (fallback == null) {
            prefs.edit()
                .remove(KEY_ACTIVE_ACCOUNT)
                .remove(KEY_ACTIVE_PROFILE)
                .remove(KEY_COOKIE)
                .apply()
        } else {
            activeAccountId = fallback.accountId
            activeProfileId = fallback.activeProfileId
            prefs.edit().putString(KEY_COOKIE, fallback.cookie).apply()
        }
        return fallback
    }

    val isSignedIn: Boolean
        get() = activeSession?.cookie?.let { hasApiSid(it) } == true

    var discordToken: String?
        get() = prefs.getString(KEY_DISCORD_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_DISCORD_TOKEN, value).apply()

    val channelPageId: String? get() = prefs.getString(KEY_CHANNEL_PAGE_ID, null)
    val channelDataSyncId: String? get() = prefs.getString(KEY_CHANNEL_DATASYNC_ID, null)
    val channelName: String? get() = prefs.getString(KEY_CHANNEL_NAME, null)
    val channelAuthUser: String? get() = prefs.getString(KEY_CHANNEL_AUTH_USER, null)

    fun selectChannel(
        pageId: String?,
        dataSyncId: String?,
        name: String?,
        authUser: String? = null,
    ) = prefs.edit()
        .putString(KEY_CHANNEL_PAGE_ID, pageId)
        .putString(KEY_CHANNEL_DATASYNC_ID, dataSyncId)
        .putString(KEY_CHANNEL_NAME, name)
        .putString(KEY_CHANNEL_AUTH_USER, authUser)
        .apply()

    fun setChannelName(name: String?) =
        prefs.edit().putString(KEY_CHANNEL_NAME, name).apply()

    fun clearChannel() = prefs.edit()
        .remove(KEY_CHANNEL_PAGE_ID)
        .remove(KEY_CHANNEL_DATASYNC_ID)
        .remove(KEY_CHANNEL_NAME)
        .remove(KEY_CHANNEL_AUTH_USER)
        .apply()

    fun onNewSession(cookie: String) {
        this.cookie = cookie
        clearChannel()
    }

    fun signOut() {
        prefs.edit().remove(KEY_COOKIE).remove(KEY_SESSIONS).remove(KEY_ACTIVE_ACCOUNT).remove(KEY_ACTIVE_PROFILE).apply()
        clearChannel()
        BrowserSession.clearGoogleCookies()
    }

    companion object {
        fun hasApiSid(cookieHeader: String): Boolean = cookieHeader.split(';').any { entry ->
            val name = entry.substringBefore('=').trim()
            val value = entry.substringAfter('=', "").trim()
            name in API_SID_NAMES && value.isNotEmpty()
        }

        private val API_SID_NAMES = setOf("SAPISID", "__Secure-3PAPISID", "__Secure-1PAPISID")
        private const val KEY_COOKIE = "cookie"
        private const val KEY_SESSIONS = "google_account_sessions_v2"
        private const val KEY_ACTIVE_ACCOUNT = "active_google_account_id_v2"
        private const val KEY_ACTIVE_PROFILE = "active_youtube_profile_id_v2"
        private const val KEY_CHANNEL_PAGE_ID = "channel_page_id"
        private const val KEY_CHANNEL_DATASYNC_ID = "channel_datasync_id"
        private const val KEY_CHANNEL_NAME = "channel_name"
        private const val KEY_CHANNEL_AUTH_USER = "channel_auth_user"
        private const val KEY_DISCORD_TOKEN = "discord_token"
    }
}
