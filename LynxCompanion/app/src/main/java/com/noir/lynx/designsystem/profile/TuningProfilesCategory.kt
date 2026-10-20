package com.noir.lynx.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.AppProfileRule
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.profile.*
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.profiles.ProfileDomain
import com.noir.lynx.ui.AppIconImage

import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween

/**
 * ProfilesCategoryTabRow — High-performance Segmented Tab Pill Row for Kernel Performance Profiles.
 * Redesigned for visual calm, ergonomic tactile feedback, and unified dark aesthetic.
 */
@Composable
fun ProfilesCategoryTabRow(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val tabs = listOf(
        Triple(0, "Katalog Profil", Icons.Default.Tune),
        Triple(1, "Otomasi Aplikasi", Icons.Default.AutoMode)
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF10141E),
        border = BorderStroke(0.6.dp, Color(0x1AFFFFFF))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.5.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            tabs.forEach { (index, title, icon) ->
                val isSelected = selectedTab == index
                val animatedBg by androidx.compose.animation.animateColorAsState(
                    targetValue = if (isSelected) Color(0xFF1E2638) else Color.Transparent,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 180),
                    label = "tab_bg_$index"
                )
                val animatedText by androidx.compose.animation.animateColorAsState(
                    targetValue = if (isSelected) TextPrimary else TextSecondary.copy(alpha = 0.65f),
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 180),
                    label = "tab_text_$index"
                )
                val animatedIcon by androidx.compose.animation.animateColorAsState(
                    targetValue = if (isSelected) Color(0xFFD0DCF0) else TextSecondary.copy(alpha = 0.5f),
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 180),
                    label = "tab_icon_$index"
                )

                Surface(
                    onClick = {
                        if (!isSelected) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectTab(index)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(9.dp),
                    color = animatedBg,
                    border = if (isSelected) {
                        BorderStroke(
                            0.6.dp,
                            Brush.verticalGradient(
                                listOf(
                                    Color(0x35FFFFFF),
                                    Color(0x10FFFFFF)
                                )
                            )
                        )
                    } else null
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = animatedIcon,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = title,
                            color = animatedText,
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            letterSpacing = (-0.1).sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * ProfilesFeedbackBottomSheet — Elegant Dark Minimal ModalBottomSheet for Notification & Feedback Style Selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesFeedbackBottomSheet(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    context: Context,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        scrimColor = Color.Black.copy(alpha = 0.72f),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = BorderGlass,
                    shape = RoundedCornerShape(3.dp),
                    modifier = Modifier.size(width = 36.dp, height = 3.dp)
                ) {}
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(top = 6.dp, bottom = 32.dp)
        ) {
            // ── Header ────────────────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                // Minimal icon tile — no heavy fill
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(AccentCyan.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = AccentCyan.copy(alpha = 0.85f),
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column {
                    Text(
                        text = "Gaya Notifikasi & Feedback",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    )
                    Text(
                        text = "Visual umpan balik saat profil performa beralih",
                        color = TextSecondary.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }

            // ── Option List ───────────────────────────────────────────────────────────
            val feedbackOptions = listOf(
                Triple("jewel_top", "Jewel Dynamic Island (HUD)", "Kapsul permata melayang di atas layar — Cyberpunk HUD"),
                Triple("jewel_bottom", "Jewel Bottom Capsule (HUD)", "Kapsul permata melayang di bawah layar — Cyberpunk HUD"),
                Triple("system_toast", "System Toast (OS Text)", "Gelembung teks standar Android • Ringan (~8 MB RAM)"),
                Triple("status_bar", "Status Bar Saja (0 MB RAM)", "Notifikasi hening di status bar • Tanpa pop-up layar"),
                Triple("disabled", "Nonaktifkan Feedback", "Hening total — tanpa notifikasi atau pop-up visual")
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                feedbackOptions.forEach { (key, title, subtitle) ->
                    val isSelected = uiState.modeFeedbackStyle == key
                    Surface(
                        onClick = {
                            viewModel.setModeFeedbackStyle(context, key)
                            when (key) {
                                "jewel_top", "jewel_bottom" -> {
                                    viewModel.triggerModeFeedback("balance", "Seimbang", 0xFF00A3FFL)
                                }
                                "system_toast" -> {
                                    viewModel.dismissModeFeedback()
                                    android.widget.Toast.makeText(context, "⚖️ Lynx: Mode Seimbang", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                "status_bar" -> {
                                    viewModel.dismissModeFeedback()
                                    viewModel.triggerUniversalToast("Status Bar Saja (0 MB RAM)", statusTag = "HENING", type = com.noir.lynx.data.ToastType.INFO, accentColorHex = 0xFF00E5FF, durationMs = 1500L)
                                }
                                "disabled" -> {
                                    viewModel.dismissModeFeedback()
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) AccentCyan.copy(alpha = 0.09f) else BgElevated,
                        border = BorderStroke(
                            if (isSelected) 1.dp else 0.6.dp,
                            if (isSelected) AccentCyan.copy(alpha = 0.7f) else BorderSubtle
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left: Title + description
                            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = title,
                                        color = if (isSelected) AccentCyan else TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                    )
                                    if (key == "jewel_top") {
                                        Surface(
                                            shape = RoundedCornerShape(3.dp),
                                            color = AccentCyan.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "DEFAULT",
                                                color = AccentCyan.copy(alpha = 0.9f),
                                                fontSize = 7.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.4.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(1.5.dp))
                                Text(
                                    text = subtitle,
                                    color = TextSecondary.copy(alpha = 0.6f),
                                    fontSize = 9.5.sp,
                                    lineHeight = 13.sp
                                )
                            }

                            // Right: Radio indicator — clean filled dot
                            Box(
                                modifier = Modifier.size(18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    // Outer ring
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Transparent,
                                        border = BorderStroke(1.5.dp, AccentCyan),
                                        modifier = Modifier.size(18.dp)
                                    ) {}
                                    // Inner filled dot
                                    Surface(
                                        shape = CircleShape,
                                        color = AccentCyan,
                                        modifier = Modifier.size(8.dp)
                                    ) {}
                                } else {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Transparent,
                                        border = BorderStroke(1.dp, BorderGlass),
                                        modifier = Modifier.size(18.dp)
                                    ) {}
                                }
                            }
                        }
                    }
                }
            }

            // ── Divider ───────────────────────────────────────────────────────────────
            HorizontalDivider(
                color = BorderSubtle,
                thickness = 0.7.dp,
                modifier = Modifier.padding(vertical = 14.dp)
            )

            // ── Dual Morph Switch ─────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "Sinkronisasi Morpher Header",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(1.5.dp))
                    Text(
                        text = "Teks header ikut bertransisi serasi saat Jewel Toast aktif",
                        color = TextSecondary.copy(alpha = 0.65f),
                        fontSize = 10.sp,
                        lineHeight = 13.sp
                    )
                }
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Switch(
                        checked = uiState.modeFeedbackDualMorph,
                        onCheckedChange = { viewModel.setModeFeedbackDualMorph(context, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BgDeepOled,
                            checkedTrackColor = AccentCyan,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = BgElevated,
                            uncheckedBorderColor = BorderSubtle
                        )
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Live Test Button (Mode-Aware Feedback Tester) ─────────────────────────
            Surface(
                onClick = {
                    val sampleModes = listOf(
                        Triple("powersave", "Hemat", 0xFF00E676L),
                        Triple("balance", "Seimbang", 0xFF00A3FFL),
                        Triple("performance", "Performa", 0xFFFF9100L),
                        Triple("extreme", "Ekstrem", 0xFFFF3D00L)
                    )
                    val randomSample = sampleModes.random()

                    when (uiState.modeFeedbackStyle) {
                        "jewel_top", "jewel_bottom" -> {
                            viewModel.triggerModeFeedback(randomSample.first, randomSample.second, randomSample.third)
                        }
                        "system_toast" -> {
                            val emoji = when (randomSample.first) {
                                "powersave" -> "🍃"
                                "balance" -> "⚖️"
                                "performance" -> "⚡"
                                "extreme" -> "🔥"
                                else -> "⚖️"
                            }
                            android.widget.Toast.makeText(context, "$emoji Lynx: Mode ${randomSample.second}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        "status_bar" -> {
                            viewModel.triggerUniversalToast("Status Bar Saja (Hening)", statusTag = "HENING", type = com.noir.lynx.data.ToastType.INFO, accentColorHex = 0xFF00E5FF, durationMs = 1500L)
                        }
                        "disabled" -> {
                            viewModel.triggerUniversalToast("Feedback Dinonaktifkan (Hening Total)", statusTag = "OFF", type = com.noir.lynx.data.ToastType.INFO, accentColorHex = 0xFF888888, durationMs = 1500L)
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Uji Coba Animasi Toast (Live Test)",
                        color = AccentCyan,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * tuningProfilesCategoryItems — Modular LazyListScope items for Kernel Performance Profile Subscreen.
 *
 * Tab 0: Katalog Profil
 * - Active State Hero Card
 * - 2x2 Bento Quick-Switch Grid
 *
 * Tab 1: Otomasi Aplikasi
 * - Master Automation & Cooldown Configuration Card
 * - Virtualized App Profile Rule Items (with one-tap profile cycle pill)
 */
fun LazyListScope.tuningProfilesCategoryItems(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    context: Context,
    onAddAppClick: () -> Unit = {},
    onEditRuleClick: (AppProfileRule) -> Unit = {},
    onExtremeConfirmRequired: () -> Unit
) {
    val state = uiState.state

    when (uiState.selectedProfileTab) {
        0 -> {
            // ══════════════════════════════════════════════════════════
            // TAB 0: PROFILE
            // ══════════════════════════════════════════════════════════

            // ── 1. Active Status Hero Card ─────────────────────────
            item(key = "tab0_hero_active_card") {
                val activeProfileId = state.activeProfile
                val activeColor = when (activeProfileId.lowercase()) {
                    "powersave" -> AccentGreen
                    "balance" -> AccentBlue
                    "performance" -> AccentOrange
                    "extreme" -> AccentRed
                    "auto" -> AccentCyan
                    else -> AccentCyan
                }
                val activeIcon = when (activeProfileId.lowercase()) {
                    "powersave" -> Icons.Default.BatteryChargingFull
                    "balance" -> Icons.Default.Tune
                    "performance" -> Icons.Default.Bolt
                    "extreme" -> Icons.Default.LocalFireDepartment
                    "auto" -> Icons.Default.AutoMode
                    else -> Icons.Default.Widgets
                }
                val activeTitle = when (activeProfileId.lowercase()) {
                    "powersave" -> "Hemat Daya (Powersave)"
                    "balance" -> "Seimbang (Balance)"
                    "performance" -> "Performa Tinggi (Performance)"
                    "extreme" -> "Ekstrem (Extreme)"
                    "auto" -> "Auto (AI Adaptive)"
                    else -> activeProfileId.replaceFirstChar { it.uppercase() }
                }
                val activeDesc = when (activeProfileId.lowercase()) {
                    "powersave" -> "Efisiensi daya maksimal untuk penggunaan baterai tahan lama"
                    "balance" -> "Responsif, dingin, dan optimal untuk seluruh aktivitas harian"
                    "performance" -> "Prioritas clock tinggi, devfreq responsif, dan frame rate stabil"
                    "extreme" -> "Bypass batasan termal, clock maksimal tanpa batas untuk gaming berat"
                    "auto" -> "Otomatis berpindah profil performa saat bermain game dan kembali ke profil dasar"
                    else -> "Profil aktif saat ini"
                }

                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF10141F),
                        border = BorderStroke(
                            0.8.dp,
                            Brush.verticalGradient(
                                listOf(
                                    activeColor.copy(alpha = 0.28f),
                                    Color(0x15FFFFFF)
                                )
                            )
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Top Row: Zen Reassurance Status Bar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    // Subtle Zen Pulse Indicator
                                    Box(contentAlignment = Alignment.Center) {
                                        Surface(
                                            shape = CircleShape,
                                            color = activeColor.copy(alpha = 0.18f),
                                            modifier = Modifier.size(12.dp)
                                        ) {}
                                        Surface(
                                            shape = CircleShape,
                                            color = activeColor,
                                            modifier = Modifier.size(6.dp)
                                        ) {}
                                    }
                                    Text(
                                        text = if (activeProfileId == "auto") "Mesin Otomatis Adaptif" else "Profil Sistem Aktif",
                                        color = TextSecondary.copy(alpha = 0.85f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val isAutoRunning = uiState.isAppAutomationActive || com.noir.lynx.service.LynxAppAutomationService.isRunning
                                    if (isAutoRunning) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0x1400E676),
                                            border = BorderStroke(0.5.dp, Color(0x2800E676))
                                        ) {
                                            Text(
                                                text = "Otomasi Aktif",
                                                color = AccentGreen.copy(alpha = 0.9f),
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                            )
                                        }
                                    }

                                    val activeGamePkg = com.noir.lynx.service.LynxAppAutomationService.activeGamePackage
                                    val activeGameName = com.noir.lynx.service.LynxAppAutomationService.activeGameName
                                    if (state.profileOverrides.isNotEmpty() || activeGamePkg != null) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0x18FF9100),
                                            border = BorderStroke(0.5.dp, Color(0x35FF9100))
                                        ) {
                                            Text(
                                                text = if (activeGameName != null) "Override: ${activeGameName.take(14)}" else "Override Aktif",
                                                color = AccentOrange.copy(alpha = 0.9f),
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(13.dp))

                            // Main Row: Icon + Title & Desc
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = activeColor.copy(alpha = 0.10f),
                                    border = BorderStroke(0.6.dp, activeColor.copy(alpha = 0.25f)),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = activeIcon,
                                            contentDescription = null,
                                            tint = activeColor,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = activeTitle,
                                        color = TextPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = (-0.2).sp
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = activeDesc,
                                        color = TextSecondary.copy(alpha = 0.75f),
                                        fontSize = 11.5.sp,
                                        lineHeight = 15.sp
                                    )
                                }
                            }

                            // Subsystem telemetry specs (Calm unified ribbon)
                            Spacer(Modifier.height(13.dp))
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
                                    val cpuGov = uiState.clusters.firstOrNull()?.curGov?.ifEmpty { "Schedutil" } ?: "Schedutil"
                                    val thermalMode = state.thermal.mode.ifEmpty { "Dinamis" }.replaceFirstChar { it.uppercase() }
                                    val defaultGpuFallback = when (uiState.gpuInfo.platform) {
                                        "adreno" -> "Adreno Boost"
                                        "mali_ged", "mali_kbase" -> "GED Boost"
                                        else -> "GPU Boost"
                                    }
                                    val gpuState = if (uiState.telemetry != null && uiState.telemetry.gpuFreq > 0) "${uiState.telemetry.gpuFreq}MHz" else defaultGpuFallback

                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text("CPU", color = TextSecondary.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                        Text(cpuGov, color = TextPrimary.copy(alpha = 0.9f), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Text("•", color = TextSecondary.copy(alpha = 0.3f), fontSize = 9.sp)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text("GPU", color = TextSecondary.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                        Text(gpuState, color = TextPrimary.copy(alpha = 0.9f), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Text("•", color = TextSecondary.copy(alpha = 0.3f), fontSize = 9.sp)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text("Termal", color = TextSecondary.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                        Text(thermalMode, color = TextPrimary.copy(alpha = 0.9f), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }

                            val isAutoRunning = uiState.isAppAutomationActive || com.noir.lynx.service.LynxAppAutomationService.isRunning
                            val baselineId = com.noir.lynx.service.LynxAppAutomationService.currentBaseline
                            val baselineDisplayName = when (baselineId.lowercase().trim()) {
                                "performance" -> "Performa Tinggi"
                                "extreme" -> "Ekstrem"
                                "powersave" -> "Hemat Daya"
                                else -> "Seimbang"
                            }

                            if (isAutoRunning || activeProfileId == "auto") {
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Restore,
                                            contentDescription = null,
                                            tint = TextSecondary.copy(alpha = 0.6f),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(Modifier.width(5.dp))
                                        Text(
                                            text = "Pemulihan saat keluar app:",
                                            color = TextSecondary.copy(alpha = 0.65f),
                                            fontSize = 10.5.sp
                                        )
                                    }
                                    Text(
                                        text = baselineDisplayName,
                                        color = AccentCyan.copy(alpha = 0.9f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            if (state.profileOverrides.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider(color = Color(0x15FFFFFF), thickness = 0.6.dp)
                                Spacer(Modifier.height(6.dp))
                                val overrides = state.profileOverrides.entries
                                    .sortedBy { it.key.storageKey }
                                    .joinToString(" • ") { (domain, profile) -> "${domain.storageKey.uppercase()}: ${profile.uppercase()}" }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Override aktif: $overrides",
                                        color = TextSecondary.copy(alpha = 0.7f),
                                        fontSize = 10.sp,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    )
                                    Surface(
                                        onClick = {
                                            viewModel.selectKernelPerformanceProfile(activeProfileId, context, true)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = AccentOrange.copy(alpha = 0.10f),
                                        border = BorderStroke(0.6.dp, AccentOrange.copy(alpha = 0.35f)),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 9.dp),
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.RestartAlt,
                                                contentDescription = null,
                                                tint = AccentOrange,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = "Reset Override",
                                                color = AccentOrange,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── 2. Section Header: Katalog Profil ────────────
            item(key = "tab0_profiles_section_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Katalog Profil",
                            color = TextSecondary.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.2.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF141824),
                            border = BorderStroke(0.5.dp, Color(0x18FFFFFF))
                        ) {
                            Text(
                                text = "${uiState.kernelProfileCatalog.profiles.size} Pilihan",
                                color = TextSecondary.copy(alpha = 0.75f),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // ── 3. Profile Catalog Grid (Direct First-Class Bento) ──
            item(key = "tab0_profiles_catalog_card") {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    ProfileGrid(
                        catalog = uiState.kernelProfileCatalog,
                        currentProfile = state.activeProfile,
                        hasOverrides = state.profileOverrides.isNotEmpty(),
                        gpuPlatform = uiState.gpuInfo.platform,
                        state = state,
                        onProfileSelected = { id, clearOverrides ->
                            val colorHex = when (id.lowercase()) {
                                "powersave" -> 0xFF00E676L
                                "balance" -> 0xFF00A3FFL
                                "performance" -> 0xFFFF9100L
                                "extreme" -> 0xFFFF3D00L
                                else -> 0xFF00E5FFL
                            }
                            val labelResource = mapOf(
                                "powersave" to com.noir.lynx.R.string.audit_mode_powersave,
                                "balance" to com.noir.lynx.R.string.audit_mode_balance,
                                "performance" to com.noir.lynx.R.string.audit_mode_performance,
                                "extreme" to com.noir.lynx.R.string.audit_mode_extreme
                            )[id.lowercase()]
                            val label = labelResource?.let(context::getString) ?: id
                            viewModel.triggerModeFeedback(id, label, colorHex)
                            viewModel.selectKernelPerformanceProfile(id, context, clearOverrides)
                        },
                        onExtremeConfirmRequired = onExtremeConfirmRequired,
                        onProfileSaved = { profile, replaceBuiltIn, replaceCustom ->
                            viewModel.saveKernelPerformanceProfile(profile, replaceBuiltIn, replaceCustom)
                        },
                        onProfileDeleted = { id ->
                            viewModel.deleteKernelPerformanceProfile(id)
                        },
                        onProfileDuplicated = { id ->
                            viewModel.duplicateKernelPerformanceProfile(id)
                        },
                        onRestoreBuiltIn = { id ->
                            viewModel.restoreBuiltInKernelProfile(id, context)
                        }
                    )
                }
            }
        }

        1 -> {
            // ══════════════════════════════════════════════════════════
            // TAB 1: OTOMASI APLIKASI
            // ══════════════════════════════════════════════════════════
            // Tab 1 is rendered via dedicated full-height ProfilesAutomationScreen
            // in parent containers (TuningTabContent and TuningProfilesCategory)
            // for unconstrained 120 FPS fluid scrolling and Bento card layout.
            item(key = "tab1_automation_screen_placeholder") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Layanan Otomasi Profil dimuat di layar utama.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

/**
 * ProfilesAutomationScreen — Dedicated Two-Zone Screen for Kernel Performance App Automation.
 * Redesigned for psychological calm, transparent system reassurance, zero-friction controls,
 * and unified obsidian cyberpunk aesthetics.
 *
 * Zone 1 (Top / Pinned): Modular Service Toggle, Dual-Pillar Zen Safety Ribbon, and Hysteresis Cooldown Capsule.
 * Stays stationary or flows seamlessly in LazyColumn so controls are accessible without cognitive stress.
 *
 * Zone 2 (Bottom / Flow): In-Place Scrollable App Rules Bento Cards.
 * Features mode-harmonic borders, one-tap quick profile cycle pills, and transparent status feedback.
 */
@Composable
fun ProfilesAutomationScreen(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    context: Context,
    onAddAppClick: () -> Unit,
    onEditRuleClick: (AppProfileRule) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var showClearAllDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("all") } // "all", "game", "active"

    val filteredRules = remember(uiState.appProfileRules, searchQuery, selectedFilter) {
        uiState.appProfileRules
            .filter { rule ->
                val matchesSearch = searchQuery.isBlank() ||
                    rule.appName.contains(searchQuery, ignoreCase = true) ||
                    rule.packageName.contains(searchQuery, ignoreCase = true)
                val matchesFilter = when (selectedFilter) {
                    "game" -> rule.isGame
                    "active" -> rule.enabled
                    else -> true
                }
                matchesSearch && matchesFilter
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.appName.ifBlank { it.packageName } })
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            containerColor = Color(0xFF10141F),
            shape = RoundedCornerShape(20.dp),
            icon = {
                Surface(
                    shape = CircleShape,
                    color = AccentRed.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.35f)),
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = AccentRed,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    text = "Hapus Semua Aturan Profil?",
                    color = TextPrimary,
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF0C0F17),
                        border = BorderStroke(0.6.dp, Color(0x18FFFFFF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Total Aturan Terdaftar",
                                color = TextSecondary.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = AccentRed.copy(alpha = 0.16f),
                                border = BorderStroke(0.8.dp, AccentRed.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "${uiState.appProfileRules.size} Aplikasi",
                                    color = AccentRed,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "Tindakan ini akan menghapus seluruh konfigurasi profil khusus aplikasi. Anda dapat memindai ulang game terpasang kapan saja dengan 1 ketukan.",
                        color = TextSecondary.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Center
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AccentCyan.copy(alpha = 0.08f),
                        border = BorderStroke(0.6.dp, AccentCyan.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Pengaturan Profil Sistem di Tab Katalog tidak akan terpengaruh.",
                                color = TextPrimary.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                lineHeight = 14.5.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showClearAllDialog = false
                        viewModel.clearAllAppProfileRules(context)
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = AccentRed.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.6f)),
                    modifier = Modifier.height(44.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Hapus Semua (${uiState.appProfileRules.size})",
                            color = AccentRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                    }
                }
            },
            dismissButton = {
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showClearAllDialog = false
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF0C0F17),
                    border = BorderStroke(0.8.dp, Color(0x18FFFFFF)),
                    modifier = Modifier.height(44.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Batalkan",
                            color = TextSecondary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp
                        )
                    }
                }
            }
        )
    }

    LaunchedEffect(uiState.appProfileRules) {
        val pkgs = uiState.appProfileRules.take(20).map { it.packageName }
        AppIconLoader.preload(context, pkgs)
    }

    val lazyListState = rememberLazyListState()

    LazyColumn(
        state = lazyListState,
        flingBehavior = rememberFluidFlingBehavior(),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "auto_top_spacer") {
            Spacer(Modifier.height(4.dp))
        }

        // ══════════════════════════════════════════════════════════════
        // 1. HERO SERVICE CARD: MASTER SWITCH & COOLDOWN BENTO
        // ══════════════════════════════════════════════════════════════
        item(key = "auto_hero_service_card") {
            AutomationHeroServiceCard(
                uiState = uiState,
                viewModel = viewModel,
                context = context
            )
        }

        // ══════════════════════════════════════════════════════════════
        // 2. SECTION HEADER & ACTIONS (Matching Tab 0 Typography)
        // ══════════════════════════════════════════════════════════════
        item(key = "auto_section_header") {
            AutomationSectionHeader(
                uiState = uiState,
                showOverflowMenu = showOverflowMenu,
                onOpenOverflowMenu = { showOverflowMenu = true },
                onOverflowMenuDismiss = { showOverflowMenu = false },
                onAddAppClick = onAddAppClick,
                onClearAllClick = { showClearAllDialog = true },
                viewModel = viewModel,
                context = context
            )
        }

        // ══════════════════════════════════════════════════════════════
        // 3. SEARCH & FILTER TOOLBAR (Only if rules exist)
        // ══════════════════════════════════════════════════════════════
        if (uiState.appProfileRules.isNotEmpty()) {
            item(key = "auto_search_filter_bar") {
                AutomationSearchFilterBar(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    selectedFilter = selectedFilter,
                    onSelectFilter = { selectedFilter = it },
                    totalCount = uiState.appProfileRules.size,
                    gameCount = uiState.appProfileRules.count { it.isGame },
                    activeCount = uiState.appProfileRules.count { it.enabled }
                )
            }
        }

        // ══════════════════════════════════════════════════════════════
        // 4. RULES BENTO CARDS OR EMPTY STATES
        // ══════════════════════════════════════════════════════════════
        if (uiState.appProfileRules.isEmpty()) {
            if (uiState.isAppRulesLoading) {
                item(key = "auto_loading_rules_card") {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF10141F),
                        border = BorderStroke(0.8.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = AccentCyan,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Memuat otomasi aplikasi...",
                                color = TextSecondary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else {
                item(key = "auto_empty_rules_card") {
                    AutomationEmptyRulesCard(
                        uiState = uiState,
                        viewModel = viewModel,
                        context = context,
                        onAddAppClick = onAddAppClick
                    )
                }
            }
        } else if (filteredRules.isEmpty()) {
            item(key = "auto_empty_search_card") {
                AutomationEmptySearchCard(
                    onReset = {
                        searchQuery = ""
                        selectedFilter = "all"
                    }
                )
            }
        } else {
            items(
                items = filteredRules,
                key = { it.packageName },
                contentType = { "app_rule_bento" }
            ) { rule ->
                AppRuleBentoCard(
                    rule = rule,
                    context = context,
                    viewModel = viewModel,
                    onEditRuleClick = onEditRuleClick
                )
            }
        }

        // Bottom Navigation Bar spacer to prevent floating dock collision
        item(key = "auto_bottom_nav_spacer") {
            Spacer(modifier = Modifier.height(115.dp).navigationBarsPadding())
        }
    }
}

/**
 * AutomationHeroServiceCard — High-presence Zen Hero Bento Card for Kernel Performance App Automation.
 * Features living Zen pulse indicator, reassuring dual-pillar safety ribbon (Baseline Recovery + Deep Sleep Guard),
 * tactile master switch, and Nothing OS segmented anti-stutter cooldown capsule.
 */
@Composable
fun AutomationHeroServiceCard(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    context: Context,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var showActivationDialog by remember { mutableStateOf(false) }
    var dontShowAgain by remember { mutableStateOf(false) }
    val prefs = remember { context.getSharedPreferences("lynx_automation_prefs", Context.MODE_PRIVATE) }
    val isSuppressed = remember { prefs.getBoolean("suppress_automation_confirm_dialog", false) }

    val isAutomationRunning = uiState.isAppAutomationActive || com.noir.lynx.service.LynxAppAutomationService.isRunning
    val activeGamePkg = com.noir.lynx.service.LynxAppAutomationService.activeGamePackage
    val activeGameName = com.noir.lynx.service.LynxAppAutomationService.activeGameName
    val hasOverride = uiState.state.profileOverrides.isNotEmpty() || activeGamePkg != null

    val baselineId = com.noir.lynx.service.LynxAppAutomationService.currentBaseline
    val baselineDisplayName = when (baselineId.lowercase().trim()) {
        "performance" -> "Performa Tinggi"
        "extreme" -> "Ekstrem"
        "powersave" -> "Hemat Daya"
        else -> "Seimbang"
    }

    if (showActivationDialog) {
        AlertDialog(
            onDismissRequest = { showActivationDialog = false },
            containerColor = Color(0xFF10141F),
            shape = RoundedCornerShape(20.dp),
            icon = {
                Surface(
                    shape = CircleShape,
                    color = AccentCyan.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.35f)),
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AutoMode,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    text = "Aktifkan Otomasi Profil?",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Layanan ini memantau aplikasi aktif secara cerdas dengan overhead minimal untuk kenyamanan maksimal Anda.",
                        color = TextSecondary.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        lineHeight = 16.5.sp,
                        textAlign = TextAlign.Center
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0C0F17),
                        border = BorderStroke(0.8.dp, BorderGlass),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Text("⚡", fontSize = 13.sp)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Performa Terarah Saat Game Aktif",
                                        color = TextPrimary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Kernel CPU & GPU otomatis didongkrak hanya saat game terbuka di layar depan.",
                                        color = TextSecondary.copy(alpha = 0.8f),
                                        fontSize = 10.5.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                            }

                            HorizontalDivider(color = Color(0x15FFFFFF), thickness = 0.6.dp)

                            Row(verticalAlignment = Alignment.Top) {
                                Text("🛡️", fontSize = 13.sp)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Pemulihan Otomatis ke $baselineDisplayName",
                                        color = TextPrimary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Saat keluar dari game, perangkat segera kembali dingin & sejuk ke profil dasar yang aman.",
                                        color = TextSecondary.copy(alpha = 0.8f),
                                        fontSize = 10.5.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                            }

                            HorizontalDivider(color = Color(0x15FFFFFF), thickness = 0.6.dp)

                            Row(verticalAlignment = Alignment.Top) {
                                Text("🌙", fontSize = 13.sp)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Zero Wake Lock (Deep Sleep Terjaga)",
                                        color = TextPrimary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Daemon tidur lelap saat layar mati, menjamin 0% pemborosan baterai di saku Anda.",
                                        color = TextSecondary.copy(alpha = 0.8f),
                                        fontSize = 10.5.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }

                    // Checkbox "Jangan tampilkan lagi"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                dontShowAgain = !dontShowAgain
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = dontShowAgain,
                            onCheckedChange = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                dontShowAgain = it
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = AccentCyan,
                                checkmarkColor = BgDeepOled,
                                uncheckedColor = TextSecondary.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Jangan tampilkan konfirmasi ini lagi",
                            color = TextPrimary.copy(alpha = 0.9f),
                            fontSize = 11.5.sp
                        )
                    }
                }
            },
            confirmButton = {
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (dontShowAgain) {
                            prefs.edit().putBoolean("suppress_automation_confirm_dialog", true).apply()
                        }
                        showActivationDialog = false
                        viewModel.toggleAppAutomation(context, true)
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = AccentCyan.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.6f)),
                    modifier = Modifier.height(44.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aktifkan Otomasi",
                            color = AccentCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                    }
                }
            },
            dismissButton = {
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showActivationDialog = false
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF0C0F17),
                    border = BorderStroke(0.8.dp, BorderGlass),
                    modifier = Modifier.height(44.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Batal",
                            color = TextSecondary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp
                        )
                    }
                }
            }
        )
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF10141F),
        border = BorderStroke(
            0.8.dp,
            Brush.verticalGradient(
                listOf(
                    (if (isAutomationRunning) AccentCyan else Color(0xFF94A3B8)).copy(alpha = if (isAutomationRunning) 0.32f else 0.15f),
                    Color(0x12FFFFFF)
                )
            )
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Row 1: Zen Reassurance Status Bar & Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Subtle Zen Pulse Indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Surface(
                            shape = CircleShape,
                            color = (if (isAutomationRunning) AccentCyan else TextSecondary).copy(alpha = 0.18f),
                            modifier = Modifier.size(12.dp)
                        ) {}
                        Surface(
                            shape = CircleShape,
                            color = if (isAutomationRunning) AccentCyan else TextSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(6.dp)
                        ) {}
                    }
                    Text(
                        text = if (isAutomationRunning) "Mesin Otomasi Adaptif" else "Mode Otomasi Siaga",
                        color = TextSecondary.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Right: Status Badges (Active & Override)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isAutomationRunning) Color(0x1400E676) else Color(0x1294A3B8),
                        border = BorderStroke(
                            0.5.dp,
                            if (isAutomationRunning) Color(0x2800E676) else Color(0x18FFFFFF)
                        )
                    ) {
                        Text(
                            text = if (isAutomationRunning) "Otomasi Aktif" else "Siaga",
                            color = if (isAutomationRunning) AccentGreen.copy(alpha = 0.9f) else TextSecondary.copy(alpha = 0.7f),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                        )
                    }

                    if (hasOverride) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0x18FF9100),
                            border = BorderStroke(0.5.dp, Color(0x35FF9100))
                        ) {
                            Text(
                                text = if (activeGameName != null) "🎮 ${activeGameName.take(14)}" else "🎮 Override Aktif",
                                color = AccentOrange.copy(alpha = 0.95f),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(13.dp))

            // Row 2: Master Service Presence Row (Icon + Title & Calm Subtext + 48dp Switch)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isAutomationRunning) AccentCyan.copy(alpha = 0.12f) else Color(0xFF141824),
                    border = BorderStroke(
                        0.6.dp,
                        if (isAutomationRunning) AccentCyan.copy(alpha = 0.28f) else Color(0x18FFFFFF)
                    ),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AutoMode,
                            contentDescription = null,
                            tint = if (isAutomationRunning) AccentCyan else TextSecondary.copy(alpha = 0.7f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Layanan Otomasi Profil",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.2).sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (isAutomationRunning) {
                            "Mendeteksi game cerdas — mendongkrak performa saat bermain dan kembali sejuk saat selesai."
                        } else {
                            "Layanan siaga tanpa beban memori. Mengikuti profil sistem yang dipilih di Tab Katalog."
                        },
                        color = TextSecondary.copy(alpha = 0.75f),
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp
                    )
                }

                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Switch(
                        checked = uiState.isAppAutomationActive,
                        onCheckedChange = { enable ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (enable) {
                                if (isSuppressed) {
                                    viewModel.toggleAppAutomation(context, true)
                                } else {
                                    showActivationDialog = true
                                }
                            } else {
                                viewModel.toggleAppAutomation(context, false)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BgDeepOled,
                            checkedTrackColor = AccentCyan,
                            uncheckedThumbColor = TextSecondary.copy(alpha = 0.6f),
                            uncheckedTrackColor = Color(0xFF141824),
                            uncheckedBorderColor = BorderGlass
                        ),
                        modifier = Modifier.scale(0.88f)
                    )
                }
            }

            Spacer(Modifier.height(13.dp))

            // Row 3: Subsystem Telemetry Specs (Calm Unified Zen Ribbon - Matching Tab 0)
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
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = null,
                            tint = AccentCyan.copy(alpha = 0.85f),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Pemulihan:",
                            color = TextSecondary.copy(alpha = 0.55f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "$baselineDisplayName (Sejuk)",
                            color = AccentCyan.copy(alpha = 0.95f),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text("•", color = TextSecondary.copy(alpha = 0.3f), fontSize = 9.sp)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = AccentGreen.copy(alpha = 0.85f),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Baterai:",
                            color = TextSecondary.copy(alpha = 0.55f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Deep Sleep Aman (0% Drain)",
                            color = AccentGreen.copy(alpha = 0.95f),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Color(0x12FFFFFF), thickness = 0.6.dp)
            Spacer(Modifier.height(10.dp))

            // Row 4: Buffer Transisi Anti-Jank (Hysteresis Cooldown)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Buffer Transisi (Hysteresis)",
                        color = TextSecondary.copy(alpha = 0.9f),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        letterSpacing = 0.1.sp
                    )
                }

                val cooldownPillText = when (uiState.appAutomationCooldownSeconds) {
                    30 -> "30s • Rekomendasi"
                    0 -> "Terkunci"
                    else -> "${uiState.appAutomationCooldownSeconds}s Buffer"
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AccentCyan.copy(alpha = 0.12f),
                    border = BorderStroke(0.6.dp, AccentCyan.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = cooldownPillText,
                        color = AccentCyan,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = "Menahan profil saat beralih aplikasi sejenak agar chatting tidak memicu drop FPS atau lonjakan clock.",
                color = TextSecondary.copy(alpha = 0.65f),
                fontSize = 11.sp,
                lineHeight = 14.5.sp
            )

            Spacer(Modifier.height(8.dp))

            val cooldownOptions = listOf(
                5 to "5s",
                30 to "30s",
                60 to "1m",
                180 to "3m",
                0 to "Kunci"
            )

            // High-Touch Segmented Capsule (Height 42dp, Touch target 44dp+)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0C0F17),
                border = BorderStroke(0.6.dp, Color(0x18FFFFFF)),
                modifier = Modifier.fillMaxWidth().height(42.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    cooldownOptions.forEach { (sec, label) ->
                        val isSelected = uiState.appAutomationCooldownSeconds == sec
                        val animBg by animateColorAsState(
                            targetValue = if (isSelected) AccentCyan.copy(alpha = 0.20f) else Color.Transparent,
                            animationSpec = tween(180),
                            label = "cd_bg_$sec"
                        )
                        val animText by animateColorAsState(
                            targetValue = if (isSelected) AccentCyan else TextSecondary.copy(alpha = 0.75f),
                            animationSpec = tween(180),
                            label = "cd_txt_$sec"
                        )

                        Surface(
                            onClick = {
                                if (!isSelected) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.setAppAutomationCooldownSeconds(context, sec)
                                }
                            },
                            shape = RoundedCornerShape(9.dp),
                            color = animBg,
                            border = if (isSelected) BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.55f)) else null,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    color = animText,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(7.dp))
            val reassuranceNote = when (uiState.appAutomationCooldownSeconds) {
                30 -> "✓ Rekomendasi: 30 detik menahan profil saat keluar sementara agar game bebas stutter."
                5 -> "⚡ Pemulihan cepat: Kembali ke $baselineDisplayName dalam 5 detik setelah keluar game."
                0 -> "🔒 Profil terkunci: Mode game bertahan stabil hingga Anda memilih profil lain secara manual."
                else -> "⏳ Buffer pendinginan: ${uiState.appAutomationCooldownSeconds} detik toleransi jeda sebelum kembali ke $baselineDisplayName."
            }
            Text(
                text = reassuranceNote,
                color = TextSecondary.copy(alpha = 0.70f),
                fontSize = 10.5.sp,
                lineHeight = 14.sp
            )
        }
    }
}

