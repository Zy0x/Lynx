package com.noir.lynx.feature.memory.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import java.util.Locale
import kotlin.math.roundToInt

/**
 * StorageHeroCard — Always-visible overview of internal `/data` storage capacity, bus type (UFS/eMMC),
 * filesystem, JEDEC flash wear (human-translated), and primary block I/O status.
 */
@Composable
internal fun StorageHeroCard(
    uiState: LynxUiState,
    modifier: Modifier = Modifier
) {
    val storage = uiState.mountXStorageInfo
    val primaryDev = remember(uiState.ioDevices) {
        uiState.ioDevices.firstOrNull { it.isPrimary }
    }
    val totalBytes = storage.internalTotalBytes.coerceAtLeast(0L)
    val availBytes = storage.internalAvailableBytes.coerceIn(0L, totalBytes)
    val usedBytes = (totalBytes - availBytes).coerceAtLeast(0L)
    val usedRatio = if (totalBytes > 0L) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
    val usedPercent = (usedRatio * 100f).roundToInt()

    val healthColor = when {
        usedPercent >= 90 -> AccentRed
        usedPercent >= 75 -> AccentOrange
        else -> AccentCyan
    }

    val busLabel = storage.primaryStorageType
        .ifBlank { primaryDev?.deviceType?.uppercase().orEmpty() }
        .ifBlank { "Flash Internal" }
    val fsLabel = storage.internalFsType
        .ifBlank { if (!storage.isDetected) storage.fsType else "" }
        .uppercase()
    val wearInfo = remember(storage.emmcLifeTime) {
        formatEmmcWearLabel(storage.emmcLifeTime)
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF10141F),
        border = BorderStroke(
            0.8.dp,
            Brush.verticalGradient(
                listOf(
                    AccentCyan.copy(alpha = 0.28f),
                    Color(0x15FFFFFF)
                )
            )
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = healthColor.copy(alpha = 0.20f),
                        modifier = Modifier.size(12.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Surface(
                                shape = CircleShape,
                                color = healthColor,
                                modifier = Modifier.size(6.dp)
                            ) {}
                        }
                    }
                    Column {
                        Text(
                            text = "PENYIMPANAN INTERNAL (/data)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (storage.emmcName.isNotBlank()) {
                            Text(
                                text = "Chip ${storage.emmcName}",
                                fontSize = 10.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AccentCyan.copy(alpha = 0.12f),
                    border = BorderStroke(0.6.dp, AccentCyan.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = if (fsLabel.isNotBlank()) "$busLabel • $fsLabel" else busLabel,
                        color = AccentCyan,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            if (totalBytes > 0L) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = "${formatStorageGb(usedBytes, includeUnit = false)} / ${formatStorageGb(totalBytes)}",
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "$usedPercent% • Sisa ${formatStorageGb(availBytes)}",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { usedRatio },
                        color = healthColor,
                        trackColor = BgSurfaceLowest,
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            } else {
                Text(
                    text = "Kapasitas partisi /data sedang dimuat...",
                    fontSize = 11.5.sp,
                    color = TextSecondary
                )
            }

            if (wearInfo != null || primaryDev != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    wearInfo?.let { (wearLabel, wearColor) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = wearColor.copy(alpha = 0.10f),
                            border = BorderStroke(0.6.dp, wearColor.copy(alpha = 0.32f)),
                            modifier = if (primaryDev != null) Modifier.weight(1f) else Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Wear: $wearLabel",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = wearColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                    primaryDev?.let { dev ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BgElevated,
                            border = BorderStroke(0.6.dp, BorderSubtle),
                            modifier = if (wearInfo != null) Modifier.weight(1f) else Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${dev.device}: ${dev.currentScheduler.ifBlank { "default" }} • ${dev.readAheadKb} KB",
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * MountXStorageCard — Sub-tab 2 (Storage & I/O).
 * Clean direct controls without lock-bar friction:
 * - External MicroSD policy & MountX Boost/Restore (shown strictly when an external SD card is mounted)
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

    val externalSdDevices = remember(uiState.ioDevices) {
        uiState.ioDevices.filter {
            val t = it.deviceType.uppercase()
            (t == "SD" || (it.device.startsWith("mmcblk") && !it.isPrimary &&
                !it.device.contains("boot") && !it.device.contains("rpmb")))
        }
    }
    val hasExternalSd = storage.isDetected || externalSdDevices.isNotEmpty()
    val primaryBusLabel = storage.primaryStorageType.ifBlank {
        displayedDevices.firstOrNull { it.isPrimary }?.deviceType?.uppercase().orEmpty()
    }.ifBlank { "Deteksi Otomatis" }
    val primaryFsLabel = storage.internalFsType
        .ifBlank { if (!storage.isDetected) storage.fsType else "" }
        .uppercase()

    LynxCard(
        title = "Storage & I/O Scheduler",
        subtitle = buildString {
            append(primaryBusLabel)
            if (primaryFsLabel.isNotBlank()) append(" ($primaryFsLabel)")
            append(" • ${displayedDevices.size} perangkat")
        },
        icon = Icons.Default.Storage,
        accentColor = AccentCyan,
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ── 1. MountX External MicroSD Policy (shown strictly when an external SD card is mounted) ──
            if (hasExternalSd) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SdStorage,
                                contentDescription = null,
                                tint = if (storage.isOptimized) AccentGreen else AccentBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Kebijakan I/O Kartu SD (${storage.mountPoint.ifBlank { "/storage" }})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
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
                    }

                    Text(
                        text = "Membatasi read_ahead_kb kartu SD eksternal (<= 256 KB) agar baca-tulis stabil tanpa membebani antrean I/O.",
                        fontSize = 10.5.sp,
                        color = TextSecondary,
                        lineHeight = 14.sp
                    )

                    if (storage.isDetected) {
                        val sdMetaPairs = buildList {
                            if (storage.parentDevice.isNotBlank()) add("Disk" to storage.parentDevice)
                            if (storage.fsType.isNotBlank()) add("FS" to storage.fsType.uppercase())
                            if (storage.currentReadAheadKb > 0) add("Read-Ahead" to "${storage.currentReadAheadKb} KB")
                            if (storage.targetReadAheadKb != null) add("Target RA" to "${storage.targetReadAheadKb} KB")
                            if (storage.currentScheduler.isNotBlank()) add("Scheduler" to storage.currentScheduler)
                        }
                        if (sdMetaPairs.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                sdMetaPairs.chunked(2).forEach { rowPairs ->
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
                    } else if (externalSdDevices.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AccentBlueDim,
                            border = BorderStroke(0.6.dp, AccentBlue.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = externalSdDevices.joinToString(", ") {
                                    "${it.device} (${it.currentScheduler.ifBlank { "default" }} • ${it.readAheadKb} KB)"
                                },
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = AccentBlue,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
                HorizontalDivider(color = BorderGlass, thickness = 0.6.dp)
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
                        val schedCols = if (dev.availableSchedulers.size == 4) 2 else 3
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            dev.availableSchedulers.chunked(schedCols).forEach { rowScheds ->
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
                                    repeat(schedCols - rowScheds.size) { Spacer(Modifier.weight(1f)) }
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
                            val rqOptions = listOf(0 to "0 • Off", 1 to "1 • Sibling", 2 to "2 • Strict")
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

private fun formatStorageGb(bytes: Long, includeUnit: Boolean = true): String {
    if (bytes <= 0L) return if (includeUnit) "0.0 GB" else "0.0"
    val gb = bytes / 1_000_000_000.0
    val formatted = "%.1f".format(Locale.US, gb)
    return if (includeUnit) "$formatted GB" else formatted
}

private fun formatEmmcWearLabel(raw: String): Pair<String, Color>? {
    if (raw.isBlank()) return null
    val worst = raw.trim()
        .split(Regex("\\s+"))
        .mapNotNull { token -> token.removePrefix("0x").removePrefix("0X").toIntOrNull(16) }
        .filter { it > 0 }
        .maxOrNull() ?: return null
    return when {
        worst == 0x01 -> "Sehat (0–10%)" to AccentGreen
        worst in 0x02..0x03 -> "Baik (${(worst - 1) * 10}–${worst * 10}%)" to AccentGreen
        worst in 0x04..0x06 -> "Normal (${(worst - 1) * 10}–${worst * 10}%)" to AccentCyan
        worst in 0x07..0x09 -> "Menua (${(worst - 1) * 10}–${worst * 10}%)" to AccentOrange
        worst == 0x0A -> "Kritis (90–100%)" to AccentRed
        worst >= 0x0B -> "Maksimal (>100%)" to AccentRed
        else -> null
    }
}
