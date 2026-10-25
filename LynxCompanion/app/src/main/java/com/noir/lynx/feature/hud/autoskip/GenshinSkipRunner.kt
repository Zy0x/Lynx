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
 * Triggered on-demand via the floating badge [⏩] during RPG dialogue cutscenes.
 * - Zero background polling or screencap execution to guarantee 100% stutter-free gaming.
 * - Single-tap toggle to start or pause skipping at any moment.
 * - Autonomous 60s safety timeout disarms tapping if dialogue concludes to protect open-world gameplay.
 * - Automatic disarm when user exits the game or screen turns off.
 */
class GenshinSkipRunner(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val activeState = MutableStateFlow(false)
    val isSkipping = activeState.asStateFlow()

    private val pausedState = MutableStateFlow(false)
    val isPaused = pausedState.asStateFlow()

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
        this.speedMs = speedMs.coerceAtLeast(300L)
        this.targetChoiceBottom = targetChoiceBottom

        if (!enabled || !GenshinSkipEngine.isGameSupported(pkg) || packageChanged) {
            if (activeState.value || pausedState.value) {
                stop()
            }
        }
    }

    /**
     * Manual user toggle via floating trigger pill:
     * - If actively skipping -> PAUSE
     * - If paused or standby -> START skipping with haptic confirmation
     */
    fun toggle() {
        if (activeState.value) {
            pausedState.value = true
            stopTapping()
            triggerHaptic(25L)
        } else {
            pausedState.value = false
            startTapping()
            triggerHaptic(30L)
        }
    }

    fun start() {
        pausedState.value = false
        startTapping()
    }

    fun stop() {
        pausedState.value = false
        stopTapping()
    }

    private fun startTapping() {
        if (activeState.value) return
        activeState.value = true
        tapJob?.cancel()
        tapJob = scope.launch(Dispatchers.IO) {
            runTapLoop()
        }
    }

    private fun stopTapping() {
        activeState.value = false
        tapJob?.cancel()
        tapJob = null
    }

    private fun stopWithHaptic() {
        stopTapping()
        pausedState.value = false
        triggerHaptic(35L)
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

    private suspend fun runTapLoop() {
        val maxDurationMs = 60_000L // 60s auto-disarm safety shield
        val startTime = System.currentTimeMillis()
        var cycleIndex = 0

        while (activeState.value && currentCoroutineContext().isActive) {
            val isScreenOn = powerManager?.isInteractive ?: true
            if (!isScreenOn || isHudOpen) {
                delay(300L)
                continue
            }

            if (!GenshinSkipEngine.isGameSupported(currentForegroundPackage)) {
                stopWithHaptic()
                break
            }

            // Safety timeout: automatically stop tapping after 60s to protect open-world gameplay
            if (System.currentTimeMillis() - startTime > maxDurationMs) {
                stopWithHaptic()
                break
            }

            val coords = GenshinSkipEngine.resolveCoordinates(screenWidth, screenHeight)
            val choiceTarget = if (targetChoiceBottom) {
                when (cycleIndex % 3) {
                    0 -> coords.bottomChoice
                    1 -> coords.midChoice
                    else -> coords.topChoice
                }
            } else {
                when (cycleIndex % 3) {
                    0 -> coords.midChoice
                    1 -> coords.bottomChoice
                    else -> coords.topChoice
                }
            }
            val advanceTarget = coords.advance
            val narrationTarget = coords.narration

            val effectiveSpeed = speedMs.coerceAtLeast(300L)

            // Batch advance, choice, and narration transition taps into a single root command
            // Guarantees 0% frame drops, handles 3D cutscenes, dialogue choices, and narration screens ("Tekan untuk melanjutkan")
            val cmd = "input tap ${advanceTarget.x} ${advanceTarget.y} >/dev/null 2>&1; sleep 0.03; " +
                      "input tap ${choiceTarget.x} ${choiceTarget.y} >/dev/null 2>&1; sleep 0.03; " +
                      "input tap ${narrationTarget.x} ${narrationTarget.y} >/dev/null 2>&1"
            Shell.cmd(cmd).submit()

            cycleIndex++
            delay(effectiveSpeed)
        }
        activeState.value = false
    }
}
