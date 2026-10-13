package com.noir.lynx.feature.cpu.ui

import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.designsystem.card.*
import com.noir.lynx.designsystem.controls.*
import com.noir.lynx.designsystem.tweak.*
import com.noir.lynx.designsystem.profile.*

import java.util.Locale
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.noir.lynx.designsystem.controls.SafeSlider as Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.noir.lynx.data.*

import com.noir.lynx.ui.*

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
    var isAdvancedExpanded by remember { mutableStateOf(false) }
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
        LynxCard(
            title = "PERFORMA CPU & KLUSTER",
            icon = Icons.Default.Speed,
            accentColor = AccentCyan
        ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Level 1: 3+1 Master Profile Selector (Battery | Balanced | Performa | Ekstrem)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PROFIL PERFORMA CPU",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.8.sp
                )
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 3 Standard Profiles
                standardProfiles.forEach { (key, label, icon) ->
                    val isSelected = currentProfile == key || (key == "performance" && currentProfile == "gaming")
                    val pillAccent = when (key) {
                        "battery" -> AccentGreen
                        "performance" -> AccentBlue
                        else -> AccentCyan
                    }

                    Surface(
                        onClick = { onApplyProfile(key) },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 46.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) pillAccent.copy(alpha = 0.22f) else BgSurfaceLowest,
                        border = BorderStroke(1.dp, if (isSelected) pillAccent else BorderSubtle)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (isSelected) pillAccent else TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) TextPrimary else TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // +1 Ekstrem Profile (Requires safety confirmation dialog)
                val isExtremeSelected = currentProfile == "extreme"
                Surface(
                    onClick = {
                        if (isExtremeSelected) {
                            onApplyProfile("balanced")
                        } else {
                            showExtremeConfirmDialog = true
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 46.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = if (isExtremeSelected) AccentRed.copy(alpha = 0.22f) else BgSurfaceLowest,
                    border = BorderStroke(1.dp, if (isExtremeSelected) AccentRed else BorderSubtle)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = "Ekstrem",
                            tint = if (isExtremeSelected) AccentRed else TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "Ekstrem",
                            fontSize = 10.sp,
                            fontWeight = if (isExtremeSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isExtremeSelected) TextPrimary else TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Description of active profile
            val profileDesc = when (currentProfile) {
                "battery" -> "Mengoptimalkan frekuensi untuk daya tahan baterai maksimal dan suhu tetap dingin."
                "performance", "gaming" -> "Menaikkan batas frekuensi bawah dan mempercepat responsivitas untuk gaming stabil dengan perlindungan termal aktif."
                "extreme" -> "Mode Ekstrem aktif: Seluruh core terkunci pada frekuensi puncak 100% tanpa batas daya OEM. Disarankan memakai cooler eksternal."
                else -> "Menyeimbangkan efisiensi daya harian dengan akselerasi cerdas saat aplikasi dibuka."
            }
            Text(
                text = profileDesc,
                fontSize = 10.5.sp,
                color = TextTertiary,
                lineHeight = 14.sp
            )

            HorizontalDivider(color = BorderSubtle, thickness = 0.8.dp, modifier = Modifier.padding(vertical = 2.dp))

            // Section: CPU CLUSTERS (Flat Surface, No nested cards)
            Text(
                text = "KONTROL KLUSTER CPU",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 0.8.sp
            )

            if (clusters.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Hardware Control", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Tidak tersedia pada kernel ini.\n✓ CPU terdeteksi\n✕ Frequency override dibatasi kernel OEM.",
                            fontSize = 10.sp,
                            color = TextSecondary,
                            lineHeight = 14.sp
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

            }
        }
        // Standalone Level 2 Trigger: "Pengaturan Lanjutan"
        Surface(
            onClick = { isAdvancedExpanded = !isAdvancedExpanded },
            shape = RoundedCornerShape(16.dp),
            color = BgCard,
            border = BorderStroke(1.dp, if (isAdvancedExpanded) AccentCyan.copy(alpha = 0.5f) else BorderSubtle),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp)
                .defaultMinSize(minHeight = 52.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AccentCyan.copy(alpha = 0.12f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Pengaturan Lanjutan",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (isAdvancedExpanded) "Tutup pengaturan terperinci" else "Hardware engine & verifikasi pengembang",
                            fontSize = 9.5.sp,
                            color = TextSecondary
                        )
                    }
                }

                Icon(
                    imageVector = if (isAdvancedExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Level 2 Expanded Area: Platform Engine & Developer Drawer (Full Width Sibling Cards)
        AnimatedVisibility(
            visible = isAdvancedExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Platform Hardware Boost (MediaTek PPM, CCI, DVFSRC / Qualcomm Devfreq & Boost)
                if (schedInfo.isPpmSupported || schedInfo.isMtkCciSupported || schedInfo.isMtkPowerModeSupported || schedInfo.isQcomBoostSupported || schedInfo.isQcomDevfreqBusSupported || schedInfo.ddrAvailFreqsMhz.isNotEmpty()) {
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
                        onShowSwitchInfo = onShowSwitchInfo
                    )
                }

                // Developer Verification Drawer Trigger (Phase 10: Hidden in Advanced)
                Surface(
                    onClick = { showDeveloperDrawer = true },
                    shape = RoundedCornerShape(22.dp),
                    color = BgCard,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).defaultMinSize(minHeight = 52.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Terminal, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Verifikasi Pengembang (Sysfs)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text("Status keaslian node kernel & perizinan hardware", fontSize = 9.5.sp, color = TextSecondary)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    // Extreme Mode Confirmation Dialog
    if (showExtremeConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExtremeConfirmDialog = false },
            containerColor = BgCard,
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
                    text = "Mode Ekstrem akan mengunci seluruh core CPU pada frekuensi clock tertinggi dan menonaktifkan idle downclock. Suhu baterai dan SoC akan meningkat cepat. Disarankan menggunakan pendingin eksternal (phone cooler). Apakah Anda yakin?",
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
                    )
                ) {
                    Text("Tetap Aktifkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExtremeConfirmDialog = false }) {
                    Text("Batal", color = TextSecondary)
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

// ============================================================
//  6. CPU UNIFIED SYSTEM CARD (TAB 1: SYSTEM CONTROLS)
// ============================================================

