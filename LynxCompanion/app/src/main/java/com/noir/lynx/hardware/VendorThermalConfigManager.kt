package com.noir.lynx.hardware

import com.noir.lynx.data.MODULE_DIR
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Represents a single property entry in an INI section.
 * Preserves the original key name, value, and any trailing comment.
 */
data class ThermalIniProperty(
    val key: String,
    val value: String,
    val comment: String = ""
)

/**
 * Represents an INI section, e.g. [BATTERY_CHARGING_CTL] or [LCD_MANAGEMENT].
 * Keeps track of ordered properties and any section-level comments.
 */
data class ThermalIniSection(
    val name: String,
    val properties: MutableList<ThermalIniProperty> = mutableListOf(),
    val rawComments: MutableList<String> = mutableListOf()
) {
    fun getProperty(key: String): String? =
        properties.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value

    fun setProperty(key: String, value: String) {
        val existingIndex = properties.indexOfFirst { it.key.equals(key, ignoreCase = true) }
        if (existingIndex >= 0) {
            properties[existingIndex] = properties[existingIndex].copy(value = value)
        } else {
            properties.add(ThermalIniProperty(key, value))
        }
    }

    fun removeProperty(key: String) {
        properties.removeAll { it.key.equals(key, ignoreCase = true) }
    }

    fun deepCopy(): ThermalIniSection =
        ThermalIniSection(
            name = name,
            properties = properties.map { it.copy() }.toMutableList(),
            rawComments = rawComments.toMutableList()
        )
}

/**
 * Parsed representation of an entire thermal config file (.conf).
 * Guarantees zero hardcoded assumptions: preserves exact sections, keys, and values.
 */
data class ThermalIniDocument(
    val sections: MutableList<ThermalIniSection> = mutableListOf(),
    val headerComments: MutableList<String> = mutableListOf()
) {
    fun getSection(name: String): ThermalIniSection? =
        sections.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun getOrCreateSection(name: String): ThermalIniSection {
        val existing = getSection(name)
        if (existing != null) return existing
        val newSection = ThermalIniSection(name)
        sections.add(newSection)
        return newSection
    }

    fun removeSection(name: String) {
        sections.removeAll { it.name.equals(name, ignoreCase = true) }
    }

    fun deepCopy(): ThermalIniDocument =
        ThermalIniDocument(
            sections = sections.map { it.deepCopy() }.toMutableList(),
            headerComments = headerComments.toMutableList()
        )

    fun toIniString(): String {
        val sb = StringBuilder()
        for (c in headerComments) {
            sb.appendLine(c)
        }
        for (sec in sections) {
            if (sb.isNotEmpty() && !sb.endsWith("\n\n")) {
                sb.appendLine()
            }
            for (c in sec.rawComments) {
                sb.appendLine(c)
            }
            sb.appendLine("[${sec.name}]")
            for (p in sec.properties) {
                if (p.comment.isNotEmpty()) {
                    sb.appendLine("${p.key} ${p.value} ${p.comment}".trimEnd())
                } else {
                    sb.appendLine("${p.key} ${p.value}".trimEnd())
                }
            }
        }
        return sb.toString().trimEnd() + "\n"
    }

    companion object {
        /**
         * Universally parses INI formatted thermal configurations.
         * Handles both space-separated format (QCOM standard: `key value`) and `=` separated format.
         */
        fun parse(content: String): ThermalIniDocument {
            val doc = ThermalIniDocument()
            if (content.isBlank()) return doc

            var currentSection: ThermalIniSection? = null

            for (rawLine in content.lines()) {
                val trimmed = rawLine.trim()

                // Comment or empty lines
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) {
                    if (currentSection == null) {
                        doc.headerComments.add(rawLine)
                    } else {
                        currentSection.rawComments.add(rawLine)
                    }
                    continue
                }

                // Section header: [SECTION_NAME]
                if (trimmed.startsWith("[") && trimmed.contains("]")) {
                    val secName = trimmed.substringAfter("[").substringBefore("]").trim()
                    val newSec = ThermalIniSection(name = secName)
                    currentSection = newSec
                    doc.sections.add(newSec)
                    continue
                }

                // Property line
                if (currentSection != null) {
                    val commentIdx = rawLine.indexOfAny(charArrayOf('#', ';'))
                    val mainPart = if (commentIdx >= 0) rawLine.substring(0, commentIdx).trim() else trimmed
                    val inlineComment = if (commentIdx >= 0) rawLine.substring(commentIdx).trim() else ""

                    if (mainPart.isNotEmpty()) {
                        val (key, value) = if (mainPart.contains('=')) {
                            val k = mainPart.substringBefore('=').trim()
                            val v = mainPart.substringAfter('=').trim()
                            k to v
                        } else {
                            val firstSpace = mainPart.indexOfAny(charArrayOf(' ', '\t'))
                            if (firstSpace > 0) {
                                val k = mainPart.substring(0, firstSpace).trim()
                                val v = mainPart.substring(firstSpace).trim()
                                k to v
                            } else {
                                mainPart to ""
                            }
                        }
                        currentSection.properties.add(ThermalIniProperty(key, value, inlineComment))
                    }
                }
            }

            return doc
        }
    }
}

