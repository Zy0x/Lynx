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
                    .clickable { isExpanded = !isExpanded },
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

                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = TextSecondary.copy(alpha = 0.6f),
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
                    HorizontalDivider(color = Color(0x15FFFFFF), thickness = 0.8.dp)

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
                            color = TextSecondary.copy(alpha = 0.75f),
                            letterSpacing = 0.6.sp
                        )

                        Text(
                            text = "${cluster.availFreqs.size} Tingkatan",
                            fontSize = 9.sp,
                            color = TextSecondary.copy(alpha = 0.5f)
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
                            shape = RoundedCornerShape(9.dp),
                            color = Color(0xFF0C0F17),
                            border = BorderStroke(0.6.dp, Color(0x15FFFFFF))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Min", fontSize = 8.5.sp, color = TextSecondary.copy(alpha = 0.5f))
                                    Text("$minMhz MHz", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = TextPrimary)
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSecondary.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                            }
                        }

                        // Max MHz Pill
                        Surface(
                            onClick = { freqPickerTarget = Pair(cluster, false) },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(9.dp),
                            color = Color(0xFF0C0F17),
                            border = BorderStroke(0.6.dp, Color(0x15FFFFFF))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Max", fontSize = 8.5.sp, color = TextSecondary.copy(alpha = 0.5f))
                                    Text("$maxMhz MHz", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = clusterAccent)
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSecondary.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                            }
                        }

                        // Thermal Lock Toggle
                        IconButton(
                            onClick = { onLockToggle(cluster.id, !cluster.isLocked, cluster.curMin, cluster.curMax) },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (cluster.isLocked) AccentRed.copy(alpha = 0.15f) else Color(0xFF0C0F17))
                                .border(0.6.dp, if (cluster.isLocked) AccentRed.copy(alpha = 0.4f) else Color(0x15FFFFFF), RoundedCornerShape(9.dp))
                        ) {
                            Icon(
                                imageVector = if (cluster.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = localizedUiText("Kunci Frekuensi"),
                                tint = if (cluster.isLocked) AccentRed else TextSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // 2. CPU Response (Governor)
                    Text(
                        text = "CPU RESPONSE (GOVERNOR)",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary.copy(alpha = 0.75f),
                        letterSpacing = 0.6.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = { govPickerTarget = cluster },
                            modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(9.dp),
                            color = Color(0xFF0C0F17),
                            border = BorderStroke(0.6.dp, Color(0x15FFFFFF))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Speed, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "$govDisplayName (${cluster.curGov})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary
                                    )
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSecondary.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                            }
                        }

                        // Tunables Button
                        Surface(
                            onClick = {
                                onLoadTunables(cluster.id, cluster.curGov)
                                tunablesTarget = cluster
                            },
                            modifier = Modifier.height(42.dp),
                            shape = RoundedCornerShape(9.dp),
                            color = Color(0xFF0C0F17),
                            border = BorderStroke(0.6.dp, Color(0x15FFFFFF))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = TextSecondary.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Tunables", fontSize = 10.5.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
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
                                if (isMinPicker) {
                                    onFreqChange(targetCluster.id, freqKhz, null)
                                } else {
                                    onFreqChange(targetCluster.id, null, freqKhz)
                                }
                                freqPickerTarget = null
                            },
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            shape = RoundedCornerShape(9.dp),
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
                                onGovChange(targetCluster.id, gov)
                                govPickerTarget = null
                            },
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            shape = RoundedCornerShape(9.dp),
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

    // Modal Bottom Sheet: Governor Tunables
    if (tunablesTarget != null) {
        val targetCluster = tunablesTarget!!
        val tunables = governorTunables[targetCluster.id] ?: emptyList()
        ModalBottomSheet(
            onDismissRequest = { tunablesTarget = null },
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

