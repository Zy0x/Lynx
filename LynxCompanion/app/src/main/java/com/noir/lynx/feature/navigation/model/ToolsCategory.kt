package com.noir.lynx.feature.navigation.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.noir.lynx.designsystem.theme.*

/**
 * ToolsCategory — Categorized navigation model for Halaman Alat (Tools & Lab Hub).
 *
 * Separates diagnostic tools, bench testing runners, and partition flashers
 * from kernel configuration parameters (Tuning Engine), ensuring zero redundancy.
 */
enum class ToolsCategory(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentColor: Color
) {
    BATTERY_LAB(
        id = "battery_lab",
        title = "Lab Baterai & Daya",
        subtitle = "Bench Pengisian, Stopwatch & Uji Kapasitas Nyata",
        icon = Icons.Default.BatteryChargingFull,
        accentColor = AccentGreen
    ),
    BENCHMARK(
        id = "benchmark",
        title = "Lab Benchmark & Optimasi",
        subtitle = "FPS Profiler, Storage I/O & CCleaner Purge",
        icon = Icons.Default.Assessment,
        accentColor = AccentPurple
    ),
    FLASHER(
        id = "flasher",
        title = "Flasher & Partisi Boot",
        subtitle = "AnyKernel3 Flasher & Backup / Restore Citra Boot",
        icon = Icons.Default.InstallMobile,
        accentColor = AccentOrange
    ),
    SYSTEM_DIAG(
        id = "system_diag",
        title = "Diagnostik & Audit Sistem",
        subtitle = "Wakelocks, Sensor Thermal, SELinux & Custom Rules",
        icon = Icons.Default.Troubleshoot,
        accentColor = AccentCyan
    ),
    LOG_CENTER(
        id = "log_center",
        title = "Pusat Log & Forensik Sistem",
        subtitle = "Kernel Panic, Dropbox Crash, Log Daemon & Ring Buffer Dmesg",
        icon = Icons.Default.Terminal,
        accentColor = AccentBlue
    ),
    PREFERENCES(
        id = "preferences",
        title = "Preferensi & Media Decoder",
        subtitle = "Bahasa Aplikasi & Akselerasi Video Hardware",
        icon = Icons.Default.Settings,
        accentColor = AccentBlue
    );

    companion object {
        fun fromId(id: String?): ToolsCategory? = values().firstOrNull { it.id == id }
    }
}
