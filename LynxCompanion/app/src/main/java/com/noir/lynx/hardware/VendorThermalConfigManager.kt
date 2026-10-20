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
 * Toggles for additional vendor thermal daemon rules.
 */
data class VendorThermalTweaks(
    val antiLcdDimming: Boolean = false,
    val cpuGpuHeadroom: Boolean = false
)

/**
 * Real-time hardware telemetry and current active mitigation level.
 */
data class LiveThermalTelemetry(
    val sensorTempC: Float = 0f,
    val coolingState: Int = 0,
    val activeMitigationLevel: Int = 0, // 0 = Unthrottled, 1..5 = Level 1..5
    val isThrottled: Boolean = false,
    val estimatedMaxCurrentMa: Int = 3000,
    val daemonPid: String = ""
)

/**
 * Snapshot of vendor thermal architecture, live bind mount, and Magisk Magic Mount overlay state.
 */
data class VendorThermalStudioState(
    val isSupported: Boolean = false,
    val vendorArchitecture: String = "generic", // "qcom", "mtk", "generic"
    val stockConfigPath: String = "",
    val overlayConfigPath: String = "",
    val isOverlayActive: Boolean = false,
    val isLiveBindMounted: Boolean = false,
    val stockThresholds: BatteryChargingThresholds = BatteryChargingThresholds(37, 38, 41, 42, 45),
    val activeThresholds: BatteryChargingThresholds = BatteryChargingThresholds(37, 38, 41, 42, 45),
    val tweaks: VendorThermalTweaks = VendorThermalTweaks(),
    val telemetry: LiveThermalTelemetry = LiveThermalTelemetry(),
    val rawSection: String = "",
    val message: String = ""
)

/**
 * VendorThermalConfigManager — Manages vendor-level thermal daemon configurations
 * (Qualcomm thermal-engine.conf & MediaTek .tp/thermal.conf) via Magisk/KernelSU Magic Mount
 * and live runtime bind mount testing without reboot.
 */
object VendorThermalConfigManager {

    private const val QCOM_STOCK_CONF = "/vendor/etc/thermal-engine.conf"
    private const val QCOM_OVERLAY_CONF = "$MODULE_DIR/system/vendor/etc/thermal-engine.conf"
    private const val QCOM_LIVE_TMP_CONF = "/data/local/tmp/lynx_thermal_engine_live.conf"

    private const val MTK_STOCK_CONF = "/vendor/etc/.tp/thermal.conf"
    private const val MTK_OVERLAY_CONF = "$MODULE_DIR/system/vendor/etc/.tp/thermal.conf"

