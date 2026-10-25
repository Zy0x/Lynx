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
 * Ultra-low overhead, lag-free Dialogue Auto-Skip Runner with Autonomous Hands-Free Trigger & Manual Pause.
 * When the trigger is active in a supported RPG, it continuously watches for dialogue:
 * - Automatically engages skipping when dialogue appears on screen without requiring manual button press.
 * - Automatically stops tapping when dialogue finishes to protect open-world gameplay.
 * - Allows the player to pause at any time by pressing the floating pill, and resume whenever ready.
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
    var speedMs: Long = 250L

    @Volatile
    var targetChoiceBottom: Boolean = false

    @Volatile
    var screenWidth: Int = 2460

    @Volatile
    var screenHeight: Int = 1080

    @Volatile
    var isEnabled: Boolean = false

    private var tapJob: Job? = null
    private var watcherJob: Job? = null
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

    fun syncState(
        pkg: String,
        isHudOpen: Boolean,
        width: Int,
        height: Int,
        enabled: Boolean,
        speedMs: Long,
        targetChoiceBottom: Boolean
    ) {
        this.currentForegroundPackage = pkg
        this.isHudOpen = isHudOpen
        this.screenWidth = width
        this.screenHeight = height
        this.isEnabled = enabled
        this.speedMs = speedMs
        this.targetChoiceBottom = targetChoiceBottom

        if (enabled && GenshinSkipEngine.isGameSupported(pkg)) {
            startAutonomousWatcher()
        } else {
            stopAutonomousWatcher()
        }
    }

    private fun startAutonomousWatcher() {
        if (watcherJob?.isActive == true) return
        watcherJob = scope.launch(Dispatchers.IO) {
            runAutonomousWatcher()
        }
    }

    private fun stopAutonomousWatcher() {
        watcherJob?.cancel()
        watcherJob = null
        stopTapping()
        pausedState.value = false
    }

    /**
     * Manual user toggle via floating trigger pill:
     * - If actively skipping -> PAUSE
     * - If paused -> RESUME
     * - If in standby -> manual force start / toggle
     */
    fun toggle() {
        if (activeState.value) {
            // User manually pauses during dialogue
            pausedState.value = true
            stopTapping()
            triggerHaptic(25L)
        } else if (pausedState.value) {
            // User manually resumes
            pausedState.value = false
            if (GenshinSkipEngine.checkDialogueActive(screenWidth, screenHeight)) {
                startTapping()
            }
            triggerHaptic(25L)
        } else {
            // Manual pre-arm / forced start in standby
            startTapping()
            triggerHaptic(25L)
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
     * Autonomous hands-free dialogue watcher.
     * Continuously senses dialogue presence:
     * - Automatically starts skipping as soon as dialogue appears.
     * - Automatically stops tapping when dialogue ends.
     * - Automatically resets manual pause when dialogue concludes.
     */
    private suspend fun runAutonomousWatcher() {
        var nonDialogueStreak = 0
        var activeRunStart = 0L
        val maxContinuousRunMs = 90_000L // 90s safety cap

        while (currentCoroutineContext().isActive && isEnabled) {
            // Dynamic adaptive sampling:
            // 650ms during active dialogue for snappy stopping, 1200ms during standby (<0.5% CPU)
            val checkInterval = if (activeState.value) 650L else 1200L
            delay(checkInterval)

            val isScreenOn = powerManager?.isInteractive ?: true
            if (!isScreenOn || isHudOpen || !GenshinSkipEngine.isGameSupported(currentForegroundPackage)) {
                if (activeState.value) {
                    stopTapping()
                }
                continue
            }

            // Safety watchdog timeout
            if (activeState.value && System.currentTimeMillis() - activeRunStart > maxContinuousRunMs) {
                stopWithHaptic()
                activeRunStart = 0L
                continue
            }

            val isDialogue = GenshinSkipEngine.checkDialogueActive(screenWidth, screenHeight)

            if (isDialogue) {
                nonDialogueStreak = 0
                // Hands-free auto-engage: If not paused and not currently tapping, start skipping automatically!
                if (!pausedState.value && !activeState.value) {
                    startTapping()
                    activeRunStart = System.currentTimeMillis()
                }
            } else {
                nonDialogueStreak++
                if (nonDialogueStreak >= 2) {
                    // Dialogue concluded -> stop tapping to shield open-world controls
                    if (activeState.value) {
                        stopWithHaptic()
                        activeRunStart = 0L
                    }
                    // Dialogue is fully over, reset paused state so the next conversation can auto-skip automatically
                    if (pausedState.value) {
                        pausedState.value = false
                    }
                }
            }
        }
    }

    private fun tap(x: Int, y: Int) {
        Shell.cmd("input tap $x $y >/dev/null 2>&1").submit()
    }
}
