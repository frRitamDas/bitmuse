from pathlib import Path
import re

ROOT = Path('.')
NANOJSON = 'e9d656ddb49a412a5a0a5d5ef20ca7ef09549996'


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old in text:
        return text.replace(old, new, 1)
    if new in text:
        return text
    raise RuntimeError(f'{label}: expected marker not found')


def patch_build() -> None:
    path = ROOT / 'app/build.gradle.kts'
    text = path.read_text(encoding='utf-8')

    text = re.sub(r'compileSdk = \d+', 'compileSdk = 35', text, count=1)
    text = re.sub(r'minSdk = \d+', 'minSdk = 21', text, count=1)
    text = re.sub(r'targetSdk = \d+', 'targetSdk = 35', text, count=1)
    text = re.sub(r'versionCode = \d+', 'versionCode = 21', text, count=1)
    text = re.sub(r'versionName = "[^"]+"', 'versionName = "1.5.7"', text, count=1)

    old_compile = '''    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }'''
    new_compile = '''    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }'''
    text = replace_once(text, old_compile, new_compile, 'Java/desugaring compile options')

    old_kotlin = '''kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}'''
    new_kotlin = '''kotlinOptions {
    jvmTarget = "1.8"
}'''
    text = replace_once(text, old_kotlin, new_kotlin, 'Kotlin JVM target')

    text = text.replace(
        'implementation("com.github.TeamNewPipe:nanojson:1d9e1aea9049fc9f85e68b43ba39fe7be1c1f751")',
        f'implementation("com.github.TeamNewPipe:nanojson:{NANOJSON}")',
    )
    if f'coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")' not in text:
        text = text.replace('dependencies {\n    val composeBom', 'dependencies {\n    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")\n    val composeBom', 1)

    required = [
        'compileSdk = 35',
        'minSdk = 21',
        'targetSdk = 35',
        'versionCode = 21',
        'versionName = "1.5.7"',
        'isCoreLibraryDesugaringEnabled = true',
        'sourceCompatibility = JavaVersion.VERSION_1_8',
        'targetCompatibility = JavaVersion.VERSION_1_8',
        'jvmTarget = "1.8"',
        f'com.github.TeamNewPipe:nanojson:{NANOJSON}',
        'com.android.tools:desugar_jdk_libs:2.1.4',
    ]
    missing = [item for item in required if item not in text]
    if missing:
        raise RuntimeError('build.gradle.kts verification failed: ' + ', '.join(missing))
    path.write_text(text, encoding='utf-8')


def patch_vendored_newpipe_utils() -> None:
    path = ROOT / 'app/src/main/java/org/schabi/newpipe/extractor/utils/Utils.java'
    text = path.read_text(encoding='utf-8')

    text = text.replace('import java.util.Arrays;\n', '')
    text = text.replace('import java.util.Objects;\n', '')
    text = text.replace('import java.util.stream.Collectors;\n', '')

    text = text.replace('return string == null || string.isBlank();', 'return string == null || string.trim().isEmpty();')

    old_join = '''        return elements.entrySet().stream()
                .map(entry -> entry.getKey() + mapJoin + entry.getValue())
                .collect(Collectors.joining(delimiter));'''
    new_join = '''        final StringBuilder joined = new StringBuilder();
        boolean first = true;
        for (final Map.Entry<? extends CharSequence, ? extends CharSequence> entry : elements.entrySet()) {
            if (!first) joined.append(delimiter);
            joined.append(entry.getKey()).append(mapJoin).append(entry.getValue());
            first = false;
        }
        return joined.toString();'''
    text = replace_once(text, old_join, new_join, 'NewPipe Utils join')

    old_non_empty = '''        return Arrays.stream(elements)
                .filter(s -> !isNullOrEmpty(s) && !s.equals("null"))
                .collect(Collectors.joining(delimiter));'''
    new_non_empty = '''        final StringBuilder joined = new StringBuilder();
        boolean first = true;
        for (final String element : elements) {
            if (isNullOrEmpty(element) || element.equals("null")) continue;
            if (!first) joined.append(delimiter);
            joined.append(element);
            first = false;
        }
        return joined.toString();'''
    text = replace_once(text, old_non_empty, new_non_empty, 'NewPipe Utils non-empty join')

    old_regex = '''        return getStringResultFromRegexArray(input,
                Arrays.stream(regexes)
                        .filter(Objects::nonNull)
                        .map(Pattern::compile)
                        .toArray(Pattern[]::new),
                group);'''
    new_regex = '''        final Pattern[] compiled = new Pattern[regexes.length];
        int count = 0;
        for (final String regex : regexes) {
            if (regex != null) compiled[count++] = Pattern.compile(regex);
        }
        return getStringResultFromRegexArray(
                input, java.util.Arrays.copyOf(compiled, count), group);'''
    text = replace_once(text, old_regex, new_regex, 'NewPipe Utils regex conversion')

    if '.stream()' in text or 'Arrays.stream' in text or 'streamAsJsonObjects' in text:
        raise RuntimeError('NewPipe Utils still contains Java stream API usage')
    path.write_text(text, encoding='utf-8')


