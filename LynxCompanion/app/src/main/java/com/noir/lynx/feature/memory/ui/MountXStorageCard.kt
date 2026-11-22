package com.noir.lynx.feature.memory.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.data.IoDeviceInfo
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.data.MountXStorageInfo
import com.noir.lynx.data.StorageVolumeInfo
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.card.UnsupportedBadge
import com.noir.lynx.designsystem.controls.LynxActionButton
import com.noir.lynx.designsystem.controls.LynxNumericPresetRow
import com.noir.lynx.designsystem.controls.LynxSwitch
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.ui.LynxViewModel
import com.noir.lynx.ui.applyBlockGroupPreset
import com.noir.lynx.ui.setIoQueueTunable
import com.noir.lynx.ui.setIoScheduler
import com.noir.lynx.ui.setMountXOverride
import com.noir.lynx.ui.setReadAheadKb
import com.noir.lynx.ui.tuneBlockGroup

private data class BlockGroupDescriptor(
    val id: String,
    val title: String,
    val shortTitle: String,
    val busSubtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val devices: List<IoDeviceInfo>,
    val volumes: List<StorageVolumeInfo>
)

/**
 * MountXStorageCard — Complete Sub-tab 2 ("Penyimpanan"):
 * 1. Integrasi MountX (Module + App `app.mountx`, or `Unsupported` when absent; Override confirmation)
 * 2. Tuning Induk Kelompok Blok (`Internal Flash sda..sdc` / `MicroSD` / `USB OTG`)
 * 3. Tuning Lanjutan & Inspektor Per-Blok (collapsed by default)
 */
@Composable
fun MountXStorageCard(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val storage = uiState.mountXStorageInfo
    var showOverrideDialog by remember { mutableStateOf(false) }

    if (showOverrideDialog) {
        MountXOverrideConfirmDialog(
            storage = storage,
            onDismiss = { showOverrideDialog = false },
            onConfirmUnlock = {
                showOverrideDialog = false
                viewModel.setMountXOverride(true)
            }
        )
    }

    val groups = remember(uiState.ioDevices, uiState.storageVolumes) {
        buildStorageBlockGroups(uiState.ioDevices, uiState.storageVolumes)
    }
    var selectedGroupId by remember { mutableStateOf("internal") }
    LaunchedEffect(groups) {
        if (groups.isNotEmpty() && groups.none { it.id == selectedGroupId }) {
            selectedGroupId = groups.first().id
        }
    }
    val activeGroup = remember(groups, selectedGroupId) {
        groups.firstOrNull { it.id == selectedGroupId } ?: groups.firstOrNull()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MountXIntegrationCard(
            storage = storage,
            onRequestOverrideUnlock = { showOverrideDialog = true },
            onRelockToMountX = { viewModel.setMountXOverride(false) }
        )

        BlockGroupMasterCard(
            groups = groups,
            activeGroup = activeGroup,
            mountx = storage,
            onSelectGroup = { selectedGroupId = it },
            onRequestOverrideUnlock = { showOverrideDialog = true },
            viewModel = viewModel
        )

        AdvancedPerBlockInspectorCard(
            allDevices = uiState.ioDevices,
            activeGroup = activeGroup,
            mountx = storage,
            onRequestOverrideUnlock = { showOverrideDialog = true },
            viewModel = viewModel
        )
    }
}

