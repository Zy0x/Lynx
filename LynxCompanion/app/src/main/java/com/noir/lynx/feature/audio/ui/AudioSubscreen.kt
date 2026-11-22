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
        // ── 1. Audio Subsystem & Output Monitor Card ─────
        val isMmap = state.audio.lowLatencyMmap
        LynxCard(
            title = "Audio Subsystem & Output Monitor",
            subtitle = "${audioTelem.codecDriverLabel} • ${audioTelem.serverStatus}",
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
                        if (state.audio.deepBufferBypass) "Pipeline: Real-Time Direct Stream"
                        else "Pipeline: Buffered AudioFlinger Stream",
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
                        Text("Output Route", color = TextSecondary, fontSize = 10.sp)
                        Text(
                            audioTelem.activeDevice,
                            color = AccentCyan,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Latency Path", color = TextSecondary, fontSize = 10.sp)
                        Text(
                            if (isMmap) "MMAP Direct" else "Standard Mixer",
                            color = if (isMmap) AccentGreen else TextSecondary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("PCM Sampling", color = TextSecondary, fontSize = 10.sp)
                        Text(
                            "${audioTelem.sampleRate} • ${audioTelem.bitDepthLabel.replace(" PCM", "")}",
                            color = AccentPurple,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // ── 2. Quick Audio Profile (1-Tap Preset) Card ──────────
        val activePreset = state.audio.activePreset
        LynxCard(
            title = "Quick Audio Profile (1-Tap Preset)",
            subtitle = "Pilih kombinasi konfigurasi pipeline audio secara instan",
            icon = Icons.Default.AutoAwesome,
            accentColor = AccentGreen
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val presets = listOf(
                    Triple("oem_default", "OEM Default", "Standar Pabrik"),
                    Triple("gaming_mmap", "Low-Latency", "MMAP + No Buffer"),
                    Triple("hires_dac", "Hi-Res & DAC", "32KB + A2DP Guard")
                )
                presets.forEach { (key, title, desc) ->
                    val isSelected = activePreset == key
                    val chipAccent = when (key) {
                        "gaming_mmap" -> AccentCyan
                        "hires_dac" -> AccentOrange
                        else -> AccentGreen
                    }
                    Surface(
                        onClick = { viewModel.applyAudioPreset(key) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) chipAccent.copy(alpha = 0.16f) else BgElevated,
                        border = BorderStroke(
                            width = if (isSelected) 1.2.dp else 0.8.dp,
                            color = if (isSelected) chipAccent else BorderGlass
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                title,
                                color = if (isSelected) chipAccent else TextPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                desc,
                                color = TextSecondary,
                                fontSize = 9.5.sp
                            )
                        }
                    }
                }
            }
        }

        // ── 3. Low-Latency Gaming Audio (MMAP) Card ─────────────
        LynxCard(
            title = "Low-Latency Gaming Audio (MMAP)",
            subtitle = "Optimalisasi jalur memori audio langsung untuk menekan delay suara",
            icon = Icons.Default.Speed,
            accentColor = AccentCyan
        ) {
            LynxSwitch(
                label = "AAudio MMAP Direct DMA",
                subLabel = "Fitur kirim memori langsung (Memory-Mapped DMA) ke chip suara tanpa antrean mixer Android agar suara langkah kaki & tembakan bebas delay.",
                checked = state.audio.lowLatencyMmap,
                onCheckedChange = { viewModel.setAudioMmap(it) },
            )

            HorizontalDivider(
                color = BorderGlass.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 10.dp)
            )

            LynxSwitch(
                label = "Deep Buffer Media Bypass",
                subLabel = "Mematikan antrean buffer besar (audio.deep_buffer.media=false) agar pemrosesan suara game dan video langsung diputar secara real-time.",
                checked = state.audio.deepBufferBypass,
                onCheckedChange = { viewModel.setAudioDeepBufferBypass(it) },
            )
        }

        // ── 4. Hi-Res DAC Offload & Bluetooth A2DP Card ─────────
        LynxCard(
            title = "Hi-Res DAC Offload & Bluetooth A2DP",
            subtitle = "Presisi pengiriman paket konverter suara (DAC) & kestabilan nirkabel",
            icon = Icons.Default.GraphicEq,
            accentColor = AccentOrange
        ) {
            LynxSwitch(
                label = "Direct DAC 32KB Offload Buffer",
                subLabel = "Memperkecil paket pengiriman data langsung ke chip konverter suara (DAC Offload 32KB) agar detail musik lebih presisi dan bebas patah-patah (jitter).",
                checked = state.audio.directDacOffload,
                onCheckedChange = { viewModel.setAudioDirectDacOffload(it) },
            )

            HorizontalDivider(
                color = BorderGlass.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 10.dp)
            )

            LynxSwitch(
                label = "Bluetooth A2DP Crackle & Drift Guard",
                subLabel = "Menjaga kestabilan sinkronisasi sinyal audio nirkabel (A2DP) dan prioritas proses Bluetooth pada TWS/headset agar suara tidak kresek atau tertinggal.",
                checked = state.audio.btCrackleGuard,
                onCheckedChange = { viewModel.setAudioBtCrackleGuard(it) },
            )
        }

        // ── 5. Hardware Sound Gain (DAC Amplifier) Card ─────────
        LynxCard(
            title = "Hardware Sound Gain (DAC Amplifier)",
            subtitle = "Penguat daya sinyal analog/digital langsung pada register chip codec (dB Gain)",
            icon = Icons.Default.Tune,
            accentColor = AccentPurple
        ) {
            if (soundCtrl.isSupported) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Kernel Sound Control Active (Faux/Franco)",
                        color = AccentGreen,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = {
                            viewModel.setSoundGain(0, 0, 0, 0, soundCtrl.highPerfMode)
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.heightIn(min = 36.dp)
                    ) {
                        Text("Reset 0 dB", color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                var hpGain by remember(soundCtrl.headphoneGainL) { mutableFloatStateOf(soundCtrl.headphoneGainL.toFloat()) }
                var spkGain by remember(soundCtrl.speakerGain) { mutableFloatStateOf(soundCtrl.speakerGain.toFloat()) }
                var micGain by remember(soundCtrl.micGain) { mutableFloatStateOf(soundCtrl.micGain.toFloat()) }
                var isDraggingSound by remember { mutableStateOf(false) }
                var lastSoundInteraction by remember { mutableLongStateOf(0L) }

                LaunchedEffect(soundCtrl.headphoneGainL, soundCtrl.speakerGain, soundCtrl.micGain) {
                    val now = System.currentTimeMillis()
                    if (!isDraggingSound && (now - lastSoundInteraction > 3500L)) {
                        hpGain = soundCtrl.headphoneGainL.toFloat()
                        spkGain = soundCtrl.speakerGain.toFloat()
                        micGain = soundCtrl.micGain.toFloat()
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
                        viewModel.setSoundGain(hpGain.toInt(), hpGain.toInt(), spkGain.toInt(), micGain.toInt(), soundCtrl.highPerfMode)
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
                        viewModel.setSoundGain(hpGain.toInt(), hpGain.toInt(), spkGain.toInt(), micGain.toInt(), soundCtrl.highPerfMode)
                    },
                    valueRange = -20f..20f,
                    steps = 40,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(4.dp))

                // Microphone Gain
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Microphone Gain",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "${micGain.toInt()} dB",
                        color = AccentPurple,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = micGain,
                    onValueChange = { gain ->
                        isDraggingSound = true
                        lastSoundInteraction = System.currentTimeMillis()
                        micGain = gain
                    },
                    onValueChangeFinished = {
                        isDraggingSound = false
                        lastSoundInteraction = System.currentTimeMillis()
                        viewModel.setSoundGain(hpGain.toInt(), hpGain.toInt(), spkGain.toInt(), micGain.toInt(), soundCtrl.highPerfMode)
                    },
                    valueRange = -20f..20f,
                    steps = 40,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(4.dp))

                LynxSwitch(
                    label = "High-Performance Class-AB DAC Mode",
                    subLabel = "Mengaktifkan mode daya penuh (Class-AB) pada amplifier internal DAC untuk mengurangi distorsi harmonik pada volume tinggi.",
                    checked = soundCtrl.highPerfMode,
                    onCheckedChange = { hp ->
                        viewModel.setSoundGain(hpGain.toInt(), hpGain.toInt(), spkGain.toInt(), micGain.toInt(), hp)
                    }
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, BorderGlass),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                "ALSA / ASoC Codec (${audioTelem.codecDriverLabel})",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Modul kernel sound_control tidak tersedia; penguatan sinyal amplifier (dB Gain) diatur otomatis oleh driver codec bawaan.",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 14.5.sp
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AccentPurple.copy(alpha = 0.14f),
                            border = BorderStroke(0.8.dp, AccentPurple.copy(alpha = 0.45f))
                        ) {
                            Text(
                                "CODEC MANAGED",
                                color = AccentPurple,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
