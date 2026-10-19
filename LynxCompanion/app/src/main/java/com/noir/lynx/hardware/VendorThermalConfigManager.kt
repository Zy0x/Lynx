package com.noir.lynx.hardware

import com.noir.lynx.data.MODULE_DIR
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Data model for battery charging mitigation trip points.
 * All temperatures in Celsius for UI (internally scaled to milli-Celsius for config files).
 */
data class BatteryChargingThresholds(
    val t1: Int, // Level 1 (e.g. 37-40°C)
    val t2: Int, // Level 2 (e.g. 38-42°C)
    val t3: Int, // Level 3 (e.g. 41-45°C)
    val t4: Int, // Level 4 (e.g. 42-48°C)
    val t5: Int, // Level 5 (e.g. 45-52°C)
    val sensorName: String = "quiet-therm-adc",
    val samplingMs: Int = 10000,
    val actionInfo: String = "1 2 4 5 6"
) {
    fun toThresholdsMilliC(): String =
        "${t1 * 1000} ${t2 * 1000} ${t3 * 1000} ${t4 * 1000} ${t5 * 1000}"

    /**
     * Automatic -2°C hysteresis calculation preventing rapid flapping.
     */
    fun toThresholdsClrMilliC(): String {
        val c1 = (t1 - 2).coerceAtLeast(30)
        val c2 = (t2 - 2).coerceAtLeast(c1 + 1)
        val c3 = (t3 - 2).coerceAtLeast(c2 + 1)
        val c4 = (t4 - 2).coerceAtLeast(c3 + 1)
        val c5 = (t5 - 2).coerceAtLeast(c4 + 1)
        return "${c1 * 1000} ${c2 * 1000} ${c3 * 1000} ${c4 * 1000} ${c5 * 1000}"
    }
}

/**
 * Snapshot of vendor thermal architecture and Magisk Magic Mount overlay state.
 */
data class VendorThermalStudioState(
    val isSupported: Boolean = false,
    val vendorArchitecture: String = "generic", // "qcom", "mtk", "generic"
    val stockConfigPath: String = "",
    val overlayConfigPath: String = "",
    val isOverlayActive: Boolean = false,
    val stockThresholds: BatteryChargingThresholds = BatteryChargingThresholds(37, 38, 41, 42, 45),
    val activeThresholds: BatteryChargingThresholds = BatteryChargingThresholds(37, 38, 41, 42, 45),
    val rawSection: String = "",
    val message: String = ""
)

/**
 * VendorThermalConfigManager — Manages vendor-level thermal daemon configurations
 * (Qualcomm thermal-engine.conf & MediaTek .tp/thermal.conf) via Magisk/KernelSU Magic Mount.
 */
object VendorThermalConfigManager {

    private const val QCOM_STOCK_CONF = "/vendor/etc/thermal-engine.conf"
    private const val QCOM_OVERLAY_CONF = "$MODULE_DIR/system/vendor/etc/thermal-engine.conf"

    private const val MTK_STOCK_CONF = "/vendor/etc/.tp/thermal.conf"
    private const val MTK_OVERLAY_CONF = "$MODULE_DIR/system/vendor/etc/.tp/thermal.conf"

