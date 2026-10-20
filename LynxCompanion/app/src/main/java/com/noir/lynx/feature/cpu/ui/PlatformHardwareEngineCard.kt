package com.noir.lynx.feature.cpu.ui

import java.util.Locale
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.*
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.designsystem.controls.*
import com.noir.lynx.designsystem.tweak.SwitchTweakInfo
import com.noir.lynx.ui.*

/**
 * PlatformHardwareEngineCard — Unified Expandable Advanced Hardware Settings Card.
 * Matches the exact dropdown design of CompactClusterRow for visual unity and high precision.
 */
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
    onOpenDeveloperVerification: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var isExpanded by remember { mutableStateOf(false) }

    val isQcom = schedInfo.isQcomBoostSupported || schedInfo.isQcomDevfreqBusSupported
    val isMtk = (schedInfo.isPpmSupported || schedInfo.isMtkCciSupported) && !isQcom

    val platformLabel = when {
        isQcom -> "Snapdragon HAL"
        isMtk -> "MediaTek HAL"
        else -> "Silicon Engine"
    }

    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "platformEngineChevron"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF10141F),
        border = BorderStroke(
            0.8.dp,
            if (isExpanded) {
                Brush.verticalGradient(
                    listOf(
                        AccentCyan.copy(alpha = 0.35f),
                        Color(0x15FFFFFF)
                    )
                )
            } else {
                Brush.verticalGradient(
                    listOf(
                        Color(0x28FFFFFF),
                        Color(0x10FFFFFF)
                    )
                )
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // ── 1. Header (Clickable to Toggle Expansion) ─────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isExpanded = !isExpanded
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AccentCyan.copy(alpha = 0.12f),
                        border = BorderStroke(0.6.dp, AccentCyan.copy(alpha = 0.25f)),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Pengaturan Lanjutan",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = Color(0xFF141824),
                                border = BorderStroke(0.5.dp, Color(0x18FFFFFF))
                            ) {
                                Text(
                                    text = platformLabel,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AccentCyan,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                )
                            }
                        }
                        Text(
                            text = if (isExpanded) "Platform hardware engine & verifikasi pengembang" else "Ketuk untuk membuka kontrol hardware terperinci",
                            fontSize = 10.sp,
                            color = TextSecondary.copy(alpha = 0.75f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = if (isExpanded) AccentCyan else TextSecondary.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(rotationAngle)
                )
            }

            // ── 2. Expanded Content (Nested seamlessly inside the card) ─
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = Color(0x15FFFFFF), thickness = 0.8.dp)

                    // ── Sub-section 1: Silicon Power Mode (Hardware DVFS Curve) ──
                    if (isMtk || isQcom) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF0C0F17),
                            border = BorderStroke(0.6.dp, Color(0x15FFFFFF)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = when {
                                                isMtk -> "Mode Daya Silikon (Hardware DVFS)"
                                                isQcom -> "Mode Daya Silikon (Hardware Devfreq)"
                                                else -> "Mode Daya Silikon"
                                            },
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
                                            color = TextSecondary.copy(alpha = 0.75f),
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
                                        .background(Color(0xFF141824), RoundedCornerShape(8.dp))
                                        .padding(3.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    for ((modeVal, modeLabel, activeColor) in powerModes) {
                                        val isSelected = schedInfo.mtkCpuPowerMode == modeVal
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(38.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isSelected) activeColor.copy(alpha = 0.16f) else Color.Transparent)
                                                .border(
                                                    width = if (isSelected) 0.8.dp else 0.dp,
                                                    color = if (isSelected) activeColor.copy(alpha = 0.55f) else Color.Transparent,
                                                    shape = RoundedCornerShape(6.dp)
                                                )
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    onMtkCpuPowerModeChange(modeVal)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "$modeLabel [$modeVal]",
                                                color = if (isSelected) activeColor else TextSecondary.copy(alpha = 0.85f),
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── Sub-section 2: Memory Bus & Interconnect (CCI + DRAM / Devfreq) ──
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
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF0C0F17),
                            border = BorderStroke(0.6.dp, Color(0x15FFFFFF)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
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
                                                "Pita memori DRAM (DVFSRC) serta interkoneksi L3 Cache CCI"
                                            } else {
                                                "Bandwidth CPU-LLCC-DDR (bw_hwmon), L3 Latency & UFS Controller"
                                            },
                                            color = TextSecondary.copy(alpha = 0.75f),
                                            fontSize = 9.5.sp
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (activeBusProfile == "max") AccentOrange.copy(alpha = 0.14f) else AccentCyan.copy(alpha = 0.12f),
                                        border = BorderStroke(
                                            0.6.dp,
                                            if (activeBusProfile == "max") AccentOrange.copy(alpha = 0.45f) else AccentCyan.copy(alpha = 0.38f)
                                        )
                                    ) {
                                        Text(
                                            text = liveBusTelemetryText,
                                            color = if (activeBusProfile == "max") AccentOrange else AccentCyan,
                                            fontSize = 9.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
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
                                        .background(Color(0xFF141824), RoundedCornerShape(8.dp))
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
                                                    width = if (isSelected) 0.8.dp else 0.dp,
                                                    color = if (isSelected) opt.color.copy(alpha = 0.55f) else Color.Transparent,
                                                    shape = RoundedCornerShape(6.dp)
                                                )
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                                    maxLines = 1
                                                )
                                                Text(
                                                    text = opt.subHint,
                                                    color = if (isSelected) opt.color.copy(alpha = 0.88f) else TextSecondary.copy(alpha = 0.5f),
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

                    // ── Sub-section 3: Switch Toggles (Touch, Anti-Throttling, Thermal) ──
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
                        onInfoClick = if (onShowSwitchInfo != null) {
                            {
                                onShowSwitchInfo(
                                    SwitchTweakInfo(
                                        id = "universal_touch_boost",
                                        title = "Akselerasi Respons Sentuhan (Touch Boost)",
                                        category = "Kernel Touch & Input Engine",
                                        description = "Mengaktifkan akselerasi sentuhan langsung di tingkat driver kernel. Pada MediaTek mengonfigurasi PerfMgr Touch Boost serta mengaktifkan Game Mode pada IC digitizer layar. Pada Snapdragon mengaktifkan MSM Performance Touchboost, CPU Input Boost, serta devfreq bus wake.",
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
                        } else null
                    )

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
                            onInfoClick = if (onShowSwitchInfo != null) {
                                {
                                    onShowSwitchInfo(
                                        SwitchTweakInfo(
                                            id = "universal_anti_throttling_guard",
                                            title = "Pertahankan Plafon Clock Puncak (Anti-Throttling)",
                                            category = "Silicon Ceiling & Power Guard",
                                            description = if (isMtk) {
                                                "Menggabungkan perlindungan plafon frekuensi silikon secara menyeluruh: menonaktifkan Dynamic Low Power Throttling (PPM_POLICY_DLPT), Power Throttling baterai lemah, Force Limit, CPU Ceiling Frequency Protection, Syslimiter, dan pembatas arus puncak Imax, sekaligus mengaktifkan SYS_BOOST."
                                            } else {
                                                "Mengunci batas plafon atas di /sys/module/msm_performance/parameters/cpu_max_freq ke frekuensi tertinggi pada seluruh core, sekaligus menahan seluruh core kluster Big tetap aktif melalui core_ctl."
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
                            } else null
                        )

                        LynxSwitch(
                            label = "Sinkronisasi Thermal Policy Hardware",
                            subLabel = if (isMtk) {
                                "Izinkan PPM, EARA Thermal & FPSGO memangkas clock saat suhu tinggi"
                            } else {
                                "Izinkan MSM Thermal & Core Control memangkas clock saat suhu tinggi"
                            },
                            checked = schedInfo.ppmThermalThrottlingEnabled,
                            onCheckedChange = { enabled -> onPpmPolicyChange(4, enabled) },
                            onInfoClick = if (onShowSwitchInfo != null) {
                                {
                                    onShowSwitchInfo(
                                        SwitchTweakInfo(
                                            id = "ppm_thermal_throttling",
                                            title = "Sinkronisasi Thermal Policy Hardware",
                                            category = "Hardware Thermal Governor",
                                            description = if (isMtk) {
                                                "Mengontrol integrasi sensor suhu terhadap tabel frekuensi di tiga titik kernel: PPM_POLICY_THERMAL, EARA Thermal AI, serta batas suhu CPU pada FPSGO."
                                            } else {
                                                "Mengontrol modul pembatas suhu kernel Qualcomm secara langsung (/sys/module/msm_thermal)."
                                            },
                                            isChecked = schedInfo.ppmThermalThrottlingEnabled,
                                            onCheckedChange = { enabled -> onPpmPolicyChange(4, enabled) },
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
                            } else null
                        )
                    }

                    // ── Sub-section 4: Integrated Developer Verification Tile ─
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenDeveloperVerification?.invoke()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0C0F17),
                        border = BorderStroke(0.6.dp, Color(0x15FFFFFF)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = AccentCyan.copy(alpha = 0.12f),
                                    border = BorderStroke(0.5.dp, AccentCyan.copy(alpha = 0.25f)),
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Terminal,
                                            contentDescription = null,
                                            tint = AccentCyan,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Verifikasi Pengembang (Sysfs)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Status keaslian node kernel & perizinan hardware",
                                        fontSize = 9.5.sp,
                                        color = TextSecondary.copy(alpha = 0.75f)
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = TextSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
