package com.noir.lynx.feature.memory.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.ui.LynxViewModel
import com.noir.lynx.ui.triggerMountXBoost

/**
 * MountX & MicroSD Ultra Storage Engine Card
 * Akselerasi streaming aset game flash (Genshin Impact, Star Rail, Wuthering Waves)
 * via I/O block queue, F2FS gaming shield, VM dirty byte pacing, dan prioritas MMCQD.
 */
@Composable
fun MountXStorageCard(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val mountx = uiState.mountXStorageInfo

    LynxCard(
        title = "MountX & MicroSD Ultra Storage Engine",
        icon = Icons.Default.SdCard,
        accentColor = AccentPurple,
        modifier = modifier
    ) {
        // Status Bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = if (mountx.isDetected) "Penyimpanan Eksternal MountX Terdeteksi" else "Penyimpanan Blok Flash Internal",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp
                )
                Text(
                    text = if (mountx.isDetected) {
                        "Partisi: ${mountx.mountPoint} (${mountx.partitionName.uppercase()}, ${mountx.fsType.uppercase()})"
                    } else {
                        "Modul storage siap mengakselerasi I/O block queue dan mencegah stall dirty writeback."
                    },
                    color = if (mountx.isDetected) AccentPurple else TextSecondary,
                    fontSize = 10.5.sp
                )
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (mountx.isOptimized) AccentPurple.copy(alpha = 0.2f) else BgElevated,
                border = BorderStroke(0.8.dp, if (mountx.isOptimized) AccentPurple else BorderSubtle)
            ) {
                Text(
                    text = if (mountx.isOptimized) "STREAMING ACTIVE" else "STANDBY / BALANCED",
                    color = if (mountx.isOptimized) AccentPurple else TextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }

        // Diagnostic Grid
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, BorderSubtle)
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text("Flash Read-Ahead", color = TextSecondary, fontSize = 9.5.sp)
                        Text("${mountx.currentReadAheadKb} KB", color = AccentPurple, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, BorderSubtle)
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text("I/O Scheduler", color = TextSecondary, fontSize = 9.5.sp)
                        Text(mountx.currentScheduler.ifEmpty { "deadline" }, color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, BorderSubtle)
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text("VM Writeback Pacing", color = TextSecondary, fontSize = 9.5.sp)
                        val bgMb = mountx.dirtyBackgroundBytes / (1024 * 1024)
                        val dMb = mountx.dirtyBytes / (1024 * 1024)
                        val label = if (bgMb > 0) "${bgMb}MB / ${dMb}MB (Anti-Freeze)" else "Auto (Ratio)"
                        Text(label, color = AccentGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, BorderSubtle)
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text("MMCQD Realtime Thread", color = TextSecondary, fontSize = 9.5.sp)
                        val pidsCount = mountx.mmcqdPids.size
                        Text(if (pidsCount > 0) "$pidsCount Worker (-20 Prioritas)" else "Standar (0)", color = AccentOrange, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        // Action Buttons
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                onClick = { viewModel.triggerMountXBoost(true) },
                shape = RoundedCornerShape(10.dp),
                color = AccentPurple.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, AccentPurple.copy(alpha = 0.5f)),
                modifier = Modifier.weight(1.3f)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 9.dp, horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Speed, null, tint = AccentPurple, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Aktifkan Ultra Streaming", color = AccentPurple, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }

            Surface(
                onClick = { viewModel.triggerMountXBoost(false) },
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 9.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Refresh, null, tint = TextSecondary, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Kembalikan Standar", color = TextSecondary, fontWeight = FontWeight.Medium, fontSize = 11.sp)
                }
            }
        }
    }
}
