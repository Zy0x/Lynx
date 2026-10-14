package com.noir.lynx.feature.network.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.designsystem.controls.SafeSlider as Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.*
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.designsystem.card.*
import com.noir.lynx.designsystem.controls.*
import com.noir.lynx.designsystem.tweak.*
import com.noir.lynx.designsystem.profile.*

@Composable
fun SoundControlCard(
    soundControl: SoundControlInfo,
    onSetGain: (Int, Int, Int, Int, Boolean) -> Unit,
) {
    var hpL by remember { mutableIntStateOf(soundControl.headphoneGainL) }
    var hpR by remember { mutableIntStateOf(soundControl.headphoneGainR) }
    var spk by remember { mutableIntStateOf(soundControl.speakerGain) }
    var mic by remember { mutableIntStateOf(soundControl.micGain) }
    var hpMode by remember { mutableStateOf(soundControl.highPerfMode) }
    var lastSoundTouch by remember { mutableLongStateOf(0L) }

    LaunchedEffect(soundControl) {
        if (System.currentTimeMillis() - lastSoundTouch > 3500L) {
            hpL = soundControl.headphoneGainL
            hpR = soundControl.headphoneGainR
            spk = soundControl.speakerGain
            mic = soundControl.micGain
            hpMode = soundControl.highPerfMode
        }
    }

    LynxCard(
        title = "Sound Control (Audio Gain Booster)",
        icon = Icons.Default.VolumeUp,
        accentColor = if (soundControl.isSupported) AccentGreen else AccentOrange
    ) {
        if (!soundControl.isSupported) {
            UnsupportedBadge(reason = soundControl.unsupportedReason)
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Headphone Gain (L/R): ${hpL}dB / ${hpR}dB", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Slider(value = hpL.toFloat(), onValueChange = { hpL = it.toInt(); hpR = it.toInt(); lastSoundTouch = System.currentTimeMillis() }, valueRange = -10f..20f, colors = SliderDefaults.colors(thumbColor = AccentGreen, activeTrackColor = AccentGreen))

                Text("Speaker Gain: ${spk}dB", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Slider(value = spk.toFloat(), onValueChange = { spk = it.toInt(); lastSoundTouch = System.currentTimeMillis() }, valueRange = -10f..20f, colors = SliderDefaults.colors(thumbColor = AccentGreen, activeTrackColor = AccentGreen))

                Text("Microphone Gain: ${mic}dB", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Slider(value = mic.toFloat(), onValueChange = { mic = it.toInt(); lastSoundTouch = System.currentTimeMillis() }, valueRange = -10f..20f, colors = SliderDefaults.colors(thumbColor = AccentGreen, activeTrackColor = AccentGreen))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("High Performance Audio DAC", color = TextSecondary, fontSize = 12.sp)
                    Switch(
                        checked = hpMode,
                        onCheckedChange = {
                            lastSoundTouch = System.currentTimeMillis()
                            hpMode = it
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = BgDeepOled, checkedTrackColor = AccentGreen)
                    )
                }

                LynxActionButton(
                    text = "Simpan Pengaturan Gain Audio",
                    icon = Icons.Default.Save,
                    onClick = {
                        lastSoundTouch = System.currentTimeMillis()
                        onSetGain(hpL, hpR, spk, mic, hpMode)
                    },
                    accentColor = AccentGreen
                )
            }
        }
    }
}