    /**
     * Detects thermal architecture, locates stock files, checks active overlays and bind mounts.
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

            // Probe existence and bind mount status
            val probeCmd = """
                if [ -f "$stockPath" ]; then echo "STOCK_OK"; else echo "STOCK_MISSING"; fi
                if [ -f "$overlayPath" ]; then echo "OVERLAY_OK"; else echo "OVERLAY_MISSING"; fi
                if grep -q "$stockPath" /proc/mounts 2>/dev/null; then echo "BIND_ACTIVE"; fi
            """.trimIndent()
            val probeRes = Shell.cmd(probeCmd).exec()
            val hasStock = probeRes.out.contains("STOCK_OK")
            val hasOverlay = probeRes.out.contains("OVERLAY_OK")
            val isBindMounted = probeRes.out.contains("BIND_ACTIVE")

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

            // Read overlay or active file
            val (activeParsed, rawActive) = if (hasOverlay) {
                val overlayRaw = readProtectedFile(overlayPath)
                (parseBatterySection(overlayRaw) ?: stockParsed) to overlayRaw
            } else {
                stockParsed to stockRaw
            }

            val tweaks = detectTweaks(rawActive)
            val telemetry = queryLiveTelemetry(activeParsed)

            VendorThermalStudioState(
                isSupported = true,
                vendorArchitecture = vendorArch,
                stockConfigPath = stockPath,
                overlayConfigPath = overlayPath,
                isOverlayActive = hasOverlay,
                isLiveBindMounted = isBindMounted,
                stockThresholds = stockParsed,
                activeThresholds = activeParsed,
                tweaks = tweaks,
                telemetry = telemetry,
                rawSection = rawActive,
                message = when {
                    isBindMounted -> "Konfigurasi aktif secara Live via Bind Mount"
                    hasOverlay -> "Overlay Lynx Magic Mount aktif di direktori modul"
                    else -> "Menggunakan konfigurasi bawaan pabrik OEM"
                }
            )
        } catch (e: Exception) {
            VendorThermalStudioState(
                isSupported = false,
                message = "Gagal mendeteksi konfigurasi termal vendor: ${e.message}"
            )
        }
    }

    /**
     * Queries real-time battery / thermistor temperature, cooling state, and active mitigation level.
     */
    suspend fun queryLiveTelemetry(thresholds: BatteryChargingThresholds): LiveThermalTelemetry = withContext(Dispatchers.IO) {
        try {
            val cmd = """
                # 1. Read battery/thermistor temperature in Celsius
                temp=0
                if [ -f /sys/class/power_supply/battery/temp ]; then
                    raw=${'$'}(cat /sys/class/power_supply/battery/temp 2>/dev/null)
                    if [ "${'$'}raw" -gt 1000 ]; then temp=${'$'}((raw / 1000)); else temp=${'$'}((raw / 10)); fi
                fi
                if [ "${'$'}temp" -eq 0 ]; then
                    for tz in /sys/class/thermal/thermal_zone*; do
                        [ -d "${'$'}tz" ] || continue
                        type=${'$'}(cat "${'$'}tz/type" 2>/dev/null)
                        case "${'$'}type" in
                            *quiet-therm*|*battery*|*bms*)
                                raw=${'$'}(cat "${'$'}tz/temp" 2>/dev/null)
                                if [ "${'$'}raw" -gt 1000 ]; then temp=${'$'}((raw / 1000)); else temp=${'$'}raw; fi
                                break
                                ;;
                        esac
                    done
                fi

                # 2. Read cooling device state for battery
                cstate=0
                for cd in /sys/class/thermal/cooling_device*; do
                    [ -d "${'$'}cd" ] || continue
                    type=${'$'}(cat "${'$'}cd/type" 2>/dev/null)
                    case "${'$'}type" in
                        *battery*|*charger*|*bms*)
                            cstate=${'$'}(cat "${'$'}cd/cur_state" 2>/dev/null || echo 0)
                            break
                            ;;
                    esac
                done

                # 3. Check daemon process
                pid=${'$'}(pgrep -f thermal-engine | head -n 1)

                echo "TEL|${'$'}temp|${'$'}cstate|${'$'}pid"
            """.trimIndent()

            val res = Shell.cmd(cmd).exec()
            val line = res.out.firstOrNull { it.startsWith("TEL|") } ?: "TEL|0|0|"
            val parts = line.split("|")
            val tempC = parts.getOrNull(1)?.toFloatOrNull() ?: 0f
            val cstate = parts.getOrNull(2)?.toIntOrNull() ?: 0
            val pid = parts.getOrNull(3) ?: ""

            // Calculate active level based on temp vs thresholds
            val level = when {
                tempC <= 0f -> 0
                tempC < thresholds.t1 -> 0 // Unthrottled
                tempC < thresholds.t2 -> 1 // Level 1
                tempC < thresholds.t3 -> 2 // Level 2
                tempC < thresholds.t4 -> 3 // Level 3
                tempC < thresholds.t5 -> 4 // Level 4
                else -> 5 // Level 5
            }

            val estimatedMa = when (level) {
                0 -> 3100
                1 -> 2500
                2 -> 2000
                3 -> 1500
                4 -> 1000
                5 -> 0
                else -> 3000
            }

            LiveThermalTelemetry(
                sensorTempC = tempC,
                coolingState = cstate,
                activeMitigationLevel = level,
                isThrottled = level > 0,
                estimatedMaxCurrentMa = estimatedMa,
                daemonPid = pid
            )
        } catch (_: Exception) {
            LiveThermalTelemetry()
        }
    }

