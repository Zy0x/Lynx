package com.noir.lynx.feature.hud.overlay

import android.app.ActivityManager
import android.content.Context
import com.noir.lynx.service.LynxFloatingHudService

/** Only inspects the owned service flag and app process, preserving the previous recovery behavior. */
internal fun hudProcessAlive(context: Context, flagName: String): Boolean {
    val file = java.io.File(context.filesDir, flagName)
    if (!file.exists()) return false
    val pid = runCatching { file.readText().trim().toIntOrNull() }.getOrNull()
    if (pid != null && pid > 0) {
        val procDir = java.io.File("/proc/$pid")
        if (procDir.exists()) {
            val cmdline = runCatching { java.io.File(procDir, "cmdline").readText() }.getOrDefault("")
            if (cmdline.contains("com.noir.lynx:hud")) return true
        }
    }
    val am = runCatching { context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager }.getOrNull()
    if (am != null) {
        @Suppress("DEPRECATION")
        val isServiceRunning = runCatching {
            am.getRunningServices(50)?.any { it.service.className == LynxFloatingHudService::class.java.name }
        }.getOrNull() ?: false
        if (isServiceRunning) return true
    }
    runCatching { file.delete() }
    return false
}
