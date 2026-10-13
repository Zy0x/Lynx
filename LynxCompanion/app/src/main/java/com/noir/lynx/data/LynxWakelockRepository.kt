package com.noir.lynx.data

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Wake-source telemetry, wakelock blocking, and device-idle controls. */
object LynxWakelockRepository {
    suspend fun readTopWakelocks(): List<WakelockItem> = withContext(Dispatchers.IO) {
        try {
            if (LynxRepository.isModuleInstalled()) {
                val nativeRes = Shell.cmd("'$LYNXD_PATH' system top-wakelocks 2>/dev/null").exec()
                if (nativeRes.isSuccess && nativeRes.out.isNotEmpty()) {
                    val nativeItems = nativeRes.out.mapNotNull { line ->
                        val p = line.split("|")
                        if (p.size >= 3) {
                            WakelockItem(
                                name = p[0].trim(),
                                activeCount = p[1].trim().toLongOrNull() ?: 0L,
                                preventSuspendMs = p[2].trim().toLongOrNull() ?: 0L
                            )
                        } else null
                    }
                    if (nativeItems.isNotEmpty()) return@withContext nativeItems
                }
            }
            val script = """
                for w in /sys/class/wakeup/wakeup*; do
                    [ -d "${'$'}w" ] || continue
                    n=${'$'}(cat "${'$'}w/name" 2>/dev/null)
                    c=${'$'}(cat "${'$'}w/active_count" 2>/dev/null)
                    t=${'$'}(cat "${'$'}w/prevent_suspend_time_ms" 2>/dev/null)
                    if [ -n "${'$'}n" ] && [ "${'$'}c" -gt 0 ] 2>/dev/null; then
                        echo "${'$'}n|${'$'}c|${'$'}{t:-0}"
                    fi
                done | sort -t'|' -k2 -nr | head -n 10
            """.trimIndent()
            val r = Shell.cmd(script).exec()
            val items = r.out.mapNotNull { line ->
                val p = line.split("|")
                if (p.size >= 3) {
                    val name = p[0].trim()
                    val count = p[1].trim().toLongOrNull() ?: 0L
                    val ms = p[2].trim().toLongOrNull() ?: 0L
                    WakelockItem(name = name, activeCount = count, preventSuspendMs = ms)
                } else null
            }
            if (items.isNotEmpty()) items else {
                val fallback = Shell.cmd("dumpsys power 2>/dev/null | grep -A 8 'Wake Locks:'").exec().out
                    .map { it.trim() }
                    .filter { it.isNotBlank() && !it.startsWith("Wake Locks:") }
                    .map { WakelockItem(name = it, activeCount = 1, preventSuspendMs = 0) }
                fallback
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun readWakelocks(): List<String> = withContext(Dispatchers.IO) {
        readTopWakelocks().map { it.name }
    }



    suspend fun readWakelockBlockerInfo(): WakelockBlockerInfo = withContext(Dispatchers.IO) {
        try {
            val boefflaNodes = listOf(
                "/sys/devices/virtual/misc/boeffla_wakelock_blocker/wakelock_blocker",
                "/sys/class/misc/boeffla_wakelock_blocker/default_wakelock_blocker"
            )
            val driverPath = boefflaNodes.firstOrNull {
                Shell.cmd("[ -e '$it' ] && echo yes").exec().out.firstOrNull() == "yes"
            } ?: ""

            val isSupported = driverPath.isNotBlank()
            val blockedList = if (isSupported) {
                Shell.cmd("cat '$driverPath' 2>/dev/null").exec().out
                    .firstOrNull()?.trim()?.split(";")?.filter { it.isNotBlank() } ?: emptyList()
            } else emptyList()

            val avail = readTopWakelocks().map { it.name }.filter { it.isNotBlank() }
            val dozeRes = Shell.cmd("dumpsys deviceidle get deep").exec().out.firstOrNull()?.trim()
            val isDoze = dozeRes?.equals("IDLE", ignoreCase = true) == true

            WakelockBlockerInfo(
                isDriverSupported = isSupported,
                driverPath = driverPath,
                blockedWakelocks = blockedList,
                availableWakelocks = avail,
                aggressiveDozeEnabled = isDoze
            )
        } catch (_: Exception) {
            WakelockBlockerInfo()
        }
    }

    suspend fun setWakelockBlocked(name: String, blocked: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val info = readWakelockBlockerInfo()
            if (!info.isDriverSupported || info.driverPath.isBlank()) return@withContext false

            val current = info.blockedWakelocks.toMutableSet()
            if (blocked) current.add(name) else current.remove(name)
            val joined = current.joinToString(";")
            Shell.cmd("echo '$joined' > '${info.driverPath}' 2>/dev/null; echo ok")
                .exec().out.firstOrNull()?.trim() == "ok"
        } catch (_: Exception) { false }
    }

    suspend fun toggleAggressiveDoze(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            if (enabled) {
                Shell.cmd("dumpsys deviceidle force-idle >/dev/null 2>&1; echo ok")
                    .exec().out.firstOrNull()?.trim() == "ok"
            } else {
                Shell.cmd("dumpsys deviceidle unforce >/dev/null 2>&1; echo ok")
                    .exec().out.firstOrNull()?.trim() == "ok"
            }
        } catch (_: Exception) { false }
    }


}
