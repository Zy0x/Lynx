package com.noir.lynx.feature.navigation.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.noir.lynx.designsystem.theme.*

import androidx.compose.material.icons.automirrored.filled.VolumeUp

enum class TuningCategory(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentColor: Color
) {
    PROFILES("profiles", "Profil Performa Kernel", "Katalog Profil & Otomasi Per-Aplikasi", Icons.Default.Speed, AccentOrange),
    HUD("hud", "Floating Game HUD", "Overlay FPS, OSD Real-Time & Gaya Layout", Icons.Default.Visibility, AccentCyan),
    CPU("cpu", "CPU Performa", "Kluster, Respon Daya & Penjadwal", Icons.Default.Memory, AccentCyan),
    GPU("gpu", "GPU & Display", "Clock GPU, Driver Boost & Refresh Rate", Icons.Default.SportsEsports, AccentOrange),
    THERMAL("thermal", "Thermal & Anti-Throttling", "Live Matrix, Trip Points & Bypass", Icons.Default.LocalFireDepartment, AccentRed),
    CHARGING("charging", "Battery & Charging", "Bypass Charging & Extreme Fast Charge", Icons.Default.BatteryChargingFull, AccentGreen),
    MEMORY("memory", "Memory & Storage", "ZRAM, Swappiness, LMK & I/O", Icons.Default.Storage, AccentPurple),
    NETWORK("network", "Wi-Fi & Network Engine", "Wi-Fi CAM, TCP Congestion & DNS Switcher", Icons.Default.Wifi, AccentBlue),
    AUDIO("audio", "Sound & Audio Subsystem", "Low-Latency Audio MMAP, DAC & Gain", Icons.AutoMirrored.Filled.VolumeUp, AccentCyan),
    SYSTEM("system", "Subsystem & Deep Tunables", "Sysfs Lanjutan, Printk & Kernel Safety", Icons.Default.Security, Color(0xFF8B5CF6));

    companion object {
        fun fromId(id: String?): TuningCategory? = values().firstOrNull { it.id == id }
    }
}
