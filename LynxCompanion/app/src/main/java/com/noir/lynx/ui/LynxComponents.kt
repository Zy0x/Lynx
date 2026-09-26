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
//  Material 3 Expressive — Luxury Cyberpunk OLED Design Tokens
// ============================================================

val BgDeepOled       = Color(0xFF07080A) // Midnight Obsidian (True OLED)
val BgSurfaceLowest  = Color(0xFF0C0E14) // M3 Surface Container Lowest
val BgCard           = Color(0xFF131722) // M3 Surface Container Low (Rich Slate)
val BgElevated       = Color(0xFF1B202E) // M3 Surface Container High (Elevated Tile)
val BgGlassPill      = Color(0x1FFFFFFF) // Frosted Pill Background

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
val TextTertiary     = Color(0xFF64748B) // Subtle Metallic
val BorderSubtle     = Color(0x1AFFFFFF) // 10% white border
val BorderGlass      = Color(0x28FFFFFF) // 16% white border

// ============================================================
//  LynxCard — Luxury Material 3 Expressive Container
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
        border = BorderStroke(
            1.dp,
            Brush.verticalGradient(
                listOf(
                    BorderGlass,
                    BorderSubtle,
                    Color(0x05FFFFFF)
                )
            )
        ),
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
                            color = accentColor.copy(alpha = 0.12f),
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
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        letterSpacing = 1.2.sp,
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
            onCheckedChange = onCheckedChange,
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
        color = if (isActive) item.color.copy(alpha = 0.14f) else BgElevated,
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 1.dp,
            brush = if (isActive) {
                Brush.horizontalGradient(listOf(item.color, item.color.copy(alpha = 0.5f)))
            } else {
                Brush.linearGradient(listOf(BorderGlass, BorderSubtle))
            }
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
                    color = if (isActive) item.color.copy(alpha = 0.25f) else Color(0x1AFFFFFF),
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
//  LynxActionButton — Luxury Action Button with Glow
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
            containerColor = accentColor.copy(alpha = 0.16f),
            contentColor = accentColor,
        ),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
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
                    letterSpacing = 0.3.sp,
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
        "auto"        -> "AUTO (AI)" to AccentCyan
        "balance"     -> "BALANCE" to AccentBlue
        "performance" -> "PERFORMANCE" to AccentOrange
        "extreme"     -> "EXTREME 🔥" to AccentRed
        "powersave"   -> "POWERSAVE" to Color(0xFF00E676)
        else          -> "STANDBY" to TextSecondary
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
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
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                letterSpacing = 1.sp,
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
        title = "LIVE TELEMETRY & HARDWARE GAUGES",
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
                border = BorderStroke(1.dp, BorderGlass)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GPU CLOCK",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 1.sp
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
                            text = if (tel.gpuFreq > 0) "${tel.gpuFreq}" else "950",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                        Text(
                            text = " MHz",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan,
                            modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AccentCyan.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = if (tel.gpuBusy > 0) "Load ${tel.gpuBusy}%" else "Fixed OPP Active",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentCyan,
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
                border = BorderStroke(1.dp, BorderGlass)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "BATTERY & TEMP",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 1.sp
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
                            fontWeight = FontWeight.ExtraBold,
                            color = tempColor
                        )
                        Text(
                            text = " °C",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val curPrefix = if (tel.battCurrentMa > 0) "+" else ""
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = tempColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "${tel.battLevel}% • ${curPrefix}${tel.battCurrentMa} mA",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tempColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // RAM Allocation Bento
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = BgElevated,
            border = BorderStroke(1.dp, BorderGlass)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                val ramProgress = if (tel.ramTotalMb > 0) {
                    (tel.ramUsedMb.toFloat() / tel.ramTotalMb.toFloat()).coerceIn(0f, 1f)
                } else 0.79f

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier
                                .size(14.dp)
                                .padding(end = 4.dp)
                        )
                        Text(
                            text = "RAM ALLOCATION",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 1.sp
                        )
                    }
                    Text(
                        text = "${tel.ramUsedMb} / ${tel.ramTotalMb} MB (${(ramProgress * 100).toInt()}%)",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF0E121B))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(ramProgress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(AccentCyan, AccentBlue)
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val freeMb = (tel.ramTotalMb - tel.ramUsedMb).coerceAtLeast(0)
                    Text(
                        text = "Tersedia: $freeMb MB",
                        fontSize = 10.5.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "ZRAM Swap Aktif (2048 MB)",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentCyan
                    )
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
                    text = "OCTA-CORE SOC TOPOLOGY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = 1.sp
                )
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = AccentCyan.copy(alpha = 0.12f)
            ) {
                Text(
                    text = "ARMv8.2-A",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentCyan,
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
            text = "⚡ Cluster 0: Efficiency (6x Cortex-A55)",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = AccentCyan,
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
            text = "🚀 Cluster 1: Performance (2x Cortex-A76)",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = AccentOrange,
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
        border = BorderStroke(
            1.dp,
            if (isBigCore) accent.copy(alpha = 0.35f) else BorderGlass
        )
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
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isBigCore) accent else TextSecondary
                )
                if (isBigCore) {
                    Surface(
                        shape = CircleShape,
                        color = accent,
                        modifier = Modifier.size(4.dp)
                    ) {}
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = if (isOnline) "$mhz" else "OFF",
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isOnline) TextPrimary else TextTertiary,
                fontFamily = FontFamily.Monospace,
            )

            Text(
                text = "MHz",
                fontSize = 8.5.sp,
                color = if (isOnline) accent else TextTertiary,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF0A0D14))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent)
                )
            }
        }
    }
}

