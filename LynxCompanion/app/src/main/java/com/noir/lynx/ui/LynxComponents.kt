package com.noir.lynx.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.*

// ============================================================
//  Material 3 Expressive — Clean Minimalist Obsidian Design Tokens
// ============================================================

val BgDeepOled       = Color(0xFF07080A) // Midnight Obsidian (True OLED)
val BgSurfaceLowest  = Color(0xFF090A0F) // Surface Container Lowest
val BgCard           = Color(0xFF12141D) // Deep Graphite Container (Clean Matte)
val BgElevated       = Color(0xFF181B26) // Elevated Tile Container
val BgGlassPill      = Color(0x14FFFFFF) // Subtle Frosted Pill Background

val AccentCyan       = Color(0xFF00F5A0) // Luminous Cyber Mint
val AccentCyanDim    = Color(0x1F00F5A0)
val AccentBlue       = Color(0xFF00A3FF) // Electric Azure
val AccentBlueDim    = Color(0x1F00A3FF)
val AccentOrange     = Color(0xFFFF9F2E) // Sunset Amber Flame
val AccentOrangeDim  = Color(0x1FFF9F2E)
val AccentRed        = Color(0xFFFF3B5C) // Neon Crimson
val AccentRedDim     = Color(0x1FFF3B5C)
val AccentPurple     = Color(0xFFA855F7) // Royal Amethyst
val AccentGreen      = Color(0xFF10B981) // Emerald Mint

val TextPrimary      = Color(0xFFF8FAFC) // Ultra Clean Soft White
val TextSecondary    = Color(0xFF94A3B8) // Cool Slate Grey
val TextTertiary     = Color(0xFF64748B) // Subtle Metallic Grey
val BorderSubtle     = Color(0x14FFFFFF) // Hairline 8% Border
val BorderGlass      = Color(0x1EFFFFFF) // Subtle 12% Border

// ============================================================
//  LynxCard — Luxury Minimalist Container (Option A Clean)
// ============================================================

@Composable
fun LynxCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accentColor: Color = AccentCyan,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(22.dp),
        color = BgCard,
        border = BorderStroke(0.8.dp, BorderSubtle),
        tonalElevation = 2.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            if (title.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 14.dp)
                ) {
                    if (icon != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BgElevated,
                            border = BorderStroke(0.8.dp, BorderSubtle),
                            modifier = Modifier.padding(end = 10.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        letterSpacing = 0.sp,
                    )
                }
            }
            content()
        }
    }
}

// ============================================================
//  LynxSwitch — Luxury Toggle Row
// ============================================================

@Composable
fun LynxSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subLabel: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 14.dp)
        ) {
            Text(
                text = label,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) TextPrimary else TextTertiary,
            )
            if (subLabel != null) {
                Text(
                    text = subLabel,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = BgDeepOled,
                checkedTrackColor = AccentCyan,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = BgElevated,
                uncheckedBorderColor = BorderSubtle,
            ),
        )
    }
}

// ============================================================
//  LynxSlider — Labeled Luxury Slider with Value Badge
// ============================================================

@Composable
fun LynxSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    displayValue: String,
    accentColor: Color = AccentCyan,
    modifier: Modifier = Modifier,
) {
    val start = if (valueRange.start.isFinite()) valueRange.start else 0f
    val end = if (valueRange.endInclusive.isFinite()) valueRange.endInclusive else 100f
    val safeStart = minOf(start, end)
    val safeEnd = maxOf(start, end).let { if (it <= safeStart) safeStart + 1f else it }
    val safeRange = safeStart..safeEnd
    val safeValue = if (value.isFinite()) value.coerceIn(safeStart, safeEnd) else safeStart
    val safeSteps = steps.coerceAtLeast(0)

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = accentColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f))
            ) {
                Text(
                    text = displayValue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        Slider(
            value = safeValue,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = safeRange,
            steps = safeSteps,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = BgElevated,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ============================================================
//  ProfileGrid — Luxury Bento Profile Selector
// ============================================================

@Composable
fun ProfileGrid(
    currentProfile: String,
    onProfileSelected: (String) -> Unit,
    onExtremeConfirmRequired: () -> Unit,
) {
    val profiles = listOf(
        ProfileItem("auto", "Auto (AI)", "Adaptive Foreground", Icons.Default.Bolt, AccentCyan),
        ProfileItem("balance", "Balance", "EAS Daily Efficiency", Icons.Default.Tune, AccentBlue),
        ProfileItem("performance", "Performance", "High Clock Sustained", Icons.Default.Speed, AccentOrange),
        ProfileItem("extreme", "Extreme", "Unrestricted Bypass", Icons.Default.LocalFireDepartment, AccentRed),
        ProfileItem("powersave", "Powersave", "Deep C-States Endurance", Icons.Default.BatteryChargingFull, Color(0xFF00E676)),
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Hero top card for Auto AI
        val autoProfile = profiles.first()
        val isAutoActive = currentProfile == autoProfile.id
        ProfileBentoTile(
            item = autoProfile,
            isActive = isAutoActive,
            isWide = true,
            onClick = { onProfileSelected(autoProfile.id) }
        )

        // 2x2 Grid for Balance, Perf, Extreme, Powersave
        val rest = profiles.drop(1)
        rest.chunked(2).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { item ->
                    val isActive = currentProfile == item.id
                    ProfileBentoTile(
                        item = item,
                        isActive = isActive,
                        isWide = false,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (item.id == "extreme" && currentProfile != "extreme") {
                                onExtremeConfirmRequired()
                            } else {
                                onProfileSelected(item.id)
                            }
                        }
                    )
                }
            }
        }
    }
}

private data class ProfileItem(
    val id: String,
    val name: String,
    val desc: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
private fun ProfileBentoTile(
    item: ProfileItem,
    isActive: Boolean,
    isWide: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isActive) item.color.copy(alpha = 0.12f) else BgElevated,
        border = BorderStroke(
            width = if (isActive) 1.2.dp else 0.8.dp,
            color = if (isActive) item.color.copy(alpha = 0.6f) else BorderSubtle
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(if (isWide) 64.dp else 74.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isActive) item.color.copy(alpha = 0.2f) else Color(0x0FFFFFFF),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = if (isActive) item.color else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = item.name,
                        fontSize = 13.5.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isActive) item.color else TextPrimary,
                    )
                    Text(
                        text = item.desc,
                        fontSize = 10.sp,
                        color = TextSecondary,
                        maxLines = 1,
                    )
                }
            }

            if (isActive) {
                Surface(
                    shape = CircleShape,
                    color = item.color,
                    modifier = Modifier
                        .size(8.dp)
                        .padding(end = 2.dp)
                ) {}
            }
        }
    }
}

// ============================================================
//  LynxActionButton — Clean Minimalist Action Button
// ============================================================

