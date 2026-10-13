package com.noir.lynx.data

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.noir.lynx.LynxApp
import com.noir.lynx.hardware.NodeWriter
import com.noir.lynx.hardware.WriteResult
import com.noir.lynx.hardware.gpu.GpuBackendManager
import com.noir.lynx.service.LynxAppAutomationService
import com.topjohnwu.superuser.Shell
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

internal object LynxBatteryTelemetryRepository {
        suspend fun readBatteryDetails(includeDeepAdcAndTz: Boolean = false): BatteryDetails? = withContext(Dispatchers.IO) {
            try {
                val deepFlag = if (includeDeepAdcAndTz) "1" else "0"
                val script = """
                    cap=${'$'}(cat /sys/class/power_supply/battery/capacity 2>/dev/null || cat /sys/class/power_supply/bms/capacity 2>/dev/null || echo 0)
                    stat=${'$'}(cat /sys/class/power_supply/battery/status 2>/dev/null || cat /sys/class/power_supply/bms/status 2>/dev/null || echo "Unknown")
                    hlth=${'$'}(cat /sys/class/power_supply/battery/health 2>/dev/null || cat /sys/class/power_supply/bms/health 2>/dev/null || echo "Good")
                    temp=${'$'}(cat /sys/class/power_supply/battery/temp 2>/dev/null)
                    [ -z "${'$'}temp" ] || [ "${'$'}temp" = "0" ] 2>/dev/null && temp=${'$'}(cat /sys/class/power_supply/bms/temp 2>/dev/null)
                    [ -z "${'$'}temp" ] && temp=0
                    volt=${'$'}(cat /sys/class/power_supply/battery/voltage_now 2>/dev/null)
                    [ -z "${'$'}volt" ] || [ "${'$'}volt" = "0" ] 2>/dev/null && volt=${'$'}(cat /sys/class/power_supply/bms/voltage_now 2>/dev/null)
                    [ -z "${'$'}volt" ] && volt=0
                    cur=${'$'}(cat /sys/class/power_supply/battery/current_now 2>/dev/null)
                    [ -z "${'$'}cur" ] || [ "${'$'}cur" = "0" ] 2>/dev/null && cur=${'$'}(cat /sys/class/power_supply/battery/BatteryAverageCurrent 2>/dev/null)
                    [ -z "${'$'}cur" ] || [ "${'$'}cur" = "0" ] 2>/dev/null && cur=${'$'}(cat /sys/class/power_supply/bms/current_now 2>/dev/null)
                    [ -z "${'$'}cur" ] || [ "${'$'}cur" = "0" ] 2>/dev/null && cur=${'$'}(cat /sys/class/power_supply/battery/current_avg 2>/dev/null)
                    [ -z "${'$'}cur" ] && cur=0
                    cyc=${'$'}(cat /sys/class/power_supply/battery/cycle_count 2>/dev/null || echo -1)
                    cnt=${'$'}(cat /sys/class/power_supply/battery/charge_counter 2>/dev/null || echo 0)
                    full=${'$'}(cat /sys/class/power_supply/battery/charge_full 2>/dev/null)
                    if [ -z "${'$'}full" ] || [ "${'$'}full" = "0" ]; then full=${'$'}(cat /sys/class/power_supply/bms/charge_full 2>/dev/null); fi
                    if [ -z "${'$'}full" ] || [ "${'$'}full" = "0" ]; then full=${'$'}(cat /sys/class/power_supply/battery/charge_full_design 2>/dev/null); fi
                    if [ -z "${'$'}full" ] || [ "${'$'}full" = "0" ]; then full=${'$'}(cat /sys/class/power_supply/bms/charge_full_design 2>/dev/null); fi
                    [ -z "${'$'}full" ] && full=0

                    uon=${'$'}(cat /sys/class/power_supply/usb/online 2>/dev/null || cat /sys/class/power_supply/ac/online 2>/dev/null || echo 0)
                    is_plugged=0
                    if [ "${'$'}uon" = "1" ] || [ "${'$'}stat" = "Charging" ] || [ "${'$'}stat" = "Full" ] || [ "${'$'}stat" = "Not charging" ]; then
                        is_plugged=1
                    fi
                    if [ "${'$'}stat" = "Discharging" ] && [ "${'$'}uon" != "1" ]; then
                        is_plugged=0
                    fi

                    if [ "${'$'}is_plugged" = "0" ]; then
                        adpv=0
                        raw_ibus=0
                        ibus=0
                        chgtyp=""
                        cst="state=discharging cap=${'$'}cap"
                        rm -f /dev/lynx_extreme_charging 2>/dev/null
                    else
                        adpv=${'$'}(cat /sys/devices/platform/odm/odm:tran_battery/Pump_Express_VCharger 2>/dev/null)
                        [ -z "${'$'}adpv" ] || [ "${'$'}adpv" -le 0 ] 2>/dev/null && adpv=${'$'}(cat /sys/devices/platform/charger/ADC_Charger_Voltage 2>/dev/null)
                        [ -z "${'$'}adpv" ] || [ "${'$'}adpv" -le 0 ] 2>/dev/null && adpv=${'$'}(cat /sys/class/power_supply/usb/voltage_now 2>/dev/null)
                        [ -z "${'$'}adpv" ] || [ "${'$'}adpv" -le 0 ] 2>/dev/null && adpv=${'$'}(cat /sys/class/power_supply/battery/charger_voltage 2>/dev/null)
                        [ -z "${'$'}adpv" ] || [ "${'$'}adpv" -le 0 ] 2>/dev/null && adpv=${'$'}(cat /sys/class/power_supply/main/voltage_now 2>/dev/null)
                        [ -z "${'$'}adpv" ] && adpv=0

                        chgtyp=${'$'}(cat /sys/devices/platform/charger/Charger_Type 2>/dev/null || cat /sys/class/power_supply/usb/type 2>/dev/null || echo "")

                        # Read only true live ADC current sensors (never static limit nodes like charger/input_current)
                        raw_ibus=${'$'}(cat /sys/devices/platform/odm/odm:tran_battery/Pump_Express_ICharger 2>/dev/null)
                        pe_ibus=0
                        if [ -n "${'$'}raw_ibus" ] && [ "${'$'}raw_ibus" -gt 0 ] 2>/dev/null; then pe_ibus=1; fi
                        [ "${'$'}pe_ibus" = "1" ] || raw_ibus=${'$'}(cat /sys/bus/i2c/drivers/rt9759/*/Ibus 2>/dev/null | head -n 1)
                        [ -z "${'$'}raw_ibus" ] || [ "${'$'}raw_ibus" -le 0 ] 2>/dev/null && raw_ibus=${'$'}(cat /sys/class/power_supply/usb/current_now 2>/dev/null)
                        [ -z "${'$'}raw_ibus" ] || [ "${'$'}raw_ibus" -le 0 ] 2>/dev/null && raw_ibus=${'$'}(cat /sys/class/power_supply/usb/input_current_now 2>/dev/null)
                        [ -z "${'$'}raw_ibus" ] || [ "${'$'}raw_ibus" -le 0 ] 2>/dev/null && raw_ibus=${'$'}(cat /sys/class/power_supply/main/current_now 2>/dev/null)
                        [ -z "${'$'}raw_ibus" ] && raw_ibus=0

                        # Scaling heuristic: µA values are always > 100000, mA values are ≤ 9000.
                        if [ -n "${'$'}raw_ibus" ] && [ "${'$'}raw_ibus" -gt 100000 ] 2>/dev/null; then
                            ibus=${'$'}(( raw_ibus / 1000 ))
                        elif [ "${'$'}pe_ibus" = "1" ] && [ "${'$'}raw_ibus" -gt 10 ] && [ "${'$'}raw_ibus" -lt 1000 ] 2>/dev/null; then
                            ibus=${'$'}(( raw_ibus * 10 ))
                        else
                            ibus="${'$'}raw_ibus"
                        fi
                    fi

                    rfc=${'$'}([ "${'$'}chgtyp" = "9" ] && echo 1 || (cat /sys/bus/i2c/drivers/rt9759/*/rfc_dcp_ta 2>/dev/null | head -n 1 || echo 0))
                    rtmp=${'$'}(cat /sys/class/thermal/thermal_zone1/temp 2>/dev/null)
                    [ -z "${'$'}rtmp" ] || [ "${'$'}rtmp" -le 0 ] 2>/dev/null && rtmp=${'$'}temp
                    grd=${'$'}([ -f /dev/lynx_charging_guard ] && echo 1 || echo 0)
                    cst=${'$'}(cat /dev/lynx_charging_state 2>/dev/null || echo "")
                    bpa=${'$'}([ -f /dev/lynx_bypass_active ] && echo 1 || echo 0)

                    # Multi-vendor PMIC detection
                    pmic_sig=""
                    if [ -d /sys/class/power_supply/smb1390 ] || [ -d /sys/bus/i2c/drivers/smb1390 ]; then
                        pmic_sig="smb1390"
                    elif [ -d /sys/class/power_supply/smb1355 ] || [ -d /sys/bus/i2c/drivers/smb1355 ]; then
                        pmic_sig="smb1355"
                    elif [ -d /sys/class/power_supply/bms ] || [ -d /sys/class/qcom-battery ]; then
                        pmic_sig="qcom_pmic"
                    elif [ -d /sys/bus/i2c/drivers/rt9759 ]; then
                        pmic_sig="rt9759"
                    elif [ -e /sys/class/power_supply/sec-direct-charger ]; then
                        pmic_sig="sec_direct"
                    elif [ -d /sys/bus/i2c/drivers/max77705 ] || [ -d /sys/bus/i2c/drivers/max77854 ] || [ -d /sys/class/power_supply/sec-charger ]; then
                        pmic_sig="sec_max"
                    elif [ -d /sys/bus/i2c/drivers/ln8000 ] || [ -d /sys/bus/i2c/drivers/sc8551 ] || [ -e /sys/class/power_supply/battery/sub_charger_type ]; then
                        pmic_sig="mi_pump"
                    elif [ -e /sys/class/power_supply/battery/vooc_charging ] || [ -e /sys/class/power_supply/battery/cool_mode ]; then
                        pmic_sig="vooc_pump"
                    elif [ -d /sys/bus/i2c/drivers/max77759 ] || [ -d /sys/bus/i2c/drivers/da9121 ]; then
                        pmic_sig="pixel_pmic"
                    fi

                    # Multi-vendor fast charging protocol signals
                    sec_sig=${'$'}(cat /sys/class/power_supply/battery/afc_result 2>/dev/null || cat /sys/class/power_supply/battery/charge_mode 2>/dev/null || echo "")
                    vooc_sig=${'$'}(cat /sys/class/power_supply/battery/vooc_charging 2>/dev/null || cat /sys/class/power_supply/battery/fast_charge_user_type 2>/dev/null || echo "")
                    qc_sig=${'$'}(cat /sys/class/power_supply/usb/quick_charge_type 2>/dev/null || cat /sys/class/qcom-battery/qc_type 2>/dev/null || cat /sys/class/power_supply/usb/real_type 2>/dev/null || cat /sys/class/power_supply/usb/type 2>/dev/null || echo "")
                    mi_sig=${'$'}(cat /sys/class/power_supply/battery/fastcharge_mode 2>/dev/null || cat /sys/class/power_supply/battery/boost_current 2>/dev/null || echo "")
                    pd_sig=${'$'}(cat /sys/class/power_supply/usb/pd_active 2>/dev/null || cat /sys/class/power_supply/usb/pd_allowed 2>/dev/null || cat /sys/class/typec/pd_active 2>/dev/null || echo "")

                    mtk_chg=${'$'}(cat /sys/devices/platform/charger/Charger_Type 2>/dev/null || echo "")
                    [ "${'$'}mtk_chg" = "7" ] && pd_sig="1"
                    [ "${'$'}mtk_chg" = "9" ] && rfc="1"
                    ptyp=${'$'}(cat /sys/class/power_supply/usb/type 2>/dev/null || cat /sys/class/power_supply/usb/real_type 2>/dev/null || echo "")
                    [ -z "${'$'}ptyp" ] && [ -n "${'$'}mtk_chg" ] && [ "${'$'}mtk_chg" != "0" ] && ptyp="MTK_${'$'}mtk_chg"
                    [ -z "${'$'}ptyp" ] && [ -d /sys/class/power_supply/pc_port ] && ptyp="SDP"
                    rcbl=${'$'}(cat /sys/devices/platform/battery/FG_meter_resistance 2>/dev/null || cat /sys/class/power_supply/battery/resistance 2>/dev/null || cat /sys/class/power_supply/bms/resistance 2>/dev/null || echo 0)
                    spftmp=${'$'}(cat /sys/devices/platform/battery/Battery_Temperature 2>/dev/null || echo 0)

                    echo "${'$'}cap|${'$'}stat|${'$'}hlth|${'$'}temp|${'$'}volt|${'$'}cur|${'$'}cyc|${'$'}cnt|${'$'}adpv|${'$'}chgtyp|${'$'}ibus|${'$'}rfc|${'$'}rtmp|${'$'}grd|${'$'}cst|${'$'}pmic_sig|${'$'}sec_sig|${'$'}vooc_sig|${'$'}qc_sig|${'$'}mi_sig|${'$'}pd_sig|${'$'}ptyp|${'$'}rcbl|${'$'}spftmp|${'$'}is_plugged|${'$'}bpa|${'$'}full"

                    if [ "$deepFlag" = "1" ]; then
                        echo "---ADC---"
                        echo "Pump_Express_VCharger=${'$'}adpv"
                        echo "Pump_Express_ICharger=${'$'}raw_ibus"
                        echo "ADC_Charger_Voltage=${'$'}(cat /sys/devices/platform/charger/ADC_Charger_Voltage 2>/dev/null || echo 0)"
                        echo "BatteryAverageCurrent=${'$'}(cat /sys/class/power_supply/battery/BatteryAverageCurrent 2>/dev/null || echo 0)"
                        echo "sc_ibat_limit=${'$'}(cat /sys/devices/platform/charger/sc_ibat_limit 2>/dev/null || echo 0)"
                        echo "pdc_max_watt=${'$'}(cat /sys/devices/platform/charger/pdc_max_watt 2>/dev/null || echo 0)"
                        echo "input_current=${'$'}(cat /sys/devices/platform/charger/input_current 2>/dev/null || echo 0)"
                        echo "chg1_current=${'$'}(cat /sys/devices/platform/charger/chg1_current 2>/dev/null || echo 0)"
                        echo "BN_TestMode=${'$'}(cat /sys/devices/platform/charger/BN_TestMode 2>/dev/null || echo 0)"
                        echo "pe40=${'$'}(cat /sys/devices/platform/charger/pe40 2>/dev/null || echo 0)"
                        echo "Battery_Temperature=${'$'}spftmp"
                        echo "pcb_thermal_debug=${'$'}(cat /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug 2>/dev/null || cat /sys/devices/platform/tran_battery/pcb_thermal_debug 2>/dev/null || echo 0)"

                        echo "---TZ---"
                        for tz in /sys/class/thermal/thermal_zone*; do
                            [ -d "${'$'}tz" ] || continue
                            t=${'$'}(cat "${'$'}tz/type" 2>/dev/null)
                            v=${'$'}(cat "${'$'}tz/temp" 2>/dev/null)
                            [ -n "${'$'}t" ] && [ -n "${'$'}v" ] && echo "${'$'}{tz##*/}:${'$'}{t}:${'$'}{v}"
                        done
                    fi
                """.trimIndent()
                val r = Shell.cmd(script).exec()
                val lines = r.out
                val firstLine = lines.firstOrNull { it.contains("|") }?.trim() ?: return@withContext null
                val parts = firstLine.split("|")
                if (parts.size >= 8) {
                    val cap = parts[0].toIntOrNull() ?: 0
                    val stat = parts[1].ifBlank { "Unknown" }
                    val hlth = parts[2].ifBlank { "Good" }
                    val rawTemp = parts[3].toFloatOrNull() ?: 0f
                    val tempC = if (rawTemp > 100f) rawTemp / 10f else rawTemp
                    val rawVolt = parts[4].toIntOrNull() ?: 0
                    val voltMv = if (rawVolt > 100000) rawVolt / 1000 else rawVolt
                    val rawCur = parts[5].toIntOrNull() ?: 0
                    val curMa = if (Math.abs(rawCur) > 10000) rawCur / 1000 else rawCur
                    val cyc = parts[6].toIntOrNull() ?: -1
                    val rawCnt = parts[7].toIntOrNull() ?: 0
                    val cntMah = if (rawCnt > 100000) rawCnt / 1000 else rawCnt
                    val rawFull = parts.getOrNull(26)?.toLongOrNull() ?: 0L
                    val fullChargeMah = when {
                        rawFull in 1_000L..20_000L -> rawFull.toInt()
                        rawFull > 100_000L -> (rawFull / 1000L).toInt()
                        else -> 0
                    }
                    val rawAdpv = parts.getOrNull(8)?.toIntOrNull() ?: 0
                    val chgTyp = parts.getOrNull(9) ?: ""
                    val adpMv = if (rawAdpv > 100000) rawAdpv / 1000 else rawAdpv

                    val rawIbus = parts.getOrNull(10)?.toIntOrNull() ?: 0
                    val ibusMa = if (Math.abs(rawIbus) > 10000) rawIbus / 1000 else rawIbus
                    val rfcAuth = parts.getOrNull(11)?.trim() == "1"
                    val rawRtmp = parts.getOrNull(12)?.toFloatOrNull() ?: 0f
                    val realTempC = if (rawRtmp > 1000f) rawRtmp / 1000f else if (rawRtmp > 100f) rawRtmp / 10f else rawRtmp
                    val isGuardActive = parts.getOrNull(13)?.trim() == "1"
                    val chgState = parts.getOrNull(14)?.trim() ?: ""

                    val pmicSig = parts.getOrNull(15)?.trim() ?: ""
                    val secSig = parts.getOrNull(16)?.trim() ?: ""
                    val voocSig = parts.getOrNull(17)?.trim() ?: ""
                    val qcSig = parts.getOrNull(18)?.trim() ?: ""
                    val miSig = parts.getOrNull(19)?.trim() ?: ""
                    val pdSig = parts.getOrNull(20)?.trim() ?: ""

                    val rawPtyp = parts.getOrNull(21)?.trim() ?: ""
                    val rawRcbl = parts.getOrNull(22)?.toIntOrNull() ?: 0
                    val rawSpf = parts.getOrNull(23)?.toFloatOrNull() ?: 0f
                    val spoofedTemp = if (rawSpf > 100f) rawSpf / 10f else rawSpf

                    val isLaptop = rawPtyp.contains("SDP", ignoreCase = true) || rawPtyp.contains("CDP", ignoreCase = true) || rawPtyp.contains("PC", ignoreCase = true)
                    val portTypeDisplay = when {
                        isLaptop -> "USB PC/Laptop (SDP/CDP)"
                        rawPtyp.isNotBlank() && rawPtyp != "0" -> rawPtyp
                        else -> "Adaptor Charger"
                    }

                    // Parse Raw ADC block
                    val adcMap = mutableMapOf<String, String>()
                    var inAdc = false
                    var inTz = false
                    val tzList = mutableListOf<Pair<String, Float>>()

                    for (l in lines) {
                        val trimmed = l.trim()
                        if (trimmed == "---ADC---") {
                            inAdc = true
                            inTz = false
                            continue
                        } else if (trimmed == "---TZ---") {
                            inAdc = false
                            inTz = true
                            continue
                        }

                        if (inAdc && trimmed.contains("=")) {
                            val kv = trimmed.split("=", limit = 2)
                            if (kv.size == 2) adcMap[kv[0]] = kv[1]
                        } else if (inTz && trimmed.contains(":")) {
                            val tzParts = trimmed.split(":")
                            if (tzParts.size >= 3) {
                                val zName = "${tzParts[0]} (${tzParts[1]})"
                                val rawTzTemp = tzParts[2].toFloatOrNull() ?: 0f
                                val absTemp = kotlin.math.abs(rawTzTemp)
                                val normTemp = if (absTemp > 1000f) rawTzTemp / 1000f else if (absTemp > 100f) rawTzTemp / 10f else rawTzTemp
                                tzList.add(Pair(zName, normTemp))
                            }
                        }
                    }

                    val isPlugged = parts.getOrNull(24)?.trim() == "1"
                    val rawBpa = parts.getOrNull(25)?.trim() == "1"
                    val isBypassActive = rawBpa || chgState.contains("bypass")
                    val isCharging = isPlugged && (curMa > 50 || stat.equals("Charging", ignoreCase = true))
                    val isOvernightLatched = isPlugged && (cap >= 100 || stat.equals("Full", ignoreCase = true) || chgState.contains("bypass_100"))
                    val isLimitLatched = isPlugged && (chgState.contains("bypass_limit") || (isBypassActive && !isOvernightLatched && !chgState.contains("bypass_forced")))
                    val isTapering = isPlugged && (chgState.contains("tapering") || (cap in 90..99 && isCharging))

                    val cal = java.util.Calendar.getInstance()
                    val hr = cal.get(java.util.Calendar.HOUR_OF_DAY)
                    val isNightHour = (hr >= 23 || hr < 6)
                    val isNightPref = LynxRepository.appContext?.getSharedPreferences("lynx_charging_prefs", Context.MODE_PRIVATE)?.getBoolean("night_sleep_guard", false) == true
                    val isNightGentle = isPlugged && (chgState.contains("night_gentle") || (isNightPref && isNightHour && isCharging && !isOvernightLatched && !isLimitLatched))

                    val watt = if (isCharging && curMa > 0 && voltMv > 0) {
                        (curMa.toFloat() * voltMv.toFloat()) / 1_000_000f
                    } else 0f

                    val activeIC = when {
                        !isPlugged -> "Standby / Baterai"
                        rfcAuth || chgTyp == "9" || pmicSig == "rt9759" || (adpMv > 7000 && curMa >= 1500 && pmicSig.isBlank()) -> "Direct Charge Pump (RT9759 2:1)"
                        pmicSig == "smb1390" || (qcSig.contains("QC", ignoreCase = true) && adpMv > 7000) -> "Qualcomm SMB1390 Dual-Pump"
                        pmicSig == "smb1355" -> "Qualcomm SMB1355 Companion PMIC"
                        pmicSig == "qcom_pmic" -> "Qualcomm PMIC (PM8150/PM6150 Buck)"
                        pmicSig == "sec_direct" -> "Samsung Direct Charger (S2MU/Maxim)"
                        pmicSig == "sec_max" -> "Samsung PMIC (Maxim MAX77x Buck)"
                        pmicSig == "mi_pump" -> "Xiaomi HyperCharge Dual-Pump (LN8000/SC8551)"
                        pmicSig == "vooc_pump" || voocSig == "1" -> "SuperVOOC / Warp Charge Pump"
                        pmicSig == "pixel_pmic" -> "Google Tensor PMIC (MAX77759)"
                        else -> "Switching Buck Converter (RT9471/Universal)"
                    }

                    val ptypUpper = rawPtyp.uppercase(java.util.Locale.US)
                    val qcUpper = qcSig.uppercase(java.util.Locale.US)
                    val protocol = when {
                        !isPlugged -> "Battery Power"
                        rfcAuth || chgTyp == "9" || rawPtyp == "MTK_9" || ptypUpper.contains("TRAN") || ptypUpper.contains("RFC") -> {
                            val wLabel = if (watt >= 60f) "68W" else if (watt >= 40f) "45W" else if (watt >= 28f) "33W" else "Flash Charge"
                            "Transsion $wLabel (PE+ Direct Pump)"
                        }
                        voocSig == "1" || voocSig == "2" || ptypUpper.contains("VOOC") || ptypUpper.contains("DART") -> "SuperVOOC / Warp Fast Charge"
                        miSig == "1" && adpMv > 8000 -> "Xiaomi HyperCharge / Turbo (67W-120W)"
                        secSig == "1" || secSig.contains("AFC", ignoreCase = true) -> if (adpMv > 8000) "Samsung Super Fast Charging (25W/45W)" else "Samsung Adaptive Fast Charging (AFC)"
                        ptypUpper.contains("HVDCP_3P5") || qcUpper.contains("4") || qcUpper.contains("5") -> "Qualcomm Quick Charge 4.0/5.0"
                        ptypUpper.contains("HVDCP_3") || qcUpper.contains("3") || qcUpper.contains("QC3") -> "Qualcomm Quick Charge 3.0"
                        ptypUpper.contains("HVDCP") || qcUpper.contains("2") || qcUpper.contains("QC2") || qcUpper.contains("QC") || (qcSig.isNotBlank() && qcSig != "0") -> "Qualcomm Quick Charge"
                        pdSig == "1" || pdSig == "2" || chgTyp == "7" || rawPtyp == "MTK_7" || ptypUpper.contains("PD") -> {
                            if (pdSig == "2" || ptypUpper.contains("PPS")) "USB Power Delivery / PPS (" + String.format(java.util.Locale.US, "%.1f", (if (adpMv > 1000) adpMv else voltMv) / 1000f) + "V)"
                            else "USB Power Delivery (USB-PD)"
                        }
                        chgTyp == "8" || rawPtyp == "MTK_8" || ptypUpper.contains("PUMP") || ptypUpper.contains("PE") -> "MediaTek Pump Express (PE+)"
                        adpMv > 8500 || (watt >= 15f && isCharging) -> "Adaptor Pengisian Cepat (Fast Charger)"
                        isLaptop || ptypUpper.contains("SDP") || ptypUpper.contains("CDP") -> "Standard USB 5V (500mA - 1500mA)"
                        else -> "Adaptor USB Standar (DCP 5V/2A)"
                    }

                    val adapterWatt = if (isPlugged && adpMv > 1000 && ibusMa > 50) {
                        (adpMv.toFloat() * ibusMa.toFloat()) / 1_000_000f
                    } else if (isCharging && watt > 0.1f) {
                        val effFactor = if (rfcAuth || pmicSig == "rt9759" || pmicSig == "smb1390" || pmicSig == "mi_pump" || voocSig == "1") 0.96f else 0.88f
                        watt / effFactor
                    } else 0f

                    val effectiveIbusMa = if (isPlugged && ibusMa > 50) {
                        ibusMa
                    } else if (isPlugged && adpMv > 1000 && adapterWatt > 0.1f) {
                        ((adapterWatt * 1_000_000f) / adpMv.toFloat()).toInt()
                    } else 0

                    val efficiency = if (isPlugged && adapterWatt > 0.5f && watt > 0.5f) {
                        ((watt / adapterWatt) * 100f).toInt().coerceIn(60, 99)
                    } else if (isCharging) {
                        if (rfcAuth) 96 else 88
                    } else 0

                    BatteryDetails(
                        level = cap,
                        status = stat,
                        health = hlth,
                        tempC = tempC,
                        voltageMv = voltMv,
                        currentMa = curMa,
                        cycleCount = cyc,
                        chargeCounterMah = cntMah,
                        fullChargeCapacityMah = fullChargeMah,
                        chargerVoltageMv = adpMv,
                        chargerWatt = watt,
                        fastChargeProtocol = protocol,
                        activeICName = activeIC,
                        adapterVoltageMv = adpMv,
                        adapterCurrentMa = effectiveIbusMa,
                        adapterWatt = adapterWatt,
                        chargingEfficiencyPercent = efficiency,
                        realPhysicalTempC = realTempC,
                        spoofedTempC = spoofedTemp,
                        cableResistanceMohm = rawRcbl,
                        portType = portTypeDisplay,
                        isLaptopPort = isLaptop,
                        rawAdcDetails = adcMap,
                        thermalZoneMatrix = tzList,
                        isPlugged = isPlugged,
                        isEmergencyGuardActive = isGuardActive,
                        isOvernightBypassLatched = isOvernightLatched,
                        isSmartLimitLatched = isLimitLatched,
                        isBypassActive = isBypassActive,
                        isSmartTaperingActive = isTapering,
                        isNightGentleActive = isNightGentle
                    )
                } else null
            } catch (e: Exception) { null }
        }

        suspend fun readBatteryInfoStats(currentDetails: BatteryDetails?): BatteryInfoStats = LynxBatteryInfoStatsRepository.readBatteryInfoStats(currentDetails)

}
