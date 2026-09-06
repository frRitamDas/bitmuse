from pathlib import Path

ROOT = Path('.')


def add_import(text: str, imp: str) -> str:
    if imp in text:
        return text
    package_end = text.find('\n', text.find('package ')) + 1
    return text[:package_end] + imp + '\n' + text[package_end:]


def patch_branding() -> None:
    for path in ROOT.glob('app/src/main/res/**/strings.xml'):
        text = path.read_text(encoding='utf-8')
        new = text.replace('BitChord', 'Pexpo')
        if new != text:
            path.write_text(new, encoding='utf-8')

    for path in ROOT.glob('app/src/main/java/**/*.kt'):
        text = path.read_text(encoding='utf-8')
        new = text.replace('Music/BitChord', 'Music/Pexpo')
        if new != text:
            path.write_text(new, encoding='utf-8')

    download_store = ROOT / 'app/src/main/java/com/music/bitchord/download/DownloadStore.kt'
    if download_store.exists():
        text = download_store.read_text(encoding='utf-8')
        new = text.replace('const val FOLDER = "BitChord"', 'const val FOLDER = "Pexpo"')
        if new != text:
            download_store.write_text(new, encoding='utf-8')

    share_sheet = ROOT / 'app/src/main/java/com/music/bitchord/ui/replay/ReplayShareSheet.kt'
    if share_sheet.exists():
        text = share_sheet.read_text(encoding='utf-8')
        new = text.replace('Environment.DIRECTORY_PICTURES}/BitChord', 'Environment.DIRECTORY_PICTURES}/Pexpo')
        if new != text:
            share_sheet.write_text(new, encoding='utf-8')


