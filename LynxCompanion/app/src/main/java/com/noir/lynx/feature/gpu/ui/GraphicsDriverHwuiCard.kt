package com.noir.lynx.feature.gpu.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.*
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.designsystem.card.*
import com.noir.lynx.designsystem.controls.*

/**
 * Unified Graphics Driver, HWUI Compositor, and Render Pipeline Card.
 * Consolidates Updatable Game Driver, HWUI 5-Engine Backend, and Swapchain Buffering.
 */
@Composable
fun GraphicsDriverHwuiCard(
    graphics: GraphicsHwuiInfo,
    silicon: GpuSiliconInfo? = null,
    onSetGameDriver: (String) -> Unit,
    onSetRenderer: (String) -> Unit,
    onSetLatch: (Boolean) -> Unit,
    onSetMsaa: (Boolean) -> Unit,
    onSetOemShield: (Boolean) -> Unit,
    onSetDisableBackpressure: (Boolean) -> Unit = {},
    onRefreshShaderCache: () -> Unit = {},
    onSetTextureCacheSize: ((Int) -> Unit)? = null,
    onSetAsyncShaderPipeline: ((Boolean) -> Unit)? = null,
    onSetPureHwVsync: ((Boolean) -> Unit)? = null,
    wrapInCard: Boolean = false,
    modifier: Modifier = Modifier
) {
    val cardAccent = AccentPurple

    val cardContent = @Composable {
        Column(Modifier.fillMaxWidth()) {
        // ── 1. UPDATABLE GAME DRIVER ──
        Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            Text("Driver Game Khusus", color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Text("Aktifkan driver GPU terpisah untuk performa gaming yang lebih optimal", color = TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(bottom = 6.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Pair("default", "Bawaan Sistem"),
                    Pair("all_apps", "Driver Game (Semua App)")
                ).forEach { (mode, label) ->
                    val isSel = graphics.updatableGameDriver == mode
                    Surface(
                        onClick = { onSetGameDriver(mode) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSel) cardAccent.copy(alpha = 0.22f) else BgElevated,
                        border = BorderStroke(1.2.dp, if (isSel) cardAccent else BorderGlass)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                            Text(label, color = if (isSel) cardAccent else TextSecondary, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(bottom = 8.dp))

        // ── 2. UI RENDERING ENGINE (HWUI) — 5-ENGINE PIPELINE ──
        Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Mesin Render Antarmuka (HWUI)", color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = BgElevated,
                    border = BorderStroke(0.6.dp, BorderGlass)
                ) {
                    val supportedCount = listOf("auto", "skiagl", "skiavk", "skiagraphite", "angle").count { graphics.isBackendSupported(it) }
                    Text(
                        text = "$supportedCount Engine Didukung",
                        color = cardAccent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text("Pilih backend pemrosesan grafis untuk antarmuka sistem", color = TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(bottom = 6.dp))

            val backends = listOf(
                Triple("auto", "Default", "Stabil"),
                Triple("skiagl", "SkiaGL", "OpenGL ES"),
                Triple("skiavk", "SkiaVK", if (graphics.isVulkanSupported) "Vulkan" else "Tidak Didukung"),
                Triple("skiagraphite", "Graphite", if (graphics.isGraphiteSupported) "Skia Next" else "Tidak Didukung"),
                Triple("angle", "ANGLE", if (graphics.isAngleSupported) "Khronos" else "Tidak Didukung")
            )

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                backends.forEach { (backend, title, badge) ->
                    val isSupported = graphics.isBackendSupported(backend)
                    val isSel = graphics.hwuiRenderer == backend
                    Surface(
                        onClick = { if (isSupported) onSetRenderer(backend) },
                        enabled = isSupported,
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSel) cardAccent.copy(alpha = 0.22f) else if (!isSupported) BgElevated.copy(alpha = 0.35f) else BgElevated,
                        border = BorderStroke(1.2.dp, if (isSel) cardAccent else BorderGlass),
                        modifier = Modifier
                            .defaultMinSize(minWidth = 96.dp, minHeight = 46.dp)
                            .alpha(if (isSupported) 1f else 0.45f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = title,
                                color = if (isSel) cardAccent else if (!isSupported) TextSecondary else TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                            )
                            Spacer(Modifier.height(2.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isSel) cardAccent.copy(alpha = 0.25f) else BgSurfaceLowest,
                                border = BorderStroke(0.6.dp, if (isSel) cardAccent.copy(alpha = 0.5f) else BorderGlass)
                            ) {
                                Text(
                                    text = if (!isSupported) "Tidak Didukung" else badge,
                                    color = if (isSel) cardAccent else if (!isSupported) TextSecondary.copy(alpha = 0.8f) else TextSecondary,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(bottom = 8.dp))

        // ── 3. SURFACEFLINGER SWAPCHAIN BUFFERING ──
        if (graphics.isSfLatchSupported) {
            LynxSwitch(
                label = "Respon Sentuhan Cepat (Latch Unsignaled)",
                subLabel = "Memangkas jeda respon sentuhan hingga 1 frame untuk interaksi langsung",
                checked = graphics.surfaceFlingerLatchUnsignaled,
                onCheckedChange = { onSetLatch(it) }
            )
        }

        if (graphics.isSfDisableBackpressureSupported) {
            LynxSwitch(
                label = "Bypass Antrean Frame Layar",
                subLabel = "Mengurangi antrean buffer tampilan untuk respon visual langsung",
                checked = graphics.surfaceFlingerDisableBackpressure,
                onCheckedChange = { onSetDisableBackpressure(it) }
            )
        }

        // Force 4x MSAA
        LynxSwitch(
            label = "Penghalus Grafis 3D (4x MSAA)",
            subLabel = "Meningkatkan ketajaman tepi visual pada game 3D",
            checked = graphics.force4xMsaa,
            onCheckedChange = { onSetMsaa(it) }
        )

        // ── 5. MEMORY & SHADER PIPELINE DELEGATION ──
        GraphicsMemoryPipelineSection(
            graphics = graphics,
            cardAccent = cardAccent,
            onRefreshShaderCache = onRefreshShaderCache,
            onSetOemShield = onSetOemShield,
            onSetTextureCacheSize = onSetTextureCacheSize,
            onSetAsyncShaderPipeline = onSetAsyncShaderPipeline,
            onSetPureHwVsync = onSetPureHwVsync
        )
    }
    }

    if (wrapInCard) {
        LynxCard(
            title = "Pengaturan Grafis & Driver Game",
            subtitle = "Optimalisasi performa game, backend Vulkan, dan respon grafis",
            icon = Icons.Default.Speed,
            accentColor = cardAccent,
            modifier = modifier
        ) {
            cardContent()
        }
    } else {
        Box(modifier = modifier.fillMaxWidth()) {
            cardContent()
        }
    }
}