@Composable
private fun BlockGroupMasterCard(
    groups: List<BlockGroupDescriptor>,
    activeGroup: BlockGroupDescriptor?,
    mountx: MountXStorageInfo,
    onSelectGroup: (String) -> Unit,
    onRequestOverrideUnlock: () -> Unit,
    viewModel: LynxViewModel
) {
    LynxCard(
        title = "Tuning Induk Kelompok Blok",
        subtitle = activeGroup?.let {
            "${it.title} • ${it.devices.size} blok fisik diatur serentak"
        } ?: "Mengatur seluruh blok satu kategori sekaligus",
        icon = Icons.Default.Storage,
        accentColor = activeGroup?.accent ?: AccentCyan
    ) {
        if (groups.isEmpty() || activeGroup == null) {
            UnsupportedBadge(
                reason = "Tidak ada kelompok block device (/sys/block/*/queue) yang tersedia pada kernel ini."
            )
            return@LynxCard
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ── 1. Selector Kategori Kelompok Blok ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groups.forEach { grp ->
                    val selected = grp.id == activeGroup.id
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) grp.accent.copy(alpha = 0.16f) else BgElevated,
                        border = BorderStroke(
                            if (selected) 1.dp else 0.6.dp,
                            if (selected) grp.accent.copy(alpha = 0.65f) else BorderSubtle
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectGroup(grp.id) }
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = grp.icon,
                                    contentDescription = null,
                                    tint = if (selected) grp.accent else TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = grp.shortTitle,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) grp.accent else TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "${grp.devices.size} blok",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (selected) grp.accent.copy(alpha = 0.85f) else TextSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // ── 2. Ringkasan Anggota & Filesystem Kelompok ──
            val memberListText = activeGroup.devices.joinToString(", ") { it.device }
            val fsSet = activeGroup.volumes
                .map { it.fsType.uppercase() }
                .filter { it.isNotBlank() }
                .distinct()
            val hasF2fs = activeGroup.volumes.any { it.fsType.equals("f2fs", ignoreCase = true) || it.f2fsSysfsExists }
            val anyProtected = activeGroup.devices.any { it.mountxLocked || mountx.isDiskProtected(it.device) }
            val allProtected = activeGroup.devices.all { it.mountxLocked || mountx.isDiskProtected(it.device) }
            val unlockedMembers = activeGroup.devices.filter { !it.mountxLocked && !mountx.isDiskProtected(it.device) }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgSurfaceLowest,
                border = BorderStroke(0.6.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Anggota Induk (${activeGroup.devices.size} blok)",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            if (activeGroup.busSubtitle.isNotBlank()) {
                                StorageMetaBadge(activeGroup.busSubtitle, activeGroup.accent)
                            }
                            fsSet.forEach { fs ->
                                StorageMetaBadge(fs, AccentBlue)
                            }
                        }
                    }
                    Text(
                        text = memberListText,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (activeGroup.volumes.isNotEmpty()) {
                        val partSummary = activeGroup.volumes.joinToString(" • ") { v ->
                            "${v.partition.ifBlank { v.parentDisk }} (${v.fsType.uppercase()})"
                        }
                        Text(
                            text = "Partisi terpasang: $partSummary",
                            fontSize = 10.sp,
                            color = TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (allProtected) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AccentOrange.copy(alpha = 0.08f),
                    border = BorderStroke(0.7.dp, AccentOrange.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = AccentOrange,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Kelompok Dikunci oleh MountX",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentOrange
                                )
                                Text(
                                    text = "Parameter kelompok ini sedang dijaga agar tidak menimpa setting I/O MountX. Buka Override Lanjutan jika Anda ingin mengatur manual via Lynx.",
                                    fontSize = 10.5.sp,
                                    color = TextSecondary,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                        LynxActionButton(
                            text = "Buka Override (Konfirmasi Lanjutan)",
                            icon = Icons.Default.LockOpen,
                            accentColor = AccentOrange,
                            onClick = onRequestOverrideUnlock
                        )
                    }
                }
            } else {
                if (anyProtected) {
                    Text(
                        text = "Catatan: Sebagian blok dikunci MountX; perubahan induk hanya diterapkan ke ${unlockedMembers.size} blok yang tidak dikunci.",
                        fontSize = 10.5.sp,
                        color = AccentOrange
                    )
                }

                // ── 3. Preset Cepat 1-Tap Kelompok ──
                Text(
                    text = "Preset Cepat Induk (${activeGroup.shortTitle})",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "powersave" to "Standar Aman",
                        "balanced" to "Responsif",
                        "performance" to "Performa"
                    ).forEach { (presetKey, label) ->
                        MemoryOptionChip(
                            label = label,
                            selected = false,
                            accentColor = when (presetKey) {
                                "powersave" -> AccentGreen
                                "performance" -> AccentOrange
                                else -> activeGroup.accent
                            },
                            onClick = {
                                viewModel.applyBlockGroupPreset(
                                    groupId = activeGroup.id,
                                    devicesInGroup = activeGroup.devices,
                                    preset = presetKey
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                HorizontalDivider(color = BorderGlass, thickness = 0.6.dp)

                // ── 4. Aggregated Group Parameters via Shared UI Controls ──
                val sourceDevs = unlockedMembers.ifEmpty { activeGroup.devices }
                val commonScheds = sourceDevs
                    .map { it.availableSchedulers.toSet() }
                    .reduceOrNull { acc, s -> acc intersect s }
                    ?.toList()
                    ?.ifEmpty { sourceDevs.flatMap { it.availableSchedulers }.distinct() }
                    ?: sourceDevs.flatMap { it.availableSchedulers }.distinct()

                val schedVals = sourceDevs.map { it.currentScheduler }.distinct()
                val uniformSched = if (schedVals.size == 1) schedVals.first() else ""
                val schedSubtitle = if (schedVals.size > 1) {
                    "Saat ini bervariasi (${schedVals.joinToString("/")})"
                } else {
                    "Aktif di semua blok: ${uniformSched.ifBlank { "default" }}"
                }

                val raVals = sourceDevs.map { it.readAheadKb }.filter { it > 0 }.distinct()
                val raRepresentative = raVals.firstOrNull() ?: 128
                val raDisplay = when {
                    raVals.isEmpty() -> "—"
                    raVals.size == 1 -> "${raVals.first()} KB"
                    else -> "${raVals.min()}–${raVals.max()} KB (Campuran)"
                }

                val nrVals = sourceDevs.mapNotNull { it.nrRequests }.distinct()
                val nrRepresentative = nrVals.firstOrNull()
                val nrDisplay = when {
                    nrVals.isEmpty() -> null
                    nrVals.size == 1 -> "${nrVals.first()} req"
                    else -> "${nrVals.min()}–${nrVals.max()} req (Campuran)"
                }

                val rqaVals = sourceDevs.mapNotNull { it.rqAffinity }.distinct()
                val rqaUniform = if (rqaVals.size == 1) rqaVals.first() else -1

                val iosVals = sourceDevs.mapNotNull { it.iostats }.distinct()
                val iosChecked = iosVals.firstOrNull()?.let { it != 0 }

                val randVals = sourceDevs.mapNotNull { it.addRandom }.distinct()
                val randChecked = randVals.firstOrNull()?.let { it != 0 }

                val hintType = activeGroup.busSubtitle.ifBlank { activeGroup.shortTitle }
                IoQueueTuningSection(
                    scopeLabel = "Induk ${activeGroup.shortTitle} (${sourceDevs.size} blok)",
                    deviceTypeHint = hintType,
                    availableSchedulers = commonScheds,
                    currentScheduler = uniformSched,
                    schedulerHeader = schedSubtitle,
                    readAheadKb = raRepresentative,
                    readAheadDisplay = raDisplay,
                    targetReadAheadKb = sourceDevs.firstNotNullOfOrNull { it.targetReadAheadKb },
                    nrRequests = nrRepresentative,
                    nrRequestsDisplay = nrDisplay,
                    rqAffinity = if (rqaVals.isEmpty()) null else rqaUniform,
                    iostats = iosChecked,
                    addRandom = randChecked,
                    accentColor = activeGroup.accent,
                    onSelectScheduler = { sched ->
                        viewModel.tuneBlockGroup(activeGroup.id, activeGroup.devices, "scheduler", sched)
                    },
                    onCommitReadAhead = { kb ->
                        viewModel.tuneBlockGroup(activeGroup.id, activeGroup.devices, "read_ahead_kb", kb.toString())
                    },
                    onCommitNrRequests = { req ->
                        viewModel.tuneBlockGroup(activeGroup.id, activeGroup.devices, "nr_requests", req.toString())
                    },
                    onSelectRqAffinity = { v ->
                        viewModel.tuneBlockGroup(activeGroup.id, activeGroup.devices, "rq_affinity", v.toString())
                    },
                    onToggleIostats = { checked ->
                        viewModel.tuneBlockGroup(activeGroup.id, activeGroup.devices, "iostats", if (checked) "1" else "0")
                    },
                    onToggleAddRandom = { checked ->
                        viewModel.tuneBlockGroup(activeGroup.id, activeGroup.devices, "add_random", if (checked) "1" else "0")
                    }
                )
            }

            if (activeGroup.volumes.isNotEmpty() || hasF2fs) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(0.6.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val fsNote = buildString {
                        if (hasF2fs) {
                            append("Filesystem F2FS aktif (/sys/fs/f2fs terdeteksi) — cocok dengan Read-Ahead terukur tanpa buffer berlebihan.")
                        } else if (fsSet.contains("EXT4")) {
                            append("Filesystem EXT4 aktif — jurnal POSIX stabil pada I/O acak & sekuensial.")
                        } else if (fsSet.any { it in listOf("EXFAT", "VFAT", "FAT32", "NTFS", "FUSEBLK") }) {
                            append("Media eksternal (${fsSet.joinToString("/")}) — Read-Ahead lebih besar membantu transfer & streaming file besar.")
                        } else {
                            append("Gunakan Tuning Lanjutan di bawah jika perlu membedakan satu blok khusus saja.")
                        }
                    }
                    Text(
                        text = fsNote,
                        fontSize = 10.5.sp,
                        color = TextSecondary,
                        lineHeight = 14.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AdvancedPerBlockInspectorCard(
    allDevices: List<IoDeviceInfo>,
    activeGroup: BlockGroupDescriptor?,
    mountx: MountXStorageInfo,
    onRequestOverrideUnlock: () -> Unit,
    viewModel: LynxViewModel
) {
    var expanded by remember { mutableStateOf(false) }
    var scopeAllGroups by remember { mutableStateOf(false) }
    var includeVirtual by remember { mutableStateOf(false) }

    val physicalCount = remember(allDevices) { allDevices.count { !isVirtualOrBootBlock(it) } }
    val virtualCount = remember(allDevices, physicalCount) { (allDevices.size - physicalCount).coerceAtLeast(0) }

    val displayedDevices = remember(allDevices, activeGroup, scopeAllGroups, includeVirtual) {
        val base = if (!scopeAllGroups && activeGroup != null && !includeVirtual) {
            activeGroup.devices
        } else {
            allDevices
        }
        if (includeVirtual) base else base.filter { !isVirtualOrBootBlock(it) }
    }

    LynxCard(
        title = "Tuning Lanjutan Per-Blok (Expert)",
        subtitle = "$physicalCount blok fisik • $virtualCount blok virtual/mapper",
        icon = Icons.Default.Tune,
        accentColor = AccentPurple
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                onClick = { expanded = !expanded },
                shape = RoundedCornerShape(12.dp),
                color = BgSurfaceLowest,
                border = BorderStroke(0.6.dp, BorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (expanded) "Tutup Inspektor Blok Individual" else "Buka Kontrol Per-Blok & Blok Virtual",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = AccentPurple,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(220)),
                exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(animationSpec = tween(180))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MemoryOptionChip(
                            label = "Kelompok (${activeGroup?.shortTitle ?: "Aktif"})",
                            selected = !scopeAllGroups,
                            accentColor = AccentPurple,
                            onClick = { scopeAllGroups = false },
                            modifier = Modifier.weight(1f)
                        )
                        MemoryOptionChip(
                            label = "Semua Blok Fisik",
                            selected = scopeAllGroups,
                            accentColor = AccentPurple,
                            onClick = { scopeAllGroups = true },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    LynxSwitch(
                        label = "Tampilkan Blok Virtual / Mapper",
                        subLabel = "Menyertakan dm-*, zram, loop, dan partisi boot hardware",
                        checked = includeVirtual,
                        onCheckedChange = { includeVirtual = it }
                    )

                    if (displayedDevices.isEmpty()) {
                        UnsupportedBadge(reason = "Tidak ada blok yang cocok dengan filter ini.")
                    } else {
                        displayedDevices.forEach { dev ->
                            val isLocked = dev.mountxLocked || mountx.isDiskProtected(dev.device)
                            IoBlockDevicePanel(
                                dev = dev,
                                isLockedByMountX = isLocked,
                                defaultExpanded = displayedDevices.size == 1,
                                onRequestOverrideUnlock = onRequestOverrideUnlock,
                                onSelectScheduler = { sched -> viewModel.setIoScheduler(dev.device, sched) },
                                onCommitReadAhead = { kb -> viewModel.setReadAheadKb(dev.device, kb) },
                                onCommitNrRequests = { req -> viewModel.setIoQueueTunable(dev.device, "nr_requests", req) },
                                onSelectRqAffinity = { v -> viewModel.setIoQueueTunable(dev.device, "rq_affinity", v) },
                                onToggleIostats = { c -> viewModel.setIoQueueTunable(dev.device, "iostats", if (c) 1 else 0) },
                                onToggleAddRandom = { c -> viewModel.setIoQueueTunable(dev.device, "add_random", if (c) 1 else 0) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IoBlockDevicePanel(
    dev: IoDeviceInfo,
    isLockedByMountX: Boolean,
    defaultExpanded: Boolean,
    onRequestOverrideUnlock: () -> Unit,
    onSelectScheduler: (String) -> Unit,
    onCommitReadAhead: (Int) -> Unit,
    onCommitNrRequests: (Int) -> Unit,
    onSelectRqAffinity: (Int) -> Unit,
    onToggleIostats: (Boolean) -> Unit,
    onToggleAddRandom: (Boolean) -> Unit
) {
    var expanded by remember(dev.device) { mutableStateOf(defaultExpanded) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BgSurfaceLowest,
        border = BorderStroke(
            0.6.dp,
            when {
                isLockedByMountX -> AccentOrange.copy(alpha = 0.45f)
                dev.isPrimary -> AccentCyan.copy(alpha = 0.35f)
                else -> BorderSubtle
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Surface(
                onClick = { expanded = !expanded },
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
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
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary
                            )
                            if (dev.deviceType.isNotBlank()) {
                                StorageMetaBadge(dev.deviceType.uppercase(), AccentBlue)
                            }
                            if (dev.isPrimary) {
                                StorageMetaBadge("UTAMA", AccentCyan)
                            }
                            if (isLockedByMountX) {
                                StorageMetaBadge("MOUNTX", AccentOrange)
                            }
                        }
                        val metaLine = buildString {
                            append(dev.currentScheduler.ifBlank { "default" })
                            append(" • ${dev.readAheadKb} KB")
                            if (dev.sizeBytes > 0L) append(" • ${formatStorageGb(dev.sizeBytes)}")
                        }
                        Text(
                            text = metaLine,
                            fontSize = 10.5.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(200)) + fadeIn(animationSpec = tween(200)),
                exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(animationSpec = tween(180))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isLockedByMountX) {
                        Text(
                            text = "Blok ini dilindungi oleh MountX. Aktifkan Override Lanjutan untuk mengedit langsung.",
                            fontSize = 10.5.sp,
                            color = AccentOrange
                        )
                        LynxActionButton(
                            text = "Buka Override (Konfirmasi)",
                            icon = Icons.Default.LockOpen,
                            accentColor = AccentOrange,
                            onClick = onRequestOverrideUnlock
                        )
                    } else {
                        IoQueueTuningSection(
                            scopeLabel = "/dev/${dev.device}",
                            deviceTypeHint = dev.deviceType.ifBlank { "Block" },
                            availableSchedulers = dev.availableSchedulers,
                            currentScheduler = dev.currentScheduler,
                            schedulerHeader = "I/O Scheduler (${dev.currentScheduler.ifBlank { "default" }})",
                            readAheadKb = dev.readAheadKb.coerceAtLeast(16),
                            readAheadDisplay = "${dev.readAheadKb} KB",
                            targetReadAheadKb = dev.targetReadAheadKb,
                            nrRequests = dev.nrRequests,
                            nrRequestsDisplay = dev.nrRequests?.let { "$it req" },
                            rqAffinity = dev.rqAffinity,
                            iostats = dev.iostats?.let { it != 0 },
                            addRandom = dev.addRandom?.let { it != 0 },
                            accentColor = AccentCyan,
                            onSelectScheduler = onSelectScheduler,
                            onCommitReadAhead = onCommitReadAhead,
                            onCommitNrRequests = onCommitNrRequests,
                            onSelectRqAffinity = onSelectRqAffinity,
                            onToggleIostats = onToggleIostats,
                            onToggleAddRandom = onToggleAddRandom
                        )
                    }
                }
            }
        }
    }
}

/**
 * Shared I/O Queue Tuning UI used uniformly by both Group Induk Master Card and Per-Block Inspector.
 */
@Composable
private fun IoQueueTuningSection(
    scopeLabel: String,
    deviceTypeHint: String,
    availableSchedulers: List<String>,
    currentScheduler: String,
    schedulerHeader: String,
    readAheadKb: Int,
    readAheadDisplay: String,
    targetReadAheadKb: Int?,
    nrRequests: Int?,
    nrRequestsDisplay: String?,
    rqAffinity: Int?,
    iostats: Boolean?,
    addRandom: Boolean?,
    accentColor: Color,
    onSelectScheduler: (String) -> Unit,
    onCommitReadAhead: (Int) -> Unit,
    onCommitNrRequests: (Int) -> Unit,
    onSelectRqAffinity: (Int) -> Unit,
    onToggleIostats: (Boolean) -> Unit,
    onToggleAddRandom: (Boolean) -> Unit
) {
    val raPresets = remember(deviceTypeHint, targetReadAheadKb) {
        buildDynamicReadAheadPresets(deviceTypeHint, targetReadAheadKb)
    }
    val nrPresets = remember(deviceTypeHint) {
        val t = deviceTypeHint.uppercase()
        if (t.contains("UFS") || t.contains("NVME")) {
            listOf("32 • Rendah" to 32, "64 • Cepat" to 64, "128 • Standar" to 128, "256 • Tinggi" to 256, "512 • Maks" to 512)
        } else {
            listOf("32 • Aman" to 32, "64 • Responsif" to 64, "128 • Standar" to 128, "256 • Sekuensial" to 256, "512 • Besar" to 512)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (availableSchedulers.isNotEmpty()) {
            Text(
                text = schedulerHeader,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )
            val schedCols = if (availableSchedulers.size == 4) 2 else 3
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                availableSchedulers.chunked(schedCols).forEach { rowScheds ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowScheds.forEach { sched ->
                            MemoryOptionChip(
                                label = sched,
                                selected = currentScheduler == sched,
                                accentColor = accentColor,
                                onClick = { onSelectScheduler(sched) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(schedCols - rowScheds.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        LynxNumericPresetRow(
            label = "Read-Ahead Cache",
            value = readAheadKb.coerceIn(16, 16384),
            displayValue = readAheadDisplay,
            min = 16,
            max = 16384,
            unit = "KB",
            subtitle = "Buffer prefetch baca berurutan ($scopeLabel)",
            dialogTitle = "Read-Ahead ($scopeLabel)",
            dialogHint = "Tipe: $deviceTypeHint. Nilai kecil (64–128 KB) fokus respons random 4K; nilai besar (512–2048 KB) mempercepat streaming aset & game besar.",
            presets = raPresets,
            accentColor = accentColor,
            onCommitValue = onCommitReadAhead
        )

        if (nrRequests != null && nrRequestsDisplay != null) {
            LynxNumericPresetRow(
                label = "Queue Depth (nr_requests)",
                value = nrRequests.coerceIn(8, 2048),
                displayValue = nrRequestsDisplay,
                min = 8,
                max = 2048,
                unit = "req",
                subtitle = "Kedalaman antrean permintaan blok kernel",
                dialogTitle = "Queue Depth ($scopeLabel)",
                dialogHint = "Membatasi atau memperluas antrean I/O serentak di driver blok.",
                presets = nrPresets,
                accentColor = AccentBlue,
                onCommitValue = onCommitNrRequests
            )
        }

        if (rqAffinity != null) {
            Text(
                text = "RQ Affinity",
                fontSize = 11.sp,
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
                        selected = rqAffinity == valOpt,
                        accentColor = AccentBlue,
                        onClick = { onSelectRqAffinity(valOpt) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (iostats != null) {
            LynxSwitch(
                label = "Kernel I/O Stats (iostats)",
                subLabel = if (!iostats) "Nonaktif — kurangi overhead statistik I/O" else "Aktif — catat statistik blok",
                checked = iostats,
                onCheckedChange = onToggleIostats
            )
        }

        if (addRandom != null) {
            LynxSwitch(
                label = "Block Entropy Overhead (add_random)",
                subLabel = if (!addRandom) "Nonaktif — hindari jeda pengumpulan RNG di tiap request I/O" else "Aktif — kumpulkan timing disk ke pool RNG",
                checked = addRandom,
                onCheckedChange = onToggleAddRandom
            )
        }
    }
}

private fun buildStorageBlockGroups(
    devices: List<IoDeviceInfo>,
    volumes: List<StorageVolumeInfo>
): List<BlockGroupDescriptor> {
    val physicalOnly = devices.filter { !isVirtualOrBootBlock(it) }
    val grouped = physicalOnly.groupBy { it.resolvedGroup() }
    val order = listOf("internal", "sd", "usb", "virtual")
    val descriptors = mutableListOf<BlockGroupDescriptor>()

    for (key in order) {
        val devs = grouped[key].orEmpty()
        if (devs.isEmpty()) continue
        val vols = volumes.filter { v ->
            v.group.equals(key, ignoreCase = true) || devs.any { it.device == v.parentDisk }
        }
        val primaryType = devs.firstOrNull { it.isPrimary }?.deviceType
            ?: devs.first().deviceType
        descriptors.add(
            when (key) {
                "internal" -> BlockGroupDescriptor(
                    id = "internal",
                    title = "Penyimpanan Internal",
                    shortTitle = "Internal",
                    busSubtitle = primaryType.uppercase().ifBlank { "Flash" },
                    icon = Icons.Default.Storage,
                    accent = AccentCyan,
                    devices = devs,
                    volumes = vols
                )
                "sd" -> BlockGroupDescriptor(
                    id = "sd",
                    title = "Kartu Memori / MicroSD",
                    shortTitle = "MicroSD",
                    busSubtitle = "SD Card",
                    icon = Icons.Default.SdStorage,
                    accent = AccentBlue,
                    devices = devs,
                    volumes = vols
                )
                "usb" -> BlockGroupDescriptor(
                    id = "usb",
                    title = "USB OTG / Drive Eksternal",
                    shortTitle = "USB OTG",
                    busSubtitle = "USB Mass",
                    icon = Icons.Default.Usb,
                    accent = AccentPurple,
                    devices = devs,
                    volumes = vols
                )
                else -> BlockGroupDescriptor(
                    id = key,
                    title = "Blok Lainnya",
                    shortTitle = key.uppercase(),
                    busSubtitle = "Block",
                    icon = Icons.Default.Storage,
                    accent = AccentBlue,
                    devices = devs,
                    volumes = vols
                )
            }
        )
    }

    if (descriptors.isEmpty() && devices.isNotEmpty()) {
        descriptors.add(
            BlockGroupDescriptor(
                id = "virtual",
                title = "Blok Virtual",
                shortTitle = "Virtual",
                busSubtitle = "Virtual",
                icon = Icons.Default.Storage,
                accent = AccentPurple,
                devices = devices,
                volumes = volumes
            )
        )
    }
    return descriptors
}

private fun isVirtualOrBootBlock(dev: IoDeviceInfo): Boolean {
    val name = dev.device.lowercase()
    if (name.startsWith("zram") || name.startsWith("loop") || name.startsWith("ram") || name.startsWith("dm-")) {
        return true
    }
    if (name.contains("boot") || name.contains("rpmb")) {
        return true
    }
    return dev.resolvedGroup() == "virtual"
}

private fun buildDynamicReadAheadPresets(deviceType: String, targetKb: Int?): List<Pair<String, Int>> {
    val t = deviceType.uppercase()
    val base = when {
        t.contains("UFS") || t.contains("NVME") -> listOf(
            "64 KB • Acak 4K" to 64,
            "128 KB • Seimbang" to 128,
            "256 KB • Standar" to 256,
            "512 KB • Sekuensial" to 512,
            "1024 KB • Burst" to 1024
        )
        t.contains("EMMC") -> listOf(
            "64 KB • Responsif" to 64,
            "128 KB • Seimbang" to 128,
            "256 KB • Standar" to 256,
            "512 KB • Besar" to 512
        )
        else -> listOf(
            "128 KB • Ringan" to 128,
            "256 KB • Aman" to 256,
            "512 KB • Seimbang" to 512,
            "1024 KB • MountX" to 1024,
            "2048 KB • Streaming" to 2048
        )
    }
    return if (targetKb != null && targetKb > 0 && base.none { it.second == targetKb }) {
        (base + ("$targetKb KB • Target" to targetKb)).sortedBy { it.second }
    } else {
        base
    }
}
