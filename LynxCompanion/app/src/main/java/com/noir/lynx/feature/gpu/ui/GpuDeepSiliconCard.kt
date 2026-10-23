package com.noir.lynx.feature.gpu.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.GpuSiliconInfo
import com.noir.lynx.designsystem.theme.*

/**
 * GpuDeepSiliconCard - Hardware-level register and kernel power collapse tuner
 * for Qualcomm Adreno GPUs.
 */
@Composable
fun GpuDeepSiliconCard(
    silicon: GpuSiliconInfo,
    onSetSptpPc: (Boolean) -> Unit,
    onSetSkipSaveRestore: (Boolean) -> Unit,
    onSetPreemptLevel: (Int) -> Unit,
    onSetWakeNice: (Int) -> Unit,
    onSetMinPwrLevel: (Int) -> Unit,
    onSetGpuLlcSlice: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showWakeNiceDialog by remember { mutableStateOf(false) }
    var showMinPwrDialog by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = BgSurfaceLowest,
        border = BorderStroke(1.dp, BorderGlass),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── HEADER ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(AccentCyan.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Adreno Deep Silicon Tuning",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Register hardware, power collapse & scheduling kernel",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AccentCyan.copy(alpha = 0.15f),
                    border = BorderStroke(0.5.dp, AccentCyan.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "SILICON",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            HorizontalDivider(color = BorderGlass)

            // ── 1. SPTP POWER COLLAPSE ──
            SiliconToggleRow(
                title = "SPTP Power Collapse",
                description = if (silicon.sptpPcEnabled) "ON: Efisiensi sub-pipeline ALU saat idle" else "OFF: Nol latency bangun ALU shader (Esports)",
                checked = silicon.sptpPcEnabled,
                onCheckedChange = onSetSptpPc,
                accentColor = AccentCyan
            )

            // ── 2. SKIP REGISTER SAVE / RESTORE ──
            SiliconToggleRow(
                title = "Skip Save / Restore",
                description = if (silicon.skipSaveRestore) "ON: Pangkas 2-4ms frame spike saat transisi idle cepat" else "OFF: Prosedur standar register save/restore",
                checked = silicon.skipSaveRestore,
                onCheckedChange = onSetSkipSaveRestore,
                accentColor = AccentPurple
            )

            // ── 3. PREEMPTION LEVEL ──
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Preemption Level", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(
                        text = when (silicon.preemptLevel) {
                            0 -> "Level 0 (Off)"
                            1 -> "Level 1 (Cmd Buffer)"
                            2 -> "Level 2 (Drawcall)"
                            else -> "Level ${silicon.preemptLevel}"
                        },
                        fontSize = 11.sp,
                        color = AccentCyan,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = "Granularitas interupsi rendering grafis kernel KGSL",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        0 to "Off",
                        1 to "Cmd Buffer",
                        2 to "Drawcall"
                    ).forEach { (lvl, label) ->
                        val isSelected = silicon.preemptLevel == lvl
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) AccentCyan.copy(alpha = 0.2f) else BgElevated,
                            border = BorderStroke(1.dp, if (isSelected) AccentCyan else BorderGlass),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clickable { onSetPreemptLevel(lvl) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) AccentCyan else TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = BorderGlass)

            // ── 4. GPU WAKE NICE (CUSTOM NUMBER DIALOG) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("GPU Wake Nice Priority", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(
                        text = "Prioritas scheduling kernel Linux thread kgsl_worker (-20 s/d 19)",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BgElevated,
                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .clickable { showWakeNiceDialog = true }
                        .padding(start = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(14.dp))
                        Text(
                            text = "${silicon.wakeNice}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan
                        )
                    }
                }
            }

            // ── 5. MIN POWER LEVEL FLOOR (CUSTOM NUMBER DIALOG) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Min PwrLevel Floor", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(
                        text = "Kunci lantai power level agar tidak downclock berlebih saat beban dinamis",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BgElevated,
                    border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .clickable { showMinPwrDialog = true }
                        .padding(start = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(14.dp))
                        Text(
                            text = "Lvl ${silicon.minPwrLevel}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentOrange
                        )
                    }
                }
            }

            // ── 6. LAST LEVEL CACHE (LLC) SLICING ──
            SiliconToggleRow(
                title = "GPU System LLC Cache Slicing",
                description = if (silicon.gpuLlcSliceEnabled) "ON: Alokasikan dedicated System Cache (LLC), hemat daya DRAM" else "OFF: System Cache bersama CPU/DSP",
                checked = silicon.gpuLlcSliceEnabled,
                onCheckedChange = onSetGpuLlcSlice,
                accentColor = AccentGreen
            )
        }
    }

    // Modal Dialog: GPU Wake Nice
    if (showWakeNiceDialog) {
        GpuCustomNumberDialog(
            title = "GPU Wake Nice Priority",
            description = "Atur prioritas scheduling thread kernel Adreno KGSL. Nilai negatif (-20 s/d -1) memberikan prioritas sangat tinggi.",
            currentValue = silicon.wakeNice,
            unit = "nice",
            minVal = -20,
            maxVal = 19,
            step = 1,
            accentColor = AccentCyan,
            onConfirm = onSetWakeNice,
            onDismiss = { showWakeNiceDialog = false }
        )
    }

    // Modal Dialog: Min Power Level Floor
    if (showMinPwrDialog) {
        val maxFloor = (silicon.numPwrLevels - 1).coerceAtLeast(5)
        GpuCustomNumberDialog(
            title = "Min PwrLevel Floor",
            description = "Tentukan lantai power level minimum (0 = Clock Maksimum, $maxFloor = Hemat Daya). Nilai rendah mencegah downclock.",
            currentValue = silicon.minPwrLevel,
            unit = "lvl",
            minVal = 0,
            maxVal = maxFloor,
            step = 1,
            accentColor = AccentOrange,
            onConfirm = onSetMinPwrLevel,
            onDismiss = { showMinPwrDialog = false }
        )
    }
}

@Composable
private fun SiliconToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = description,
                fontSize = 11.sp,
                color = TextSecondary
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = accentColor,
                uncheckedThumbColor = TextTertiary,
                uncheckedTrackColor = BgElevated
            )
        )
    }
}
