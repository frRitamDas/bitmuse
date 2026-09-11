from pathlib import Path

ROOT = Path('.')


def patch_home_offline_skeleton() -> None:
    path = ROOT / 'app/src/main/java/com/music/pexpo/ui/screens/HomeScreen.kt'
    text = path.read_text(encoding='utf-8')

    # The 1.5.6 Home behavior may already be present on main. The maintenance
    # script also has to work from an older 1.5.5 checkout, so only insert the
    # network declarations when both are genuinely absent. Never duplicate
    # local Compose declarations.
    context_marker = '    val homeContext = androidx.compose.ui.platform.LocalContext.current\n'
    network_marker = '    val networkAvailableForHome = remember {'
    marker = '    PullToRefresh(\n        refreshing = refreshing,'

    if context_marker not in text and network_marker not in text:
        if marker not in text:
            raise RuntimeError('HomeScreen: expected PullToRefresh marker not found')
        replacement = '''    // Reuse Pexpo's existing skeleton UI when the device has no active
    // network. This intentionally does not introduce another skeleton
    // implementation: offline Home should look exactly like Home loading.
    val homeContext = androidx.compose.ui.platform.LocalContext.current
    val networkAvailableForHome = remember {
        homeContext.getSystemService(android.net.ConnectivityManager::class.java)
            ?.activeNetwork != null
    }

    PullToRefresh(
        refreshing = refreshing,'''
        text = text.replace(marker, replacement, 1)
    elif context_marker not in text or network_marker not in text:
        raise RuntimeError('HomeScreen: partial offline-network patch detected; refusing to create duplicate state')

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

    if old in text:
        text = text.replace(old, new, 1)
    elif new not in text:
        raise RuntimeError('HomeScreen: expected error state block not found')

    path.write_text(text, encoding='utf-8')


def patch_fresh_google_login() -> None:
    path = ROOT / 'app/src/main/java/com/music/pexpo/auth/YtMusicLoginScreen.kt'
    text = path.read_text(encoding='utf-8')

    # A fresh Pexpo login must never request passive authentication. Together
    # with BrowserSession's completed cookie cleanup, this prevents Google from
    # silently reusing the account that was just signed out.
    old_url = 'passive=true&continue=https%3A%2F%2Fmusic.youtube.com%2F'
    new_url = 'passive=false&continue=https%3A%2F%2Fmusic.youtube.com%2F'
    if old_url in text:
        text = text.replace(old_url, new_url, 1)
    elif new_url not in text:
        raise RuntimeError('YtMusicLoginScreen: expected Google login URL not found')

    # Avoid displaying a stale cached YouTube page while a new Google session
    # is being established. This does not touch the cookie jar.
    marker = '                settings.domStorageEnabled = true\n'
    cache_line = '                settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE\n'
    if cache_line not in text:
        if marker not in text:
            raise RuntimeError('YtMusicLoginScreen: expected WebView settings marker not found')
        text = text.replace(marker, marker + cache_line, 1)

    old_cleanup = '''                    BrowserSession.clearGoogleCookies {
                        post {
                            stopLoading()
                            clearHistory()
                            loadUrl(LOGIN_URL)
                        }
                    }'''
    new_cleanup = '''                    BrowserSession.clearGoogleCookies {
                        post {
                            stopLoading()
                            clearHistory()
                            clearCache(true)
                            loadUrl(LOGIN_URL)
                        }
                    }'''
    if old_cleanup in text:
        text = text.replace(old_cleanup, new_cleanup, 1)
    elif new_cleanup not in text:
        raise RuntimeError('YtMusicLoginScreen: expected fresh-login navigation block not found')

    path.write_text(text, encoding='utf-8')


if __name__ == '__main__':
    patch_home_offline_skeleton()
    patch_fresh_google_login()
    print('Pexpo 1.5.6 patches applied idempotently: offline Home skeleton + deterministic fresh Google login.')
