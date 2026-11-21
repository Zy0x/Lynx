package com.noir.lynx.feature.memory.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.controls.LynxActionButton
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.ui.LynxViewModel
import com.noir.lynx.ui.cleanRam

/**
 * MemoryHeroCard — Clean real-time RAM & Swap summary + safe background cleanup.
 * Properly padded at 16.dp horizontal margin to align seamlessly with all LynxCard containers.
 */
@Composable
internal fun MemoryHeroCard(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val telemetry = uiState.telemetry
    val ramTotalMb = telemetry?.ramTotalMb?.takeIf { it > 0 } ?: 4096
    val ramUsedMb = (telemetry?.ramUsedMb ?: 0).coerceIn(0, ramTotalMb)
    val ramAvailMb = (ramTotalMb - ramUsedMb).coerceAtLeast(0)
    val ramRatio = if (ramTotalMb > 0) (ramUsedMb.toFloat() / ramTotalMb.toFloat()).coerceIn(0f, 1f) else 0f
    val ramPercent = (ramRatio * 100f).toInt()

    val zramConfiguredMb = uiState.state.memory.zramSizeMb
    val zramTotalMb = (telemetry?.zramTotalMb?.takeIf { it > 0 } ?: telemetry?.swapTotalMb?.takeIf { it > 0 } ?: zramConfiguredMb)
    val zramUsedMb = (telemetry?.zramUsedMb?.takeIf { it > 0 } ?: telemetry?.swapUsedMb ?: 0).coerceAtLeast(0)
    val activeAlgo = uiState.zramCompAlgorithm.ifBlank { "lz4" }
    val swappiness = uiState.vmAdvanced.swappiness

    val healthColor = when {
        ramPercent >= 85 -> AccentOrange
        ramPercent >= 70 -> AccentCyan
        else -> AccentGreen
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF10141F),
        border = BorderStroke(
            0.8.dp,
            Brush.verticalGradient(
                listOf(
                    AccentCyan.copy(alpha = 0.28f),
                    Color(0x15FFFFFF)
                )
            )
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Status Row
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
                            color = healthColor.copy(alpha = 0.20f),
                            modifier = Modifier.size(12.dp)
                        ) {}
                        Surface(
                            shape = CircleShape,
                            color = healthColor,
                            modifier = Modifier.size(6.dp)
                        ) {}
                    }
                    Text(
                        text = "Status Memori & Swap",
                        color = TextSecondary.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AccentCyan.copy(alpha = 0.12f),
                    border = BorderStroke(0.6.dp, AccentCyan.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = if (zramTotalMb > 0) "Swap $zramUsedMb / $zramTotalMb MB" else "ZRAM Off",
                        color = AccentCyan,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Main RAM Metric Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgElevated,
                    border = BorderStroke(0.6.dp, BorderSubtle),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (ramUsedMb > 0) "$ramUsedMb / $ramTotalMb MB" else "$ramTotalMb MB Total RAM",
                        color = TextPrimary,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (ramUsedMb > 0) "Tersedia ~$ramAvailMb MB" else "Memantau penggunaan RAM",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = healthColor.copy(alpha = 0.12f),
                    border = BorderStroke(0.6.dp, healthColor.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = "$ramPercent% Terpakai",
                        color = healthColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                    )
                }
            }

            // Live RAM Utilization Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(BgSurfaceLowest)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(ramRatio.coerceAtLeast(0.02f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(Brush.horizontalGradient(listOf(AccentBlue, healthColor)))
                )
            }

            // Concise Non-Redundant Telemetry Ribbon
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(0.6.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Kompresi", color = TextSecondary, fontSize = 10.5.sp)
                        Text(activeAlgo.uppercase(), color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Swappiness", color = TextSecondary, fontSize = 10.5.sp)
                        Text("$swappiness", color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    if (uiState.ksmStats.supported) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("KSM", color = TextSecondary, fontSize = 10.5.sp)
                            Text(
                                text = if (uiState.ksmStats.enabled) "Aktif" else "Off",
                                color = if (uiState.ksmStats.enabled) AccentGreen else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Safe Background RAM Cleanup Button
            LynxActionButton(
                text = if (uiState.isCleaningRam) "Membersihkan Cache Latar..." else "Bersihkan Cache & Buffer RAM",
                icon = Icons.Default.CleaningServices,
                isLoading = uiState.isCleaningRam,
                accentColor = AccentCyan,
                onClick = { viewModel.cleanRam() }
            )
        }
    }
}
