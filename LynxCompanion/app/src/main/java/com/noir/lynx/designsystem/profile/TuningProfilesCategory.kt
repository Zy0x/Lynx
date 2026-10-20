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
 * ProfilesCategoryTabRow â€” High-performance Segmented Tab Pill Row for Kernel Performance Profiles.
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
 * ProfilesFeedbackBottomSheet â€” Elegant Dark Minimal ModalBottomSheet for Notification & Feedback Style Selection.
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
            // â”€â”€ Header â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                // Minimal icon tile â€” no heavy fill
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

            // â”€â”€ Option List â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            val feedbackOptions = listOf(
                Triple("jewel_top", "Jewel Dynamic Island (HUD)", "Kapsul permata melayang di atas layar â€” Cyberpunk HUD"),
                Triple("jewel_bottom", "Jewel Bottom Capsule (HUD)", "Kapsul permata melayang di bawah layar â€” Cyberpunk HUD"),
                Triple("system_toast", "System Toast (OS Text)", "Gelembung teks standar Android â€¢ Ringan (~8 MB RAM)"),
                Triple("status_bar", "Status Bar Saja (0 MB RAM)", "Notifikasi hening di status bar â€¢ Tanpa pop-up layar"),
                Triple("disabled", "Nonaktifkan Feedback", "Hening total â€” tanpa notifikasi atau pop-up visual")
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
                                    android.widget.Toast.makeText(context, "âš–ï¸ Lynx: Mode Seimbang", android.widget.Toast.LENGTH_SHORT).show()
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

                            // Right: Radio indicator â€” clean filled dot
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

            // â”€â”€ Divider â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            HorizontalDivider(
                color = BorderSubtle,
                thickness = 0.7.dp,
                modifier = Modifier.padding(vertical = 14.dp)
            )

            // â”€â”€ Dual Morph Switch â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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

            // â”€â”€ Live Test Button (Mode-Aware Feedback Tester) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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
                                "powersave" -> "ðŸƒ"
                                "balance" -> "âš–ï¸"
                                "performance" -> "âš¡"
                                "extreme" -> "ðŸ”¥"
                                else -> "âš–ï¸"
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
 * tuningProfilesCategoryItems â€” Modular LazyListScope items for Kernel Performance Profile Subscreen.
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
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // TAB 0: PROFILE
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

            // â”€â”€ 1. Active Status Hero Card â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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
                                    Text("â€¢", color = TextSecondary.copy(alpha = 0.3f), fontSize = 9.sp)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text("GPU", color = TextSecondary.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                        Text(gpuState, color = TextPrimary.copy(alpha = 0.9f), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Text("â€¢", color = TextSecondary.copy(alpha = 0.3f), fontSize = 9.sp)
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
                                    .joinToString(" â€¢ ") { (domain, profile) -> "${domain.storageKey.uppercase()}: ${profile.uppercase()}" }
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

            // â”€â”€ 2. Section Header: Katalog Profil â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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

            // â”€â”€ 3. Profile Catalog Grid (Direct First-Class Bento) â”€â”€
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
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // TAB 1: OTOMASI APLIKASI
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
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

// ProfilesAutomationScreen and associated components are modularized in ProfilesAutomationScreen.kt

/**
 * TuningProfilesCategory - Standalone Composable wrapper for Kernel Performance Profiles.
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
