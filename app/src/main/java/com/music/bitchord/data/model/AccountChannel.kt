package com.music.bitchord.data.model

/**
 * One YouTube identity the signed-in Google session can act as.
 *
 * A personal channel has no delegated page id; a brand channel normally has
 * both a page id and a data-sync id supplied by YouTube's account switcher.
 */
data class AccountChannel(
    val name: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val pageId: String?,
    val dataSyncId: String?,
    val activeOnWeb: Boolean,
) {
    val key: String get() = pageId ?: dataSyncId ?: name
}
