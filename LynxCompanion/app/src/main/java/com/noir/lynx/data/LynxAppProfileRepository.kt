package com.noir.lynx.data
import android.content.Intent
import android.os.Build
import android.content.Context
import android.util.Log
import com.topjohnwu.superuser.Shell
import com.noir.lynx.service.LynxAppAutomationService
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
internal object LynxAppProfileRepository {
    suspend fun grantOverlayPermission(): Boolean = withContext(Dispatchers.IO) {
        try {
            val r = Shell.cmd(
                "appops set com.noir.lynx SYSTEM_ALERT_WINDOW allow 2>/dev/null",
                "appops set com.noir.lynx.debug SYSTEM_ALERT_WINDOW allow 2>/dev/null",
                "pm grant com.noir.lynx android.permission.SYSTEM_ALERT_WINDOW 2>/dev/null",
                "pm grant com.noir.lynx.debug android.permission.SYSTEM_ALERT_WINDOW 2>/dev/null"
            ).exec()
            r.isSuccess
        } catch (e: Exception) { false }
    }

    suspend fun grantUsageStatsPermission(): Boolean = withContext(Dispatchers.IO) {
        try {
            val r = Shell.cmd(
                "appops set com.noir.lynx GET_USAGE_STATS allow 2>/dev/null",
                "appops set com.noir.lynx.debug GET_USAGE_STATS allow 2>/dev/null",
                "pm grant com.noir.lynx android.permission.PACKAGE_USAGE_STATS 2>/dev/null",
                "pm grant com.noir.lynx.debug android.permission.PACKAGE_USAGE_STATS 2>/dev/null"
            ).exec()
            r.isSuccess
        } catch (e: Exception) { false }
    }

    suspend fun deployWatcherScripts(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (LynxRepository.isModuleInstalled()) {
                // Native lynxd daemon handles automation and foreground app profiling with zero shell overhead
                Shell.cmd("rm -f /data/adb/lynx/apply_profile.sh /data/adb/lynx/lynx_watcher.sh 2>/dev/null").exec()
                return@withContext true
            }
            val applyScript = LynxAutomationScripts.read("apply_profile.sh")

            val watcherScript = LynxAutomationScripts.read("lynx_watcher.sh")

            LynxRepository.writeTextToFileSafely("/data/adb/lynx/apply_profile.sh", applyScript, "755")
            LynxRepository.writeTextToFileSafely("/data/adb/lynx/lynx_watcher.sh", watcherScript, "755")

            true
        } catch (e: Exception) {
            Log.e("LynxRepository", "deployWatcherScripts error: ${e.message}")
            false
        }
    }



    suspend fun startAppAutomation(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            grantUsageStatsPermission()
            grantOverlayPermission()
            deployWatcherScripts()

            Shell.cmd(
                "echo 1 > /data/adb/lynx/automation_enabled",
                "chmod 666 /data/adb/lynx/automation_enabled 2>/dev/null"
            ).exec()

            val lynxdPath = LynxRepository.getLynxdPath()
            Shell.cmd(
                "pkill -f lynx_watcher.sh 2>/dev/null; " +
                "if [ -x '$lynxdPath' ]; then '$lynxdPath' daemon start >/dev/null 2>&1; fi"
            ).exec()

            val intent = Intent(context, LynxAppAutomationService::class.java).apply {
                action = LynxAppAutomationService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            true
        } catch (e: Exception) {
            Log.e("LynxRepository", "startAppAutomation failed: ${e.message}")
            false
        }
    }

    suspend fun stopAppAutomation(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val lynxdPath = LynxRepository.getLynxdPath()
            Shell.cmd(
                "echo 0 > /data/adb/lynx/automation_enabled",
                "if [ -x '$lynxdPath' ]; then '$lynxdPath' daemon stop 2>/dev/null; fi",
                "killall lynxd 2>/dev/null",
                "pkill -f lynx_watcher.sh 2>/dev/null",
                "rm -f /data/adb/lynx/watcher.pid 2>/dev/null"
            ).exec()

            val intent = Intent(context, LynxAppAutomationService::class.java).apply {
                action = LynxAppAutomationService.ACTION_STOP
            }
            context.stopService(intent)
            true
        } catch (e: Exception) {
            Log.e("LynxRepository", "stopAppAutomation failed: ${e.message}")
            false
        }
    }

    suspend fun isAppAutomationRunning(): Boolean = withContext(Dispatchers.IO) {
        try {
            val r = Shell.cmd("cat /data/adb/lynx/automation_enabled 2>/dev/null").exec()
            val flag = r.out.firstOrNull()?.trim()
            if (flag == "1") return@withContext true

            if (isWatcherDaemonAlive()) return@withContext true

            LynxAppAutomationService.isRunning
        } catch (e: Exception) {
            LynxAppAutomationService.isRunning
        }
    }

    suspend fun isWatcherDaemonAlive(): Boolean = withContext(Dispatchers.IO) {
        val check = Shell.cmd("pgrep -f 'lynxd daemon' || pgrep -f lynx_watcher.sh").exec()
        check.isSuccess && check.out.any { it.trim().isNotEmpty() }
    }
}
