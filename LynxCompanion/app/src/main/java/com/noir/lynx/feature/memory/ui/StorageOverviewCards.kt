package com.noir.lynx.feature.memory.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.data.MountXStorageInfo
import com.noir.lynx.data.StorageVolumeInfo
import com.noir.lynx.designsystem.card.LynxCapabilityBadge
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.card.LynxSectionDivider
import com.noir.lynx.designsystem.card.LynxSectionHeader
import com.noir.lynx.designsystem.controls.LynxActionButton
import com.noir.lynx.designsystem.theme.*
import java.util.Locale
import kotlin.math.roundToInt

/**
 * StorageHeroCard — Always-visible multi-volume storage overview on Tab "Penyimpanan".
 * Shows internal `/data`, flash chip / JEDEC wear, primary scheduler/RA, and all mounted external
 * volumes (MicroSD multi-partitions, USB OTG, and user/MountX custom mounts).
 */
@Composable
internal fun StorageHeroCard(
    uiState: LynxUiState,
    modifier: Modifier = Modifier
) {
    val storage = uiState.mountXStorageInfo
    val primaryDev = remember(uiState.ioDevices) {
        uiState.ioDevices.firstOrNull { it.isPrimary }
            ?: uiState.ioDevices.firstOrNull { it.resolvedGroup() == "internal" }
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
    val fsLabel = storage.internalFsType.uppercase()
    val wearInfo = remember(storage.emmcLifeTime) {
        formatEmmcWearLabel(storage.emmcLifeTime)
    }

    val externalVolumes = remember(uiState.storageVolumes) {
        uiState.storageVolumes.filter { vol ->
            if (vol.isPrimaryInternal) return@filter false
            val p = vol.partition.lowercase()
            val d = vol.parentDisk.lowercase()
            val m = vol.mountPoint.lowercase()
            if (p.startsWith("loop") || p.startsWith("zram") || p.startsWith("dm-") || p.startsWith("ram") || p.startsWith("nbd")) {
                return@filter false
            }
            if (d.startsWith("loop") || d.startsWith("zram") || d.startsWith("dm-") || d.startsWith("ram") || d.startsWith("nbd")) {
                return@filter false
            }
            if (m.contains("apex") || m.startsWith("/system") || m.startsWith("/vendor")) {
                return@filter false
            }
            val grp = vol.group.lowercase()
            grp.isBlank() || grp in listOf("sd", "usb", "external")
        }
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
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
            .padding(horizontal = 16.dp, vertical = 6.dp)
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
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = healthColor.copy(alpha = 0.20f),
                        modifier = Modifier.size(14.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Surface(
                                shape = CircleShape,
                                color = healthColor,
                                modifier = Modifier.size(7.dp)
                            ) {}
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Status & Partisi Penyimpanan",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (storage.emmcName.isNotBlank()) {
                                "Chip ${storage.emmcName} • Kapasitas internal & eksternal"
                            } else {
                                "Ringkasan kapasitas internal & media terpasang"
                            },
                            fontSize = 11.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                StorageMetaBadge(
                    text = if (fsLabel.isNotBlank()) "$busLabel • $fsLabel" else busLabel,
                    color = AccentCyan
                )
            }
            HorizontalDivider(color = BorderSubtle, thickness = 0.7.dp)

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
                    text = "Kapasitas partisi internal sedang dimuat...",
                    fontSize = 11.5.sp,
                    color = TextSecondary
                )
            }

            if (wearInfo != null || primaryDev != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    wearInfo?.let { wear ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = wear.color.copy(alpha = 0.08f),
                            border = BorderStroke(0.6.dp, wear.color.copy(alpha = 0.30f)),
                            modifier = (if (primaryDev != null) Modifier.weight(1f) else Modifier.fillMaxWidth())
                                .fillMaxHeight()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "TINGKAT KEAUSAN FLASH",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = wear.label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = wear.color,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = wear.detail,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    primaryDev?.let { dev ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BgElevated,
                            border = BorderStroke(0.6.dp, BorderSubtle),
                            modifier = (if (wearInfo != null) Modifier.weight(1f) else Modifier.fillMaxWidth())
                                .fillMaxHeight()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "ANTREAN UTAMA (${dev.device})",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${dev.currentScheduler.ifBlank { "default" }} • RA ${dev.readAheadKb} KB",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val queueMeta = buildString {
                                    if (fsLabel.isNotBlank()) append(fsLabel) else append(busLabel)
                                    dev.nrRequests?.let { append(" • Queue $it") }
                                }
                                Text(
                                    text = queueMeta,
                                    fontSize = 9.5.sp,
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

            if (externalVolumes.isNotEmpty()) {
                StorageSectionDivider()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StorageSectionHeader(
                        title = "Media Eksternal Terpasang",
                        subtitle = "Partisi SD Card, USB OTG, atau mount eksternal aktif",
                        badgeText = "${externalVolumes.size} PARTISI",
                        accentColor = AccentBlue
                    )
                    externalVolumes.forEach { vol ->
                        ExternalVolumeRow(vol = vol)
                    }
                }
            } else {
                StorageSectionDivider()
                Text(
                    text = "Media Eksternal: Tidak ada MicroSD / USB OTG yang terpasang saat ini.",
                    fontSize = 10.5.sp,
                    color = TextTertiary
                )
            }
        }
    }
}

