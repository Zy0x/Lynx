package com.noir.lynx.feature.hud.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.addins.*

@Composable
internal fun HudRecorderMasterCard(context: Context) {
    val shared = HudGamingAddins.current?.recorder
    val recorder = remember(context, shared) { shared ?: HudScreenRecorder(context) }
    DisposableEffect(recorder) { onDispose { if (shared == null) recorder.close() } }
    HudRecorderControls(recorder)
}

/** Shared settings for Companion and the bounded sidebar; no unimplemented target-FPS control. */
@Composable
internal fun HudRecorderControls(provided: HudScreenRecorder? = null) {
    val recorder = provided ?: HudGamingAddins.current?.recorder ?: return
    val state by recorder.state.collectAsState()
    val quality by recorder.quality.collectAsState()
    val audio by recorder.audioSource.collectAsState()
    val bitrate by recorder.bitrate.collectAsState()
    val compatibility by recorder.compatibility.collectAsState()
    val stealth by recorder.stealthOverlay.collectAsState()
    val recordInfo = "${hudString(R.string.hud_record_audio_note)}\n\n" +
    "• ${hudString(R.string.hud_record_fps_note)}\n\n" +
    "• ${hudString(R.string.hud_record_compatibility_note)}\n\n" +
    "• ${hudString(R.string.hud_record_storage)}"

    HudSubpages(listOf(R.string.hud_detail_controls, R.string.hud_detail_video, R.string.hud_record_audio, R.string.hud_detail_advanced)) { section ->
        HudSection(
            title = hudString(R.string.hud_record_title),
            badge = hudString(when (state.phase) {
                HudRecordingPhase.CHECKING -> R.string.hud_cap_checking
                HudRecordingPhase.UNSUPPORTED -> R.string.hud_cap_unsupported
                HudRecordingPhase.FAILED -> R.string.hud_record_failed
                HudRecordingPhase.SAVED -> R.string.hud_record_saved
                else -> R.string.hud_cap_ready
            }),
            info = recordInfo
        ) {
            if (section == 0) {
                recorder.outputSize()?.let { Text(hudString(R.string.hud_record_resolution, it.first, it.second), fontSize = 12.sp, color = HudBlue) }
                HudAction(hudString(if (state.busy) R.string.hud_record_stop else R.string.hud_record_start),
                    enabled = state.phase !in listOf(HudRecordingPhase.CHECKING, HudRecordingPhase.UNSUPPORTED),
                    onClick = { if (state.busy) recorder.stop() else recorder.start() }, modifier = Modifier.fillMaxWidth(), isDanger = state.busy)
                if (state.phase == HudRecordingPhase.FAILED) Text(hudString(R.string.hud_record_error_help), fontSize = 12.sp, color = HudOrange)
                if (state.lastUri != null && !state.busy) HudAction(hudString(R.string.hud_record_open), onClick = recorder::openLast, modifier = Modifier.fillMaxWidth())
            }
            if (section == 1) {
                val qualities = listOf(hudString(R.string.hud_record_light), hudString(R.string.hud_record_balanced), hudString(R.string.hud_record_native))
                HudAdaptiveGrid(qualities, maximum = 2) { i -> HudChoice(qualities[i], quality == i, { recorder.setQuality(i) }, Modifier.weight(1f), enabled = !state.busy) }
                Text(hudString(R.string.hud_record_bitrate), fontSize = 12.sp, color = HudMuted)
                val rates = listOf(4, 8, 16, 24).map { hudString(R.string.hud_record_mbps, it) }
                HudAdaptiveGrid(rates, maximum = 2) { i -> HudChoice(rates[i], bitrate == i, { recorder.setBitrate(i) }, Modifier.weight(1f), enabled = !state.busy && !compatibility) }
            }
            if (section == 2) {
                Text(hudString(R.string.hud_record_audio), fontSize = 12.sp, color = HudMuted)
                val sources = listOf(R.string.hud_audio_off, R.string.hud_audio_mic, R.string.hud_audio_internal, R.string.hud_audio_mix).map { hudString(it) }
                HudAdaptiveGrid(sources, maximum = 2) { i -> HudChoice(sources[i], audio == i, { recorder.setAudioSource(i) }, Modifier.weight(1f), enabled = !state.busy) }
                Text(hudString(R.string.hud_record_audio_note), fontSize = 12.sp, color = HudMuted)
            }
            if (section == 3) {
                HudPreferenceToggle(hudString(R.string.hud_record_compatibility), if (state.busy) compatibility || state.software else compatibility) {
                    if (!state.busy) recorder.setCompatibility(it)
                }
                HudPreferenceToggle(hudString(R.string.hud_record_stealth), stealth, recorder::setStealthOverlay)
                Text(hudString(R.string.hud_record_compatibility_note), fontSize = 12.sp, color = HudMuted)
            }
        }
    }
}
