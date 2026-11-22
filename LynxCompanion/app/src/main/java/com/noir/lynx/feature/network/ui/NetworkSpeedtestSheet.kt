package com.noir.lynx.feature.network.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.card.LynxCapabilityBadge
import com.noir.lynx.designsystem.card.LynxSectionDivider
import com.noir.lynx.designsystem.card.LynxSectionHeader
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.feature.network.model.SPEEDTEST_SERVER_PRESETS
import com.noir.lynx.feature.network.model.SpeedtestPhase
import com.noir.lynx.feature.network.model.SpeedtestSnapshot
import com.noir.lynx.feature.network.model.SpeedtestUiState
import com.noir.lynx.ui.*
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

internal fun launchExternalOoklaSpeedtest(context: Context): Boolean {
    val pm = context.packageManager
    val launchIntent = pm.getLaunchIntentForPackage("org.zwanoo.android.speedtest")
    if (launchIntent != null) {
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(launchIntent) }.isSuccess
    }
    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.speedtest.net/")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching { context.startActivity(webIntent) }.isSuccess
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NetworkSpeedtestBottomSheet(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    onDismiss: () -> Unit
) {
    val st = uiState.speedtestState
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * 0.90f).dp
    val selectedPreset = remember(st.selectedServerId) {
        SPEEDTEST_SERVER_PRESETS.firstOrNull { it.id == st.selectedServerId }
            ?: SPEEDTEST_SERVER_PRESETS.first()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BgCard,
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp),
                shape = RoundedCornerShape(2.dp),
                color = BorderSubtle
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Speedtest & Bufferbloat",
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        if (st.serverEdgeLabel.isNotBlank()) {
                            LynxCapabilityBadge(st.serverEdgeLabel, AccentCyan)
                        }
                    }
                    val metaLine = buildString {
                        if (st.activeIfaceLabel.isNotBlank()) append(st.activeIfaceLabel)
                        if (st.tcpAlgoSnapshot.isNotBlank()) {
                            if (isNotEmpty()) append(" • ")
                            append("TCP ${st.tcpAlgoSnapshot}")
                        }
                        if (st.qdiscSnapshot.isNotBlank()) {
                            if (isNotEmpty()) append(" • ")
                            append(st.qdiscSnapshot)
                        }
                    }.ifBlank { "Multi-Stream Throughput + Loaded Latency" }
                    Text(
                        text = metaLine,
                        fontSize = 10.5.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup", tint = TextSecondary)
                }
            }

            HorizontalDivider(color = BorderGlass, thickness = 0.6.dp, modifier = Modifier.padding(top = 6.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Server / Mode selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SPEEDTEST_SERVER_PRESETS.forEach { preset ->
                        val selected = st.selectedServerId == preset.id
                        Surface(
                            onClick = { viewModel.selectSpeedtestServer(preset.id) },
                            enabled = !st.isRunning,
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) AccentCyan.copy(alpha = 0.16f) else BgSurfaceLowest,
                            border = BorderStroke(
                                if (selected) 1.2.dp else 0.7.dp,
                                if (selected) AccentCyan else BorderSubtle
                            ),
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = preset.name,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) AccentCyan else TextSecondary,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Text(
                    text = selectedPreset.description,
                    fontSize = 10.5.sp,
                    color = TextSecondary,
                    lineHeight = 14.sp
                )

                if (st.selectedServerId == "custom") {
                    OutlinedTextField(
                        value = st.customBaseUrl,
                        onValueChange = { viewModel.setSpeedtestCustomUrl(it) },
                        enabled = !st.isRunning,
                        singleLine = true,
                        label = { Text("URL Server HTTPS (LibreSpeed / File Uji)", fontSize = 11.sp) },
                        placeholder = { Text("https://librespeed.contoh.id", fontSize = 11.sp, color = TextTertiary) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }

                // Dual Speed Readout (Download & Upload)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(
                        0.8.dp,
                        when (st.phase) {
                            SpeedtestPhase.DOWNLOAD -> AccentCyan.copy(alpha = 0.45f)
                            SpeedtestPhase.UPLOAD -> AccentGreen.copy(alpha = 0.45f)
                            SpeedtestPhase.ERROR -> AccentRed.copy(alpha = 0.45f)
                            else -> BorderSubtle
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SpeedReadoutColumn(
                                label = "DOWNLOAD",
                                speedMbps = st.downloadMbps,
                                sub = if (st.downloadPeakMbps > 0f) {
                                    "Puncak ${"%.1f".format(Locale.US, st.downloadPeakMbps)} Mbps"
                                } else {
                                    "Multi-stream HTTP/TLS"
                                },
                                accentColor = AccentCyan,
                                active = st.phase == SpeedtestPhase.DOWNLOAD,
                                modifier = Modifier.weight(1f)
                            )
                            SpeedReadoutColumn(
                                label = "UPLOAD",
                                speedMbps = st.uploadMbps,
                                sub = if (st.uploadPeakMbps > 0f) {
                                    "Puncak ${"%.1f".format(Locale.US, st.uploadPeakMbps)} Mbps"
                                } else {
                                    "Adaptive Stream POST"
                                },
                                accentColor = AccentGreen,
                                active = st.phase == SpeedtestPhase.UPLOAD,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (st.speedSamples.size >= 2) {
                            val waveColor = if (st.phase == SpeedtestPhase.UPLOAD) AccentGreen else AccentCyan
                            SpeedWaveformStrip(
                                samples = st.speedSamples,
                                lineColor = waveColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                            )
                        }

                        if (st.isRunning || st.progress > 0f) {
                            LinearProgressIndicator(
                                progress = { st.progress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp),
                                color = when (st.phase) {
                                    SpeedtestPhase.UPLOAD -> AccentGreen
                                    SpeedtestPhase.ERROR -> AccentRed
                                    else -> AccentCyan
                                },
                                trackColor = BgElevated
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = st.statusText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (st.phase == SpeedtestPhase.ERROR) AccentRed else TextSecondary,
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (st.totalTransferredMb > 0f) {
                                Text(
                                    text = "${"%.1f".format(Locale.US, st.totalTransferredMb)} MB",
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        if (!st.errorMessage.isNullOrBlank()) {
                            Text(
                                text = st.errorMessage,
                                fontSize = 10.5.sp,
                                color = AccentRed,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                // Latency & Bufferbloat 3-Tile Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LatencyDetailTile(
                        label = "IDLE PING",
                        value = if (st.idleLatencyMs > 0f) "${"%.1f".format(Locale.US, st.idleLatencyMs)} ms" else "—",
                        sub = if (st.idleJitterMs > 0f) "Jitter ${"%.1f".format(Locale.US, st.idleJitterMs)} ms" else "Sebelum beban",
                        accent = AccentBlue,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    LatencyDetailTile(
                        label = "SAAT UNDUH",
                        value = if (st.downloadLoadedLatencyMs > 0f) "${"%.0f".format(Locale.US, st.downloadLoadedLatencyMs)} ms" else "—",
                        sub = formatLoadedDelta(st.idleLatencyMs, st.downloadLoadedLatencyMs),
                        accent = AccentCyan,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    LatencyDetailTile(
                        label = "SAAT UNGGAH",
                        value = if (st.uploadLoadedLatencyMs > 0f) "${"%.0f".format(Locale.US, st.uploadLoadedLatencyMs)} ms" else "—",
                        sub = formatLoadedDelta(st.idleLatencyMs, st.uploadLoadedLatencyMs),
                        accent = AccentPurple,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }

                // Bufferbloat Grade Banner
                if (st.bufferbloatGrade.isNotBlank()) {
                    val gradeColor = when (st.bufferbloatGrade) {
                        "A+", "A" -> AccentGreen
                        "B" -> AccentCyan
                        "C" -> AccentOrange
                        else -> AccentRed
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = gradeColor.copy(alpha = 0.11f),
                        border = BorderStroke(0.8.dp, gradeColor.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LynxCapabilityBadge("BUFFERBLOAT ${st.bufferbloatGrade}", gradeColor)
                            Text(
                                text = st.bufferbloatSummary,
                                fontSize = 11.sp,
                                color = TextPrimary,
                                lineHeight = 14.5.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Baseline Before vs After Comparison
                if (st.baselineResult != null || (st.hasResult && !st.isRunning)) {
                    LynxSectionDivider()
                    SpeedtestBaselineSection(
                        st = st,
                        baseline = st.baselineResult,
                        onSaveBaseline = { viewModel.saveSpeedtestBaseline() },
                        onClearBaseline = { viewModel.clearSpeedtestBaseline() }
                    )
                }

                // Actions Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (st.isRunning) {
                                viewModel.cancelNetworkSpeedtest()
                            } else {
                                viewModel.startNetworkSpeedtest()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (st.isRunning) AccentRed else AccentCyan,
                            contentColor = if (st.isRunning) TextPrimary else BgDeepOled
                        ),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Icon(
                            imageVector = if (st.isRunning) Icons.Default.Stop else Icons.Default.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = when {
                                st.isRunning -> "Batalkan Uji"
                                st.hasResult -> "Uji Ulang"
                                else -> "Mulai Speedtest"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    OutlinedButton(
                        onClick = { launchExternalOoklaSpeedtest(context) },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.8.dp, BorderSubtle),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "Buka Ookla",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedReadoutColumn(
    label: String,
    speedMbps: Float,
    sub: String,
    accentColor: Color,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = if (active) accentColor.copy(alpha = 0.08f) else BgElevated,
        border = BorderStroke(0.6.dp, if (active) accentColor.copy(alpha = 0.4f) else BorderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (active) accentColor else TextSecondary,
                maxLines = 1
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (speedMbps > 0f) "%.1f".format(Locale.US, speedMbps) else "—",
                    fontSize = 22.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (speedMbps > 0f) accentColor else TextTertiary,
                    maxLines = 1
                )
                Text(
                    text = "Mbps",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
            Text(
                text = sub,
                fontSize = 9.5.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SpeedWaveformStrip(
    samples: List<Float>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (samples.size < 2) return@Canvas
        val maxVal = max(1f, samples.maxOrNull() ?: 1f)
        val w = size.width
        val h = size.height
        val pad = 3.dp.toPx()
        val usableH = (h - pad * 2f).coerceAtLeast(1f)
        val stepX = w / (samples.size - 1).coerceAtLeast(1)

        val path = Path()
        samples.forEachIndexed { idx, v ->
            val x = idx * stepX
            val norm = (v / maxVal).coerceIn(0f, 1f)
            val y = pad + (1f - norm) * usableH
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        // subtle grid baseline
        drawLine(
            color = lineColor.copy(alpha = 0.22f),
            start = Offset(0f, h - pad),
            end = Offset(w, h - pad),
            strokeWidth = 0.8.dp.toPx()
        )
    }
}

@Composable
private fun LatencyDetailTile(
    label: String,
    value: String,
    sub: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = BgSurfaceLowest,
        border = BorderStroke(0.7.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextSecondary, maxLines = 1)
            Text(
                text = value,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (value == "—") TextTertiary else accent,
                maxLines = 1
            )
            Text(sub, fontSize = 9.5.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SpeedtestBaselineSection(
    st: SpeedtestUiState,
    baseline: SpeedtestSnapshot?,
    onSaveBaseline: () -> Unit,
    onClearBaseline: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LynxSectionHeader(
            title = "Pembanding Tuning (Sebelum vs Sesudah)",
            subtitle = if (baseline == null) {
                "Simpan hasil saat ini sebagai patokan sebelum mengubah TCP/Qdisc"
            } else {
                "Patokan: ${baseline.tcpAlgo.ifBlank { "TCP" }} / ${baseline.qdisc.ifBlank { "qdisc" }} (${baseline.serverLabel})"
            },
            badgeText = if (baseline != null) "BASELINE AKTIF" else null,
            accentColor = AccentPurple
        )

        if (baseline != null && st.hasResult) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgSurfaceLowest,
                border = BorderStroke(0.7.dp, AccentPurple.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ComparisonDeltaChip(
                            label = "DL Δ",
                            delta = st.downloadMbps - baseline.downloadMbps,
                            unit = "Mbps",
                            higherIsBetter = true,
                            modifier = Modifier.weight(1f)
                        )
                        ComparisonDeltaChip(
                            label = "UL Δ",
                            delta = st.uploadMbps - baseline.uploadMbps,
                            unit = "Mbps",
                            higherIsBetter = true,
                            modifier = Modifier.weight(1f)
                        )
                        ComparisonDeltaChip(
                            label = "Ping Δ",
                            delta = st.idleLatencyMs - baseline.idleLatencyMs,
                            unit = "ms",
                            higherIsBetter = false,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onSaveBaseline) {
                            Text("Perbarui Patokan", fontSize = 11.sp, color = AccentCyan, fontWeight = FontWeight.SemiBold)
                        }
                        TextButton(onClick = onClearBaseline) {
                            Text("Hapus Patokan", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                }
            }
        } else if (baseline == null && st.hasResult && !st.isRunning) {
            OutlinedButton(
                onClick = onSaveBaseline,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(0.7.dp, AccentPurple.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
            ) {
                Icon(Icons.Default.BookmarkAdd, contentDescription = null, tint = AccentPurple, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Simpan Hasil Ini Sebagai Baseline Tuning",
                    color = AccentPurple,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ComparisonDeltaChip(
    label: String,
    delta: Float,
    unit: String,
    higherIsBetter: Boolean,
    modifier: Modifier = Modifier
) {
    val positive = delta > 0.05f
    val good = if (higherIsBetter) positive else delta < -0.05f
    val neutral = kotlin.math.abs(delta) <= 0.05f
    val color = when {
        neutral -> TextSecondary
        good -> AccentGreen
        else -> AccentOrange
    }
    val prefix = if (delta > 0f) "+" else ""
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(0.6.dp, color.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 9.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
            Text(
                text = "$prefix${"%.1f".format(Locale.US, delta)} $unit",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1
            )
        }
    }
}

private fun formatLoadedDelta(idleMs: Float, loadedMs: Float): String {
    if (idleMs <= 0f || loadedMs <= 0f) return "Selama beban"
    val d = max(0f, loadedMs - idleMs).roundToInt()
    return "+$d ms beban"
}
