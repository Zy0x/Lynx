package com.noir.lynx.feature.memory.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
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
 * MountXStorageCard — Flat, clean overview inside Tab 2 ("Penyimpanan"):
 * 1. Integrasi MountX (Module + App `app.mountx`, or `UNSUPPORTED` when absent)
 * 2. Media Penyimpanan (expanded by default) + Blok Kernel & Non-Storage Expert accordion (collapsed by default)
 * 3. Tapping any category opens a focused `ModalBottomSheet` (zero nested tabs, zero nested sub-pages)
 */
@Composable
fun MountXStorageCard(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val storage = uiState.mountXStorageInfo
    var activeSheetGroupId by remember { mutableStateOf<String?>(null) }
    var showOverrideDialog by remember { mutableStateOf(false) }
    var showNonStorageAccordion by remember { mutableStateOf(false) }

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
    val storageGroups = remember(allGroups) { allGroups.filter { it.isStorageMedia } }
    val nonStorageGroups = remember(allGroups) { allGroups.filter { !it.isStorageMedia } }
    val activeSheetGroup = remember(allGroups, activeSheetGroupId) {
        activeSheetGroupId?.let { target -> allGroups.firstOrNull { it.id == target } }
    }

    LaunchedEffect(activeSheetGroupId, activeSheetGroup, allGroups.isNotEmpty()) {
        if (activeSheetGroupId != null && allGroups.isNotEmpty() && activeSheetGroup == null) {
            activeSheetGroupId = null
        }
    }

    if (activeSheetGroup != null) {
        StorageBlockGroupBottomSheet(
            activeGroup = activeSheetGroup,
            mountx = storage,
            onDismiss = { activeSheetGroupId = null },
            onRequestOverrideUnlock = { showOverrideDialog = true },
            onRelockToMountX = { viewModel.setMountXOverride(false) },
            viewModel = viewModel
        )
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

        LynxCard(
            title = "Optimasi Kecepatan Baca/Tulis",
            subtitle = "Pilih kategori penyimpanan untuk mengatur kecepatan antrean data",
            icon = Icons.Default.Storage,
            accentColor = AccentCyan
        ) {
            if (allGroups.isEmpty()) {
                UnsupportedBadge(
                    reason = "Tidak ada kelompok penyimpanan (/sys/block/*/queue) yang tersedia pada kernel ini."
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider(color = BorderSubtle, thickness = 0.7.dp)
                    if (storageGroups.isNotEmpty()) {
                        StorageSectionHeader(
                            title = "Media Penyimpanan Utama",
                            subtitle = "Memori internal, kartu MicroSD & penyimpanan eksternal",
                            badgeText = "${storageGroups.size} KATEGORI",
                            accentColor = AccentCyan
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            storageGroups.forEach { grp ->
                                BlockGroupDirectoryRow(
                                    grp = grp,
                                    mountx = storage,
                                    onClick = { activeSheetGroupId = grp.id }
                                )
                            }
                        }
                    }

                    if (nonStorageGroups.isNotEmpty()) {
                        if (storageGroups.isNotEmpty()) {
                            StorageSectionDivider()
                        }
                        val nonStorageBlockTotal = remember(nonStorageGroups) {
                            nonStorageGroups.sumOf { it.devices.size }
                        }
                        StorageSectionHeader(
                            title = "Partisi Sistem & Virtual (Lanjutan)",
                            subtitle = "Partisi terenkripsi, ZRAM swap & blok sistem internal",
                            badgeText = "${nonStorageGroups.size} KATEGORI",
                            accentColor = AccentPurple
                        )
                        Surface(
                            onClick = { showNonStorageAccordion = !showNonStorageAccordion },
                            shape = RoundedCornerShape(12.dp),
                            color = BgSurfaceLowest,
                            border = BorderStroke(0.6.dp, BorderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = if (showNonStorageAccordion) {
                                            "Sembunyikan Partisi Sistem & Virtual"
                                        } else {
                                            "Tampilkan Partisi Sistem & Virtual"
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${nonStorageGroups.joinToString(", ") { it.shortTitle }} • Total $nonStorageBlockTotal blok",
                                        fontSize = 10.sp,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    imageVector = if (showNonStorageAccordion) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = AccentPurple,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = showNonStorageAccordion,
                            enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(220)),
                            exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(animationSpec = tween(180))
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                nonStorageGroups.forEach { grp ->
                                    BlockGroupDirectoryRow(
                                        grp = grp,
                                        mountx = storage,
                                        onClick = { activeSheetGroupId = grp.id }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockGroupDirectoryRow(
    grp: BlockGroupDescriptor,
    mountx: MountXStorageInfo,
    onClick: () -> Unit
) {
    val writableCount = grp.devices.count { !it.isReadOnly }
    val allProtected = writableCount > 0 && grp.devices.filter { !it.isReadOnly }
        .all { it.mountxLocked || mountx.isDiskProtected(it.device) }
    val activeDevs = grp.devices.filter { !it.isReadOnly }.ifEmpty { grp.devices }
    val schedSummary = activeDevs.map { it.currentScheduler }.distinct().let { scheds ->
        when {
            scheds.isEmpty() -> "default"
            scheds.size == 1 -> scheds.first().ifBlank { "default" }
            else -> "campuran"
        }
    }
    val raSummary = activeDevs.map { it.readAheadKb }.distinct().let { ras ->
        when {
            ras.isEmpty() -> ""
            ras.size == 1 -> "${ras.first()} KB"
            else -> "${ras.min()}–${ras.max()} KB"
        }
    }
    val countLabel = if (writableCount < grp.devices.size) {
        "$writableCount dapat diatur dari ${grp.devices.size} blok"
    } else {
        "${grp.devices.size} blok aktif"
    }
    val previewNames = grp.devices.take(3).joinToString(", ") { it.device } +
        if (grp.devices.size > 3) " (+${grp.devices.size - 3})" else ""

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = BgSurfaceLowest,
        border = BorderStroke(
            0.7.dp,
            if (allProtected) AccentOrange.copy(alpha = 0.40f) else grp.accent.copy(alpha = 0.25f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = grp.accent.copy(alpha = 0.14f),
                border = BorderStroke(0.6.dp, grp.accent.copy(alpha = 0.35f)),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = grp.icon,
                        contentDescription = null,
                        tint = grp.accent,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = grp.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (allProtected) {
                        StorageMetaBadge("MOUNTX", AccentOrange)
                    } else if (grp.busSubtitle.isNotBlank()) {
                        StorageMetaBadge(grp.busSubtitle, grp.accent)
                    }
                }
                Text(
                    text = "$countLabel • $previewNames",
                    fontSize = 10.5.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val metricLine = buildString {
                    append(schedSummary)
                    if (raSummary.isNotBlank()) append(" • RA $raSummary")
                    if (grp.volumes.isNotEmpty()) {
                        val fsList = grp.volumes.map { it.fsType.uppercase() }.filter { it.isNotBlank() }.distinct()
                        if (fsList.isNotEmpty()) append(" • ${fsList.joinToString("/")}")
                    }
                }
                Text(
                    text = metricLine,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = grp.accent.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Buka panel ${grp.title}",
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * StorageBlockGroupBottomSheet — Modal bottom sheet focused on ONE block category.
 * Presents group-level Induk tuning immediately at top + collapsible individual block inspector
 * with Writable/All filter & 12-item pagination so the sheet stays light and responsive.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StorageBlockGroupBottomSheet(
    activeGroup: BlockGroupDescriptor,
    mountx: MountXStorageInfo,
    onDismiss: () -> Unit,
    onRequestOverrideUnlock: () -> Unit,
    onRelockToMountX: () -> Unit,
    viewModel: LynxViewModel
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * 0.88f).dp

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BgCard,
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp),
                shape = RoundedCornerShape(2.dp),
                color = BorderSubtle
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .navigationBarsPadding()
        ) {
            // ── Sticky Sheet Header ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = activeGroup.accent.copy(alpha = 0.14f),
                    border = BorderStroke(0.6.dp, activeGroup.accent.copy(alpha = 0.35f)),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = activeGroup.icon,
                            contentDescription = null,
                            tint = activeGroup.accent,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = activeGroup.title,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (activeGroup.busSubtitle.isNotBlank()) {
                            StorageMetaBadge(activeGroup.busSubtitle, activeGroup.accent)
                        }
                    }
                    val writableCount = activeGroup.devices.count { !it.isReadOnly }
                    Text(
                        text = if (writableCount < activeGroup.devices.size) {
                            "$writableCount dapat diatur dari ${activeGroup.devices.size} blok"
                        } else {
                            "${activeGroup.devices.size} blok aktif • Pengaturan Serentak"
                        },
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup panel",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(color = BorderGlass, thickness = 0.6.dp, modifier = Modifier.padding(top = 6.dp))

            // ── Scrollable Body Inside Sheet ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (activeGroup.volumes.isNotEmpty()) {
                    CategoryVolumesSection(activeGroup = activeGroup)
                    StorageSectionDivider()
                }

                CategoryMasterTuningSection(
                    activeGroup = activeGroup,
                    mountx = mountx,
                    onRequestOverrideUnlock = onRequestOverrideUnlock,
                    onRelockToMountX = onRelockToMountX,
                    viewModel = viewModel
                )

                StorageSectionDivider()

                CategoryPerBlockSection(
                    activeGroup = activeGroup,
                    mountx = mountx,
                    onRequestOverrideUnlock = onRequestOverrideUnlock,
                    viewModel = viewModel
                )

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun CategoryVolumesSection(activeGroup: BlockGroupDescriptor) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StorageSectionHeader(
            title = "Partisi & Titik Mount",
            subtitle = "Volume dan filesystem aktif dalam kategori ini",
            badgeText = "${activeGroup.volumes.size} PARTISI",
            accentColor = AccentBlue
        )
        activeGroup.volumes.forEach { vol ->
            val total = if (vol.totalBytes > 0L) vol.totalBytes else vol.partitionSizeBytes
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgSurfaceLowest,
                border = BorderStroke(0.6.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f).padding(end = 6.dp)
                        ) {
                            Text(
                                text = vol.partition.ifBlank { vol.parentDisk },
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            if (vol.fsType.isNotBlank()) {
                                StorageMetaBadge(vol.fsType.uppercase(), AccentBlue)
                            }
                            if (vol.isMountxTarget) {
                                StorageMetaBadge("MOUNTX", AccentGreen)
                            }
                        }
                        if (total > 0L) {
                            Text(
                                text = formatStorageGb(total),
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        }
                    }
                    if (vol.mountPoint.isNotBlank()) {
                        Text(
                            text = vol.mountPoint,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryMasterTuningSection(
    activeGroup: BlockGroupDescriptor,
    mountx: MountXStorageInfo,
    onRequestOverrideUnlock: () -> Unit,
    onRelockToMountX: () -> Unit,
    viewModel: LynxViewModel
) {
    val writableDevs = activeGroup.devices.filter { !it.isReadOnly }
    val unlockedWritableDevs = writableDevs.filter { !it.mountxLocked && !mountx.isDiskProtected(it.device) }
    val anyProtected = writableDevs.any { it.mountxLocked || mountx.isDiskProtected(it.device) }
    val allProtected = writableDevs.isNotEmpty() && unlockedWritableDevs.isEmpty()
    val hasOverrideUnlockedInGroup = mountx.mountxInstalled &&
        mountx.ioTweaksActive &&
        mountx.overrideUnlocked &&
        activeGroup.devices.any { it.mountxManaged || mountx.managedDisks.contains(it.device) }

    val fsSet = activeGroup.volumes
        .map { it.fsType.uppercase() }
        .filter { it.isNotBlank() }
        .distinct()
    val hasF2fs = activeGroup.volumes.any { it.fsType.equals("f2fs", ignoreCase = true) || it.f2fsSysfsExists }
    val previewMembers = activeGroup.devices.take(5).joinToString(", ") { it.device } +
        if (activeGroup.devices.size > 5) " (+${activeGroup.devices.size - 5} lagi)" else ""

    StorageSectionHeader(
        title = "Target Pengaturan",
        subtitle = "Blok penyimpanan dalam kelompok ${activeGroup.shortTitle}",
        badgeText = "${writableDevs.size}/${activeGroup.devices.size} AKTIF",
        accentColor = activeGroup.accent
    )

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
            Text(
                text = "Anggota: $previewMembers",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (hasOverrideUnlockedInGroup) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StorageMetaBadge("OVERRIDE SESI AKTIF", AccentOrange)
                    Surface(
                        onClick = onRelockToMountX,
                        shape = RoundedCornerShape(8.dp),
                        color = AccentCyan.copy(alpha = 0.14f),
                        border = BorderStroke(0.6.dp, AccentCyan.copy(alpha = 0.4f)),
                        modifier = Modifier.heightIn(min = 38.dp)
                    ) {
                        Text(
                            text = "Kunci ke MountX",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
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

        StorageSectionDivider()

        StorageSectionHeader(
            title = "Mode Cepat",
            subtitle = "Terapkan profil baca/tulis ke ${unlockedWritableDevs.size} blok ${activeGroup.shortTitle}",
            accentColor = activeGroup.accent
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

        StorageSectionDivider()

        StorageSectionHeader(
            title = "Pengaturan Antrean Baca/Tulis",
            subtitle = "Atur penjadwal, ukuran baca-awal (Read-Ahead) & kapasitas antrean",
            accentColor = activeGroup.accent
        )

        val commonScheds = unlockedWritableDevs
            .map { it.availableSchedulers.toSet() }
            .reduceOrNull { acc, s -> acc intersect s }
            ?.toList()
            ?.ifEmpty { unlockedWritableDevs.flatMap { it.availableSchedulers }.distinct() }
            ?: unlockedWritableDevs.flatMap { it.availableSchedulers }.distinct()

        val schedVals = unlockedWritableDevs.map { it.currentScheduler }.distinct()
        val uniformSched = if (schedVals.size == 1) schedVals.first() else ""
        val schedSubtitle = if (schedVals.size > 1) {
            "Bervariasi antar blok: ${schedVals.joinToString("/")}"
        } else {
            "Aktif saat ini: ${uniformSched.ifBlank { "default" }}"
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

    val categoryNote = when {
        activeGroup.id == "zram" -> "ZRAM bekerja di dalam RAM. Ukuran Baca-Awal (Read-Ahead) 0 KB paling disarankan agar tetap responsif."
        activeGroup.id == "dm" -> "Partisi sistem terenkripsi; pengaturan hanya diterapkan pada blok yang mengizinkan perubahan."
        activeGroup.id == "loop" -> "Partisi kontainer modul sistem (APEX). Perubahan berlaku ke seluruh slot yang aktif."
        activeGroup.id == "hw_boot" -> "Partisi boot perangkat keras; hanya untuk informasi dan tidak digunakan aplikasi."
        activeGroup.id == "ram" || activeGroup.id == "other" -> "Blok virtual kernel; perubahan diterapkan ke blok yang mendukung penyesuaian."
        hasF2fs -> "Format penyimpanan F2FS aktif — bekerja optimal dengan Read-Ahead sedang (128–512 KB)."
        fsSet.contains("EXT4") -> "Format penyimpanan EXT4 aktif — stabil untuk penggunaan harian maupun game."
        fsSet.any { it in listOf("EXFAT", "VFAT", "FAT32", "NTFS", "FUSEBLK") } ->
            "Media eksternal (${fsSet.joinToString("/")}) — Read-Ahead lebih besar membantu mempercepat baca file besar."
        else -> "Gunakan daftar blok di bawah bila ingin mengatur satu partisi tertentu saja."
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = activeGroup.accent.copy(alpha = 0.06f),
        border = BorderStroke(0.6.dp, activeGroup.accent.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = "CATATAN KARAKTERISTIK",
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = activeGroup.accent
            )
            Text(
                text = categoryNote,
                fontSize = 10.5.sp,
                color = TextSecondary,
                lineHeight = 14.5.sp
            )
        }
    }
}

@Composable
private fun CategoryPerBlockSection(
    activeGroup: BlockGroupDescriptor,
    mountx: MountXStorageInfo,
    onRequestOverrideUnlock: () -> Unit,
    viewModel: LynxViewModel
) {
    val hasRoNodes = remember(activeGroup.devices) { activeGroup.devices.any { it.isReadOnly } }
    val hasWritableNodes = remember(activeGroup.devices) { activeGroup.devices.any { !it.isReadOnly } }

    var expanded by remember(activeGroup.id) { mutableStateOf(false) }
    var writableOnlyFilter by remember(activeGroup.id) { mutableStateOf(hasRoNodes && hasWritableNodes) }
    var showAllNodes by remember(activeGroup.id, writableOnlyFilter) { mutableStateOf(false) }

    val filteredList = remember(activeGroup.devices, writableOnlyFilter) {
        if (writableOnlyFilter) activeGroup.devices.filter { !it.isReadOnly } else activeGroup.devices
    }
    val visibleDevices = remember(filteredList, showAllNodes) {
        if (showAllNodes || filteredList.size <= 12) filteredList else filteredList.take(12)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StorageSectionHeader(
            title = "Inspeksi Blok Individual (Lanjutan)",
            subtitle = "Override setelan satu node blok saja bila dibutuhkan",
            badgeText = "${activeGroup.devices.size} BLOK",
            accentColor = AccentPurple
        )
        Surface(
            onClick = { expanded = !expanded },
            shape = RoundedCornerShape(12.dp),
            color = BgSurfaceLowest,
            border = BorderStroke(0.6.dp, AccentPurple.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = AccentPurple,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (expanded) {
                            "Tutup Daftar Blok Individual"
                        } else {
                            "Buka Daftar Blok (${activeGroup.devices.size} node)"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
                if (hasRoNodes && hasWritableNodes) {
                    val wCount = activeGroup.devices.count { !it.isReadOnly }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MemoryOptionChip(
                            label = "Writable Saja ($wCount)",
                            selected = writableOnlyFilter,
                            accentColor = AccentPurple,
                            onClick = { writableOnlyFilter = true },
                            modifier = Modifier.weight(1f)
                        )
                        MemoryOptionChip(
                            label = "Semua + RO (${activeGroup.devices.size})",
                            selected = !writableOnlyFilter,
                            accentColor = AccentPurple,
                            onClick = { writableOnlyFilter = false },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

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

                if (!showAllNodes && filteredList.size > visibleDevices.size) {
                    LynxActionButton(
                        text = "Tampilkan Semua (${filteredList.size} blok)",
                        accentColor = AccentPurple,
                        onClick = { showAllNodes = true }
                    )
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
                            if (dev.deviceType.isNotBlank()) {
                                StorageMetaBadge(dev.deviceType.uppercase(), AccentBlue)
                            }
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
                            schedulerHeader = "Aktif saat ini: ${dev.currentScheduler.ifBlank { "default" }}",
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
 * Shared I/O Queue Tuning UI used uniformly by both Group Induk Sheet and Per-Block Inspector.
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
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Penjadwal I/O (Scheduler)",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = schedulerHeader,
                    fontSize = 10.5.sp,
                    color = TextSecondary
                )
            }
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
            label = "Ukuran Baca-Awal (Read-Ahead)",
            value = readAheadKb.coerceIn(0, 16384),
            displayValue = readAheadDisplay,
            min = 0,
            max = 16384,
            unit = "KB",
            subtitle = "Kapasitas data yang disiapkan lebih awal saat membaca file ($scopeLabel)",
            dialogTitle = "Ukuran Baca-Awal ($scopeLabel)",
            dialogHint = if (categoryId == "zram") {
                "Untuk ZRAM, 0 KB sangat disarankan agar pembacaan memori tetap instan."
            } else {
                "Nilai kecil cocok untuk buka aplikasi cepat; nilai besar mempercepat pemuatan file & game besar."
            },
            presets = raPresets,
            accentColor = accentColor,
            onCommitValue = onCommitReadAhead
        )

        if (nrRequests != null && nrRequestsDisplay != null) {
            LynxNumericPresetRow(
                label = "Kapasitas Antrean Data",
                value = nrRequests.coerceIn(8, 2048),
                displayValue = nrRequestsDisplay,
                min = 8,
                max = 2048,
                unit = "req",
                subtitle = "Jumlah maksimal antrean baca/tulis dalam satu waktu",
                dialogTitle = "Kapasitas Antrean ($scopeLabel)",
                dialogHint = "Mengatur banyaknya antrean permintaan baca/tulis yang dapat diproses sekaligus.",
                presets = nrPresets,
                accentColor = AccentBlue,
                onCommitValue = onCommitNrRequests
            )
        }

        if (rqAffinity != null || iostats != null || addRandom != null) {
            HorizontalDivider(
                color = Color(0x22FFFFFF),
                thickness = 0.7.dp,
                modifier = Modifier.padding(vertical = 2.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = "Efisiensi Pemrosesan Antrean",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = "Penempatan inti CPU & pengurangan beban pencatatan latar belakang",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
        }

        if (rqAffinity != null) {
            Text(
                text = "Keterikatan Inti CPU (RQ Affinity)",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )
            val rqOptions = listOf(0 to "0 • Bebas", 1 to "1 • Kluster Sama", 2 to "2 • Inti Sama")
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
            HorizontalDivider(
                color = BorderGlass.copy(alpha = 0.6f),
                thickness = 0.6.dp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            LynxSwitch(
                label = "Pencatatan Statistik Disk (I/O Stats)",
                subLabel = if (!iostats) "Nonaktif — lebih ringan tanpa beban pencatatan statistik" else "Aktif — mencatat aktivitas baca/tulis penyimpanan",
                checked = iostats,
                onCheckedChange = onToggleIostats
            )
        }

        if (addRandom != null) {
            HorizontalDivider(
                color = BorderGlass.copy(alpha = 0.6f),
                thickness = 0.6.dp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            LynxSwitch(
                label = "Sumbang Waktu Disk ke Entropi",
                subLabel = if (!addRandom) "Nonaktif — kurangi beban tambahan di setiap aktivitas baca/tulis" else "Aktif — gunakan waktu akses penyimpanan untuk data acak",
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
    val knownOrder = listOf("internal", "sd", "usb", "external", "dm", "loop", "zram", "hw_boot", "ram", "other")
    val dynamicKeys = knownOrder.filter { grouped.containsKey(it) } +
        grouped.keys.filter { it !in knownOrder }.sorted()

    val descriptors = mutableListOf<BlockGroupDescriptor>()
    for (key in dynamicKeys) {
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
                    title = "USB OTG / Drive USB",
                    shortTitle = "USB OTG",
                    busSubtitle = "USB",
                    icon = Icons.Default.Usb,
                    accent = AccentPurple,
                    devices = devs,
                    volumes = vols
                )
                "external" -> BlockGroupDescriptor(
                    id = "external",
                    isStorageMedia = true,
                    title = "Penyimpanan Eksternal Lain",
                    shortTitle = "Eksternal",
                    busSubtitle = primaryType.uppercase().ifBlank { "Ext" },
                    icon = Icons.Default.SdStorage,
                    accent = AccentGreen,
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
                "hw_boot" -> BlockGroupDescriptor(
                    id = "hw_boot",
                    isStorageMedia = false,
                    title = "Partisi Boot & RPMB Hardware",
                    shortTitle = "Boot/RPMB",
                    busSubtitle = "HW BOOT",
                    icon = Icons.Default.Memory,
                    accent = AccentOrange,
                    devices = devs,
                    volumes = emptyList()
                )
                "ram" -> BlockGroupDescriptor(
                    id = "ram",
                    isStorageMedia = false,
                    title = "Blok RAMDisk (ram*)",
                    shortTitle = "RAMDisk",
                    busSubtitle = "RAM",
                    icon = Icons.Default.Memory,
                    accent = AccentOrange,
                    devices = devs,
                    volumes = emptyList()
                )
                else -> BlockGroupDescriptor(
                    id = key,
                    isStorageMedia = false,
                    title = "Blok Kernel Lainnya (${key.uppercase()})",
                    shortTitle = key.uppercase(),
                    busSubtitle = key.uppercase(),
                    icon = Icons.Default.Tune,
                    accent = AccentBlue,
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
