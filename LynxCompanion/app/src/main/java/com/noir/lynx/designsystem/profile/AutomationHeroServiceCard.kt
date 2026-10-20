package com.noir.lynx.ui

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.theme.*

/**
 * AutomationHeroServiceCard — Calm Zen Service Card for App Automation.
 * Replaces dense preachy paragraphs with serene, intuitive system indicators.
 */
@Composable
fun AutomationHeroServiceCard(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    context: Context,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var showActivationDialog by remember { mutableStateOf(false) }
    var dontShowAgain by remember { mutableStateOf(false) }
    val prefs = remember { context.getSharedPreferences("lynx_automation_prefs", Context.MODE_PRIVATE) }
    val isSuppressed = remember { prefs.getBoolean("suppress_automation_confirm_dialog", false) }

    val isAutomationRunning = uiState.isAppAutomationActive || com.noir.lynx.service.LynxAppAutomationService.isRunning
    val activeGamePkg = com.noir.lynx.service.LynxAppAutomationService.activeGamePackage
    val activeGameName = com.noir.lynx.service.LynxAppAutomationService.activeGameName
    val hasOverride = uiState.state.profileOverrides.isNotEmpty() || activeGamePkg != null

    val baselineId = com.noir.lynx.service.LynxAppAutomationService.currentBaseline
    val baselineDisplayName = when (baselineId.lowercase().trim()) {
        "performance" -> "Performa Tinggi"
        "extreme" -> "Ekstrem"
        "powersave" -> "Hemat Daya"
        else -> "Seimbang"
    }

    if (showActivationDialog) {
        AlertDialog(
            onDismissRequest = { showActivationDialog = false },
            containerColor = Color(0xFF10141F),
            shape = RoundedCornerShape(18.dp),
            icon = {
                Surface(
                    shape = CircleShape,
                    color = AccentCyan.copy(alpha = 0.12f),
                    border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.35f)),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AutoMode,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    text = "Aktifkan Otomasi Profil?",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Layanan akan otomatis menerapkan profil performa saat aplikasi dibuka dan kembali ke profil sistem saat ditutup.",
                        color = TextSecondary.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Center
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                dontShowAgain = !dontShowAgain
                            }
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = dontShowAgain,
                            onCheckedChange = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                dontShowAgain = it
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = AccentCyan,
                                checkmarkColor = BgDeepOled,
                                uncheckedColor = TextSecondary.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Jangan tampilkan lagi",
                            color = TextPrimary.copy(alpha = 0.85f),
                            fontSize = 11.5.sp
                        )
                    }
                }
            },
            confirmButton = {
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (dontShowAgain) {
                            prefs.edit().putBoolean("suppress_automation_confirm_dialog", true).apply()
                        }
                        showActivationDialog = false
                        viewModel.toggleAppAutomation(context, true)
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = AccentCyan.copy(alpha = 0.18f),
                    border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.5f)),
                    modifier = Modifier.height(40.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aktifkan",
                            color = AccentCyan,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            dismissButton = {
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showActivationDialog = false
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0C0F17),
                    border = BorderStroke(0.6.dp, BorderGlass),
                    modifier = Modifier.height(40.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Batal",
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        )
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF10141F),
        border = BorderStroke(
            0.8.dp,
            Brush.verticalGradient(
                listOf(
                    (if (isAutomationRunning) AccentCyan else Color(0xFF94A3B8)).copy(alpha = if (isAutomationRunning) 0.28f else 0.12f),
                    Color(0x10FFFFFF)
                )
            )
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Row 1: Status Bar (Status Otomasi & Indicators)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Surface(
                            shape = CircleShape,
                            color = (if (isAutomationRunning) AccentCyan else TextSecondary).copy(alpha = 0.18f),
                            modifier = Modifier.size(10.dp)
                        ) {}
                        Surface(
                            shape = CircleShape,
                            color = if (isAutomationRunning) AccentCyan else TextSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(5.dp)
                        ) {}
                    }
                    Text(
                        text = "Status Otomasi",
                        color = TextSecondary.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = if (isAutomationRunning) Color(0x1400E676) else Color(0x1094A3B8),
                        border = BorderStroke(
                            0.5.dp,
                            if (isAutomationRunning) Color(0x2800E676) else Color(0x15FFFFFF)
                        )
                    ) {
                        Text(
                            text = if (isAutomationRunning) "Aktif" else "Siaga",
                            color = if (isAutomationRunning) AccentGreen.copy(alpha = 0.9f) else TextSecondary.copy(alpha = 0.7f),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (hasOverride) {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = Color(0x18FF9100),
                            border = BorderStroke(0.5.dp, Color(0x35FF9100))
                        ) {
                            Text(
                                text = if (activeGameName != null) "Override: ${activeGameName.take(12)}" else "Override Aktif",
                                color = AccentOrange.copy(alpha = 0.95f),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Row 2: Master Switch & Clean Description
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isAutomationRunning) AccentCyan.copy(alpha = 0.12f) else Color(0xFF141824),
                    border = BorderStroke(
                        0.6.dp,
                        if (isAutomationRunning) AccentCyan.copy(alpha = 0.28f) else Color(0x16FFFFFF)
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AutoMode,
                            contentDescription = null,
                            tint = if (isAutomationRunning) AccentCyan else TextSecondary.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                    Text(
                        text = "Otomasi Aplikasi",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.2).sp
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = "Otomatis beralih ke profil performa saat aplikasi dibuka.",
                        color = TextSecondary.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        lineHeight = 14.5.sp
                    )
                }

                Box(
                    modifier = Modifier.size(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Switch(
                        checked = uiState.isAppAutomationActive,
                        onCheckedChange = { enable ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (enable) {
                                if (isSuppressed) {
                                    viewModel.toggleAppAutomation(context, true)
                                } else {
                                    showActivationDialog = true
                                }
                            } else {
                                viewModel.toggleAppAutomation(context, false)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BgDeepOled,
                            checkedTrackColor = AccentCyan,
                            uncheckedThumbColor = TextSecondary.copy(alpha = 0.6f),
                            uncheckedTrackColor = Color(0xFF141824),
                            uncheckedBorderColor = BorderGlass
                        ),
                        modifier = Modifier.scale(0.84f)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Row 3: Tranquil Baseline Recovery Strip
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF0C0F17),
                border = BorderStroke(0.5.dp, Color(0x14FFFFFF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            text = "Profil Dasar:",
                            color = TextSecondary.copy(alpha = 0.55f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = baselineDisplayName,
                            color = AccentCyan.copy(alpha = 0.9f),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text("•", color = TextSecondary.copy(alpha = 0.25f), fontSize = 9.sp)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            text = "Layar Mati:",
                            color = TextSecondary.copy(alpha = 0.55f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Deep Sleep",
                            color = AccentGreen.copy(alpha = 0.9f),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Color(0x10FFFFFF), thickness = 0.5.dp)
            Spacer(Modifier.height(8.dp))

            // Row 4: Buffer Transisi / Cooldown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Jeda Transisi",
                    color = TextSecondary.copy(alpha = 0.85f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp
                )

                val cooldownLabel = when (uiState.appAutomationCooldownSeconds) {
                    0 -> "Terkunci"
                    60 -> "1 Menit"
                    180 -> "3 Menit"
                    else -> "${uiState.appAutomationCooldownSeconds} Detik"
                }

                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = AccentCyan.copy(alpha = 0.10f),
                    border = BorderStroke(0.5.dp, AccentCyan.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = cooldownLabel,
                        color = AccentCyan,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            val cooldownOptions = listOf(
                5 to "5s",
                30 to "30s",
                60 to "1m",
                180 to "3m",
                0 to "Kunci"
            )

            // Segmented Pills (Anti-Clash, Height 36dp)
            Surface(
                shape = RoundedCornerShape(9.dp),
                color = Color(0xFF0C0F17),
                border = BorderStroke(0.5.dp, Color(0x16FFFFFF)),
                modifier = Modifier.fillMaxWidth().height(36.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(2.5.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    cooldownOptions.forEach { (sec, label) ->
                        val isSelected = uiState.appAutomationCooldownSeconds == sec
                        val animBg by animateColorAsState(
                            targetValue = if (isSelected) AccentCyan.copy(alpha = 0.20f) else Color.Transparent,
                            animationSpec = tween(150),
                            label = "cd_bg_$sec"
                        )
                        val animText by animateColorAsState(
                            targetValue = if (isSelected) AccentCyan else TextSecondary.copy(alpha = 0.7f),
                            animationSpec = tween(150),
                            label = "cd_txt_$sec"
                        )

                        Surface(
                            onClick = {
                                if (!isSelected) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.setAppAutomationCooldownSeconds(context, sec)
                                }
                            },
                            shape = RoundedCornerShape(7.dp),
                            color = animBg,
                            border = if (isSelected) BorderStroke(0.6.dp, AccentCyan.copy(alpha = 0.5f)) else null,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    color = animText,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(5.dp))
            Text(
                text = "Mencegah penurunan performa saat beralih aplikasi sementara.",
                color = TextSecondary.copy(alpha = 0.55f),
                fontSize = 10.sp,
                lineHeight = 13.5.sp
            )
        }
    }
}

/**
 * Backward-compatible alias for AutomationHeroServiceCard.
 */
@Composable
fun AutomationServiceTopSection(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    context: Context,
    modifier: Modifier = Modifier
) {
    AutomationHeroServiceCard(
        uiState = uiState,
        viewModel = viewModel,
        context = context,
        modifier = modifier
    )
}
