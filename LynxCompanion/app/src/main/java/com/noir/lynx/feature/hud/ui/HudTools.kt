package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import com.noir.lynx.feature.hud.autoskip.GenshinSkipEngine
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.HudActionState
import com.noir.lynx.feature.hud.data.*
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
internal fun HudTools(
    prefs: HudPreferences,
    store: HudPreferencesStore,
    rates: List<Float>,
    activeHz: HudReading,
    action: HudActionState,
    onHz: (Float) -> Unit,
    onCapture: () -> Unit,
    onStop: () -> Unit,
    onBoostRam: () -> Unit,
    brightnessLocked: Boolean = false,
    onToggleBrightnessLock: (Boolean) -> Unit = {},
    onToggleAutoSkip: (Boolean) -> Unit = { store.setAutoSkipEnabled(it) },
    onSetAutoSkipSpeed: (Long) -> Unit = { store.setAutoSkipSpeed(it) },
    onSetAutoSkipChoiceBottom: (Boolean) -> Unit = { store.setAutoSkipChoiceBottom(it) },
    isSkipping: Boolean = false,
    isPaused: Boolean = false,
    isTappingLive: Boolean = false,
    onSnapshotScene: () -> Unit = {},
    sceneResult: GenshinSkipEngine.SceneSnapshotResult? = null
) {
    var timerRemainingSec by rememberSaveable { mutableStateOf(0) }
    var timerActivePreset by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(timerRemainingSec > 0) {
        while (timerRemainingSec > 0) {
            delay(1_000L)
            timerRemainingSec = (timerRemainingSec - 1).coerceAtLeast(0)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HudExpandableSection(
            title = hudString(R.string.hud_refresh),
            detail = hudString(R.string.hud_value, activeHz.text(), activeHz.unit),
            initiallyOpen = true,
            info = "Pengaturan Refresh Rate Layar:\n\nPilih kecepatan refresh rate panel (Hz) yang diinginkan secara langsung saat di dalam game untuk menghemat baterai atau memaksimalkan kelancaran grafis."
        ) {
            action.requestedHz?.let {
                Text(
                    text = hudString(
                        R.string.hud_requested_hz,
                        HudReading(it.toDouble(), "Hz", status = "valid").text()
                    ),
                    fontSize = 11.5.sp,
                    color = HudCyan
                )
            }
            if (rates.isEmpty()) {
                Text(
                    text = hudString(R.string.hud_unsupported),
                    color = HudMuted,
                    fontSize = 12.sp
                )
            } else {
                val labels = rates.map { hz ->
                    hudString(R.string.hud_value, HudReading(hz.toDouble(), "Hz", status = "valid").text(), "Hz")
                }
                HudAdaptiveGrid(labels, maximum = 4) { i ->
                    val isSelected = activeHz.status == "valid" && activeHz.value?.let { abs(it - rates[i]) < 0.05 } == true
                    HudChoice(
                        label = labels[i],
                        selected = isSelected,
                        onClick = { onHz(rates[i]) },
                        modifier = Modifier.weight(1f),
                        enabled = !action.busy
                    )
                }
            }
        }

        HudExpandableSection(
            title = hudString(R.string.hud_mini_title),
            detail = hudString(R.string.hud_selected_count, prefs.selected.size),
            initiallyOpen = false
        ) {
            HudMetricSelection(prefs, store, showPin = false)
        }

        HudExpandableSection(
            title = hudString(R.string.hud_floating_short),
            detail = hudString(
                if (prefs.floatingEngine == "standalone") R.string.floating_engine_root else R.string.floating_engine_rom
            ),
            initiallyOpen = false
        ) {
            HudFloatingSettings(prefs, store)
        }

        // Game Brightness Lock
        HudExpandableSection(
            title = hudString(R.string.hud_brightness_lock_title),
            detail = hudString(if (brightnessLocked) R.string.hud_active else R.string.hud_skip_off)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = hudString(R.string.hud_brightness_lock_desc),
                    fontSize = 11.5.sp,
                    color = HudMuted,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = brightnessLocked,
                    onCheckedChange = { onToggleBrightnessLock(it) },
                    enabled = !action.busy,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = HudBlueBadge,
                        uncheckedThumbColor = HudMuted,
                        uncheckedTrackColor = HudElevated
                    )
                )
            }
        }

        HudAutomationControls(
            prefs = prefs,
            busy = action.busy,
            isSkipping = isSkipping,
            isPaused = isPaused,
            isTappingLive = isSkipping && !isPaused,
            onEnabled = onToggleAutoSkip,
            onSpeed = onSetAutoSkipSpeed,
            onChoice = onSetAutoSkipChoiceBottom
        )

        // Magic Box · Cooldown Timer
        val mins = timerRemainingSec / 60
        val secs = timerRemainingSec % 60
        val timerBadge = if (timerRemainingSec > 0) {
            String.format("%02d:%02d", mins, secs)
        } else {
            hudString(R.string.hud_timer_ready)
        }
        HudExpandableSection(
            title = hudString(R.string.hud_timer_title),
            detail = timerBadge,
            info = hudString(R.string.hud_timer_desc)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = String.format("%02d:%02d", mins, secs),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (timerRemainingSec > 0) HudCyan else Color.White
                )
                HudChoice(
                    label = hudString(R.string.hud_timer_reset),
                    selected = false,
                    onClick = {
                        timerActivePreset = 0
                        timerRemainingSec = 0
                    },
                    modifier = Modifier.width(82.dp),
                    enabled = timerRemainingSec > 0
                )
            }

            val presets = listOf(
                30 to "30s",
                60 to "60s",
                120 to "2m",
                180 to "3m"
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presets.forEach { (sec, label) ->
                    HudChoice(
                        label = label,
                        selected = timerRemainingSec > 0 && timerActivePreset == sec,
                        onClick = {
                            timerActivePreset = sec
                            timerRemainingSec = sec
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        HudExpandableSection(
            title = "Hoyo Auto-Skip & Diagnosa",
            detail = if (isPaused) "Paused" else if (isTappingLive) "Skipping" else "Siaga (Standby)",
            initiallyOpen = false,
            info = "Fitur Hoyo RPG Auto-Skip & Diagnosa Scene:\n\nAuto-Skip mendeteksi dialog game secara cerdas. Jika Anda menemukan scene game baru yang tidak terskip atau salah tekan, gunakan tombol di bawah untuk mengambil snapshot diagnosa ke /sdcard/Lynx/scenes/."
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Aktifkan Hoyo Auto-Skip",
                    fontSize = 13.sp,
                    color = Color.White
                )
                Switch(
                    checked = prefs.autoSkipEnabled,
                    onCheckedChange = onToggleAutoSkip,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = HudCyan,
                        checkedTrackColor = HudCyan.copy(alpha = 0.5f)
                    )
                )
            }

            if (prefs.autoSkipEnabled) {
                Spacer(modifier = Modifier.height(6.dp))
                HudAction(
                    label = "📸 Snapshot Diagnosa Scene",
                    onClick = onSnapshotScene,
                    modifier = Modifier.fillMaxWidth()
                )

                if (sceneResult != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = HudBackground,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, HudCyan.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val isDialog = sceneResult.telemetry.dialogueDeclared
                            val verdictBg = if (isDialog) Color(0x2A43E5C1) else Color(0x2AFBBF24)
                            val verdictBorder = if (isDialog) HudCyan.copy(alpha = 0.5f) else HudOrange.copy(alpha = 0.5f)
                            val verdictColor = if (isDialog) HudCyan else HudOrange
                            Surface(
                                color = verdictBg,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(0.75.dp, verdictBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isDialog) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                                        contentDescription = null,
                                        tint = verdictColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (isDialog) hudString(R.string.hud_autoskip_scene_detected)
                                               else hudString(R.string.hud_autoskip_scene_non_dialog),
                                        color = verdictColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Text(
                                text = hudString(
                                    R.string.hud_autoskip_scene_pixels,
                                    sceneResult.telemetry.matchingPixels,
                                    sceneResult.telemetry.thresholdRequired
                                ),
                                color = Color(0xFFE2E8F0),
                                fontSize = 11.sp
                            )
                            Text(
                                text = hudString(
                                    R.string.hud_autoskip_scene_zone,
                                    sceneResult.telemetry.xMin,
                                    sceneResult.telemetry.xMax,
                                    sceneResult.telemetry.yRow
                                ),
                                color = HudMuted,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "${sceneResult.baseName}_full.png",
                                color = HudMuted.copy(alpha = 0.7f),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        HudAction(
            label = hudString(R.string.hud_stop),
            onClick = onStop,
            modifier = Modifier.fillMaxWidth(),
            isDanger = true
        )
    }
}
