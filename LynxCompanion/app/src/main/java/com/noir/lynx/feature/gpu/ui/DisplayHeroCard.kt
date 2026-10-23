package com.noir.lynx.feature.gpu.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Smartphone
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
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.theme.*

/**
 * DisplayHeroCard — Zen Reassurance Hero Card for Display & Panel Calibration Tab.
 * Presents key display telemetry, refresh rate modes, and calibration state.
 * Structured with the gold-standard 4x2 Micro-Bento Grid matching GpuHeroCard.
 */
@Composable
fun DisplayHeroCard(
    uiState: LynxUiState,
    modifier: Modifier = Modifier
) {
    val curHz = if (uiState.displayRefreshRate > 0) uiState.displayRefreshRate else 60
    val graphics = uiState.graphicsHwui
    val isAuto = uiState.isAutoRefreshRate
    val isCalibrationEnabled = uiState.isColorCalibrationEnabled
    val colorProfile = uiState.colorMatrixProfile
    val touchBoost = uiState.state.displayTouch.touchboost
    val touchDuration = uiState.gpuSilicon.touchBoostDurationMs
    val dcDimming = graphics.dcDimmingEnabled
    val hbm = uiState.displayCalibration.hbmEnabled
    val isKcal = uiState.displayCalibration.isKcalSupported

    val heroAccent = when {
        curHz >= 120 -> AccentCyan
        curHz >= 90 -> AccentBlue
        else -> AccentGreen
    }

    val animatedAccent by animateColorAsState(
        targetValue = heroAccent,
        animationSpec = tween(durationMillis = 200),
        label = "display_hero_accent"
    )

    val profileTitle = if (isCalibrationEnabled) {
        when (colorProfile) {
            com.noir.lynx.display.ColorMatrixProfile.ACCURATE -> "Profil Akurat (D65)"
            com.noir.lynx.display.ColorMatrixProfile.GAMING -> "Profil Gaming Vivid"
            com.noir.lynx.display.ColorMatrixProfile.CINEMA -> "Profil Sinema DCI-P3"
            com.noir.lynx.display.ColorMatrixProfile.READING -> "Profil Membaca Hangat"
            else -> colorProfile.name.ifEmpty { "Kalibrasi Aktif" }
        }
    } else "Warna Standar OEM"

    val displaySubtitle = if (isAuto) {
        "Dinamis Otomatis (60Hz – ${curHz}Hz) • Siap Sentuh"
    } else {
        "Refresh Rate Terkunci • ${curHz}Hz Konsisten"
    }

    val resolutionLabel = when {
        graphics.activeResolution.isNotBlank() -> graphics.activeResolution
        graphics.nativeResolution.isNotBlank() -> graphics.nativeResolution
        else -> "1080 × 2340 FHD+"
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
                        text = "Panel Display & Output Layar",
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
                            imageVector = Icons.Default.Smartphone,
                            contentDescription = null,
                            tint = animatedAccent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = resolutionLabel,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.2).sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = displaySubtitle,
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
                    val modeText = if (isAuto) "Otomatis" else "Terkunci"
                    val resShort = if (resolutionLabel.contains("x", ignoreCase = true) || resolutionLabel.contains("×")) {
                        resolutionLabel.substringBefore(" ").take(9)
                    } else "FHD+"
                    val colorText = if (isCalibrationEnabled) colorProfile.name.take(8) else "Standar"
                    val touchText = if (touchBoost) "${touchDuration}ms" else "Off"
                    val dcText = if (dcDimming) "Aktif" else "Off"
                    val hbmText = if (hbm) "Aktif" else "Off"
                    val kcalText = if (isKcal) "Hardware" else "Standar"

                    // Row 1: Refresh, Mode, Resolusi, Warna
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DisplayMetricTile(
                            label = "Refresh",
                            value = "${curHz}Hz",
                            valueColor = animatedAccent.copy(alpha = 0.95f),
                            modifier = Modifier.weight(1f)
                        )
                        DisplayMetricTile(
                            label = "Mode",
                            value = modeText,
                            valueColor = TextPrimary.copy(alpha = 0.9f),
                            modifier = Modifier.weight(1f)
                        )
                        DisplayMetricTile(
                            label = "Resolusi",
                            value = resShort,
                            valueColor = AccentCyan.copy(alpha = 0.95f),
                            modifier = Modifier.weight(1f)
                        )
                        DisplayMetricTile(
                            label = "Warna",
                            value = colorText,
                            valueColor = if (isCalibrationEnabled) AccentGreen else TextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Row 2: DC Dimming, TouchBoost, HBM, KCAL
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DisplayMetricTile(
                            label = "DC Dimming",
                            value = dcText,
                            valueColor = if (dcDimming) AccentGreen else TextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        DisplayMetricTile(
                            label = "TouchBoost",
                            value = touchText,
                            valueColor = if (touchBoost) AccentCyan else TextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        DisplayMetricTile(
                            label = "HBM",
                            value = hbmText,
                            valueColor = if (hbm) AccentOrange else TextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        DisplayMetricTile(
                            label = "KCAL",
                            value = kcalText,
                            valueColor = if (isKcal) AccentCyan else TextSecondary,
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
                    text = "Panel display terkalibrasi dengan proteksi refresh rate & responsivitas sentuh.",
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

@Composable
private fun DisplayMetricTile(
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
