package com.noir.lynx.feature.memory.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
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
 * MemoryHeroCard — Clearly separated live telemetry panels for Physical RAM (MB) and ZRAM Swap (GB),
 * each with dedicated utilization meters, status badges, and safe background cleanup action.
 */
@Composable
internal fun MemoryHeroCard(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val telemetry = uiState.telemetry

    // ── Physical RAM (kept in MB as requested) ──
    val ramTotalMb = telemetry?.ramTotalMb?.takeIf { it > 0 } ?: 4096
    val ramUsedMb = (telemetry?.ramUsedMb ?: 0).coerceIn(0, ramTotalMb)
    val ramAvailMb = (ramTotalMb - ramUsedMb).coerceAtLeast(0)
    val ramRatio = if (ramTotalMb > 0) (ramUsedMb.toFloat() / ramTotalMb.toFloat()).coerceIn(0f, 1f) else 0f
    val ramPercent = (ramRatio * 100f).toInt()
    val ramHealthColor = when {
        ramPercent >= 85 -> AccentOrange
        ramPercent >= 70 -> AccentCyan
        else -> AccentGreen
    }

    // ── ZRAM Swap (strictly isolated section in GB) ──
    val zramTotalMb = (telemetry?.zramTotalMb ?: uiState.state.memory.zramSizeMb).coerceAtLeast(0)
    val zramUsedMb = if (zramTotalMb > 0) {
        (telemetry?.zramUsedMb?.takeIf { it >= 0 } ?: telemetry?.swapUsedMb ?: 0).coerceIn(0, zramTotalMb)
    } else 0
    val zramAvailMb = (zramTotalMb - zramUsedMb).coerceAtLeast(0)
    val zramRatio = if (zramTotalMb > 0) (zramUsedMb.toFloat() / zramTotalMb.toFloat()).coerceIn(0f, 1f) else 0f
    val zramPercent = (zramRatio * 100f).toInt()
    val activeAlgo = uiState.zramCompAlgorithm.ifBlank { "lz4" }
    val swappiness = uiState.vmAdvanced.swappiness
    val zramColor = when {
        zramTotalMb <= 0 -> TextTertiary
        zramPercent >= 85 -> AccentOrange
        else -> AccentPurple
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
            // ── Block 1: Physical RAM ──
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        StatusDot(color = ramHealthColor)
                        Text(
                            text = "RAM FISIK",
                            color = TextPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ramHealthColor.copy(alpha = 0.12f),
                        border = BorderStroke(0.6.dp, ramHealthColor.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "$ramPercent% Terpakai",
                            color = ramHealthColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BgElevated,
                        border = BorderStroke(0.6.dp, BorderSubtle),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (ramUsedMb > 0) "$ramUsedMb / $ramTotalMb MB" else "$ramTotalMb MB Total",
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
                }

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
                            .background(Brush.horizontalGradient(listOf(AccentBlue, ramHealthColor)))
                    )
                }
            }

            HorizontalDivider(color = BorderGlass, thickness = 0.6.dp)

            // ── Block 2: ZRAM Swap ──
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        StatusDot(color = zramColor)
                        Text(
                            text = "ZRAM SWAP",
                            color = TextPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = zramColor.copy(alpha = 0.12f),
                        border = BorderStroke(0.6.dp, zramColor.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = if (zramTotalMb > 0) {
                                "${activeAlgo.uppercase()} • $zramPercent% Terpakai"
                            } else {
                                "NONAKTIF"
                            },
                            color = zramColor,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BgElevated,
                        border = BorderStroke(0.6.dp, BorderSubtle),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                tint = zramColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (zramTotalMb > 0) {
                                "${formatZramGb(zramUsedMb, includeUnit = false)} / ${formatZramGb(zramTotalMb)}"
                            } else {
                                "0.0 GB (Nonaktif)"
                            },
                            color = if (zramTotalMb > 0) TextPrimary else TextSecondary,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.2).sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (zramTotalMb > 0) {
                                "Tersedia ${formatZramGb(zramAvailMb)} • Swappiness $swappiness"
                            } else {
                                "ZRAM swap dimatikan • Swappiness $swappiness"
                            },
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(BgSurfaceLowest)
                ) {
                    if (zramTotalMb > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(zramRatio.coerceAtLeast(0.02f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(Brush.horizontalGradient(listOf(AccentCyan, zramColor)))
                        )
                    }
                }
            }

            // ── Safe Background RAM Cleanup Button ──
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

@Composable
private fun StatusDot(color: Color) {
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.20f),
        modifier = Modifier.size(12.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                shape = CircleShape,
                color = color,
                modifier = Modifier.size(6.dp)
            ) {}
        }
    }
}
