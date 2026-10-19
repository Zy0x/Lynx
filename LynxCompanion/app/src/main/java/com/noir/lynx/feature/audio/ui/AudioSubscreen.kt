package com.noir.lynx.feature.audio.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.controls.LynxSwitch
import com.noir.lynx.designsystem.controls.SafeSlider as Slider
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.ui.*

/**
 * Subscreen: Sound & Audio Hardware Subsystem
 * Completely separated tab dedicated to low-latency audio, MMAP bypass, and DAC gain control.
 */
@Composable
fun AudioSubscreen(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val state = uiState.state
    val audioTelem = uiState.audioTelemetry
    val soundCtrl = uiState.soundControl

    LaunchedEffect(Unit) {
        viewModel.refreshAudioSubsystem()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── 1. Live Audio Subsystem & Hardware Monitor Card ─────
        val isMmap = state.audio.lowLatencyMmap
        LynxCard(
            title = "Live Audio Subsystem & Output Monitor",
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            accentColor = AccentCyan
        ) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        audioTelem.activeDevice,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${audioTelem.sampleRate} • ${audioTelem.serverStatus}",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isMmap) AccentCyan.copy(alpha = 0.15f) else BgElevated,
                    border = BorderStroke(1.dp, if (isMmap) AccentCyan.copy(alpha = 0.5f) else BorderGlass)
                ) {
                    Text(
                        if (isMmap) "● MMAP EXCLUSIVE" else "○ MIXER STANDARD",
                        color = if (isMmap) AccentCyan else TextSecondary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderGlass),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Output Hardware", color = TextSecondary, fontSize = 10.sp)
                        Text(audioTelem.activeDevice, color = AccentCyan, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Latency Profile", color = TextSecondary, fontSize = 10.sp)
                        Text(if (isMmap) "Sub-10ms DMA" else "Normal (~45ms)", color = if (isMmap) AccentGreen else TextSecondary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Hi-Res Sampling", color = TextSecondary, fontSize = 10.sp)
                        Text("48.0 kHz 24b", color = AccentPurple, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ── 2. Low-Latency Gaming Audio MMAP Card ───────────────
        LynxCard(
            title = "Low-Latency Gaming Audio MMAP",
            icon = Icons.Default.Speed,
            accentColor = AccentCyan
        ) {
            LynxSwitch(
                label = "AAudio MMAP Direct DMA Bypass",
                subLabel = "Bypass AudioServer mixer Android untuk latensi respon suara tembakan & langkah kaki instan di game",
                checked = state.audio.lowLatencyMmap,
                onCheckedChange = { viewModel.setAudioMmap(it) },
            )

            HorizontalDivider(
                color = BorderGlass.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 10.dp)
            )

            LynxSwitch(
                label = "Deep Buffer Media Bypass",
                subLabel = "Mematikan buffering audio lambat (audio.deep_buffer.media=false) demi sinkronisasi suara real-time",
                checked = state.audio.deepBufferBypass,
                onCheckedChange = { viewModel.setAudioDeepBufferBypass(it) },
            )
        }

        // ── 3. Direct Hi-Res Audio & Buffer Offload Card ────────
        LynxCard(
            title = "Hi-Res Audio & Fast-Track DAC Offload",
            icon = Icons.Default.GraphicEq,
            accentColor = AccentOrange
        ) {
            LynxSwitch(
                label = "Direct DAC 32KB Offload Buffer",
                subLabel = "Mengurangi ukuran offload buffer ke 32KB & kunci multiplier fast-track untuk transmisi audio tanpa jitter",
                checked = state.audio.directDacOffload,
                onCheckedChange = { viewModel.setAudioDirectDacOffload(it) },
            )

            HorizontalDivider(
                color = BorderGlass.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 10.dp)
            )

            LynxSwitch(
                label = "Bluetooth Crackle & Drift Guard",
                subLabel = "Pencegahan packet loss dan sinkronisasi clock Bluetooth A2DP (LDAC / AAC / AptX)",
                checked = state.audio.btCrackleGuard,
                onCheckedChange = {
                    viewModel.recordStateMutation()
                    viewModel.setKey("audio.bt_crackle_guard", it.toString(), "bool")
                },
            )
        }

        // ── 4. Sound Control Driver Gain Card ────────────────────
        LynxCard(
            title = "Hardware Sound Gain & DAC Amplifier",
            icon = Icons.Default.Tune,
            accentColor = AccentPurple
        ) {
            if (soundCtrl.isSupported) {
                Text(
                    "Driver Sound Control Kernel Terdeteksi (Faux/Franco)",
                    color = AccentGreen,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                var hpGain by remember(soundCtrl.headphoneGainL) { mutableFloatStateOf(soundCtrl.headphoneGainL.toFloat()) }
                var spkGain by remember(soundCtrl.speakerGain) { mutableFloatStateOf(soundCtrl.speakerGain.toFloat()) }
                var isDraggingSound by remember { mutableStateOf(false) }
                var lastSoundInteraction by remember { mutableLongStateOf(0L) }

                LaunchedEffect(soundCtrl.headphoneGainL, soundCtrl.speakerGain) {
                    val now = System.currentTimeMillis()
                    if (!isDraggingSound && (now - lastSoundInteraction > 3500L)) {
                        hpGain = soundCtrl.headphoneGainL.toFloat()
                        spkGain = soundCtrl.speakerGain.toFloat()
                    }
                }

                // Headphone L/R
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Headphone Gain (L/R)",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "${hpGain.toInt()} dB",
                        color = AccentPurple,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = hpGain,
                    onValueChange = { gain ->
                        isDraggingSound = true
                        lastSoundInteraction = System.currentTimeMillis()
                        hpGain = gain
                    },
                    onValueChangeFinished = {
                        isDraggingSound = false
                        lastSoundInteraction = System.currentTimeMillis()
                        viewModel.setSoundGain(hpGain.toInt(), hpGain.toInt(), spkGain.toInt(), soundCtrl.micGain, soundCtrl.highPerfMode)
                    },
                    valueRange = -20f..20f,
                    steps = 40,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(4.dp))

                // Speaker Gain
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Speaker Gain",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "${spkGain.toInt()} dB",
                        color = AccentPurple,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = spkGain,
                    onValueChange = { gain ->
                        isDraggingSound = true
                        lastSoundInteraction = System.currentTimeMillis()
                        spkGain = gain
                    },
                    onValueChangeFinished = {
                        isDraggingSound = false
                        lastSoundInteraction = System.currentTimeMillis()
                        viewModel.setSoundGain(hpGain.toInt(), hpGain.toInt(), spkGain.toInt(), soundCtrl.micGain, soundCtrl.highPerfMode)
                    },
                    valueRange = -20f..20f,
                    steps = 40,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(4.dp))

                LynxSwitch(
                    label = "High-Performance DAC Mode",
                    subLabel = "Mengaktifkan class-AB high power mode pada internal codec DAC",
                    checked = soundCtrl.highPerfMode,
                    onCheckedChange = { hp ->
                        viewModel.setSoundGain(hpGain.toInt(), hpGain.toInt(), spkGain.toInt(), soundCtrl.micGain, hp)
                    }
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, BorderGlass),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            "Driver Hardware Sound Control (Franco/Faux)",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Kernel saat ini tidak menyertakan modul sound_control lawas. Sistem Lynx mengarahkan tuning audio langsung melalui AAudio HAL Direct DMA, Fast-Track, dan MMAP yang 100% universal di seluruh perangkat Android.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}
