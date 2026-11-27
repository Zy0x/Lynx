package com.noir.lynx.feature.battery.ui.lab

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.feature.battery.lab.BatteryLabSessionManager
import com.noir.lynx.feature.battery.lab.BatteryLabSessionState
import com.noir.lynx.feature.battery.lab.SavedBatteryLabSession
import com.noir.lynx.feature.battery.ui.ChargingSparklineWaveform
import com.noir.lynx.ui.LynxViewModel
import java.util.Locale

/**
 * BatteryLabSubscreen — Modern Battery & Power Lab Studio.
 * Features live charging stopwatch with real-time waveform sparkline,
 * true capacity fuel-gauge audit, interactive stage pipeline, and 1-click reports.
 */
@Composable
fun BatteryLabSubscreen(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    onNavigateToTuningBattery: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sessionState by BatteryLabSessionManager.sessionState.collectAsState()
    val savedSessions by BatteryLabSessionManager.savedSessions.collectAsState()
    val details = uiState.batteryDetails
    val health = uiState.batteryHealthStats

    var inspectedSession by remember { mutableStateOf<SavedBatteryLabSession?>(null) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var sessionToDelete by remember { mutableStateOf<SavedBatteryLabSession?>(null) }

    LaunchedEffect(Unit) {
        BatteryLabSessionManager.loadSessions(context)
    }

    // Update telemetry whenever uiState delivers fresh battery details
    LaunchedEffect(details?.currentMa, details?.voltageMv, details?.level, details?.tempC) {
        if (sessionState.isRunning) {
            BatteryLabSessionManager.updateTelemetry(context, details, health)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── 1. Live Charging Session Runner Card ───────────────────────────
        LiveChargingSessionCard(
            sessionState = sessionState,
            uiState = uiState,
            onStart = { BatteryLabSessionManager.startSession(details, health) },
            onStop = { BatteryLabSessionManager.stopSession(context, details, health) },
            onReset = { BatteryLabSessionManager.resetSession() }
        )

        // ── 2. True Battery Capacity & Label Audit Card ─────────────────────
        BatteryTrueCapacityAuditCard(
            uiState = uiState,
            sessionState = sessionState
        )

        // ── 3. Dual-IC Handover & Physical Curve Card ──────────────────────
        DualIcHandoverStageCard(
            sessionState = sessionState,
            uiState = uiState
        )

        // ── 4. Milestone Timeline Card ──────────────────────────────────────
        if (sessionState.milestones.isNotEmpty()) {
            ChargingMilestonesCard(sessionState = sessionState)
        }

        // ── 5. Saved Sessions History Card (Pengujian 1, 2, 3, dst) ─────────
        SavedSessionsHistoryCard(
            savedSessions = savedSessions,
            onInspect = { inspectedSession = it },
            onDelete = { sessionToDelete = it },
            onClearAll = { showClearAllConfirm = true }
        )

        // ── 6. 1-Click Export Report Card ───────────────────────────────────
        ChargingExportReportCard(
            sessionState = sessionState,
            onExport = {
                BatteryLabSessionManager.exportReportToClipboard(context, health, details)
            }
        )

        // ── 7. Non-Redundant Navigation Link to Tuning ─────────────────────
        NonRedundantTuningLinkCard(
            onNavigate = onNavigateToTuningBattery
        )

        Spacer(Modifier.height(115.dp).navigationBarsPadding())
    }

    // Dialogs: Inspected Session, Clear All Confirm, and Delete Single Confirm
    if (inspectedSession != null) {
        BatterySessionDetailDialog(
            session = inspectedSession!!,
            health = health,
            onDismiss = { inspectedSession = null },
            onExport = {
                BatteryLabSessionManager.exportSavedSessionReport(context, inspectedSession!!, health)
            }
        )
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = AccentRed)
                    Text("Bersihkan Seluruh Riwayat?", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(
                    "Semua catatan riwayat pengujian baterai (${savedSessions.size} sesi) akan dihapus secara permanen dari penyimpanan perangkat.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        BatteryLabSessionManager.clearAllSessions(context)
                        showClearAllConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                ) {
                    Text("Bersihkan", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showClearAllConfirm = false },
                    border = BorderStroke(0.8.dp, BorderSubtle)
                ) {
                    Text("Batal", color = TextSecondary)
                }
            },
            containerColor = BgCard,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (sessionToDelete != null) {
        val s = sessionToDelete!!
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = AccentRed)
                    Text("Hapus Sesi #${s.sessionNumber}?", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(
                    "Catatan pengujian sesi #${s.sessionNumber} (${s.dateFormatted}, ${s.startLevel}% → ${s.endLevel}%) akan dihapus permanen.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        BatteryLabSessionManager.deleteSession(context, s.id)
                        sessionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                ) {
                    Text("Hapus", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { sessionToDelete = null },
                    border = BorderStroke(0.8.dp, BorderSubtle)
                ) {
                    Text("Batal", color = TextSecondary)
                }
            },
            containerColor = BgCard,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

// ============================================================================
// 1. Live Charging Session Card with Waveform Sparkline
// ============================================================================
@Composable
private fun LiveChargingSessionCard(
    sessionState: BatteryLabSessionState,
    uiState: LynxUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit
) {
    val details = uiState.batteryDetails
    val isRunning = sessionState.isRunning
    val isCharging = details?.isCharging == true || (details?.currentMa ?: 0) > 0
    val curVolt = details?.voltageMv ?: 0
    val curMa = details?.currentMa ?: 0
    val liveWatt = if ((details?.chargerWatt ?: 0f) > 0f) details!!.chargerWatt else ((curVolt * curMa) / 1_000_000f)
    val haptic = LocalHapticFeedback.current

    val infiniteTransition = rememberInfiniteTransition(label = "charging_dot_pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "charging_dot_alpha"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = BgCard,
        border = BorderStroke(1.dp, if (isRunning) AccentGreen.copy(alpha = 0.6f) else BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row: Icon + Title + Liveness Dot + Status Badge
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
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isRunning) AccentGreen.copy(alpha = 0.16f) else BgElevated)
                            .border(
                                BorderStroke(0.8.dp, if (isRunning) AccentGreen.copy(alpha = 0.5f) else BorderSubtle),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isCharging) Icons.Default.Bolt else Icons.Default.BatteryChargingFull,
                            contentDescription = null,
                            tint = if (isRunning) AccentGreen else AccentCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "SESI PENGUJIAN CHARGING",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isRunning) AccentGreen else AccentCyan,
                            letterSpacing = 0.6.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isRunning) AccentGreen.copy(alpha = dotAlpha)
                                        else TextSecondary.copy(alpha = 0.4f)
                                    )
                            )
                            Text(
                                text = if (isRunning) "MEREKAM DATA CHARGING" else "SIAP MENGUJI",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }
                }

                // Mode Badge: Charging vs Discharging
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isCharging) AccentGreen.copy(alpha = 0.18f) else BgElevated,
                    border = BorderStroke(0.8.dp, if (isCharging) AccentGreen.copy(alpha = 0.5f) else BorderSubtle)
                ) {
                    Text(
                        text = if (isCharging) "CHARGING IN" else "DISCHARGING",
                        color = if (isCharging) AccentGreen else AccentOrange,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Main Stopwatch Banner
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF070B0E),
                border = BorderStroke(1.dp, if (isRunning) AccentGreen.copy(alpha = 0.4f) else BorderGlass),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "DURASI PENGUJIAN AKTIF",
                        color = TextTertiary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = BatteryLabSessionManager.formatDuration(sessionState.elapsedSeconds),
                        color = if (isRunning) AccentGreen else TextPrimary,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )

                    if (sessionState.startLevel > 0) {
                        Spacer(Modifier.height(4.dp))
                        val delta = sessionState.currentLevel - sessionState.startLevel
                        Text(
                            text = "Rentang: ${sessionState.startLevel}% → ${sessionState.currentLevel}% (${if (delta >= 0) "+$delta" else "$delta"}%)",
                            color = AccentCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Integrated Live Sparkline Waveform
            val currentSamples = details?.currentHistorySamples ?: emptyList()
            if (currentSamples.isNotEmpty()) {
                ChargingSparklineWaveform(
                    samples = currentSamples,
                    currentMa = curMa,
                    accentColor = if (isCharging) AccentGreen else AccentCyan,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 4-Tile Live Metrics Bento Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricMiniTile(
                    title = "Daya Baterai",
                    value = "${String.format(Locale.US, "%.1f", liveWatt)} W",
                    subtitle = "Peak: ${String.format(Locale.US, "%.1f", sessionState.peakWatt)}W",
                    accent = AccentGreen,
                    icon = Icons.Default.Bolt,
                    modifier = Modifier.weight(1f)
                )
                MetricMiniTile(
                    title = if (isCharging) "Arus Pengisian" else "Arus Keluar",
                    value = "${if (curMa > 0) "+$curMa" else "$curMa"} mA",
                    subtitle = "Peak: ${sessionState.peakCurrentMa}mA",
                    accent = if (isCharging) AccentCyan else AccentOrange,
                    icon = Icons.Default.Speed,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricMiniTile(
                    title = "Tegangan Sel",
                    value = "${String.format(Locale.US, "%.2f", curVolt / 1000f)} V",
                    subtitle = "$curVolt mV",
                    accent = AccentOrange,
                    icon = Icons.Default.FlashOn,
                    modifier = Modifier.weight(1f)
                )
                MetricMiniTile(
                    title = "Energi & Suhu",
                    value = "${sessionState.absorbedMah} mAh",
                    subtitle = "Suhu: ${String.format(Locale.US, "%.1f", details?.tempC ?: 0f)}°C",
                    accent = AccentPurple,
                    icon = Icons.Default.Thermostat,
                    modifier = Modifier.weight(1f)
                )
            }

            // Action Buttons Row (min 50dp touch target)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!isRunning) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onStart()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 50.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Mulai Sesi Uji Lab", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    if (sessionState.elapsedSeconds > 0) {
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onReset()
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, BorderGlass),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = BgElevated),
                            modifier = Modifier.heightIn(min = 50.dp)
                        ) {
                            Icon(Icons.Default.Refresh, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onStop()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 50.dp)
                    ) {
                        Icon(Icons.Default.Stop, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Hentikan & Catat Sesi", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Summary of completed session if available
            sessionState.lastCompletedSessionSummary?.let { summary ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = summary,
                            color = TextPrimary,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// 2. True Battery Capacity & Label Audit Card (with Cell Silhouette)
// ============================================================================
@Composable
private fun BatteryTrueCapacityAuditCard(
    uiState: LynxUiState,
    sessionState: BatteryLabSessionState
) {
    val health = uiState.batteryHealthStats
    val details = uiState.batteryDetails

    val designMah = health.designedCapacityMah.coerceAtLeast(1000)
    val actualMah = health.actualCapacityMah.coerceAtLeast(1000)
    val healthPct = if (designMah > 0) ((actualMah.toFloat() / designMah.toFloat()) * 100f).toInt() else 100
    val isScamOrDegraded = healthPct < 75

    val healthGrade = when {
        healthPct >= 95 -> "Grade A+ • Sangat Prima"
        healthPct >= 85 -> "Grade A • Sehat Normal"
        healthPct >= 75 -> "Grade B • Degradasi Ringan"
        else -> "Grade C • Disarankan Servis"
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = BgCard,
        border = BorderStroke(1.dp, if (isScamOrDegraded) AccentOrange.copy(alpha = 0.5f) else BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row
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
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isScamOrDegraded) AccentOrange.copy(alpha = 0.16f) else AccentGreen.copy(alpha = 0.16f))
                            .border(
                                BorderStroke(0.8.dp, if (isScamOrDegraded) AccentOrange.copy(alpha = 0.4f) else AccentGreen.copy(alpha = 0.4f)),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isScamOrDegraded) Icons.Default.Warning else Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = if (isScamOrDegraded) AccentOrange else AccentGreen,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "DIAGNOSTIK KAPASITAS FISIK BATERAI",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isScamOrDegraded) AccentOrange else AccentGreen,
                            letterSpacing = 0.6.sp
                        )
                        Text(
                            text = "Audit Fuel Gauge Kernel vs Klaim Label",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isScamOrDegraded) AccentOrange.copy(alpha = 0.18f) else AccentGreen.copy(alpha = 0.18f),
                    border = BorderStroke(0.8.dp, if (isScamOrDegraded) AccentOrange else AccentGreen)
                ) {
                    Text(
                        text = if (isScamOrDegraded) "3RD-PARTY" else "GENUINE",
                        color = if (isScamOrDegraded) AccentOrange else AccentGreen,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Visual Silhouette Battery Cell
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF070B0E),
                border = BorderStroke(1.dp, BorderGlass),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = healthGrade,
                            color = if (isScamOrDegraded) AccentOrange else AccentGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Kapasitas Kimia: $actualMah mAh (${healthPct}% Integritas)",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    // Battery Terminal Icon & Level
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = BgElevated,
                            border = BorderStroke(0.6.dp, BorderSubtle)
                        ) {
                            Text(
                                text = "${details?.level ?: 0}% SoC",
                                color = AccentCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Capacity Comparison Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Klaim Label
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, BorderGlass),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Klaim Label / Desain OEM", color = TextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text("$designMah mAh", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("PowerProfile spesifikasi", color = TextSecondary, fontSize = 9.5.sp)
                    }
                }

                // Aktual Fisik Kernel
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, if (isScamOrDegraded) AccentOrange.copy(alpha = 0.4f) else BorderGlass),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Kapasitas Fisik Aktual", color = TextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "$actualMah mAh",
                            color = if (isScamOrDegraded) AccentOrange else AccentGreen,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text("Fuel Gauge charge_full", color = TextSecondary, fontSize = 9.5.sp)
                    }
                }
            }

            // Capacity Integrity Bar
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Rasio Integritas Sel Nyata", color = TextSecondary, fontSize = 11.sp)
                    Text("$healthPct%", color = if (isScamOrDegraded) AccentOrange else AccentGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (healthPct / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = if (isScamOrDegraded) AccentOrange else AccentGreen,
                    trackColor = BgElevated
                )
            }

            // Forensic Explanation Box
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF070B0E),
                border = BorderStroke(0.8.dp, if (isScamOrDegraded) AccentOrange.copy(alpha = 0.25f) else BorderGlass),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isScamOrDegraded) Icons.Default.Info else Icons.Default.Check,
                            null,
                            tint = if (isScamOrDegraded) AccentOrange else AccentGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isScamOrDegraded) "ANALISIS FORENSIK KESEHATAN BATERAI" else "ANALISIS STATUS BATERAI",
                            color = if (isScamOrDegraded) AccentOrange else AccentGreen,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (isScamOrDegraded) {
                            "Kapasitas kimia sel terbaca $actualMah mAh (~$healthPct% dari spesifikasi desain $designMah mAh). Baterai mengalami penurunan kapasitas akibat siklus pemakaian atau menggunakan sel non-standar. Lynx otomatis menerapkan kurva arus adaptif agar sel tidak overload dan terhindar dari panas berlebih."
                        } else {
                            "Kapasitas kimia sel baterai konsisten dengan spesifikasi desain OEM ($actualMah / $designMah mAh, ~$healthPct%). Kalibrasi fuel gauge kernel sinkron."
                        },
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

