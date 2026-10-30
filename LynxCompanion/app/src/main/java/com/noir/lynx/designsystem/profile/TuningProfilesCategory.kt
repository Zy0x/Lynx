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

// ProfilesFeedbackBottomSheet is modularized in ProfilesFeedbackBottomSheet.kt

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
    onExtremeConfirmRequired: () -> Unit,
    targetTab: Int = uiState.selectedProfileTab
) {
    val state = uiState.state

    when (targetTab) {
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
    targetTab: Int = uiState.selectedProfileTab,
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
