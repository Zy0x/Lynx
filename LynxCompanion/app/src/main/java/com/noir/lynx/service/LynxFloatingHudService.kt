package com.noir.lynx.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput

import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.noir.lynx.data.LynxRepository
import com.noir.lynx.ui.MainActivity
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * LynxFloatingHudService — Real-time floating Game HUD / OSD Overlay.
 *
 * Provides a draggable, compact or expanded cyberpunk telemetry dashboard:
 * - Real-time Render FPS (SurfaceFlinger presentation deltas / MTK FPSGO)
 * - Display Panel Hardware Refresh Rate (Hz)
 * - CPU top clock & GPU clock / load %
 * - Battery temperature & current drain (mA)
 * - Instant on-the-fly Kernel Profile switcher
 */
class LynxFloatingHudService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var floatView: View? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    private fun getDisplayRefreshRate(): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display?.mode?.refreshRate?.roundToInt() ?: 120
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay?.mode?.refreshRate?.roundToInt() ?: 120
            }
        } catch (_: Exception) {
            120
        }
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    companion object {
        var isRunning: Boolean = false
            private set
        const val ACTION_START = "com.noir.lynx.service.START_HUD"
        const val ACTION_STOP = "com.noir.lynx.service.STOP_HUD"
        private const val NOTIF_CHANNEL_ID = "lynx_game_hud_channel"
        private const val NOTIF_ID = 8801
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIF_ID, buildForegroundNotification())
        createFloatingHud()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            cleanUpAndStop()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun cleanUpAndStop() {
        isRunning = false
        serviceScope.cancel()
        floatView?.let { view ->
            try {
                windowManager.removeViewImmediate(view)
            } catch (e: Exception) {
                try {
                    windowManager.removeView(view)
                } catch (_: Exception) {}
            }
        }
        floatView = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingHud() {
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 80
            y = 200
        }

        val telemetryFlow = kotlinx.coroutines.flow.MutableStateFlow(LynxRepository.FloatingHudTelemetry())
        val isHudBenchmarkingFlow = kotlinx.coroutines.flow.MutableStateFlow(false)
        val hudBenchmarkCountdownFlow = kotlinx.coroutines.flow.MutableStateFlow(60)
        val hudBenchmarkSummaryFlow = kotlinx.coroutines.flow.MutableStateFlow<com.noir.lynx.data.LynxBenchmarkResult?>(null)

        // Start Telemetry Query Loop (500ms for ultra-smooth live HUD)
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                Shell.cmd("dumpsys SurfaceFlinger --timestats -enable 2>/dev/null").exec()
            }
            while (isActive) {
                val displayHz = getDisplayRefreshRate()
                val tel = withContext(Dispatchers.IO) {
                    LynxRepository.readFloatingHudTelemetry(displayHz)
                }
                telemetryFlow.value = tel
                delay(500L)
            }
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@LynxFloatingHudService)
            setViewTreeSavedStateRegistryOwner(this@LynxFloatingHudService)
            setContent {
                val tel by telemetryFlow.collectAsState()
                val isHudBenchmarking by isHudBenchmarkingFlow.collectAsState()
                val hudBenchmarkCountdown by hudBenchmarkCountdownFlow.collectAsState()
                val hudBenchmarkSummary by hudBenchmarkSummaryFlow.collectAsState()
                var isExpanded by remember { mutableStateOf(false) }

                FloatingHudContent(
                    telemetry = tel,
                    isExpanded = isExpanded,
                    isHudBenchmarking = isHudBenchmarking,
                    hudBenchmarkCountdown = hudBenchmarkCountdown,
                    hudBenchmarkSummary = hudBenchmarkSummary,
                    onToggleExpand = { isExpanded = !isExpanded },
                    onClose = { cleanUpAndStop() },
                    onProfileSelect = { profile ->
                        serviceScope.launch {
                            LynxRepository.setProfile(profile)
                        }
                    },
                    onStartHudBenchmark = {
                        serviceScope.launch {
                            if (isHudBenchmarkingFlow.value) return@launch
                            isHudBenchmarkingFlow.value = true
                            hudBenchmarkSummaryFlow.value = null
                            try {
                                val res = withContext(Dispatchers.IO) {
                                    LynxRepository.runHardwareBenchmark(durationSeconds = 60) { rem ->
                                        hudBenchmarkCountdownFlow.value = rem
                                    }
                                }
                                hudBenchmarkSummaryFlow.value = res
                            } catch (e: Exception) {
                                e.printStackTrace()
                            } finally {
                                isHudBenchmarkingFlow.value = false
                            }
                        }
                    },
                    onDrag = { dx, dy ->
                        layoutParams.x = (layoutParams.x + dx).toInt()
                        layoutParams.y = (layoutParams.y + dy).toInt()
                        try {
                            windowManager.updateViewLayout(this@apply, layoutParams)
                        } catch (_: Exception) {}
                    }
                )
            }
        }

        floatView = composeView
        try {
            windowManager.addView(composeView, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                "Lynx Game HUD Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi status overlay Game HUD Lynx"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pOpenIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, LynxFloatingHudService::class.java).apply {
            action = ACTION_STOP
        }
        val pStopIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIF_CHANNEL_ID)
                .setContentTitle("🎮 Lynx Floating Game HUD Aktif")
                .setContentText("Ketuk untuk membuka Lynx atau tutup overlay")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pOpenIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Tutup HUD", pStopIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("🎮 Lynx Floating Game HUD Aktif")
                .setContentText("Ketuk untuk membuka Lynx")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pOpenIntent)
                .build()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanUpAndStop()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}