def patch_account_avatar_cache() -> None:
    # The selector opens on explicit account-state refreshes. Google can change
    # the bytes behind the same avatar URL, so Coil's normal URL cache can keep
    # displaying yesterday's image even after Home has the fresh URL/content.
    path = ROOT / 'app/src/main/java/com/music/pexpo/ui/components/AccountProfileSelector.kt'
    text = path.read_text(encoding='utf-8')
    if 'CachePolicy.DISABLED' in text:
        return
    text = text.replace('import coil3.compose.AsyncImage\n', 'import coil3.compose.AsyncImage\nimport coil3.request.CachePolicy\nimport coil3.request.ImageRequest\nimport androidx.compose.ui.platform.LocalContext\n')
    old = '''@Composable private fun ProfileAvatar(profile: YouTubeProfile) {
    val model = profile.avatar?.trim()?.takeIf { it.isNotEmpty() }
    if (model != null) AsyncImage(model = model, contentDescription = profile.name, modifier = Modifier.size(38.dp).clip(CircleShape))
    else Box(Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Person, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
}'''
    new = '''@Composable private fun ProfileAvatar(profile: YouTubeProfile) {
    val model = profile.avatar?.trim()?.takeIf { it.isNotEmpty() }
    if (model != null) {
        val context = LocalContext.current
        val request = remember(model) {
            ImageRequest.Builder(context)
                .data(model)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .diskCachePolicy(CachePolicy.DISABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .build()
        }
        AsyncImage(model = request, contentDescription = profile.name, modifier = Modifier.size(38.dp).clip(CircleShape))
    } else Box(Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Person, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
}'''
    text = replace_once(text, old, new, 'account selector avatar cache')
    path.write_text(text, encoding='utf-8')


def verify_source_streams() -> None:
    violations = []
    for path in (ROOT / 'app/src').rglob('*'):
        if not path.is_file() or path.suffix.lower() not in {'.kt', '.java'}:
            continue
        text = path.read_text(encoding='utf-8', errors='ignore')
        if 'streamAsJsonObjects' in text or re.search(r'\.stream\s*\(', text):
            violations.append(str(path))
    if violations:
        raise RuntimeError('Java stream usage remains in app/src: ' + ', '.join(violations))


def verify_release_identity() -> None:
    gradle = (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8')
    if 'namespace = "com.music.pexpo"' not in gradle or 'applicationId = "com.music.pexpo"' not in gradle:
        raise RuntimeError('Pexpo production identity is incorrect')


if __name__ == '__main__':
    patch_build()
    patch_vendored_newpipe_utils()
    patch_account_avatar_cache()
    verify_source_streams()
    verify_release_identity()
    print('Pexpo 1.5.7 universal compatibility patch applied and verified.')
