package com.noir.lynx.ui

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.AppProfileRule
import com.noir.lynx.designsystem.theme.*

/**
 * AppRuleBentoCard — Exquisitely Crafted, Anti-Overlap Bento Card.
 * Solves all horizontal clipping issues by using a strictly budgeted, proportional layout.
 */
@Composable
fun AppRuleBentoCard(
    rule: AppProfileRule,
    context: Context,
    viewModel: LynxViewModel,
    onEditRuleClick: (AppProfileRule) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    val profDotColor = when (rule.targetProfile.lowercase().trim()) {
        "extreme" -> AccentRed
        "performance" -> AccentOrange
        "powersave" -> AccentGreen
        else -> AccentCyan
    }

    val shortProfName = when (rule.targetProfile.lowercase().trim()) {
        "extreme" -> "Ekstrem"
        "performance" -> "Performa"
        "powersave" -> "Hemat"
        else -> "Seimbang"
    }

    val targetBg = if (rule.enabled) Color(0xFF10141F) else Color(0xFF12141D).copy(alpha = 0.65f)
    val targetBorder = if (rule.enabled) profDotColor.copy(alpha = 0.28f) else BorderSubtle

    val animatedBg by animateColorAsState(targetBg, animationSpec = tween(150), label = "rule_bg_${rule.packageName}")
    val animatedBorder by animateColorAsState(targetBorder, animationSpec = tween(150), label = "rule_border_${rule.packageName}")

    val onRowClick = remember(rule) { { onEditRuleClick(rule) } }
    val onToggle = remember(rule.packageName) {
        { enabled: Boolean ->
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            viewModel.toggleAppProfileRule(context, rule.packageName, enabled)
        }
    }

    Surface(
        onClick = onRowClick,
        shape = RoundedCornerShape(14.dp),
        color = animatedBg,
        border = BorderStroke(if (rule.enabled) 0.8.dp else 0.5.dp, animatedBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            // Upper Row: App Icon + Name & Package + Master Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIconImage(
                    packageName = rule.packageName,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(9.dp))
                )

                Spacer(Modifier.width(10.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = rule.appName,
                            color = if (rule.enabled) TextPrimary else TextSecondary.copy(alpha = 0.6f),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (rule.isGame) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = AccentOrange.copy(alpha = 0.14f),
                                border = BorderStroke(0.5.dp, AccentOrange.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "GAME",
                                    color = AccentOrange,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = rule.packageName,
                        color = TextSecondary.copy(alpha = 0.45f),
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(6.dp))

                Box(
                    modifier = Modifier.size(42.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Switch(
                        checked = rule.enabled,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BgDeepOled,
                            checkedTrackColor = profDotColor,
                            uncheckedThumbColor = TextSecondary.copy(alpha = 0.5f),
                            uncheckedTrackColor = Color(0xFF141824),
                            uncheckedBorderColor = BorderGlass
                        ),
                        modifier = Modifier.scale(0.80f)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = Color(0x10FFFFFF), thickness = 0.5.dp)
            Spacer(Modifier.height(6.dp))

            // Lower Row: 1-Tap Quick Profile Cycle Pill + Details + Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 1-Tap Profile Cycle Pill (Compact: ~90-100dp)
                Surface(
                    onClick = {
                        val nextProfile = when (rule.targetProfile.lowercase().trim()) {
                            "powersave" -> "balance"
                            "balance" -> "performance"
                            "performance" -> "extreme"
                            "extreme" -> "powersave"
                            else -> "performance"
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val updated = rule.copy(targetProfile = nextProfile)
                        viewModel.addOrUpdateAppProfileRule(context, updated)
                    },
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF0C0F17),
                    border = BorderStroke(0.6.dp, profDotColor.copy(alpha = if (rule.enabled) 0.45f else 0.20f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = profDotColor,
                            modifier = Modifier.size(4.5.dp)
                        ) {}
                        Text(
                            text = shortProfName,
                            color = if (rule.enabled) TextPrimary else TextSecondary.copy(alpha = 0.6f),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Ganti Profil",
                            tint = profDotColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }

                // Right: Optional Badges + Edit Link
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (rule.targetRefreshRate != null) {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = Color(0xFF0C0F17),
                            border = BorderStroke(0.5.dp, Color(0x16FFFFFF))
                        ) {
                            Text(
                                text = "${rule.targetRefreshRate}Hz",
                                color = TextSecondary.copy(alpha = 0.8f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (rule.autoFloatingHud) {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = AccentCyan.copy(alpha = 0.12f),
                            border = BorderStroke(0.5.dp, AccentCyan.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "ENGINE",
                                color = AccentCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onEditRuleClick(rule)
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Ubah",
                                color = TextSecondary.copy(alpha = 0.7f),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Ubah",
                                tint = TextSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * AppRuleEmbeddedRow — Backward-compatible row adapter delegating to AppRuleBentoCard.
 */
@Composable
fun AppRuleEmbeddedRow(
    rule: AppProfileRule,
    context: Context,
    viewModel: LynxViewModel,
    onEditRuleClick: (AppProfileRule) -> Unit
) {
    AppRuleBentoCard(
        rule = rule,
        context = context,
        viewModel = viewModel,
        onEditRuleClick = onEditRuleClick
    )
}
