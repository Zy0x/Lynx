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
fun DisplayTelemetryCard(
    caps: GraphicsCapabilities,
    currentHz: Int = 0,
    modifier: Modifier = Modifier
) {
    val cardAccent = AccentBlue

    LynxCard(
        title = "Panel Layar & Engine Visual",
        subtitle = "Karakteristik fisik panel, resolusi, dan gamut warna",
        icon = Icons.Default.Smartphone,
        accentColor = cardAccent,
        modifier = modifier
    ) {
        // Native Resolution & Supported Refresh Rates
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = BgElevated,
            border = BorderStroke(0.8.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "RESOLUSI FISIK PANEL",
                        color = TextSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )
                    Spacer(Modifier.height(3.dp))
                    val resText = caps.displayModes.firstOrNull()?.let { "${it.width} × ${it.height}" } ?: "1080 × 2460"
                    Text(
                        text = "$resText Piksel",
                        color = AccentBlue,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        softWrap = true
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        val supportedFrequencies = if (caps.displayModes.isNotEmpty()) {
                            caps.displayModes.map { "${it.fps.toInt()}Hz" }.distinct()
                        } else listOf("60Hz", "90Hz", "120Hz")

                        val maxHz = caps.displayModes.maxOfOrNull { it.fps.toInt() } ?: 120

                        supportedFrequencies.forEach { hz ->
                            val hzInt = hz.removeSuffix("Hz").toIntOrNull() ?: 60
                            val isCurrent = (currentHz > 0 && currentHz == hzInt) || (currentHz == 0 && hz == "${maxHz}Hz")
                            Surface(
                                shape = RoundedCornerShape(7.dp),
                                color = if (isCurrent) AccentCyan.copy(alpha = 0.22f) else BgSurfaceLowest,
                                border = BorderStroke(1.dp, if (isCurrent) AccentCyan else BorderGlass),
                                modifier = Modifier.defaultMinSize(minHeight = 32.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = if (isCurrent) "$hz (Aktif)" else hz,
                                        color = if (isCurrent) AccentCyan else TextSecondary,
                                        fontSize = 10.5.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Mode Refresh Rate Panel (Kontrol di Tab Tuning)",
                        color = TextTertiary,
                        fontSize = 9.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // 2x2 Display Spec Tiles with IntrinsicSize.Min for matching height
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InfoSpecTile(
                label = "KEPADATAN PIKSEL",
                value = "${caps.displayDpi} DPI",
                sub = String.format(Locale.US, "Skala Kepadatan %.2fx (xxhdpi)", caps.displayDensity),
                accentColor = TextPrimary,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            InfoSpecTile(
                label = "GAMUT & RUANG WARNA",
                value = caps.displayColorMode,
                sub = if (caps.wideColor) "DCI-P3 Wide Gamut (10-bit)" else "Profil Warna Terkalibrasi",
                accentColor = if (caps.wideColor) AccentGreen else AccentCyan,
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
            val hdrLabel = if (caps.hdrTypes.isNotEmpty()) caps.hdrTypes.joinToString(", ") else "SDR (Standar)"
            InfoSpecTile(
                label = "RENTANG DINAMIS (HDR)",
                value = hdrLabel,
                sub = caps.maxLuminanceNits?.let { "Kecerahan Puncak ${it.toInt()} Nits" } ?: "Rentang Kontras Standar",
                accentColor = if (caps.hdrTypes.isNotEmpty()) AccentGreen else TextSecondary,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            InfoSpecTile(
                label = "HARDWARE COMPOSER",
                value = if (caps.surfaceFlingerHwc.isNotBlank()) caps.surfaceFlingerHwc else "HWC 2.x Direct",
                sub = "Komposisi Layar Hardware",
                accentColor = AccentBlue,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }

        // Hardware Panel Feature Row (clean, informative)
        val activeFeatures = buildList {
            if (caps.dcDimmingNode != null) add("DC Dimming")
            if (caps.hbmNode != null) add("HBM Booster")
            if (caps.hasKcal) add("Kernel KCAL")
        }
        if (activeFeatures.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(0.6.dp, AccentGreen.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Fitur Kernel Aktif: ${activeFeatures.joinToString(" • ")}",
                        color = AccentGreen,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        softWrap = true
                    )
                }
            }
        }
    }
}

// ── 3. SURFACEFLINGER & COMPOSITOR PIPELINE CARD ──
@Composable
fun CompositorPipelineCard(
    pipeline: DisplayPipelineInfo,
    modifier: Modifier = Modifier
) {
    val cardAccent = AccentGreen
    val hwcPct = ((1f - pipeline.clientCompositionRatio) * 100f).coerceIn(0f, 100f)
    val clientPct = (pipeline.clientCompositionRatio * 100f).coerceIn(0f, 100f)

    LynxCard(
        title = "Pipeline Komposisi Grafis",
        subtitle = "Distribusi SurfaceFlinger dan HWUI Canvas",
        icon = Icons.Default.Layers,
        accentColor = cardAccent,
        modifier = modifier
    ) {
        // Visual Composition Segmented Bar
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = BgElevated,
            border = BorderStroke(0.8.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DISTRIBUSI KOMPOSISI BUFFER",
                        color = TextSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f%% Direct HWC", hwcPct),
                        color = AccentGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Segmented Progress Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(BgSurfaceLowest)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(hwcPct.coerceAtLeast(1f))
                            .background(AccentGreen)
                    )
                    if (clientPct > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(clientPct.coerceAtLeast(1f))
                                .background(AccentOrange)
                        )
                    }
                }

                Spacer(Modifier.height(7.dp))

                Text(
                    text = if (clientPct == 0f) {
                        "Seluruh frame dikomposisikan langsung oleh Hardware Composer tanpa membebani GPU."
                    } else {
                        "Sebagian buffer dikomposisikan via GPU Client Fallback (${String.format(Locale.US, "%.1f%%", clientPct)})."
                    },
                    color = TextTertiary,
                    fontSize = 10.sp,
                    lineHeight = 14.5.sp,
                    softWrap = true
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // HWUI & Frame Stability 2-Tile Grid
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InfoSpecTile(
                label = "RENDERER KANVAS HWUI",
                value = pipeline.skiaPipeline,
                sub = if (pipeline.isVulkanCompositor) "Vulkan Backend Aktif" else "OpenGL ES Backend Aktif",
                accentColor = AccentCyan,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            InfoSpecTile(
                label = "KESEHATAN BUFFER FRAME",
                value = if (pipeline.missedFrames == 0L) "Optimal (0 Terlewat)" else "${pipeline.missedFrames} Terlewat",
                sub = "Sinkronisasi Hardware VSYNC",
                accentColor = if (pipeline.missedFrames == 0L) AccentGreen else AccentRed,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

// ── 4. KERNEL NODES ACCORDION CARD ──
@Composable
fun KernelNodesInspectorCard(
    caps: GraphicsCapabilities,
    modifier: Modifier = Modifier
) {
    val cardAccent = AccentOrange
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }

    val nodes = caps.nodes

    LynxCard(
        title = "Node Kernel GPU & Sysfs",
        subtitle = "${nodes.size} node kernel terverifikasi • Akses I/O langsung",
        icon = Icons.Default.Terminal,
        accentColor = cardAccent,
        modifier = modifier
    ) {
        Surface(
            onClick = { isExpanded = !isExpanded },
            shape = RoundedCornerShape(10.dp),
            color = BgElevated,
            border = BorderStroke(0.8.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isExpanded) "Sembunyikan Daftar Node Fisik" else "Daftar Node Fisik Driver (${nodes.size} Node)",
                        color = TextPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        softWrap = true
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = localizedUiText("Toggle"),
                    tint = cardAccent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                nodes.forEach { node ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgElevated,
                        border = BorderStroke(0.6.dp, BorderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                clipboard.setText(AnnotatedString(node.path))
                                Toast.makeText(context, AppLocale.getString(context, R.string.toast_path_copied, node.path), Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = node.label,
                                    color = TextPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    softWrap = true
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = node.path,
                                    color = TextTertiary,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 13.sp,
                                    softWrap = true
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = if (node.writable) AccentGreenDim else AccentCyanDim,
                                border = BorderStroke(0.6.dp, if (node.writable) AccentGreen.copy(alpha = 0.5f) else AccentCyan.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = if (node.writable) "R/W" else "RO",
                                    color = if (node.writable) AccentGreen else AccentCyan,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── REUSABLE HELPER TILES ──
@Composable
fun InfoSpecTile(
    label: String,
    value: String,
    sub: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BgElevated,
        border = BorderStroke(0.8.dp, BorderSubtle),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = label,
                    color = TextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    lineHeight = 12.sp,
                    softWrap = true
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = value,
                    color = accentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 17.sp,
                    softWrap = true
                )
            }
            if (sub.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = sub,
                    color = TextTertiary,
                    fontSize = 9.5.sp,
                    lineHeight = 13.5.sp,
                    softWrap = true
                )
            }
        }
    }
}


// Backwards-compatible aliases
@Composable
fun CapabilityScannerCard(caps: GraphicsCapabilities, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    GpuSiliconIdentityCard(caps = caps, onRefresh = onRefresh, modifier = modifier)
}

@Composable
fun GraphicsDebugCard(caps: GraphicsCapabilities, pipeline: DisplayPipelineInfo, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    CompositorPipelineCard(pipeline = pipeline, modifier = modifier)
}

@Composable
fun NodeExplorerCard(caps: GraphicsCapabilities, modifier: Modifier = Modifier) {
    KernelNodesInspectorCard(caps = caps, modifier = modifier)
}

// ============================================================
//  PER-APP GRAPHICS & GAME DRIVER HUB CARD
// ============================================================

