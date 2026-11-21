package com.noir.lynx.feature.battery.ui
import com.noir.lynx.ui.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.theme.AccentBlue
import com.noir.lynx.designsystem.theme.AccentCyan
import com.noir.lynx.designsystem.theme.AccentGreen
import com.noir.lynx.ui.LynxViewModel

fun batteryCurrentLevel(uiState: LynxUiState): Int =
    uiState.batteryInfoStats.currentLevel.takeIf { it > 0 } ?: (uiState.batteryDetails?.level ?: 0)

@Composable
fun BatteryInformationContent(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BatterySectionHeader(
            title = "Siklus Waktu & Laju Konsumsi",
            subtitle = "Durasi layar aktif (SOT), proporsi deep sleep & estimasi sisa waktu",
            accentColor = AccentGreen,
            badge = "RUNTIME"
        )
        BatteryRuntimeLiveCard(viewModel)

        BatterySectionHeader(
            title = "Riwayat Penurunan & Aplikasi",
            subtitle = "Grafik kurva konsumsi per-1% & daftar aplikasi pemakai daya",
            accentColor = AccentCyan,
            badge = "TELEMETRI"
        )
        BatteryHistoryLiveCard(viewModel)

        BatterySectionHeader(
            title = "Spesifikasi Fisik & Fuel Gauge",
            subtitle = "Kapasitas desain vs aktual, siklus pengisian & kalibrasi BMS",
            accentColor = AccentBlue,
            badge = "HARDWARE"
        )
        BatteryHardwareLiveCard(uiState, viewModel)
    }
}

// Stable card keys let the list skip off-screen draw trees and retain its anchor.
fun LazyListScope.batteryInformationItems(uiState: LynxUiState, viewModel: LynxViewModel) {
    item(key = "battery_section_runtime") {
        BatterySectionHeader(
            title = "Siklus Waktu & Laju Konsumsi",
            subtitle = "Durasi layar aktif (SOT), proporsi deep sleep & estimasi sisa waktu",
            accentColor = AccentGreen,
            badge = "RUNTIME"
        )
    }
    item(key = "battery_runtime") { BatteryRuntimeLiveCard(viewModel) }

    item(key = "battery_section_history") {
        BatterySectionHeader(
            title = "Riwayat Penurunan & Aplikasi",
            subtitle = "Grafik kurva konsumsi per-1% & daftar aplikasi pemakai daya",
            accentColor = AccentCyan,
            badge = "TELEMETRI"
        )
    }
    item(key = "battery_history") { BatteryHistoryLiveCard(viewModel) }

    item(key = "battery_section_hardware") {
        BatterySectionHeader(
            title = "Spesifikasi Fisik & Fuel Gauge",
            subtitle = "Kapasitas desain vs aktual, siklus pengisian & kalibrasi BMS",
            accentColor = AccentBlue,
            badge = "HARDWARE"
        )
    }
    item(key = "battery_hardware") { BatteryHardwareLiveCard(uiState, viewModel) }
}

@Composable
private fun BatteryRuntimeLiveCard(viewModel: LynxViewModel) {
    val liveState by viewModel.batteryInformationUiState.collectAsState()
    val currentLevel = liveState.stats.currentLevel.takeIf { it > 0 } ?: (liveState.details?.level ?: 0)
    BatteryRuntimeCard(liveState.stats, currentLevel)
}

@Composable
private fun BatteryHistoryLiveCard(viewModel: LynxViewModel) {
    val liveState by viewModel.batteryInformationUiState.collectAsState()
    val currentLevel = liveState.stats.currentLevel.takeIf { it > 0 } ?: (liveState.details?.level ?: 0)
    RealDischargeStepHistoryCard(liveState.stats, currentLevel)
}

@Composable
private fun BatteryHardwareLiveCard(uiState: LynxUiState, viewModel: LynxViewModel) {
    val liveState by viewModel.batteryInformationUiState.collectAsState()
    val currentLevel = liveState.stats.currentLevel.takeIf { it > 0 } ?: (liveState.details?.level ?: 0)
    BatteryHardwareCard(
        stats = liveState.stats,
        currentLevel = currentLevel,
        isCalibrating = uiState.isCalibratingBattery,
        onCalibrate = { viewModel.calibrateBatteryStats() },
        onOpenDetail = { viewModel.openBatteryDetailSheet() }
    )
}
