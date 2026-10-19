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
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.ModeToastData
import com.noir.lynx.data.ToastType
import com.noir.lynx.designsystem.theme.TextPrimary

internal val ToastBgObsidian = Color(0xFF090D14)

@Composable
fun LynxModeToastHud(
    toastData: ModeToastData?,
    feedbackStyle: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isModeToast = toastData?.type == ToastType.MODE
    // Mode transitions only show in HUD if jewel_top or jewel_bottom is selected.
    // Non-mode toasts (ACTION, ERROR, SUCCESS, INFO) are ALWAYS visible to provide user feedback.
    val isVisible = toastData != null && when {
        !isModeToast -> true
        feedbackStyle == "jewel_top" || feedbackStyle == "jewel_bottom" -> true
        else -> false // system_toast, status_bar, disabled, header_morph
    }

    val isBottom = feedbackStyle == "jewel_bottom"

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (isBottom) {
                    Modifier
                        .navigationBarsPadding()
                        .padding(bottom = 96.dp)
                } else {
                    Modifier
                        .statusBarsPadding()
                        .padding(top = 4.dp)
                }
            ),
        contentAlignment = if (isBottom) Alignment.BottomCenter else Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = if (isBottom) {
                slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 400f)
                ) + fadeIn(animationSpec = tween(150))
            } else {
                slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = spring(dampingRatio = 0.75f, stiffness = 420f)
                ) + fadeIn(animationSpec = tween(140))
            },
            exit = if (isBottom) {
                slideOutVertically(
                    targetOffsetY = { it / 2 },
                    animationSpec = tween(150)
                ) + fadeOut(animationSpec = tween(120))
            } else {
                slideOutVertically(
                    targetOffsetY = { -it },
                    animationSpec = tween(160)
                ) + fadeOut(animationSpec = tween(120))
            }
        ) {
            if (toastData != null) {
                JewelToastPill(toastData = toastData, onDismiss = onDismiss, isCompactMini = isBottom)
            }
        }
    }
}

@Composable
internal fun JewelToastPill(
    toastData: ModeToastData,
    onDismiss: () -> Unit,
    isCompactMini: Boolean
) {
    val accentColor by animateColorAsState(
        targetValue = Color(toastData.accentColorHex),
        animationSpec = tween(200),
        label = "ToastAccentColor"
    )

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
        AnimatedContent(
            targetState = toastData,
            transitionSpec = {
                (fadeIn(animationSpec = tween(160)) + scaleIn(initialScale = 0.94f, animationSpec = tween(160)))
                    .togetherWith(fadeOut(animationSpec = tween(120)) + scaleOut(targetScale = 0.94f, animationSpec = tween(120)))
            },
            label = "JewelToastContentTransition"
        ) { currentToast ->
            val currentAccent = Color(currentToast.accentColorHex)
            val currentIcon = resolveToastIcon(currentToast)
            val currentTag = currentToast.statusTag.ifBlank { "AKTIF" }

            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = if (isCompactMini) 3.5.dp else 4.5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.5.dp)
            ) {
                // Glowing Jewel Dot
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(currentAccent, CircleShape)
                        .shadow(elevation = 4.dp, shape = CircleShape, ambientColor = currentAccent, spotColor = currentAccent)
                )

                // Micro Mode / Action Icon
                Icon(
                    imageVector = currentIcon,
                    contentDescription = null,
                    tint = currentAccent,
                    modifier = Modifier.size(12.dp)
                )

                // Toast Message / Label
                Text(
                    text = currentToast.modeLabel,
                    color = TextPrimary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0"),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 240.dp)
                )

                // Status Tag Chip
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = currentAccent.copy(alpha = 0.16f),
                    border = BorderStroke(0.5.dp, currentAccent.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = currentTag,
                        color = currentAccent,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.3.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

internal fun resolveToastIcon(toast: ModeToastData): ImageVector = when {
    toast.iconType.isNotBlank() -> when (toast.iconType.lowercase()) {
        "check", "success" -> Icons.Default.CheckCircle
        "error", "gagal" -> Icons.Default.ErrorOutline
        "warning", "alert" -> Icons.Default.Warning
        "info" -> Icons.Default.Info
        "cpu" -> Icons.Default.Speed
        "gpu" -> Icons.Default.SportsEsports
        "ram", "memory" -> Icons.Default.DeleteSweep
        "battery", "charging" -> Icons.Default.BatteryChargingFull
        "network", "wifi" -> Icons.Default.Wifi
        "thermal" -> Icons.Default.Thermostat
        else -> Icons.Default.CheckCircle
    }
    toast.type == ToastType.ERROR -> Icons.Default.ErrorOutline
    toast.type == ToastType.WARNING -> Icons.Default.Warning
    toast.type == ToastType.SUCCESS -> Icons.Default.CheckCircle
    toast.modeId.equals("powersave", ignoreCase = true) -> Icons.Default.BatteryChargingFull
    toast.modeId.equals("balance", ignoreCase = true) -> Icons.Default.Tune
    toast.modeId.equals("performance", ignoreCase = true) -> Icons.Default.Speed
    toast.modeId.equals("extreme", ignoreCase = true) -> Icons.Default.LocalFireDepartment
    else -> Icons.Default.CheckCircle
}
