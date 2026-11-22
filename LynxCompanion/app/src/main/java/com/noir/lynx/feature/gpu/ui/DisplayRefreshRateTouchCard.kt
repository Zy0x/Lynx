package com.noir.lynx.feature.gpu.ui

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.*
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.designsystem.card.*
import com.noir.lynx.designsystem.controls.*
import com.noir.lynx.designsystem.tweak.*
import com.noir.lynx.designsystem.profile.*

/**
 * Kartu Refresh Rate & Output Layar.
 * Mengontrol kecepatan refresh layar, pembuka batasan FPS, DC Dimming, dan resolusi render game.
 */
@Composable
fun DisplayRefreshRateTouchCard(
    currentHz: Int,
    isAuto: Boolean,
    supportedRates: List<Int>,
    dcDimmingSupported: Boolean,
    dcDimmingEnabled: Boolean,
    isEsportsDownscaleActive: Boolean = false,
    nativeResolution: String = "",
    activeResolution: String = "",
    forcedFpsUnlockerHz: Int = 0,
    onSetRefreshRate: (hz: Int, isAuto: Boolean) -> Unit,
    onSetDcDimming: (Boolean) -> Unit,
    onSetRenderScale: ((String) -> Unit)? = null,
    onSetForcedFpsUnlocker: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val cardAccent = AccentCyan
    val maxHz = supportedRates.maxOrNull() ?: currentHz.coerceAtLeast(60)

    val context = LocalContext.current
    val displayManager = remember { context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager }
    var liveHz by remember { mutableIntStateOf(if (currentHz > 0) currentHz else 60) }

    LaunchedEffect(currentHz, isAuto) {
        if (!isAuto && currentHz > 0) {
            liveHz = currentHz
        }
        while (isActive) {
            val defaultDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display
            } else {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
            }
            val dynamicRate = defaultDisplay?.refreshRate?.roundToInt() ?: 0
            if (dynamicRate > 0 && dynamicRate != liveHz) {
                liveHz = dynamicRate
            }
            delay(1000)
        }
    }

    DisposableEffect(displayManager, currentHz) {
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
            override fun onDisplayChanged(displayId: Int) {
                val display = displayManager?.getDisplay(displayId)
                val hz = display?.refreshRate?.roundToInt() ?: 0
                if (hz > 0) {
                    liveHz = hz
                }
            }
        }
        val defaultDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
        }
        val initialHz = defaultDisplay?.refreshRate?.roundToInt() ?: currentHz
        if (initialHz > 0) liveHz = initialHz

        displayManager?.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        onDispose {
            displayManager?.unregisterDisplayListener(listener)
        }
    }

    LynxCard(
        title = "Refresh Rate & Respon Layar",
        subtitle = "Kelancaran animasi visual dan respon sentuhan panel",
        icon = Icons.Default.Smartphone,
        accentColor = cardAccent,
        modifier = modifier
    ) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Kecepatan Refresh Layar", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(
                    if (isAuto) "Mode Otomatis: Menyesuaikan sentuhan dan konten secara adaptif"
                    else "Mode Tetap: Terkunci pada frekuensi pilihan",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
            // Minimalist Digital Real-Time Metric
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "REAL-TIME",
                    color = cardAccent.copy(alpha = 0.85f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                )
                Text(
                    text = "$liveHz Hz",
                    color = cardAccent,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.2).sp
                )
            }
        }

        // Dynamic Refresh Rate Chip Selector: Auto + Fixed Rates (Non-scrollable grid to eliminate gesture conflict with tab swipe)
        val allRateItems = remember(supportedRates, maxHz) {
            listOf(null to "Auto") + supportedRates.map { it to "${it}Hz" }
        }

        val rateChunks = remember(allRateItems) {
            if (allRateItems.size <= 4) listOf(allRateItems)
            else allRateItems.chunked(3)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            rateChunks.forEach { chunk ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    chunk.forEach { (hzOpt, label) ->
                        val isAutoChip = hzOpt == null
                        val isSelected = if (isAutoChip) isAuto else (!isAuto && currentHz == hzOpt)
                        val subText = if (isAutoChip) "Adaptif" else when (hzOpt) {
                            60 -> "Hemat"
                            90 -> "Halus"
                            120 -> "Gaming"
                            144, 165 -> "Ultra"
                            240 -> "Esports"
                            else -> "Tersedia"
                        }

                        Surface(
                            onClick = {
                                if (isAutoChip) onSetRefreshRate(maxHz, true)
                                else hzOpt?.let { onSetRefreshRate(it, false) }
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) cardAccent.copy(alpha = 0.22f) else BgElevated,
                            border = BorderStroke(1.5.dp, if (isSelected) cardAccent else BorderGlass),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    label,
                                    color = if (isSelected) cardAccent else TextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                                Text(
                                    subText,
                                    color = if (isSelected) cardAccent.copy(alpha = 0.8f) else TextSecondary,
                                    fontSize = 9.5.sp
                                )
                            }
                        }
                    }
                    if (rateChunks.size > 1 && chunk.size < 3) {
                        repeat(3 - chunk.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        if (supportedRates.size <= 1) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = BgElevated,
                border = BorderStroke(1.dp, BorderGlass),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Verified, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(13.dp))
                    Text(
                        "Panel Fisik: ${supportedRates.firstOrNull() ?: 60}Hz (Frekuensi Tunggal Layar)",
                        color = TextSecondary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Force Peak FPS Unlocker (Bypass Limit Game/Aplikasi)
        if (onSetForcedFpsUnlocker != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BgElevated,
                border = BorderStroke(1.dp, if (forcedFpsUnlockerHz > 0) AccentOrange.copy(alpha = 0.5f) else BorderGlass),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = if (forcedFpsUnlockerHz > 0) AccentOrange else cardAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "BUKA LIMIT FPS GAME",
                                color = TextSecondary,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp
                            )
                        }
                        if (forcedFpsUnlockerHz > 0) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = AccentOrange.copy(alpha = 0.15f),
                                border = BorderStroke(0.6.dp, AccentOrange.copy(alpha = 0.45f))
                            ) {
                                Text(
                                    "${forcedFpsUnlockerHz} FPS AKTIF",
                                    color = AccentOrange,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "Izinkan game mendeteksi opsi frame rate tertinggi (90/120/144/240 FPS) tanpa dibatasi oleh sistem Android.",
                        color = TextSecondary,
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Force Peak FPS Unlocker Options: Top Standar Mode + 4 Balanced Tiers (Zero scroll conflict with tab swipe)
                    val isStdSelected = forcedFpsUnlockerHz == 0
                    Surface(
                        onClick = { onSetForcedFpsUnlocker(0) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isStdSelected) AccentGreen.copy(alpha = 0.18f) else BgSurfaceLowest,
                        border = BorderStroke(1.2.dp, if (isStdSelected) AccentGreen else BorderGlass),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isStdSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isStdSelected) AccentGreen else TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Column {
                                    Text(
                                        text = "Standar (Limit Sistem Android)",
                                        color = if (isStdSelected) AccentGreen else TextPrimary,
                                        fontWeight = if (isStdSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.5.sp
                                    )
                                    Text(
                                        text = "Ikuti batas frame dinamis bawaan sistem dan game",
                                        color = if (isStdSelected) AccentGreen.copy(alpha = 0.8f) else TextSecondary,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isStdSelected) AccentGreen.copy(alpha = 0.25f) else Color.Transparent,
                                border = BorderStroke(0.6.dp, if (isStdSelected) AccentGreen.copy(alpha = 0.6f) else BorderGlass)
                            ) {
                                Text(
                                    text = if (isStdSelected) "AKTIF" else "DEFAULT",
                                    color = if (isStdSelected) AccentGreen else TextSecondary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(90, 120, 144, 240).forEach { fps ->
                            val isSelected = forcedFpsUnlockerHz == fps
                            Surface(
                                onClick = { onSetForcedFpsUnlocker(fps) },
                                shape = RoundedCornerShape(9.dp),
                                color = if (isSelected) AccentOrange.copy(alpha = 0.22f) else BgSurfaceLowest,
                                border = BorderStroke(1.2.dp, if (isSelected) AccentOrange else BorderGlass),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "${fps} FPS",
                                        color = if (isSelected) AccentOrange else TextPrimary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.5.sp
                                    )
                                    Text(
                                        text = "Terbuka",
                                        color = if (isSelected) AccentOrange.copy(alpha = 0.8f) else TextSecondary,
                                        fontSize = 8.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // DC Dimming Switch if supported
        if (dcDimmingSupported) {
            HorizontalDivider(color = BorderGlass.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 6.dp))
            LynxSwitch(
                label = "Peredam Kedipan Layar (DC Dimming)",
                subLabel = "Mengurangi kedipan layar pada kecerahan rendah demi kenyamanan mata",
                checked = dcDimmingEnabled,
                onCheckedChange = { onSetDcDimming(it) }
            )
        }

        // Esports Dynamic Render Scaler
        if (onSetRenderScale != null) {
            HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(vertical = 10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
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
                        "PENURUN RESOLUSI GAME (BOOSTER FPS)",
                        color = TextSecondary,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )
                }
                if (isEsportsDownscaleActive) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AccentOrange.copy(alpha = 0.15f),
                        border = BorderStroke(0.6.dp, AccentOrange.copy(alpha = 0.45f))
                    ) {
                        Text(
                            "AKTIF • ${activeResolution}",
                            color = AccentOrange,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Text(
                "Turunkan resolusi render untuk mendongkrak kelancaran game berat secara instan tanpa merusak proporsi layar.",
                color = TextSecondary,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (nativeResolution.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, BorderGlass),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Smartphone, contentDescription = null, tint = cardAccent, modifier = Modifier.size(13.dp))
                        Text(
                            "Resolusi Asli: ${nativeResolution}  →  Aktif: ${activeResolution.ifEmpty { nativeResolution }}",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            val scalePresets = listOf(
                Triple("native", "Native", "Resolusi Penuh"),
                Triple("900p_balanced", "900p", "Seimbang +20% FPS"),
                Triple("720p_esports", "720p", "Esports +40% FPS")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                scalePresets.forEach { (preset, title, sub) ->
                    val isSelected = when (preset) {
                        "native" -> !isEsportsDownscaleActive || activeResolution == nativeResolution
                        "720p_esports" -> isEsportsDownscaleActive && activeResolution.startsWith("720")
                        "900p_balanced" -> isEsportsDownscaleActive && activeResolution.startsWith("900")
                        else -> false
                    }
                    val chipColor = when (preset) {
                        "native" -> AccentGreen
                        "900p_balanced" -> AccentCyan
                        else -> AccentOrange
                    }
                    Surface(
                        onClick = { onSetRenderScale(preset) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) chipColor.copy(alpha = 0.2f) else BgElevated,
                        border = BorderStroke(1.2.dp, if (isSelected) chipColor else BorderGlass),
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 56.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                title,
                                color = if (isSelected) chipColor else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                            )
                            Text(
                                sub,
                                color = if (isSelected) chipColor.copy(alpha = 0.8f) else TextSecondary,
                                fontSize = 8.5.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 11.sp
                            )
                        }
                    }
                }
            }

            // Safety warning
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = AccentOrange.copy(alpha = 0.07f),
                border = BorderStroke(0.8.dp, AccentOrange.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(12.dp))
                    Text(
                        "Resolusi otomatis dikembalikan ke normal saat reboot. Tekan tombol Native untuk reset kapan saja.",
                        color = TextSecondary,
                        fontSize = 9.5.sp,
                        lineHeight = 13.sp
                    )
                }
            }
        }
    }
}