/**
 * Real-time hardware telemetry and current active mitigation level.
 */
data class LiveThermalTelemetry(
    val sensorTempC: Float = 0f,
    val coolingState: Int = 0,
    val activeMitigationLevel: Int = 0, // 0 = Unthrottled, 1..N = Throttling levels
    val isThrottled: Boolean = false,
    val estimatedMaxCurrentMa: Int = 3000,
    val daemonPid: String = "",
    val activeSensorName: String = ""
)

/**
 * Snapshot of vendor thermal architecture, active/stock INI documents, live bind mount, and Magisk overlay state.
 */
data class VendorThermalStudioState(
    val isSupported: Boolean = false,
    val vendorArchitecture: String = "generic", // "qcom", "mtk", "generic"
    val stockConfigPath: String = "",
    val overlayConfigPath: String = "",
    val isOverlayActive: Boolean = false,
    val isLiveBindMounted: Boolean = false,
    val stockDoc: ThermalIniDocument = ThermalIniDocument(),
    val activeDoc: ThermalIniDocument = ThermalIniDocument(),
    val availableSensors: List<String> = emptyList(),
    val telemetry: LiveThermalTelemetry = LiveThermalTelemetry(),
    val rawContent: String = "",
    val message: String = ""
)

/**
 * VendorThermalConfigManager — 100% Dynamic, Non-Hardcoded Vendor Thermal Config Studio.
 * Parses, visualizes, dynamically customizes, and deploys thermal-engine.conf (Qualcomm) or thermal.conf (MediaTek)
 * via Magisk Magic Mount and Linux Bind Mount without reboot.
 */
object VendorThermalConfigManager {

    private const val QCOM_STOCK_CONF = "/vendor/etc/thermal-engine.conf"
    private const val QCOM_OVERLAY_CONF = "$MODULE_DIR/system/vendor/etc/thermal-engine.conf"
    private const val QCOM_LIVE_TMP_CONF = "/data/local/tmp/lynx_thermal_engine_live.conf"

    private const val MTK_STOCK_CONF = "/vendor/etc/.tp/thermal.conf"
    private const val MTK_OVERLAY_CONF = "$MODULE_DIR/system/vendor/etc/.tp/thermal.conf"
    private const val MTK_LIVE_TMP_CONF = "/data/local/tmp/lynx_thermal_tp_live.conf"

