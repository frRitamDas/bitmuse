from pathlib import Path
import re

ROOT = Path('.')


def first_existing(*relative_paths: str) -> Path:
    for relative in relative_paths:
        path = ROOT / relative
        if path.exists():
            return path
    raise RuntimeError(f'None of the expected source paths exist: {relative_paths!r}')


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        if new in text:
            return text
        raise RuntimeError(f'{label}: expected source marker not found')
    return text.replace(old, new, 1)


def patch_main_activity() -> None:
    path = first_existing(
        'app/src/main/java/com/music/pexpo/MainActivity.kt',
        'app/src/main/java/com/music/bitchord/MainActivity.kt',
    )
    text = path.read_text(encoding='utf-8')

    signed_in_marker = 'onClick = { viewModel.refreshGoogleAccounts(); showAccountSelector = true },'
    signed_in_replacement = '''onClick = {
                                    if (signedIn) {
                                        viewModel.refreshGoogleAccounts()
                                        showAccountSelector = true
                                    } else {
                                        showSettings = true
                                    }
                                },'''
    if signed_in_marker in text:
        text = text.replace(signed_in_marker, signed_in_replacement, 1)

    old_listen_as = '''                            onSwitchChannel = {
                                // Asked for on open rather than on sign-in: it
                                // is a request per session that most listeners,
                                // who have exactly one channel, never need.
                                viewModel.loadChannels()
                                showChannelPicker = true
                            },'''
    new_listen_as = '''                            onSwitchChannel = {
                                // Account & integrations uses the exact same account/profile
                                // selector as the Home avatar. Refresh persisted sessions first
                                // so a newly signed-in account and avatar appear immediately.
                                viewModel.refreshGoogleAccounts()
                                showAccountSelector = true
                            },'''
    if old_listen_as in text:
        text = text.replace(old_listen_as, new_listen_as, 1)

    path.write_text(text, encoding='utf-8')


def patch_gradle_dependency() -> None:
    path = ROOT / 'app/build.gradle.kts'
    text = path.read_text(encoding='utf-8')
    bad = '    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.3")\n'
    good = '    implementation(files(newPipeExtractorStripped))\n    implementation("com.github.TeamNewPipe:nanojson:e9d656ddb49a412a5a0a5d5ef20ca7ef09549996")\n'
    if bad in text:
        text = text.replace(bad, good, 1)
    path.write_text(text, encoding='utf-8')


def patch_canvas_network_rule() -> None:
    path = first_existing(
        'app/src/main/java/com/music/pexpo/ui/player/NowPlayingScreen.kt',
        'app/src/main/java/com/music/bitchord/ui/player/NowPlayingScreen.kt',
    )
    text = path.read_text(encoding='utf-8')

    # Replace the entire Canvas settings/gating section in one operation. This
    # deliberately removes any stale/duplicate declaration left by an earlier
    # patch attempt, making the maintenance script safe to rerun.
    start = text.find('    val canvasEnabled by AppSettings.animatedCanvas.collectAsStateWithLifecycle()')
    end_marker = '    var canvas by remember(song.videoId) { mutableStateOf<CanvasArtwork?>(null) }'
    end = text.find(end_marker, start)
    if start < 0 or end < 0:
        if '    val canvasAllowedNow = canvasEnabled && when (canvasTransport)' in text and text.count('canvasAllowedNow') >= 1:
            return
        raise RuntimeError('Canvas network rule: expected Canvas state section not found')

    new_section = '''    val canvasEnabled by AppSettings.animatedCanvas.collectAsStateWithLifecycle()
    val canvasOverCellular by AppSettings.canvasOverCellular.collectAsStateWithLifecycle()

    // Canvas is gated by the actual default network transport, not meteredness.
    // Wi-Fi may itself be marked metered, so AppSettings.meteredConnection is
    // intentionally not used for this feature gate.
    var canvasTransport by remember { mutableStateOf("offline") }
    DisposableEffect(context) {
        val manager = context.getSystemService(android.net.ConnectivityManager::class.java)

        fun transportOf(capabilities: android.net.NetworkCapabilities?): String = when {
            capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true -> "wifi"
            capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "cellular"
            capabilities == null -> "offline"
            else -> "other"
        }

        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: android.net.Network,
                capabilities: android.net.NetworkCapabilities,
            ) {
                canvasTransport = transportOf(capabilities)
            }

            override fun onLost(network: android.net.Network) {
                canvasTransport = "offline"
            }
        }

        runCatching { manager?.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { manager?.unregisterNetworkCallback(callback) } }
    }

    // Wi-Fi always permits Canvas. The user's preference only gates cellular.
    // Other active transports retain Canvas support; offline does not request it.
    val canvasAllowedNow = canvasEnabled && when (canvasTransport) {
        "cellular" -> canvasOverCellular
        "offline" -> false
        else -> true
    }
'''
    text = text[:start] + new_section + text[end:]
    path.write_text(text, encoding='utf-8')


def rename_pexpo_identity() -> None:
    # Keep compatibility-sensitive persisted preference/database identifiers
    # intact; changing those strings blindly would make an upgrade look like a
    # fresh install. Everything that is the BitChord source/package identity is
    # renamed to Pexpo.
    replacements = (
        ('com.music.bitchord', 'com.music.pexpo'),
        ('BitChord', 'Pexpo'),
        ('Bitchord', 'Pexpo'),
        ('BITCHORD', 'PEXPO'),
        ('bitchord', 'pexpo'),
    )
    protected = 'bitchord_settings'
    skip_suffixes = {'.png', '.jpg', '.jpeg', '.webp', '.gif', '.mp3', '.mp4', '.onnx', '.jks', '.keystore'}

    files = []
    for path in ROOT.rglob('*'):
        if not path.is_file():
            continue
        if '.git' in path.parts or 'build' in path.parts or '.gradle' in path.parts:
            continue
        # Keep this migration script's legacy-token map intact; it is the
        # mechanism that performs the source rename on future clean checkouts.
        if path == ROOT / 'scripts/patch_155.py':
            continue
        if path.suffix.lower() in skip_suffixes:
            continue
        files.append(path)

    for path in files:
        try:
            text = path.read_text(encoding='utf-8')
        except (UnicodeDecodeError, OSError):
            continue
        original = text
        for old, new in replacements:
            text = text.replace(old, new)
        # Preserve the legacy preference-file name for existing installations.
        text = text.replace('pexpo_settings', protected)
        if text != original:
            path.write_text(text, encoding='utf-8')

    rename_tokens = (
        ('BitChord', 'Pexpo'),
        ('Bitchord', 'Pexpo'),
        ('BITCHORD', 'PEXPO'),
        ('bitchord', 'pexpo'),
    )
    paths = sorted(
        [
            p for p in ROOT.rglob('*')
            if '.git' not in p.parts
            and 'build' not in p.parts
            and '.gradle' not in p.parts
            and p != ROOT / 'scripts/patch_155.py'
        ],
        key=lambda p: len(p.parts),
        reverse=True,
    )
    for path in paths:
        new_name = path.name
        for old, new in rename_tokens:
            new_name = new_name.replace(old, new)
        if new_name != path.name:
            path.rename(path.with_name(new_name))


if __name__ == '__main__':
    patch_main_activity()
    patch_gradle_dependency()
    patch_canvas_network_rule()
    rename_pexpo_identity()
    print('Pexpo 1.5.5 maintenance patch applied: account routing, Canvas transport gating, and BitChord -> Pexpo identity rename.')
