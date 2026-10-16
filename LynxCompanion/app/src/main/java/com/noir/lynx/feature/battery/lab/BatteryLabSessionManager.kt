package com.noir.lynx.feature.battery.lab

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.noir.lynx.data.BatteryDetails
import com.noir.lynx.data.BatteryHealthStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChargingMilestone(
    val level: Int,
    val elapsedSeconds: Long,
    val watt: Float,
    val currentMa: Int,
    val voltageMv: Int,
    val tempC: Float,
    val stage: String
)

data class BatteryLabSessionState(
    val isRunning: Boolean = false,
    val startTimestampMs: Long = 0L,
    val elapsedSeconds: Long = 0L,
    val startLevel: Int = 0,
    val currentLevel: Int = 0,
    val startVoltageMv: Int = 0,
    val currentVoltageMv: Int = 0,
    val peakCurrentMa: Int = 0,
    val peakWatt: Float = 0f,
    val peakTempC: Float = 0f,
    val initialChargeCounterMah: Int = 0,
    val currentChargeCounterMah: Int = 0,
    val absorbedMah: Int = 0,
    val activeStageName: String = "Standby",
    val activeStageDetail: String = "Hubungkan charger dan mulai pengujian lab",
    val milestones: List<ChargingMilestone> = emptyList(),
    val lastCompletedSessionSummary: String? = null
)

object BatteryLabSessionManager {

    private val _sessionState = MutableStateFlow(BatteryLabSessionState())
    val sessionState: StateFlow<BatteryLabSessionState> = _sessionState.asStateFlow()

    fun startSession(details: BatteryDetails?, health: BatteryHealthStats) {
        val now = System.currentTimeMillis()
        val curLevel = details?.level ?: 0
        val curVolt = details?.voltageMv ?: 0
        val curMa = details?.currentMa ?: 0
        val curWatt = details?.chargerWatt ?: ((curVolt * curMa) / 1_000_000f)
        val curTemp = details?.tempC ?: 0f
        val counterMah = details?.chargeCounterMah ?: 0

        _sessionState.value = BatteryLabSessionState(
            isRunning = true,
            startTimestampMs = now,
            elapsedSeconds = 0L,
            startLevel = curLevel,
            currentLevel = curLevel,
            startVoltageMv = curVolt,
            currentVoltageMv = curVolt,
            peakCurrentMa = curMa,
            peakWatt = curWatt,
            peakTempC = curTemp,
            initialChargeCounterMah = counterMah,
            currentChargeCounterMah = counterMah,
            absorbedMah = 0,
            activeStageName = resolveStageName(curLevel, curMa, details),
            activeStageDetail = resolveStageDetail(curLevel, curMa, details),
            milestones = emptyList(),
            lastCompletedSessionSummary = null
        )
    }

    fun stopSession(details: BatteryDetails?, health: BatteryHealthStats) {
        val current = _sessionState.value
        if (!current.isRunning) return

        val now = System.currentTimeMillis()
        val totalSec = if (current.startTimestampMs > 0) (now - current.startTimestampMs) / 1000L else current.elapsedSeconds
        val formattedTime = formatDuration(totalSec)
        val deltaLevel = (details?.level ?: current.currentLevel) - current.startLevel

        val summary = "Sesi Selesai: ${current.startLevel}% → ${details?.level ?: current.currentLevel}% (+$deltaLevel%) dalam $formattedTime. Daya Puncak: ${String.format(Locale.US, "%.1f", current.peakWatt)}W (${current.peakCurrentMa} mA). Total Terserap: ${current.absorbedMah} mAh."

        _sessionState.update {
            it.copy(
                isRunning = false,
                elapsedSeconds = totalSec,
                lastCompletedSessionSummary = summary
            )
        }
    }

    fun resetSession() {
        _sessionState.value = BatteryLabSessionState()
    }

