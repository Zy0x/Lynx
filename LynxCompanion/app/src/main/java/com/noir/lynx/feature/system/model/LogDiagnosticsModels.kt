package com.noir.lynx.feature.system.model

/**
 * Log Category Tabs in Diagnostic & Forensic Hub
 */
enum class LogCategoryTab(val id: Int, val title: String, val subtitle: String) {
    KERNEL_PANIC(0, "Kernel Panic", "Last Kmsg & Crash Boot"),
    ANDROID_CRASH(1, "Android Crash", "Dropbox ANR & App Crashes"),
    LYNX_DAEMON(2, "Lynx Daemon", "Per-App & Battery Watchdog"),
    LIVE_DMESG(3, "Live Dmesg", "Ring Buffer Kernel Real-Time")
}

/**
 * Kernel Panic & Boot Reason details extracted from system properties and kernel buffers
 */
data class KernelPanicDetails(
    val bootReason: String = "",
    val bootReasonLast: String = "",
    val isAbnormalReboot: Boolean = false,
    val panicSignature: String = "", // e.g. "pc: shrink_page_list+0x120"
    val timestamp: String = "",
    val fullLog: String = "",
    val logSource: String = "", // "dumpsys dropbox SYSTEM_LAST_KMSG", "pstore ramoops", etc.
    val isLoading: Boolean = false
)

/**
 * Individual Android Crash/ANR incident parsed from Dropbox
 */
data class AndroidCrashIncident(
    val id: String,
    val tag: String, // "data_app_crash", "data_app_anr", "system_server_crash", etc.
    val timestamp: String,
    val packageName: String,
    val processName: String,
    val pid: String,
    val subject: String,
    val stackTraceSnippet: String,
    val fullLog: String
)

/**
 * Android Crash Log State
 */
data class AndroidCrashState(
    val incidents: List<AndroidCrashIncident> = emptyList(),
    val isLoading: Boolean = false,
    val selectedIncident: AndroidCrashIncident? = null,
    val searchFilter: String = ""
)

/**
 * Lynx Daemon Log State
 */
data class LynxDaemonLogState(
    val logs: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val filter: String = "ALL" // "ALL", "ERRORS", "PROFILE", "CHARGING"
)

/**
 * Master Diagnostic Hub State
 */
data class DiagnosticHubState(
    val selectedTab: LogCategoryTab = LogCategoryTab.KERNEL_PANIC,
    val kernelPanic: KernelPanicDetails = KernelPanicDetails(),
    val androidCrashes: AndroidCrashState = AndroidCrashState(),
    val lynxDaemon: LynxDaemonLogState = LynxDaemonLogState(),
    val isExportingZip: Boolean = false,
    val exportZipPath: String? = null,
    val quickSummary: String = "",
    val hasUnreadCrashAlert: Boolean = false
)
