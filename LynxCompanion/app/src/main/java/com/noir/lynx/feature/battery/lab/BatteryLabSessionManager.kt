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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class ChargingMilestone(
    val level: Int,
    val elapsedSeconds: Long,
    val watt: Float,
    val currentMa: Int,
    val voltageMv: Int,
    val tempC: Float,
    val stage: String
)

data class SavedBatteryLabSession(
    val id: String,
    val sessionNumber: Int,
    val timestampMs: Long,
    val dateFormatted: String,
    val durationSeconds: Long,
    val startLevel: Int,
    val endLevel: Int,
    val startVoltageMv: Int,
    val endVoltageMv: Int,
    val peakWatt: Float,
    val peakCurrentMa: Int,
    val peakTempC: Float,
    val absorbedMah: Int,
    val activeStageName: String,
    val milestones: List<ChargingMilestone> = emptyList(),
    val summary: String
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

    private val _savedSessions = MutableStateFlow<List<SavedBatteryLabSession>>(emptyList())
    val savedSessions: StateFlow<List<SavedBatteryLabSession>> = _savedSessions.asStateFlow()

    private const val SESSIONS_FILE_NAME = "battery_lab_sessions.json"

    fun loadSessions(context: Context) {
        runCatching {
            val file = File(context.filesDir, SESSIONS_FILE_NAME)
            if (!file.exists()) {
                _savedSessions.value = emptyList()
                return
            }
            val content = file.readText()
            if (content.isBlank()) {
                _savedSessions.value = emptyList()
                return
            }
            val jsonArray = JSONArray(content)
            val list = mutableListOf<SavedBatteryLabSession>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val milestonesList = mutableListOf<ChargingMilestone>()
                val milestonesArray = obj.optJSONArray("milestones")
                if (milestonesArray != null) {
                    for (j in 0 until milestonesArray.length()) {
                        val mObj = milestonesArray.getJSONObject(j)
                        milestonesList.add(
                            ChargingMilestone(
                                level = mObj.optInt("level", 0),
                                elapsedSeconds = mObj.optLong("elapsedSeconds", 0L),
                                watt = mObj.optDouble("watt", 0.0).toFloat(),
                                currentMa = mObj.optInt("currentMa", 0),
                                voltageMv = mObj.optInt("voltageMv", 0),
                                tempC = mObj.optDouble("tempC", 0.0).toFloat(),
                                stage = mObj.optString("stage", "")
                            )
                        )
                    }
                }
                list.add(
                    SavedBatteryLabSession(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        sessionNumber = obj.optInt("sessionNumber", i + 1),
                        timestampMs = obj.optLong("timestampMs", System.currentTimeMillis()),
                        dateFormatted = obj.optString("dateFormatted", "Waktu tidak diketahui"),
                        durationSeconds = obj.optLong("durationSeconds", 0L),
                        startLevel = obj.optInt("startLevel", 0),
                        endLevel = obj.optInt("endLevel", 0),
                        startVoltageMv = obj.optInt("startVoltageMv", 0),
                        endVoltageMv = obj.optInt("endVoltageMv", 0),
                        peakWatt = obj.optDouble("peakWatt", 0.0).toFloat(),
                        peakCurrentMa = obj.optInt("peakCurrentMa", 0),
                        peakTempC = obj.optDouble("peakTempC", 0.0).toFloat(),
                        absorbedMah = obj.optInt("absorbedMah", 0),
                        activeStageName = obj.optString("activeStageName", "Selesai"),
                        milestones = milestonesList,
                        summary = obj.optString("summary", "")
                    )
                )
            }
            _savedSessions.value = list
        }
    }

    private fun persistSessions(context: Context) {
        runCatching {
            val file = File(context.filesDir, SESSIONS_FILE_NAME)
            val jsonArray = JSONArray()
            for (s in _savedSessions.value) {
                val obj = JSONObject().apply {
                    put("id", s.id)
                    put("sessionNumber", s.sessionNumber)
                    put("timestampMs", s.timestampMs)
                    put("dateFormatted", s.dateFormatted)
                    put("durationSeconds", s.durationSeconds)
                    put("startLevel", s.startLevel)
                    put("endLevel", s.endLevel)
                    put("startVoltageMv", s.startVoltageMv)
                    put("endVoltageMv", s.endVoltageMv)
                    put("peakWatt", s.peakWatt.toDouble())
                    put("peakCurrentMa", s.peakCurrentMa)
                    put("peakTempC", s.peakTempC.toDouble())
                    put("absorbedMah", s.absorbedMah)
                    put("activeStageName", s.activeStageName)
                    put("summary", s.summary)

                    val mArray = JSONArray()
                    for (m in s.milestones) {
                        mArray.put(JSONObject().apply {
                            put("level", m.level)
                            put("elapsedSeconds", m.elapsedSeconds)
                            put("watt", m.watt.toDouble())
                            put("currentMa", m.currentMa)
                            put("voltageMv", m.voltageMv)
                            put("tempC", m.tempC.toDouble())
                            put("stage", m.stage)
                        })
                    }
                    put("milestones", mArray)
                }
                jsonArray.put(obj)
            }
            file.writeText(jsonArray.toString(2))
        }
    }

    fun deleteSession(context: Context, sessionId: String) {
        _savedSessions.update { list ->
            list.filterNot { it.id == sessionId }
        }
        persistSessions(context)
        Toast.makeText(context, "Sesi pengujian berhasil dihapus", Toast.LENGTH_SHORT).show()
    }

    fun clearAllSessions(context: Context) {
        _savedSessions.value = emptyList()
        runCatching { File(context.filesDir, SESSIONS_FILE_NAME).delete() }
        Toast.makeText(context, "Seluruh riwayat sesi pengujian dibersihkan", Toast.LENGTH_SHORT).show()
    }

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

    fun stopSession(context: Context? = null, details: BatteryDetails?, health: BatteryHealthStats) {
        val current = _sessionState.value
        if (!current.isRunning) return

        val now = System.currentTimeMillis()
        val totalSec = if (current.startTimestampMs > 0) (now - current.startTimestampMs) / 1000L else current.elapsedSeconds
        val formattedTime = formatDuration(totalSec)
        val endLevel = details?.level ?: current.currentLevel
        val deltaLevel = endLevel - current.startLevel

        val summary = "Sesi Selesai: ${current.startLevel}% → $endLevel% (${if (deltaLevel >= 0) "+$deltaLevel" else "$deltaLevel"}%) dalam $formattedTime. Daya Puncak: ${String.format(Locale.US, "%.1f", current.peakWatt)}W (${current.peakCurrentMa} mA). Total Terserap: ${current.absorbedMah} mAh."

        _sessionState.update {
            it.copy(
                isRunning = false,
                elapsedSeconds = totalSec,
                lastCompletedSessionSummary = summary
            )
        }

        // Archive into persistent saved sessions
        if (context != null) {
            val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
            val dateStr = dateFormat.format(Date(if (current.startTimestampMs > 0) current.startTimestampMs else now))
            val nextNumber = (_savedSessions.value.maxOfOrNull { it.sessionNumber } ?: 0) + 1

            val saved = SavedBatteryLabSession(
                id = UUID.randomUUID().toString(),
                sessionNumber = nextNumber,
                timestampMs = now,
                dateFormatted = dateStr,
                durationSeconds = totalSec,
                startLevel = current.startLevel,
                endLevel = endLevel,
                startVoltageMv = current.startVoltageMv,
                endVoltageMv = details?.voltageMv ?: current.currentVoltageMv,
                peakWatt = current.peakWatt,
                peakCurrentMa = current.peakCurrentMa,
                peakTempC = current.peakTempC,
                absorbedMah = current.absorbedMah,
                activeStageName = current.activeStageName,
                milestones = current.milestones,
                summary = summary
            )

            _savedSessions.update { listOf(saved) + it }
            persistSessions(context)
        }
    }

    fun resetSession() {
        _sessionState.value = BatteryLabSessionState()
    }

    fun updateTelemetry(context: Context? = null, details: BatteryDetails?, health: BatteryHealthStats) {
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

        val autoSummary = if (shouldAutoFinish) {
            "Pengisian Sempurna 100%: ${current.startLevel}% → 100% dalam ${formatDuration(elapsed)}. Total Terserap: $absorbed mAh."
        } else current.lastCompletedSessionSummary

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
                lastCompletedSessionSummary = autoSummary
            )
        }

        // If auto-finished and context available, archive to persistent storage
        if (shouldAutoFinish && context != null) {
            val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
            val dateStr = dateFormat.format(Date(now))
            val nextNumber = (_savedSessions.value.maxOfOrNull { it.sessionNumber } ?: 0) + 1

            val saved = SavedBatteryLabSession(
                id = UUID.randomUUID().toString(),
                sessionNumber = nextNumber,
                timestampMs = now,
                dateFormatted = dateStr,
                durationSeconds = elapsed,
                startLevel = current.startLevel,
                endLevel = curLevel,
                startVoltageMv = current.startVoltageMv,
                endVoltageMv = curVolt,
                peakWatt = peakW,
                peakCurrentMa = peakMa,
                peakTempC = peakT,
                absorbedMah = absorbed,
                activeStageName = stageName,
                milestones = newMilestones,
                summary = autoSummary ?: ""
            )

            _savedSessions.update { listOf(saved) + it }
            persistSessions(context)
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
            level >= 90 || (level >= 85 && currentMa < 1500) -> "Tahap 3: Electrochemical CV Tapering"
            else -> "Tahap 1: Rapid / Regulated CC Phase"
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
            level >= 90 || (level >= 85 && currentMa < 1500) -> "Saturasi tegangan konstan (CV). Arus diturunkan bertahap untuk mencegah lithium plating pada anoda."
            else -> "Regulasi arus fase Constant Current (CC) dikontrol langsung oleh kernel PMIC."
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
        sb.appendLine("- **Rentang Baterai**: ${state.startLevel}% → ${state.currentLevel}% (${if (deltaLevel >= 0) "+$deltaLevel" else "$deltaLevel"}%)")
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

    fun exportSavedSessionReport(
        context: Context,
        session: SavedBatteryLabSession,
        health: BatteryHealthStats
    ): Boolean {
        val designMah = health.designedCapacityMah.coerceAtLeast(1000)
        val actualMah = health.actualCapacityMah.coerceAtLeast(1000)
        val healthPct = if (designMah > 0) ((actualMah.toFloat() / designMah.toFloat()) * 100f).toInt() else 100
        val deltaLevel = session.endLevel - session.startLevel
        val formattedDuration = formatDuration(session.durationSeconds)

        val sb = StringBuilder()
        sb.appendLine("# ⚡ LAPORAN BENCHMARK PENGISIAN DAYA — SESI #${session.sessionNumber}")
        sb.appendLine("> Diuji melalui **Lynx Kernel Manager — Lab Baterai & Daya**")
        sb.appendLine("- **Waktu Pengujian**: ${session.dateFormatted}")
        sb.appendLine("- **Durasi Pengujian**: $formattedDuration")
        sb.appendLine("- **Rentang Baterai**: ${session.startLevel}% → ${session.endLevel}% (${if (deltaLevel >= 0) "+$deltaLevel" else "$deltaLevel"}%)")
        sb.appendLine()
        sb.appendLine("## 📊 1. Ringkasan Kinerja Sesi")
        sb.appendLine("- **Daya Puncak (Peak Watt)**: ${String.format(Locale.US, "%.1f", session.peakWatt)} W")
        sb.appendLine("- **Arus Maksimal (Peak Current)**: ${session.peakCurrentMa} mA")
        sb.appendLine("- **Tegangan Sel**: ${session.endVoltageMv} mV")
        sb.appendLine("- **Suhu Puncak Baterai**: ${String.format(Locale.US, "%.1f", session.peakTempC)} °C")
        sb.appendLine("- **Total Energi Terserap**: ${session.absorbedMah} mAh")
        sb.appendLine("- **Tahapan Terakhir**: ${session.activeStageName}")
        sb.appendLine()
        sb.appendLine("## 🔋 2. Audit Kapasitas Fisik vs Label OEM")
        sb.appendLine("- **Kapasitas Tertera (Design/Label)**: $designMah mAh")
        sb.appendLine("- **Kapasitas Fisik Nyata (Kernel Fuel Gauge)**: $actualMah mAh")
        sb.appendLine("- **Rasio Integritas Sel**: $healthPct%")
        sb.appendLine()
        sb.appendLine("## ⏱️ 3. Milestone Waktu Tahapan")
        if (session.milestones.isEmpty()) {
            sb.appendLine("- *Tidak ada data milestone terpisah untuk sesi ini.*")
        } else {
            sb.appendLine("| Persentase | Waktu Capai | Daya (W) | Arus (mA) | Suhu (°C) | Tahap IC |")
            sb.appendLine("| :---: | :---: | :---: | :---: | :---: | :--- |")
            for (m in session.milestones) {
                sb.appendLine("| **${m.level}%** | ${formatDuration(m.elapsedSeconds)} | ${String.format(Locale.US, "%.1f", m.watt)}W | ${m.currentMa} mA | ${String.format(Locale.US, "%.1f", m.tempC)}°C | ${m.stage} |")
            }
        }
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine("*Dihasilkan secara otomatis oleh Lynx Universal Lab Engine.*")

        val reportText = sb.toString()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        return if (clipboard != null) {
            val clip = ClipData.newPlainText("Laporan Sesi #${session.sessionNumber}", reportText)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Laporan Sesi #${session.sessionNumber} disalin ke clipboard!", Toast.LENGTH_SHORT).show()
            true
        } else {
            false
        }
    }
}
