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
 * Consolidates Updatable Game Driver, HWUI 5-Engine Backend, Swapchain Buffering,
 * SurfaceFlinger Latch & Early Phase, and VSync Bypass into a single pipeline source of truth.
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
    onSetEarlyPhase: (Boolean) -> Unit = {},
    onSetPhaseOffset: ((earlyNs: Long, earlyGlNs: Long) -> Unit)? = null,
    onSetVsyncSwapBypass: ((Boolean) -> Unit)? = null,
    onRefreshShaderCache: () -> Unit = {},
    onSetTextureCacheSize: ((Int) -> Unit)? = null,
    onSetAsyncShaderPipeline: ((Boolean) -> Unit)? = null,
    onSetPureHwVsync: ((Boolean) -> Unit)? = null,
    onSetShaderCompilerThreads: ((Int) -> Unit)? = null,
    onSetShaderBlobCache: ((Int) -> Unit)? = null,
    onSetSwapchainBufferMode: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val cardAccent = AccentPurple
    var showPhaseDialog by remember { mutableStateOf(false) }

    LynxCard(
        title = "Pengaturan Grafis & Driver Game",
        subtitle = "Optimalisasi performa game, backend Vulkan, dan respon grafis",
        icon = Icons.Default.Speed,
        accentColor = cardAccent,
        modifier = modifier
    ) {
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
                    Text(
                        text = "5 Pilihan Engine",
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
                Triple("skiagraphite", "Graphite", if (graphics.isGraphiteSupported) "Android 14+" else "Tidak Didukung"),
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

        // ── 3. SWAPCHAIN BUFFER MODE & VSYNC INTERVAL BYPASS ──
        if (onSetSwapchainBufferMode != null) {
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Text("Mode Antrean Frame Swapchain", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("Double (2 Buffers) untuk respon input kompetitif instan, Triple (3 Buffers) untuk kestabilan visual", color = TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(bottom = 6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("double" to "Double (2 Buffers)", "triple" to "Triple (3 Buffers)").forEach { (mode, label) ->
                        val isSel = graphics.swapchainBufferMode == mode
                        Surface(
                            onClick = { onSetSwapchainBufferMode(mode) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSel) cardAccent.copy(alpha = 0.22f) else BgElevated,
                            border = BorderStroke(1.2.dp, if (isSel) cardAccent else BorderGlass)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 9.dp)) {
                                Text(label, color = if (isSel) cardAccent else TextSecondary, fontSize = 10.5.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
        }

        // VSync Swap Interval Bypass (Consolidated from SurfaceFlingerPhaseCard)
        if (onSetVsyncSwapBypass != null && silicon != null) {
            LynxSwitch(
                label = "Bypass VSync Swap Interval",
                subLabel = if (silicon.isVsyncSwapBypass) "Bypass (swapinterval=0): Respon input instan tanpa jeda VSync" else "Sync (swapinterval=1): Presentasi frame terikat VSync",
                checked = silicon.isVsyncSwapBypass,
                onCheckedChange = { onSetVsyncSwapBypass(it) }
            )
        }

        // ── 4. SURFACEFLINGER LATENCY & EARLY PHASE OFFSET ──
        // Latch Unsignaled
        LynxSwitch(
            label = "Respon Sentuhan Cepat (Latch Unsignaled)",
            subLabel = "Memangkas jeda respon sentuhan hingga 1 frame untuk interaksi langsung",
            checked = graphics.surfaceFlingerLatchUnsignaled,
            onCheckedChange = { onSetLatch(it) }
        )

        // Bypass Backpressure
        LynxSwitch(
            label = "Bypass Antrean Frame Layar",
            subLabel = "Mengurangi antrean buffer tampilan untuk respon visual langsung",
            checked = graphics.surfaceFlingerDisableBackpressure,
            onCheckedChange = { onSetDisableBackpressure(it) }
        )

        // Early Phase Offset (Consolidated with Fine-Tune Dialog)
        val phaseMs = ((silicon?.sfEarlyPhaseOffsetNs ?: 0L) / 1_000_000L).toInt()
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = "Prioritas Sentuhan Instan (Early Phase)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = if (graphics.isEarlyPhaseOffset) "Render frame lebih awal sebelum VSync pulse (${if (phaseMs > 0) "${phaseMs}ms" else "Auto Offset"})" else "Sinyal pemrosesan frame sentuhan standar",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (graphics.isEarlyPhaseOffset && onSetPhaseOffset != null) {
                    Surface(
                        onClick = { showPhaseDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        color = BgElevated,
                        border = BorderStroke(1.dp, cardAccent.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = cardAccent, modifier = Modifier.size(13.dp))
                            Text(
                                text = if (phaseMs > 0) "${phaseMs}ms" else "0ms",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = cardAccent
                            )
                        }
                    }
                }

                Switch(
                    checked = graphics.isEarlyPhaseOffset,
                    onCheckedChange = { onSetEarlyPhase(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = cardAccent,
                        uncheckedThumbColor = TextTertiary,
                        uncheckedTrackColor = BgElevated
                    )
                )
            }
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
            onSetShaderBlobCache = onSetShaderBlobCache,
            onSetShaderCompilerThreads = onSetShaderCompilerThreads,
            onSetAsyncShaderPipeline = onSetAsyncShaderPipeline,
            onSetPureHwVsync = onSetPureHwVsync
        )

        // ── 6. DAMPAK & RISIKO PIPELINE ACCORDION ──
        HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(vertical = 4.dp))
        var showPipelineImpact by remember { mutableStateOf(false) }
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = BgElevated,
            border = BorderStroke(1.dp, if (showPipelineImpact) cardAccent.copy(alpha = 0.5f) else BorderGlass),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showPipelineImpact = !showPipelineImpact }
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🛡️", fontSize = 13.sp)
                        Text(
                            text = "Dampak & Analisis Risiko Pipeline",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = if (showPipelineImpact) "Tutup ▲" else "Lihat Detail ▼",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = cardAccent
                    )
                }

                if (showPipelineImpact) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BgSurfaceLowest, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("VSync Swap Interval Bypass (swapinterval=0)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("• Respon: Input instan dan zero frame latency saat gaming.", fontSize = 10.sp, color = AccentGreen)
                        Text("• Safeguard: Lynx Dynamic Safeguard otomatis menetralkannya saat aplikasi media (video/streaming) aktif untuk mencegah playback error.", fontSize = 10.sp, color = AccentOrange)
                        Spacer(Modifier.height(2.dp))
                        Text("Early Phase Offset", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("• Respon: Memajukan sinyal sentuh SurfaceFlinger sebelum VSync.", fontSize = 10.sp, color = AccentGreen)
                        Text("• Catatan: Nilai 3-7ms ideal untuk touch-first gaming tanpa resiko frame-skip.", fontSize = 10.sp, color = TextSecondary)
                    }
                }
            }
        }
    }

    if (showPhaseDialog && onSetPhaseOffset != null && silicon != null) {
        val currentMs = ((silicon.sfEarlyPhaseOffsetNs) / 1_000_000L).toInt().coerceIn(0, 15)
        GpuCustomNumberDialog(
            title = "SurfaceFlinger Early Phase Offset",
            description = "Offset waktu awal render SurfaceFlinger sebelum pulsa VSync (0 - 15ms).",
            currentValue = currentMs,
            unit = "ms",
            minVal = 0,
            maxVal = 15,
            step = 1,
            accentColor = cardAccent,
            onConfirm = { newVal ->
                val ns = newVal.toLong() * 1_000_000L
                onSetPhaseOffset(ns, ns)
                showPhaseDialog = false
            },
            onDismiss = { showPhaseDialog = false }
        )
    }
}
