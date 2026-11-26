package com.noir.lynx.feature.hud.ui

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.feature.hud.addins.*

/**
 * Modern Game Screen Recording Studio for Lynx Companion & In-Game Sidebar.
 * Engineered with ergonomic Bento cards, real-time status pulses, and >= 48dp touch targets.
 */
@Composable
internal fun HudRecorderMasterCard(context: Context) {
    val shared = HudGamingAddins.current?.recorder
    val recorder = remember(context, shared) { shared ?: HudScreenRecorder(context) }
    DisposableEffect(recorder) {
        onDispose {
            if (shared == null) recorder.close()
        }
    }
    HudRecorderControls(recorder)
}

@Composable
internal fun HudRecorderControls(provided: HudScreenRecorder? = null) {
    val recorder = provided ?: HudGamingAddins.current?.recorder ?: return
    val state by recorder.state.collectAsState()
    val quality by recorder.quality.collectAsState()
    val audio by recorder.audioSource.collectAsState()
    val bitrate by recorder.bitrate.collectAsState()
    val compatibility by recorder.compatibility.collectAsState()
    val stealth by recorder.stealthOverlay.collectAsState()

    var selectedSubpage by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ============================================================
        // 1. HERO STUDIO RECORDING BENTO CARD
        // ============================================================
        RecorderHeroStudioCard(
            recorder = recorder,
            state = state,
            quality = quality,
            audio = audio,
            bitrate = bitrate,
            compatibility = compatibility,
            stealth = stealth
        )

        // ============================================================
        // 2. SUBTAB NAVIGATION ROW (Studio, Video, Audio, Lanjutan)
        // ============================================================
        RecorderSubtabRow(
            selectedIndex = selectedSubpage,
            onSelect = { selectedSubpage = it }
        )

        // ============================================================
        // 3. SUBPAGE CONTENT
        // ============================================================
        AnimatedContent(
            targetState = selectedSubpage,
            transitionSpec = {
                fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(140))
            },
            label = "recorder_subpage_anim"
        ) { section ->
            when (section) {
                0 -> RecorderStudioSection(
                    recorder = recorder,
                    state = state,
                    compatibility = compatibility
                )
                1 -> RecorderVideoSection(
                    recorder = recorder,
                    quality = quality,
                    bitrate = bitrate,
                    isBusy = state.busy,
                    compatibility = compatibility
                )
                2 -> RecorderAudioSection(
                    recorder = recorder,
                    audio = audio,
                    isBusy = state.busy
                )
                3 -> RecorderAdvancedSection(
                    recorder = recorder,
                    compatibility = compatibility,
                    stealth = stealth,
                    isBusy = state.busy,
                    softwareActive = state.software
                )
            }
        }
    }
}

