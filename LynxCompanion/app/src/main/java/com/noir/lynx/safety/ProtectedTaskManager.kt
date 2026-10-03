package com.noir.lynx.safety

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

enum class TaskSafetyCategory {
    CRITICAL,   // SystemUI, SurfaceFlinger, system_server - CANNOT be restricted to little-only
    IMPORTANT,  // Launcher, Phone, Settings - Warning shown if restricted
    USER        // Games, Social, Benchmarks - Full user customization allowed
}

data class ProtectedProcess(
    val processName: String,
    val displayName: String,
    val packageName: String,
    val category: TaskSafetyCategory,
    val isLocked: Boolean,
    val description: String
)

/**
 * Guard layer preventing critical Android OS components from being starved, frozen, or isolated
 * improperly by aggressive CPU affinity masks.
 */
object ProtectedTaskManager {

    private val CRITICAL_PROCESSES = setOf(
        "com.android.systemui",
        "surfaceflinger",
        "system_server",
        "zygote",
        "zygote64",
        "inputflinger",
        "mediaserver",
        "audioserver",
        "servicemanager",
        "hwservicemanager",
        "vold"
    )

    private val IMPORTANT_PREFIXES = listOf(
        "com.android.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.miui.home",
        "com.transsion.XOSLauncher",
        "com.sec.android.app.launcher",
        "com.oppo.launcher",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.phone",
        "com.android.settings"
    )

    fun getCategory(processOrPkgName: String): TaskSafetyCategory {
        val lower = processOrPkgName.lowercase().trim()
        if (CRITICAL_PROCESSES.contains(lower) || CRITICAL_PROCESSES.any { lower.contains(it) }) {
            return TaskSafetyCategory.CRITICAL
        }
        if (IMPORTANT_PREFIXES.any { lower.contains(it) }) {
            return TaskSafetyCategory.IMPORTANT
        }
        return TaskSafetyCategory.USER
    }

    fun isProtected(processOrPkgName: String): Boolean {
        val cat = getCategory(processOrPkgName)
        return cat == TaskSafetyCategory.CRITICAL || cat == TaskSafetyCategory.IMPORTANT
    }

    fun isStrictlyLocked(processOrPkgName: String): Boolean {
        return getCategory(processOrPkgName) == TaskSafetyCategory.CRITICAL
    }

    /**
     * Default list of recognized protected processes for presentation in UI.
     */
    fun getDefaultProtectedTasks(): List<ProtectedProcess> {
        return listOf(
            ProtectedProcess(
                processName = "com.android.systemui",
                displayName = "Android SystemUI",
                packageName = "com.android.systemui",
                category = TaskSafetyCategory.CRITICAL,
                isLocked = true,
                description = "Navigasi gesture, status bar, quick settings, dan notifikasi."
            ),
            ProtectedProcess(
                processName = "surfaceflinger",
                displayName = "SurfaceFlinger",
                packageName = "android.ui.compositor",
                category = TaskSafetyCategory.CRITICAL,
                isLocked = true,
                description = "Kompositor grafis layar utama. Menjamin frame rate rendering tetap mulus."
            ),
            ProtectedProcess(
                processName = "system_server",
                displayName = "Android System Server",
                packageName = "android",
                category = TaskSafetyCategory.CRITICAL,
                isLocked = true,
                description = "Manajer inti layanan Android (ActivityManager, WindowManager, PowerManager)."
            ),
            ProtectedProcess(
                processName = "zygote",
                displayName = "Zygote Init Daemon",
                packageName = "android.zygote",
                category = TaskSafetyCategory.CRITICAL,
                isLocked = true,
                description = "Proses induk yang melahirkan seluruh aplikasi di ekosistem Android."
            ),
            ProtectedProcess(
                processName = "inputflinger",
                displayName = "Input Dispatcher",
                packageName = "android.input",
                category = TaskSafetyCategory.CRITICAL,
                isLocked = true,
                description = "Pengontrol driver sentuhan layar dan responsivitas touch input."
            ),
            ProtectedProcess(
                processName = "launcher",
                displayName = "System Launcher / Beranda",
                packageName = "com.android.launcher",
                category = TaskSafetyCategory.IMPORTANT,
                isLocked = false,
                description = "Layar utama perangkat. Diizinkan tuning dengan peringatan stabilitas."
            )
        )
    }
}
