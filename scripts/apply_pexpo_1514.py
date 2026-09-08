from pathlib import Path


def edit(path, fn):
    p = Path(path)
    text = p.read_text()
    new = fn(text)
    if new == text:
        raise SystemExit(f"No change made to {path}")
    p.write_text(new)


def build_gradle(s):
    s = s.replace("versionCode = 16", "versionCode = 17", 1)
    s = s.replace('versionName = "1.5.4"', 'versionName = "1.5.1.4"', 1)
    return s


def manifest(s):
    if 'android:windowSoftInputMode="adjustResize"' in s:
        return s
    anchor = 'android:launchMode="singleTask"\n            android:screenOrientation="portrait"'
    if anchor not in s:
        raise SystemExit("MainActivity manifest anchor not found")
    return s.replace(anchor, anchor + '\n            android:windowSoftInputMode="adjustResize"', 1)


def main_activity(s):
    if 'import androidx.compose.foundation.layout.imePadding' not in s:
        anchor = 'import androidx.compose.foundation.layout.height\n'
        if anchor not in s:
            raise SystemExit("MainActivity import anchor not found")
        s = s.replace(anchor, anchor + 'import androidx.compose.foundation.layout.imePadding\n', 1)
    old = 'BoxWithConstraints(Modifier.fillMaxSize()) {\n                    BitChordApp('
    if old in s:
        return s.replace(old, 'BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {\n                    BitChordApp(', 1)
    if 'BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {' in s:
        return s
    raise SystemExit("MainActivity root BoxWithConstraints anchor not found")


def updater(s):
    while 'import android.content.pm.PackageManager\nimport android.content.pm.PackageManager' in s:
        s = s.replace('import android.content.pm.PackageManager\nimport android.content.pm.PackageManager', 'import android.content.pm.PackageManager', 1)
    old = '''    private fun isNewer(latest: String, current: String): Boolean {
        val l = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val c = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(l.size, c.size)) {
            val a = l.getOrElse(i) { 0 }; val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }'''
    new = '''    private fun isNewer(latest: String, current: String): Boolean {
        fun key(version: String): List<Int> {
            val parts = version.split(".").map { it.toIntOrNull() ?: 0 }
            // 1.5.1.4 is the major maintenance patch attached to 1.5.4.
            // Order it between 1.5.4 and 1.5.5.
            if (parts.size == 4 && parts[2] == 1) {
                return listOf(parts[0], parts[1], parts[3], 1)
            }
            return listOf(
                parts.getOrElse(0) { 0 },
                parts.getOrElse(1) { 0 },
                parts.getOrElse(2) { 0 },
                0,
            )
        }

        val l = key(latest)
        val c = key(current)
        for (i in l.indices) {
            if (l[i] != c[i]) return l[i] > c[i]
        }
        return false
    }'''
    if old not in s:
        raise SystemExit("AppUpdateChecker comparator anchor not found")
    return s.replace(old, new, 1)


def android_workflow(s):
    replacements = [
        ('Build Pexpo v1.5.4', 'Build Pexpo v1.5.1.4'),
        ('pexpo-1.5.4-universal.apk', 'pexpo-1.5.1.4-universal.apk'),
        ('pexpo-1.5.4-arm64-v8a.apk', 'pexpo-1.5.1.4-arm64-v8a.apk'),
        ('pexpo-1.5.4-armeabi-v7a.apk', 'pexpo-1.5.1.4-armeabi-v7a.apk'),
        ('pexpo-1.5.4-x86_64.apk', 'pexpo-1.5.1.4-x86_64.apk'),
        ('pexpo-v1.5.4-release', 'pexpo-v1.5.1.4-release'),
        ('Publish GitHub Release v1.5.4', 'Publish GitHub Release v1.5.1.4'),
        ('gh release view v1.5.4', 'gh release view v1.5.1.4'),
        ('gh release create v1.5.4', 'gh release create v1.5.1.4'),
        ("--title 'Pexpo Music v1.5.4'", "--title 'Pexpo Music v1.5.1.4'"),
    ]
    for old, new in replacements:
        if old not in s:
            raise SystemExit(f"Android workflow anchor not found: {old}")
        s = s.replace(old, new, 1)
    s = s.replace(
        "Pexpo Music v1.5.4\\n\\n### Improved system stability and bug fixes",
        "Pexpo Music v1.5.1.4\\n\\n### Major bug and system fixes for the Pexpo 1.5.4 release line",
        1,
    )
    return s


edit('app/build.gradle.kts', build_gradle)
edit('app/src/main/AndroidManifest.xml', manifest)
edit('app/src/main/java/com/music/bitchord/MainActivity.kt', main_activity)
edit('app/src/main/java/com/music/bitchord/data/AppUpdateChecker.kt', updater)
edit('.github/workflows/android.yml', android_workflow)

source_formats = Path('app/src/main/java/com/music/bitchord/data/sources/addon/SourceFormats.kt')
source_text = source_formats.read_text()
source_new = source_text.replace(
    'That is not a web address BitChord can open',
    'That is not a web address Pexpo can open',
)
if source_new != source_text:
    source_formats.write_text(source_new)
