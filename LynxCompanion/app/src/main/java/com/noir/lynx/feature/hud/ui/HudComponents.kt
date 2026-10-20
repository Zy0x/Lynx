package com.noir.lynx.feature.hud.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.AppLocale
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.data.HudReading

internal val HudCyan = Color(0xFF58E5D1)
internal val HudBackground = Color(0xFF090D10)
internal val HudCard = Color(0xFF151C20)
internal val HudMuted = Color(0xFFA6B4BB)

@Composable
internal fun hudString(@StringRes id: Int, vararg args: Any): String = AppLocale.getString(LocalContext.current, id, *args)

@Composable
internal fun HudTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = HudCyan, onPrimary = Color.Black,
        background = HudBackground, surface = HudBackground, onSurface = Color(0xFFF0F6F7),
        surfaceVariant = HudCard, onSurfaceVariant = HudMuted), content = content)
}

@Composable
internal fun HudValue(reading: HudReading, large: Boolean = false) {
    Text(text = reading.text(), fontSize = if (large) 32.sp else 18.sp,
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = if (reading.value != null && reading.status == "valid") Color.White else HudMuted,
        maxLines = 1)
}

@Composable
internal fun HudAction(label: String, enabled: Boolean = true, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp),
        border = BorderStroke(1.dp, HudMuted.copy(alpha = 0.22f)), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
        Text(text = label, fontSize = 12.sp)
    }
}

@Composable
internal fun statusText(status: String): String = hudString(when (status) {
    "valid" -> R.string.hud_valid
    "stale" -> R.string.hud_stale
    "unsupported" -> R.string.hud_unsupported
    "permission_denied" -> R.string.hud_permission_denied
    else -> R.string.hud_warming_up
})
