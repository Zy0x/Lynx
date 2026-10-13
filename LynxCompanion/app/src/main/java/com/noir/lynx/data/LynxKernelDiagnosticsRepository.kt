package com.noir.lynx.data

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Kernel log export and emergency return-to-stock operations. */
object LynxKernelDiagnosticsRepository {
    suspend fun readDmesgLog(limit: Int = 300, filter: String = ""): List<String> = withContext(Dispatchers.IO) {
        try {
            val cmd = if (filter.isBlank()) {
                "dmesg 2>/dev/null | tail -n $limit"
            } else {
                val cleanFilter = filter.replace("'", "").trim()
                "dmesg 2>/dev/null | grep -i '$cleanFilter' | tail -n $limit"
            }
            val res = Shell.cmd(cmd).exec()
            if (res.out.isEmpty()) listOf("[Dmesg kosong atau tidak ada entri yang cocok dengan filter]") else res.out
        } catch (e: Exception) {
            listOf("[Gagal membaca dmesg log: ${e.message}]")
        }
    }

    suspend fun exportDmesgToFile(): String? = withContext(Dispatchers.IO) {
        try {
            val debugDir = "/storage/emulated/0/Debug"
            Shell.cmd("mkdir -p '$debugDir' 2>/dev/null").exec()
            val filename = "dmesg_dump_${System.currentTimeMillis()}.txt"
            val targetPath = "$debugDir/$filename"
            val res = Shell.cmd("dmesg > '$targetPath' 2>/dev/null && chmod 666 '$targetPath'").exec()
            if (res.isSuccess) targetPath else null
        } catch (e: Exception) {
            null
        }
    }

    // ============================================================
    //  BOOTLOOP PROTECTION & EMERGENCY REVERT TO STOCK
    // ============================================================

    suspend fun resetKernelToStock(): String = withContext(Dispatchers.IO) {
        try {
            // 1. Reset CPU policies to hardware min/max and default governor
            Shell.cmd(
                "for p in /sys/devices/system/cpu/cpufreq/policy*; do",
                "  [ -d \"\$p\" ] || continue",
                "  hw_min=\$(cat \$p/cpuinfo_min_freq 2>/dev/null)",
                "  hw_max=\$(cat \$p/cpuinfo_max_freq 2>/dev/null)",
                "  [ -n \"\$hw_min\" ] && echo \$hw_min > \$p/scaling_min_freq 2>/dev/null",
                "  [ -n \"\$hw_max\" ] && echo \$hw_max > \$p/scaling_max_freq 2>/dev/null",
                "  [ -f \$p/scaling_governor ] && echo 'schedutil' > \$p/scaling_governor 2>/dev/null",
                "done",
                // 2. Reset GPU boost and limit
                "echo 0 > /proc/gpufreq/gpufreq_opp_freq 2>/dev/null || true",
                "echo '0 0' > /proc/gpufreq/gpufreq_fixed_freq_volt 2>/dev/null || true",
                "echo 1 > /proc/mali/dvfs_enable 2>/dev/null || true",
                "echo 0 > /sys/module/ged/parameters/gpu_cust_boost_freq 2>/dev/null || true",
                "echo 0 > /sys/kernel/ged/hal/custom_boost_gpu_freq 2>/dev/null || true",
                "echo 0 > /sys/kernel/ged/hal/custom_upbound_gpu_freq 2>/dev/null || true",
                // 3. Clear any bootloop or watchdog lock files
                "rm -f /sdcard/Debug/SAFE_MODE 2>/dev/null",
                "rm -f /data/adb/lynx_boot_watchdog 2>/dev/null",
                "rm -f /data/adb/service.d/lynx* 2>/dev/null"
            ).exec()
            "Semua parameter CPU/GPU dipulihkan ke profil aman bawaan pabrik (Stock)."
        } catch (e: Exception) {
            "Gagal reset: ${e.message}"
        }
    }


}
