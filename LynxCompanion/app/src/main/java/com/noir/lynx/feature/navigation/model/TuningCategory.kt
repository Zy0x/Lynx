package com.noir.lynx.feature.navigation.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.noir.lynx.designsystem.theme.*

enum class TuningCategory(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentColor: Color
) {
    PROFILES("profiles", "Profil Performa Kernel", "Katalog Profil, Kustomisasi & Editor Preset", Icons.Default.Speed, AccentOrange),
    CPU("cpu", "CPU Performa", "Kluster, Respon Daya & Penjadwal", Icons.Default.Memory, AccentCyan),
    GPU("gpu", "GPU & Display", "Clock GPU, GED Boost & Refresh Rate", Icons.Default.SportsEsports, AccentOrange),
    THERMAL("thermal", "Thermal & Anti-Throttling", "Live Matrix, Trip Points & Bypass", Icons.Default.LocalFireDepartment, AccentRed),
    CHARGING("charging", "Battery & Charging", "Bypass Charging & Extreme Fast Charge", Icons.Default.BatteryChargingFull, AccentGreen),
    MEMORY("memory", "Memory & Storage", "ZRAM, Swappiness, LMK & I/O", Icons.Default.Storage, AccentPurple),
    NETWORK("network", "Network & Audio", "TCP Congestion, Ping & Audio MMAP", Icons.Default.Wifi, AccentBlue),
    SYSTEM("system", "Subsystem & Deep Tunables", "Otomasi Per-App & Deep Sysfs", Icons.Default.Security, Color(0xFF8B5CF6));

    companion object {
        fun fromId(id: String?): TuningCategory? = values().firstOrNull { it.id == id }
    }
}
