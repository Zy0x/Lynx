package com.noir.lynx.data
import java.io.File
import android.content.Context
import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.abs
internal object LynxCpuClusterRepository {
    fun reapplyClusterLock(policyId: Int, minFreq: Long, maxFreq: Long) {
        if (LynxRepository.explicitlyUnlockedClusters.contains(policyId)) return
        try {
            val pDir = "/sys/devices/system/cpu/cpufreq/policy$policyId"
            val hwMax = Shell.cmd("cat $pDir/cpuinfo_max_freq 2>/dev/null").exec().out.firstOrNull()?.toLongOrNull() ?: 2400000L
            val cmd = buildString {
                append("chmod 644 $pDir/scaling_min_freq $pDir/scaling_max_freq 2>/dev/null; ")
                append("echo $hwMax > $pDir/scaling_max_freq 2>/dev/null; ")
                append("echo $minFreq > $pDir/scaling_min_freq 2>/dev/null; ")
                append("echo $maxFreq > $pDir/scaling_max_freq 2>/dev/null; ")
                // Dynamic MTK cluster discovery:
                append("c_idx=0; ")
                append("for p in /sys/devices/system/cpu/cpufreq/policy*; do ")
                append("if [ \"\${p##*policy}\" = \"$policyId\" ]; then ")
                append("if [ -f /proc/ppm/policy/hard_userlimit_max_cpu_freq ]; then ")
                append("echo \"\$c_idx $maxFreq\" > /proc/ppm/policy/hard_userlimit_max_cpu_freq 2>/dev/null; ")
                append("echo \"\$c_idx $minFreq\" > /proc/ppm/policy/hard_userlimit_min_cpu_freq 2>/dev/null; ")
                append("fi; ")
                append("if [ -f /proc/ppm/policy/userlimit_max_cpu_freq ]; then ")
                append("echo \"\$c_idx $maxFreq\" > /proc/ppm/policy/userlimit_max_cpu_freq 2>/dev/null; ")
                append("echo \"\$c_idx $minFreq\" > /proc/ppm/policy/userlimit_min_cpu_freq 2>/dev/null; ")
                append("fi; ")
                append("break; ")
                append("fi; ")
                append("c_idx=\$((c_idx + 1)); ")
                append("done; ")
                if (minFreq == maxFreq) {
                    append("if [ -f /proc/ppm/policy_status ]; then echo '2 0' > /proc/ppm/policy_status 2>/dev/null; fi; ")
                }
                append("chmod 444 $pDir/scaling_min_freq $pDir/scaling_max_freq 2>/dev/null; ")
            }
            Shell.cmd(cmd).submit()
        } catch (_: Exception) {}
    }

    private var cachedTopology: List<CpuClusterInfo>? = null

