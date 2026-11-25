package com.noir.lynx.feature.cpu.ui

import com.noir.lynx.core.locale.localizedUiText
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
import androidx.compose.material.icons.automirrored.filled.*
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.noir.lynx.feature.cpu.ui.cluster.dialogs.QuickTunablesBottomSheet

import com.noir.lynx.ui.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactClusterRow(
    cluster: CpuClusterInfo,
    governorTunables: Map<Int, List<GovernorTunable>>,
    cpuCores: List<CpuCoreInfo> = emptyList(),
    onFreqChange: (policyId: Int, minFreq: Long?, maxFreq: Long?) -> Unit,
    onGovChange: (policyId: Int, gov: String) -> Unit,
    onLoadTunables: (policyId: Int, gov: String) -> Unit,
    onTunableChange: (policyId: Int, gov: String, key: String, value: String) -> Unit,
    onLockToggle: (policyId: Int, isLock: Boolean, minFreq: Long, maxFreq: Long) -> Unit,
    onApplyGovernorPreset: ((policyId: Int, preset: String) -> Unit)? = null,
    onSetCustomRateLimits: ((policyId: Int, upUs: Long, downUs: Long) -> Unit)? = null,
    defaultExpanded: Boolean = true,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var isExpanded by remember { mutableStateOf(defaultExpanded) }

    val clusterCores = remember(cpuCores, cluster) {
        cpuCores.filter { cluster.containsCore(it.coreId) }
    }
    val onlineCores = remember(clusterCores) { clusterCores.filter { it.isOnline } }
    val liveMhz = remember(onlineCores) {
        val peakKhz = onlineCores.maxOfOrNull { it.curFreqKhz } ?: 0L
        if (peakKhz > 0L) (peakKhz / 1000).toInt() else null
    }

    val isPrimeCluster = cluster.role.contains("Prime", ignoreCase = true)
    val isMidCluster = cluster.role.contains("Mid", ignoreCase = true)
    val isPerfCluster = cluster.id > 0
    val clusterAccent = when {
        isPrimeCluster -> AccentPurple
        isMidCluster -> AccentCyan
        isPerfCluster -> AccentOrange
        else -> AccentGreen
    }
    val clusterTitle = when {
        isPrimeCluster -> "Kluster Prime"
        isMidCluster -> "Kluster $PERFORMA_LABEL Mid"
        isPerfCluster -> "Kluster $PERFORMA_LABEL"
        else -> "Kluster Efisiensi"
    }
    val minMhz = (cluster.curMin / 1000).toInt()
    val maxMhz = (cluster.curMax / 1000).toInt()

    val govDisplayName = when (cluster.curGov.lowercase()) {
        "schedutil" -> "Balanced"
        "walt" -> "WALT Balanced"
        "sugov_ext" -> "Ext Balanced"
        "energy_step" -> "Energy Step"
        "interactive" -> "Interactive"
        "ondemand" -> "OnDemand"
        "performance" -> "Instant"
        "powersave" -> "Energy Saver"
        else -> cluster.curGov
    }

    var freqPickerTarget by remember { mutableStateOf<Pair<CpuClusterInfo, Boolean>?>(null) }
    var govPickerTarget by remember { mutableStateOf<CpuClusterInfo?>(null) }
    var tunablesTarget by remember { mutableStateOf<CpuClusterInfo?>(null) }
    var editingTunable by remember { mutableStateOf<Triple<Int, String, GovernorTunable>?>(null) }
    var editValueText by remember { mutableStateOf("") }

    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "clusterChevron"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF10141F),
        border = BorderStroke(0.8.dp, Color(0x18FFFFFF)),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row (Clickable to Toggle Expansion)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isExpanded = !isExpanded
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = clusterAccent.copy(alpha = 0.12f),
                        border = BorderStroke(0.6.dp, clusterAccent.copy(alpha = 0.25f)),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.DeveloperBoard,
                                contentDescription = null,
                                tint = clusterAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = clusterTitle,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "(Policy ${cluster.id})",
                                fontSize = 10.sp,
                                color = TextSecondary.copy(alpha = 0.6f),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        val coreCount = cluster.cpus.split(" ").filter { it.isNotBlank() }.size
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = "$coreCount Inti (${cluster.cpus}) • $minMhz-$maxMhz MHz",
                                fontSize = 10.sp,
                                color = TextSecondary.copy(alpha = 0.75f)
                            )
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = Color(0xFF141824),
                                border = BorderStroke(0.5.dp, Color(0x18FFFFFF))
                            ) {
                                Text(
                                    text = cluster.curGov,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = clusterAccent,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (liveMhz != null && liveMhz > 0) {
                        val liveColor = when {
                            liveMhz >= (maxMhz * 0.9f) -> AccentOrange
                            liveMhz <= (minMhz * 1.1f) -> AccentGreen
                            else -> AccentCyan
                        }
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = liveColor.copy(alpha = 0.14f),
                            border = BorderStroke(0.6.dp, liveColor.copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(liveColor)
                                )
                                Text(
                                    text = "$liveMhz MHz",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = liveColor
                                )
                            }
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color(0x15FFFFFF),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isExpanded) "Tutup" else "Buka",
                                tint = if (isExpanded) clusterAccent else TextSecondary.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .size(17.dp)
                                    .rotate(rotationAngle)
                            )
                        }
                    }
                }
            }

            // Expanded Granular Controls (Level 2)
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = Color(0x15FFFFFF), thickness = 0.8.dp)

                    // 1. Frequency Levels (OPP Table Dual-Pill)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = clusterAccent,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "FREKUENSI HARDWARE (OPP)",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary.copy(alpha = 0.85f),
                                letterSpacing = 0.6.sp
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = Color(0xFF141824),
                            border = BorderStroke(0.5.dp, Color(0x18FFFFFF))
                        ) {
                            Text(
                                text = "${cluster.availFreqs.size} Level OPP",
                                fontSize = 8.5.sp,
                                color = TextSecondary.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Min MHz Pill
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                freqPickerTarget = Pair(cluster, true)
                            },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF141824),
                            border = BorderStroke(0.7.dp, Color(0x1AFFFFFF))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0x15FFFFFF),
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDownward,
                                                contentDescription = null,
                                                tint = TextSecondary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = "BATAS MIN (IDLE)",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextSecondary.copy(alpha = 0.65f),
                                            letterSpacing = 0.4.sp
                                        )
                                        Text(
                                            text = "$minMhz MHz",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = TextPrimary
                                        )
                                    }
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSecondary.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                            }
                        }

                        // Max MHz Pill
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                freqPickerTarget = Pair(cluster, false)
                            },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF141824),
                            border = BorderStroke(0.8.dp, clusterAccent.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = clusterAccent.copy(alpha = 0.15f),
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowUpward,
                                                contentDescription = null,
                                                tint = clusterAccent,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = "BATAS MAX (TURBO)",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = clusterAccent,
                                            letterSpacing = 0.4.sp
                                        )
                                        Text(
                                            text = "$maxMhz MHz",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = clusterAccent
                                        )
                                    }
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = clusterAccent, modifier = Modifier.size(16.dp))
                            }
                        }

                        // Thermal Lock Toggle
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onLockToggle(cluster.id, !cluster.isLocked, cluster.curMin, cluster.curMax)
                            },
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (cluster.isLocked) AccentRed.copy(alpha = 0.18f) else Color(0xFF141824))
                                .border(
                                    0.8.dp,
                                    if (cluster.isLocked) AccentRed.copy(alpha = 0.5f) else Color(0x18FFFFFF),
                                    RoundedCornerShape(10.dp)
                                )
                        ) {
                            Icon(
                                imageVector = if (cluster.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = localizedUiText("Kunci Frekuensi"),
                                tint = if (cluster.isLocked) AccentRed else TextSecondary.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // 2. CPU Response (Governor)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "RESPON DAYA & GUBERNUR",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary.copy(alpha = 0.85f),
                                letterSpacing = 0.6.sp
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = Color(0xFF141824),
                            border = BorderStroke(0.5.dp, Color(0x18FFFFFF))
                        ) {
                            Text(
                                text = cluster.curGov.uppercase(),
                                fontSize = 8.5.sp,
                                color = AccentCyan,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                govPickerTarget = cluster
                            },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF141824),
                            border = BorderStroke(0.7.dp, Color(0x1AFFFFFF))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = AccentCyan.copy(alpha = 0.14f),
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Speed,
                                                contentDescription = null,
                                                tint = AccentCyan,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = "GUBERNUR AKTIF",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextSecondary.copy(alpha = 0.65f),
                                            letterSpacing = 0.4.sp
                                        )
                                        Text(
                                            text = "$govDisplayName (${cluster.curGov})",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary
                                        )
                                    }
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSecondary.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                            }
                        }

                        // Tunables Button
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onLoadTunables(cluster.id, cluster.curGov)
                                tunablesTarget = cluster
                            },
                            modifier = Modifier.height(50.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF141824),
                            border = BorderStroke(0.8.dp, clusterAccent.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = clusterAccent, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "PARAMETER",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextSecondary.copy(alpha = 0.65f),
                                        letterSpacing = 0.4.sp
                                    )
                                    Text("Tunables", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = clusterAccent)
                                }
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = clusterAccent.copy(alpha = 0.6f), modifier = Modifier.size(10.dp))
                            }
                        }
                    }

                    // Status banner when locked
                    if (cluster.isLocked) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AccentRed.copy(alpha = 0.10f),
                            border = BorderStroke(0.6.dp, AccentRed.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = AccentRed, modifier = Modifier.size(14.dp))
                                Text(
                                    text = "Kunci Frekuensi Aktif: Clock ditahan pada rentang $minMhz-$maxMhz MHz.",
                                    fontSize = 10.sp,
                                    color = AccentRed,
                                    lineHeight = 13.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet: Frequency Picker
    if (freqPickerTarget != null) {
        val (targetCluster, isMinPicker) = freqPickerTarget!!
        ModalBottomSheet(
            onDismissRequest = { freqPickerTarget = null },
            containerColor = Color(0xFF141824),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0x30FFFFFF)) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = "Pilih Frekuensi ${if (isMinPicker) "Minimum" else "Maksimum"}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Policy ${targetCluster.id} (${targetCluster.role})",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                    flingBehavior = rememberFluidFlingBehavior(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(
                        items = targetCluster.availFreqs,
                        key = { it },
                        contentType = { "cpu_freq_item" }
                    ) { freqKhz ->
                        val mhz = (freqKhz / 1000).toInt()
                        val isCurrent = if (isMinPicker) targetCluster.curMin == freqKhz else targetCluster.curMax == freqKhz
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (isMinPicker) {
                                    onFreqChange(targetCluster.id, freqKhz, null)
                                } else {
                                    onFreqChange(targetCluster.id, null, freqKhz)
                                }
                                freqPickerTarget = null
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isCurrent) clusterAccent.copy(alpha = 0.16f) else Color(0xFF0C0F17),
                            border = if (isCurrent) BorderStroke(0.8.dp, clusterAccent) else BorderStroke(0.5.dp, Color(0x12FFFFFF))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("$mhz MHz", fontSize = 12.sp, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal, fontFamily = FontFamily.Monospace, color = TextPrimary)
                                if (isCurrent) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = clusterAccent, modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet: Governor Picker
    if (govPickerTarget != null) {
        val targetCluster = govPickerTarget!!
        ModalBottomSheet(
            onDismissRequest = { govPickerTarget = null },
            containerColor = Color(0xFF141824),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0x30FFFFFF)) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = "Pilih Respon CPU (Governor)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Policy ${targetCluster.id} (${targetCluster.role})",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                    flingBehavior = rememberFluidFlingBehavior(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(
                        items = targetCluster.availGovs,
                        key = { it },
                        contentType = { "cpu_gov_item" }
                    ) { gov ->
                        val isCurrent = targetCluster.curGov.equals(gov, true)
                        val humanGov = when (gov.lowercase()) {
                            "schedutil" -> "Seimbang (schedutil)"
                            "performance" -> "Respon Instan (performance)"
                            "powersave" -> "Hemat Daya (powersave)"
                            else -> gov
                        }
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onGovChange(targetCluster.id, gov)
                                govPickerTarget = null
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isCurrent) AccentCyan.copy(alpha = 0.16f) else Color(0xFF0C0F17),
                            border = if (isCurrent) BorderStroke(0.8.dp, AccentCyan) else BorderStroke(0.5.dp, Color(0x12FFFFFF))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(humanGov, fontSize = 12.sp, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal, color = TextPrimary)
                                if (isCurrent) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet: Governor Tunables (QuickTunablesBottomSheet with Schedutil Presets & Custom µs)
    tunablesTarget?.let { targetCluster ->
        val tunables = governorTunables[targetCluster.id] ?: emptyList()
        QuickTunablesBottomSheet(
            cluster = targetCluster,
            tunables = tunables,
            onDismiss = { tunablesTarget = null },
            onTunableChange = onTunableChange,
            onLoadTunables = onLoadTunables,
            onApplyGovernorPreset = onApplyGovernorPreset,
            onSetCustomRateLimits = onSetCustomRateLimits,
            onOpenEditTunable = { pId, gov, t ->
                editingTunable = Triple(pId, gov, t)
                editValueText = t.currentValue
            }
        )
    }

    // Edit Single Tunable Dialog
    if (editingTunable != null) {
        val (pId, gov, tunable) = editingTunable!!
        AlertDialog(
            onDismissRequest = { editingTunable = null },
            containerColor = BgCard,
            title = {
                Text("Edit Tunable: ${tunable.displayName}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Column {
                    Text("Node: ${tunable.key}", fontSize = 10.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editValueText,
                        onValueChange = { editValueText = it },
                        label = { Text("Nilai (${tunable.unit.ifBlank { "angka" }})") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onTunableChange(pId, gov, tunable.key, editValueText.trim())
                        editingTunable = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = Color.Black)
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingTunable = null }) {
                    Text("Batal", color = TextSecondary)
                }
            }
        )
    }
}

// ============================================================
//  5. CPU UNIFIED PERFORMANCE CARD (TAB 0: PERFORMANCE)
// ============================================================

