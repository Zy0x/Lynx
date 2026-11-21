package com.noir.lynx.feature.memory.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.data.IoDeviceInfo
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.card.UnsupportedBadge
import com.noir.lynx.designsystem.controls.LynxActionButton
import com.noir.lynx.designsystem.controls.LynxSwitch
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.ui.LynxViewModel
import com.noir.lynx.ui.setIoQueueTunable
import com.noir.lynx.ui.setIoScheduler
import com.noir.lynx.ui.setReadAheadKb
import com.noir.lynx.ui.triggerMountXBoost

/**
 * MountXStorageCard — Sub-tab 2 (Storage & I/O).
 * Clean direct controls without lock-bar friction:
 * - MountX Flash Shield + 1-tap Boost/Restore
 * - External MicroSD summary if detected
 * - Primary physical block device expanded by default; secondary/virtual nodes collapsible
 */
@Composable
fun MountXStorageCard(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val storage = uiState.mountXStorageInfo
    var showVirtualDevices by remember { mutableStateOf(false) }

    var customRaDevice by remember { mutableStateOf<IoDeviceInfo?>(null) }
    var customNrDevice by remember { mutableStateOf<IoDeviceInfo?>(null) }

    val displayedDevices = remember(uiState.ioDevices, showVirtualDevices) {
        if (showVirtualDevices) {
            uiState.ioDevices
        } else {
            val physical = uiState.ioDevices.filter { isPrimaryOrPhysicalBlock(it) }
            if (physical.isNotEmpty()) physical else uiState.ioDevices.filter {
                !it.device.startsWith("zram") && !it.device.startsWith("loop") && !it.device.startsWith("ram")
            }
        }
    }

    customRaDevice?.let { dev ->
        CustomNumericInputDialog(
            title = "Read-Ahead Kustom (${dev.device})",
            unit = "KB",
            currentValue = dev.readAheadKb.coerceIn(16, 16384),
            min = 16,
            max = 16384,
            hint = buildString {
                append("Tipe: ${dev.deviceType.ifBlank { "Block" }}. ")
                if (dev.targetReadAheadKb != null) {
                    append("Rekomendasi sistem: ${dev.targetReadAheadKb} KB.")
                } else {
                    append("Nilai kecil cocok untuk random I/O, besar untuk baca berurutan.")
                }
            },
            accentColor = AccentCyan,
            onDismiss = { customRaDevice = null },
            onConfirm = { kb ->
                val targetDev = dev.device
                customRaDevice = null
                viewModel.setReadAheadKb(targetDev, kb)
            }
        )
    }

    customNrDevice?.let { dev ->
        CustomNumericInputDialog(
            title = "Queue Depth nr_requests (${dev.device})",
            unit = "req",
            currentValue = (dev.nrRequests ?: 128).coerceIn(8, 2048),
            min = 8,
            max = 2048,
            hint = "Kedalaman antrean permintaan blok (/sys/block/${dev.device}/queue/nr_requests).",
            accentColor = AccentBlue,
            onDismiss = { customNrDevice = null },
            onConfirm = { value ->
                val targetDev = dev.device
                customNrDevice = null
                viewModel.setIoQueueTunable(targetDev, "nr_requests", value)
            }
        )
    }

    LynxCard(
        title = "Storage & I/O Scheduler",
        subtitle = buildString {
            append(storage.primaryStorageType.ifBlank { "Deteksi Otomatis" })
            if (storage.fsType.isNotBlank()) append(" (${storage.fsType.uppercase()})")
            append(" • ${displayedDevices.size} perangkat")
        },
        icon = Icons.Default.Storage,
        accentColor = AccentCyan,
        action = {
            if (storage.isDetected) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (storage.isOptimized) AccentGreenDim else BgElevated,
                    border = BorderStroke(
                        0.6.dp,
                        if (storage.isOptimized) AccentGreen.copy(alpha = 0.4f) else BorderSubtle
                    )
                ) {
                    Text(
                        text = if (storage.isOptimized) "OPTIMAL" else "STANDAR",
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (storage.isOptimized) AccentGreen else TextSecondary,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
        },
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ── 1. MountX Physical Flash Shield & Anti-Thrashing ──
            if (storage.isDetected) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (storage.isOptimized) AccentGreen else AccentBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "MountX Flash Shield (${storage.mountPoint.ifBlank { "/data" }})",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    val metaPairs = buildList {
                        if (storage.parentDevice.isNotBlank()) add("Disk" to storage.parentDevice)
                        if (storage.primaryStorageType.isNotBlank()) add("Bus" to storage.primaryStorageType)
                        if (storage.fsType.isNotBlank()) add("FS" to storage.fsType.uppercase())
                        if (storage.emmcName.isNotBlank()) add("Chip" to storage.emmcName)
                        if (storage.emmcLifeTime.isNotBlank()) add("Wear" to storage.emmcLifeTime)
                        if (storage.targetReadAheadKb != null) add("Target RA" to "${storage.targetReadAheadKb} KB")
                    }
                    if (metaPairs.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            metaPairs.chunked(2).forEach { rowPairs ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowPairs.forEach { (k, v) ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = BgElevated,
                                            border = BorderStroke(0.6.dp, BorderSubtle),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(k, fontSize = 10.5.sp, color = TextSecondary)
                                                Text(
                                                    v,
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = TextPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    if (rowPairs.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LynxActionButton(
                            text = "MountX Boost",
                            icon = Icons.Default.Bolt,
                            accentColor = AccentCyan,
                            onClick = { viewModel.triggerMountXBoost(context, true) },
                            modifier = Modifier.weight(1f)
                        )
                        LynxActionButton(
                            text = "Pulihkan Standar",
                            accentColor = AccentBlue,
                            onClick = { viewModel.triggerMountXBoost(context, false) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                HorizontalDivider(color = BorderGlass, thickness = 0.6.dp)
            }

            // ── 2. External MicroSD Card Quick Summary (if detected by kernel) ──
            val externalSdDevices = remember(uiState.ioDevices) {
                uiState.ioDevices.filter {
                    val t = it.deviceType.uppercase()
                    (t == "SD" || (it.device.startsWith("mmcblk") && !it.isPrimary &&
                        !it.device.contains("boot") && !it.device.contains("rpmb")))
                }
            }
            if (externalSdDevices.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AccentBlueDim,
                    border = BorderStroke(0.6.dp, AccentBlue.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SdStorage,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "MicroSD Eksternal Terdeteksi",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = externalSdDevices.joinToString(", ") {
                                    "${it.device} (${it.currentScheduler.ifBlank { "default" }} • ${it.readAheadKb} KB)"
                                },
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = AccentBlue
                            )
                        }
                    }
                }
            }

            // ── 3. Dynamic Block Device Toggle (Virtual / Boot Partition visibility) ──
            LynxSwitch(
                label = "Tampilkan Perangkat Virtual & Partisi Boot",
                subLabel = "Menampilkan ${displayedDevices.size} dari ${uiState.ioDevices.size} node blok",
                checked = showVirtualDevices,
                onCheckedChange = { showVirtualDevices = it }
            )

            // ── 4. Dynamic Per-Device I/O Scheduler, Read-Ahead & Queue Tunables ──
            if (displayedDevices.isEmpty()) {
                UnsupportedBadge(
                    reason = "Tidak ada block device I/O sysfs (/sys/block/*/queue) yang dapat dibaca pada kernel ini."
                )
            } else {
                displayedDevices.forEach { dev ->
                    IoBlockDevicePanel(
                        dev = dev,
                        defaultExpanded = dev.isPrimary || displayedDevices.size == 1,
                        onSelectScheduler = { sched -> viewModel.setIoScheduler(dev.device, sched) },
                        onSelectReadAhead = { kb -> viewModel.setReadAheadKb(dev.device, kb) },
                        onOpenCustomReadAhead = { customRaDevice = dev },
                        onOpenCustomNrRequests = { customNrDevice = dev },
                        onSetQueueTunable = { key, value ->
                            viewModel.setIoQueueTunable(dev.device, key, value)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun IoBlockDevicePanel(
    dev: IoDeviceInfo,
    defaultExpanded: Boolean,
    onSelectScheduler: (String) -> Unit,
    onSelectReadAhead: (Int) -> Unit,
    onOpenCustomReadAhead: () -> Unit,
    onOpenCustomNrRequests: () -> Unit,
    onSetQueueTunable: (key: String, value: Int) -> Unit
) {
    var expanded by remember(dev.device) { mutableStateOf(defaultExpanded) }
    val raOptions = remember(dev.deviceType, dev.targetReadAheadKb, dev.readAheadKb) {
        buildDynamicReadAheadOptions(
            deviceType = dev.deviceType,
            targetKb = dev.targetReadAheadKb
        )
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BgSurfaceLowest,
        border = BorderStroke(
            0.6.dp,
            if (dev.isPrimary) AccentCyan.copy(alpha = 0.35f) else BorderSubtle
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row: clickable accordion toggle for clean Progressive Disclosure
            Surface(
                onClick = { expanded = !expanded },
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "/dev/${dev.device}",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary
                            )
                            if (dev.deviceType.isNotBlank()) {
                                IoMetaBadge(dev.deviceType.uppercase(), AccentBlue)
                            }
                            if (dev.isPrimary) {
                                IoMetaBadge("UTAMA", AccentCyan)
                            }
                        }
                        if (!expanded) {
                            Text(
                                text = "${dev.currentScheduler.ifBlank { "default" }} • ${dev.readAheadKb} KB",
                                fontSize = 10.5.sp,
                                color = TextSecondary
                            )
                        } else if (dev.policy.isNotBlank()) {
                            Text(
                                text = dev.policy,
                                fontSize = 10.5.sp,
                                color = TextTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${dev.readAheadKb} KB",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan
                        )
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(220)),
                exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(animationSpec = tween(180))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (dev.availableSchedulers.isNotEmpty()) {
                        Text(
                            text = "I/O Scheduler (${dev.currentScheduler.ifBlank { "default" }})",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            dev.availableSchedulers.chunked(3).forEach { rowScheds ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowScheds.forEach { sched ->
                                        MemoryOptionChip(
                                            label = sched,
                                            selected = dev.currentScheduler == sched,
                                            accentColor = AccentCyan,
                                            onClick = { onSelectScheduler(sched) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    repeat(3 - rowScheds.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }

                    Text(
                        text = "Read-Ahead Cache",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                    val raChipItems: List<Pair<String, Int?>> =
                        raOptions.map { "${it} KB" to it } + ("Kustom" to null)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        raChipItems.chunked(3).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { (label, kbOpt) ->
                                    val isCustom = kbOpt == null
                                    val isSelected = if (isCustom) {
                                        dev.readAheadKb !in raOptions && dev.readAheadKb > 0
                                    } else {
                                        dev.readAheadKb == kbOpt
                                    }
                                    MemoryOptionChip(
                                        label = if (isCustom && isSelected) "${dev.readAheadKb}K*" else label,
                                        selected = isSelected,
                                        accentColor = if (isCustom) AccentPurple else AccentBlue,
                                        onClick = {
                                            if (kbOpt != null) onSelectReadAhead(kbOpt) else onOpenCustomReadAhead()
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }

                    val hasQueueTunables = dev.nrRequests != null || dev.rqAffinity != null || dev.iostats != null
                    if (hasQueueTunables) {
                        HorizontalDivider(color = BorderGlass, thickness = 0.6.dp)

                        dev.nrRequests?.let { nrReq ->
                            val nrPresets = remember(dev.deviceType) {
                                val t = dev.deviceType.uppercase()
                                if (t.contains("UFS") || t.contains("NVME")) listOf(64, 128, 256)
                                else listOf(32, 64, 128)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Queue Depth (nr_requests)",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "$nrReq req",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = AccentBlue
                                )
                            }
                            val nrItems: List<Pair<String, Int?>> =
                                nrPresets.map { "$it" to it } + ("Kustom" to null)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                nrItems.forEach { (label, valOpt) ->
                                    val isCustom = valOpt == null
                                    val isSelected = if (isCustom) nrReq !in nrPresets else nrReq == valOpt
                                    MemoryOptionChip(
                                        label = if (isCustom && isSelected) "${nrReq}*" else label,
                                        selected = isSelected,
                                        accentColor = if (isCustom) AccentPurple else AccentCyan,
                                        onClick = {
                                            if (valOpt != null) onSetQueueTunable("nr_requests", valOpt)
                                            else onOpenCustomNrRequests()
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        dev.rqAffinity?.let { rqAff ->
                            Text(
                                text = "RQ Affinity",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary
                            )
                            val rqOptions = listOf(0 to "0 (Off)", 1 to "1 (Sibling)", 2 to "2 (Strict)")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rqOptions.forEach { (valOpt, label) ->
                                    MemoryOptionChip(
                                        label = label,
                                        selected = rqAff == valOpt,
                                        accentColor = AccentBlue,
                                        onClick = { onSetQueueTunable("rq_affinity", valOpt) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        dev.iostats?.let { iostat ->
                            LynxSwitch(
                                label = "Kernel I/O Stats (iostats)",
                                subLabel = if (iostat == 0) {
                                    "Nonaktif — kurangi overhead statistik blok"
                                } else {
                                    "Aktif — catat metrik diskstats"
                                },
                                checked = iostat != 0,
                                onCheckedChange = { checked ->
                                    onSetQueueTunable("iostats", if (checked) 1 else 0)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IoMetaBadge(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(0.6.dp, color.copy(alpha = 0.35f))
    ) {
        Text(
            text = text,
            fontSize = 9.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun isPrimaryOrPhysicalBlock(dev: IoDeviceInfo): Boolean {
    val name = dev.device.lowercase()
    if (name.startsWith("zram") || name.startsWith("loop") || name.startsWith("ram") || name.startsWith("dm-")) {
        return false
    }
    if (name.contains("boot") || name.contains("rpmb")) {
        return false
    }
    if (dev.isPrimary) return true
    val type = dev.deviceType.uppercase()
    if (type in listOf("UFS", "EMMC", "NVME", "SD", "MMC")) return true
    return name.startsWith("sd") || name.startsWith("mmcblk") || name.startsWith("nvme")
}

private fun buildDynamicReadAheadOptions(deviceType: String, targetKb: Int?): List<Int> {
    val t = deviceType.uppercase()
    val base = when {
        t.contains("UFS") || t.contains("NVME") -> listOf(32, 64, 128, 256, 512)
        t.contains("EMMC") || t.contains("SD") || t.contains("MMC") -> listOf(64, 128, 256, 512, 1024)
        else -> listOf(128, 256, 512, 1024)
    }
    return (base + listOfNotNull(targetKb?.takeIf { it > 0 }))
        .distinct()
        .sorted()
        .take(5)
}
