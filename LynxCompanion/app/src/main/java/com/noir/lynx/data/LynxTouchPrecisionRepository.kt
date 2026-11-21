package com.noir.lynx.data

import android.content.Context
import android.util.Log
import com.noir.lynx.hardware.GraphicsPipelineProbe
import com.noir.lynx.hardware.TouchIrqShellBuilder
import com.topjohnwu.superuser.Shell
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hardware probe and transaction writer for touch micro-sensitivity, Novatek SPI bus clock,
 * kernel big-core touch IRQ pinning, MTK KTCH touch boost, Android input resampling,
 * and opt-in Edge Back Gesture calibration.
 */
object LynxTouchPrecisionRepository {
    private const val TAG = "LynxTouchPrecision"
    private const val PREFS_NAME = "lynx_touch_precision_prefs"

    @Volatile
    private var cachedSupport: TouchPrecisionHardwareSupport? = null

    fun cachedHardwareSupport(): TouchPrecisionHardwareSupport? = cachedSupport

    fun loadSavedConfig(context: Context): TouchPrecisionConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return TouchPrecisionConfig(
            applyOnBoot = prefs.getBoolean("apply_on_boot", true),
            nvtMicroGain = prefs.getInt("nvt_micro_gain", 1).coerceIn(0, 12),
            nvtEdgeGuardPx = prefs.getInt("nvt_edge_guard_px", 48).coerceIn(0, 240),
            nvtSpiSpeedHz = prefs.getInt("nvt_spi_speed_hz", 15_000_000).coerceIn(4_800_000, 24_000_000),
            nvtGameMode = prefs.getBoolean("nvt_game_mode", true),
            nvtHopFix = prefs.getBoolean("nvt_hop_fix", true),
            nvtJitterLevel = prefs.getInt("nvt_jitter_level", 0).coerceIn(0, 3),
            nvtSmoothLevel = prefs.getInt("nvt_smooth_level", 0).coerceIn(0, 3),
            ktchEnabled = prefs.getBoolean("ktch_enabled", true),
            ktchDurationMs = prefs.getInt("ktch_duration_ms", 280).coerceIn(0, 2000),
            ktchEasBoost = prefs.getInt("ktch_eas_boost", 80).coerceIn(0, 100),
            edgeGestureCustomEnabled = prefs.getBoolean("edge_gesture_custom_enabled", false),
            backGestureOverlay = prefs.getString("back_gesture_overlay", TouchPrecisionConfig.OVERLAY_NARROW)
                ?: TouchPrecisionConfig.OVERLAY_NARROW,
            backGestureScaleLeft = prefs.getFloat("back_gesture_scale_left", 0.18f).coerceIn(0.0f, 2.0f),
            backGestureScaleRight = prefs.getFloat("back_gesture_scale_right", 0.18f).coerceIn(0.0f, 2.0f),
            backGestureLinkSides = prefs.getBoolean("back_gesture_link_sides", true),
            backGestureMlEnabled = prefs.getBoolean("back_gesture_ml_enabled", true),
            backGestureMlThreshold = prefs.getFloat("back_gesture_ml_threshold", 0.96f).coerceIn(0.50f, 0.99f),
            inputResampling = prefs.getBoolean("input_resampling", false),
            touchIrqBigCores = prefs.getBoolean("touch_irq_big_cores", true),
        )
    }

    fun saveConfigPrefs(context: Context, config: TouchPrecisionConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("apply_on_boot", config.applyOnBoot)
            .putInt("nvt_micro_gain", config.nvtMicroGain.coerceIn(0, 12))
            .putInt("nvt_edge_guard_px", config.nvtEdgeGuardPx.coerceIn(0, 240))
            .putInt("nvt_spi_speed_hz", config.nvtSpiSpeedHz.coerceIn(4_800_000, 24_000_000))
            .putBoolean("nvt_game_mode", config.nvtGameMode)
            .putBoolean("nvt_hop_fix", config.nvtHopFix)
            .putInt("nvt_jitter_level", config.nvtJitterLevel.coerceIn(0, 3))
            .putInt("nvt_smooth_level", config.nvtSmoothLevel.coerceIn(0, 3))
            .putBoolean("ktch_enabled", config.ktchEnabled)
            .putInt("ktch_duration_ms", config.ktchDurationMs.coerceIn(0, 2000))
            .putInt("ktch_eas_boost", config.ktchEasBoost.coerceIn(0, 100))
            .putBoolean("edge_gesture_custom_enabled", config.edgeGestureCustomEnabled)
            .putString("back_gesture_overlay", config.backGestureOverlay)
            .putFloat("back_gesture_scale_left", config.backGestureScaleLeft.coerceIn(0.0f, 2.0f))
            .putFloat("back_gesture_scale_right", config.backGestureScaleRight.coerceIn(0.0f, 2.0f))
            .putBoolean("back_gesture_link_sides", config.backGestureLinkSides)
            .putBoolean("back_gesture_ml_enabled", config.backGestureMlEnabled)
            .putFloat("back_gesture_ml_threshold", config.backGestureMlThreshold.coerceIn(0.50f, 0.99f))
            .putBoolean("input_resampling", config.inputResampling)
            .putBoolean("touch_irq_big_cores", config.touchIrqBigCores)
            .apply()
    }

    suspend fun probeHardwareSupport(): TouchPrecisionHardwareSupport = withContext(Dispatchers.IO) {
        val script = """
            nvt_present=0; [ -e /proc/nvt_micro_gain ] && nvt_present=1
            ktch_present=0; [ -e /proc/perfmgr/tchbst/kernel/tb_enable ] && ktch_present=1
            gain=${'$'}(cat /proc/nvt_micro_gain 2>/dev/null | tr -dc '0-9')
            eg=${'$'}(cat /proc/nvt_edge_guard 2>/dev/null | tr -dc '0-9')
            spi=${'$'}(cat /proc/nvt_spi_speed 2>/dev/null | tr '\n' ' ')
            stats=${'$'}(head -n 3 /proc/nvt_touch_stats 2>/dev/null | tr '\n' ' ')
            fw=${'$'}(cat /proc/nvt_fw_version 2>/dev/null | tr -d '\n')
            ktch_dur=${'$'}(cat /proc/perfmgr/tchbst/kernel/tb_duration_ms 2>/dev/null | tr -dc '0-9')
            ktch_eas=${'$'}(cat /proc/perfmgr/tchbst/kernel/tb_eas_boost 2>/dev/null | tr -dc '0-9')
            resamp=${'$'}(getprop ro.input.resampling 2>/dev/null | tr -d '[:space:]')
            edge_dump=${'$'}(dumpsys activity service com.android.systemui/.SystemUIService 2>/dev/null | grep -E "mEdgeWidthLeft=|mEdgeWidthRight=|mMLModelThreshold=" | head -n 6 | tr '\n' ';')
            ${TouchIrqShellBuilder.buildProbeSnippet()}
            echo "NVT=${'$'}nvt_present"
            echo "NVT_IRQ_NODE=${'$'}nvt_irq_node"
            echo "KTCH=${'$'}ktch_present"
            echo "GAIN=${'$'}gain"
            echo "EG=${'$'}eg"
            echo "SPI=${'$'}spi"
            echo "STATS=${'$'}stats"
            echo "FW=${'$'}fw"
            echo "KDUR=${'$'}ktch_dur"
            echo "KEAS=${'$'}ktch_eas"
            echo "RESAMP=${'$'}resamp"
            echo "RESAMP_SUP=${'$'}resamp_sup"
            echo "IRQ_SUP=${'$'}irq_sup"
            echo "IRQPIN=${'$'}irq_pin"
            echo "EDGE=${'$'}edge_dump"
        """.trimIndent()
        val lines = runCatching { Shell.cmd(script).exec().out }.getOrDefault(emptyList())
        return@withContext parseProbeLines(lines).also { cachedSupport = it }
    }

    /**
     * Quick-writes Novatek Micro-Gain / Edge Guard directly from HUD Touch Lab without
     * mutating unrelated SystemUI overlay or SPI clock preferences.
     */
    suspend fun quickSetNovatekMicroSense(
        context: Context,
        microGain: Int,
        edgeGuardPx: Int? = null,
    ): TouchPrecisionHardwareSupport = withContext(Dispatchers.IO) {
        val current = loadSavedConfig(context)
        val clampedGain = microGain.coerceIn(0, 12)
        val clampedEg = (edgeGuardPx ?: current.nvtEdgeGuardPx).coerceIn(0, 240)
        val updated = current.copy(nvtMicroGain = clampedGain, nvtEdgeGuardPx = clampedEg)
        saveConfigPrefs(context, updated)
        if (updated.applyOnBoot) {
            LynxAppStateRepository.writeStateKey("display_touch.nvt_micro_gain", clampedGain.toString(), "int")
            if (edgeGuardPx != null) {
                LynxAppStateRepository.writeStateKey("display_touch.nvt_edge_guard_px", clampedEg.toString(), "int")
            }
        }
        val script = buildString {
            appendLine("if [ -e /proc/nvt_micro_gain ]; then")
            appendLine("  echo $clampedGain > /proc/nvt_micro_gain 2>/dev/null")
            if (edgeGuardPx != null) {
                appendLine("  echo $clampedEg > /proc/nvt_edge_guard 2>/dev/null")
            }
            appendLine("fi")
        }
        runCatching { Shell.cmd(script).exec() }
        probeHardwareSupport()
    }

    suspend fun applyConfig(
        context: Context,
        config: TouchPrecisionConfig,
        persistAsDefault: Boolean,
        applyEdgeGesture: Boolean = config.edgeGestureCustomEnabled,
    ): TouchPrecisionHardwareSupport = withContext(Dispatchers.IO) {
        if (persistAsDefault) {
            saveConfigPrefs(context, config)
            syncConfigJson(config)
        }

        val gain = config.nvtMicroGain.coerceIn(0, 12)
        val eg = config.nvtEdgeGuardPx.coerceIn(0, 240)
        val spi = config.nvtSpiSpeedHz.coerceIn(4_800_000, 24_000_000)
        val gameVal = if (config.nvtGameMode) "1" else "0"
        val hopCmd = if (config.nvtHopFix) "0x75" else "0x76"
        val jitCmd = String.format(Locale.US, "0x7F 0x0C 0x%02X", config.nvtJitterLevel.coerceIn(0, 3))
        val smCmd = String.format(Locale.US, "0x7F 0x0D 0x%02X", config.nvtSmoothLevel.coerceIn(0, 3))
        val ktchEn = if (config.ktchEnabled) "1" else "0"
        val ktchDur = config.ktchDurationMs.coerceIn(0, 2000)
        val ktchEas = config.ktchEasBoost.coerceIn(0, 100)
        val resamp = if (config.inputResampling) "true" else "false"
        val resamplingSupported = GraphicsPipelineProbe.detect().isInputResamplingSupported

        val irqCmd = TouchIrqShellBuilder.buildApplyAffinitySnippet(config.touchIrqBigCores)
        val resampCmd = if (resamplingSupported) {
            GraphicsPipelineProbe.buildSetPropCommand("ro.input.resampling", resamp, readOnlyProp = true)
        } else {
            ""
        }

        val edgeBlock = if (applyEdgeGesture) {
            val scaleL = String.format(Locale.US, "%.2f", config.backGestureScaleLeft.coerceIn(0f, 2f))
            val scaleR = String.format(Locale.US, "%.2f", config.backGestureScaleRight.coerceIn(0f, 2f))
            val mlThresh = config.backGestureMlThreshold.coerceIn(0.50f, 0.99f)
            val mlStr = String.format(Locale.US, "%.2f", mlThresh)
            val tmpMlStr = if (abs(mlThresh - 0.95f) > 0.005f) "0.951" else "0.961"
            val mlEn = if (config.backGestureMlEnabled) "true" else "false"
            val overlayCmd = when (config.backGestureOverlay) {
                TouchPrecisionConfig.OVERLAY_DEFAULT -> """
                    cmd overlay disable com.android.internal.systemui.navbar.gestural_narrow_back >/dev/null 2>&1
                    cmd overlay disable com.android.internal.systemui.navbar.gestural_wide_back >/dev/null 2>&1
                    cmd overlay disable com.android.internal.systemui.navbar.gestural_extra_wide_back >/dev/null 2>&1
                """.trimIndent()
                TouchPrecisionConfig.OVERLAY_WIDE -> """
                    cmd overlay disable com.android.internal.systemui.navbar.gestural_narrow_back >/dev/null 2>&1
                    cmd overlay disable com.android.internal.systemui.navbar.gestural_extra_wide_back >/dev/null 2>&1
                    cmd overlay enable com.android.internal.systemui.navbar.gestural_wide_back >/dev/null 2>&1
                """.trimIndent()
                TouchPrecisionConfig.OVERLAY_EXTRA_WIDE -> """
                    cmd overlay disable com.android.internal.systemui.navbar.gestural_narrow_back >/dev/null 2>&1
                    cmd overlay disable com.android.internal.systemui.navbar.gestural_wide_back >/dev/null 2>&1
                    cmd overlay enable com.android.internal.systemui.navbar.gestural_extra_wide_back >/dev/null 2>&1
                """.trimIndent()
                else -> """
                    cmd overlay disable com.android.internal.systemui.navbar.gestural_wide_back >/dev/null 2>&1
                    cmd overlay disable com.android.internal.systemui.navbar.gestural_extra_wide_back >/dev/null 2>&1
                    cmd overlay enable com.android.internal.systemui.navbar.gestural_narrow_back >/dev/null 2>&1
                """.trimIndent()
            }
            """
                $overlayCmd
                settings put secure back_gesture_inset_scale_left $scaleL 2>/dev/null
                settings put secure back_gesture_inset_scale_right $scaleR 2>/dev/null
                device_config put systemui use_back_gesture_ml_model false >/dev/null 2>&1
                device_config put systemui back_gesture_ml_model_threshold $tmpMlStr >/dev/null 2>&1
                sleep 0.25
                device_config put systemui back_gesture_ml_model_threshold $mlStr >/dev/null 2>&1
                device_config put systemui use_back_gesture_ml_model $mlEn >/dev/null 2>&1
            """.trimIndent()
        } else {
            ""
        }

        val cmd = """
            if [ -e /proc/nvt_micro_gain ]; then
                echo $gameVal > /proc/nvt_game_mode 2>/dev/null
                echo $spi > /proc/nvt_spi_speed 2>/dev/null
                echo $gain > /proc/nvt_micro_gain 2>/dev/null
                echo $eg > /proc/nvt_edge_guard 2>/dev/null
                echo "$hopCmd" > /proc/nvt_touch_cmd 2>/dev/null
                echo "$jitCmd" > /proc/nvt_touch_cmd 2>/dev/null
                echo "$smCmd" > /proc/nvt_touch_cmd 2>/dev/null
            fi
            if [ -e /proc/perfmgr/tchbst/kernel/tb_enable ]; then
                echo $ktchEn > /proc/perfmgr/tchbst/kernel/tb_enable 2>/dev/null
                echo $ktchDur > /proc/perfmgr/tchbst/kernel/tb_duration_ms 2>/dev/null
                echo $ktchEas > /proc/perfmgr/tchbst/kernel/tb_eas_boost 2>/dev/null
            fi
            $irqCmd
            $resampCmd
            $edgeBlock
        """.trimIndent()

        runCatching { Shell.cmd(cmd).exec() }
            .onFailure { Log.e(TAG, "applyConfig error: ${it.message}") }

        probeHardwareSupport()
    }

    private suspend fun syncConfigJson(cfg: TouchPrecisionConfig) {
        val pairs = listOf(
            Triple("display_touch.touch_boot_persist", cfg.applyOnBoot.toString(), "bool"),
            Triple("display_touch.nvt_micro_gain", cfg.nvtMicroGain.toString(), "int"),
            Triple("display_touch.nvt_edge_guard_px", cfg.nvtEdgeGuardPx.toString(), "int"),
            Triple("display_touch.nvt_spi_speed_hz", cfg.nvtSpiSpeedHz.toString(), "int"),
            Triple("display_touch.nvt_game_mode", cfg.nvtGameMode.toString(), "bool"),
            Triple("display_touch.nvt_hop_fix", cfg.nvtHopFix.toString(), "bool"),
            Triple("display_touch.nvt_jitter_level", cfg.nvtJitterLevel.toString(), "int"),
            Triple("display_touch.nvt_smooth_level", cfg.nvtSmoothLevel.toString(), "int"),
            Triple("display_touch.ktch_enabled", cfg.ktchEnabled.toString(), "bool"),
            Triple("display_touch.ktch_duration_ms", cfg.ktchDurationMs.toString(), "int"),
            Triple("display_touch.ktch_eas_boost", cfg.ktchEasBoost.toString(), "int"),
            Triple("display_touch.edge_gesture_custom_enabled", cfg.edgeGestureCustomEnabled.toString(), "bool"),
            Triple("display_touch.back_gesture_overlay", cfg.backGestureOverlay, "str"),
            Triple("display_touch.back_gesture_scale_left", String.format(Locale.US, "%.2f", cfg.backGestureScaleLeft), "float"),
            Triple("display_touch.back_gesture_scale_right", String.format(Locale.US, "%.2f", cfg.backGestureScaleRight), "float"),
            Triple("display_touch.back_gesture_ml_enabled", cfg.backGestureMlEnabled.toString(), "bool"),
            Triple("display_touch.back_gesture_ml_threshold", String.format(Locale.US, "%.2f", cfg.backGestureMlThreshold), "float"),
            Triple("display_touch.input_resampling", cfg.inputResampling.toString(), "bool"),
            Triple("display_touch.touch_irq_big_cores", cfg.touchIrqBigCores.toString(), "bool"),
        )
        for ((k, v, t) in pairs) {
            LynxAppStateRepository.writeStateKey(k, v, t)
        }
    }

    internal fun parseProbeLines(lines: List<String>): TouchPrecisionHardwareSupport {
        val kv = lines.mapNotNull { line ->
            val idx = line.indexOf('=')
            if (idx > 0) line.substring(0, idx) to line.substring(idx + 1) else null
        }.toMap()

        val spiRaw = kv["SPI"].orEmpty()
        val spiHz = Regex("spi_hz=(\\d+)").find(spiRaw)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 15_000_000
        val spiReq = Regex("req=(\\d+)").find(spiRaw)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: spiHz
        val stats = kv["STATS"].orEmpty()
        val dtLast = Regex("dt_us\\(last/min/max\\)=(\\d+)/").find(stats)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val spiErr = Regex("spi_err=(\\d+)").find(stats)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val csumErr = Regex("csum_err=(\\d+)").find(stats)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val frameCnt = Regex("frame=(\\d+)").find(stats)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
        val microMoveCnt = Regex("micro_move=(\\d+)").find(stats)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
        val gameActive = Regex("game=(\\d+)").find(stats)?.groupValues?.getOrNull(1)?.toIntOrNull() == 1
        val hopActive = Regex("hop_fix=(\\d+)").find(stats)?.groupValues?.getOrNull(1)?.toIntOrNull() != 0
        val jitLvl = Regex("jitter=\\d+\\(0x([0-9A-Fa-f]+)\\)").find(stats)?.groupValues?.getOrNull(1)?.toIntOrNull(16) ?: 0
        val smLvl = Regex("smooth=\\d+\\(0x([0-9A-Fa-f]+)\\)").find(stats)?.groupValues?.getOrNull(1)?.toIntOrNull(16) ?: 0
        val fwRaw = kv["FW"].orEmpty().trim()
        val fwVer = Regex("fw_ver=(0x[0-9A-Fa-f]+|\\d+)")
            .find(fwRaw)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { raw ->
                if (raw.startsWith("0x", ignoreCase = true)) raw
                else raw.toIntOrNull()?.let { String.format(Locale.US, "0x%02X", it) } ?: raw
            }
            ?: fwRaw.ifBlank { "-" }
        val edgeRaw = kv["EDGE"].orEmpty()
        val leftPx = Regex("mEdgeWidthLeft=(\\d+)").find(edgeRaw)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 14
        val rightPx = Regex("mEdgeWidthRight=(\\d+)").find(edgeRaw)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 14
        val mlActive = Regex("mMLModelThreshold=([0-9.]+)").find(edgeRaw)?.groupValues?.getOrNull(1) ?: "0.96"

        return TouchPrecisionHardwareSupport(
            nvtPresent = kv["NVT"] == "1",
            nvtIrqBigCoresNodePresent = kv["NVT_IRQ_NODE"] == "1",
            ktchPresent = kv["KTCH"] == "1",
            nvtMicroGainActive = kv["GAIN"]?.toIntOrNull() ?: 1,
            nvtEdgeGuardActive = kv["EG"]?.toIntOrNull() ?: 48,
            nvtSpiHzActive = spiHz,
            nvtSpiHzReq = spiReq,
            nvtGameModeActive = gameActive,
            nvtHopFixActive = hopActive,
            nvtJitterLevel = jitLvl,
            nvtSmoothLevel = smLvl,
            nvtFwVersion = fwVer,
            nvtDtUsLast = dtLast,
            nvtSpiErrors = spiErr,
            nvtCsumErrors = csumErr,
            nvtFrameCount = frameCnt,
            nvtMicroMoveCount = microMoveCnt,
            ktchDurationActive = kv["KDUR"]?.toIntOrNull() ?: 280,
            ktchEasBoostActive = kv["KEAS"]?.toIntOrNull() ?: 80,
            edgeWidthLeftPx = leftPx,
            edgeWidthRightPx = rightPx,
            edgeMlThresholdActive = mlActive,
            activeResampling = kv["RESAMP"] == "true" || kv["RESAMP"] == "1",
            activeIrqPinned = kv["IRQPIN"] == "1",
            resamplingSupported = kv["RESAMP_SUP"] == "1",
            touchIrqSupported = kv["IRQ_SUP"] == "1",
        )
    }

    fun roundScale2(value: Float): Float = ((value * 50f).roundToInt() / 50f).coerceIn(0.0f, 2.0f)

    fun roundThreshold2(value: Float): Float = ((value * 100f).roundToInt() / 100f).coerceIn(0.50f, 0.99f)
}
