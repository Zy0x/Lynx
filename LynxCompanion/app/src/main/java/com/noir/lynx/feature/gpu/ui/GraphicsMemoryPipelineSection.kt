package com.noir.lynx.feature.gpu.ui

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.noir.lynx.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.GraphicsHwuiInfo
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.designsystem.controls.LynxSwitch

/**
 * Memory, Shader Cache, and Hardware Synchronization Pipeline section.
 */
@Composable
fun GraphicsMemoryPipelineSection(
    graphics: GraphicsHwuiInfo,
    cardAccent: Color,
    onRefreshShaderCache: () -> Unit,
    onSetOemShield: (Boolean) -> Unit,
    onSetTextureCacheSize: ((Int) -> Unit)?,
    onSetShaderBlobCache: ((Int) -> Unit)?,
    onSetShaderCompilerThreads: ((Int) -> Unit)?,
    onSetAsyncShaderPipeline: ((Boolean) -> Unit)?,
    onSetPureHwVsync: ((Boolean) -> Unit)?
) {
    var showCustomTextureDialog by remember { mutableStateOf(false) }
    var showCustomShaderBlobDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── 1. SHADER & PIPELINE CACHE INVENTORY ──
        HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(vertical = 4.dp))
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

                Button(
                    onClick = onRefreshShaderCache,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = cardAccent.copy(alpha = 0.22f),
                        contentColor = cardAccent
                    ),
                    border = BorderStroke(1.dp, cardAccent.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp)
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

                Text(
                    text = stringResource(R.string.shader_cache_guidance),
                    color = TextTertiary,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        // ── 2. OEM THROTTLER SHIELD ──
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
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
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

        // ── 3. MEMORY ALLOCATION & MULTI-CORE SHADER COMPILATION ──
        if (onSetTextureCacheSize != null || onSetAsyncShaderPipeline != null || onSetPureHwVsync != null || onSetShaderCompilerThreads != null) {
            HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(vertical = 4.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
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
                Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    Text(
                        "Alokasi Memori Cache Tekstur",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Ukuran memori grafis untuk kelancaran scrolling antarmuka.",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
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
                                modifier = Modifier.defaultMinSize(minWidth = 70.dp, minHeight = 48.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
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
                                        fontSize = 8.5.sp
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
                            modifier = Modifier.defaultMinSize(minWidth = 70.dp, minHeight = 48.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
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
                                    fontSize = 8.5.sp
                                )
                            }
                        }
                    }
                }
            }

            // Disk Shader Blob Cache Size Selector
            if (onSetShaderBlobCache != null) {
                Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    Text(
                        "Kapasitas Disk Shader Blob Cache",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Penyimpanan shader terkompilasi di disk untuk mencegah micro-stutter pada game.",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
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
                                modifier = Modifier.defaultMinSize(minWidth = 70.dp, minHeight = 48.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
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
                                        fontSize = 8.5.sp
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
                            modifier = Modifier.defaultMinSize(minWidth = 70.dp, minHeight = 48.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
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
                                    fontSize = 8.5.sp
                                )
                            }
                        }
                    }
                }
            }

            // Skia HWUI Compiler Threads Selector
            if (onSetShaderCompilerThreads != null) {
                Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    Text(
                        "Kompilasi Grafis Multi-Core",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Gunakan inti CPU paralel untuk pra-kompilasi shader visual.",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
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
                                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 46.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
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
                                        fontSize = 8.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Async Shader & Pure Hardware VSync
            if (onSetAsyncShaderPipeline != null) {
                LynxSwitch(
                    label = "Pra-Pemuatan Efek Visual (Async Shader)",
                    subLabel = "Siapkan shader di latar belakang agar rendering game tetap lancar",
                    checked = graphics.asyncShaderPipelineEnabled,
                    onCheckedChange = { onSetAsyncShaderPipeline(it) }
                )
            }

            if (onSetPureHwVsync != null) {
                LynxSwitch(
                    label = "Akselerasi VSync Hardware Layar",
                    subLabel = "Sinkronisasi animasi langsung ke panel fisik layar untuk mencegah tearing",
                    checked = graphics.pureHwVsyncEnabled,
                    onCheckedChange = { onSetPureHwVsync(it) }
                )
            }
        }
    }

    if (showCustomTextureDialog && onSetTextureCacheSize != null) {
        GpuCustomNumberDialog(
            title = "Alokasi Cache Tekstur",
            description = "Atur ukuran memori cache grafis untuk rendering antarmuka.",
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
            description = "Menyimpan shader terkompilasi di disk untuk mencegah freeze game.",
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
