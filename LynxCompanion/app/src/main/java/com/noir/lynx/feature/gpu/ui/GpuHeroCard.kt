package com.noir.lynx.feature.gpu.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.GpuInfo
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.theme.*

/**
 * GpuHeroCard — Zen Reassurance Hero Card for GPU & Display Subscreen.
 * Follows the gold-standard calm design language of TuningProfilesCategory.
 * Anti AI slop, essential language only, zero redundant technical clutter.
 */
@Composable
fun GpuHeroCard(
    uiState: LynxUiState,
    gpu: GpuInfo,
    modifier: Modifier = Modifier
) {
    val activeProfile = gpu.activeProfile.lowercase()
    val curHz = if (uiState.displayRefreshRate > 0) uiState.displayRefreshRate else 60

    val heroAccent = when (activeProfile) {
        "battery" -> AccentGreen
        "balanced" -> AccentCyan
        "esports" -> AccentOrange
        "extreme" -> AccentRed
        else -> AccentCyan
    }

    val animatedAccent by animateColorAsState(
        targetValue = heroAccent,
        animationSpec = tween(durationMillis = 200),
        label = "gpu_hero_accent"
    )

    val profileTitle = when (activeProfile) {
        "battery" -> "Profil Hemat Daya"
        "balanced" -> "Profil Seimbang"
        "esports" -> "Profil Esports"
        "extreme" -> "Profil Ekstrem"
        else -> "Akselerasi Grafis"
    }

    val profileSub = when (activeProfile) {
        "battery" -> "Efisiensi baterai maksimal • Layar hemat daya"
        "balanced" -> "Responsif, sejuk, dan optimal untuk harian"
        "esports" -> "Latensi rendah • Prioritas kelancaran game"
        "extreme" -> "Performa maksimal tanpa pembatasan clock"
        else -> "Siap digunakan"
    }

    val gpuTitle = when {
        gpu.activeBackendName.isNotBlank() -> gpu.activeBackendName
        gpu.subArchitecture.isNotBlank() -> gpu.subArchitecture
        gpu.platform == "adreno" -> "Qualcomm Adreno"
        gpu.platform.contains("mali") -> "MediaTek Mali"
        else -> "GPU Grafis"
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF10141F),
        border = BorderStroke(
            0.8.dp,
            Brush.verticalGradient(
                listOf(
                    animatedAccent.copy(alpha = 0.28f),
                    Color(0x15FFFFFF)
                )
            )
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── 1. Top Row: Zen Reassurance Status Bar ───────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Surface(
                            shape = CircleShape,
                            color = animatedAccent.copy(alpha = 0.18f),
                            modifier = Modifier.size(12.dp)
                        ) {}
                        Surface(
                            shape = CircleShape,
                            color = animatedAccent,
                            modifier = Modifier.size(6.dp)
                        ) {}
                    }
                    Text(
                        text = "Akselerasi Grafis & Layar",
                        color = TextSecondary.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = animatedAccent.copy(alpha = 0.12f),
                    border = BorderStroke(0.5.dp, animatedAccent.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = profileTitle,
                        color = animatedAccent,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                    )
                }
            }

            Spacer(Modifier.height(13.dp))

            // ── 2. Main Row: Icon + Title & Desc + Hz Pill ───────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = animatedAccent.copy(alpha = 0.10f),
                    border = BorderStroke(0.6.dp, animatedAccent.copy(alpha = 0.25f)),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = null,
                            tint = animatedAccent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = gpuTitle,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.2).sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = profileSub,
                        color = TextSecondary.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Refresh Rate Pill
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = animatedAccent.copy(alpha = 0.12f),
                    border = BorderStroke(0.6.dp, animatedAccent.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = "${curHz}Hz",
                        color = animatedAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                    )
                }
            }

            // ── 3. Calm Unified Micro-Bento Telemetry Grid ─────────────
            Spacer(Modifier.height(13.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0C0F17),
                border = BorderStroke(0.6.dp, Color(0x18FFFFFF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(5.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val freqText = if (gpu.curFreqMhz > 0) "${gpu.curFreqMhz} MHz" else "Otomatis"
                    val renderer = uiState.graphicsHwui.hwuiRenderer.replace("skia", "").uppercase()
                    val tempText = if (gpu.gpuTempC > 0f) String.format(java.util.Locale.US, "%.0f°C", gpu.gpuTempC) else "Aman"
                    val wattage = gpu.dynamicWattage
                    val wattText = String.format(java.util.Locale.US, "%.2f W", wattage)
                    val bottleneck = gpu.dynamicBottleneck
                    val vramMb = if (gpu.vramUsageMb > 0) "${gpu.vramUsageMb} MB" else "Dinamis"

                    // Row 1: Clock, Engine, Layar, Suhu
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GpuMetricTile(
                            label = "Clock",
                            value = freqText,
                            valueColor = animatedAccent.copy(alpha = 0.95f),
                            modifier = Modifier.weight(1f)
                        )
                        GpuMetricTile(
                            label = "Engine",
                            value = if (renderer.isNotBlank()) renderer else "DEFAULT",
                            valueColor = TextPrimary.copy(alpha = 0.9f),
                            modifier = Modifier.weight(1f)
                        )
                        GpuMetricTile(
                            label = "Layar",
                            value = "${curHz}Hz",
                            valueColor = AccentCyan.copy(alpha = 0.95f),
                            modifier = Modifier.weight(1f)
                        )
                        GpuMetricTile(
                            label = "Suhu",
                            value = tempText,
                            valueColor = if (gpu.gpuTempC >= 60f) AccentOrange else AccentGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Row 2: Daya GPU, Pipeline, VRAM, Beban
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GpuMetricTile(
                            label = "Daya",
                            value = wattText,
                            valueColor = if (wattage > 2.0f) AccentOrange else AccentCyan,
                            modifier = Modifier.weight(1f)
                        )
                        GpuMetricTile(
                            label = "Pipeline",
                            value = bottleneck,
                            valueColor = if (bottleneck == "GPU-Bound") AccentOrange else AccentGreen,
                            modifier = Modifier.weight(1f)
                        )
                        GpuMetricTile(
                            label = "VRAM",
                            value = vramMb,
                            valueColor = TextPrimary.copy(alpha = 0.9f),
                            modifier = Modifier.weight(1f)
                        )
                        GpuMetricTile(
                            label = "Beban",
                            value = "${gpu.gpuLoadPercent}%",
                            valueColor = if (gpu.gpuLoadPercent >= 80) AccentOrange else AccentCyan,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── 4. Psychological Reassurance Footer ──────────────
            Spacer(Modifier.height(9.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = AccentGreen,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "Akselerasi grafis hardware aktif dengan proteksi keamanan termal kernel.",
                    color = TextSecondary.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Micro-bento metric pill with high-contrast typography and anti-wrap bounds.
 */
@Composable
private fun GpuMetricTile(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF121622),
        border = BorderStroke(0.5.dp, Color(0x12FFFFFF)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 5.5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label.uppercase(),
                color = TextSecondary.copy(alpha = 0.6f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.3.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(1.5.dp))
            Text(
                text = value,
                color = valueColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.1).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