@Composable
private fun ExternalVolumeRow(vol: StorageVolumeInfo) {
    val isUsb = vol.group.equals("usb", ignoreCase = true) || vol.deviceType.equals("usb", ignoreCase = true)
    val accent = when {
        vol.isMountxTarget -> AccentCyan
        isUsb -> AccentPurple
        else -> AccentBlue
    }
    val total = if (vol.totalBytes > 0L) vol.totalBytes else vol.partitionSizeBytes
    val avail = vol.availBytes.coerceIn(0L, total)
    val used = if (vol.totalBytes > 0L) (vol.totalBytes - avail).coerceAtLeast(0L) else 0L
    val ratio = if (vol.totalBytes > 0L) (used.toFloat() / vol.totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = BgSurfaceLowest,
        border = BorderStroke(0.6.dp, accent.copy(alpha = 0.28f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
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
                    Icon(
                        imageVector = when {
                            isUsb -> Icons.Default.Usb
                            vol.group == "sd" || vol.deviceType.equals("sd", ignoreCase = true) -> Icons.Default.SdStorage
                            else -> Icons.Default.Storage
                        },
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = vol.partition.ifBlank { vol.parentDisk },
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (vol.fsType.isNotBlank()) {
                        StorageMetaBadge(vol.fsType.uppercase(), accent)
                    }
                    if (vol.isMountxTarget) {
                        StorageMetaBadge("MOUNTX", AccentGreen)
                    }
                }
                Text(
                    text = if (vol.totalBytes > 0L) {
                        "${formatStorageGb(used, includeUnit = false)} / ${formatStorageGb(vol.totalBytes)}"
                    } else if (total > 0L) {
                        formatStorageGb(total)
                    } else {
                        "Aktif"
                    },
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
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
            if (vol.totalBytes > 0L) {
                LinearProgressIndicator(
                    progress = { ratio },
                    color = accent,
                    trackColor = BgElevated,
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }
        }
    }
}

/**
 * MountXIntegrationCard — Displays accurate MountX ecosystem detection
 * (Module + App `app.mountx`, dynamic SD_BASE without hardcoded paths, or UNSUPPORTED when absent)
 * and enforces protected mode with multi-step override confirmation.
 */
@Composable
internal fun MountXIntegrationCard(
    storage: MountXStorageInfo,
    onRequestOverrideUnlock: () -> Unit,
    onRelockToMountX: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = when {
        !storage.mountxInstalled -> TextSecondary
        storage.overrideUnlocked -> AccentOrange
        else -> AccentCyan
    }

    LynxCard(
        title = "Integrasi MountX",
        subtitle = when {
            !storage.mountxInstalled -> "Tidak terdeteksi di sistem"
            storage.mountxMode == "module+app" -> "Modul + App Standalone Aktif"
            storage.mountxMode == "module" -> "Modul Root Aktif"
            storage.mountxMode == "app" -> "App Standalone (app.mountx)"
            else -> "Ekosistem Terhubung"
        },
        icon = Icons.Default.SdStorage,
        accentColor = accent,
        modifier = modifier
    ) {
        if (!storage.mountxInstalled) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BgSurfaceLowest,
                border = BorderStroke(0.6.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Status Ekosistem MountX",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        StorageMetaBadge("UNSUPPORTED", TextSecondary)
                    }
                    Text(
                        text = "Baik modul root MountX maupun aplikasi standalone (app.mountx) tidak terpasang pada perangkat ini. Seluruh kontrol blok penyimpanan berjalan langsung melalui Lynx.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 15.sp
                    )
                }
            }
            return@LynxCard
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HorizontalDivider(color = BorderSubtle, thickness = 0.7.dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val modeText = when (storage.mountxMode) {
                        "module+app" -> "MODUL + APP"
                        "module" -> "MODUL ROOT"
                        else -> "APP ONLY"
                    }
                    StorageMetaBadge(modeText, AccentCyan)
                    if (storage.moduleVersion.isNotBlank()) {
                        StorageMetaBadge(storage.moduleVersion, AccentBlue)
                    }
                }
                StorageMetaBadge(
                    text = when {
                        storage.managedDisks.isEmpty() -> "SIAGA"
                        storage.overrideUnlocked -> "OVERRIDE AKTIF"
                        storage.ioTweaksActive -> "DILINDUNGI"
                        else -> "TERPASANG"
                    },
                    color = when {
                        storage.managedDisks.isEmpty() -> AccentBlue
                        storage.overrideUnlocked -> AccentOrange
                        storage.ioTweaksActive -> AccentGreen
                        else -> AccentBlue
                    }
                )
            }

            if (storage.managedDisks.isEmpty() && storage.activeBindCount == 0) {
                Text(
                    text = "MountX terpasang dalam mode siaga (belum ada partisi/bind-mount target yang dikonfigurasi di MountX). Kontrol penyimpanan Lynx bebas digunakan tanpa konflik.",
                    fontSize = 10.5.sp,
                    color = TextSecondary,
                    lineHeight = 14.5.sp
                )
            }

            val hasMountXConfig = storage.managedDisks.isNotEmpty() ||
                storage.sdBase.isNotBlank() ||
                storage.sdBlock.isNotBlank() ||
                storage.activeBindCount > 0

            val metaPairs = if (!hasMountXConfig) emptyList() else buildList {
                val base = storage.sdBase.ifBlank { storage.mountPoint }
                if (base.isNotBlank()) add("Basis Mount" to base)
                val blk = storage.sdBlock.ifBlank { storage.partitionName }
                if (blk.isNotBlank()) {
                    val suffix = if (storage.fsType.isNotBlank()) " (${storage.fsType.uppercase()})" else ""
                    add("Blok Target" to "${blk.substringAfterLast('/')}$suffix")
                }
                if (storage.managedDisks.isNotEmpty()) {
                    add("Disk Dijaga" to storage.managedDisks.joinToString(", "))
                } else if (storage.parentDevice.isNotBlank()) {
                    add("Disk Target" to storage.parentDevice)
                }
                val tweakSummary = if (storage.ioTweaksActive) {
                    val ra = storage.configuredReadAheadKb ?: storage.currentReadAheadKb
                    val sch = storage.configuredScheduler.ifBlank { storage.currentScheduler }.ifBlank { "default" }
                    if (ra > 0) "$ra KB • $sch" else sch
                } else {
                    "IO Tweaks Nonaktif"
                }
                add("Profil I/O MountX" to tweakSummary)
                if (storage.activeBindCount > 0) {
                    add("Bind-Mount" to "${storage.activeBindCount} target aktif")
                }
            }

            if (metaPairs.isNotEmpty()) {
                StorageSectionDivider()
                StorageSectionHeader(
                    title = "Parameter & Proteksi MountX",
                    subtitle = "Target volume serta profil I/O yang dikelola aktif",
                    accentColor = accent
                )
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
                                    Column(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(k, fontSize = 10.sp, color = TextSecondary)
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

            if (storage.ioTweaksActive) {
                StorageSectionDivider()
                if (!storage.overrideUnlocked) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AccentCyan.copy(alpha = 0.08f),
                        border = BorderStroke(0.6.dp, AccentCyan.copy(alpha = 0.30f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Lynx mengunci pengubahan otomatis agar pengaturan I/O MountX tidak tertimpa. Anda tetap bisa membuka override manual jika ingin menguji dari Lynx.",
                                fontSize = 10.5.sp,
                                color = TextSecondary,
                                lineHeight = 14.sp
                            )
                        }
                    }
                    LynxActionButton(
                        text = "Buka Kunci / Override Lanjutan",
                        icon = Icons.Default.LockOpen,
                        accentColor = AccentOrange,
                        onClick = onRequestOverrideUnlock
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AccentOrange.copy(alpha = 0.10f),
                        border = BorderStroke(0.6.dp, AccentOrange.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = AccentOrange,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Override manual sedang terbuka untuk sesi berjalan. Perubahan berlaku langsung di kernel sysfs dan bisa dikunci kembali kapan saja.",
                                fontSize = 10.5.sp,
                                color = AccentOrange,
                                lineHeight = 14.sp
                            )
                        }
                    }
                    LynxActionButton(
                        text = "Kunci Kembali (Serahkan ke MountX)",
                        icon = Icons.Default.Lock,
                        accentColor = AccentCyan,
                        onClick = onRelockToMountX
                    )
                }
            }
        }
    }
}

