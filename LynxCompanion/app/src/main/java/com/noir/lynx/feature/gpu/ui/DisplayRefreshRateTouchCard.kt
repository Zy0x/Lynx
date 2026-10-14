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
    dcDimmingSupported: Boolean,
    dcDimmingEnabled: Boolean,
    onSetRefreshRate: (hz: Int, isAuto: Boolean) -> Unit,
    onSetTouchBoost: (Boolean) -> Unit,
    onSetDcDimming: (Boolean) -> Unit,
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
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 12.dp),
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

        // TouchBoost Switch
        LynxSwitch(
            label = "TouchBoost & Responsivitas Sentuh",
            subLabel = "Prioritaskan sampling rate sentuhan 240Hz+ pada driver input",
            checked = touchBoost,
            onCheckedChange = { onSetTouchBoost(it) }
        )

        // DC Dimming Switch if supported
        if (dcDimmingSupported) {
            LynxSwitch(
                label = "Anti-Flicker DC Dimming (OLED)",
                subLabel = "Mengurangi kedipan layar PWM pada tingkat kecerahan rendah demi kesehatan mata",
                checked = dcDimmingEnabled,
                onCheckedChange = { onSetDcDimming(it) }
            )
        }
    }
}