    /**
     * Detects if Anti-Dimming or CPU/GPU Headroom tweaks are present in config.
     */
    private fun detectTweaks(content: String): VendorThermalTweaks {
        if (content.isBlank()) return VendorThermalTweaks()
        val hasAntiDim = content.contains("thresholds 56000 60000") ||
                content.contains("thresholds 55000 58000") ||
                content.contains("# [LCD_MANAGEMENT]")
        val hasHeadroom = content.contains("set_point 65000") && content.contains("[SS-MSM-THERM")
        return VendorThermalTweaks(antiLcdDimming = hasAntiDim, cpuGpuHeadroom = hasHeadroom)
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
     * Generates custom thermal-engine.conf and applies it permanently to the Magisk module directory.
     */
    suspend fun applyCustomThresholds(
        thresholds: BatteryChargingThresholds,
        tweaks: VendorThermalTweaks = VendorThermalTweaks()
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val state = detectState()
            if (!state.isSupported) {
                return@withContext Result.failure(IllegalStateException(state.message))
            }

            val baseContent = readProtectedFile(
                if (state.isOverlayActive) state.overlayConfigPath else state.stockConfigPath
            )

            val newContent = buildModifiedContent(baseContent, thresholds, tweaks)
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
                Result.success("Overlay berhasil dipasang ke modul Lynx. Memerlukan Reboot untuk aktif permanen.")
            } else {
                Result.failure(RuntimeException("Gagal menulis berkas overlay: ${res.err.joinToString("\n")}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Applies custom config immediately at runtime via Linux Bind Mount and reloads thermal daemon
     * WITHOUT REBOOTING the device.
     */
    suspend fun applyLiveBindMount(
        thresholds: BatteryChargingThresholds,
        tweaks: VendorThermalTweaks = VendorThermalTweaks()
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val state = detectState()
            if (!state.isSupported) {
                return@withContext Result.failure(IllegalStateException(state.message))
            }

            val baseContent = readProtectedFile(
                if (state.isOverlayActive) state.overlayConfigPath else state.stockConfigPath
            )
            val newContent = buildModifiedContent(baseContent, thresholds, tweaks)
            val liveConf = QCOM_LIVE_TMP_CONF
            val stockConf = state.stockConfigPath

            val liveScript = """
                cat << 'EOF' > "$liveConf"
$newContent
EOF
                if [ ! -s "$liveConf" ]; then
                    echo "WRITE_FAIL"
                    exit 1
                fi
                chmod 644 "$liveConf"
                chcon u:object_r:vendor_configs_file:s0 "$liveConf" 2>/dev/null || chcon u:object_r:system_file:s0 "$liveConf" 2>/dev/null

                # Unmount previous live mount if present to avoid nesting
                umount "$stockConf" 2>/dev/null

                # Bind mount custom config over vendor stock file
                mount -o bind "$liveConf" "$stockConf"
                if grep -q "$stockConf" /proc/mounts 2>/dev/null; then
                    # Restart thermal-engine service cleanly via init
                    stop vendor.thermal-engine 2>/dev/null || stop thermal-engine 2>/dev/null
                    sleep 0.3
                    start vendor.thermal-engine 2>/dev/null || start thermal-engine 2>/dev/null
                    killall -HUP thermal-engine 2>/dev/null
                    echo "LIVE_MOUNT_OK"
                else
                    echo "BIND_FAIL"
                fi
            """.trimIndent()

            val res = Shell.cmd(liveScript).exec()
            if (res.out.contains("LIVE_MOUNT_OK")) {
                Result.success("Berhasil diuji secara Live! Daemon thermal-engine telah dimuat ulang tanpa reboot.")
            } else {
                Result.failure(RuntimeException("Gagal menerapkan live bind mount: ${res.err.joinToString("\n")}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Cancels live bind mount and restores stock runtime vendor config.
     */
    suspend fun cancelLiveBindMount(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val state = detectState()
            val stockConf = state.stockConfigPath
            val revertScript = """
                umount "$stockConf" 2>/dev/null
                rm -f "$QCOM_LIVE_TMP_CONF" 2>/dev/null
                stop vendor.thermal-engine 2>/dev/null || stop thermal-engine 2>/dev/null
                sleep 0.3
                start vendor.thermal-engine 2>/dev/null || start thermal-engine 2>/dev/null
                echo "UNMOUNT_OK"
            """.trimIndent()

            val res = Shell.cmd(revertScript).exec()
            if (res.out.contains("UNMOUNT_OK")) {
                Result.success("Live bind mount dibatalkan. Daemon kembali ke konfigurasi normal.")
            } else {
                Result.failure(RuntimeException("Gagal membatalkan live bind mount."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Injects or modifies [BATTERY_CHARGING_CTL], [LCD_MANAGEMENT], and CPU/GPU rules.
     */
    private fun buildModifiedContent(
        original: String,
        thresholds: BatteryChargingThresholds,
        tweaks: VendorThermalTweaks
    ): String {
        val newThresholdsLine = "thresholds ${thresholds.toThresholdsMilliC()}"
        val newThresholdsClrLine = "thresholds_clr ${thresholds.toThresholdsClrMilliC()}"

        val lines = original.lines()
        val result = mutableListOf<String>()
        var inBattery = false
        var inLcd = false
        var inSilver = false
        var inGold = false
        var hasBatterySection = original.contains("[BATTERY_CHARGING_CTL]", ignoreCase = true)

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                inBattery = trimmed.equals("[BATTERY_CHARGING_CTL]", ignoreCase = true)
                inLcd = trimmed.equals("[LCD_MANAGEMENT]", ignoreCase = true)
                inSilver = trimmed.startsWith("[SS-MSM-THERM-SILVER", ignoreCase = true)
                inGold = trimmed.startsWith("[SS-MSM-THERM-GOLD", ignoreCase = true)
                result.add(line)
                continue
            }

            if (inBattery) {
                when {
                    trimmed.startsWith("thresholds ", ignoreCase = true) -> result.add(newThresholdsLine)
                    trimmed.startsWith("thresholds_clr ", ignoreCase = true) -> result.add(newThresholdsClrLine)
                    else -> result.add(line)
                }
            } else if (inLcd && tweaks.antiLcdDimming) {
                // Relax LCD dimming thresholds to 56°C / 60°C
                when {
                    trimmed.startsWith("thresholds ", ignoreCase = true) -> result.add("thresholds 56000 60000")
                    trimmed.startsWith("thresholds_clr ", ignoreCase = true) -> result.add("thresholds_clr 54000 58000")
                    else -> result.add(line)
                }
            } else if ((inSilver || inGold) && tweaks.cpuGpuHeadroom) {
                // Bump CPU set points by +5°C for sustained gaming FPS
                when {
                    trimmed.startsWith("set_point 60000", ignoreCase = true) -> result.add("set_point 65000")
                    trimmed.startsWith("set_point_clr 55000", ignoreCase = true) -> result.add("set_point_clr 60000")
                    trimmed.startsWith("set_point 68000", ignoreCase = true) -> result.add("set_point 73000")
                    trimmed.startsWith("set_point_clr 62000", ignoreCase = true) -> result.add("set_point_clr 67000")
                    trimmed.startsWith("set_point 75000", ignoreCase = true) -> result.add("set_point 80000")
                    trimmed.startsWith("set_point_clr 70000", ignoreCase = true) -> result.add("set_point_clr 75000")
                    else -> result.add(line)
                }
            } else {
                result.add(line)
            }
        }

        if (!hasBatterySection) {
            result.add("")
            result.add("[BATTERY_CHARGING_CTL]")
            result.add("algo_type monitor")
            result.add("sampling ${thresholds.samplingMs}")
            result.add("sensor ${thresholds.sensorName}")
            result.add(newThresholdsLine)
            result.add(newThresholdsClrLine)
            result.add("actions battery battery battery battery battery")
            result.add("action_info ${thresholds.actionInfo}")
        }

        return result.joinToString("\n")
    }

    /**
     * Removes the module overlay file, returning thermal management to the OEM stock baseline.
     */
    suspend fun resetToStockBaseline(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val state = detectState()
            cancelLiveBindMount()

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