// ============================================================================
// 3. Interactive Charging Pipeline Stepper Card
// ============================================================================
@Composable
private fun DualIcHandoverStageCard(
    sessionState: BatteryLabSessionState,
    uiState: LynxUiState
) {
    val details = uiState.batteryDetails
    val curLevel = details?.level ?: 0
    val curMa = details?.currentMa ?: 0
    val isTranssionHw = details?.hardwarePlatform.equals("Transsion", ignoreCase = true) ||
            details?.activeICName?.contains("RT9759", ignoreCase = true) == true

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = BgCard,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentCyan.copy(alpha = 0.16f))
                        .border(BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.4f)), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Hub,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        if (isTranssionHw) "TAHAPAN FISIK & HANDOVER DUAL-IC" else "PIPELINE TAHAPAN REGULASI CHARGING",
                        color = AccentCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.6.sp
                    )
                    Text(
                        if (isTranssionHw) "Direct Pump 2:1 ke Switching Buck" else "Alur Constant Current (CC) ke Constant Voltage (CV)",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Active Stage Highlight Box
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AccentCyan.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Text(
                        text = "TAHAPAN REGULASI AKTIF SAAT INI:",
                        color = AccentCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = sessionState.activeStageName,
                        color = TextPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = sessionState.activeStageDetail,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            // Pipeline Stepper List
            val stages = if (isTranssionHw) {
                listOf(
                    StageStepData("1. Direct Pump 2:1", "0% - 90% @ High-Current Direct", "Target > 3000 mA", curLevel < 90 && curMa > 3000),
                    StageStepData("2. Switching Buck", "Modulasi arus adaptif baterai", "Target 1500–3000 mA", curLevel < 90 && curMa in 1500..3000),
                    StageStepData("3. CV Safe Tapering", "90% - 100% @ Tapered Saturation", "Taper < 1500 mA", curLevel in 90..99),
                    StageStepData("4. AutoCut Cutoff", "100% Full Saturation / Cutoff", "Target 0 mA", curLevel >= 100)
                )
            } else {
                listOf(
                    StageStepData("1. Rapid CC Phase", "0% - 90% @ Puncak Arus via PMIC", "Target > 2000 mA", curLevel < 90 && curMa >= 2000),
                    StageStepData("2. Regulated CC Step", "Modulasi arus adaptif baterai", "Target 1000–2000 mA", curLevel < 90 && curMa in 1..1999),
                    StageStepData("3. CV Safe Tapering", "90% - 100% @ Tapered Saturation", "Taper < 1000 mA", curLevel in 90..99),
                    StageStepData("4. AutoCut Cutoff", "100% Full Saturation / Cutoff", "Target 0 mA", curLevel >= 100)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                stages.forEach { stage ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (stage.isCurrent) AccentCyan.copy(alpha = 0.12f) else BgElevated,
                        border = BorderStroke(
                            1.dp,
                            if (stage.isCurrent) AccentCyan.copy(alpha = 0.6f) else BorderGlass
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (stage.isCurrent) AccentCyan else BorderGlass)
                                )
                                Column {
                                    Text(
                                        text = stage.title,
                                        color = if (stage.isCurrent) AccentCyan else TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = if (stage.isCurrent) FontWeight.Bold else FontWeight.Medium
                                    )
                                    Text(
                                        text = stage.desc,
                                        color = TextSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (stage.isCurrent) AccentCyan.copy(alpha = 0.2f) else BgSurfaceLowest,
                                border = BorderStroke(0.6.dp, if (stage.isCurrent) AccentCyan.copy(alpha = 0.4f) else BorderSubtle)
                            ) {
                                Text(
                                    text = if (stage.isCurrent) "AKTIF • ${stage.tag}" else stage.tag,
                                    color = if (stage.isCurrent) AccentCyan else TextTertiary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class StageStepData(
    val title: String,
    val desc: String,
    val tag: String,
    val isCurrent: Boolean
)

// ============================================================================
// 4. Milestone Timeline Card
// ============================================================================
@Composable
private fun ChargingMilestonesCard(
    sessionState: BatteryLabSessionState
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = BgCard,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timeline, null, tint = AccentPurple, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Milestone Capaian Sesi (${sessionState.milestones.size})",
                    color = TextPrimary,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            sessionState.milestones.forEach { m ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, BorderGlass),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${m.level}%", color = AccentPurple, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    BatteryLabSessionManager.formatDuration(m.elapsedSeconds),
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                m.stage,
                                color = TextSecondary,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "${String.format(Locale.US, "%.1f", m.watt)} W",
                                color = AccentGreen,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${m.currentMa} mA  •  ${String.format(Locale.US, "%.1f", m.tempC)}°C",
                                color = TextTertiary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 5. 1-Click Export Report Card
// ============================================================================
@Composable
private fun ChargingExportReportCard(
    sessionState: BatteryLabSessionState,
    onExport: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = BgCard,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Ekspor Laporan Pengujian",
                    color = TextPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Salin ringkasan telemetri charging & audit kapasitas ke clipboard (format Markdown rapi)",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }

            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onExport()
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                modifier = Modifier.heightIn(min = 46.dp)
            ) {
                Icon(Icons.Default.ContentCopy, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Salin", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ============================================================================
// 6. Non-Redundant Navigation Link to Tuning
// ============================================================================
@Composable
private fun NonRedundantTuningLinkCard(
    onNavigate: () -> Unit
) {
    Surface(
        onClick = onNavigate,
        shape = RoundedCornerShape(16.dp),
        color = BgElevated,
        border = BorderStroke(0.8.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Tune, null, tint = AccentGreen, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "Konfigurasi Parameter Charging di Tuning Engine",
                        color = TextPrimary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Atur Bypass Charging, batas arus mA, & AutoCut di Tab Tuning ↗",
                        color = TextSecondary,
                        fontSize = 10.5.sp
                    )
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ============================================================================
// 5. Saved Sessions History Card (Pengujian 1, 2, 3, dst)
// ============================================================================
@Composable
private fun SavedSessionsHistoryCard(
    savedSessions: List<SavedBatteryLabSession>,
    onInspect: (SavedBatteryLabSession) -> Unit,
    onDelete: (SavedBatteryLabSession) -> Unit,
    onClearAll: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = BgCard,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Title + Session Count Badge + Clear All Button
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
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AccentPurple.copy(alpha = 0.15f))
                            .border(BorderStroke(0.8.dp, AccentPurple.copy(alpha = 0.4f)), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = AccentPurple,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "LOG RIWAYAT PENGUJIAN",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentPurple,
                                letterSpacing = 0.5.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = BgElevated,
                                border = BorderStroke(0.8.dp, BorderGlass)
                            ) {
                                Text(
                                    text = "${savedSessions.size} Sesi",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Arsip Pengujian 1, 2, 3, dst",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }

                if (savedSessions.isNotEmpty()) {
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onClearAll()
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = AccentRed.copy(alpha = 0.12f),
                        border = BorderStroke(0.8.dp, AccentRed.copy(alpha = 0.4f)),
                        modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.DeleteSweep, null, tint = AccentRed, modifier = Modifier.size(15.dp))
                            Text("Bersihkan", fontSize = 10.5.sp, color = AccentRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Text(
                text = "Setiap pengujian charging yang dihentikan akan otomatis diarsipkan secara lokal. Anda dapat membuka detail metrik atau menghapus sesi kapan saja.",
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 15.sp
            )

            if (savedSessions.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, BorderGlass),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.HourglassEmpty,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "Belum Ada Sesi Pengujian Tersimpan",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                        Text(
                            text = "Jalankan pengujian di atas lalu tekan 'Selesai & Kunci Data' untuk mengarsipkan data sesi pengujian pertama.",
                            fontSize = 10.5.sp,
                            color = TextTertiary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    savedSessions.forEach { session ->
                        SavedSessionItemRow(
                            session = session,
                            onInspect = { onInspect(session) },
                            onDelete = { onDelete(session) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedSessionItemRow(
    session: SavedBatteryLabSession,
    onInspect: () -> Unit,
    onDelete: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val deltaLevel = session.endLevel - session.startLevel
    val formattedDuration = BatteryLabSessionManager.formatDuration(session.durationSeconds)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = BgElevated,
        border = BorderStroke(0.8.dp, BorderGlass),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onInspect()
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Row: Sesi # badge + Date + Delete icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AccentCyan.copy(alpha = 0.16f),
                        border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "SESI #${session.sessionNumber}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = session.dateFormatted,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDelete()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Hapus Sesi",
                        tint = AccentRed.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Middle Row: Metrics chips (Delta level, Duration, Peak Watt, Absorbed)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Delta level chip
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BgCard,
                    border = BorderStroke(0.6.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("LEVEL", fontSize = 8.5.sp, color = TextTertiary, fontWeight = FontWeight.Bold)
                        Text(
                            "${session.startLevel}%→${session.endLevel}%",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (deltaLevel >= 0) AccentGreen else AccentOrange
                        )
                    }
                }

                // Duration chip
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BgCard,
                    border = BorderStroke(0.6.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("DURASI", fontSize = 8.5.sp, color = TextTertiary, fontWeight = FontWeight.Bold)
                        Text(formattedDuration, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                }

                // Peak Watt chip
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BgCard,
                    border = BorderStroke(0.6.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("PEAK", fontSize = 8.5.sp, color = TextTertiary, fontWeight = FontWeight.Bold)
                        Text("${String.format(Locale.US, "%.1f", session.peakWatt)}W", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
                    }
                }

                // Absorbed chip
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BgCard,
                    border = BorderStroke(0.6.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("TERSERAP", fontSize = 8.5.sp, color = TextTertiary, fontWeight = FontWeight.Bold)
                        Text("${session.absorbedMah}mAh", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = AccentPurple)
                    }
                }
            }

            // Bottom Row: Stage tag + Open button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(AccentGreen)
                    )
                    Text(
                        text = session.activeStageName,
                        fontSize = 10.5.sp,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Buka Detail",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentCyan
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ============================================================================
// BatterySessionDetailDialog — Inspection Dialog for Saved Session
// ============================================================================
@Composable
private fun BatterySessionDetailDialog(
    session: SavedBatteryLabSession,
    health: com.noir.lynx.data.BatteryHealthStats,
    onDismiss: () -> Unit,
    onExport: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val deltaLevel = session.endLevel - session.startLevel
    val formattedDuration = BatteryLabSessionManager.formatDuration(session.durationSeconds)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = BgCard,
            border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Dialog Header: Title + Session # + Close Button
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
                                .clip(RoundedCornerShape(10.dp))
                                .background(AccentCyan.copy(alpha = 0.16f))
                                .border(BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.5f)), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.BatteryChargingFull, null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text(
                                text = "DETAIL SESI #${session.sessionNumber}",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = session.dateFormatted,
                                fontSize = 10.5.sp,
                                color = TextTertiary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Close, "Tutup", tint = TextSecondary, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Scrollable Body Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Summary Banner
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BgElevated,
                        border = BorderStroke(0.8.dp, BorderGlass),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("RINGKASAN SESI", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
                            Text(session.summary, fontSize = 11.5.sp, color = TextPrimary, lineHeight = 16.sp)
                        }
                    }

                    // 2x3 Metric Grid
                    Text("METRIK ANALITIK PENGISIAN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextTertiary)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricMiniTile(
                            title = "Rentang Persentase",
                            value = "${session.startLevel}% → ${session.endLevel}%",
                            subtitle = if (deltaLevel >= 0) "+$deltaLevel% Baterai" else "$deltaLevel% Baterai",
                            accent = if (deltaLevel >= 0) AccentGreen else AccentOrange,
                            icon = Icons.Default.Bolt,
                            modifier = Modifier.weight(1f)
                        )
                        MetricMiniTile(
                            title = "Durasi Pengujian",
                            value = formattedDuration,
                            subtitle = "${session.durationSeconds} detik total",
                            accent = TextPrimary,
                            icon = Icons.Default.Timer,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricMiniTile(
                            title = "Daya Puncak (Watt)",
                            value = "${String.format(Locale.US, "%.1f", session.peakWatt)} W",
                            subtitle = "Arus Puncak: ${session.peakCurrentMa} mA",
                            accent = AccentCyan,
                            icon = Icons.Default.FlashOn,
                            modifier = Modifier.weight(1f)
                        )
                        MetricMiniTile(
                            title = "Energi Terserap",
                            value = "${session.absorbedMah} mAh",
                            subtitle = "Kapasitas muatan masuk",
                            accent = AccentPurple,
                            icon = Icons.Default.BatteryFull,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricMiniTile(
                            title = "Tegangan Sel Akhir",
                            value = "${session.endVoltageMv} mV",
                            subtitle = "Awal: ${session.startVoltageMv} mV",
                            accent = AccentBlue,
                            icon = Icons.Default.Power,
                            modifier = Modifier.weight(1f)
                        )
                        MetricMiniTile(
                            title = "Suhu Puncak",
                            value = "${String.format(Locale.US, "%.1f", session.peakTempC)} °C",
                            subtitle = if (session.peakTempC < 42f) "Suhu Aman Termal" else "Mendekati Throttling",
                            accent = if (session.peakTempC < 42f) AccentGreen else AccentRed,
                            icon = Icons.Default.Thermostat,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Milestone Timeline if available
                    if (session.milestones.isNotEmpty()) {
                        Text("MILESTONE WAKTU CAPAI TAHAP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextTertiary)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BgElevated,
                            border = BorderStroke(0.8.dp, BorderGlass),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                session.milestones.forEach { m ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = AccentGreen.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    "${m.level}%",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = AccentGreen,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                            Text(
                                                m.stage,
                                                fontSize = 10.sp,
                                                color = TextSecondary,
                                                maxLines = 1
                                            )
                                        }

                                        Text(
                                            "${BatteryLabSessionManager.formatDuration(m.elapsedSeconds)} • ${String.format(Locale.US, "%.1f", m.watt)}W",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = TextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Bottom Action Buttons: Copy Markdown Report + Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.8.dp, BorderSubtle),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 46.dp)
                    ) {
                        Text("Tutup", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onExport()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                        modifier = Modifier
                            .weight(1.4f)
                            .heightIn(min = 46.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Salin Laporan", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ============================================================================
// Helper Mini Tile
// ============================================================================
@Composable
private fun MetricMiniTile(
    title: String,
    value: String,
    subtitle: String,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BgElevated,
        border = BorderStroke(0.8.dp, BorderGlass),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(title, color = TextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                if (icon != null) {
                    Icon(icon, null, tint = accent.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(value, color = accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = TextSecondary, fontSize = 9.5.sp)
        }
    }
}
