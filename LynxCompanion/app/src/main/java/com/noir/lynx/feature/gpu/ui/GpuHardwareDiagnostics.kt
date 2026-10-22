package com.noir.lynx.feature.gpu.ui

import com.noir.lynx.core.locale.AppLocale

import com.noir.lynx.R

import com.noir.lynx.core.locale.localizedUiText

import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.designsystem.card.*
import com.noir.lynx.designsystem.controls.*
import com.noir.lynx.designsystem.tweak.*
import com.noir.lynx.designsystem.profile.*

import java.util.Locale
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.designsystem.controls.SafeSlider as Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.*
import com.noir.lynx.display.ColorMatrixEngine
import com.noir.lynx.display.ColorMatrixProfile
import com.noir.lynx.display.DisplayPipelineInfo
import com.noir.lynx.hardware.DisplayModeInfo
import com.noir.lynx.hardware.GpuBackend
import com.noir.lynx.hardware.GraphicsCapabilities
import com.noir.lynx.hardware.NodeStatus
import com.noir.lynx.lab.DropCorrelator
import com.noir.lynx.lab.DropEvent
import com.noir.lynx.lab.FrameSessionReport
import java.text.SimpleDateFormat
import java.util.*
import com.noir.lynx.ui.*

@Composable
fun GpuHardwareInfoDashboard(
    caps: GraphicsCapabilities,
    pipeline: DisplayPipelineInfo,
    gpuInfo: GpuInfo = GpuInfo(),
    currentHz: Int = 0,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GpuSiliconIdentityCard(
            caps = caps,
            onRefresh = onRefresh
        )

        // 1b. Kernel OPP Frequency & Power Level Table
        GpuOppTableCard(
            gpuInfo = gpuInfo
        )

        DisplayTelemetryCard(
            caps = caps,
            currentHz = currentHz
        )

        CompositorPipelineCard(
            pipeline = pipeline
        )

        KernelNodesInspectorCard(
            caps = caps
        )
    }
}

