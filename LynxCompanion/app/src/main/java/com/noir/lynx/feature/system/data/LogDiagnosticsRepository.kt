package com.noir.lynx.feature.system.data

import android.content.Context
import com.noir.lynx.feature.system.model.AndroidCrashIncident
import com.noir.lynx.feature.system.model.AndroidCrashState
import com.noir.lynx.feature.system.model.KernelPanicDetails
import com.noir.lynx.feature.system.model.LynxDaemonLogState
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * LogDiagnosticsRepository — Master Engine for Categorized System & Kernel Forensics.
 */
object LogDiagnosticsRepository {

    suspend fun readKernelPanicDetails(): KernelPanicDetails = withContext(Dispatchers.IO) {
        try {
            val bootReason = Shell.cmd("getprop sys.boot.reason").exec().out.firstOrNull()?.trim() ?: ""
            val bootReasonLast = Shell.cmd("getprop sys.boot.reason.last").exec().out.firstOrNull()?.trim() ?: ""
            val roBootReason = Shell.cmd("getprop ro.boot.bootreason").exec().out.firstOrNull()?.trim() ?: ""

            val combinedReasons = "$bootReason $bootReasonLast $roBootReason".lowercase(Locale.ROOT)
            val isAbnormal = combinedReasons.contains("panic") ||
                    combinedReasons.contains("watchdog") ||
                    combinedReasons.contains("crash") ||
                    combinedReasons.contains("oops") ||
                    combinedReasons.contains("die") ||
                    combinedReasons.contains("reboot,recovery")

            // 1. Try dumpsys dropbox SYSTEM_LAST_KMSG
            var logSource = ""
            var fullLog = ""
            val dropboxRes = Shell.cmd("dumpsys dropbox --print SYSTEM_LAST_KMSG 2>/dev/null").exec()
            if (dropboxRes.isSuccess && dropboxRes.out.isNotEmpty() && !dropboxRes.out.joinToString("\n").contains("Drop box contents: 0 entries")) {
                fullLog = dropboxRes.out.joinToString("\n")
                logSource = "Dropbox (SYSTEM_LAST_KMSG)"
            }

            // 2. Fallback to /sys/fs/pstore/console-ramoops-0 or dmesg-ramoops-0
            if (fullLog.isBlank()) {
                val pstoreRes = Shell.cmd("cat /sys/fs/pstore/console-ramoops-0 2>/dev/null || cat /sys/fs/pstore/dmesg-ramoops-0 2>/dev/null").exec()
                if (pstoreRes.isSuccess && pstoreRes.out.isNotEmpty()) {
                    fullLog = pstoreRes.out.joinToString("\n")
                    logSource = "Kernel PStore (/sys/fs/pstore/ramoops)"
                }
            }

            // 3. Fallback to /proc/last_kmsg
            if (fullLog.isBlank()) {
                val procKmsgRes = Shell.cmd("cat /proc/last_kmsg 2>/dev/null").exec()
                if (procKmsgRes.isSuccess && procKmsgRes.out.isNotEmpty()) {
                    fullLog = procKmsgRes.out.joinToString("\n")
                    logSource = "Kernel Buffer (/proc/last_kmsg)"
                }
            }

            if (fullLog.isBlank()) {
                fullLog = "[Tidak ada log panic/kmsg tersimpan dari boot sebelumnya. Boot reason saat ini: ${if (bootReason.isNotBlank()) bootReason else "Normal"}]"
                logSource = "System Property Status"
            }

            // Extract panic signature
            var signature = ""
            val pcRegex = Regex("""pc\s*:\s*([a-zA-Z0-9_+/]+)""")
            val pcMatch = pcRegex.find(fullLog)
            if (pcMatch != null) {
                signature = "PC: " + pcMatch.groupValues[1]
            } else {
                val panicRegex = Regex("""Kernel panic[^\n]*""")
                val panicMatch = panicRegex.find(fullLog)
                if (panicMatch != null) {
                    signature = panicMatch.value.take(60)
                } else if (isAbnormal) {
                    signature = "Boot Reason: $bootReason"
                } else {
                    signature = "Sistem berjalan normal (Tidak ada rekaman Panic)"
                }
            }

            KernelPanicDetails(
                bootReason = if (bootReason.isNotBlank()) bootReason else if (roBootReason.isNotBlank()) roBootReason else "Normal",
                bootReasonLast = bootReasonLast,
                isAbnormalReboot = isAbnormal,
                panicSignature = signature,
                timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                fullLog = fullLog,
                logSource = logSource,
                isLoading = false
            )
        } catch (e: Exception) {
            KernelPanicDetails(
                bootReason = "Error",
                panicSignature = "Gagal membaca log: ${e.message}",
                fullLog = "Exception reading panic details: ${e.message}",
                logSource = "Error",
                isLoading = false
            )
        }
    }

