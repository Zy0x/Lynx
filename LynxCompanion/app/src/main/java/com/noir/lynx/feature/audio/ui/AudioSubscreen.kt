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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.controls.LynxActionButton
import com.noir.lynx.designsystem.controls.LynxSwitch
import com.noir.lynx.designsystem.controls.SafeSlider as Slider
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.ui.*

/**
 * Subscreen: Sound & Audio Hardware Subsystem
 * Organized into 3 focused tabs:
 *  0 -> Pipeline & Gaming (1-Tap Presets, AAudio MMAP, Deep Buffer, DAC Offload, Hardware Sound Gain)
 *  1 -> Bluetooth & Dev (A2DP Hardware Offload, Absolute Volume, LDAC Bitrate, SBC Dual-Channel HD, AVRCP Version, USB Routing)
 *  2 -> GSI & HAL Fix (Dynamic PHH Project Treble GSI detection, System BT Audio HAL, SoundFX Bypass, CAF Audio Policy, SoC HAL Fixes, Soft Reload)
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
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.refreshAudioSubsystem()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── Always-Visible Hero: Audio Subsystem & Output Monitor Card ─────
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

        // ── 3-Tab Category Selector Bar ─────────────────────────
        AudioCategoryTabBar(
            selectedTab = selectedTab,
            isGsiDetected = audioTelem.isPhhGsiDetected,
            onSelectTab = { selectedTab = it }
        )

        when (selectedTab) {
            0 -> AudioPipelineGamingTab(
                uiState = uiState,
                viewModel = viewModel
            )
            1 -> AudioBluetoothDevTab(
                uiState = uiState,
                viewModel = viewModel
            )
            2 -> AudioGsiHalFixTab(
                uiState = uiState,
                viewModel = viewModel
            )
        }
    }
}

@Composable
private fun AudioCategoryTabBar(
    selectedTab: Int,
    isGsiDetected: Boolean,
    onSelectTab: (Int) -> Unit
) {
    val tabs = listOf(
        Triple(0, "Pipeline & Game", Icons.Default.Speed),
        Triple(1, "Bluetooth & Dev", Icons.Default.BluetoothAudio),
        Triple(2, if (isGsiDetected) "GSI & HAL ●" else "GSI & HAL Fix", Icons.Default.Build)
    )
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = BgElevated,
        border = BorderStroke(0.8.dp, BorderGlass),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tabs.forEach { (idx, label, icon) ->
                val selected = selectedTab == idx
                val accent = when (idx) {
                    0 -> AccentCyan
                    1 -> AccentBlue
                    else -> AccentOrange
                }
                Surface(
                    onClick = { onSelectTab(idx) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) accent.copy(alpha = 0.16f) else Color.Transparent,
                    border = if (selected) BorderStroke(1.dp, accent.copy(alpha = 0.55f)) else null,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (selected) accent else TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = label,
                            color = if (selected) accent else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioPipelineGamingTab(
    uiState: LynxUiState,
    viewModel: LynxViewModel
) {
    val state = uiState.state
    val audioTelem = uiState.audioTelemetry
    val soundCtrl = uiState.soundControl

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // ── 1. Quick Audio Profile (1-Tap Preset) Card ──────────
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

        // ── 2. Low-Latency Gaming Audio (MMAP) Card ─────────────
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

        // ── 3. Hi-Res DAC Offload, Resampler & Spatial Pipeline Card ─────────
        LynxCard(
            title = "Hi-Res DAC Offload, Resampler & Spatial Engine",
            subtitle = "Engine aktif: ${audioTelem.detectedAudioEffectsLabel} • Presisi konverter DAC & anti-pop",
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
                label = "Hi-Res 24-Bit PCM & Sinc Resampler (Quality 7)",
                subLabel = "Mengaktifkan interpolasi resampler presisi tertinggi AudioFlinger (af.resampler.quality=7) serta offload PCM 24-bit langsung ke DAC.",
                checked = state.audio.hiresResampler24Bit,
                onCheckedChange = { viewModel.setAudioHiresResampler24Bit(it) },
            )

            HorizontalDivider(
                color = BorderGlass.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 10.dp)
            )

            LynxSwitch(
                label = "ASoC Codec Pop/Click Idle Guard (pmdown_time)",
                subLabel = "Mencegah chip codec ALSA/ASoC tidur terlalu cepat saat jeda suara agar tidak muncul bunyi 'pop/klik' atau suku kata pertama terpotong.",
                checked = state.audio.codecIdlePopGuard,
                onCheckedChange = { viewModel.setAudioCodecIdlePopGuard(it) },
            )

            HorizontalDivider(
                color = BorderGlass.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 10.dp)
            )

            LynxSwitch(
                label = "Spatial Audio & Multi-Channel Surround Engine",
                subLabel = "Mengaktifkan pipeline Spatializer Android (ro.audio.spatializer_enabled) & alokasi uClamp efek suara 3D untuk separasi arah suara yang lebih luas.",
                checked = state.audio.spatializerBoost,
                onCheckedChange = { viewModel.setAudioSpatializerBoost(it) },
            )

            HorizontalDivider(
                color = BorderGlass.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 10.dp)
            )

            LynxSwitch(
                label = "Fine Media Volume Steps (30-Step Precision)",
                subLabel = "Menggandakan tingkat kehalusan tombol volume media dari 15 langkah menjadi 30 langkah (ro.config.media_vol_steps=30) di seluruh perangkat.",
                checked = state.audio.fineVolumeSteps30,
                onCheckedChange = { viewModel.setAudioFineVolumeSteps30(it) },
            )
        }

        // ── 4. Hardware Sound Gain (DAC Amplifier) Card ─────────
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

@Composable
private fun AudioBluetoothDevTab(
    uiState: LynxUiState,
    viewModel: LynxViewModel
) {
    val audio = uiState.state.audio
    val telem = uiState.audioTelemetry

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // ── 1. Bluetooth A2DP Offload & Volume Architecture ─────
        LynxCard(
            title = "Bluetooth A2DP Offload & Volume Architecture",
            subtitle = "Pengaturan jalur pemrosesan audio Bluetooth & sinkronisasi volume Opsi Pengembang",
            icon = Icons.Default.BluetoothAudio,
            accentColor = AccentBlue,
            action = {
                val isHostStack = audio.disableA2dpHwOffload
                val badgeColor = if (isHostStack) AccentCyan else AccentBlue
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor.copy(alpha = 0.14f),
                    border = BorderStroke(0.8.dp, badgeColor.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = if (isHostStack) "HOST STACK" else "HW OFFLOAD",
                        color = badgeColor,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
        ) {
            LynxSwitch(
                label = "Disable Bluetooth A2DP Hardware Offload",
                subLabel = "Memindahkan pemrosesan audio Bluetooth dari DSP vendor ke sistem utama Android (persist.bluetooth.a2dp_offload.disabled) — solusi utama mengatasi TWS bisu atau putus-putus di Custom ROM & GSI.",
                checked = audio.disableA2dpHwOffload,
                onCheckedChange = { viewModel.setAudioA2dpHwOffloadDisabled(it) }
            )

            HorizontalDivider(
                color = BorderGlass.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 10.dp)
            )

            LynxSwitch(
                label = "Bluetooth A2DP Crackle & Clock Drift Guard",
                subLabel = "Menjaga kestabilan sinkronisasi sinyal audio nirkabel (A2DP) dan menaikkan prioritas proses Bluetooth pada TWS/headset agar suara tidak kresek atau tertinggal.",
                checked = audio.btCrackleGuard,
                onCheckedChange = { viewModel.setAudioBtCrackleGuard(it) }
            )

            HorizontalDivider(
                color = BorderGlass.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 10.dp)
            )

            LynxSwitch(
                label = "Disable Bluetooth Absolute Volume",
                subLabel = "Memisahkan pengatur volume ponsel dan volume internal TWS/speaker Bluetooth (persist.bluetooth.disableabsvol) agar kenaikan volume lebih halus dan tidak terlalu keras di level rendah.",
                checked = audio.disableAbsoluteVolume,
                onCheckedChange = { viewModel.setAudioDisableAbsoluteVolume(it) }
            )
        }

        // ── 2. Bluetooth Codec, LDAC Bitrate & AVRCP Protocol ───
        LynxCard(
            title = "Bluetooth Codec, LDAC Bitrate & AVRCP Protocol",
            subtitle = "Kontrol kualitas bandwidth LDAC/SBC HD/aptX/LC3 serta kompatibilitas kontrol media",
            icon = Icons.Default.GraphicEq,
            accentColor = AccentCyan
        ) {
            Text(
                text = "LDAC Playback Quality & Bitrate Mode",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Mengatur target bitrate codec Sony LDAC (vendor.bluetooth.ldac.abr) antara kestabilan sinyal vs detail audio",
                color = TextSecondary,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )

            val ldacOptions = listOf(
                Triple("adaptive", "Adaptive", "Best Effort"),
                Triple("balanced", "660 kbps", "Balanced"),
                Triple("hires_990", "990 kbps", "Hi-Res Audio")
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ldacOptions.forEach { (key, title, sub) ->
                    val selected = audio.ldacQualityMode == key
                    Surface(
                        onClick = { viewModel.setAudioLdacQualityMode(key) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) AccentCyan.copy(alpha = 0.16f) else BgElevated,
                        border = BorderStroke(
                            width = if (selected) 1.2.dp else 0.8.dp,
                            color = if (selected) AccentCyan else BorderGlass
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 50.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = title,
                                color = if (selected) AccentCyan else TextPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = sub,
                                color = TextSecondary,
                                fontSize = 9.5.sp
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = BorderGlass.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 10.dp)
            )

            LynxSwitch(
                label = "SBC Dual-Channel HD Audio (SBC XQ)",
                subLabel = "Meningkatkan bandwidth codec standar SBC ke mode Dual-Channel bitrate tinggi (~452–551 kbps) setara kualitas aptX HD pada TWS yang tidak mendukung LDAC.",
                checked = audio.sbcDualChannelHd,
                onCheckedChange = { viewModel.setAudioSbcDualChannelHd(it) }
            )

            HorizontalDivider(
                color = BorderGlass.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 10.dp)
            )

            LynxSwitch(
                label = "Qualcomm aptX Adaptive R2.2 & TWS+ Low-Latency",
                subLabel = "Mengaktifkan ekstensi aptX Adaptive R2.2 & dual-stream TWS+ (persist.vendor.qcom.bluetooth.aptxadaptiver2_2_support) untuk menekan delay suara pada chipset/TWS yang mendukung.",
                checked = audio.aptxAdaptiveLowLatency,
                onCheckedChange = { viewModel.setAudioAptxAdaptiveLowLatency(it) }
            )

            HorizontalDivider(
                color = BorderGlass.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 10.dp)
            )

            LynxSwitch(
                label = "Bluetooth HFP SWB HD Voice & NREC Bypass (In-Game Mic)",
                subLabel = "Mengaktifkan Super-Wideband 32kHz Voice (bluetooth.hfp.swb.supported) & mematikan NREC vendor (bt_headset_nrec_on=0) agar suara game tidak mendem saat mikrofon TWS menyala.",
                checked = audio.btHfpHdVoiceNrecBypass,
                onCheckedChange = { viewModel.setAudioBtHfpHdVoiceNrecBypass(it) }
            )

            HorizontalDivider(
                color = BorderGlass.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 10.dp)
            )

            LynxSwitch(
                label = "Bluetooth LE Audio (LC3 Unicast BAP)",
                subLabel = "Mengontrol profil Bluetooth Low-Energy Audio generasi baru (bluetooth.profile.bap.unicast.client.enabled); matikan jika TWS LE Audio mengalami putus-nyambung di GSI.",
                checked = audio.leAudioLc3Enabled,
                onCheckedChange = { viewModel.setAudioLeAudioLc3(it) }
            )

            HorizontalDivider(
                color = BorderGlass.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 10.dp)
            )

            Text(
                text = "Bluetooth AVRCP Protocol Version",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Versi protokol kontrol media Bluetooth (persist.bluetooth.avrcpversion) untuk kompatibilitas tombol TWS & head-unit mobil",
                color = TextSecondary,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )

            val avrcpOptions = listOf(
                "default" to "Default",
                "avrcp14" to "AVRCP 1.4",
                "avrcp15" to "AVRCP 1.5",
                "avrcp16" to "AVRCP 1.6"
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                avrcpOptions.forEach { (key, label) ->
                    val selected = audio.avrcpVersion == key
                    Surface(
                        onClick = { viewModel.setAudioAvrcpVersion(key) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) AccentBlue.copy(alpha = 0.16f) else BgElevated,
                        border = BorderStroke(
                            width = if (selected) 1.2.dp else 0.8.dp,
                            color = if (selected) AccentBlue else BorderGlass
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (selected) AccentBlue else TextPrimary,
                                fontSize = 10.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // ── 3. USB Audio DAC & Developer Routing ────────────────
        LynxCard(
            title = "USB Audio DAC & Developer Routing",
            subtitle = "Kontrol pengalihan jalur suara port USB Type-C (Opsi Pengembang)",
            icon = Icons.Default.Usb,
            accentColor = AccentPurple
        ) {
            LynxSwitch(
                label = "Disable USB Audio Automatic Routing",
                subLabel = "Mencegah sistem mengalihkan suara otomatis ke port USB-C (usb_audio_automatic_routing_disabled) saat memasang kipas pendingin (cooler), hub, atau dock non-audio.",
                checked = audio.disableUsbAudioRouting,
                onCheckedChange = { viewModel.setAudioDisableUsbRouting(it) }
            )
        }
    }
}

@Composable
private fun AudioGsiHalFixTab(
    uiState: LynxUiState,
    viewModel: LynxViewModel
) {
    val audio = uiState.state.audio
    val telem = uiState.audioTelemetry
    val showPhhControls = telem.isPhhGsiDetected || audio.forceShowGsiControls

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // ── 1. Project Treble / GSI & Audio HAL Detection Card ──
        LynxCard(
            title = "Project Treble / GSI & Audio HAL Architecture",
            subtitle = "Deteksi otomatis lingkungan ROM, layanan Audio HAL & hook phh-prop-handler",
            icon = Icons.Default.Build,
            accentColor = if (telem.isPhhGsiDetected) AccentOrange else AccentCyan,
            action = {
                val badgeText = if (telem.isPhhGsiDetected) "PHH TREBLE GSI" else "TREBLE HAL"
                val badgeColor = if (telem.isPhhGsiDetected) AccentOrange else AccentCyan
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor.copy(alpha = 0.14f),
                    border = BorderStroke(0.8.dp, badgeColor.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderGlass),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = telem.romTypeLabel,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    val halMeta = buildString {
                        append("Audio HAL: ${telem.audioHalServiceLabel}")
                        if (telem.btAudioHalServiceLabel.isNotBlank()) {
                            append(" • BT HAL: ${telem.btAudioHalServiceLabel}")
                        }
                        append(" • SoC: ${telem.socVendor.uppercase()}")
                    }
                    Text(
                        text = halMeta,
                        color = TextSecondary,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = if (telem.hasPhhPropHandler) {
                            "Handler /system/bin/phh-prop-handler.sh terdeteksi aktif — perubahan kebijakan GSI akan langsung mengeksekusi hook native PHH."
                        } else {
                            "ROM ini menggunakan Audio HAL Treble standar (non-PHH GSI). Perbaikan properti universal tetap tersedia."
                        },
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 14.5.sp
                    )
                }
            }

            if (!telem.isPhhGsiDetected) {
                Spacer(Modifier.height(8.dp))
                LynxSwitch(
                    label = "Compatibility Override (Buka Kontrol PHH Treble GSI)",
                    subLabel = "Tampilkan seluruh sakelar kompatibilitas PHH Treble GSI secara paksa pada ROM Custom/Porting non-GSI.",
                    checked = audio.forceShowGsiControls,
                    onCheckedChange = { viewModel.setForceShowGsiControls(it) }
                )
            }
        }

        // ── 2. PHH Treble & GSI Audio Workarounds Card ──────────
        if (showPhhControls) {
            LynxCard(
                title = "PHH Treble & GSI Audio Workarounds",
                subtitle = "Perbaikan masalah Bluetooth bisu, efek DSP pecah & rute telepon pada ROM GSI",
                icon = Icons.Default.Healing,
                accentColor = AccentOrange
            ) {
                LynxSwitch(
                    label = "System Bluetooth Audio HAL (persist.bluetooth.system_audio_hal.enabled)",
                    subLabel = "Mengalihkan pemrosesan audio Bluetooth dari HAL vendor ke System BT Audio HAL (system.bt-audio-hal) dan mematikan A2DP offload untuk memperbaiki TWS bisu di Android GSI.",
                    checked = audio.systemBtAudioHalEnabled,
                    onCheckedChange = { viewModel.setAudioSystemBtAudioHal(it) }
                )

                HorizontalDivider(
                    color = BorderGlass.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 10.dp)
                )

                LynxSwitch(
                    label = "Disable SoundFX & Audio Effects (persist.sys.phh.disable_audio_effects)",
                    subLabel = "Mengabaikan pustaka efek DSP vendor (ro.audio.ignore_effects=true) untuk mengatasi suara pecah, sember, atau pemutar musik crash pada ROM GSI.",
                    checked = audio.phhDisableAudioEffects,
                    onCheckedChange = { viewModel.setAudioPhhDisableEffects(it) }
                )

                HorizontalDivider(
                    color = BorderGlass.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 10.dp)
                )

                LynxSwitch(
                    label = "Alternate CAF / Vendor Audio Policy (persist.sys.phh.caf.audio_policy)",
                    subLabel = "Memasang konfigurasi audio_policy_configuration.xml alternatif dari partisi vendor untuk memperbaiki rute speaker/headset yang tidak keluar suara di GSI.",
                    checked = audio.phhCafAudioPolicy,
                    onCheckedChange = { viewModel.setAudioPhhCafPolicy(it) }
                )

                HorizontalDivider(
                    color = BorderGlass.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 10.dp)
                )

                LynxSwitch(
                    label = "In-Call Voice & Fluence Route Fix (persist.sys.phh.disable_voice_call_in)",
                    subLabel = "Menonaktifkan rute voice_call_in & dual-mic Fluence bermasalah saat suara panggilan telepon atau mikrofon tidak terdengar di GSI/Custom ROM.",
                    checked = audio.phhDisableVoiceCallIn,
                    onCheckedChange = { viewModel.setAudioPhhVoiceCallRouteFix(it) }
                )

                HorizontalDivider(
                    color = BorderGlass.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 10.dp)
                )

                LynxSwitch(
                    label = "Incoming Call Binder Thread Fix (allow_binder_thread_on_incoming_calls)",
                    subLabel = "Membuka blokir thread binder panggilan masuk (ro.telephony.block_binder_thread_on_incoming_calls=false) agar telepon masuk tidak membeku atau bisu di GSI.",
                    checked = audio.phhAllowIncomingCallBinder,
                    onCheckedChange = { viewModel.setAudioPhhIncomingCallBinder(it) }
                )
            }
        }

        // ── 3. Dynamic SoC Vendor Audio HAL Compatibility Card ──
        LynxCard(
            title = "SoC Vendor Audio HAL Compatibility (${telem.socVendor.uppercase()})",
            subtitle = "Penyesuaian pustaka efek & penguat audio spesifik arsitektur chipset",
            icon = Icons.Default.Memory,
            accentColor = AccentPurple
        ) {
            if (telem.socVendor == "qcom" || telem.hasVolumeListenerLib || showPhhControls) {
                LynxSwitch(
                    label = "Disable Qualcomm Volume Listener (libvolumelistener.so)",
                    subLabel = "Memutus pustaka efek libvolumelistener.so Qualcomm (persist.sys.phh.disable_soundvolume_effect) yang sering menyebabkan volume melonjak atau distorsi saat berganti aplikasi.",
                    checked = audio.phhDisableVolumeListener,
                    onCheckedChange = { viewModel.setAudioPhhDisableVolumeListener(it) }
                )

                HorizontalDivider(
                    color = BorderGlass.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 10.dp)
                )

                LynxSwitch(
                    label = "Qualcomm Fluence Dual-Mic Noise Suppression Bypass",
                    subLabel = "Mematikan pemrosesan dual-mic Fluence Qualcomm (ro.qc.sdk.audio.fluencetype=none) jika suara rekaman/telepon terdengar robotik atau tertutup.",
                    checked = audio.qcomFluenceBypass,
                    onCheckedChange = { viewModel.setAudioQcomFluenceBypass(it) }
                )
            }

            if ((telem.socVendor == "qcom" || telem.hasVolumeListenerLib || showPhhControls) &&
                (telem.socVendor == "mtk" || telem.hasBesLoudnessSupport)
            ) {
                HorizontalDivider(
                    color = BorderGlass.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }

            if (telem.socVendor == "mtk" || telem.hasBesLoudnessSupport) {
                LynxSwitch(
                    label = "MediaTek BesLoudness Audio Enhancer (besloudness_state)",
                    subLabel = "Mengontrol post-processing kekerasan suara speaker internal pada HAL MediaTek (persist.vendor.audiohal.besloudness_state); matikan jika suara terdengar tertekan (pumping).",
                    checked = audio.vendorBesLoudnessEnabled,
                    onCheckedChange = { viewModel.setAudioVendorBesLoudness(it) }
                )
            }
        }

        // ── 4. Dynamic Audio & Bluetooth HAL Property Scanner ───
        if (telem.discoveredVendorAudioProps.isNotEmpty()) {
            var showScanner by rememberSaveable { mutableStateOf(false) }
            LynxCard(
                title = "Dynamic Audio & Bluetooth HAL Property Scanner",
                subtitle = "${telem.discoveredVendorAudioProps.size} properti audio/Bluetooth aktif terdeteksi di perangkat ini",
                icon = Icons.Default.Search,
                accentColor = AccentBlue
            ) {
                Surface(
                    onClick = { showScanner = !showScanner },
                    shape = RoundedCornerShape(10.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, BorderGlass),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (showScanner) "Sembunyikan Daftar Properti HAL" else "Lihat Properti Audio & Bluetooth HAL Terdeteksi",
                            color = TextPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = if (showScanner) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = AccentBlue
                        )
                    }
                }

                if (showScanner) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgDeepOled,
                        border = BorderStroke(0.6.dp, BorderGlass),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            telem.discoveredVendorAudioProps.forEach { (k, v) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = k,
                                        color = TextSecondary,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    )
                                    Text(
                                        text = v.ifBlank { "(empty)" },
                                        color = AccentCyan,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── 4. Audio Service Live Reload & Baseline Recovery ────
        LynxCard(
            title = "Audio Service Live Reload & Baseline Recovery",
            subtitle = "Muat ulang layanan audio tanpa reboot atau pulihkan seluruh properti ke standar pabrik",
            icon = Icons.Default.Refresh,
            accentColor = AccentGreen
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LynxActionButton(
                    text = "Reload AudioServer & Bluetooth Audio HAL (Tanpa Reboot)",
                    icon = Icons.Default.Refresh,
                    accentColor = AccentCyan,
                    onClick = { viewModel.reloadAudioServerAndHal() }
                )
                LynxActionButton(
                    text = "Restore OEM Audio Baseline (Reset Standar Pabrik)",
                    icon = Icons.Default.Restore,
                    accentColor = AccentOrange,
                    onClick = { viewModel.restoreAudioOemBaseline() }
                )
            }
        }
    }
}
