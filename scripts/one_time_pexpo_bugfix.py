from pathlib import Path


def replace_once(path: str, old: str, new: str, label: str) -> None:
    p = Path(path)
    s = p.read_text()
    count = s.count(old)
    if count == 0:
        raise SystemExit(f"{label}: target text not found")
    if count != 1:
        raise SystemExit(f"{label}: target occurs {count} times")
    p.write_text(s.replace(old, new))


# Bug #1 — refresh the persisted session and immediately refetch missing
# YouTube profile/channel thumbnails while the account selector is open.
p = Path("app/src/main/java/com/music/pexpo/ui/MainViewModel.kt")
s = p.read_text()
old = """    fun refreshGoogleAccounts() {
        _googleAccounts.value = authStore.sessions
        _activeAccountId.value = authStore.activeSession?.accountId
        _activeProfileId.value = authStore.activeProfileId
    }"""
new = """    fun refreshGoogleAccounts() {
        _googleAccounts.value = authStore.sessions
        _activeAccountId.value = authStore.activeSession?.accountId
        _activeProfileId.value = authStore.activeProfileId

        // A fresh sign-in can have the session immediately while the YouTube
        // channel/profile thumbnails are still missing. Reuse the existing
        // authoritative channel fetch; its result updates the same session
        // objects while the selector is already open, so no restart is needed.
        if (authStore.sessions.any { session ->
                session.profiles.any { profile -> profile.avatar.isNullOrBlank() }
            }) {
            loadChannels(force = true)
        }
    }"""
if "loadChannels(force = true)" not in s:
    replace_once(p.as_posix(), old, new, "Bug #1 account refresh")

# Bug #2 — transport is not the same thing as meteredness. Android explicitly
# allows Wi-Fi to be metered, so the display must use NetworkCapabilities
# transport while AppSettings.meteredConnection remains the billing signal.
p = Path("app/src/main/java/com/music/pexpo/ui/screens/SettingsSheet.kt")
s = p.read_text()
if "import androidx.compose.runtime.DisposableEffect" not in s:
    replace_once(
        p.as_posix(),
        "import androidx.compose.runtime.Composable\n",
        "import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.DisposableEffect\n",
        "Bug #2 DisposableEffect import",
    )
    s = p.read_text()
if "var activeTransport by remember" not in s:
    old = "    val metered by AppSettings.meteredConnection.collectAsStateWithLifecycle()\n"
    new = """    val metered by AppSettings.meteredConnection.collectAsStateWithLifecycle()
    var activeTransport by remember { mutableStateOf(\"other\") }
    DisposableEffect(context) {
        val manager = context.getSystemService(android.net.ConnectivityManager::class.java)
        fun updateTransport() {
            val network = manager?.activeNetwork
            val capabilities = network?.let { manager.getNetworkCapabilities(it) }
            activeTransport = when {
                capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true -> \"wifi\"
                capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true -> \"cellular\"
                else -> \"other\"
            }
        }
        updateTransport()
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) = updateTransport()
            override fun onLost(network: android.net.Network) = updateTransport()
            override fun onCapabilitiesChanged(network: android.net.Network, capabilities: android.net.NetworkCapabilities) {
                activeTransport = when {
                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> \"wifi\"
                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> \"cellular\"
                    else -> \"other\"
                }
            }
        }
        runCatching { manager?.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { manager?.unregisterNetworkCallback(callback) } }
    }
    val onWifi = activeTransport == \"wifi\"
    val onCellular = activeTransport == \"cellular\"
"""
    replace_once(p.as_posix(), old, new, "Bug #2 transport state")
    s = p.read_text()
if "takeIf { onWifi }" not in s:
    replace_once(p.as_posix(), "badge = stringResource(R.string.in_use).takeIf { metered == false },", "badge = stringResource(R.string.in_use).takeIf { onWifi },", "Bug #2 Wi-Fi badge")
    s = p.read_text()
if "takeIf { onCellular }" not in s:
    replace_once(p.as_posix(), "badge = stringResource(R.string.in_use).takeIf { metered == true },", "badge = stringResource(R.string.in_use).takeIf { onCellular },", "Bug #2 cellular badge")

# Legacy gate — current 1.5.1.4 is the minimum and is never blocked. The
# existing Pexpo version ordering puts 1.5.1.4 between 1.5.4 and 1.5.5.
p = Path("app/src/main/java/com/music/pexpo/PexpoApplication.kt")
s = p.read_text()
if "Pexpo update required" not in s:
    if "import android.app.Activity\n" not in s:
        replace_once(
            p.as_posix(),
            "import android.app.Application\n",
            "import android.app.Activity\nimport android.app.AlertDialog\nimport android.app.Application\nimport android.content.Intent\nimport android.net.Uri\n",
            "Legacy gate imports",
        )
    s = p.read_text()
    if "registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks" not in s:
        replace_once(
            p.as_posix(),
            "        super.onCreate()\n",
            """        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: android.os.Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) {
                if (isLegacyBuild()) showLegacyBuildGate(activity)
            }
        })
""",
            "Legacy gate lifecycle",
        )
    s = p.read_text()
    marker = "    private fun initLastfm() {\n"
    addition = """    private fun isLegacyBuild(): Boolean {
        fun key(version: String): List<Int> {
            val parts = version.removePrefix(\"v\").split(\".\").map { it.toIntOrNull() ?: 0 }
            if (parts.size == 4 && parts[2] == 1) {
                return listOf(parts[0], parts[1], parts[3], 1)
            }
            return listOf(parts.getOrElse(0) { 0 }, parts.getOrElse(1) { 0 }, parts.getOrElse(2) { 0 }, 0)
        }
        val current = key(BuildConfig.VERSION_NAME)
        val minimum = key(\"1.5.1.4\")
        return current.zip(minimum).firstOrNull { (a, b) -> a != b }?.let { it.first < it.second } == true
    }

    private fun showLegacyBuildGate(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (activity is androidx.appcompat.app.AppCompatActivity && activity.isChangingConfigurations) return
        AlertDialog.Builder(activity)
            .setTitle(\"Pexpo update required\")
            .setMessage(\"This version of Pexpo is no longer supported. Please install Pexpo 1.5.1.4 or newer from Pexpo Updates.\")
            .setCancelable(false)
            .setPositiveButton(\"Download latest\") { _, _ ->
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(\"https://pexpoupdates.xo.je\")))
                activity.finishAndRemoveTask()
            }
            .show()
    }

"""
    if "private fun isLegacyBuild()" not in s:
        replace_once(p.as_posix(), marker, addition + marker, "Legacy gate methods")

print("Pexpo bugfix patch applied and verified: account avatar refresh, transport-aware network indicator, legacy build gate.")
