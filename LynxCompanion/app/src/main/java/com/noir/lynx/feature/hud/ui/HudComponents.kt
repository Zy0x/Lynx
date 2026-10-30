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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.AppLocale
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.data.HudReading

// Opaque graphite surfaces keep game content separate from the overlay controls.
internal val HudCyan = Color(0xFF00F0FF)
internal val HudBlue = Color(0xFF60A5FA)
internal val HudBlueBadge = Color(0x400284C7)
internal val HudOrange = Color(0xFFFBBF24)
internal val HudRed = Color(0xFFF87171)
internal val HudPurple = Color(0xFFC084FC)
internal val HudBackground = Color(0xD90C121B)
internal val HudCard = Color(0x66182333)
internal val HudElevated = Color(0x8C243347)
internal val HudHandleSurface = Color(0x6612151B)
internal val HudBorder = Color(0x2EFFFFFF)
internal val HudAccentBorder = Color(0x5900F0FF)
internal val HudMuted = Color(0xFF94A3B8)

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
        color = containerColor, border = BorderStroke(1.dp, if (isDanger) HudRed.copy(alpha = 0.35f) else HudBorder),
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
