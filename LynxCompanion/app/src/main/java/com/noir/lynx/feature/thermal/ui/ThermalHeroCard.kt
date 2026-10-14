package com.noir.lynx.feature.thermal.ui
import com.noir.lynx.ui.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.*
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.controls.LynxSwitch
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.designsystem.tweak.LynxTweakSheet
import com.noir.lynx.designsystem.tweak.LynxTweakTile
import com.noir.lynx.designsystem.tweak.TweakConfig
import com.noir.lynx.ui.LynxViewModel
import kotlinx.coroutines.delay

@Composable
internal fun ThermalHeroCard(uiState: LynxUiState) {
    val state = uiState.state
    val validZones = uiState.thermalZones.filter { it.tempC in 15f..115f }
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
            ThermalEngineMode.HARDWARE_SAFETY_DOMINANT -> "Software Throttling Dimatikan • Proteksi Silikon & Baterai Aktif"
            ThermalEngineMode.THERMAL_STABLE -> "Stabilisasi Kurva OPP • Menahan FPS Drop • Trip 52-55°C"
            ThermalEngineMode.PERFORMANCE -> "Toleransi Panas Tinggi • Kunci Clock Maksimal"
            ThermalEngineMode.DEFAULT_OEM -> "Proteksi Standar Pabrik • OEM Throttling Aktif"
        }
        val heroAccent = when (mode) {
            ThermalEngineMode.HARDWARE_SAFETY_DOMINANT -> AccentRed
            ThermalEngineMode.THERMAL_STABLE -> AccentCyan
            ThermalEngineMode.PERFORMANCE -> AccentOrange
            ThermalEngineMode.DEFAULT_OEM -> AccentGreen
        }
        val heroAccentDim = when (mode) {
            ThermalEngineMode.HARDWARE_SAFETY_DOMINANT -> AccentRedDim
            ThermalEngineMode.THERMAL_STABLE -> AccentCyanDim
            ThermalEngineMode.PERFORMANCE -> AccentOrangeDim
            ThermalEngineMode.DEFAULT_OEM -> AccentGreenDim
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = BgCard,
            border = BorderStroke(1.dp, heroAccent.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(heroAccentDim),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isDominant) Icons.Default.LocalFireDepartment else Icons.Default.Shield,
                                contentDescription = null,
                                tint = heroAccent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    modeTitle,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp,
                                    letterSpacing = (-0.2).sp
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = heroAccent.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        "AKTIF",
                                        color = heroAccent,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                modeSub,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Temperature Pill
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = when {
                            maxTemp >= 60f -> AccentRedDim
                            maxTemp >= 48f -> AccentOrangeDim
                            else -> AccentCyanDim
                        },
                        border = BorderStroke(
                            1.dp,
                            when {
                                maxTemp >= 60f -> AccentRed.copy(alpha = 0.5f)
                                maxTemp >= 48f -> AccentOrange.copy(alpha = 0.5f)
                                else -> AccentCyan.copy(alpha = 0.5f)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Thermostat,
                                contentDescription = null,
                                tint = when {
                                    maxTemp >= 60f -> AccentRed
                                    maxTemp >= 48f -> AccentOrange
                                    else -> AccentCyan
                                },
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (maxTemp > 0) String.format("%.1f°C", maxTemp) else "--",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Transparent Status Transparency Grid (Production Audit)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Software Throttling", color = TextSecondary, fontSize = 11.sp)
                            Text(
                                if (statusDetails.softwareThrottlingActive) "AKTIF (OEM Daemon)" else "NONAKTIF (By-passed)",
                                color = if (statusDetails.softwareThrottlingActive) AccentOrange else AccentCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Hardware Safety", color = TextSecondary, fontSize = 11.sp)
                            Text(
                                if (statusDetails.hardwareSafetyActive) "AKTIF (LMh / SPM Protected)" else "TERBATAS",
                                color = AccentGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Battery Guard", color = TextSecondary, fontSize = 11.sp)
                            Text(
                                if (statusDetails.batteryGuardActive) "AKTIF (Maks 45°C Cutoff)" else "NONAKTIF",
                                color = AccentGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Writable Zones", color = TextSecondary, fontSize = 11.sp)
                            val totalCount = if (statusDetails.totalZonesCount > 0) statusDetails.totalZonesCount else validZones.size
                            val writableCount = if (statusDetails.writableZonesCount > 0) statusDetails.writableZonesCount else validZones.count { it.isWritable }
                            Text(
                                "$writableCount / $totalCount Sensor",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Terakhir Diterapkan", color = TextSecondary, fontSize = 11.sp)
                            Text(
                                statusDetails.lastAppliedTimestamp,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                        if (statusDetails.transactionId.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Transaction ID", color = TextSecondary, fontSize = 10.5.sp)
                                Text(
                                    statusDetails.transactionId,
                                    color = AccentCyan,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // 3 Mini Stat Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Chip 1: Trip Point
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgElevated,
                        border = BorderStroke(0.8.dp, BorderSubtle),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text("Batas Trip", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${state.thermal.customTempLimitC}°C",
                                color = AccentOrange,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Chip 2: Clock Floor
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgElevated,
                        border = BorderStroke(0.8.dp, BorderSubtle),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text("CPU Floor", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${state.overclock.cpuFloorRatio}%",
                                color = AccentCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Chip 3: Anti-Joyose
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgElevated,
                        border = BorderStroke(0.8.dp, BorderSubtle),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text("Anti-OEM", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (state.oemNeutralizer.joyoseNeutralize) "Aktif" else "Off",
                                color = if (state.oemNeutralizer.joyoseNeutralize) AccentGreen else TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
}