    fun updateTelemetry(details: BatteryDetails?, health: BatteryHealthStats) {
        val current = _sessionState.value
        if (!current.isRunning || details == null) return

        val now = System.currentTimeMillis()
        val elapsed = if (current.startTimestampMs > 0) (now - current.startTimestampMs) / 1000L else current.elapsedSeconds + 1L

        val curLevel = details.level
        val curVolt = details.voltageMv
        val curMa = details.currentMa
        val curWatt = if (details.chargerWatt > 0f) details.chargerWatt else ((curVolt * curMa) / 1_000_000f)
        val curTemp = details.tempC
        val curCounter = details.chargeCounterMah

        val peakMa = maxOf(current.peakCurrentMa, curMa)
        val peakW = maxOf(current.peakWatt, curWatt)
        val peakT = maxOf(current.peakTempC, curTemp)

        // Calculate absorbed mAh from delta charge counter or incremental integration
        val absorbed = if (current.initialChargeCounterMah > 0 && curCounter >= current.initialChargeCounterMah) {
            curCounter - current.initialChargeCounterMah
        } else {
            // Integration fallback: avg current * elapsed hours
            val avgMa = if (current.peakCurrentMa > 0) (current.peakCurrentMa + curMa) / 2 else curMa
            ((avgMa.toDouble() * elapsed.toDouble()) / 3600.0).toInt()
        }

        val stageName = resolveStageName(curLevel, curMa, details)
        val stageDetail = resolveStageDetail(curLevel, curMa, details)

        // Check milestones
        val standardMilestones = listOf(20, 30, 40, 50, 60, 70, 75, 80, 85, 90, 95, 100)
        val existingMilestoneLevels = current.milestones.map { it.level }.toSet()
        val newMilestones = current.milestones.toMutableList()

        for (m in standardMilestones) {
            if (curLevel >= m && m > current.startLevel && !existingMilestoneLevels.contains(m)) {
                newMilestones.add(
                    ChargingMilestone(
                        level = m,
                        elapsedSeconds = elapsed,
                        watt = curWatt,
                        currentMa = curMa,
                        voltageMv = curVolt,
                        tempC = curTemp,
                        stage = stageName
                    )
                )
            }
        }

        // Auto-stop when reaching 100% full
        val shouldAutoFinish = curLevel >= 100 && curMa < 200

        _sessionState.update {
            it.copy(
                elapsedSeconds = elapsed,
                currentLevel = curLevel,
                currentVoltageMv = curVolt,
                peakCurrentMa = peakMa,
                peakWatt = peakW,
                peakTempC = peakT,
                currentChargeCounterMah = curCounter,
                absorbedMah = absorbed,
                activeStageName = stageName,
                activeStageDetail = stageDetail,
                milestones = newMilestones,
                isRunning = if (shouldAutoFinish) false else it.isRunning,
                lastCompletedSessionSummary = if (shouldAutoFinish) {
                    "Pengisian Sempurna 100%: ${current.startLevel}% → 100% dalam ${formatDuration(elapsed)}. Total Terserap: $absorbed mAh."
                } else it.lastCompletedSessionSummary
            )
        }
    }

    private fun resolveStageName(level: Int, currentMa: Int, details: BatteryDetails?): String {
        val hw = details?.hardwarePlatform.orEmpty()
        val isTranssion = hw.equals("Transsion", ignoreCase = true) || details?.activeICName?.contains("RT9759", ignoreCase = true) == true
        val isQualcomm = hw.equals("Qualcomm", ignoreCase = true) || details?.activeICName?.contains("Qualcomm", ignoreCase = true) == true

        return when {
            level >= 100 || (level >= 99 && currentMa < 200) -> "Tahap 4: AutoCut / Trickle Saturation"
            currentMa > 3000 -> {
                when {
                    isTranssion -> "Tahap 1: Dual-Pump Direct Charge (RT9759 2:1)"
                    isQualcomm -> "Tahap 1: High-Rate CC Phase (${details?.activeICName?.ifBlank { "Qualcomm PMIC" } ?: "Qualcomm PMIC"})"
                    details?.activeICName?.isNotBlank() == true -> "Tahap 1: High-Rate CC Phase (${details.activeICName})"
                    else -> "Tahap 1: Rapid Direct / Constant Current"
                }
            }
            currentMa in 1500..3000 -> {
                when {
                    isTranssion -> "Tahap 2: Switching Buck (RT9471)"
                    isQualcomm -> "Tahap 2: Regulated PMIC Buck Step"
                    else -> "Tahap 2: Regulated Constant Current Step"
                }
            }
            level >= 75 || currentMa < 1500 -> "Tahap 3: Electrochemical CV Tapering"
            else -> "Tahap Pengisian Aktif"
        }
    }

    private fun resolveStageDetail(level: Int, currentMa: Int, details: BatteryDetails?): String {
        val hw = details?.hardwarePlatform.orEmpty()
        val isTranssion = hw.equals("Transsion", ignoreCase = true) || details?.activeICName?.contains("RT9759", ignoreCase = true) == true
        val isQualcomm = hw.equals("Qualcomm", ignoreCase = true) || details?.activeICName?.contains("Qualcomm", ignoreCase = true) == true

        return when {
            level >= 100 || (level >= 99 && currentMa < 200) -> "Baterai jenuh 100%. Modul memutus arus pengisian penuh untuk menjaga anoda baterai."
            currentMa > 3000 -> {
                when {
                    isTranssion -> "Efisiensi ~97% @ high-power direct charge. Arus puncak dialirkan langsung tanpa konversi induktor untuk minim panas."
                    isQualcomm -> "Regulasi fase Constant Current (CC) arus tinggi dikontrol langsung oleh kernel Qualcomm PMIC."
                    else -> "Arus fase Constant Current (CC) dialirkan dalam batas termal hardware optimal."
                }
            }
            currentMa in 1500..3000 -> {
                when {
                    isTranssion -> "Handover otomatis ke IC sekunder untuk menjaga voltase sel di bawah batas aman termal."
                    isQualcomm -> "Regulasi modulasi arus aktif oleh Qualcomm PMIC untuk menjaga kestabilan voltase sel dan suhu kerja."
                    else -> "Regulasi modulasi arus aktif oleh PMIC untuk menjaga kestabilan voltase sel dan suhu kerja."
                }
            }
            level >= 75 || currentMa < 1500 -> "Saturasi tegangan konstan (CV). Arus diturunkan bertahap untuk mencegah lithium plating pada anoda."
            else -> "Pengisian daya berjalan dalam batas arus adaptif kernel."
        }
    }