    /**
     * Detects thermal architecture, locates stock files, and checks for existing module overlays.
     */
    suspend fun detectState(): VendorThermalStudioState = withContext(Dispatchers.IO) {
        try {
            val soc = com.noir.lynx.data.LynxRepository.getSocPlatformName().lowercase()
            val isQcom = soc.contains("qcom") || soc.contains("qualcomm") || soc.contains("sdm") ||
                    soc.contains("sm") || soc.contains("msm") || java.io.File("/sys/class/kgsl").exists()
            val isMtk = soc.contains("mtk") || soc.contains("mediatek") || soc.contains("mt") ||
                    java.io.File("/proc/ppm").exists() || java.io.File("/dev/ged").exists()

            val vendorArch = when {
                isQcom -> "qcom"
                isMtk -> "mtk"
                else -> "generic"
            }

            val (stockPath, overlayPath) = when (vendorArch) {
                "qcom" -> QCOM_STOCK_CONF to QCOM_OVERLAY_CONF
                "mtk" -> MTK_STOCK_CONF to MTK_OVERLAY_CONF
                else -> "" to ""
            }

            if (stockPath.isEmpty()) {
                return@withContext VendorThermalStudioState(
                    isSupported = false,
                    vendorArchitecture = vendorArch,
                    message = "Arsitektur $vendorArch tidak mendukung konfigurasi file thermal vendor."
                )
            }

            // Probe existence using root shell (bypassing Android 10+ vendor SELinux denials)
            val probeCmd = """
                if [ -f "$stockPath" ]; then echo "STOCK_OK"; else echo "STOCK_MISSING"; fi
                if [ -f "$overlayPath" ]; then echo "OVERLAY_OK"; else echo "OVERLAY_MISSING"; fi
            """.trimIndent()
            val probeRes = Shell.cmd(probeCmd).exec()
            val hasStock = probeRes.out.contains("STOCK_OK")
            val hasOverlay = probeRes.out.contains("OVERLAY_OK")

            if (!hasStock && !hasOverlay) {
                return@withContext VendorThermalStudioState(
                    isSupported = false,
                    vendorArchitecture = vendorArch,
                    stockConfigPath = stockPath,
                    overlayConfigPath = overlayPath,
                    message = "Berkas konfigurasi termal vendor ($stockPath) tidak ditemukan di partisi vendor."
                )
            }

            // Read stock baseline
            val stockRaw = readProtectedFile(stockPath)
            val stockParsed = parseBatterySection(stockRaw) ?: BatteryChargingThresholds(37, 38, 41, 42, 45)

            // Read overlay if exists
            val (activeParsed, rawActive) = if (hasOverlay) {
                val overlayRaw = readProtectedFile(overlayPath)
                (parseBatterySection(overlayRaw) ?: stockParsed) to overlayRaw
            } else {
                stockParsed to stockRaw
            }

            VendorThermalStudioState(
                isSupported = true,
                vendorArchitecture = vendorArch,
                stockConfigPath = stockPath,
                overlayConfigPath = overlayPath,
                isOverlayActive = hasOverlay,
                stockThresholds = stockParsed,
                activeThresholds = activeParsed,
                rawSection = rawActive,
                message = if (hasOverlay) "Overlay Lynx Magic Mount aktif" else "Menggunakan konfigurasi bawaan OEM"
            )
        } catch (e: Exception) {
            VendorThermalStudioState(
                isSupported = false,
                message = "Gagal mendeteksi konfigurasi termal vendor: ${e.message}"
            )
        }
    }

