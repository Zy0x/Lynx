package com.noir.lynx.feature.memory.ui
import com.noir.lynx.ui.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── Memory & Swappiness Cache Card ──────────────────────
        LynxCard(
            title = "Memory & Swappiness Cache",
            icon = Icons.Default.Storage,
            accentColor = AccentBlue
        ) {
            // Dynamic ZRAM Compression Algorithm Selection
            Text(
                "Algoritma Kompresi ZRAM (Kernel Swap)",
                color = TextPrimary, fontSize = 13.sp,
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
                        border = BorderStroke(1.2.dp, if (isSelected) AccentBlue else BorderGlass)
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
                                color = if (isSelected) AccentBlue.copy(alpha = 0.8f) else TextSecondary,
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
                label = "Ukuran Alokasi ZRAM (Skala RAM Fisik: ${ramTotal.toInt()} MB)",
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
                color = TextPrimary, fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
            )
            val vmPresets = listOf(
                Triple("gaming", "Gaming", "Zero-stutter I/O"),
                Triple("balanced", "Balanced", "OEM Default"),
                Triple("battery", "Battery", "Low Writeback")
            )
            val activeVmPreset = uiState.vmAdvanced.activePreset
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                vmPresets.forEach { (presetKey, title, subtitle) ->
                    val isSelected = activeVmPreset == presetKey
                    Surface(
                        onClick = { viewModel.applyVmPreset(presetKey) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) AccentBlue.copy(alpha = 0.22f) else BgElevated,
                        border = BorderStroke(1.2.dp, if (isSelected) AccentBlue else BorderGlass)
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
                                color = if (isSelected) AccentBlue.copy(alpha = 0.8f) else TextSecondary,
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            var swapValue by remember { mutableFloatStateOf(uiState.vmAdvanced.swappiness.toFloat()) }
            var dirtyValue by remember { mutableFloatStateOf(uiState.vmAdvanced.dirtyRatio.toFloat()) }
            var dirtyBgValue by remember { mutableFloatStateOf(uiState.vmAdvanced.dirtyBackgroundRatio.toFloat()) }
            var vfsValue by remember { mutableFloatStateOf(uiState.vmAdvanced.vfsCachePressure.toFloat()) }
            var expireValue by remember { mutableFloatStateOf((uiState.vmAdvanced.dirtyExpireCentisecs / 100).toFloat()) }
            var writebackValue by remember { mutableFloatStateOf((uiState.vmAdvanced.dirtyWritebackCentisecs / 100).toFloat()) }
            var statValue by remember { mutableFloatStateOf(uiState.vmAdvanced.statInterval.toFloat()) }

            var isInteractingVm by remember { mutableStateOf(false) }
            var lastVmInteraction by remember { mutableLongStateOf(0L) }

            LaunchedEffect(uiState.vmAdvanced) {
                if (!isInteractingVm && (System.currentTimeMillis() - lastVmInteraction > 3500L)) {
                    swapValue = uiState.vmAdvanced.swappiness.toFloat()
                    dirtyValue = uiState.vmAdvanced.dirtyRatio.toFloat()
                    dirtyBgValue = uiState.vmAdvanced.dirtyBackgroundRatio.toFloat()
                    vfsValue = uiState.vmAdvanced.vfsCachePressure.toFloat()
                    expireValue = (uiState.vmAdvanced.dirtyExpireCentisecs / 100).toFloat()
                    writebackValue = (uiState.vmAdvanced.dirtyWritebackCentisecs / 100).toFloat()
                    statValue = uiState.vmAdvanced.statInterval.toFloat()
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

            LynxSlider(
                label = "VM Dirty Ratio (Batas Writeback)",
                value = dirtyValue,
                onValueChange = {
                    isInteractingVm = true
                    lastVmInteraction = System.currentTimeMillis()
                    dirtyValue = it
                },
                onValueChangeFinished = {
                    isInteractingVm = false
                    lastVmInteraction = System.currentTimeMillis()
                    viewModel.setDirtyRatio(dirtyValue.toInt())
                },
                valueRange = 5f..60f,
                steps = 10,
                displayValue = "${dirtyValue.toInt()}%",
                accentColor = AccentBlue
            )

            LynxSlider(
                label = "VM Dirty Background Ratio",
                value = dirtyBgValue,
                onValueChange = {
                    isInteractingVm = true
                    lastVmInteraction = System.currentTimeMillis()
                    dirtyBgValue = it
                },
                onValueChangeFinished = {
                    isInteractingVm = false
                    lastVmInteraction = System.currentTimeMillis()
                    viewModel.setDirtyBackgroundRatio(dirtyBgValue.toInt())
                },
                valueRange = 1f..30f,
                steps = 28,
                displayValue = "${dirtyBgValue.toInt()}%",
                accentColor = AccentBlue
            )

            LynxSlider(
                label = "VFS Cache Pressure (Reclaim Rate)",
                value = vfsValue,
                onValueChange = {
                    isInteractingVm = true
                    lastVmInteraction = System.currentTimeMillis()
                    vfsValue = it
                },
                onValueChangeFinished = {
                    isInteractingVm = false
                    lastVmInteraction = System.currentTimeMillis()
                    viewModel.setVfsCachePressure(vfsValue.toInt())
                },
                valueRange = 50f..200f,
                steps = 14,
                displayValue = "${vfsValue.toInt()}",
                accentColor = AccentBlue
            )

            LynxSlider(
                label = "VM Dirty Expire Time",
                value = expireValue,
                onValueChange = {
                    isInteractingVm = true
                    lastVmInteraction = System.currentTimeMillis()
                    expireValue = it
                },
                onValueChangeFinished = {
                    isInteractingVm = false
                    lastVmInteraction = System.currentTimeMillis()
                    viewModel.setDirtyExpireCentisecs((expireValue * 100).toInt())
                },
                valueRange = 10f..60f,
                steps = 9,
                displayValue = "${expireValue.toInt()}s",
                accentColor = AccentBlue
            )

            LynxSlider(
                label = "VM Dirty Writeback Interval",
                value = writebackValue,
                onValueChange = {
                    isInteractingVm = true
                    lastVmInteraction = System.currentTimeMillis()
                    writebackValue = it
                },
                onValueChangeFinished = {
                    isInteractingVm = false
                    lastVmInteraction = System.currentTimeMillis()
                    viewModel.setDirtyWritebackCentisecs((writebackValue * 100).toInt())
                },
                valueRange = 1f..15f,
                steps = 13,
                displayValue = "${writebackValue.toInt()}s",
                accentColor = AccentBlue
            )

            LynxSlider(
                label = "VM Stat Interval (Pembaruan Statistik)",
                value = statValue,
                onValueChange = {
                    isInteractingVm = true
                    lastVmInteraction = System.currentTimeMillis()
                    statValue = it
                },
                onValueChangeFinished = {
                    isInteractingVm = false
                    lastVmInteraction = System.currentTimeMillis()
                    viewModel.setVmStatInterval(statValue.toInt())
                },
                valueRange = 1f..10f,
                steps = 8,
                displayValue = "${statValue.toInt()}s",
                accentColor = AccentBlue
            )

            Spacer(Modifier.height(8.dp))
            LynxActionButton(
                text = "Bersihkan Proses Latar Aman",
                icon = Icons.Default.CleaningServices,
                onClick = { viewModel.cleanRam() },
                accentColor = AccentCyan,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // ── KSM (Kernel Same-page Merging) Card ──────────────────
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
                    Text("Gabung halaman memori identik, hemat ${String.format("%.1f", ksm.savedMb)} MB RAM",
                        color = TextSecondary, fontSize = 11.sp)
                }
                Switch(
                    checked = ksm.enabled,
                    onCheckedChange = { viewModel.setKsmEnabled(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = AccentBlue,
                        checkedTrackColor = AccentBlue.copy(alpha = 0.4f))
                )
            }
            if (ksm.enabled) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${ksm.pagesSharing}", color = AccentBlue,
                            fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Halaman Sharing", color = TextSecondary, fontSize = 10.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${ksm.pagesShared}", color = AccentCyan,
                            fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Halaman Shared", color = TextSecondary, fontSize = 10.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(String.format("%.1f MB", ksm.savedMb), color = AccentOrange,
                            fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("RAM Hemat", color = TextSecondary, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                var scanValue by remember { mutableFloatStateOf(ksm.pagesToScan.toFloat()) }
                var isInteractingKsm by remember { mutableStateOf(false) }
                var lastKsmInteraction by remember { mutableLongStateOf(0L) }

                LaunchedEffect(ksm.pagesToScan) {
                    if (!isInteractingKsm && (System.currentTimeMillis() - lastKsmInteraction > 3500L)) {
                        scanValue = ksm.pagesToScan.toFloat()
                    }
                }

                LynxSlider(
                    label = "Pages to Scan per Cycle",
                    value = scanValue,
                    onValueChange = {
                        isInteractingKsm = true
                        lastKsmInteraction = System.currentTimeMillis()
                        scanValue = it
                    },
                    onValueChangeFinished = {
                        isInteractingKsm = false
                        lastKsmInteraction = System.currentTimeMillis()
                        viewModel.setKsmTunables(scanValue.toInt(), ksm.sleepMs)
                    },
                    valueRange = 50f..500f,
                    steps = 8,
                    displayValue = "${scanValue.toInt()} pages",
                    accentColor = AccentBlue
                )
            }
        }

        // ── LMK Minfree Preset Card ──────────────────────────────
        LynxCard(
            title = "LMK — Low Memory Killer",
            icon = Icons.Default.DeleteSweep,
            accentColor = AccentOrange
        ) {
            Text(
                "Atur seberapa agresif sistem Android mengakhiri aplikasi latar belakang untuk membebaskan RAM.",
                color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp,
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
                    shape = RoundedCornerShape(12.dp),
                    color = BgElevated,
                    border = BorderStroke(1.dp, BorderGlass)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(label, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(desc, color = TextSecondary, fontSize = 11.sp)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Text(
                "Catatan: LMK Kernel hanya efektif pada Android 10 ke bawah. Android 11+ menggunakan LMKD userspace.",
                color = TextSecondary.copy(alpha = 0.7f), fontSize = 10.sp, lineHeight = 14.sp
            )
        }

        // ── Memory LMK & Entropy Tuner Card ──────────────────────
        MemoryEntropyCard(
            memoryEntropy = uiState.memoryEntropy,
            onSetEntropy = { r, w ->
                viewModel.setEntropyThresholds(r, w)
            }
        )

        // ── I/O Scheduler Card ───────────────────────────────────
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
                        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
                    )
                    Text("Scheduler", color = TextSecondary, fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 4.dp))
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
                                    1.dp, if (isActive) AccentPurple else BorderGlass
                                )
                            ) {
                                Text(sched,
                                    color = if (isActive) AccentPurple else TextSecondary,
                                    fontSize = 11.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    var raValue by remember(dev.device) { mutableFloatStateOf(dev.readAheadKb.toFloat()) }
                    var isInteractingRa by remember(dev.device) { mutableStateOf(false) }
                    var lastRaInteraction by remember(dev.device) { mutableLongStateOf(0L) }

                    LaunchedEffect(dev.device, dev.readAheadKb) {
                        if (!isInteractingRa && (System.currentTimeMillis() - lastRaInteraction > 3500L)) {
                            raValue = dev.readAheadKb.toFloat()
                        }
                    }

                    LynxSlider(
                        label = "Read-Ahead Buffer",
                        value = raValue,
                        onValueChange = {
                            isInteractingRa = true
                            lastRaInteraction = System.currentTimeMillis()
                            raValue = it
                        },
                        onValueChangeFinished = {
                            isInteractingRa = false
                            lastRaInteraction = System.currentTimeMillis()
                            viewModel.setReadAheadKb(dev.device, raValue.toInt())
                        },
                        valueRange = 128f..4096f,
                        steps = 7,
                        displayValue = "${raValue.toInt()} KB",
                        accentColor = AccentPurple
                    )
                    HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(top = 8.dp))
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