@Composable
fun LynxActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isLoading: Boolean = false,
    accentColor: Color = AccentCyan,
) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accentColor.copy(alpha = 0.14f),
            contentColor = accentColor,
        ),
        border = BorderStroke(0.8.dp, accentColor.copy(alpha = 0.35f)),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = accentColor,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(end = 8.dp)
                    )
                }
                Text(
                    text = text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ============================================================
//  Status Badge & Header Helpers
// ============================================================

@Composable
fun StatusBadge(profile: String) {
    val (label, color) = when (profile) {
        "auto"        -> "Auto (AI)" to AccentCyan
        "balance"     -> "Balance" to AccentBlue
        "performance" -> "Performance" to AccentOrange
        "extreme"     -> "Extreme 🔥" to AccentRed
        "powersave"   -> "Powersave" to Color(0xFF00E676)
        else          -> "Standby" to TextSecondary
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = BgElevated,
        border = BorderStroke(0.8.dp, BorderSubtle),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = color,
                modifier = Modifier
                    .size(6.dp)
                    .padding(end = 2.dp)
            ) {}
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                letterSpacing = 0.sp,
            )
        }
    }
}

@Composable
fun SocChip(soc: String, isSpoofed: Boolean) {
    val label = when {
        soc == "qcom" && !isSpoofed -> "Snapdragon"
        soc == "mtk"  && !isSpoofed -> "MediaTek Dimensity"
        soc == "qcom" && isSpoofed  -> "Snapdragon (Spoofed)"
        soc == "mtk"  && isSpoofed  -> "MediaTek (Spoofed)"
        else                         -> "Universal Linux"
    }
    val color = if (isSpoofed) AccentOrange else AccentCyan

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(0.8.dp, color.copy(alpha = 0.25f))
    ) {
        Text(
            text = label,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

// ============================================================
//  Live Hardware Telemetry Card — Luxury Bento-Box Layout
// ============================================================

@Composable
fun LiveTelemetryCard(telemetry: TelemetryData?) {
    val tel = telemetry ?: TelemetryData()

    LynxCard(
        title = "Live Telemetry & Hardware Gauges",
        icon = Icons.Default.Speed,
        accentColor = AccentCyan
    ) {
        // Bento Row: GPU & Battery
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // GPU Bento Box
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GPU Clock",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            letterSpacing = 0.sp
                        )
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (tel.gpuFreq > 0) "${tel.gpuFreq}" else "300",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = " MHz",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = BgSurfaceLowest,
                        border = BorderStroke(0.8.dp, BorderSubtle)
                    ) {
                        Text(
                            text = if (tel.gpuBusy > 0) "Load ${tel.gpuBusy}%" else "Dynamic DVFS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Battery & Thermals Bento Box
            val tempFloat = tel.temp.toFloatOrNull() ?: 38f
            val tempColor = when {
                tempFloat >= 45f -> AccentRed
                tempFloat >= 40f -> AccentOrange
                else             -> AccentCyan
            }

            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Battery & Temp",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            letterSpacing = 0.sp
                        )
                        Icon(
                            imageVector = Icons.Default.Thermostat,
                            contentDescription = null,
                            tint = tempColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = tel.temp.ifBlank { "38.7" },
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = tempColor
                        )
                        Text(
                            text = " °C",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val curPrefix = if (tel.battCurrentMa > 0) "+" else ""
                    val wattText = if (tel.battWatt > 0.05f) {
                        val sign = if (tel.isCharging) "+" else ""
                        " • ${sign}${String.format(java.util.Locale.US, "%.1f", tel.battWatt)}W"
                    } else ""
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = BgSurfaceLowest,
                        border = BorderStroke(0.8.dp, BorderSubtle)
                    ) {
                        Text(
                            text = "${tel.battLevel}% • ${curPrefix}${tel.battCurrentMa} mA${wattText}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Memory, ZRAM & Swap Allocation Bento (Tiered with Dynamic Discovery)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = BgElevated,
            border = BorderStroke(0.8.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                val ramProgress = if (tel.ramTotalMb > 0) {
                    (tel.ramUsedMb.toFloat() / tel.ramTotalMb.toFloat()).coerceIn(0f, 1f)
                } else 0.70f
                val ramPercent = (ramProgress * 100).toInt()
                val freeRamMb = (tel.ramTotalMb - tel.ramUsedMb).coerceAtLeast(0)

                // ── 1. BAR RAM FISIK (Dominan & Menonjol) ────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier
                                .size(14.dp)
                                .padding(end = 4.dp)
                        )
                        Text(
                            text = "RAM Fisik",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            letterSpacing = 0.sp
                        )
                    }
                    Text(
                        text = "${tel.ramUsedMb} / ${tel.ramTotalMb} MB ($ramPercent%)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan
                    )
                }

                Spacer(modifier = Modifier.height(7.dp))

                // Prominent Physical RAM Progress Bar (7.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(3.5.dp))
                        .background(Color(0xFF0E121B))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(ramProgress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.5.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(AccentCyan.copy(alpha = 0.85f), AccentCyan)
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Tersedia: $freeRamMb MB",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "Penggunaan: $ramPercent%",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                }

                // ── 2. BAR ZRAM DISK SWAP (Kecerdasan Dinamis) ────────
                val isZramActive = tel.zramTotalMb > 0
                if (isZramActive) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(
                        color = BorderSubtle,
                        thickness = 0.6.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val zramProgress = (tel.zramUsedMb.toFloat() / tel.zramTotalMb.toFloat()).coerceIn(0f, 1f)
                    val zramPercent = (zramProgress * 100).toInt()
                    val freeZramMb = (tel.zramTotalMb - tel.zramUsedMb).coerceAtLeast(0)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = AccentBlue,
                                modifier = Modifier
                                    .size(13.dp)
                                    .padding(end = 4.dp)
                            )
                            Text(
                                text = "ZRAM Swap",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                letterSpacing = 0.sp
                            )
                        }
                        Text(
                            text = "${tel.zramUsedMb} / ${tel.zramTotalMb} MB ($zramPercent%)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentBlue
                        )
                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    // ZRAM Progress Bar (4.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.5.dp)
                            .clip(RoundedCornerShape(2.5.dp))
                            .background(Color(0xFF0E121B))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(zramProgress)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.5.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(AccentBlue.copy(alpha = 0.75f), AccentBlue)
                                    )
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Bebas: $freeZramMb MB",
                            fontSize = 9.5.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "Kompresi RAM Kernel",
                            fontSize = 9.5.sp,
                            color = TextSecondary
                        )
                    }
                }

                // ── 3. BAR SWAP MEMORY (Kecerdasan Dinamis Disk Swap) ──
                val hasDedicatedDiskSwap = (tel.swapTotalMb > tel.zramTotalMb && (tel.swapTotalMb - tel.zramTotalMb) >= 64) ||
                        (tel.swapTotalMb > 0 && !isZramActive)
                if (hasDedicatedDiskSwap) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(
                        color = BorderSubtle,
                        thickness = 0.6.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val extraSwapTotal = if (isZramActive) tel.swapTotalMb - tel.zramTotalMb else tel.swapTotalMb
                    val extraSwapUsed = if (isZramActive) (tel.swapUsedMb - tel.zramUsedMb).coerceAtLeast(0) else tel.swapUsedMb
                    val swapProgress = if (extraSwapTotal > 0) (extraSwapUsed.toFloat() / extraSwapTotal.toFloat()).coerceIn(0f, 1f) else 0f
                    val swapPercent = (swapProgress * 100).toInt()
                    val freeSwapMb = (extraSwapTotal - extraSwapUsed).coerceAtLeast(0)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = null,
                                tint = AccentPurple,
                                modifier = Modifier
                                    .size(13.dp)
                                    .padding(end = 4.dp)
                            )
                            Text(
                                text = "Swap Virtual Disk",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                letterSpacing = 0.sp
                            )
                        }
                        Text(
                            text = "$extraSwapUsed / $extraSwapTotal MB ($swapPercent%)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentPurple
                        )
                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    // Swap Progress Bar (4.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.5.dp)
                            .clip(RoundedCornerShape(2.5.dp))
                            .background(Color(0xFF0E121B))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(swapProgress)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.5.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(AccentPurple.copy(alpha = 0.75f), AccentPurple)
                                    )
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Bebas: $freeSwapMb MB",
                            fontSize = 9.5.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "Paging Disk File",
                            fontSize = 9.5.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // CPU Cores Architecture Matrix (Little vs Big Clusters)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(end = 4.dp)
                )
                Text(
                    text = "Octa-Core SoC Topology",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    letterSpacing = 0.sp
                )
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle)
            ) {
                Text(
                    text = "ARMv8.2-A",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val cores = if (tel.cpu.isNotEmpty()) tel.cpu else listOf(2000000L, 2000000L, 2000000L, 2000000L, 2000000L, 2000000L, 2050000L, 2050000L)
        val littleCores = cores.take(6)
        val bigCores = cores.drop(6)

        // Cluster 0: Efficiency Cores (0-5)
        Text(
            text = "Cluster 0: Efficiency (6x Cortex-A55)",
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        littleCores.chunked(3).forEachIndexed { rowIdx, rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowItems.forEachIndexed { colIdx, freqHz ->
                    val coreIdx = rowIdx * 3 + colIdx
                    CpuCoreBentoTile(
                        coreIdx = coreIdx,
                        freqHz = freqHz,
                        isBigCore = false,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Cluster 1: Performance Cores (6-7)
        Text(
            text = "Cluster 1: Performance (2x Cortex-A76)",
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            bigCores.forEachIndexed { idx, freqHz ->
                val coreIdx = 6 + idx
                CpuCoreBentoTile(
                    coreIdx = coreIdx,
                    freqHz = freqHz,
                    isBigCore = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CpuCoreBentoTile(
    coreIdx: Int,
    freqHz: Long,
    isBigCore: Boolean,
    modifier: Modifier = Modifier
) {
    val mhz = (freqHz / 1000).toInt()
    val isOnline = mhz > 0
    val maxScale = if (isBigCore) 2400f else 2000f
    val progress = if (isOnline) (mhz / maxScale).coerceIn(0.1f, 1f) else 0f
    val accent = if (isBigCore) AccentOrange else AccentCyan

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = BgElevated,
        border = BorderStroke(0.8.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CPU $coreIdx",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
                if (isBigCore) {
                    Surface(
                        shape = CircleShape,
                        color = AccentOrange,
                        modifier = Modifier.size(4.dp)
                    ) {}
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = if (isOnline) "$mhz" else "OFF",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isOnline) TextPrimary else TextTertiary,
            )

            Text(
                text = "MHz",
                fontSize = 8.5.sp,
                color = TextTertiary,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color(0xFF0A0D14))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(1.dp))
                        .background(accent.copy(alpha = 0.8f))
                )
            }
        }
    }
}

// ============================================================
//  CpuClusterTunerCard — Luxury Cluster & Governor Tuner
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CpuClusterTunerCard(
    clusters: List<CpuClusterInfo>,
    governorTunables: Map<Int, List<GovernorTunable>> = emptyMap(),
    onFreqChange: (policyId: Int, minFreq: Long?, maxFreq: Long?) -> Unit,
    onGovChange: (policyId: Int, gov: String) -> Unit,
    onLoadTunables: (policyId: Int, gov: String) -> Unit = { _, _ -> },
    onTunableChange: (policyId: Int, gov: String, key: String, value: String) -> Unit = { _, _, _, _ -> },
) {
    // ── Bottom Sheet States ──
    var freqPickerTarget by remember { mutableStateOf<Pair<CpuClusterInfo, Boolean>?>(null) }
    var govPickerTarget by remember { mutableStateOf<CpuClusterInfo?>(null) }
    var tunablesTarget by remember { mutableStateOf<CpuClusterInfo?>(null) }

    // ── Edit Single Tunable Value Dialog ──
    var editingTunable by remember { mutableStateOf<Triple<Int, String, GovernorTunable>?>(null) }
    var editValueText by remember { mutableStateOf("") }

    if (editingTunable != null) {
        val (pId, gov, tunable) = editingTunable!!
        AlertDialog(
            onDismissRequest = { editingTunable = null },
            containerColor = BgCard,
            titleContentColor = TextPrimary,
            title = { Text("Edit Tunable: ${tunable.displayName}", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        "Sysfs Key: ${tunable.key}",
                        color = TextSecondary, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    OutlinedTextField(
                        value = editValueText,
                        onValueChange = { editValueText = it },
                        label = { Text("Nilai (${tunable.unit.ifBlank { "angka" }})") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = BorderGlass,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editValueText.isNotBlank()) {
                        onTunableChange(pId, gov, tunable.key, editValueText.trim())
                        editingTunable = null
                    }
                }) {
                    Text("Terapkan", color = AccentCyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingTunable = null }) {
                    Text("Batal", color = TextSecondary)
                }
            }
        )
    }

    // ── Main Card: Dynamic CPU Clusters ──
    LynxCard(
        title = "Dynamic CPU Clusters & Governors",
        icon = Icons.Default.Tune,
        accentColor = AccentCyan
    ) {
        if (clusters.isEmpty()) {
            Text(
                text = "Memuat topologi cluster CPU...",
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                clusters.forEach { cluster ->
                    val isPerfCluster = cluster.id > 0
                    val clusterAccent = if (isPerfCluster) AccentOrange else AccentCyan
                    val freqs = cluster.availFreqs.sorted()
                    val minMhz = (cluster.curMin / 1000).toInt()
                    val maxMhz = (cluster.curMax / 1000).toInt()

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = BgElevated,
                        border = BorderStroke(1.dp, clusterAccent.copy(alpha = 0.22f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // 1. Cluster Header Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = CircleShape,
                                        color = clusterAccent.copy(alpha = 0.16f),
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${cluster.id}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = clusterAccent
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Policy ${cluster.id}: ${cluster.role}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.2.sp,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "Cores: ${cluster.cpus}",
                                            fontSize = 10.5.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = clusterAccent.copy(alpha = 0.14f),
                                    border = BorderStroke(0.8.dp, clusterAccent.copy(alpha = 0.35f))
                                ) {
                                    Text(
                                        text = cluster.curGov,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = clusterAccent,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // 2. Mini Range Bar (Spectrum Indicator)
                            if (freqs.isNotEmpty()) {
                                val minLimit = freqs.first()
                                val maxLimit = freqs.last()
                                val span = (maxLimit - minLimit).coerceAtLeast(1L).toFloat()
                                val startFraction = ((cluster.curMin.coerceAtLeast(minLimit) - minLimit).toFloat() / span).coerceIn(0f, 1f)
                                val endFraction = ((cluster.curMax.coerceAtMost(maxLimit) - minLimit).toFloat() / span).coerceIn(startFraction, 1f)
                                val isPinned = cluster.curMin >= cluster.curMax

                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                    // Custom visual spectrum track with precision bounds
                                    BoxWithConstraints(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(Color(0xFF0F1219))
                                    ) {
                                        val totalWidth = maxWidth
                                        if (isPinned) {
                                            // Saat frekuensi terkunci/pinned: tampilkan pin indicator di posisi titik frekuensi
                                            val pinWidth = 10.dp
                                            val pinOffset = ((totalWidth - pinWidth) * startFraction).coerceIn(0.dp, totalWidth - pinWidth)
                                            Box(
                                                modifier = Modifier
                                                    .offset(x = pinOffset)
                                                    .width(pinWidth)
                                                    .fillMaxHeight()
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(clusterAccent)
                                            )
                                        } else {
                                            // Rentang dinamis dari startFraction ke endFraction
                                            val barStart = totalWidth * startFraction
                                            val barEnd = totalWidth * endFraction
                                            val barWidth = (barEnd - barStart).coerceAtLeast(6.dp)
                                            val clampedOffset = barStart.coerceIn(0.dp, totalWidth - barWidth)

                                            Box(
                                                modifier = Modifier
                                                    .offset(x = clampedOffset)
                                                    .width(barWidth)
                                                    .fillMaxHeight()
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(
                                                        Brush.horizontalGradient(
                                                            listOf(
                                                                clusterAccent.copy(alpha = 0.6f),
                                                                clusterAccent
                                                            )
                                                        )
                                                    )
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("${minLimit / 1000} MHz", fontSize = 9.sp, color = TextTertiary)
                                        Text(
                                            text = if (isPinned) "Terkunci: ${cluster.curMin / 1000} MHz" else "Rentang Operasi Aktif",
                                            fontSize = 9.sp,
                                            color = clusterAccent.copy(alpha = 0.85f),
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text("${maxLimit / 1000} MHz", fontSize = 9.sp, color = TextTertiary)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // 3. Dual Pill Dropdown Selector (Min & Max Freq)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Min Frequency Pill
                                Surface(
                                    onClick = { freqPickerTarget = Pair(cluster, true) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF10121A),
                                    border = BorderStroke(1.dp, clusterAccent.copy(alpha = 0.25f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Batas Bawah",
                                                fontSize = 9.5.sp,
                                                color = TextSecondary
                                            )
                                            Text(
                                                text = "$minMhz MHz",
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = clusterAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                // Max Frequency Pill
                                Surface(
                                    onClick = { freqPickerTarget = Pair(cluster, false) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF10121A),
                                    border = BorderStroke(1.dp, clusterAccent.copy(alpha = 0.25f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Batas Puncak",
                                                fontSize = 9.5.sp,
                                                color = TextSecondary
                                            )
                                            Text(
                                                text = "$maxMhz MHz",
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = clusterAccent
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = clusterAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // 4. Governor Pill & Quick Tunables Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Governor Selector Pill
                                Surface(
                                    onClick = { govPickerTarget = cluster },
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF10121A),
                                    border = BorderStroke(1.dp, BorderSubtle),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Speed,
                                                contentDescription = null,
                                                tint = clusterAccent,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Column {
                                                Text(
                                                    text = "Governor",
                                                    fontSize = 9.sp,
                                                    color = TextSecondary
                                                )
                                                Text(
                                                    text = cluster.curGov,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                // Quick Tunables Button
                                Surface(
                                    onClick = {
                                        tunablesTarget = cluster
                                        onLoadTunables(cluster.id, cluster.curGov)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = clusterAccent.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, clusterAccent.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = null,
                                            tint = clusterAccent,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "Tunables",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = clusterAccent
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

    // ── Translucent Frosted Glass Modal Bottom Sheet: Frequency Picker ──
    if (freqPickerTarget != null) {
        val (targetCluster, isMinPicker) = freqPickerTarget!!
        val clusterAccent = if (targetCluster.id > 0) AccentOrange else AccentCyan
        val freqs = targetCluster.availFreqs.sorted()
        val curFreq = if (isMinPicker) targetCluster.curMin else targetCluster.curMax

        ModalBottomSheet(
            onDismissRequest = { freqPickerTarget = null },
            containerColor = Color(0xFA0D1017),
            scrimColor = Color.Black.copy(alpha = 0.65f),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = {
                Surface(
                    color = Color(0x33FFFFFF),
                    shape = CircleShape,
                    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 38.dp, height = 4.dp)
                ) {}
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isMinPicker) "Pilih Batas Minimum" else "Pilih Batas Puncak",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Policy ${targetCluster.id}: ${targetCluster.role} (${targetCluster.cpus})",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = clusterAccent.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, clusterAccent.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "${curFreq / 1000} MHz",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = clusterAccent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                HorizontalDivider(color = BorderGlass.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 12.dp))

                // Stepped frequencies grid (2 columns)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    freqs.chunked(2).forEach { rowFreqs ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowFreqs.forEach { f ->
                                val mhz = (f / 1000).toInt()
                                val isSelected = f == curFreq
                                val isAutoAdjust = if (isMinPicker) f > targetCluster.curMax else f < targetCluster.curMin

                                Surface(
                                    onClick = {
                                        if (isMinPicker) {
                                            val newMax = if (f > targetCluster.curMax) f else targetCluster.curMax
                                            onFreqChange(targetCluster.id, f, newMax)
                                        } else {
                                            val newMin = if (f < targetCluster.curMin) f else targetCluster.curMin
                                            onFreqChange(targetCluster.id, newMin, f)
                                        }
                                        freqPickerTarget = null
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = when {
                                        isSelected -> clusterAccent.copy(alpha = 0.22f)
                                        isAutoAdjust -> Color(0xFF1C1914)
                                        else -> Color(0xFF141722)
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        when {
                                            isSelected -> clusterAccent
                                            isAutoAdjust -> Color(0x66E5A93C)
                                            else -> BorderGlass
                                        }
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = if (mhz >= 1000) String.format(java.util.Locale.US, "%.2f GHz", mhz / 1000f) else "$mhz MHz",
                                                fontSize = 12.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = when {
                                                    isSelected -> clusterAccent
                                                    isAutoAdjust -> Color(0xFFE5C158)
                                                    else -> TextPrimary
                                                }
                                            )
                                            Text(
                                                text = "$mhz MHz",
                                                fontSize = 9.sp,
                                                color = if (isAutoAdjust) Color(0xFF998855) else TextSecondary
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = clusterAccent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        } else if (isAutoAdjust) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0x33E5A93C)
                                            ) {
                                                Text(
                                                    text = if (isMinPicker) "Auto Max" else "Auto Min",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFE5C158),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (rowFreqs.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Translucent Frosted Glass Modal Bottom Sheet: Governor Picker ──
    if (govPickerTarget != null) {
        val targetCluster = govPickerTarget!!
        val clusterAccent = if (targetCluster.id > 0) AccentOrange else AccentCyan

        ModalBottomSheet(
            onDismissRequest = { govPickerTarget = null },
            containerColor = Color(0xFA0D1017),
            scrimColor = Color.Black.copy(alpha = 0.65f),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = {
                Surface(
                    color = Color(0x33FFFFFF),
                    shape = CircleShape,
                    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 38.dp, height = 4.dp)
                ) {}
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Pilih Scaling Governor",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Policy ${targetCluster.id}: ${targetCluster.role} • Algoritma Respon Jam CPU",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = clusterAccent.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, clusterAccent.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = targetCluster.curGov,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = clusterAccent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                HorizontalDivider(color = BorderGlass.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    targetCluster.availGovs.forEach { gov ->
                        val isSelected = gov == targetCluster.curGov
                        val (tag, desc) = when (gov.lowercase()) {
                            "schedutil" -> Pair("Direkomendasikan", "EAS energy-aware scheduler bawaan kernel Linux modern")
                            "performance" -> Pair("Performa Maksimal", "Kunci frekuensi tertinggi setiap saat tanpa throttling")
                            "powersave" -> Pair("Ekstrem Hemat", "Kunci clock terendah untuk memaksimalkan daya tahan baterai")
                            "ondemand" -> Pair("Tradisional", "Naik instan ke batas atas saat CPU terdeteksi sibuk")
                            "conservative" -> Pair("Bertahap", "Menaikkan dan menurunkan frekuensi secara halus")
                            "userspace" -> Pair("Manual", "Frekuensi dikontrol langsung oleh aplikasi ruang pengguna")
                            else -> Pair("Alternatif", "Algoritma pengontrol frekuensi OEM")
                        }

                        Surface(
                            onClick = {
                                onGovChange(targetCluster.id, gov)
                                govPickerTarget = null
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) clusterAccent.copy(alpha = 0.2f) else Color(0xFF141722),
                            border = BorderStroke(1.dp, if (isSelected) clusterAccent else BorderGlass),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = gov,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) clusterAccent else TextPrimary
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isSelected) clusterAccent.copy(alpha = 0.2f) else Color(0xFF1C1F2B)
                                        ) {
                                            Text(
                                                text = tag,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) clusterAccent else TextSecondary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = desc,
                                        fontSize = 10.5.sp,
                                        color = TextSecondary,
                                        lineHeight = 14.sp
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = clusterAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Translucent Frosted Glass Modal Bottom Sheet: Governor Tunables ──
    if (tunablesTarget != null) {
        val targetCluster = tunablesTarget!!
        val clusterAccent = if (targetCluster.id > 0) AccentOrange else AccentCyan
        val tunables = governorTunables[targetCluster.id] ?: emptyList()

        ModalBottomSheet(
            onDismissRequest = { tunablesTarget = null },
            containerColor = Color(0xFA0D1017),
            scrimColor = Color.Black.copy(alpha = 0.65f),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = {
                Surface(
                    color = Color(0x33FFFFFF),
                    shape = CircleShape,
                    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 38.dp, height = 4.dp)
                ) {}
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Governor Tunables: ${targetCluster.curGov}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Policy ${targetCluster.id}: ${targetCluster.role} • Sysfs Kernel Tweaks",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = clusterAccent.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, clusterAccent.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "${tunables.size} Tunable",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = clusterAccent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                HorizontalDivider(color = BorderGlass.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 12.dp))

                if (tunables.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Tidak ada tunable sysfs yang terdeteksi untuk governor ${targetCluster.curGov}",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tunables.forEach { tunable ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF141722),
                                border = BorderStroke(1.dp, BorderSubtle),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = tunable.displayName,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = tunable.key,
                                            fontSize = 9.5.sp,
                                            color = TextSecondary,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    if (tunable.isBoolean) {
                                        Switch(
                                            checked = tunable.currentValue == "1",
                                            onCheckedChange = { isChecked ->
                                                onTunableChange(targetCluster.id, targetCluster.curGov, tunable.key, if (isChecked) "1" else "0")
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = clusterAccent,
                                                checkedTrackColor = clusterAccent.copy(alpha = 0.4f)
                                            )
                                        )
                                    } else {
                                        Surface(
                                            onClick = {
                                                editingTunable = Triple(targetCluster.id, targetCluster.curGov, tunable)
                                                editValueText = tunable.currentValue
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            color = clusterAccent.copy(alpha = 0.16f),
                                            border = BorderStroke(1.dp, clusterAccent.copy(alpha = 0.4f))
                                        ) {
                                            Text(
                                                text = "${tunable.currentValue} ${tunable.unit}".trim(),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = clusterAccent,
                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
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
}

// ============================================================
//  FlasherCard — AnyKernel3 Kernel Flasher
// ============================================================

@Composable
fun FlasherCard(
    isFlashing: Boolean,
    flashLog: String,
    onFlash: (zipPath: String) -> Unit,
) {
    var zipInput by remember { mutableStateOf("/sdcard/Download/kernel.zip") }

    LynxCard(
        title = "AnyKernel3 Kernel Flasher",
        icon = Icons.Default.Bolt,
        accentColor = AccentCyan
    ) {
        Text(
            text = "Flash kernel kustom (ZIP) langsung dari sistem. Kernel lama akan otomatis dicadangkan sebelum proses penulisan.",
            fontSize = 12.sp,
            color = TextSecondary,
            lineHeight = 17.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        OutlinedTextField(
            value = zipInput,
            onValueChange = { zipInput = it },
            label = { Text("Path File ZIP Kernel") },
            placeholder = { Text("/sdcard/Download/kernel.zip") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentCyan,
                unfocusedBorderColor = BorderSubtle,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = BgElevated,
                unfocusedContainerColor = BgElevated,
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        LynxActionButton(
            text = "Flash Kernel Sekarang",
            icon = Icons.Default.Bolt,
            isLoading = isFlashing,
            onClick = {
                if (zipInput.isNotBlank()) onFlash(zipInput.trim())
            },
            accentColor = AccentCyan
        )

        if (flashLog.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF090B10),
                border = BorderStroke(1.dp, BorderGlass),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
            ) {
                Text(
                    text = flashLog,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = AccentCyan,
                    modifier = Modifier
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }
}

// ============================================================
//  BootBackupCard — Partition Backup & Restore
// ============================================================

@Composable
fun BootBackupCard(
    backups: List<BootBackupInfo>,
    isBackingUp: Boolean,
    onBackup: () -> Unit,
    onRestore: (path: String) -> Unit,
) {
    var restoreTarget by remember { mutableStateOf<BootBackupInfo?>(null) }

    if (restoreTarget != null) {
        AlertDialog(
            onDismissRequest = { restoreTarget = null },
            containerColor = BgCard,
            titleContentColor = AccentOrange,
            textContentColor = TextSecondary,
            title = { Text("⚠️ Pulihkan Partisi Kernel?") },
            text = { Text("Apakah Anda yakin ingin memulihkan partisi dari backup '${restoreTarget?.name}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        restoreTarget?.let { onRestore(it.path) }
                        restoreTarget = null
                    }
                ) {
                    Text("Pulihkan", color = AccentOrange, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { restoreTarget = null }) {
                    Text("Batal", color = TextSecondary)
                }
            }
        )
    }

    LynxCard(
        title = "Cadangan Partisi Boot",
        icon = Icons.Default.Storage,
        accentColor = AccentBlue
    ) {
        Text(
            text = "Cadangkan dan pulihkan partisi boot dan init_boot perangkat Anda kapan saja.",
            fontSize = 12.sp,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LynxActionButton(
            text = "Buat Backup Boot Baru",
            icon = Icons.Default.Storage,
            isLoading = isBackingUp,
            onClick = onBackup,
            accentColor = AccentBlue
        )

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "RIWAYAT BACKUP TERSIMPAN",
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (backups.isEmpty()) {
            Text(
                text = "Belum ada backup partisi tersimpan.",
                fontSize = 12.sp,
                color = TextTertiary
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                backups.forEach { b ->
                    val mb = (b.size / (1024f * 1024f))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BgElevated,
                        border = BorderStroke(1.dp, BorderGlass),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = b.name,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = String.format("%.1f MB", mb),
                                    fontSize = 10.5.sp,
                                    color = TextSecondary
                                )
                            }
                            OutlinedButton(
                                onClick = { restoreTarget = b },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentOrange),
                                border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.5f))
                            ) {
                                Text(text = "Restore", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
//  StandaloneModuleBanner — Golden Amber Frosted Glass Banner
// ============================================================

@Composable
fun StandaloneModuleBanner(onInstallModule: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF1B150A),
        border = BorderStroke(
            1.dp,
            Brush.horizontalGradient(
                listOf(
                    AccentOrange.copy(alpha = 0.6f),
                    AccentOrange.copy(alpha = 0.15f)
                )
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentOrange.copy(alpha = 0.2f),
                    modifier = Modifier.padding(end = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = AccentOrange,
                        modifier = Modifier
                            .padding(6.dp)
                            .size(16.dp)
                    )
                }
                Text(
                    text = "Modul Lynx Deity Belum Terpasang",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentOrange
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Aplikasi sedang berjalan dalam Standalone Root Mode. Seluruh Telemetri, CPU Cluster Tuner, dan AnyKernel Flasher aktif mandiri! Untuk mengaktifkan Smart-AI foreground daemon dan Charging Bypass, Anda dapat memasang modul root Lynx.",
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 17.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            LynxActionButton(
                text = "Pasang Modul Lynx Deity Sekarang",
                icon = Icons.Default.Build,
                onClick = onInstallModule,
                accentColor = AccentOrange
            )
        }
    }
}

// ============================================================
//  FKM PARITY: UNSUPPORTED BADGE (ZERO ASSUMPTION GUARANTEE)
// ============================================================

@Composable
fun UnsupportedBadge(reason: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = AccentOrange.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = AccentOrange.copy(alpha = 0.2f),
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = AccentOrange,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "UNSUPPORTED",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AccentOrange,
                    letterSpacing = 1.sp
                )
                Text(
                    text = reason,
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

// ============================================================
//  FKM PARITY: VOLTAGE CONTROL CARD
// ============================================================

@Composable
fun VoltageControlCard(
    voltageInfo: VoltageTableInfo,
    onApplyOffset: (Int) -> Unit,
) {
    var offsetMv by remember(voltageInfo.globalOffsetMv) { mutableStateOf(voltageInfo.globalOffsetMv) }

    LynxCard(
        title = "Voltage Control (Undervolting)",
        icon = Icons.Default.Bolt,
        accentColor = if (voltageInfo.isSupported) AccentCyan else AccentOrange
    ) {
        if (!voltageInfo.isSupported) {
            UnsupportedBadge(reason = voltageInfo.unsupportedReason)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(BgElevated.copy(alpha = 0.5f))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Global Voltage Offset", color = TextSecondary, fontSize = 12.sp)
                    Text("Terkunci (0 mV)", color = TextTertiary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    Text("LOCKED", color = TextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Global Undervolt Offset", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        text = "${if (offsetMv > 0) "+" else ""}$offsetMv mV",
                        color = if (offsetMv < 0) AccentCyan else if (offsetMv > 0) AccentRed else TextSecondary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }
                Slider(
                    value = offsetMv.toFloat(),
                    onValueChange = { offsetMv = it.toInt() },
                    valueRange = -100f..50f,
                    steps = 29,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentCyan,
                        activeTrackColor = AccentCyan,
                        inactiveTrackColor = BgElevated
                    )
                )
                LynxActionButton(
                    text = "Terapkan Offset Voltase (${offsetMv} mV)",
                    icon = Icons.Default.Check,
                    onClick = { onApplyOffset(offsetMv) },
                    accentColor = AccentCyan
                )
            }
        }
    }
}

// ============================================================
//  FKM PARITY: DISPLAY CALIBRATION CARD (KCAL & HBM)
// ============================================================

@Composable
fun DisplayCalibrationCard(
    displayCalibration: DisplayCalibrationInfo,
    onSetKcal: (Boolean, Int, Int, Int, Int, Int, Int, Int) -> Unit,
    onSetHbm: (Boolean) -> Unit,
) {
    var kcalEnabled by remember(displayCalibration.kcalEnabled) { mutableStateOf(displayCalibration.kcalEnabled) }
    var red by remember(displayCalibration.red) { mutableStateOf(displayCalibration.red) }
    var green by remember(displayCalibration.green) { mutableStateOf(displayCalibration.green) }
    var blue by remember(displayCalibration.blue) { mutableStateOf(displayCalibration.blue) }
    var saturation by remember(displayCalibration.saturation) { mutableStateOf(displayCalibration.saturation) }

    LynxCard(
        title = "Kalibrasi Layar (KCAL & HBM)",
        icon = Icons.Default.Palette,
        accentColor = AccentPurple
    ) {
        // --- HBM Section ---
        Text("High Brightness Mode (HBM)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        if (!displayCalibration.isHbmSupported) {
            UnsupportedBadge(reason = displayCalibration.hbmUnsupportedReason)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("HBM Sunlight Booster", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text("Meningkatkan batas kecerahan panel display di luar slider standar", color = TextSecondary, fontSize = 10.5.sp)
                }
                Switch(
                    checked = displayCalibration.hbmEnabled,
                    onCheckedChange = { onSetHbm(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = BgDeepOled, checkedTrackColor = AccentPurple)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Divider(color = BorderSubtle)
        Spacer(modifier = Modifier.height(14.dp))

        // --- KCAL Section ---
        Text("KCAL Color Calibration", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        if (!displayCalibration.isKcalSupported) {
            UnsupportedBadge(reason = displayCalibration.kcalUnsupportedReason)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Aktifkan KCAL Engine", color = TextSecondary, fontSize = 12.sp)
                Switch(
                    checked = kcalEnabled,
                    onCheckedChange = { kcalEnabled = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = BgDeepOled, checkedTrackColor = AccentPurple)
                )
            }
            if (kcalEnabled) {
                Text("Red: $red", color = AccentRed, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                Slider(value = red.toFloat(), onValueChange = { red = it.toInt() }, valueRange = 100f..256f, colors = SliderDefaults.colors(thumbColor = AccentRed, activeTrackColor = AccentRed))

                Text("Green: $green", color = AccentGreen, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                Slider(value = green.toFloat(), onValueChange = { green = it.toInt() }, valueRange = 100f..256f, colors = SliderDefaults.colors(thumbColor = AccentGreen, activeTrackColor = AccentGreen))

                Text("Blue: $blue", color = AccentBlue, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                Slider(value = blue.toFloat(), onValueChange = { blue = it.toInt() }, valueRange = 100f..256f, colors = SliderDefaults.colors(thumbColor = AccentBlue, activeTrackColor = AccentBlue))

                Text("Saturation: $saturation", color = AccentPurple, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                Slider(value = saturation.toFloat(), onValueChange = { saturation = it.toInt() }, valueRange = 128f..383f, colors = SliderDefaults.colors(thumbColor = AccentPurple, activeTrackColor = AccentPurple))

                LynxActionButton(
                    text = "Terapkan Kalibrasi Warna",
                    icon = Icons.Default.Save,
                    onClick = {
                        onSetKcal(true, red, green, blue, saturation, displayCalibration.value, displayCalibration.contrast, displayCalibration.hue)
                    },
                    accentColor = AccentPurple
                )
            }
        }
    }
}

// ============================================================
//  FKM PARITY: SOUND CONTROL CARD
// ============================================================

@Composable
fun SoundControlCard(
    soundControl: SoundControlInfo,
    onSetGain: (Int, Int, Int, Int, Boolean) -> Unit,
) {
    var hpL by remember(soundControl.headphoneGainL) { mutableStateOf(soundControl.headphoneGainL) }
    var hpR by remember(soundControl.headphoneGainR) { mutableStateOf(soundControl.headphoneGainR) }
    var spk by remember(soundControl.speakerGain) { mutableStateOf(soundControl.speakerGain) }
    var mic by remember(soundControl.micGain) { mutableStateOf(soundControl.micGain) }
    var hpMode by remember(soundControl.highPerfMode) { mutableStateOf(soundControl.highPerfMode) }

    LynxCard(
        title = "Sound Control (Audio Gain Booster)",
        icon = Icons.Default.VolumeUp,
        accentColor = if (soundControl.isSupported) AccentGreen else AccentOrange
    ) {
        if (!soundControl.isSupported) {
            UnsupportedBadge(reason = soundControl.unsupportedReason)
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Headphone Gain (L/R): ${hpL}dB / ${hpR}dB", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Slider(value = hpL.toFloat(), onValueChange = { hpL = it.toInt(); hpR = it.toInt() }, valueRange = -10f..20f, colors = SliderDefaults.colors(thumbColor = AccentGreen, activeTrackColor = AccentGreen))

                Text("Speaker Gain: ${spk}dB", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Slider(value = spk.toFloat(), onValueChange = { spk = it.toInt() }, valueRange = -10f..20f, colors = SliderDefaults.colors(thumbColor = AccentGreen, activeTrackColor = AccentGreen))

                Text("Microphone Gain: ${mic}dB", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Slider(value = mic.toFloat(), onValueChange = { mic = it.toInt() }, valueRange = -10f..20f, colors = SliderDefaults.colors(thumbColor = AccentGreen, activeTrackColor = AccentGreen))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("High Performance Audio DAC", color = TextSecondary, fontSize = 12.sp)
                    Switch(checked = hpMode, onCheckedChange = { hpMode = it }, colors = SwitchDefaults.colors(checkedThumbColor = BgDeepOled, checkedTrackColor = AccentGreen))
                }

                LynxActionButton(
                    text = "Simpan Pengaturan Gain Audio",
                    icon = Icons.Default.Save,
                    onClick = { onSetGain(hpL, hpR, spk, mic, hpMode) },
                    accentColor = AccentGreen
                )
            }
        }
    }
}

// ============================================================
//  FKM PARITY: BATTERY HEALTH & DEEP SLEEP CARD
// ============================================================

@Composable
fun BatteryHealthStatsCard(
    batteryHealth: BatteryHealthStats,
    onRefresh: () -> Unit,
) {
    LynxCard(
        title = "Kesehatan Baterai & Deep Sleep Guard",
        icon = Icons.Default.BatteryChargingFull,
        accentColor = AccentCyan
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Health % Bento Tile
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = BgElevated,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Favorite, null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Health", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("${batteryHealth.healthPct}%", color = AccentCyan, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        Text(if (batteryHealth.healthPct >= 80) "Kondisi Prima" else "Degradasi Moderat", color = TextTertiary, fontSize = 10.sp)
                    }
                }

                // Cycle Count Bento Tile
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = BgElevated,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Refresh, null, tint = AccentOrange, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Siklus Pengisian", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (batteryHealth.cycleCount >= 0) "${batteryHealth.cycleCount} Siklus" else "N/A",
                            color = AccentOrange,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text("Hardware Node Real", color = TextTertiary, fontSize = 10.sp)
                    }
                }
            }

            // Deep Sleep & Drain Metrics Row
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BgSurfaceLowest,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Rasio Deep Sleep (Tidur Pulas)", color = TextSecondary, fontSize = 11.5.sp)
                        Text(
                            text = "${batteryHealth.deepSleepPct.toInt()}%",
                            color = if (batteryHealth.deepSleepPct > 70f) AccentGreen else AccentOrange,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { batteryHealth.deepSleepPct / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = if (batteryHealth.deepSleepPct > 70f) AccentGreen else AccentOrange,
                        trackColor = BgElevated
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Kapasitas: ${batteryHealth.actualCapacityMah} / ${batteryHealth.designedCapacityMah} mAh", color = TextTertiary, fontSize = 10.5.sp)
                        Text("Drain: ${String.format("%.1f", batteryHealth.activeDrainRateMh)}%/h (Aktif)", color = TextTertiary, fontSize = 10.5.sp)
                    }
                }
            }

            LynxActionButton(
                text = "Segarkan Statistik Baterai & Sleep",
                icon = Icons.Default.Refresh,
                onClick = onRefresh,
                accentColor = AccentCyan
            )
        }
    }
}

// ============================================================
//  FKM PARITY: MEMORY LMK & ENTROPY TUNER CARD
// ============================================================

@Composable
fun MemoryEntropyCard(
    memoryEntropy: MemoryEntropyInfo,
    onSetEntropy: (Int, Int) -> Unit,
) {
    var readThresh by remember(memoryEntropy.readThreshold) { mutableStateOf(memoryEntropy.readThreshold) }
    var writeThresh by remember(memoryEntropy.writeThreshold) { mutableStateOf(memoryEntropy.writeThreshold) }

    LynxCard(
        title = "LMK Minfree & Entropy Tuner",
        icon = Icons.Default.Memory,
        accentColor = AccentBlue
    ) {
        // LMK Architecture status
        Text("Low Memory Killer (LMK)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        if (!memoryEntropy.isLmkLegacySupported) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = memoryEntropy.lmkReason,
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        } else {
            Text("Legacy Minfree Levels: ${memoryEntropy.minfreeMb.joinToString(", ") { "${it}MB" }}", color = TextSecondary, fontSize = 11.5.sp)
        }

        Spacer(Modifier.height(14.dp))
        Divider(color = BorderSubtle)
        Spacer(Modifier.height(14.dp))

        // Entropy Pool Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Linux Kernel Random Entropy", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(
                "${memoryEntropy.entropyAvail} / 4096 bits",
                color = AccentCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { (memoryEntropy.entropyAvail.toFloat() / 4096f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = AccentCyan,
            trackColor = BgElevated
        )

        Spacer(Modifier.height(10.dp))
        Text("Read Wakeup Threshold: $readThresh bits", color = TextSecondary, fontSize = 11.5.sp)
        Slider(
            value = readThresh.toFloat(),
            onValueChange = { readThresh = it.toInt() },
            valueRange = 32f..256f,
            colors = SliderDefaults.colors(thumbColor = AccentBlue, activeTrackColor = AccentBlue)
        )

        Text("Write Wakeup Threshold: $writeThresh bits", color = TextSecondary, fontSize = 11.5.sp)
        Slider(
            value = writeThresh.toFloat(),
            onValueChange = { writeThresh = it.toInt() },
            valueRange = 256f..2048f,
            colors = SliderDefaults.colors(thumbColor = AccentBlue, activeTrackColor = AccentBlue)
        )

        LynxActionButton(
            text = "Terapkan Threshold Entropi",
            icon = Icons.Default.Save,
            onClick = { onSetEntropy(readThresh, writeThresh) },
            accentColor = AccentBlue
        )
    }
}

// ============================================================
//  FKM PARITY: CUSTOM SCRIPT MANAGER CARD
// ============================================================

@Composable
fun CustomScriptManagerCard(
    scripts: List<CustomScriptItem>,
    onExecute: (CustomScriptItem) -> Unit,
    onSave: (CustomScriptItem) -> Unit,
    onDelete: (String) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newScript by remember { mutableStateOf("") }
    var newRunOnBoot by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = BgCard,
            title = { Text("Tambah Skrip Shell Baru", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Nama Skrip") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan, unfocusedBorderColor = BorderGlass, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                    )
                    OutlinedTextField(
                        value = newScript,
                        onValueChange = { newScript = it },
                        label = { Text("Perintah Shell (Root)") },
                        modifier = Modifier.fillMaxWidth().height(140.dp).padding(bottom = 8.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan, unfocusedBorderColor = BorderGlass, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Jalankan Saat Boot", color = TextSecondary, fontSize = 12.sp)
                        Switch(
                            checked = newRunOnBoot,
                            onCheckedChange = { newRunOnBoot = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = BgDeepOled, checkedTrackColor = AccentCyan)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank() && newScript.isNotBlank()) {
                            onSave(
                                CustomScriptItem(
                                    id = System.currentTimeMillis().toString(),
                                    name = newName.trim(),
                                    script = newScript.trim(),
                                    runOnBoot = newRunOnBoot
                                )
                            )
                            newName = ""
                            newScript = ""
                            newRunOnBoot = false
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Simpan", color = AccentCyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Batal", color = TextSecondary)
                }
            }
        )
    }

    LynxCard(
        title = "Custom Shell Script Manager",
        icon = Icons.Default.Terminal,
        accentColor = AccentCyan
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Pustaka Skrip Kernel & Sistem (${scripts.size})",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                TextButton(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = AccentCyan)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Tambah", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            scripts.forEach { item ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgElevated,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                if (item.runOnBoot) {
                                    Text("⚡ Run on boot", color = AccentCyan, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Row {
                                IconButton(onClick = { onExecute(item) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.PlayArrow, null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { onDelete(item.id) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Delete, null, tint = AccentRed, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        if (item.lastOutput.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = BgSurfaceLowest,
                                border = BorderStroke(1.dp, BorderSubtle),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = item.lastOutput,
                                    color = if (item.lastExitCode == 0) AccentCyan else AccentOrange,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
//  FKM PARITY: KERNEL DMESG VIEWER CARD
// ============================================================

@Composable
fun DmesgViewerCard(
    dmesgState: DmesgLogState,
    onLoadLogs: (String) -> Unit,
    onExport: () -> Unit,
) {
    var searchFilter by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (dmesgState.logs.isEmpty()) {
            onLoadLogs("")
        }
    }

    LynxCard(
        title = "Kernel dmesg Ring Buffer Viewer",
        icon = Icons.Default.Description,
        accentColor = AccentBlue
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchFilter,
                    onValueChange = {
                        searchFilter = it
                        onLoadLogs(it)
                    },
                    placeholder = { Text("Filter dmesg (e.g. cpu, thermal, gpu)...", color = TextSecondary, fontSize = 11.5.sp) },
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = BorderGlass,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                IconButton(onClick = { onLoadLogs(searchFilter) }) {
                    Icon(Icons.Default.Refresh, null, tint = AccentBlue)
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgSurfaceLowest,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            ) {
                if (dmesgState.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentBlue, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        dmesgState.logs.takeLast(100).forEach { line ->
                            Text(
                                text = line,
                                color = when {
                                    line.contains("error", ignoreCase = true) || line.contains("fail", ignoreCase = true) -> AccentRed
                                    line.contains("warn", ignoreCase = true) -> AccentOrange
                                    else -> TextSecondary
                                },
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.5.sp,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            LynxActionButton(
                text = "Ekspor Log Lengkap ke /sdcard/Debug/",
                icon = Icons.Default.FileDownload,
                onClick = onExport,
                accentColor = AccentBlue
            )
        }
    }
}

// ============================================================
//  Option B: Tuning Hub Bento Grid & Category Navigation
// ============================================================

enum class TuningCategory(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentColor: Color
) {
    CPU(
        id = "cpu",
        title = "CPU & Governor",
        subtitle = "Topologi Core, Hotplug & Schedutil",
        icon = Icons.Default.Memory,
        accentColor = AccentCyan
    ),
    GPU(
        id = "gpu",
        title = "GPU & Display",
        subtitle = "Clock GPU, GED Boost & Refresh Rate",
        icon = Icons.Default.SportsEsports,
        accentColor = AccentOrange
    ),
    MEMORY(
        id = "memory",
        title = "Memory & Storage",
        subtitle = "ZRAM, Swappiness, LMK & I/O",
        icon = Icons.Default.Storage,
        accentColor = AccentPurple
    ),
    CHARGING(
        id = "charging",
        title = "Battery & Charging",
        subtitle = "Bypass Charging & Extreme Fast Charge",
        icon = Icons.Default.BatteryChargingFull,
        accentColor = AccentGreen
    ),
    NETWORK(
        id = "network",
        title = "Network & Audio",
        subtitle = "TCP Congestion, Ping & Audio MMAP",
        icon = Icons.Default.Wifi,
        accentColor = AccentBlue
    ),
    SYSTEM(
        id = "system",
        title = "Subsystem & Deep Tunables",
        subtitle = "OEM Neutralizer, Otomasi & Sysfs",
        icon = Icons.Default.Security,
        accentColor = AccentRed
    );

    companion object {
        fun fromId(id: String?): TuningCategory? = values().firstOrNull { it.id == id }
    }
}

@Composable
fun SubsystemCategoryCard(
    category: TuningCategory,
    badgeText: String,
    detailText1: String,
    detailText2: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp),
        shape = RoundedCornerShape(20.dp),
        color = BgCard,
        border = BorderStroke(0.8.dp, BorderSubtle),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Left Icon Tile (min 48x48 touch visual)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = category.title,
                        tint = category.accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Middle Info Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // Title + Subtle Badge Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = category.title,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )

                    if (badgeText.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = BgElevated,
                            border = BorderStroke(0.8.dp, BorderSubtle)
                        ) {
                            Text(
                                text = badgeText,
                                color = category.accentColor,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(Modifier.height(3.dp))

                Text(
                    text = category.subtitle,
                    color = TextSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    maxLines = 1
                )

                // Clean status row (Option A: no badge clutter, clean inline specs)
                val statusItems = listOfNotNull(
                    detailText1.takeIf { it.isNotBlank() },
                    detailText2.takeIf { it.isNotBlank() }
                )
                if (statusItems.isNotEmpty()) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = statusItems.joinToString("  •  "),
                        color = TextTertiary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }

            // Right Chevron (Indicating navigable into sub-screen)
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Buka ${category.title}",
                tint = TextTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun TuningBentoCard(
    category: TuningCategory,
    badgeText: String,
    detailText1: String,
    detailText2: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SubsystemCategoryCard(
        category = category,
        badgeText = badgeText,
        detailText1 = detailText1,
        detailText2 = detailText2,
        onClick = onClick,
        modifier = modifier
    )
}


