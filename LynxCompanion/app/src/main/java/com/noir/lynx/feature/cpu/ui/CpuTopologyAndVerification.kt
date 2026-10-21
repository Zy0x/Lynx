package com.noir.lynx.feature.cpu.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.data.*
import com.noir.lynx.designsystem.theme.*
import java.util.Locale

/**
 * CpuTopologySpecCard — Redesigned Silicon Architecture & OPP Steppings.
 *
 * Design Standards:
 * 1. Master Bento Overview: 2x2 Clean Hardware Matrix (Chipset, ISA, Interconnect, Driver).
 * 2. Progressive Cluster Cards: Tactile accordions with Little/Big badges, OPP counts, and Time-In-State.
 * 3. Psychological Comfort: Sentence-case headers, clean monospace metrics, zero raw technical alarm.
 * 4. Transparent Verification: Integrated trigger to Developer Sysfs Verification Sheet.
 */
@Composable
fun CpuTopologySpecCard(
    socPlatform: String,
    socTopology: String,
    clusters: List<CpuClusterInfo>,
    siliconDetails: CpuSiliconTopologyDetails = CpuSiliconTopologyDetails(),
    uiState: LynxUiState? = null,
    onOpenVerification: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val expandedClusters = remember { mutableStateMapOf<Int, Boolean>() }
    var internalShowVerification by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF10141F),
        border = BorderStroke(0.8.dp, Brush.verticalGradient(listOf(Color(0x28FFFFFF), Color(0x10FFFFFF)))),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AccentCyan.copy(alpha = 0.12f),
                        border = BorderStroke(0.5.dp, AccentCyan.copy(alpha = 0.35f)),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Topologi Silikon & Kluster Inti", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("Arsitektur mikro, bus data, dan level frekuensi", fontSize = 9.5.sp, color = TextSecondary.copy(alpha = 0.75f))
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF0C0F17),
                    border = BorderStroke(0.5.dp, Color(0x18FFFFFF))
                ) {
                    Text(
                        text = "${clusters.size} Kluster",
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            // ── 1. 2x2 Bento Overview Grid ──────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Chipset & Vendor
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF0C0F17),
                        border = BorderStroke(0.6.dp, Color(0x15FFFFFF)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Model Chipset", fontSize = 8.5.sp, color = TextTertiary, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = socPlatform.ifBlank { "Qualcomm / MediaTek ARM" },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = siliconDetails.implementerName.ifBlank { "ARM Limited" },
                                fontSize = 8.5.sp,
                                color = TextSecondary.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Core Topology & ISA
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF0C0F17),
                        border = BorderStroke(0.6.dp, Color(0x15FFFFFF)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Susunan Inti & ISA", fontSize = 8.5.sp, color = TextTertiary, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = socTopology.ifBlank { "8 Inti Heterogen" },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = siliconDetails.isaArchitecture.ifBlank { "ARMv8-A (64-bit)" },
                                fontSize = 8.5.sp,
                                color = TextSecondary.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Interconnect & Memory Bus
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF0C0F17),
                        border = BorderStroke(0.6.dp, Color(0x15FFFFFF)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Interkoneksi Bus", fontSize = 8.5.sp, color = TextTertiary, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(2.dp))
                            val cciText = when {
                                siliconDetails.interconnectBusLabel.isNotBlank() -> siliconDetails.interconnectBusLabel
                                siliconDetails.cciFreqMhz > 0 -> "${siliconDetails.cciFreqMhz} MHz"
                                else -> "Shared L3 / DSU Bus"
                            }
                            Text(
                                text = cciText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Beban memori adaptif",
                                fontSize = 8.5.sp,
                                color = TextSecondary.copy(alpha = 0.7f),
                                maxLines = 1
                            )
                        }
                    }

                    // Driver CPUFreq & Idle
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF0C0F17),
                        border = BorderStroke(0.6.dp, Color(0x15FFFFFF)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Driver & Manajemen Daya", fontSize = 8.5.sp, color = TextTertiary, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = siliconDetails.scalingDriver.ifBlank { "cpufreq-dt" },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (siliconDetails.cStateSummary.isNotBlank()) siliconDetails.cStateSummary.substringBefore(" (") else "C-States Terintegrasi",
                                fontSize = 8.5.sp,
                                color = TextSecondary.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // ── 2. Structured Cluster Silicon Blocks ─────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                clusters.forEach { cluster ->
                    val detail = siliconDetails.clusterDetails[cluster.id]
                    val freqs = cluster.availFreqs.map { it / 1000 }.sorted()
                    val minMhz = freqs.minOrNull() ?: 0
                    val maxMhz = freqs.maxOrNull() ?: 0
                    val count = freqs.size
                    val isPrimeCluster = cluster.role.contains("Prime", ignoreCase = true)
                    val isPerfCluster = cluster.id > 0
                    val clusterColor = when {
                        isPrimeCluster -> AccentPurple
                        isPerfCluster -> AccentOrange
                        else -> AccentGreen
                    }
                    val isExpanded = expandedClusters[cluster.id] == true
                    val rotAngle by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "rot")

                    val microArchHeader = if (detail != null && detail.microArchName.isNotBlank()) {
                        "Kluster ${cluster.id} · ${detail.microArchName}"
                    } else {
                        "Kluster ${cluster.id} (${cluster.role})"
                    }

                    val subHeader = if (detail != null) {
                        "${detail.coreRangeLabel} • ${cluster.role} • $count OPP • Gov: ${cluster.curGov}"
                    } else {
                        "Core ${cluster.cpus} • ${cluster.role} • $count OPP • Gov: ${cluster.curGov}"
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0C0F17),
                        border = BorderStroke(0.6.dp, if (isExpanded) clusterColor.copy(alpha = 0.45f) else Color(0x15FFFFFF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Cluster Header Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        expandedClusters[cluster.id] = !isExpanded
                                    },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = clusterColor.copy(alpha = 0.15f),
                                        border = BorderStroke(0.5.dp, clusterColor.copy(alpha = 0.35f))
                                    ) {
                                        Text(
                                            text = if (isPerfCluster) "Big" else "Little",
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = clusterColor,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(microArchHeader, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                        Text(subHeader, fontSize = 9.sp, color = TextSecondary.copy(alpha = 0.75f))
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "$minMhz–$maxMhz MHz",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = clusterColor
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = TextTertiary,
                                        modifier = Modifier.size(16.dp).rotate(rotAngle)
                                    )
                                }
                            }

                            AnimatedVisibility(
                                visible = isExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    HorizontalDivider(color = Color(0x10FFFFFF), thickness = 0.6.dp)

                                    // Capacity & Telemetry Summary
                                    if (detail != null) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text("Kapasitas EAS", fontSize = 8.5.sp, color = TextTertiary)
                                                Text("${detail.easCapacity} / 1024", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TextPrimary)
                                            }
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("Latensi Transisi", fontSize = 8.5.sp, color = TextTertiary)
                                                Text(if (detail.transitionLatencyUs > 0) "${detail.transitionLatencyUs} µs" else "Instan", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TextPrimary)
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("Total Transisi", fontSize = 8.5.sp, color = TextTertiary)
                                                val trans = when {
                                                    detail.totalTransitions >= 1_000_000L -> String.format(Locale.US, "%.1fM", detail.totalTransitions / 1_000_000.0)
                                                    detail.totalTransitions >= 1_000L -> String.format(Locale.US, "%.1fK", detail.totalTransitions / 1_000.0)
                                                    else -> "${detail.totalTransitions}"
                                                }
                                                Text(trans, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TextPrimary)
                                            }
                                        }

                                        // Dominant Frequency Residency (Time-In-State)
                                        if (detail.topResidencies.isNotEmpty()) {
                                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                Text("Frekuensi Dominan (Time-In-State)", fontSize = 8.5.sp, color = TextTertiary, fontWeight = FontWeight.Medium)
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    detail.topResidencies.take(3).forEach { item ->
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = Color(0xFF10141F),
                                                            border = BorderStroke(0.5.dp, Color(0x15FFFFFF)),
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                                Text("${item.freqMhz} MHz", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = clusterColor)
                                                                Text(String.format(Locale.US, "%.1f%%", item.percentage), fontSize = 8.sp, color = TextSecondary)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Full OPP stepping frequency chips
                                    if (freqs.isNotEmpty()) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("Daftar Lengkap Titik Frekuensi ($count OPP)", fontSize = 8.5.sp, color = TextTertiary, fontWeight = FontWeight.Medium)
                                            Text(
                                                text = freqs.joinToString("  •  ") + " MHz",
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = TextSecondary.copy(alpha = 0.85f),
                                                lineHeight = 13.5.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── 3. Sysfs Verification Button ────────────────────────
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (onOpenVerification != null) {
                        onOpenVerification()
                    } else {
                        internalShowVerification = true
                    }
                },
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF0C0F17),
                border = BorderStroke(0.6.dp, AccentCyan.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth().height(42.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Verifikasi Integritas Node Sysfs", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AccentCyan)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = AccentGreen.copy(alpha = 0.15f),
                            border = BorderStroke(0.5.dp, AccentGreen.copy(alpha = 0.35f))
                        ) {
                            Text("100% Asli", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = AccentGreen, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AccentCyan.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }

    if (internalShowVerification && uiState != null) {
        CpuDeveloperVerificationSheet(
            uiState = uiState,
            onDismiss = { internalShowVerification = false }
        )
    }
}
