package com.noir.lynx.feature.memory.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Memory
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
    val isStorageMedia: Boolean,
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
 * 2. Tuning Induk Kelompok Blok dengan pemisahan kategori penuh:
 *    - Media Penyimpanan: Internal (UFS/eMMC), MicroSD, USB OTG
 *    - Blok Non-Storage Kernel: DM Mapper (`dm-*`), Loopback (`loop*`), ZRAM (`zram*`), RAMDisk/Boot (`ram*`)
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

    val allGroups = remember(uiState.ioDevices, uiState.storageVolumes) {
        buildSeparatedBlockGroups(uiState.ioDevices, uiState.storageVolumes)
    }
    var showNonStorageTier by remember { mutableStateOf(false) }
    val tierGroups = remember(allGroups, showNonStorageTier) {
        val filtered = allGroups.filter { it.isStorageMedia != showNonStorageTier }
        filtered.ifEmpty { allGroups }
    }

    var selectedGroupId by remember { mutableStateOf("internal") }
    LaunchedEffect(tierGroups) {
        if (tierGroups.isNotEmpty() && tierGroups.none { it.id == selectedGroupId }) {
            selectedGroupId = tierGroups.first().id
        }
    }
    val activeGroup = remember(tierGroups, selectedGroupId) {
        tierGroups.firstOrNull { it.id == selectedGroupId } ?: tierGroups.firstOrNull()
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
            allGroups = allGroups,
            tierGroups = tierGroups,
            showNonStorageTier = showNonStorageTier,
            activeGroup = activeGroup,
            mountx = storage,
            onSelectTier = { isNonStorage -> showNonStorageTier = isNonStorage },
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
    allGroups: List<BlockGroupDescriptor>,
    tierGroups: List<BlockGroupDescriptor>,
    showNonStorageTier: Boolean,
    activeGroup: BlockGroupDescriptor?,
    mountx: MountXStorageInfo,
    onSelectTier: (Boolean) -> Unit,
    onSelectGroup: (String) -> Unit,
    onRequestOverrideUnlock: () -> Unit,
    viewModel: LynxViewModel
) {
    val storageCount = remember(allGroups) { allGroups.count { it.isStorageMedia } }
    val nonStorageCount = remember(allGroups) { allGroups.count { !it.isStorageMedia } }

    LynxCard(
        title = "Tuning Induk Kelompok Blok",
        subtitle = activeGroup?.let {
            "${it.title} • ${it.devices.size} blok"
        } ?: "Kategori penyimpanan & blok virtual terpisah",
        icon = activeGroup?.icon ?: Icons.Default.Storage,
        accentColor = activeGroup?.accent ?: AccentCyan
    ) {
        if (allGroups.isEmpty() || activeGroup == null) {
            UnsupportedBadge(
                reason = "Tidak ada kelompok block device (/sys/block/*/queue) yang tersedia pada kernel ini."
            )
            return@LynxCard
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ── 1. Tier Toggle: Media Penyimpanan vs Non-Storage (DM / Loop / ZRAM / RAM) ──
            if (nonStorageCount > 0 && storageCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MemoryOptionChip(
                        label = "Media Penyimpanan ($storageCount)",
                        selected = !showNonStorageTier,
                        accentColor = AccentCyan,
                        onClick = { onSelectTier(false) },
                        modifier = Modifier.weight(1f)
                    )
                    MemoryOptionChip(
                        label = "Non-Storage ($nonStorageCount)",
                        selected = showNonStorageTier,
                        accentColor = AccentPurple,
                        onClick = { onSelectTier(true) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── 2. Category Selector Pills (Strictly Separated Per Category) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tierGroups.forEach { grp ->
                    val selected = grp.id == activeGroup.id
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) grp.accent.copy(alpha = 0.16f) else BgElevated,
                        border = BorderStroke(
                            if (selected) 1.dp else 0.6.dp,
                            if (selected) grp.accent.copy(alpha = 0.65f) else BorderSubtle
                        ),
                        modifier = Modifier
                            .widthIn(min = 96.dp)
                            .heightIn(min = 52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectGroup(grp.id) }
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
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
                                    maxLines = 1
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

            // ── 3. Member & Filesystem Summary Strip ──
            val writableDevs = activeGroup.devices.filter { !it.isReadOnly }
            val unlockedWritableDevs = writableDevs.filter { !it.mountxLocked && !mountx.isDiskProtected(it.device) }
            val anyProtected = writableDevs.any { it.mountxLocked || mountx.isDiskProtected(it.device) }
            val allProtected = writableDevs.isNotEmpty() && unlockedWritableDevs.isEmpty()

            val previewMembers = activeGroup.devices.take(5).joinToString(", ") { it.device } +
                if (activeGroup.devices.size > 5) " (+${activeGroup.devices.size - 5} lagi)" else ""
            val fsSet = activeGroup.volumes
                .map { it.fsType.uppercase() }
                .filter { it.isNotBlank() }
                .distinct()
            val hasF2fs = activeGroup.volumes.any { it.fsType.equals("f2fs", ignoreCase = true) || it.f2fsSysfsExists }

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
                            text = if (writableDevs.size < activeGroup.devices.size) {
                                "Anggota (${writableDevs.size} writable / ${activeGroup.devices.size} total)"
                            } else {
                                "Anggota Induk (${activeGroup.devices.size} blok)"
                            },
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
                        text = previewMembers,
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

            if (writableDevs.isEmpty()) {
                UnsupportedBadge(
                    reason = "Seluruh blok dalam kategori ${activeGroup.title} berstatus Read-Only (ro=1) pada kernel."
                )
            } else if (allProtected) {
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
                                    text = "Kategori Dikunci oleh MountX",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentOrange
                                )
                                Text(
                                    text = "Parameter blok ini dijaga agar tidak menimpa setting I/O MountX. Buka Override Lanjutan untuk mengaturnya dari Lynx.",
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
                        text = "Sebagian blok dikunci MountX; perubahan induk hanya diterapkan ke ${unlockedWritableDevs.size} blok yang bebas.",
                        fontSize = 10.5.sp,
                        color = AccentOrange
                    )
                }

                // ── 4. Preset Cepat Kategori ──
                Text(
                    text = "Preset Cepat (${activeGroup.shortTitle})",
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
                                    devicesInGroup = unlockedWritableDevs,
                                    preset = presetKey
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                HorizontalDivider(color = BorderGlass, thickness = 0.6.dp)

                // ── 5. Aggregated Group Tunables ──
                val commonScheds = unlockedWritableDevs
                    .map { it.availableSchedulers.toSet() }
                    .reduceOrNull { acc, s -> acc intersect s }
                    ?.toList()
                    ?.ifEmpty { unlockedWritableDevs.flatMap { it.availableSchedulers }.distinct() }
                    ?: unlockedWritableDevs.flatMap { it.availableSchedulers }.distinct()

                val schedVals = unlockedWritableDevs.map { it.currentScheduler }.distinct()
                val uniformSched = if (schedVals.size == 1) schedVals.first() else ""
                val schedSubtitle = if (schedVals.size > 1) {
                    "Saat ini bervariasi (${schedVals.joinToString("/")})"
                } else {
                    "Aktif: ${uniformSched.ifBlank { "default" }}"
                }

                val raVals = unlockedWritableDevs.map { it.readAheadKb }.distinct()
                val raRepresentative = raVals.firstOrNull() ?: 128
                val raDisplay = when {
                    raVals.isEmpty() -> "—"
                    raVals.size == 1 -> "${raVals.first()} KB"
                    else -> "${raVals.min()}–${raVals.max()} KB (Campuran)"
                }

                val nrVals = unlockedWritableDevs.mapNotNull { it.nrRequests }.distinct()
                val nrRepresentative = nrVals.firstOrNull()
                val nrDisplay = when {
                    nrVals.isEmpty() -> null
                    nrVals.size == 1 -> "${nrVals.first()} req"
                    else -> "${nrVals.min()}–${nrVals.max()} req (Campuran)"
                }

                val rqaVals = unlockedWritableDevs.mapNotNull { it.rqAffinity }.distinct()
                val rqaUniform = if (rqaVals.size == 1) rqaVals.first() else -1

                val iosVals = unlockedWritableDevs.mapNotNull { it.iostats }.distinct()
                val iosChecked = iosVals.firstOrNull()?.let { it != 0 }

                val randVals = unlockedWritableDevs.mapNotNull { it.addRandom }.distinct()
                val randChecked = randVals.firstOrNull()?.let { it != 0 }

                IoQueueTuningSection(
                    categoryId = activeGroup.id,
                    scopeLabel = "${activeGroup.shortTitle} (${unlockedWritableDevs.size} blok)",
                    deviceTypeHint = activeGroup.busSubtitle.ifBlank { activeGroup.shortTitle },
                    availableSchedulers = commonScheds,
                    currentScheduler = uniformSched,
                    schedulerHeader = schedSubtitle,
                    readAheadKb = raRepresentative,
                    readAheadDisplay = raDisplay,
                    targetReadAheadKb = unlockedWritableDevs.firstNotNullOfOrNull { it.targetReadAheadKb },
                    nrRequests = nrRepresentative,
                    nrRequestsDisplay = nrDisplay,
                    rqAffinity = if (rqaVals.isEmpty()) null else rqaUniform,
                    iostats = iosChecked,
                    addRandom = randChecked,
                    accentColor = activeGroup.accent,
                    onSelectScheduler = { sched ->
                        viewModel.tuneBlockGroup(activeGroup.id, unlockedWritableDevs, "scheduler", sched)
                    },
                    onCommitReadAhead = { kb ->
                        viewModel.tuneBlockGroup(activeGroup.id, unlockedWritableDevs, "read_ahead_kb", kb.toString())
                    },
                    onCommitNrRequests = { req ->
                        viewModel.tuneBlockGroup(activeGroup.id, unlockedWritableDevs, "nr_requests", req.toString())
                    },
                    onSelectRqAffinity = { v ->
                        viewModel.tuneBlockGroup(activeGroup.id, unlockedWritableDevs, "rq_affinity", v.toString())
                    },
                    onToggleIostats = { checked ->
                        viewModel.tuneBlockGroup(activeGroup.id, unlockedWritableDevs, "iostats", if (checked) "1" else "0")
                    },
                    onToggleAddRandom = { checked ->
                        viewModel.tuneBlockGroup(activeGroup.id, unlockedWritableDevs, "add_random", if (checked) "1" else "0")
                    }
                )
            }

            // ── 6. Category Context Note ──
            val categoryNote = when {
                activeGroup.id == "zram" -> "ZRAM adalah blok kompresi RAM (swap). Disarankan Read-Ahead 0 KB bersama Page-Cluster 0 agar kernel membaca tepat 1 halaman tanpa prefetch berlebih."
                activeGroup.id == "dm" -> "Device Mapper (dm-*) membungkus partisi FBE/LVM/dynamic. Sebagian dm-verity bersifat Read-Only; perubahan hanya diterapkan ke node writable seperti /data."
                activeGroup.id == "loop" -> "Blok Loopback (loop*) dipakai modul APEX & image virtual; pengaturan scheduler/read_ahead berlaku serentak ke seluruh slot loop."
                activeGroup.id == "ram" -> "Blok hardware boot/RPMB/ramdisk. Umumnya kecil dan tidak dipakai menyimpan file aplikasi user."
                hasF2fs -> "Filesystem F2FS aktif (/sys/fs/f2fs terdeteksi) — optimal dengan Read-Ahead terukur tanpa buffer berlebih."
                fsSet.contains("EXT4") -> "Filesystem EXT4 aktif — stabil pada beban baca/tulis acak dan sekuensial."
                fsSet.any { it in listOf("EXFAT", "VFAT", "FAT32", "NTFS", "FUSEBLK") } ->
                    "Media eksternal (${fsSet.joinToString("/")}) — Read-Ahead lebih besar membantu transfer dan pemutaran aset besar."
                else -> "Gunakan Tuning Lanjutan di bawah bila ingin menyetel satu blok secara terpisah."
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgSurfaceLowest,
                border = BorderStroke(0.6.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = categoryNote,
                    fontSize = 10.5.sp,
                    color = TextSecondary,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
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
    var scopeAllCategories by remember { mutableStateOf(false) }
    var showAllNodes by remember(activeGroup?.id, scopeAllCategories) { mutableStateOf(false) }

    val storageCount = remember(allDevices) { allDevices.count { it.isPhysicalStorageGroup() } }
    val nonStorageCount = remember(allDevices, storageCount) { (allDevices.size - storageCount).coerceAtLeast(0) }

    val sourceList = remember(allDevices, activeGroup, scopeAllCategories) {
        if (!scopeAllCategories && activeGroup != null) {
            activeGroup.devices
        } else {
            allDevices
        }
    }
    val visibleDevices = remember(sourceList, showAllNodes) {
        if (showAllNodes || sourceList.size <= 12) sourceList else sourceList.take(12)
    }

    LynxCard(
        title = "Tuning Lanjutan Per-Blok (Expert)",
        subtitle = "$storageCount blok penyimpanan • $nonStorageCount blok DM/Loop/ZRAM/RAM",
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
                        text = if (expanded) {
                            "Tutup Inspektor Per-Blok"
                        } else {
                            "Buka Inspektor (${activeGroup?.shortTitle ?: "Semua"}: ${activeGroup?.devices?.size ?: allDevices.size} blok)"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
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
                            label = "${activeGroup?.shortTitle ?: "Kategori"} (${activeGroup?.devices?.size ?: 0})",
                            selected = !scopeAllCategories,
                            accentColor = AccentPurple,
                            onClick = { scopeAllCategories = false },
                            modifier = Modifier.weight(1f)
                        )
                        MemoryOptionChip(
                            label = "Semua (${allDevices.size})",
                            selected = scopeAllCategories,
                            accentColor = AccentPurple,
                            onClick = { scopeAllCategories = true },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (visibleDevices.isEmpty()) {
                        UnsupportedBadge(reason = "Tidak ada blok yang tersedia pada kategori ini.")
                    } else {
                        visibleDevices.forEach { dev ->
                            val isLocked = dev.mountxLocked || mountx.isDiskProtected(dev.device)
                            IoBlockDevicePanel(
                                dev = dev,
                                isLockedByMountX = isLocked,
                                defaultExpanded = visibleDevices.size == 1 && !dev.isReadOnly,
                                onRequestOverrideUnlock = onRequestOverrideUnlock,
                                onSelectScheduler = { sched -> viewModel.setIoScheduler(dev.device, sched) },
                                onCommitReadAhead = { kb -> viewModel.setReadAheadKb(dev.device, kb) },
                                onCommitNrRequests = { req -> viewModel.setIoQueueTunable(dev.device, "nr_requests", req) },
                                onSelectRqAffinity = { v -> viewModel.setIoQueueTunable(dev.device, "rq_affinity", v) },
                                onToggleIostats = { c -> viewModel.setIoQueueTunable(dev.device, "iostats", if (c) 1 else 0) },
                                onToggleAddRandom = { c -> viewModel.setIoQueueTunable(dev.device, "add_random", if (c) 1 else 0) }
                            )
                        }

                        if (!showAllNodes && sourceList.size > visibleDevices.size) {
                            LynxActionButton(
                                text = "Tampilkan Semua (${sourceList.size} blok)",
                                accentColor = AccentPurple,
                                onClick = { showAllNodes = true }
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
    val groupId = remember(dev.device, dev.group, dev.deviceType) { dev.resolvedGroup() }

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
                            StorageMetaBadge(groupId.uppercase(), AccentBlue)
                            if (dev.isPrimary) {
                                StorageMetaBadge("UTAMA", AccentCyan)
                            }
                            if (dev.isReadOnly) {
                                StorageMetaBadge("RO", TextSecondary)
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
                    if (dev.isReadOnly) {
                        Text(
                            text = "Node blok ini bersifat Read-Only (ro=1, biasanya sistem/verity) sehingga kernel tidak menerima penulisan I/O.",
                            fontSize = 10.5.sp,
                            color = TextSecondary
                        )
                    } else if (isLockedByMountX) {
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
                            categoryId = groupId,
                            scopeLabel = "/dev/${dev.device}",
                            deviceTypeHint = dev.deviceType.ifBlank { groupId.uppercase() },
                            availableSchedulers = dev.availableSchedulers,
                            currentScheduler = dev.currentScheduler,
                            schedulerHeader = "I/O Scheduler (${dev.currentScheduler.ifBlank { "default" }})",
                            readAheadKb = dev.readAheadKb.coerceAtLeast(0),
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
    categoryId: String,
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
    val raPresets = remember(categoryId, deviceTypeHint, targetReadAheadKb) {
        buildDynamicReadAheadPresets(categoryId, deviceTypeHint, targetReadAheadKb)
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
            value = readAheadKb.coerceIn(0, 16384),
            displayValue = readAheadDisplay,
            min = 0,
            max = 16384,
            unit = "KB",
            subtitle = "Buffer prefetch baca berurutan ($scopeLabel)",
            dialogTitle = "Read-Ahead ($scopeLabel)",
            dialogHint = if (categoryId == "zram") {
                "Untuk ZRAM swap, 0 KB menonaktifkan swap prefetch sehingga latensi paging turun."
            } else {
                "Tipe: $deviceTypeHint. Nilai kecil fokus respons random 4K; nilai besar mempercepat transfer & streaming aset besar."
            },
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

private fun buildSeparatedBlockGroups(
    devices: List<IoDeviceInfo>,
    volumes: List<StorageVolumeInfo>
): List<BlockGroupDescriptor> {
    val grouped = devices.groupBy { it.resolvedGroup() }
    val order = listOf("internal", "sd", "usb", "dm", "loop", "zram", "ram")
    val descriptors = mutableListOf<BlockGroupDescriptor>()

    for (key in order) {
        val devs = grouped[key].orEmpty()
        if (devs.isEmpty()) continue
        val vols = volumes.filter { v ->
            v.group.equals(key, ignoreCase = true) || devs.any { it.device == v.parentDisk }
        }
        val primaryType = devs.firstOrNull { it.isPrimary }?.deviceType ?: devs.first().deviceType
        descriptors.add(
            when (key) {
                "internal" -> BlockGroupDescriptor(
                    id = "internal",
                    isStorageMedia = true,
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
                    isStorageMedia = true,
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
                    isStorageMedia = true,
                    title = "USB OTG / Drive Eksternal",
                    shortTitle = "USB OTG",
                    busSubtitle = "USB Mass",
                    icon = Icons.Default.Usb,
                    accent = AccentPurple,
                    devices = devs,
                    volumes = vols
                )
                "dm" -> BlockGroupDescriptor(
                    id = "dm",
                    isStorageMedia = false,
                    title = "Device Mapper (dm-*)",
                    shortTitle = "DM Mapper",
                    busSubtitle = "DM",
                    icon = Icons.Default.Tune,
                    accent = AccentPurple,
                    devices = devs,
                    volumes = emptyList()
                )
                "loop" -> BlockGroupDescriptor(
                    id = "loop",
                    isStorageMedia = false,
                    title = "Blok Loopback (loop*)",
                    shortTitle = "Loopback",
                    busSubtitle = "LOOP",
                    icon = Icons.Default.Tune,
                    accent = AccentBlue,
                    devices = devs,
                    volumes = emptyList()
                )
                "zram" -> BlockGroupDescriptor(
                    id = "zram",
                    isStorageMedia = false,
                    title = "Blok ZRAM Swap (zram*)",
                    shortTitle = "ZRAM",
                    busSubtitle = "ZRAM",
                    icon = Icons.Default.Memory,
                    accent = AccentGreen,
                    devices = devs,
                    volumes = emptyList()
                )
                else -> BlockGroupDescriptor(
                    id = "ram",
                    isStorageMedia = false,
                    title = "RAMDisk & Boot Hardware",
                    shortTitle = "RAMDisk",
                    busSubtitle = "RAM/Boot",
                    icon = Icons.Default.Memory,
                    accent = AccentOrange,
                    devices = devs,
                    volumes = emptyList()
                )
            }
        )
    }
    return descriptors
}

private fun buildDynamicReadAheadPresets(categoryId: String, deviceType: String, targetKb: Int?): List<Pair<String, Int>> {
    if (categoryId == "zram") {
        return listOf(
            "0 KB • Off (Optimal)" to 0,
            "4 KB • Minimum" to 4,
            "16 KB • Ringan" to 16,
            "128 KB • Default" to 128
        )
    }
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
            "0 KB • Off" to 0,
            "128 KB • Ringan" to 128,
            "256 KB • Aman" to 256,
            "512 KB • Seimbang" to 512,
            "1024 KB • Tinggi" to 1024,
            "2048 KB • Streaming" to 2048
        )
    }
    return if (targetKb != null && targetKb >= 0 && base.none { it.second == targetKb }) {
        (base + ("$targetKb KB • Target" to targetKb)).sortedBy { it.second }
    } else {
        base
    }
}