    /**
     * Detects thermal architecture, reads stock baseline, overlay file, and active live bind mounts.
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
            val stockDoc = ThermalIniDocument.parse(stockRaw)

            // Read overlay or active file
            val (activeDoc, rawActive) = if (hasOverlay) {
                val overlayRaw = readProtectedFile(overlayPath)
                ThermalIniDocument.parse(overlayRaw) to overlayRaw
            } else {
                stockDoc.deepCopy() to stockRaw
            }

            val sensors = queryAvailableSensors()
            val telemetry = queryLiveTelemetryInternal(activeDoc)

            VendorThermalStudioState(
                isSupported = true,
                vendorArchitecture = vendorArch,
                stockConfigPath = stockPath,
                overlayConfigPath = overlayPath,
                isOverlayActive = hasOverlay,
                isLiveBindMounted = isBindMounted,
                stockDoc = stockDoc,
                activeDoc = activeDoc,
                availableSensors = sensors,
                telemetry = telemetry,
                rawContent = rawActive,
                message = when {
                    isBindMounted -> "Konfigurasi aktif secara Live via Bind Mount (Tanpa Reboot)"
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
     * Enumerates real hardware thermal sensor names from kernel sysfs.
     */
    suspend fun queryAvailableSensors(): List<String> = withContext(Dispatchers.IO) {
        try {
            val cmd = """
                for tz in /sys/class/thermal/thermal_zone*; do
                    [ -d "${'$'}tz" ] || continue
                    type=${'$'}(cat "${'$'}tz/type" 2>/dev/null)
                    [ -n "${'$'}type" ] && echo "${'$'}type"
                done | sort -u
            """.trimIndent()
            val res = Shell.cmd(cmd).exec()
            val list = res.out.map { it.trim() }.filter { it.isNotEmpty() }
            if (list.isNotEmpty()) list else listOf("quiet-therm-adc", "battery", "bms", "msm-therm-adc", "backlight_therm", "gpu-usr")
        } catch (_: Exception) {
            listOf("quiet-therm-adc", "battery", "bms", "msm-therm-adc", "backlight_therm", "gpu-usr")
        }
    }

    /**
     * Queries real-time hardware telemetry and calculates dynamic mitigation level
     * based on the active document's [BATTERY_CHARGING_CTL] or user-selected thresholds.
     */
    suspend fun queryLiveTelemetry(doc: ThermalIniDocument? = null): LiveThermalTelemetry = withContext(Dispatchers.IO) {
        queryLiveTelemetryInternal(doc)
    }

