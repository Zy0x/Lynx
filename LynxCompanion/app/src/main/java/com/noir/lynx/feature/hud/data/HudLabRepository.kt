package com.noir.lynx.feature.hud.data

import android.content.Context
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.abs
import kotlin.math.roundToInt

enum class HudCapability {
    CHECKING,
    READY,
    NEEDS_ROOT,
    UNSUPPORTED
}

data class HudBypassStatus(
    val capability: HudCapability = HudCapability.CHECKING,
    val enabled: Boolean = false,
    val active: Boolean = false,
    val chipSummary: String = "",
    val thresholdPct: Int = 40,
    val thermalCutActive: Boolean = false
)

data class HudTouchStatus(
    val capability: HudCapability = HudCapability.CHECKING,
    val enabled: Boolean = false,
    val backendLabel: String = ""
)

data class HudTouchSample(
    val rateHz: Int? = null,
    val intervalMs: Double? = null,
    val activeFingers: Int = 0,
    val maxFingers: Int = 0
)

data class HudShieldStatus(
    val capability: HudCapability = HudCapability.CHECKING,
    val headsUpBlocked: Boolean = false,
    val zenMode: Int = 0
)

data class HudNetworkSample(
    val avgMs: Double? = null,
    val jitterMs: Double? = null,
    val lossPct: Int = 100
) {
    val score: Int
        get() {
            val avg = avgMs ?: return 0
            val jitter = jitterMs ?: 0.0
            val penalty = (avg * 0.45) + (jitter * 1.1) + (lossPct * 0.7)
            return (100.0 - penalty).roundToInt().coerceIn(0, 100)
        }
}

internal fun computeTouchSampleMetrics(
    deltasMs: List<Double>,
    activeFingers: Int,
    previousMaxFingers: Int = 0
): HudTouchSample {
    val filtered = deltasMs.filter { it.isFinite() && it in 0.8..100.0 }
    val maxFingers = maxOf(activeFingers, previousMaxFingers)
    if (filtered.isEmpty()) {
        return HudTouchSample(
            rateHz = null,
            intervalMs = null,
            activeFingers = activeFingers,
            maxFingers = maxFingers
        )
    }
    val avgMs = filtered.average()
    val hz = (1000.0 / avgMs).roundToInt().coerceIn(15, 1000)
    return HudTouchSample(
        rateHz = hz,
        intervalMs = avgMs,
        activeFingers = activeFingers,
        maxFingers = maxFingers
    )
}

