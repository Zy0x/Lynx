package com.noir.lynx.data

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import java.io.File

/** Read-only kernel and hardware capability probe. */
object LynxKernelCapabilityRepository {
    @Volatile
    private var cachedReport: KernelCapabilityReport? = null

    suspend fun scanKernelCapabilities(): KernelCapabilityReport = withContext(Dispatchers.IO) {
        cachedReport?.let { return@withContext it }
        try {
            // Direct pure Kotlin Sysfs Fast-Path (<0.2ms)
            val relDirect = System.getProperty("os.version") ?: ""
            val easDirect = File("/sys/devices/system/cpu/eas").exists() ||
                runCatching { File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors").readText().contains("schedutil") }.getOrDefault(false)
            val uclampDirect = File("/proc/sys/kernel/sched_util_clamp_min").exists()
            val zramDirect = runCatching { File("/sys/block/zram0/comp_algorithm").readText().trim() }.getOrDefault("")
            val ksmDirect = File("/sys/kernel/mm/ksm/run").exists()
            val mglruDirect = File("/sys/kernel/mm/lru_gen/enabled").exists()
            val gpuDirect = when {
                File("/sys/class/kgsl/kgsl-3d0").exists() -> "Qualcomm Adreno (KGSL)"
                File("/proc/gpufreq").exists() || File("/sys/module/ged").exists() || File("/dev/mali0").exists() -> "MediaTek Mali (GED/DVFS)"
                File("/dev/mali").exists() -> "ARM Mali Generic"
                else -> "none"
            }
            val tcpDirect = runCatching { File("/proc/sys/net/ipv4/tcp_available_congestion_control").readText().trim() }.getOrDefault("")
            val thermalDir = File("/sys/class/thermal")
            val tzCountDirect = if (thermalDir.isDirectory) {
                thermalDir.listFiles { f -> f.name.startsWith("thermal_zone") }?.size ?: 0
            } else 0
            val ioDirect = runCatching {
                // Find primary storage device scheduler dynamically with zero hardcoded device names.
                // Prioritize UFS / NVMe, then verified eMMC (MMC type or ext_csd), then generic.
                var chosenSched = ""
                val blockDir = File("/sys/block")
                if (blockDir.isDirectory) {
                    val blocks = blockDir.listFiles() ?: emptyArray()
                    // 1. Check UFS / NVMe
                    for (b in blocks) {
                        val sPath = File(b, "queue/scheduler")
                        if (!sPath.exists()) continue
                        val drv = runCatching { File(b, "device/driver").canonicalPath }.getOrDefault("")
                        if (drv.contains("ufshcd") || drv.contains("ufs") || b.name.startsWith("nvme")) {
                            chosenSched = sPath.readText().trim()
                            break
                        }
                    }
                    // 2. If no UFS, check verified eMMC
                    if (chosenSched.isBlank()) {
                        for (b in blocks) {
                            val sPath = File(b, "queue/scheduler")
                            if (!sPath.exists()) continue
                            val devType = runCatching { File(b, "device/type").readText().trim().uppercase() }.getOrDefault("")
                            val hasExtCsd = File(b, "device/ext_csd").exists()
                            if (devType == "MMC" || hasExtCsd) {
                                chosenSched = sPath.readText().trim()
                                break
                            }
                        }
                    }
                    // 3. Fallback to any physical block device
                    if (chosenSched.isBlank()) {
                        for (b in blocks) {
                            if (b.name.startsWith("loop") || b.name.startsWith("ram") || b.name.startsWith("zram")) continue
                            val sPath = File(b, "queue/scheduler")
                            if (sPath.exists()) {
                                chosenSched = sPath.readText().trim()
                                break
                            }
                        }
                    }
                }
                chosenSched
            }.getOrDefault("")

            if (relDirect.isNotBlank() || zramDirect.isNotBlank() || tzCountDirect > 0) {
                val items = mutableListOf<KernelCapabilityItem>()
                items.add(KernelCapabilityItem("Energy Aware Scheduling (EAS)", if (easDirect) "Aktif (Schedutil governor didukung)" else "Tidak terdeteksi", easDirect))
                items.add(KernelCapabilityItem("UCLAMP Task Clamping", if (uclampDirect) "Kernel mendukung sched_util_clamp" else "Tidak tersedia di kernel ini", uclampDirect))
                items.add(KernelCapabilityItem("GPU Driver Subsistem", if (gpuDirect != "none") gpuDirect else "Generic Linux framebuffer", gpuDirect != "none"))
                items.add(KernelCapabilityItem("Algoritma Kompresi ZRAM", if (zramDirect.isNotBlank()) zramDirect else "Algoritma ZRAM standar", zramDirect.isNotBlank()))
                items.add(KernelCapabilityItem("Kernel Same-page Merging (KSM)", if (ksmDirect) "Didukung (/sys/kernel/mm/ksm/run)" else "Tidak dikompilasi di kernel ini", ksmDirect))
                items.add(KernelCapabilityItem("Multi-Gen LRU (MGLRU)", if (mglruDirect) "Aktif (Linux 6.1+ memory manager)" else "Belum didukung oleh kernel ini", mglruDirect))
                items.add(KernelCapabilityItem("TCP Congestion Algorithms", if (tcpDirect.isNotBlank()) tcpDirect else "Default Linux", tcpDirect.contains("bbr") || tcpDirect.contains("cubic")))
                items.add(KernelCapabilityItem("Thermal Management Zones", "$tzCountDirect thermal zones aktif", tzCountDirect > 0))
                items.add(KernelCapabilityItem("Storage I/O Schedulers", if (ioDirect.isNotBlank()) ioDirect else "Standard queue", ioDirect.isNotBlank()))

                val supportedCount = items.count { it.isSupported }
                val score = if (items.isNotEmpty()) (supportedCount * 100) / items.size else 0
                val report = KernelCapabilityReport(scorePercent = score, kernelRelease = relDirect, items = items)
                cachedReport = report
                return@withContext report
            }

            val script = """
                echo "rel:${'$'}(uname -r 2>/dev/null)"
                eas=0
                [ -d /sys/devices/system/cpu/eas ] && eas=1
                grep -q "schedutil" /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors 2>/dev/null && eas=1
                echo "eas:${'$'}eas"
                uclamp=0
                [ -f /proc/sys/kernel/sched_util_clamp_min ] && uclamp=1
                echo "uclamp:${'$'}uclamp"
                zram_algs=${'$'}(cat /sys/block/zram0/comp_algorithm 2>/dev/null)
                echo "zram:${'$'}zram_algs"
                ksm=0
                [ -f /sys/kernel/mm/ksm/run ] && ksm=1
                echo "ksm:${'$'}ksm"
                mglru=0
                [ -f /sys/kernel/mm/lru_gen/enabled ] && mglru=1
                echo "mglru:${'$'}mglru"
                gpu_type="none"
                if [ -d /sys/class/kgsl/kgsl-3d0 ]; then gpu_type="Qualcomm Adreno (KGSL)";
                elif [ -d /proc/gpufreq ] || [ -d /sys/module/ged ] || [ -c /dev/mali0 ]; then gpu_type="MediaTek Mali (GED/DVFS)";
                elif [ -c /dev/mali ]; then gpu_type="ARM Mali Generic"; fi
                echo "gpu:${'$'}gpu_type"
                tcp=${'$'}(cat /proc/sys/net/ipv4/tcp_available_congestion_control 2>/dev/null)
                echo "tcp:${'$'}tcp"
                tz_count=${'$'}(ls -d /sys/class/thermal/thermal_zone* 2>/dev/null | wc -l)
                echo "thermal:${'$'}tz_count"
                sched=""
                for u in /sys/block/sd*/queue/scheduler /sys/block/nvme*/queue/scheduler; do
                    if [ -f "${'$'}u" ]; then
                        b_dir=${'$'}(dirname ${'$'}(dirname "${'$'}u"))
                        drv=${'$'}(readlink "${'$'}b_dir/device/driver" 2>/dev/null)
                        case "${'$'}drv" in *ufshcd*|*ufs*) sched=${'$'}(cat "${'$'}u" 2>/dev/null); break ;; esac
                    fi
                done
                if [ -z "${'$'}sched" ]; then
                    for m in /sys/block/mmcblk*/queue/scheduler; do
                        b_dir=${'$'}(dirname ${'$'}(dirname "${'$'}m"))
                        if [ "${'$'}(cat "${'$'}b_dir/device/type" 2>/dev/null | tr '[:lower:]' '[:upper:]')" = "MMC" ] || [ -f "${'$'}b_dir/device/ext_csd" ]; then
                            sched=${'$'}(cat "${'$'}m" 2>/dev/null); break
                        fi
                    done
                fi
                [ -z "${'$'}sched" ] && sched=${'$'}(cat /sys/block/sda/queue/scheduler 2>/dev/null)
                [ -z "${'$'}sched" ] && sched=${'$'}(cat /sys/block/mmcblk0/queue/scheduler 2>/dev/null)
                echo "io:${'$'}sched"
            """.trimIndent()
            val r = Shell.cmd(script).exec()
            var rel = ""; var eas = false; var uclamp = false; var zram = ""; var ksm = false; var mglru = false
            var gpu = "none"; var tcp = ""; var tzCount = 0; var io = ""
            for (line in r.out) {
                val t = line.trim()
                when {
                    t.startsWith("rel:") -> rel = t.removePrefix("rel:")
                    t.startsWith("eas:") -> eas = t.removePrefix("eas:") == "1"
                    t.startsWith("uclamp:") -> uclamp = t.removePrefix("uclamp:") == "1"
                    t.startsWith("zram:") -> zram = t.removePrefix("zram:")
                    t.startsWith("ksm:") -> ksm = t.removePrefix("ksm:") == "1"
                    t.startsWith("mglru:") -> mglru = t.removePrefix("mglru:") == "1"
                    t.startsWith("gpu:") -> gpu = t.removePrefix("gpu:")
                    t.startsWith("tcp:") -> tcp = t.removePrefix("tcp:")
                    t.startsWith("thermal:") -> tzCount = t.removePrefix("thermal:").toIntOrNull() ?: 0
                    t.startsWith("io:") -> io = t.removePrefix("io:")
                }
            }
            val items = mutableListOf<KernelCapabilityItem>()
            items.add(KernelCapabilityItem("Energy Aware Scheduling (EAS)", if (eas) "Aktif (Schedutil governor didukung)" else "Tidak terdeteksi", eas))
            items.add(KernelCapabilityItem("UCLAMP Task Clamping", if (uclamp) "Kernel mendukung sched_util_clamp" else "Tidak tersedia di kernel ini", uclamp))
            items.add(KernelCapabilityItem("GPU Driver Subsistem", if (gpu != "none") gpu else "Generic Linux framebuffer", gpu != "none"))
            items.add(KernelCapabilityItem("Algoritma Kompresi ZRAM", if (zram.isNotBlank()) zram else "Algoritma ZRAM standar", zram.isNotBlank()))
            items.add(KernelCapabilityItem("Kernel Same-page Merging (KSM)", if (ksm) "Didukung (/sys/kernel/mm/ksm/run)" else "Tidak dikompilasi di kernel ini", ksm))
            items.add(KernelCapabilityItem("Multi-Gen LRU (MGLRU)", if (mglru) "Aktif (Linux 6.1+ memory manager)" else "Belum didukung oleh kernel ini", mglru))
            items.add(KernelCapabilityItem("TCP Congestion Algorithms", if (tcp.isNotBlank()) tcp else "Default Linux", tcp.contains("bbr") || tcp.contains("cubic")))
            items.add(KernelCapabilityItem("Thermal Management Zones", "$tzCount thermal zones aktif", tzCount > 0))
            items.add(KernelCapabilityItem("Storage I/O Schedulers", if (io.isNotBlank()) io else "Standard queue", io.isNotBlank()))

            val supportedCount = items.count { it.isSupported }
            val score = if (items.isNotEmpty()) (supportedCount * 100) / items.size else 0
            KernelCapabilityReport(scorePercent = score, kernelRelease = rel, items = items)
        } catch (e: Exception) {
            KernelCapabilityReport()
        }
    }


}
