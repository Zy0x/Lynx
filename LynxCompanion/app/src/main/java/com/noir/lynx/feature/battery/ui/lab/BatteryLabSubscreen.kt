package com.noir.lynx.feature.battery.ui.lab

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.feature.battery.lab.BatteryLabSessionManager
import com.noir.lynx.feature.battery.lab.BatteryLabSessionState
import com.noir.lynx.ui.LynxViewModel
import java.util.Locale

@Composable
fun BatteryLabSubscreen(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    onNavigateToTuningBattery: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sessionState by BatteryLabSessionManager.sessionState.collectAsState()
    val details = uiState.batteryDetails
    val health = uiState.batteryHealthStats

    // Update telemetry whenever uiState delivers fresh battery details
    LaunchedEffect(details?.currentMa, details?.voltageMv, details?.level, details?.tempC) {
        if (sessionState.isRunning) {
            BatteryLabSessionManager.updateTelemetry(details, health)
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
            onStop = { BatteryLabSessionManager.stopSession(details, health) },
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

        // ── 5. 1-Click Export Report Card ───────────────────────────────────
        ChargingExportReportCard(
            sessionState = sessionState,
            onExport = {
                BatteryLabSessionManager.exportReportToClipboard(context, health, details)
            }
        )

        // ── 6. Non-Redundant Navigation Link to Tuning ─────────────────────
        NonRedundantTuningLinkCard(
            onNavigate = onNavigateToTuningBattery
        )

        Spacer(Modifier.height(115.dp).navigationBarsPadding())
    }
}

// ============================================================================
// 1. Live Charging Session Card
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
    val curVolt = details?.voltageMv ?: 0
    val curMa = details?.currentMa ?: 0
    val liveWatt = if ((details?.chargerWatt ?: 0f) > 0f) details!!.chargerWatt else ((curVolt * curMa) / 1_000_000f)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = BgCard,
        border = BorderStroke(0.8.dp, if (isRunning) AccentGreen.copy(alpha = 0.5f) else BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Title + Live Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AccentGreen.copy(alpha = 0.15f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Bolt,
                                contentDescription = null,
                                tint = AccentGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "Sesi Pengujian Charging",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (isRunning) "Perekaman aktif di background..." else "Siap untuk menguji sesi pengisian daya",
                            color = if (isRunning) AccentGreen else TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isRunning) AccentGreen.copy(alpha = 0.2f) else BgElevated,
                    border = BorderStroke(0.8.dp, if (isRunning) AccentGreen else BorderSubtle)
                ) {
                    Text(
                        if (isRunning) "RECORDING" else "IDLE",
                        color = if (isRunning) AccentGreen else TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Main Stopwatch Banner
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF070B0E),
                border = BorderStroke(1.dp, if (isRunning) AccentGreen.copy(alpha = 0.35f) else BorderGlass),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "DURASI PENGUJIAN AKTIF",
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
                            text = "Rentang: ${sessionState.startLevel}% → ${sessionState.currentLevel}% (+$delta%)",
                            color = AccentCyan,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 4-Tile Live Metrics Bento Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Daya (Watt)
                MetricMiniTile(
                    title = "Daya Saat Ini",
                    value = "${String.format(Locale.US, "%.1f", liveWatt)} W",
                    subtitle = "Peak: ${String.format(Locale.US, "%.1f", sessionState.peakWatt)}W",
                    accent = AccentGreen,
                    modifier = Modifier.weight(1f)
                )
                // Arus (mA)
                MetricMiniTile(
                    title = "Arus Pengisian",
                    value = "$curMa mA",
                    subtitle = "Peak: ${sessionState.peakCurrentMa}mA",
                    accent = AccentCyan,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Tegangan Sel (Volt)
                MetricMiniTile(
                    title = "Tegangan Sel",
                    value = "${String.format(Locale.US, "%.2f", curVolt / 1000f)} V",
                    subtitle = "$curVolt mV",
                    accent = AccentOrange,
                    modifier = Modifier.weight(1f)
                )
                // Total Terserap (mAh)
                MetricMiniTile(
                    title = "Energi Terserap",
                    value = "${sessionState.absorbedMah} mAh",
                    subtitle = "Suhu: ${String.format(Locale.US, "%.1f", details?.tempC ?: 0f)}°C",
                    accent = AccentPurple,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(14.dp))

            // Action Buttons Row (min 48dp touch target)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!isRunning) {
                    Button(
                        onClick = onStart,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Mulai Sesi Uji", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    if (sessionState.elapsedSeconds > 0) {
                        OutlinedButton(
                            onClick = onReset,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, BorderGlass),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = BgElevated),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Refresh, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                } else {
                    Button(
                        onClick = onStop,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Stop, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Hentikan & Catat Sesi", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Summary of completed session if available
            sessionState.lastCompletedSessionSummary?.let { summary ->
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
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
// 2. True Battery Capacity & Label Audit Card
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

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = BgCard,
        border = BorderStroke(0.8.dp, if (isScamOrDegraded) AccentOrange.copy(alpha = 0.4f) else BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isScamOrDegraded) AccentOrange.copy(alpha = 0.15f) else AccentGreen.copy(alpha = 0.15f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (isScamOrDegraded) Icons.Default.Warning else Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = if (isScamOrDegraded) AccentOrange else AccentGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "Diagnostik Kapasitas Fisik Baterai",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Audit Fuel Gauge Kernel vs Klaim Kemasan Label",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isScamOrDegraded) AccentOrange.copy(alpha = 0.18f) else AccentGreen.copy(alpha = 0.18f),
                    border = BorderStroke(0.8.dp, if (isScamOrDegraded) AccentOrange else AccentGreen)
                ) {
                    Text(
                        if (isScamOrDegraded) "3RD-PARTY" else "GENUINE",
                        color = if (isScamOrDegraded) AccentOrange else AccentGreen,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Capacity Comparison Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Tertera / Label
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, BorderGlass),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text("Klaim Label / Desain OEM", color = TextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(3.dp))
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
                    Column(Modifier.padding(10.dp)) {
                        Text("Kapasitas Fisik Aktual", color = TextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(3.dp))
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

            Spacer(Modifier.height(12.dp))

            // Capacity Integrity Bar
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Rasio Integritas Sel Nyata", color = TextSecondary, fontSize = 11.sp)
                    Text("$healthPct%", color = if (isScamOrDegraded) AccentOrange else AccentGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (healthPct / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = if (isScamOrDegraded) AccentOrange else AccentGreen,
                    trackColor = BgElevated
                )
            }

            Spacer(Modifier.height(12.dp))

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
// 3. Dual-IC Handover & Physical Curve Card
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
        border = BorderStroke(0.8.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AccentCyan.copy(alpha = 0.15f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Hub,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        if (isTranssionHw) "Tahapan Fisik & Handover Dual-IC" else "Tahapan Fisik & Regulasi Pengisian",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (isTranssionHw) "Alur perpindahan hardware Direct Charge Pump ke Switching Buck"
                        else "Alur tahapan regulasi Constant Current (CC) ke Constant Voltage (CV)",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Active Stage Highlight Box
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AccentCyan.copy(alpha = 0.08f),
                border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("TAHAPAN AKTIF SAAT INI:", color = AccentCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        sessionState.activeStageName,
                        color = TextPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        sessionState.activeStageDetail,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Stage Step Indicators
            val stages = if (isTranssionHw) {
                listOf(
                    Triple("1. Direct Pump 2:1", "0% - 75% @ High-Current Direct", curMa > 3000),
                    Triple("2. Switching Buck", "75% - 85% @ Regulated Step", curLevel in 75..84 || curMa in 1500..3000),
                    Triple("3. CV Safe Tapering", "85% - 100% @ Tapered Saturation", curLevel >= 85 || (curMa < 1500 && curLevel >= 70)),
                    Triple("4. AutoCut Cutoff", "100% Full Saturation", curLevel >= 100)
                )
            } else {
                listOf(
                    Triple("1. Rapid CC Phase", "Fase arus konstan tinggi via PMIC", curMa > 2500),
                    Triple("2. Regulated CC Step", "Modulasi arus adaptif baterai", curLevel in 60..84 || curMa in 1200..2500),
                    Triple("3. CV Safe Tapering", "Saturasi voltase konstan anoda", curLevel >= 85 || (curMa < 1200 && curLevel >= 60)),
                    Triple("4. AutoCut Cutoff", "100% Full Saturation", curLevel >= 100)
                )
            }

            stages.forEach { (title, desc, isCurrent) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isCurrent) AccentCyan else BorderGlass)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            title,
                            color = if (isCurrent) AccentCyan else TextPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                        )
                        Text(desc, color = TextTertiary, fontSize = 10.sp)
                    }
                    if (isCurrent) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = AccentCyan.copy(alpha = 0.2f)
                        ) {
                            Text("AKTIF", color = AccentCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

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
        border = BorderStroke(0.8.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
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

            Spacer(Modifier.height(10.dp))

            sessionState.milestones.forEach { m ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, BorderGlass),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = BgCard,
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
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    "Ekspor Laporan Pengujian",
                    color = TextPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Salin ringkasan telemetri charging & audit kapasitas ke clipboard (Markdown)",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            Button(
                onClick = onExport,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                modifier = Modifier.heightIn(min = 44.dp)
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
            Row(modifier = Modifier.weight(1f).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
// Helper Mini Tile
// ============================================================================
@Composable
private fun MetricMiniTile(
    title: String,
    value: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BgElevated,
        border = BorderStroke(0.8.dp, BorderGlass),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            Text(title, color = TextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(value, color = accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = TextSecondary, fontSize = 9.5.sp)
        }
    }
}
