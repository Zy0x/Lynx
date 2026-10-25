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
    val chipSummary: String = ""
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
        if (!isRootAvailable()) {
            return@withContext HudBypassStatus(capability = HudCapability.NEEDS_ROOT)
        }
        val bin = binaryPath() ?: return@withContext HudBypassStatus(capability = HudCapability.UNSUPPORTED)
        val qBin = "'" + bin.replace("'", "'\\''") + "'"
        val raw = Shell.cmd("$qBin charging status 2>/dev/null").exec().out.joinToString("\n")
        parseChargingStatusJson(raw, rootAvailable = true)
    }

    suspend fun setBypass(enabled: Boolean): HudBypassStatus = withContext(Dispatchers.IO) {
        if (!isRootAvailable()) {
            return@withContext HudBypassStatus(capability = HudCapability.NEEDS_ROOT)
        }
        val bin = binaryPath() ?: return@withContext HudBypassStatus(capability = HudCapability.UNSUPPORTED)
        val qBin = "'" + bin.replace("'", "'\\''") + "'"
        val cmd = if (enabled) {
            "$qBin state set charging.bypass_enabled=true >/dev/null 2>&1; $qBin charging bypass >/dev/null 2>&1; $qBin charging status 2>/dev/null"
        } else {
            "rm -f /dev/lynx_bypass_active 2>/dev/null; $qBin state set charging.bypass_enabled=false >/dev/null 2>&1; $qBin charging apply >/dev/null 2>&1; $qBin charging status 2>/dev/null"
        }
        val raw = Shell.cmd(cmd).exec().out.joinToString("\n")
        parseChargingStatusJson(raw, rootAvailable = true)
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
