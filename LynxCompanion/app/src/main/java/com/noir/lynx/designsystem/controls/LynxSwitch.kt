package com.noir.lynx.designsystem.controls

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.designsystem.theme.AccentCyan
import com.noir.lynx.designsystem.theme.BgDeepOled
import com.noir.lynx.designsystem.theme.BgElevated
import com.noir.lynx.designsystem.theme.BorderSubtle
import com.noir.lynx.designsystem.theme.TextPrimary
import com.noir.lynx.designsystem.theme.TextSecondary
import com.noir.lynx.designsystem.theme.TextTertiary

/**
 * LynxSwitch — Luxury Toggle Row with debounced interaction guard and optional education info sheet trigger.
 */
@Composable
fun LynxSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subLabel: String? = null,
    enabled: Boolean = true,
    onInfoClick: (() -> Unit)? = null,
) {
    var localChecked by remember { mutableStateOf(checked) }
    var lastInteractionTime by remember { mutableLongStateOf(0L) }
    var lastClickTime by remember { mutableLongStateOf(0L) }

    // Synchronize incoming external changes once background execution has settled (600ms)
    LaunchedEffect(checked) {
        val now = System.currentTimeMillis()
        if (now - lastInteractionTime > 600L) {
            localChecked = checked
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) {
                if (onInfoClick != null) {
                    onInfoClick()
                } else {
                    val now = System.currentTimeMillis()
                    if (now - lastClickTime >= 50L) {
                        lastClickTime = now
                        lastInteractionTime = now
                        val next = !localChecked
                        localChecked = next
                        onCheckedChange(next)
                    }
                }
            }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) TextPrimary else TextTertiary,
                )
                if (onInfoClick != null) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = stringResource(R.string.full_info_guide),
                        tint = TextTertiary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
            if (subLabel != null) {
                Text(
                    text = subLabel,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Switch(
            checked = localChecked,
            onCheckedChange = { newChecked ->
                val now = System.currentTimeMillis()
                if (now - lastClickTime >= 50L) {
                    lastClickTime = now
                    lastInteractionTime = now
                    localChecked = newChecked
                    onCheckedChange(newChecked)
                }
            },
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = BgDeepOled,
                checkedTrackColor = AccentCyan,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = BgElevated,
                uncheckedBorderColor = BorderSubtle,
            ),
        )
    }
}
