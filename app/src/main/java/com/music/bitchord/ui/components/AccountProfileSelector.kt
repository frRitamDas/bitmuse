package com.music.bitchord.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.auth.GoogleAccountSession
import com.music.bitchord.auth.YouTubeProfile
import com.music.bitchord.auth.profileId
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.AccountChannel
import com.music.bitchord.data.settings.AppSettings
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/** Shared compact selector for every visible YouTube account avatar. */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun AccountProfileSelector(
    accounts: List<GoogleAccountSession>,
    activeAccountId: String?,
    activeProfileId: String?,
    hazeState: HazeState,
    onSelect: (GoogleAccountSession, YouTubeProfile) -> Unit,
    onAddAccount: () -> Unit,
    onRemoveAccount: (GoogleAccountSession) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var managing by remember { mutableStateOf(false) }
    var displayAccounts by remember(accounts) { mutableStateOf(accounts) }
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val shape = MaterialTheme.shapes.extraLarge

    // Refresh the first-party account switcher data when this popup is opened.
    // Older sessions may have been persisted before their YouTube avatar was
    // discovered. The account switcher is the authoritative source for the
    // current profile photo, so merge its artwork into the visible session
    // without disturbing any account/profile ids or selection state.
    LaunchedEffect(accounts) {
        displayAccounts = accounts
        val channels = YtMusicRepository.accountChannels().getOrNull().orEmpty()
        if (channels.isEmpty()) return@LaunchedEffect

        val byIdentity = channels.associateBy { it.key }
        displayAccounts = accounts.map { account ->
            account.copy(
                profiles = account.profiles.map { profile ->
                    val identity = profile.pageId ?: profile.dataSyncId ?: profile.name
                    val channel = byIdentity[identity]
                        ?: channels.firstOrNull {
                            profileId(it.pageId, it.dataSyncId, it.name) == profile.profileId ||
                                it.pageId == profile.pageId && it.dataSyncId == profile.dataSyncId ||
                                it.name == profile.name
                        }
                    channel?.let { profile.mergeAccountChannel(it) } ?: profile
                },
            )
        }
    }

    Column(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = .48f))
            .clickable(onClick = onDismiss),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = shape,
            modifier = Modifier
                .padding(top = 56.dp, start = 20.dp, end = 20.dp)
                .fillMaxWidth()
                .clip(shape)
                .then(
                    if (reduceDynamicBlur) {
                        Modifier.background(MaterialTheme.colorScheme.surface)
                    } else {
                        Modifier.optimizedHazeEffect(
                            state = hazeState,
                            style = HazeMaterials.thin(MaterialTheme.colorScheme.surface),
                        )
                    },
                )
                .clickable(onClick = {}),
        ) {
            LazyColumn {
                item {
                    Text(
                        stringResource(R.string.switch_account),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(20.dp, 18.dp, 20.dp, 8.dp),
                    )
                }
                displayAccounts.forEach { account ->
                    item {
                        Text(
                            account.email.ifBlank { account.name.ifBlank { stringResource(R.string.accounts) } },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp, 12.dp, 20.dp, 4.dp),
                        )
                    }
                    items(account.profiles.size) { index ->
                        val profile = account.profiles[index]
                        ProfileRow(
                            profile,
                            account.accountId == activeAccountId && profile.profileId == activeProfileId,
                            managing,
                            { onSelect(account, profile); onDismiss() },
                            { onRemoveAccount(account) },
                        )
                    }
                }
                item {
                    DisabledSelectorAction(
                        icon = Icons.Rounded.Add,
                        label = stringResource(R.string.add_account),
                    )
                }
                item {
                    SelectorAction(Icons.Rounded.ManageAccounts, stringResource(R.string.manage_accounts)) {
                        managing = !managing
                    }
                }
                item { SelectorAction(Icons.Rounded.Settings, stringResource(R.string.settings), onOpenSettings) }
            }
        }
    }
}

private fun YouTubeProfile.mergeAccountChannel(channel: AccountChannel): YouTubeProfile = copy(
    name = channel.name.ifBlank { name },
    handle = channel.subtitle.ifBlank { handle },
    avatar = channel.thumbnailUrl ?: avatar,
    pageId = channel.pageId ?: pageId,
    dataSyncId = channel.dataSyncId ?: dataSyncId,
)

@Composable
private fun ProfileRow(
    profile: YouTubeProfile,
    selected: Boolean,
    managing: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val description = stringResource(if (selected) R.string.selected_account else R.string.switch_account)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clickable(role = Role.RadioButton, onClick = if (managing) onRemove else onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val photo = profile.avatar?.takeIf { it.isNotBlank() }
            ?: profile.pageId?.takeIf { it.isNotBlank() }?.let { "https://www.youtube.com/channel/$it/picture" }
        if (photo != null) {
            AsyncImage(
                model = photo,
                contentDescription = description,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(38.dp).clip(MaterialTheme.shapes.extraLarge),
            )
        } else {
            Icon(
                Icons.Rounded.Person,
                null,
                Modifier.size(38.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(profile.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                profile.handle.ifBlank {
                    stringResource(if (profile.isBrandAccount) R.string.brand_account else R.string.personal)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected && !managing) {
            Icon(
                Icons.Rounded.Check,
                stringResource(R.string.selected_account),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (managing) Text(stringResource(R.string.sign_out), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun DisabledSelectorAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.blur(1.6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
            Spacer(Modifier.width(16.dp))
            Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
        }
        Spacer(Modifier.weight(1f))
        Text(
            "Temporarily Disabled",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .72f),
            maxLines = 1,
        )
    }
}

@Composable
private fun SelectorAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(icon, null)
        Spacer(Modifier.width(16.dp))
        Text(label)
    }
}