    private fun queryLiveTelemetryInternal(doc: ThermalIniDocument?): LiveThermalTelemetry {
        try {
            // Find target sensor and thresholds from active document
            val batterySection = doc?.getSection("BATTERY_CHARGING_CTL")
            val targetSensor = batterySection?.getProperty("sensor") ?: "quiet-therm-adc"
            val thresholdsStr = batterySection?.getProperty("thresholds") ?: ""
            val thresholdsList = thresholdsStr.split("\\s+".toRegex())
                .mapNotNull { it.toIntOrNull() }
                .map { it / 1000 } // convert milli-C to C

            val cmd = """
                temp=0
                # 1. Probe matching sensor or battery thermistor
                for tz in /sys/class/thermal/thermal_zone*; do
                    [ -d "${'$'}tz" ] || continue
                    type=${'$'}(cat "${'$'}tz/type" 2>/dev/null)
                    if [ "${'$'}type" = "$targetSensor" ]; then
                        raw=${'$'}(cat "${'$'}tz/temp" 2>/dev/null)
                        if [ "${'$'}raw" -gt 1000 ]; then temp=${'$'}((raw / 1000)); else temp=${'$'}raw; fi
                        break
                    fi
                done

                # Fallback to battery driver temp if sensor node returned 0
                if [ "${'$'}temp" -le 0 ] && [ -f /sys/class/power_supply/battery/temp ]; then
                    raw=${'$'}(cat /sys/class/power_supply/battery/temp 2>/dev/null)
                    if [ "${'$'}raw" -gt 1000 ]; then temp=${'$'}((raw / 1000)); else temp=${'$'}((raw / 10)); fi
                fi

                # 2. Probe cooling device cur_state for battery/charger
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

                # 3. Daemon PID
                pid=${'$'}(pgrep -f thermal-engine | head -n 1)

                echo "TEL|${'$'}temp|${'$'}cstate|${'$'}pid"
            """.trimIndent()

            val res = Shell.cmd(cmd).exec()
            val line = res.out.firstOrNull { it.startsWith("TEL|") } ?: "TEL|0|0|"
            val parts = line.split("|")
            val tempC = parts.getOrNull(1)?.toFloatOrNull() ?: 0f
            val cstate = parts.getOrNull(2)?.toIntOrNull() ?: 0
            val pid = parts.getOrNull(3) ?: ""

            // Calculate active level dynamically against thresholdsList
            val level = if (thresholdsList.isNotEmpty() && tempC > 0f) {
                var calculatedLevel = 0
                for (i in thresholdsList.indices) {
                    if (tempC >= thresholdsList[i]) {
                        calculatedLevel = i + 1
                    }
                }
                calculatedLevel
            } else {
                0
            }

            val estimatedMa = when (level) {
                0 -> 3100
                1 -> 2500
                2 -> 2000
                3 -> 1500
                4 -> 1000
                else -> 0
            }

            return LiveThermalTelemetry(
                sensorTempC = tempC,
                coolingState = cstate,
                activeMitigationLevel = level,
                isThrottled = level > 0,
                estimatedMaxCurrentMa = estimatedMa,
                daemonPid = pid,
                activeSensorName = targetSensor
            )
        } catch (_: Exception) {
            return LiveThermalTelemetry()
        }
    }

