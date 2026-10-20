package com.noir.lynx.feature.cpu.ui

import com.noir.lynx.core.locale.localizedUiText

import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.designsystem.card.*
import com.noir.lynx.designsystem.controls.*
import com.noir.lynx.designsystem.tweak.*
import com.noir.lynx.designsystem.profile.*

import java.util.Locale
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.*
import com.noir.lynx.ui.*

/**
 * CpuUnifiedPerformanceCard — Master Bento CPU Performance Tuner.
 * Harmonized with Profile Kernel Performa (TuningProfilesCategory) design language.
 */
@Composable
fun CpuUnifiedPerformanceCard(
    uiState: LynxUiState,
    clusters: List<CpuClusterInfo>,
    governorTunables: Map<Int, List<GovernorTunable>>,
    onApplyProfile: (String) -> Unit,
    onFreqChange: (policyId: Int, minFreq: Long?, maxFreq: Long?) -> Unit,
    onGovChange: (policyId: Int, gov: String) -> Unit,
    onLoadTunables: (policyId: Int, gov: String) -> Unit,
    onTunableChange: (policyId: Int, gov: String, key: String, value: String) -> Unit,
    onLockToggle: (policyId: Int, isLock: Boolean, minFreq: Long, maxFreq: Long) -> Unit,
    schedInfo: SchedulerInfo,
    onPpmPolicyChange: (Int, Boolean) -> Unit,
    onQcomTouchboostChange: (Boolean) -> Unit,
    onQcomInputBoostChange: (Long, Int) -> Unit,
    onMtkInterconnectBoostChange: (Boolean) -> Unit = {},
    onMtkCpuPowerModeChange: (Int) -> Unit = {},
    onMtkDlptBypassChange: (Boolean) -> Unit = {},
    onQcomDevfreqBusBoostChange: (Boolean) -> Unit = {},
    onUniversalBusProfileChange: (String) -> Unit = {},
    onUniversalTouchBoostChange: (Boolean) -> Unit = {},
    onUniversalAntiThrottlingGuardChange: (Boolean) -> Unit = {},
    onShowSwitchInfo: ((SwitchTweakInfo) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var showExtremeConfirmDialog by remember { mutableStateOf(false) }
    var showDeveloperDrawer by remember { mutableStateOf(false) }
    val currentProfile = uiState.cpuComprehensiveProfile.lowercase()

    // Master CPU Presets: 3 Standard + 1 Ekstrem
    val standardProfiles = listOf(
        Triple("battery", "Baterai", Icons.Default.BatteryChargingFull),
        Triple("balanced", "Seimbang", Icons.Default.Tune),
        Triple("performance", PERFORMA_LABEL, Icons.Default.Speed)
    )

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // ── 1. Section Header: Profil Performa CPU ────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Profil Performa CPU",
                    color = TextSecondary.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.2.sp
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF141824),
                    border = BorderStroke(0.5.dp, Color(0x18FFFFFF))
                ) {
                    Text(
                        text = "4 Pilihan",
                        color = TextSecondary.copy(alpha = 0.75f),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            if (uiState.isCpuModified) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(AccentOrange, CircleShape)
                    )
                    Text(
                        text = "Terkustomisasi",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentOrange
                    )
                }
            }
        }

        // ── 2. Master Profile Bento Card ──────────────────────────
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF10141F),
            border = BorderStroke(
                0.8.dp,
                Brush.verticalGradient(
                    listOf(
                        Color(0x28FFFFFF),
                        Color(0x10FFFFFF)
                    )
                )
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 4 Profile Selector Pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    standardProfiles.forEach { (key, label, icon) ->
                        val isSelected = currentProfile == key || (key == "performance" && currentProfile == "gaming")
                        val pillAccent = when (key) {
                            "battery" -> AccentGreen
                            "performance" -> AccentOrange
                            else -> AccentCyan
                        }
                        val targetBg = if (isSelected) pillAccent.copy(alpha = 0.16f) else Color(0xFF141824)
                        val targetBorder = if (isSelected) pillAccent.copy(alpha = 0.6f) else Color(0x18FFFFFF)
                        val animatedBg by animateColorAsState(targetBg, animationSpec = tween(150), label = "cpu_pill_bg_$key")
                        val animatedBorder by animateColorAsState(targetBorder, animationSpec = tween(150), label = "cpu_pill_border_$key")

                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onApplyProfile(key)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = animatedBg,
                            border = BorderStroke(0.8.dp, animatedBorder)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = localizedUiText(label),
                                    tint = if (isSelected) pillAccent else TextSecondary.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (isSelected) TextPrimary else TextSecondary.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // +1 Ekstrem Profile Pill
                    val isExtremeSelected = currentProfile == "extreme"
                    val targetExtremeBg = if (isExtremeSelected) AccentRed.copy(alpha = 0.16f) else Color(0xFF141824)
                    val targetExtremeBorder = if (isExtremeSelected) AccentRed.copy(alpha = 0.6f) else Color(0x18FFFFFF)
                    val animatedExtremeBg by animateColorAsState(targetExtremeBg, animationSpec = tween(150), label = "cpu_extreme_bg")
                    val animatedExtremeBorder by animateColorAsState(targetExtremeBorder, animationSpec = tween(150), label = "cpu_extreme_border")

                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (isExtremeSelected) {
                                onApplyProfile("balanced")
                            } else {
                                showExtremeConfirmDialog = true
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = animatedExtremeBg,
                        border = BorderStroke(0.8.dp, animatedExtremeBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = localizedUiText("Ekstrem"),
                                tint = if (isExtremeSelected) AccentRed else TextSecondary.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "Ekstrem",
                                fontSize = 11.sp,
                                fontWeight = if (isExtremeSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isExtremeSelected) TextPrimary else TextSecondary.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Description Ribbon
                val profileDesc = when (currentProfile) {
                    "battery" -> "Mengoptimalkan frekuensi untuk daya tahan baterai maksimal dan suhu tetap dingin."
                    "performance", "gaming" -> "Menaikkan batas frekuensi bawah dan mempercepat responsivitas untuk gaming stabil."
                    "extreme" -> "Mode Ekstrem aktif: Seluruh core terkunci pada frekuensi puncak 100% tanpa batas daya OEM."
                    else -> "Menyeimbangkan efisiensi daya harian dengan akselerasi cerdas saat aplikasi dibuka."
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0C0F17),
                    border = BorderStroke(0.5.dp, Color(0x12FFFFFF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = profileDesc,
                        fontSize = 11.sp,
                        color = TextSecondary.copy(alpha = 0.8f),
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
            }
        }

        // ── 3. Section Header: Kontrol Kluster CPU ─────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Kontrol Kluster CPU",
                    color = TextSecondary.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.2.sp
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF141824),
                    border = BorderStroke(0.5.dp, Color(0x18FFFFFF))
                ) {
                    Text(
                        text = "${clusters.size} Kluster",
                        color = TextSecondary.copy(alpha = 0.75f),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // ── 4. Clusters List ───────────────────────────────────────
        if (clusters.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF10141F),
                border = BorderStroke(0.8.dp, Color(0x18FFFFFF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Kontrol Hardware", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Tidak tersedia pada kernel ini.\n✓ CPU terdeteksi\n✕ Frequency override dibatasi kernel OEM.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 15.sp
                    )
                }
            }
        } else {
            clusters.forEach { cluster ->
                CompactClusterRow(
                    cluster = cluster,
                    governorTunables = governorTunables,
                    onFreqChange = onFreqChange,
                    onGovChange = onGovChange,
                    onLoadTunables = onLoadTunables,
                    onTunableChange = onTunableChange,
                    onLockToggle = onLockToggle
                )
            }
        }

        // ── 5. Pengaturan Lanjutan (Advanced Hardware Settings) ──
        PlatformHardwareEngineCard(
            schedInfo = schedInfo,
            onPpmPolicyChange = onPpmPolicyChange,
            onQcomTouchboostChange = onQcomTouchboostChange,
            onQcomInputBoostChange = onQcomInputBoostChange,
            onMtkInterconnectBoostChange = onMtkInterconnectBoostChange,
            onMtkCpuPowerModeChange = onMtkCpuPowerModeChange,
            onMtkDlptBypassChange = onMtkDlptBypassChange,
            onQcomDevfreqBusBoostChange = onQcomDevfreqBusBoostChange,
            onUniversalBusProfileChange = onUniversalBusProfileChange,
            onUniversalTouchBoostChange = onUniversalTouchBoostChange,
            onUniversalAntiThrottlingGuardChange = onUniversalAntiThrottlingGuardChange,
            onShowSwitchInfo = onShowSwitchInfo,
            onOpenDeveloperVerification = { showDeveloperDrawer = true }
        )
    }

    // Extreme Mode Confirmation Dialog
    if (showExtremeConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExtremeConfirmDialog = false },
            containerColor = Color(0xFF141824),
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = AccentRed,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Konfirmasi Mode Ekstrem",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "Mode Ekstrem akan mengunci seluruh core CPU pada frekuensi clock tertinggi dan menonaktifkan idle downclock. Suhu baterai dan SoC akan meningkat cepat. Disarankan memakai pendingin eksternal (phone cooler). Apakah Anda yakin?",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExtremeConfirmDialog = false
                        onApplyProfile("extreme")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Tetap Aktifkan", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExtremeConfirmDialog = false }) {
                    Text("Batal", color = TextSecondary, fontSize = 12.sp)
                }
            }
        )
    }

    // Developer Verification Bottom Sheet
    if (showDeveloperDrawer) {
        CpuDeveloperVerificationSheet(
            uiState = uiState,
            onDismiss = { showDeveloperDrawer = false }
        )
    }
}
