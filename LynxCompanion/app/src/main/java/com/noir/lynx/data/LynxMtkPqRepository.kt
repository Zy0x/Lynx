package com.noir.lynx.data

import android.content.Context
import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hardware probe and transaction writer for MediaTek Picture Quality (MTK PQ)
 * properties and PQ HAL service reload synchronization.
 */
object LynxMtkPqRepository {
    private const val TAG = "LynxMtkPq"
    private const val PREFS_NAME = "lynx_mtk_pq_prefs"

    @Volatile
    private var cachedSupport: MtkPqHardwareSupport? = null

    fun cachedHardwareSupport(): MtkPqHardwareSupport? = cachedSupport

    fun loadSavedConfig(context: Context): MtkPqConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val def = MtkPqConfig.STOCK_BALANCED
        return MtkPqConfig(
            applyOnBoot = prefs.getBoolean("apply_on_boot", def.applyOnBoot),
            aalBypass = prefs.getBoolean("aal_bypass", def.aalBypass),
            dreMode = prefs.getInt("dre_mode", def.dreMode),
            vpDreEnabled = prefs.getBoolean("vp_dre_enabled", def.vpDreEnabled),
            shpIndex = prefs.getInt("shp_index", def.shpIndex),
            isoShpEnabled = prefs.getBoolean("iso_shp_enabled", def.isoShpEnabled),
            ultraResEnabled = prefs.getBoolean("ultrares_enabled", def.ultraResEnabled),
            ultraResStrength = prefs.getInt("ultrares_strength", def.ultraResStrength),
            debugShpEnabled = prefs.getBoolean("debug_shp_enabled", def.debugShpEnabled),
            debugDshpEnabled = prefs.getBoolean("debug_dshp_enabled", def.debugDshpEnabled),
        ).sanitized()
    }

    fun saveConfigPrefs(context: Context, config: MtkPqConfig) {
        val clean = config.sanitized()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("apply_on_boot", clean.applyOnBoot)
            .putBoolean("aal_bypass", clean.aalBypass)
            .putInt("dre_mode", clean.dreMode)
            .putBoolean("vp_dre_enabled", clean.vpDreEnabled)
            .putInt("shp_index", clean.shpIndex)
            .putBoolean("iso_shp_enabled", clean.isoShpEnabled)
            .putBoolean("ultrares_enabled", clean.ultraResEnabled)
            .putInt("ultrares_strength", clean.ultraResStrength)
            .putBoolean("debug_shp_enabled", clean.debugShpEnabled)
            .putBoolean("debug_dshp_enabled", clean.debugDshpEnabled)
            .apply()
    }

    suspend fun probeHardwareSupport(): MtkPqHardwareSupport = withContext(Dispatchers.IO) {
        val script = """
            pq_sup=${'$'}(getprop ro.vendor.mtk_pq_support 2>/dev/null | tr -dc '0-9')
            aal_sup=${'$'}(getprop ro.vendor.mtk_aal_support 2>/dev/null | tr -dc '0-9')
            col_mode=${'$'}(getprop ro.vendor.mtk_pq_color_mode 2>/dev/null | tr -dc '0-9')
            svc_name=""
            for s in pq-2-2 vendor.pq-2-2 pq-2-1 vendor.pq-2-1 pq-2-0; do
                st=${'$'}(getprop "init.svc.${'$'}s" 2>/dev/null | tr -d '[:space:]')
                if [ -n "${'$'}st" ]; then
                    svc_name="${'$'}s"
                    break
                fi
            done
            echo "PQ_SUP=${'$'}pq_sup"
            echo "AAL_SUP=${'$'}aal_sup"
            echo "COL_MODE=${'$'}col_mode"
            echo "SVC=${'$'}svc_name"
            echo "AAL_BYP=${'$'}(getprop persist.vendor.sys.pq.disp.aal.bypass 2>/dev/null | tr -dc '0-9')"
            echo "DRE_EN=${'$'}(getprop persist.vendor.sys.pq.mdp.dre.en 2>/dev/null | tr -dc '0-9')"
            echo "VP_DRE=${'$'}(getprop persist.vendor.sys.pq.mdp.vp.dre.en 2>/dev/null | tr -dc '0-9')"
            echo "SHP_IDX=${'$'}(getprop persist.vendor.sys.pq.shp.idx 2>/dev/null | tr -dc '0-9')"
            echo "ISO_SHP=${'$'}(getprop persist.vendor.sys.pq.iso.shp.en 2>/dev/null | tr -dc '0-9')"
            echo "UR_EN=${'$'}(getprop persist.vendor.sys.pq.ultrares.en 2>/dev/null | tr -dc '0-9')"
            echo "UR_STR=${'$'}(getprop persist.vendor.sys.pq.ultrares.strength 2>/dev/null | tr -dc '0-9')"
            echo "DBG_SHP=${'$'}(getprop vendor.debug.pq.shp.en 2>/dev/null | tr -dc '0-9')"
            echo "DBG_DSHP=${'$'}(getprop vendor.debug.pq.dshp.en 2>/dev/null | tr -dc '0-9')"
        """.trimIndent()

        val lines = runCatching { Shell.cmd(script).exec().out }.getOrDefault(emptyList())
        parseProbeLines(lines).also { cachedSupport = it }
    }

    suspend fun applyConfig(
        context: Context,
        config: MtkPqConfig,
        persistAsDefault: Boolean,
        restartPqService: Boolean = true,
    ): MtkPqHardwareSupport = withContext(Dispatchers.IO) {
        val clean = config.sanitized()
        if (persistAsDefault) {
            saveConfigPrefs(context, clean)
            syncConfigJson(clean)
        }

        val currentSupport = probeHardwareSupport()
        if (!currentSupport.supported) {
            return@withContext currentSupport.copy(lastReloadOk = false).also { cachedSupport = it }
        }

        val cmd = buildApplyScript(clean, currentSupport.pqServiceName, restartPqService)
        val execRes = runCatching { Shell.cmd(cmd).exec() }
            .onFailure { Log.e(TAG, "applyConfig failed: ${it.message}") }
            .getOrNull()

        val reloadOk = execRes?.out?.any { it.contains("RELOAD_OK") || it.contains("PROPS_OK") } == true
        val refreshed = probeHardwareSupport().copy(lastReloadOk = reloadOk)
        cachedSupport = refreshed
        refreshed
    }

    suspend fun resetToDefault(context: Context): MtkPqHardwareSupport {
        val keepBoot = loadSavedConfig(context).applyOnBoot
        val def = MtkPqConfig.STOCK_BALANCED.copy(applyOnBoot = keepBoot)
        return applyConfig(context, def, persistAsDefault = true, restartPqService = true)
    }

    suspend fun applySavedDefaultsOnBoot(context: Context) = withContext(Dispatchers.IO) {
        val cfg = loadSavedConfig(context)
        if (!cfg.applyOnBoot) return@withContext
        val support = probeHardwareSupport()
        if (!support.supported) return@withContext

        val activeConfig = MtkPqConfig(
            applyOnBoot = cfg.applyOnBoot,
            aalBypass = support.activeAalBypass,
            dreMode = support.activeDreMode,
            vpDreEnabled = support.activeVpDreEnabled,
            shpIndex = support.activeShpIndex,
            isoShpEnabled = support.activeIsoShpEnabled,
            ultraResEnabled = support.activeUltraResEnabled,
            ultraResStrength = support.activeUltraResStrength,
            debugShpEnabled = support.activeDebugShpEnabled,
            debugDshpEnabled = support.activeDebugDshpEnabled,
        )
        // Skip boot service bounce when already matching active persisted properties
        if (activeConfig.matchesOpticalPreset(cfg)) return@withContext

        applyConfig(context, cfg, persistAsDefault = false, restartPqService = true)
    }

    internal fun buildApplyScript(
        config: MtkPqConfig,
        detectedService: String,
        restartPqService: Boolean,
    ): String {
        val clean = config.sanitized()
        val aalVal = if (clean.aalBypass) 1 else 0
        val vpDreVal = if (clean.vpDreEnabled) 1 else 0
        val isoShpVal = if (clean.isoShpEnabled) 1 else 0
        val urEnVal = if (clean.ultraResEnabled) 1 else 0
        val dbgShpVal = if (clean.debugShpEnabled) 1 else 0
        val dbgDshpVal = if (clean.debugDshpEnabled) 1 else 0

        val svcReloadBlock = if (restartPqService) {
            """
            SVC="$detectedService"
            if [ -z "${'$'}SVC" ]; then
                for s in pq-2-2 vendor.pq-2-2 pq-2-1 vendor.pq-2-1 pq-2-0; do
                    if [ -n "${'$'}(getprop "init.svc.${'$'}s" 2>/dev/null)" ]; then
                        SVC="${'$'}s"
                        break
                    fi
                done
            fi
            if [ -n "${'$'}SVC" ]; then
                stop "${'$'}SVC" 2>/dev/null
                sleep 0.2
                start "${'$'}SVC" 2>/dev/null
                for _i in 1 2 3 4 5; do
                    if [ "${'$'}(getprop "init.svc.${'$'}SVC" 2>/dev/null)" = "running" ]; then
                        echo "RELOAD_OK"
                        break
                    fi
                    sleep 0.15
                done
            else
                echo "PROPS_OK"
            fi
            """.trimIndent()
        } else {
            """echo "PROPS_OK""""
        }

        return """
            RP=""
            for b in /data/adb/ksu/bin/resetprop /data/adb/magisk/resetprop /data/adb/ap/bin/resetprop; do
                [ -x "${'$'}b" ] && RP="${'$'}b" && break
            done
            set_pq_prop() {
                setprop "${'$'}1" "${'$'}2" 2>/dev/null && return 0
                if [ -n "${'$'}RP" ]; then
                    "${'$'}RP" "${'$'}1" "${'$'}2" 2>/dev/null
                fi
            }
            set_pq_prop persist.vendor.sys.pq.disp.aal.bypass $aalVal
            set_pq_prop persist.vendor.sys.pq.mdp.dre.en ${clean.dreMode}
            set_pq_prop persist.vendor.sys.pq.mdp.vp.dre.en $vpDreVal
            set_pq_prop persist.vendor.sys.pq.shp.idx ${clean.shpIndex}
            set_pq_prop persist.vendor.sys.pq.iso.shp.en $isoShpVal
            set_pq_prop persist.vendor.sys.pq.ultrares.en $urEnVal
            set_pq_prop persist.vendor.sys.pq.ultrares.strength ${clean.ultraResStrength}
            set_pq_prop vendor.debug.pq.shp.en $dbgShpVal
            set_pq_prop vendor.debug.pq.dshp.en $dbgDshpVal
            $svcReloadBlock
        """.trimIndent()
    }

    internal fun parseProbeLines(lines: List<String>): MtkPqHardwareSupport {
        val kv = lines.mapNotNull { line ->
            val idx = line.indexOf('=')
            if (idx > 0) line.substring(0, idx) to line.substring(idx + 1).trim() else null
        }.toMap()

        val pqLevel = kv["PQ_SUP"]?.toIntOrNull() ?: 0
        val aalSupport = kv["AAL_SUP"] == "1"
        val svcName = kv["SVC"].orEmpty()
        val isSupported = pqLevel > 0 || aalSupport || svcName.isNotEmpty()

        return MtkPqHardwareSupport(
            probed = true,
            supported = isSupported,
            mtkPqSupportLevel = pqLevel,
            mtkAalSupport = aalSupport,
            pqColorMode = kv["COL_MODE"]?.toIntOrNull() ?: 0,
            pqServiceName = svcName,
            activeAalBypass = kv["AAL_BYP"] == "1",
            activeDreMode = (kv["DRE_EN"]?.toIntOrNull() ?: 2).coerceIn(0, 2),
            activeVpDreEnabled = (kv["VP_DRE"] ?: "1") != "0",
            activeShpIndex = (kv["SHP_IDX"]?.toIntOrNull() ?: 2).coerceIn(0, 9),
            activeIsoShpEnabled = (kv["ISO_SHP"] ?: "1") != "0",
            activeUltraResEnabled = (kv["UR_EN"] ?: "1") != "0",
            activeUltraResStrength = (kv["UR_STR"]?.toIntOrNull() ?: 0).coerceIn(0, 4),
            activeDebugShpEnabled = (kv["DBG_SHP"] ?: "1") != "0",
            activeDebugDshpEnabled = (kv["DBG_DSHP"] ?: "1") != "0",
        )
    }

    private suspend fun syncConfigJson(cfg: MtkPqConfig) {
        val clean = cfg.sanitized()
        val pairs = listOf(
            Triple("mtk_pq.aal_bypass", clean.aalBypass.toString(), "bool"),
            Triple("mtk_pq.dre_mode", clean.dreMode.toString(), "int"),
            Triple("mtk_pq.vp_dre_enabled", clean.vpDreEnabled.toString(), "bool"),
            Triple("mtk_pq.shp_index", clean.shpIndex.toString(), "int"),
            Triple("mtk_pq.iso_shp_enabled", clean.isoShpEnabled.toString(), "bool"),
            Triple("mtk_pq.ultrares_enabled", clean.ultraResEnabled.toString(), "bool"),
            Triple("mtk_pq.ultrares_strength", clean.ultraResStrength.toString(), "int"),
            Triple("mtk_pq.debug_shp_enabled", clean.debugShpEnabled.toString(), "bool"),
            Triple("mtk_pq.debug_dshp_enabled", clean.debugDshpEnabled.toString(), "bool"),
        )
        for ((k, v, t) in pairs) {
            LynxAppStateRepository.writeStateKey(k, v, t)
        }
    }
}
