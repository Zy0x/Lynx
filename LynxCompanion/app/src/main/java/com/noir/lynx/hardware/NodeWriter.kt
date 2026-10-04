package com.noir.lynx.hardware

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NodeWriter {

    suspend fun writeVerified(path: String, value: String): WriteResult = withContext(Dispatchers.IO) {
        try {
            val probeCmd = """
                if [ ! -f "$path" ]; then
                    echo "MISSING"
                else
                    echo "$value" > "$path" 2>/dev/null
                    read_back=$(cat "$path" 2>/dev/null | tr -d '\r\n')
                    echo "READBACK:${'$'}read_back"
                fi
            """.trimIndent()

            val res = Shell.cmd(probeCmd).exec().out
            val line = res.firstOrNull()?.trim() ?: "MISSING"

            if (line == "MISSING") {
                return@withContext WriteResult.Unsupported
            }

            if (line.startsWith("READBACK:")) {
                val readVal = line.removePrefix("READBACK:").trim()
                // Many sysfs nodes format integers or strip trailing spaces
                val expected = value.trim()
                if (readVal == expected || readVal.contains(expected)) {
                    return@withContext WriteResult.Applied
                } else {
                    return@withContext WriteResult.Rejected(readVal)
                }
            }

            WriteResult.Unsupported
        } catch (e: Exception) {
            WriteResult.Unsupported
        }
    }
}
