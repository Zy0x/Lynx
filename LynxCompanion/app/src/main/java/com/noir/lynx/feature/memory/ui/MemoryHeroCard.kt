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
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.ui.LynxViewModel
import com.noir.lynx.ui.cleanRam

/**
 * MemoryHeroCard — Real-time RAM & ZRAM telemetry summary + quick safe background cleanup.
 */
@Composable
internal fun MemoryHeroCard(uiState: LynxUiState, viewModel: LynxViewModel) {
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
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF10141F),
        border = BorderStroke(
            0.8.dp,
            Brush.verticalGradient(
                listOf(
                    AccentBlue.copy(alpha = 0.28f),
                    Color(0x15FFFFFF)
                )
            )
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                            color = AccentBlue.copy(alpha = 0.18f),
                            modifier = Modifier.size(12.dp)
                        ) {}
                        Surface(
                            shape = CircleShape,
                            color = AccentBlue,
                            modifier = Modifier.size(6.dp)
                        ) {}
                    }
                    Text(
                        text = "Ringkasan Memori & ZRAM",
                        color = TextSecondary.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AccentBlue.copy(alpha = 0.12f),
                    border = BorderStroke(0.5.dp, AccentBlue.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = if (zramTotalMb > 0) "Swap $zramUsedMb / $zramTotalMb MB" else "ZRAM Off",
                        color = AccentBlue,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Main RAM Metric Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AccentBlue.copy(alpha = 0.10f),
                    border = BorderStroke(0.6.dp, AccentBlue.copy(alpha = 0.25f)),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (ramUsedMb > 0) "$ramUsedMb / $ramTotalMb MB" else "$ramTotalMb MB Total RAM",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.2).sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (ramUsedMb > 0) "Sisa bebas ~$ramAvailMb MB • Swap ${activeAlgo.uppercase()}" else "Manajemen RAM & komposisi ZRAM",
                        color = TextSecondary.copy(alpha = 0.80f),
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
            Spacer(Modifier.height(10.dp))
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

            // Unified Telemetry Ribbon
            Spacer(Modifier.height(11.dp))
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Target", color = TextSecondary.copy(alpha = 0.55f), fontSize = 10.sp)
                        Text("${zramConfiguredMb} MB", color = AccentBlue, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("•", color = TextSecondary.copy(alpha = 0.3f), fontSize = 9.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Algo", color = TextSecondary.copy(alpha = 0.55f), fontSize = 10.sp)
                        Text(activeAlgo.uppercase(), color = TextPrimary, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("•", color = TextSecondary.copy(alpha = 0.3f), fontSize = 9.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Swappiness", color = TextSecondary.copy(alpha = 0.55f), fontSize = 10.sp)
                        Text("$swappiness", color = AccentCyan, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (uiState.ksmStats.supported) {
                        Text("•", color = TextSecondary.copy(alpha = 0.3f), fontSize = 9.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("KSM", color = TextSecondary.copy(alpha = 0.55f), fontSize = 10.sp)
                            Text(
                                text = if (uiState.ksmStats.enabled) "Aktif" else "Off",
                                color = if (uiState.ksmStats.enabled) AccentGreen else TextSecondary,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Safe Background RAM Cleanup Button (48.dp)
            Surface(
                onClick = { if (!uiState.isCleaningRam) viewModel.cleanRam() },
                enabled = !uiState.isCleaningRam,
                shape = RoundedCornerShape(10.dp),
                color = AccentCyan.copy(alpha = 0.12f),
                border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.42f)),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (uiState.isCleaningRam) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = AccentCyan,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Membersihkan Cache Latar...", color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.CleaningServices, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Bersihkan Proses Latar & Buffer RAM", color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
