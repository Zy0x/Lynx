package com.noir.lynx.feature.hud.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.AppLocale
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.data.HudReading

internal val HudCyan = Color(0xFF00F5A0) // Luminous Cyber Mint
internal val HudBlue = Color(0xFF00C2FF) // Electric Azure
internal val HudOrange = Color(0xFFFF9F2E) // Sunset Amber Flame
internal val HudRed = Color(0xFFFF4D6D) // Tactical Crimson
internal val HudPurple = Color(0xFFA855F7) // Royal Amethyst
internal val HudBackground = Color(0xF5080B10) // Deep Obsidian Glass OLED
internal val HudCard = Color(0xFF10151F) // Rich Graphite Bento Surface
internal val HudElevated = Color(0xFF171E2C) // Elevated Interactive Tile
internal val HudBorder = Color(0x24FFFFFF) // 14% Crisp Glass Hairline
internal val HudAccentBorder = Color(0x4500F5A0) // Cyber Mint Accent Border
internal val HudMuted = Color(0xFF94A3B8) // Cool Slate Grey

@Composable
internal fun hudString(@StringRes id: Int, vararg args: Any): String =
    AppLocale.getString(LocalContext.current, id, *args)

@Composable
internal fun HudTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = HudCyan,
            onPrimary = Color.Black,
            background = HudBackground,
            surface = HudBackground,
            onSurface = Color(0xFFF8FAFC),
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
        fontSize = if (large) 28.sp else 16.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        color = if (reading.value != null && reading.status == "valid") Color.White else HudMuted,
        maxLines = 1
    )
}

@Composable
internal fun HudAction(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accentColor: Color = HudCyan,
    isDanger: Boolean = false
) {
    val containerColor = when {
        isDanger -> HudRed.copy(alpha = 0.12f)
        else -> HudElevated
    }
    val borderColor = when {
        !enabled -> HudBorder.copy(alpha = 0.08f)
        isDanger -> HudRed.copy(alpha = 0.45f)
        else -> HudBorder
    }
    val textColor = when {
        !enabled -> HudMuted.copy(alpha = 0.4f)
        isDanger -> HudRed
        else -> Color(0xFFF1F5F9)
    }

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = textColor,
            disabledContainerColor = HudCard.copy(alpha = 0.5f),
            disabledContentColor = HudMuted.copy(alpha = 0.4f)
        ),
        modifier = modifier.heightIn(min = 44.dp),
        border = BorderStroke(1.dp, borderColor),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = if (!enabled) HudMuted.copy(alpha = 0.4f) else if (isDanger) HudRed else accentColor
                )
            }
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