@Composable
internal fun MountXOverrideConfirmDialog(
    storage: MountXStorageInfo,
    onDismiss: () -> Unit,
    onConfirmUnlock: () -> Unit
) {
    var acknowledged by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141824),
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = AccentOrange,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = "Konfirmasi Lanjutan: Override MountX",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Disk target (${storage.managedDisks.joinToString(", ").ifBlank { storage.parentDevice.ifBlank { "SD" } }}) saat ini dikelola aktif oleh MountX (${storage.mountxMode}).",
                    fontSize = 12.sp,
                    color = TextPrimary,
                    lineHeight = 17.sp
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(0.6.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val base = storage.sdBase.ifBlank { storage.mountPoint }
                        if (base.isNotBlank()) {
                            Text("• Basis aktif: $base", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
                        }
                        val ra = storage.configuredReadAheadKb ?: storage.currentReadAheadKb
                        val sch = storage.configuredScheduler.ifBlank { storage.currentScheduler }
                        Text("• Setting MountX: RA $ra KB | Scheduler $sch", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AccentCyan)
                    }
                }
                Text(
                    text = "• Override hanya mengubah nilai sysfs kernel pada sesi berjalan dan tidak memodifikasi file config.conf maupun database app.mountx.\n• Saat layanan MountX dipicu atau perangkat reboot, aturan MountX akan kembali menjadi default kecuali diubah permanen di MountX.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (acknowledged) AccentOrange.copy(alpha = 0.12f) else BgElevated,
                    border = BorderStroke(
                        0.6.dp,
                        if (acknowledged) AccentOrange.copy(alpha = 0.45f) else BorderSubtle
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { acknowledged = !acknowledged }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = acknowledged,
                            onCheckedChange = { acknowledged = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = AccentOrange,
                                uncheckedColor = TextSecondary
                            )
                        )
                        Text(
                            text = "Saya paham dan izinkan Lynx membuka kontrol manual sementara",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirmUnlock,
                enabled = acknowledged,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = AccentOrange,
                    disabledContentColor = TextTertiary
                )
            ) {
                Text("Aktifkan Override", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = TextSecondary)
            }
        }
    )
}

