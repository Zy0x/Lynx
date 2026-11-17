package com.noir.lynx.feature.hud.autoskip

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * Ultra-low overhead, lag-free Dialogue Auto-Skip Runner.
 * Supports 3 discrete operational modes:
 * - [SkipMode.PAUSED]: Zero taps, 100% player control for combat & navigation.
 * - [SkipMode.AUTO]: Scene-Aware Auto-Skip using 2D structural dialogue disc-pair recognition.
 * - [SkipMode.MANUAL]: Continuous Fast-Burst Nonstop tapping without vision/pixel checks.
 *
 * Uses a single synchronous dedicated root pipe so synthetic taps never backlog in the shared
 * libsu queue and immediately pause whenever any overlay surface is touched or opened.
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

    companion object {
        private const val MIN_INTER_TAP_GAP_MS = 45L
        private const val EST_TAP_EXEC_MS = 95L
        private const val TOUCH_RELEASE_COOLDOWN_MS = 360L
        private const val OVERLAY_TOUCH_FAILSAFE_MS = 4500L
        private const val TAP_SENTINEL = "__LYNX_TAP_OK__"
    }

    private val modeState = MutableStateFlow(SkipMode.PAUSED)
    val currentMode = modeState.asStateFlow()

    private val activeState = MutableStateFlow(false)
    val isSkipping = activeState.asStateFlow()

    private val pausedState = MutableStateFlow(true)
    val isPaused = pausedState.asStateFlow()

    private val executingState = MutableStateFlow(false)
    val isTappingLive = executingState.asStateFlow()

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

    @Volatile
    private var overlayInteracting: Boolean = false

    @Volatile
    private var overlayTouchStartedMs: Long = 0L

    @Volatile
    private var touchResumeDeadlineMs: Long = 0L

    @Volatile
    private var tapInFlight: Boolean = false

    @Volatile
    private var lastSyntheticTapStartedMs: Long = 0L

    @Volatile
    private var lastSyntheticTapEndedMs: Long = 0L

    private val overlayRects = ConcurrentHashMap<String, GenshinSkipEngine.OverlayRect>()
    private val tapLock = Any()
    private var tapProc: Process? = null
    private var tapWriter: BufferedWriter? = null
    private var tapReader: BufferedReader? = null

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

        if (isHudOpen) {
            executingState.value = false
        }
        if (!enabled) {
            if (modeState.value != SkipMode.PAUSED) {
                setMode(SkipMode.PAUSED)
            }
        }
    }

    /** Immediately notifies the runner that the sidebar / classic HUD expanded or collapsed. */
    fun setHudOpenImmediate(open: Boolean) {
        this.isHudOpen = open
        if (open) {
            executingState.value = false
        }
    }

    /** Registers or clears a live overlay window bounds rect so synthetic taps never self-tap overlays. */
    fun updateOverlayRect(key: String, rect: GenshinSkipEngine.OverlayRect?) {
        if (rect == null || rect.right <= rect.left || rect.bottom <= rect.top) {
            overlayRects.remove(key)
        } else {
            overlayRects[key] = rect
        }
    }

    /** Pauses synthetic tap dispatch immediately while a finger interacts with any Lynx overlay. */
    fun onOverlayTouchChanged(touching: Boolean) {
        val now = SystemClock.uptimeMillis()
        if (touching) {
            overlayTouchStartedMs = now
            overlayInteracting = true
            executingState.value = false
        } else {
            overlayInteracting = false
            touchResumeDeadlineMs = now + TOUCH_RELEASE_COOLDOWN_MS
        }
    }

    /** Returns true while a synthetic tap is executing or recently completed, suppressing false outside-touch closes. */
    fun isSyntheticTapWindow(graceMs: Long = 650L): Boolean {
        val now = SystemClock.uptimeMillis()
        return tapInFlight ||
            (now - lastSyntheticTapEndedMs) in 0L..graceMs ||
            (now - lastSyntheticTapStartedMs) in 0L..(graceMs + 250L)
    }

    /** Returns true when an overlay ACTION_CANCEL closely coincides with an injected synthetic tap. */
    fun shouldRecoverCancelledTap(windowMs: Long = 280L): Boolean {
        if (modeState.value == SkipMode.PAUSED) return false
        val now = SystemClock.uptimeMillis()
        return tapInFlight ||
            (now - lastSyntheticTapEndedMs) in 0L..windowMs ||
            (now - lastSyntheticTapStartedMs) in 0L..windowMs
    }

    private fun isTouchShieldActive(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (overlayInteracting) {
            if (now - overlayTouchStartedMs > OVERLAY_TOUCH_FAILSAFE_MS) {
                overlayInteracting = false
            } else {
                return true
            }
        }
        return (touchResumeDeadlineMs - now) > 0L
    }

    private fun shouldSuppressTappingNow(): Boolean {
        val isScreenOn = powerManager?.isInteractive ?: true
        return !isScreenOn ||
            isHudOpen ||
            isTouchShieldActive() ||
            modeState.value == SkipMode.PAUSED ||
            !GenshinSkipEngine.isGameSupported(currentForegroundPackage)
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
                closeTapPipeAsync()
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

    private fun ensureTapPipe(): Boolean {
        synchronized(tapLock) {
            val existing = tapProc
            if (existing != null && existing.isAlive && tapWriter != null && tapReader != null) {
                return true
            }
            closeTapPipeLocked()
            return try {
                val suBin = com.noir.lynx.data.LynxRepository.getSuBinary()
                val proc = ProcessBuilder(suBin).redirectErrorStream(true).start()
                tapProc = proc
                tapWriter = BufferedWriter(OutputStreamWriter(proc.outputStream))
                tapReader = BufferedReader(InputStreamReader(proc.inputStream))
                true
            } catch (_: Throwable) {
                closeTapPipeLocked()
                false
            }
        }
    }

    private fun closeTapPipeLocked() {
        runCatching { tapWriter?.close() }
        runCatching { tapReader?.close() }
        runCatching { tapProc?.destroy() }
        tapWriter = null
        tapReader = null
        tapProc = null
    }

    private fun closeTapPipeAsync() {
        scope.launch(Dispatchers.IO) {
            synchronized(tapLock) { closeTapPipeLocked() }
        }
    }

    private fun executeSingleTapSync(tapBin: String, x: Int, y: Int): Long {
        val start = SystemClock.uptimeMillis()
        lastSyntheticTapStartedMs = start
        tapInFlight = true
        try {
            if (!ensureTapPipe()) return 0L
            synchronized(tapLock) {
                val writer = tapWriter ?: return 0L
                val reader = tapReader ?: return 0L
                writer.write("$tapBin $x $y >/dev/null 2>&1; echo $TAP_SENTINEL\n")
                writer.flush()
                while (true) {
                    val line = reader.readLine() ?: run {
                        closeTapPipeLocked()
                        return 0L
                    }
                    if (line.contains(TAP_SENTINEL)) break
                }
            }
        } catch (_: Throwable) {
            synchronized(tapLock) { closeTapPipeLocked() }
        } finally {
            tapInFlight = false
            lastSyntheticTapEndedMs = SystemClock.uptimeMillis()
        }
        return (lastSyntheticTapEndedMs - start).coerceAtLeast(0L)
    }

    private suspend fun readExactBytes(stream: BufferedInputStream, target: ByteArray): Int {
        var total = 0
        while (total < target.size) {
            currentCoroutineContext().ensureActive()
            val r = stream.read(target, total, target.size - total)
            if (r <= 0) break
            total += r
        }
        return total
    }

    private suspend fun skipExactBytes(stream: BufferedInputStream, bytesToSkip: Long, scratch: ByteArray): Boolean {
        var skipped = 0L
        while (skipped < bytesToSkip) {
            currentCoroutineContext().ensureActive()
            val toRead = minOf(scratch.size.toLong(), bytesToSkip - skipped).toInt()
            val r = stream.read(scratch, 0, toRead)
            if (r <= 0) return false
            skipped += r
        }
        return true
    }

    suspend fun probeScene(): GenshinSkipEngine.SceneClassification = withContext(Dispatchers.IO) {
        var proc: Process? = null
        try {
            val suBin = com.noir.lynx.data.LynxRepository.getSuBinary()
            proc = ProcessBuilder(suBin, "-c", "exec screencap").start()
            val stream = BufferedInputStream(proc.inputStream, 32768)

            val headerBuf = ByteArray(16)
            if (readExactBytes(stream, headerBuf) < 16) {
                return@withContext GenshinSkipEngine.SceneClassification(
                    GenshinSkipEngine.SceneType.UNKNOWN, 0f, 0, 0, 0f, 0f
                )
            }

            val parsedHeader = GenshinSkipEngine.parseScreencapHeader(headerBuf)
            val frameW = parsedHeader?.width ?: max(screenWidth, screenHeight).coerceAtLeast(1280)
            val frameH = parsedHeader?.height ?: min(screenWidth, screenHeight).coerceAtLeast(720)
            val isBgra = parsedHeader?.isBgra == true

            // Do not probe landscape dialogue controls while display buffer is vertical
            if (frameW < frameH) {
                return@withContext GenshinSkipEngine.SceneClassification(
                    GenshinSkipEngine.SceneType.OPEN_WORLD, 0.90f, 0, 0, 0f, 0f
                )
            }

            val bandSpec = GenshinSkipEngine.calculateDialogueHeaderBand(frameW, frameH)
            val skipBuf = ByteArray(16384)
            val initialSkip = (bandSpec.bandStartByte - 16L).coerceAtLeast(0L)
            if (!skipExactBytes(stream, initialSkip, skipBuf)) {
                return@withContext GenshinSkipEngine.SceneClassification(
                    GenshinSkipEngine.SceneType.UNKNOWN, 0f, 0, 0, 0f, 0f
                )
            }

            val bandBuf = ByteArray(bandSpec.bandBytes)
            if (readExactBytes(stream, bandBuf) < bandBuf.size) {
                return@withContext GenshinSkipEngine.SceneClassification(
                    GenshinSkipEngine.SceneType.UNKNOWN, 0f, 0, 0, 0f, 0f
                )
            }

            val bandMetrics = GenshinSkipEngine.evaluateDialogueHeaderBand(bandBuf, bandSpec, isBgra = isBgra)
            if (bandMetrics.isConfirmedDialogue) {
                return@withContext GenshinSkipEngine.SceneClassification(
                    GenshinSkipEngine.SceneType.STANDARD_DIALOGUE,
                    0.99f,
                    bandMetrics.peakDiscCreamMatches,
                    0,
                    bandMetrics.blackRatio,
                    bandMetrics.whiteRatio
                )
            }

            if (bandMetrics.whiteRatio > 0.85f) {
                return@withContext GenshinSkipEngine.SceneClassification(
                    GenshinSkipEngine.SceneType.WHITE_LOADING,
                    0.99f,
                    bandMetrics.peakDiscCreamMatches,
                    0,
                    bandMetrics.blackRatio,
                    bandMetrics.whiteRatio
                )
            }

            if (bandMetrics.blackRatio < 0.85f) {
                return@withContext GenshinSkipEngine.SceneClassification(
                    GenshinSkipEngine.SceneType.OPEN_WORLD,
                    0.95f,
                    bandMetrics.peakDiscCreamMatches,
                    0,
                    bandMetrics.blackRatio,
                    bandMetrics.whiteRatio
                )
            }

            // Deep Probe: Pitch black upper header detected (blackRatio >= 85%).
            // Advance forward down the screencap stream to bottom narration prompt band.
            val (promptHead, promptSliceBytes) = GenshinSkipEngine.calculatePromptScanSlice(frameW, frameH)
            val promptStart = promptHead - promptSliceBytes
            val gapToPrompt = (promptStart - bandSpec.bandEndByte).coerceAtLeast(0L)
            if (!skipExactBytes(stream, gapToPrompt, skipBuf)) {
                return@withContext GenshinSkipEngine.SceneClassification(
                    GenshinSkipEngine.SceneType.OPEN_WORLD, 0.80f, bandMetrics.peakDiscCreamMatches, 0, bandMetrics.blackRatio, bandMetrics.whiteRatio
                )
            }

            val promptSlice = ByteArray(promptSliceBytes.toInt())
            if (readExactBytes(stream, promptSlice) < promptSlice.size) {
                return@withContext GenshinSkipEngine.SceneClassification(
                    GenshinSkipEngine.SceneType.OPEN_WORLD, 0.80f, bandMetrics.peakDiscCreamMatches, 0, bandMetrics.blackRatio, bandMetrics.whiteRatio
                )
            }

            val metrics = GenshinSkipEngine.evaluatePromptSliceWithMargins(promptSlice, frameW, frameH, isBgra = isBgra)
            return@withContext if (metrics.isConfirmedNarration) {
                GenshinSkipEngine.SceneClassification(
                    GenshinSkipEngine.SceneType.BLACK_INTERSTITIAL,
                    0.99f,
                    bandMetrics.peakDiscCreamMatches,
                    metrics.goldMatches,
                    bandMetrics.blackRatio,
                    bandMetrics.whiteRatio
                )
            } else {
                GenshinSkipEngine.SceneClassification(
                    GenshinSkipEngine.SceneType.OPEN_WORLD,
                    0.90f,
                    bandMetrics.peakDiscCreamMatches,
                    metrics.goldMatches,
                    bandMetrics.blackRatio,
                    bandMetrics.whiteRatio
                )
            }
        } catch (ce: CancellationException) {
            throw ce
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

    /**
     * Waits up to [durationMs] while aborting promptly (<=25ms) if an overlay touch, HUD open,
     * or PAUSED state transition interrupts tapping.
     */
    private suspend fun awaitWithTouchShield(durationMs: Long): Boolean {
        var remaining = durationMs.coerceAtLeast(0L)
        while (remaining > 0L && currentCoroutineContext().isActive) {
            if (shouldSuppressTappingNow()) return false
            val slice = min(25L, remaining)
            delay(slice)
            remaining -= slice
        }
        return !shouldSuppressTappingNow() && currentCoroutineContext().isActive
    }

    private suspend fun runTapLoop() {
        val maxContinuousManualMs = 60_000L // 60s auto-disarm safety shield for manual non-stop mode
        val manualStartTime = System.currentTimeMillis()
        var cycleIndex = 0
        var consecutiveInactiveCount = 0
        var burstCountRemaining = 0
        var activeSceneType = GenshinSkipEngine.SceneType.UNKNOWN
        val tapBin = if (Build.VERSION.SDK_INT >= 29) "cmd input tap" else "input tap"

        try {
            while (modeState.value != SkipMode.PAUSED && currentCoroutineContext().isActive) {
                if (shouldSuppressTappingNow()) {
                    executingState.value = false
                    burstCountRemaining = 0
                    delay(if (isTouchShieldActive()) 60L else 250L)
                    continue
                }

                val current = modeState.value

                // Safety timeout: automatically stop tapping after 60s in MANUAL mode to protect open-world gameplay
                if (current == SkipMode.MANUAL && (System.currentTimeMillis() - manualStartTime > maxContinuousManualMs)) {
                    setMode(SkipMode.PAUSED)
                    break
                }

                val effSpeed = speedMs.coerceAtLeast(45L)
                val estCycleMs = max(effSpeed, EST_TAP_EXEC_MS + MIN_INTER_TAP_GAP_MS)

                if (current == SkipMode.AUTO) {
                    if (burstCountRemaining <= 0) {
                        val scene = probeScene()
                        if (shouldSuppressTappingNow()) {
                            executingState.value = false
                            continue
                        }
                        activeSceneType = scene.sceneType
                        when (scene.sceneType) {
                            GenshinSkipEngine.SceneType.STANDARD_DIALOGUE -> {
                                burstCountRemaining = (680L / estCycleMs).toInt().coerceIn(4, 10)
                                consecutiveInactiveCount = 0
                                executingState.value = true
                            }
                            GenshinSkipEngine.SceneType.BLACK_INTERSTITIAL -> {
                                burstCountRemaining = (520L / estCycleMs).toInt().coerceIn(3, 6)
                                consecutiveInactiveCount = 0
                                executingState.value = true
                            }
                            else -> {
                                burstCountRemaining = 0
                                executingState.value = false
                                consecutiveInactiveCount++
                                val standbyDelay = if (consecutiveInactiveCount > 3) 1050L else 650L
                                awaitWithTouchShield(standbyDelay)
                                continue
                            }
                        }
                    }
                    burstCountRemaining--
                } else if (current == SkipMode.MANUAL) {
                    executingState.value = true
                }

                // Double-check touch/HUD shield right before synchronous tap execution
                if (shouldSuppressTappingNow()) {
                    executingState.value = false
                    burstCountRemaining = 0
                    continue
                }

                val rawCoords = GenshinSkipEngine.resolveCoordinates(screenWidth, screenHeight)
                val choiceTarget = if (targetChoiceBottom) {
                    when (cycleIndex % 4) {
                        0 -> rawCoords.choice4
                        1 -> rawCoords.choice3
                        2 -> rawCoords.choice2
                        else -> rawCoords.choice1
                    }
                } else {
                    when (cycleIndex % 4) {
                        0 -> rawCoords.choice2
                        1 -> rawCoords.choice1
                        2 -> rawCoords.choice3
                        else -> rawCoords.choice4
                    }
                }

                val rawTarget = if (activeSceneType == GenshinSkipEngine.SceneType.BLACK_INTERSTITIAL) {
                    rawCoords.narration
                } else {
                    when (cycleIndex % 3) {
                        0 -> rawCoords.advance
                        1 -> choiceTarget
                        else -> rawCoords.narration
                    }
                }

                val activeOverlays = overlayRects.values.toList()
                val padPx = max(24, (min(screenWidth, screenHeight) * 28) / 1080)
                val safeTarget = GenshinSkipEngine.resolveTapAvoidingOverlays(
                    rawTarget, activeOverlays, screenWidth, screenHeight, padPx
                )

                val elapsedMs = executeSingleTapSync(tapBin, safeTarget.x, safeTarget.y)
                cycleIndex++

                // Always leave a genuine inter-tap quiet breather window so human overlay touches never starve
                val breatherMs = max(effSpeed - elapsedMs, MIN_INTER_TAP_GAP_MS)
                awaitWithTouchShield(breatherMs)
            }
        } finally {
            executingState.value = false
            closeTapPipeAsync()
            if (modeState.value != SkipMode.PAUSED && !currentCoroutineContext().isActive) {
                modeState.value = SkipMode.PAUSED
                activeState.value = false
                pausedState.value = true
            }
        }

        if (modeState.value != SkipMode.PAUSED) {
            setMode(SkipMode.PAUSED)
        }
    }
}
