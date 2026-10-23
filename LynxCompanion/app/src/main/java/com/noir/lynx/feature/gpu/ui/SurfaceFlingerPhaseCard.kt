package com.noir.lynx.feature.gpu.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TouchApp
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
 * SurfaceFlingerPhaseCard - Compositor phase offsets, TouchBoost precision,
 * and VSync Swap Bypass tuning.
 */
@Composable
fun SurfaceFlingerPhaseCard(
    silicon: GpuSiliconInfo,
    onSetPhaseOffset: (earlyNs: Long, earlyGlNs: Long) -> Unit,
    onSetTouchBoostDuration: (Int) -> Unit,
    onSetVsyncSwapBypass: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPhaseDialog by remember { mutableStateOf(false) }
    var showTouchBoostDialog by remember { mutableStateOf(false) }

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
                            .background(AccentBlue.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "SurfaceFlinger & Pipeline Precision",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Fase offset VSync, durasi touchboost & bypass swapchain",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AccentBlue.copy(alpha = 0.15f),
                    border = BorderStroke(0.5.dp, AccentBlue.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "PIPELINE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentBlue,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            HorizontalDivider(color = BorderGlass)

            // ── 1. EARLY PHASE OFFSET NS ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("SF Early Phase Offset", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(
                        text = "Offset waktu awal render SurfaceFlinger sebelum VSync pulse",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                val phaseMs = (silicon.sfEarlyPhaseOffsetNs / 1_000_000L).toInt()
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BgElevated,
                    border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .clickable { showPhaseDialog = true }
                        .padding(start = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(14.dp))
                        Text(
                            text = if (phaseMs == 0) "Default (0ms)" else "${phaseMs}ms",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentBlue
                        )
                    }
                }
            }

            // ── 2. TOUCHBOOST DURATION MS ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("TouchBoost Duration", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(
                        text = "Lama penguncian clock responsif saat layar disentuh (50 - 2000 ms)",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BgElevated,
                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .clickable { showTouchBoostDialog = true }
                        .padding(start = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.TouchApp, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(14.dp))
                        Text(
                            text = "${silicon.touchBoostDurationMs}ms",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan
                        )
                    }
                }
            }

            // ── 3. VSYNC SWAP BYPASS ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "VSync Swap Interval Bypass",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (silicon.isVsyncSwapBypass) "Bypass (swapinterval=0): Respon input instan tanpa menunggu VSync" else "Sync (swapinterval=1): Presentasi frame terikat VSync",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                Switch(
                    checked = silicon.isVsyncSwapBypass,
                    onCheckedChange = onSetVsyncSwapBypass,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = AccentOrange,
                        uncheckedThumbColor = TextTertiary,
                        uncheckedTrackColor = BgElevated
                    )
                )
            }

            HorizontalDivider(color = BorderGlass)

            // ── 4. DAMPAK & RISIKO ACCORDION (TRANSPARANSI TWEAK) ──
            var showImpactDetails by remember { mutableStateOf(false) }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(1.dp, if (showImpactDetails) AccentBlue.copy(alpha = 0.5f) else BorderGlass),
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
                            Text("🛡️", fontSize = 14.sp)
                            Text(
                                text = "Dampak & Analisis Risiko Pipeline",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Text(
                            text = if (showImpactDetails) "Tutup ▲" else "Lihat Detail ▼",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentBlue
                        )
                    }

                    if (showImpactDetails) {
                        Spacer(modifier = Modifier.height(10.dp))
                        ImpactRow(
                            title = "VSync Swap Bypass (swapinterval=0)",
                            pro = "Respon input instan dan zero frame latency.",
                            con = "Dapat memicu 'Playback Error' video (Twitter/ExoPlayer) & tearing WebView jika dipaksakan. Lynx Dynamic Safeguard otomatis menetralkannya saat aplikasi media aktif."
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ImpactRow(
                            title = "Early Phase Offset (ns/ms)",
                            pro = "SurfaceFlinger render frame lebih awal sebelum VSync, memangkas input-to-display latency.",
                            con = "Offset terlalu besar (>12ms) dapat memicu frame drop pada beban multi-layer kompleks."
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ImpactRow(
                            title = "TouchBoost Duration",
                            pro = "Mencegah frame drop saat jari terangkat dari layar kontrol game.",
                            con = "Durasi >1000ms menahan clock CPU/GPU lebih lama, konsumsi baterai sedikit meningkat."
                        )
                    }
                }
            }
        }
    }

    // Modal Dialog: SurfaceFlinger Early Phase Offset (in ms)
    if (showPhaseDialog) {
        val currentMs = (silicon.sfEarlyPhaseOffsetNs / 1_000_000L).toInt()
        GpuCustomNumberDialog(
            title = "SF Early Phase Offset",
            description = "Waktu jeda awal komposisi frame sebelum sinyal VSync. 1ms - 5ms memangkas input latency pada game kompetitif. (0 = Default Android).",
            currentValue = currentMs,
            unit = "ms",
            minVal = 0,
            maxVal = 20,
            step = 1,
            accentColor = AccentBlue,
            onConfirm = { ms ->
                val ns = ms.toLong() * 1_000_000L
                onSetPhaseOffset(ns, ns)
            },
            onDismiss = { showPhaseDialog = false }
        )
    }

    // Modal Dialog: TouchBoost Duration (in ms)
    if (showTouchBoostDialog) {
        GpuCustomNumberDialog(
            title = "TouchBoost Duration",
            description = "Tentukan durasi penguncian frekuensi render saat jari menyentuh panel layar. 500ms adalah standar OEM, 800-1000ms untuk gaming.",
            currentValue = silicon.touchBoostDurationMs,
            unit = "ms",
            minVal = 50,
            maxVal = 2000,
            step = 50,
            accentColor = AccentCyan,
            onConfirm = onSetTouchBoostDuration,
            onDismiss = { showTouchBoostDialog = false }
        )
    }
}

@Composable
internal fun ImpactRow(
    title: String,
    pro: String,
    con: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgSurfaceLowest, RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = title, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("✅", fontSize = 10.sp)
            Text(text = pro, fontSize = 10.5.sp, color = AccentGreen, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("⚠️", fontSize = 10.sp)
            Text(text = con, fontSize = 10.5.sp, color = AccentOrange, modifier = Modifier.weight(1f))
        }
    }
}