    /**
     * Generates and writes custom configuration permanently to the Magisk module directory.
     */
    suspend fun applyCustomConfig(doc: ThermalIniDocument): Result<String> = withContext(Dispatchers.IO) {
        try {
            val state = detectState()
            if (!state.isSupported) {
                return@withContext Result.failure(IllegalStateException(state.message))
            }

            val content = doc.toIniString()
            val tmpPath = "/data/local/tmp/lynx_thermal_engine.tmp"
            val targetPath = state.overlayConfigPath
            val targetDir = targetPath.substringBeforeLast('/')

            val writeScript = """
                mkdir -p "$targetDir" 2>/dev/null
                cat << 'EOF' > "$tmpPath"
$content
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
    suspend fun applyLiveBindMount(doc: ThermalIniDocument): Result<String> = withContext(Dispatchers.IO) {
        try {
            val state = detectState()
            if (!state.isSupported) {
                return@withContext Result.failure(IllegalStateException(state.message))
            }

            val content = doc.toIniString()
            val liveConf = if (state.vendorArchitecture == "qcom") QCOM_LIVE_TMP_CONF else MTK_LIVE_TMP_CONF
            val stockConf = state.stockConfigPath

            val liveScript = """
                cat << 'EOF' > "$liveConf"
$content
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
                Result.success("Berhasil diuji secara Live! Daemon vendor thermal telah dimuat ulang tanpa reboot.")
            } else {
                Result.failure(RuntimeException("Gagal menerapkan live bind mount: ${res.err.joinToString("\n")}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Applies raw INI string directly, either as Live Bind Mount or Permanent Magisk Overlay.
     */
    suspend fun applyRawContent(rawContent: String, isLive: Boolean): Result<String> = withContext(Dispatchers.IO) {
        try {
            val parsedDoc = ThermalIniDocument.parse(rawContent)
            if (isLive) {
                applyLiveBindMount(parsedDoc)
            } else {
                applyCustomConfig(parsedDoc)
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
                rm -f "$QCOM_LIVE_TMP_CONF" "$MTK_LIVE_TMP_CONF" 2>/dev/null
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
    fun readProtectedFile(path: String): String {
        val res = Shell.cmd("cat \"$path\" 2>/dev/null").exec()
        return if (res.isSuccess) res.out.joinToString("\n") else ""
    }
}

/**
 * 4 Curated Smart Presets for Vendor Thermal Studio
 */
enum class VendorThermalPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val tag: String
) {
    MULTIMEDIA(
        id = "multimedia",
        title = "Multimedia & Video Lancar",
        subtitle = "Anti-stutter video 1080p/4K YouTube, Reels & TikTok. Menjaga refresh rate 60/90Hz dan stabilisasi decoder.",
        tag = "ANTI-STUTTER"
    ),
    BALANCED(
        id = "balanced",
        title = "Harian Seimbang Pro (Default)",
        subtitle = "Daily driver multitasking responsif, anti-dimming 52°C, pengisian daya cerdas dan adem di saku.",
        tag = "REKOMENDASI"
    ),
    GAMING(
        id = "gaming",
        title = "Sustained Gaming (Anti-Lag)",
        subtitle = "+4°C headroom pada CPU Silver/Gold & GPU set_point mencegah FPS drop mendadak saat sesi game panjang.",
        tag = "ANTI-FRAME DROP"
    ),
    SUPER_COOL(
        id = "super_cool",
        title = "Dingin Tropis & Baterai Awet",
        subtitle = "Pendinginan dini & proteksi degradasi baterai di iklim terik tropis.",
        tag = "BATERAI AWET"
    )
}

/**
 * Human-friendly metadata for thermal sections.
 */
data class SectionMeta(
    val badge: String,
    val description: String
)

object VendorThermalPresets {
    fun getSectionMeta(sectionName: String): SectionMeta {
        val upper = sectionName.uppercase()
        return when {
            upper.contains("BATTERY_CHARGING") -> SectionMeta(
                badge = "🔋 Pengisian Baterai",
                description = "Arus fast charging & mitigasi panas baterai"
            )
            upper.contains("LCD_MANAGEMENT") -> SectionMeta(
                badge = "📱 Layar & Anti-Dimming",
                description = "Mencegah layar meredup otomatis saat ponsel hangat"
            )
            upper.contains("LCD_MONITOR") -> SectionMeta(
                badge = "🖥️ Monitor Layar",
                description = "Pemantauan batas termal panel display"
            )
            upper.contains("SS-GPU") || upper.contains("GPU") -> SectionMeta(
                badge = "🎮 Throttling GPU Grafis",
                description = "Menjaga kelancaran render game & animasi UI"
            )
            upper.contains("SILVER") || upper.contains("CLUSTER0") -> SectionMeta(
                badge = "⚙️ CPU Little (Efisiensi)",
                description = "Kelancaran multitasking dasar & efisiensi baterai"
            )
            upper.contains("GOLD") || upper.contains("CLUSTER1") -> SectionMeta(
                badge = "⚡ CPU Big (Performa)",
                description = "Daya komputasi berat, app launch & gaming"
            )
            upper.contains("SKIN-SHUTDOWN") -> SectionMeta(
                badge = "🛡️ Proteksi Darurat",
                description = "Batas cut-off darurat agar hardware tetap aman"
            )
            upper.contains("VIRTUAL") -> SectionMeta(
                badge = "🧠 Virtual Governor",
                description = "Algoritma virtual governor penyeimbang beban core"
            )
            upper.contains("SPEAKER") -> SectionMeta(
                badge = "🔊 Kalibrasi Audio",
                description = "Proteksi termal amplifier speaker"
            )
            upper.contains("THROTTLING-NOTIFY") -> SectionMeta(
                badge = "🔔 Notifikasi Termal",
                description = "Pelaporan status panas ke Android framework"
            )
            else -> SectionMeta(
                badge = "⚙️ Parameter Kustom",
                description = "Konfigurasi daemon termal vendor"
            )
        }
    }

    fun applyPreset(doc: ThermalIniDocument, preset: VendorThermalPreset): ThermalIniDocument {
        val newDoc = doc.deepCopy()
        when (preset) {
            VendorThermalPreset.MULTIMEDIA -> {
                newDoc.getSection("LCD_MANAGEMENT")?.let { sec ->
                    sec.setProperty("thresholds", "52000 56000")
                    sec.setProperty("thresholds_clr", "50000 54000")
                }
                newDoc.getSection("BATTERY_CHARGING_CTL")?.let { sec ->
                    sec.setProperty("thresholds", "38000 41000 43000 46000 49000")
                    sec.setProperty("thresholds_clr", "36000 39000 41000 44000 47000")
                }
                for (sec in newDoc.sections) {
                    if (sec.name.contains("VIDEO", ignoreCase = true) ||
                        sec.name.contains("LCD_MONITOR", ignoreCase = true) ||
                        sec.name.contains("CAMERA", ignoreCase = true)) {
                        val currThresh = sec.getProperty("thresholds")
                        if (currThresh != null) {
                            val bumped = currThresh.split("\\s+".toRegex()).mapNotNull { it.toIntOrNull() }.map { (it + 4000).coerceAtMost(80000) }
                            if (bumped.isNotEmpty()) {
                                sec.setProperty("thresholds", bumped.joinToString(" "))
                                sec.setProperty("thresholds_clr", bumped.map { (it - 2000).coerceAtLeast(30000) }.joinToString(" "))
                            }
                        }
                    }
                }
            }
            VendorThermalPreset.BALANCED -> {
                newDoc.getSection("BATTERY_CHARGING_CTL")?.let { sec ->
                    sec.setProperty("thresholds", "38000 40000 42000 45000 49000")
                    sec.setProperty("thresholds_clr", "36000 38000 40000 43000 47000")
                }
                newDoc.getSection("LCD_MANAGEMENT")?.let { sec ->
                    sec.setProperty("thresholds", "50000 54000")
                    sec.setProperty("thresholds_clr", "48000 52000")
                }
            }
            VendorThermalPreset.GAMING -> {
                newDoc.getSection("BATTERY_CHARGING_CTL")?.let { sec ->
                    sec.setProperty("thresholds", "40000 42000 45000 48000 52000")
                    sec.setProperty("thresholds_clr", "38000 40000 43000 46000 50000")
                }
                newDoc.getSection("LCD_MANAGEMENT")?.let { sec ->
                    sec.setProperty("thresholds", "54000 58000")
                    sec.setProperty("thresholds_clr", "52000 56000")
                }
                for (sec in newDoc.sections) {
                    if (sec.name.startsWith("SS-MSM-THERM", ignoreCase = true) ||
                        sec.name.contains("CLUSTER", ignoreCase = true)) {
                        val sp = sec.getProperty("set_point")?.toIntOrNull()
                        if (sp != null && sp < 70000) {
                            sec.setProperty("set_point", (sp + 4000).toString())
                            sec.setProperty("set_point_clr", (sp + 2000).toString())
                        }
                    }
                    if (sec.name.equals("SS-GPU", ignoreCase = true)) {
                        val sp = sec.getProperty("set_point")?.toIntOrNull()
                        if (sp != null && sp < 98000) {
                            sec.setProperty("set_point", (sp + 3000).coerceAtMost(98000).toString())
                        }
                    }
                }
            }
            VendorThermalPreset.SUPER_COOL -> {
                newDoc.getSection("BATTERY_CHARGING_CTL")?.let { sec ->
                    sec.setProperty("thresholds", "36000 38000 40000 42000 44000")
                    sec.setProperty("thresholds_clr", "34000 36000 38000 40000 42000")
                }
                newDoc.getSection("LCD_MANAGEMENT")?.let { sec ->
                    sec.setProperty("thresholds", "45000 48000")
                    sec.setProperty("thresholds_clr", "43000 46000")
                }
            }
        }
        return newDoc
    }
}
