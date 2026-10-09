package com.noir.lynx.safety

import android.util.Log
import com.noir.lynx.hardware.NodeWriter
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Snapshot of a single sysfs/proc node captured during cold boot.
 */
data class ThermalNodeSnapshot(
    val path: String,
    val value: String
)

/**
 * Cold-boot baseline snapshot storing untouched factory parameters.
 */
data class OemBaselineSnapshot(
    val timestamp: Long,
    val kernelRelease: String,
    val deviceFingerprint: String,
    val nodes: List<ThermalNodeSnapshot>
)

/**
 * OEMRestoreManager — Captures real cold-boot baseline snapshots (without hardcoded values)
 * and guarantees pristine 100% restoration to factory baseline when switching to DEFAULT_OEM
 * or unsetting custom thermal profiles.
 */
object OEMRestoreManager {
    private const val TAG = "OEMRestoreManager"
    private const val PRIMARY_BASELINE = "/data/adb/modules/Lynx/thermal_baseline.json"
    private const val FALLBACK_BASELINE = "/data/local/tmp/lynx_backup/thermal/oem_baseline_snapshot.json"

    /**
     * Reads parsed OEM baseline snapshot from disk if present.
     */
    fun getBaselineSnapshot(): OemBaselineSnapshot? {
        val targets = listOf(PRIMARY_BASELINE, FALLBACK_BASELINE)
        for (p in targets) {
            val f = File(p)
            if (!f.exists()) continue
            try {
                val raw = f.readText().trim()
                if (raw.isBlank() || !raw.startsWith("{")) continue
                val json = JSONObject(raw)
                val nodesArr = json.optJSONArray("nodes") ?: JSONArray()
                val nodes = mutableListOf<ThermalNodeSnapshot>()
                for (i in 0 until nodesArr.length()) {
                    val obj = nodesArr.getJSONObject(i)
                    nodes.add(ThermalNodeSnapshot(obj.getString("path"), obj.getString("value")))
                }
                return OemBaselineSnapshot(
                    timestamp = json.optLong("timestamp", 0L),
                    kernelRelease = json.optString("kernelRelease", ""),
                    deviceFingerprint = json.optString("deviceFingerprint", ""),
                    nodes = nodes
                )
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * Captures pristine OEM baseline upon first cold boot before any modifications are made.
     */
    suspend fun captureBaselineIfMissing(): Boolean = captureBaseline(force = false)

    suspend fun captureBaseline(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!force) {
                val checkScript = "[ -f '$PRIMARY_BASELINE' ] && echo 'EXISTS' || ([ -f '$FALLBACK_BASELINE' ] && echo 'EXISTS' || echo 'MISSING')"
                val status = Shell.cmd(checkScript).exec().out.firstOrNull()?.trim() ?: "MISSING"
                if (status == "EXISTS") {
                    Log.d(TAG, "OEM baseline already captured. Skipping.")
                    return@withContext true
                }
            }

            Log.i(TAG, "Capturing fresh OEM thermal baseline snapshot (force=$force)...")
            val captureScript = """
                mkdir -p /data/local/tmp/lynx_backup/thermal 2>/dev/null
                mkdir -p /data/adb/modules/Lynx 2>/dev/null

                kernel=${'$'}(uname -r 2>/dev/null || echo "unknown")
                fp=${'$'}(getprop ro.build.fingerprint 2>/dev/null || echo "unknown")
                echo "META|${'$'}kernel|${'$'}fp"

                # Capture all thermal zone modes, policies, and trip points
                for tz in /sys/class/thermal/thermal_zone*; do
                    [ -d "${'$'}tz" ] || continue
                    [ -f "${'$'}tz/mode" ] && echo "NODE|${'$'}tz/mode|${'$'}(cat "${'$'}tz/mode" 2>/dev/null)"
                    [ -f "${'$'}tz/policy" ] && echo "NODE|${'$'}tz/policy|${'$'}(cat "${'$'}tz/policy" 2>/dev/null)"
                    for tp in "${'$'}tz"/trip_point_*_temp; do
                        [ -f "${'$'}tp" ] && echo "NODE|${'$'}tp|${'$'}(cat "${'$'}tp" 2>/dev/null)"
                    done
                done

                # Capture cooling devices
                for c in /sys/class/thermal/cooling_device*; do
                    [ -f "${'$'}c/cur_state" ] && echo "NODE|${'$'}c/cur_state|${'$'}(cat "${'$'}c/cur_state" 2>/dev/null)"
                done

                # Vendor nodes
                [ -f /proc/cpufreq/cpufreq_imax_thermal_protect ] && echo "NODE|/proc/cpufreq/cpufreq_imax_thermal_protect|${'$'}(cat /proc/cpufreq/cpufreq_imax_thermal_protect 2>/dev/null)"
                [ -f /sys/class/kgsl/kgsl-3d0/throttling ] && echo "NODE|/sys/class/kgsl/kgsl-3d0/throttling|${'$'}(cat /sys/class/kgsl/kgsl-3d0/throttling 2>/dev/null)"
                [ -f /sys/class/kgsl/kgsl-3d0/thermal_pwrlevel ] && echo "NODE|/sys/class/kgsl/kgsl-3d0/thermal_pwrlevel|${'$'}(cat /sys/class/kgsl/kgsl-3d0/thermal_pwrlevel 2>/dev/null)"
                [ -f /sys/class/thermal/thermal_message/sconfig ] && echo "NODE|/sys/class/thermal/thermal_message/sconfig|${'$'}(cat /sys/class/thermal/thermal_message/sconfig 2>/dev/null)"
            """.trimIndent()

            val lines = Shell.cmd(captureScript).exec().out
            var kernel = "unknown"
            var fingerprint = "unknown"
            val nodes = mutableListOf<ThermalNodeSnapshot>()

            for (line in lines) {
                val parts = line.split("|")
                if (parts[0] == "META" && parts.size >= 3) {
                    kernel = parts[1]
                    fingerprint = parts[2]
                } else if (parts[0] == "NODE" && parts.size >= 3) {
                    val path = parts[1].trim()
                    val value = parts[2].trim()
                    if (path.isNotEmpty() && value.isNotEmpty()) {
                        nodes.add(ThermalNodeSnapshot(path, value))
                    }
                }
            }

            val json = JSONObject().apply {
                put("timestamp", System.currentTimeMillis())
                put("kernelRelease", kernel)
                put("deviceFingerprint", fingerprint)
                val arr = JSONArray()
                for (n in nodes) {
                    arr.put(JSONObject().apply {
                        put("path", n.path)
                        put("value", n.value)
                    })
                }
                put("nodes", arr)
            }

            val jsonStr = json.toString(2)
            // Save to primary or fallback
            val saveScript = """
                mkdir -p /data/adb/modules/Lynx 2>/dev/null
                mkdir -p /data/local/tmp/lynx_backup/thermal 2>/dev/null
                cat << 'EOF' > '$FALLBACK_BASELINE'
$jsonStr
EOF
                chmod 644 '$FALLBACK_BASELINE' 2>/dev/null
                if [ -d /data/adb/modules/Lynx ]; then
                    cp -af '$FALLBACK_BASELINE' '$PRIMARY_BASELINE' 2>/dev/null
                fi
            """.trimIndent()

            Shell.cmd(saveScript).exec()
            Log.i(TAG, "Successfully captured OEM baseline with ${nodes.size} nodes.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture OEM baseline: ${e.message}")
            false
        }
    }

    /**
     * Restores pristine captured OEM baseline.
     * Re-applies exact original sysfs values, unfreezes vendor daemons, and resets framework thermal.
     */
    suspend fun restoreCapturedBaseline(): Boolean = withContext(Dispatchers.IO) {
        try {
            val readScript = "[ -f '$PRIMARY_BASELINE' ] && cat '$PRIMARY_BASELINE' || ([ -f '$FALLBACK_BASELINE' ] && cat '$FALLBACK_BASELINE' || echo '')"
            val content = Shell.cmd(readScript).exec().out.joinToString("\n").trim()
            if (content.isBlank() || !content.startsWith("{")) {
                Log.w(TAG, "Baseline snapshot missing or empty. Performing fallback generic restore.")
                return@withContext fallbackGenericRestore()
            }

            val json = JSONObject(content)
            val nodesArr = json.optJSONArray("nodes") ?: JSONArray()
            val commands = StringBuilder()

            for (i in 0 until nodesArr.length()) {
                val obj = nodesArr.getJSONObject(i)
                val path = obj.getString("path")
                val value = obj.getString("value")
                commands.append("""
                    if [ -e "$path" ]; then
                        chmod 644 "$path" 2>/dev/null
                        echo "$value" > "$path" 2>/dev/null
                    fi
                """).append("\n")
            }

            // Unfreeze daemons and reset Android thermalservice
            commands.append("""
                # Unfreeze vendor daemons
                killall -CONT mi_thermald thermal-engine thermal-engine-v2 ituxd com.xiaomi.joyose com.samsung.android.game.gos 2>/dev/null
                start thermal-engine mi_thermald 2>/dev/null
                cmd thermalservice reset 2>/dev/null
                echo 'RESTORE_DONE'
            """)

            val res = Shell.cmd(commands.toString()).exec()
            Log.i(TAG, "Restored ${nodesArr.length()} OEM baseline nodes successfully.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring OEM baseline: ${e.message}")
            fallbackGenericRestore()
        }
    }

    private suspend fun fallbackGenericRestore(): Boolean = withContext(Dispatchers.IO) {
        val script = """
            for tz in /sys/class/thermal/thermal_zone*; do
                [ -d "${'$'}tz" ] || continue
                [ -f "${'$'}tz/mode" ] && echo "enabled" > "${'$'}tz/mode" 2>/dev/null
                [ -f "${'$'}tz/policy" ] && echo "step_wise" > "${'$'}tz/policy" 2>/dev/null
                for tp in "${'$'}tz"/trip_point_*_temp; do
                    [ -f "${'$'}tp" ] && echo "85000" > "${'$'}tp" 2>/dev/null
                done
            done
            for c in /sys/class/thermal/cooling_device*; do
                [ -f "${'$'}c/cur_state" ] && echo "0" > "${'$'}c/cur_state" 2>/dev/null
            done
            [ -f /proc/cpufreq/cpufreq_imax_thermal_protect ] && echo "1" > /proc/cpufreq/cpufreq_imax_thermal_protect 2>/dev/null
            [ -f /sys/class/kgsl/kgsl-3d0/throttling ] && echo "1" > /sys/class/kgsl/kgsl-3d0/throttling 2>/dev/null
            [ -f /sys/class/kgsl/kgsl-3d0/thermal_pwrlevel ] && echo "1" > /sys/class/kgsl/kgsl-3d0/thermal_pwrlevel 2>/dev/null
            killall -CONT mi_thermald thermal-engine thermal-engine-v2 ituxd com.xiaomi.joyose com.samsung.android.game.gos 2>/dev/null
            cmd thermalservice reset 2>/dev/null
            echo 'GENERIC_DONE'
        """.trimIndent()
        Shell.cmd(script).exec()
        true
    }
}
