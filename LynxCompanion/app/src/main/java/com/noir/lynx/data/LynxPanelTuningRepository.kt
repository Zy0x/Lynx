package com.noir.lynx.data

import android.content.Context
import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hardware probe and sysfs/procfs writer for NT36672C panel CABC (DCS 0x55)
 * and MediaTek Display ESD Watchdog polling interval (/proc/mtk_esd_period_ms).
 */
object LynxPanelTuningRepository {
    private const val TAG = "LynxPanelTuning"
    private const val PREFS_NAME = "lynx_panel_tuning_prefs"

    const val CABC_NODE = "/sys/kernel/oplus_display/LCM_CABC"
    const val ESD_PERIOD_NODE = "/proc/mtk_esd_period_ms"

    @Volatile
    private var cachedSupport: PanelTuningHardwareSupport? = null

    fun cachedHardwareSupport(): PanelTuningHardwareSupport? = cachedSupport

    fun loadSavedConfig(context: Context): PanelTuningConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val def = PanelTuningConfig.STOCK_BASELINE
        return PanelTuningConfig(
            cabcMode = prefs.getInt("cabc_mode", def.cabcMode),
            esdPeriodMs = prefs.getInt("esd_period_ms", def.esdPeriodMs),
            applyOnBoot = prefs.getBoolean("apply_on_boot", def.applyOnBoot),
        ).sanitized()
    }

    fun saveConfigPrefs(context: Context, config: PanelTuningConfig) {
        val clean = config.sanitized()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putInt("cabc_mode", clean.cabcMode)
            putInt("esd_period_ms", clean.esdPeriodMs)
            putBoolean("apply_on_boot", clean.applyOnBoot)
            apply()
        }
    }

    suspend fun probeHardwareSupport(): PanelTuningHardwareSupport = withContext(Dispatchers.IO) {
        val out = Shell.cmd(buildProbeScript()).exec().out
        parseProbeLines(out).also { cachedSupport = it }
    }

    suspend fun applyConfig(
        context: Context,
        config: PanelTuningConfig,
        persistAsDefault: Boolean = true,
    ): PanelTuningHardwareSupport = withContext(Dispatchers.IO) {
        val clean = config.sanitized()
        if (persistAsDefault) {
            saveConfigPrefs(context, clean)
        }
        val out = Shell.cmd(buildApplyScript(clean)).exec().out
        syncConfigJson(clean)
        parseProbeLines(out).also { cachedSupport = it }
    }

    suspend fun resetToDefault(context: Context): PanelTuningHardwareSupport = withContext(Dispatchers.IO) {
        val currentBootFlag = loadSavedConfig(context).applyOnBoot
        val reset = PanelTuningConfig.STOCK_BASELINE.copy(applyOnBoot = currentBootFlag)
        applyConfig(context, reset, persistAsDefault = true)
    }

    suspend fun applySavedDefaultsOnBoot(context: Context) = withContext(Dispatchers.IO) {
        try {
            val saved = loadSavedConfig(context)
            if (!saved.applyOnBoot) {
                Log.i(TAG, "Panel tuning apply-on-boot disabled by user")
                return@withContext
            }
            val current = probeHardwareSupport()
            if (!current.hasAnySupport) {
                return@withContext
            }
            val applied = applyConfig(context, saved, persistAsDefault = false)
            Log.i(
                TAG,
                "Applied saved panel tuning on boot (cabc=${applied.activeCabcMode}, esdMs=${applied.activeEsdPeriodMs})",
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed applying panel tuning on boot: ${e.message}")
        }
    }

    internal fun buildProbeScript(): String {
        return """
            if [ -f "$CABC_NODE" ] && cat "$CABC_NODE" >/dev/null 2>&1; then
                cabc_val=$(cat "$CABC_NODE" 2>/dev/null | tr -d '\r\n ')
                echo "CABC_SUP=1"
                echo "CABC_VAL=${'$'}cabc_val"
            else
                echo "CABC_SUP=0"
                echo "CABC_VAL=-1"
            fi
            if [ -f "$ESD_PERIOD_NODE" ] && cat "$ESD_PERIOD_NODE" >/dev/null 2>&1; then
                esd_val=$(cat "$ESD_PERIOD_NODE" 2>/dev/null | tr -d '\r\n ')
                echo "ESD_SUP=1"
                echo "ESD_VAL=${'$'}esd_val"
            else
                echo "ESD_SUP=0"
                echo "ESD_VAL=-1"
            fi
            echo "WRITE_OK=1"
        """.trimIndent()
    }

    internal fun buildApplyScript(config: PanelTuningConfig): String {
        val clean = config.sanitized()
        return """
            write_ok=1
            if [ -f "$CABC_NODE" ] && cat "$CABC_NODE" >/dev/null 2>&1; then
                if ! echo "${clean.cabcMode}" > "$CABC_NODE" 2>/dev/null; then
                    write_ok=0
                fi
                cabc_val=$(cat "$CABC_NODE" 2>/dev/null | tr -d '\r\n ')
                echo "CABC_SUP=1"
                echo "CABC_VAL=${'$'}cabc_val"
            else
                echo "CABC_SUP=0"
                echo "CABC_VAL=-1"
            fi
            if [ -f "$ESD_PERIOD_NODE" ] && cat "$ESD_PERIOD_NODE" >/dev/null 2>&1; then
                if ! echo "${clean.esdPeriodMs}" > "$ESD_PERIOD_NODE" 2>/dev/null; then
                    write_ok=0
                fi
                esd_val=$(cat "$ESD_PERIOD_NODE" 2>/dev/null | tr -d '\r\n ')
                echo "ESD_SUP=1"
                echo "ESD_VAL=${'$'}esd_val"
            else
                echo "ESD_SUP=0"
                echo "ESD_VAL=-1"
            fi
            echo "WRITE_OK=${'$'}write_ok"
        """.trimIndent()
    }

    internal fun parseProbeLines(lines: List<String>): PanelTuningHardwareSupport {
        val kv = mutableMapOf<String, String>()
        for (line in lines) {
            val idx = line.indexOf('=')
            if (idx > 0) {
                kv[line.substring(0, idx).trim()] = line.substring(idx + 1).trim()
            }
        }
        val cabcSup = kv["CABC_SUP"] == "1"
        val cabcVal = (kv["CABC_VAL"]?.toIntOrNull() ?: PanelTuningConfig.DEFAULT_CABC_MODE)
            .coerceIn(PanelTuningConfig.MIN_CABC_MODE, PanelTuningConfig.MAX_CABC_MODE)
        val esdSup = kv["ESD_SUP"] == "1"
        val esdRaw = kv["ESD_VAL"]?.toIntOrNull() ?: PanelTuningConfig.DEFAULT_ESD_PERIOD_MS
        val esdVal = PanelTuningConfig.clampEsdPeriodMs(esdRaw)
        val writeOk = kv["WRITE_OK"] != "0"

        return PanelTuningHardwareSupport(
            probed = true,
            cabcSupported = cabcSup,
            esdSupported = esdSup,
            activeCabcMode = if (cabcSup) cabcVal else PanelTuningConfig.DEFAULT_CABC_MODE,
            activeEsdPeriodMs = if (esdSup) esdVal else PanelTuningConfig.DEFAULT_ESD_PERIOD_MS,
            lastWriteOk = writeOk,
        )
    }

    internal fun syncConfigJson(clean: PanelTuningConfig) {
        val lynxdBin = "/data/adb/modules/Lynx/system/bin/lynxd"
        Shell.cmd(
            "if [ -x \"$lynxdBin\" ]; then " +
                "\"$lynxdBin\" state set " +
                "panel.cabc_mode=${clean.cabcMode} " +
                "panel.esd_period_ms=${clean.esdPeriodMs} " +
                "panel.apply_on_boot=${clean.applyOnBoot} " +
                "2>/dev/null; " +
                "elif command -v lynxd >/dev/null 2>&1; then " +
                "lynxd state set " +
                "panel.cabc_mode=${clean.cabcMode} " +
                "panel.esd_period_ms=${clean.esdPeriodMs} " +
                "panel.apply_on_boot=${clean.applyOnBoot} " +
                "2>/dev/null; " +
                "fi",
        ).exec()
    }
}
