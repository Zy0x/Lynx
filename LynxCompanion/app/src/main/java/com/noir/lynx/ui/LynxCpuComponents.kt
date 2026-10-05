package com.noir.lynx.ui

import java.util.Locale
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.*

// ============================================================
//  1. CPU TOP NAVIGATION TAB ROW (3 TABS: PERFORMANCE, SYSTEM, MONITOR)
// ============================================================

@Composable
fun CpuTabRow(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        Triple(0, "Performance", Icons.Default.Bolt),
        Triple(1, "System", Icons.Default.SettingsSuggest),
        Triple(2, "Monitor", Icons.Default.Analytics)
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        color = BgCard,
        border = BorderStroke(0.8.dp, BorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEach { (index, title, icon) ->
                val isSelected = selectedTab == index
                val tabAccent = when (index) {
                    0 -> AccentCyan
                    1 -> AccentOrange
                    2 -> AccentGreen
                    else -> AccentCyan
                }
                Surface(
                    onClick = { onSelectTab(index) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) tabAccent.copy(alpha = 0.18f) else Color.Transparent,
                    border = if (isSelected) BorderStroke(1.dp, tabAccent.copy(alpha = 0.5f)) else null
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = if (isSelected) tabAccent else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = title,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) TextPrimary else TextSecondary
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
//  2. CPU DASHBOARD HERO CARD (LEVEL 1 ONE-GLANCE OVERVIEW)
// ============================================================

@Composable
fun CpuDashboardHeroCard(
    uiState: LynxUiState,
    modifier: Modifier = Modifier
) {
    val cores = uiState.cpuCores
    val totalLoad = uiState.totalCpuLoadPercent.coerceIn(0, 100)
    val peakKhz = cores.filter { it.isOnline }.maxOfOrNull { it.curFreqKhz } ?: 0L
    val peakGhz = peakKhz / 1000000f
    val onlineCount = cores.count { it.isOnline }
    val totalCount = cores.size.coerceAtLeast(8)
    val tempC: Int = uiState.thermalZones.firstOrNull { it.type.contains("cpu", true) }?.tempC?.toInt()
        ?: (uiState.batteryDetails?.tempC?.toInt() ?: 38)
    val healthScore = uiState.cpuHealthScore.coerceIn(15, 100)
    val currentProfile = uiState.cpuComprehensiveProfile.lowercase()

    val profileLabel = when (currentProfile) {
        "battery" -> "Efisiensi (Battery)"
        "gaming" -> "Performa (Gaming)"
        "extreme" -> "Ekstrem (Unrestricted)"
        else -> "Seimbang (Balanced)"
    }
    val profileAccent = when (currentProfile) {
        "battery" -> AccentGreen
        "gaming" -> AccentOrange
        "extreme" -> AccentRed
        else -> AccentCyan
    }

    val healthLabel = when {
        healthScore >= 85 -> "Optimal"
        healthScore >= 70 -> "Stabil"
        healthScore >= 50 -> "Beban Sedang"
        else -> "Beban Kritis"
    }
    val healthAccent = when {
        healthScore >= 80 -> AccentGreen
        healthScore >= 60 -> AccentCyan
        healthScore >= 40 -> AccentOrange
        else -> AccentRed
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = BgCard,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Status Mode & Chipset
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(profileAccent)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = profileLabel,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = profileAccent
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(0.8.dp, BorderSubtle)
                ) {
                    Text(
                        text = if (uiState.socPlatformName.isNotBlank()) uiState.socPlatformName else "ARMv8 SoC",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 4-Column Primary Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Metric 1: Peak Speed
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("PEAK CLOCK", fontSize = 8.5.sp, color = TextTertiary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = if (peakGhz > 0f) String.format(Locale.US, "%.2f", peakGhz) else "--",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AccentCyan
                        )
                        Text("GHz", fontSize = 8.5.sp, color = TextSecondary)
                    }
                }

                // Metric 2: CPU Temp
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("SUHU CPU", fontSize = 8.5.sp, color = TextTertiary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "$tempC°",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (tempC >= 45) AccentRed else (if (tempC >= 40) AccentOrange else AccentGreen)
                        )
                        Text("Celsius", fontSize = 8.5.sp, color = TextSecondary)
                    }
                }

                // Metric 3: Total Load
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("BEBAN CPU", fontSize = 8.5.sp, color = TextTertiary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "$totalLoad%",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (totalLoad > 75) AccentOrange else TextPrimary
                        )
                        Text("Total", fontSize = 8.5.sp, color = TextSecondary)
                    }
                }

                // Metric 4: Cores Online
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("CORE AKTIF", fontSize = 8.5.sp, color = TextTertiary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "$onlineCount/$totalCount",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (onlineCount == totalCount) AccentGreen else AccentOrange
                        )
                        Text("Silicon", fontSize = 8.5.sp, color = TextSecondary)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // CPU Health & Performance Score Meter
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderGlass),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.HealthAndSafety,
                                contentDescription = null,
                                tint = healthAccent,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "Skor Kesehatan Sistem",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        }
                        Text(
                            text = "$healthScore% ($healthLabel)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = healthAccent
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    // Progress Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(2.5.dp))
                            .background(BgSurfaceLowest)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = (healthScore / 100f).coerceIn(0.05f, 1f))
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(healthAccent.copy(alpha = 0.5f), healthAccent)
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
//  3. CPU SMART RECOMMENDATION CARD (DISMISSIBLE CONDITION BANNER)
// ============================================================

