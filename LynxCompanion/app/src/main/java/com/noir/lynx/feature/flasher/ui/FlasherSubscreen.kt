package com.noir.lynx.feature.flasher.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.feature.flasher.*
import com.noir.lynx.ui.LynxViewModel
import com.noir.lynx.ui.resetKernelToStock
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * FlasherSubscreen — Studio Universal Kernel & Partition Image Flasher.
 * Mendukung flashing berkas paket .ZIP (AnyKernel3) dan citra mentah partisi .IMG (Boot, Vendor Boot, DTBO, Init Boot).
 * Dilengkapi proteksi anti-brick multi-tier, inspeksi ukuran fisik, dan backup otomatis sebelum penulisan.
 */
@Composable
fun FlasherSubscreen(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var activeSlot by remember { mutableStateOf("") }
    var partitions by remember { mutableStateOf<List<PartitionBlockInfo>>(emptyList()) }
    var discoveredFiles by remember { mutableStateOf<List<DiscoveredFlashFile>>(emptyList()) }
    var savedBackups by remember { mutableStateOf<List<SavedBootBackup>>(emptyList()) }
    var isLoadingData by remember { mutableStateOf(true) }

    var selectedFile by remember { mutableStateOf<DiscoveredFlashFile?>(null) }
    var selectedTargetPartition by remember { mutableStateOf(PartitionTarget.BOOT) }
    var validationResult by remember { mutableStateOf<ImageValidationResult?>(null) }
    var isValidating by remember { mutableStateOf(false) }

    var autoBackupEnabled by remember { mutableStateOf(true) }
    var isFlashing by remember { mutableStateOf(false) }
    var terminalLogs by remember { mutableStateOf<List<String>>(emptyList()) }

    var showConfirmFlashDialog by remember { mutableStateOf(false) }
    var showPostFlashDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var backupToRestore by remember { mutableStateOf<SavedBootBackup?>(null) }
    var backupToDelete by remember { mutableStateOf<SavedBootBackup?>(null) }
    var showResetStockConfirm by remember { mutableStateOf(false) }

    val terminalScrollState = rememberScrollState()

    // Fungsi refresh seluruh data hardware & backup
    suspend fun refreshDataInternal() {
        isLoadingData = true
        activeSlot = FlasherManager.detectActiveSlot()
        partitions = FlasherManager.inspectPartitions()
        discoveredFiles = FlasherManager.scanDiscoveredFiles()
        savedBackups = FlasherManager.listBackups()
        isLoadingData = false
    }

    fun reloadAllData() {
        scope.launch {
            refreshDataInternal()
        }
    }

    LaunchedEffect(Unit) {
        reloadAllData()
    }

    // Auto-scroll terminal log ke bawah saat ada pesan baru
    LaunchedEffect(terminalLogs.size) {
        if (terminalLogs.isNotEmpty()) {
            terminalScrollState.animateScrollTo(terminalScrollState.maxValue)
        }
    }

    // Validasi ulang saat berkas atau target partisi berubah
    LaunchedEffect(selectedFile, selectedTargetPartition) {
        val f = selectedFile
        if (f != null) {
            isValidating = true
            validationResult = FlasherManager.validateTargetFile(f.path, selectedTargetPartition)
            isValidating = false
        } else {
            validationResult = null
        }
    }

    // SAF Document Picker Launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val contentResolver = context.contentResolver
                    var displayName = "picked_file"
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && cursor.moveToFirst()) {
                            displayName = cursor.getString(nameIndex)
                        }
                    }

                    val cacheFile = File(context.cacheDir, displayName)
                    withContext(Dispatchers.IO) {
                        contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(cacheFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }

                    val ext = cacheFile.extension.lowercase()
                    val picked = DiscoveredFlashFile(
                        file = cacheFile,
                        name = cacheFile.name,
                        path = cacheFile.absolutePath,
                        sizeBytes = cacheFile.length(),
                        sizeMb = cacheFile.length() / (1024f * 1024f),
                        modifiedDate = cacheFile.lastModified(),
                        isZip = ext == "zip",
                        isImg = ext == "img"
                    )
                    selectedFile = picked
                    selectedTargetPartition = PartitionTarget.fromFileName(cacheFile.name)
                } catch (e: Exception) {
                    Toast.makeText(context, "Gagal memuat berkas: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── 1. Hero Active Slot & Hardware Partition Inspector Card ──────────
        ActiveKernelSlotHeroCard(
            activeSlot = activeSlot,
            partitions = partitions,
            isLoading = isLoadingData,
            onRefresh = { reloadAllData() }
        )

        // ── 2. Universal Kernel & Image Flasher Studio Card ─────────────────
        UniversalFlasherStudioCard(
            selectedFile = selectedFile,
            discoveredFiles = discoveredFiles,
            validationResult = validationResult,
            isValidating = isValidating,
            selectedTargetPartition = selectedTargetPartition,
            autoBackupEnabled = autoBackupEnabled,
            isFlashing = isFlashing,
            terminalLogs = terminalLogs,
            terminalScrollState = terminalScrollState,
            onSelectDiscoveredFile = { file ->
                selectedFile = file
                selectedTargetPartition = PartitionTarget.fromFileName(file.name)
            },
            onPickFile = {
                filePickerLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
            },
            onClearSelectedFile = {
                selectedFile = null
                validationResult = null
            },
            onSelectTargetPartition = { selectedTargetPartition = it },
            onToggleAutoBackup = { autoBackupEnabled = it },
            onRequestFlash = {
                if (validationResult?.isValid == true) {
                    showConfirmFlashDialog = true
                }
            }
        )

        // ── 3. Vault Cadangan & Restorasi Partisi Boot ──────────────────────
        BootBackupVaultCard(
            savedBackups = savedBackups,
            isLoading = isLoadingData,
            onCreateBackup = { showBackupDialog = true },
            onRestore = { backupToRestore = it },
            onDelete = { backupToDelete = it },
            onShare = { backup ->
                try {
                    val file = File(backup.path)
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/octet-stream"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Bagikan Cadangan Partisi"))
                } catch (e: Exception) {
                    Toast.makeText(context, "Gagal membagikan berkas: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )

        // ── 4. Emergency & Disaster Recovery Tools ──────────────────────────
        FlasherSafetyToolsCard(
            onReboot = { mode ->
                val ok = FlasherManager.rebootDevice(mode)
                if (!ok) Toast.makeText(context, "Gagal memulai ulang ke $mode", Toast.LENGTH_SHORT).show()
            },
            onRequestResetStock = { showResetStockConfirm = true }
        )

        Spacer(Modifier.height(115.dp).navigationBarsPadding())
    }

    // ── Dialog Konfirmasi Keamanan Pra-Flash ──────────────────────────────────
    if (showConfirmFlashDialog && selectedFile != null && validationResult != null) {
        val f = selectedFile!!
        val v = validationResult!!
        AlertDialog(
            onDismissRequest = { showConfirmFlashDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = AccentOrange)
                    Text("Konfirmasi Penulisan Partisi", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Anda akan menuliskan berkas langsung ke partisi fisik hardware. Pastikan perangkat memiliki daya baterai mencukupi.",
                        color = TextSecondary, fontSize = 12.5.sp, lineHeight = 16.sp
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgElevated,
                        border = BorderStroke(0.8.dp, BorderGlass),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Berkas: ${f.name}", fontSize = 11.5.sp, color = TextPrimary, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text("Target : ${v.targetPartition.title} (${v.targetNodePath})", fontSize = 11.sp, color = AccentCyan)
                            Text("Ukuran : ${"%.2f".format(v.fileSizeMb)} MB / Partisi ${"%.1f".format(v.targetPartitionSizeMb)} MB", fontSize = 11.sp, color = TextSecondary)
                            if (autoBackupEnabled) {
                                Text("🛡️ Auto Pre-Flash Backup: AKTIF", fontSize = 10.5.sp, color = AccentGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (v.warnings.isNotEmpty()) {
                        Text(
                            "⚠️ Peringatan: ${v.warnings.first()}",
                            color = AccentOrange, fontSize = 11.sp, lineHeight = 14.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmFlashDialog = false
                        isFlashing = true
                        terminalLogs = listOf("Memulai inisialisasi flashing...")

                        scope.launch {
                            val (success, message) = if (f.isZip) {
                                FlasherManager.flashAnyKernelZip(
                                    zipPath = f.path,
                                    autoBackup = autoBackupEnabled,
                                    onLog = { line -> terminalLogs = terminalLogs + line }
                                )
                            } else {
                                FlasherManager.flashRawImage(
                                    imagePath = f.path,
                                    target = v.targetPartition,
                                    autoBackup = autoBackupEnabled,
                                    onLog = { line -> terminalLogs = terminalLogs + line }
                                )
                            }

                            isFlashing = false
                            reloadAllData()
                            if (success) {
                                showPostFlashDialog = true
                            } else {
                                Toast.makeText(context, "Flashing gagal: $message", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                ) {
                    Text("Tulis Partisi Sekarang", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showConfirmFlashDialog = false },
                    border = BorderStroke(0.8.dp, BorderSubtle)
                ) {
                    Text("Batal", color = TextSecondary)
                }
            },
            containerColor = BgCard,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // ── Dialog Pasca-Flash Sukses ─────────────────────────────────────────────
    if (showPostFlashDialog) {
        AlertDialog(
            onDismissRequest = { showPostFlashDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentGreen)
                    Text("Flashing Berhasil!", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(
                    "Partisi hardware telah berhasil diperbarui dan disinkronkan ke flash memory. Disarankan untuk me-reboot perangkat agar kernel atau citra baru aktif.",
                    color = TextSecondary, fontSize = 13.sp, lineHeight = 17.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPostFlashDialog = false
                        FlasherManager.rebootDevice("system")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                ) {
                    Text("Reboot ke Sistem", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            showPostFlashDialog = false
                            FlasherManager.rebootDevice("recovery")
                        },
                        border = BorderStroke(0.8.dp, AccentOrange.copy(alpha = 0.5f))
                    ) {
                        Text("Recovery", color = AccentOrange)
                    }
                    TextButton(onClick = { showPostFlashDialog = false }) {
                        Text("Tutup", color = TextSecondary)
                    }
                }
            },
            containerColor = BgCard,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // ── Dialog Pemilihan Cadangan Baru ────────────────────────────────────────
    if (showBackupDialog) {
        var selectedBackupTarget by remember { mutableStateOf(PartitionTarget.BOOT) }
        var isBackingUpNow by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isBackingUpNow) showBackupDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Save, contentDescription = null, tint = AccentBlue)
                    Text("Buat Cadangan Partisi", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Pilih partisi fisik yang ingin Anda cadangkan ke berkas .img di folder /sdcard/Lynx/backups/.",
                        color = TextSecondary, fontSize = 12.5.sp
                    )

                    PartitionTarget.values().take(4).forEach { target ->
                        val isSel = selectedBackupTarget == target
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSel) AccentBlue.copy(alpha = 0.15f) else BgElevated,
                            border = BorderStroke(0.8.dp, if (isSel) AccentBlue else BorderGlass),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedBackupTarget = target }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(target.title, color = if (isSel) AccentBlue else TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                    Text(target.desc, color = TextSecondary, fontSize = 10.sp)
                                }
                                if (isSel) {
                                    Icon(Icons.Default.Check, null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    if (isBackingUpNow) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AccentBlue, strokeWidth = 2.dp)
                            Text("Mencadangkan partisi...", color = AccentBlue, fontSize = 11.5.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isBackingUpNow = true
                        scope.launch {
                            val (ok, msg) = FlasherManager.createPartitionBackup(selectedBackupTarget)
                            isBackingUpNow = false
                            showBackupDialog = false
                            refreshDataInternal()
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isBackingUpNow,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                ) {
                    Text("Cadangkan Sekarang", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showBackupDialog = false },
                    enabled = !isBackingUpNow,
                    border = BorderStroke(0.8.dp, BorderSubtle)
                ) {
                    Text("Batal", color = TextSecondary)
                }
            },
            containerColor = BgCard,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // ── Dialog Pulihkan Cadangan (Restore) ────────────────────────────────────
    if (backupToRestore != null) {
        val b = backupToRestore!!
        AlertDialog(
            onDismissRequest = { backupToRestore = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Restore, contentDescription = null, tint = AccentOrange)
                    Text("Pulihkan Cadangan Partisi?", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Partisi fisik aktif '${b.partitionType.title}' akan ditimpa dengan berkas cadangan ini. Pastikan Anda ingin memulihkan kernel tersebut.",
                        color = TextSecondary, fontSize = 12.5.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BgElevated,
                        border = BorderStroke(0.8.dp, BorderGlass),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Berkas: ${b.name}", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                            Text("Ukuran: ${"%.1f".format(b.sizeMb)} MB  •  Dibuat: ${b.dateFormatted}", fontSize = 10.5.sp, color = TextSecondary)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val path = b.path
                        val target = b.partitionType
                        backupToRestore = null
                        isFlashing = true
                        terminalLogs = listOf("Memulai pemulihan partisi dari cadangan...")

                        scope.launch {
                            val (success, msg) = FlasherManager.restoreBackup(
                                backupPath = path,
                                target = target,
                                onLog = { line -> terminalLogs = terminalLogs + line }
                            )
                            isFlashing = false
                            reloadAllData()
                            if (success) {
                                showPostFlashDialog = true
                            } else {
                                Toast.makeText(context, "Gagal memulihkan: $msg", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
                ) {
                    Text("Pulihkan Sekarang", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { backupToRestore = null },
                    border = BorderStroke(0.8.dp, BorderSubtle)
                ) {
                    Text("Batal", color = TextSecondary)
                }
            },
            containerColor = BgCard,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // ── Dialog Hapus Cadangan ────────────────────────────────────────────────
    if (backupToDelete != null) {
        val b = backupToDelete!!
        AlertDialog(
            onDismissRequest = { backupToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = AccentRed)
                    Text("Hapus Cadangan?", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(
                    "Berkas cadangan '${b.name}' (${"%.1f".format(b.sizeMb)} MB) akan dihapus permanen dari penyimpanan perangkat.",
                    color = TextSecondary, fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val path = b.path
                        backupToDelete = null
                        scope.launch {
                            val ok = FlasherManager.deleteBackup(path)
                            refreshDataInternal()
                            if (ok) {
                                Toast.makeText(context, "Cadangan dihapus.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                ) {
                    Text("Hapus", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { backupToDelete = null },
                    border = BorderStroke(0.8.dp, BorderSubtle)
                ) {
                    Text("Batal", color = TextSecondary)
                }
            },
            containerColor = BgCard,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // ── Dialog Reset Kernel Stock ────────────────────────────────────────────
    if (showResetStockConfirm) {
        AlertDialog(
            onDismissRequest = { showResetStockConfirm = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Restore, contentDescription = null, tint = AccentGreen)
                    Text("Reset Kernel ke Standar OEM?", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(
                    "Semua parameter CPU, GPU, governor, thermal, dan I/O tuning akan dikembalikan secara aman ke konfigurasi bawaan pabrik OEM tanpa memodifikasi partisi flash.",
                    color = TextSecondary, fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetKernelToStock()
                        showResetStockConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                ) {
                    Text("Reset Aman", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showResetStockConfirm = false },
                    border = BorderStroke(0.8.dp, BorderSubtle)
                ) {
                    Text("Batal", color = TextSecondary)
                }
            },
            containerColor = BgCard,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

// ============================================================================
// 1. Hero Active Slot & Hardware Partition Inspector Card
// ============================================================================
@Composable
private fun ActiveKernelSlotHeroCard(
    activeSlot: String,
    partitions: List<PartitionBlockInfo>,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = BgCard,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AccentCyan.copy(alpha = 0.15f))
                            .border(BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.4f)), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Memory, null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text("INSPEKTOR PARTISI HARDWARE", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AccentCyan, letterSpacing = 0.5.sp)
                        Text("Status Slot & Tabel Blok Fisik", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                }

                // Slot Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentGreen.copy(alpha = 0.15f),
                    border = BorderStroke(0.8.dp, AccentGreen.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = if (activeSlot.isNotBlank()) "SLOT ${activeSlot.replace("_", "").uppercase()} (AKTIF)" else "NON-A/B",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Status Shield
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderGlass),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Shield, null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                    Text(
                        "Auto Pre-Flash Backup Terpasang: Partisi lama selalu dicadangkan otomatis sebelum proses penulisan blok dimulai.",
                        fontSize = 10.5.sp,
                        color = TextSecondary,
                        lineHeight = 14.sp
                    )
                }
            }

            // Grid Partisi Terdeteksi
            if (isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AccentCyan, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Memindai tabel partisi kernel...", color = TextSecondary, fontSize = 11.5.sp)
                }
            } else if (partitions.isEmpty()) {
                Text("Tidak ada partisi boot yang terdeteksi.", color = TextSecondary, fontSize = 11.sp)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    partitions.forEach { part ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BgElevated,
                            border = BorderStroke(0.7.dp, BorderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = when (part.target) {
                                            PartitionTarget.BOOT -> Icons.Default.Bolt
                                            PartitionTarget.VENDOR_BOOT -> Icons.Default.SettingsApplications
                                            PartitionTarget.DTBO -> Icons.Default.DeviceHub
                                            else -> Icons.Default.Storage
                                        },
                                        contentDescription = null,
                                        tint = when (part.target) {
                                            PartitionTarget.BOOT -> AccentCyan
                                            PartitionTarget.VENDOR_BOOT -> AccentBlue
                                            PartitionTarget.DTBO -> AccentOrange
                                            else -> AccentGreen
                                        },
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column {
                                        Text(part.target.title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(part.nodePath, color = TextTertiary, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.White.copy(alpha = 0.06f),
                                    border = BorderStroke(0.6.dp, BorderGlass)
                                ) {
                                    Text(
                                        text = "${"%.1f".format(part.sizeMb)} MB",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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

// ============================================================================
// 2. Universal Kernel & Image Flasher Studio Card
// ============================================================================
@Composable
private fun UniversalFlasherStudioCard(
    selectedFile: DiscoveredFlashFile?,
    discoveredFiles: List<DiscoveredFlashFile>,
    validationResult: ImageValidationResult?,
    isValidating: Boolean,
    selectedTargetPartition: PartitionTarget,
    autoBackupEnabled: Boolean,
    isFlashing: Boolean,
    terminalLogs: List<String>,
    terminalScrollState: androidx.compose.foundation.ScrollState,
    onSelectDiscoveredFile: (DiscoveredFlashFile) -> Unit,
    onPickFile: () -> Unit,
    onClearSelectedFile: () -> Unit,
    onSelectTargetPartition: (PartitionTarget) -> Unit,
    onToggleAutoBackup: (Boolean) -> Unit,
    onRequestFlash: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = BgCard,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AccentCyan.copy(alpha = 0.15f))
                            .border(BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.4f)), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Bolt, null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text("UNIVERSAL KERNEL FLASHER", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AccentCyan, letterSpacing = 0.5.sp)
                        Text("AnyKernel3 (.ZIP) & Raw Image (.IMG)", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                }
            }

            Text(
                text = "Pilih berkas kernel .ZIP untuk diekstrak langsung via AnyKernel3, atau berkas .IMG untuk dituliskan secara presisi ke partisi boot target.",
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 15.sp
            )

            // Tombol Utama File Picker
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPickFile()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BgElevated),
                border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f))
            ) {
                Icon(Icons.Default.FolderOpen, null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Pilih Berkas Kernel (.ZIP / .IMG)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            // Quick Carousel Berkas Terdeteksi di /sdcard/Download
            if (discoveredFiles.isNotEmpty() && selectedFile == null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "BERKAS TERDETEKSI DI /SDCARD/DOWNLOAD",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextTertiary,
                        letterSpacing = 0.8.sp
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(discoveredFiles.take(8)) { f ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = BgElevated,
                                border = BorderStroke(0.7.dp, if (f.isImg) AccentCyan.copy(alpha = 0.4f) else AccentBlue.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .width(170.dp)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onSelectDiscoveredFile(f)
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            if (f.isImg) Icons.Default.InsertDriveFile else Icons.Default.FolderZip,
                                            null,
                                            tint = if (f.isImg) AccentCyan else AccentBlue,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (f.isImg) AccentCyan.copy(alpha = 0.15f) else AccentBlue.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = if (f.isImg) "IMG" else "ZIP",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (f.isImg) AccentCyan else AccentBlue,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = f.name,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${"%.1f".format(f.sizeMb)} MB",
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bento Box Berkas Terpilih & Validasi
            if (selectedFile != null) {
                val f = selectedFile
                val v = validationResult

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = BgElevated,
                    border = BorderStroke(1.dp, if (v?.isValid == true) AccentCyan.copy(alpha = 0.5f) else AccentOrange.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Header Berkas Terpilih
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(f.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${"%.2f".format(f.sizeMb)} MB  •  ${if (f.isZip) "Paket AnyKernel3 ZIP" else "Citra Mentah Partisi IMG"}", color = TextSecondary, fontSize = 10.5.sp)
                            }
                            IconButton(onClick = onClearSelectedFile, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, "Batal Pilih", tint = TextTertiary, modifier = Modifier.size(18.dp))
                            }
                        }

                        // Target Partition Selector (Khusus .IMG)
                        if (f.isImg) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("PILIH PARTISI TARGET PENULISAN:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextTertiary, letterSpacing = 0.5.sp)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(PartitionTarget.BOOT, PartitionTarget.VENDOR_BOOT, PartitionTarget.DTBO).forEach { part ->
                                        val isSel = selectedTargetPartition == part
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSel) AccentCyan.copy(alpha = 0.2f) else BgCard,
                                            border = BorderStroke(0.8.dp, if (isSel) AccentCyan else BorderGlass),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { onSelectTargetPartition(part) }
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(vertical = 6.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(part.title, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal, color = if (isSel) AccentCyan else TextSecondary)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Status Validasi Forensik
                        if (isValidating) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), color = AccentCyan, strokeWidth = 1.5.dp)
                                Text("Memeriksa integritas berkas & ukuran partisi...", color = TextSecondary, fontSize = 11.sp)
                            }
                        } else if (v != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                // SHA-256 Checksum Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("SHA-256: ${v.sha256.take(16)}...", fontSize = 10.sp, color = TextTertiary, fontFamily = FontFamily.Monospace)
                                    Text(
                                        "Salin Hash",
                                        fontSize = 10.sp,
                                        color = AccentCyan,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable {
                                            clipboard.setText(AnnotatedString(v.sha256))
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    )
                                }

                                // Safety Checks
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        if (v.isSizeSafe) Icons.Default.CheckCircle else Icons.Default.Error,
                                        null,
                                        tint = if (v.isSizeSafe) AccentGreen else AccentRed,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        if (v.isSizeSafe) "Kapasitas partisi target aman (${"%.1f".format(v.targetPartitionSizeMb)} MB)" else "Ukuran berkas melebihi kapasitas partisi!",
                                        fontSize = 11.sp,
                                        color = if (v.isSizeSafe) AccentGreen else AccentRed
                                    )
                                }

                                if (f.isImg && v.magicHeader.isNotBlank()) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        val isMagicOk = (v.targetPartition == PartitionTarget.BOOT && v.magicHeader.startsWith("ANDROID!")) ||
                                                (v.targetPartition == PartitionTarget.VENDOR_BOOT && v.magicHeader.startsWith("VNDRBOOT")) ||
                                                (v.targetPartition == PartitionTarget.DTBO)
                                        Icon(
                                            if (isMagicOk) Icons.Default.CheckCircle else Icons.Default.Warning,
                                            null,
                                            tint = if (isMagicOk) AccentGreen else AccentOrange,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            "Header Magic: '${v.magicHeader.take(8)}' (${if (isMagicOk) "Valid" else "Non-Standar"})",
                                            fontSize = 11.sp,
                                            color = if (isMagicOk) AccentGreen else AccentOrange
                                        )
                                    }
                                }
                            }
                        }

                        // Toggle Auto Pre-Flash Backup
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Auto Pre-Flash Backup", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text("Cadangkan partisi aktif secara otomatis sebelum penulisan", fontSize = 10.sp, color = TextSecondary)
                            }
                            Switch(
                                checked = autoBackupEnabled,
                                onCheckedChange = onToggleAutoBackup,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AccentGreen,
                                    uncheckedTrackColor = BgCard
                                )
                            )
                        }

                        // Tombol Eksekusi Flash
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onRequestFlash()
                            },
                            enabled = !isFlashing && (validationResult?.isValid == true),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                        ) {
                            if (isFlashing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Sedang Menulis Partisi...", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            } else {
                                Icon(Icons.Default.Bolt, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (f.isZip) "Flash Kernel AnyKernel3 (.ZIP)" else "Flash Citra Partisi (.IMG)",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            // Terminal Flashing Output Console
            if (terminalLogs.isNotEmpty() || isFlashing) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("LOG TERMINAL FLASHING REAL-TIME", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextTertiary, letterSpacing = 0.5.sp)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF07090E),
                        border = BorderStroke(1.dp, BorderGlass),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 220.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .verticalScroll(terminalScrollState)
                        ) {
                            terminalLogs.forEach { logLine ->
                                Text(
                                    text = logLine,
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = when {
                                        logLine.contains("BERHASIL") || logLine.contains("[OK]") -> AccentGreen
                                        logLine.contains("ERROR") || logLine.contains("DIBATALKAN") || logLine.contains("Error:") -> AccentRed
                                        logLine.contains("Peringatan:") || logLine.contains("Warning:") -> AccentOrange
                                        else -> AccentCyan
                                    },
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 3. Vault Cadangan & Restorasi Partisi Boot Card
// ============================================================================
@Composable
private fun BootBackupVaultCard(
    savedBackups: List<SavedBootBackup>,
    isLoading: Boolean,
    onCreateBackup: () -> Unit,
    onRestore: (SavedBootBackup) -> Unit,
    onDelete: (SavedBootBackup) -> Unit,
    onShare: (SavedBootBackup) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = BgCard,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AccentBlue.copy(alpha = 0.15f))
                            .border(BorderStroke(0.8.dp, AccentBlue.copy(alpha = 0.4f)), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Storage, null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text("VAULT CADANGAN & RESTORASI", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AccentBlue, letterSpacing = 0.5.sp)
                        Text("Citra Boot (.IMG) Tersimpan", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                }

                // Badge Jumlah Backup
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentBlue.copy(alpha = 0.15f),
                    border = BorderStroke(0.8.dp, AccentBlue.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "${savedBackups.size} Cadangan",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentBlue,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "Seluruh cadangan partisi disimpan di folder /sdcard/Lynx/backups/. Anda dapat memulihkan (restore) citra kernel kapan saja secara langsung tanpa PC.",
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 15.sp
            )

            // Tombol Buat Cadangan Baru
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCreateBackup()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Icon(Icons.Default.Save, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Buat Cadangan Partisi Baru", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            // List Berkas Cadangan
            if (isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = AccentBlue, strokeWidth = 1.5.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Memuat daftar cadangan...", color = TextSecondary, fontSize = 11.sp)
                }
            } else if (savedBackups.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgElevated,
                    border = BorderStroke(0.6.dp, BorderGlass),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, null, tint = TextTertiary, modifier = Modifier.size(28.dp))
                        Text("Belum ada cadangan partisi tersimpan.", fontSize = 12.sp, color = TextSecondary)
                        Text("Cadangan otomatis dibuat saat Anda melakukan flashing kernel baru.", fontSize = 10.5.sp, color = TextTertiary, textAlign = TextAlign.Center)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    savedBackups.forEach { b ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BgElevated,
                            border = BorderStroke(0.7.dp, BorderSubtle),
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
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = when (b.partitionType) {
                                                PartitionTarget.BOOT -> AccentCyan.copy(alpha = 0.15f)
                                                PartitionTarget.VENDOR_BOOT -> AccentBlue.copy(alpha = 0.15f)
                                                PartitionTarget.DTBO -> AccentOrange.copy(alpha = 0.15f)
                                                else -> AccentGreen.copy(alpha = 0.15f)
                                            }
                                        ) {
                                            Text(
                                                text = b.partitionType.title.uppercase(),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = when (b.partitionType) {
                                                    PartitionTarget.BOOT -> AccentCyan
                                                    PartitionTarget.VENDOR_BOOT -> AccentBlue
                                                    PartitionTarget.DTBO -> AccentOrange
                                                    else -> AccentGreen
                                                },
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Column {
                                            Text(b.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("${"%.1f".format(b.sizeMb)} MB  •  ${b.dateFormatted}", color = TextSecondary, fontSize = 10.sp)
                                        }
                                    }

                                    // Tombol Aksi Hapus
                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onDelete(b)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, "Hapus", tint = AccentRed, modifier = Modifier.size(16.dp))
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Tombol Restore
                                    Button(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onRestore(b)
                                        },
                                        modifier = Modifier.weight(1f).height(38.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
                                    ) {
                                        Icon(Icons.Default.Restore, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Pulihkan (Restore)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }

                                    // Tombol Bagikan
                                    OutlinedButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onShare(b)
                                        },
                                        modifier = Modifier.height(38.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(0.8.dp, BorderGlass)
                                    ) {
                                        Icon(Icons.Default.Share, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 4. Emergency & Disaster Recovery Tools Card
// ============================================================================
@Composable
private fun FlasherSafetyToolsCard(
    onReboot: (String) -> Unit,
    onRequestResetStock: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = BgCard,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AccentGreen.copy(alpha = 0.15f))
                        .border(BorderStroke(0.8.dp, AccentGreen.copy(alpha = 0.4f)), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Security, null, tint = AccentGreen, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text("ALAT PEMULIHAN & REBOOT", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AccentGreen, letterSpacing = 0.5.sp)
                    Text("Reboot Cepat & Restorasi Kernel OEM", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            }

            Text(
                text = "Pintasan cepat untuk me-reboot perangkat ke sistem, recovery mode (TWRP/OrangeFox), atau bootloader/fastboot, serta mengembalikan seluruh tuning sysfs kernel ke standar bawaan pabrik OEM.",
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 15.sp
            )

            // Grid Tombol Reboot
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onReboot("system")
                    },
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BgElevated),
                    border = BorderStroke(0.8.dp, BorderSubtle)
                ) {
                    Icon(Icons.Default.Refresh, null, tint = TextPrimary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reboot", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onReboot("recovery")
                    },
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BgElevated),
                    border = BorderStroke(0.8.dp, AccentOrange.copy(alpha = 0.4f))
                ) {
                    Icon(Icons.Default.Build, null, tint = AccentOrange, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Recovery", fontSize = 11.sp, color = AccentOrange, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onReboot("bootloader")
                    },
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BgElevated),
                    border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.4f))
                ) {
                    Icon(Icons.Default.Usb, null, tint = AccentCyan, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Fastboot", fontSize = 11.sp, color = AccentCyan, fontWeight = FontWeight.Bold)
                }
            }

            // Tombol Reset Kernel Stock Safe
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onRequestResetStock()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen.copy(alpha = 0.2f)),
                border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.RestartAlt, null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reset Aman Tuning Kernel ke Bawaan (Stock Safe)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
            }
        }
    }
}
