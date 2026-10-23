package com.noir.lynx.feature.gpu.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.GpuInfo
import com.noir.lynx.designsystem.theme.*

/**
 * GpuTelemetryStrip — Minimalist, Clean 1-Row Telemetry Bar for GPU Subscreen.
 * Replaces heavy nested boxes with an integrated, airy telemetry strip
 * separated by subtle hairline dividers (Anti AI-Slop / Anti-Badge Pile).
 */
@Composable
fun GpuTelemetryStrip(
    gpu: GpuInfo,
    renderer: String,
    modifier: Modifier = Modifier
) {
    val displayEngine = if (renderer.isNotBlank() && renderer != "AUTO") renderer else "GL"
    val govShort = when {
        gpu.currentGovernor.contains("adreno-tz", ignoreCase = true) -> "Adreno-TZ"
        gpu.currentGovernor.contains("performance", ignoreCase = true) -> "Performance"
        gpu.currentGovernor.contains("powersave", ignoreCase = true) -> "Powersave"
        gpu.currentGovernor.contains("schedutil", ignoreCase = true) -> "Schedutil"
        gpu.currentGovernor.isNotBlank() -> gpu.currentGovernor.take(10)
        else -> "Dynamic"
    }
    val wattage = gpu.dynamicWattage
    val wattText = String.format(java.util.Locale.US, "%.2f W", wattage)
    val vramText = if (gpu.vramUsageMb > 0) "${gpu.vramUsageMb} MB" else "Dinamis"

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF0E121B),
        border = BorderStroke(0.6.dp, Color(0x14FFFFFF)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 7.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GpuMetricItem(
                label = "Engine",
                value = displayEngine,
                valueColor = TextPrimary.copy(alpha = 0.95f),
                modifier = Modifier.weight(1f)
            )
            VerticalDivider(
                color = Color(0x14FFFFFF),
                modifier = Modifier.height(20.dp).width(0.6.dp)
            )
            GpuMetricItem(
                label = "Governor",
                value = govShort,
                valueColor = AccentCyan.copy(alpha = 0.95f),
                modifier = Modifier.weight(1f)
            )
            VerticalDivider(
                color = Color(0x14FFFFFF),
                modifier = Modifier.height(20.dp).width(0.6.dp)
            )
            GpuMetricItem(
                label = "Daya",
                value = wattText,
                valueColor = if (wattage > 2.0f) AccentOrange else AccentGreen,
                modifier = Modifier.weight(1f)
            )
            VerticalDivider(
                color = Color(0x14FFFFFF),
                modifier = Modifier.height(20.dp).width(0.6.dp)
            )
            GpuMetricItem(
                label = "VRAM",
                value = vramText,
                valueColor = TextPrimary.copy(alpha = 0.95f),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Minimalist metric item with clean typography and no enclosing sub-boxes.
 */
@Composable
private fun GpuMetricItem(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label.uppercase(),
            color = TextSecondary.copy(alpha = 0.6f),
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.4.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.1).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Modal Bottom Sheet for selecting Min or Max GPU frequency.
 * Extracted into a reusable helper component to ensure strict line limits.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpuFrequencyPickerSheet(
    targetIsMin: Boolean,
    gpu: GpuInfo,
    onDismiss: () -> Unit,
    onSetFreq: (minMhz: Int?, maxMhz: Int?) -> Unit
) {
    val freqs = gpu.availFreqsMhz.sorted()
    val curFreq = if (targetIsMin) gpu.minFreqMhz else gpu.maxFreqMhz
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cardAccent = AccentOrange

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFA0D1017),
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Surface(
                color = Color(0x33FFFFFF),
                shape = CircleShape,
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 38.dp, height = 4.dp)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (targetIsMin) "Pilih Frekuensi Minimum GPU" else "Pilih Frekuensi Maksimum GPU",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Tersedia ${freqs.size} step frekuensi clock OPP",
                        fontSize = 11.5.sp,
                        color = TextSecondary
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = cardAccent.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, cardAccent.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "$curFreq MHz",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = cardAccent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(bottom = 12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                freqs.forEach { f ->
                    val isSelected = f == curFreq
                    Surface(
                        onClick = {
                            if (targetIsMin) {
                                val newMax = if (f > gpu.maxFreqMhz) f else gpu.maxFreqMhz
                                onSetFreq(f, newMax)
                            } else {
                                val newMin = if (f < gpu.minFreqMhz) f else gpu.minFreqMhz
                                onSetFreq(newMin, f)
                            }
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) cardAccent.copy(alpha = 0.22f) else Color(0xFF141722),
                        border = BorderStroke(1.dp, if (isSelected) cardAccent else BorderGlass),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 44.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$f MHz",
                                color = if (isSelected) cardAccent else TextPrimary,
                                fontSize = 13.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = cardAccent,
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