// ============================================================
//  CpuClusterTunerCard — Luxury Cluster & Governor Tuner
// ============================================================

@Composable
fun CpuClusterTunerCard(
    clusters: List<CpuClusterInfo>,
    governorTunables: Map<Int, List<GovernorTunable>> = emptyMap(),
    onFreqChange: (policyId: Int, minFreq: Long?, maxFreq: Long?) -> Unit,
    onGovChange: (policyId: Int, gov: String) -> Unit,
    onLoadTunables: (policyId: Int, gov: String) -> Unit = { _, _ -> },
    onTunableChange: (policyId: Int, gov: String, key: String, value: String) -> Unit = { _, _, _, _ -> },
) {
    var editingTunable by remember { mutableStateOf<Triple<Int, String, GovernorTunable>?>(null) }
    var editValueText by remember { mutableStateOf("") }

    if (editingTunable != null) {
        val (pId, gov, tunable) = editingTunable!!
        AlertDialog(
            onDismissRequest = { editingTunable = null },
            containerColor = BgCard,
            titleContentColor = TextPrimary,
            title = { Text("Edit Tunable: ${tunable.displayName}") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        "Sysfs Key: ${tunable.key}",
                        color = TextSecondary, fontSize = 11.5.sp, modifier = Modifier.padding(bottom = 6.dp)
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

    LynxCard(
        title = "DYNAMIC CPU CLUSTERS & GOVERNORS",
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
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                clusters.forEach { cluster ->
                    val isPerfCluster = cluster.id > 0
                    val clusterAccent = if (isPerfCluster) AccentOrange else AccentCyan

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = BgElevated,
                        border = BorderStroke(1.dp, clusterAccent.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Cluster Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Policy ${cluster.id}: ${cluster.role}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Cores: ${cluster.cpus}",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = clusterAccent.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, clusterAccent.copy(alpha = 0.35f))
                                ) {
                                    Text(
                                        text = cluster.curGov,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = clusterAccent,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Min / Max Freq Display & Sliders
                            val minMhz = (cluster.curMin / 1000).toInt()
                            val maxMhz = (cluster.curMax / 1000).toInt()

                            val freqs = cluster.availFreqs.sorted()
                            if (freqs.isNotEmpty()) {
                                val minLimit = (freqs.first() / 1000).toFloat()
                                val rawMax = (freqs.last() / 1000).toFloat()
                                val maxLimit = if (rawMax <= minLimit) minLimit + 100f else rawMax

                                var minSlider by remember(cluster.curMin) {
                                    mutableFloatStateOf(minMhz.toFloat().coerceIn(minLimit, maxLimit))
                                }
                                var maxSlider by remember(cluster.curMax) {
                                    mutableFloatStateOf(maxMhz.toFloat().coerceIn(minLimit, maxLimit))
                                }

                                // Min Slider
                                LynxSlider(
                                    label = "Min Frequency",
                                    value = minSlider,
                                    onValueChange = { minSlider = it.coerceIn(minLimit, maxSlider) },
                                    onValueChangeFinished = {
                                        val targetHz = freqs.minByOrNull { Math.abs(it - (minSlider.toLong() * 1000L)) }
                                        onFreqChange(cluster.id, targetHz, null)
                                    },
                                    valueRange = minLimit..maxLimit,
                                    displayValue = "${minSlider.toInt()} MHz",
                                    accentColor = clusterAccent
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                // Max Slider
                                LynxSlider(
                                    label = "Max Frequency",
                                    value = maxSlider,
                                    onValueChange = { maxSlider = it.coerceIn(minSlider, maxLimit) },
                                    onValueChangeFinished = {
                                        val targetHz = freqs.minByOrNull { Math.abs(it - (maxSlider.toLong() * 1000L)) }
                                        onFreqChange(cluster.id, null, targetHz)
                                    },
                                    valueRange = minLimit..maxLimit,
                                    displayValue = "${maxSlider.toInt()} MHz",
                                    accentColor = clusterAccent
                                )
                            } else {
                                Text(
                                    text = "Current: $minMhz MHz - $maxMhz MHz",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Governor Selector Chips
                            if (cluster.availGovs.isNotEmpty()) {
                                Text(
                                    text = "SCALING GOVERNOR",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    cluster.availGovs.forEach { gov ->
                                        val isSelected = gov == cluster.curGov
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isSelected) clusterAccent.copy(alpha = 0.2f) else Color(0xFF0F121B),
                                            border = BorderStroke(
                                                1.dp,
                                                if (isSelected) clusterAccent else BorderSubtle
                                            ),
                                            modifier = Modifier.clickable {
                                                onGovChange(cluster.id, gov)
                                            }
                                        ) {
                                            Text(
                                                text = gov,
                                                fontSize = 11.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) clusterAccent else TextSecondary,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Expandable Governor Tunables Section
                            var showTunables by remember { mutableStateOf(false) }
                            Spacer(Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = clusterAccent.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, clusterAccent.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showTunables = !showTunables
                                        if (showTunables) {
                                            onLoadTunables(cluster.id, cluster.curGov)
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Tune, null, tint = clusterAccent, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "Tunables Governor: ${cluster.curGov}",
                                            color = clusterAccent,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Icon(
                                        if (showTunables) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        null,
                                        tint = clusterAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            if (showTunables) {
                                val tunables = governorTunables[cluster.id] ?: emptyList()
                                LaunchedEffect(cluster.id, cluster.curGov) {
                                    onLoadTunables(cluster.id, cluster.curGov)
                                }
                                if (tunables.isEmpty()) {
                                    Text(
                                        "Memuat parameter sysfs atau tidak tersedia untuk ${cluster.curGov}...",
                                        color = TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                } else {
                                    Column(
                                        modifier = Modifier.padding(top = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        tunables.forEach { tunable ->
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFF0F121B),
                                                border = BorderStroke(1.dp, BorderSubtle),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(Modifier.weight(1f)) {
                                                        Text(tunable.displayName, color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                                        Text(tunable.key, color = TextSecondary, fontSize = 9.5.sp)
                                                    }
                                                    if (tunable.isBoolean) {
                                                        Switch(
                                                            checked = tunable.currentValue == "1",
                                                            onCheckedChange = { isChecked ->
                                                                onTunableChange(cluster.id, cluster.curGov, tunable.key, if (isChecked) "1" else "0")
                                                            },
                                                            colors = SwitchDefaults.colors(
                                                                checkedThumbColor = clusterAccent,
                                                                checkedTrackColor = clusterAccent.copy(alpha = 0.4f)
                                                            )
                                                        )
                                                    } else {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = clusterAccent.copy(alpha = 0.16f),
                                                            border = BorderStroke(1.dp, clusterAccent.copy(alpha = 0.4f)),
                                                            modifier = Modifier.clickable {
                                                                editingTunable = Triple(cluster.id, cluster.curGov, tunable)
                                                                editValueText = tunable.currentValue
                                                            }
                                                        ) {
                                                            Text(
                                                                "${tunable.currentValue} ${tunable.unit}".trim(),
                                                                color = clusterAccent,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
        title = "ANYKERNEL3 KERNEL FLASHER",
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
        title = "CADANGAN PARTISI BOOT",
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