@Composable
fun CpuSmartRecommendationCard(
    recommendation: String,
    onApplyRecommendation: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = AccentOrange.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = AccentOrange,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Rekomendasi Cerdas Lynx",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentOrange
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = TextTertiary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = recommendation,
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 15.sp
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onApplyRecommendation,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentOrange,
                        contentColor = Color.Black
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = "Terapkan Sekarang",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ============================================================
//  4. CPU UNIFIED PERFORMANCE CARD (LEVEL 1 PILLS & LEVEL 2 EXPANSION)
// ============================================================

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
    modifier: Modifier = Modifier
) {
    var isAdvancedExpanded by remember { mutableStateOf(false) }
    var showExtremeConfirmDialog by remember { mutableStateOf(false) }
    val currentProfile = uiState.cpuComprehensiveProfile.lowercase()

    val profiles = listOf(
        Triple("battery", "Efisiensi", Icons.Default.BatteryChargingFull),
        Triple("balanced", "Seimbang", Icons.Default.Tune),
        Triple("gaming", "Performa", Icons.Default.Bolt)
    )

    LynxCard(
        title = "PERFORMA CPU & KLUSTER",
        icon = Icons.Default.Speed,
        accentColor = AccentCyan,
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Level 1: 3-Pill 1-Click Profile Selector (Clean Consumer View)
            Text(
                text = "PILIH MODE PERFORMA",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 0.8.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                profiles.forEach { (key, label, icon) ->
                    val isSelected = currentProfile == key
                    val pillAccent = when (key) {
                        "battery" -> AccentGreen
                        "gaming" -> AccentOrange
                        else -> AccentCyan
                    }

                    Surface(
                        onClick = { onApplyProfile(key) },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 44.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) pillAccent.copy(alpha = 0.22f) else BgSurfaceLowest,
                        border = BorderStroke(1.dp, if (isSelected) pillAccent else BorderSubtle)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (isSelected) pillAccent else TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = label,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) TextPrimary else TextSecondary
                            )
                        }
                    }
                }
            }

            // Description of active profile
            val profileDesc = when (currentProfile) {
                "battery" -> "Mengoptimalkan frekuensi untuk daya tahan baterai maksimal dan suhu tetap dingin."
                "gaming" -> "Menaikkan batas frekuensi bawah dan mempercepat akselerasi tugas untuk stabilitas frame rate."
                "extreme" -> "Mengunci frekuensi tertinggi tanpa pembatasan termal untuk performa benchmark puncak."
                else -> "Menyeimbangkan efisiensi daya harian dengan akselerasi cerdas saat aplikasi dibuka."
            }
            Text(
                text = profileDesc,
                fontSize = 10.5.sp,
                color = TextTertiary,
                lineHeight = 14.sp
            )

            HorizontalDivider(color = BorderSubtle, thickness = 0.8.dp, modifier = Modifier.padding(vertical = 2.dp))

            // Progressive Disclosure Toggle: "Pengaturan Lanjutan >"
            Surface(
                onClick = { isAdvancedExpanded = !isAdvancedExpanded },
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(1.dp, if (isAdvancedExpanded) AccentCyan.copy(alpha = 0.4f) else BorderGlass),
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Pengaturan Frekuensi & Kernel Lanjutan",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (isAdvancedExpanded) "Tutup pengaturan terperinci" else "Atur frekuensi diskrit per kluster & governor",
                                fontSize = 9.5.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Icon(
                        imageVector = if (isAdvancedExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Level 2 Expanded Area: Embedded Cluster Tuner & Hardware Boost
            AnimatedVisibility(
                visible = isAdvancedExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Embed CpuClusterTunerCard for full granular control (Dual-Pill OPP picker, no slider)
                    CpuClusterTunerCard(
                        clusters = clusters,
                        governorTunables = governorTunables,
                        onFreqChange = onFreqChange,
                        onGovChange = onGovChange,
                        onLoadTunables = onLoadTunables,
                        onTunableChange = onTunableChange,
                        onLockToggle = onLockToggle
                    )

                    // Platform Hardware Boost (MediaTek PPM / Qualcomm Input Boost)
                    if (schedInfo.isPpmSupported || schedInfo.isQcomBoostSupported) {
                        PlatformHardwareEngineCard(
                            schedInfo = schedInfo,
                            onPpmPolicyChange = onPpmPolicyChange,
                            onQcomTouchboostChange = onQcomTouchboostChange,
                            onQcomInputBoostChange = onQcomInputBoostChange
                        )
                    }

                    // Hidden Extreme Mode Trigger with Safety Confirmation
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AccentRed.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Mode Ekstrem (Tanpa Batas)",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentRed
                                )
                                Text(
                                    text = "Kunci frekuensi maksimum CPU. Menghasilkan panas tinggi dan baterai lebih boros.",
                                    fontSize = 9.5.sp,
                                    color = TextSecondary,
                                    lineHeight = 13.sp
                                )
                            }

                            Button(
                                onClick = { showExtremeConfirmDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentRed,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Aktifkan", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
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
}

// ============================================================
//  5. CPU UNIFIED SYSTEM CARD (TAB 1: SYSTEM CONTROLS)
// ============================================================

@Composable
fun CpuUnifiedSystemCard(
    context: Context,
    uiState: LynxUiState,
    onApplySystemPreset: (String) -> Unit,
    onApplyCpuSetPreset: (String) -> Unit,
    onToggleCpuSetCore: (String, Int) -> Unit,
    onSetCpuSetApplyOnBoot: (Boolean) -> Unit,
    onApplyCpuIdlePreset: (String) -> Unit,
    onSetCoreParkingMode: (String) -> Unit,
    onToggleCStateDisabled: (Int, Boolean) -> Unit,
    onArmPllModeChange: (Boolean) -> Unit,
    onSetIdleApplyOnBoot: (Boolean) -> Unit,
    onUclampChange: (Float, Float) -> Unit,
    onMigrationChange: (Float, Float) -> Unit,
    onResetSection: (Pair<String, String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var isAdvancedSystemExpanded by remember { mutableStateOf(false) }
    var activeTooltip by remember { mutableStateOf<String?>(null) }
    val schedInfo = uiState.schedulerInfo

    LynxCard(
        title = "OPTIMASI SISTEM & PENJADWAL",
        icon = Icons.Default.SettingsSuggest,
        accentColor = AccentOrange,
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Level 1: 1-Click System Optimization Presets
            Text(
                text = "PRESET OPTIMASI SISTEM",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 0.8.sp
            )

            val presets = listOf(
                Triple("balanced", "Seimbang", Icons.Default.Tune),
                Triple("gaming", "Responsif", Icons.Default.Bolt),
                Triple("battery", "Hemat Daya", Icons.Default.BatteryChargingFull)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presets.forEach { (key, label, icon) ->
                    val isSelected = schedInfo.activePreset.equals(key, true)
                    Surface(
                        onClick = { onApplySystemPreset(key) },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 44.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) AccentOrange.copy(alpha = 0.22f) else BgSurfaceLowest,
                        border = BorderStroke(1.dp, if (isSelected) AccentOrange else BorderSubtle)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (isSelected) AccentOrange else TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = label,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) TextPrimary else TextSecondary
                            )
                        }
                    }
                }
            }

            Text(
                text = "Preset menyelaraskan latensi kernel, alokasi beban aplikasi penting, dan status hemat daya secara otomatis.",
                fontSize = 10.5.sp,
                color = TextTertiary,
                lineHeight = 14.sp
            )

            HorizontalDivider(color = BorderSubtle, thickness = 0.8.dp, modifier = Modifier.padding(vertical = 2.dp))

            // Progressive Disclosure: "Pengaturan Sistem Lanjutan >"
            Surface(
                onClick = { isAdvancedSystemExpanded = !isAdvancedSystemExpanded },
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(1.dp, if (isAdvancedSystemExpanded) AccentOrange.copy(alpha = 0.4f) else BorderGlass),
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = AccentOrange,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Pengaturan Sistem Lanjutan",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (isAdvancedSystemExpanded) "Tutup pengaturan lanjutan" else "Task Shield, Core Efficiency & Penjadwal Kernel",
                                fontSize = 9.5.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Icon(
                        imageVector = if (isAdvancedSystemExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = AccentOrange,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Level 2 Expanded Area: Detailed System Tunables with Human-Friendly Titles
            AnimatedVisibility(
                visible = isAdvancedSystemExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // 1. Core Efficiency (Formerly CPU Idle Core Parking)
                    CpuIdleCoreParkingCard(
                        cpuIdle = uiState.cpuIdle,
                        clusters = uiState.clusters,
                        onApplyPreset = onApplyCpuIdlePreset,
                        onSetCoreParkingMode = onSetCoreParkingMode,
                        onToggleCStateDisabled = { idx, dis -> onToggleCStateDisabled(idx, dis) },
                        onArmPllModeChange = onArmPllModeChange,
                        onApplyOnBootChange = onSetIdleApplyOnBoot,
                        onResetToOem = { onResetSection("cpuidle" to "Core Efficiency") },
                        isModified = uiState.cpuIdle.applyOnBoot
                    )

                    // 2. Task Shield & Isolasi Aplikasi (Formerly CPU Sets)
                    CpuSetsTaskShieldCard(
                        cpuSets = uiState.cpuSets,
                        clusters = uiState.clusters,
                        onApplyPreset = onApplyCpuSetPreset,
                        onToggleCore = onToggleCpuSetCore,
                        onApplyOnBootChange = onSetCpuSetApplyOnBoot,
                        onResetToOem = { onResetSection("cpuset" to "Task Shield & Isolasi") },
                        isModified = uiState.cpuSets.applyOnBoot
                    )

                    // 3. Task Priority Boost (Uclamp) & Kernel Scheduler Card
                    KernelSchedulerCard(
                        schedInfo = schedInfo,
                        onApplyPreset = onApplySystemPreset,
                        onUclampChange = onUclampChange,
                        onMigrationChange = onMigrationChange,
                        onShowTooltip = { activeTooltip = it },
                        onResetToOem = { onResetSection("scheduler" to "Penjadwal Kernel") }
                    )
                }
            }
        }
    }

    // Technical Tooltip Dialog
    if (activeTooltip != null) {
        AlertDialog(
            onDismissRequest = { activeTooltip = null },
            containerColor = BgCard,
            title = {
                Text(
                    text = "Informasi Kernel Hardware",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = activeTooltip!!,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { activeTooltip = null }) {
                    Text("Tutup", color = AccentCyan)
                }
            }
        )
    }
}

// ============================================================
//  6. KERNEL SCHEDULER CARD (EAS UCLAMP & MIGRATION)
// ============================================================

@Composable
fun KernelSchedulerCard(
    schedInfo: SchedulerInfo,
    onApplyPreset: (String) -> Unit,
    onUclampChange: (Float, Float) -> Unit,
    onMigrationChange: (Float, Float) -> Unit,
    onShowTooltip: (String) -> Unit,
    onResetToOem: () -> Unit,
    modifier: Modifier = Modifier
) {
    var uclampMin by remember { mutableFloatStateOf(schedInfo.uclampMin.toFloat()) }
    var uclampMax by remember { mutableFloatStateOf(schedInfo.uclampMax.toFloat()) }

    LaunchedEffect(schedInfo) {
        uclampMin = schedInfo.uclampMin.toFloat()
        uclampMax = schedInfo.uclampMax.toFloat()
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BgElevated,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Task Priority Boost (Penjadwal EAS)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                IconButton(
                    onClick = {
                        onShowTooltip("Kernel Node: /proc/sys/kernel/sched_util_clamp_min\n\nFitur ini menginstruksikan penjadwal Energy Aware Scheduling (EAS) untuk mengalokasikan kapasitas frekuensi minimum langsung saat aplikasi prioritas berjalan, meminimalkan jeda respons sentuhan.")
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info Teknis",
                        tint = TextTertiary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Text(
                text = "Mengatur percepatan frekuensi langsung untuk aplikasi yang sedang aktif di layar.",
                fontSize = 10.5.sp,
                color = TextSecondary
            )

            // Uclamp Min Slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Prioritas Frekuensi Minimum", fontSize = 10.5.sp, color = TextPrimary)
                    Text("${uclampMin.toInt()}%", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
                }
                Slider(
                    value = uclampMin,
                    onValueChange = { uclampMin = it },
                    onValueChangeFinished = { onUclampChange(uclampMin, uclampMax) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentCyan,
                        activeTrackColor = AccentCyan,
                        inactiveTrackColor = BgSurfaceLowest
                    )
                )
            }
        }
    }
}

// ============================================================
//  7. CPU HARDWARE MONITOR CARD (TAB 2: MONITOR)
// ============================================================

@Composable
fun CpuHardwareMonitorCard(
    uiState: LynxUiState,
    onToggleCore: (CpuCoreInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val cores = uiState.cpuCores
    val history = uiState.cpuLoadHistory
    val curLoad = uiState.totalCpuLoadPercent.coerceIn(0, 100)
    val minVal = if (history.isNotEmpty()) history.minOrNull() ?: curLoad else curLoad
    val maxVal = if (history.isNotEmpty()) history.maxOrNull() ?: curLoad else curLoad
    val avgVal = if (history.isNotEmpty()) history.average().toInt() else curLoad

    var showCore0Notice by remember { mutableStateOf(false) }
    var pendingHotplugCore by remember { mutableStateOf<CpuCoreInfo?>(null) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Master Hero Card (8-Core Matrix + Bezier Waveform)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = BgCard,
            border = BorderStroke(1.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Header: Title
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "CPU MONITOR & SILICON TELEMETRY",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = BgSurfaceLowest,
                        border = BorderStroke(0.8.dp, BorderSubtle)
                    ) {
                        Text(
                            text = "REAL-TIME",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentGreen,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                // Bezier Curve Load Waveform
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Beban CPU Real-Time", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text("Min $minVal% • Avg $avgVal% • Max $maxVal%", fontSize = 9.5.sp, color = TextSecondary)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        if (w <= 0 || h <= 0) return@Canvas

                        val gridColor = BorderSubtle
                        drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, h * 0.25f), end = androidx.compose.ui.geometry.Offset(w, h * 0.25f), strokeWidth = 1f)
                        drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, h * 0.50f), end = androidx.compose.ui.geometry.Offset(w, h * 0.50f), strokeWidth = 1f)
                        drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, h * 0.75f), end = androidx.compose.ui.geometry.Offset(w, h * 0.75f), strokeWidth = 1f)

                        val points = if (history.size < 2) listOf(curLoad, curLoad) else history
                        val stepX = w / (points.size - 1).coerceAtLeast(1)
                        val path = Path()
                        val fillPath = Path()

                        points.forEachIndexed { i, load ->
                            val normY = (1f - (load.coerceIn(0, 100) / 100f)) * (h - 8f) + 4f
                            val x = i * stepX
                            if (i == 0) {
                                path.moveTo(x, normY)
                                fillPath.moveTo(x, h)
                                fillPath.lineTo(x, normY)
                            } else {
                                val prevLoad = points[i - 1]
                                val prevNormY = (1f - (prevLoad.coerceIn(0, 100) / 100f)) * (h - 8f) + 4f
                                val prevX = (i - 1) * stepX
                                val cx1 = prevX + (x - prevX) / 2f
                                val cy1 = prevNormY
                                val cx2 = prevX + (x - prevX) / 2f
                                val cy2 = normY
                                path.cubicTo(cx1, cy1, cx2, cy2, x, normY)
                                fillPath.cubicTo(cx1, cy1, cx2, cy2, x, normY)
                            }
                        }

                        fillPath.lineTo(w, h)
                        fillPath.close()

                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(AccentGreen.copy(alpha = 0.35f), AccentGreen.copy(alpha = 0.05f), Color.Transparent)
                            )
                        )
                        drawPath(path = path, color = AccentGreen, style = Stroke(width = 2f))

                        val lastX = w
                        val lastY = (1f - (curLoad / 100f)) * (h - 8f) + 4f
                        drawCircle(color = AccentGreen.copy(alpha = 0.3f), radius = 6f, center = androidx.compose.ui.geometry.Offset(lastX, lastY))
                        drawCircle(color = AccentGreen, radius = 3f, center = androidx.compose.ui.geometry.Offset(lastX, lastY))
                    }
                }

                HorizontalDivider(color = BorderSubtle, thickness = 1.dp, modifier = Modifier.padding(vertical = 10.dp))

                // Per-Core Silicon Status Matrix
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("STATUS PER-CORE SILICON", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 0.8.sp)
                    Text("Ketuk core untuk hotplug", fontSize = 9.sp, color = TextTertiary)
                }

                val rows = cores.chunked(4)
                rows.forEachIndexed { rowIndex, quad ->
                    if (rowIndex > 0) Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quad.forEach { core ->
                            val parentCluster = uiState.clusters.find { it.containsCore(core.coreId) }
                            val isPerfCore = parentCluster?.let { it.role.contains("Big", true) || it.role.contains("Perf", true) || it.id > 0 } ?: (core.coreId >= 6)
                            val coreAccent = if (!core.isOnline) TextTertiary else if (isPerfCore) AccentOrange else AccentCyan
                            val load = core.loadPercent.coerceIn(0, 100)

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BgSurfaceLowest,
                                border = BorderStroke(0.8.dp, if (core.isOnline) coreAccent.copy(alpha = 0.35f) else BorderSubtle),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        if (core.coreId == 0) {
                                            showCore0Notice = true
                                        } else if (core.isSwitchable) {
                                            pendingHotplugCore = core
                                        }
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "C${core.coreId}",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (core.isOnline) coreAccent else TextSecondary
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(if (core.isOnline) coreAccent else AccentRed)
                                        )
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = if (core.isOnline) "${core.curFreqKhz / 1000}" else "OFF",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (core.isOnline) TextPrimary else AccentRed
                                    )
                                    if (core.isOnline) {
                                        Text("MHz", fontSize = 8.sp, color = TextSecondary, lineHeight = 8.sp)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(1.5.dp))
                                            .background(BgElevated)
                                    ) {
                                        if (core.isOnline) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(fraction = (load / 100f).coerceIn(0.04f, 1f))
                                                    .fillMaxHeight()
                                                    .background(coreAccent)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = if (core.isOnline) "$load%" else "--",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (core.isOnline) TextSecondary else TextTertiary
                                    )
                                }
                            }
                        }
                        if (quad.size < 4) {
                            for (i in 0 until (4 - quad.size)) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // Top CPU Consumer Processes Card (Scene-Style)
        TopProcessListCard(procs = uiState.topCpuProcesses.take(8))

        // Hardware Topology Spec Card
        CpuTopologySpecCard(
            socPlatform = uiState.socPlatformName,
            socTopology = uiState.socTopology,
            clusters = uiState.clusters
        )
    }

    // Hotplug Confirmation Dialog
    if (pendingHotplugCore != null) {
        val core = pendingHotplugCore!!
        val actionText = if (core.isOnline) "mematikan (Offline)" else "mengaktifkan (Online)"
        AlertDialog(
            onDismissRequest = { pendingHotplugCore = null },
            containerColor = BgCard,
            title = {
                Text(
                    text = "Konfirmasi CPU Hotplug",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "Apakah Anda yakin ingin $actionText Core ${core.coreId}?",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val c = core
                        pendingHotplugCore = null
                        onToggleCore(c)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = Color.Black)
                ) {
                    Text("Terapkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingHotplugCore = null }) {
                    Text("Batal", color = TextSecondary)
                }
            }
        )
    }

    // Core 0 Protection Warning Dialog
    if (showCore0Notice) {
        AlertDialog(
            onDismissRequest = { showCore0Notice = false },
            containerColor = BgCard,
            title = {
                Text("Core 0 Dilindungi", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Text(
                    "Core 0 adalah Master Core sistem operasi Android yang menjalankan bootloader dan init daemon utama. Mematikan Core 0 akan memicu kernel panic seketika.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { showCore0Notice = false }) {
                    Text("Mengerti", color = AccentCyan)
                }
            }
        )
    }
}