    suspend fun readAndroidCrashes(): List<AndroidCrashIncident> = withContext(Dispatchers.IO) {
        val incidents = mutableListOf<AndroidCrashIncident>()
        try {
            // Get last 20 dropbox entries related to crash/anr/watchdog
            val listCmd = "dumpsys dropbox 2>/dev/null | grep -E 'data_app_crash|data_app_anr|system_server_crash|system_server_wtf|watchdog' | tail -n 25"
            val lines = Shell.cmd(listCmd).exec().out

            for (line in lines.reversed()) {
                val parts = line.trim().split(" ")
                if (parts.size >= 3) {
                    val date = parts[0]
                    val time = parts[1]
                    val tag = parts[2]
                    val timestamp = "$date $time"
                    val id = "$tag@$timestamp"

                    // Read content of this specific entry
                    val detailCmd = "dumpsys dropbox --print '$tag' 2>/dev/null | grep -A 40 '$timestamp' | head -n 45"
                    val entryLines = Shell.cmd(detailCmd).exec().out
                    val fullSnippet = entryLines.joinToString("\n")

                    var pkg = ""
                    var proc = ""
                    var pid = ""
                    var subject = ""

                    for (l in entryLines) {
                        val trimmed = l.trim()
                        if (trimmed.startsWith("Process:")) {
                            proc = trimmed.removePrefix("Process:").trim()
                        } else if (trimmed.startsWith("Package:")) {
                            pkg = trimmed.removePrefix("Package:").trim().split(" ").firstOrNull() ?: ""
                        } else if (trimmed.startsWith("PID:")) {
                            pid = trimmed.removePrefix("PID:").trim()
                        } else if (trimmed.startsWith("Subject:")) {
                            subject = trimmed.removePrefix("Subject:").trim()
                        }
                    }

                    if (pkg.isBlank() && proc.isNotBlank()) pkg = proc
                    if (pkg.isBlank()) pkg = "android.system"

                    val snippet = if (subject.isNotBlank()) {
                        subject
                    } else {
                        entryLines.firstOrNull { it.contains("Exception") || it.contains("Error") || it.contains("signal") } ?: "$tag detected"
                    }

                    incidents.add(
                        AndroidCrashIncident(
                            id = id,
                            tag = tag,
                            timestamp = timestamp,
                            packageName = pkg,
                            processName = proc,
                            pid = pid,
                            subject = subject,
                            stackTraceSnippet = snippet,
                            fullLog = if (fullSnippet.isNotBlank()) fullSnippet else "[Log detail kosong untuk $id]"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Return empty list on failure
        }
        incidents
    }

    suspend fun readLynxDaemonLogs(filter: String = "ALL"): List<String> = withContext(Dispatchers.IO) {
        try {
            val logFile = "/data/adb/modules/Lynx/logs/lynxd.log"
            val fallback = "/storage/emulated/0/Lynx/Lynx.log"
            val target = if (Shell.cmd("[ -f '$logFile' ]").exec().isSuccess) logFile else fallback

            val baseCmd = "tail -n 400 '$target' 2>/dev/null"
            val lines = Shell.cmd(baseCmd).exec().out
            if (lines.isEmpty()) {
                return@withContext listOf("[Log daemon lynxd masih kosong atau file belum dibuat]")
            }

            when (filter.uppercase(Locale.ROOT)) {
                "ERRORS" -> lines.filter { it.contains("error", ignoreCase = true) || it.contains("fail", ignoreCase = true) || it.contains("warn", ignoreCase = true) }
                "PROFILE" -> lines.filter { it.contains("profile", ignoreCase = true) || it.contains("peralihan", ignoreCase = true) || it.contains("boost", ignoreCase = true) }
                "CHARGING" -> lines.filter { it.contains("charg", ignoreCase = true) || it.contains("pengisian", ignoreCase = true) || it.contains("battery", ignoreCase = true) }
                else -> lines
            }
        } catch (e: Exception) {
            listOf("[Gagal membaca log daemon: ${e.message}]")
        }
    }

    suspend fun generateDiagnosticZip(context: Context): String? = withContext(Dispatchers.IO) {
        try {
            val diagDir = File("/storage/emulated/0/Lynx/Diagnostics")
            if (!diagDir.exists()) {
                diagDir.mkdirs()
            }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val zipFile = File(diagDir, "Lynx_Diag_Report_$timeStamp.zip")

            val bootReason = Shell.cmd("getprop sys.boot.reason; getprop sys.boot.reason.last; getprop ro.boot.bootreason; uptime").exec().out.joinToString("\n")
            val lastKmsg = Shell.cmd("dumpsys dropbox --print SYSTEM_LAST_KMSG 2>/dev/null || cat /sys/fs/pstore/console-ramoops-0 2>/dev/null").exec().out.joinToString("\n")
            val dropboxCrashes = Shell.cmd("dumpsys dropbox 2>/dev/null | tail -n 60").exec().out.joinToString("\n")
            val daemonLog = Shell.cmd("tail -n 500 /data/adb/modules/Lynx/logs/lynxd.log 2>/dev/null || cat /storage/emulated/0/Lynx/Lynx.log 2>/dev/null").exec().out.joinToString("\n")
            val dmesgSnapshot = Shell.cmd("dmesg 2>/dev/null | tail -n 500").exec().out.joinToString("\n")
            val hardwareSpec = Shell.cmd("/data/adb/modules/Lynx/system/bin/lynxd status --json 2>/dev/null || getprop").exec().out.joinToString("\n")

            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                fun addEntry(name: String, content: String) {
                    val entry = ZipEntry(name)
                    zos.putNextEntry(entry)
                    zos.write(content.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }

                addEntry("00_BOOT_REASON_AND_UPTIME.txt", bootReason)
                addEntry("01_KERNEL_LAST_KMSG.txt", lastKmsg)
                addEntry("02_ANDROID_DROPBOX_CRASHES.txt", dropboxCrashes)
                addEntry("03_LYNX_DAEMON.log", daemonLog)
                addEntry("04_DMESG_SNAPSHOT.txt", dmesgSnapshot)
                addEntry("05_DEVICE_TELEMETRY.json", hardwareSpec)
            }

            Shell.cmd("chmod 666 '${zipFile.absolutePath}' 2>/dev/null").exec()
            zipFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    suspend fun generateQuickSummary(): String = withContext(Dispatchers.IO) {
        try {
            val model = Shell.cmd("getprop ro.product.model").exec().out.firstOrNull()?.trim() ?: "Android"
            val os = Shell.cmd("getprop ro.build.version.release").exec().out.firstOrNull()?.trim() ?: ""
            val kernel = Shell.cmd("uname -r").exec().out.firstOrNull()?.trim() ?: ""
            val bootReason = Shell.cmd("getprop sys.boot.reason").exec().out.firstOrNull()?.trim() ?: "Normal"
            val uptime = Shell.cmd("uptime").exec().out.firstOrNull()?.trim() ?: ""

            """
            ════════════════════════════════════════════
            📊 LYNX FORENSIC & DIAGNOSTIC SUMMARY
            ════════════════════════════════════════════
            • Perangkat    : $model (Android $os)
            • Kernel       : $kernel
            • Uptime       : $uptime
            • Boot Reason  : $bootReason
            • Panic State  : ${if (bootReason.contains("panic", ignoreCase = true) || bootReason.contains("watchdog", ignoreCase = true)) "⚠️ KERNEL PANIC / WATCHDOG DETECTED" else "✅ NORMAL BOOT"}
            • Generated At : ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}
            ════════════════════════════════════════════
            """.trimIndent()
        } catch (e: Exception) {
            "Gagal menghasilkan ringkasan: ${e.message}"
        }
    }
}
