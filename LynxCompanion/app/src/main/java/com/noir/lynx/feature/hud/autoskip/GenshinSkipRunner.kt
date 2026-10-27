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
import java.io.BufferedInputStream

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

    private val executingState = MutableStateFlow(false)
    val isTappingLive = executingState.asStateFlow()

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
        this.currentForegroundPackage = pkg
        this.isHudOpen = isHudOpen
        this.screenWidth = width
        this.screenHeight = height
        this.isEnabled = enabled
        this.speedMs = speedMs.coerceAtLeast(45L)
        this.targetChoiceBottom = targetChoiceBottom

        if (!enabled) {
            if (modeState.value != SkipMode.PAUSED) {
                setMode(SkipMode.PAUSED)
            }
        }
    }

    private var lastActiveMode: SkipMode = SkipMode.MANUAL

    /**
     * Manual user toggle via floating trigger pill.
     * Cycles through operational modes alternating through Pause:
     * [SkipMode.AUTO] -> [SkipMode.PAUSED] -> [SkipMode.MANUAL] (NonStop) -> [SkipMode.PAUSED] -> [SkipMode.AUTO] ...
     */
    fun toggle() {
        when (modeState.value) {
            SkipMode.AUTO -> {
                lastActiveMode = SkipMode.AUTO
                setMode(SkipMode.PAUSED)
            }
            SkipMode.MANUAL -> {
                lastActiveMode = SkipMode.MANUAL
                setMode(SkipMode.PAUSED)
            }
            SkipMode.PAUSED -> {
                if (lastActiveMode == SkipMode.AUTO) {
                    setMode(SkipMode.MANUAL)
                } else {
                    setMode(SkipMode.AUTO)
                }
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
                executingState.value = false
                tapJob?.cancel()
                tapJob = null
                triggerHaptic(25L)
            }
            SkipMode.AUTO -> {
                activeState.value = true
                pausedState.value = false
                executingState.value = false
                tapJob?.cancel()
                tapJob = scope.launch(Dispatchers.IO) { runTapLoop() }
                triggerHaptic(30L)
            }
            SkipMode.MANUAL -> {
                activeState.value = true
                pausedState.value = false
                executingState.value = true
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

    suspend fun probeScene(): GenshinSkipEngine.SceneClassification = withContext(Dispatchers.IO) {
        var proc: Process? = null
        try {
            val (tlHead, tlSliceBytes) = GenshinSkipEngine.calculateDialogueScanSlice(screenWidth, screenHeight)
            val tlStart = tlHead - tlSliceBytes

            // In-process native binary stream: skips 6 external shell forks (head, tail, xxd, grep, wc)
            // Aborts immediately after reading target bytes, saving SurfaceFlinger GPU bandwidth and frame drops
            proc = ProcessBuilder("su", "-c", "exec screencap").start()
            val stream = BufferedInputStream(proc.inputStream, 32768)

            // 1. Skip to top-left dialogue slice (row 56)
            var skipped = 0L
            val skipBuf = ByteArray(16384)
            while (skipped < tlStart) {
                val toRead = minOf(skipBuf.size.toLong(), tlStart - skipped).toInt()
                val r = stream.read(skipBuf, 0, toRead)
                if (r <= 0) break
                skipped += r
            }
            if (skipped < tlStart) {
                return@withContext GenshinSkipEngine.SceneClassification(
                    GenshinSkipEngine.SceneType.UNKNOWN, 0f, 0, 0, 0f, 0f
                )
            }

            val tlSlice = ByteArray(tlSliceBytes.toInt())
            var tlRead = 0
            while (tlRead < tlSlice.size) {
                val r = stream.read(tlSlice, tlRead, tlSlice.size - tlRead)
                if (r <= 0) break
                tlRead += r
            }
            if (tlRead < tlSlice.size) {
                return@withContext GenshinSkipEngine.SceneClassification(
                    GenshinSkipEngine.SceneType.UNKNOWN, 0f, 0, 0, 0f, 0f
                )
            }

            var dialogueMatches = 0
            var blackPixels = 0
            var whitePixels = 0
            val totalSampled = tlSlice.size / 4
            var i = 0
            val limit = tlSlice.size - 3
            while (i < limit) {
                val red = tlSlice[i].toInt() and 0xFF
                val green = tlSlice[i + 1].toInt() and 0xFF
                val blue = tlSlice[i + 2].toInt() and 0xFF
                if (GenshinSkipEngine.isDialogueColor(red, green, blue)) {
                    dialogueMatches++
                }
                if (red < 30 && green < 30 && blue < 30) {
                    blackPixels++
                }
                if (red > 230 && green > 230 && blue > 230) {
                    whitePixels++
                }
                i += 4
            }

            val minThreshold = GenshinSkipEngine.calculateMinDialogueThreshold(screenHeight)
            val blackRatio = if (totalSampled > 0) blackPixels.toFloat() / totalSampled else 0f
            val whiteRatio = if (totalSampled > 0) whitePixels.toFloat() / totalSampled else 0f

            // Fast Exit 1: Standard dialogue control confirmed in top-left (<10ms probe)
            if (dialogueMatches >= minThreshold) {
                return@withContext GenshinSkipEngine.SceneClassification(
                    GenshinSkipEngine.SceneType.STANDARD_DIALOGUE,
                    0.98f,
                    dialogueMatches,
                    0,
                    blackRatio,
                    whiteRatio
                )
            }

            // Fast Exit 2: Pure white loading screen
            if (whiteRatio > 0.85f) {
                return@withContext GenshinSkipEngine.SceneClassification(
                    GenshinSkipEngine.SceneType.WHITE_LOADING,
                    0.99f,
                    dialogueMatches,
                    0,
                    blackRatio,
                    whiteRatio
                )
            }

            // Fast Exit 3: Normal open-world gameplay or start screen (colorful sky/terrain, not black)
            if (blackRatio < 0.85f) {
                return@withContext GenshinSkipEngine.SceneClassification(
                    GenshinSkipEngine.SceneType.OPEN_WORLD,
                    0.90f,
                    dialogueMatches,
                    0,
                    blackRatio,
                    whiteRatio
                )
            }

            // Deep Probe: Pitch black screen detected in top row (blackRatio >= 85%).
            // Advance forward down the screencap stream to row 944 to check for narration prompt ("Tekan untuk melanjutkan")
            val (promptHead, promptSliceBytes) = GenshinSkipEngine.calculatePromptScanSlice(screenWidth, screenHeight)
            val promptStart = promptHead - promptSliceBytes

            var currentOffset = tlHead
            while (currentOffset < promptStart) {
                val toRead = minOf(skipBuf.size.toLong(), promptStart - currentOffset).toInt()
                val r = stream.read(skipBuf, 0, toRead)
                if (r <= 0) break
                currentOffset += r
            }
            if (currentOffset < promptStart) {
                return@withContext GenshinSkipEngine.SceneClassification(
                    GenshinSkipEngine.SceneType.OPEN_WORLD, 0.80f, dialogueMatches, 0, blackRatio, whiteRatio
                )
            }

            val promptSlice = ByteArray(promptSliceBytes.toInt())
            var pRead = 0
            while (pRead < promptSlice.size) {
                val r = stream.read(promptSlice, pRead, promptSlice.size - pRead)
                if (r <= 0) break
                pRead += r
            }
            if (pRead < promptSlice.size) {
                return@withContext GenshinSkipEngine.SceneClassification(
                    GenshinSkipEngine.SceneType.OPEN_WORLD, 0.80f, dialogueMatches, 0, blackRatio, whiteRatio
                )
            }

            var goldMatches = 0
            var pi = 0
            val plimit = promptSlice.size - 3
            while (pi < plimit) {
                val r = promptSlice[pi].toInt() and 0xFF
                val g = promptSlice[pi + 1].toInt() and 0xFF
                val b = promptSlice[pi + 2].toInt() and 0xFF
                if (GenshinSkipEngine.isPromptGoldColor(r, g, b)) {
                    goldMatches++
                }
                pi += 4
            }

            if (goldMatches >= 15) {
                return@withContext GenshinSkipEngine.SceneClassification(
                    GenshinSkipEngine.SceneType.BLACK_INTERSTITIAL,
                    0.99f,
                    dialogueMatches,
                    goldMatches,
                    blackRatio,
                    whiteRatio
                )
            } else {
                return@withContext GenshinSkipEngine.SceneClassification(
                    GenshinSkipEngine.SceneType.OPEN_WORLD,
                    0.85f,
                    dialogueMatches,
                    goldMatches,
                    blackRatio,
                    whiteRatio
                )
            }
        } catch (_: Throwable) {
            return@withContext GenshinSkipEngine.SceneClassification(
                GenshinSkipEngine.SceneType.UNKNOWN, 0f, 0, 0, 0f, 0f
            )
        } finally {
            runCatching { proc?.destroy() }
        }
    }

    suspend fun probeMatchingPixels(): Int {
        return probeScene().dialogueMatches
    }

    suspend fun isGenshinDialogueActive(): Boolean {
        val scene = probeScene()
        return scene.sceneType == GenshinSkipEngine.SceneType.STANDARD_DIALOGUE ||
               scene.sceneType == GenshinSkipEngine.SceneType.BLACK_INTERSTITIAL
    }

    private suspend fun runTapLoop() {
        val maxContinuousManualMs = 60_000L // 60s auto-disarm safety shield for manual non-stop mode
        val manualStartTime = System.currentTimeMillis()
        var cycleIndex = 0
        var consecutiveInactiveCount = 0
        var burstCountRemaining = 0
        var activeSceneType = GenshinSkipEngine.SceneType.UNKNOWN
        val tapBin = if (Build.VERSION.SDK_INT >= 29) "cmd input tap" else "input tap"

        while (modeState.value != SkipMode.PAUSED && currentCoroutineContext().isActive) {
            val isScreenOn = powerManager?.isInteractive ?: true
            if (!isScreenOn || isHudOpen || !GenshinSkipEngine.isGameSupported(currentForegroundPackage)) {
                executingState.value = false
                delay(1000L)
                continue
            }

            val current = modeState.value

            // Safety timeout: automatically stop tapping after 60s in MANUAL mode to protect open-world gameplay
            if (current == SkipMode.MANUAL && (System.currentTimeMillis() - manualStartTime > maxContinuousManualMs)) {
                setMode(SkipMode.PAUSED)
                break
            }

            // Multi-Scene Vision AI Cadence for SkipMode.AUTO:
            // 1. Standby Phase (Outside Dialogue): Polls lightweight slice at 1200ms, sends 0 taps. Guarantees 0% lag & 60/120 FPS.
            // 2. Burst Phase (Inside Standard Dialogue): Fires fast cycling taps without screencap interrupts.
            // 3. Burst Phase (Inside Black Interstitial): Fires rapid taps on narration prompt target.
            // 4. Dialogue End: Returns cleanly to Standby Phase (0 tap) without ever resetting user mode to PAUSED.
            if (current == SkipMode.AUTO) {
                if (burstCountRemaining <= 0) {
                    val scene = probeScene()
                    activeSceneType = scene.sceneType
                    when (scene.sceneType) {
                        GenshinSkipEngine.SceneType.STANDARD_DIALOGUE -> {
                            val effSpeed = speedMs.coerceAtLeast(45L)
                            burstCountRemaining = (1200L / effSpeed).toInt().coerceIn(8, 24)
                            consecutiveInactiveCount = 0
                            executingState.value = true
                        }
                        GenshinSkipEngine.SceneType.BLACK_INTERSTITIAL -> {
                            val effSpeed = speedMs.coerceAtLeast(45L)
                            burstCountRemaining = (800L / effSpeed).toInt().coerceIn(4, 16)
                            consecutiveInactiveCount = 0
                            executingState.value = true
                        }
                        else -> {
                            burstCountRemaining = 0
                            executingState.value = false
                            consecutiveInactiveCount++
                            val standbyDelay = if (consecutiveInactiveCount > 3) 1200L else 800L
                            delay(standbyDelay)
                            continue
                        }
                    }
                }
                burstCountRemaining--
            } else if (current == SkipMode.MANUAL) {
                executingState.value = true
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

            val effectiveSpeed = speedMs.coerceAtLeast(45L)

            // Adaptive Multi-Scene Touch Dispatch:
            // - BLACK_INTERSTITIAL: Confirms the prompt directly on narrationTarget ("Tekan untuk melanjutkan")
            // - STANDARD_DIALOGUE / MANUAL: Cycles through Advance, Choice, and Narration targets
            val target = if (activeSceneType == GenshinSkipEngine.SceneType.BLACK_INTERSTITIAL) {
                narrationTarget
            } else {
                when (cycleIndex % 3) {
                    0 -> advanceTarget
                    1 -> choiceTarget
                    else -> narrationTarget
                }
            }
            Shell.cmd("$tapBin ${target.x} ${target.y} >/dev/null 2>&1").submit()

            cycleIndex++
            delay(effectiveSpeed)
        }

        if (modeState.value != SkipMode.PAUSED) {
            setMode(SkipMode.PAUSED)
        }
    }
}
