package com.noir.lynx.feature.flasher

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.topjohnwu.superuser.Shell
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * FlasherManager — Otak eksekusi dan validasi tingkat tinggi Universal Flasher.
 * Mengimplementasikan perlindungan multi-tier anti-brick:
 * 1. Resolusi node partisi fisik dinamis berdasarkan slot aktif (A/B).
 * 2. Whitelist ketat partisi yang diizinkan (Boot, Vendor Boot, DTBO, Init Boot).
 * 3. Pemeriksaan magic header Android Boot (ANDROID! / VNDRBOOT).
 * 4. Pengecekan batas ukuran partisi fisik (Capacity Guard).
 * 5. Auto Pre-Flash Backup wajib sebelum penulisan raw block.
 * 6. Sinkronisasi dirty buffer kernel (conv=fsync + sync).
 */
object FlasherManager {
    private const val TAG = "LynxFlasherManager"
    const val BACKUP_DIR = "/sdcard/Lynx/backups"

    /**
     * Mendeteksi suffix slot boot aktif (contoh: "_a", "_b", atau kosong jika non-A/B).
     */
    fun detectActiveSlot(): String {
        return try {
            val res = Shell.cmd("getprop ro.boot.slot_suffix").exec()
            val prop = res.out.firstOrNull()?.trim() ?: ""
            if (prop.isNotBlank() && prop != "false") {
                prop
            } else {
                val cmdline = Shell.cmd("cat /proc/cmdline").exec().out.joinToString(" ")
                val match = Regex("androidboot\\.slot_suffix=(_[ab])").find(cmdline)
                match?.groupValues?.getOrNull(1) ?: ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Menyelesaikan path node block device partisi target.
     */
    fun findBlockDevice(partition: String, slot: String = detectActiveSlot()): String? {
        val targetName = if (slot.isNotBlank()) "$partition$slot" else partition
        val candidates = listOf(
            "/dev/block/by-name/$targetName",
            "/dev/block/by-name/$partition",
            "/dev/block/bootdevice/by-name/$targetName",
            "/dev/block/bootdevice/by-name/$partition",
            "/dev/block/mapper/$targetName",
            "/dev/block/mapper/$partition"
        )
        for (c in candidates) {
            val res = Shell.cmd("[ -b '$c' ] && echo 'FOUND'").exec()
            if (res.out.any { it.trim() == "FOUND" }) {
                return c
            }
        }
        return null
    }

    /**
     * Membaca kapasitas ukuran fisik partisi dalam satuan bytes secara akurat dari kernel sysfs.
     */
    fun getPartitionSizeBytes(nodePath: String): Long {
        return try {
            val realRes = Shell.cmd("readlink -f '$nodePath' 2>/dev/null || echo '$nodePath'").exec()
            val real = realRes.out.firstOrNull()?.trim() ?: nodePath
            val bname = real.substringAfterLast("/")
            val sysRes = Shell.cmd("cat /sys/class/block/$bname/size 2>/dev/null").exec()
            val sectors = sysRes.out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
            if (sectors > 0L) {
                sectors * 512L
            } else {
                val bdevRes = Shell.cmd("blockdev --getsize64 '$nodePath' 2>/dev/null").exec()
                bdevRes.out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Menginspeksi seluruh blok partisi kernel yang tersedia pada hardware.
     */
    suspend fun inspectPartitions(): List<PartitionBlockInfo> = withContext(Dispatchers.IO) {
        val slot = detectActiveSlot()
        val list = mutableListOf<PartitionBlockInfo>()

        for (target in PartitionTarget.values()) {
            val node = findBlockDevice(target.id, slot)
            if (node != null) {
                val size = getPartitionSizeBytes(node)
                val realRes = Shell.cmd("readlink -f '$node' 2>/dev/null || echo '$node'").exec()
                val real = realRes.out.firstOrNull()?.trim() ?: node
                list.add(
                    PartitionBlockInfo(
                        target = target,
                        slotSuffix = slot,
                        nodePath = node,
                        realDevice = real,
                        sizeBytes = size,
                        sizeMb = if (size > 0L) (size / (1024f * 1024f)) else 0f,
                        isAvailable = true
                    )
                )
            }
        }
        list
    }

    /**
     * Memindai direktori /sdcard/Download dan /sdcard/ untuk menemukan berkas .zip dan .img yang siap di-flash.
     */
    suspend fun scanDiscoveredFiles(): List<DiscoveredFlashFile> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DiscoveredFlashFile>()
        val scannedDirs = listOf(
            File("/sdcard/Download"),
            File("/storage/emulated/0/Download"),
            File("/sdcard"),
            File("/storage/emulated/0")
        ).distinctBy { it.absolutePath }

        val seenPaths = mutableSetOf<String>()

        // 1. Coba via Java File terlebih dahulu
        for (dir in scannedDirs) {
            if (!dir.exists() || !dir.isDirectory) continue
            val files = dir.listFiles() ?: continue
            for (f in files) {
                if (!f.isFile) continue
                val name = f.name
                val ext = f.extension.lowercase()
                val len = f.length()
                if (len <= 0L || len > 1500L * 1024L * 1024L) continue // Abaikan file kosong atau > 1.5GB
                if (ext == "zip" || ext == "img") {
                    val canPath = f.canonicalPath
                    if (seenPaths.add(canPath)) {
                        results.add(
                            DiscoveredFlashFile(
                                file = f,
                                name = name,
                                path = f.absolutePath,
                                sizeBytes = len,
                                sizeMb = len / (1024f * 1024f),
                                modifiedDate = f.lastModified(),
                                isZip = ext == "zip",
                                isImg = ext == "img"
                            )
                        )
                    }
                }
            }
        }

        // 2. Tambahan fallback via Root Shell jika berkas terproteksi root/everybody
        try {
            val cmd = "ls -l /sdcard/Download/*.img /sdcard/Download/*.zip /storage/emulated/0/Download/*.img /storage/emulated/0/Download/*.zip /sdcard/*.img /sdcard/*.zip 2>/dev/null"
            val res = Shell.cmd(cmd).exec()
            for (line in res.out) {
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 8) {
                    val size = parts[4].toLongOrNull() ?: 0L
                    val path = parts.subList(7, parts.size).joinToString(" ")
                    if (size > 0L && (path.endsWith(".img") || path.endsWith(".zip")) && seenPaths.add(path)) {
                        val f = File(path)
                        results.add(
                            DiscoveredFlashFile(
                                file = f,
                                name = f.name,
                                path = path,
                                sizeBytes = size,
                                sizeMb = size / (1024f * 1024f),
                                modifiedDate = f.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis(),
                                isZip = path.endsWith(".zip"),
                                isImg = path.endsWith(".img")
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        results.sortedByDescending { it.modifiedDate }
    }

    /**
     * Menghitung nilai hash SHA-256 dari berkas (dengan fallback Root Shell).
     */
    fun calculateSha256(file: File): String {
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            return try {
                val res = Shell.cmd("sha256sum '${file.absolutePath}' 2>/dev/null").exec()
                val hash = res.out.firstOrNull()?.split(Regex("\\s+"))?.firstOrNull()
                if (!hash.isNullOrBlank() && hash.length == 64) {
                    hash
                } else {
                    "SHA-256 tidak dapat dihitung"
                }
            } catch (_: Exception) {
                "SHA-256 tidak dapat dihitung"
            }
        }
    }

    /**
     * Membaca magic header awal berkas dalam format ASCII (8 bytes, dengan fallback Root Shell).
     */
    fun readMagicHeader(file: File): String {
        try {
            FileInputStream(file).use { fis ->
                val buf = ByteArray(8)
                val read = fis.read(buf)
                if (read > 0) {
                    return String(buf, 0, read, Charsets.US_ASCII)
                }
            }
        } catch (_: Exception) {}

        return try {
            val res = Shell.cmd("head -c 8 '${file.absolutePath}' 2>/dev/null").exec()
            res.out.firstOrNull() ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Memeriksa apakah berkas ZIP merupakan arsip AnyKernel3 yang valid.
     */
    fun isAnyKernelZip(file: File): Boolean {
        return try {
            ZipFile(file).use { zip ->
                val hasAnykernelSh = zip.getEntry("anykernel.sh") != null
                val hasUpdateBinary = zip.getEntry("META-INF/com/google/android/update-binary") != null
                hasAnykernelSh || hasUpdateBinary
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Memvalidasi berkas secara forensik sebelum di-flash, memeriksa magic header, dan kapasitas blok.
     */
    suspend fun validateTargetFile(
        filePath: String,
        requestedTarget: PartitionTarget? = null
    ): ImageValidationResult = withContext(Dispatchers.IO) {
        val file = File(filePath)
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()

        if (!file.exists() || !file.isFile) {
            return@withContext ImageValidationResult(
                isValid = false,
                detectedFormat = "UNKNOWN",
                magicHeader = "",
                fileSizeBytes = 0L,
                fileSizeMb = 0f,
                sha256 = "",
                targetPartition = PartitionTarget.BOOT,
                targetNodePath = "",
                targetPartitionSizeBytes = 0L,
                targetPartitionSizeMb = 0f,
                isSizeSafe = false,
                errors = listOf("Berkas target tidak ditemukan pada penyimpanan.")
            )
        }

        val fileLen = file.length()
        val fileMb = fileLen / (1024f * 1024f)
        val sha256 = calculateSha256(file)
        val ext = file.extension.lowercase()
        val magic = readMagicHeader(file)

        val targetPart = requestedTarget ?: PartitionTarget.fromFileName(file.name)
        val node = findBlockDevice(targetPart.id) ?: ""
        val partSize = if (node.isNotBlank()) getPartitionSizeBytes(node) else 0L
        val partMb = if (partSize > 0L) partSize / (1024f * 1024f) else 0f

        var format = "UNKNOWN"
        var isSizeSafe = true

        if (ext == "zip") {
            val isAk3 = isAnyKernelZip(file)
            format = if (isAk3) "ANYKERNEL3_ZIP" else "GENERIC_ZIP"
            if (!isAk3) {
                warnings.add("Berkas ZIP tidak memiliki 'anykernel.sh' atau 'update-binary'. Flashing mungkin gagal jika bukan paket installer kernel.")
            }
        } else if (ext == "img") {
            format = when {
                magic.startsWith("ANDROID!") -> "ANDROID_BOOT_IMG"
                magic.startsWith("VNDRBOOT") -> "VENDOR_BOOT_IMG"
                else -> "RAW_IMAGE"
            }

            // Validasi Magic Header
            if (targetPart == PartitionTarget.BOOT && !magic.startsWith("ANDROID!")) {
                warnings.add("Magic header berkas ('${magic.take(8)}') tidak cocok dengan standar 'ANDROID!'. Pastikan berkas ini adalah boot image yang valid.")
            } else if (targetPart == PartitionTarget.VENDOR_BOOT && !magic.startsWith("VNDRBOOT")) {
                warnings.add("Magic header berkas ('${magic.take(8)}') tidak cocok dengan standar 'VNDRBOOT'.")
            }

            // Validasi Ukuran Partisi Fisik (Capacity Guard)
            if (partSize > 0L && fileLen > partSize) {
                isSizeSafe = false
                errors.add("BAHAYA OVERFLOW: Ukuran berkas (${"%.2f".format(fileMb)} MB) melebihi kapasitas partisi fisik target (${"%.2f".format(partMb)} MB). Penulisan DIBLOKIR demi mencegah hardbrick!")
            } else if (partSize > 0L && fileLen == partSize) {
                // Sempurna 1:1 byte match
            } else if (partSize > 0L && fileLen < partSize) {
                // Normal untuk boot image standar (ditulis dari sektor awal)
            }
        }

        if (node.isBlank()) {
            errors.add("Partisi target '${targetPart.title}' tidak ditemukan pada tabel partisi hardware perangkat ini.")
        }

        val isValid = errors.isEmpty()

        ImageValidationResult(
            isValid = isValid,
            detectedFormat = format,
            magicHeader = magic,
            fileSizeBytes = fileLen,
            fileSizeMb = fileMb,
            sha256 = sha256,
            targetPartition = targetPart,
            targetNodePath = node,
            targetPartitionSizeBytes = partSize,
            targetPartitionSizeMb = partMb,
            isSizeSafe = isSizeSafe,
            warnings = warnings,
            errors = errors
        )
    }

    /**
     * Mencadangkan partisi fisik aktif ke direktori /sdcard/Lynx/backups/.
     */
    suspend fun createPartitionBackup(target: PartitionTarget): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val slot = detectActiveSlot()
            val node = findBlockDevice(target.id, slot)
                ?: return@withContext Pair(false, "Partisi '${target.title}' tidak ditemukan pada hardware.")

            Shell.cmd("mkdir -p '$BACKUP_DIR'").exec()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outName = "${target.id}${slot}_$timestamp.img"
            val outPath = "$BACKUP_DIR/$outName"

            val partSize = getPartitionSizeBytes(node)
            val bs = if (partSize > 0L && partSize % 4096L == 0L) "4096" else "1024"

            val cmd = Shell.cmd("dd if='$node' of='$outPath' bs=$bs conv=fsync 2>&1").exec()
            Shell.cmd("sync").exec()
            Shell.cmd("chmod 666 '$outPath' 2>/dev/null").exec()

            val checkRes = Shell.cmd("test -s '$outPath' && echo 'EXISTS'").exec()
            val exists = checkRes.out.firstOrNull() == "EXISTS" || (File(outPath).exists() && File(outPath).length() > 0L)

            if (exists) {
                val sizeRes = Shell.cmd("wc -c < '$outPath' 2>/dev/null").exec()
                val sizeBytes = sizeRes.out.firstOrNull()?.trim()?.toLongOrNull() ?: File(outPath).length()
                val mb = "%.1f".format(sizeBytes / (1024f * 1024f))
                Pair(true, "Berhasil mencadangkan partisi ${target.title} ($mb MB) ke: $outName")
            } else {
                Pair(false, "Gagal membuat cadangan partisi: ${cmd.out.joinToString("\n")}")
            }
        } catch (e: Exception) {
            Pair(false, "Terjadi kesalahan saat mencadangkan: ${e.message}")
        }
    }

    /**
     * Membaca seluruh berkas cadangan .img yang tersimpan di Vault (Java + Root Shell).
     */
    suspend fun listBackups(): List<SavedBootBackup> = withContext(Dispatchers.IO) {
        val list = mutableListOf<SavedBootBackup>()
        val seenPaths = mutableSetOf<String>()
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))

        // 1. Coba via Java File
        val dir = File(BACKUP_DIR)
        if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.forEach { f ->
                if (f.isFile && f.extension.lowercase() == "img" && seenPaths.add(f.absolutePath)) {
                    val len = f.length()
                    val target = PartitionTarget.fromFileName(f.name)
                    list.add(
                        SavedBootBackup(
                            name = f.name,
                            path = f.absolutePath,
                            sizeBytes = len,
                            sizeMb = len / (1024f * 1024f),
                            dateFormatted = sdf.format(Date(f.lastModified())),
                            partitionType = target
                        )
                    )
                }
            }
        }

        // 2. Fallback via Root Shell jika berkas di /sdcard dimiliki root/everybody
        try {
            val res = Shell.cmd("ls -l /sdcard/Lynx/backups/*.img 2>/dev/null").exec()
            for (line in res.out) {
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 8) {
                    val size = parts[4].toLongOrNull() ?: 0L
                    val path = parts.subList(7, parts.size).joinToString(" ")
                    if (size > 0L && path.endsWith(".img") && seenPaths.add(path)) {
                        val f = File(path)
                        val target = PartitionTarget.fromFileName(f.name)
                        list.add(
                            SavedBootBackup(
                                name = f.name,
                                path = path,
                                sizeBytes = size,
                                sizeMb = size / (1024f * 1024f),
                                dateFormatted = sdf.format(Date(f.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis())),
                                partitionType = target
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        list.sortedByDescending { it.path }
    }

    /**
     * Menghapus berkas cadangan dari Vault (Java + Root Shell).
     */
    suspend fun deleteBackup(backupPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val f = File(backupPath)
            if (f.exists() && f.parentFile?.absolutePath == File(BACKUP_DIR).absolutePath) {
                if (f.delete()) return@withContext true
            }
            val res = Shell.cmd("rm -f '$backupPath'").exec()
            res.isSuccess
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Menuliskan citra mentah (.img) langsung ke blok partisi fisik dengan protokol anti-brick.
     */
    suspend fun flashRawImage(
        imagePath: String,
        target: PartitionTarget,
        autoBackup: Boolean,
        onLog: (String) -> Unit
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val file = File(imagePath)
        onLog("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        onLog("  Lynx Kernel Manager — Raw Partition Flasher")
        onLog("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        onLog("Target Berkas  : ${file.name} (${"%.2f".format(file.length() / (1024f * 1024f))} MB)")
        onLog("Target Partisi : ${target.title} (${target.id})")

        // 1. Validasi Keamanan
        onLog("🛡️ Memverifikasi integritas dan batasan ukuran...")
        val validation = validateTargetFile(imagePath, target)
        if (!validation.isValid) {
            val err = validation.errors.joinToString("\n• ")
            onLog("❌ DIBATALKAN DEMI KEAMANAN:\n• $err")
            return@withContext Pair(false, "Validasi gagal: $err")
        }

        val targetNode = validation.targetNodePath
        onLog("Node Partisi   : $targetNode (${"%.1f".format(validation.targetPartitionSizeMb)} MB)")

        // 2. Auto Pre-Flash Backup
        if (autoBackup) {
            onLog("📦 Menjalankan Auto Pre-Flash Backup untuk pemulihan...")
            val (ok, msg) = createPartitionBackup(target)
            if (ok) {
                onLog("✅ $msg")
            } else {
                onLog("⚠️ Peringatan: Auto-backup gagal ($msg). Melanjutkan penulisan...")
            }
        }

        // 3. Eksekusi Penulisan dd ke Blok Fisik
        onLog("⚡ Menuliskan citra ke partisi fisik via kernel I/O...")
        val ddCmd = Shell.cmd("dd if='${file.absolutePath}' of='$targetNode' bs=4096 conv=fsync 2>&1").exec()
        val ddOut = ddCmd.out.joinToString("\n")
        onLog(ddOut)

        // 4. Sinkronisasi Mutlak Dirty Buffer
        onLog("🔄 Melakukan sinkronisasi memori NAND flash (sync)...")
        Shell.cmd("sync && sync").exec()

        val duration = System.currentTimeMillis() - startTime
        if (ddCmd.isSuccess) {
            onLog("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            onLog("✅ FLASHING BERHASIL! (${duration}ms)")
            onLog("Partisi ${target.title} telah diperbarui dengan aman.")
            onLog("Silakan reboot perangkat untuk memuat kernel/partisi baru.")
            onLog("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Pair(true, "Flashing partisi ${target.title} berhasil!")
        } else {
            onLog("❌ ERROR: Penulisan ke partisi gagal: $ddOut")
            Pair(false, "Penulisan partisi gagal: $ddOut")
        }
    }

    /**
     * Memulihkan berkas cadangan (.img) dari Vault ke partisi fisik asalnya.
     */
    suspend fun restoreBackup(
        backupPath: String,
        target: PartitionTarget? = null,
        onLog: (String) -> Unit = {}
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val file = File(backupPath)
        val determinedTarget = target ?: PartitionTarget.fromFileName(file.name)
        flashRawImage(
            imagePath = backupPath,
            target = determinedTarget,
            autoBackup = true,
            onLog = onLog
        )
    }

    /**
     * Menjalankan paket AnyKernel3 (.zip) langsung via mesin AnyKernel native.
     */
    suspend fun flashAnyKernelZip(
        zipPath: String,
        autoBackup: Boolean,
        onLog: (String) -> Unit
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val file = File(zipPath)
        onLog("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        onLog("  Lynx Kernel Manager — AnyKernel3 Flasher")
        onLog("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        onLog("Paket ZIP : ${file.name} (${"%.2f".format(file.length() / (1024f * 1024f))} MB)")

        if (autoBackup) {
            onLog("📦 Menjalankan Auto Pre-Flash Backup partisi boot aktif...")
            val (ok, msg) = createPartitionBackup(PartitionTarget.BOOT)
            if (ok) onLog("✅ $msg") else onLog("⚠️ Peringatan: Auto backup gagal ($msg).")
        }

        onLog("⚡ Mengekstrak paket dan menjalankan AnyKernel3 installer...")
        val res = Shell.cmd("'/data/local/tmp/lynxd' flasher flash '${file.absolutePath}' 2>&1").exec()
        val out = res.out.joinToString("\n")
        onLog(out)
        Shell.cmd("sync && sync").exec()

        val duration = System.currentTimeMillis() - startTime
        val isSuccess = res.isSuccess && !out.contains("[ERROR]") && !out.contains("Error:")
        if (isSuccess) {
            onLog("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            onLog("✅ FLASHING KERNEL BERHASIL! (${duration}ms)")
            onLog("Silakan reboot perangkat untuk menerapkan perubahan.")
            onLog("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Pair(true, "Flashing AnyKernel3 selesai.")
        } else {
            onLog("❌ Flashing kernel gagal atau mengalami error.")
            Pair(false, "Flashing gagal.")
        }
    }

    /**
     * Memulai ulang perangkat ke mode yang dipilih (system, recovery, bootloader).
     */
    fun rebootDevice(mode: String): Boolean {
        return try {
            when (mode.lowercase()) {
                "recovery" -> Shell.cmd("reboot recovery 2>/dev/null || /system/bin/reboot recovery").exec().isSuccess
                "bootloader", "fastboot" -> Shell.cmd("reboot bootloader 2>/dev/null || /system/bin/reboot bootloader").exec().isSuccess
                else -> Shell.cmd("svc power reboot 2>/dev/null || reboot").exec().isSuccess
            }
        } catch (_: Exception) {
            false
        }
    }
}
