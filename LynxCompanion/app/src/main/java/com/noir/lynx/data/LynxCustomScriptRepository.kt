package com.noir.lynx.data

import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Local/module-backed custom script storage and execution. */
private const val CUSTOM_RULES_PATH = "$MODULE_DIR/custom_rules.sh"

object LynxCustomScriptRepository {
    private suspend fun getCustomScriptsJsonPath(): String {
        val basePath = if (LynxRepository.isModuleInstalled()) MODULE_DIR else "/data/user/0/com.noir.lynx/files"
        Shell.cmd("mkdir -p '$basePath' 2>/dev/null").exec()
        return "$basePath/custom_scripts.json"
    }

    suspend fun readCustomScripts(): List<CustomScriptItem> = withContext(Dispatchers.IO) {
        try {
            val path = getCustomScriptsJsonPath()
            val res = Shell.cmd("cat '$path' 2>/dev/null").exec()
            if (!res.isSuccess || res.out.isEmpty()) return@withContext getDefaultCustomScripts()

            val text = res.out.joinToString("\n")
            val array = org.json.JSONArray(text)
            val list = mutableListOf<CustomScriptItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    CustomScriptItem(
                        id = obj.optString("id", System.currentTimeMillis().toString()),
                        name = obj.optString("name", "Script #$i"),
                        script = obj.optString("script", ""),
                        runOnBoot = obj.optBoolean("runOnBoot", false),
                        lastRunTime = obj.optLong("lastRunTime", 0L),
                        lastExitCode = if (obj.has("lastExitCode")) obj.optInt("lastExitCode") else null,
                        lastOutput = obj.optString("lastOutput", "")
                    )
                )
            }
            if (list.isEmpty()) getDefaultCustomScripts() else list
        } catch (e: Exception) {
            getDefaultCustomScripts()
        }
    }

    private fun getDefaultCustomScripts(): List<CustomScriptItem> {
        return listOf(
            CustomScriptItem(
                id = "drop_caches_flush",
                name = "Drop Caches & Flush Buffers",
                script = "sync\necho 3 > /proc/sys/vm/drop_caches\necho 'RAM Caches Successfully Flushed'",
                runOnBoot = false
            ),
            CustomScriptItem(
                id = "fstrim_all",
                name = "FSTRIM System & Data Partitions",
                script = "fstrim -v /data\nfstrim -v /cache 2>/dev/null || true\necho 'Storage Blocks Trimmed'",
                runOnBoot = false
            ),
            CustomScriptItem(
                id = "optimize_tcp",
                name = "Aggressive TCP Buffer Optimization",
                script = "echo 3 > /proc/sys/net/ipv4/tcp_fastopen 2>/dev/null\necho 1 > /proc/sys/net/ipv4/tcp_tw_reuse 2>/dev/null\necho 'TCP Stack Tuned'",
                runOnBoot = false
            )
        )
    }

    suspend fun saveCustomScripts(scripts: List<CustomScriptItem>): Boolean = withContext(Dispatchers.IO) {
        try {
            val path = getCustomScriptsJsonPath()
            val array = org.json.JSONArray()
            for (s in scripts) {
                val obj = JSONObject()
                obj.put("id", s.id)
                obj.put("name", s.name)
                obj.put("script", s.script)
                obj.put("runOnBoot", s.runOnBoot)
                obj.put("lastRunTime", s.lastRunTime)
                if (s.lastExitCode != null) obj.put("lastExitCode", s.lastExitCode)
                obj.put("lastOutput", s.lastOutput)
                array.put(obj)
            }
            val escaped = array.toString().replace("'", "'\\''")
            Shell.cmd("echo '$escaped' > '$path' 2>/dev/null").exec().isSuccess
        } catch (e: Exception) {
            false
        }
    }

    suspend fun executeCustomScript(item: CustomScriptItem): CustomScriptItem = withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()
            val res = Shell.cmd(item.script).exec()
            val durationMs = System.currentTimeMillis() - startTime
            val outText = res.out.joinToString("\n").trim()
            val errText = res.err.joinToString("\n").trim()
            val combined = buildString {
                if (outText.isNotEmpty()) append(outText)
                if (errText.isNotEmpty()) {
                    if (isNotEmpty()) append("\n[STDERR]\n")
                    append(errText)
                }
                append("\n[Selesai dalam ${durationMs}ms | Exit: ${res.code}]")
            }
            item.copy(
                lastRunTime = System.currentTimeMillis(),
                lastExitCode = res.code,
                lastOutput = combined
            )
        } catch (e: Exception) {
            item.copy(
                lastRunTime = System.currentTimeMillis(),
                lastExitCode = -1,
                lastOutput = "Gagal mengeksekusi skrip: ${e.message}"
            )
        }
    }



    suspend fun readCustomRules(): String = withContext(Dispatchers.IO) {
        try {
            val r = Shell.cmd("[ -f '$CUSTOM_RULES_PATH' ] && cat '$CUSTOM_RULES_PATH' 2>/dev/null").exec()
            val content = r.out.joinToString("\n").trim()
            if (content.isNotBlank()) content else """
#!/system/bin/sh
# Lynx Universal — Skrip Boot Kustom Pengguna (custom_rules.sh)
# Dijalankan otomatis saat sistem selesai boot oleh service.sh

# Contoh sysfs tweak kustom:
# echo 1 > /proc/sys/net/ipv4/tcp_tw_reuse
# echo 0 > /proc/sys/kernel/sched_schedstats
""".trimIndent()
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun saveCustomRules(script: String): Boolean = withContext(Dispatchers.IO) {
        try {
            LynxRepository.writeTextToFileSafely(CUSTOM_RULES_PATH, script, "755")
        } catch (e: Exception) { false }
    }

    suspend fun executeCustomRules(): String = withContext(Dispatchers.IO) {
        try {
            val r = Shell.cmd(
                "chmod 755 '$CUSTOM_RULES_PATH' 2>/dev/null",
                "sh '$CUSTOM_RULES_PATH' 2>&1"
            ).exec()
            r.out.joinToString("\n").ifBlank { "Skrip berhasil dieksekusi tanpa error." }
        } catch (e: Exception) {
            "Gagal mengeksekusi skrip: ${e.message}"
        }
    }
}
