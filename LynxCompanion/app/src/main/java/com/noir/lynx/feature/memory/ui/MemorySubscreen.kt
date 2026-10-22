package com.noir.lynx.feature.memory.ui
import com.noir.lynx.ui.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.res.stringResource
import com.noir.lynx.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.controls.LynxActionButton
import com.noir.lynx.designsystem.controls.LynxSlider
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.ui.LynxViewModel

/**
 * Subscreen 5: Memory & Storage I/O Composable
 * Redesigned for Zen calm, anti AI slop, progressive disclosure, and zero slider fatigue.
 */
@Composable
fun MemorySubscreen(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val state = uiState.state

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── 1. Hero Card: Zen Memory & Swap ─────────────────────
        MemoryHeroCard(uiState, viewModel)

        // ── 2. Memory & Swappiness Cache Card ───────────────────
        LynxCard(
            title = "Alokasi Virtual Memory & ZRAM",
            icon = Icons.Default.Storage,
            accentColor = AccentBlue
        ) {
            // Dynamic ZRAM Compression Algorithm Selection
            Text(
                "Algoritma Kompresi ZRAM (Kernel Swap)",
                color = TextPrimary, fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp)
            )
            val algos = uiState.availZramCompAlgorithms.ifEmpty { listOf("lz4", "lzo", "zstd") }
            val activeAlgo = uiState.zramCompAlgorithm.ifBlank { "lz4" }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                algos.forEach { algo ->
                    val isSelected = algo == activeAlgo
                    Surface(
                        onClick = { viewModel.setZramCompAlgorithm(algo) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) AccentBlue.copy(alpha = 0.22f) else BgElevated,
                        border = BorderStroke(if (isSelected) 1.2.dp else 0.8.dp, if (isSelected) AccentBlue else BorderSubtle)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Text(
                                algo.uppercase(),
                                color = if (isSelected) AccentBlue else TextPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            Text(
                                when (algo) {
                                    "zstd" -> "Rasio Max"
                                    "lz4" -> "Cepat"
                                    "lz4hc" -> "Optimal"
                                    "lzo-rle" -> "Hemat"
                                    else -> "Kompresi"
                                },
                                color = if (isSelected) AccentBlue.copy(alpha = 0.85f) else TextSecondary,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }

            val ramTotal = (uiState.telemetry?.ramTotalMb?.takeIf { it > 0 } ?: 4096).toFloat()
            val minZram = 512f
            val maxZram = maxOf(4096f, ramTotal)
            val zramSteps = (((maxZram - minZram) / 512f).toInt() - 1).coerceAtLeast(0)

            var zramValue by remember {
                mutableFloatStateOf(state.memory.zramSizeMb.toFloat().coerceIn(minZram, maxZram))
            }
            var isInteractingZram by remember { mutableStateOf(false) }
            var lastZramInteraction by remember { mutableLongStateOf(0L) }

            LaunchedEffect(state.memory.zramSizeMb) {
                if (!isInteractingZram && (System.currentTimeMillis() - lastZramInteraction > 3500L)) {
                    zramValue = state.memory.zramSizeMb.toFloat().coerceIn(minZram, maxZram)
                }
            }

            LynxSlider(
                label = "Ukuran Alokasi ZRAM (RAM Fisik: ${ramTotal.toInt()} MB)",
                value = zramValue,
                onValueChange = {
                    isInteractingZram = true
                    lastZramInteraction = System.currentTimeMillis()
                    zramValue = it
                },
                onValueChangeFinished = {
                    isInteractingZram = false
                    lastZramInteraction = System.currentTimeMillis()
                    viewModel.setZramSizeMb(zramValue.toInt())
                },
                valueRange = minZram..maxZram,
                steps = zramSteps,
                displayValue = "${zramValue.toInt()} MB",
                accentColor = AccentBlue
            )

            // ── VM Tuning Presets ─────────────────────────────
            Text(
                "Preset Rekomendasi Virtual Memory",
                color = TextPrimary, fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
            )
            val vmPresets = listOf(
                Triple("gaming", "Gaming", "Zero-stutter I/O"),
                Triple("balanced", "Balanced", "Standar Harian"),
                Triple("battery", "Battery", "Low Writeback")
            )
            val activeVmPreset = uiState.vmAdvanced.activePreset
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                vmPresets.forEach { (presetKey, title, subtitle) ->
                    val isSelected = activeVmPreset == presetKey
                    Surface(
                        onClick = { viewModel.applyVmPreset(presetKey) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) AccentBlue.copy(alpha = 0.22f) else BgElevated,
                        border = BorderStroke(if (isSelected) 1.2.dp else 0.8.dp, if (isSelected) AccentBlue else BorderSubtle)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            Text(
                                title,
                                color = if (isSelected) AccentBlue else TextPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            Text(
                                subtitle,
                                color = if (isSelected) AccentBlue.copy(alpha = 0.85f) else TextSecondary,
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            var swapValue by remember { mutableFloatStateOf(uiState.vmAdvanced.swappiness.toFloat()) }
            var isInteractingVm by remember { mutableStateOf(false) }
            var lastVmInteraction by remember { mutableLongStateOf(0L) }

            LaunchedEffect(uiState.vmAdvanced.swappiness) {
                if (!isInteractingVm && (System.currentTimeMillis() - lastVmInteraction > 3500L)) {
                    swapValue = uiState.vmAdvanced.swappiness.toFloat()
                }
            }

            LynxSlider(
                label = "Virtual Memory Swappiness",
                value = swapValue,
                onValueChange = {
                    isInteractingVm = true
                    lastVmInteraction = System.currentTimeMillis()
                    swapValue = it
                },
                onValueChangeFinished = {
                    isInteractingVm = false
                    lastVmInteraction = System.currentTimeMillis()
                    viewModel.setSwappiness(swapValue.toInt())
                },
                valueRange = 0f..200f,
                steps = 19,
                displayValue = "${swapValue.toInt()}",
                accentColor = AccentBlue
            )

            // ── Progressive Disclosure: Parameter Lanjutan VM ─
            var showAdvancedVm by remember { mutableStateOf(false) }
            Spacer(Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(0.7.dp, BorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAdvancedVm = !showAdvancedVm }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text(
                                text = "Konfigurasi Virtual Memory Lanjutan",
                                color = TextPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (showAdvancedVm) "Sembunyikan parameter writeback & VFS" else "Dirty Ratio, VFS Cache Pressure, Writeback Interval",
                                color = TextSecondary,
                                fontSize = 9.5.sp
                            )
                        }
                    }
                    Icon(
                        imageVector = if (showAdvancedVm) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(visible = showAdvancedVm) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    var dirtyValue by remember { mutableFloatStateOf(uiState.vmAdvanced.dirtyRatio.toFloat()) }
                    var dirtyBgValue by remember { mutableFloatStateOf(uiState.vmAdvanced.dirtyBackgroundRatio.toFloat()) }
                    var vfsValue by remember { mutableFloatStateOf(uiState.vmAdvanced.vfsCachePressure.toFloat()) }
                    var expireValue by remember { mutableFloatStateOf((uiState.vmAdvanced.dirtyExpireCentisecs / 100).toFloat()) }
                    var writebackValue by remember { mutableFloatStateOf((uiState.vmAdvanced.dirtyWritebackCentisecs / 100).toFloat()) }

                    LynxSlider(
                        label = "VM Dirty Ratio (Batas Writeback)",
                        value = dirtyValue,
                        onValueChange = { dirtyValue = it },
                        onValueChangeFinished = { viewModel.setDirtyRatio(dirtyValue.toInt()) },
                        valueRange = 5f..60f,
                        steps = 10,
                        displayValue = "${dirtyValue.toInt()}%",
                        accentColor = AccentBlue
                    )

                    LynxSlider(
                        label = "VM Dirty Background Ratio",
                        value = dirtyBgValue,
                        onValueChange = { dirtyBgValue = it },
                        onValueChangeFinished = { viewModel.setDirtyBackgroundRatio(dirtyBgValue.toInt()) },
                        valueRange = 1f..30f,
                        steps = 28,
                        displayValue = "${dirtyBgValue.toInt()}%",
                        accentColor = AccentBlue
                    )

                    LynxSlider(
                        label = "VFS Cache Pressure (Reclaim Rate)",
                        value = vfsValue,
                        onValueChange = { vfsValue = it },
                        onValueChangeFinished = { viewModel.setVfsCachePressure(vfsValue.toInt()) },
                        valueRange = 50f..200f,
                        steps = 14,
                        displayValue = "${vfsValue.toInt()}",
                        accentColor = AccentBlue
                    )

                    LynxSlider(
                        label = "VM Dirty Expire Time",
                        value = expireValue,
                        onValueChange = { expireValue = it },
                        onValueChangeFinished = { viewModel.setDirtyExpireCentisecs((expireValue * 100).toInt()) },
                        valueRange = 10f..60f,
                        steps = 9,
                        displayValue = "${expireValue.toInt()}s",
                        accentColor = AccentBlue
                    )

                    LynxSlider(
                        label = "VM Dirty Writeback Interval",
                        value = writebackValue,
                        onValueChange = { writebackValue = it },
                        onValueChangeFinished = { viewModel.setDirtyWritebackCentisecs((writebackValue * 100).toInt()) },
                        valueRange = 1f..15f,
                        steps = 13,
                        displayValue = "${writebackValue.toInt()}s",
                        accentColor = AccentBlue
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            LynxActionButton(
                text = "Bersihkan Proses Latar & Buffer RAM",
                icon = Icons.Default.CleaningServices,
                onClick = { viewModel.cleanRam() },
                accentColor = AccentCyan,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // ── 3. KSM (Kernel Same-page Merging) Card ───────────────
        LaunchedEffect(Unit) { viewModel.refreshKsmStats() }
        val ksm = uiState.ksmStats
        LynxCard(
            title = "KSM — Kernel Memory Merging",
            icon = Icons.Default.Memory,
            accentColor = AccentBlue
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Kernel Same-page Merging", color = TextPrimary,
                        fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(stringResource(R.string.ksm_saved_memory, String.format("%.1f", ksm.savedMb)),
                        color = TextSecondary, fontSize = 11.sp)
                }
                Switch(
                    checked = ksm.enabled,
                    onCheckedChange = { viewModel.setKsmEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AccentBlue,
                        checkedTrackColor = AccentBlue.copy(alpha = 0.4f)
                    )
                )
            }
            if (ksm.enabled) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${ksm.pagesSharing}", color = AccentBlue,
                            fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Sharing", color = TextSecondary, fontSize = 10.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${ksm.pagesShared}", color = AccentCyan,
                            fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Shared", color = TextSecondary, fontSize = 10.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(String.format("%.1f MB", ksm.savedMb), color = AccentOrange,
                            fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("RAM Hemat", color = TextSecondary, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                var scanValue by remember { mutableFloatStateOf(ksm.pagesToScan.toFloat()) }
                LynxSlider(
                    label = "Pages to Scan per Cycle",
                    value = scanValue,
                    onValueChange = { scanValue = it },
                    onValueChangeFinished = { viewModel.setKsmTunables(scanValue.toInt(), ksm.sleepMs) },
                    valueRange = 50f..500f,
                    steps = 8,
                    displayValue = "${scanValue.toInt()} pages",
                    accentColor = AccentBlue
                )
            }
        }

        // ── 4. LMK Minfree Preset Card ───────────────────────────
        LynxCard(
            title = "LMK — Low Memory Killer",
            icon = Icons.Default.DeleteSweep,
            accentColor = AccentOrange
        ) {
            Text(
                "Atur seberapa agresif sistem Android mengakhiri aplikasi latar belakang untuk membebaskan RAM.",
                color = TextSecondary, fontSize = 11.5.sp, lineHeight = 16.sp,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            val lmkPresets = listOf(
                Triple("conservative", "Conservative", "Simpan lebih banyak app di RAM"),
                Triple("balanced",     "Balanced",     "Standar harian — default Android"),
                Triple("gaming",       "Gaming",       "Prioritaskan game aktif"),
                Triple("aggressive",   "Aggressive",   "Kill agresif, maksimalkan RAM bebas"),
            )
            lmkPresets.forEach { (id, label, desc) ->
                Surface(
                    onClick = { viewModel.applyLmkPreset(id) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, BorderSubtle)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(label, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                            Text(desc, color = TextSecondary, fontSize = 10.5.sp)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // ── 5. Memory LMK & Entropy Tuner Card ───────────────────
        MemoryEntropyCard(
            memoryEntropy = uiState.memoryEntropy,
            onSetEntropy = { r, w ->
                viewModel.setEntropyThresholds(r, w)
            }
        )

        // ── 6. Adaptive SD I/O Policy ───────────────────────────
        MountXStorageCard(uiState = uiState, viewModel = viewModel)

        // ── 7. I/O Scheduler Card ────────────────────────────────
        if (uiState.ioDevices.isNotEmpty()) {
            LynxCard(
                title = "I/O Scheduler Manager",
                icon = Icons.Default.Storage,
                accentColor = AccentPurple
            ) {
                uiState.ioDevices.forEach { dev ->
                    Text(
                        "/dev/${dev.device}",
                        color = AccentPurple, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(dev.availableSchedulers.size) { i ->
                            val sched = dev.availableSchedulers[i]
                            val isActive = sched == dev.currentScheduler
                            Surface(
                                onClick = { viewModel.setIoScheduler(dev.device, sched) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isActive) AccentPurple.copy(alpha = 0.2f) else BgElevated,
                                border = BorderStroke(
                                    0.8.dp, if (isActive) AccentPurple else BorderSubtle
                                )
                            ) {
                                Text(
                                    sched,
                                    color = if (isActive) AccentPurple else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    var raValue by remember(dev.device) { mutableFloatStateOf(dev.readAheadKb.toFloat()) }
                    LynxSlider(
                        label = "Read-Ahead Buffer",
                        value = raValue,
                        onValueChange = { raValue = it },
                        onValueChangeFinished = { viewModel.setReadAheadKb(dev.device, raValue.toInt()) },
                        valueRange = 128f..4096f,
                        steps = 7,
                        displayValue = "${raValue.toInt()} KB",
                        accentColor = AccentPurple
                    )
                    HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

/**
 * Backward compatibility alias for legacy call sites.
 */
@Composable
fun TuningMemoryCategory(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    MemorySubscreen(uiState = uiState, viewModel = viewModel, modifier = modifier)
}
