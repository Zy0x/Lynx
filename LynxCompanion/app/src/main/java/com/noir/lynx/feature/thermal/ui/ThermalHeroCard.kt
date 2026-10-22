package com.noir.lynx.feature.thermal.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.*
import com.noir.lynx.designsystem.theme.*

/**
 * ThermalHeroCard — Zen Reassurance Hero Card for Hardware Thermal Management.
 * Redesigned to match the gold-standard calm aesthetic of TuningProfilesCategory.
 * Anti AI slop, zero redundant clutter, and psychological safety for users.
 */
@Composable
internal fun ThermalHeroCard(uiState: LynxUiState) {
    val state = uiState.state
    val validZones = remember(uiState.thermalZones) {
        uiState.thermalZones.filter { it.tempC in 15f..115f }
    }
    val maxTemp = validZones.maxOfOrNull { it.tempC } ?: (uiState.telemetry?.temp?.toFloat() ?: 0f)
    val fullBypass = state.thermal.fullBypass
    val statusDetails = uiState.thermalStatusDetails
    val mode = statusDetails.activeMode
    val isDominant = mode == ThermalEngineMode.HARDWARE_SAFETY_DOMINANT || fullBypass

    val modeTitle = when (mode) {
        ThermalEngineMode.HARDWARE_SAFETY_DOMINANT -> "Hardware Safety Dominant"
        ThermalEngineMode.THERMAL_STABLE -> "Thermal Stabil (Sustained)"
        ThermalEngineMode.PERFORMANCE -> "Performa Kompetitif"
        ThermalEngineMode.DEFAULT_OEM -> "Default OEM (Baseline)"
    }
    val modeSub = when (mode) {
        ThermalEngineMode.HARDWARE_SAFETY_DOMINANT -> "Software throttling dinonaktifkan • Proteksi silikon & baterai aktif"
        ThermalEngineMode.THERMAL_STABLE -> "Stabilisasi kurva OPP • Menahan FPS drop • Trip 52-55°C"
        ThermalEngineMode.PERFORMANCE -> "Toleransi panas tinggi • Kunci clock frekuensi maksimal"
        ThermalEngineMode.DEFAULT_OEM -> "Proteksi standar pabrik • OEM throttling aktif"
    }
    val heroAccent = when (mode) {
        ThermalEngineMode.HARDWARE_SAFETY_DOMINANT -> AccentRed
        ThermalEngineMode.THERMAL_STABLE -> AccentCyan
        ThermalEngineMode.PERFORMANCE -> AccentOrange
        ThermalEngineMode.DEFAULT_OEM -> AccentGreen
    }
    val heroIcon = when (mode) {
        ThermalEngineMode.HARDWARE_SAFETY_DOMINANT -> Icons.Default.LocalFireDepartment
        ThermalEngineMode.THERMAL_STABLE -> Icons.Default.Shield
        ThermalEngineMode.PERFORMANCE -> Icons.Default.Bolt
        ThermalEngineMode.DEFAULT_OEM -> Icons.Default.Tune
    }

    val animatedAccent by animateColorAsState(
        targetValue = heroAccent,
        animationSpec = tween(durationMillis = 200),
        label = "thermal_hero_accent"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF10141F),
        border = BorderStroke(
            0.8.dp,
            Brush.verticalGradient(
                listOf(
                    animatedAccent.copy(alpha = 0.28f),
                    Color(0x15FFFFFF)
                )
            )
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── 1. Top Row: Zen Reassurance Status Bar ───────────
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
                            color = animatedAccent.copy(alpha = 0.18f),
                            modifier = Modifier.size(12.dp)
                        ) {}
                        Surface(
                            shape = CircleShape,
                            color = animatedAccent,
                            modifier = Modifier.size(6.dp)
                        ) {}
                    }
                    Text(
                        text = "Manajemen Termal Hardware",
                        color = TextSecondary.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Calm Status Pill
                val statusText = when {
                    isDominant -> "Bypass Throttling"
                    mode == ThermalEngineMode.THERMAL_STABLE -> "Stabil & Dingin"
                    mode == ThermalEngineMode.PERFORMANCE -> "Performa Siaga"
                    else -> "Standar Pabrik"
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = animatedAccent.copy(alpha = 0.12f),
                    border = BorderStroke(0.5.dp, animatedAccent.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = statusText,
                        color = animatedAccent,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                    )
                }
            }

            Spacer(Modifier.height(13.dp))

            // ── 2. Main Row: Icon + Title & Desc + Temp Pill ──────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = animatedAccent.copy(alpha = 0.10f),
                    border = BorderStroke(0.6.dp, animatedAccent.copy(alpha = 0.25f)),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = heroIcon,
                            contentDescription = null,
                            tint = animatedAccent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = modeTitle,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.2).sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = modeSub,
                        color = TextSecondary.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Reassuring Temperature Pill
                val tempAccent = when {
                    maxTemp >= 60f -> AccentRed
                    maxTemp >= 48f -> AccentOrange
                    else -> AccentGreen
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = tempAccent.copy(alpha = 0.12f),
                    border = BorderStroke(0.6.dp, tempAccent.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Thermostat,
                            contentDescription = null,
                            tint = tempAccent,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (maxTemp > 0) String.format("%.1f°C", maxTemp) else "--",
                            color = TextPrimary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ── 3. Subsystem Telemetry Ribbon (Calm Unified Ribbon) ──
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
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tripText = "${state.thermal.customTempLimitC}°C"
                    val floorText = "${state.overclock.cpuFloorRatio}%"
                    val joyoseText = if (state.oemNeutralizer.joyoseNeutralize) "Aktif" else "Off"
                    val totalZones = if (statusDetails.totalZonesCount > 0) statusDetails.totalZonesCount else validZones.size
                    val writableZones = if (statusDetails.writableZonesCount > 0) statusDetails.writableZonesCount else validZones.count { it.isWritable }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Batas Trip", color = TextSecondary.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        Text(tripText, color = AccentOrange.copy(alpha = 0.9f), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("•", color = TextSecondary.copy(alpha = 0.3f), fontSize = 9.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("CPU Floor", color = TextSecondary.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        Text(floorText, color = AccentCyan.copy(alpha = 0.9f), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("•", color = TextSecondary.copy(alpha = 0.3f), fontSize = 9.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Anti-OEM", color = TextSecondary.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        Text(joyoseText, color = if (state.oemNeutralizer.joyoseNeutralize) AccentGreen else TextSecondary, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("•", color = TextSecondary.copy(alpha = 0.3f), fontSize = 9.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Sensor", color = TextSecondary.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        Text("$writableZones/$totalZones RW", color = TextPrimary.copy(alpha = 0.85f), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // ── 4. Psychological Reassurance Footer ──────────────
            Spacer(Modifier.height(9.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (isDominant) Icons.Default.Shield else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (isDominant) AccentGreen else AccentCyan.copy(alpha = 0.8f),
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = if (isDominant) {
                        "Proteksi silikon kernel (LMh) & cutoff baterai (45°C) tetap aktif demi keamanan hardware."
                    } else {
                        "Thermal governor mengatur clock dinamis sesuai kurva OPP secara otomatis."
                    },
                    color = TextSecondary.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
