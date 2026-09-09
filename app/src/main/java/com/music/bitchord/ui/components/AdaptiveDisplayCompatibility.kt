package com.music.bitchord.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ViewCompact
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings

@Composable
fun AdaptiveDisplayCompatibilitySetting() {
    val compact by AppSettings.adaptiveDisplayCompatibility.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ViewCompact, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("Compact Mode", style = MaterialTheme.typography.titleMedium)
                Text("Denser spacing, smaller gaps and better use of narrow screens.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = compact,
                onCheckedChange = { AppSettings.adaptiveDisplayCompatibility.value = it },
            )
        }
        Text(
            "Useful for small phones, split-screen, floating windows and displays where the normal layout feels too large.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/**
 * Global compact-layout workaround used at the existing app root.
 * It combines width-aware density, short-window density and font scaling.
 * OFF is a true pass-through so the normal layout is unchanged.
 */
@Composable
fun AdaptiveDisplayCompatibility(content: @Composable () -> Unit) {
    val compact by AppSettings.adaptiveDisplayCompatibility.collectAsStateWithLifecycle()
    if (!compact) {
        content()
        return
    }

    val configuration = LocalConfiguration.current
    val baseDensity = LocalDensity.current
    val widthDp = configuration.screenWidthDp.coerceAtLeast(1)
    val heightDp = configuration.screenHeightDp.coerceAtLeast(1)

    // 360dp is the compact-phone baseline. Never enlarge a normal layout.
    val widthScale = (widthDp / 360f).coerceIn(0.86f, 1f)

    // Split-screen/floating windows can be short even when their width is fine.
    val heightScale = when {
        heightDp < 480 -> (heightDp / 560f).coerceIn(0.90f, 1f)
        heightDp < 600 -> (heightDp / 600f).coerceIn(0.94f, 1f)
        else -> 1f
    }

    val scale = minOf(widthScale, heightScale)
    val fontScale = (baseDensity.fontScale * scale).coerceIn(0.88f, baseDensity.fontScale)

    CompositionLocalProvider(
        LocalDensity provides Density(
            density = baseDensity.density * scale,
            fontScale = fontScale,
        ),
    ) {
        // Do not depend on WindowInsets.safeDrawing here: this project’s
        // Compose Foundation version does not expose that API. The existing
        // app/root insets remain responsible for system-bar handling.
        Box(Modifier.fillMaxWidth()) {
            content()
        }
    }
}
