package com.noir.lynx.data
import android.util.Log
import com.noir.lynx.hardware.NodeWriter
import com.noir.lynx.hardware.WriteResult
import com.noir.lynx.hardware.gpu.GpuBackendManager
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
internal object LynxGpuControlRepository {
    suspend fun writeGpuFeature(nodePath: String, value: String): WriteResult = withContext(Dispatchers.IO) {
        try {
            NodeWriter.writeVerified(nodePath, value)
        } catch (e: Exception) {
            WriteResult.Rejected("Eksepsi: ${e.message}")
        }
    }

    suspend fun setGpuBoostLevel(level: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val adapterRes = GpuBackendManager.getActiveAdapter().setBoost(level)

            val script = """
                if [ -d /sys/class/kgsl/kgsl-3d0 ]; then
                    for b in /sys/class/kgsl/kgsl-3d0/devfreq/adrenoboost /sys/class/devfreq/*kgsl*/adrenoboost; do
                        if [ -f "${'$'}b" ]; then
                            chmod 644 "${'$'}b" 2>/dev/null
                            echo $level > "${'$'}b" 2>/dev/null
                        fi
                    done
                    echo ok
                elif [ -d /sys/module/ged/parameters ] || [ -d /sys/kernel/ged/hal ]; then
                    if [ "$level" = "0" ]; then
                        chmod 644 /sys/module/ged/parameters/ged_boost_enable 2>/dev/null
                        echo 0 > /sys/module/ged/parameters/ged_boost_enable 2>/dev/null
                        echo 0 > /sys/module/ged/parameters/gx_game_mode 2>/dev/null
                        echo 0 > /sys/module/ged/parameters/boost_amp 2>/dev/null
                        echo 0 > /sys/module/ged/parameters/gx_boost_on 2>/dev/null
                        echo 0 > /sys/module/ged/parameters/ged_smart_boost 2>/dev/null
                        echo 0 > /sys/kernel/ged/hal/gpu_boost_level 2>/dev/null
                    elif [ "$level" = "1" ]; then
                        chmod 644 /sys/module/ged/parameters/ged_boost_enable 2>/dev/null
                        echo 1 > /sys/module/ged/parameters/ged_boost_enable 2>/dev/null
                        echo 0 > /sys/module/ged/parameters/gx_game_mode 2>/dev/null
                        echo 1 > /sys/module/ged/parameters/boost_amp 2>/dev/null
                        echo 1 > /sys/module/ged/parameters/ged_smart_boost 2>/dev/null
                        echo 1 > /sys/kernel/ged/hal/gpu_boost_level 2>/dev/null
                    elif [ "$level" = "2" ]; then
                        chmod 644 /sys/module/ged/parameters/ged_boost_enable 2>/dev/null
                        echo 2 > /sys/module/ged/parameters/ged_boost_enable 2>/dev/null
                        echo 1 > /sys/module/ged/parameters/gx_game_mode 2>/dev/null
                        echo 2 > /sys/module/ged/parameters/boost_amp 2>/dev/null
                        echo 1 > /sys/module/ged/parameters/gx_boost_on 2>/dev/null
                        echo 1 > /sys/module/ged/parameters/enable_gpu_boost 2>/dev/null
                        echo 1 > /sys/module/ged/parameters/ged_smart_boost 2>/dev/null
                        echo 2 > /sys/kernel/ged/hal/gpu_boost_level 2>/dev/null
                    fi
                    echo ok
                else
                    echo ok
                fi
            """.trimIndent()
            val scriptOk = Shell.cmd(script).exec().out.firstOrNull()?.trim() == "ok"
            scriptOk || adapterRes is WriteResult.Applied
        } catch (e: Exception) { false }
    }

    suspend fun setGpuFreq(minHz: Long?, maxHz: Long?): Boolean = withContext(Dispatchers.IO) {
        try {
            fun toMhz(hz: Long?): Int? {
                if (hz == null || hz <= 0L) return null
                return if (hz > 1_000_000L) (hz / 1_000_000L).toInt() else if (hz > 10_000L) (hz / 1000L).toInt() else hz.toInt()
            }
            val minMhzVal = toMhz(minHz)
            val maxMhzVal = toMhz(maxHz)
            val adapterRes = GpuBackendManager.getActiveAdapter().setFrequencyRange(minMhzVal, maxMhzVal)
            if (adapterRes !is WriteResult.Rejected) return@withContext true

            val isLock = minMhzVal != null && maxMhzVal != null && minMhzVal == maxMhzVal
            val script = """
                if [ -d /sys/class/kgsl/kgsl-3d0/devfreq ]; then
                    D="/sys/class/kgsl/kgsl-3d0/devfreq"
                    ${if (minHz != null) "chmod 644 \$D/min_freq 2>/dev/null; echo $minHz > \$D/min_freq 2>/dev/null;" else ""}
                    ${if (maxHz != null) "chmod 644 \$D/max_freq 2>/dev/null; echo $maxHz > \$D/max_freq 2>/dev/null;" else ""}
                    echo ok
                elif [ -d /proc/gpufreq ] || [ -d /sys/module/ged ] || [ -d /sys/kernel/ged/hal ]; then
                    ${if (isLock && maxMhzVal != null) """
                        lockKhz=${maxMhzVal * 1000}
                        [ -e /proc/gpufreq/gpufreq_opp_freq ] && echo ${'$'}lockKhz > /proc/gpufreq/gpufreq_opp_freq 2>/dev/null
                        [ -e /proc/gpufreqv2/gpufreq_opp_freq ] && echo ${'$'}lockKhz > /proc/gpufreqv2/gpufreq_opp_freq 2>/dev/null
                    """ else """
                        [ -e /proc/gpufreq/gpufreq_opp_freq ] && echo 0 > /proc/gpufreq/gpufreq_opp_freq 2>/dev/null
                        [ -e /proc/gpufreqv2/gpufreq_opp_freq ] && echo 0 > /proc/gpufreqv2/gpufreq_opp_freq 2>/dev/null
                    """}
                    ${if (minHz != null) """
                        minKhz=${if (minHz > 1_000_000L) minHz / 1000L else if (minHz < 10_000L) minHz * 1000L else minHz}
                        minIdx=${'$'}(awk -F'[][]' -v f="freq = ${'$'}minKhz," '${'$'}0 ~ f {print int(${'$'}2); exit}' /proc/gpufreq/gpufreq_opp_dump /proc/gpufreqv2/gpu_working_opp_table /proc/gpufreqv2/gpufreq_opp_dump 2>/dev/null)
                        [ -n "${'$'}minIdx" ] && echo ${'$'}minIdx > /sys/kernel/ged/hal/custom_boost_gpu_freq 2>/dev/null
                        chmod 644 /sys/module/ged/parameters/gpu_bottom_freq 2>/dev/null
                        echo ${'$'}minKhz > /sys/module/ged/parameters/gpu_bottom_freq 2>/dev/null
                        chmod 644 /sys/module/ged/parameters/gpu_cust_boost_freq 2>/dev/null
                        echo ${'$'}minKhz > /sys/module/ged/parameters/gpu_cust_boost_freq 2>/dev/null
                    """ else ""}
                    ${if (maxHz != null) """
                        maxKhz=${if (maxHz > 1_000_000L) maxHz / 1000L else if (maxHz < 10_000L) maxHz * 1000L else maxHz}
                        maxIdx=${'$'}(awk -F'[][]' -v f="freq = ${'$'}maxKhz," '${'$'}0 ~ f {print int(${'$'}2); exit}' /proc/gpufreq/gpufreq_opp_dump /proc/gpufreqv2/gpu_working_opp_table /proc/gpufreqv2/gpufreq_opp_dump 2>/dev/null)
                        [ -n "${'$'}maxIdx" ] && echo ${'$'}maxIdx > /sys/kernel/ged/hal/custom_upbound_gpu_freq 2>/dev/null
                        chmod 644 /sys/module/ged/parameters/gpu_cust_upbound_freq 2>/dev/null
                        echo ${'$'}maxKhz > /sys/module/ged/parameters/gpu_cust_upbound_freq 2>/dev/null
                    """ else ""}
                    echo ok
                else
                    devpath=""
                    for d in /sys/class/devfreq/*gpu* /sys/class/devfreq/*mali* /sys/class/devfreq/*sgpu*; do
                        if [ -d "${'$'}d" ]; then devpath="${'$'}d"; break; fi
                    done
                    if [ -n "${'$'}devpath" ]; then
                        ${if (minHz != null) "chmod 644 \$devpath/min_freq 2>/dev/null; echo $minHz > \$devpath/min_freq 2>/dev/null;" else ""}
                        ${if (maxHz != null) "chmod 644 \$devpath/max_freq 2>/dev/null; echo $maxHz > \$devpath/max_freq 2>/dev/null;" else ""}
                        echo ok
                    else
                        echo unsupported
                    fi
                fi
            """.trimIndent()
            Shell.cmd(script).exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    suspend fun setGpuGovernor(governor: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val cleanGov = governor.split(Regex("[\\s•]+")).firstOrNull()?.trim() ?: governor.trim()
            val adapterRes = GpuBackendManager.getActiveAdapter().setGovernor(cleanGov)
            if (adapterRes !is WriteResult.Rejected) return@withContext true

            val script = """
                if [ -d /sys/class/kgsl/kgsl-3d0/devfreq ]; then
                    chmod 644 /sys/class/kgsl/kgsl-3d0/devfreq/governor 2>/dev/null
                    echo '$cleanGov' > /sys/class/kgsl/kgsl-3d0/devfreq/governor 2>/dev/null
                    echo ok
                elif [ -d /sys/kernel/ged/hal ]; then
                    echo '$cleanGov' > /sys/kernel/ged/hal/dvfs_loading_mode 2>/dev/null
                    echo ok
                else
                    devpath=""
                    for d in /sys/class/devfreq/*gpu* /sys/class/devfreq/*mali* /sys/class/devfreq/*sgpu*; do
                        if [ -d "${'$'}d" ]; then devpath="${'$'}d"; break; fi
                    done
                    if [ -n "${'$'}devpath" ]; then
                        chmod 644 "${'$'}devpath/governor" 2>/dev/null
                        echo '$cleanGov' > "${'$'}devpath/governor" 2>/dev/null
                        echo ok
                    else
                        echo unsupported
                    fi
                fi
            """.trimIndent()
            Shell.cmd(script).exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    suspend fun setGpuThermalBypass(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val adapterRes = GpuBackendManager.getActiveAdapter().setThermalBypass(enabled)

            val limitEnable = if (enabled) 0 else 1
            val script = """
                if [ -d /sys/class/kgsl/kgsl-3d0 ]; then
                    chmod 644 /sys/class/kgsl/kgsl-3d0/thermal_pwrlevel 2>/dev/null
                    chmod 644 /sys/class/kgsl/kgsl-3d0/throttling 2>/dev/null
                    echo $limitEnable > /sys/class/kgsl/kgsl-3d0/thermal_pwrlevel 2>/dev/null
                    echo $limitEnable > /sys/class/kgsl/kgsl-3d0/throttling 2>/dev/null
                    echo ok
                elif [ -f /proc/gpufreq/gpufreq_limit_table ] || [ -f /proc/gpufreqv2/gpufreq_power_limited ] || [ -d /sys/kernel/fpsgo ]; then
                    if [ -f /proc/gpufreq/gpufreq_limit_table ]; then
                        for id in 3 4 5 6 7; do
                            echo "${'$'}id $limitEnable $limitEnable" > /proc/gpufreq/gpufreq_limit_table 2>/dev/null
                        done
                    fi
                    if [ -f /proc/gpufreqv2/gpufreq_power_limited ]; then
                        echo "ignore_thermal_protect ${if (enabled) 1 else 0}" > /proc/gpufreqv2/gpufreq_power_limited 2>/dev/null
                        echo "ignore_pbm_limited ${if (enabled) 1 else 0}" > /proc/gpufreqv2/gpufreq_power_limited 2>/dev/null
                    fi
                    echo $limitEnable > /sys/module/fbt_cpu/parameters/thrm_limit_cpu 2>/dev/null
                    echo $limitEnable > /sys/kernel/fpsgo/fbt/thrm_limit_cpu 2>/dev/null
                    echo $limitEnable > /sys/kernel/eara_thermal/enable 2>/dev/null
                    echo ok
                else
                    echo unsupported
                fi
            """.trimIndent()
            val scriptOk = Shell.cmd(script).exec().out.firstOrNull()?.trim() == "ok"
            scriptOk || adapterRes is WriteResult.Applied
        } catch (e: Exception) { false }
    }

    suspend fun setGpuBusAlwaysOn(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val valStr = if (enabled) "1" else "0"
            val script = """
                if [ -d /sys/class/kgsl/kgsl-3d0 ]; then
                    chmod 644 /sys/class/kgsl/kgsl-3d0/force_bus_on 2>/dev/null
                    chmod 644 /sys/class/kgsl/kgsl-3d0/force_clk_on 2>/dev/null
                    chmod 644 /sys/class/kgsl/kgsl-3d0/force_rail_on 2>/dev/null
                    chmod 644 /sys/class/kgsl/kgsl-3d0/force_no_nap 2>/dev/null
                    echo $valStr > /sys/class/kgsl/kgsl-3d0/force_bus_on 2>/dev/null
                    echo $valStr > /sys/class/kgsl/kgsl-3d0/force_clk_on 2>/dev/null
                    echo $valStr > /sys/class/kgsl/kgsl-3d0/force_rail_on 2>/dev/null
                    echo $valStr > /sys/class/kgsl/kgsl-3d0/force_no_nap 2>/dev/null
                    echo ok
                else
                    echo unsupported
                fi
            """.trimIndent()
            Shell.cmd(script).exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    suspend fun setGpuFramePacing(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val valStr = if (enabled) "1" else "0"
            val script = """
                if [ -d /sys/kernel/fpsgo/common ] || [ -d /sys/kernel/fpsgo/fbt ]; then
                    echo $valStr > /sys/kernel/fpsgo/common/gpu_block_boost 2>/dev/null
                    echo $valStr > /sys/kernel/fpsgo/common/fpsgo_enable 2>/dev/null
                    echo $valStr > /sys/kernel/fpsgo/fbt/boost_ta 2>/dev/null
                    echo ok
                else
                    echo unsupported
                fi
            """.trimIndent()
            Shell.cmd(script).exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    suspend fun setGpuIdleTimer(ms: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val script = """
                if [ -f /sys/class/kgsl/kgsl-3d0/idle_timer ]; then
                    chmod 644 /sys/class/kgsl/kgsl-3d0/idle_timer 2>/dev/null
                    echo $ms > /sys/class/kgsl/kgsl-3d0/idle_timer 2>/dev/null
                    echo ok
                else
                    echo unsupported
                fi
            """.trimIndent()
            Shell.cmd(script).exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    suspend fun setMaliDvfsMargin(margin: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val script = """
                if [ -f /sys/kernel/ged/hal/dvfs_margin_value ]; then
                    chmod 644 /sys/kernel/ged/hal/dvfs_margin_value 2>/dev/null
                    echo $margin > /sys/kernel/ged/hal/dvfs_margin_value 2>/dev/null
                    echo ok
                else
                    echo unsupported
                fi
            """.trimIndent()
            Shell.cmd(script).exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    suspend fun setAdrenoPwrLevel(pwrLevel: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val script = """
                if [ -d /sys/class/kgsl/kgsl-3d0 ]; then
                    chmod 644 /sys/class/kgsl/kgsl-3d0/min_pwrlevel 2>/dev/null
                    chmod 644 /sys/class/kgsl/kgsl-3d0/max_pwrlevel 2>/dev/null
                    chmod 644 /sys/class/kgsl/kgsl-3d0/default_pwrlevel 2>/dev/null
                    echo $pwrLevel > /sys/class/kgsl/kgsl-3d0/min_pwrlevel 2>/dev/null
                    echo $pwrLevel > /sys/class/kgsl/kgsl-3d0/max_pwrlevel 2>/dev/null
                    echo $pwrLevel > /sys/class/kgsl/kgsl-3d0/default_pwrlevel 2>/dev/null
                    echo ok
                else
                    echo unsupported
                fi
            """.trimIndent()
            Shell.cmd(script).exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    suspend fun setAdrenoTzTargetLoad(targetLoad: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val script = """
                node="/sys/class/kgsl/kgsl-3d0/pwrscale/trustzone/target_load"
                if [ -f "${'$'}node" ]; then
                    chmod 644 "${'$'}node" 2>/dev/null
                    echo $targetLoad > "${'$'}node" 2>/dev/null
                    echo ok
                else
                    echo unsupported
                fi
            """.trimIndent()
            Shell.cmd(script).exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    suspend fun setAdrenoForceRail(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val v = if (enabled) "1" else "0"
            val script = """
                if [ -d /sys/class/kgsl/kgsl-3d0 ]; then
                    for f in force_rail_on force_bus_on force_clk_on; do
                        node="/sys/class/kgsl/kgsl-3d0/${'$'}f"
                        if [ -f "${'$'}node" ]; then
                            chmod 644 "${'$'}node" 2>/dev/null
                            echo $v > "${'$'}node" 2>/dev/null
                        fi
                    done
                    echo ok
                else
                    echo unsupported
                fi
            """.trimIndent()
            Shell.cmd(script).exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    suspend fun setFpsgoUltraRescue(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val v = if (enabled) "1" else "0"
            val script = """
                for n in /sys/kernel/fpsgo/fbt/ultra_rescue /sys/kernel/fpsgo/common/ultra_rescue; do
                    if [ -e "${'$'}n" ]; then
                        echo $v > "${'$'}n" 2>/dev/null
                    fi
                done
                echo ok
            """.trimIndent()
            Shell.cmd(script).exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    suspend fun setMaliCoreMask(unmaskAll: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val script = """
                for mask in /sys/devices/platform/*mali*/core_mask /sys/devices/platform/soc/*mali*/core_mask /sys/class/misc/mali0/device/core_mask; do
                    if [ -f "${'$'}mask" ]; then
                        chmod 644 "${'$'}mask" 2>/dev/null
                        echo ${if (unmaskAll) "0xFF" else "0x0F"} > "${'$'}mask" 2>/dev/null
                    fi
                done
                echo ok
            """.trimIndent()
            Shell.cmd(script).exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    suspend fun setMaliPowerPolicy(policy: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val script = """
                for pol in /sys/devices/platform/*mali*/power_policy /sys/devices/platform/soc/*mali*/power_policy /sys/class/misc/mali0/device/power_policy; do
                    if [ -f "${'$'}pol" ]; then
                        chmod 644 "${'$'}pol" 2>/dev/null
                        echo "$policy" > "${'$'}pol" 2>/dev/null
                    fi
                done
                if [ -e /proc/mali/always_on ]; then
                    echo ${if (policy == "always_on") "1" else "0"} > /proc/mali/always_on 2>/dev/null
                fi
                echo ok
            """.trimIndent()
            Shell.cmd(script).exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    suspend fun setGpuPowerPolicy(policy: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val adapterRes = GpuBackendManager.getActiveAdapter().applyPowerPolicy(policy)
            if (adapterRes !is WriteResult.Rejected) return@withContext true

            val isPerf = policy == "always_on"
            setMaliPowerPolicy(policy)
            if (isPerf) {
                setGpuGovernor("1")
                setGpuIdleTimer(80)
            } else {
                setGpuGovernor("0")
                setGpuIdleTimer(20)
            }
            true
        } catch (e: Exception) { false }
    }

    suspend fun setSurfaceFlingerLatchUnsignaled(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val v = if (enabled) "1" else "0"
            Shell.cmd("setprop debug.sf.latch_unsignaled $v").exec().isSuccess
        } catch (e: Exception) { false }
    }

    suspend fun setSurfaceFlingerDisableBackpressure(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val v = if (enabled) "1" else "0"
            val glV = if (enabled) "0" else "1"
            Shell.cmd("setprop debug.sf.disable_backpressure $v; setprop debug.sf.enable_gl_backpressure $glV").exec().isSuccess
        } catch (e: Exception) { false }
    }

    suspend fun applyGpuProfile(profile: String, gpu: GpuInfo): Boolean = withContext(Dispatchers.IO) {
        try {
            val freqs = gpu.availFreqsMhz.sorted()
            val minAvail = freqs.firstOrNull() ?: 300
            val maxAvail = freqs.lastOrNull() ?: 850

            when (profile) {
                "battery" -> {
                    val halfMax = freqs.filter { it <= (maxAvail * 0.65).toInt() }.lastOrNull() ?: minAvail
                    setGpuFreq(minAvail.toLong() * 1_000_000L, halfMax.toLong() * 1_000_000L)
                    setGpuBoostLevel(0)
                    setGpuThermalBypass(false)
                    setGpuBusAlwaysOn(false)
                    setGpuFramePacing(false)
                    setGpuIdleTimer(20)
                    setMaliDvfsMargin(0)
                    setFpsgoUltraRescue(false)
                    setMaliPowerPolicy("coarse_demand")
                    Shell.cmd(
                        "chmod 644 /sys/module/ged/parameters/ged_monitor_3D_fence_disable 2>/dev/null; " +
                        "echo 0 > /sys/module/ged/parameters/ged_monitor_3D_fence_disable 2>/dev/null; " +
                        "for d in /sys/devices/platform/*.mali /sys/devices/platform/*mali* /sys/class/misc/mali0/device; do " +
                        "[ -f \"${'$'}d/dvfs_period\" ] && echo 100 > \"${'$'}d/dvfs_period\" 2>/dev/null; done"
                    ).exec()
                    setSurfaceFlingerLatchUnsignaled(false)
                    setSurfaceFlingerDisableBackpressure(false)
                }
                "balanced" -> {
                    setGpuFreq(minAvail.toLong() * 1_000_000L, maxAvail.toLong() * 1_000_000L)
                    setGpuBoostLevel(1)
                    setGpuThermalBypass(false)
                    setGpuBusAlwaysOn(false)
                    setGpuFramePacing(false)
                    setGpuIdleTimer(64)
                    setMaliDvfsMargin(10)
                    setFpsgoUltraRescue(false)
                    setMaliPowerPolicy("coarse_demand")
                    Shell.cmd(
                        "chmod 644 /sys/module/ged/parameters/ged_monitor_3D_fence_disable 2>/dev/null; " +
                        "echo 0 > /sys/module/ged/parameters/ged_monitor_3D_fence_disable 2>/dev/null; " +
                        "for d in /sys/devices/platform/*.mali /sys/devices/platform/*mali* /sys/class/misc/mali0/device; do " +
                        "[ -f \"${'$'}d/dvfs_period\" ] && echo 50 > \"${'$'}d/dvfs_period\" 2>/dev/null; done"
                    ).exec()
                    setSurfaceFlingerLatchUnsignaled(true)
                    setSurfaceFlingerDisableBackpressure(false)
                }
                "esports" -> {
                    val floor = freqs.filter { it >= (maxAvail * 0.65).toInt() }.firstOrNull() ?: minAvail
                    setGpuFreq(floor.toLong() * 1_000_000L, maxAvail.toLong() * 1_000_000L)
                    setGpuBoostLevel(2)
                    setGpuThermalBypass(false)
                    setGpuBusAlwaysOn(true)
                    setGpuFramePacing(true)
                    setGpuIdleTimer(80)
                    setMaliDvfsMargin(20)
                    setFpsgoUltraRescue(true)
                    setMaliPowerPolicy("always_on")
                    setMaliCoreMask(true)
                    setAdrenoTzTargetLoad(60)
                    Shell.cmd(
                        "chmod 644 /sys/module/ged/parameters/ged_monitor_3D_fence_disable 2>/dev/null; " +
                        "echo 1 > /sys/module/ged/parameters/ged_monitor_3D_fence_disable 2>/dev/null; " +
                        "for d in /sys/devices/platform/*.mali /sys/devices/platform/*mali* /sys/class/misc/mali0/device; do " +
                        "[ -f \"${'$'}d/dvfs_period\" ] && echo 25 > \"${'$'}d/dvfs_period\" 2>/dev/null; done"
                    ).exec()
                    setSurfaceFlingerLatchUnsignaled(true)
                    setSurfaceFlingerDisableBackpressure(true)
                }
                "extreme" -> {
                    setGpuFreq(maxAvail.toLong() * 1_000_000L, maxAvail.toLong() * 1_000_000L)
                    setGpuBoostLevel(2)
                    setGpuThermalBypass(true)
                    setGpuBusAlwaysOn(true)
                    setGpuFramePacing(true)
                    setGpuIdleTimer(100)
                    setMaliDvfsMargin(30)
                    setFpsgoUltraRescue(true)
                    setMaliPowerPolicy("always_on")
                    setMaliCoreMask(true)
                    setAdrenoPwrLevel(0)
                    setAdrenoTzTargetLoad(50)
                    Shell.cmd(
                        "chmod 644 /sys/module/ged/parameters/ged_monitor_3D_fence_disable 2>/dev/null; " +
                        "echo 1 > /sys/module/ged/parameters/ged_monitor_3D_fence_disable 2>/dev/null; " +
                        "for d in /sys/devices/platform/*.mali /sys/devices/platform/*mali* /sys/class/misc/mali0/device; do " +
                        "[ -f \"${'$'}d/dvfs_period\" ] && echo 10 > \"${'$'}d/dvfs_period\" 2>/dev/null; done"
                    ).exec()
                    setSurfaceFlingerLatchUnsignaled(true)
                    setSurfaceFlingerDisableBackpressure(true)
                }
            }
            true
        } catch (e: Exception) { false }
    }


    // ----------------------------------------------------------------
    //  Universal Compositor & HWUI Texture Cache Tweaks
    // ----------------------------------------------------------------

    suspend fun setTextureCacheSize(mb: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val validMb = mb.coerceIn(48, 128)
            val r1 = Shell.cmd("setprop ro.hwui.texture_cache_size $validMb").exec().isSuccess
            val r2 = Shell.cmd("setprop ro.hwui.layer_cache_size ${(validMb * 0.75).toInt()}").exec().isSuccess
            val r3 = Shell.cmd("setprop ro.hwui.path_cache_size ${(validMb * 0.5).toInt()}").exec().isSuccess
            // Runtime override via system_server props (affects running processes after restart)
            Shell.cmd("service call SurfaceFlinger 1037 2>/dev/null").exec()
            r1 || r2 || r3
        } catch (e: Exception) { false }
    }

    suspend fun setAsyncShaderPipeline(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val v = if (enabled) "true" else "false"
            val r1 = Shell.cmd("setprop debug.hwui.use_gpu_pixel_buffers $v").exec().isSuccess
            val r2 = Shell.cmd("setprop debug.hwui.render_thread $v").exec().isSuccess
            val r3 = Shell.cmd("setprop debug.hwui.use_buffer_age $v").exec().isSuccess
            r1 || r2 || r3
        } catch (e: Exception) { false }
    }

    suspend fun setPureHwVsync(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            // debug.cpurend.vsync=false = Pure Hardware VSync (disable CPU rendering vsync)
            val v = if (enabled) "false" else "true"
            Shell.cmd("setprop debug.cpurend.vsync $v").exec().isSuccess
        } catch (e: Exception) { false }
    }

    suspend fun readNativeResolution(): Triple<String, String, Int> = withContext(Dispatchers.IO) {
        try {
            // Read current wm size and wm density
            val wmOut = Shell.cmd("wm size; wm density").exec().out.joinToString("\n")
            val physicalMatch = Regex("Physical size:\\s*(\\d+x\\d+)").find(wmOut)
            val overrideMatch = Regex("Override size:\\s*(\\d+x\\d+)").find(wmOut)
            val physicalDensity = Regex("Physical density:\\s*(\\d+)").find(wmOut)?.groupValues?.get(1)?.toIntOrNull() ?: 0

            val physical = physicalMatch?.groupValues?.get(1) ?: ""
            val current = overrideMatch?.groupValues?.get(1) ?: physical
            Triple(physical, current, physicalDensity)
        } catch (e: Exception) { Triple("", "", 0) }
    }

    suspend fun setDisplayRenderScale(preset: String): Boolean = withContext(Dispatchers.IO) {
        try {
            when (preset) {
                "native" -> {
                    // Reset to native resolution
                    val r1 = Shell.cmd("wm size reset").exec().isSuccess
                    val r2 = Shell.cmd("wm density reset").exec().isSuccess
                    r1 || r2
                }
                "720p_esports" -> {
                    // Read physical size first to compute correct aspect-ratio scaled 720p
                    val (physical, _, physDpi) = readNativeResolution()
                    val parts = physical.split("x")
                    val physW = parts.getOrNull(0)?.toIntOrNull() ?: 1080
                    val physH = parts.getOrNull(1)?.toIntOrNull() ?: 2400
                    // Maintain aspect ratio: scale down to 720p width
                    val targetW = 720
                    val targetH = ((physH.toFloat() / physW.toFloat()) * targetW).toInt()
                    val targetDpi = ((physDpi.toFloat() / physW.toFloat()) * targetW).toInt().coerceAtLeast(200)
                    val r1 = Shell.cmd("wm size ${targetW}x${targetH}").exec().isSuccess
                    val r2 = Shell.cmd("wm density $targetDpi").exec().isSuccess
                    r1 || r2
                }
                "900p_balanced" -> {
                    val (physical, _, physDpi) = readNativeResolution()
                    val parts = physical.split("x")
                    val physW = parts.getOrNull(0)?.toIntOrNull() ?: 1080
                    val physH = parts.getOrNull(1)?.toIntOrNull() ?: 2400
                    val targetW = 900
                    val targetH = ((physH.toFloat() / physW.toFloat()) * targetW).toInt()
                    val targetDpi = ((physDpi.toFloat() / physW.toFloat()) * targetW).toInt().coerceAtLeast(230)
                    val r1 = Shell.cmd("wm size ${targetW}x${targetH}").exec().isSuccess
                    val r2 = Shell.cmd("wm density $targetDpi").exec().isSuccess
                    r1 || r2
                }
                else -> false
            }
        } catch (e: Exception) { false }
    }

    // ----------------------------------------------------------------
    //  Phase 1 Quick Wins: KSM (Kernel Same-page Merging)
    // ----------------------------------------------------------------


}
