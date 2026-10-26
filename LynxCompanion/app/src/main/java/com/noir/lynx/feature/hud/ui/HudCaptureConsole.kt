package com.noir.lynx.feature.hud.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.addins.*
import com.noir.lynx.feature.hud.data.*
import kotlinx.coroutines.delay
import java.util.Locale

private fun sessionTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
}

@Composable
internal fun HudRecordingBar() {
    val recorder = HudGamingAddins.current?.recorder ?: return
    val state by recorder.state.collectAsState()
    if (!state.busy) return
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.phase) { while (true) { now = SystemClock.elapsedRealtime(); delay(1000) } }
    Surface(
        color = HudRed.copy(alpha = .14f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, HudRed.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(HudRed, CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            val label = when(state.phase) {
                HudRecordingPhase.COUNTDOWN -> hudString(R.string.hud_record_countdown, state.countdown)
                HudRecordingPhase.SAVING -> hudString(R.string.hud_record_saving)
                else -> hudString(R.string.hud_record_elapsed, sessionTime(now - state.startedAt))
            }
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = HudRed, modifier = Modifier.weight(1f))
            IconButton(onClick = recorder::stop, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.StopCircle, hudString(R.string.hud_record_stop), tint = HudRed, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
internal fun HudRecorderControls() {
    val recorder = HudGamingAddins.current?.recorder ?: return
    val state by recorder.state.collectAsState()
    val quality by recorder.quality.collectAsState()
    val compatibility by recorder.compatibility.collectAsState()
    HudSection(hudString(R.string.hud_record_title), badge = hudString(when(state.phase) {
        HudRecordingPhase.CHECKING -> R.string.hud_cap_checking
        HudRecordingPhase.UNSUPPORTED -> R.string.hud_cap_unsupported
        HudRecordingPhase.FAILED -> R.string.hud_record_failed
        HudRecordingPhase.SAVED -> R.string.hud_record_saved
        else -> R.string.hud_cap_ready
    })) {
        Text(hudString(R.string.hud_record_description), fontSize = 12.sp, color = HudMuted)
        val labels = listOf(hudString(R.string.hud_record_light), hudString(R.string.hud_record_balanced), hudString(R.string.hud_record_native))
        HudAdaptiveGrid(labels, maximum = 3) { index ->
            HudChoice(labels[index], quality == index, { recorder.setQuality(index) }, modifier = Modifier.weight(1f), enabled = !state.busy)
        }
        recorder.outputSize()?.let { size -> Text(hudString(R.string.hud_record_resolution, size.first, size.second), fontSize = 12.sp, color = HudBlue) }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(hudString(R.string.hud_record_compatibility), fontSize = 12.sp, color = Color.White, modifier = Modifier.weight(1f))
            Switch(if (state.busy) compatibility || state.software else compatibility, recorder::setCompatibility, enabled = !state.busy,
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = HudBlueBadge))
        }
        HudAction(hudString(if (state.busy) R.string.hud_record_stop else R.string.hud_record_start),
            enabled = state.phase !in listOf(HudRecordingPhase.CHECKING, HudRecordingPhase.UNSUPPORTED),
            onClick = { if (state.busy) recorder.stop() else recorder.start() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), isDanger = state.busy)
        if (state.phase == HudRecordingPhase.FAILED) Text(hudString(R.string.hud_record_error_help), fontSize = 12.sp, color = HudOrange)
        if (state.lastUri != null && !state.busy) HudAction(hudString(R.string.hud_record_open), onClick = recorder::openLast, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
        HudExpandableSection(hudString(R.string.hud_record_details)) {
            Text(hudString(R.string.hud_record_compatibility_note), fontSize = 12.sp, color = HudMuted)
            Text(hudString(R.string.hud_record_storage), fontSize = 12.sp, color = HudMuted)
        }
    }
}

@Composable
internal fun HudConsoleControls(brightnessLocked: Boolean, onBrightness: (Boolean) -> Unit) {
    val runtime = HudGamingAddins.current ?: return
    val state by runtime.console.state.collectAsState()
    val root by runtime.rootAvailable.collectAsState()
    val volumeLabel = hudString(R.string.hud_console_volume)
    LaunchedEffect(runtime) { while (true) { runtime.console.refreshVolume(); delay(1000) } }
    HudSection(hudString(R.string.hud_console_title)) {
        Text(if (state.maxVolume > 0) hudString(R.string.hud_console_volume_value, state.volume, state.maxVolume) else "—", fontSize = 12.sp, color = HudBlue)
        if (state.maxVolume > 0) Slider(state.volume.toFloat(), { runtime.console.setVolume(it.toInt()) },
            valueRange = 0f..state.maxVolume.toFloat(), enabled = !state.fixedVolume, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = volumeLabel })
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(hudString(R.string.hud_brightness_lock_title), fontSize = 12.sp, color = Color.White, modifier = Modifier.weight(1f))
            Switch(brightnessLocked, onBrightness, enabled = root, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = HudBlueBadge))
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(hudString(R.string.hud_console_awake), fontSize = 12.sp, color = Color.White, modifier = Modifier.weight(1f))
            Switch(state.awakeRequested, runtime::setAwake, enabled = root, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = HudBlueBadge))
        }
        Text(hudString(R.string.hud_console_restore_note), fontSize = 12.sp, color = HudMuted)
    }
}

@Composable
internal fun HudSessionControls(snapshot: HudSnapshot) {
    val runtime = HudGamingAddins.current ?: return
    val start by runtime.batteryStart.collectAsState()
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(runtime) { while (true) { elapsed = SystemClock.elapsedRealtime() - runtime.startedAt; delay(1000) } }
    HudSection(hudString(R.string.hud_session_title)) {
        HudSessionLine(hudString(R.string.hud_session_duration), sessionTime(elapsed))
        val current = snapshot[HudMetric.BATTERY_LEVEL]
        HudSessionLine(hudString(R.string.hud_session_battery_start), start?.let { "$it%" } ?: "—")
        HudSessionLine(hudString(R.string.hud_metric_battery), if (current.status == "valid") "${current.text()}%" else "—")
        HudSessionLine(hudString(R.string.hud_session_package), snapshot.packageName.ifBlank { "—" })
        Text(hudString(R.string.hud_session_note), fontSize = 12.sp, color = HudMuted)
    }
}

@Composable
private fun HudSessionLine(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, fontSize = 12.sp, color = HudMuted)
        Text(value, fontSize = 14.sp, color = Color.White)
    }
}