@Composable
internal fun StorageSectionDivider(modifier: Modifier = Modifier) =
    LynxSectionDivider(modifier = modifier)

@Composable
internal fun StorageSectionHeader(
    title: String,
    subtitle: String? = null,
    badgeText: String? = null,
    accentColor: Color = AccentCyan,
    modifier: Modifier = Modifier
) = LynxSectionHeader(
    title = title,
    subtitle = subtitle,
    badgeText = badgeText,
    accentColor = accentColor,
    modifier = modifier
)

@Composable
internal fun StorageMetaBadge(text: String, color: Color) =
    LynxCapabilityBadge(text = text, color = color)

internal fun formatStorageGb(bytes: Long, includeUnit: Boolean = true): String {
    if (bytes <= 0L) return if (includeUnit) "0.0 GB" else "0.0"
    val gb = bytes / 1_000_000_000.0
    val formatted = "%.1f".format(Locale.US, gb)
    return if (includeUnit) "$formatted GB" else formatted
}

internal data class EmmcWearInfo(
    val label: String,
    val detail: String,
    val color: Color
)

internal fun formatEmmcWearLabel(raw: String): EmmcWearInfo? {
    if (raw.isBlank()) return null
    val tokens = raw.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    val values = tokens
        .mapNotNull { token -> token.removePrefix("0x").removePrefix("0X").toIntOrNull(16) }
        .filter { it > 0 }
    val worst = values.maxOrNull() ?: return null
    val detail = "JEDEC A/B: ${tokens.joinToString(" • ")}"
    return when {
        worst == 0x01 -> EmmcWearInfo("0–10% Terpakai (Baru)", detail, AccentGreen)
        worst in 0x02..0x03 -> EmmcWearInfo("${(worst - 1) * 10}–${worst * 10}% Terpakai (Baik)", detail, AccentGreen)
        worst in 0x04..0x06 -> EmmcWearInfo("${(worst - 1) * 10}–${worst * 10}% Terpakai (Normal)", detail, AccentCyan)
        worst in 0x07..0x09 -> EmmcWearInfo("${(worst - 1) * 10}–${worst * 10}% Terpakai (Menua)", detail, AccentOrange)
        worst == 0x0A -> EmmcWearInfo("90–100% Terpakai (Kritis)", detail, AccentRed)
        worst >= 0x0B -> EmmcWearInfo(">100% Terpakai (Lewat Batas)", detail, AccentRed)
        else -> null
    }
}
