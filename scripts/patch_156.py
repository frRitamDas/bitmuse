from pathlib import Path

ROOT = Path('.')


def patch_home_offline_skeleton() -> None:
    path = ROOT / 'app/src/main/java/com/music/pexpo/ui/screens/HomeScreen.kt'
    text = path.read_text(encoding='utf-8')

    marker = '    PullToRefresh(\n        refreshing = refreshing,'
    if marker not in text:
        if 'val networkAvailableForHome' in text and 'feedSkeleton()' in text:
            return
        raise RuntimeError('HomeScreen: expected PullToRefresh marker not found')

    replacement = '''    // Reuse Pexpo's existing skeleton UI when the device has no active
    // network. This intentionally does not introduce another skeleton
    // implementation: offline Home should look exactly like Home loading.
    val networkAvailableForHome = remember {
        androidx.compose.ui.platform.LocalContext.current
            .getSystemService(android.net.ConnectivityManager::class.java)
            ?.activeNetwork != null
    }

    PullToRefresh(
        refreshing = refreshing,'''
    text = text.replace(marker, replacement, 1)

    old = '''                is UiState.Error -> item {
                    MessageState(state.message, actionLabel = stringResource(R.string.retry), onAction = onRetry)
                }'''
    new = '''                is UiState.Error -> {
                    if (!networkAvailableForHome) {
                        // No connection: keep the established Home skeleton
                        // instead of replacing the page with a hard error card.
                        feedSkeleton()
                    } else {
                        item {
                            MessageState(state.message, actionLabel = stringResource(R.string.retry), onAction = onRetry)
                        }
                    }
                }'''
    if old not in text:
        if 'if (!networkAvailableForHome)' not in text:
            raise RuntimeError('HomeScreen: expected error state block not found')
    else:
        text = text.replace(old, new, 1)

    path.write_text(text, encoding='utf-8')


if __name__ == '__main__':
    patch_home_offline_skeleton()
    print('Pexpo 1.5.6 patch applied: offline Home reuses existing skeleton UI.')