// ============================================================
//  COMPOSE OVERLAY UI
// ============================================================

private val BgHud = Color(0xEE121418)
private val BorderHud = Color(0x4400E5FF)
private val NeonCyan = Color(0xFF00E5FF)
private val NeonGreen = Color(0xFF00E676)
private val NeonGold = Color(0xFFFFB300)
private val NeonRed = Color(0xFFFF5252)

@Composable
fun FloatingHudContent(
    telemetry: LynxRepository.FloatingHudTelemetry,
    isExpanded: Boolean,
    isHudBenchmarking: Boolean = false,
    hudBenchmarkCountdown: Int = 60,
    hudBenchmarkSummary: com.noir.lynx.data.LynxBenchmarkResult? = null,
    onToggleExpand: () -> Unit,
    onClose: () -> Unit,
    onProfileSelect: (String) -> Unit,
    onStartHudBenchmark: () -> Unit = {},
    onDrag: (Float, Float) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = BgHud,
        border = BorderStroke(1.dp, BorderHud),
        shadowElevation = 8.dp,
        modifier = Modifier.padding(4.dp)
    ) {
        Column(
            modifier = Modifier
                .wrapContentSize()
                .padding(8.dp)
        ) {
            // COMPACT PILL (Always visible or toggled)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                // Drag Handle
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = "Geser HUD",
                    tint = Color(0x99FFFFFF),
                    modifier = Modifier
                        .size(16.dp)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.x, dragAmount.y)
                            }
                        }
                )

                // Render FPS Badge
                val fpsText = "${telemetry.renderFps} FPS"
                val fpsColor = when {
                    telemetry.renderFps >= 90 -> NeonCyan
                    telemetry.renderFps >= 50 -> NeonGreen
                    telemetry.renderFps > 0 -> NeonGold
                    else -> Color(0x88FFFFFF)
                }
                Text(
                    text = fpsText,
                    color = fpsColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                // Subtle Separator Dot
                Text("·", color = Color(0x66FFFFFF), fontSize = 12.sp, fontWeight = FontWeight.Bold)

                // Frame Time Badge (ms) - Replaces 120Hz per user request
                val ftPillStr = if (telemetry.renderFps > 0) {
                    val ftVal = 1000f / telemetry.renderFps
                    String.format(java.util.Locale.US, "%.1fms", ftVal)
                } else "--ms"
                Text(
                    text = ftPillStr,
                    color = Color(0xEEFFFFFF),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )

                // Divider
                Text("|", color = Color(0x33FFFFFF), fontSize = 11.sp)

                // Watt & Temp Badge
                val wattStr = if (telemetry.battWatt > 0.05f) {
                    val sign = if (telemetry.isCharging) "+" else ""
                    "${sign}${String.format(java.util.Locale.US, "%.1f", telemetry.battWatt)}W · "
                } else ""
                Text(
                    text = "${wattStr}${telemetry.battTempC}°C",
                    color = when {
                        telemetry.battTempC > 42f -> NeonRed
                        telemetry.isCharging -> NeonGreen
                        else -> NeonGold
                    },
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )

                // Mode Badge
                val profileShort = when (telemetry.activeProfile.lowercase()) {
                    "performance" -> "PERF"
                    "extreme" -> "EXT"
                    "powersave" -> "PWR"
                    "auto" -> "AI"
                    else -> "BAL"
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x3300E5FF))
                        .padding(horizontal = 5.dp, vertical = 1.5.dp)
                ) {
                    Text(
                        text = profileShort,
                        color = NeonCyan,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // EXPANDED VIEW
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .width(235.dp)
                ) {
                    // Draggable Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    onDrag(dragAmount.x, dragAmount.y)
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DragHandle, "Geser", tint = Color(0x99FFFFFF), modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "LYNX OSD TELEMETRY",
                            color = NeonCyan,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                    HorizontalDivider(color = Color(0x33FFFFFF), thickness = 1.dp)
                    Spacer(Modifier.height(8.dp))

                    // Row 1: Render FPS & Display Refresh Rate
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("RENDER FPS", color = Color(0x88FFFFFF), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                            val fpsColor = when {
                                telemetry.renderFps >= 90 -> NeonCyan
                                telemetry.renderFps >= 50 -> NeonGreen
                                telemetry.renderFps > 0 -> NeonGold
                                else -> Color(0x88FFFFFF)
                            }
                            Text(
                                text = "${telemetry.renderFps} FPS",
                                color = fpsColor,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("REFRESH RATE", color = Color(0x88FFFFFF), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${telemetry.refreshRateHz} Hz",
                                color = NeonCyan,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Row 2: Frame Time & GPU Clock
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("FRAME TIME", color = Color(0x88FFFFFF), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                            val ftStr = if (telemetry.renderFps > 0) {
                                String.format(java.util.Locale.US, "%.1f ms", 1000f / telemetry.renderFps)
                            } else "-- ms"
                            Text(
                                text = ftStr,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("GPU CLOCK", color = Color(0x88FFFFFF), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                            val gpuStr = if (telemetry.gpuFreqMhz > 0) {
                                "${telemetry.gpuFreqMhz} MHz (${telemetry.gpuLoadPct}%)"
                            } else if (telemetry.gpuLoadPct > 0) {
                                "Load ${telemetry.gpuLoadPct}%"
                            } else "--"
                            Text(
                                gpuStr,
                                color = NeonCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Row 3: CPU Clock & Power (Watts)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("CPU CLOCK", color = Color(0x88FFFFFF), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (telemetry.cpuFreqMhz > 0) "${telemetry.cpuFreqMhz} MHz" else "--",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("DAYA (WATT)", color = Color(0x88FFFFFF), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                            val wattLabel = if (telemetry.battWatt > 0.05f) {
                                val s = if (telemetry.isCharging) "+" else ""
                                "${s}${String.format(java.util.Locale.US, "%.2f", telemetry.battWatt)} W"
                            } else "-- W"
                            Text(
                                text = wattLabel,
                                color = if (telemetry.isCharging) NeonGreen else NeonCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Row 4: Battery & Current
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("BATERAI & SUHU", color = Color(0x88FFFFFF), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${telemetry.battTempC}°C · ${telemetry.battLevelPct}%",
                                color = if (telemetry.battTempC > 42f) NeonRed else NeonGold,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("ARUS (mA)", color = Color(0x88FFFFFF), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                            val curSign = if (telemetry.battCurrentMa > 0) "+" else ""
                            Text(
                                text = "${curSign}${telemetry.battCurrentMa} mA",
                                color = if (telemetry.isCharging) NeonGreen else Color(0xCCFFFFFF),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text("QUICK PROFILE SWITCHER", color = Color(0x88FFFFFF), fontSize = 9.sp)
                    Spacer(Modifier.height(4.dp))

                    // Profile Pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val profiles = listOf("balance" to "BAL", "performance" to "PERF", "extreme" to "EXT", "powersave" to "PWR")
                        profiles.forEach { (key, label) ->
                            val isSel = telemetry.activeProfile.equals(key, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) NeonCyan else Color(0x22FFFFFF))
                                    .clickable { onProfileSelect(key) }
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSel) Color.Black else Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Live Benchmark Quick Action
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isHudBenchmarking) Color(0x33FFB300) else Color(0x2200E5FF),
                        border = BorderStroke(1.dp, if (isHudBenchmarking) NeonGold else NeonCyan.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isHudBenchmarking) { onStartHudBenchmark() }
                            .padding(vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Speed,
                                contentDescription = null,
                                tint = if (isHudBenchmarking) NeonGold else NeonCyan,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isHudBenchmarking) "Merekam Pacing... (${hudBenchmarkCountdown}s)" else "⚡ Live Benchmark (1 Menit)",
                                color = if (isHudBenchmarking) NeonGold else NeonCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Benchmark result summary card if available
                    hudBenchmarkSummary?.let { sum ->
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0x3300E676),
                            border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(6.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("BENCHMARK SELESAI", color = NeonGreen, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                    Text("${sum.averageFps.roundToInt()} FPS avg", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    text = "1% Low: ${String.format(java.util.Locale.US, "%.1f", sum.fps1PercentLow)} FPS · Pacing: ${String.format(java.util.Locale.US, "%.1f", sum.medianFrametimeMs)}ms (±${String.format(java.util.Locale.US, "%.1f", sum.frametimeJitterMs)}ms)",
                                    color = Color(0xEEFFFFFF),
                                    fontSize = 8.5.sp
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Controls: Minimize & Close
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Minimize",
                            color = Color(0xAAFFFFFF),
                            fontSize = 10.sp,
                            modifier = Modifier
                                .clickable { onToggleExpand() }
                                .padding(4.dp)
                        )
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0x33FF5252))
                                .clickable { onClose() }
                                .padding(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Tutup",
                                tint = NeonRed,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
