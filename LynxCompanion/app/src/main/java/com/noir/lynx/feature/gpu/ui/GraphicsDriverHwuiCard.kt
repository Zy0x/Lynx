package com.noir.lynx.feature.gpu.ui

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.noir.lynx.R
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.*
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.designsystem.card.*
import com.noir.lynx.designsystem.controls.*
import com.noir.lynx.designsystem.tweak.*
import com.noir.lynx.designsystem.profile.*

@Composable
fun GraphicsDriverHwuiCard(
    graphics: GraphicsHwuiInfo,
    onSetGameDriver: (String) -> Unit,
    onSetRenderer: (String) -> Unit,
    onSetLatch: (Boolean) -> Unit,
    onSetMsaa: (Boolean) -> Unit,
    onSetOemShield: (Boolean) -> Unit,
    onSetDisableBackpressure: (Boolean) -> Unit = {},
    onSetEarlyPhase: (Boolean) -> Unit = {},
    onRefreshShaderCache: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val cardAccent = AccentPurple

    LynxCard(
        title = "Driver Grafis & HWUI Engine",
        subtitle = "Optimasi driver produksi game, backend Vulkan, dan latency render",
        icon = Icons.Default.Speed,
        accentColor = cardAccent,
        modifier = modifier
    ) {
        // 1. Updatable Game Driver
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Text("Production Game Driver (AOSP)", color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Text("Memaksa sistem Android memuat driver GPU terpisah yang dioptimalkan untuk performa game", color = TextSecondary, fontSize = 10.5.sp, modifier = Modifier.padding(bottom = 6.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Pair("default", "Bawaan Sistem"),
                    Pair("all_apps", "Game Driver (Semua App)")
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

        HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(bottom = 10.dp))

        // 2. UI Rendering Engine (HWUI) — 5-Engine Pipeline
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("UI Rendering Engine (HWUI)", color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = BgElevated,
                    border = BorderStroke(0.6.dp, BorderGlass)
                ) {
                    Text(
                        text = "5 Engine Pipeline",
                        color = cardAccent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text("Pilih pipeline compositing antarmuka sistem dan render canvas", color = TextSecondary, fontSize = 10.5.sp, modifier = Modifier.padding(bottom = 6.dp))

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
                            .defaultMinSize(minWidth = 100.dp, minHeight = 48.dp)
                            .alpha(if (isSupported) 1f else 0.45f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = title,
                                color = if (isSel) cardAccent else if (!isSupported) TextSecondary else TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                            )
                            Spacer(Modifier.height(3.dp))
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

        HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(bottom = 10.dp))

        // 3. Low-Latency SurfaceFlinger Latch
        LynxSwitch(
            label = "SurfaceFlinger Latch Unsignaled",
            subLabel = "Memangkas antrean render buffer dan memotong input lag touch hingga 1 frame (~8.3ms)",
            checked = graphics.surfaceFlingerLatchUnsignaled,
            onCheckedChange = { onSetLatch(it) }
        )

        // 3b. Bypass SurfaceFlinger Backpressure
        LynxSwitch(
            label = "Bypass SurfaceFlinger Backpressure",
            subLabel = "Cegah jeda penundaan antrian buffer grafis untuk respon input instan tanpa stutter",
            checked = graphics.surfaceFlingerDisableBackpressure,
            onCheckedChange = { onSetDisableBackpressure(it) }
        )

        // 3c. Ultra-Low Touch Latency (VSync Early Offset)
        LynxSwitch(
            label = "Ultra-Low Touch Latency (VSync Early Offset)",
            subLabel = "Memajukan dispatch buffer grafis SurfaceFlinger untuk memangkas latensi respon sentuhan",
            checked = graphics.isEarlyPhaseOffset,
            onCheckedChange = { onSetEarlyPhase(it) }
        )

        // 4. Force 4x MSAA
        LynxSwitch(
            label = "Paksa 4x Multisample Anti-Aliasing (MSAA)",
            subLabel = "Meningkatkan ketajaman tepi 3D pada game & canvas antarmuka grafis",
            checked = graphics.force4xMsaa,
            onCheckedChange = { onSetMsaa(it) }
        )

        // 5. Shader & Pipeline Cache Manager
        HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(vertical = 10.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = BgElevated,
            border = BorderStroke(1.dp, BorderGlass),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = cardAccent.copy(alpha = 0.15f),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = cardAccent,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                stringResource(R.string.shader_cache_title),
                                color = TextPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            val cacheMb = graphics.shaderCacheSizeBytes / (1024f * 1024f)
                            Text(
                                text = if (graphics.shaderCacheSizeBytes > 0) {
                                    pluralStringResource(
                                        R.plurals.shader_cache_inventory,
                                        graphics.shaderCacheCount,
                                        cacheMb,
                                        graphics.shaderCacheCount
                                    )
                                } else {
                                    stringResource(R.string.shader_cache_not_detected)
                                },
                                color = TextSecondary,
                                fontSize = 10.5.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onRefreshShaderCache,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = cardAccent.copy(alpha = 0.22f),
                            contentColor = cardAccent
                        ),
                        border = BorderStroke(1.dp, cardAccent.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1.2f).defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.shader_cache_refresh),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.shader_cache_guidance),
                    color = TextTertiary,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        // 6. OEM Throttler Shield
        Spacer(Modifier.height(8.dp))
        if (graphics.detectedOemThrottler.isNotBlank()) {
            LynxSwitch(
                label = "Shield Anti-Throttling OEM (${graphics.detectedOemThrottler})",
                subLabel = "Bekukan daemon latar belakang OEM agar tidak mencekik limit frame rate game",
                checked = graphics.isOemThrottlerDisabled,
                onCheckedChange = { onSetOemShield(it) }
            )
        } else {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = BgElevated,
                border = BorderStroke(1.dp, BorderGlass),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Verified, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                    Text("ROM Bersih: Tidak ditemukan background daemon throttling agresif vendor OEM", color = TextSecondary, fontSize = 11.sp)
                }
            }
        }
    }
}
