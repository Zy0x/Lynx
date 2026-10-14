package com.noir.lynx.feature.cpu.ui

import java.util.Locale
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.designsystem.controls.SafeSlider as Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.*
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.designsystem.card.*
import com.noir.lynx.designsystem.controls.*
import com.noir.lynx.designsystem.tweak.*
import com.noir.lynx.designsystem.profile.*
import com.noir.lynx.feature.navigation.model.TuningCategory

// ============================================================
//  Live Hardware Telemetry Card — Luxury Bento-Box Layout
// ============================================================

import com.noir.lynx.ui.*

@Composable
fun PlatformHardwareEngineCard(
    schedInfo: SchedulerInfo,
    onPpmPolicyChange: (Int, Boolean) -> Unit,
    onQcomTouchboostChange: (Boolean) -> Unit,
    onQcomInputBoostChange: (Long, Int) -> Unit,
    onMtkInterconnectBoostChange: (Boolean) -> Unit = {},
    onMtkCpuPowerModeChange: (Int) -> Unit = {},
    onMtkDlptBypassChange: (Boolean) -> Unit = {},
    onQcomDevfreqBusBoostChange: (Boolean) -> Unit = {},
    onUniversalBusProfileChange: (String) -> Unit = {},
    onUniversalTouchBoostChange: (Boolean) -> Unit = {},
    onUniversalAntiThrottlingGuardChange: (Boolean) -> Unit = {},
    onShowSwitchInfo: ((SwitchTweakInfo) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isQcom = schedInfo.isQcomBoostSupported || schedInfo.isQcomDevfreqBusSupported
    val isMtk = (schedInfo.isPpmSupported || schedInfo.isMtkCciSupported) && !isQcom

    val engineTitle = "Platform Hardware Engine"
    val engineSubtitle = when {
        isMtk -> "MediaTek DVFS, CCI Interconnect, DVFSRC DRAM & PPM"
        isQcom -> "Snapdragon QTI HAL, Devfreq LLCC-DDR & Input Boost"
        else -> "Universal Silicon Interconnect & Power Guard"
    }

    LynxCard(
        title = engineTitle,
        subtitle = engineSubtitle,
        icon = Icons.Default.Memory,
        accentColor = AccentCyan,
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // 1. Selector 1: Mode Daya Silikon (Hardware DVFS Curve)
            if (isMtk || isQcom) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Mode Daya Silikon (Hardware DVFS)",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isMtk) {
                                        "Kurva voltase & agresivitas tabel frekuensi hardware (/proc/cpufreq & perf)"
                                    } else {
                                        "Agresivitas eskalasi frekuensi silikon & sched_load_boost per-kluster"
                                    },
                                    color = TextSecondary,
                                    fontSize = 9.5.sp
                                )
                            }
                        }

                        val powerModes = listOf(
                            Triple(0, "Standar", AccentCyan),
                            Triple(1, "Hemat", AccentGreen),
                            Triple(3, "Performa", AccentOrange)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BgElevated, RoundedCornerShape(8.dp))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            powerModes.forEach { (modeVal, modeLabel, activeColor) ->
                                val isSelected = schedInfo.mtkCpuPowerMode == modeVal
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) activeColor.copy(alpha = 0.16f) else Color.Transparent)
                                        .border(
                                            width = if (isSelected) 1.dp else 0.dp,
                                            color = if (isSelected) activeColor.copy(alpha = 0.55f) else Color.Transparent,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable { onMtkCpuPowerModeChange(modeVal) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$modeLabel [$modeVal]",
                                        color = if (isSelected) activeColor else TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Selector 2: Frekuensi Bus Memori & Interkoneksi (CCI + DVFSRC DRAM / Qcom Devfreq)
            if (isMtk || isQcom) {
                val activeBusProfile = schedInfo.busBandwidthProfile.lowercase().let { p ->
                    when (p) {
                        "eco", "powersave" -> "eco"
                        "balanced", "balance" -> "balanced"
                        "max", "performance", "extreme" -> "max"
                        else -> if (schedInfo.mtkCciPerfMode || schedInfo.qcomDevfreqBusBoostEnabled) "max" else "auto"
                    }
                }

                val freqUnit = if (isMtk) "MHz" else "MB/s"

                val liveBusTelemetryText = remember(
                    schedInfo.mtkCciFreqMhz,
                    schedInfo.ddrCurrentFreqMhz,
                    schedInfo.ddrAvailFreqsMhz,
                    schedInfo.mtkCciPerfMode,
                    schedInfo.qcomDevfreqBusBoostEnabled,
                    activeBusProfile,
                    isMtk
                ) {
                    if (isMtk) {
                        val cciPart = when {
                            schedInfo.mtkCciFreqMhz > 0 -> "CCI ${schedInfo.mtkCciFreqMhz} MHz"
                            schedInfo.mtkCciPerfMode -> "CCI Perf"
                            else -> "CCI Auto"
                        }
                        val ddrPart = when {
                            schedInfo.ddrCurrentFreqMhz > 0 -> "DDR ${schedInfo.ddrCurrentFreqMhz} MHz"
                            schedInfo.ddrAvailFreqsMhz.isNotEmpty() -> {
                                val targetMhz = when (activeBusProfile) {
                                    "max" -> schedInfo.ddrAvailFreqsMhz.first()
                                    "balanced" -> schedInfo.ddrAvailFreqsMhz[schedInfo.ddrAvailFreqsMhz.size / 2]
                                    "eco" -> schedInfo.ddrAvailFreqsMhz.last()
                                    else -> 0
                                }
                                if (targetMhz > 0) "DDR $targetMhz MHz" else "DDR Auto"
                            }
                            else -> ""
                        }
                        if (ddrPart.isNotEmpty()) "$cciPart • $ddrPart" else cciPart
                    } else {
                        when {
                            schedInfo.ddrCurrentFreqMhz > 0 -> "DDR-BW ${schedInfo.ddrCurrentFreqMhz} MB/s"
                            schedInfo.ddrAvailFreqsMhz.isNotEmpty() -> {
                                val targetBw = when (activeBusProfile) {
                                    "max" -> schedInfo.ddrAvailFreqsMhz.first()
                                    "balanced" -> schedInfo.ddrAvailFreqsMhz[schedInfo.ddrAvailFreqsMhz.size / 2]
                                    "eco" -> schedInfo.ddrAvailFreqsMhz.last()
                                    else -> 0
                                }
                                if (targetBw > 0) "DDR-BW $targetBw MB/s" else "LLCC-DDR Dinamis"
                            }
                            schedInfo.qcomDevfreqBusBoostEnabled -> "LLCC-DDR Puncak"
                            else -> "LLCC-DDR Dinamis"
                        }
                    }
                }

                val maxDdrMhz = schedInfo.ddrAvailFreqsMhz.firstOrNull() ?: 0
                val midDdrMhz = if (schedInfo.ddrAvailFreqsMhz.size >= 3) {
                    schedInfo.ddrAvailFreqsMhz[schedInfo.ddrAvailFreqsMhz.size / 2]
                } else 0
                val ecoDdrMhz = if (schedInfo.ddrAvailFreqsMhz.size >= 2) {
                    schedInfo.ddrAvailFreqsMhz.last()
                } else 0

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                                Text(
                                    text = "Frekuensi Bus Memori & Interkoneksi",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isMtk) {
                                        "Pita memori DRAM (DVFSRC & CM_MGR) serta interkoneksi L3 Cache CCI"
                                    } else {
                                        "Bandwidth CPU-LLCC-DDR (bw_hwmon), L3 Latency & UFS Controller"
                                    },
                                    color = TextSecondary,
                                    fontSize = 9.5.sp
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (activeBusProfile == "max") AccentOrange.copy(alpha = 0.14f) else AccentCyan.copy(alpha = 0.12f),
                                border = BorderStroke(
                                    0.8.dp,
                                    if (activeBusProfile == "max") AccentOrange.copy(alpha = 0.45f) else AccentCyan.copy(alpha = 0.38f)
                                )
                            ) {
                                Text(
                                    text = liveBusTelemetryText,
                                    color = if (activeBusProfile == "max") AccentOrange else AccentCyan,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.5.dp)
                                )
                            }
                        }

                        data class BusSegmentOption(
                            val key: String,
                            val label: String,
                            val subHint: String,
                            val color: Color
                        )

                        val busOptions = listOf(
                            BusSegmentOption("auto", "Otomatis", "Dinamis", AccentCyan),
                            BusSegmentOption("eco", "Efisien", if (ecoDdrMhz > 0) "$ecoDdrMhz $freqUnit" else "Hemat", AccentGreen),
                            BusSegmentOption("balanced", "Seimbang", if (midDdrMhz > 0) "$midDdrMhz $freqUnit" else "Stabil", AccentBlue),
                            BusSegmentOption("max", "Maksimum", if (maxDdrMhz > 0) "$maxDdrMhz $freqUnit" else "Puncak", AccentOrange)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BgElevated, RoundedCornerShape(8.dp))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            busOptions.forEach { opt ->
                                val isSelected = activeBusProfile == opt.key
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) opt.color.copy(alpha = 0.16f) else Color.Transparent)
                                        .border(
                                            width = if (isSelected) 1.dp else 0.dp,
                                            color = if (isSelected) opt.color.copy(alpha = 0.55f) else Color.Transparent,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable {
                                            onUniversalBusProfileChange(opt.key)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = opt.label,
                                            color = if (isSelected) opt.color else TextPrimary,
                                            fontSize = 10.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = opt.subHint,
                                            color = if (isSelected) opt.color.copy(alpha = 0.88f) else TextTertiary,
                                            fontSize = 8.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Switch 1: Akselerasi Respons Sentuhan (Kernel Touch Boost)
            val touchBoostChecked = schedInfo.universalTouchBoostEnabled || schedInfo.qcomTouchboostEnabled
            LynxSwitch(
                label = "Akselerasi Respons Sentuhan (Touch Boost)",
                subLabel = if (isMtk) {
                    "Lonjakan instan frekuensi CPU & sensitivitas panel saat jari menyentuh layar"
                } else {
                    "Lonjakan frekuensi CPU seketika saat jari menyentuh panel layar (Input Boost)"
                },
                checked = touchBoostChecked,
                onCheckedChange = { enabled ->
                    onUniversalTouchBoostChange(enabled)
                },
                onInfoClick = onShowSwitchInfo?.let { show ->
                    {
                        show(
                            SwitchTweakInfo(
                                id = "universal_touch_boost",
                                title = "Akselerasi Respons Sentuhan (Touch Boost)",
                                category = "Kernel Touch & Input Engine",
                                description = "Mengaktifkan akselerasi sentuhan langsung di tingkat driver kernel. Pada MediaTek mengonfigurasi PerfMgr Touch Boost (/proc/perfmgr/tchbst/user/usrtch: eas_boost=100, touch_opp=0, deboost_when_render=0) serta mengaktifkan Game Mode pada IC digitizer layar (goodix_ts/tp_wake_switch). Pada Snapdragon mengaktifkan MSM Performance Touchboost, CPU Input Boost, serta devfreq bus wake.",
                                isChecked = touchBoostChecked,
                                onCheckedChange = onUniversalTouchBoostChange,
                                onStatusText = "Touch Boost & Digitizer Game Mode Aktif",
                                onEffect = "Transisi gulir 120Hz, ketikan cepat, dan pergerakan bidikan (aim) merespons seketika tanpa jeda frekuensi.",
                                offStatusText = "Standar Adaptif OEM",
                                offEffect = "Frekuensi CPU hanya naik ketika beban aplikasi meningkat, lebih hemat daya saat membaca konten statis.",
                                gamingRecommendation = "Sangat Disarankan [ON] untuk respons bidikan instan",
                                balancedRecommendation = "[OFF] atau [ON] sesuai preferensi kelancaran UI",
                                batteryRecommendation = "[OFF] untuk efisiensi daya saat navigasi ringan"
                            )
                        )
                    }
                }
            )

            // 4. Switch 2: Pertahankan Plafon Clock Puncak (Anti-Throttling Guard)
            if (isMtk || isQcom) {
                val guardChecked = schedInfo.antiThrottlingGuardEnabled || schedInfo.ppmDlptBypassEnabled
                LynxSwitch(
                    label = "Pertahankan Plafon Clock Puncak (Anti-Throttling)",
                    subLabel = if (isMtk) {
                        "Cegah pemangkasan clock mendadak akibat arus puncak (DLPT, CFP, Imax & Low-Batt)"
                    } else {
                        "Kunci plafon msm_performance (cpu_max_freq) & tahan core_ctl kluster Big"
                    },
                    checked = guardChecked,
                    onCheckedChange = { enabled ->
                        onUniversalAntiThrottlingGuardChange(enabled)
                    },
                    onInfoClick = onShowSwitchInfo?.let { show ->
                        {
                            show(
                                SwitchTweakInfo(
                                    id = "universal_anti_throttling_guard",
                                    title = "Pertahankan Plafon Clock Puncak (Anti-Throttling)",
                                    category = "Silicon Ceiling & Power Guard",
                                    description = if (isMtk) {
                                        "Menggabungkan perlindungan plafon frekuensi silikon secara menyeluruh: menonaktifkan Dynamic Low Power Throttling (PPM_POLICY_DLPT [2]), Power Throttling baterai lemah (PPM_POLICY_PWR_THRO [3]), Force Limit [5], CPU Ceiling Frequency Protection (/proc/perfmgr/boost_ctrl/cpu_ctrl/cfp_enable), Syslimiter, dan pembatas arus puncak Imax, sekaligus mengaktifkan SYS_BOOST [9]."
                                    } else {
                                        "Mengunci batas plafon atas di /sys/module/msm_performance/parameters/cpu_max_freq ke frekuensi perangkat keras tertinggi pada seluruh core (0-7), sekaligus menahan seluruh core kluster Big tetap aktif melalui core_ctl (min_cpus = max_cpus, offline_delay_ms = 500, busy_up_thres = 0)."
                                    },
                                    isChecked = guardChecked,
                                    onCheckedChange = onUniversalAntiThrottlingGuardChange,
                                    onStatusText = "Plafon Clock Terkunci Stabil (Bypass Aktif)",
                                    onEffect = "Kluster Big dan Little mampu mempertahankan frekuensi puncak tanpa dipotong tiba-tiba saat GPU & CPU menarik arus tinggi bersamaan.",
                                    offStatusText = "Pembatas Arus & Daya OEM Aktif",
                                    offEffect = "Driver manajemen daya dapat menurunkan plafon frekuensi CPU atau mematikan core Big saat beban berfluktuasi.",
                                    gamingRecommendation = "Sangat Disarankan [ON] untuk mencegah frame drop mendadak",
                                    balancedRecommendation = "[OFF] untuk manajemen arus standar pabrik",
                                    batteryRecommendation = "[OFF] untuk menjaga konsumsi puncak baterai"
                                )
                            )
                        }
                    }
                )

                // 5. Switch 3: Sinkronisasi Thermal Policy Hardware
                LynxSwitch(
                    label = "Sinkronisasi Thermal Policy Hardware",
                    subLabel = if (isMtk) {
                        "Izinkan PPM, EARA Thermal & FPSGO memangkas clock saat suhu tinggi"
                    } else {
                        "Izinkan MSM Thermal & Core Control memangkas clock saat suhu tinggi"
                    },
                    checked = schedInfo.ppmThermalThrottlingEnabled,
                    onCheckedChange = { onPpmPolicyChange(4, it) },
                    onInfoClick = onShowSwitchInfo?.let { show ->
                        {
                            show(
                                SwitchTweakInfo(
                                    id = "ppm_thermal_throttling",
                                    title = "Sinkronisasi Thermal Policy Hardware",
                                    category = "Hardware Thermal Governor",
                                    description = if (isMtk) {
                                        "Mengontrol integrasi sensor suhu terhadap tabel frekuensi di tiga titik kernel sekaligus: PPM_POLICY_THERMAL [4], EARA Thermal AI (/sys/kernel/eara_thermal/enable), serta batas suhu CPU pada FPSGO (/sys/kernel/fpsgo/fbt/thrm_limit_cpu)."
                                    } else {
                                        "Mengontrol modul pembatas suhu kernel Qualcomm secara langsung (/sys/module/msm_thermal/parameters/enabled [Y/N], /sys/module/msm_thermal/core_control/enabled [1/0], dan /sys/module/msm_thermal/vdd_restriction/enabled)."
                                    },
                                    isChecked = schedInfo.ppmThermalThrottlingEnabled,
                                    onCheckedChange = { onPpmPolicyChange(4, it) },
                                    onStatusText = "Proteksi Termal Hardware Aktif",
                                    onEffect = "Kernel menurunkan frekuensi CPU secara bertahap saat suhu melewati ambang aman demi menjaga perangkat tetap dingin.",
                                    offStatusText = "Bypass Termal Hardware Aktif",
                                    offEffect = "Governor termal kernel tidak memangkas clock CPU berdasarkan suhu (disarankan gunakan pendingin eksternal).",
                                    gamingRecommendation = "[OFF] dengan cooler eksternal, atau [ON] tanpa cooler",
                                    balancedRecommendation = "Sangat Disarankan [ON] untuk keamanan suhu harian",
                                    batteryRecommendation = "[ON] efisiensi termal maksimal"
                                )
                            )
                        }
                    }
                )
            }
        }
    }
}

// ============================================================
//  CPU CONTROL CENTER — MASTER ARCHITECTURE & OWNERSHIP LAYER
// ============================================================

