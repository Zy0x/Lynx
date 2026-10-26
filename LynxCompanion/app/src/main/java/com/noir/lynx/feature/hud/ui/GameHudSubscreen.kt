package com.noir.lynx.feature.hud.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.feature.hud.data.HudPreferencesStore
import com.noir.lynx.ui.LynxViewModel
import com.noir.lynx.ui.setHudMode
import com.noir.lynx.ui.setHudStyle
import com.noir.lynx.ui.toggleGameHud

/**
 * Luxury Material 3 Cyberpunk Lynx Engine Master Control Subscreen.
 * Elegant bento-card hierarchy, touch-first >=48dp controls, and comprehensive add-in preview.
 */
@Composable
fun GameHudSubscreen(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var permission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permission = Settings.canDrawOverlays(context)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val store = remember(context) { HudPreferencesStore(context) }
    val prefs by store.changes.collectAsState(initial = store.read())

    HudTheme {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── 1. Hero Bento Card: Lynx Engine Master Toggle ───────────
            val isActive = uiState.isGameHudActive
            val heroBorderColor by animateColorAsState(
                targetValue = if (isActive) HudCyan.copy(alpha = 0.65f) else Color(0xFF1E2638),
                label = "HeroBorder"
            )

            Surface(
                color = Color(0xFF131722),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.2.dp, heroBorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = if (isActive) HudCyan.copy(alpha = 0.16f) else Color(0xFF1B2130),
                                shape = CircleShape,
                                border = BorderStroke(
                                    1.dp,
                                    if (isActive) HudCyan.copy(alpha = 0.5f) else Color(0xFF28344A)
                                ),
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.SportsEsports,
                                        contentDescription = null,
                                        tint = if (isActive) HudCyan else HudMuted,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Lynx Engine",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (isActive) "Aktif · Bilah Samping Siap" else "Siaga · Ketuk untuk Mengaktifkan",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isActive) HudCyan else HudMuted
                                )
                            }
                        }

                        Switch(
                            checked = isActive,
                            onCheckedChange = { viewModel.toggleGameHud(context, it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = HudCyan,
                                uncheckedThumbColor = HudMuted,
                                uncheckedTrackColor = Color(0xFF202B37)
                            ),
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }

                    // Permission warning banner if permission is missing
                    if (!permission) {
                        Surface(
                            color = HudOrange.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, HudOrange.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = HudOrange,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = hudString(R.string.hud_permission),
                                        fontSize = 11.5.sp,
                                        color = Color.White
                                    )
                                }
                                HudAction(
                                    label = hudString(R.string.hud_permission_action),
                                    onClick = {
                                        context.startActivity(
                                            Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}")
                                            )
                                        )
                                    },
                                    modifier = Modifier.heightIn(min = 40.dp)
                                )
                            }
                        }
                    }

                    // Status Pills Bar (Quick Specs)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EngineStatusBadge(
                            label = if (uiState.hudMode == 0) "Bilah Samping" else "Klasik Floating",
                            icon = if (uiState.hudMode == 0) Icons.Default.Dock else Icons.Default.FlipToFront,
                            accentColor = HudBlue,
                            modifier = Modifier.weight(1f)
                        )
                        EngineStatusBadge(
                            label = if (permission) "Izin Layar OK" else "Butuh Izin",
                            icon = if (permission) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                            accentColor = if (permission) HudCyan else HudOrange,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── 2. Interactive Layout Mode Selector ─────────────────────
            HudSection(
                title = hudString(R.string.hud_mode_title),
                badge = hudString(if (uiState.hudMode == 0) R.string.hud_sidebar else R.string.hud_classic),
                accentColor = HudCyan
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModeSelectionCard(
                        title = hudString(R.string.hud_sidebar),
                        description = "Gaya XBoost Game Dock dengan bilah geser di tepi layar dan telemetri lengkap",
                        icon = Icons.Default.Dock,
                        selected = uiState.hudMode == 0,
                        onClick = { viewModel.setHudMode(context, 0) },
                        modifier = Modifier.weight(1f)
                    )
                    ModeSelectionCard(
                        title = hudString(R.string.hud_classic),
                        description = "Overlay floating minimalis yang selalu melayang di atas permainan Anda",
                        icon = Icons.Default.FlipToFront,
                        selected = uiState.hudMode == 1,
                        onClick = { viewModel.setHudMode(context, 1) },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (uiState.hudMode == 1) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Pilih Gaya Klasik:",
                        fontSize = 11.5.sp,
                        color = HudMuted
                    )
                    val styles = listOf(
                        R.string.hud_style_pillar, R.string.hud_style_ribbon, R.string.hud_style_esport,
                        R.string.hud_style_tiles, R.string.hud_style_deck, R.string.hud_style_ghost
                    ).map { hudString(it) }
                    HudAdaptiveGrid(styles, maximum = 2) { index ->
                        HudChoice(
                            label = styles[index],
                            selected = uiState.hudStyle == index + 1,
                            onClick = { viewModel.setHudStyle(context, index + 1) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── 3. Screen & Audio Recorder Master Configuration ─────────
            HudRecorderMasterCard(context = context)

            // ── 4. Features & Add-ins Showcase ──────────────────────────
            HudSection(
                title = "Fitur Permainan & Add-in Bawaan",
                badge = "8 Fitur Siap",
                accentColor = HudBlue
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AddinFeatureChip(
                            title = "Perekam Layar MP4",
                            subtitle = "Indikator Capsule Pill",
                            icon = Icons.Default.Videocam,
                            accentColor = HudRed,
                            modifier = Modifier.weight(1f)
                        )
                        AddinFeatureChip(
                            title = "Tangkapan Layar",
                            subtitle = "Mini Preview Galeri",
                            icon = Icons.Default.CameraAlt,
                            accentColor = HudCyan,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AddinFeatureChip(
                            title = "Pembersih RAM",
                            subtitle = "Drop Caches & Feedback",
                            icon = Icons.Default.Bolt,
                            accentColor = HudCyan,
                            modifier = Modifier.weight(1f)
                        )
                        AddinFeatureChip(
                            title = "Bypass Charging",
                            subtitle = "Driver PMIC Arus Langsung",
                            icon = Icons.Default.BatteryChargingFull,
                            accentColor = HudOrange,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AddinFeatureChip(
                            title = "Touch Master",
                            subtitle = "Multi-OEM TouchBoost",
                            icon = Icons.Default.TouchApp,
                            accentColor = HudBlue,
                            modifier = Modifier.weight(1f)
                        )
                        AddinFeatureChip(
                            title = "Game Network",
                            subtitle = "Wi-Fi Low-Latency Mode",
                            icon = Icons.Default.NetworkCheck,
                            accentColor = HudCyan,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── 4. Mini Floating Overlay Preferences ────────────────────
            HudExpandableSection(
                title = hudString(R.string.hud_mini_title),
                detail = hudString(R.string.hud_selected_count, prefs.selected.size)
            ) {
                HudMetricSelection(prefs, store)
            }

            // ── 5. Freeform & Floating Windows Settings ─────────────────
            HudExpandableSection(
                title = hudString(R.string.hud_floating_short),
                detail = hudString(
                    when {
                        !prefs.floatingEnabled -> R.string.hud_off
                        prefs.floatingEngine == "standalone" -> R.string.floating_engine_root
                        else -> R.string.floating_engine_rom
                    }
                )
            ) {
                HudFloatingSettings(prefs, store)
            }
        }
    }
}

@Composable
private fun EngineStatusBadge(
    label: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF161A26),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF222B3D)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ModeSelectionCard(
    title: String,
    description: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) HudCyan.copy(alpha = 0.7f) else Color(0xFF1E2638),
        label = "ModeBorder"
    )
    val bgColor by animateColorAsState(
        targetValue = if (selected) HudCyan.copy(alpha = 0.12f) else HudElevated,
        label = "ModeBg"
    )

    Surface(
        onClick = onClick,
        color = bgColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.2.dp, borderColor),
        modifier = modifier.heightIn(min = 105.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) HudCyan else HudMuted,
                    modifier = Modifier.size(22.dp)
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = HudCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = title,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else Color(0xFFD0D4DC)
            )
            Text(
                text = description,
                fontSize = 10.5.sp,
                color = HudMuted,
                lineHeight = 13.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AddinFeatureChip(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF151924),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF1E2638)),
        modifier = modifier.heightIn(min = 52.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Surface(
                color = accentColor.copy(alpha = 0.14f),
                shape = CircleShape,
                modifier = Modifier.size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = HudMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HudRecorderMasterCard(
    context: android.content.Context,
    modifier: Modifier = Modifier
) {
    val recorder = remember(context) { com.noir.lynx.feature.hud.addins.HudGamingAddins.current?.recorder ?: com.noir.lynx.feature.hud.addins.HudScreenRecorder(context) }
    val state by recorder.state.collectAsState()
    val quality by recorder.quality.collectAsState()
    val audioSource by recorder.audioSource.collectAsState()
    val bitrate by recorder.bitrate.collectAsState()
    val fps by recorder.fps.collectAsState()
    val compatibility by recorder.compatibility.collectAsState()
    val stealthOverlay by recorder.stealthOverlay.collectAsState()

    HudSection(
        title = "Perekam Layar & Audio Game",
        badge = if (compatibility) "Codec2 AVC" else "Hardware AVC",
        accentColor = HudRed
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Live resolution indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Konfigurasi perekaman video, frame rate & stream suara game",
                    fontSize = 11.5.sp,
                    color = HudMuted,
                    modifier = Modifier.weight(1f)
                )
                recorder.outputSize()?.let { size ->
                    Surface(
                        color = HudRed.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, HudRed.copy(alpha = 0.4f)),
                        modifier = Modifier.padding(start = 6.dp)
                    ) {
                        Text(
                            text = "${size.first}×${size.second}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = HudRed,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // 1. Audio Source Selector
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Sumber Audio (Suara):",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                val audioLabels = listOf("Bisu", "Mikrofon", "Internal", "Internal+Mic")
                val audioIcons = listOf(Icons.Default.VolumeOff, Icons.Default.Mic, Icons.Default.VolumeUp, Icons.Default.Headphones)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    audioLabels.forEachIndexed { index, label ->
                        val isSelected = audioSource == index
                        Surface(
                            onClick = { recorder.setAudioSource(index) },
                            color = if (isSelected) HudRed.copy(alpha = 0.2f) else Color(0xFF151924),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (isSelected) HudRed else Color(0xFF1E2638)),
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = audioIcons[index],
                                    contentDescription = null,
                                    tint = if (isSelected) HudRed else HudMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color(0xFFB0B4C0),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // 2. Video Resolution Picker
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Resolusi Layar:",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                val resLabels = listOf("720p HD", "1080p FHD", "Native Layar")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    resLabels.forEachIndexed { index, label ->
                        val isSelected = quality == index
                        Surface(
                            onClick = { recorder.setQuality(index) },
                            color = if (isSelected) HudCyan.copy(alpha = 0.18f) else Color(0xFF151924),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (isSelected) HudCyan else Color(0xFF1E2638)),
                            modifier = Modifier.weight(1f).heightIn(min = 38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color(0xFFB0B4C0)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Bitrate & Target FPS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Bitrate
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Bitrate Video:",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    val bitrateLabels = listOf("4M", "8M", "16M", "24M")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        bitrateLabels.forEachIndexed { index, label ->
                            val isSelected = bitrate == index
                            Surface(
                                onClick = { recorder.setBitrate(index) },
                                color = if (isSelected) HudBlue.copy(alpha = 0.2f) else Color(0xFF151924),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isSelected) HudBlue else Color(0xFF1E2638)),
                                modifier = Modifier.weight(1f).heightIn(min = 34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                                    Text(
                                        text = label,
                                        fontSize = 10.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else Color(0xFFB0B4C0)
                                    )
                                }
                            }
                        }
                    }
                }

                // FPS
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Target FPS:",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    val fpsOptions = listOf(30, 60, 120)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        fpsOptions.forEach { target ->
                            val isSelected = fps == target
                            Surface(
                                onClick = { recorder.setFps(target) },
                                color = if (isSelected) HudOrange.copy(alpha = 0.2f) else Color(0xFF151924),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isSelected) HudOrange else Color(0xFF1E2638)),
                                modifier = Modifier.weight(1f).heightIn(min = 34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                                    Text(
                                        text = "$target",
                                        fontSize = 10.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else Color(0xFFB0B4C0)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Stealth & Compatibility Toggles
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Stealth Mode Toggle
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sembunyikan Pill (FLAG_SECURE)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Menggunakan Secure Layer agar tombol tidak tampak di MP4",
                            fontSize = 10.sp,
                            color = HudMuted
                        )
                    }
                    Switch(
                        checked = stealthOverlay,
                        onCheckedChange = recorder::setStealthOverlay,
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = HudCyan),
                        modifier = Modifier.heightIn(min = 40.dp)
                    )
                }

                // Codec Compatibility Toggle
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mode Kompatibilitas Codec2 AVC",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Wajib aktif di MediaTek untuk mencegah encoder error -38",
                            fontSize = 10.sp,
                            color = HudMuted
                        )
                    }
                    Switch(
                        checked = compatibility,
                        onCheckedChange = recorder::setCompatibility,
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = HudCyan),
                        modifier = Modifier.heightIn(min = 40.dp)
                    )
                }
            }

            // 5. Output Path Card
            Surface(
                color = Color(0xFF0F1219),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFF1E2638)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = HudCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Lokasi Berkas MP4:",
                            fontSize = 10.sp,
                            color = HudMuted
                        )
                        Text(
                            text = "/storage/emulated/0/Movies/Lynx",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                    if (state.lastUri != null && !state.busy) {
                        HudAction(
                            label = "Buka Galeri",
                            onClick = recorder::openLast,
                            modifier = Modifier.heightIn(min = 34.dp)
                        )
                    }
                }
            }
        }
    }
}
