package com.noir.lynx.designsystem.profile

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.data.AppProfileRule
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.ui.AppIconImage

/**
 * EditAppRuleDialog — Zen-Minimalist App Profile Configuration Dialog.
 *
 * Designed for psychological calm, zero text clipping, and symmetric ergonomics:
 * - Anti-overlap layout across non-standard screen ratios and DPIs.
 * - Dynamic refresh rate picker respecting actual hardware panel capabilities.
 * - Balanced 1:1 action buttons with clear tactile affordance.
 * - Concise, professional copywriting free from redundant AI explanations.
 */
@Composable
fun EditAppRuleDialog(
    rule: AppProfileRule,
    isExistingRule: Boolean,
    supportedRefreshRates: List<Int> = listOf(60, 90, 120),
    onDismiss: () -> Unit,
    onSave: (AppProfileRule) -> Unit,
    onDelete: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    var targetProf by remember(rule) { mutableStateOf(rule.targetProfile) }
    var targetHz by remember(rule) { mutableStateOf(rule.targetRefreshRate) }
    var autoHud by remember(rule) { mutableStateOf(rule.autoFloatingHud) }
    var isRuleEnabled by remember(rule) { mutableStateOf(rule.enabled) }

    // Deduplicate and sort hardware refresh rates with fallback
    val resolvedRefreshRates = remember(supportedRefreshRates) {
        val cleanList = supportedRefreshRates.filter { it in 30..240 }.distinct().sorted()
        if (cleanList.isEmpty()) listOf(60, 90, 120) else cleanList
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF10141F),
            border = BorderStroke(
                0.8.dp,
                Brush.verticalGradient(
                    listOf(Color(0x30FFFFFF), Color(0x10FFFFFF))
                )
            ),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 420.dp)
                .heightIn(max = 660.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // ── 1. Header Row (App identity + close button) ────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BgDeepOled.copy(alpha = 0.7f),
                            border = BorderStroke(0.8.dp, BorderGlass),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                AppIconImage(
                                    packageName = rule.packageName,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = rule.appName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (rule.isGame) {
                                    Spacer(Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = AccentOrange.copy(alpha = 0.16f),
                                        border = BorderStroke(0.6.dp, AccentOrange.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = "GAME",
                                            color = AccentOrange,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 4.5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = rule.packageName,
                                fontSize = 10.5.sp,
                                color = TextSecondary.copy(alpha = 0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDismiss()
                        },
                        shape = CircleShape,
                        color = Color(0xFF141824),
                        border = BorderStroke(0.6.dp, Color(0x18FFFFFF)),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Tutup",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // ── 2. Scrollable Configuration Sections ───────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Card 1: Master Rule Active Switch
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0C0F17),
                        border = BorderStroke(0.6.dp, Color(0x15FFFFFF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                Text(
                                    text = "Status Aturan",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp
                                )
                                Spacer(Modifier.height(1.5.dp))
                                Text(
                                    text = if (isRuleEnabled) "Aktif saat aplikasi di foreground" else "Aturan dinonaktifkan sementara",
                                    color = TextSecondary.copy(alpha = 0.65f),
                                    fontSize = 10.sp
                                )
                            }
                            Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                Switch(
                                    checked = isRuleEnabled,
                                    onCheckedChange = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        isRuleEnabled = it
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = BgDeepOled,
                                        checkedTrackColor = AccentCyan,
                                        uncheckedThumbColor = TextSecondary.copy(alpha = 0.5f),
                                        uncheckedTrackColor = Color(0xFF141824)
                                    ),
                                    modifier = Modifier.scale(0.85f)
                                )
                            }
                        }
                    }

                    // Card 2: Performance Profile Selector
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0C0F17),
                        border = BorderStroke(0.6.dp, Color(0x15FFFFFF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Profil Performa",
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(1.5.dp))
                            Text(
                                text = "Konfigurasi komputasi CPU & GPU",
                                color = TextSecondary.copy(alpha = 0.65f),
                                fontSize = 10.sp
                            )
                            Spacer(Modifier.height(9.dp))

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF10141E),
                                border = BorderStroke(0.6.dp, Color(0x18FFFFFF)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(2.5.dp),
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    listOf(
                                        "extreme" to ("Ekstrem" to AccentRed),
                                        "performance" to ("Performa" to AccentOrange),
                                        "balance" to ("Seimbang" to AccentCyan),
                                        "powersave" to ("Hemat" to AccentGreen),
                                    ).forEach { (prof, meta) ->
                                        val isSel = targetProf.equals(prof, ignoreCase = true)
                                        val accentColor = meta.second
                                        Surface(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                targetProf = prof
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isSel) accentColor.copy(alpha = 0.20f) else Color.Transparent,
                                            border = if (isSel) BorderStroke(0.8.dp, accentColor.copy(alpha = 0.6f)) else null,
                                            modifier = Modifier.weight(1f).fillMaxHeight()
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = meta.first,
                                                    color = if (isSel) accentColor else TextSecondary.copy(alpha = 0.75f),
                                                    fontSize = 10.5.sp,
                                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Card 3: Screen Refresh Rate Lock
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0C0F17),
                        border = BorderStroke(0.6.dp, Color(0x15FFFFFF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Kunci Refresh Rate Layar",
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(1.5.dp))
                            Text(
                                text = "Kunci refresh rate display saat aplikasi aktif",
                                color = TextSecondary.copy(alpha = 0.65f),
                                fontSize = 10.sp
                            )
                            Spacer(Modifier.height(9.dp))

                            // Dynamically assemble options: null (Bawaan) + supported hardware rates
                            val hzOptions: List<Pair<Int?, String>> = listOf(null to "Bawaan") +
                                resolvedRefreshRates.map { it to "${it}Hz" }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF10141E),
                                border = BorderStroke(0.6.dp, Color(0x18FFFFFF)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(2.5.dp),
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    hzOptions.forEach { (hz, label) ->
                                        val isSel = targetHz == hz
                                        Surface(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                targetHz = hz
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isSel) AccentPurple.copy(alpha = 0.20f) else Color.Transparent,
                                            border = if (isSel) BorderStroke(0.8.dp, AccentPurple.copy(alpha = 0.6f)) else null,
                                            modifier = Modifier.weight(1f).fillMaxHeight()
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = label,
                                                    color = if (isSel) AccentPurple else TextSecondary.copy(alpha = 0.75f),
                                                    fontSize = 10.5.sp,
                                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Card 4: Floating Game HUD (OSD) Toggle
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0C0F17),
                        border = BorderStroke(0.6.dp, Color(0x15FFFFFF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                Text(
                                    text = "Floating Game HUD",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp
                                )
                                Spacer(Modifier.height(1.5.dp))
                                Text(
                                    text = "Tampilkan overlay FPS, suhu, & daya watt real-time",
                                    color = TextSecondary.copy(alpha = 0.65f),
                                    fontSize = 10.sp
                                )
                            }
                            Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                Switch(
                                    checked = autoHud,
                                    onCheckedChange = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        autoHud = it
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = BgDeepOled,
                                        checkedTrackColor = AccentCyan,
                                        uncheckedThumbColor = TextSecondary.copy(alpha = 0.5f),
                                        uncheckedTrackColor = Color(0xFF141824)
                                    ),
                                    modifier = Modifier.scale(0.85f)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── 3. Symmetrical Action Buttons (Balanced 1:1) ───────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isExistingRule) {
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onDelete(rule.packageName)
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = AccentRed.copy(alpha = 0.12f),
                            border = BorderStroke(0.8.dp, AccentRed.copy(alpha = 0.40f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = null,
                                        tint = AccentRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Hapus",
                                        color = AccentRed,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    } else {
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF141824),
                            border = BorderStroke(0.6.dp, Color(0x18FFFFFF)),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Batal",
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val updated = rule.copy(
                                targetProfile = targetProf,
                                targetRefreshRate = targetHz,
                                autoFloatingHud = autoHud,
                                enabled = isRuleEnabled
                            )
                            onSave(updated)
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = AccentCyan.copy(alpha = 0.18f),
                        border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.60f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Simpan",
                                    color = AccentCyan,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
