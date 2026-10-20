package com.noir.lynx.feature.hud.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.data.LynxRepository
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.ui.LynxViewModel
import com.noir.lynx.ui.openBenchmarkDialog
import com.noir.lynx.ui.setHudMode
import com.noir.lynx.ui.setHudPinMiniFps
import com.noir.lynx.ui.setHudStyle
import com.noir.lynx.ui.toggleGameHud
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * GameHudSubscreen — Zen-Minimalist Floating Game HUD & Real-time OSD Overlay.
 *
 * Fully redesigned for psychological calm, zero redundancy, and clean visual hierarchy:
 * - Unified Hero Master Controller merging status, master switch, and benchmark shortcut.
 * - Symmetrical, tactile interaction mode selector (Edge Drawer vs Floating Window).
 * - Anti-overlap 6-tile bento catalog for OSD telemetry layout presets.
 * - Explicit overlay permission watchdog with direct root-grant & system settings bridge.
 * - Concise, professional copywriting completely free from AI brochure slop.
 */
@Composable
fun GameHudSubscreen(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val hasOverlayPermission = remember(uiState) {
        Settings.canDrawOverlays(context)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── 0. Overlay Permission Watchdog Card ─────────────────
        if (!hasOverlayPermission) {
            HudOverlayPermissionCard(
                onGrant = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.viewModelScope.launch(Dispatchers.IO) {
                        val granted = LynxRepository.grantOverlayPermission()
                        if (!granted || !Settings.canDrawOverlays(context)) {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            try {
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    }
                }
            )
        }

        // ── 1. Unified Hero Master Controller ───────────────────
        HudHeroMasterCard(
            isActive = uiState.isGameHudActive,
            hudMode = uiState.hudMode,
            onToggle = { enable ->
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.toggleGameHud(context, enable)
            },
            onOpenBenchmark = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.openBenchmarkDialog()
            }
        )

        // ── 2. Display & Interaction Mode Card ─────────────────
        HudInteractionModeCard(
            currentMode = uiState.hudMode,
            pinMiniFps = uiState.hudPinMiniFps,
            onSelectMode = { mode ->
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.setHudMode(context, mode)
            },
            onTogglePinMiniFps = { pin ->
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.setHudPinMiniFps(context, pin)
            }
        )

        // ── 3. Visual Layout Style Catalog ─────────────────────
        HudStyleCatalogCard(
            selectedStyle = uiState.hudStyle,
            onSelectStyle = { styleId ->
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.setHudStyle(context, styleId)
            }
        )

        HudMiniSettings()

        Spacer(Modifier.height(16.dp))
    }
}

/**
 * HudOverlayPermissionCard — Explanatory alert card shown when overlay permission is revoked or missing.
 */
