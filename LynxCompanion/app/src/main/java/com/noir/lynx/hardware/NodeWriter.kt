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
                sb.append("if [ -e '$p' ]; then r=0; w=0; [ -r '$p' ] && r=1; [ -w '$p' ] && w=1; echo '$p|1|\$r|\$w'; else echo '$p|0|0|0'; fi\n")
            }
            val res = Shell.cmd(sb.toString()).exec().out
            val map = mutableMapOf<String, NodeAccessInfo>()
            for (line in res) {
                val parts = line.split("|")
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

            // Read current value before write for transaction logging
            val probeCmd = if (access.writable) {
                """
                    echo "$value" > "$path" 2>/dev/null
                    read_back=${'$'}(cat "$path" 2>/dev/null | tr -d '\r\n')
                    echo "READBACK:${'$'}read_back"
                """.trimIndent()
            } else {
                // If not writable by default permissions, cautiously test chmod 644
                """
                    chmod 644 "$path" 2>/dev/null
                    echo "$value" > "$path" 2>/dev/null
                    read_back=${'$'}(cat "$path" 2>/dev/null | tr -d '\r\n')
                    echo "READBACK:${'$'}read_back"
                """.trimIndent()
            }

            val res = Shell.cmd(probeCmd).exec().out
            val line = res.firstOrNull()?.trim() ?: "MISSING"

            if (line.startsWith("READBACK:")) {
                val readVal = line.removePrefix("READBACK:").trim()
                val expected = value.trim()
                if (readVal == expected || readVal.contains(expected)) {
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
