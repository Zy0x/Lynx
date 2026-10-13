package com.noir.lynx.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.ModeToastData
import com.noir.lynx.designsystem.theme.TextPrimary

private val ToastBgObsidian = Color(0xFF090D14)

@Composable
fun LynxModeToastHud(
    toastData: ModeToastData?,
    feedbackStyle: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isVisible = toastData != null && feedbackStyle != "header_morph"

    when (feedbackStyle) {
        "jewel_bottom" -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(bottom = 96.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                AnimatedVisibility(
                    visible = isVisible,
                    enter = slideInVertically(
                        initialOffsetY = { it / 2 },
                        animationSpec = spring(dampingRatio = 0.78f, stiffness = 400f)
                    ) + fadeIn(animationSpec = tween(150)),
                    exit = slideOutVertically(
                        targetOffsetY = { it / 2 },
                        animationSpec = tween(150)
                    ) + fadeOut(animationSpec = tween(120))
                ) {
                    if (toastData != null) {
                        JewelToastPill(toastData = toastData, onDismiss = onDismiss, isCompactMini = true)
                    }
                }
            }
        }
        else -> {
            // Default: "jewel_top" (Jewel Dynamic Island)
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = 4.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                AnimatedVisibility(
                    visible = isVisible,
                    enter = slideInVertically(
                        initialOffsetY = { -it },
                        animationSpec = spring(dampingRatio = 0.75f, stiffness = 420f)
                    ) + fadeIn(animationSpec = tween(140)),
                    exit = slideOutVertically(
                        targetOffsetY = { -it },
                        animationSpec = tween(160)
                    ) + fadeOut(animationSpec = tween(120))
                ) {
                    if (toastData != null) {
                        JewelToastPill(toastData = toastData, onDismiss = onDismiss, isCompactMini = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun JewelToastPill(
    toastData: ModeToastData,
    onDismiss: () -> Unit,
    isCompactMini: Boolean
) {
    val accentColor = Color(toastData.accentColorHex)
    val icon = resolveModeIcon(toastData.modeId)

    Surface(
        shape = CircleShape,
        color = ToastBgObsidian,
        border = BorderStroke(0.85.dp, accentColor.copy(alpha = 0.55f)),
        shadowElevation = 8.dp,
        modifier = Modifier
            .defaultMinSize(minHeight = if (isCompactMini) 26.dp else 28.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = if (isCompactMini) 3.5.dp else 4.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.5.dp)
        ) {
            // Glowing Jewel Dot
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(accentColor, CircleShape)
                    .shadow(elevation = 4.dp, shape = CircleShape, ambientColor = accentColor, spotColor = accentColor)
            )

            // Micro Mode Icon
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(12.dp)
            )

            // Mode Name
            Text(
                text = toastData.modeLabel,
                color = TextPrimary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
            )

            // Status Tag Chip
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = accentColor.copy(alpha = 0.16f),
                border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.4f))
            ) {
                Text(
                    text = "AKTIF",
                    color = accentColor,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.3.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}

private fun resolveModeIcon(modeId: String): ImageVector = when (modeId.lowercase()) {
    "powersave" -> Icons.Default.BatteryChargingFull
    "balance" -> Icons.Default.Tune
    "performance" -> Icons.Default.Speed
    "extreme" -> Icons.Default.LocalFireDepartment
    else -> Icons.Default.CheckCircle
}
