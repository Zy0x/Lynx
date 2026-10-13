package com.noir.lynx.feature.battery.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.BatteryDetails
import com.noir.lynx.data.ChargingConfig
import com.noir.lynx.data.TelemetryData
import com.noir.lynx.designsystem.theme.*
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * ChargingHeroDisplayCard — Luxury Cyberpunk OLED Charging Display.
 *
 * Dedicated executive hero card for Dashboard:
 * - Ring Gauge: Displays Battery Level and Active Lynx Charging Mode (or Standby / Bypass).
 * - Right Banner: Displays Real Hardware Power Source & Protocol (Transsion, QC, USB-PD, VOOC, etc.).
 * - 2x2 Bento Grid: True real-time electrical telemetry (Watts, Temp, Volts, and Current).
 * - Zero Redundancy: Non-charging states are cleanly separated without duplicate labels.
 */
@Composable
fun ChargingHeroDisplayCard(
    battery: BatteryDetails? = null,
    telemetry: TelemetryData? = null,
    chargingConfig: ChargingConfig = ChargingConfig(),
    accentColor: Color = AccentCyan,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var osPlugged by remember { mutableStateOf(false) }
    var osCharging by remember { mutableStateOf(false) }
    var osLevel by remember { mutableIntStateOf(-1) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    osPlugged = plugged > 0
                    osCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    if (level > 0) osLevel = level
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val sticky = context.registerReceiver(receiver, filter)
        sticky?.let { intent ->
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            osPlugged = plugged > 0
            osCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
            if (level > 0) osLevel = level
        }
        onDispose {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }

    // Direct Real-Time Hardware Electrical & Protocol Sampler (1.0s - 1.2s guaranteed cadence)
    var liveHardwareCurrentMa by remember { mutableIntStateOf(0) }
    var liveHardwareCurrentUa by remember { mutableStateOf(0L) }
    var liveHardwareVoltageMv by remember { mutableIntStateOf(0) }
    var liveHardwareTempC by remember { mutableFloatStateOf(0f) }
    var liveHardwareChargeCounterUah by remember { mutableStateOf(0L) }
    var liveHardwareFullChargeUah by remember { mutableStateOf(0L) }
    var liveHardwareUsbOnline by remember { mutableIntStateOf(0) }
    var liveHardwareStatus by remember { mutableStateOf("Discharging") }
    var liveHardwareProtocol by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val currentNodes = listOf(
            File("/sys/class/power_supply/battery/current_now"),
            File("/sys/class/power_supply/bms/current_now"),
            File("/sys/class/power_supply/battery/BatteryAverageCurrent")
        )
        val voltageNodes = listOf(
            File("/sys/class/power_supply/battery/voltage_now"),
            File("/sys/class/power_supply/bms/voltage_now")
        )
        val tempNodes = listOf(
            File("/sys/class/power_supply/battery/temp"),
            File("/sys/class/power_supply/bms/temp")
        )
        val chargeCounterNodes = listOf(
            File("/sys/class/power_supply/battery/charge_counter"),
            File("/sys/class/power_supply/bms/charge_counter")
        )
        val fullChargeNodes = listOf(
            File("/sys/class/power_supply/battery/charge_full"),
            File("/sys/class/power_supply/bms/charge_full")
        )
        val onlineNodes = listOf(
            File("/sys/class/power_supply/usb/online"),
            File("/sys/class/power_supply/ac/online"),
            File("/sys/class/power_supply/charger/online")
        )
        val statusNodes = listOf(
            File("/sys/class/power_supply/battery/status"),
            File("/sys/class/power_supply/bms/status")
        )

        while (true) {
            // 1. Direct read of current (<0.05ms)
            var readCurrentUa: Long? = null
            for (node in currentNodes) {
                try {
                    if (node.exists() && node.canRead()) {
                        val raw = node.readText().trim().toLongOrNull()
                        if (raw != null && raw != 0L) {
                            readCurrentUa = if (kotlin.math.abs(raw) > 10_000L) raw else raw * 1000L
                            break
                        }
                    }
                } catch (_: Exception) {}
            }
            if (readCurrentUa == null && bm != null) {
                try {
                    val prop = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                    if (prop != Int.MIN_VALUE && prop != 0) {
                        readCurrentUa = if (kotlin.math.abs(prop) > 10_000) prop.toLong() else prop * 1000L
                    }
                } catch (_: Exception) {}
            }
            if (readCurrentUa != null && readCurrentUa != 0L) {
                liveHardwareCurrentUa = readCurrentUa
                liveHardwareCurrentMa = (readCurrentUa / 1000L).toInt()
            }

            // 2. Direct read of voltage
            for (node in voltageNodes) {
                try {
                    if (node.exists() && node.canRead()) {
                        val raw = node.readText().trim().toIntOrNull()
                        if (raw != null && raw > 0) {
                            liveHardwareVoltageMv = if (raw > 100_000) raw / 1000 else raw
                            break
                        }
                    }
                } catch (_: Exception) {}
            }

            // 3. Direct read of temp
            for (node in tempNodes) {
                try {
                    if (node.exists() && node.canRead()) {
                        val raw = node.readText().trim().toFloatOrNull()
                        if (raw != null && raw > 0f) {
                            liveHardwareTempC = if (raw > 1000f) raw / 1000f else if (raw > 100f) raw / 10f else raw
                            break
                        }
                    }
                } catch (_: Exception) {}
            }

            // Keep the fuel-gauge's native µAh resolution for SOC interpolation.
            for (node in chargeCounterNodes) {
                try {
                    if (node.exists() && node.canRead()) {
                        val raw = node.readText().trim().toLongOrNull()
                        if (raw != null && raw > 0L) {
                            val uah = if (raw > 100_000L) raw else raw * 1000L
                            if (uah in 1_000_000L..20_000_000L) {
                                liveHardwareChargeCounterUah = uah
                                break
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
            for (node in fullChargeNodes) {
                try {
                    if (node.exists() && node.canRead()) {
                        val raw = node.readText().trim().toLongOrNull()
                        if (raw != null && raw > 0L) {
                            val uah = if (raw > 100_000L) raw else raw * 1000L
                            if (uah in 1_000_000L..20_000_000L) {
                                liveHardwareFullChargeUah = uah
                                break
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // 4. Direct read of online & hardware status
            var anyOnline = 0
            for (node in onlineNodes) {
                try {
                    if (node.exists() && node.canRead()) {
                        if (node.readText().trim() == "1") {
                            anyOnline = 1
                            break
                        }
                    }
                } catch (_: Exception) {}
            }
            liveHardwareUsbOnline = anyOnline

            for (node in statusNodes) {
                try {
                    if (node.exists() && node.canRead()) {
                        val st = node.readText().trim()
                        if (st.isNotBlank()) {
                            liveHardwareStatus = st
                            break
                        }
                    }
                } catch (_: Exception) {}
            }

            // 5. Direct resolution of physical charger protocol
            if (anyOnline == 1 || liveHardwareStatus.equals("Charging", ignoreCase = true)) {
                try {
                    val mtkChgType = File("/sys/devices/platform/charger/Charger_Type").takeIf { it.exists() }?.readText()?.trim().orEmpty()
                    val peNode = File("/sys/devices/platform/charger/Pump_Express").takeIf { it.exists() }?.readText()?.trim().orEmpty()
                    val usbType = File("/sys/class/power_supply/usb/type").takeIf { it.exists() }?.readText()?.trim().orEmpty()
                    val usbRealType = File("/sys/class/power_supply/usb/real_type").takeIf { it.exists() }?.readText()?.trim().orEmpty()
                    val qcType = File("/sys/class/power_supply/usb/quick_charge_type").takeIf { it.exists() }?.readText()?.trim().orEmpty()
                    val qcomQc = File("/sys/class/qcom-battery/qc_type").takeIf { it.exists() }?.readText()?.trim().orEmpty()
                    val pdActive = File("/sys/class/power_supply/usb/pd_active").takeIf { it.exists() }?.readText()?.trim().orEmpty()
                    val typecPd = File("/sys/class/typec/pd_active").takeIf { it.exists() }?.readText()?.trim().orEmpty()
                    val vooc = File("/sys/class/power_supply/battery/vooc_charging").takeIf { it.exists() }?.readText()?.trim().orEmpty()
                    val miFast = File("/sys/class/power_supply/battery/fastcharge_mode").takeIf { it.exists() }?.readText()?.trim().orEmpty()

                    val ptyp = (usbRealType.ifBlank { usbType }).ifBlank { mtkChgType }
                    val qcSig = qcType.ifBlank { qcomQc }
                    val pdSig = pdActive.ifBlank { typecPd }

                    liveHardwareProtocol = resolveHardwareProtocolDirect(
                        rawPtyp = ptyp,
                        qcSig = qcSig,
                        pdSig = pdSig,
                        mtkChgType = mtkChgType,
                        peSig = peNode,
                        voocSig = vooc,
                        miSig = miFast
                    )
                } catch (_: Exception) {}
            } else {
                liveHardwareProtocol = ""
            }

            delay(1200L) // Guaranteed 1.2s fresh hardware tick
        }
    }

    val batt = battery ?: BatteryDetails()
    val tel = telemetry ?: TelemetryData()

    val rawCurrentMa = when {
        liveHardwareCurrentMa != 0 -> liveHardwareCurrentMa
        batt.currentMa != 0 -> batt.currentMa
        else -> tel.battCurrentMa
    }

    val isHwPlugged = (liveHardwareUsbOnline == 1) || osPlugged
    val isHwStatusCharging = liveHardwareStatus.equals("Charging", ignoreCase = true) || osCharging
    val isHwStatusDischarging = liveHardwareStatus.equals("Discharging", ignoreCase = true)

    // Definitively check plugged: If OS and sysfs report offline and status is Discharging, it is 100% unplugged
    val isPlugged = (isHwPlugged || (isHwStatusCharging && rawCurrentMa > 50)) &&
            !(isHwStatusDischarging && !isHwPlugged && rawCurrentMa <= 0)

    val isBypass = isPlugged && !isHwStatusCharging && (batt.isBypassActive || chargingConfig.bypassEnabled || batt.isSmartLimitLatched)

    // Charging is strictly active ONLY if plugged, not in bypass, and electrical flow is positive or not discharging
    val isCharging = isPlugged && !isBypass && (!isHwStatusDischarging || rawCurrentMa > 50)

    // Base Integer Battery Level
    val baseLevel = (if (osLevel > 0) osLevel else if (batt.level > 0) batt.level else tel.battLevel).coerceIn(0, 100)

    val remainingChargeUah = liveHardwareChargeCounterUah.takeIf { it > 0L }
        ?: (batt.chargeCounterMah.toLong() * 1000L)
    val fullChargeUah = liveHardwareFullChargeUah.takeIf { it > 0L }
        ?: (batt.fullChargeCapacityMah.toLong() * 1000L)
    val cellCurrentUa = liveHardwareCurrentUa.takeIf { it != 0L }
        ?: (batt.currentMa.toLong() * 1000L)
    val counterUahState = rememberUpdatedState(remainingChargeUah)
    val fullChargeUahState = rememberUpdatedState(fullChargeUah)
    val cellCurrentUaState = rememberUpdatedState(cellCurrentUa)
    var integratedChargeUah by remember { mutableStateOf(0.0) }
    var hasIntegratedCharge by remember { mutableStateOf(false) }
    var currentCalibrationGain by remember { mutableStateOf(1.0) }
    var levelEntryAnchorUah by remember { mutableStateOf(0L) }
    var levelEntryIntegratedUah by remember { mutableStateOf(0.0) }

    // Coulomb counting: ΔQ(µAh) = I(µA) × Δt(seconds) / 3600.
    // Start at the fuel-gauge counter, integrate only measured battery current,
    // and gently correct drift when the fuel gauge advances its own counter.
    LaunchedEffect(isCharging, isBypass) {
        hasIntegratedCharge = false
        integratedChargeUah = 0.0
        currentCalibrationGain = 1.0
        levelEntryIntegratedUah = 0.0
        levelEntryAnchorUah = if (remainingChargeUah > 0L) remainingChargeUah else 0L
        if (!isCharging || isBypass) return@LaunchedEffect

        var lastTickNs = System.nanoTime()
        var counterAnchorUah = 0L
        var uncalibratedChargeSinceAnchorUah = 0.0
        while (true) {
            delay(250L)
            val nowNs = System.nanoTime()
            val fullUah = fullChargeUahState.value
            val measuredUah = counterUahState.value
            if (fullUah < 1_000_000L || measuredUah <= 0L || measuredUah > fullUah * 1.05) {
                lastTickNs = nowNs
                continue
            }

            if (!hasIntegratedCharge) {
                integratedChargeUah = measuredUah.toDouble()
                counterAnchorUah = measuredUah
                if (levelEntryAnchorUah <= 0L) levelEntryAnchorUah = measuredUah
                hasIntegratedCharge = true
                lastTickNs = nowNs
                continue
            }

            val elapsedSeconds = ((nowNs - lastTickNs).coerceAtLeast(0L) / 1_000_000_000.0)
                .coerceAtMost(1.0)
            lastTickNs = nowNs
            val measuredCurrentUa = kotlin.math.abs(cellCurrentUaState.value)
            var uncalibratedDeltaUah = 0.0
            if (measuredCurrentUa in 10_000L..30_000_000L && elapsedSeconds > 0.0) {
                uncalibratedDeltaUah = measuredCurrentUa.toDouble() * elapsedSeconds / 3600.0
                uncalibratedChargeSinceAnchorUah += uncalibratedDeltaUah
                val calibratedDelta = uncalibratedDeltaUah * currentCalibrationGain
                integratedChargeUah += calibratedDelta
                levelEntryIntegratedUah += calibratedDelta
            }

            val counterDeltaUah = measuredUah - counterAnchorUah
            // X698's fuel-gauge counter is quantized and can remain unchanged for
            // many seconds. Use each meaningful counter step as a calibration
            // reference for the current sensor instead of trusting its raw scale.
            if (kotlin.math.abs(counterDeltaUah) >= (fullUah * 0.0025).toLong() &&
                uncalibratedChargeSinceAnchorUah >= fullUah * 0.0025
            ) {
                val sampledGain = (kotlin.math.abs(counterDeltaUah).toDouble() /
                    uncalibratedChargeSinceAnchorUah).coerceIn(0.20, 1.50)
                currentCalibrationGain = sampledGain
                integratedChargeUah = measuredUah.toDouble()
                counterAnchorUah = measuredUah
                uncalibratedChargeSinceAnchorUah = 0.0
            }
            integratedChargeUah = integratedChargeUah.coerceIn(0.0, fullUah.toDouble())
        }
    }

    var lastRecordedBaseLevel by remember { mutableIntStateOf(baseLevel) }
    var levelEntryTimeNs by remember { mutableLongStateOf(System.nanoTime()) }

    LaunchedEffect(baseLevel) {
        if (baseLevel != lastRecordedBaseLevel) {
            lastRecordedBaseLevel = baseLevel
            levelEntryTimeNs = System.nanoTime()
            levelEntryAnchorUah = if (remainingChargeUah > 0L) remainingChargeUah else 0L
            levelEntryIntegratedUah = 0.0
        }
    }

    val effectiveCurrentMa = when {
        isBypass || !isCharging -> 0f
        rawCurrentMa > 50 -> rawCurrentMa.toFloat()
        batt.adapterCurrentMa > 50 -> batt.adapterCurrentMa.toFloat()
        batt.chargerWatt > 1f && batt.voltageMv > 3000 -> (batt.chargerWatt * 1_000_000f) / batt.voltageMv.toFloat()
        else -> 0f
    }

    // High-Precision Decimal SOC Calculation (Guaranteed continuous & non-null while charging!)
    val (integerStr, decimalStr, displayedSoc) = remember(
        isCharging, isBypass, baseLevel, levelEntryAnchorUah, levelEntryIntegratedUah,
        remainingChargeUah, fullChargeUah, effectiveCurrentMa, rawCurrentMa, liveHardwareCurrentMa
    ) {
        if (!isCharging || isBypass) {
            Triple(baseLevel.toString(), "", baseLevel.toFloat())
        } else {
            val onePercentUah = if (fullChargeUah in 1_000_000L..20_000_000L) fullChargeUah / 100.0 else 50_000.0
            var fraction = 0f

            // 1. Hardware Fuel-Gauge Coulomb counter delta since entering this percent
            if (levelEntryAnchorUah > 0L && remainingChargeUah >= levelEntryAnchorUah) {
                val deltaUah = (remainingChargeUah - levelEntryAnchorUah).toDouble()
                val coulombFraction = (deltaUah / onePercentUah).toFloat()
                if (coulombFraction in 0f..0.95f) {
                    fraction = coulombFraction
                }
            }

            // 2. Real-time Integrated Current Flow (Coulombs = Amperes × seconds)
            if (fraction == 0f && levelEntryIntegratedUah > 0.0) {
                val integratedFraction = (levelEntryIntegratedUah / onePercentUah).toFloat()
                if (integratedFraction in 0f..0.95f) {
                    fraction = integratedFraction
                }
            }

            // 3. Absolute Fuel-Gauge ratio if closely aligned
            if (fraction == 0f && remainingChargeUah > 0L && fullChargeUah > 0L) {
                val directSoc = (remainingChargeUah.toDouble() / fullChargeUah.toDouble() * 100.0).toFloat()
                val diff = directSoc - baseLevel.toFloat()
                if (diff in 0f..0.95f) {
                    fraction = diff
                }
            }

            // 4. Continuous electrical flow estimation fallback (ensures decimal NEVER drops!)
            if (fraction == 0f) {
                val nowNs = System.nanoTime()
                val elapsedSec = ((nowNs - levelEntryTimeNs).coerceAtLeast(0L) / 1_000_000_000.0).toFloat()
                val activeFlowMa = if (effectiveCurrentMa > 50f) effectiveCurrentMa
                    else if (rawCurrentMa > 50) rawCurrentMa.toFloat()
                    else 2000f
                val nominalCapacityMah = if (fullChargeUah in 1_000_000L..10_000_000L) (fullChargeUah / 1000L).toFloat() else 5000f
                val secPerPercent = (nominalCapacityMah * 36f) / activeFlowMa.coerceAtLeast(500f)
                fraction = (elapsedSec / secPerPercent).coerceIn(0f, 0.95f)
            }

            val tenths = (fraction * 10f).toInt().coerceIn(0, 9)
            val soc = (baseLevel.toFloat() + (tenths / 10f)).coerceIn(0f, 100f)
            Triple(baseLevel.toString(), ".$tenths", soc)
        }
    }
    val animatedSoc by animateFloatAsState(
        targetValue = displayedSoc,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "battery_soc_ring"
    )

    // Hardware Electrical Telemetry Calculations
    val liveVolts = (if (liveHardwareVoltageMv > 0) liveHardwareVoltageMv else if (batt.voltageMv > 0) batt.voltageMv else tel.battVoltMv) / 1000f
    val liveWatts = if (isBypass) 0.0f else if (tel.battWatt > 0.05f) tel.battWatt else {
        ((liveVolts * (if (isCharging) effectiveCurrentMa else kotlin.math.abs(rawCurrentMa.toFloat()))) / 1000f)
    }
    val tempFloat = when {
        liveHardwareTempC > 0f -> liveHardwareTempC
        tel.temp.toFloatOrNull() != null -> tel.temp.toFloat()
        batt.realPhysicalTempC > 0f -> batt.realPhysicalTempC
        else -> 28f
    }
    val tempColor = when {
        tempFloat >= 43f -> AccentRed
        tempFloat >= 38f -> AccentOrange
        else -> AccentCyan
    }

    // Resolved Hardware Protocol Name for Banner
    val detectedProtocolName = remember(isPlugged, liveHardwareProtocol, batt.fastChargeProtocol) {
        when {
            !isPlugged -> "Baterai Internal (Li-Ion)"
            liveHardwareProtocol.isNotBlank() -> liveHardwareProtocol
            batt.fastChargeProtocol.isNotBlank() && !batt.fastChargeProtocol.contains("Battery", true) -> batt.fastChargeProtocol
            else -> "Adaptor Daya Eksternal"
        }
    }

    // Active Lynx Charging Mode Resolution (Single Source of Truth)
    val activeChargingMode = remember(chargingConfig.chargingMode, chargingConfig.extremeChargingEnabled, chargingConfig.limitCurrentMa) {
        when {
            chargingConfig.chargingMode.isNotBlank() -> chargingConfig.chargingMode.lowercase()
            chargingConfig.extremeChargingEnabled -> "hyper"
            chargingConfig.limitCurrentMa in 500..2500 -> "cool"
            chargingConfig.limitCurrentMa > 2500 && chargingConfig.limitCurrentMa != 6000 -> "custom"
            else -> "standard"
        }
    }

    // Banner & Theme Configuration
    val modeInfo = remember(isPlugged, isBypass, isCharging, detectedProtocolName, liveWatts, activeChargingMode) {
        when {
            !isPlugged -> ChargingDisplayMode(
                title = "BATERAI INTERNAL",
                description = "Menyuplai motherboard perangkat secara mandiri",
                badgeColor = TextSecondary,
                badgeBg = BgSurfaceLowest,
                icon = Icons.Default.BatteryStd
            )
            isBypass -> ChargingDisplayMode(
                title = "⚡ BYPASS CHARGING AKTIF",
                description = "Daya dialihkan langsung ke motherboard • 0 mA ke sel baterai",
                badgeColor = AccentCyan,
                badgeBg = AccentCyanDim,
                icon = Icons.Default.Shield
            )
            else -> {
                val badgeCol = when (activeChargingMode) {
                    "hyper" -> Color(0xFF00FF9D)
                    "cool" -> AccentCyan
                    "custom" -> AccentPurple
                    else -> AccentGreen
                }
                val badgeB = when (activeChargingMode) {
                    "hyper" -> Color(0x1F00FF9D)
                    "cool" -> AccentCyanDim
                    "custom" -> AccentPurple.copy(alpha = 0.15f)
                    else -> AccentGreenDim
                }
                val desc = if (liveWatts > 0.5f) "Pengisian cepat aktif • ${String.format(Locale.US, "%.1f", liveWatts)} W" else "Sumber daya eksternal terhubung"
                ChargingDisplayMode(
                    title = detectedProtocolName,
                    description = desc,
                    badgeColor = badgeCol,
                    badgeBg = badgeB,
                    icon = Icons.Default.Bolt
                )
            }
        }
    }

    // Animated battery-level ring with a subtle charging pulse.
    val infiniteTransition = rememberInfiniteTransition(label = "charging_glow_ring")
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = if (isCharging) 1.0f else 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_pulse"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(22.dp),
        color = BgCard,
        border = BorderStroke(1.dp, if (isPlugged) modeInfo.badgeColor.copy(alpha = 0.35f) else BorderGlass)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: Card Title & Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isPlugged) Icons.Default.Bolt else Icons.Default.BatteryFull,
                        contentDescription = null,
                        tint = modeInfo.badgeColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isPlugged) "TURBO CHARGE DISPLAY" else "STATUS DAYA & BATERAI",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 0.8.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = modeInfo.badgeBg,
                    border = BorderStroke(0.8.dp, modeInfo.badgeColor.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = if (isBypass) "BYPASS" else if (isCharging) "CHARGING" else if (isPlugged) "TERHUBUNG" else "BATERAI",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Black,
                        color = modeInfo.badgeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Horizontal Split: Ring Gauge on Left, Protocol Banner & Metrics 2x2 on Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Central Ring Gauge: Battery Percentage & Active Lynx Charging Mode
                Box(
                    modifier = Modifier.size(114.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Background Track Arc
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = Color(0x18FFFFFF),
                            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // Dynamic Energy Sweep Arc
                    Canvas(modifier = Modifier.fillMaxSize().rotate(-90f)) {
                        val strokeW = 6.dp.toPx()
                        val sweepBrush = if (isCharging) {
                            Brush.sweepGradient(
                                listOf(
                                    modeInfo.badgeColor.copy(alpha = 0.05f),
                                    modeInfo.badgeColor.copy(alpha = 0.40f),
                                    modeInfo.badgeColor.copy(alpha = ringAlpha)
                                )
                            )
                        } else if (isBypass) {
                            Brush.sweepGradient(
                                listOf(
                                    AccentCyan.copy(alpha = 0.6f),
                                    AccentGreen.copy(alpha = 0.9f)
                                )
                            )
                        } else {
                            Brush.sweepGradient(
                                listOf(
                                    AccentCyan.copy(alpha = 0.3f),
                                    AccentCyan.copy(alpha = 0.7f)
                                )
                            )
                        }

                        drawArc(
                            brush = sweepBrush,
                            startAngle = 0f,
                            sweepAngle = (animatedSoc * 3.6f).coerceIn(0f, 360f),
                            useCenter = false,
                            style = Stroke(width = strokeW, cap = StrokeCap.Round)
                        )
                    }

                    // Inner Glow Background
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .clip(CircleShape)
                            .background(BgElevated.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Main Percentage Row
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = integerStr,
                                    fontSize = if (isCharging) 28.sp else 32.sp,
                                    fontWeight = FontWeight.Black,
                                    color = TextPrimary,
                                    fontFamily = FontFamily.SansSerif,
                                    letterSpacing = (-1.0).sp
                                )
                                Row(
                                    modifier = Modifier.padding(bottom = if (isCharging) 4.dp else 5.dp, start = 1.dp),
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    if (decimalStr.isNotEmpty()) {
                                        Text(
                                            text = decimalStr,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = modeInfo.badgeColor,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Text(
                                        text = "%",
                                        fontSize = if (isCharging) 11.sp else 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCharging) modeInfo.badgeColor.copy(alpha = 0.85f) else TextSecondary.copy(alpha = 0.85f),
                                        fontFamily = FontFamily.SansSerif,
                                        modifier = Modifier.padding(start = if (isCharging) 1.dp else 2.dp)
                                    )
                                }
                            }

                            if (decimalStr.isNotEmpty()) {
                                Text(
                                    text = "ESTIMASI",
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextTertiary,
                                    letterSpacing = 0.8.sp
                                )
                            }

                            // Charging Mode State under percentage (exclusively showing active mode)
                            val ringModeText = when {
                                isBypass -> "⚡ BYPASS"
                                !isCharging -> "STANDBY"
                                activeChargingMode == "hyper" -> "⚡ HYPER MODE"
                                activeChargingMode == "cool" -> "⚡ COOL MODE"
                                activeChargingMode == "custom" -> "⚡ CUSTOM"
                                else -> "⚡ STANDARD"
                            }
                            Text(
                                text = ringModeText,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isBypass) AccentCyan else if (isCharging) modeInfo.badgeColor else TextTertiary,
                                letterSpacing = 0.6.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))

                // Right Column: Hardware Protocol Banner + 2x2 Bento Metrics Grid
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Protocol / Power Source Banner
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgElevated,
                        border = BorderStroke(0.8.dp, modeInfo.badgeColor.copy(alpha = 0.30f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(modeInfo.badgeBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = modeInfo.icon,
                                    contentDescription = null,
                                    tint = modeInfo.badgeColor,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = modeInfo.title,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1
                                )
                                Text(
                                    text = modeInfo.description,
                                    fontSize = 8.5.sp,
                                    color = TextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // 2x2 Hardware Telemetry Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MetricMiniTile(
                            label = if (isCharging) "Daya Masuk" else "Konsumsi",
                            value = "${String.format(Locale.US, "%.1f", liveWatts)} W",
                            color = if (isCharging) modeInfo.badgeColor else TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        MetricMiniTile(
                            label = "Suhu Sel",
                            value = "${String.format(Locale.US, "%.1f", tempFloat)} °C",
                            color = tempColor,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MetricMiniTile(
                            label = "Tegangan",
                            value = "${String.format(Locale.US, "%.2f", liveVolts)} V",
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        MetricMiniTile(
                            label = "Arus Riil",
                            value = when {
                                isBypass -> "0 mA (Bypass)"
                                isCharging -> "+${effectiveCurrentMa.roundToInt()} mA"
                                rawCurrentMa > 0 -> "+${rawCurrentMa} mA"
                                else -> "${rawCurrentMa} mA"
                            },
                            color = if (isBypass) AccentCyan else if (isCharging) AccentGreen else TextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricMiniTile(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = BgSurfaceLowest,
        border = BorderStroke(0.6.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = TextTertiary,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }
    }
}

private fun resolveHardwareProtocolDirect(
    rawPtyp: String,
    qcSig: String,
    pdSig: String,
    mtkChgType: String,
    peSig: String,
    voocSig: String,
    miSig: String
): String {
    val ptypUpper = rawPtyp.uppercase(Locale.US)
    val qcUpper = qcSig.uppercase(Locale.US)

    return when {
        // Transsion Fast Charge (Infinix / Tecno 33W/45W/68W PE+ Direct Pump)
        mtkChgType == "9" || ptypUpper.contains("TRAN") || ptypUpper.contains("RFC") -> {
            "Transsion Flash Charge (PE+ Pump)"
        }
        // Qualcomm Quick Charge (HVDCP, HVDCP_3, HVDCP_3P5, QC 2.0/3.0/4.0/5.0)
        ptypUpper.contains("HVDCP_3P5") || qcUpper.contains("4") || qcUpper.contains("5") -> {
            "Qualcomm Quick Charge 4.0/5.0"
        }
        ptypUpper.contains("HVDCP_3") || qcUpper.contains("3") || qcUpper.contains("QC3") -> {
            "Qualcomm Quick Charge 3.0"
        }
        ptypUpper.contains("HVDCP") || qcUpper.contains("2") || qcUpper.contains("QC2") || qcUpper.contains("QC") -> {
            "Qualcomm Quick Charge"
        }
        // USB Power Delivery & PPS
        mtkChgType == "7" || pdSig == "1" || pdSig == "2" || ptypUpper.contains("PD") -> {
            if (pdSig == "2" || ptypUpper.contains("PPS")) "USB Power Delivery (PD / PPS)"
            else "USB Power Delivery (USB-PD)"
        }
        // SuperVOOC / Dart Charge (OPPO / Realme / OnePlus)
        voocSig == "1" || voocSig == "2" || ptypUpper.contains("VOOC") || ptypUpper.contains("DART") -> {
            "SuperVOOC / Dart Charge"
        }
        // Xiaomi Turbo / HyperCharge
        miSig == "1" || miSig == "2" || ptypUpper.contains("TURBO") || ptypUpper.contains("HYPER") -> {
            "Xiaomi Turbo / HyperCharge"
        }
        // MediaTek Pump Express
        mtkChgType == "8" || peSig.isNotBlank() && peSig != "0" || ptypUpper.contains("PUMP") || ptypUpper.contains("PE") -> {
            "MediaTek Pump Express (PE+)"
        }
        // PC / Laptop Port
        ptypUpper.contains("SDP") || ptypUpper.contains("CDP") || ptypUpper.contains("PC") -> {
            "USB Komputer/Laptop (SDP/CDP)"
        }
        // Standard Adapter
        else -> "Adaptor USB Standar (DCP 5V/2A)"
    }
}

private data class ChargingDisplayMode(
    val title: String,
    val description: String,
    val badgeColor: Color,
    val badgeBg: Color,
    val icon: ImageVector
)
