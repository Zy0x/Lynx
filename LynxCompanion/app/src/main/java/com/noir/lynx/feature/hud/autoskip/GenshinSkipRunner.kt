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
 * Ultra-low overhead, lag-free Dialogue Auto-Skip Runner with intelligent Auto-Disarm.
 * Executes micro-taps only when active and dialogue is in progress.
 * Automatically halts tapping and disarms the pill as soon as dialogue finishes to protect gameplay.
 */
class GenshinSkipRunner(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val activeState = MutableStateFlow(false)
    val isSkipping = activeState.asStateFlow()

    @Volatile
    var currentForegroundPackage: String = ""

    @Volatile
    var isHudOpen: Boolean = false

    @Volatile
    var speedMs: Long = 250L

    @Volatile
    var targetChoiceBottom: Boolean = false

    @Volatile
    var screenWidth: Int = 2460

    @Volatile
    var screenHeight: Int = 1080

    private var job: Job? = null
    private var monitorJob: Job? = null
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var touchDeviceNode: String? = null

    init {
        scope.launch(Dispatchers.IO) {
            findTouchDevice()
        }
    }

    private fun findTouchDevice() {
        if (!Shell.getShell().isRoot) return
        val out = Shell.cmd("getevent -lp 2>/dev/null").exec().out
        var currentDev: String? = null
        for (line in out) {
            val trimmed = line.trim()
            if (trimmed.startsWith("add device")) {
                currentDev = trimmed.substringAfter(":", "").trim()
            } else if (trimmed.contains("INPUT_PROP_DIRECT") && currentDev != null) {
                touchDeviceNode = currentDev
                break
            }
        }
    }

    fun toggle() {
        if (activeState.value) stop() else start()
    }

    fun start() {
        if (activeState.value) return
        activeState.value = true
        job?.cancel()
        monitorJob?.cancel()

        job = scope.launch(Dispatchers.IO) {
            runLoop()
        }
        monitorJob = scope.launch(Dispatchers.IO) {
            runDialogueMonitor()
        }
    }

    fun stop() {
        activeState.value = false
        job?.cancel()
        job = null
        monitorJob?.cancel()
        monitorJob = null
    }

    private fun stopWithHaptic() {
        stop()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(35L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(35L)
            }
        } catch (_: Throwable) {}
    }

    private suspend fun runLoop() {
        while (activeState.value && currentCoroutineContext().isActive) {
            val isScreenOn = powerManager?.isInteractive ?: true
            if (!isScreenOn || isHudOpen) {
                delay(300L)
                continue
            }

            if (!GenshinSkipEngine.isGameSupported(currentForegroundPackage)) {
                delay(400L)
                continue
            }

            val coords = GenshinSkipEngine.resolveCoordinates(screenWidth, screenHeight)
            val choiceTarget = if (targetChoiceBottom) coords.bottomChoice else coords.topChoice
            val advanceTarget = coords.advance

            val halfSpeed = (speedMs / 2).coerceAtLeast(60L)

            // 1. Choice tap (clicks dialogue option pill if visible)
            tap(choiceTarget.x, choiceTarget.y)
            delay(halfSpeed)

            // 2. Advance tap (clicks dialogue typewriter forward)
            tap(advanceTarget.x, advanceTarget.y)
            delay(halfSpeed)
        }
        activeState.value = false
    }

    /**
     * Parallel background watcher: monitors dialogue presence every ~750ms.
     * When dialogue disappears for 2 consecutive checks (~1.5s), automatically halts tapping
     * to prevent interference with gameplay and reverts HUD pill state.
     */
    private suspend fun runDialogueMonitor() {
        delay(400L)
        var hasDetectedDialogue = false
        var nonDialogueStreak = 0
        val startTime = System.currentTimeMillis()
        val maxDurationMs = 90_000L // 90s safety watchdog

        while (activeState.value && currentCoroutineContext().isActive) {
            delay(750L)

            // 1. Max safety duration cap
            if (System.currentTimeMillis() - startTime > maxDurationMs) {
                stopWithHaptic()
                break
            }

            val isScreenOn = powerManager?.isInteractive ?: true
            if (!isScreenOn || isHudOpen) {
                continue
            }

            // 2. Ensure supported game is in foreground
            if (!GenshinSkipEngine.isGameSupported(currentForegroundPackage)) {
                stopWithHaptic()
                break
            }

            // 3. Inspect top-left dialogue control circle color
            val isDialogue = GenshinSkipEngine.checkDialogueActive(screenWidth, screenHeight)

            if (isDialogue) {
                hasDetectedDialogue = true
                nonDialogueStreak = 0
            } else {
                nonDialogueStreak++
                if (hasDetectedDialogue && nonDialogueStreak >= 2) {
                    // Dialogue has finished! Auto-disarm immediately so gameplay is completely undisturbed.
                    stopWithHaptic()
                    break
                } else if (!hasDetectedDialogue && nonDialogueStreak >= 5) {
                    // Pre-armed standby: no dialogue started within ~4s, disarm safely.
                    stopWithHaptic()
                    break
                }
            }
        }
    }

    private fun tap(x: Int, y: Int) {
        Shell.cmd("input tap $x $y >/dev/null 2>&1").submit()
    }
}