// ============================================================
//  8. TOP PROCESS LIST CARD (SCENE-STYLE PROCESS MONITOR)
// ============================================================

@Composable
fun TopProcessListCard(
    procs: List<CpuProcessInfo>,
    modifier: Modifier = Modifier
) {
    LynxCard(
        title = "PROSES KONSUMSI CPU TERTINGGI",
        icon = Icons.Default.Terminal,
        accentColor = AccentOrange,
        modifier = modifier
    ) {
        if (procs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(60.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Memindai proses aktif...", fontSize = 11.sp, color = TextSecondary)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                procs.forEach { p ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(BgElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Widgets,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = p.name,
                                fontSize = 11.sp,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Text(
                            text = String.format(Locale.US, "%.1f%%", p.cpuPercent),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentOrange
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
//  9. CPU TOPOLOGY SPEC CARD (HARDWARE TRUTH ARCHITECTURE)
// ============================================================

@Composable
fun CpuTopologySpecCard(
    socPlatform: String,
    socTopology: String,
    clusters: List<CpuClusterInfo>,
    modifier: Modifier = Modifier
) {
    LynxCard(
        title = "TOPOLOGI SILIKON & LEVEL FREKUENSI",
        icon = Icons.Default.Memory,
        accentColor = AccentCyan,
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Chipset & Topology Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("MODEL CHIPSET", fontSize = 8.5.sp, color = TextTertiary, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (socPlatform.isNotBlank()) socPlatform else "Qualcomm / MediaTek ARM",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("SUSUNAN INTI", fontSize = 8.5.sp, color = TextTertiary, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (socTopology.isNotBlank()) socTopology else "8 Cores Heterogeneous",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            HorizontalDivider(color = BorderSubtle, thickness = 0.8.dp, modifier = Modifier.padding(vertical = 4.dp))

            // Cluster OPP Frequency Levels Listing
            clusters.forEach { cluster ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Kluster ${cluster.id} (${cluster.role})",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan
                        )
                        Text(
                            text = "${cluster.availFreqs.size} Level Frekuensi",
                            fontSize = 9.5.sp,
                            color = TextTertiary
                        )
                    }

                    // Frequency chips preview
                    val freqs = cluster.availFreqs.map { it / 1000 }
                    Text(
                        text = freqs.joinToString(" • ") { "$it MHz" },
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary,
                        lineHeight = 13.sp
                    )
                }
            }
        }
    }
}