internal fun parseChargingStatusJson(raw: String, rootAvailable: Boolean): HudBypassStatus {
    if (!rootAvailable) {
        return HudBypassStatus(capability = HudCapability.NEEDS_ROOT)
    }
    if (raw.isBlank() || !raw.contains("{")) {
        return HudBypassStatus(capability = HudCapability.UNSUPPORTED)
    }
    val enabled = Regex(""""bypass_enabled"\s*:\s*(true|false)""")
        .find(raw)?.groupValues?.getOrNull(1)?.toBooleanStrictOrNull() ?: false
    val active = Regex(""""bypass_active"\s*:\s*(true|false)""")
        .find(raw)?.groupValues?.getOrNull(1)?.toBooleanStrictOrNull() ?: false
    val nodesCount = Regex(""""dynamic_scanned_nodes_count"\s*:\s*(\d+)""")
        .find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    val chips = Regex(""""detected_hardware_chips"\s*:\s*"([^"]*)"""")
        .find(raw)?.groupValues?.getOrNull(1)?.trim().orEmpty()

    val supported = nodesCount > 0 || (chips.isNotBlank() && !chips.equals("Generic Power Supply", ignoreCase = true)) || enabled || active
    return HudBypassStatus(
        capability = if (supported) HudCapability.READY else HudCapability.UNSUPPORTED,
        enabled = enabled,
        active = active,
        chipSummary = chips
    )
}

class HudLabRepository(private val context: Context) {

    private fun binaryPath(): String? {
        val packaged = File(context.applicationInfo.nativeLibraryDir, "liblynxhud.so")
        if (packaged.canExecute()) return packaged.absolutePath
        val moduleBin = File("/data/adb/modules/Lynx/system/bin/lynxd")
        if (moduleBin.canExecute()) return moduleBin.absolutePath
        return null
    }

    private fun isRootAvailable(): Boolean =
        runCatching { Shell.getShell().isRoot }.getOrDefault(false)

    suspend fun probeBypass(): HudBypassStatus = withContext(Dispatchers.IO) {
        val currentSession = com.noir.lynx.feature.hud.addins.HudGamingAddins.current?.bypass
        if (currentSession != null) {
            val status = currentSession.probe()
            if (status.capability != HudCapability.UNSUPPORTED) return@withContext status
        }
        com.noir.lynx.feature.hud.addins.HudBypassSession(context).probe()
    }

    suspend fun setBypass(enabled: Boolean): HudBypassStatus = withContext(Dispatchers.IO) {
        val session = com.noir.lynx.feature.hud.addins.HudGamingAddins.current?.bypass
            ?: com.noir.lynx.feature.hud.addins.HudBypassSession(context)
        session.set(enabled)
        session.probe()
    }

    suspend fun setBypassThreshold(pct: Int): HudBypassStatus = withContext(Dispatchers.IO) {
        val session = com.noir.lynx.feature.hud.addins.HudGamingAddins.current?.bypass
            ?: com.noir.lynx.feature.hud.addins.HudBypassSession(context)
        session.setThreshold(pct)
        session.probe()
    }

    suspend fun probeTouchBoost(): HudTouchStatus = withContext(Dispatchers.IO) {
        val currentSession = com.noir.lynx.feature.hud.addins.HudGamingAddins.current?.touch
        if (currentSession != null) {
            val status = currentSession.probe()
            if (status.capability != HudCapability.UNSUPPORTED) return@withContext status
        }
        com.noir.lynx.feature.hud.addins.HudTouchSession(context).probe()
    }

    suspend fun setTouchBoost(enabled: Boolean): HudTouchStatus = withContext(Dispatchers.IO) {
        val session = com.noir.lynx.feature.hud.addins.HudGamingAddins.current?.touch
            ?: com.noir.lynx.feature.hud.addins.HudTouchSession(context)
        session.set(enabled)
        session.probe()
    }

    suspend fun probeShield(): HudShieldStatus = withContext(Dispatchers.IO) {
        if (!isRootAvailable()) {
            return@withContext HudShieldStatus(capability = HudCapability.NEEDS_ROOT)
        }
        val lines = Shell.cmd(
            "echo \"HEADSUP=\$(settings get global heads_up_notifications_enabled 2>/dev/null)\"; echo \"ZEN=\$(settings get global zen_mode 2>/dev/null)\""
        ).exec().out
        val headsUpRaw = lines.firstOrNull { it.startsWith("HEADSUP=") }?.substringAfter("HEADSUP=")?.trim()
        val zenRaw = lines.firstOrNull { it.startsWith("ZEN=") }?.substringAfter("ZEN=")?.trim()?.toIntOrNull()
        if (zenRaw !in 0..3 || headsUpRaw !in listOf("0", "1", "null")) {
            return@withContext HudShieldStatus(capability = HudCapability.UNSUPPORTED)
        }
        HudShieldStatus(
            capability = HudCapability.READY,
            headsUpBlocked = headsUpRaw == "0",
            zenMode = zenRaw ?: 0
        )
    }

    suspend fun probeNetwork(
        host: String = "1.1.1.1",
        port: Int = 443,
        attempts: Int = 3,
        timeoutMs: Int = 1500
    ): HudNetworkSample = withContext(Dispatchers.IO) {
        val totalAttempts = attempts.coerceAtLeast(1)
        val latencies = ArrayList<Double>(totalAttempts)
        for (i in 0 until totalAttempts) {
            val sampleMs = runCatching {
                Socket().use { socket ->
                    val startNs = System.nanoTime()
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                    (System.nanoTime() - startNs) / 1_000_000.0
                }
            }.getOrNull()
            if (sampleMs != null && sampleMs.isFinite() && sampleMs >= 0.0) {
                latencies.add(sampleMs)
            }
            if (i + 1 < totalAttempts) {
                delay(60L)
            }
        }
        if (latencies.isEmpty()) {
            return@withContext HudNetworkSample(avgMs = null, jitterMs = null, lossPct = 100)
        }
        val avg = latencies.average()
        val jitter = if (latencies.size >= 2) {
            var diffSum = 0.0
            for (i in 1 until latencies.size) {
                diffSum += abs(latencies[i] - latencies[i - 1])
            }
            diffSum / (latencies.size - 1)
        } else {
            0.0
        }
        val failed = totalAttempts - latencies.size
        val lossPct = ((failed * 100.0) / totalAttempts).roundToInt().coerceIn(0, 100)
        HudNetworkSample(avgMs = avg, jitterMs = jitter, lossPct = lossPct)
    }
}
