package com.noir.lynx.feature.hud.ui

import android.os.SystemClock
import androidx.compose.runtime.*
import com.noir.lynx.feature.hud.HudActionState
import com.noir.lynx.feature.hud.addins.HudGamingAddins
import com.noir.lynx.feature.hud.addins.HudRecordingPhase
import com.noir.lynx.feature.hud.data.*
import kotlinx.coroutines.delay
import java.util.Locale

/** Frequent game actions stay together; configuration and application management live in Tools. */
@Composable
internal fun HudHomePage(
    snapshot: HudSnapshot, action: HudActionState, prefs: HudPreferences, store: HudPreferencesStore,
    focusActive: Boolean, onProfile: (String) -> Unit, onDetails: () -> Unit,
    onCapture: () -> Unit, onBoostRam: () -> Unit, onToggleFocus: () -> Unit, onClose: () -> Unit
) {
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
        HudQuickTileRow(
            pinned = prefs.pinned, focusActive = focusActive, busy = action.busy,
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