def patch_account_menu() -> None:
    path = ROOT / 'app/src/main/java/com/music/bitchord/ui/components/FrostedTopBar.kt'
    text = path.read_text(encoding='utf-8')
    start = text.find('@Composable\nfun TopBarAccountButton(')
    if start < 0:
        raise RuntimeError('TopBarAccountButton marker not found')
    end = text.find('\n/**', start + 10)
    if end < 0:
        raise RuntimeError('TopBarAccountButton end marker not found')

    new_function = '''@Composable
fun TopBarAccountButton(
    account: Account?,
    onClick: () -> Unit,
    onSwipeProfile: ((forward: Boolean) -> Boolean)? = null,
    modifier: Modifier = Modifier,
    onAddAccount: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
) {
    val viewModel: MainViewModel = viewModel()
    val accounts by viewModel.googleAccounts.collectAsStateWithLifecycle()
    val activeAccountId by viewModel.activeAccountId.collectAsStateWithLifecycle()
    val activeProfileId by viewModel.activeProfileId.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf(false) }
    val translation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    IconButton(
        onClick = { expanded = !expanded },
        modifier = modifier
            .graphicsLayer { translationY = translation.value }
            .pointerInput(onSwipeProfile) {
                if (onSwipeProfile == null) return@pointerInput
                var drag = 0f
                detectVerticalDragGestures(
                    onVerticalDrag = { change, amount -> change.consume(); drag += amount },
                    onDragEnd = {
                        if (kotlin.math.abs(drag) < 28f) return@detectVerticalDragGestures
                        if (!onSwipeProfile.invoke(drag > 0f)) scope.launch {
                            translation.snapTo(if (drag > 0f) 9f else -9f)
                            translation.animateTo(0f, spring())
                        }
                    },
                )
            },
    ) {
        val photo = account?.thumbnailUrl
        if (photo != null) {
            AsyncImage(
                model = photo,
                contentDescription = stringResource(R.string.switch_account),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(AVATAR_SIZE).clip(CircleShape).thumbnailBorder(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(AVATAR_SIZE)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .thumbnailBorder(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Person,
                    contentDescription = stringResource(R.string.switch_account),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false; managing = false },
        modifier = Modifier.widthIn(min = 260.dp, max = 340.dp),
    ) {
        Text(
            text = account?.name ?: "Pexpo account",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )
        if (accounts.isEmpty()) {
            DropdownMenuItem(
                text = { Text("Sign in to an account") },
                onClick = {
                    expanded = false
                    onAddAccount?.invoke() ?: onClick()
                },
                leadingIcon = { Icon(Icons.Rounded.PersonAdd, null) },
            )
        } else {
            accounts.forEach { googleAccount ->
                if (managing) {
                    DropdownMenuItem(
                        text = {
                            Text(if (googleAccount.accountId == activeAccountId) "${googleAccount.name} (current)" else googleAccount.name)
                        },
                        onClick = {
                            viewModel.removeAccount(googleAccount.accountId)
                            managing = false
                            expanded = false
                        },
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) },
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text(googleAccount.name) },
                        onClick = {
                            val profile = googleAccount.profiles.firstOrNull { it.profileId == activeProfileId }
                                ?: googleAccount.profiles.firstOrNull()
                            if (profile != null) {
                                viewModel.selectProfile(googleAccount.accountId, profile.profileId, profile)
                            }
                            expanded = false
                        },
                        leadingIcon = { Icon(Icons.Rounded.AccountCircle, null) },
                    )
                }
            }
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(if (managing) "Done" else "Manage accounts") },
            onClick = { managing = !managing },
            leadingIcon = { Icon(Icons.Rounded.ManageAccounts, null) },
        )
        DropdownMenuItem(
            text = { Text("Add account") },
            onClick = {
                expanded = false
                onAddAccount?.invoke() ?: onClick()
            },
            leadingIcon = { Icon(Icons.Rounded.PersonAdd, null) },
        )
        DropdownMenuItem(
            text = { Text("Settings") },
            onClick = {
                expanded = false
                onOpenSettings?.invoke() ?: onClick()
            },
            leadingIcon = { Icon(Icons.Rounded.Settings, null) },
        )
    }
}
'''

    text = text[:start] + new_function + text[end:]
    for imp in [
        'import androidx.compose.material3.DropdownMenu',
        'import androidx.compose.material3.DropdownMenuItem',
        'import androidx.compose.runtime.mutableStateOf',
        'import androidx.compose.runtime.setValue',
        'import androidx.compose.ui.unit.dp',
        'import androidx.lifecycle.viewmodel.compose.viewModel',
        'import com.music.bitchord.ui.MainViewModel',
        'import androidx.compose.material.icons.rounded.AccountCircle',
        'import androidx.compose.material.icons.rounded.DeleteOutline',
        'import androidx.compose.material.icons.rounded.ManageAccounts',
        'import androidx.compose.material.icons.rounded.PersonAdd',
        'import androidx.compose.material.icons.rounded.Settings',
    ]:
        text = add_import(text, imp)
    path.write_text(text, encoding='utf-8')

    main = ROOT / 'app/src/main/java/com/music/bitchord/MainActivity.kt'
    text = main.read_text(encoding='utf-8')
    old = '''onClick = {
                                    if (signedIn) {
                                        viewModel.loadChannels()
                                        showAccountSelector = true
                                    } else showSettings = true
                                },'''
    new = '''onClick = { showSettings = true },
                                onAddAccount = { webSession = WebSessionMode.SIGN_IN },
                                onOpenSettings = { showSettings = true },'''
    if old not in text:
        raise RuntimeError('MainActivity account callback marker not found')
    main.write_text(text.replace(old, new, 1), encoding='utf-8')


def patch_replay_safety() -> None:
    path = ROOT / 'app/src/main/java/com/music/bitchord/data/stats/ListeningStats.kt'
    text = path.read_text(encoding='utf-8')
    marker = '    private const val DIRECTORY = "listening"'
    comment = '    // Replay is device-local and must never be cleared by authentication changes.\n'
    if marker in text and comment not in text:
        path.write_text(text.replace(marker, comment + marker, 1), encoding='utf-8')


if __name__ == '__main__':
    patch_branding()
    patch_account_menu()
    patch_replay_safety()
    print('Pexpo maintenance patch applied.')
