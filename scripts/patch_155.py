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


if __name__ == '__main__':
    patch_main_activity()
    print('Pexpo 1.5.5 maintenance patch applied.')
