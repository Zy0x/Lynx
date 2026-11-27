package com.noir.lynx.feature.hud.ui

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.data.LynxBenchmarkResult
import com.noir.lynx.data.LynxRepository
import com.noir.lynx.feature.benchmark.BenchmarkSessionManager
import com.noir.lynx.feature.hud.HudActionState
import com.noir.lynx.feature.hud.addins.HudGamingAddins
import com.noir.lynx.feature.hud.addins.HudRecordingPhase
import com.noir.lynx.feature.hud.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Frequent game actions stay together; configuration and application management live in Tools. */
@Composable
internal fun HudHomePage(
    landscape: Boolean, snapshot: HudSnapshot, action: HudActionState, prefs: HudPreferences, store: HudPreferencesStore,
    focusActive: Boolean, onProfile: (String) -> Unit, onDetails: () -> Unit,
    onCapture: () -> Unit, onBoostRam: () -> Unit, onToggleFocus: () -> Unit, onClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isHudBenchmarking by remember { mutableStateOf(false) }
    var hudBenchmarkCountdown by remember { mutableIntStateOf(60) }
    var hudBenchmarkResult by remember { mutableStateOf<LynxBenchmarkResult?>(null) }

    val recorder = HudGamingAddins.current?.recorder
    val recording by (recorder?.state?.collectAsState() ?: remember { mutableStateOf(null) })
    val isRecording = recording?.busy == true
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(isRecording) {
        while (isRecording) {
            now = SystemClock.elapsedRealtime()
            delay(1000)
        }
    }
    val duration = recording?.takeIf { isRecording }?.let {
        if (it.phase == HudRecordingPhase.COUNTDOWN) "${it.countdown}s"
        else {
            val seconds = ((now - it.startedAt).coerceAtLeast(0) / 1000)
            String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
        }
    }

    HudSummaryContent(snapshot, action, onProfile, onDetails) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // ── In-Overlay Live Benchmark 60s Runner ────────────────────────
            HudOverlayBenchmarkBar(
                isBenchmarking = isHudBenchmarking,
                countdown = hudBenchmarkCountdown,
                summary = hudBenchmarkResult,
                onStart = {
                    if (isHudBenchmarking) return@HudOverlayBenchmarkBar
                    coroutineScope.launch {
                        isHudBenchmarking = true
                        hudBenchmarkCountdown = 60
                        val res = withContext(Dispatchers.IO) {
                            LynxRepository.runHardwareBenchmark(
                                durationSeconds = 60,
                                targetPackage = snapshot.packageName.ifBlank { null },
                                onProgress = { remaining -> hudBenchmarkCountdown = remaining }
                            )
                        }
                        isHudBenchmarking = false
                        hudBenchmarkResult = res
                        if (res != null) {
                            BenchmarkSessionManager.saveSession(context, res)
                        }
                    }
                }
            )

            // ── Quick Tool Icon Tiles ───────────────────────────────────────
            HudQuickTileRow(
                landscape = landscape, pinned = prefs.pinned, focusActive = focusActive, busy = action.busy,
                isRecording = isRecording, recordDuration = duration, onCapture = onCapture,
                onToggleRecord = {
                    if (isRecording) recorder?.stop()
                    else { onClose(); recorder?.start() }
                },
                onBoostRam = onBoostRam, onTogglePin = { store.pin(!prefs.pinned) },
                onToggleFocus = onToggleFocus, autoSkipEnabled = prefs.autoSkipEnabled,
                onToggleAutoSkip = { store.setAutoSkipEnabled(!prefs.autoSkipEnabled) }
            )
        }
    }
}

@Composable
private fun HudOverlayBenchmarkBar(
    isBenchmarking: Boolean,
    countdown: Int,
    summary: LynxBenchmarkResult?,
    onStart: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        onClick = {
            if (!isBenchmarking) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onStart()
            }
        },
        shape = RoundedCornerShape(10.dp),
        color = if (isBenchmarking) Color(0xFFE040FB).copy(alpha = 0.22f) else HudElevated,
        border = BorderStroke(1.dp, if (isBenchmarking) Color(0xFFE040FB) else HudBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = if (isBenchmarking) Color(0xFFE040FB) else HudCyan,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (isBenchmarking) "Merekam Frame Pacing... ${countdown}s" else "Mulai Benchmark 60s In-Game",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isBenchmarking) Color(0xFFE040FB) else Color.White
                )
            }

            if (summary != null && !isBenchmarking) {
                val stabPct = (100f - summary.jankyFramesPercent).coerceIn(0f, 100f).toInt()
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF00E676).copy(alpha = 0.16f),
                    border = BorderStroke(0.6.dp, Color(0xFF00E676).copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "Stab $stabPct% · 1%L ${summary.fps1PercentLow.toInt()}",
                        color = Color(0xFF00E676),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
                    )
                }
            } else if (!isBenchmarking) {
                Text(
                    text = "Uji 60s ➔",
                    color = HudMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

