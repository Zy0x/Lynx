package com.noir.lynx.service
import androidx.compose.foundation.layout.ColumnScope

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
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import kotlin.math.roundToInt

@Composable
internal fun ColumnScope.HudQuickToolsAndBenchmark(
    currentRefreshRate: Int,
    pinMiniFps: Boolean,
    isHudBenchmarking: Boolean,
    hudBenchmarkCountdown: Int,
    hudBenchmarkSummary: com.noir.lynx.data.LynxBenchmarkResult?,
    onBoostRam: () -> Unit,
    onCycleRefreshRate: () -> Unit,
    onTogglePinMiniFps: () -> Unit,
    onStartHudBenchmark: () -> Unit,
    onInteraction: () -> Unit
) {
    var isBoosting by remember { mutableStateOf(false) }
    var boostFeedback by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
                // 4. Quick Gaming Tools
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "GAMING TOOLS",
                        color = TextSlate,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Boost RAM
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isBoosting) NeonGreen.copy(alpha = 0.2f) else Color(0x18FFFFFF),
                            border = BorderStroke(1.dp, if (isBoosting) NeonGreen else Color(0x22FFFFFF)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (!isBoosting) {
                                        isBoosting = true
                                        boostFeedback = "..."
                                        onBoostRam()
                                        onInteraction()
                                        coroutineScope.launch {
                                            delay(1200L)
                                            isBoosting = false
                                            boostFeedback = null
                                        }
                                    }
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    if (isBoosting) "Bersih" else "Boost RAM",
                                    color = if (isBoosting) NeonGreen else NeonCyan,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text("Drop Caches", color = TextMuted, fontSize = 7.sp, maxLines = 1)
                            }
                        }

                        // Lock Refresh Rate
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0x18FFFFFF),
                            border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    onCycleRefreshRate()
                                    onInteraction()
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "$currentRefreshRate Hz",
                                    color = NeonGold,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text("Ganti Hz", color = TextMuted, fontSize = 7.sp, maxLines = 1)
                            }
                        }

                        // Pin Mini FPS
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (pinMiniFps) NeonGreen.copy(alpha = 0.2f) else Color(0x18FFFFFF),
                            border = BorderStroke(1.dp, if (pinMiniFps) NeonGreen else Color(0x22FFFFFF)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    onTogglePinMiniFps()
                                    onInteraction()
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Pin FPS",
                                    color = if (pinMiniFps) NeonGreen else TextSlate,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(if (pinMiniFps) "Aktif" else "Off", color = TextMuted, fontSize = 7.sp, maxLines = 1)
                            }
                        }
                    }
                }

                // 5. Live Benchmark Trigger
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isHudBenchmarking) NeonMagenta.copy(alpha = 0.2f) else Color(0x12FFFFFF),
                    border = BorderStroke(1.dp, if (isHudBenchmarking) NeonMagenta else Color(0x1AFFFFFF)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (!isHudBenchmarking) {
                                onStartHudBenchmark()
                                onInteraction()
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("⏱", fontSize = 9.sp)
                            Text(
                                if (isHudBenchmarking) "Benchmarking... ${hudBenchmarkCountdown}s" else "Mulai Benchmark 60s",
                                color = if (isHudBenchmarking) NeonMagenta else TextSlate,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (hudBenchmarkSummary != null && !isHudBenchmarking) {
                            val stabPct = (100f - hudBenchmarkSummary.jankyFramesPercent).coerceIn(0f, 100f).toInt()
                            Text(
                                "Stab $stabPct% · 1%L ${hudBenchmarkSummary.fps1PercentLow.toInt()}",
                                color = NeonGreen,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
}
    }
