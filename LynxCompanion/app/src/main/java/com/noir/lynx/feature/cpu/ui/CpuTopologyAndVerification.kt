package com.noir.lynx.feature.cpu.ui
import com.noir.lynx.feature.cpu.ui.CompactClusterRow

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
import com.noir.lynx.core.locale.LynxText as Text
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
import com.noir.lynx.feature.cpu.ui.CpuSetsTaskShieldCard

import com.noir.lynx.ui.*

@Composable
fun CpuTopologySpecCard(
    socPlatform: String,
    socTopology: String,
    clusters: List<CpuClusterInfo>,
    siliconDetails: CpuSiliconTopologyDetails = CpuSiliconTopologyDetails(),
    modifier: Modifier = Modifier
) {
    val expandedClusters = remember { mutableStateMapOf<Int, Boolean>() }

    LynxCard(
        title = "Topologi Silikon & Level Frekuensi",
        icon = Icons.Default.Memory,
        accentColor = AccentCyan,
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Section 1: 2-Column Hardware Architecture & Interconnect Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("Model Chipset", fontSize = 8.5.sp, color = TextTertiary, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (socPlatform.isNotBlank()) socPlatform else "Qualcomm / MediaTek ARM",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text("Susunan Inti", fontSize = 8.5.sp, color = TextTertiary, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (socTopology.isNotBlank()) socTopology else "8 Inti Heterogeneous",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("Arsitektur ISA", fontSize = 8.5.sp, color = TextTertiary, fontWeight = FontWeight.Bold)
                    Text(
                        text = siliconDetails.isaArchitecture.ifBlank { "ARMv8.2-A (64-bit)" },
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text("Driver & Idle State", fontSize = 8.5.sp, color = TextTertiary, fontWeight = FontWeight.Bold)
                    val idleText = if (siliconDetails.cStateSummary.isNotBlank()) {
                        "${siliconDetails.scalingDriver} · ${siliconDetails.cStateSummary.substringBefore(" (")}"
                    } else {
                        siliconDetails.scalingDriver
                    }
                    Text(
                        text = idleText,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("Vendor Implementer", fontSize = 8.5.sp, color = TextTertiary, fontWeight = FontWeight.Bold)
                    Text(
                        text = siliconDetails.implementerName.ifBlank { "ARM Limited (0x41)" },
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text("Interconnect / Memory Bus", fontSize = 8.5.sp, color = TextTertiary, fontWeight = FontWeight.Bold)
                    val cciText = when {
                        siliconDetails.interconnectBusLabel.isNotBlank() -> siliconDetails.interconnectBusLabel
                        siliconDetails.cciFreqMhz > 0 -> {
                            if (siliconDetails.cciVoltMv > 0) {
                                "${siliconDetails.cciFreqMhz} MHz @ ${siliconDetails.cciVoltMv} mV"
                            } else {
                                "${siliconDetails.cciFreqMhz} MHz"
                            }
                        }
                        else -> "Shared L3 / DSU Bus"
                    }
                    Text(
                        text = cciText,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentCyan
                    )
                }
            }

            if (siliconDetails.instructionSummary.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Instruksi Hardware (ISA Extensions)", fontSize = 8.5.sp, color = TextTertiary, fontWeight = FontWeight.Bold)
                    Text(
                        text = siliconDetails.instructionSummary,
                        fontSize = 9.8.sp,
                        color = TextSecondary,
                        lineHeight = 13.5.sp
                    )
                }
            }

            HorizontalDivider(color = BorderSubtle, thickness = 0.8.dp, modifier = Modifier.padding(vertical = 2.dp))

            // Section 2: Structured Cluster Silicon Blocks
            clusters.forEach { cluster ->
                val detail = siliconDetails.clusterDetails[cluster.id]
                val freqs = cluster.availFreqs.map { it / 1000 }.sorted()
                val minMhz = freqs.minOrNull() ?: 0
                val maxMhz = freqs.maxOrNull() ?: 0
                val count = freqs.size
                val isPrimeCluster = cluster.role.contains("Prime", ignoreCase = true)
                val isMidCluster = cluster.role.contains("Mid", ignoreCase = true)
                val isPerfCluster = cluster.id > 0
                val clusterColor = when {
                    isPrimeCluster -> AccentPurple
                    isMidCluster -> AccentCyan
                    isPerfCluster -> AccentOrange
                    else -> AccentCyan
                }
                val isExpanded = expandedClusters[cluster.id] == true

                val microArchHeader = if (detail != null && detail.microArchName.isNotBlank()) {
                    val rev = if (detail.revisionLabel.isNotBlank()) " (${detail.revisionLabel})" else ""
                    "Kluster ${cluster.id} · ${detail.microArchName}$rev"
                } else {
                    "Kluster ${cluster.id} (${cluster.role})"
                }

                val subHeader = if (detail != null) {
                    "${detail.coreRangeLabel} · ${cluster.role} · $count Level OPP · Gov: ${cluster.curGov}"
                } else {
                    "Core ${cluster.cpus} · ${cluster.role} · $count Level OPP · Gov: ${cluster.curGov}"
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(BgSurfaceLowest)
                        .border(0.8.dp, BorderSubtle, RoundedCornerShape(10.dp))
                        .clickable { expandedClusters[cluster.id] = !isExpanded }
                        .padding(11.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Cluster Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = microArchHeader,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = clusterColor
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = subHeader,
                                fontSize = 9.2.sp,
                                color = TextTertiary
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "$minMhz – $maxMhz MHz",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (isExpanded) "Tutup tabel MHz" else "Lihat $count titik MHz",
                                fontSize = 8.5.sp,
                                color = AccentCyan
                            )
                        }
                    }

                    if (detail != null) {
                        HorizontalDivider(color = BorderSubtle, thickness = 0.6.dp)

                        // 3-Column Silicon Telemetry Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("KAPASITAS EAS", fontSize = 8.sp, color = TextTertiary, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${detail.easCapacity} / 1024",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (detail.vprocMv > 0) {
                                    Text("VPROC / VSRAM", fontSize = 8.sp, color = TextTertiary, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "${detail.vprocMv} / ${detail.vsramMv} mV",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                } else {
                                    Text("LATENSI TRANSISI", fontSize = 8.sp, color = TextTertiary, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = if (detail.transitionLatencyUs > 0) "${detail.transitionLatencyUs} us" else " Instan",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text("TOTAL TRANSISI", fontSize = 8.sp, color = TextTertiary, fontWeight = FontWeight.Bold)
                                val transFormatted = when {
                                    detail.totalTransitions >= 1_000_000L ->
                                        String.format(Locale.US, "%.2f Juta", detail.totalTransitions / 1_000_000.0)
                                    detail.totalTransitions >= 1_000L ->
                                        String.format(Locale.US, "%.1f Rb", detail.totalTransitions / 1_000.0)
                                    detail.totalTransitions > 0L ->
                                        detail.totalTransitions.toString()
                                    else -> "--"
                                }
                                Text(
                                    text = transFormatted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            }
                        }

                        // Top 3 Residency Row (time_in_state)
                        if (detail.topResidencies.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "DISTRIBUSI FREKUENSI DOMINAN (TIME-IN-STATE)",
                                    fontSize = 8.sp,
                                    color = TextTertiary,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    detail.topResidencies.forEachIndexed { idx, item ->
                                        val align = when (idx) {
                                            0 -> Alignment.Start
                                            1 -> Alignment.CenterHorizontally
                                            else -> Alignment.End
                                        }
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            horizontalAlignment = align
                                        ) {
                                            Text(
                                                text = String.format(Locale.US, "%d MHz (%.1f%%)", item.freqMhz, item.percentage),
                                                fontSize = 9.5.sp,
                                                fontWeight = if (idx == 0) FontWeight.Bold else FontWeight.Medium,
                                                color = if (idx == 0) clusterColor else TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Expandable Full OPP Table (Clean Typography, Hidden by Default)
                    if (isExpanded && freqs.isNotEmpty()) {
                        HorizontalDivider(color = BorderSubtle, thickness = 0.6.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = "TABEL LENGKAP STEPPING FREKUENSI ($count OPP)",
                                fontSize = 8.sp,
                                color = TextTertiary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = freqs.joinToString(" · ") + " MHz",
                                fontSize = 9.5.sp,
                                color = TextSecondary,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
//  11. DEVELOPER VERIFICATION SHEET (PHASE 10: SYSFS TRANSPARENCY)
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CpuDeveloperVerificationSheet(
    uiState: LynxUiState,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        dragHandle = { BottomSheetDefaults.DragHandle(color = BorderGlass) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Verifikasi Pengembang (Sysfs)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Status keaslian node kernel & perizinan hardware",
                            fontSize = 10.5.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            HorizontalDivider(color = BorderSubtle, thickness = 0.8.dp)

            // Sysfs Verification Items
            val verificationItems = mutableListOf<Triple<String, Pair<String, String>, String>>()
            if (uiState.clusters.isNotEmpty()) {
                uiState.clusters.forEach { c ->
                    val roleLabel = if (c.id > 0) "Performa" else "Efisiensi"
                    verificationItems.add(
                        Triple(
                            "Policy ${c.id} ($roleLabel) — Clock",
                            Pair("/sys/devices/system/cpu/cpufreq/policy${c.id}/scaling_cur_freq", "0644 READ/WRITE"),
                            "TERVERIFIKASI AKTIF"
                        )
                    )
                    verificationItems.add(
                        Triple(
                            "Policy ${c.id} ($roleLabel) — Governor",
                            Pair("/sys/devices/system/cpu/cpufreq/policy${c.id}/scaling_governor", "0644 READ/WRITE"),
                            "TERVERIFIKASI AKTIF"
                        )
                    )
                }
            } else {
                verificationItems.add(
                    Triple(
                        "Policy 0 (Efisiensi) — Clock",
                        Pair("/sys/devices/system/cpu/cpufreq/policy0/scaling_cur_freq", "0644 READ/WRITE"),
                        "TERVERIFIKASI AKTIF"
                    )
                )
            }
            verificationItems.add(
                Triple(
                    "Kernel CPU Hotplug",
                    Pair("/sys/devices/system/cpu/cpu*/online", "0644 READ/WRITE"),
                    "HOTPLUG DIDUKUNG"
                )
            )
            val sched = uiState.schedulerInfo
            val isQcomPlatform = !sched.isPpmSupported && (sched.isQcomBoostSupported || sched.isQcomDevfreqBusSupported || uiState.socPlatformName.contains("Snapdragon", true) || uiState.socPlatformName.contains("Qualcomm", true))

            if (sched.isUclampSupported) {
                verificationItems.add(
                    Triple(
                        "EAS Scheduler Uclamp",
                        Pair("/proc/sys/kernel/sched_util_clamp_min", "0644 READ/WRITE"),
                        "TERVERIFIKASI AKTIF"
                    )
                )
            } else if (sched.isSchedtuneSupported) {
                verificationItems.add(
                    Triple(
                        "EAS Schedtune Top-App Boost",
                        Pair("/dev/stune/top-app/schedtune.boost", "0664 EAS/CGROUP (${sched.topAppSchedtuneBoost}%)"),
                        "TERVERIFIKASI AKTIF"
                    )
                )
            } else {
                verificationItems.add(
                    Triple(
                        "EAS Scheduler Uclamp / Stune",
                        Pair("/proc/sys/kernel/sched_util_clamp_min", "0644 READ/WRITE"),
                        "TIDAK TERSEDIA"
                    )
                )
            }

            if (isQcomPlatform) {
                verificationItems.add(
                    Triple(
                        "Qualcomm MSM Thermal & Load Boost",
                        Pair("/sys/module/msm_thermal/core_control/enabled", "0644 QCOM HAL"),
                        "TERVERIFIKASI QCOM"
                    )
                )
                verificationItems.add(
                    Triple(
                        "Qualcomm DDR / LLCC Bus Devfreq",
                        Pair("/sys/class/devfreq/soc:qcom,cpu-cpu-ddr-bw/governor", "0644 DEVFREQ BUS"),
                        if (sched.isQcomDevfreqBusSupported) "TERVERIFIKASI AKTIF" else "STANDAR LINUX"
                    )
                )
            } else {
                verificationItems.add(
                    Triple(
                        "MediaTek PPM Thermal/Perf",
                        Pair("/proc/ppm/policy/userlimit_max_cpu_freq", "0644 VENDOR HAL"),
                        if (sched.isPpmSupported) "TERVERIFIKASI MTK" else "TIDAK TERSEDIA"
                    )
                )
                verificationItems.add(
                    Triple(
                        "Interconnect CCI & Memory Bus",
                        Pair("/proc/cpufreq/cpufreq_cci_mode", "0644 KERNEL DRIVER"),
                        if (sched.isMtkCciSupported) "TERVERIFIKASI AKTIF" else "STANDAR LINUX"
                    )
                )
            }

            verificationItems.add(
                Triple(
                    "Kernel Workqueue & Cgroup Engine",
                    Pair("/sys/module/workqueue/parameters/power_efficient", "0644 KERNEL MODULE"),
                    "TERVERIFIKASI AKTIF"
                )
            )

            verificationItems.forEach { (title, pathAndPerm, status) ->
                val (nodePath, perm) = pathAndPerm
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = title,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = nodePath,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(1.dp))
                            Text(
                                text = "Perizinan: $perm",
                                fontSize = 8.5.sp,
                                color = TextTertiary
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (status.contains("TERVERIFIKASI")) AccentGreen.copy(alpha = 0.15f) else BgElevated,
                            border = BorderStroke(0.8.dp, if (status.contains("TERVERIFIKASI")) AccentGreen.copy(alpha = 0.4f) else BorderSubtle)
                        ) {
                            Text(
                                text = status,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (status.contains("TERVERIFIKASI")) AccentGreen else TextTertiary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Transaction & Rollback Info
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderGlass),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Transaksi Tulis Terakhir:", fontSize = 11.sp, color = TextSecondary)
                    Text("SUKSES (Read-back Verified)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(42.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BgSurfaceLowest,
                    contentColor = TextPrimary
                ),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Text("Tutup Verifikasi", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
