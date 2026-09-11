from pathlib import Path

ROOT = Path('.')


def replace_in(path: Path, replacements: list[tuple[str, str]]) -> None:
    if not path.exists():
        return
    text = path.read_text(encoding='utf-8')
    new = text
    for old, value in replacements:
        new = new.replace(old, value)
    if new != text:
        path.write_text(new, encoding='utf-8')


def patch_branding() -> None:
    # Visible app strings. Keep package/class compatibility names unchanged.
    for path in ROOT.glob('app/src/main/res/**/strings.xml'):
        replace_in(path, [('Pexpo', 'Pexpo'), ('pexpo %1$s', 'Pexpo %1$s')])

    for path in ROOT.glob('app/src/main/java/**/*.kt'):
        replace_in(path, [
            ('"Visit Pexpo"', '"Visit Pexpo"'),
            ('"Listening to Pexpo"', '"Listening to Pexpo"'),
            ('"Pexpo"', '"Pexpo"'),
        ])

    replace_in(ROOT / 'app/src/main/java/com/music/pexpo/data/discord/DiscordRPC.kt', [
        ('const val PROJECT_URL = "https://github.com/kushagrasinghx/Pexpo"',
         'const val PROJECT_URL = "https://pexpomusic.xo.je"'),
        ('const val DEFAULT_BUTTON_2 = "Visit Pexpo"',
         'const val DEFAULT_BUTTON_2 = "Visit Pexpo"'),
    ])

    # The shared Replay poster is drawn directly onto a Bitmap, so Android
    # string resources cannot fix its brand. Likewise the saved filename is
    # visible in galleries/file managers and must not leak the old name.
    replace_in(ROOT / 'app/src/main/java/com/music/pexpo/ui/replay/ReplayPoster.kt', [
        ('canvas.drawText("Pexpo",', 'canvas.drawText("Pexpo",'),
    ])
    replace_in(ROOT / 'app/src/main/java/com/music/pexpo/ui/replay/ReplayShareSheet.kt', [
        ('"pexpo-replay-${label.replace', '"pexpo-replay-${label.replace'),
        ('${Environment.DIRECTORY_PICTURES}/Pexpo', '${Environment.DIRECTORY_PICTURES}/Pexpo'),
    ])


def patch_player_bar_stability() -> None:
    path = ROOT / 'app/src/main/java/com/music/pexpo/MainActivity.kt'
    text = path.read_text(encoding='utf-8')

    anchor = '    val player = rememberPlayerState(controller)\n'
    block = '''    val player = rememberPlayerState(controller)

    // Samsung/One UI can briefly detach and recreate the MediaController while
    // the foreground activity remains alive. During that hand-off
    // rememberPlayerState can legitimately expose a null current item for one
    // or two frames. Driving the mini-player visibility directly from that
    // transient null made the bottom transport jump upward and disappear.
    // Hold the last known track through a short controller hand-off; a genuine
    // stop still clears it promptly. This is UI-only and never changes playback.
    var visiblePlayerSong by remember { mutableStateOf<Song?>(null) }
    LaunchedEffect(player.song?.videoId) {
        val current = player.song
        if (current != null) {
            visiblePlayerSong = current
        } else {
            kotlinx.coroutines.delay(1500)
            if (player.song == null) visiblePlayerSong = null
        }
    }
'''
    if anchor in text and 'var visiblePlayerSong by remember' not in text:
        text = text.replace(anchor, block, 1)

    text = text.replace(
        'withMiniPlayer = player.song != null && !playerDocked,',
        'withMiniPlayer = visiblePlayerSong != null && !playerDocked,',
    )
    text = text.replace(
        'player.song?.takeUnless { playerDocked }?.let { song ->',
        'visiblePlayerSong?.takeUnless { playerDocked }?.let { song ->',
    )
    text = text.replace(
        'song = player.song?.takeUnless { playerDocked },',
        'song = visiblePlayerSong?.takeUnless { playerDocked },',
    )
    path.write_text(text, encoding='utf-8')


def patch_version() -> None:
    replace_in(ROOT / 'app/build.gradle.kts', [
        ('versionCode = 15', 'versionCode = 16'),
        ('versionName = "1.5.3"', 'versionName = "1.5.4"'),
    ])


def patch_workflow() -> None:
    path = ROOT / '.github/workflows/android.yml'
    replace_in(path, [
        ('Finalize Pexpo v1.5.3 source fixes', 'Finalize Pexpo v1.5.4 source fixes'),
        ('Build Pexpo v1.5.3', 'Build Pexpo v1.5.4'),
        ('pexpo-1.5.3-', 'pexpo-1.5.4-'),
        ('pexpo-v1.5.3-release', 'pexpo-v1.5.4-release'),
        ('Publish GitHub Release v1.5.3', 'Publish GitHub Release v1.5.4'),
        ('gh release view v1.5.3', 'gh release view v1.5.4'),
        ('v1.5.3 already published.', 'v1.5.4 already published.'),
        ('gh release create v1.5.3', 'gh release create v1.5.4'),
        ("--title 'Pexpo Music v1.5.3'", "--title 'Pexpo Music v1.5.4'"),
        ("Pexpo Music v1.5.3: fixed account/profile switcher, working Add account and Manage accounts, duplicate Sign out, Replay persistence safety, remaining Pexpo Replay branding, and variant-aware in-app APK updates.",
         "Pexpo Music v1.5.4: improved system stability and bug fixes, persistent mini-player recovery on Samsung/One UI, complete Pexpo branding in Discord and Replay sharing, Pexpo website button, and safer release/update handling."),
    ])


if __name__ == '__main__':
    patch_branding()
    patch_player_bar_stability()
    patch_version()
    patch_workflow()
    print('Pexpo v1.5.4 patch applied.')
