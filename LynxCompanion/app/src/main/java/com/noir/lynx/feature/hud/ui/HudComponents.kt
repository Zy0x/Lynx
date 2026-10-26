package com.noir.lynx.feature.hud.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.AppLocale
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.data.HudReading

// Google Play Games Sidekick Material 3 Dark Palette (High-Readability Tonal Contrast)
internal val HudCyan = Color(0xFF43E5C1)            // Play Games Emerald Mint
internal val HudBlue = Color(0xFF74B9FF)            // Sidekick Sky Blue Link/Accent
internal val HudBlueBadge = Color(0xFF134D74)       // Sidekick Top Pill Cobalt Blue
internal val HudOrange = Color(0xFFFBBF24)
internal val HudRed = Color(0xFFF87171)
internal val HudPurple = Color(0xFFC084FC)
internal val HudBackground = Color(0xF50B1016)      // 93% Deep Obsidian Drawer (prevents background text bleed-through)
internal val HudCard = Color(0xFF171E27)            // 96% Matte Bento Card Surface
internal val HudElevated = Color(0xFF202B37)        // Elevated Pill Surface
internal val HudHandleSurface = Color(0x6612151B)   // 40% Frosted Edge Handle Tab
internal val HudBorder = Color(0x18FFFFFF)          // Subtle 9% divider hairline
internal val HudAccentBorder = Color(0x338AB4F8)
internal val HudMuted = Color(0xFF9BAABD)           // Material 3 Neutral Slate

@Composable
internal fun hudString(@StringRes id: Int, vararg args: Any): String =
    AppLocale.getString(LocalContext.current, id, *args)

@Composable
internal fun HudTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = HudBlue,
            onPrimary = Color.Black,
            background = HudBackground,
            surface = HudBackground,
            onSurface = Color(0xFFE8EAED),
            surfaceVariant = HudCard,
            onSurfaceVariant = HudMuted
        ),
        typography = MaterialTheme.typography.copy(
            bodyLarge = MaterialTheme.typography.bodyLarge.copy(
                lineHeight = TextUnit.Unspecified,
                letterSpacing = 0.sp
            )
        ),
        content = content
    )
}

@Composable
internal fun HudValue(reading: HudReading, large: Boolean = false) {
    Text(
        text = reading.text(),
        fontSize = if (large) 22.sp else 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (reading.value != null && reading.status == "valid") Color.White else HudMuted
    )
}

@Composable
internal fun HudAction(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accentColor: Color = HudBlue,
    isDanger: Boolean = false
) {
    val containerColor = when {
        !enabled -> HudCard.copy(alpha = 0.45f)
        isDanger -> HudRed.copy(alpha = 0.16f)
        else -> HudElevated
    }
    val textColor = when {
        !enabled -> HudMuted.copy(alpha = 0.4f)
        isDanger -> HudRed
        else -> Color(0xFFE8EAED)
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (!enabled) HudMuted.copy(alpha = 0.4f) else if (isDanger) HudRed else accentColor
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        }
    }
}

@Composable
internal fun statusText(status: String): String = hudString(
    when (status) {
        "valid" -> R.string.hud_valid
        "stale" -> R.string.hud_stale
        "unsupported" -> R.string.hud_unsupported
        "permission_denied" -> R.string.hud_permission_denied
        else -> R.string.hud_warming_up
    }
)
