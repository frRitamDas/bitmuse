from pathlib import Path
import re

ROOT = Path('.')


def add_import(text: str, imp: str) -> str:
    if imp in text:
        return text
    package_end = text.find('\n', text.find('package ')) + 1
    return text[:package_end] + imp + '\n' + text[package_end:]


def patch_branding() -> None:
    for path in ROOT.glob('app/src/main/res/**/strings.xml'):
        text = path.read_text(encoding='utf-8')
        new = text.replace('BitChord', 'Pexpo').replace('bitchord %1$s', 'Pexpo %1$s')
        if new != text:
            path.write_text(new, encoding='utf-8')

    # Visible/exported labels only. Never rename the Kotlin/package compatibility
    # identifiers such as BitChordTheme or com.music.bitchord.
    for path in ROOT.glob('app/src/main/java/**/*.kt'):
        text = path.read_text(encoding='utf-8')
        new = text
        new = new.replace('Music/BitChord', 'Music/Pexpo')
        new = new.replace('text = "BitChord"', 'text = "Pexpo"')
        new = new.replace('appendLine("BitChord log —', 'appendLine("Pexpo log —')
        new = new.replace('That doesn\'t look like a BitChord backup', "That doesn't look like a Pexpo backup")
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
) {
    val translation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    IconButton(
        onClick = onClick,
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
}
'''
    text = text[:start] + new_function + text[end:]
    # The old dropdown imports are harmless but removing them keeps this file clean.
    for imp in [
        'import androidx.compose.material3.DropdownMenu',
        'import androidx.compose.material3.DropdownMenuItem',
        'import androidx.compose.foundation.layout.widthIn',
        'import androidx.compose.runtime.mutableStateOf',
        'import androidx.compose.runtime.setValue',
        'import androidx.lifecycle.viewmodel.compose.viewModel',
        'import com.music.bitchord.ui.MainViewModel',
        'import androidx.compose.material.icons.rounded.AccountCircle',
        'import androidx.compose.material.icons.rounded.DeleteOutline',
        'import androidx.compose.material.icons.rounded.ManageAccounts',
        'import androidx.compose.material.icons.rounded.PersonAdd',
        'import androidx.compose.material.icons.rounded.Settings',
    ]:
        text = text.replace(imp + '\n', '')
    path.write_text(text, encoding='utf-8')

    main = ROOT / 'app/src/main/java/com/music/bitchord/MainActivity.kt'
    text = main.read_text(encoding='utf-8')
    text = add_import(text, 'import com.music.bitchord.ui.components.AccountProfileSelector')

    # Collect the persistent Google account/profile list once at the app level.
    anchor = '    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()\n'
    collectors = '''    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val googleAccounts by viewModel.googleAccounts.collectAsStateWithLifecycle()
    val activeAccountId by viewModel.activeAccountId.collectAsStateWithLifecycle()
    val activeProfileId by viewModel.activeProfileId.collectAsStateWithLifecycle()
'''
    if anchor in text and collectors not in text:
        text = text.replace(anchor, collectors, 1)

    old_call = '''TopBarAccountButton(
                                account = account,
                                onClick = { showSettings = true },
                                onAddAccount = { webSession = WebSessionMode.SIGN_IN },
                                onOpenSettings = { showSettings = true },
                                onSwipeProfile = { forward -> viewModel.stepProfile(forward) },
                            )'''
    new_call = '''TopBarAccountButton(
                                account = account,
                                onClick = { showAccountSelector = true },
                                onSwipeProfile = { forward -> viewModel.stepProfile(forward) },
                            )'''
    if old_call in text:
        text = text.replace(old_call, new_call, 1)

    # Mount the already-designed full account selector as a real overlay. This
    # keeps Add account / Manage accounts / Settings wired to the same state and
    # avoids the cramped generic DropdownMenu that caused the reported bug.
    marker = '        // ---- Update available (once per launch) ----\n'
    overlay = '''        if (showAccountSelector) {
            BackHandler { showAccountSelector = false }
            AccountProfileSelector(
                accounts = googleAccounts,
                activeAccountId = activeAccountId,
                activeProfileId = activeProfileId,
                hazeState = hazeState,
                onSelect = { selectedAccount, profile ->
                    viewModel.selectProfile(selectedAccount.accountId, profile.profileId, profile)
                    showAccountSelector = false
                },
                onAddAccount = {
                    showAccountSelector = false
                    webSession = WebSessionMode.SIGN_IN
                },
                onRemoveAccount = { selectedAccount ->
                    viewModel.removeAccount(selectedAccount.accountId)
                },
                onOpenSettings = {
                    showAccountSelector = false
                    showSettings = true
                },
                onDismiss = { showAccountSelector = false },
            )
        }

'''
    if marker in text and 'AccountProfileSelector(' not in text:
        text = text.replace(marker, overlay + marker, 1)
    main.write_text(text, encoding='utf-8')


def patch_account_screen() -> None:
    path = ROOT / 'app/src/main/java/com/music/bitchord/ui/screens/AccountAndScrobblingScreen.kt'
    text = path.read_text(encoding='utf-8')
    duplicate = '''            SettingsGroup { DestructiveRow(label = stringResource(R.string.sign_out), onClick = onSignOut) }\n'''
    if duplicate in text:
        text = text.replace(duplicate, '', 1)
    path.write_text(text, encoding='utf-8')


def patch_replay_safety() -> None:
    path = ROOT / 'app/src/main/java/com/music/bitchord/data/stats/ListeningStats.kt'
    text = path.read_text(encoding='utf-8')
    marker = '    private const val DIRECTORY = "listening"'
    comment = '    // Replay is device-local and must never be cleared by authentication changes.\n'
    if marker in text and comment not in text:
        text = text.replace(marker, comment + marker, 1)
    path.write_text(text, encoding='utf-8')


def patch_updater() -> None:
    path = ROOT / 'app/src/main/java/com/music/bitchord/data/AppUpdateChecker.kt'
    text = path.read_text(encoding='utf-8')
    text = text.replace('import android.content.Intent\n', 'import android.content.Intent\nimport android.content.pm.PackageManager\n')
    text = text.replace('suspend fun check() = withContext(Dispatchers.IO) {', 'suspend fun check(context: Context) = withContext(Dispatchers.IO) {')
    text = text.replace('val apkUrl = apkAssetUrl(release)', 'val apkUrl = apkAssetUrl(release, installedVariant(context))')
    text = text.replace('private fun apkAssetUrl(release: JsonObject): String? = runCatching {', 'private fun apkAssetUrl(release: JsonObject, variant: String): String? = runCatching {')
    old_asset = '''            ?.firstOrNull { asset ->
                asset["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk", ignoreCase = true) == true &&
                    asset["state"]?.jsonPrimitive?.contentOrNull == "uploaded"
            }'''
    new_asset = '''            ?.filter { asset ->
                asset["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk", ignoreCase = true) == true &&
                    asset["state"]?.jsonPrimitive?.contentOrNull == "uploaded"
            }
            ?.sortedBy { asset ->
                val name = asset["name"]?.jsonPrimitive?.contentOrNull.orEmpty().lowercase()
                if (name.contains("-$variant.apk")) 0 else if (name.contains("-universal.apk")) 1 else 2
            }
            ?.firstOrNull()'''
    text = text.replace(old_asset, new_asset)
    text = text.replace('File(dir, "bitchord-${info.version}.apk")', 'File(dir, "pexpo-${info.version}.apk")')

    helper = '''
    /**
     * Detects the APK family that is actually installed. A monolithic APK has
     * no split names and is the universal build; an ABI APK has a config split.
     * This is deliberately based on PackageInfo rather than the device ABI:
     * an arm64 phone can have the universal APK installed, and must receive the
     * universal APK again as requested by the user.
     */
    private fun installedVariant(context: Context): String {
        val splits = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).splitNames.orEmpty()
        }.getOrDefault(emptyArray())
        val joined = splits.joinToString(" ").lowercase()
        return when {
            joined.contains("arm64") || joined.contains("arm64_v8a") -> "arm64-v8a"
            joined.contains("armeabi") || joined.contains("armeabi_v7a") -> "armeabi-v7a"
            joined.contains("x86_64") -> "x86_64"
            else -> "universal"
        }
    }
'''
    if 'private fun installedVariant(context: Context)' not in text:
        text = text.replace('\n    /** Numeric, dot-separated comparison', helper + '\n    /** Numeric, dot-separated comparison', 1)
    path.write_text(text, encoding='utf-8')


def patch_viewmodel_update_call() -> None:
    path = ROOT / 'app/src/main/java/com/music/bitchord/ui/MainViewModel.kt'
    text = path.read_text(encoding='utf-8')
    text = text.replace('AppUpdateChecker.check()','AppUpdateChecker.check(getApplication())')
    path.write_text(text, encoding='utf-8')


if __name__ == '__main__':
    patch_branding()
    patch_account_menu()
    patch_account_screen()
    patch_replay_safety()
    patch_updater()
    patch_viewmodel_update_call()
    print('Pexpo v1.5.3 maintenance patch applied.')
