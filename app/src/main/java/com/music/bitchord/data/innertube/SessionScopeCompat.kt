package com.music.bitchord.data.innertube

/**
 * Restores the profile-selection hooks removed by the partial brand-channel
 * revert. The main Innertube client still owns the actual request pipeline;
 * these helpers provide the missing override values without replacing later
 * playback/client fixes in Innertube.kt.
 */
internal data class ChannelSelectionCompat(
    val pageId: String?,
    val dataSyncId: String?,
    val authUser: String?,
)

private var selectedChannel: ChannelSelectionCompat? = null
private var selectedCookie: String? = null

/** Referenced by the existing Innertube implementation. */
internal var channelOverride: ChannelSelectionCompat?
    get() = selectedChannel?.takeIf { selectedCookie == Innertube.cookie }
    set(value) {
        selectedChannel = value
        selectedCookie = Innertube.cookie
    }

fun Innertube.selectChannel(pageId: String?, dataSyncId: String?, authUser: String? = null) {
    channelOverride = if (pageId == null && dataSyncId == null) null
    else ChannelSelectionCompat(pageId, dataSyncId, authUser)
}

/** The captured browser identity is represented by the durable channel override. */
fun Innertube.adoptSessionScope(
    pageId: String?,
    dataSyncId: String?,
    authUser: String?,
    visitorData: String?,
    clientVersion: String?,
    loggedIn: Boolean,
) {
    if (loggedIn) selectChannel(pageId, dataSyncId, authUser) else selectChannel(null, null)
}

private fun field(session: Any?, name: String): String? = runCatching {
    val f = session?.javaClass?.getDeclaredField(name) ?: return null
    f.isAccessible = true
    f.get(session) as? String
}.getOrNull()

/** Referenced by the existing Innertube implementation. */
internal fun pageIdFor(session: Any?): String? =
    channelOverride?.pageId ?: field(session, "pageId")

/** Referenced by the existing Innertube implementation. */
internal fun dataSyncIdFor(session: Any?): String? =
    channelOverride?.dataSyncId ?: field(session, "dataSyncId")

/** Referenced by the existing Innertube implementation. */
internal fun authUserFor(session: Any?): String =
    channelOverride?.authUser ?: field(session, "authUser") ?: "0"
