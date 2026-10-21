package com.noir.lynx.feature.gpu.ui

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt
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
fun DisplayRefreshRateTouchCard(
    currentHz: Int,
    isAuto: Boolean,
    supportedRates: List<Int>,
    touchBoost: Boolean,
    touchIrqSteering: Boolean = false,
    dcDimmingSupported: Boolean,
    dcDimmingEnabled: Boolean,
    isEsportsDownscaleActive: Boolean = false,
    nativeResolution: String = "",
    activeResolution: String = "",
    onSetRefreshRate: (hz: Int, isAuto: Boolean) -> Unit,
    onSetTouchBoost: (Boolean) -> Unit,
    onSetTouchIrqSteering: ((Boolean) -> Unit)? = null,
    onSetDcDimming: (Boolean) -> Unit,
    onSetRenderScale: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val cardAccent = AccentCyan
    val maxHz = supportedRates.maxOrNull() ?: 120

    val context = LocalContext.current
    val displayManager = remember { context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager }
    var liveHz by remember { mutableIntStateOf(if (currentHz > 0) currentHz else 60) }

    LaunchedEffect(currentHz, isAuto) {
        if (!isAuto && currentHz > 0) {
            liveHz = currentHz
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
        title = "Display Refresh Rate & Touch",
        subtitle = "Kecepatan refresh layar dinamis, sentuhan touchboost, dan panel",
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
                    if (isAuto) "Mode Auto Dinamis: Panel menyesuaikan clock instan saat layar disentuh"
                    else "Panel dikunci tetap pada frekuensi stabil",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
            // Minimalist Digital Real-Time Metric (Non-Badge)
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

        // Dynamic Refresh Rate Chip Selector: Auto + Fixed Rates
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Auto Chip
            Surface(
                onClick = { onSetRefreshRate(maxHz, true) },
                shape = RoundedCornerShape(10.dp),
                color = if (isAuto) cardAccent.copy(alpha = 0.22f) else BgElevated,
                border = BorderStroke(1.5.dp, if (isAuto) cardAccent else BorderGlass),
                modifier = Modifier.defaultMinSize(minWidth = 72.dp, minHeight = 48.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text("Auto", color = if (isAuto) cardAccent else TextPrimary, fontWeight = if (isAuto) FontWeight.Bold else FontWeight.Medium, fontSize = 12.sp)
                    Text("0 - ${maxHz}Hz", color = if (isAuto) cardAccent.copy(alpha = 0.8f) else TextSecondary, fontSize = 9.5.sp)
                }
            }

            // Fixed Supported Rates
            supportedRates.forEach { hz ->
                val isSelected = (!isAuto && currentHz == hz)
                Surface(
                    onClick = { onSetRefreshRate(hz, false) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) cardAccent.copy(alpha = 0.22f) else BgElevated,
                    border = BorderStroke(1.5.dp, if (isSelected) cardAccent else BorderGlass),
                    modifier = Modifier.defaultMinSize(minWidth = 64.dp, minHeight = 48.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text("${hz}Hz", color = if (isSelected) cardAccent else TextPrimary, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, fontSize = 12.sp)
                        Text(
                            when (hz) {
                                60 -> "Hemat"
                                90 -> "Halus"
                                120 -> "Gaming"
                                144, 165 -> "Ultra"
                                240 -> "E-Sport"
                                else -> "Tersedia"
                            },
                            color = if (isSelected) cardAccent.copy(alpha = 0.8f) else TextSecondary,
                            fontSize = 9.5.sp
                        )
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
                        "Panel Fisik Terverifikasi: ${supportedRates.firstOrNull() ?: 60}Hz Native (Mode Tunggal HWC)",
                        color = TextSecondary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // TouchBoost Switch
        LynxSwitch(
            label = "TouchBoost & Responsivitas Sentuh",
            subLabel = "Prioritaskan sampling rate sentuhan 240Hz+ pada driver input",
            checked = touchBoost,
            onCheckedChange = { onSetTouchBoost(it) }
        )

        // Hardware IRQ Touch Steering
        if (onSetTouchIrqSteering != null) {
            LynxSwitch(
                label = "Hardware IRQ Touch Steering (Bebaskan CPU0)",
                subLabel = "Arahkan interrupt digitizer sentuhan ke CPU1 untuk menghilangkan micro-stutter ksoftirqd",
                checked = touchIrqSteering,
                onCheckedChange = { onSetTouchIrqSteering(it) }
            )
        }

        // DC Dimming Switch if supported
        if (dcDimmingSupported) {
            LynxSwitch(
                label = "Anti-Flicker DC Dimming (OLED)",
                subLabel = "Mengurangi kedipan layar PWM pada tingkat kecerahan rendah demi kesehatan mata",
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
                        "ESPORTS RENDER SCALER",
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
                "Turunkan resolusi render untuk mendongkrak FPS game berat. Aspek rasio dipertahankan otomatis tanpa distorsi teks.",
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
                            "Native: ${nativeResolution}  →  Aktif: ${activeResolution.ifEmpty { nativeResolution }}",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            val scalePresets = listOf(
                Triple("native", "Native", "Resolusi Penuh"),
                Triple("900p_balanced", "900p", "Balanced +20% FPS"),
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
                        "Resolusi otomatis dikembalikan ke Native saat reboot. Gunakan tombol Native untuk reset manual kapan saja.",
                        color = TextSecondary,
                        fontSize = 9.5.sp,
                        lineHeight = 13.sp
                    )
                }
            }
        }
    }
}