// ============================================================
// 1. HERO STUDIO MASTER CARD
// ============================================================
@Composable
private fun RecorderHeroStudioCard(
    recorder: HudScreenRecorder,
    state: HudRecordingState,
    quality: Int,
    audio: Int,
    bitrate: Int,
    compatibility: Boolean,
    stealth: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "recorder_dot_pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recorder_dot_alpha"
    )

    val isRecording = state.phase == HudRecordingPhase.RECORDING
    val isBusy = state.busy
    val isUnsupported = state.phase == HudRecordingPhase.UNSUPPORTED
    val isFailed = state.phase == HudRecordingPhase.FAILED

    val statusColor = when {
        isRecording -> AccentRed
        isBusy -> AccentOrange
        isFailed -> AccentRed
        isUnsupported -> AccentOrange
        else -> AccentCyan
    }

    val statusText = when (state.phase) {
        HudRecordingPhase.CHECKING -> "MEMERIKSA ENCODER..."
        HudRecordingPhase.UNSUPPORTED -> "ENCODER TIDAK DIDUKUNG"
        HudRecordingPhase.COUNTDOWN -> "MEMULAI (${state.countdown}s)..."
        HudRecordingPhase.RECORDING -> "SEDANG MEREKAM GAME"
        HudRecordingPhase.SAVING -> "MENYIMPAN REKAMAN..."
        HudRecordingPhase.SAVED -> "REKAMAN TERSIMPAN"
        HudRecordingPhase.FAILED -> "GAGAL MEREKAM"
        HudRecordingPhase.IDLE -> "STUDIO SIAP MEREKAM"
    }

    val outputDimensions = recorder.outputSize()
    val resolutionLabel = if (outputDimensions != null) "${outputDimensions.first}×${outputDimensions.second}" else "Otomatis"
    val bitrateLabel = "${listOf(4, 8, 16, 24).getOrElse(bitrate) { 16 }} Mbps"
    val audioLabel = when (audio) {
        0 -> "Bisu"
        1 -> "Mic"
        2 -> "Game Murni"
        else -> "Mix Game+Mic"
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = BgCard,
        border = BorderStroke(1.dp, if (isRecording) AccentRed.copy(alpha = 0.7f) else if (isBusy) AccentOrange.copy(alpha = 0.5f) else AccentCyan.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row: Icon + Title + Liveness Dot
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(statusColor.copy(alpha = 0.15f))
                            .border(BorderStroke(0.8.dp, statusColor.copy(alpha = 0.4f)), RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.FiberManualRecord else Icons.Default.Videocam,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "STUDIO PEREKAM GAME LYNX",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor,
                            letterSpacing = 0.6.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(statusColor.copy(alpha = if (isBusy) dotAlpha else 1.0f))
                            )
                            Text(
                                text = statusText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }
                }

                // Segments or Mode Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BgElevated,
                    border = BorderStroke(0.6.dp, BorderSubtle)
                ) {
                    Text(
                        text = if (isRecording && state.segments > 0) "Segmen #${state.segments + 1}" else if (compatibility) "Software" else "Hardware GPU",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (compatibility) AccentOrange else AccentCyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Configuration Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RecorderSummaryBadge(label = resolutionLabel, icon = Icons.Default.AspectRatio, modifier = Modifier.weight(1f))
                RecorderSummaryBadge(label = bitrateLabel, icon = Icons.Default.Speed, modifier = Modifier.weight(1f))
                RecorderSummaryBadge(label = audioLabel, icon = Icons.Default.Headphones, modifier = Modifier.weight(1.2f))
                RecorderSummaryBadge(
                    label = if (stealth) "Stealth ON" else "Stealth OFF",
                    icon = Icons.Default.Security,
                    modifier = Modifier.weight(1.1f),
                    activeColor = if (stealth) AccentGreen else TextSecondary
                )
            }

            // Hero CTA Button (>= 52dp Touch Target)
            val ctaContainerColor = when {
                isRecording -> AccentRed
                isBusy -> AccentOrange
                isUnsupported -> BgElevated
                else -> AccentCyan
            }
            val ctaTextColor = when {
                isRecording -> Color.White
                isBusy -> Color.Black
                isUnsupported -> TextSecondary
                else -> Color.Black
            }

            Surface(
                onClick = {
                    if (isBusy) recorder.stop() else recorder.start()
                },
                enabled = !isUnsupported && state.phase != HudRecordingPhase.CHECKING && state.phase != HudRecordingPhase.SAVING,
                shape = RoundedCornerShape(14.dp),
                color = ctaContainerColor,
                border = BorderStroke(1.dp, ctaContainerColor.copy(alpha = 0.8f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = when {
                            isRecording -> Icons.Default.Stop
                            isBusy -> Icons.Default.HourglassTop
                            else -> Icons.Default.FiberManualRecord
                        },
                        contentDescription = null,
                        tint = ctaTextColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            isRecording -> "HENTIKAN & SIMPAN REKAMAN"
                            state.phase == HudRecordingPhase.COUNTDOWN -> "MEMULAI (${state.countdown}s) • KLIK UNTUK BATAL"
                            state.phase == HudRecordingPhase.SAVING -> "MEMPROSES & MENYIMPAN VIDEO..."
                            isFailed -> "COBA MULAI PEREKAMAN LAGI"
                            else -> "MULAI PEREKAMAN GAME"
                        },
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = ctaTextColor,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Quick "Buka Rekaman Terakhir" Card if available
            if (state.lastUri != null && !isBusy) {
                Surface(
                    onClick = recorder::openLast,
                    shape = RoundedCornerShape(12.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 46.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "Rekaman Terakhir Siap Diputar",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Tersimpan di Galeri & Movies/Lynx/",
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Putar",
                            tint = AccentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecorderSummaryBadge(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    activeColor: Color = AccentCyan
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = BgElevated,
        border = BorderStroke(0.6.dp, BorderSubtle),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = activeColor,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 1
            )
        }
    }
}

// ============================================================
// 2. SUBTAB NAVIGATION ROW 48DP
// ============================================================
@Composable
private fun RecorderSubtabRow(
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val tabs = listOf(
        Triple(0, "Studio", Icons.Default.Videocam),
        Triple(1, "Video", Icons.Default.Movie),
        Triple(2, "Audio", Icons.Default.Mic),
        Triple(3, "Lanjutan", Icons.Default.Tune)
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = BgCard,
        border = BorderStroke(0.8.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEach { (index, title, icon) ->
                val isSelected = selectedIndex == index
                Surface(
                    onClick = { onSelect(index) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) AccentCyan.copy(alpha = 0.16f) else Color.Transparent,
                    border = BorderStroke(
                        0.8.dp,
                        if (isSelected) AccentCyan.copy(alpha = 0.5f) else Color.Transparent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = if (isSelected) AccentCyan else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = title,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) AccentCyan else TextSecondary
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// SUBPAGE 0: STUDIO KONTROL & STATUS
// ============================================================
@Composable
private fun RecorderStudioSection(
    recorder: HudScreenRecorder,
    state: HudRecordingState,
    compatibility: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Status Hardware Acceleration
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = BgCard,
            border = BorderStroke(0.8.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "AKSELERASI HARDWARE ENCODER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan,
                        letterSpacing = 0.5.sp
                    )
                }
                Text(
                    text = if (compatibility)
                        "Menggunakan encoder software (c2.android.avc.encoder) dengan batasan bitrate 2 Mbps untuk kestabilan sistem."
                    else
                        "Menggunakan akselerasi hardware internal SoC (/system/bin/screenrecord) secara langsung via root. Perekaman berjalan di thread GPU mandiri tanpa membebani core CPU game (zero jank).",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )
            }
        }

        // Storage Output Info
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = BgCard,
            border = BorderStroke(0.8.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "LOKASI PENYIMPANAN VIDEO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentBlue,
                        letterSpacing = 0.5.sp
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BgElevated,
                    border = BorderStroke(0.6.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "/sdcard/Movies/Lynx/",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
                Text(
                    text = "Video hasil rekaman otomatis terindeks ke Galeri / Google Photos dan tersimpan dalam format MP4 (H.264 / AAC).",
                    fontSize = 10.5.sp,
                    color = TextSecondary
                )
            }
        }

        // Tips Zero-Lag
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = BgCard,
            border = BorderStroke(0.8.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = AccentGreen,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Tips: Perekaman layar dapat dimulai atau dihentikan kapan saja langsung dari tombol pintas sidebar in-game tanpa perlu keluar ke aplikasi Lynx Companion.",
                    fontSize = 10.5.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

// ============================================================
// SUBPAGE 1: KUALITAS VIDEO & BITRATE
// ============================================================
@Composable
private fun RecorderVideoSection(
    recorder: HudScreenRecorder,
    quality: Int,
    bitrate: Int,
    isBusy: Boolean,
    compatibility: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- 1. RESOLUSI VIDEO ---
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "RESOLUSI VIDEO",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AccentCyan,
                letterSpacing = 0.5.sp
            )

            val qualityOptions = listOf(
                Triple(0, "Ringan (720p)", "Prioritas FPS stabil, hemat RAM & baterai. Disarankan untuk turnamen dan game berat."),
                Triple(1, "Seimbang (1080p)", "Tampilan tajam & proporsional untuk konten YouTube / TikTok tanpa membebani sistem."),
                Triple(2, "Asli (Native)", "Piksel 1:1 sesuai resolusi fisik layar. Kejernihan maksimal tanpa kompresi skala.")
            )

            qualityOptions.forEach { (value, label, desc) ->
                val isSelected = quality == value
                Surface(
                    onClick = { if (!isBusy) recorder.setQuality(value) },
                    enabled = !isBusy && !compatibility,
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) AccentCyan.copy(alpha = 0.12f) else BgCard,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) AccentCyan.copy(alpha = 0.6f) else BorderSubtle
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { if (!isBusy) recorder.setQuality(value) },
                            enabled = !isBusy && !compatibility,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = AccentCyan,
                                unselectedColor = TextSecondary
                            )
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) TextPrimary else TextSecondary
                                )
                                if (value == 1) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = AccentCyan.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "Rekomendasi",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AccentCyan,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = desc,
                                fontSize = 10.5.sp,
                                color = TextSecondary,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // --- 2. BITRATE STREAMING ---
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BITRATE VIDEO",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentCyan,
                    letterSpacing = 0.5.sp
                )
                if (compatibility) {
                    Text(
                        text = "Terkunci di 2 Mbps (Software Mode)",
                        fontSize = 10.sp,
                        color = AccentOrange
                    )
                }
            }

            val bitrateOptions = listOf(
                Pair(0, "4 Mbps • Hemat (~30 MB/mnt)"),
                Pair(1, "8 Mbps • Standar (~60 MB/mnt)"),
                Pair(2, "16 Mbps • Tinggi (~120 MB/mnt)"),
                Pair(3, "24 Mbps • Ultra Studio (~180 MB/mnt)")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                bitrateOptions.take(2).forEach { (idx, label) ->
                    val isSelected = bitrate == idx
                    Surface(
                        onClick = { if (!isBusy && !compatibility) recorder.setBitrate(idx) },
                        enabled = !isBusy && !compatibility,
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) AccentCyan.copy(alpha = 0.14f) else BgCard,
                        border = BorderStroke(1.dp, if (isSelected) AccentCyan.copy(alpha = 0.5f) else BorderSubtle),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) AccentCyan else TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                bitrateOptions.drop(2).forEach { (idx, label) ->
                    val isSelected = bitrate == idx
                    Surface(
                        onClick = { if (!isBusy && !compatibility) recorder.setBitrate(idx) },
                        enabled = !isBusy && !compatibility,
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) AccentCyan.copy(alpha = 0.14f) else BgCard,
                        border = BorderStroke(1.dp, if (isSelected) AccentCyan.copy(alpha = 0.5f) else BorderSubtle),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) AccentCyan else TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// SUBPAGE 2: SUMBER AUDIO
// ============================================================
@Composable
private fun RecorderAudioSection(
    recorder: HudScreenRecorder,
    audio: Int,
    isBusy: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "SUMBER SUARA REKAMAN",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = AccentCyan,
            letterSpacing = 0.5.sp
        )

        val audioOptions = listOf(
            Triple(0, "Tanpa Suara (Mati)", "Merekam video tanpa trek audio. Ukuran berkas paling kecil & hemat storage."),
            Triple(1, "Mikrofon Saja", "Hanya merekam mikrofon eksternal (suara Anda) tanpa suara sistem/game."),
            Triple(2, "Audio Game Murni (Internal)", "Merekam suara in-game 100% digital tanpa menangkap kebisingan lingkungan luar."),
            Triple(3, "Campuran (Game + Mikrofon)", "Menggabungkan suara game dan komentar mic secara bersamaan untuk video konten YouTube/TikTok.")
        )

        val audioIcons = listOf(
            Icons.Default.VolumeOff,
            Icons.Default.Mic,
            Icons.Default.VolumeUp,
            Icons.Default.Headphones
        )

        audioOptions.forEachIndexed { index, (value, title, desc) ->
            val isSelected = audio == value
            Surface(
                onClick = { if (!isBusy) recorder.setAudioSource(value) },
                enabled = !isBusy,
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) AccentCyan.copy(alpha = 0.12f) else BgCard,
                border = BorderStroke(1.dp, if (isSelected) AccentCyan.copy(alpha = 0.6f) else BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) AccentCyan.copy(alpha = 0.2f) else BgElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = audioIcons.getOrElse(index) { Icons.Default.Mic },
                            contentDescription = null,
                            tint = if (isSelected) AccentCyan else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) TextPrimary else TextSecondary
                            )
                            if (value == 2) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = AccentCyan.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "Fitur Unggulan",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentCyan,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = desc,
                            fontSize = 10.5.sp,
                            color = TextSecondary,
                            lineHeight = 14.sp
                        )
                    }

                    RadioButton(
                        selected = isSelected,
                        onClick = { if (!isBusy) recorder.setAudioSource(value) },
                        enabled = !isBusy,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = AccentCyan,
                            unselectedColor = TextSecondary
                        )
                    )
                }
            }
        }

        // Info Root Audio Capture
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = BgCard,
            border = BorderStroke(0.8.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Fitur Audio Internal berjalan langsung pada level kernel AOSP dengan enkoder AAC stereo 48 kHz 192 kbps.",
                    fontSize = 10.5.sp,
                    color = TextSecondary,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

// ============================================================
// SUBPAGE 3: FITUR LANJUTAN & PRIVASI
// ============================================================
@Composable
private fun RecorderAdvancedSection(
    recorder: HudScreenRecorder,
    compatibility: Boolean,
    stealth: Boolean,
    isBusy: Boolean,
    softwareActive: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "PENGATURAN PRIVASI & ENCODER",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = AccentCyan,
            letterSpacing = 0.5.sp
        )

        // 1. Stealth Overlay Switch
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = BgCard,
            border = BorderStroke(0.8.dp, if (stealth) AccentGreen.copy(alpha = 0.5f) else BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (stealth) AccentGreen.copy(alpha = 0.15f) else BgElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = if (stealth) AccentGreen else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Stealth Overlay (Anti-Bocor)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Sembunyikan jendela apung, dock, dan HUD telemetri dari hasil rekaman video agar layar tetap bersih.",
                            fontSize = 10.5.sp,
                            color = TextSecondary,
                            lineHeight = 14.sp
                        )
                    }
                }

                Switch(
                    checked = stealth,
                    onCheckedChange = recorder::setStealthOverlay,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentGreen,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = BgElevated,
                        uncheckedBorderColor = BorderSubtle
                    ),
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }
        }

        // 2. Compatibility Switch
        val isCompatEffective = if (isBusy) compatibility || softwareActive else compatibility
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = BgCard,
            border = BorderStroke(0.8.dp, if (isCompatEffective) AccentOrange.copy(alpha = 0.5f) else BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isCompatEffective) AccentOrange.copy(alpha = 0.15f) else BgElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = if (isCompatEffective) AccentOrange else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Software Codec Fallback",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Gunakan enkoder perangkat lunak jika enkoder perangkat keras GPU mengalami crash pada ROM kustom.",
                            fontSize = 10.5.sp,
                            color = TextSecondary,
                            lineHeight = 14.sp
                        )
                    }
                }

                Switch(
                    checked = isCompatEffective,
                    onCheckedChange = { if (!isBusy) recorder.setCompatibility(it) },
                    enabled = !isBusy,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentOrange,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = BgElevated,
                        uncheckedBorderColor = BorderSubtle
                    ),
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }
        }

        // Storage & Performance Guidelines Card
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = BgCard,
            border = BorderStroke(0.8.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SdStorage,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "MANAJEMEN PENYIMPANAN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan
                    )
                }
                Text(
                    text = "• Rekaman otomatis dipecah menjadi beberapa segmen jika batas waktu 3 menit tercapai (pembatasan AOSP).\n• Rekaman yang dihentikan mendadak saat game force-close akan secara otomatis dipulihkan saat Lynx Companion dibuka kembali.",
                    fontSize = 10.5.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )
            }
        }
    }
}
