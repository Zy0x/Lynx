package com.noir.lynx.feature.hud.autoskip

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ultra-low overhead, lag-free Dialogue Auto-Skip Runner.
 * Supports 3 discrete operational modes:
 * - [SkipMode.PAUSED]: Zero taps, 100% player control for combat & navigation.
 * - [SkipMode.AUTO]: Scene-Aware Auto-Skip. Detects dialogue UI and stops tapping within <=0.4s when dialogue ends.
 * - [SkipMode.MANUAL]: Continuous Fast-Burst Nonstop. Rapid continuous tapping without vision/pixel checks.
 *
 * Cycles via floating trigger pill in 4 stages:
 * PAUSED -> AUTO -> PAUSED -> MANUAL -> PAUSED -> AUTO ...
 */
class GenshinSkipRunner(
    private val context: Context,
    private val scope: CoroutineScope
) {
    enum class SkipMode {
        PAUSED,
        AUTO,
        MANUAL
    }

    private val modeState = MutableStateFlow(SkipMode.PAUSED)
    val currentMode = modeState.asStateFlow()

    private val activeState = MutableStateFlow(false)
    val isSkipping = activeState.asStateFlow()

    private val pausedState = MutableStateFlow(true)
    val isPaused = pausedState.asStateFlow()

    private var previousActiveMode: SkipMode = SkipMode.MANUAL

    @Volatile
    var currentForegroundPackage: String = ""

    @Volatile
    var isHudOpen: Boolean = false

    @Volatile
    var speedMs: Long = 350L

    @Volatile
    var targetChoiceBottom: Boolean = false

    @Volatile
    var screenWidth: Int = 2460

    @Volatile
    var screenHeight: Int = 1080

    @Volatile
    var isEnabled: Boolean = false

    private var tapJob: Job? = null
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun syncState(
        pkg: String,
        isHudOpen: Boolean,
        width: Int,
        height: Int,
        enabled: Boolean,
        speedMs: Long,
        targetChoiceBottom: Boolean
    ) {
        val packageChanged = this.currentForegroundPackage != pkg
        this.currentForegroundPackage = pkg
        this.isHudOpen = isHudOpen
        this.screenWidth = width
        this.screenHeight = height
        this.isEnabled = enabled
        this.speedMs = speedMs.coerceAtLeast(150L)
        this.targetChoiceBottom = targetChoiceBottom

        if (!enabled || !GenshinSkipEngine.isGameSupported(pkg) || packageChanged) {
            if (modeState.value != SkipMode.PAUSED) {
                setMode(SkipMode.PAUSED)
            }
        }
    }

    /**
     * Manual user toggle via floating trigger pill.
     * Direct toggle between intelligent AUTO dialogue skip and PAUSED.
     */
    fun toggle() {
        when (modeState.value) {
            SkipMode.PAUSED -> {
                setMode(SkipMode.AUTO)
            }
            SkipMode.AUTO, SkipMode.MANUAL -> {
                setMode(SkipMode.PAUSED)
            }
        }
    }

    fun start() {
        setMode(SkipMode.AUTO)
    }

    fun stop() {
        setMode(SkipMode.PAUSED)
    }

    fun setMode(mode: SkipMode) {
        if (modeState.value == mode && (tapJob != null || mode == SkipMode.PAUSED)) return
        modeState.value = mode
        when (mode) {
            SkipMode.PAUSED -> {
                activeState.value = false
                pausedState.value = true
                tapJob?.cancel()
                tapJob = null
                triggerHaptic(25L)
            }
            SkipMode.AUTO -> {
                activeState.value = true
                pausedState.value = false
                tapJob?.cancel()
                tapJob = scope.launch(Dispatchers.IO) { runTapLoop() }
                triggerHaptic(30L)
            }
            SkipMode.MANUAL -> {
                activeState.value = true
                pausedState.value = false
                tapJob?.cancel()
                tapJob = scope.launch(Dispatchers.IO) { runTapLoop() }
                triggerHaptic(45L)
            }
        }
    }

    private fun triggerHaptic(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (_: Throwable) {}
    }

    suspend fun isGenshinDialogueActive(): Boolean = withContext(Dispatchers.IO) {
        try {
            val (headBytes, sliceBytes) = GenshinSkipEngine.calculateDialogueScanSlice(screenWidth, screenHeight)
            val minThreshold = GenshinSkipEngine.calculateMinDialogueThreshold(screenHeight)
            val cmd = "screencap | head -c $headBytes | tail -c $sliceBytes | xxd -p | grep -o -E 'e[6-f]e[0-8]d[2-d]|ece5d8|ece4d8|ede5da|ede5d8|eee6da|ebe4d7' | wc -l"
            val result = Shell.cmd(cmd).exec()
            if (result.isSuccess && result.out.isNotEmpty()) {
                val count = result.out.firstOrNull()?.trim()?.toIntOrNull() ?: 0
                return@withContext count >= minThreshold
            }
        } catch (_: Throwable) {}
        return@withContext false
    }

    private suspend fun runTapLoop() {
        val maxContinuousManualMs = 60_000L // 60s auto-disarm safety shield for manual non-stop mode
        val manualStartTime = System.currentTimeMillis()
        var cycleIndex = 0
        var consecutiveInactiveCount = 0

        while (modeState.value != SkipMode.PAUSED && currentCoroutineContext().isActive) {
            val isScreenOn = powerManager?.isInteractive ?: true
            if (!isScreenOn || isHudOpen) {
                delay(300L)
                continue
            }

            if (!GenshinSkipEngine.isGameSupported(currentForegroundPackage)) {
                setMode(SkipMode.PAUSED)
                break
            }

            val current = modeState.value

            // Safety timeout: automatically stop tapping after 60s in MANUAL mode to protect open-world gameplay
            if (current == SkipMode.MANUAL && (System.currentTimeMillis() - manualStartTime > maxContinuousManualMs)) {
                setMode(SkipMode.PAUSED)
                break
            }

            // Scene-Aware Auto check for SkipMode.AUTO
            var checkDuration = 0L
            if (current == SkipMode.AUTO) {
                val t0 = System.currentTimeMillis()
                val isDialogue = isGenshinDialogueActive()
                checkDuration = System.currentTimeMillis() - t0
                if (!isDialogue) {
                    consecutiveInactiveCount++
                    // Standby Hening: dialogue is inactive, immediately suppress all taps.
                    // Protects player navigation, combat, joystick, camera movement, and open-world HUD.
                    delay(200L)
                    if (consecutiveInactiveCount > 60) {
                        // After ~12s without dialogue, disarm to PAUSED
                        setMode(SkipMode.PAUSED)
                        break
                    }
                    continue
                } else {
                    consecutiveInactiveCount = 0
                }
            }

            val coords = GenshinSkipEngine.resolveCoordinates(screenWidth, screenHeight)
            val choiceTarget = if (targetChoiceBottom) {
                when (cycleIndex % 4) {
                    0 -> coords.choice4
                    1 -> coords.choice3
                    2 -> coords.choice2
                    else -> coords.choice1
                }
            } else {
                when (cycleIndex % 4) {
                    0 -> coords.choice2
                    1 -> coords.choice1
                    2 -> coords.choice3
                    else -> coords.choice4
                }
            }
            val advanceTarget = coords.advance
            val narrationTarget = coords.narration

            val effectiveSpeed = speedMs.coerceAtLeast(150L)

            // Dynamic cadence: alternate between (advance + choice) and (advance + narration)
            // Guarantees fast dialogue skip without touch event queue congestion
            val cmd = if (cycleIndex % 2 == 0) {
                "input tap ${advanceTarget.x} ${advanceTarget.y} >/dev/null 2>&1; sleep 0.04; " +
                "input tap ${choiceTarget.x} ${choiceTarget.y} >/dev/null 2>&1"
            } else {
                "input tap ${advanceTarget.x} ${advanceTarget.y} >/dev/null 2>&1; sleep 0.04; " +
                "input tap ${narrationTarget.x} ${narrationTarget.y} >/dev/null 2>&1"
            }
            Shell.cmd(cmd).submit()

            cycleIndex++
            val remainingDelay = if (current == SkipMode.AUTO) {
                (effectiveSpeed - checkDuration).coerceAtLeast(60L)
            } else {
                effectiveSpeed
            }
            delay(remainingDelay)
        }

        if (modeState.value != SkipMode.PAUSED) {
            setMode(SkipMode.PAUSED)
        }
    }
}
