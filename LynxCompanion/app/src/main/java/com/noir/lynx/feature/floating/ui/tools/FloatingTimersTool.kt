package com.noir.lynx.feature.floating.ui.tools

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private data class ObjectiveTimer(
    val id: String,
    val name: String,
    val totalSeconds: Int,
    val accentColor: Color,
    val iconEmoji: String
)

@Composable
fun FloatingTimersTool(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val timerDefs = remember {
        listOf(
            ObjectiveTimer("turtle", "Turtle / Herald", 120, Color(0xFF00E676), "🐢"),
            ObjectiveTimer("lord", "Lord / Nashor", 300, Color(0xFFFFD600), "👑"),
            ObjectiveTimer("dragon", "Elemental Dragon", 240, Color(0xFFFF5252), "🐉"),
            ObjectiveTimer("flash", "Enemy Flash CD", 300, Color(0xFF00E5FF), "⚡"),
            ObjectiveTimer("cleanse", "Enemy Cleanse / Purify", 90, Color(0xFF7C4DFF), "🛡️"),
            ObjectiveTimer("ult", "Enemy Ultimate CD", 60, Color(0xFFFF4081), "⚔️")
        )
    }

    // Map of timerId to remaining seconds (or null if idle)
    var activeTimers by remember { mutableStateOf(mapOf<String, Int>()) }
    var runningTimers by remember { mutableStateOf(setOf<String>()) }

    fun vibrateAlert() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                v.vibrate(300)
            }
        } catch (_: Exception) {}
    }

    // Timer countdown loop
    LaunchedEffect(runningTimers) {
        while (runningTimers.isNotEmpty()) {
            delay(1000)
            val nextMap = activeTimers.toMutableMap()
            val nextRunning = runningTimers.toMutableSet()

            runningTimers.forEach { id ->
                val rem = nextMap[id] ?: 0
                if (rem > 1) {
                    nextMap[id] = rem - 1
                } else {
                    nextMap[id] = 0
                    nextRunning.remove(id)
                    vibrateAlert()
                }
            }

            activeTimers = nextMap
            runningTimers = nextRunning
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C1017))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Quick Reset All header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "COUNTDOWN TAKTIS REAL-TIME",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = Color(0xFF6B7D96)
            )

            if (runningTimers.isNotEmpty() || activeTimers.isNotEmpty()) {
                Surface(
                    onClick = {
                        runningTimers = emptySet()
                        activeTimers = emptyMap()
                    },
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF1E2633),
                    modifier = Modifier.height(20.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                        Text("Reset Semua", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
                    }
                }
            }
        }

        // Timers List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(timerDefs, key = { it.id }) { timer ->
                val isRunning = timer.id in runningTimers
                val remaining = activeTimers[timer.id]
                val currentRem = remaining ?: timer.totalSeconds
                val progress = (currentRem.toFloat() / timer.totalSeconds).coerceIn(0f, 1f)
                val isExpired = remaining != null && remaining == 0

                val minutes = currentRem / 60
                val seconds = currentRem % 60
                val timeStr = String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isExpired) Color(0xFF281010) else Color(0xFF121924),
                    border = BorderStroke(
                        0.8.dp,
                        if (isExpired) Color(0xFFFF5252)
                        else if (isRunning) timer.accentColor.copy(alpha = 0.6f)
                        else Color(0xFF202C3D)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(timer.iconEmoji, fontSize = 13.sp)
                                Column {
                                    Text(
                                        text = timer.name,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isExpired) Color(0xFFFF5252) else Color.White
                                    )
                                    Text(
                                        text = if (isExpired) "RESPAWNED / SIAP!" else "${timer.totalSeconds} detik",
                                        fontSize = 8.5.sp,
                                        color = if (isExpired) Color(0xFFFF8A80) else Color(0xFF7E8EA6)
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = timeStr,
                                    fontSize = 15.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isExpired) Color(0xFFFF5252) else if (isRunning) timer.accentColor else Color(0xFF90A0B8)
                                )

                                // Action button
                                Surface(
                                    onClick = {
                                        if (isRunning) {
                                            runningTimers = runningTimers - timer.id
                                        } else {
                                            if (remaining == null || remaining == 0) {
                                                activeTimers = activeTimers + (timer.id to timer.totalSeconds)
                                            }
                                            runningTimers = runningTimers + timer.id
                                        }
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isRunning) Color(0xFF2C2216) else timer.accentColor.copy(alpha = 0.2f),
                                    border = BorderStroke(0.6.dp, if (isRunning) Color(0xFFFFB300) else timer.accentColor),
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = if (isRunning) Color(0xFFFFB300) else timer.accentColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                // Reset single
                                if (remaining != null) {
                                    Surface(
                                        onClick = {
                                            runningTimers = runningTimers - timer.id
                                            activeTimers = activeTimers - timer.id
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF1B2330),
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = null,
                                                tint = Color(0xFF6B7E99),
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Progress bar
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(2.5.dp).clip(RoundedCornerShape(2.dp)),
                            color = if (isExpired) Color(0xFFFF5252) else timer.accentColor,
                            trackColor = Color(0xFF1A2330)
                        )
                    }
                }
            }
        }
    }
}
