package com.music.bitchord.data

import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.model.AccountChannel
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Compatibility layer for the account-switcher API lost during the partial revert. */
suspend fun YtMusicRepository.accountChannels(): Result<List<AccountChannel>> = runCatching {
    val first = runCatching { InnertubeParser.parseAccountChannelsCompat(Innertube.accountsList()) }
        .getOrDefault(emptyList())
    if (first.isNotEmpty()) first
    else InnertubeParser.parseAccountChannelsCompat(Innertube.accountSwitcher())
}

fun InnertubeParser.parseAccountChannelsCompat(root: JsonElement): List<AccountChannel> =
    accountItems(root).mapNotNull { item ->
        val name = item.stringAt("accountName").orEmpty()
        if (name.isBlank()) return@mapNotNull null
        val pageId = item.stringAt("pageId")
        val dataSyncId = item.stringAt("datasyncIdToken")?.substringBefore("||")?.takeIf { it.isNotBlank() }
        if (pageId == null && dataSyncId == null) return@mapNotNull null
        AccountChannel(
            name = name,
            subtitle = item.stringAt("channelHandle") ?: item.stringAt("accountByline").orEmpty(),
            thumbnailUrl = findThumbnail(item),
            pageId = pageId,
            dataSyncId = dataSyncId,
            activeOnWeb = item.booleanAt("isSelected"),
        )
    }.distinctBy { it.key }

private fun accountItems(root: JsonElement): List<JsonObject> = buildList {
    fun walk(node: JsonElement) {
        when (node) {
            is JsonObject -> {
                node["accountItem"]?.let { value ->
                    when (value) {
                        is JsonObject -> add(value)
                        is JsonArray -> value.forEach { if (it is JsonObject) add(it) }
                        else -> Unit
                    }
                }
                node.values.forEach(::walk)
            }
            is JsonArray -> node.forEach(::walk)
            else -> Unit
        }
    }
    walk(root)
}

private fun JsonObject.stringAt(key: String): String? = run {
    val direct = (this[key] as? JsonPrimitive)?.contentOrNull
    if (!direct.isNullOrBlank()) return@run direct
    fun walk(node: JsonElement): String? = when (node) {
        is JsonObject -> {
            (node[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: node.values.firstNotNullOfOrNull(::walk)
        }
        is JsonArray -> node.firstNotNullOfOrNull(::walk)
        else -> null
    }
    walk(this)
}

private fun JsonObject.booleanAt(key: String): Boolean = stringAt(key) == "true"

private fun findThumbnail(item: JsonObject): String? {
    fun walk(node: JsonElement): String? = when (node) {
        is JsonObject -> {
            val key = node["url"] as? JsonPrimitive
            key?.contentOrNull?.takeIf { it.startsWith("http") }
                ?: node.values.firstNotNullOfOrNull(::walk)
        }
        is JsonArray -> node.firstNotNullOfOrNull(::walk)
        else -> null
    }
    return walk(item["accountPhoto"] ?: item["thumbnail"] ?: item)
}
