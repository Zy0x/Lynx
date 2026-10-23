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
import androidx.compose.ui.draw.clip
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
    onSetTextureCacheSize: ((Int) -> Unit)? = null,
    onSetAsyncShaderPipeline: ((Boolean) -> Unit)? = null,
    onSetPureHwVsync: ((Boolean) -> Unit)? = null,
    onSetShaderCompilerThreads: ((Int) -> Unit)? = null,
    onSetShaderBlobCache: ((Int) -> Unit)? = null,
    onSetSwapchainBufferMode: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val cardAccent = AccentPurple
    var showCustomTextureDialog by remember { mutableStateOf(false) }
    var showCustomShaderBlobDialog by remember { mutableStateOf(false) }

    LynxCard(
        title = "Pengaturan Grafis & Driver Game",
        subtitle = "Optimalisasi performa game, backend Vulkan, dan respon grafis",
        icon = Icons.Default.Speed,
        accentColor = cardAccent,
        modifier = modifier
    ) {
        // 1. Updatable Game Driver
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Text("Driver Game Khusus", color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Text("Aktifkan driver GPU terpisah untuk performa gaming yang lebih optimal", color = TextSecondary, fontSize = 10.5.sp, modifier = Modifier.padding(bottom = 6.dp))

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

        HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(bottom = 10.dp))

        // 2. UI Rendering Engine (HWUI) — 5-Engine Pipeline
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
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
            Text("Pilih backend pemrosesan grafis untuk antarmuka sistem", color = TextSecondary, fontSize = 10.5.sp, modifier = Modifier.padding(bottom = 6.dp))

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

        // 2b. Swapchain Buffer Mode (Double vs Triple Buffering)
        if (onSetSwapchainBufferMode != null) {
            Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
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

        // 3. Low-Latency SurfaceFlinger Latch
        LynxSwitch(
            label = "Respon Sentuhan Cepat (Latch Unsignaled)",
            subLabel = "Memangkas jeda respon sentuhan hingga 1 frame untuk interaksi yang lebih instan",
            checked = graphics.surfaceFlingerLatchUnsignaled,
            onCheckedChange = { onSetLatch(it) }
        )

        // 3b. Bypass SurfaceFlinger Backpressure
        LynxSwitch(
            label = "Bypass Antrean Frame Layar",
            subLabel = "Mengurangi antrean buffer tampilan untuk respon visual langsung",
            checked = graphics.surfaceFlingerDisableBackpressure,
            onCheckedChange = { onSetDisableBackpressure(it) }
        )

        // 3c. Ultra-Low Touch Latency (VSync Early Offset)
        LynxSwitch(
            label = "Prioritas Sentuhan Instan (Early Phase)",
            subLabel = "Memajukan sinyal pemrosesan frame sentuhan pada layar",
            checked = graphics.isEarlyPhaseOffset,
            onCheckedChange = { onSetEarlyPhase(it) }
        )

        // 4. Force 4x MSAA
        LynxSwitch(
            label = "Penghalus Grafis 3D (4x MSAA)",
            subLabel = "Meningkatkan ketajaman tepi visual pada game 3D",
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
                label = "Perlindungan Throttling Vendor (${graphics.detectedOemThrottler})",
                subLabel = "Cegah pembatasan frame rate otomatis dari vendor OEM saat bermain game",
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
                    Text("Sistem Bersih: Tidak ada daemon pembatas performa vendor aktif", color = TextSecondary, fontSize = 11.sp)
                }
            }
        }

        // 7. Universal HWUI Compositor & Memory Pipeline
        if (onSetTextureCacheSize != null || onSetAsyncShaderPipeline != null || onSetPureHwVsync != null || onSetShaderCompilerThreads != null) {
            HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(vertical = 10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = androidx.compose.ui.Modifier
                            .size(width = 3.dp, height = 12.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(cardAccent)
                    )
                    Text(
                        text = "MEMORI & PIPELINE RENDER",
                        color = TextSecondary,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AccentGreen.copy(alpha = 0.12f),
                    border = BorderStroke(0.6.dp, AccentGreen.copy(alpha = 0.35f))
                ) {
                    Text(
                        "Universal Android",
                        color = AccentGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // HWUI Texture Cache Size Selector
            if (onSetTextureCacheSize != null) {
                Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Text(
                        "Alokasi Memori Cache Tekstur",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Ukuran memori grafis untuk kelancaran scrolling aplikasi. Efektif setelah membuka ulang aplikasi.",
                        color = TextSecondary,
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        graphics.textureCacheOptions.forEach { (mb, label) ->
                            val isSelected = graphics.textureCacheMb == mb
                            Surface(
                                onClick = { onSetTextureCacheSize(mb) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) cardAccent.copy(alpha = 0.2f) else BgElevated,
                                border = BorderStroke(1.2.dp, if (isSelected) cardAccent else BorderGlass),
                                modifier = Modifier.defaultMinSize(minWidth = 70.dp, minHeight = 52.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        "${mb}MB",
                                        color = if (isSelected) cardAccent else TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                    Text(
                                        label.substringAfter("(").removeSuffix(")"),
                                        color = if (isSelected) cardAccent.copy(alpha = 0.8f) else TextSecondary,
                                        fontSize = 8.5.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }

                        val isPresetTexture = graphics.textureCacheOptions.any { it.first == graphics.textureCacheMb }
                        val isCustomTexture = !isPresetTexture && graphics.textureCacheMb > 0
                        Surface(
                            onClick = { showCustomTextureDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isCustomTexture) cardAccent.copy(alpha = 0.25f) else BgElevated,
                            border = BorderStroke(1.2.dp, if (isCustomTexture) cardAccent else BorderGlass),
                            modifier = Modifier.defaultMinSize(minWidth = 70.dp, minHeight = 52.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    if (isCustomTexture) "${graphics.textureCacheMb}MB" else "✎ Kustom",
                                    color = if (isCustomTexture) cardAccent else TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isCustomTexture) FontWeight.Bold else FontWeight.Medium
                                )
                                Text(
                                    if (isCustomTexture) "Kustom" else "Bebas",
                                    color = if (isCustomTexture) cardAccent.copy(alpha = 0.8f) else TextSecondary,
                                    fontSize = 8.5.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // Disk Shader Blob Cache Size Selector
            if (onSetShaderBlobCache != null) {
                Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Text(
                        "Kapasitas Disk Shader Blob Cache",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Menyimpan kompilasi shader grafis di disk untuk mencegah freeze/stutter pada game open-world.",
                        color = TextSecondary,
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        graphics.shaderBlobCacheOptions.forEach { (mb, label) ->
                            val isSelected = graphics.shaderBlobCacheMb == mb
                            Surface(
                                onClick = { onSetShaderBlobCache(mb) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) cardAccent.copy(alpha = 0.2f) else BgElevated,
                                border = BorderStroke(1.2.dp, if (isSelected) cardAccent else BorderGlass),
                                modifier = Modifier.defaultMinSize(minWidth = 70.dp, minHeight = 52.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        "${mb}MB",
                                        color = if (isSelected) cardAccent else TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                    Text(
                                        label.substringAfter("(").removeSuffix(")"),
                                        color = if (isSelected) cardAccent.copy(alpha = 0.8f) else TextSecondary,
                                        fontSize = 8.5.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }

                        val isPresetBlob = graphics.shaderBlobCacheOptions.any { it.first == graphics.shaderBlobCacheMb }
                        val isCustomBlob = !isPresetBlob && graphics.shaderBlobCacheMb > 0
                        Surface(
                            onClick = { showCustomShaderBlobDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isCustomBlob) cardAccent.copy(alpha = 0.25f) else BgElevated,
                            border = BorderStroke(1.2.dp, if (isCustomBlob) cardAccent else BorderGlass),
                            modifier = Modifier.defaultMinSize(minWidth = 70.dp, minHeight = 52.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    if (isCustomBlob) "${graphics.shaderBlobCacheMb}MB" else "✎ Kustom",
                                    color = if (isCustomBlob) cardAccent else TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isCustomBlob) FontWeight.Bold else FontWeight.Medium
                                )
                                Text(
                                    if (isCustomBlob) "Kustom" else "Bebas",
                                    color = if (isCustomBlob) cardAccent.copy(alpha = 0.8f) else TextSecondary,
                                    fontSize = 8.5.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // Skia HWUI Compiler Threads Selector
            if (onSetShaderCompilerThreads != null) {
                Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Text(
                        "Kompilasi Grafis Multi-Core",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Gunakan inti CPU paralel untuk menyiapkan efek visual di latar belakang tanpa mengganggu jalannya game.",
                        color = TextSecondary,
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        graphics.shaderCompilerThreadOptions.forEach { (threads, label) ->
                            val isSelected = graphics.shaderCompilerThreads == threads
                            Surface(
                                onClick = { onSetShaderCompilerThreads(threads) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) cardAccent.copy(alpha = 0.2f) else BgElevated,
                                border = BorderStroke(1.2.dp, if (isSelected) cardAccent else BorderGlass),
                                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 50.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        if (threads == 0) "Auto" else "${threads} Inti",
                                        color = if (isSelected) cardAccent else TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                    Text(
                                        label.substringAfter("(").removeSuffix(")"),
                                        color = if (isSelected) cardAccent.copy(alpha = 0.8f) else TextSecondary,
                                        fontSize = 8.5.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Async Shader & GPU Pixel Buffers
            if (onSetAsyncShaderPipeline != null) {
                LynxSwitch(
                    label = "Pra-Pemuatan Efek Visual (Async Shader)",
                    subLabel = "Siapkan efek grafik baru di latar belakang agar rendering game tetap lancar",
                    checked = graphics.asyncShaderPipelineEnabled,
                    onCheckedChange = { onSetAsyncShaderPipeline(it) }
                )
            }

            // Pure Hardware VSync
            if (onSetPureHwVsync != null) {
                LynxSwitch(
                    label = "Akselerasi VSync Hardware Layar",
                    subLabel = "Sinkronisasi animasi langsung ke panel fisik layar untuk mencegah tampilan patah (tearing)",
                    checked = graphics.pureHwVsyncEnabled,
                    onCheckedChange = { onSetPureHwVsync(it) }
                )
            }
        }
    }

    if (showCustomTextureDialog && onSetTextureCacheSize != null) {
        GpuCustomNumberDialog(
            title = "Alokasi Cache Tekstur",
            description = "Atur ukuran memori cache grafis untuk rendering antarmuka dan kelancaran scrolling aplikasi.",
            currentValue = graphics.textureCacheMb.coerceIn(16, 512),
            unit = "MB",
            minVal = 16,
            maxVal = 512,
            step = 8,
            accentColor = cardAccent,
            onConfirm = { newVal -> onSetTextureCacheSize(newVal) },
            onDismiss = { showCustomTextureDialog = false }
        )
    }

    if (showCustomShaderBlobDialog && onSetShaderBlobCache != null) {
        GpuCustomNumberDialog(
            title = "Kapasitas Disk Shader Blob Cache",
            description = "Menyimpan kompilasi shader grafis di penyimpanan flash disk untuk mencegah freeze/stutter saat game open-world.",
            currentValue = graphics.shaderBlobCacheMb.coerceIn(32, 2048),
            unit = "MB",
            minVal = 32,
            maxVal = 2048,
            step = 32,
            accentColor = cardAccent,
            onConfirm = { newVal -> onSetShaderBlobCache(newVal) },
            onDismiss = { showCustomShaderBlobDialog = false }
        )
    }
}
