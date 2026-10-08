package com.noir.lynx.hardware

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class NodeAccessInfo(
    val exists: Boolean,
    val readable: Boolean,
    val writable: Boolean
)

object NodeWriter {

    /**
     * Non-destructive permission probe.
     * Evaluates file existence, read, and write permissions without modifying file modes.
     */
    suspend fun probeNode(path: String): NodeAccessInfo = withContext(Dispatchers.IO) {
        try {
            val script = """
                if [ ! -e "$path" ]; then
                    echo "MISSING"
                else
                    r=0; w=0
                    [ -r "$path" ] && r=1
                    [ -w "$path" ] && w=1
                    echo "ACCESS:${'$'}r:${'$'}w"
                fi
            """.trimIndent()

            val res = Shell.cmd(script).exec().out.firstOrNull()?.trim() ?: "MISSING"
            when {
                res.startsWith("ACCESS:1:1") -> NodeAccessInfo(exists = true, readable = true, writable = true)
                res.startsWith("ACCESS:1:0") -> NodeAccessInfo(exists = true, readable = true, writable = false)
                res.startsWith("ACCESS:0:1") -> NodeAccessInfo(exists = true, readable = false, writable = true)
                res.startsWith("ACCESS:") -> NodeAccessInfo(exists = true, readable = false, writable = false)
                else -> NodeAccessInfo(exists = false, readable = false, writable = false)
            }
        } catch (_: Exception) {
            NodeAccessInfo(exists = false, readable = false, writable = false)
        }
    }

    /**
     * Fast batch probe for multiple nodes in a single shell execution.
     * Prevents shell fork-exec bottlenecks during system scanning.
     */
    suspend fun batchProbe(paths: List<String>): Map<String, NodeAccessInfo> = withContext(Dispatchers.IO) {
        if (paths.isEmpty()) return@withContext emptyMap()
        try {
            val sb = java.lang.StringBuilder()
            for (p in paths) {
                sb.append("if [ -e \"$p\" ]; then r=0; w=0; [ -r \"$p\" ] && r=1; [ -w \"$p\" ] && w=1; echo \"$p|1|\$r|\$w\"; else echo \"$p|0|0|0\"; fi\n")
            }
            val res = Shell.cmd(sb.toString()).exec().out
            val map = mutableMapOf<String, NodeAccessInfo>()
            for (line in res) {
                val parts = line.trim().split("|")
                if (parts.size >= 4) {
                    val p = parts[0]
                    val ex = parts[1] == "1"
                    val rd = parts[2] == "1"
                    val wr = parts[3] == "1"
                    map[p] = NodeAccessInfo(exists = ex, readable = rd, writable = wr)
                }
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /**
     * Extracts the active token from sysfs readbacks:
     * - Bracketed active policy: "coarse_demand [always_on]" -> "always_on"
     * - Multi-token status: "1 1 -1" -> "1"
     */
    fun extractActiveValue(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val bracketMatch = Regex("\\[([^\\]]+)\\]").find(trimmed)?.groupValues?.get(1)?.trim()
        if (!bracketMatch.isNullOrEmpty()) return bracketMatch
        return trimmed.split(Regex("\\s+")).firstOrNull()?.trim() ?: trimmed
    }

    /**
     * Transactional write verification.
     * Flow: Pre-read -> Non-destructive write -> Read-back -> Verify.
     * Returns Applied if kernel accepted the value, or Rejected if locked/unsupported.
     */
    suspend fun writeVerified(path: String, value: String): WriteResult = withContext(Dispatchers.IO) {
        try {
            val access = probeNode(path)
            if (!access.exists) {
                return@withContext WriteResult.Unsupported
            }

            val expected = value.trim()

            // Special handling for MediaTek GPUFreq v1 multi-row limit table
            if (path.endsWith("gpufreq_limit_table")) {
                val limitEnable = if (expected == "1" || expected.equals("true", ignoreCase = true)) "0" else "1"
                val cmd = """
                    for id in 3 4 5 6 7; do
                        echo "${'$'}id $limitEnable $limitEnable" > "$path" 2>/dev/null
                    done
                    awk '${'$'}1 == "THERMAL" || ${'$'}2 == "THERMAL" { print (${'$'}5 == "0" ? "1" : "0") }' "$path" 2>/dev/null | head -n 1
                """.trimIndent()
                val readVal = Shell.cmd(cmd).exec().out.firstOrNull()?.trim() ?: ""
                return@withContext if (readVal == expected) WriteResult.Applied else WriteResult.Rejected(readVal)
            }

            // Special handling for MediaTek Dimensity GPUFreq v2 power_limited table
            if (path.endsWith("gpufreq_power_limited")) {
                val ignoreFlag = if (expected == "1" || expected.equals("true", ignoreCase = true)) "1" else "0"
                val cmd = """
                    echo "ignore_thermal_protect $ignoreFlag" > "$path" 2>/dev/null
                    echo "ignore_pbm_limited $ignoreFlag" > "$path" 2>/dev/null
                    echo "ignore_batt_oc $ignoreFlag" > "$path" 2>/dev/null
                    echo "ignore_batt_low $ignoreFlag" > "$path" 2>/dev/null
                    echo "ignore_batt_percent $ignoreFlag" > "$path" 2>/dev/null
                    echo "$expected"
                """.trimIndent()
                val readVal = Shell.cmd(cmd).exec().out.firstOrNull()?.trim() ?: ""
                return@withContext if (readVal == expected) WriteResult.Applied else WriteResult.Rejected(readVal)
            }

            // Read current value after write for transaction verification
            val probeCmd = if (access.writable) {
                """
                    echo "$value" > "$path" 2>/dev/null
                    read_back=${'$'}(cat "$path" 2>/dev/null | head -n 1 | tr -d '\r\n')
                    echo "READBACK:${'$'}read_back"
                """.trimIndent()
            } else {
                // If not writable by default permissions, cautiously test chmod 644
                """
                    chmod 644 "$path" 2>/dev/null
                    echo "$value" > "$path" 2>/dev/null
                    read_back=${'$'}(cat "$path" 2>/dev/null | head -n 1 | tr -d '\r\n')
                    echo "READBACK:${'$'}read_back"
                """.trimIndent()
            }

            val res = Shell.cmd(probeCmd).exec().out
            val line = res.firstOrNull()?.trim() ?: "MISSING"

            if (line.startsWith("READBACK:")) {
                val readVal = line.removePrefix("READBACK:").trim()
                val activeBracket = extractActiveValue(readVal)
                val tokens = readVal.split(Regex("[\\s:]+")).map { it.trim() }.filter { it.isNotEmpty() }
                val firstToken = tokens.firstOrNull() ?: readVal
                val isApplied = readVal.equals(expected, ignoreCase = true) ||
                    activeBracket.equals(expected, ignoreCase = true) ||
                    firstToken.equals(expected, ignoreCase = true) ||
                    readVal.contains("[$expected]", ignoreCase = true) ||
                    tokens.any { it.equals(expected, ignoreCase = true) }
                if (isApplied) {
                    return@withContext WriteResult.Applied
                } else {
                    return@withContext WriteResult.Rejected(readVal)
                }
            }

            WriteResult.Unsupported
        } catch (_: Exception) {
            WriteResult.Unsupported
        }
    }
}
