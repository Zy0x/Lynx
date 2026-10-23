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
import com.noir.lynx.designsystem.controls.LynxSwitch

/**
 * Modular controls for Qualcomm Adreno & MediaTek Deep Silicon features:
 * Preemption Granularity, PwrLevel Floor & Ceiling, Power Collapse, LLC, and Wake Nice.
 */

@Composable
fun PreemptionLevelSelector(
    preemptLevel: Int,
    isSupported: Boolean = true,
    onSetPreemptLevel: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Hardware Preemption Granularity",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = "Interupsi rendering grafis kernel Linux KGSL",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = AccentCyan.copy(alpha = 0.15f),
                border = BorderStroke(0.6.dp, AccentCyan.copy(alpha = 0.4f))
            ) {
                Text(
                    text = when (preemptLevel) {
                        0 -> "Off"
                        1 -> "Cmd Buffer"
                        2 -> "Drawcall"
                        else -> "Lvl $preemptLevel"
                    },
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentCyan,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                0 to "0 • Off",
                1 to "1 • Cmd Buffer",
                2 to "2 • Drawcall"
            ).forEach { (lvl, label) ->
                val isSelected = preemptLevel == lvl
                Surface(
                    onClick = { if (isSupported) onSetPreemptLevel(lvl) },
                    enabled = isSupported,
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) AccentCyan.copy(alpha = 0.22f) else BgElevated,
                    border = BorderStroke(1.dp, if (isSelected) AccentCyan else BorderGlass),
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = label,
                            fontSize = 10.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) AccentCyan else TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PwrLevelFloorCeilingRow(
    minPwrLevel: Int,
    maxPwrLevel: Int,
    numPwrLevels: Int = 0,
    onOpenMinPwrDialog: () -> Unit,
    onOpenMaxPwrDialog: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Braket Power Level (Lantai & Plafon)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = "Lantai (Min) mencegah underclock, Plafon (Max) membatasi puncak clock",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
            if (numPwrLevels > 0) {
                Text(
                    text = "Total $numPwrLevels Level",
                    fontSize = 9.sp,
                    color = TextTertiary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Max PwrLevel (Ceiling / Plafon)
            Surface(
                onClick = onOpenMaxPwrDialog,
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.5f)),
                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 52.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Plafon (Max)", fontSize = 9.5.sp, color = TextSecondary)
                        Text("Level $maxPwrLevel", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                    }
                    Icon(Icons.Default.Tune, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(16.dp))
                }
            }

            // Min PwrLevel (Floor / Lantai)
            Surface(
                onClick = onOpenMinPwrDialog,
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f)),
                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 52.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Lantai (Min)", fontSize = 9.5.sp, color = TextSecondary)
                        Text("Level $minPwrLevel", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
                    }
                    Icon(Icons.Default.Tune, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun SiliconSleepAndRegistersSection(
    silicon: GpuSiliconInfo,
    onSetSptpPc: (Boolean) -> Unit,
    onSetSkipSaveRestore: (Boolean) -> Unit,
    onSetWakeNice: (Int) -> Unit,
    onSetGpuLlcSlice: (Boolean) -> Unit,
    onOpenWakeNiceDialog: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // SPTP Power Collapse
        LynxSwitch(
            label = "SPTP Power Collapse (Sub-Pipeline ALU)",
            subLabel = if (silicon.sptpPcEnabled) "ON: Efisiensi sub-pipeline ALU saat idle" else "OFF: Nol latency bangun ALU shader (Esports)",
            checked = silicon.sptpPcEnabled,
            onCheckedChange = onSetSptpPc
        )

        // Skip Save / Restore
        LynxSwitch(
            label = "Skip Register Save / Restore",
            subLabel = if (silicon.skipSaveRestore) "ON: Pangkas 2-4ms frame spike saat transisi idle cepat" else "OFF: Prosedur standar register save/restore",
            checked = silicon.skipSaveRestore,
            onCheckedChange = onSetSkipSaveRestore
        )

        // GPU LLC Cache Slicing
        LynxSwitch(
            label = "GPU System LLC Cache Slicing",
            subLabel = if (silicon.gpuLlcSliceEnabled) "ON: Alokasikan dedicated System Cache (LLC), hemat daya DRAM" else "OFF: System Cache bersama CPU/DSP",
            checked = silicon.gpuLlcSliceEnabled,
            onCheckedChange = onSetGpuLlcSlice
        )

        // Wake Nice Priority
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Prioritas kgsl_worker (Wake Nice)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(
                    text = "Prioritas scheduling thread kernel GPU (-20 tertinggi s/d 19)",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
            Surface(
                onClick = onOpenWakeNiceDialog,
                shape = RoundedCornerShape(8.dp),
                color = BgElevated,
                border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.6f)),
                modifier = Modifier.padding(start = 8.dp)
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
    }
}

