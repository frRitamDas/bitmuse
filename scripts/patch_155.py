from pathlib import Path

ROOT = Path('.')


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        if new in text:
            return text
        raise RuntimeError(f'{label}: expected source marker not found')
    return text.replace(old, new, 1)


def patch_main_activity() -> None:
    path = ROOT / 'app/src/main/java/com/music/bitchord/MainActivity.kt'
    text = path.read_text(encoding='utf-8')
    text = replace_once(
        text,
        'onClick = { viewModel.refreshGoogleAccounts(); showAccountSelector = true },',
        '''onClick = {
                                    if (signedIn) {
                                        viewModel.refreshGoogleAccounts()
                                        showAccountSelector = true
                                    } else {
                                        showSettings = true
                                    }
                                },''',
        'top-bar account routing',
    )
    text = replace_once(
        text,
        '''                            onSwitchChannel = {
                                // Asked for on open rather than on sign-in: it
                                // is a request per session that most listeners,
                                // who have exactly one channel, never need.
                                viewModel.loadChannels()
                                showChannelPicker = true
                            },''',
        '''                            onSwitchChannel = {
                                // Account & integrations uses the exact same account/profile
                                // selector as the Home avatar. Refresh persisted sessions first
                                // so a newly signed-in account and avatar appear immediately.
                                viewModel.refreshGoogleAccounts()
                                showAccountSelector = true
                            },''',
        'settings Listen as routing',
    )
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
    path = ROOT / 'app/src/main/java/com/music/bitchord/ui/player/NowPlayingScreen.kt'
    text = path.read_text(encoding='utf-8')
    old = '''    val canvasEnabled by AppSettings.animatedCanvas.collectAsStateWithLifecycle()
    val canvasOverCellular by AppSettings.canvasOverCellular.collectAsStateWithLifecycle()
    val meteredConnection by AppSettings.meteredConnection.collectAsStateWithLifecycle()
    // The switch turns the feature off outright; this is the narrower "not
    // over cellular" case — see [AppSettings.canvasOverCellular] for why a
    // clip's own loop makes that worth guarding separately from a still image.
'''
    new = '''    val canvasEnabled by AppSettings.animatedCanvas.collectAsStateWithLifecycle()
    val canvasOverCellular by AppSettings.canvasOverCellular.collectAsStateWithLifecycle()

    // Canvas is gated by the actual radio transport, not meteredness. Android
    // permits Wi-Fi to be marked metered, so isActiveNetworkMetered cannot be
    // used to decide whether the user's "over cellular" preference applies.
    var canvasTransport by remember { mutableStateOf("other") }
    DisposableEffect(context) {
        val manager = context.getSystemService(android.net.ConnectivityManager::class.java)
        fun readTransport() {
            val network = manager?.activeNetwork
            val capabilities = network?.let { manager.getNetworkCapabilities(it) }
            canvasTransport = when {
                capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true -> "wifi"
                capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "cellular"
                else -> "other"
            }
        }
        fun transportOf(capabilities: android.net.NetworkCapabilities): String = when {
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            else -> "other"
        }
        readTransport()
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) = readTransport()
            override fun onLost(network: android.net.Network) = readTransport()
            override fun onCapabilitiesChanged(network: android.net.Network, capabilities: android.net.NetworkCapabilities) {
                canvasTransport = transportOf(capabilities)
            }
        }
        runCatching { manager?.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { manager?.unregisterNetworkCallback(callback) } }
    }

    // Wi-Fi always permits Canvas. Cellular is controlled by the explicit
    // preference. Offline/unknown transports do not start a Canvas request.
    val canvasAllowedNow = canvasEnabled && when (canvasTransport) {
        "wifi" -> true
        "cellular" -> canvasOverCellular
        else -> false
    }
'''
    if old in text:
        text = text.replace(old, new, 1)
    elif 'var canvasTransport by remember' not in text:
        raise RuntimeError('Canvas network rule: expected source marker not found')
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
        [p for p in ROOT.rglob('*') if '.git' not in p.parts and 'build' not in p.parts and '.gradle' not in p.parts],
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
