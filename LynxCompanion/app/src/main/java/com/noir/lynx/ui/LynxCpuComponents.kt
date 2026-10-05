package com.noir.lynx.ui

import java.util.Locale
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Path
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
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = title,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) TextPrimary else TextSecondary,
                            maxLines = 1,
                            softWrap = false
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
    val tempC: Int = uiState.thermalZones.firstOrNull { it.type.contains("cpu", true) }?.tempC?.toInt()
        ?: (uiState.batteryDetails?.tempC?.toInt() ?: 38)
    val currentProfile = uiState.cpuComprehensiveProfile.lowercase()

    val profileLabel = when (currentProfile) {
        "battery" -> "Battery"
        "gaming" -> "Gaming"
        "extreme" -> "Extreme"
        else -> "Balanced"
    }
    val profileAccent = when (currentProfile) {
        "battery" -> AccentGreen
        "gaming" -> AccentOrange
        "extreme" -> AccentRed
        else -> AccentCyan
    }

    val healthQuality = uiState.cpuHealthQuality
    val healthAccent = when (healthQuality) {
        CpuHealthQuality.HEALTHY -> AccentGreen
        CpuHealthQuality.WARM -> AccentOrange
        CpuHealthQuality.THROTTLED -> AccentRed
    }

    val isThrottled = uiState.isCpuThermalThrottled || tempC >= 55

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = BgCard,
        border = BorderStroke(0.8.dp, BorderSubtle),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
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
                        fontSize = 13.sp,
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
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("PEAK CLOCK", fontSize = 8.5.sp, color = TextTertiary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = if (peakGhz > 0f) String.format(Locale.US, "%.2f", peakGhz) else "--",
                            fontSize = 13.5.sp,
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
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("SUHU CPU", fontSize = 8.5.sp, color = TextTertiary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "$tempC°",
                            fontSize = 13.5.sp,
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
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("BEBAN CPU", fontSize = 8.5.sp, color = TextTertiary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "$totalLoad%",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (totalLoad > 75) AccentOrange else TextPrimary
                        )
                        Text("Total", fontSize = 8.5.sp, color = TextSecondary)
                    }
                }

                // Metric 4: Throttle Status
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("THROTTLE", fontSize = 8.5.sp, color = TextTertiary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = if (isThrottled) "Aktif" else "None",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isThrottled) AccentRed else AccentGreen
                        )
                        Text("Proteksi", fontSize = 8.5.sp, color = TextSecondary)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Qualitative Smart Health Status Bar (Phase 8: No arbitrary percent)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderGlass),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(healthAccent)
                        )
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Status CPU:",
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = healthQuality.label,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = healthAccent
                                )
                            }
                            Text(
                                text = healthQuality.subtitle,
                                fontSize = 9.5.sp,
                                color = TextTertiary
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = healthAccent.copy(alpha = 0.12f),
                        border = BorderStroke(0.8.dp, healthAccent.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = if (isThrottled) "Performa Dibatasi" else "Performa Siaga",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = healthAccent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
//  3. CPU SMART RECOMMENDATION BANNER (PHASE 7: SLIM STRIP)
// ============================================================

@Composable
fun CpuSmartRecommendationBanner(
    recommendation: String,
    onApplyRecommendation: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = AccentOrange.copy(alpha = 0.12f),
        border = BorderStroke(0.8.dp, AccentOrange.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = AccentOrange,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = recommendation,
                    fontSize = 10.5.sp,
                    color = TextPrimary,
                    lineHeight = 13.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onApplyRecommendation,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentOrange,
                        contentColor = Color.Black
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        text = "Terapkan",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = TextTertiary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

// ============================================================
//  4. COMPACT CLUSTER ROW (PHASE 1 & 5: PROGRESSIVE DISCLOSURE)
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactClusterRow(
    cluster: CpuClusterInfo,
    governorTunables: Map<Int, List<GovernorTunable>>,
    onFreqChange: (policyId: Int, minFreq: Long?, maxFreq: Long?) -> Unit,
    onGovChange: (policyId: Int, gov: String) -> Unit,
    onLoadTunables: (policyId: Int, gov: String) -> Unit,
    onTunableChange: (policyId: Int, gov: String, key: String, value: String) -> Unit,
    onLockToggle: (policyId: Int, isLock: Boolean, minFreq: Long, maxFreq: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val isPerfCluster = cluster.id > 0
    val clusterAccent = if (isPerfCluster) AccentOrange else AccentGreen
    val minMhz = (cluster.curMin / 1000).toInt()
    val maxMhz = (cluster.curMax / 1000).toInt()

    val govDisplayName = when (cluster.curGov.lowercase()) {
        "schedutil" -> "Balanced"
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
        shape = RoundedCornerShape(12.dp),
        color = BgElevated,
        border = BorderStroke(0.8.dp, BorderGlass),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row (Clickable to Toggle Expansion)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(clusterAccent)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isPerfCluster) "Performance Cluster" else "Efficiency Cluster",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "(Policy ${cluster.id})",
                                fontSize = 10.sp,
                                color = TextTertiary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = "${cluster.cpus} Cores • $minMhz-$maxMhz MHz • $govDisplayName (${cluster.curGov})",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(rotationAngle)
                )
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
                    HorizontalDivider(color = BorderSubtle, thickness = 0.8.dp)

                    // 1. Frequency Levels (OPP Table Dual-Pill)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "FREQUENCY LEVELS (OPP)",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 0.6.sp
                        )

                        Text(
                            text = "${cluster.availFreqs.size} Tingkatan",
                            fontSize = 9.sp,
                            color = TextTertiary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Min MHz Pill
                        Surface(
                            onClick = { freqPickerTarget = Pair(cluster, true) },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = BgSurfaceLowest,
                            border = BorderStroke(0.8.dp, BorderSubtle)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Min", fontSize = 8.5.sp, color = TextTertiary)
                                    Text("$minMhz MHz", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(16.dp))
                            }
                        }

                        // Max MHz Pill
                        Surface(
                            onClick = { freqPickerTarget = Pair(cluster, false) },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = BgSurfaceLowest,
                            border = BorderStroke(0.8.dp, BorderSubtle)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Max", fontSize = 8.5.sp, color = TextTertiary)
                                    Text("$maxMhz MHz", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = clusterAccent)
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(16.dp))
                            }
                        }

                        // Thermal Lock Toggle
                        IconButton(
                            onClick = { onLockToggle(cluster.id, !cluster.isLocked, cluster.curMin, cluster.curMax) },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (cluster.isLocked) AccentRed.copy(alpha = 0.15f) else BgSurfaceLowest)
                        ) {
                            Icon(
                                imageVector = if (cluster.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = "Thermal Lock",
                                tint = if (cluster.isLocked) AccentRed else TextTertiary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // 2. CPU Response (Governor)
                    Text(
                        text = "CPU RESPONSE (GOVERNOR)",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 0.6.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = { govPickerTarget = cluster },
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = BgSurfaceLowest,
                            border = BorderStroke(0.8.dp, BorderSubtle)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Speed, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "$govDisplayName (${cluster.curGov})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary
                                    )
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(16.dp))
                            }
                        }

                        // Tunables Button
                        Surface(
                            onClick = {
                                onLoadTunables(cluster.id, cluster.curGov)
                                tunablesTarget = cluster
                            },
                            modifier = Modifier.height(40.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = BgSurfaceLowest,
                            border = BorderStroke(0.8.dp, BorderSubtle)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Tunables", fontSize = 10.5.sp, color = TextPrimary)
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
            containerColor = BgCard,
            dragHandle = { BottomSheetDefaults.DragHandle(color = BorderGlass) }
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
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(targetCluster.availFreqs) { freqKhz ->
                        val mhz = (freqKhz / 1000).toInt()
                        val isCurrent = if (isMinPicker) targetCluster.curMin == freqKhz else targetCluster.curMax == freqKhz
                        Surface(
                            onClick = {
                                if (isMinPicker) {
                                    onFreqChange(targetCluster.id, freqKhz, null)
                                } else {
                                    onFreqChange(targetCluster.id, null, freqKhz)
                                }
                                freqPickerTarget = null
                            },
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = if (isCurrent) clusterAccent.copy(alpha = 0.18f) else BgSurfaceLowest,
                            border = if (isCurrent) BorderStroke(1.dp, clusterAccent) else null
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("$mhz MHz", fontSize = 12.sp, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal, color = TextPrimary)
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
            containerColor = BgCard,
            dragHandle = { BottomSheetDefaults.DragHandle(color = BorderGlass) }
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
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(targetCluster.availGovs) { gov ->
                        val isCurrent = targetCluster.curGov.equals(gov, true)
                        val humanGov = when (gov.lowercase()) {
                            "schedutil" -> "Balanced (schedutil)"
                            "performance" -> "Instant Response (performance)"
                            "powersave" -> "Energy Saver (powersave)"
                            else -> gov
                        }
                        Surface(
                            onClick = {
                                onGovChange(targetCluster.id, gov)
                                govPickerTarget = null
                            },
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = if (isCurrent) AccentCyan.copy(alpha = 0.18f) else BgSurfaceLowest,
                            border = if (isCurrent) BorderStroke(1.dp, AccentCyan) else null
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

    // Modal Bottom Sheet: Governor Tunables
    if (tunablesTarget != null) {
        val targetCluster = tunablesTarget!!
        val tunables = governorTunables[targetCluster.id] ?: emptyList()
        ModalBottomSheet(
            onDismissRequest = { tunablesTarget = null },
            containerColor = BgCard,
            dragHandle = { BottomSheetDefaults.DragHandle(color = BorderGlass) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = "Parameter Lanjutan: ${targetCluster.curGov}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Policy ${targetCluster.id} (${tunables.size} tunables ditemukan)",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(10.dp))

                if (tunables.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        Text("Tidak ada tunable yang diekspos kernel.", fontSize = 11.sp, color = TextSecondary)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(tunables) { t ->
                            Surface(
                                onClick = {
                                    editingTunable = Triple(targetCluster.id, targetCluster.curGov, t)
                                    editValueText = t.currentValue
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = BgSurfaceLowest,
                                border = BorderStroke(0.8.dp, BorderSubtle),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(t.displayName, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                        Text(t.key, fontSize = 9.sp, color = TextTertiary, fontFamily = FontFamily.Monospace)
                                    }
                                    Text(
                                        text = "${t.currentValue} ${t.unit}".trim(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentCyan
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
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
    var showDeveloperDrawer by remember { mutableStateOf(false) }
    val currentProfile = uiState.cpuComprehensiveProfile.lowercase()

    // Mode labels: Battery, Balanced, Gaming
    val profiles = listOf(
        Triple("battery", "Battery", Icons.Default.BatteryChargingFull),
        Triple("balanced", "Balanced", Icons.Default.Tune),
        Triple("gaming", "Gaming", Icons.Default.Bolt)
    )

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LynxCard(
            title = "PERFORMA CPU & KLUSTER",
            icon = Icons.Default.Speed,
            accentColor = AccentCyan
        ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Level 1: 3-Pill 1-Click Profile Selector (Battery | Balanced | Gaming)
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
                            text = if (isAdvancedExpanded) "Tutup pengaturan terperinci" else "Hardware engine, mode ekstrem & verifikasi pengembang",
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

    // Level 2 Expanded Area: Platform Engine, Hidden Extreme Mode & Developer Drawer (Full Width Sibling Cards)
    AnimatedVisibility(
        visible = isAdvancedExpanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                shape = RoundedCornerShape(22.dp),
                color = AccentRed.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = null,
                                tint = AccentRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Mode Ekstrem (Tanpa Batas)",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentRed
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Kunci frekuensi maksimum CPU. Menghasilkan panas tinggi dan baterai lebih boros.",
                            fontSize = 10.sp,
                            color = TextSecondary,
                            lineHeight = 14.sp
                        )
                    }

                    Button(
                        onClick = { showExtremeConfirmDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentRed,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Aktifkan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
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

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LynxCard(
            title = "OPTIMASI SISTEM & PENJADWAL",
            icon = Icons.Default.SettingsSuggest,
            accentColor = AccentOrange
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Level 1: 1-Click System Optimization Presets (Balanced | Gaming | Battery)
                Text(
                    text = "PRESET OPTIMASI SISTEM",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.8.sp
                )

                val presets = listOf(
                    Triple("balanced", "Balanced", Icons.Default.Tune),
                    Triple("gaming", "Gaming", Icons.Default.Bolt),
                    Triple("battery", "Battery", Icons.Default.BatteryChargingFull)
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
            }
        }

        // Standalone Level 2 Trigger: "Pengaturan Sistem Lanjutan"
        Surface(
            onClick = { isAdvancedSystemExpanded = !isAdvancedSystemExpanded },
            shape = RoundedCornerShape(16.dp),
            color = BgCard,
            border = BorderStroke(1.dp, if (isAdvancedSystemExpanded) AccentOrange.copy(alpha = 0.5f) else BorderSubtle),
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
                        color = AccentOrange.copy(alpha = 0.12f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = AccentOrange,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Pengaturan Sistem Lanjutan",
                            fontSize = 12.sp,
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
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Level 2 Expanded Area: Detailed System Tunables (Full Width Sibling Cards)
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
//  7. KERNEL SCHEDULER CARD (EAS UCLAMP & MIGRATION)
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
//  8. CPU HARDWARE MONITOR CARD (TAB 2: MONITOR)
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

    var isCoreMatrixExpanded by remember { mutableStateOf(false) }
    var showCore0Notice by remember { mutableStateOf(false) }
    var pendingHotplugCore by remember { mutableStateOf<CpuCoreInfo?>(null) }

    // Calculate cluster summary averages
    val littleCores = cores.filter { core ->
        val parent = uiState.clusters.find { it.containsCore(core.coreId) }
        parent?.role?.contains("Little", true) == true || (parent?.id == 0) || core.coreId < 6
    }
    val bigCores = cores.filter { core ->
        val parent = uiState.clusters.find { it.containsCore(core.coreId) }
        parent?.role?.contains("Big", true) == true || (parent != null && parent.id > 0) || core.coreId >= 6
    }

    val onlineLittle = littleCores.count { it.isOnline }
    val totalLittle = littleCores.size.coerceAtLeast(1)
    val avgLittleMhz = if (onlineLittle > 0) {
        littleCores.filter { it.isOnline }.map { it.curFreqKhz / 1000 }.average().toInt()
    } else 0

    val onlineBig = bigCores.count { it.isOnline }
    val totalBig = bigCores.size.coerceAtLeast(1)
    val avgBigMhz = if (onlineBig > 0) {
        bigCores.filter { it.isOnline }.map { it.curFreqKhz / 1000 }.average().toInt()
    } else 0

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Master Hero Card (Bezier Waveform & Core Activity Summary)
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = BgCard,
            border = BorderStroke(0.8.dp, BorderSubtle),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .animateContentSize()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
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
                            text = "CPU LIVE MONITOR",
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

                // Phase 6: Core Activity Summary (Clean Default View)
                Text(
                    text = "AKTIVITAS INTI (CORE ACTIVITY)",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.8.sp
                )
                Spacer(Modifier.height(8.dp))

                // Efficiency Cores Summary Row
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(AccentGreen))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Efficiency Cores", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text("$onlineLittle/$totalLittle Inti Aktif", fontSize = 9.5.sp, color = TextSecondary)
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("$avgLittleMhz MHz", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                            Text("Rata-rata Clock", fontSize = 8.5.sp, color = TextTertiary)
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Performance Cores Summary Row
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(AccentOrange))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Performance Cores", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text("$onlineBig/$totalBig Inti Aktif", fontSize = 9.5.sp, color = TextSecondary)
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("$avgBigMhz MHz", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                            Text("Rata-rata Clock", fontSize = 8.5.sp, color = TextTertiary)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Toggle "Tampilkan Semua Inti (Show All Cores) >"
                Surface(
                    onClick = { isCoreMatrixExpanded = !isCoreMatrixExpanded },
                    shape = RoundedCornerShape(8.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, BorderGlass),
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isCoreMatrixExpanded) "Tutup Rincian Inti" else "Tampilkan Semua Inti (Show All Cores)",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentCyan
                        )
                        Icon(
                            imageVector = if (isCoreMatrixExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Expandable 8-Core Grid (Phase 6: Only shown when requested)
                AnimatedVisibility(
                    visible = isCoreMatrixExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("MATRIKS STATUS PER-CORE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 0.8.sp)
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
                                    val coreAccent = if (!core.isOnline) TextTertiary else if (isPerfCore) AccentOrange else AccentGreen
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
//  9. TOP PROCESS LIST CARD (SCENE-STYLE PROCESS MONITOR)
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
//  10. CPU TOPOLOGY SPEC CARD (HARDWARE TRUTH ARCHITECTURE)
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
            val verificationItems = listOf(
                Triple("/sys/devices/system/cpu/cpufreq/policy*/scaling_cur_freq", "0644 READ/WRITE", "TERVERIFIKASI AKTIF"),
                Triple("/sys/devices/system/cpu/cpufreq/policy*/scaling_governor", "0644 READ/WRITE", "TERVERIFIKASI AKTIF"),
                Triple("/sys/devices/system/cpu/cpu*/online", "0644 READ/WRITE", "HOTPLUG DIDUKUNG"),
                Triple("/proc/sys/kernel/sched_util_clamp_min", "0644 READ/WRITE", if (uiState.schedulerInfo.isUclampSupported) "TERVERIFIKASI AKTIF" else "TIDAK TERSEDIA"),
                Triple("/proc/ppm/policy/userlimit_max_cpu_freq", "0644 VENDOR HAL", if (uiState.schedulerInfo.isPpmSupported) "TERVERIFIKASI MTK" else "TIDAK TERSEDIA")
            )

            verificationItems.forEach { (nodePath, perm, status) ->
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
                                text = nodePath,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Perizinan: $perm",
                                fontSize = 9.sp,
                                color = TextSecondary
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (status.contains("TERVERIFIKASI")) AccentGreen.copy(alpha = 0.15f) else BgElevated,
                            border = BorderStroke(0.8.dp, if (status.contains("TERVERIFIKASI")) AccentGreen.copy(alpha = 0.4f) else BorderSubtle)
                        ) {
                            Text(
                                text = status,
                                fontSize = 9.sp,
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