@Composable
fun SiliconImpactAccordion() {
    var showImpactDetails by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = BgElevated,
        border = BorderStroke(1.dp, if (showImpactDetails) AccentCyan.copy(alpha = 0.5f) else BorderGlass),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showImpactDetails = !showImpactDetails }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🛡️", fontSize = 13.sp)
                    Text(
                        text = "Dampak & Analisis Risiko Silikon",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Text(
                    text = if (showImpactDetails) "Tutup ▲" else "Lihat Detail ▼",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentCyan
                )
            }

            if (showImpactDetails) {
                Spacer(modifier = Modifier.height(10.dp))
                ImpactDetailItem(
                    title = "Preemption Granularity (Level 2 Drawcall)",
                    pro = "Penyusupan drawcall instan tanpa menunggu command buffer selesai, frame time lebih rata.",
                    con = "Menambah sedikit interrupt overhead pada GPU kelas entry jika game sangat ringan."
                )
                Spacer(modifier = Modifier.height(8.dp))
                ImpactDetailItem(
                    title = "SPTP PC & Skip Save/Restore",
                    pro = "Menghilangkan jeda bangun GPU (wake latency), rendering frame pertama langsung instan.",
                    con = "Konsumsi daya idle sedikit meningkat saat layar menyala tanpa interaksi grafis."
                )
                Spacer(modifier = Modifier.height(8.dp))
                ImpactDetailItem(
                    title = "PwrLevel Floor (Lantai Clock)",
                    pro = "Mencegah GPU drop ke frekuensi terendah saat scene game dinamis.",
                    con = "Suhu perangkat dapat meningkat jika digunakan tanpa pendingin eksternal pada iklim panas."
                )
            }
        }
    }
}

@Composable
private fun ImpactDetailItem(
    title: String,
    pro: String,
    con: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgSurfaceLowest, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text("• Keuntungan: $pro", fontSize = 10.sp, color = AccentGreen)
        Text("• Perhatian: $con", fontSize = 10.sp, color = AccentOrange)
    }
}

@Composable
fun SiliconCustomDialogs(
    silicon: GpuSiliconInfo?,
    showWakeNiceDialog: Boolean,
    showMinPwrDialog: Boolean,
    showMaxPwrDialog: Boolean,
    onSetWakeNice: (Int) -> Unit,
    onSetMinPwrLevel: (Int) -> Unit,
    onSetAdrenoPwrLevel: (Int) -> Unit,
    onDismissWakeNice: () -> Unit,
    onDismissMinPwr: () -> Unit,
    onDismissMaxPwr: () -> Unit
) {
    if (showWakeNiceDialog && silicon != null) {
        GpuCustomNumberDialog(
            title = "GPU Wake Nice Priority",
            description = "Prioritas scheduling thread kernel kgsl_worker (-20 tertinggi s/d 19 terendah).",
            currentValue = silicon.wakeNice.coerceIn(-20, 19),
            unit = "",
            minVal = -20,
            maxVal = 19,
            step = 1,
            accentColor = AccentCyan,
            onConfirm = { newVal ->
                onSetWakeNice(newVal)
                onDismissWakeNice()
            },
            onDismiss = onDismissWakeNice
        )
    }

    if (showMinPwrDialog && silicon != null) {
        val maxLevel = if (silicon.numPwrLevels > 0) silicon.numPwrLevels - 1 else 10
        GpuCustomNumberDialog(
            title = "Min PwrLevel (Lantai Clock)",
            description = "Batas bawah power level agar GPU tidak downclock berlebih saat beban dinamis (0 = Puncak Clock, $maxLevel = Idle Rendah).",
            currentValue = silicon.minPwrLevel.coerceIn(0, maxLevel),
            unit = "Lvl",
            minVal = 0,
            maxVal = maxLevel,
            step = 1,
            accentColor = AccentCyan,
            onConfirm = { newVal ->
                onSetMinPwrLevel(newVal)
                onDismissMinPwr()
            },
            onDismiss = onDismissMinPwr
        )
    }

    if (showMaxPwrDialog && silicon != null) {
        val maxLevel = if (silicon.numPwrLevels > 0) silicon.numPwrLevels - 1 else 10
        GpuCustomNumberDialog(
            title = "Max PwrLevel (Plafon Clock)",
            description = "Batas atas power level untuk membatasi frekuensi puncak GPU (0 = Puncak Clock, $maxLevel = Idle Rendah).",
            currentValue = silicon.maxPwrLevel.coerceIn(0, maxLevel),
            unit = "Lvl",
            minVal = 0,
            maxVal = maxLevel,
            step = 1,
            accentColor = AccentOrange,
            onConfirm = { newVal ->
                onSetAdrenoPwrLevel(newVal)
                onDismissMaxPwr()
            },
            onDismiss = onDismissMaxPwr
        )
    }
}