    /**
     * Parses the [BATTERY_CHARGING_CTL] section from INI-formatted thermal-engine.conf.
     */
    private fun parseBatterySection(content: String): BatteryChargingThresholds? {
        if (content.isBlank()) return null
        val lines = content.lines()
        var inBatterySection = false
        var thresholdsStr = ""
        var sensorName = "quiet-therm-adc"
        var samplingMs = 10000
        var actionInfo = "1 2 4 5 6"

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                val sectionName = trimmed.substring(1, trimmed.length - 1).trim()
                inBatterySection = sectionName.equals("BATTERY_CHARGING_CTL", ignoreCase = true)
                continue
            }
            if (inBatterySection) {
                when {
                    trimmed.startsWith("thresholds ", ignoreCase = true) -> {
                        thresholdsStr = trimmed.substringAfter("thresholds").trim()
                    }
                    trimmed.startsWith("sensor ", ignoreCase = true) -> {
                        sensorName = trimmed.substringAfter("sensor").trim()
                    }
                    trimmed.startsWith("sampling ", ignoreCase = true) -> {
                        samplingMs = trimmed.substringAfter("sampling").trim().toIntOrNull() ?: 10000
                    }
                    trimmed.startsWith("action_info ", ignoreCase = true) -> {
                        actionInfo = trimmed.substringAfter("action_info").trim()
                    }
                }
            }
        }

        if (thresholdsStr.isNotEmpty()) {
            val tokens = thresholdsStr.split("\\s+".toRegex()).mapNotNull { it.toIntOrNull() }
            if (tokens.size >= 5) {
                return BatteryChargingThresholds(
                    t1 = tokens[0] / 1000,
                    t2 = tokens[1] / 1000,
                    t3 = tokens[2] / 1000,
                    t4 = tokens[3] / 1000,
                    t5 = tokens[4] / 1000,
                    sensorName = sensorName,
                    samplingMs = samplingMs,
                    actionInfo = actionInfo
                )
            }
        }
        return null
    }

    /**
     * Generates a custom thermal-engine.conf with updated charging thresholds and
     * places it cleanly in the active Lynx Magisk module directory with proper permissions.
     */
    suspend fun applyCustomThresholds(
        thresholds: BatteryChargingThresholds
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val state = detectState()
            if (!state.isSupported) {
                return@withContext Result.failure(IllegalStateException(state.message))
            }

            // 1. Read base configuration (from active overlay or stock baseline)
            val baseContent = readProtectedFile(
                if (state.isOverlayActive) state.overlayConfigPath else state.stockConfigPath
            )

            // 2. Build modified INI content
            val newContent = buildModifiedContent(baseContent, thresholds)

            // 3. Write atomically to temporary file first
            val tmpPath = "/data/local/tmp/lynx_thermal_engine.tmp"
            val targetPath = state.overlayConfigPath
            val targetDir = targetPath.substringBeforeLast('/')

            val writeScript = """
                mkdir -p "$targetDir" 2>/dev/null
                cat << 'EOF' > "$tmpPath"
$newContent
EOF
                if [ -s "$tmpPath" ]; then
                    cp -af "$tmpPath" "$targetPath"
                    chmod 644 "$targetPath"
                    chcon u:object_r:system_file:s0 "$targetPath" 2>/dev/null
                    rm -f "$tmpPath" 2>/dev/null
                    echo "APPLY_OK"
                else
                    echo "WRITE_FAILED"
                fi
            """.trimIndent()

            val res = Shell.cmd(writeScript).exec()
            if (res.out.contains("APPLY_OK")) {
                Result.success("Overlay berhasil dipasang ke $targetPath. Memerlukan Reboot untuk aktif.")
            } else {
                Result.failure(RuntimeException("Gagal menulis berkas overlay: ${res.err.joinToString("\n")}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Injects or modifies the [BATTERY_CHARGING_CTL] section inside the config text.
     */
    private fun buildModifiedContent(
        original: String,
        thresholds: BatteryChargingThresholds
    ): String {
        val newThresholdsLine = "thresholds ${thresholds.toThresholdsMilliC()}"
        val newThresholdsClrLine = "thresholds_clr ${thresholds.toThresholdsClrMilliC()}"

        if (!original.contains("[BATTERY_CHARGING_CTL]", ignoreCase = true)) {
            // Append clean section if missing
            return original.trimEnd() + "\n\n" + """
                [BATTERY_CHARGING_CTL]
                algo_type monitor
                sampling ${thresholds.samplingMs}
                sensor ${thresholds.sensorName}
                $newThresholdsLine
                $newThresholdsClrLine
                actions battery battery battery battery battery
                action_info ${thresholds.actionInfo}
            """.trimIndent() + "\n"
        }

        // Section exists: replace thresholds and thresholds_clr line by line
        val lines = original.lines()
        val result = mutableListOf<String>()
        var inBattery = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                inBattery = trimmed.equals("[BATTERY_CHARGING_CTL]", ignoreCase = true)
                result.add(line)
                continue
            }

            if (inBattery) {
                when {
                    trimmed.startsWith("thresholds ", ignoreCase = true) -> {
                        result.add(newThresholdsLine)
                    }
                    trimmed.startsWith("thresholds_clr ", ignoreCase = true) -> {
                        result.add(newThresholdsClrLine)
                    }
                    else -> result.add(line)
                }
            } else {
                result.add(line)
            }
        }

        return result.joinToString("\n")
    }

    /**
     * Removes the module overlay file, returning thermal management to the OEM stock baseline.
     */
    suspend fun resetToStockBaseline(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val state = detectState()
            if (!state.isOverlayActive) {
                return@withContext Result.success("Sistem sudah menggunakan konfigurasi bawaan OEM.")
            }

            val rmCmd = """
                rm -f "${state.overlayConfigPath}" 2>/dev/null
                if [ ! -f "${state.overlayConfigPath}" ]; then echo "REMOVE_OK"; else echo "REMOVE_FAILED"; fi
            """.trimIndent()

            val res = Shell.cmd(rmCmd).exec()
            if (res.out.contains("REMOVE_OK")) {
                Result.success("Overlay berhasil dihapus. Sistem akan kembali ke stock baseline setelah Reboot.")
            } else {
                Result.failure(RuntimeException("Gagal menghapus overlay: ${res.err.joinToString("\n")}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reads file contents via Root Shell to bypass restrictive Android permissions.
     */
    private fun readProtectedFile(path: String): String {
        val res = Shell.cmd("cat \"$path\" 2>/dev/null").exec()
        return if (res.isSuccess) res.out.joinToString("\n") else ""
    }
}
