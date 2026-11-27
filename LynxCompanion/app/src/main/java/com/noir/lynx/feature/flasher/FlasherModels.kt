package com.noir.lynx.feature.flasher

import java.io.File

/**
 * Target partisi fisik Android yang diizinkan untuk di-flash atau dicadangkan.
 * STRICT WHITELIST: Mencegah penulisan ke partisi sensitif seperti system, userdata, nvram, dsb.
 */
enum class PartitionTarget(
    val id: String,
    val title: String,
    val desc: String,
    val expectedMagic: String = ""
) {
    BOOT("boot", "Boot (Kernel)", "Kernel Image & Primary Ramdisk", "ANDROID!"),
    VENDOR_BOOT("vendor_boot", "Vendor Boot", "Vendor Ramdisk, Modules & DTB", "VNDRBOOT"),
    DTBO("dtbo", "DTBO", "Device Tree Blob Overlay", ""),
    INIT_BOOT("init_boot", "Init Boot", "GKI Generic Ramdisk (Android 13+)", "ANDROID!"),
    RECOVERY("recovery", "Recovery", "Dedicated Custom Recovery", "ANDROID!");

    companion object {
        fun fromFileName(name: String): PartitionTarget {
            val lower = name.lowercase()
            return when {
                lower.startsWith("init_boot") || lower.contains("init_boot") -> INIT_BOOT
                lower.startsWith("vendor_boot") || lower.contains("vendor_boot") -> VENDOR_BOOT
                lower.startsWith("dtbo") || lower.contains("dtbo") -> DTBO
                lower.startsWith("recovery") || lower.contains("recovery") || lower.contains("twrp") || lower.contains("orangefox") -> RECOVERY
                else -> BOOT
            }
        }

        fun fromId(id: String): PartitionTarget =
            values().firstOrNull { it.id.equals(id, ignoreCase = true) } ?: BOOT
    }
}

/**
 * Informasi blok partisi fisik kernel yang terdeteksi pada hardware.
 */
data class PartitionBlockInfo(
    val target: PartitionTarget,
    val slotSuffix: String,
    val nodePath: String,
    val realDevice: String,
    val sizeBytes: Long,
    val sizeMb: Float,
    val isAvailable: Boolean
)

/**
 * Berkas paket kernel (.zip) atau citra mentah (.img) yang terdeteksi di penyimpanan lokal.
 */
data class DiscoveredFlashFile(
    val file: File,
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val sizeMb: Float,
    val modifiedDate: Long,
    val isZip: Boolean,
    val isImg: Boolean
)

/**
 * Hasil validasi forensik dan pemeriksaan keamanan anti-brick berkas sebelum di-flash.
 */
data class ImageValidationResult(
    val isValid: Boolean,
    val detectedFormat: String, // "ANYKERNEL3_ZIP", "ANDROID_BOOT_IMG", "RAW_IMAGE", "UNKNOWN"
    val magicHeader: String,
    val fileSizeBytes: Long,
    val fileSizeMb: Float,
    val sha256: String,
    val targetPartition: PartitionTarget,
    val targetNodePath: String,
    val targetPartitionSizeBytes: Long,
    val targetPartitionSizeMb: Float,
    val isSizeSafe: Boolean,
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList()
)

/**
 * Entri berkas cadangan (.img) partisi boot yang tersimpan di Vault /sdcard/Lynx/backups/.
 */
data class SavedBootBackup(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val sizeMb: Float,
    val dateFormatted: String,
    val partitionType: PartitionTarget
)
