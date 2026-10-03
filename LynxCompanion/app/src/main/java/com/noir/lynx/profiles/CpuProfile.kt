package com.noir.lynx.profiles

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

enum class CpuControlProfile(val displayName: String, val badge: String, val description: String) {
    OEM_MANAGED("Managed by System (OEM)", "🟢 OEM", "Sistem Android bawaan mengontrol penuh seluruh penjadwalan dan daya."),
    GAMING("Gaming & Extreme Response", "⚡ Gaming", "Prioritas latensi instan 0µs, isolasi Big Cores untuk game, dan boost penjadwal aktif."),
    BALANCED("Balanced Daily", "⚖️ Seimbang", "Manajemen dinamis optimal untuk penggunaan harian, transisi mulus, dan efisiensi seimbang."),
    BATTERY("Deep Battery Saver", "🔋 Hemat Daya", "Prioritaskan status tidur lelap mikroprosesor, park core performa, dan kurangi alokasi daya."),
    CUSTOM("Custom User Tuning", "🛠️ Kustom", "Seluruh subsistem dikonfigurasi secara manual sesuai kebutuhan pengguna.")
}

/**
 * Snapshot representing a complete state checkpoint of all CPU subsystems.
 * Used for "Last Known Good" and "OEM Factory" rollback in Recovery Center.
 */
data class CpuProfileSnapshot(
    val profile: CpuControlProfile = CpuControlProfile.OEM_MANAGED,
    val isMasterOverride: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val cpusetTopApp: String = "0-7",
    val cpusetForeground: String = "0-7",
    val cpusetBackground: String = "0-3",
    val cpusetSystemBg: String = "0-3",
    val idleSemanticMode: String = "OEM_DEFAULT",
    val coreParkingMode: String = "DYNAMIC",
    val schedtuneBoostTopApp: Int = 15,
    val schedtuneBoostFg: Int = 0,
    val schedtuneBoostBg: Int = 0,
    val schedtunePreferIdle: Boolean = true,
    val governorPolicy0: String = "schedutil",
    val governorPolicy1: String = "schedutil"
) {
    fun getFormattedTime(): String {
        return try {
            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            sdf.format(Date(timestamp))
        } catch (_: Exception) {
            "Baru saja"
        }
    }

    fun toJson(): String {
        val j = JSONObject()
        j.put("profile", profile.name)
        j.put("isMasterOverride", isMasterOverride)
        j.put("timestamp", timestamp)
        j.put("cpusetTopApp", cpusetTopApp)
        j.put("cpusetForeground", cpusetForeground)
        j.put("cpusetBackground", cpusetBackground)
        j.put("cpusetSystemBg", cpusetSystemBg)
        j.put("idleSemanticMode", idleSemanticMode)
        j.put("coreParkingMode", coreParkingMode)
        j.put("schedtuneBoostTopApp", schedtuneBoostTopApp)
        j.put("schedtuneBoostFg", schedtuneBoostFg)
        j.put("schedtuneBoostBg", schedtuneBoostBg)
        j.put("schedtunePreferIdle", schedtunePreferIdle)
        j.put("governorPolicy0", governorPolicy0)
        j.put("governorPolicy1", governorPolicy1)
        return j.toString(2)
    }

    companion object {
        fun fromJson(jsonStr: String): CpuProfileSnapshot? {
            return try {
                val j = JSONObject(jsonStr)
                val profName = j.optString("profile", "OEM_MANAGED")
                val prof = try { CpuControlProfile.valueOf(profName) } catch (_: Exception) { CpuControlProfile.OEM_MANAGED }
                CpuProfileSnapshot(
                    profile = prof,
                    isMasterOverride = j.optBoolean("isMasterOverride", false),
                    timestamp = j.optLong("timestamp", System.currentTimeMillis()),
                    cpusetTopApp = j.optString("cpusetTopApp", "0-7"),
                    cpusetForeground = j.optString("cpusetForeground", "0-7"),
                    cpusetBackground = j.optString("cpusetBackground", "0-3"),
                    cpusetSystemBg = j.optString("cpusetSystemBg", "0-3"),
                    idleSemanticMode = j.optString("idleSemanticMode", "OEM_DEFAULT"),
                    coreParkingMode = j.optString("coreParkingMode", "DYNAMIC"),
                    schedtuneBoostTopApp = j.optInt("schedtuneBoostTopApp", 15),
                    schedtuneBoostFg = j.optInt("schedtuneBoostFg", 0),
                    schedtuneBoostBg = j.optInt("schedtuneBoostBg", 0),
                    schedtunePreferIdle = j.optBoolean("schedtunePreferIdle", true),
                    governorPolicy0 = j.optString("governorPolicy0", "schedutil"),
                    governorPolicy1 = j.optString("governorPolicy1", "schedutil")
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
