from pathlib import Path

ROOT = Path('.')


def patch_home_offline_skeleton() -> None:
    path = ROOT / 'app/src/main/java/com/music/pexpo/ui/screens/HomeScreen.kt'
    text = path.read_text(encoding='utf-8')

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

    final_markers = (
        'freshSessionKey: Int = 0',
        'key(mode, freshSessionKey)',
        'WebStorage.getInstance().deleteAllData()',
        'loadUrl(LOGIN_URL)',
    )
    if all(marker in text for marker in final_markers):
        return

    old_url = 'passive=true&continue=https%3A%2F%2Fmusic.youtube.com%2F'
    new_url = 'passive=false&continue=https%3A%2F%2Fmusic.youtube.com%2F'
    if old_url in text:
        text = text.replace(old_url, new_url, 1)
    elif new_url not in text:
        raise RuntimeError('YtMusicLoginScreen: expected Google login URL not found')

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
                            clearFormData()
                            runCatching { WebStorage.getInstance().deleteAllData() }
                            runCatching { CookieManager.getInstance().flush() }
                            loadUrl(LOGIN_URL)
                        }
                    }'''
    if old_cleanup in text:
        text = text.replace(old_cleanup, new_cleanup, 1)
    elif new_cleanup not in text:
        raise RuntimeError('YtMusicLoginScreen: expected fresh-login navigation block not found')

    path.write_text(text, encoding='utf-8')


def patch_fresh_login_wiring() -> None:
    state_path = ROOT / 'app/src/main/java/com/music/pexpo/MainActivityStateCompat.kt'
    state = state_path.read_text(encoding='utf-8')
    if 'freshGoogleSessionKey' not in state:
        marker = 'private val webSessionState = mutableStateOf<WebSessionMode?>(null)\n'
        replacement = marker + '\n/** Increments whenever a new SIGN_IN transaction is opened. */\nvar freshGoogleSessionKey by mutableIntStateOf(0)\n'
        if marker not in state:
            raise RuntimeError('MainActivityStateCompat: expected webSession state marker not found')
        state = state.replace(marker, replacement, 1)

    old_setter = '''    set(value) {
        webSessionState.value = value
        if (value == null) {'''
    new_setter = '''    set(value) {
        if (value == WebSessionMode.SIGN_IN && webSessionState.value != WebSessionMode.SIGN_IN) {
            freshGoogleSessionKey++
        }
        webSessionState.value = value
        if (value == null) {'''
    if old_setter in state:
        state = state.replace(old_setter, new_setter, 1)
    elif 'freshGoogleSessionKey++' not in state:
        raise RuntimeError('MainActivityStateCompat: expected webSession setter not found')
    state_path.write_text(state, encoding='utf-8')

    activity_path = ROOT / 'app/src/main/java/com/music/pexpo/MainActivity.kt'
    activity = activity_path.read_text(encoding='utf-8')
    if 'freshSessionKey = freshGoogleSessionKey' not in activity:
        marker = '                        captureRequest = captureRequest,\n'
        replacement = marker + '                        freshSessionKey = freshGoogleSessionKey,\n'
        if marker not in activity:
            raise RuntimeError('MainActivity: expected YtMusicLoginScreen captureRequest marker not found')
        activity = activity.replace(marker, replacement, 1)
        activity_path.write_text(activity, encoding='utf-8')


if __name__ == '__main__':
    patch_home_offline_skeleton()
    patch_fresh_google_login()
    patch_fresh_login_wiring()
    print('Pexpo 1.5.6 patches applied idempotently: offline Home skeleton + fresh Google authentication transaction.')
