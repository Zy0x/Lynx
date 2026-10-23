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

/**
 * Kartu Refresh Rate & Respon Layar.
 * Mengontrol kecepatan refresh layar, pembuka batasan FPS, respon sentuh, dan resolusi render game.
 */
@Composable
fun DisplayRefreshRateTouchCard(
    currentHz: Int,
    isAuto: Boolean,
    supportedRates: List<Int>,
    touchBoost: Boolean,
    touchBoostDurationMs: Int = 500,
    touchIrqSteering: Boolean = false,
    dcDimmingSupported: Boolean,
    dcDimmingEnabled: Boolean,
    isEsportsDownscaleActive: Boolean = false,
    nativeResolution: String = "",
    activeResolution: String = "",
    forcedFpsUnlockerHz: Int = 0,
    onSetRefreshRate: (hz: Int, isAuto: Boolean) -> Unit,
    onSetTouchBoost: (Boolean) -> Unit,
    onSetTouchBoostDuration: ((Int) -> Unit)? = null,
    onSetTouchIrqSteering: ((Boolean) -> Unit)? = null,
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
    var showTouchDurationDialog by remember { mutableStateOf(false) }

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
                    Text("Adaptif", color = if (isAuto) cardAccent.copy(alpha = 0.8f) else TextSecondary, fontSize = 9.5.sp)
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
                                240 -> "Esports"
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

                    val fpsOptions = listOf(0, 90, 120, 144, 240)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        fpsOptions.forEach { fps ->
                            val isSelected = forcedFpsUnlockerHz == fps
                            val chipAccent = if (fps == 0) AccentGreen else AccentOrange
                            Surface(
                                onClick = { onSetForcedFpsUnlocker(fps) },
                                shape = RoundedCornerShape(9.dp),
                                color = if (isSelected) chipAccent.copy(alpha = 0.22f) else BgSurfaceLowest,
                                border = BorderStroke(1.2.dp, if (isSelected) chipAccent else BorderGlass),
                                modifier = Modifier.defaultMinSize(minWidth = 64.dp, minHeight = 44.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = if (fps == 0) "Standar" else "${fps} FPS",
                                        color = if (isSelected) chipAccent else TextPrimary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.5.sp
                                    )
                                    Text(
                                        text = if (fps == 0) "Off" else "Terbuka",
                                        color = if (isSelected) chipAccent.copy(alpha = 0.8f) else TextSecondary,
                                        fontSize = 8.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // TouchBoost Switch & Duration Tuning
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = "Respon Sentuhan Cepat (TouchBoost)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = if (touchBoost) "Penguncian clock saat sentuhan aktif (${touchBoostDurationMs}ms)" else "Tingkatkan laju pemindaian sentuhan untuk kontrol game yang lebih presisi",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (touchBoost && onSetTouchBoostDuration != null) {
                    Surface(
                        onClick = { showTouchDurationDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        color = BgElevated,
                        border = BorderStroke(1.dp, cardAccent.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.TouchApp, contentDescription = null, tint = cardAccent, modifier = Modifier.size(13.dp))
                            Text(
                                text = "${touchBoostDurationMs}ms",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = cardAccent
                            )
                        }
                    }
                }

                Switch(
                    checked = touchBoost,
                    onCheckedChange = { onSetTouchBoost(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = androidx.compose.ui.graphics.Color.Black,
                        checkedTrackColor = cardAccent,
                        uncheckedThumbColor = TextTertiary,
                        uncheckedTrackColor = BgElevated
                    )
                )
            }
        }

        // Hardware IRQ Touch Steering
        if (onSetTouchIrqSteering != null) {
            LynxSwitch(
                label = "Optimasi Inti Khusus Sentuhan",
                subLabel = "Pindahkan pemrosesan sentuh ke inti CPU sekunder agar game bebas lag",
                checked = touchIrqSteering,
                onCheckedChange = { onSetTouchIrqSteering(it) }
            )
        }

        // DC Dimming Switch if supported
        if (dcDimmingSupported) {
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

    if (showTouchDurationDialog && onSetTouchBoostDuration != null) {
        GpuCustomNumberDialog(
            title = "Durasi Respon TouchBoost",
            description = "Lama waktu penguncian clock performa tinggi saat jari menyentuh layar (50 - 2000 ms).",
            currentValue = touchBoostDurationMs.coerceIn(50, 2000),
            unit = "ms",
            minVal = 50,
            maxVal = 2000,
            step = 50,
            accentColor = cardAccent,
            onConfirm = { newVal ->
                onSetTouchBoostDuration(newVal)
                showTouchDurationDialog = false
            },
            onDismiss = { showTouchDurationDialog = false }
        )
    }
}
