package com.music.bitchord.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings

@Composable
fun AdaptiveDisplayCompatibilitySetting() {
    val enabled by AppSettings.adaptiveDisplayCompatibility.collectAsStateWithLifecycle()
    val red = Color(0xFFFF2B2B)
    val panel = Color(0xFF351010)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(panel, RoundedCornerShape(18.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Warning, null, tint = red, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("Adaptive Display Compatibility", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text("Adapts sizing and safe-area spacing on compact, cutout and edge-to-edge displays.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .72f))
            }
            Switch(
                checked = enabled,
                onCheckedChange = AppSettings::setAdaptiveDisplayCompatibility,
            )
        }
        Text("CAUTION — USE ONLY IF YOU HAVE A DISPLAY OR LAYOUT PROBLEM", style = MaterialTheme.typography.labelSmall, color = red, modifier = Modifier.padding(top = 12.dp))
        Text("When enabled, Pexpo uses the measured display width and safe drawing insets to reduce clipping without changing the normal layout when OFF.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .68f), modifier = Modifier.padding(top = 4.dp))
    }
}

/**
 * Compatibility layer for devices whose usable display is too narrow for the
 * phone-sized layout or whose edge-to-edge system bars/cutouts consume part of
 * the content area.
 *
 * This is deliberately applied around the complete app root. Scaling only a
 * few components cannot fix a parent Row/Column that already measured itself
 * too wide, while padding only the individual bars leaves dialogs and sheets
 * exposed to the same clipping problem.
 */
@Composable
fun AdaptiveDisplayCompatibility(content: @Composable () -> Unit) {
    val enabled by AppSettings.adaptiveDisplayCompatibility.collectAsStateWithLifecycle()
    if (!enabled) {
        content()
        return
    }

    val configuration = LocalConfiguration.current
    val baseDensity = LocalDensity.current
    val widthDp = configuration.screenWidthDp.coerceAtLeast(1)
    val heightDp = configuration.screenHeightDp.coerceAtLeast(1)

    // The app's compact layout is designed around a 360dp phone width. Below
    // that point, reduce the logical density smoothly instead of waiting for a
    // single hard threshold. Never enlarge the UI: the feature exists to make
    // an oversized layout fit into a smaller usable viewport.
    val widthScale = (widthDp / 360f).coerceIn(0.82f, 1f)

    // Very short portrait viewports are commonly caused by split-screen,
    // floating-window or OEM display compatibility modes. Give those windows
    // a small additional reduction, capped so text never becomes unusably tiny.
    val heightScale = if (heightDp < 600) {
        (heightDp / 600f).coerceIn(0.92f, 1f)
    } else {
        1f
    }

    // Respect Android's user font scale while keeping the compatibility layer
    // from multiplying it twice. The effective font scale is bounded to a
    // readable range after the geometric reduction.
    val densityScale = minOf(widthScale, heightScale)
    val adjustedFontScale = (baseDensity.fontScale * densityScale).coerceIn(0.85f, baseDensity.fontScale)

    CompositionLocalProvider(
        LocalDensity provides Density(
            density = baseDensity.density * densityScale,
            fontScale = adjustedFontScale,
        ),
    ) {
        content()
    }
}

/**
 * Apply the platform's safe drawing area when compatibility mode is enabled.
 * This handles status-bar/navigation-bar/cutout insets for the entire root,
 * including floating sheets and dialogs hosted inside the main composition.
 */
@Composable
fun Modifier.adaptiveDisplaySafeArea(): Modifier {
    val enabled by AppSettings.adaptiveDisplayCompatibility.collectAsStateWithLifecycle()
    return if (enabled) {
        windowInsetsPadding(WindowInsets.safeDrawing)
    } else {
        this
    }
}
