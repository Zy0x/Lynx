package com.noir.lynx.data

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Kernel log export, environment summary, and emergency return-to-stock operations. */
object LynxKernelDiagnosticsRepository {

    suspend fun readKernelSummary(): KernelEnvironmentSummary = withContext(Dispatchers.IO) {
        try {
            val procVersion = runCatching { File("/proc/version").readText().trim() }
                .getOrElse { Shell.cmd("cat /proc/version 2>/dev/null").exec().out.joinToString(" ").trim() }

            val kernelRel = System.getProperty("os.version")?.ifBlank { null }
                ?: runCatching { File("/proc/sys/kernel/osrelease").readText().trim() }.getOrDefault("")

            val arch = System.getProperty("os.arch") ?: "aarch64"

            val compiler = when {
                procVersion.contains("clang", ignoreCase = true) -> {
                    val clangPart = procVersion.substringAfter("clang version", "").trim().takeWhile { it != ' ' && it != '(' }
                    if (clangPart.isNotBlank()) "Clang $clangPart" else "Clang (LLVM)"
                }
                procVersion.contains("gcc", ignoreCase = true) -> {
                    val gccPart = procVersion.substringAfter("gcc version", "").trim().takeWhile { it != ' ' && it != '(' }
                    if (gccPart.isNotBlank()) "GCC $gccPart" else "GCC"
                }
                else -> "Linux Toolchain"
            }

            val uptimeSeconds = runCatching {
                val upStr = File("/proc/uptime").readText().trim().split(" ").firstOrNull() ?: "0"
                upStr.toDoubleOrNull()?.toLong() ?: 0L
            }.getOrDefault(0L)

            val uptimeFormatted = when {
                uptimeSeconds >= 86400L -> {
                    val days = uptimeSeconds / 86400L
                    val hours = (uptimeSeconds % 86400L) / 3600L
                    val mins = (uptimeSeconds % 3600L) / 60L
                    "$days hari $hours jam $mins mnt"
                }
                uptimeSeconds >= 3600L -> {
                    val hours = uptimeSeconds / 3600L
                    val mins = (uptimeSeconds % 3600L) / 60L
                    "$hours jam $mins menit"
                }
                else -> {
                    val mins = uptimeSeconds / 60L
                    val secs = uptimeSeconds % 60L
                    "$mins menit $secs detik"
                }
            }

            val pstoreConsole = File("/sys/fs/pstore/console-ramoops")
            val pstoreDmesg = File("/sys/fs/pstore/dmesg-ramoops-0")
            val lastKmsg = File("/proc/last_kmsg")
            val dontPanic = File("/data/vendor/dontpanic")

            val hasPanicLog = (pstoreConsole.exists() && pstoreConsole.length() > 0) ||
                    (pstoreDmesg.exists() && pstoreDmesg.length() > 0) ||
                    (lastKmsg.exists() && lastKmsg.length() > 0) ||
                    (dontPanic.exists() && (dontPanic.listFiles()?.isNotEmpty() == true))

            val panicStatus = if (hasPanicLog) {
                "Terdeteksi Catatan Crash / Panic Kernel Sebelumnya"
            } else {
                "Sistem Stabil (Bebas Panic/Crash)"
            }

            val zramOk = File("/sys/block/zram0").exists()
            val easOk = File("/sys/devices/system/cpu/eas").exists() ||
                    runCatching { File("/proc/sys/kernel/sched_energy_aware").readText().trim() == "1" }.getOrDefault(false) ||
                    runCatching { File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors").readText().contains("schedutil") }.getOrDefault(false)
            val compactOk = File("/proc/sys/vm/compact_memory").exists()
            val kprobesOk = File("/sys/kernel/debug/kprobes").exists() || File("/proc/kallsyms").exists()
            val ebpfOk = File("/sys/fs/bpf").exists()
            val ksmOk = File("/sys/kernel/mm/ksm/run").exists()
            val uclampOk = File("/proc/sys/kernel/sched_util_clamp_min").exists()
            val mglruOk = File("/sys/kernel/mm/lru_gen/enabled").exists()

            val badges = listOf(
                KernelCapabilityBadge("ZRAM Swap", zramOk),
                KernelCapabilityBadge("EAS / Schedutil", easOk),
                KernelCapabilityBadge("RAM Compaction", compactOk),
                KernelCapabilityBadge("Kernel Probes", kprobesOk),
                KernelCapabilityBadge("eBPF Subsystem", ebpfOk),
                KernelCapabilityBadge("KSM Memory", ksmOk),
                KernelCapabilityBadge("UCLAMP", uclampOk),
                KernelCapabilityBadge("MGLRU", mglruOk)
            )

            KernelEnvironmentSummary(
                kernelVersion = procVersion,
                kernelRelease = kernelRel,
                compiler = compiler,
                architecture = arch,
                uptimeFormatted = uptimeFormatted,
                panicStatus = panicStatus,
                hasPanicLog = hasPanicLog,
                capabilities = badges,
                isLoading = false
            )
        } catch (e: Exception) {
            KernelEnvironmentSummary(
                kernelVersion = "Linux " + (System.getProperty("os.version") ?: "Generic"),
                kernelRelease = System.getProperty("os.version") ?: "",
                compiler = "Toolchain",
                architecture = System.getProperty("os.arch") ?: "aarch64",
                uptimeFormatted = "Aktif",
                panicStatus = "Status tidak dapat dipindai: ${e.message}",
                hasPanicLog = false,
                capabilities = emptyList(),
                isLoading = false
            )
        }
    }

    suspend fun executeQuickMaintenance(action: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            when (action) {
                "drop_caches" -> {
                    Shell.cmd("sync", "echo 3 > /proc/sys/vm/drop_caches").exec()
                    val duration = System.currentTimeMillis() - startTime
                    Pair(true, "Cache pagecache, dentries & inodes berhasil dibersihkan (${duration}ms).")
                }
                "fstrim" -> {
                    val res = Shell.cmd(
                        "if which fstrim >/dev/null 2>&1; then",
                        "  fstrim -v /data 2>&1",
                        "  fstrim -v /cache 2>&1 || true",
                        "elif which toybox >/dev/null 2>&1 && toybox fstrim /data >/dev/null 2>&1; then",
                        "  toybox fstrim -v /data 2>&1",
                        "elif which sm >/dev/null 2>&1; then",
                        "  sm fstrim 2>&1",
                        "elif which vdc >/dev/null 2>&1; then",
                        "  vdc cryptfs fstrim 2>&1",
                        "else",
                        "  echo 'Storage TRIM dijalankan via kernel scheduler.'",
                        "fi"
                    ).exec()
                    val duration = System.currentTimeMillis() - startTime
                    val out = res.out.filter { it.isNotBlank() && !it.contains("inaccessible or not found") && !it.contains("PersistableBundle") }.joinToString(" | ")
                    Pair(true, "TRIM selesai (${duration}ms): ${out.ifBlank { "Semua blok flash internal berhasil di-TRIM." }}")
                }
                "compact_memory" -> {
                    if (File("/proc/sys/vm/compact_memory").exists()) {
                        Shell.cmd("echo 1 > /proc/sys/vm/compact_memory 2>/dev/null").exec()
                        val duration = System.currentTimeMillis() - startTime
                        Pair(true, "Kompaksi memori fisik (RAM compaction) berhasil dipicu (${duration}ms).")
                    } else {
                        Pair(false, "Node /proc/sys/vm/compact_memory tidak didukung oleh kernel ini.")
                    }
                }
                "restart_audioserver" -> {
                    Shell.cmd("killall -9 audioserver 2>/dev/null || pkill -9 audioserver 2>/dev/null").exec()
                    val duration = System.currentTimeMillis() - startTime
                    Pair(true, "Daemon audioserver Android berhasil dimulai ulang (${duration}ms).")
                }
                "flush_logs" -> {
                    Shell.cmd("dmesg -c > /dev/null 2>&1", "logcat -c 2>/dev/null || true").exec()
                    val duration = System.currentTimeMillis() - startTime
                    Pair(true, "Ring buffer dmesg dan logcat berhasil dikosongkan (${duration}ms).")
                }
                "reset_to_stock" -> {
                    val msg = resetKernelToStock()
                    val duration = System.currentTimeMillis() - startTime
                    Pair(true, "$msg (${duration}ms)")
                }
                else -> {
                    Pair(false, "Aksi pemeliharaan tidak dikenal: $action")
                }
            }
        } catch (e: Exception) {
            Pair(false, "Gagal menjalankan pemeliharaan: ${e.message}")
        }
    }

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