// ── 1. SILICON IDENTITY & ANTI-SPOOFING CARD ──
@Composable
fun GpuSiliconIdentityCard(
    caps: GraphicsCapabilities,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardAccent = AccentCyan
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    LynxCard(
        title = "Identitas Silikon & Driver",
        subtitle = "Verifikasi ground truth kernel dan subsistem grafis",
        icon = Icons.Default.Memory,
        accentColor = cardAccent,
        action = {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier.size(34.dp)
            ) {
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = localizedUiText("Segarkan"),
                        tint = cardAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        modifier = modifier
    ) {
        // Anti-Spoofing Security & Ground Truth Status
        if (caps.isSpoofed) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AccentRed.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = localizedUiText("Spoofer Terdeteksi"),
                            tint = AccentRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Manipulasi Identitas SoC / GPU Terdeteksi",
                            color = AccentRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            softWrap = true
                        )
                    }
                    Text(
                        text = "Modul eksternal memalsukan getprop userspace. Lynx secara otomatis mengisolasi driver kernel fisik demi menjaga kestabilan dan keamanan hardware.",
                        color = TextSecondary,
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp,
                        softWrap = true,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BgSurfaceLowest,
                        border = BorderStroke(0.6.dp, BorderGlass),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Kernel Asli (Fisik)", color = TextSecondary, fontSize = 10.5.sp)
                                Text(caps.groundTruthSoc, color = AccentGreen, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("GPU Driver Asli", color = TextSecondary, fontSize = 10.5.sp)
                                Text(caps.groundTruthGpu, color = AccentGreen, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                            HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(vertical = 2.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Identitas Tiruan (Userspace)", color = TextSecondary, fontSize = 10.5.sp)
                                Text(caps.spoofedSoc ?: "Qualcomm / Generic", color = AccentOrange, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("GPU Tiruan (SurfaceFlinger)", color = TextSecondary, fontSize = 10.5.sp)
                                Text(caps.spoofedGpuModel ?: caps.gpuModel, color = AccentOrange, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, AccentGreen.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = localizedUiText("Integritas Hardware"),
                            tint = AccentGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Hardware Asli Terverifikasi",
                            color = TextPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            softWrap = true
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AccentGreen.copy(alpha = 0.15f),
                        border = BorderStroke(0.6.dp, AccentGreen.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "Driver Fisik • ${caps.backend.displayName}",
                            color = AccentGreen,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
                            softWrap = true
                        )
                    }
                }
            }
        }

        // Modern 2x2 Clean Spec Tiles with Matching Height
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InfoSpecTile(
                label = "MODEL GPU",
                value = caps.gpuModel,
                sub = "Vendor ${caps.gpuVendor}",
                accentColor = cardAccent,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            InfoSpecTile(
                label = "ARSITEKTUR SOC",
                value = caps.groundTruthSoc,
                sub = "Backend ${caps.backend.displayName}",
                accentColor = AccentBlue,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InfoSpecTile(
                label = "API OPENGL ES",
                value = caps.glesVersion,
                sub = "Driver ${caps.driverVersion}",
                accentColor = AccentGreen,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            val hasVk = !caps.vulkanVersion.isNullOrBlank()
            InfoSpecTile(
                label = "API VULKAN",
                value = if (hasVk) "Vulkan ${caps.vulkanVersion}" else "Tidak Didukung",
                sub = if (hasVk) (caps.vulkanDriverId ?: "ARM Mali Driver") else "Perangkat Hanya OpenGL ES",
                accentColor = if (hasVk) AccentPurple else TextTertiary,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }

        // Driver Binary File Path (Monospace row with copy action)
        if (caps.gpuDriverPath.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        clipboard.setText(AnnotatedString(caps.gpuDriverPath))
                        Toast.makeText(context, AppLocale.getString(context, R.string.toast_driver_path_copied), Toast.LENGTH_SHORT).show()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "BINARY DRIVER FISIK",
                            color = TextSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = caps.gpuDriverPath,
                            color = TextPrimary,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 13.5.sp,
                            softWrap = true
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = localizedUiText("Salin"),
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // Vulkan & Hardware Extensions Capability Matrix
        if (caps.vulkanFeatures.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderGlass),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "MATRIKS KAPABILITAS VULKAN & HARDWARE",
                        color = TextSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    caps.vulkanFeatures.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            row.forEach { (name, supported) ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (supported) AccentGreen.copy(alpha = 0.12f) else BgSurfaceLowest,
                                    border = BorderStroke(0.6.dp, if (supported) AccentGreen.copy(alpha = 0.35f) else BorderGlass),
                                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 28.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = name,
                                            color = if (supported) TextPrimary else TextTertiary,
                                            fontSize = 9.5.sp,
                                            fontWeight = if (supported) FontWeight.Medium else FontWeight.Normal,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = if (supported) "✓" else "—",
                                            color = if (supported) AccentGreen else TextTertiary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            if (row.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── 1b. GPU KERNEL OPP FREQUENCY & POWER LEVEL TABLE CARD ──
@Composable
fun GpuOppTableCard(
    gpuInfo: GpuInfo,
    modifier: Modifier = Modifier
) {
    val cardAccent = AccentCyan
    val freqs = remember(gpuInfo.availFreqsMhz, gpuInfo.maxFreqMhz, gpuInfo.curFreqMhz) {
        if (gpuInfo.availFreqsMhz.isNotEmpty()) {
            gpuInfo.availFreqsMhz.distinct().sortedDescending()
        } else {
            listOf(gpuInfo.maxFreqMhz, gpuInfo.curFreqMhz, gpuInfo.minFreqMhz).filter { it > 0 }.distinct().sortedDescending()
        }
    }

    if (freqs.isEmpty()) return

    val peakFreq = freqs.first()
    val minFreq = freqs.last()

    LynxCard(
        title = "Tabel Frekuensi Kernel OPP (DVFS)",
        subtitle = "Langkah clock hardware fisik GPU & pemetaan Power Level driver",
        icon = Icons.Default.Tune,
        accentColor = cardAccent,
        modifier = modifier
    ) {
        // Summary Stats Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderGlass),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Text("CLOCK AKTIF", color = TextSecondary, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (gpuInfo.curFreqMhz > 0) "${gpuInfo.curFreqMhz} MHz" else "Idle",
                        color = cardAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderGlass),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Text("LANGKAH OPP", color = TextSecondary, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${freqs.size} Level",
                        color = AccentBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderGlass),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Text("GOVERNOR", color = TextSecondary, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    Text(
                        gpuInfo.currentGovernor.ifBlank { "Default" },
                        color = AccentGreen,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }

        // OPP Steps Table
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = BgElevated,
            border = BorderStroke(0.8.dp, BorderGlass),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("LEVEL OPP / POWER", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("FREKUENSI CLOCK", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("STATUS / PERFORMA", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(vertical = 4.dp))

                freqs.forEachIndexed { index, freq ->
                    val isActive = gpuInfo.curFreqMhz == freq
                    val isPeak = freq == peakFreq
                    val isIdle = freq == minFreq
                    val isClamped = gpuInfo.maxFreqMhz in 1 until freq

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isActive) cardAccent.copy(alpha = 0.16f) else BgSurfaceLowest.copy(alpha = 0.4f),
                        border = BorderStroke(
                            if (isActive) 1.2.dp else 0.5.dp,
                            if (isActive) cardAccent else BorderGlass.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // OPP Level Index
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.width(90.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isActive) cardAccent else if (isPeak) AccentOrange else TextTertiary)
                                )
                                Text(
                                    text = if (gpuInfo.platform == "adreno") "PwrLevel $index" else "OPP $index",
                                    color = if (isActive) cardAccent else TextPrimary,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                                )
                            }

                            // Frequency MHz
                            Text(
                                text = "$freq MHz",
                                color = if (isActive) cardAccent else if (isClamped) TextTertiary else TextPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )

                            // Status Pill
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = when {
                                    isActive -> cardAccent.copy(alpha = 0.25f)
                                    isClamped -> AccentRed.copy(alpha = 0.12f)
                                    isPeak -> AccentOrange.copy(alpha = 0.15f)
                                    isIdle -> TextTertiary.copy(alpha = 0.15f)
                                    else -> BgElevated
                                },
                                border = BorderStroke(
                                    0.5.dp,
                                    when {
                                        isActive -> cardAccent
                                        isClamped -> AccentRed.copy(alpha = 0.4f)
                                        isPeak -> AccentOrange.copy(alpha = 0.4f)
                                        else -> BorderGlass
                                    }
                                )
                            ) {
                                Text(
                                    text = when {
                                        isActive -> "AKTIF"
                                        isClamped -> "Dibatasi"
                                        isPeak -> "Turbo Peak"
                                        isIdle -> "Idle Base"
                                        else -> "Tersedia"
                                    },
                                    color = when {
                                        isActive -> cardAccent
                                        isClamped -> AccentRed
                                        isPeak -> AccentOrange
                                        isIdle -> TextTertiary
                                        else -> TextSecondary
                                    },
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = BgElevated,
            border = BorderStroke(0.8.dp, BorderGlass),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = cardAccent, modifier = Modifier.size(13.dp))
                Text(
                    "Driver GPU melompat secara diskrit antar level OPP di atas. Batas frekuensi maksimum pada Tab Tuning mengunci frekuensi ke salah satu level hardware ini.",
                    color = TextSecondary,
                    fontSize = 9.5.sp,
                    lineHeight = 13.sp
                )
            }
        }
    }
}

// ── 2. DISPLAY TELEMETRY & PANEL ENGINE CARD ──
