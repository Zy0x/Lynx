package com.noir.lynx.designsystem.tweak

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.designsystem.theme.AccentCyan
import com.noir.lynx.designsystem.theme.AccentGreen
import com.noir.lynx.designsystem.theme.AccentOrange
import com.noir.lynx.designsystem.theme.BgDeepOled
import com.noir.lynx.designsystem.theme.BgElevated
import com.noir.lynx.designsystem.theme.BorderGlass
import com.noir.lynx.designsystem.theme.BorderSubtle
import com.noir.lynx.designsystem.theme.TextPrimary
import com.noir.lynx.designsystem.theme.TextSecondary
import com.noir.lynx.designsystem.theme.TextTertiary

/**
 * LynxSwitchInfoSheet — Comprehensive Tweak Education Drawer with i18n support.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LynxSwitchInfoSheet(
    info: SwitchTweakInfo,
    onDismiss: () -> Unit
) {
    var checkedState by remember(info.id) {
        mutableStateOf(info.isChecked)
    }
    var lastInteractionTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(info.id, info.isChecked) {
        val now = System.currentTimeMillis()
        if (now - lastInteractionTime > 3500L) {
            checkedState = info.isChecked
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFA0D1017),
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Surface(
                color = Color(0x33FFFFFF),
                shape = CircleShape,
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 38.dp, height = 4.dp)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            // Category Badge & Current Status Pill
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AccentCyan.copy(alpha = 0.12f),
                    border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = info.category.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (checkedState) AccentGreen.copy(alpha = 0.15f) else BgElevated,
                    border = BorderStroke(0.8.dp, if (checkedState) AccentGreen.copy(alpha = 0.4f) else BorderGlass)
                ) {
                    Text(
                        text = if (checkedState) stringResource(R.string.status_active) else stringResource(R.string.status_inactive),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (checkedState) AccentGreen else TextSecondary,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            // Title
            Text(
                text = info.title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(Modifier.height(12.dp))

            // Interactive Toggle Control Card inside the Sheet
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF12151F),
                border = BorderStroke(1.dp, if (checkedState) AccentCyan.copy(alpha = 0.35f) else BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = if (checkedState) stringResource(R.string.switch_status_active) else stringResource(R.string.switch_status_inactive),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (checkedState) AccentCyan else TextPrimary
                        )
                        Text(
                            text = stringResource(R.string.switch_status_hint),
                            fontSize = 10.5.sp,
                            color = TextSecondary,
                            lineHeight = 14.sp
                        )
                    }

                    Switch(
                        checked = checkedState,
                        onCheckedChange = { newState ->
                            lastInteractionTime = System.currentTimeMillis()
                            checkedState = newState
                            info.onCheckedChange(newState)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BgDeepOled,
                            checkedTrackColor = AccentCyan,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = BgElevated,
                            uncheckedBorderColor = BorderSubtle,
                        )
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Explanation / Function
            Text(
                text = stringResource(R.string.header_kernel_function),
                color = TextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = info.description,
                color = TextPrimary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )

            Spacer(Modifier.height(14.dp))

            // Impact Comparison: ON vs OFF
            Text(
                text = stringResource(R.string.header_status_comparison),
                color = TextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Spacer(Modifier.height(6.dp))

            // ON State Card
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, if (checkedState) AccentCyan.copy(alpha = 0.4f) else BorderSubtle),
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(16.dp).padding(top = 1.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(info.onStatusText, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(info.onEffect, fontSize = 10.5.sp, color = TextSecondary, lineHeight = 14.sp)
                    }
                }
            }

            // OFF State Card
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, if (!checkedState) AccentOrange.copy(alpha = 0.4f) else BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(16.dp).padding(top = 1.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(info.offStatusText, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(info.offEffect, fontSize = 10.5.sp, color = TextSecondary, lineHeight = 14.sp)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Recommendations
            Text(
                text = stringResource(R.string.header_scenario_recommendations),
                color = TextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Spacer(Modifier.height(6.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("• ${stringResource(R.string.label_gaming_heavy)}: ${info.gamingRecommendation}", fontSize = 10.5.sp, color = TextPrimary)
                    Text("• ${stringResource(R.string.label_daily_balanced)}: ${info.balancedRecommendation}", fontSize = 10.5.sp, color = TextSecondary)
                    Text("• ${stringResource(R.string.label_battery_saver)}: ${info.batteryRecommendation}", fontSize = 10.5.sp, color = TextSecondary)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Close button
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(42.dp)
            ) {
                Text(stringResource(R.string.close_guide), color = BgDeepOled, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}
