package com.noir.lynx.feature.battery.ui

import com.noir.lynx.core.locale.localizedUiText

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.noir.lynx.data.BatteryDetails
import com.noir.lynx.data.ChargingConfig
import com.noir.lynx.data.TelemetryData
import com.noir.lynx.designsystem.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * ChargingHeroDisplayCard — Luxury Cyberpunk OLED Charging Display.
 *
 * Dedicated executive hero card for Dashboard:
 * - Ring Gauge: Displays Battery Level and Active Lynx Charging Mode (or Standby / Bypass).
 * - Right Banner: Displays Real Hardware Power Source & Protocol (Transsion, QC, USB-PD, VOOC, etc.).
 * - 2x2 Bento Grid: True real-time electrical telemetry (Watts, Temp, Volts, and Current).
 * - Zero Redundancy: Non-charging states are cleanly separated without duplicate labels.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChargingHeroDisplayCard(
    battery: BatteryDetails? = null,
    telemetry: TelemetryData? = null,
    chargingConfig: ChargingConfig = ChargingConfig(),
    accentColor: Color = AccentCyan,
    onSelectChargingMode: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var osPlugged by remember { mutableStateOf(false) }
    var osCharging by remember { mutableStateOf(false) }
    var osLevel by remember { mutableIntStateOf(-1) }
    var showChargingModeModal by remember { mutableStateOf(false) }

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
    var liveHardwareCapacity by remember { mutableIntStateOf(-1) }
    var liveHardwareChargeCounterUah by remember { mutableStateOf(0L) }
    var liveHardwareFullChargeUah by remember { mutableStateOf(0L) }
    var liveHardwareUsbOnline by remember { mutableIntStateOf(0) }
    var liveHardwareStatus by remember { mutableStateOf("Discharging") }
    var liveHardwareProtocol by remember { mutableStateOf("") }
    var liveChargingStateMode by remember { mutableStateOf("") }
    var liveBypassActive by remember { mutableStateOf(false) }

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
        val capacityNodes = listOf(
            File("/sys/class/power_supply/battery/capacity"),
            File("/sys/class/power_supply/bms/capacity")
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

            // Direct read of live hardware capacity (0..100)
            for (node in capacityNodes) {
                try {
                    if (node.exists() && node.canRead()) {
                        val cap = node.readText().trim().toIntOrNull()
                        if (cap != null && cap in 0..100) {
                            liveHardwareCapacity = cap
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
                            if (uah in 10_000L..30_000_000L) {
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
                            if (uah in 100_000L..30_000_000L) {
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
                    fun safeRead(path: String): String = runCatching {
                        val f = File(path)
                        if (f.exists() && f.canRead()) f.readText().trim() else ""
                    }.getOrDefault("")

                    val mtkChgType = safeRead("/sys/devices/platform/charger/Charger_Type")
                    val peNode = safeRead("/sys/devices/platform/charger/Pump_Express")
                    val usbType = safeRead("/sys/class/power_supply/usb/type")
                    val usbRealType = safeRead("/sys/class/power_supply/usb/real_type")
                    val qcType = safeRead("/sys/class/power_supply/usb/quick_charge_type")
                    val qcomQc = safeRead("/sys/class/qcom-battery/qc_type")
                    val pdActive = safeRead("/sys/class/power_supply/usb/pd_active")
                    val typecPd = safeRead("/sys/class/typec/pd_active")
                    val vooc = safeRead("/sys/class/power_supply/battery/vooc_charging")
                    val miFast = safeRead("/sys/class/power_supply/battery/fastcharge_mode")

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

            // 6. Direct sample of native lynxd charging state file (/dev/lynx_charging_state)
            try {
                val chgStateFile = File("/dev/lynx_charging_state")
                if (chgStateFile.exists() && chgStateFile.canRead()) {
                    val line = chgStateFile.readText().trim()
                    val rawMode = line.substringBefore(" ").substringAfter("state=").trim()
                    if (rawMode.isNotBlank()) {
                        liveChargingStateMode = when {
                            rawMode.startsWith("cool") -> "cool"
                            rawMode.startsWith("hyper") -> "hyper"
                            rawMode.startsWith("custom") -> "custom"
                            rawMode.startsWith("standard") -> "standard"
                            else -> ""
                        }
                        liveBypassActive = rawMode.startsWith("bypass")
                    }
                }
                if (!liveBypassActive) {
                    liveBypassActive = File("/dev/lynx_bypass_active").exists()
                }
            } catch (_: Exception) {}

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

    val isBypass = (isPlugged && !isHwStatusCharging && (batt.isBypassActive || chargingConfig.bypassEnabled || batt.isSmartLimitLatched)) ||
            (isPlugged && liveBypassActive)

    // Charging is strictly active ONLY if plugged, not in bypass, and electrical flow is positive or not discharging
    val isCharging = isPlugged && !isBypass && (!isHwStatusDischarging || rawCurrentMa > 50)

    // Base Integer Battery Level (Direct Hardware Ground Truth prioritized)
    val baseLevel = (if (liveHardwareCapacity in 0..100) liveHardwareCapacity else if (osLevel in 0..100) osLevel else if (batt.level > 0) batt.level else tel.battLevel).coerceIn(0, 100)

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

    // Coulomb counting: ΔQ(µAh) = I(µA) × Δt(seconds) / 3600.
    // Anchored strictly to baseLevel: fractional tenths advance from .0 to .9 as current flows.
    LaunchedEffect(isCharging, isBypass, baseLevel) {
        hasIntegratedCharge = false
        integratedChargeUah = 0.0
        currentCalibrationGain = 1.0
        if (!isCharging || isBypass) return@LaunchedEffect

        var lastTickNs = System.nanoTime()
        var counterAnchorUah = 0L
        var uncalibratedChargeSinceAnchorUah = 0.0
        while (true) {
            delay(250L)
            val nowNs = System.nanoTime()
            val fullUah = fullChargeUahState.value
            val measuredUah = counterUahState.value
            val effectiveFullUah = if (fullUah in 100_000L..30_000_000L) fullUah else 3_000_000L

            if (!hasIntegratedCharge) {
                // If fuel-gauge remainder is within current baseLevel, use it; otherwise anchor to baseLevel
                val directPercent = if (measuredUah > 0L) (measuredUah.toDouble() / effectiveFullUah.toDouble() * 100.0) else -1.0
                val validOffset = if (directPercent >= baseLevel.toDouble() && directPercent < (baseLevel + 1).toDouble()) {
                    measuredUah.toDouble()
                } else {
                    effectiveFullUah.toDouble() * baseLevel / 100.0
                }
                integratedChargeUah = validOffset
                counterAnchorUah = if (measuredUah > 0L) measuredUah else 0L
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
            }

            if (counterAnchorUah <= 0L && measuredUah in 1L..(effectiveFullUah * 1.05).toLong()) {
                counterAnchorUah = measuredUah
                uncalibratedChargeSinceAnchorUah = 0.0
            }
            val counterDeltaUah = measuredUah - counterAnchorUah
            if (counterAnchorUah > 0L && measuredUah > 0L && measuredUah <= effectiveFullUah * 1.05 &&
                counterDeltaUah > 0L && counterDeltaUah >= (effectiveFullUah * 0.0025).toLong() &&
                uncalibratedChargeSinceAnchorUah >= effectiveFullUah * 0.0025
            ) {
                val sampledGain = (counterDeltaUah.toDouble() /
                    uncalibratedChargeSinceAnchorUah).coerceIn(0.20, 1.50)
                currentCalibrationGain = sampledGain
                counterAnchorUah = measuredUah
                uncalibratedChargeSinceAnchorUah = 0.0
            }
        }
    }

    val effectiveCurrentMa = when {
        isBypass || !isCharging -> 0f
        rawCurrentMa > 50 -> rawCurrentMa.toFloat()
        batt.adapterCurrentMa > 50 -> batt.adapterCurrentMa.toFloat()
        batt.chargerWatt > 1f && batt.voltageMv > 3000 -> (batt.chargerWatt * 1_000_000f) / batt.voltageMv.toFloat()
        else -> 0f
    }

    // High Precision Tenths Decimal SOC within current baseLevel integer:
    val uahPerOnePercent = if (fullChargeUah in 100_000L..30_000_000L) (fullChargeUah.toDouble() / 100.0) else 30_000.0
    val fractionWithinLevel = if (isCharging && !isBypass && hasIntegratedCharge && uahPerOnePercent > 0.0) {
        val baseUah = uahPerOnePercent * baseLevel
        val deltaFromBase = (integratedChargeUah - baseUah).coerceAtLeast(0.0)
        (deltaFromBase / uahPerOnePercent).coerceIn(0.0, 0.95)
    } else null

    val tenths = if (fractionWithinLevel != null) {
        kotlin.math.floor(fractionWithinLevel * 10.0 + 0.0001).toInt().coerceIn(0, 9)
    } else null

    // Strictly anchored to baseLevel so integer NEVER desyncs with status bar:
    val integerStr = baseLevel.toString()
    val decimalStr = if (isCharging && !isBypass && tenths != null) ".$tenths" else ""
    val displayedSoc = if (isCharging && !isBypass && tenths != null) {
        baseLevel.toFloat() + (tenths.toFloat() / 10f)
    } else {
        baseLevel.toFloat()
    }

    val animatedSoc by animateFloatAsState(
        targetValue = displayedSoc,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "battery_soc_ring"
    )

    // Hardware Electrical Telemetry Calculations
    val liveVolts = (if (liveHardwareVoltageMv > 0) liveHardwareVoltageMv else if (batt.voltageMv > 0) batt.voltageMv else tel.battVoltMv) / 1000f
    val liveWatts = if (isBypass) 0.0f else if (tel.battWatt > 0.05f) tel.battWatt else {
        ((liveVolts * (if (isCharging) effectiveCurrentMa else kotlin.math.abs(rawCurrentMa.toFloat()))) / 1000f)
    }

    val adapterWatts = if (batt.adapterWatt > 0.5f) batt.adapterWatt else if (batt.chargerWatt > 0.5f) batt.chargerWatt else 0f
    val adapterVolts = if (batt.adapterVoltageMv in 4000..25000) batt.adapterVoltageMv / 1000f else (if (batt.chargerVoltageMv in 4000..25000) batt.chargerVoltageMv / 1000f else 0f)
    val adapterCurrentMa = if (batt.adapterCurrentMa in 100..15000) batt.adapterCurrentMa else 0
    val hasAdapterData = isCharging && adapterVolts >= 4.0f && adapterCurrentMa >= 100 && adapterWatts >= 0.5f

    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
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

    val hasTranHw = remember {
        File("/sys/devices/platform/odm/odm:tran_battery").exists() ||
        File("/sys/devices/platform/tran_battery").exists() ||
        File("/sys/bus/i2c/drivers/rt9759").exists() ||
        File("/sys/devices/platform/charger/BN_TestMode").exists()
    }

    val hasXiaomiHw = remember {
        File("/sys/class/power_supply/battery/fastcharge_mode").exists() ||
        File("/sys/class/thermal/thermal_message").exists() ||
        File("/sys/class/power_supply/battery/turbo_power").exists() ||
        File("/sys/bus/i2c/drivers/ln8000").exists() ||
        File("/sys/bus/i2c/drivers/sc8551").exists()
    }

    val hasOplusHw = remember {
        File("/sys/class/power_supply/battery/vooc_charging").exists() ||
        File("/sys/class/oplus_chg").exists()
    }

    val hasSamsungHw = remember {
        File("/sys/class/power_supply/sec-battery").exists() ||
        File("/sys/class/sec/sec-battery").exists()
    }

    val isTranssionBrand = remember(hasTranHw, hasXiaomiHw, hasOplusHw, hasSamsungHw) {
        val m = android.os.Build.MANUFACTURER.uppercase(Locale.US)
        val b = android.os.Build.BRAND.uppercase(Locale.US)
        hasTranHw || (!hasXiaomiHw && !hasOplusHw && !hasSamsungHw &&
                (m.contains("INFINIX") || m.contains("TECNO") || m.contains("TRANSSION") ||
                 b.contains("INFINIX") || b.contains("TECNO")))
    }

    val isXiaomiBrand = remember(hasTranHw, hasXiaomiHw, hasOplusHw, hasSamsungHw) {
        val m = android.os.Build.MANUFACTURER.uppercase(Locale.US)
        val b = android.os.Build.BRAND.uppercase(Locale.US)
        (hasXiaomiHw && !hasTranHw) || (!hasTranHw && !hasOplusHw && !hasSamsungHw &&
                (m.contains("XIAOMI") || b.contains("XIAOMI") || b.contains("REDMI") || b.contains("POCO")))
    }

    val isSamsungBrand = remember(hasTranHw, hasXiaomiHw, hasOplusHw, hasSamsungHw) {
        val m = android.os.Build.MANUFACTURER.uppercase(Locale.US)
        val b = android.os.Build.BRAND.uppercase(Locale.US)
        (hasSamsungHw && !hasTranHw && !hasXiaomiHw) || (!hasTranHw && !hasXiaomiHw &&
                (m.contains("SAMSUNG") || b.contains("SAMSUNG")))
    }

    val isOppoRealmeBrand = remember(hasTranHw, hasXiaomiHw, hasOplusHw, hasSamsungHw) {
        val m = android.os.Build.MANUFACTURER.uppercase(Locale.US)
        val b = android.os.Build.BRAND.uppercase(Locale.US)
        (hasOplusHw && !hasTranHw && !hasXiaomiHw) || (!hasTranHw && !hasXiaomiHw &&
                (m.contains("OPPO") || m.contains("REALME") || m.contains("ONEPLUS") ||
                 b.contains("OPPO") || b.contains("REALME") || b.contains("ONEPLUS")))
    }

    val isPropsSpoofed = remember(hasTranHw, hasXiaomiHw) {
        val m = android.os.Build.MANUFACTURER.uppercase(Locale.US)
        val b = android.os.Build.BRAND.uppercase(Locale.US)
        batt.isPropsSpoofed ||
        (hasTranHw && (m.contains("XIAOMI") || b.contains("XIAOMI") || b.contains("REDMI") || b.contains("POCO") || m.contains("GOOGLE") || b.contains("GOOGLE") || m.contains("ASUS") || b.contains("ASUS"))) ||
        (hasXiaomiHw && (m.contains("INFINIX") || b.contains("INFINIX") || m.contains("GOOGLE") || b.contains("GOOGLE") || m.contains("ASUS") || b.contains("ASUS")))
    }

    // Resolved Hardware Protocol Name for Banner
    val detectedProtocolName = remember(isPlugged, batt.fastChargeProtocol, liveHardwareProtocol, isTranssionBrand, isXiaomiBrand, isSamsungBrand, isOppoRealmeBrand) {
        when {
            !isPlugged -> "Baterai Internal (Li-Ion)"
            batt.fastChargeProtocol.isNotBlank() && !batt.fastChargeProtocol.contains("Battery", true) -> {
                if (isXiaomiBrand && !isTranssionBrand && batt.fastChargeProtocol.contains("Transsion", ignoreCase = true)) {
                    "Xiaomi TurboCharge"
                } else if (isSamsungBrand && !isTranssionBrand && batt.fastChargeProtocol.contains("Transsion", ignoreCase = true)) {
                    "Samsung Fast Charging"
                } else if (isOppoRealmeBrand && !isTranssionBrand && batt.fastChargeProtocol.contains("Transsion", ignoreCase = true)) {
                    "SuperVOOC / Fast Charge"
                } else {
                    batt.fastChargeProtocol
                }
            }
            liveHardwareProtocol.isNotBlank() && !liveHardwareProtocol.contains("Standar") -> {
                if (isXiaomiBrand && !isTranssionBrand && liveHardwareProtocol.contains("Transsion", ignoreCase = true)) {
                    "Xiaomi TurboCharge"
                } else if (isSamsungBrand && !isTranssionBrand && liveHardwareProtocol.contains("Transsion", ignoreCase = true)) {
                    "Samsung Fast Charging"
                } else if (isOppoRealmeBrand && !isTranssionBrand && liveHardwareProtocol.contains("Transsion", ignoreCase = true)) {
                    "SuperVOOC / Fast Charge"
                } else {
                    liveHardwareProtocol
                }
            }
            isTranssionBrand -> "Transsion Super Charge (PE+)"
            isXiaomiBrand -> "Xiaomi Fast Charging"
            isSamsungBrand -> "Samsung Fast Charging"
            isOppoRealmeBrand -> "Fast Charging"
            else -> "Adaptor Daya Eksternal"
        }
    }

    // Active Lynx Charging Mode Resolution (Single Source of Truth)
    val activeChargingMode = remember(liveChargingStateMode, chargingConfig.chargingMode, chargingConfig.extremeChargingEnabled, chargingConfig.limitCurrentMa) {
        when {
            chargingConfig.chargingMode.equals("hyper", ignoreCase = true) || chargingConfig.extremeChargingEnabled -> "hyper"
            chargingConfig.chargingMode.equals("cool", ignoreCase = true) -> "cool"
            chargingConfig.chargingMode.equals("custom", ignoreCase = true) -> "custom"
            liveChargingStateMode.isNotBlank() && !liveChargingStateMode.startsWith("bypass") -> liveChargingStateMode
            chargingConfig.chargingMode.isNotBlank() -> chargingConfig.chargingMode.lowercase()
            chargingConfig.limitCurrentMa in 500..2500 -> "cool"
            chargingConfig.limitCurrentMa > 2500 && chargingConfig.limitCurrentMa != 6000 -> "custom"
            else -> "standard"
        }
    }

    // Banner & Theme Configuration
    val modeInfo = remember(isPlugged, isBypass, isCharging, detectedProtocolName, liveWatts, activeChargingMode, isPropsSpoofed) {
        when {
            !isPlugged -> ChargingDisplayMode(
                title = "Discharging",
                description = "Menggunakan daya baterai",
                badgeColor = TextSecondary,
                badgeBg = BgSurfaceLowest,
                icon = Icons.Default.BatteryStd
            )
            isBypass -> ChargingDisplayMode(
                title = "⚡ BYPASS CHARGING",
                description = "Daya ke motherboard (0 mA)",
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
                val baseDesc = if (liveWatts > 0.5f) {
                    if (hasAdapterData && pagerState.currentPage == 0) "Input adaptor • ${String.format(Locale.US, "%.1f", adapterWatts)} W"
                    else "Daya baterai • ${String.format(Locale.US, "%.1f", liveWatts)} W"
                } else "Daya terhubung"
                val desc = if (isPropsSpoofed) "$baseDesc • 🛡️ Anti-Spoof" else baseDesc
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

    // Layered charge animation: the battery arc breathes while energy sparks orbit the dial.
    val infiniteTransition = rememberInfiniteTransition(label = "charging_glow_ring")
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.70f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_pulse"
    )
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "charge_orbit"
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
            // Header Row: card title and clean hardware status icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                    // Precision EV / Cyberpunk Ring Canvas
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasSize = size.minDimension
                        val strokeW = 5.5.dp.toPx()
                        val glowW = 10.dp.toPx()
                        val radius = (canvasSize - glowW) / 2f
                        val orbitRadius = radius + 3.dp.toPx()
                        val centerOffset = Offset(size.width / 2f, size.height / 2f)

                        // 1. Sleek Recessed Dark Metallic Track Groove
                        drawCircle(
                            color = Color(0xFF0F141D),
                            radius = radius,
                            center = centerOffset,
                            style = Stroke(width = strokeW)
                        )
                        // Outer track guide hairline
                        drawCircle(
                            color = Color(0x18FFFFFF),
                            radius = radius + (strokeW / 2f) + 1.dp.toPx(),
                            center = centerOffset,
                            style = Stroke(width = 0.75.dp.toPx())
                        )
                        // Inner track guide hairline
                        drawCircle(
                            color = Color(0x10FFFFFF),
                            radius = radius - (strokeW / 2f) - 1.dp.toPx(),
                            center = centerOffset,
                            style = Stroke(width = 0.75.dp.toPx())
                        )
                        drawCircle(
                            color = modeInfo.badgeColor.copy(alpha = 0.12f),
                            radius = orbitRadius,
                            center = centerOffset,
                            style = Stroke(width = 0.8.dp.toPx())
                        )

                        // 2. Quadrant Accent Notches (12, 3, 6, 9 o'clock)
                        val notchAngles = floatArrayOf(-90f, 0f, 90f, 180f)
                        for (na in notchAngles) {
                            val nRad = Math.toRadians(na.toDouble()).toFloat()
                            val nx = centerOffset.x + radius * cos(nRad)
                            val ny = centerOffset.y + radius * sin(nRad)
                            drawCircle(
                                color = Color(0x33FFFFFF),
                                radius = 1.2.dp.toPx(),
                                center = Offset(nx, ny)
                            )
                        }

                        // 3. Dynamic Battery Energy Arc
                        val sweepAngle = (animatedSoc * 3.6f).coerceIn(1.5f, 360f)
                        val startAngle = -90f

                        // Soft Breathing Glow Halo (underneath the active arc)
                        if (isCharging || isBypass) {
                            drawArc(
                                color = modeInfo.badgeColor.copy(alpha = 0.22f * ringAlpha),
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                style = Stroke(width = glowW, cap = StrokeCap.Round)
                            )
                        }

                        // High-Precision Energy Arc
                        val primaryColor = modeInfo.badgeColor
                        val startColor = when {
                            isBypass -> AccentCyan
                            isCharging -> when (activeChargingMode) {
                                "hyper" -> Color(0xFF00E5FF)
                                "cool" -> Color(0xFF00B0FF)
                                else -> Color(0xFF00E676)
                            }
                            else -> AccentCyan.copy(alpha = 0.85f)
                        }

                        val arcBrush = Brush.linearGradient(
                            colors = listOf(startColor, primaryColor),
                            start = Offset(centerOffset.x, centerOffset.y - radius),
                            end = Offset(centerOffset.x, centerOffset.y + radius)
                        )

                        drawArc(
                            brush = arcBrush,
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            style = Stroke(width = strokeW, cap = StrokeCap.Round)
                        )

                        // Moving sparks make active charging feel like energy is flowing around the dial.
                        if (isCharging) {
                            for (trail in 0..2) {
                                val angle = Math.toRadians((orbitAngle - 90f - trail * 9f).toDouble())
                                val sparkCenter = Offset(
                                    centerOffset.x + orbitRadius * cos(angle).toFloat(),
                                    centerOffset.y + orbitRadius * sin(angle).toFloat()
                                )
                                val fade = (1f - trail * 0.27f) * ringAlpha
                                drawCircle(
                                    color = modeInfo.badgeColor.copy(alpha = 0.24f * fade),
                                    radius = (5.5f - trail * 0.7f).dp.toPx(),
                                    center = sparkCenter
                                )
                                drawCircle(
                                    color = if (trail == 0) Color.White.copy(alpha = fade) else modeInfo.badgeColor.copy(alpha = fade),
                                    radius = (2.1f - trail * 0.35f).dp.toPx(),
                                    center = sparkCenter
                                )
                            }
                        } else if (isBypass) {
                            // A short cyan comet around the outer rail represents power routed around the battery.
                            drawArc(
                                color = AccentCyan.copy(alpha = 0.32f + 0.25f * ringAlpha),
                                startAngle = orbitAngle - 90f,
                                sweepAngle = 54f,
                                useCenter = false,
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        // 4. Glowing Energy Bead / Tip Indicator at leading edge
                        val tipAngleRad = Math.toRadians((startAngle + sweepAngle).toDouble()).toFloat()
                        val tipX = centerOffset.x + radius * cos(tipAngleRad)
                        val tipY = centerOffset.y + radius * sin(tipAngleRad)
                        val tipOffset = Offset(tipX, tipY)

                        // Soft tip aura
                        drawCircle(
                            color = primaryColor.copy(alpha = if (isCharging) 0.50f * ringAlpha else 0.40f),
                            radius = (4.5.dp.toPx() * (if (isCharging) ringAlpha else 1.0f)),
                            center = tipOffset
                        )
                        // Bright white-hot energy core
                        drawCircle(
                            color = Color.White,
                            radius = 2.2.dp.toPx(),
                            center = tipOffset
                        )
                    }

                    // Inner Dial Face (Clickable for Quick Mode Selector)
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        BgElevated,
                                        Color(0xFF0D121B)
                                    )
                                )
                            )
                            .border(
                                0.8.dp,
                                modeInfo.badgeColor.copy(alpha = if (isCharging) 0.35f else 0.15f),
                                CircleShape
                            )
                            .clickable { showChargingModeModal = true },
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
                                    text = "LIVE PRECISION",
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextTertiary,
                                    letterSpacing = 0.8.sp
                                )
                            }

                            if (isCharging || isBypass) {
                                Spacer(Modifier.height(3.dp))

                                val ringModeText = when {
                                    isBypass -> "⚡ BYPASS"
                                    activeChargingMode == "hyper" -> "⚡ HYPER"
                                    activeChargingMode == "cool" -> "⚡ COOL"
                                    activeChargingMode == "custom" -> "⚡ CUSTOM"
                                    else -> "⚡ STANDARD"
                                }
                                val activePillColor = if (isBypass) AccentCyan else modeInfo.badgeColor

                                Surface(
                                    shape = RoundedCornerShape(7.dp),
                                    color = activePillColor.copy(alpha = 0.12f),
                                    border = BorderStroke(0.8.dp, activePillColor.copy(alpha = 0.40f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = ringModeText,
                                            fontSize = 7.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = activePillColor,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }
                            }
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
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = modeInfo.description,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // 2x2 Hardware Telemetry Bento Grid
                    if (hasAdapterData) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxWidth()
                        ) { page ->
                            if (page == 0) {
                                // Page 0: Input Adaptor (Hardware ADC Sensor)
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        MetricMiniTile(
                                            label = "Daya Adaptor",
                                            value = "${String.format(Locale.US, "%.1f", adapterWatts)} W",
                                            color = TextPrimary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        MetricMiniTile(
                                            label = "Suhu Baterai",
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
                                            label = "Tegangan Charger",
                                            value = "${String.format(Locale.US, "%.2f", adapterVolts)} V",
                                            color = TextPrimary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        MetricMiniTile(
                                            label = "Arus Charger",
                                            value = "+$adapterCurrentMa mA",
                                            color = TextPrimary,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            } else {
                                // Page 1: Sel Baterai (Fuel Gauge IC)
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        MetricMiniTile(
                                            label = "Daya Baterai",
                                            value = "${String.format(Locale.US, "%.1f", liveWatts)} W",
                                            color = TextPrimary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        MetricMiniTile(
                                            label = "Suhu Baterai",
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
                                            label = "Tegangan Sel",
                                            value = "${String.format(Locale.US, "%.2f", liveVolts)} V",
                                            color = TextPrimary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        MetricMiniTile(
                                            label = "Arus Baterai",
                                            value = when {
                                                isBypass -> "0 mA (Bypass)"
                                                isCharging -> "+${effectiveCurrentMa.roundToInt()} mA"
                                                rawCurrentMa > 0 -> "+${rawCurrentMa} mA"
                                                else -> "${rawCurrentMa} mA"
                                            },
                                            color = TextPrimary,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        // Compact Dot Indicator (hanya berpindah jika digeser)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(2) { index ->
                                val isSelected = pagerState.currentPage == index
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 2.5.dp)
                                        .height(3.5.dp)
                                        .width(if (isSelected) 14.dp else 4.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) AccentCyan else BorderGlass)
                                        .clickable {
                                            coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                        }
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                MetricMiniTile(
                                    label = if (isCharging) "Daya Masuk" else "Konsumsi",
                                    value = "${String.format(Locale.US, "%.1f", liveWatts)} W",
                                    color = TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricMiniTile(
                                    label = "Suhu Baterai",
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
                                    label = "Arus",
                                    value = when {
                                        isBypass -> "0 mA (Bypass)"
                                        isCharging -> "+${effectiveCurrentMa.roundToInt()} mA"
                                        rawCurrentMa > 0 -> "+${rawCurrentMa} mA"
                                        else -> "${rawCurrentMa} mA"
                                    },
                                    color = TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showChargingModeModal) {
        QuickChargingModeModal(
            currentMode = if (isBypass || chargingConfig.bypassEnabled) "bypass" else activeChargingMode,
            accentColor = if (accentColor != Color.Unspecified) accentColor else AccentCyan,
            onSelectMode = { mode ->
                onSelectChargingMode?.invoke(mode)
            },
            onDismiss = { showChargingModeModal = false }
        )
    }
}

private data class ChargingModeOption(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector
)

@Composable
private fun RadioIndicator(
    selected: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(20.dp)
            .border(
                width = if (selected) 1.8.dp else 1.2.dp,
                color = if (selected) accentColor else Color(0x38FFFFFF),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(accentColor, CircleShape)
            )
        }
    }
}

@Composable
private fun QuickChargingModeModal(
    currentMode: String,
    accentColor: Color,
    onSelectMode: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val modes = listOf(
        ChargingModeOption(
            id = "hyper",
            title = "Mode Hyper",
            description = "Kecepatan pengisian maksimal dengan daya penuh",
            icon = Icons.Default.Bolt
        ),
        ChargingModeOption(
            id = "standard",
            title = "Mode Standar",
            description = "Keseimbangan cerdas kecepatan dan suhu perangkat",
            icon = Icons.Default.Balance
        ),
        ChargingModeOption(
            id = "cool",
            title = "Mode Sejuk",
            description = "Menjaga perangkat tetap dingin untuk keawetan baterai",
            icon = Icons.Default.AcUnit
        ),
        ChargingModeOption(
            id = "bypass",
            title = "Bypass Baterai",
            description = "Jika didukung perangkat, daya adaptor dialirkan ke sistem tanpa mengisi baterai.",
            icon = Icons.Default.Shield
        )
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .wrapContentHeight()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF0F1219),
            border = BorderStroke(1.dp, Color(0x22FFFFFF)),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp)
            ) {
                // Header (Clean OEM Style)
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
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Mode Pengisian Daya",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                letterSpacing = 0.3.sp
                            )
                            Text(
                                text = "Pilih prioritas kecepatan dan kontrol termal",
                                fontSize = 10.5.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = localizedUiText("Tutup"),
                            tint = TextTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Mode Selection Cards (Streamlined OEM Rows)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    modes.forEach { item ->
                        val isSelected = currentMode == item.id
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) accentColor.copy(alpha = 0.08f) else Color(0xFF131722),
                            border = BorderStroke(
                                width = if (isSelected) 1.2.dp else 0.8.dp,
                                color = if (isSelected) accentColor.copy(alpha = 0.80f) else Color(0x18FFFFFF)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    onSelectMode(item.id)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) accentColor.copy(alpha = 0.16f)
                                            else Color(0x0EFFFFFF)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = null,
                                        tint = if (isSelected) accentColor else Color(0xFFC0C4CC),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(Modifier.width(13.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) TextPrimary else TextPrimary.copy(alpha = 0.90f)
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = item.description,
                                        fontSize = 10.5.sp,
                                        color = TextSecondary,
                                        lineHeight = 14.sp
                                    )
                                }

                                Spacer(Modifier.width(12.dp))

                                RadioIndicator(
                                    selected = isSelected,
                                    accentColor = accentColor
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Footer (Minimalist Status)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Diterapkan langsung ke kernel tanpa restart",
                        fontSize = 9.5.sp,
                        color = TextTertiary,
                        letterSpacing = 0.2.sp
                    )
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
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = BgSurfaceLowest,
        border = BorderStroke(0.6.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = TextTertiary,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                textAlign = TextAlign.Center
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
    val mfgUpper = android.os.Build.MANUFACTURER.uppercase(Locale.US)
    val brandUpper = android.os.Build.BRAND.uppercase(Locale.US)

    // Hardware Ground Truth Tier 1: Hardware physical nodes ALWAYS override software props
    val hasTranHw = File("/sys/devices/platform/odm/odm:tran_battery").exists() ||
            File("/sys/devices/platform/tran_battery").exists() ||
            File("/sys/bus/i2c/drivers/rt9759").exists() ||
            File("/sys/devices/platform/charger/BN_TestMode").exists()

    val hasXiaomiHw = File("/sys/class/power_supply/battery/fastcharge_mode").exists() ||
            File("/sys/class/thermal/thermal_message").exists() ||
            File("/sys/class/power_supply/battery/turbo_power").exists() ||
            File("/sys/bus/i2c/drivers/ln8000").exists() ||
            File("/sys/bus/i2c/drivers/sc8551").exists()

    val hasOplusHw = File("/sys/class/power_supply/battery/vooc_charging").exists() ||
            File("/sys/class/oplus_chg").exists()

    val hasSamsungHw = File("/sys/class/power_supply/sec-battery").exists() ||
            File("/sys/class/sec/sec-battery").exists()

    val isTranssion = hasTranHw || (!hasXiaomiHw && !hasOplusHw && !hasSamsungHw &&
            (mfgUpper.contains("INFINIX") || mfgUpper.contains("TECNO") || mfgUpper.contains("TRANSSION") ||
             brandUpper.contains("INFINIX") || brandUpper.contains("TECNO")))

    val isXiaomi = (hasXiaomiHw && !hasTranHw) || (!hasTranHw && !hasOplusHw && !hasSamsungHw &&
            (mfgUpper.contains("XIAOMI") || brandUpper.contains("XIAOMI") || brandUpper.contains("REDMI") || brandUpper.contains("POCO")))

    return when {
        // Transsion Fast Charge (ONLY on genuine Infinix / Tecno hardware)
        isTranssion && (mtkChgType == "9" || ptypUpper.contains("TRAN") || ptypUpper.contains("RFC")) -> {
            "Transsion Super Charge (PE+)"
        }
        // Xiaomi Turbo / HyperCharge
        isXiaomi && (miSig == "1" || miSig == "2" || ptypUpper.contains("TURBO") || ptypUpper.contains("HYPER")) -> {
            "Xiaomi TurboCharge"
        }
        // Qualcomm Quick Charge (HVDCP, HVDCP_3, HVDCP_3P5, QC 2.0/3.0/4.0/5.0)
        ptypUpper.contains("HVDCP_3P5") || qcUpper.contains("4") || qcUpper.contains("5") -> {
            "Qualcomm QC 4/5"
        }
        ptypUpper.contains("HVDCP_3") || qcUpper.contains("3") || qcUpper.contains("QC3") -> {
            "Qualcomm QC 3.0"
        }
        ptypUpper.contains("HVDCP") || qcUpper.contains("2") || qcUpper.contains("QC2") || qcUpper.contains("QC") -> {
            "Qualcomm QC"
        }
        // USB Power Delivery & PPS
        mtkChgType == "4" || mtkChgType == "7" || pdSig == "1" || pdSig == "2" || ptypUpper.contains("PD") || ptypUpper.contains("PPS") -> {
            if (pdSig == "2" || ptypUpper.contains("PPS")) "USB-PD / PPS"
            else "USB-PD"
        }
        // SuperVOOC / Dart Charge (OPPO / Realme / OnePlus)
        voocSig == "1" || voocSig == "2" || ptypUpper.contains("VOOC") || ptypUpper.contains("DART") -> {
            "SuperVOOC / Dart"
        }
        // Generic Xiaomi fallback
        miSig == "1" || miSig == "2" || ptypUpper.contains("TURBO") || ptypUpper.contains("HYPER") -> {
            "Xiaomi TurboCharge"
        }
        // MediaTek Pump Express
        mtkChgType == "8" || peSig.isNotBlank() && peSig != "0" || ptypUpper.contains("PUMP") || ptypUpper.contains("PE") -> {
            "MTK Pump Express"
        }
        // PC / Laptop Port
        ptypUpper.contains("SDP") || ptypUpper.contains("CDP") || ptypUpper.contains("PC") -> {
            "USB PC / Laptop"
        }
        // Standard Adapter
        else -> "Adaptor Standar (DCP)"
    }
}

private data class ChargingDisplayMode(
    val title: String,
    val description: String,
    val badgeColor: Color,
    val badgeBg: Color,
    val icon: ImageVector
)
