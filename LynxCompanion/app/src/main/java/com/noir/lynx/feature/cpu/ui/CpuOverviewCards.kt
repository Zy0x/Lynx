package com.noir.lynx.feature.cpu.ui

import com.noir.lynx.core.locale.localizedUiText
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.designsystem.card.*
import com.noir.lynx.designsystem.controls.*
import com.noir.lynx.designsystem.tweak.*
import com.noir.lynx.designsystem.profile.*

import java.util.Locale
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
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.*
import com.noir.lynx.ui.*

const val PERFORMA_LABEL = "Performa"

/**
 * CpuTabRow — High-performance Segmented Tab Pill Row for CPU Subscreen.
 * Redesigned to be 100% visually consistent with ProfilesCategoryTabRow in Kernel Performance Profiles.
 */
@Composable
fun CpuTabRow(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val tabs = listOf(
        Triple(0, PERFORMA_LABEL, Icons.Default.Bolt),
        Triple(1, "Sistem", Icons.Default.SettingsSuggest),
        Triple(2, "Monitor", Icons.Default.Analytics)
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
                val animatedBg by animateColorAsState(
                    targetValue = if (isSelected) Color(0xFF1E2638) else Color.Transparent,
                    animationSpec = tween(durationMillis = 180),
                    label = "cpu_tab_bg_$index"
                )
                val animatedText by animateColorAsState(
                    targetValue = if (isSelected) TextPrimary else TextSecondary.copy(alpha = 0.65f),
                    animationSpec = tween(durationMillis = 180),
                    label = "cpu_tab_text_$index"
                )
                val animatedIcon by animateColorAsState(
                    targetValue = if (isSelected) Color(0xFFD0DCF0) else TextSecondary.copy(alpha = 0.5f),
                    animationSpec = tween(durationMillis = 180),
                    label = "cpu_tab_icon_$index"
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
                            contentDescription = localizedUiText(title),
                            tint = animatedIcon,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
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

// ============================================================
//  2. CPU DASHBOARD HERO CARD (ZEN REASSURANCE & BENTO ARCHITECTURE)
// ============================================================

/**
 * CpuDashboardHeroCard — Zen Bento Hero status overview.
 * Harmonized with Profile Kernel Performa (tab0_hero_active_card) design language.
 */
@Composable
fun CpuDashboardHeroCard(
    uiState: LynxUiState,
    modifier: Modifier = Modifier
) {
    val cores = uiState.cpuCores
    val totalLoad = uiState.totalCpuLoadPercent.coerceIn(0, 100)
    val peakKhz = cores.filter { it.isOnline }.maxOfOrNull { it.curFreqKhz } ?: 0L
    val peakGhz = peakKhz / 1000000f
    val tempC: Int = uiState.resolveCpuTempC()
    val currentProfile = uiState.cpuComprehensiveProfile.lowercase()

    val profileLabel = when (currentProfile) {
        "battery" -> "Hemat Daya (Powersave)"
        "performance", "gaming" -> "Performa Tinggi (Performance)"
        "extreme" -> "Ekstrem (Extreme)"
        else -> "Seimbang (Balance)"
    }
    val profileDesc = when (currentProfile) {
        "battery" -> "Efisiensi daya maksimal, pembatasan frekuensi untuk suhu sejuk dan baterai tahan lama"
        "performance", "gaming" -> "Respons clock instan, devfreq reaktif, dan frekuensi minimum ditingkatkan"
        "extreme" -> "Bypass batasan termal, seluruh core terkunci pada frekuensi puncak 100%"
        else -> "Adaptif seimbang, responsif saat interaksi dan hemat daya siaga saat idle"
    }
    val profileAccent = when (currentProfile) {
        "battery" -> AccentGreen
        "performance", "gaming" -> AccentOrange
        "extreme" -> AccentRed
        else -> AccentCyan
    }
    val profileIcon = when (currentProfile) {
        "battery" -> Icons.Default.BatteryChargingFull
        "performance", "gaming" -> Icons.Default.Bolt
        "extreme" -> Icons.Default.LocalFireDepartment
        else -> Icons.Default.Tune
    }

    val sched = uiState.schedulerInfo
    val isGuardActive = sched.antiThrottlingGuardEnabled || sched.ppmDlptBypassEnabled || !sched.ppmThermalThrottlingEnabled
    val isThrottled = uiState.isCpuThermalThrottled
    val healthQuality = uiState.cpuHealthQuality
    val healthAccent = when {
        isThrottled -> AccentRed
        isGuardActive && healthQuality == CpuHealthQuality.WARM -> AccentCyan
        healthQuality == CpuHealthQuality.WARM -> AccentOrange
        else -> AccentGreen
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF10141F),
        border = BorderStroke(
            0.8.dp,
            Brush.verticalGradient(
                listOf(
                    profileAccent.copy(alpha = 0.28f),
                    Color(0x15FFFFFF)
                )
            )
        ),
        modifier = modifier.fillMaxWidth()
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
                            color = profileAccent.copy(alpha = 0.18f),
                            modifier = Modifier.size(12.dp)
                        ) {}
                        Surface(
                            shape = CircleShape,
                            color = profileAccent,
                            modifier = Modifier.size(6.dp)
                        ) {}
                    }
                    Text(
                        text = "Status Frekuensi CPU",
                        color = TextSecondary.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Chipset Pill
                    val socName = if (uiState.socPlatformName.isNotBlank()) uiState.socPlatformName else "ARMv8 SoC"
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF141824),
                        border = BorderStroke(0.5.dp, Color(0x18FFFFFF))
                    ) {
                        Text(
                            text = socName,
                            color = TextSecondary.copy(alpha = 0.85f),
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                        )
                    }

                    // Guard / Thermal Status Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = healthAccent.copy(alpha = 0.14f),
                        border = BorderStroke(0.5.dp, healthAccent.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = when {
                                isThrottled -> "Throttled"
                                isGuardActive -> "Guard Aktif"
                                else -> "Optimal"
                            },
                            color = healthAccent.copy(alpha = 0.95f),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                        )
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
                    color = profileAccent.copy(alpha = 0.10f),
                    border = BorderStroke(0.6.dp, profileAccent.copy(alpha = 0.25f)),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = profileIcon,
                            contentDescription = null,
                            tint = profileAccent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profileLabel,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.2).sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = profileDesc,
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
                    val peakStr = if (peakGhz > 0f) String.format(Locale.US, "%.2f GHz", peakGhz) else "--"
                    val ceilingStr = if (isGuardActive) "Plafon 100%" else "OEM Plafon"
                    val tempColor = if (tempC >= 75) AccentRed else if (tempC >= 60) AccentOrange else AccentGreen

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Clock", color = TextSecondary.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        Text(peakStr, color = TextPrimary.copy(alpha = 0.95f), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                    }
                    Text("•", color = TextSecondary.copy(alpha = 0.3f), fontSize = 9.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Suhu", color = TextSecondary.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        Text("$tempC°C", color = tempColor, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                    }
                    Text("•", color = TextSecondary.copy(alpha = 0.3f), fontSize = 9.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Beban", color = TextSecondary.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        Text("$totalLoad%", color = TextPrimary.copy(alpha = 0.95f), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                    }
                    Text("•", color = TextSecondary.copy(alpha = 0.3f), fontSize = 9.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Guard", color = TextSecondary.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        Text(ceilingStr, color = if (isGuardActive) AccentCyan else TextPrimary.copy(alpha = 0.85f), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ============================================================
//  3. CPU SMART RECOMMENDATION BANNER (ZEN BENTO SLIM STRIP)
// ============================================================

/**
 * CpuSmartRecommendationBanner — Non-intrusive recommendation banner.
 * Styled with calm subtle borders and clean action buttons.
 */
@Composable
fun CpuSmartRecommendationBanner(
    recommendation: String,
    onApplyRecommendation: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF14120E),
        border = BorderStroke(
            0.8.dp,
            Brush.verticalGradient(
                listOf(
                    AccentOrange.copy(alpha = 0.35f),
                    Color(0x15FFFFFF)
                )
            )
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentOrange.copy(alpha = 0.12f),
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = AccentOrange,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = recommendation,
                    fontSize = 11.sp,
                    color = TextPrimary.copy(alpha = 0.9f),
                    lineHeight = 14.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    onClick = onApplyRecommendation,
                    shape = RoundedCornerShape(8.dp),
                    color = AccentOrange.copy(alpha = 0.18f),
                    border = BorderStroke(0.6.dp, AccentOrange.copy(alpha = 0.45f)),
                    modifier = Modifier.height(30.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Terapkan",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentOrange
                        )
                    }
                }
                Surface(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0x10FFFFFF),
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = localizedUiText("Tutup"),
                            tint = TextSecondary.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
