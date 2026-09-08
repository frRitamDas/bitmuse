package com.music.bitchord.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings

@Composable
fun AdaptiveDisplayCompatibilitySetting() {
    val enabled by AppSettings.adaptiveDisplayCompatibility.collectAsStateWithLifecycle()
    val red = Color(0xFFFF2B2B)
    val panel = Color(0xFF351010)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).background(panel, RoundedCornerShape(18.dp)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Warning, null, tint = red, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("Adaptive Display Compatibility", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text("Automatically adapts the interface when your device clips or misplaces UI.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .72f))
            }
            Switch(checked = enabled, onCheckedChange = { AppSettings.adaptiveDisplayCompatibility.value = it })
        }
        Text("CAUTION — USE ONLY IF YOU HAVE A DISPLAY OR LAYOUT PROBLEM", style = MaterialTheme.typography.labelSmall, color = red, modifier = Modifier.padding(top = 12.dp))
        Text("Do not enable unnecessarily. If Pexpo already fits correctly, leave this OFF.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .68f), modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun AdaptiveDisplayCompatibility(content: @Composable () -> Unit) {
    val enabled by AppSettings.adaptiveDisplayCompatibility.collectAsStateWithLifecycle()
    val base = LocalDensity.current
    val configuration = LocalConfiguration.current
    val scale = if (!enabled) 1f else when {
        configuration.screenWidthDp < 360 -> .90f
        base.fontScale > 1.20f -> (1.05f / base.fontScale).coerceIn(.86f, 1f)
        else -> 1f
    }
    CompositionLocalProvider(LocalDensity provides Density(base.density * scale, base.fontScale * scale), content = content)
}
