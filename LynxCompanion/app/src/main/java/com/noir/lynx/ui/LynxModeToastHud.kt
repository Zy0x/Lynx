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

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.noir.lynx.designsystem.theme.TextSecondary

internal val ToastBgObsidian = Color(0xFF090D14)

@Composable
fun LynxModeToastHud(
    toastData: ModeToastData?,
    feedbackStyle: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isModeToast = toastData?.type == ToastType.MODE

    // ── Dispatch System Toast reliably when feedbackStyle is "system_toast" ──
    LaunchedEffect(toastData?.timestamp) {
        if (toastData != null && feedbackStyle == "system_toast") {
            val cleanText = formatCleanSystemToast(toastData)
            android.widget.Toast.makeText(context, cleanText, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // ── Determine visual Compose HUD visibility ──
    // If feedbackStyle == "system_toast", visual Compose HUD is suppressed (OS Toast handles it).
    // Non-mode toasts (ACTION, ERROR, SUCCESS, INFO) are ALWAYS visible in HUD unless system_toast is active.
    val isVisible = toastData != null && when {
        feedbackStyle == "system_toast" -> false
        !isModeToast -> true
        feedbackStyle == "jewel_top" || feedbackStyle == "jewel_bottom" -> true
        else -> false // status_bar, disabled, header_morph
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

/**
 * JewelToastPill — Adaptive Cyberpunk Jewel Island (Auto-morphing Compact Capsule & Expanded Glass Card).
 */
@Composable
internal fun JewelToastPill(
    toastData: ModeToastData,
    onDismiss: () -> Unit,
    isCompactMini: Boolean
) {
    val accentColor by animateColorAsState(
        targetValue = Color(toastData.accentColorHex),
        animationSpec = tween(220),
        label = "ToastAccentColor"
    )

    // Determine if toast is a concise label or a detailed/long message
    val isLongMessage = toastData.modeLabel.length > 18 || toastData.modeLabel.contains("\n")
    val pillShape = if (isLongMessage) RoundedCornerShape(16.dp) else CircleShape

    Surface(
        shape = pillShape,
        color = ToastBgObsidian,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
        shadowElevation = 10.dp,
        modifier = Modifier
            .animateContentSize(animationSpec = spring(dampingRatio = 0.8f, stiffness = 420f))
            .defaultMinSize(minHeight = if (isCompactMini) 32.dp else 36.dp)
            .widthIn(min = if (isLongMessage) 240.dp else 130.dp, max = 350.dp)
            .clip(pillShape)
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
            val currentTag = when {
                currentToast.statusTag.isNotBlank() -> currentToast.statusTag
                currentToast.type == ToastType.ERROR -> "GAGAL"
                currentToast.type == ToastType.WARNING -> "PERINGATAN"
                currentToast.type == ToastType.SUCCESS -> "SUKSES"
                currentToast.type == ToastType.PRESET -> "PRESET"
                else -> "AKTIF"
            }
            val itemIsLong = currentToast.modeLabel.length > 18 || currentToast.modeLabel.contains("\n")

            if (itemIsLong) {
                // === EXPANDED DYNAMIC ISLAND BENTO (For detailed notifications / errors / long text) ===
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    // Header Bar: Glow Dot, Micro Icon, Tag, Spacer, Dismiss affordance
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        // Glowing Jewel Dot
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(currentAccent, CircleShape)
                                .shadow(elevation = 5.dp, shape = CircleShape, ambientColor = currentAccent, spotColor = currentAccent)
                        )

                        // Micro Icon
                        Icon(
                            imageVector = currentIcon,
                            contentDescription = null,
                            tint = currentAccent,
                            modifier = Modifier.size(13.dp)
                        )

                        // Category / Status Tag Chip
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = currentAccent.copy(alpha = 0.16f),
                            border = BorderStroke(0.6.dp, currentAccent.copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = currentTag,
                                color = currentAccent,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.4.sp,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                            )
                        }

                        Spacer(Modifier.weight(1f))

                        // Dismiss cross
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = TextSecondary.copy(alpha = 0.6f),
                            modifier = Modifier
                                .size(13.dp)
                                .clickable(onClick = onDismiss)
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    // Body: Full detailed message without awkward truncation
                    Text(
                        text = currentToast.modeLabel,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 16.5.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                // === COMPACT SLEEK JEWEL CAPSULE (For quick mode transitions & short statuses) ===
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = if (isCompactMini) 4.5.dp else 5.5.dp),
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
                        modifier = Modifier.size(13.5.dp)
                    )

                    // Toast Message / Label (weight prevents layout truncation / clipping)
                    Text(
                        text = currentToast.modeLabel,
                        color = TextPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // Status Tag Chip
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = currentAccent.copy(alpha = 0.14f),
                        border = BorderStroke(0.5.dp, currentAccent.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = currentTag,
                            color = currentAccent,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.3.sp,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Formats toast content into a clean, modern, non-repetitive Android OS Toast string.
 */
internal fun formatCleanSystemToast(toast: ModeToastData): String {
    var cleanLabel = toast.modeLabel
        .replace(Regex("\\s*\\([^)]*\\)"), "") // remove redundant "(Powersave)", "(Extreme)", etc.
        .trim()
    if (cleanLabel.startsWith("Mode ", ignoreCase = true)) {
        cleanLabel = cleanLabel.substring(5).trim()
    }

    return when (toast.type) {
        ToastType.MODE -> {
            val emoji = when (toast.modeId.lowercase()) {
                "powersave" -> "🍃"
                "balance" -> "⚖️"
                "performance" -> "⚡"
                "extreme" -> "🔥"
                else -> "⚡"
            }
            "$emoji Mode $cleanLabel Aktif"
        }
        ToastType.SUCCESS -> "✓ $cleanLabel"
        ToastType.ERROR -> "⚠ $cleanLabel"
        ToastType.WARNING -> "⚠ $cleanLabel"
        else -> cleanLabel
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