    /**
     * Discover dynamic CPU cluster topology.
     * Works both with module and standalone root.
     */
    suspend fun readClusters(): List<CpuClusterInfo> = withContext(Dispatchers.IO) {
        try {
            // Tier 0: Direct Kotlin Sysfs Read (<0.2ms, zero root shell execution)
            val directList = runCatching {
                val cpufreqDir = File("/sys/devices/system/cpu/cpufreq")
                if (cpufreqDir.isDirectory && cpufreqDir.canRead()) {
                    val pDirs = cpufreqDir.listFiles()?.filter { it.isDirectory && it.name.startsWith("policy") }?.sortedBy { it.name }
                    if (!pDirs.isNullOrEmpty()) {
                        val list = mutableListOf<CpuClusterInfo>()
                        val totalClusters = pDirs.size
                        for ((idx, pDir) in pDirs.withIndex()) {
                            val id = pDir.name.removePrefix("policy").toIntOrNull() ?: idx
                            val cpus = runCatching {
                                val affFile = File(pDir, "related_cpus")
                                if (affFile.canRead()) affFile.readText().trim() else File(pDir, "affected_cpus").readText().trim()
                            }.getOrDefault("$id")
                            val min = runCatching { File(pDir, "scaling_min_freq").readText().trim().toLongOrNull() }.getOrNull() ?: 0L
                            val max = runCatching { File(pDir, "scaling_max_freq").readText().trim().toLongOrNull() }.getOrNull() ?: 0L
                            val gov = runCatching { File(pDir, "scaling_governor").readText().trim() }.getOrDefault("schedutil")

                            val cached = cachedTopology?.find { it.id == id }
                            val freqs = cached?.availFreqs?.ifEmpty { null } ?: runCatching {
                                File(pDir, "scaling_available_frequencies").readText().trim().split(Regex("\\s+")).mapNotNull { it.toLongOrNull() }.sorted()
                            }.getOrDefault(emptyList())

                            val govs = cached?.availGovs?.ifEmpty { null } ?: runCatching {
                                File(pDir, "scaling_available_governors").readText().trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                            }.getOrDefault(emptyList())

                            val maxFile = File(pDir, "scaling_max_freq")
                            val isLocked = !maxFile.canWrite()

                            val dynamicRole = when {
                                totalClusters <= 1 -> "Kluster Utama"
                                totalClusters == 2 -> if (idx == 0) "Efisiensi Little" else "Performa Big"
                                else -> when (idx) {
                                    0 -> "Efisiensi Little"
                                    totalClusters - 1 -> "Prime Super"
                                    else -> "Performa Mid"
                                }
                            }

                            list.add(
                                CpuClusterInfo(
                                    id = id,
                                    role = dynamicRole,
                                    cpus = cpus,
                                    curMin = min,
                                    curMax = max,
                                    curGov = gov,
                                    availFreqs = freqs,
                                    availGovs = govs,
                                    isLocked = isLocked
                                )
                            )
                        }
                        if (list.isNotEmpty()) {
                            cachedTopology = list
                            list
                        } else null
                    } else null
                } else null
            }.getOrNull()

            val moduleClusters = if (directList == null && LynxRepository.isModuleInstalled()) {
                try {
                    val cmd = "'$LYNXD_PATH' cluster topology 2>/dev/null"
                    val result = Shell.cmd(cmd).exec()

                    if (result.isSuccess && result.out.isNotEmpty()) {
                        val raw = result.out.joinToString("").trim()
                        if (raw.startsWith("{")) {
                            val json = JSONObject(raw)
                            val arr = json.optJSONArray("clusters")
                            if (arr != null && arr.length() > 0) {
                                val list = mutableListOf<CpuClusterInfo>()
                                for (i in 0 until arr.length()) {
                                    val c = arr.getJSONObject(i)
                                    val freqsArr = c.optJSONArray("avail_freqs")
                                    val freqs = mutableListOf<Long>()
                                    if (freqsArr != null) {
                                        for (j in 0 until freqsArr.length()) freqs.add(freqsArr.optLong(j))
                                    }
                                    freqs.sort()
                                    val govsArr = c.optJSONArray("avail_govs")
                                    val govs = mutableListOf<String>()
                                    if (govsArr != null) {
                                        for (j in 0 until govsArr.length()) govs.add(govsArr.optString(j))
                                    }
                                    list.add(
                                        CpuClusterInfo(
                                            id = c.optInt("id", 0),
                                            role = c.optString("role", "Cluster $i"),
                                            cpus = c.optString("cpus", "$i"),
                                            curMin = c.optLong("cur_min", 0L),
                                            curMax = c.optLong("cur_max", 0L),
                                            curGov = c.optString("cur_gov", "schedutil"),
                                            availFreqs = freqs,
                                            availGovs = govs,
                                            isLocked = c.optBoolean("is_locked", false),
                                        )
                                    )
                                }
                                list
                            } else null
                        } else null
                    } else null
                } catch (e: Exception) {
                    Log.w("LynxRepository", "lynxd cluster topology failed, falling back to sysfs: ${e.message}")
                    null
                }
            } else null

            val rawList = if (directList != null && directList.isNotEmpty()) {
                directList
            } else if (moduleClusters != null && moduleClusters.isNotEmpty()) {
                moduleClusters
            } else {
                val script = """
                    for p in /sys/devices/system/cpu/cpufreq/policy*; do
                      [ -d "${'$'}p" ] || continue
                      id=${'$'}(basename "${'$'}p")
                      echo "id:${'$'}{id#policy}"
                      aff=${'$'}(cat "${'$'}p/related_cpus" 2>/dev/null)
                      [ -z "${'$'}aff" ] && aff=${'$'}(cat "${'$'}p/affected_cpus" 2>/dev/null)
                      echo "aff:${'$'}aff"
                      echo "min:${'$'}(cat "${'$'}p/scaling_min_freq" 2>/dev/null)"
                      echo "max:${'$'}(cat "${'$'}p/scaling_max_freq" 2>/dev/null)"
                      echo "gov:${'$'}(cat "${'$'}p/scaling_governor" 2>/dev/null)"
                      echo "freqs:${'$'}(cat "${'$'}p/scaling_available_frequencies" 2>/dev/null)"
                      echo "govs:${'$'}(cat "${'$'}p/scaling_available_governors" 2>/dev/null)"
                      perms=${'$'}(ls -ld "${'$'}p/scaling_max_freq" 2>/dev/null | awk '{print ${'$'}1}')
                      echo "locked:${'$'}(case "${'$'}perms" in -r--*|-r-xr-x*|*r--r--r--*) echo 1 ;; *) echo 0 ;; esac)"
                      echo "---"
                    done
                """.trimIndent()
                val result = Shell.cmd(script).exec()
                if (!result.isSuccess || result.out.isEmpty()) return@withContext emptyList()
                val list = mutableListOf<CpuClusterInfo>()
                var curId = 0
                var curAff = "0"
                var curMin = 0L
                var curMax = 0L
                var curGov = "schedutil"
                var curFreqs = emptyList<Long>()
                var curGovs = emptyList<String>()
                var curLocked = false

                for (line in result.out) {
                    val trimmed = line.trim()
                    when {
                        trimmed.startsWith("id:") -> curId = trimmed.removePrefix("id:").trim().toIntOrNull() ?: 0
                        trimmed.startsWith("aff:") -> curAff = trimmed.removePrefix("aff:").trim()
                        trimmed.startsWith("min:") -> curMin = trimmed.removePrefix("min:").trim().toLongOrNull() ?: 0L
                        trimmed.startsWith("max:") -> curMax = trimmed.removePrefix("max:").trim().toLongOrNull() ?: 0L
                        trimmed.startsWith("gov:") -> curGov = trimmed.removePrefix("gov:").trim().ifBlank { "schedutil" }
                        trimmed.startsWith("freqs:") -> {
                            val raw = trimmed.removePrefix("freqs:").trim()
                            curFreqs = raw.split(Regex("\\s+")).mapNotNull { it.toLongOrNull() }.sorted()
                        }
                        trimmed.startsWith("govs:") -> {
                            val raw = trimmed.removePrefix("govs:").trim()
                            curGovs = raw.split(Regex("\\s+")).filter { it.isNotBlank() }
                        }
                        trimmed.startsWith("locked:") -> {
                            curLocked = trimmed.removePrefix("locked:").trim() == "1"
                        }
                        trimmed == "---" -> {
                            list.add(
                                CpuClusterInfo(
                                    id = curId,
                                    role = "Kluster $curId",
                                    cpus = curAff,
                                    curMin = curMin,
                                    curMax = curMax,
                                    curGov = curGov,
                                    availFreqs = curFreqs,
                                    availGovs = curGovs,
                                    isLocked = curLocked,
                                )
                            )
                        }
                    }
                }
                val sortedList = list.sortedBy { it.id }
                val totalClusters = sortedList.size
                sortedList.mapIndexed { idx, cl ->
                    val dynamicRole = when {
                        totalClusters <= 1 -> "Kluster Utama"
                        totalClusters == 2 -> if (idx == 0) "Efisiensi Little" else "Performa Big"
                        else -> when (idx) {
                            0 -> "Efisiensi Little"
                            totalClusters - 1 -> "Prime Super"
                            else -> "Performa Mid"
                        }
                    }
                    cl.copy(role = dynamicRole)
                }
            }

            // Enforce locked cluster bounds single source of truth:
            rawList.map { cluster ->
                val isExplicitlyUnlocked = LynxRepository.explicitlyUnlockedClusters.contains(cluster.id)
                val lockedRange = if (isExplicitlyUnlocked) null else LynxRepository.lockedClusterBounds[cluster.id]

                if (isExplicitlyUnlocked) {
                    cluster.copy(isLocked = false)
                } else if (lockedRange != null) {
                    val (lMin, lMax) = lockedRange
                    if (cluster.curMin != lMin || cluster.curMax != lMax || !cluster.isLocked) {
                        reapplyClusterLock(cluster.id, lMin, lMax)
                    }
                    cluster.copy(
                        isLocked = true,
                        curMin = lMin,
                        curMax = lMax
                    )
                } else {
                    cluster
                }
            }
        } catch (e: Exception) {
            Log.e("LynxRepository", "readClusters failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Apply frequency bounds to a CPU cluster policy.
     */
    suspend fun setClusterFreq(policyId: Int, minFreq: Long?, maxFreq: Long?): Boolean = withContext(Dispatchers.IO) {
        try {
            // Tier 0 Fast-Path: lynxd native engine (<2ms, verified safe in Rust)
            if (LynxRepository.isModuleInstalled()) {
                val cmd = "'$LYNXD_PATH' cluster set-freq $policyId '${minFreq ?: 0}' '${maxFreq ?: 0}'"
                val res = Shell.cmd(cmd).exec()
                if (res.isSuccess) return@withContext true
            }

            // Standalone Fallback: Safety Bounds Regulation & Direct Sysfs Write
            val pDir = "/sys/devices/system/cpu/cpufreq/policy$policyId"
            val minNode = "$pDir/cpuinfo_min_freq"
            val maxNode = "$pDir/cpuinfo_max_freq"
            val hwMin = Shell.cmd("cat $minNode 2>/dev/null").exec().out.firstOrNull()?.toLongOrNull() ?: 300000L
            val hwMax = Shell.cmd("cat $maxNode 2>/dev/null").exec().out.firstOrNull()?.toLongOrNull() ?: 2400000L

            val curMin = Shell.cmd("cat $pDir/scaling_min_freq 2>/dev/null").exec().out.firstOrNull()?.toLongOrNull() ?: hwMin
            val curMax = Shell.cmd("cat $pDir/scaling_max_freq 2>/dev/null").exec().out.firstOrNull()?.toLongOrNull() ?: hwMax

            var safeMin = (minFreq ?: curMin).coerceIn(hwMin, hwMax)
            var safeMax = (maxFreq ?: curMax).coerceIn(hwMin, hwMax)

            // Regulation: min cannot exceed max
            if (safeMin > safeMax) {
                if (minFreq != null) safeMax = safeMin else safeMin = safeMax
            }

            val wasLocked = !LynxRepository.explicitlyUnlockedClusters.contains(policyId) && (LynxRepository.lockedClusterBounds.containsKey(policyId) || (Shell.cmd("ls -ld $pDir/scaling_max_freq 2>/dev/null").exec().out.firstOrNull()?.let {
                it.startsWith("-r--") || it.contains("r--r--r--")
            } ?: false))

            if (wasLocked) {
                LynxRepository.lockedClusterBounds[policyId] = Pair(safeMin, safeMax)
            }

            val cmd = if (LynxRepository.isModuleInstalled()) {
                "'$LYNXD_PATH' cluster set-freq $policyId '$safeMin' '$safeMax'"
            } else {

                buildString {
                    append("chmod 644 $pDir/scaling_min_freq $pDir/scaling_max_freq 2>/dev/null; ")
                    append("echo $hwMax > $pDir/scaling_max_freq 2>/dev/null; ")
                    append("echo $safeMin > $pDir/scaling_min_freq 2>/dev/null; ")
                    append("echo $safeMax > $pDir/scaling_max_freq 2>/dev/null; ")
                    // Dynamic MediaTek PPM hardware sync if node exists
                    append("c_idx=0; ")
                    append("for p in /sys/devices/system/cpu/cpufreq/policy*; do ")
                    append("if [ \"\${p##*policy}\" = \"$policyId\" ]; then ")
                    append("if [ -f /proc/ppm/policy/hard_userlimit_max_cpu_freq ]; then ")
                    append("echo \"\$c_idx $safeMax\" > /proc/ppm/policy/hard_userlimit_max_cpu_freq 2>/dev/null; ")
                    append("echo \"\$c_idx $safeMin\" > /proc/ppm/policy/hard_userlimit_min_cpu_freq 2>/dev/null; ")
                    append("fi; ")
                    append("if [ -f /proc/ppm/policy/userlimit_max_cpu_freq ]; then ")
                    append("echo \"\$c_idx $safeMax\" > /proc/ppm/policy/userlimit_max_cpu_freq 2>/dev/null; ")
                    append("echo \"\$c_idx $safeMin\" > /proc/ppm/policy/userlimit_min_cpu_freq 2>/dev/null; ")
                    append("fi; ")
                    append("break; ")
                    append("fi; ")
                    append("c_idx=\$((c_idx + 1)); ")
                    append("done; ")
                    if (safeMin == safeMax) {
                        append("if [ -f /proc/ppm/policy_status ]; then echo '2 0' > /proc/ppm/policy_status 2>/dev/null; fi; ")
                    }
                    if (wasLocked) {
                        append("chmod 444 $pDir/scaling_min_freq $pDir/scaling_max_freq 2>/dev/null; ")
                    }
                }
            }
            Shell.cmd(cmd).exec().isSuccess
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Lock or unlock CPU cluster frequency range.
     * When locked, permissions are set to 0444 (read-only) to protect against OEM thermal throttling.
     * When unlocked, permissions are restored to 0644 and limits are reset to OEM defaults.
     */
    suspend fun setClusterLock(policyId: Int, lock: Boolean, minFreq: Long? = null, maxFreq: Long? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val pDir = "/sys/devices/system/cpu/cpufreq/policy$policyId"
            val minNode = "$pDir/cpuinfo_min_freq"
            val maxNode = "$pDir/cpuinfo_max_freq"
            val hwMin = Shell.cmd("cat $minNode 2>/dev/null").exec().out.firstOrNull()?.toLongOrNull() ?: 300000L
            val hwMax = Shell.cmd("cat $maxNode 2>/dev/null").exec().out.firstOrNull()?.toLongOrNull() ?: 2400000L

            if (lock) {
                LynxRepository.explicitlyUnlockedClusters.remove(policyId)
                val curMin = Shell.cmd("cat $pDir/scaling_min_freq 2>/dev/null").exec().out.firstOrNull()?.toLongOrNull() ?: hwMin
                val curMax = Shell.cmd("cat $pDir/scaling_max_freq 2>/dev/null").exec().out.firstOrNull()?.toLongOrNull() ?: hwMax

                var safeMin = (minFreq ?: curMin).coerceIn(hwMin, hwMax)
                var safeMax = (maxFreq ?: curMax).coerceIn(hwMin, hwMax)
                if (safeMin > safeMax) safeMax = safeMin

                LynxRepository.lockedClusterBounds[policyId] = Pair(safeMin, safeMax)

                val cmd = if (LynxRepository.isModuleInstalled()) {
                    "'$LYNXD_PATH' cluster lock $policyId '$safeMin' '$safeMax'"
                } else {
                    buildString {
                        append("chmod 644 $pDir/scaling_min_freq $pDir/scaling_max_freq 2>/dev/null; ")
                        append("echo $hwMax > $pDir/scaling_max_freq 2>/dev/null; ")
                        append("echo $safeMin > $pDir/scaling_min_freq 2>/dev/null; ")
                        append("echo $safeMax > $pDir/scaling_max_freq 2>/dev/null; ")
                        append("c_idx=0; ")
                        append("for p in /sys/devices/system/cpu/cpufreq/policy*; do ")
                        append("if [ \"\${p##*policy}\" = \"$policyId\" ]; then ")
                        append("if [ -f /proc/ppm/policy/hard_userlimit_max_cpu_freq ]; then ")
                        append("echo \"\$c_idx $safeMax\" > /proc/ppm/policy/hard_userlimit_max_cpu_freq 2>/dev/null; ")
                        append("echo \"\$c_idx $safeMin\" > /proc/ppm/policy/hard_userlimit_min_cpu_freq 2>/dev/null; ")
                        append("fi; ")
                        append("if [ -f /proc/ppm/policy/userlimit_max_cpu_freq ]; then ")
                        append("echo \"\$c_idx $safeMax\" > /proc/ppm/policy/userlimit_max_cpu_freq 2>/dev/null; ")
                        append("echo \"\$c_idx $safeMin\" > /proc/ppm/policy/userlimit_min_cpu_freq 2>/dev/null; ")
                        append("fi; ")
                        append("break; ")
                        append("fi; ")
                        append("c_idx=\$((c_idx + 1)); ")
                        append("done; ")
                        if (safeMin == safeMax) {
                            append("if [ -f /proc/ppm/policy_status ]; then echo '2 0' > /proc/ppm/policy_status 2>/dev/null; fi; ")
                        }
                        // READ-ONLY GUARD: Protect nodes from OEM thermal-engine / powerhal overwrite
                        append("chmod 444 $pDir/scaling_min_freq $pDir/scaling_max_freq 2>/dev/null; ")
                    }
                }
                Shell.cmd(cmd).exec().isSuccess
            } else {
                LynxRepository.explicitlyUnlockedClusters.add(policyId)
                LynxRepository.lockedClusterBounds.remove(policyId)
                val cmd = if (LynxRepository.isModuleInstalled()) {
                    "'$LYNXD_PATH' cluster unlock $policyId"
                } else {

                    buildString {
                        append("chmod 644 $pDir/scaling_min_freq $pDir/scaling_max_freq 2>/dev/null; ")
                        append("echo $hwMax > $pDir/scaling_max_freq 2>/dev/null; ")
                        append("echo $hwMin > $pDir/scaling_min_freq 2>/dev/null; ")
                        append("c_idx=0; ")
                        append("for p in /sys/devices/system/cpu/cpufreq/policy*; do ")
                        append("if [ \"\${p##*policy}\" = \"$policyId\" ]; then ")
                        append("if [ -f /proc/ppm/policy/hard_userlimit_max_cpu_freq ]; then ")
                        append("echo \"\$c_idx -1\" > /proc/ppm/policy/hard_userlimit_max_cpu_freq 2>/dev/null; ")
                        append("echo \"\$c_idx -1\" > /proc/ppm/policy/hard_userlimit_min_cpu_freq 2>/dev/null; ")
                        append("fi; ")
                        append("if [ -f /proc/ppm/policy/userlimit_max_cpu_freq ]; then ")
                        append("echo \"\$c_idx -1\" > /proc/ppm/policy/userlimit_max_cpu_freq 2>/dev/null; ")
                        append("echo \"\$c_idx -1\" > /proc/ppm/policy/userlimit_min_cpu_freq 2>/dev/null; ")
                        append("fi; ")
                        append("break; ")
                        append("fi; ")
                        append("c_idx=\$((c_idx + 1)); ")
                        append("done; ")
                        append("if [ -f /proc/ppm/policy_status ]; then echo '2 1' > /proc/ppm/policy_status 2>/dev/null; fi; ")
                    }
                }
                Shell.cmd(cmd).exec().isSuccess
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Apply a scaling governor to a CPU cluster policy.
     */
    suspend fun setClusterGov(policyId: Int, gov: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Tier 0 Fast-Path: lynxd native governor setter (<2ms)
            if (LynxRepository.isModuleInstalled()) {
                val cmd = "'$LYNXD_PATH' cluster set-gov $policyId '$gov'"
                val res = Shell.cmd(cmd).exec()
                if (res.isSuccess) return@withContext true
            }

            // Standalone Fallback: Safety Regulation: Only apply if governor is in scaling_available_governors
            val availGovs = Shell.cmd("cat /sys/devices/system/cpu/cpufreq/policy$policyId/scaling_available_governors 2>/dev/null").exec().out.firstOrNull() ?: ""
            if (availGovs.isNotBlank() && !availGovs.contains(gov)) {
                Log.w("LynxRepository", "Governor '$gov' is not in available governors ($availGovs), aborting for safety")
                return@withContext false
            }

            val cmd = if (LynxRepository.isModuleInstalled()) {
                "'$LYNXD_PATH' cluster set-gov $policyId '$gov'"
            } else {

                "chmod 644 /sys/devices/system/cpu/cpufreq/policy$policyId/scaling_governor 2>/dev/null; echo '$gov' > /sys/devices/system/cpu/cpufreq/policy$policyId/scaling_governor 2>/dev/null"
            }
            Shell.cmd(cmd).exec().isSuccess
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Single-pass atomic batch applicator for CPU Profiles.
     * Executes all cluster frequencies, governors, PPM limits, scheduler sysctl,
     * CPU sets, core parking, and final permissions lock in a single IPC shell execution (<100ms).
     * Eliminates 60-80 separate subshell calls and prevents race conditions/reverts.
     */
    suspend fun applyCpuBatchProfile(params: CpuBatchProfileParams, context: Context? = null): Boolean =
        LynxCpuBatchProfileRepository.applyCpuBatchProfile(params, context)

}