@Composable
private fun HudOverlayPermissionCard(
    onGrant: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF191410),
        border = BorderStroke(1.dp, Color(0x66FFB300)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x33FFB300),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Izin Overlay Belum Aktif",
                        color = Color(0xFFFFB300),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Aktifkan izin 'Tampilkan di atas aplikasi lain' agar HUD dapat muncul di layar game",
                        color = TextSecondary.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        lineHeight = 13.sp
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Surface(
                onClick = onGrant,
                shape = RoundedCornerShape(8.dp),
                color = Color(0x28FFB300),
                border = BorderStroke(0.8.dp, Color(0x88FFB300)),
                modifier = Modifier.fillMaxWidth().height(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Beri Izin Overlay",
                        color = Color(0xFFFFB300),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * HudHeroMasterCard — High-presence Zen Master Controller.
 * Consolidates master toggle, real-time status pulse, contextual hint, and benchmark access.
 */
@Composable
private fun HudHeroMasterCard(
    isActive: Boolean,
    hudMode: Int,
    onToggle: (Boolean) -> Unit,
    onOpenBenchmark: () -> Unit
) {
    val activeColor = if (isActive) AccentCyan else TextSecondary.copy(alpha = 0.5f)
    val statusBg = if (isActive) Color(0x1800E5FF) else Color(0x10FFFFFF)
    val statusBorder = if (isActive) Color(0x3500E5FF) else Color(0x15FFFFFF)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF10141F),
        border = BorderStroke(
            0.8.dp,
            Brush.verticalGradient(
                listOf(
                    if (isActive) AccentCyan.copy(alpha = 0.35f) else Color(0x22FFFFFF),
                    Color(0x10FFFFFF)
                )
            )
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Row: Zen Pulse Status & Active Mode Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Surface(
                            shape = CircleShape,
                            color = activeColor.copy(alpha = 0.20f),
                            modifier = Modifier.size(12.dp)
                        ) {}
                        Surface(
                            shape = CircleShape,
                            color = activeColor,
                            modifier = Modifier.size(6.dp)
                        ) {}
                    }
                    Text(
                        text = if (isActive) "HUD Aktif di Layar" else "HUD Nonaktif",
                        color = if (isActive) AccentCyan else TextSecondary.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusBg,
                    border = BorderStroke(0.5.dp, statusBorder)
                ) {
                    Text(
                        text = if (hudMode == 0) "Bilah Sisi" else "Jendela Bebas",
                        color = if (isActive) AccentCyan.copy(alpha = 0.9f) else TextSecondary.copy(alpha = 0.6f),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Main Row: Icon + Title/Desc + Master Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AccentCyan.copy(alpha = 0.12f),
                    border = BorderStroke(0.6.dp, AccentCyan.copy(alpha = 0.30f)),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Floating Game HUD",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Overlay real-time FPS, suhu, clock, dan daya saat game aktif",
                        color = TextSecondary.copy(alpha = 0.70f),
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Switch(
                        checked = isActive,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BgDeepOled,
                            checkedTrackColor = AccentCyan,
                            uncheckedThumbColor = TextSecondary.copy(alpha = 0.5f),
                            uncheckedTrackColor = Color(0xFF141824)
                        ),
                        modifier = Modifier.scale(0.85f)
                    )
                }
            }

            // Bottom Strip: Contextual Hint + Inline Benchmark Shortcut
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Color(0x15FFFFFF), thickness = 0.5.dp)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (hudMode == 0) "Geser dari tepi layar untuk membuka kontrol" else "Tahan & geser jendela untuk atur posisi layar",
                    color = TextSecondary.copy(alpha = 0.60f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )

                Surface(
                    onClick = onOpenBenchmark,
                    shape = RoundedCornerShape(8.dp),
                    color = AccentPurple.copy(alpha = 0.12f),
                    border = BorderStroke(0.6.dp, AccentPurple.copy(alpha = 0.40f)),
                    modifier = Modifier.height(30.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = AccentPurple,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Uji Pacing",
                            color = AccentPurple,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * HudInteractionModeCard — Interactive Mechanism Selector.
 * Offers clean 2-way segmented choices (Edge Drawer vs Floating Window) with progressive pin option.
 */
@Composable
private fun HudInteractionModeCard(
    currentMode: Int,
    pinMiniFps: Boolean,
    onSelectMode: (Int) -> Unit,
    onTogglePinMiniFps: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF10141F),
        border = BorderStroke(0.7.dp, Color(0x18FFFFFF)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Card Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF141824),
                    border = BorderStroke(0.5.dp, Color(0x1AFFFFFF)),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(Modifier.width(9.dp))
                Column {
                    Text(
                        text = "Mode Tampilan Layar",
                        color = TextPrimary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Mekanisme interaksi overlay saat aplikasi aktif",
                        color = TextSecondary.copy(alpha = 0.65f),
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 2-Way Symmetrical Mode Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Mode 0: Edge Drawer
                ModeSelectionTile(
                    title = "Bilah Sisi",
                    subtitle = "Tepi layar • Geser buka",
                    icon = Icons.Default.ViewAgenda,
                    isSelected = currentMode == 0,
                    onClick = { onSelectMode(0) },
                    modifier = Modifier.weight(1f)
                )

                // Mode 1: Floating Window
                ModeSelectionTile(
                    title = "Jendela Bebas",
                    subtitle = "Melayang • Drag bebas",
                    icon = Icons.Default.Layers,
                    isSelected = currentMode == 1,
                    onClick = { onSelectMode(1) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Progressive Disclosure: Pin Mini FPS (Edge Drawer only)
            AnimatedVisibility(
                visible = currentMode == 0,
                enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
            ) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF0C0F17),
                        border = BorderStroke(0.5.dp, Color(0x15FFFFFF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = null,
                                    tint = if (pinMiniFps) AccentCyan else TextSecondary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Pin Mini FPS di Sisi Layar",
                                        color = if (pinMiniFps) AccentCyan else TextPrimary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Tampilkan angka FPS mini saat bilah tertutup",
                                        color = TextSecondary.copy(alpha = 0.65f),
                                        fontSize = 9.5.sp
                                    )
                                }
                            }

                            Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                Switch(
                                    checked = pinMiniFps,
                                    onCheckedChange = onTogglePinMiniFps,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = BgDeepOled,
                                        checkedTrackColor = AccentCyan,
                                        uncheckedThumbColor = TextSecondary.copy(alpha = 0.5f),
                                        uncheckedTrackColor = Color(0xFF141824)
                                    ),
                                    modifier = Modifier.scale(0.80f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * ModeSelectionTile — Ergonomic selection tile for HUD interaction modes.
 */
@Composable
private fun ModeSelectionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedBg by animateColorAsState(
        targetValue = if (isSelected) AccentCyan.copy(alpha = 0.12f) else Color(0xFF0C0F17),
        animationSpec = tween(180),
        label = "mode_tile_bg"
    )
    val animatedBorder by animateColorAsState(
        targetValue = if (isSelected) AccentCyan.copy(alpha = 0.55f) else Color(0x18FFFFFF),
        animationSpec = tween(180),
        label = "mode_tile_border"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = animatedBg,
        border = BorderStroke(if (isSelected) 1.dp else 0.6.dp, animatedBorder),
        modifier = modifier.heightIn(min = 64.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = BorderStroke(1.5.dp, AccentCyan),
                        modifier = Modifier.size(16.dp)
                    ) {}
                    Surface(
                        shape = CircleShape,
                        color = AccentCyan,
                        modifier = Modifier.size(7.dp)
                    ) {}
                } else {
                    Surface(
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, Color(0x35FFFFFF)),
                        modifier = Modifier.size(16.dp)
                    ) {}
                }
            }

            Spacer(Modifier.width(9.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (isSelected) AccentCyan else TextPrimary,
                    fontSize = 11.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    color = TextSecondary.copy(alpha = 0.65f),
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * HudStyleCatalogCard — Bento Grid Catalog for 6 OSD Layout Styles.
 * Anti-overlap, clean visual typography, and clear selection dots.
 */
@Composable
private fun HudStyleCatalogCard(
    selectedStyle: Int,
    onSelectStyle: (Int) -> Unit
) {
    val styleOptions = listOf(
        Triple(1, "Vertikal RTSS", "Kolom ramping"),
        Triple(2, "Pita Atas", "Baris horizontal"),
        Triple(3, "Blok Ganda", "Panel FPS & suhu"),
        Triple(4, "Modular 4", "Empat kuadran"),
        Triple(5, "Steam Deck", "Banner konsol"),
        Triple(6, "Ghost Text", "Tanpa latar")
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF10141F),
        border = BorderStroke(0.7.dp, Color(0x18FFFFFF)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF141824),
                        border = BorderStroke(0.5.dp, Color(0x1AFFFFFF)),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(9.dp))
                    Column {
                        Text(
                            text = "Gaya Visual Grafis OSD",
                            color = TextPrimary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Format tata letak grafik telemetri pada overlay",
                            color = TextSecondary.copy(alpha = 0.65f),
                            fontSize = 10.sp
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF141824),
                    border = BorderStroke(0.5.dp, Color(0x18FFFFFF))
                ) {
                    Text(
                        text = "Format $selectedStyle",
                        color = AccentCyan.copy(alpha = 0.85f),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 2x3 Responsive Bento Grid
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                styleOptions.chunked(2).forEach { rowStyles ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        rowStyles.forEach { (styleId, label, hint) ->
                            val isSel = selectedStyle == styleId
                            val animatedBg by animateColorAsState(
                                targetValue = if (isSel) AccentCyan.copy(alpha = 0.14f) else Color(0xFF0C0F17),
                                animationSpec = tween(180),
                                label = "style_bg_$styleId"
                            )
                            val animatedBorder by animateColorAsState(
                                targetValue = if (isSel) AccentCyan.copy(alpha = 0.60f) else Color(0x15FFFFFF),
                                animationSpec = tween(180),
                                label = "style_border_$styleId"
                            )

                            Surface(
                                onClick = { onSelectStyle(styleId) },
                                shape = RoundedCornerShape(10.dp),
                                color = animatedBg,
                                border = BorderStroke(if (isSel) 1.dp else 0.6.dp, animatedBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Selection Dot
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(if (isSel) AccentCyan else Color(0x35FFFFFF))
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = label,
                                            color = if (isSel) AccentCyan else TextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = hint,
                                            color = TextSecondary.copy(alpha = 0.55f),
                                            fontSize = 8.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
