package com.noir.lynx.data

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Read-only kernel and hardware capability probe. */
object LynxKernelCapabilityRepository {
    suspend fun scanKernelCapabilities(): KernelCapabilityReport = withContext(Dispatchers.IO) {
        try {
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
                sched=${'$'}(cat /sys/block/mmcblk0/queue/scheduler 2>/dev/null)
                [ -z "${'$'}sched" ] && sched=${'$'}(cat /sys/block/sda/queue/scheduler 2>/dev/null)
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
