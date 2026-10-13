package com.noir.lynx.data
import android.content.Context
import android.util.Log
import com.topjohnwu.superuser.Shell
import com.noir.lynx.profiles.CpuProfileBoostPolicies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.abs

internal object LynxCpuBatchProfileRepository {
    suspend fun applyCpuBatchProfile(params: CpuBatchProfileParams, context: Context? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val isExtreme = (params.companionProfile == "extreme")
            val isExtremeOrPerf = (params.companionProfile == "extreme" || params.companionProfile == "performance")
            val isBattery = (params.companionProfile == "powersave" || params.companionProfile == "battery")
            val isBalanced = (params.companionProfile == "balance")
            val boostPolicy = CpuProfileBoostPolicies.forProfile(params.companionProfile)
            val totalCores = params.totalCores.coerceAtLeast(8)

            // Update in-memory lock tracking atomically
            params.clusterTargets.forEach { (cid, tgt) ->
                if (tgt.isLocked) {
                    LynxRepository.explicitlyUnlockedClusters.remove(cid)
                    LynxRepository.lockedClusterBounds[cid] = Pair(tgt.minFreq, tgt.maxFreq)
                } else {
                    LynxRepository.explicitlyUnlockedClusters.add(cid)
                    LynxRepository.lockedClusterBounds.remove(cid)
                }
            }

            val script = buildString {
                // A subpage CPU profile is a scoped override and must not mutate the master profile.
                if (params.updateMasterProfile && params.companionProfile != "auto") {
                    appendLine("if [ -x '$LYNXD_PATH' ]; then '$LYNXD_PATH' profile apply '${params.companionProfile}' 2>/dev/null; fi")
                }

                // Dynamic Cluster Topology Resolution (Supports 4+4 Snapdragon, 6+2 MediaTek, 1+3+4, 2+5+1, 1+3+2+2, 10-Core)
                // Guarantees at least 4 base cores remain active when parking Big/Prime cores
                val maxCoreIdx = (totalCores - 1).coerceAtLeast(1)
                val bgMax = if (totalCores >= 8) 2 else 1
                appendLine("little_cores=''")
                appendLine("big_cores=''")
                appendLine("for idx in 0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do")
                appendLine("  pol=\"/sys/devices/system/cpu/cpufreq/policy\$idx\"")
                appendLine("  [ -d \"\$pol\" ] || continue")
                appendLine("  c_list=\$(cat \"\$pol/related_cpus\" 2>/dev/null | tr -s '[:space:]' ' ')")
                appendLine("  cur_cnt=\$(echo \"\$little_cores\" | wc -w)")
                appendLine("  if [ \"\$cur_cnt\" -lt 4 ]; then")
                appendLine("    little_cores=\"\$little_cores \$c_list\"")
                appendLine("  else")
                appendLine("    big_cores=\"\$big_cores \$c_list\"")
                appendLine("  fi")
                appendLine("done")
                appendLine("[ -z \"\$little_cores\" ] && little_cores='0 1 2 3'")
                appendLine("little_last=\$(echo \"\$little_cores\" | awk '{print \$NF}')")
                appendLine("[ -z \"\$little_last\" ] && little_last=${(totalCores - 3).coerceAtLeast(3)}")
                appendLine("[ -z \"\$big_cores\" ] && big_cores='6 7'")

                // Pre-step: Bring all cores online before writing cpufreq policies so offline clusters accept new bounds
                appendLine("for c in /sys/devices/system/cpu/cpu[0-9]*; do")
                appendLine("  [ -d \"\$c\" ] || continue")
                appendLine("  [ -f \"\$c/online\" ] && echo 1 > \"\$c/online\" 2>/dev/null")
                appendLine("done")

                // 2. Apply each cluster targets
                params.clusterTargets.forEach { (cid, tgt) ->
                    val pDir = "/sys/devices/system/cpu/cpufreq/policy$cid"
                    appendLine("if [ -d '$pDir' ]; then")
                    appendLine("  chmod 644 '$pDir/scaling_min_freq' '$pDir/scaling_max_freq' 2>/dev/null")
                    // MediaTek PPM hardware sync first so PPM respects cluster bounds
                    appendLine("  c_idx=0")
                    appendLine("  for p in /sys/devices/system/cpu/cpufreq/policy*; do")
                    appendLine("    if [ \"\${p##*policy}\" = \"$cid\" ]; then")
                    appendLine("      if [ -f /proc/ppm/policy/hard_userlimit_max_cpu_freq ]; then")
                    appendLine("        echo \"\$c_idx ${tgt.maxFreq}\" > /proc/ppm/policy/hard_userlimit_max_cpu_freq 2>/dev/null")
                    appendLine("        echo \"\$c_idx ${tgt.minFreq}\" > /proc/ppm/policy/hard_userlimit_min_cpu_freq 2>/dev/null")
                    if (isBalanced) {
                        appendLine("        echo \"\$c_idx -1\" > /proc/ppm/policy/hard_userlimit_max_cpu_freq 2>/dev/null")
                        appendLine("        echo \"\$c_idx -1\" > /proc/ppm/policy/hard_userlimit_min_cpu_freq 2>/dev/null")
                    }
                    appendLine("      fi")
                    appendLine("      if [ -f /proc/ppm/policy/userlimit_max_cpu_freq ]; then")
                    if (tgt.isLocked) {
                        appendLine("        echo \"\$c_idx ${tgt.maxFreq}\" > /proc/ppm/policy/userlimit_max_cpu_freq 2>/dev/null")
                        appendLine("        echo \"\$c_idx ${tgt.minFreq}\" > /proc/ppm/policy/userlimit_min_cpu_freq 2>/dev/null")
                    } else {
                        appendLine("        echo \"\$c_idx -1\" > /proc/ppm/policy/userlimit_max_cpu_freq 2>/dev/null")
                        appendLine("        echo \"\$c_idx -1\" > /proc/ppm/policy/userlimit_min_cpu_freq 2>/dev/null")
                    }
                    appendLine("      fi")
                    appendLine("      break")
                    appendLine("    fi")
                    appendLine("    c_idx=\$((c_idx + 1))")
                    appendLine("  done")
                    if (tgt.isLocked || tgt.minFreq == tgt.maxFreq) {
                        appendLine("  if [ -f /proc/ppm/policy_status ]; then echo '2 0' > /proc/ppm/policy_status 2>/dev/null; fi")
                    } else {
                        appendLine("  if [ -f /proc/ppm/policy_status ]; then echo '2 1' > /proc/ppm/policy_status 2>/dev/null; fi")
                    }
                    appendLine("  hw_max=\$(cat '$pDir/cpuinfo_max_freq' 2>/dev/null)")
                    appendLine("  [ -n \"\$hw_max\" ] && echo \"\$hw_max\" > '$pDir/scaling_max_freq' 2>/dev/null")
                    appendLine("  echo '${tgt.maxFreq}' > '$pDir/scaling_max_freq' 2>/dev/null")
                    appendLine("  echo '${tgt.minFreq}' > '$pDir/scaling_min_freq' 2>/dev/null")
                    appendLine("  echo '${tgt.gov}' > '$pDir/scaling_governor' 2>/dev/null")
                    appendLine("fi")
                }

                // 3. Scheduler, EAS, Schedtune, Governor Rate-Limits & Workqueue sysfs
                appendLine("[ -f /proc/sys/kernel/sched_upmigrate ] && echo ${params.schedHystUp} > /proc/sys/kernel/sched_upmigrate 2>/dev/null")
                appendLine("[ -f /proc/sys/kernel/sched_downmigrate ] && echo ${params.schedHystDown} > /proc/sys/kernel/sched_downmigrate 2>/dev/null")
                appendLine("[ -f /sys/module/workqueue/parameters/power_efficient ] && echo '${if (isExtremeOrPerf) "N" else "Y"}' > /sys/module/workqueue/parameters/power_efficient 2>/dev/null")
                if (params.schedPreset == "extreme") {
                    appendLine("[ -f /proc/sys/kernel/sched_latency_ns ] && echo 3000000 > /proc/sys/kernel/sched_latency_ns 2>/dev/null")
                    appendLine("[ -f /proc/sys/kernel/sched_rt_runtime_us ] && echo -1 > /proc/sys/kernel/sched_rt_runtime_us 2>/dev/null")
                    appendLine("[ -f /proc/sys/kernel/sched_uclamp_util_min ] && echo 820 > /proc/sys/kernel/sched_uclamp_util_min 2>/dev/null")
                    appendLine("[ -f /proc/sys/kernel/sched_util_clamp_min ] && echo 820 > /proc/sys/kernel/sched_util_clamp_min 2>/dev/null")
                    appendLine("[ -f /proc/sys/kernel/sched_uclamp_util_max ] && echo 1024 > /proc/sys/kernel/sched_uclamp_util_max 2>/dev/null")
                    appendLine("[ -f /proc/sys/kernel/sched_util_clamp_max ] && echo 1024 > /proc/sys/kernel/sched_util_clamp_max 2>/dev/null")
                    appendLine("[ -f /dev/cpuctl/top-app/cpu.uclamp.min ] && (echo 80 > /dev/cpuctl/top-app/cpu.uclamp.min 2>/dev/null || echo 820 > /dev/cpuctl/top-app/cpu.uclamp.min 2>/dev/null)")
                    appendLine("[ -f /dev/cpuctl/foreground/cpu.uclamp.min ] && (echo 25 > /dev/cpuctl/foreground/cpu.uclamp.min 2>/dev/null || echo 256 > /dev/cpuctl/foreground/cpu.uclamp.min 2>/dev/null)")
                    appendLine("[ -f /dev/cpuctl/top-app/cpu.uclamp.max ] && (echo max > /dev/cpuctl/top-app/cpu.uclamp.max 2>/dev/null || echo 100 > /dev/cpuctl/top-app/cpu.uclamp.max 2>/dev/null)")
                    appendLine("[ -f /proc/sys/kernel/sched_boost ] && echo 2 > /proc/sys/kernel/sched_boost 2>/dev/null")
                    appendLine("[ -d /dev/stune/top-app ] && echo 80 > /dev/stune/top-app/schedtune.boost 2>/dev/null && echo 1 > /dev/stune/top-app/schedtune.prefer_idle 2>/dev/null && [ -f /dev/stune/top-app/schedtune.prefer_high_cap ] && echo 1 > /dev/stune/top-app/schedtune.prefer_high_cap 2>/dev/null")
                    appendLine("[ -d /dev/stune/foreground ] && echo 25 > /dev/stune/foreground/schedtune.boost 2>/dev/null && echo 1 > /dev/stune/foreground/schedtune.prefer_idle 2>/dev/null")
                    appendLine("[ -d /dev/stune/background ] && echo 0 > /dev/stune/background/schedtune.boost 2>/dev/null && echo 0 > /dev/stune/background/schedtune.prefer_idle 2>/dev/null")
                    appendLine("for gdir in /sys/devices/system/cpu/cpufreq/policy*/schedutil /sys/devices/system/cpu/cpufreq/policy*/walt /sys/devices/system/cpu/cpufreq/policy*/sugov_ext; do [ -d \"\$gdir\" ] || continue; echo 0 > \"\$gdir/up_rate_limit_us\" 2>/dev/null; echo 0 > \"\$gdir/down_rate_limit_us\" 2>/dev/null; done")
                } else if (params.schedPreset == "gaming") {
                    appendLine("[ -f /proc/sys/kernel/sched_latency_ns ] && echo 4000000 > /proc/sys/kernel/sched_latency_ns 2>/dev/null")
                    appendLine("[ -f /proc/sys/kernel/sched_rt_runtime_us ] && echo 980000 > /proc/sys/kernel/sched_rt_runtime_us 2>/dev/null")
                    appendLine("[ -f /proc/sys/kernel/sched_uclamp_util_min ] && echo 512 > /proc/sys/kernel/sched_uclamp_util_min 2>/dev/null")
                    appendLine("[ -f /proc/sys/kernel/sched_util_clamp_min ] && echo 512 > /proc/sys/kernel/sched_util_clamp_min 2>/dev/null")
                    appendLine("[ -f /proc/sys/kernel/sched_uclamp_util_max ] && echo 1024 > /proc/sys/kernel/sched_uclamp_util_max 2>/dev/null")
                    appendLine("[ -f /proc/sys/kernel/sched_util_clamp_max ] && echo 1024 > /proc/sys/kernel/sched_util_clamp_max 2>/dev/null")
                    appendLine("[ -f /dev/cpuctl/top-app/cpu.uclamp.min ] && (echo 50 > /dev/cpuctl/top-app/cpu.uclamp.min 2>/dev/null || echo 512 > /dev/cpuctl/top-app/cpu.uclamp.min 2>/dev/null)")
                    appendLine("[ -f /dev/cpuctl/foreground/cpu.uclamp.min ] && (echo 15 > /dev/cpuctl/foreground/cpu.uclamp.min 2>/dev/null || echo 154 > /dev/cpuctl/foreground/cpu.uclamp.min 2>/dev/null)")
                    appendLine("[ -f /dev/cpuctl/top-app/cpu.uclamp.max ] && (echo max > /dev/cpuctl/top-app/cpu.uclamp.max 2>/dev/null || echo 100 > /dev/cpuctl/top-app/cpu.uclamp.max 2>/dev/null)")
                    appendLine("[ -f /proc/sys/kernel/sched_boost ] && echo 1 > /proc/sys/kernel/sched_boost 2>/dev/null")
                    appendLine("[ -d /dev/stune/top-app ] && echo 50 > /dev/stune/top-app/schedtune.boost 2>/dev/null && echo 1 > /dev/stune/top-app/schedtune.prefer_idle 2>/dev/null && [ -f /dev/stune/top-app/schedtune.prefer_high_cap ] && echo 1 > /dev/stune/top-app/schedtune.prefer_high_cap 2>/dev/null")
                    appendLine("[ -d /dev/stune/foreground ] && echo 15 > /dev/stune/foreground/schedtune.boost 2>/dev/null && echo 0 > /dev/stune/foreground/schedtune.prefer_idle 2>/dev/null")
                    appendLine("[ -d /dev/stune/background ] && echo 0 > /dev/stune/background/schedtune.boost 2>/dev/null && echo 0 > /dev/stune/background/schedtune.prefer_idle 2>/dev/null")
                    appendLine("for gdir in /sys/devices/system/cpu/cpufreq/policy*/schedutil /sys/devices/system/cpu/cpufreq/policy*/walt /sys/devices/system/cpu/cpufreq/policy*/sugov_ext; do [ -d \"\$gdir\" ] || continue; echo 0 > \"\$gdir/up_rate_limit_us\" 2>/dev/null; echo 20000 > \"\$gdir/down_rate_limit_us\" 2>/dev/null; done")
                } else if (params.schedPreset == "battery") {
                    appendLine("[ -f /proc/sys/kernel/sched_latency_ns ] && echo 10000000 > /proc/sys/kernel/sched_latency_ns 2>/dev/null")
                    appendLine("[ -f /proc/sys/kernel/sched_rt_runtime_us ] && echo 900000 > /proc/sys/kernel/sched_rt_runtime_us 2>/dev/null")
                    appendLine("[ -f /proc/sys/kernel/sched_uclamp_util_min ] && echo 0 > /proc/sys/kernel/sched_uclamp_util_min 2>/dev/null")
                    appendLine("[ -f /proc/sys/kernel/sched_util_clamp_min ] && echo 0 > /proc/sys/kernel/sched_util_clamp_min 2>/dev/null")
                    appendLine("[ -f /proc/sys/kernel/sched_uclamp_util_max ] && echo 600 > /proc/sys/kernel/sched_uclamp_util_max 2>/dev/null")
                    appendLine("[ -f /proc/sys/kernel/sched_util_clamp_max ] && echo 600 > /proc/sys/kernel/sched_util_clamp_max 2>/dev/null")
                    appendLine("[ -f /dev/cpuctl/top-app/cpu.uclamp.min ] && echo 0 > /dev/cpuctl/top-app/cpu.uclamp.min 2>/dev/null")
                    appendLine("[ -f /dev/cpuctl/foreground/cpu.uclamp.min ] && echo 0 > /dev/cpuctl/foreground/cpu.uclamp.min 2>/dev/null")
                    appendLine("[ -f /proc/sys/kernel/sched_boost ] && echo 0 > /proc/sys/kernel/sched_boost 2>/dev/null")
                    appendLine("[ -d /dev/stune/top-app ] && echo 0 > /dev/stune/top-app/schedtune.boost 2>/dev/null && echo 0 > /dev/stune/top-app/schedtune.prefer_idle 2>/dev/null && [ -f /dev/stune/top-app/schedtune.prefer_high_cap ] && echo 0 > /dev/stune/top-app/schedtune.prefer_high_cap 2>/dev/null")
                    appendLine("[ -d /dev/stune/foreground ] && echo 0 > /dev/stune/foreground/schedtune.boost 2>/dev/null && echo 0 > /dev/stune/foreground/schedtune.prefer_idle 2>/dev/null")
                    appendLine("[ -d /dev/stune/background ] && echo 0 > /dev/stune/background/schedtune.boost 2>/dev/null && echo 0 > /dev/stune/background/schedtune.prefer_idle 2>/dev/null")
                    appendLine("for gdir in /sys/devices/system/cpu/cpufreq/policy*/schedutil /sys/devices/system/cpu/cpufreq/policy*/walt /sys/devices/system/cpu/cpufreq/policy*/sugov_ext; do [ -d \"\$gdir\" ] || continue; echo 8000 > \"\$gdir/up_rate_limit_us\" 2>/dev/null; echo 500 > \"\$gdir/down_rate_limit_us\" 2>/dev/null; done")
                } else {
                    appendLine("[ -f /proc/sys/kernel/sched_latency_ns ] && echo 6000000 > /proc/sys/kernel/sched_latency_ns 2>/dev/null")
                    appendLine("[ -f /proc/sys/kernel/sched_rt_runtime_us ] && echo 950000 > /proc/sys/kernel/sched_rt_runtime_us 2>/dev/null")
                    appendLine("[ -f /proc/sys/kernel/sched_uclamp_util_min ] && echo 0 > /proc/sys/kernel/sched_uclamp_util_min 2>/dev/null")
                    appendLine("[ -f /proc/sys/kernel/sched_util_clamp_min ] && echo 0 > /proc/sys/kernel/sched_util_clamp_min 2>/dev/null")
                    appendLine("[ -f /proc/sys/kernel/sched_uclamp_util_max ] && echo 1024 > /proc/sys/kernel/sched_uclamp_util_max 2>/dev/null")
                    appendLine("[ -f /proc/sys/kernel/sched_util_clamp_max ] && echo 1024 > /proc/sys/kernel/sched_util_clamp_max 2>/dev/null")
                    appendLine("[ -f /dev/cpuctl/top-app/cpu.uclamp.min ] && (echo ${boostPolicy.topAppMinPercent} > /dev/cpuctl/top-app/cpu.uclamp.min 2>/dev/null || echo ${boostPolicy.topAppMinPercent * 10 + 5} > /dev/cpuctl/top-app/cpu.uclamp.min 2>/dev/null)")
                    appendLine("[ -f /dev/cpuctl/foreground/cpu.uclamp.min ] && (echo ${boostPolicy.foregroundMinPercent} > /dev/cpuctl/foreground/cpu.uclamp.min 2>/dev/null || echo ${boostPolicy.foregroundMinPercent * 10 + 5} > /dev/cpuctl/foreground/cpu.uclamp.min 2>/dev/null)")
                    appendLine("[ -f /dev/cpuctl/top-app/cpu.uclamp.max ] && (echo max > /dev/cpuctl/top-app/cpu.uclamp.max 2>/dev/null || echo 100 > /dev/cpuctl/top-app/cpu.uclamp.max 2>/dev/null)")
                    appendLine("[ -f /proc/sys/kernel/sched_boost ] && echo 0 > /proc/sys/kernel/sched_boost 2>/dev/null")
                    appendLine("[ -d /dev/stune/top-app ] && echo ${boostPolicy.topAppSchedtuneBoost} > /dev/stune/top-app/schedtune.boost 2>/dev/null && echo ${if (boostPolicy.topAppPreferIdle) 1 else 0} > /dev/stune/top-app/schedtune.prefer_idle 2>/dev/null && [ -f /dev/stune/top-app/schedtune.prefer_high_cap ] && echo 0 > /dev/stune/top-app/schedtune.prefer_high_cap 2>/dev/null")
                    appendLine("[ -d /dev/stune/foreground ] && echo ${boostPolicy.foregroundSchedtuneBoost} > /dev/stune/foreground/schedtune.boost 2>/dev/null && echo 0 > /dev/stune/foreground/schedtune.prefer_idle 2>/dev/null")
                    appendLine("[ -d /dev/stune/background ] && echo 0 > /dev/stune/background/schedtune.boost 2>/dev/null && echo 0 > /dev/stune/background/schedtune.prefer_idle 2>/dev/null")
                    appendLine("for gdir in /sys/devices/system/cpu/cpufreq/policy*/schedutil /sys/devices/system/cpu/cpufreq/policy*/walt /sys/devices/system/cpu/cpufreq/policy*/sugov_ext; do [ -d \"\$gdir\" ] || continue; echo 500 > \"\$gdir/up_rate_limit_us\" 2>/dev/null; echo 10000 > \"\$gdir/down_rate_limit_us\" 2>/dev/null; done")
                }

                // 4. Task Shield (CPU Sets & Exclusivity Spread Optimization — aligned with applyCpuSetPreset & readCpuSetsInfo)
                if (params.cpuSetPreset == "gaming") {
                    appendLine("for b in /dev/cpuset /sys/fs/cgroup/cpuset; do")
                    appendLine("  [ -d \"\$b\" ] || continue")
                    appendLine("  for n in cpus cpuset.cpus; do")
                    appendLine("    [ -f \"\$b/top-app/\$n\" ] && echo \"0-$maxCoreIdx\" > \"\$b/top-app/\$n\" 2>/dev/null")
                    appendLine("    [ -f \"\$b/foreground/\$n\" ] && echo \"0-\$little_last\" > \"\$b/foreground/\$n\" 2>/dev/null")
                    appendLine("    [ -f \"\$b/background/\$n\" ] && echo \"0-$bgMax\" > \"\$b/background/\$n\" 2>/dev/null")
                    appendLine("    [ -f \"\$b/system-background/\$n\" ] && echo \"0-$bgMax\" > \"\$b/system-background/\$n\" 2>/dev/null")
                    appendLine("    [ -f \"\$b/restricted/\$n\" ] && echo '0-1' > \"\$b/restricted/\$n\" 2>/dev/null")
                    appendLine("  done")
                    appendLine("  [ -w \"\$b/top-app/mem_exclusive\" ] && echo 0 > \"\$b/top-app/mem_exclusive\" 2>/dev/null")
                    appendLine("  [ -w \"\$b/top-app/memory_spread_page\" ] && echo 0 > \"\$b/top-app/memory_spread_page\" 2>/dev/null")
                    appendLine("  [ -w \"\$b/top-app/memory_spread_slab\" ] && echo 0 > \"\$b/top-app/memory_spread_slab\" 2>/dev/null")
                    appendLine("done")
                } else if (params.cpuSetPreset == "battery") {
                    appendLine("for b in /dev/cpuset /sys/fs/cgroup/cpuset; do")
                    appendLine("  [ -d \"\$b\" ] || continue")
                    appendLine("  for n in cpus cpuset.cpus; do")
                    appendLine("    [ -f \"\$b/top-app/\$n\" ] && echo \"0-\$little_last\" > \"\$b/top-app/\$n\" 2>/dev/null")
                    appendLine("    [ -f \"\$b/foreground/\$n\" ] && echo \"0-\$little_last\" > \"\$b/foreground/\$n\" 2>/dev/null")
                    appendLine("    [ -f \"\$b/background/\$n\" ] && echo '0-1' > \"\$b/background/\$n\" 2>/dev/null")
                    appendLine("    [ -f \"\$b/system-background/\$n\" ] && echo '0-1' > \"\$b/system-background/\$n\" 2>/dev/null")
                    appendLine("    [ -f \"\$b/restricted/\$n\" ] && echo '0' > \"\$b/restricted/\$n\" 2>/dev/null")
                    appendLine("  done")
                    appendLine("done")
                } else {
                    appendLine("for b in /dev/cpuset /sys/fs/cgroup/cpuset; do")
                    appendLine("  [ -d \"\$b\" ] || continue")
                    appendLine("  for n in cpus cpuset.cpus; do")
                    appendLine("    [ -f \"\$b/top-app/\$n\" ] && echo \"0-$maxCoreIdx\" > \"\$b/top-app/\$n\" 2>/dev/null")
                    appendLine("    [ -f \"\$b/foreground/\$n\" ] && echo \"0-$maxCoreIdx\" > \"\$b/foreground/\$n\" 2>/dev/null")
                    appendLine("    [ -f \"\$b/background/\$n\" ] && echo \"0-$bgMax\" > \"\$b/background/\$n\" 2>/dev/null")
                    appendLine("    [ -f \"\$b/system-background/\$n\" ] && echo \"0-\$little_last\" > \"\$b/system-background/\$n\" 2>/dev/null")
                    appendLine("    [ -f \"\$b/restricted/\$n\" ] && echo \"0-\$little_last\" > \"\$b/restricted/\$n\" 2>/dev/null")
                    appendLine("  done")
                    appendLine("done")
                }

                // 5. Core Parking & CPUIdle C-State Synchronization
                if (params.idlePreset == "gaming") {
                    appendLine("for s in 2 3 4 5 6 7 8 9; do")
                    appendLine("  for cpu in /sys/devices/system/cpu/cpu[0-9]*; do")
                    appendLine("    [ -f \"\$cpu/cpuidle/state\$s/disable\" ] && echo 1 > \"\$cpu/cpuidle/state\$s/disable\" 2>/dev/null")
                    appendLine("  done")
                    appendLine("  [ -w /proc/cpuidle/state/enabled ] && echo \"100 \$s 0\" > /proc/cpuidle/state/enabled 2>/dev/null")
                    appendLine("done")
                    appendLine("for s in 0 1; do")
                    appendLine("  for cpu in /sys/devices/system/cpu/cpu[0-9]*; do")
                    appendLine("    [ -f \"\$cpu/cpuidle/state\$s/disable\" ] && echo 0 > \"\$cpu/cpuidle/state\$s/disable\" 2>/dev/null")
                    appendLine("  done")
                    appendLine("  [ -w /proc/cpuidle/state/enabled ] && echo \"100 \$s 1\" > /proc/cpuidle/state/enabled 2>/dev/null")
                    appendLine("done")
                    appendLine("[ -f /proc/sys/kernel/sched_cstate_aware ] && echo 0 > /proc/sys/kernel/sched_cstate_aware 2>/dev/null")
                    appendLine("[ -f /sys/module/lpm_levels/parameters/sleep_disabled ] && echo 1 > /sys/module/lpm_levels/parameters/sleep_disabled 2>/dev/null")
                } else {
                    appendLine("for s in 0 1 2 3 4 5 6 7 8 9; do")
                    appendLine("  for cpu in /sys/devices/system/cpu/cpu[0-9]*; do")
                    appendLine("    [ -f \"\$cpu/cpuidle/state\$s/disable\" ] && echo 0 > \"\$cpu/cpuidle/state\$s/disable\" 2>/dev/null")
                    appendLine("  done")
                    appendLine("  [ -w /proc/cpuidle/state/enabled ] && echo \"100 \$s 1\" > /proc/cpuidle/state/enabled 2>/dev/null")
                    appendLine("done")
                    appendLine("[ -f /proc/sys/kernel/sched_cstate_aware ] && echo 1 > /proc/sys/kernel/sched_cstate_aware 2>/dev/null")
                    appendLine("[ -f /sys/module/lpm_levels/parameters/sleep_disabled ] && echo 0 > /sys/module/lpm_levels/parameters/sleep_disabled 2>/dev/null")
                }

                if (params.parkingMode == "unpark_all") {
                    appendLine("for c in /sys/devices/system/cpu/cpu[0-9]*; do")
                    appendLine("  [ -d \"\$c\" ] || continue")
                    appendLine("  [ -f \"\$c/online\" ] && echo 1 > \"\$c/online\" 2>/dev/null")
                    appendLine("done")
                    appendLine("for ctl in /sys/devices/system/cpu/cpu*/core_ctl; do")
                    appendLine("  [ -d \"\$ctl\" ] || continue")
                    appendLine("  max_c=\$(cat \"\$ctl/max_cpus\" 2>/dev/null || echo 4)")
                    appendLine("  [ -w \"\$ctl/min_cpus\" ] && echo \"\$max_c\" > \"\$ctl/min_cpus\" 2>/dev/null")
                    appendLine("done")
                } else if (params.parkingMode == "park_big") {
                    appendLine("for c in \$little_cores; do")
                    appendLine("  [ \"\$c\" = \"0\" ] && continue")
                    appendLine("  [ -f \"/sys/devices/system/cpu/cpu\$c/online\" ] && echo 1 > \"/sys/devices/system/cpu/cpu\$c/online\" 2>/dev/null")
                    appendLine("done")
                    appendLine("for c in \$big_cores; do")
                    appendLine("  [ -f \"/sys/devices/system/cpu/cpu\$c/online\" ] && echo 0 > \"/sys/devices/system/cpu/cpu\$c/online\" 2>/dev/null")
                    appendLine("done")
                } else {
                    appendLine("for c in /sys/devices/system/cpu/cpu[0-9]*; do")
                    appendLine("  [ -d \"\$c\" ] || continue")
                    appendLine("  [ -f \"\$c/online\" ] && echo 1 > \"\$c/online\" 2>/dev/null")
                    appendLine("done")
                    appendLine("for ctl in /sys/devices/system/cpu/cpu*/core_ctl; do")
                    appendLine("  [ -d \"\$ctl\" ] || continue")
                    appendLine("  [ -w \"\$ctl/min_cpus\" ] && echo 2 > \"\$ctl/min_cpus\" 2>/dev/null")
                    appendLine("done")
                }

                // Cross-subsystem platform boosts belong to the dashboard master profile.
                // A local CPU-page profile must not silently rewrite bus, thermal, touch,
                // storage, or platform power policy while leaving other overrides intact.
                if (params.updateMasterProfile) {
                // 6. Platform Hardware Engine: Memory Bus (Devfreq / DVFSRC / DRAM_CTRL / CM_MGR), CCI Interconnect, DVFS Mode, Touch Boost & PPM
                if (isExtremeOrPerf) {
                    // MediaTek PPM, CCI Interconnect, DVFSRC DDR, PerfMgr & DVFS Power Mode
                    appendLine("if [ -f /proc/ppm/policy_status ]; then")
                    appendLine("  echo '2 0' > /proc/ppm/policy_status 2>/dev/null")
                    appendLine("  echo '3 0' > /proc/ppm/policy_status 2>/dev/null")
                    appendLine("  echo '4 ${if (isExtreme) "0" else "1"}' > /proc/ppm/policy_status 2>/dev/null")
                    appendLine("  echo '5 0' > /proc/ppm/policy_status 2>/dev/null")
                    appendLine("  echo '6 1' > /proc/ppm/policy_status 2>/dev/null")
                    appendLine("  echo '7 1' > /proc/ppm/policy_status 2>/dev/null")
                    appendLine("  echo '9 1' > /proc/ppm/policy_status 2>/dev/null")
                    appendLine("fi")
                    appendLine("[ -f /proc/cpufreq/cpufreq_cci_mode ] && echo 1 > /proc/cpufreq/cpufreq_cci_mode 2>/dev/null")
                    appendLine("[ -f /proc/cpufreq/cpufreq_power_mode ] && echo 3 > /proc/cpufreq/cpufreq_power_mode 2>/dev/null")
                    appendLine("[ -f /sys/devices/system/cpu/perf/enable ] && echo 1 > /sys/devices/system/cpu/perf/enable 2>/dev/null")
                    appendLine("[ -f /proc/cpufreq/cpufreq_imax_enable ] && echo 0 > /proc/cpufreq/cpufreq_imax_enable 2>/dev/null")
                    appendLine("[ -f /proc/cpufreq/cpufreq_imax_thermal_protect ] && echo 0 > /proc/cpufreq/cpufreq_imax_thermal_protect 2>/dev/null")
                    appendLine("[ -f /proc/cpufreq/cpufreq_debug ] && echo 0 > /proc/cpufreq/cpufreq_debug 2>/dev/null")
                    appendLine("[ -f /proc/cpuidle/control/armpll_mode ] && echo 0 > /proc/cpuidle/control/armpll_mode 2>/dev/null")
                    appendLine("[ -f /proc/cpuidle/control/buck_mode ] && echo 0 > /proc/cpuidle/control/buck_mode 2>/dev/null")
                    appendLine("[ -f /proc/perfmgr/boost_ctrl/cpu_ctrl/cfp_enable ] && echo 0 > /proc/perfmgr/boost_ctrl/cpu_ctrl/cfp_enable 2>/dev/null")
                    appendLine("[ -f /proc/perfmgr/syslimiter/syslimiter_force_disable ] && echo 1 > /proc/perfmgr/syslimiter/syslimiter_force_disable 2>/dev/null")
                    appendLine("[ -f /sys/kernel/fpsgo/fbt/thrm_limit_cpu ] && echo ${if (isExtreme) "0" else "1"} > /sys/kernel/fpsgo/fbt/thrm_limit_cpu 2>/dev/null")
                    appendLine("[ -f /sys/kernel/fpsgo/fbt/boost_ta ] && echo 1 > /sys/kernel/fpsgo/fbt/boost_ta 2>/dev/null")
                    appendLine("[ -f /sys/kernel/eara_thermal/enable ] && echo ${if (isExtreme) "0" else "1"} > /sys/kernel/eara_thermal/enable 2>/dev/null")
                    val ddrOppVal = if (isExtreme) "0" else "2"
                    appendLine("for dvfs in /sys/devices/platform/*dvfsrc*/helio-dvfsrc /sys/kernel/helio-dvfsrc; do")
                    appendLine("  [ -d \"\$dvfs\" ] || continue")
                    appendLine("  [ -w \"\$dvfs/dvfsrc_req_ddr_opp\" ] && echo $ddrOppVal > \"\$dvfs/dvfsrc_req_ddr_opp\" 2>/dev/null")
                    appendLine("  [ -w \"\$dvfs/dvfsrc_force_vcore_dvfs_opp\" ] && echo ${if (isExtreme) "0" else "-1"} > \"\$dvfs/dvfsrc_force_vcore_dvfs_opp\" 2>/dev/null")
                    appendLine("done")
                    appendLine("[ -f /proc/perfmgr/boost_ctrl/dram_ctrl/ddr ] && echo $ddrOppVal > /proc/perfmgr/boost_ctrl/dram_ctrl/ddr 2>/dev/null")
                    if (isExtreme) {
                        appendLine("if [ -f /proc/cm_mgr/dbg_cm_mgr ]; then")
                        appendLine("  echo 'cm_mgr_perf_force_enable 1' > /proc/cm_mgr/dbg_cm_mgr 2>/dev/null")
                        appendLine("  echo 'cm_mgr_dram_opp 0' > /proc/cm_mgr/dbg_cm_mgr 2>/dev/null")
                        appendLine("fi")
                    } else {
                        appendLine("if [ -f /proc/cm_mgr/dbg_cm_mgr ]; then")
                        appendLine("  echo 'cm_mgr_perf_force_enable 0' > /proc/cm_mgr/dbg_cm_mgr 2>/dev/null")
                        appendLine("  echo 'cm_mgr_dram_opp -1' > /proc/cm_mgr/dbg_cm_mgr 2>/dev/null")
                        appendLine("fi")
                    }
                    // MediaTek Touch Boost & Goodix / TP Game Mode
                    appendLine("if [ -f /proc/perfmgr/tchbst/user/usrtch ]; then")
                    appendLine("  echo 'enable 1' > /proc/perfmgr/tchbst/user/usrtch 2>/dev/null")
                    appendLine("  echo 'eas_boost 100' > /proc/perfmgr/tchbst/user/usrtch 2>/dev/null")
                    appendLine("  echo 'touch_opp 0' > /proc/perfmgr/tchbst/user/usrtch 2>/dev/null")
                    appendLine("  echo 'deboost_when_render 0' > /proc/perfmgr/tchbst/user/usrtch 2>/dev/null")
                    appendLine("fi")
                    appendLine("[ -f /sys/devices/platform/goodix_ts.0/game_mode ] && echo 1 > /sys/devices/platform/goodix_ts.0/game_mode 2>/dev/null")
                    appendLine("[ -f /sys/devices/platform/tp_wake_switch/game_mode ] && echo 1 > /sys/devices/platform/tp_wake_switch/game_mode 2>/dev/null")
                    // MediaTek PerfMgr EAS_CTRL
                    val taBoost = if (isExtreme) "80" else "50"
                    val fgBoostVal = if (isExtreme) "25" else "15"
                    appendLine("if [ -d /proc/perfmgr/boost_ctrl/eas_ctrl ]; then")
                    appendLine("  echo $taBoost > /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_boost 2>/dev/null")
                    appendLine("  echo $fgBoostVal > /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_fg_boost 2>/dev/null")
                    appendLine("  echo 1 > /proc/perfmgr/boost_ctrl/eas_ctrl/sched_big_task_rotation 2>/dev/null")
                    appendLine("  echo 0 > /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_schedplus_down_throttle 2>/dev/null")
                    appendLine("  echo 0 > /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_schedplus_up_throttle 2>/dev/null")
                    appendLine("fi")

                    // Qualcomm / Exynos / Tensor / Unisoc Devfreq Memory Bus, LLCC, L3 Cache & Load Boost
                    appendLine("for bw in /sys/class/devfreq/*; do")
                    appendLine("  [ -d \"\$bw\" ] || continue")
                    appendLine("  case \"\$bw\" in *kgsl-3d0*|*kgsl-3d*|*latfloor*) continue ;; esac")
                    appendLine("  [ -w \"\$bw/governor\" ] && echo 'performance' > \"\$bw/governor\" 2>/dev/null")
                    if (isExtreme) {
                        appendLine("  if [ -s \"\$bw/available_frequencies\" ]; then")
                        appendLine("    hi_f=\$(tr ' ' '\\n' < \"\$bw/available_frequencies\" | sort -n | tail -n1)")
                        appendLine("    [ -n \"\$hi_f\" ] && echo \"\$hi_f\" > \"\$bw/max_freq\" 2>/dev/null && echo \"\$hi_f\" > \"\$bw/min_freq\" 2>/dev/null")
                        appendLine("  fi")
                    }
                    appendLine("done")
                    appendLine("[ -w /sys/class/devfreq/soc:qcom,gpubw/polling_interval ] && echo 10 > /sys/class/devfreq/soc:qcom,gpubw/polling_interval 2>/dev/null")
                    appendLine("[ -w /sys/class/devfreq/soc:qcom,l3-cdsp/polling_interval ] && echo 10 > /sys/class/devfreq/soc:qcom,l3-cdsp/polling_interval 2>/dev/null")
                    appendLine("[ -w /sys/devices/system/edac/qcom-llcc/panic_on_ue ] && echo 0 > /sys/devices/system/edac/qcom-llcc/panic_on_ue 2>/dev/null")
                    appendLine("[ -w /sys/devices/system/edac/qcom-llcc/panic_on_ce ] && echo 0 > /sys/devices/system/edac/qcom-llcc/panic_on_ce 2>/dev/null")
                    appendLine("[ -w /sys/devices/platform/soc/1d84000.ufshc/clkgate_delay_ms_perf ] && echo 5 > /sys/devices/platform/soc/1d84000.ufshc/clkgate_delay_ms_perf 2>/dev/null")
                    appendLine("for c in \$little_cores; do [ -w \"/sys/devices/system/cpu/cpu\$c/sched_load_boost\" ] && echo 0 > \"/sys/devices/system/cpu/cpu\$c/sched_load_boost\" 2>/dev/null; done")
                    appendLine("for c in \$big_cores; do [ -w \"/sys/devices/system/cpu/cpu\$c/sched_load_boost\" ] && echo 15 > \"/sys/devices/system/cpu/cpu\$c/sched_load_boost\" 2>/dev/null; done")
                    appendLine("[ -f /sys/module/msm_thermal/core_control/enabled ] && echo ${if (isExtreme) "0" else "1"} > /sys/module/msm_thermal/core_control/enabled 2>/dev/null")
                    appendLine("[ -f /sys/module/msm_thermal/parameters/enabled ] && echo '${if (isExtreme) "N" else "Y"}' > /sys/module/msm_thermal/parameters/enabled 2>/dev/null")
                } else {
                    // Restore Balanced / Battery Memory Bus, CCI, DVFSRC, Devfreq & PPM
                    val mtkPwrMode = if (isBattery) "1" else "0"
                    val ppmPwrThro = if (boostPolicy.mediatekPowerThrottleEnabled) "1" else "0"
                    val ddrOppRestore = if (isBattery) "3" else "-1"
                    val qcomLoadBoostVal = if (isBattery) "-6" else "0"
                    appendLine("if [ -f /proc/ppm/policy_status ]; then")
                    appendLine("  echo '2 1' > /proc/ppm/policy_status 2>/dev/null")
                    appendLine("  echo '3 $ppmPwrThro' > /proc/ppm/policy_status 2>/dev/null")
                    appendLine("  echo '4 1' > /proc/ppm/policy_status 2>/dev/null")
                    appendLine("  echo '5 1' > /proc/ppm/policy_status 2>/dev/null")
                    appendLine("  echo '6 1' > /proc/ppm/policy_status 2>/dev/null")
                    appendLine("  echo '7 1' > /proc/ppm/policy_status 2>/dev/null")
                    appendLine("  echo '9 0' > /proc/ppm/policy_status 2>/dev/null")
                    appendLine("fi")
                    appendLine("[ -f /proc/cpufreq/cpufreq_cci_mode ] && echo 0 > /proc/cpufreq/cpufreq_cci_mode 2>/dev/null")
                    appendLine("[ -f /proc/cpufreq/cpufreq_power_mode ] && echo $mtkPwrMode > /proc/cpufreq/cpufreq_power_mode 2>/dev/null")
                    appendLine("[ -f /sys/devices/system/cpu/perf/enable ] && echo 0 > /sys/devices/system/cpu/perf/enable 2>/dev/null")
                    appendLine("[ -f /proc/cpufreq/cpufreq_imax_enable ] && echo 0 > /proc/cpufreq/cpufreq_imax_enable 2>/dev/null")
                    appendLine("[ -f /proc/cpufreq/cpufreq_imax_thermal_protect ] && echo 1 > /proc/cpufreq/cpufreq_imax_thermal_protect 2>/dev/null")
                    appendLine("[ -f /proc/cpuidle/control/armpll_mode ] && echo 1 > /proc/cpuidle/control/armpll_mode 2>/dev/null")
                    appendLine("[ -f /proc/cpuidle/control/buck_mode ] && echo 1 > /proc/cpuidle/control/buck_mode 2>/dev/null")
                    appendLine("[ -f /proc/perfmgr/boost_ctrl/cpu_ctrl/cfp_enable ] && echo 1 > /proc/perfmgr/boost_ctrl/cpu_ctrl/cfp_enable 2>/dev/null")
                    appendLine("[ -f /proc/perfmgr/syslimiter/syslimiter_force_disable ] && echo 0 > /proc/perfmgr/syslimiter/syslimiter_force_disable 2>/dev/null")
                    appendLine("[ -f /sys/kernel/fpsgo/fbt/thrm_limit_cpu ] && echo 1 > /sys/kernel/fpsgo/fbt/thrm_limit_cpu 2>/dev/null")
                    appendLine("[ -f /sys/kernel/eara_thermal/enable ] && echo 1 > /sys/kernel/eara_thermal/enable 2>/dev/null")
                    appendLine("for dvfs in /sys/devices/platform/*dvfsrc*/helio-dvfsrc /sys/kernel/helio-dvfsrc; do")
                    appendLine("  [ -d \"\$dvfs\" ] || continue")
                    appendLine("  [ -w \"\$dvfs/dvfsrc_req_ddr_opp\" ] && echo $ddrOppRestore > \"\$dvfs/dvfsrc_req_ddr_opp\" 2>/dev/null")
                    appendLine("  [ -w \"\$dvfs/dvfsrc_force_vcore_dvfs_opp\" ] && echo -1 > \"\$dvfs/dvfsrc_force_vcore_dvfs_opp\" 2>/dev/null")
                    appendLine("done")
                    appendLine("[ -f /proc/perfmgr/boost_ctrl/dram_ctrl/ddr ] && echo $ddrOppRestore > /proc/perfmgr/boost_ctrl/dram_ctrl/ddr 2>/dev/null")
                    appendLine("if [ -f /proc/cm_mgr/dbg_cm_mgr ]; then")
                    appendLine("  echo 'cm_mgr_perf_force_enable 0' > /proc/cm_mgr/dbg_cm_mgr 2>/dev/null")
                    appendLine("  echo 'cm_mgr_dram_opp -1' > /proc/cm_mgr/dbg_cm_mgr 2>/dev/null")
                    appendLine("fi")
                    if (isBattery) {
                        appendLine("if [ -f /proc/perfmgr/tchbst/user/usrtch ]; then")
                        appendLine("  echo 'enable 0' > /proc/perfmgr/tchbst/user/usrtch 2>/dev/null")
                        appendLine("  echo 'eas_boost 0' > /proc/perfmgr/tchbst/user/usrtch 2>/dev/null")
                        appendLine("fi")
                    } else {
                        appendLine("if [ -f /proc/perfmgr/tchbst/user/usrtch ]; then")
                        appendLine("  echo 'enable 1' > /proc/perfmgr/tchbst/user/usrtch 2>/dev/null")
                        appendLine("  echo 'eas_boost ${boostPolicy.mediatekTouchBoost}' > /proc/perfmgr/tchbst/user/usrtch 2>/dev/null")
                        appendLine("  echo 'touch_opp 2' > /proc/perfmgr/tchbst/user/usrtch 2>/dev/null")
                        appendLine("fi")
                    }
                    appendLine("[ -f /sys/devices/platform/goodix_ts.0/game_mode ] && echo 0 > /sys/devices/platform/goodix_ts.0/game_mode 2>/dev/null")
                    appendLine("[ -f /sys/devices/platform/tp_wake_switch/game_mode ] && echo 0 > /sys/devices/platform/tp_wake_switch/game_mode 2>/dev/null")
                    val taBoostRestore = boostPolicy.topAppSchedtuneBoost.toString()
                    val fgBoostRestore = boostPolicy.foregroundSchedtuneBoost.toString()
                    appendLine("if [ -d /proc/perfmgr/boost_ctrl/eas_ctrl ]; then")
                    appendLine("  echo $taBoostRestore > /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_boost 2>/dev/null")
                    appendLine("  echo $fgBoostRestore > /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_fg_boost 2>/dev/null")
                    appendLine("  echo ${if (isBattery) "0" else "1"} > /proc/perfmgr/boost_ctrl/eas_ctrl/sched_big_task_rotation 2>/dev/null")
                    appendLine("fi")

                    // Restore Multi-Vendor Devfreq Memory Bus & L3 Governors
                    appendLine("for bw in /sys/class/devfreq/*; do")
                    appendLine("  [ -d \"\$bw\" ] || continue")
                    appendLine("  case \"\$bw\" in")
                    appendLine("    *kgsl-3d0*|*kgsl-3d*) continue ;;")
                    appendLine("    *latfloor*) [ -w \"\$bw/governor\" ] && echo 'compute' > \"\$bw/governor\" 2>/dev/null ;;")
                    if (isBattery) {
                        appendLine("    *cpubw*|*llccbw*|*cpu-ddr-bw*|*ddr-bw*|*ufshc*|*devfreq_mif*|*exynos-bus*|*ddr*|*scene*|*dsu*) [ -w \"\$bw/governor\" ] && echo 'powersave' > \"\$bw/governor\" 2>/dev/null ;;")
                    } else {
                        appendLine("    *cpubw*|*llccbw*|*cpu-ddr-bw*|*ddr-bw*) [ -w \"\$bw/governor\" ] && echo 'bw_hwmon' > \"\$bw/governor\" 2>/dev/null ;;")
                        appendLine("    *ufshc*|*devfreq_mif*|*exynos-bus*|*ddr*|*scene*|*dsu*) [ -w \"\$bw/governor\" ] && (echo 'simple_ondemand' > \"\$bw/governor\" 2>/dev/null || echo 'interactive' > \"\$bw/governor\" 2>/dev/null) ;;")
                    }
                    appendLine("    *l3-cpu*|*memlat-cpu*|*cpu-ddr-lat*) [ -w \"\$bw/governor\" ] && echo 'mem_latency' > \"\$bw/governor\" 2>/dev/null ;;")
                    appendLine("    *gpubw*) [ -w \"\$bw/governor\" ] && echo 'bw_vbif' > \"\$bw/governor\" 2>/dev/null ;;")
                    appendLine("    *mincpubw*) [ -w \"\$bw/governor\" ] && echo 'cpufreq' > \"\$bw/governor\" 2>/dev/null ;;")
                    appendLine("    *venus_bus_ddr*) [ -w \"\$bw/governor\" ] && echo 'msm-vidc-ddr' > \"\$bw/governor\" 2>/dev/null ;;")
                    appendLine("    *venus_bus_llcc*) [ -w \"\$bw/governor\" ] && echo 'msm-vidc-llcc' > \"\$bw/governor\" 2>/dev/null ;;")
                    appendLine("    *snoc_cnoc_keepalive*) [ -w \"\$bw/governor\" ] && echo 'powersave' > \"\$bw/governor\" 2>/dev/null ;;")
                    appendLine("  esac")
                    appendLine("  if [ -s \"\$bw/available_frequencies\" ]; then")
                    appendLine("    lo_f=\$(tr ' ' '\\n' < \"\$bw/available_frequencies\" | sort -n | sed '/^\$/d' | head -n1)")
                    appendLine("    hi_f=\$(tr ' ' '\\n' < \"\$bw/available_frequencies\" | sort -n | tail -n1)")
                    appendLine("    [ -n \"\$lo_f\" ] && echo \"\$lo_f\" > \"\$bw/min_freq\" 2>/dev/null")
                    appendLine("    [ -n \"\$hi_f\" ] && echo \"\$hi_f\" > \"\$bw/max_freq\" 2>/dev/null")
                    appendLine("  fi")
                    appendLine("done")
                    appendLine("[ -w /sys/devices/platform/soc/1d84000.ufshc/clkgate_delay_ms_perf ] && echo 100 > /sys/devices/platform/soc/1d84000.ufshc/clkgate_delay_ms_perf 2>/dev/null")
                    appendLine("for cpu_lb in /sys/devices/system/cpu/cpu[0-9]*/sched_load_boost; do [ -w \"\$cpu_lb\" ] && echo $qcomLoadBoostVal > \"\$cpu_lb\" 2>/dev/null; done")
                    appendLine("[ -f /sys/module/msm_thermal/core_control/enabled ] && echo 1 > /sys/module/msm_thermal/core_control/enabled 2>/dev/null")
                    appendLine("[ -f /sys/module/msm_thermal/parameters/enabled ] && echo 'Y' > /sys/module/msm_thermal/parameters/enabled 2>/dev/null")
                }
                appendLine("[ -f /sys/module/msm_performance/parameters/touchboost ] && echo '${if (isExtremeOrPerf || isBalanced) "1" else "0"}' > /sys/module/msm_performance/parameters/touchboost 2>/dev/null")
                }

                // 7. FINAL STEP: Enforce Final Frequency Bounds, Core Parking & Permissions Locking/Unlocking
                params.clusterTargets.forEach { (cid, tgt) ->
                    val pDir = "/sys/devices/system/cpu/cpufreq/policy$cid"
                    appendLine("if [ -d '$pDir' ]; then")
                    appendLine("  chmod 644 '$pDir/scaling_min_freq' '$pDir/scaling_max_freq' 2>/dev/null")
                    appendLine("  echo '${tgt.maxFreq}' > '$pDir/scaling_max_freq' 2>/dev/null")
                    appendLine("  echo '${tgt.minFreq}' > '$pDir/scaling_min_freq' 2>/dev/null")
                    if (tgt.isLocked) {
                        appendLine("  chmod 444 '$pDir/scaling_min_freq' '$pDir/scaling_max_freq' 2>/dev/null")
                    }
                    appendLine("fi")
                }
                if (params.parkingMode == "park_big") {
                    appendLine("for c in \$big_cores; do")
                    appendLine("  [ -f \"/sys/devices/system/cpu/cpu\$c/online\" ] && echo 0 > \"/sys/devices/system/cpu/cpu\$c/online\" 2>/dev/null")
                    appendLine("done")
                }

                // 8. Properties & Config
                val batchBusProfile = when (params.companionProfile.lowercase()) {
                    "powersave", "battery" -> "eco"
                    "performance", "extreme" -> "max"
                    else -> "auto"
                }
                if (params.updateMasterProfile) {
                    appendLine("mkdir -p /data/adb/lynx 2>/dev/null")
                    appendLine("setprop lynx.mode '${params.companionProfile}' 2>/dev/null")
                    appendLine("echo '${params.companionProfile}' > /data/adb/lynx/active_profile 2>/dev/null")
                    if (params.companionProfile != "auto") {
                        appendLine("echo '${params.companionProfile}' > /data/adb/lynx/baseline_profile 2>/dev/null")
                    }
                }
                if (params.updateMasterProfile) {
                    appendLine("echo '$batchBusProfile' > /data/adb/lynx/bus_profile 2>/dev/null")
                }
                appendLine("echo 'done'")
            }

            if (params.updateMasterProfile) {
                LynxRepository.writeStateKey("active_profile", params.companionProfile, "str")
            }
            val res = Shell.cmd(script).exec()
            val ok = res.isSuccess
            if (ok) {
                if (context != null) LynxRepository.appContext = context.applicationContext
                val ctx = context ?: LynxRepository.appContext
                val batchBusProfile = when (params.companionProfile.lowercase()) {
                    "powersave", "battery" -> "eco"
                    "performance", "extreme" -> "max"
                    else -> "auto"
                }
                val isPerfOrExt = params.companionProfile.equals("performance", true) || params.companionProfile.equals("extreme", true)
                val mtkPwrMode = when (params.companionProfile.lowercase()) {
                    "powersave", "battery" -> 1
                    "performance", "extreme" -> 3
                    else -> 0
                }
                ctx?.getSharedPreferences("lynx_scheduler_prefs", Context.MODE_PRIVATE)
                    ?.edit()
                    ?.putString("active_preset", params.schedPreset)
                    ?.putInt("sched_upmigrate", params.schedHystUp)
                    ?.putInt("sched_downmigrate", params.schedHystDown)
                    ?.putString("bus_bandwidth_profile", batchBusProfile)
                    ?.putBoolean("mtk_cci_ddr_boost", isPerfOrExt)
                    ?.putInt("mtk_cpu_power_mode", mtkPwrMode)
                    ?.putBoolean("universal_touch_boost", isPerfOrExt)
                    ?.putBoolean("qcom_touchboost", isPerfOrExt)
                    ?.putBoolean("anti_throttling_guard", isPerfOrExt)
                    ?.putBoolean("mtk_dlpt_imax_bypass", isPerfOrExt)
                    ?.apply()
                ctx?.getSharedPreferences("lynx_cpuidle_prefs", Context.MODE_PRIVATE)
                    ?.edit()
                    ?.putString("active_preset", params.idlePreset)
                    ?.putString("core_parking_mode", params.parkingMode)
                    ?.apply()
                ctx?.getSharedPreferences("lynx_cpuset_prefs", Context.MODE_PRIVATE)
                    ?.edit()
                    ?.putString("active_preset", params.cpuSetPreset)
                    ?.apply()
            }
            ok
        } catch (e: Exception) {
            Log.e("LynxRepository", "applyCpuBatchProfile failed: ${e.message}")
            false
        }
    }

    // ----------------------------------------------------------------
    //  Phase 1 Quick Wins: GPU Advanced Control & Graphics Processes
    // ----------------------------------------------------------------
}