/**
 * Backward-compatible alias for AutomationHeroServiceCard.
 */
@Composable
fun AutomationServiceTopSection(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    context: Context,
    modifier: Modifier = Modifier
) {
    AutomationHeroServiceCard(
        uiState = uiState,
        viewModel = viewModel,
        context = context,
        modifier = modifier
    )
}

/**
 * AutomationSectionHeader — Harmonized Section Header for App Rules.
 * Matches Tab 0 section header ("Daftar Aturan Aplikasi"), rule count badge,
 * high-utility "Pindai" button, Cyan "Tambah" button, and overflow dropdown menu (without M3 white corners).
 */
@Composable
fun AutomationSectionHeader(
    uiState: LynxUiState,
    showOverflowMenu: Boolean,
    onOpenOverflowMenu: () -> Unit,
    onOverflowMenuDismiss: () -> Unit,
    onAddAppClick: () -> Unit,
    onClearAllClick: () -> Unit,
    viewModel: LynxViewModel,
    context: Context,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Daftar Aturan Aplikasi",
                color = TextSecondary.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.2.sp
            )
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF141824),
                border = BorderStroke(0.5.dp, Color(0x18FFFFFF))
            ) {
                Text(
                    text = "${uiState.appProfileRules.size} Terdaftar",
                    color = TextSecondary.copy(alpha = 0.75f),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Quick Action: Pindai Game Terpasang (Instant 1-Tap Utility)
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.autoDetectAndSyncInstalledRules(context)
                },
                enabled = !uiState.isScanningInstalledGames,
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF141824),
                border = BorderStroke(0.6.dp, Color(0x20FFFFFF)),
                modifier = Modifier.height(42.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (uiState.isScanningInstalledGames) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = AccentCyan,
                            strokeWidth = 1.8.dp
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "Memindai...",
                            color = AccentCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = TextSecondary.copy(alpha = 0.85f),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "Pindai",
                            color = TextSecondary.copy(alpha = 0.9f),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Minimal Cyan Add Button (Height 42dp touch target)
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onAddAppClick()
                },
                shape = RoundedCornerShape(10.dp),
                color = AccentCyan.copy(alpha = 0.14f),
                border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.45f)),
                modifier = Modifier.height(42.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "Tambah",
                        color = AccentCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Overflow Menu Button (⋮, 42x42dp touch target)
            Box {
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenOverflowMenu()
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF141824),
                    border = BorderStroke(0.6.dp, Color(0x18FFFFFF)),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Opsi Lainnya",
                            tint = TextSecondary.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // DropdownMenu with containerColor and shape to completely prevent white corner glitch
                MaterialTheme(
                    colorScheme = MaterialTheme.colorScheme.copy(
                        surface = Color(0xFF141824),
                        surfaceContainer = Color(0xFF141824),
                        surfaceContainerHigh = Color(0xFF141824)
                    )
                ) {
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = onOverflowMenuDismiss,
                        modifier = Modifier
                            .background(Color(0xFF141824), RoundedCornerShape(14.dp))
                            .border(1.dp, BorderGlass.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp))
                            .padding(vertical = 4.dp)
                            .widthIn(min = 210.dp)
                    ) {
                        Surface(
                            onClick = {
                                onOverflowMenuDismiss()
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.autoDetectAndSyncInstalledRules(context)
                            },
                            enabled = !uiState.isScanningInstalledGames,
                            shape = RoundedCornerShape(10.dp),
                            color = Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = AccentCyan.copy(alpha = 0.14f),
                                    border = BorderStroke(0.6.dp, AccentCyan.copy(alpha = 0.35f)),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            tint = AccentCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = "Pindai Ulang Seluruh Game",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        if (uiState.appProfileRules.isNotEmpty()) {
                            HorizontalDivider(
                                color = Color(0x18FFFFFF),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                            Surface(
                                onClick = {
                                    onOverflowMenuDismiss()
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onClearAllClick()
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = AccentRed.copy(alpha = 0.14f),
                                        border = BorderStroke(0.6.dp, AccentRed.copy(alpha = 0.35f)),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = null,
                                                tint = AccentRed,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = "Hapus Semua Aturan",
                                        color = AccentRed,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
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

/**
 * AutomationSearchFilterBar — Cyberpunk Search Bar & Filter Chips.
 * Clean, tranquil surfaces with smooth animated active state and haptic feedback.
 */
@Composable
fun AutomationSearchFilterBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedFilter: String,
    onSelectFilter: (String) -> Unit,
    totalCount: Int,
    gameCount: Int,
    activeCount: Int,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Full-Width Search Input
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF10141E),
            border = BorderStroke(
                0.7.dp,
                if (searchQuery.isNotEmpty()) AccentCyan.copy(alpha = 0.55f) else Color(0x18FFFFFF)
            ),
            modifier = Modifier.fillMaxWidth().height(42.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = if (searchQuery.isNotEmpty()) AccentCyan else TextSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Cari game atau nama paket...",
                            color = TextSecondary.copy(alpha = 0.45f),
                            fontSize = 12.5.sp
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = TextPrimary,
                            fontSize = 12.5.sp,
                            fontFamily = FontFamily.Default
                        ),
                        cursorBrush = SolidColor(AccentCyan),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSearchQueryChange("")
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Hapus pencarian",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Minimal Segmented Filter Chips (Height 32dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filterOptions = listOf(
                Triple("all", "Semua", totalCount),
                Triple("game", "Game", gameCount),
                Triple("active", "Aktif", activeCount)
            )
            filterOptions.forEach { (filterKey, label, count) ->
                val isSelected = selectedFilter == filterKey
                val animBg by animateColorAsState(
                    targetValue = if (isSelected) AccentCyan.copy(alpha = 0.16f) else Color(0xFF10141E),
                    animationSpec = tween(180),
                    label = "filter_bg_$filterKey"
                )
                val animBorder by animateColorAsState(
                    targetValue = if (isSelected) AccentCyan.copy(alpha = 0.55f) else Color(0x18FFFFFF),
                    animationSpec = tween(180),
                    label = "filter_border_$filterKey"
                )

                Surface(
                    onClick = {
                        if (!isSelected) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectFilter(filterKey)
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = animBg,
                    border = BorderStroke(0.8.dp, animBorder),
                    modifier = Modifier.height(32.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) AccentCyan else TextSecondary.copy(alpha = 0.85f),
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isSelected) AccentCyan.copy(alpha = 0.25f) else Color(0xFF0C0F17),
                            border = BorderStroke(
                                0.5.dp,
                                if (isSelected) AccentCyan.copy(alpha = 0.45f) else Color(0x18FFFFFF)
                            )
                        ) {
                            Text(
                                text = "$count",
                                color = if (isSelected) AccentCyan else TextSecondary.copy(alpha = 0.75f),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * AutomationEmptyRulesCard — Clean, Welcoming Empty State when no rules are configured.
 * Reassures the user and presents clear, accessible onboarding paths.
 */
@Composable
fun AutomationEmptyRulesCard(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    context: Context,
    onAddAppClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF10141F),
        border = BorderStroke(0.8.dp, BorderSubtle),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = AccentCyan.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.35f)),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Ruang Otomasi Siaga",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Lynx siap memantau performa game Anda secara otomatis. Clock tinggi hanya aktif saat game berjalan dan aman kembali sejuk ke profil dasar saat ditutup.",
                color = TextSecondary.copy(alpha = 0.8f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 16.5.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(Modifier.height(18.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.autoDetectAndSyncInstalledRules(context)
                    },
                    enabled = !uiState.isScanningInstalledGames,
                    shape = RoundedCornerShape(10.dp),
                    color = AccentCyan.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.55f)),
                    modifier = Modifier.height(42.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.isScanningInstalledGames) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(15.dp),
                                color = AccentCyan,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Memindai Game...",
                                color = AccentCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Pindai Game Terpasang",
                                color = AccentCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onAddAppClick()
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF0C0F17),
                    border = BorderStroke(1.dp, Color(0x18FFFFFF)),
                    modifier = Modifier.height(42.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Tambah Manual",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * AutomationEmptySearchCard — Empty state when query or filter returns zero matches.
 */
@Composable
fun AutomationEmptySearchCard(
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF10141F),
        border = BorderStroke(0.8.dp, BorderSubtle),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.SearchOff,
                contentDescription = null,
                tint = TextSecondary.copy(alpha = 0.6f),
                modifier = Modifier.size(30.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Tidak Ada Aplikasi yang Cocok",
                color = TextPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Coba kata kunci pencarian lain atau ubah filter yang dipilih.",
                color = TextSecondary.copy(alpha = 0.8f),
                fontSize = 11.5.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onReset()
            }) {
                Text(
                    text = "Reset Pencarian",
                    color = AccentCyan,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * AppRuleBentoCard — Luxury Obsidian Cyberpunk Bento Card for Individual App Profile Rule.
 * Features mode-harmonic border & aura, prominent title with [GAME] squircle,
 * emotional reassurance subtext, tactile 1-tap quick profile cycle pill, and master switch.
 */
@Composable
fun AppRuleBentoCard(
    rule: AppProfileRule,
    context: Context,
    viewModel: LynxViewModel,
    onEditRuleClick: (AppProfileRule) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    val profDotColor = when (rule.targetProfile.lowercase().trim()) {
        "extreme" -> AccentRed
        "performance" -> AccentOrange
        "powersave" -> AccentGreen
        else -> AccentCyan
    }

    val profLabel = when (rule.targetProfile.lowercase().trim()) {
        "extreme" -> context.getString(com.noir.lynx.R.string.audit_mode_extreme)
        "performance" -> context.getString(com.noir.lynx.R.string.audit_mode_performance)
        "powersave" -> context.getString(com.noir.lynx.R.string.audit_mode_powersave)
        else -> context.getString(com.noir.lynx.R.string.audit_mode_balance)
    }

    val targetBg = if (rule.enabled) Color(0xFF10141F) else Color(0xFF12141D).copy(alpha = 0.65f)
    val targetBorder = if (rule.enabled) profDotColor.copy(alpha = 0.32f) else BorderSubtle

    val animatedBg by animateColorAsState(targetBg, animationSpec = tween(180), label = "rule_bg_${rule.packageName}")
    val animatedBorder by animateColorAsState(targetBorder, animationSpec = tween(180), label = "rule_border_${rule.packageName}")

    val onRowClick = remember(rule) { { onEditRuleClick(rule) } }
    val onToggle = remember(rule.packageName) {
        { enabled: Boolean ->
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            viewModel.toggleAppProfileRule(context, rule.packageName, enabled)
        }
    }

    Surface(
        onClick = onRowClick,
        shape = RoundedCornerShape(16.dp),
        color = animatedBg,
        border = BorderStroke(if (rule.enabled) 1.dp else 0.6.dp, animatedBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 8.dp)
        ) {
            // Upper Row: App Icon + App Name & Subtext + Master Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Crisp 42dp App Icon
                AppIconImage(
                    packageName = rule.packageName,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                )

                Spacer(Modifier.width(12.dp))

                // Middle Info: Name + [GAME] Tag, Subtitle & Reassurance Line
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = rule.appName,
                            color = if (rule.enabled) TextPrimary else TextSecondary.copy(alpha = 0.65f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (rule.isGame) {
                            Surface(
                                shape = RoundedCornerShape(4.5.dp),
                                color = AccentOrange.copy(alpha = 0.16f),
                                border = BorderStroke(0.6.dp, AccentOrange.copy(alpha = 0.45f))
                            ) {
                                Text(
                                    text = "GAME",
                                    color = AccentOrange,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp,
                                    modifier = Modifier.padding(horizontal = 4.5.dp, vertical = 1.5.dp)
                                )
                            }
                        }
                    }

                    // Package name in compact format
                    Text(
                        text = rule.packageName,
                        color = TextSecondary.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Human-centric emotional reassurance subtext
                    Text(
                        text = if (rule.enabled) {
                            "⚡ Target: $profLabel • Siaga saat dibuka"
                        } else {
                            "⏸️ Ditangguhkan • Mengikuti profil sistem"
                        },
                        color = if (rule.enabled) profDotColor.copy(alpha = 0.85f) else TextSecondary.copy(alpha = 0.5f),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Master Switch for this rule (48dp touch-box container)
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Switch(
                        checked = rule.enabled,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BgDeepOled,
                            checkedTrackColor = profDotColor,
                            uncheckedThumbColor = TextSecondary.copy(alpha = 0.6f),
                            uncheckedTrackColor = Color(0xFF141824),
                            uncheckedBorderColor = BorderGlass
                        ),
                        modifier = Modifier.scale(0.85f)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Color(0x12FFFFFF), thickness = 0.5.dp)
            Spacer(Modifier.height(6.dp))

            // Lower Row: 1-Tap Quick Cycle Mode Pill + Badges (Hz, HUD) + Kustomisasi Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Badges (Mode Pill with 1-Tap Cycle + Hz + HUD)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 1-Tap Interactive Quick Profile Cycle Pill
                    Surface(
                        onClick = {
                            val nextProfile = when (rule.targetProfile.lowercase().trim()) {
                                "powersave" -> "balance"
                                "balance" -> "performance"
                                "performance" -> "extreme"
                                "extreme" -> "powersave"
                                else -> "performance"
                            }
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val updated = rule.copy(targetProfile = nextProfile)
                            viewModel.addOrUpdateAppProfileRule(context, updated)
                        },
                        shape = RoundedCornerShape(7.dp),
                        color = Color(0xFF0C0F17),
                        border = BorderStroke(0.7.dp, profDotColor.copy(alpha = if (rule.enabled) 0.5f else 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.5.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = profDotColor,
                                modifier = Modifier.size(5.dp)
                            ) {}
                            Text(
                                text = profLabel,
                                color = if (rule.enabled) TextPrimary else TextSecondary.copy(alpha = 0.6f),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Ganti Profil Cepat",
                                tint = profDotColor.copy(alpha = 0.75f),
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }

                    // Optional Target Refresh Rate Badge (Hz)
                    if (rule.targetRefreshRate != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF0C0F17),
                            border = BorderStroke(0.5.dp, Color(0x18FFFFFF))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = "${rule.targetRefreshRate} Hz",
                                    color = TextSecondary.copy(alpha = 0.85f),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Optional Floating HUD Badge
                    if (rule.autoFloatingHud) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AccentCyan.copy(alpha = 0.12f),
                            border = BorderStroke(0.5.dp, AccentCyan.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = "OSD AKTIF",
                                color = AccentCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.5.dp)
                            )
                        }
                    }
                }

                // Right: Dedicated Kustomisasi Affordance Pill
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onEditRuleClick(rule)
                    },
                    shape = RoundedCornerShape(7.dp),
                    color = Color.Transparent,
                    modifier = Modifier.height(32.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "Kustomisasi",
                            color = TextSecondary.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Kustomisasi",
                            tint = TextSecondary.copy(alpha = 0.65f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * UnifiedAppAutomationCard — Backward-compatible wrapper delegating to ProfilesAutomationScreen.
 */
@Composable
fun UnifiedAppAutomationCard(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    context: Context,
    onAddAppClick: () -> Unit,
    onEditRuleClick: (AppProfileRule) -> Unit
) {
    ProfilesAutomationScreen(
        uiState = uiState,
        viewModel = viewModel,
        context = context,
        onAddAppClick = onAddAppClick,
        onEditRuleClick = onEditRuleClick
    )
}

/**
 * AutomationRulesCard — Backward-compatible container delegating to unified bento rules.
 */
@Composable
fun AutomationRulesCard(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    context: Context,
    filteredRules: List<AppProfileRule>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedFilter: String,
    onSelectFilter: (String) -> Unit,
    showOverflowMenu: Boolean,
    onOverflowMenuDismiss: () -> Unit,
    onOpenOverflowMenu: () -> Unit,
    onClearAllClick: () -> Unit,
    onAddAppClick: () -> Unit,
    onEditRuleClick: (AppProfileRule) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AutomationSectionHeader(
            uiState = uiState,
            showOverflowMenu = showOverflowMenu,
            onOpenOverflowMenu = onOpenOverflowMenu,
            onOverflowMenuDismiss = onOverflowMenuDismiss,
            onAddAppClick = onAddAppClick,
            onClearAllClick = onClearAllClick,
            viewModel = viewModel,
            context = context
        )

        if (uiState.appProfileRules.isNotEmpty()) {
            AutomationSearchFilterBar(
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                selectedFilter = selectedFilter,
                onSelectFilter = onSelectFilter,
                totalCount = uiState.appProfileRules.size,
                gameCount = uiState.appProfileRules.count { it.isGame },
                activeCount = uiState.appProfileRules.count { it.enabled }
            )
        }

        if (uiState.appProfileRules.isEmpty()) {
            AutomationEmptyRulesCard(
                uiState = uiState,
                viewModel = viewModel,
                context = context,
                onAddAppClick = onAddAppClick
            )
        } else if (filteredRules.isEmpty()) {
            AutomationEmptySearchCard(
                onReset = {
                    onSearchQueryChange("")
                    onSelectFilter("all")
                }
            )
        } else {
            filteredRules.forEach { rule ->
                AppRuleBentoCard(
                    rule = rule,
                    context = context,
                    viewModel = viewModel,
                    onEditRuleClick = onEditRuleClick
                )
            }
        }
    }
}

/**
 * Backward-compatible row adapter delegating to AppRuleBentoCard.
 */
@Composable
fun AppRuleEmbeddedRow(
    rule: AppProfileRule,
    context: Context,
    viewModel: LynxViewModel,
    onEditRuleClick: (AppProfileRule) -> Unit
) {
    AppRuleBentoCard(
        rule = rule,
        context = context,
        viewModel = viewModel,
        onEditRuleClick = onEditRuleClick
    )
}

/**
 * TuningProfilesCategory — Standalone Composable wrapper for Kernel Performance Profiles.
 * Maintained for backward compatibility.
 */
@Composable
fun TuningProfilesCategory(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    onAddAppClick: () -> Unit = {},
    onEditRuleClick: (AppProfileRule) -> Unit = {},
    onExtremeConfirmRequired: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.loadAppAutomationCooldown(context)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        ProfilesCategoryTabRow(
            selectedTab = uiState.selectedProfileTab,
            onSelectTab = { viewModel.selectProfileTab(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        )

        if (uiState.selectedProfileTab == 1) {
            ProfilesAutomationScreen(
                uiState = uiState,
                viewModel = viewModel,
                context = context,
                onAddAppClick = onAddAppClick,
                onEditRuleClick = onEditRuleClick,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                tuningProfilesCategoryItems(
                    uiState = uiState,
                    viewModel = viewModel,
                    context = context,
                    onAddAppClick = onAddAppClick,
                    onEditRuleClick = onEditRuleClick,
                    onExtremeConfirmRequired = onExtremeConfirmRequired
                )
            }
        }
    }
}
