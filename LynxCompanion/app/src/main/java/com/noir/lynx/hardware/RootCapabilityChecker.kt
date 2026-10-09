package com.noir.lynx.hardware

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Root Environment & Capability Information
 */
data class RootEnvironmentInfo(
    val isRootGranted: Boolean = false,
    val rootManager: String = "Unknown", // "KernelSU", "APatch", "Magisk", "None"
    val selinuxMode: String = "Enforcing", // "Enforcing", "Permissive", "Disabled"
    val selinuxEnforcementImpact: Boolean = false,
    val isBusyboxAvailable: Boolean = false,
    val canWriteSysfs: Boolean = false,
    val details: String = ""
)

/**
 * RootCapabilityChecker — Validates root environment integrity,
 * root manager (KSU/APatch/Magisk), SELinux mode, and sysfs access
 * before executing kernel thermal modifications.
 */
object RootCapabilityChecker {

    suspend fun check(): RootEnvironmentInfo = withContext(Dispatchers.IO) {
        try {
            val isRoot = Shell.isAppGrantedRoot() == true || Shell.getShell().isRoot
            if (!isRoot) {
                return@withContext RootEnvironmentInfo(
                    isRootGranted = false,
                    rootManager = "None",
                    details = "Akses root belum diberikan ke LynxCompanion"
                )
            }

            val script = """
                # 1. Root manager probe
                rmgr="Unknown"
                if [ -d "/data/adb/ksu" ] || [ -f "/data/adb/ksud" ]; then
                    rmgr="KernelSU"
                elif [ -d "/data/adb/ap" ] || [ -d "/data/adb/apatch" ]; then
                    rmgr="APatch"
                elif command -v magisk >/dev/null 2>&1 || [ -d "/data/adb/magisk" ]; then
                    rmgr="Magisk"
                fi

                # 2. SELinux probe
                sel=$(getenforce 2>/dev/null || echo "Unknown")

                # 3. Busybox probe
                bb=0
                command -v busybox >/dev/null 2>&1 && bb=1

                # 4. Sysfs write permission check
                sys_w=0
                if [ -w "/sys/devices/system/cpu/cpufreq/policy0/scaling_governor" ] || \
                   [ -w "/sys/class/thermal/thermal_zone0/mode" ] || \
                   [ -w "/proc/sys/vm/drop_caches" ]; then
                    sys_w=1
                else
                    for tz in /sys/class/thermal/thermal_zone*/mode; do
                        if [ -w "${'$'}tz" ]; then sys_w=1; break; fi
                    done
                fi

                # 5. SELinux enforcement impact on thermal
                se_impact=0
                if [ "${'$'}sel" = "Enforcing" ]; then
                    dmesg 2>/dev/null | grep -i "avc: denied" | grep -iE "thermal|sysfs" | head -n 1 >/dev/null && se_impact=1
                fi

                echo "${'$'}rmgr|${'$'}sel|${'$'}bb|${'$'}sys_w|${'$'}se_impact"
            """.trimIndent()

            val out = Shell.cmd(script).exec().out.firstOrNull()?.trim() ?: ""
            val parts = out.split("|")
            val rmgr = parts.getOrNull(0) ?: "Unknown"
            val sel = parts.getOrNull(1) ?: "Enforcing"
            val bb = parts.getOrNull(2) == "1"
            val sysW = parts.getOrNull(3) == "1"
            val seImpact = parts.getOrNull(4) == "1"

            RootEnvironmentInfo(
                isRootGranted = true,
                rootManager = rmgr,
                selinuxMode = sel,
                selinuxEnforcementImpact = seImpact,
                isBusyboxAvailable = bb,
                canWriteSysfs = sysW,
                details = "Root: $rmgr | SELinux: $sel | Busybox: $bb | SysfsRW: $sysW"
            )
        } catch (e: Exception) {
            RootEnvironmentInfo(
                isRootGranted = false,
                rootManager = "Error",
                details = "Gagal memindai lingkungan root: ${e.message}"
            )
        }
    }
}