    fun formatDuration(totalSec: Long): String {
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60
        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun exportReportToClipboard(
        context: Context,
        health: BatteryHealthStats,
        details: BatteryDetails?
    ): Boolean {
        val state = _sessionState.value
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val dateStr = dateFormat.format(Date(if (state.startTimestampMs > 0) state.startTimestampMs else System.currentTimeMillis()))

        val designMah = health.designedCapacityMah.coerceAtLeast(1000)
        val actualMah = health.actualCapacityMah.coerceAtLeast(1000)
        val healthPct = if (designMah > 0) ((actualMah.toFloat() / designMah.toFloat()) * 100f).toInt() else 100
        val deltaLevel = state.currentLevel - state.startLevel
        val formattedDuration = formatDuration(state.elapsedSeconds)

        val sb = StringBuilder()
        sb.appendLine("# ⚡ LAPORAN BENCHMARK PENGISIAN DAYA & KAPASITAS BATERAI")
        sb.appendLine("> Diuji melalui **Lynx Kernel Manager — Lab Baterai & Daya**")
        sb.appendLine("- **Waktu Pengujian**: $dateStr")
        sb.appendLine("- **Durasi Pengujian**: $formattedDuration")
        sb.appendLine("- **Rentang Baterai**: ${state.startLevel}% → ${state.currentLevel}% (+$deltaLevel%)")
        sb.appendLine()
        sb.appendLine("## 📊 1. Ringkasan Kinerja Pengisian")
        sb.appendLine("- **Daya Puncak (Peak Watt)**: ${String.format(Locale.US, "%.1f", state.peakWatt)} W")
        sb.appendLine("- **Arus Maksimal (Peak Current)**: ${state.peakCurrentMa} mA")
        sb.appendLine("- **Tegangan Sel**: ${state.currentVoltageMv} mV")
        sb.appendLine("- **Suhu Puncak Baterai**: ${String.format(Locale.US, "%.1f", state.peakTempC)} °C")
        sb.appendLine("- **Total Energi Terserap**: ${state.absorbedMah} mAh")
        sb.appendLine("- **Tahapan Terakhir**: ${state.activeStageName}")
        sb.appendLine()
        sb.appendLine("## 🔋 2. Audit Kapasitas Fisik vs Label OEM")
        sb.appendLine("- **Kapasitas Tertera (Design/Label)**: $designMah mAh")
        sb.appendLine("- **Kapasitas Fisik Nyata (Kernel Fuel Gauge)**: $actualMah mAh")
        sb.appendLine("- **Rasio Integritas Sel**: $healthPct%")
        if (healthPct < 75) {
            sb.appendLine("- **Status Baterai**: ⚠️ **TERDETEKSI DEGRADASI TINGGI / KAPASITAS DI BAWAH STANDAR**")
            sb.appendLine("  *Catatan: Kapasitas fisik terkalibrasi $actualMah mAh dari spesifikasi desain $designMah mAh ($healthPct%). Disarankan kalibrasi ulang atau evaluasi kesehatan sel baterai.*")
        } else {
            sb.appendLine("- **Status Baterai**: ✅ **KONDISI BATERAI NORMAL ($healthPct%)**")
        }
        sb.appendLine()
        sb.appendLine("## ⏱️ 3. Milestone Waktu Tahapan")
        if (state.milestones.isEmpty()) {
            sb.appendLine("- *Belum ada milestone tercatat selama sesi ini.*")
        } else {
            sb.appendLine("| Persentase | Waktu Capai | Daya (W) | Arus (mA) | Suhu (°C) | Tahap IC |")
            sb.appendLine("| :---: | :---: | :---: | :---: | :---: | :--- |")
            for (m in state.milestones) {
                sb.appendLine("| **${m.level}%** | ${formatDuration(m.elapsedSeconds)} | ${String.format(Locale.US, "%.1f", m.watt)}W | ${m.currentMa} mA | ${String.format(Locale.US, "%.1f", m.tempC)}°C | ${m.stage} |")
            }
        }
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine("*Dihasilkan secara otomatis oleh Lynx Universal Lab Engine.*")

        val reportText = sb.toString()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        return if (clipboard != null) {
            val clip = ClipData.newPlainText("Laporan Pengujian Lynx", reportText)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Laporan pengujian disalin ke clipboard!", Toast.LENGTH_SHORT).show()
            true
        } else {
            false
        }
    }
}
