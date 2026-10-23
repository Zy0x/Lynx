package com.noir.lynx.feature.gpu.ui

import com.noir.lynx.core.locale.AppLocale

import com.noir.lynx.R

import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.designsystem.card.*
import com.noir.lynx.designsystem.controls.*
import com.noir.lynx.designsystem.tweak.*
import com.noir.lynx.designsystem.profile.*

import java.util.Locale
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.designsystem.controls.SafeSlider as Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.*
import com.noir.lynx.display.ColorMatrixEngine
import com.noir.lynx.display.ColorMatrixProfile
import com.noir.lynx.display.DisplayPipelineInfo
import com.noir.lynx.hardware.DisplayModeInfo
import com.noir.lynx.hardware.GpuBackend
import com.noir.lynx.hardware.GraphicsCapabilities
import com.noir.lynx.hardware.NodeStatus
import com.noir.lynx.lab.DropCorrelator
import com.noir.lynx.lab.DropEvent
import com.noir.lynx.lab.FrameSessionReport
import java.text.SimpleDateFormat
import java.util.*
import com.noir.lynx.ui.*

@Composable
fun FloatingGameHudCard(
    isHudRunning: Boolean,
    hudMode: Int,
    hudStyle: Int,
    onToggleHud: () -> Unit,
    onSetMode: (Int) -> Unit = {},
    onSetStyle: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val cardAccent = AccentCyan
    val context = LocalContext.current
    val hasOverlayPermission = remember(isHudRunning) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.provider.Settings.canDrawOverlays(context)
        } else true
    }
    val hudStyleName = when (hudStyle) {
        1 -> "RTSS Pillar"
        2 -> "Top Ribbon"
        3 -> "Dual Esport"
        4 -> "Quad Tiles"
        5 -> "Steam Deck"
        6 -> "Ghost Text"
        else -> "RTSS Pillar"
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF10141E),
        border = BorderStroke(0.6.dp, Color(0x1AFFFFFF)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = (if (isHudRunning) cardAccent else TextSecondary).copy(alpha = 0.12f),
                        border = BorderStroke(0.6.dp, (if (isHudRunning) cardAccent else TextSecondary).copy(alpha = 0.3f)),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = if (isHudRunning) cardAccent else TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "In-Game Performance HUD",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            if (isHudRunning) {
                                Surface(
                                    shape = RoundedCornerShape(5.dp),
                                    color = cardAccent.copy(alpha = 0.15f),
                                    border = BorderStroke(0.5.dp, cardAccent.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = hudStyleName,
                                        color = cardAccent,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (isHudRunning) "Overlay aktif • Pantau FPS, frame pacing & clock di game" else "Aktifkan OSD real-time saat pengujian benchmark frame",
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp,
                            color = TextSecondary.copy(alpha = 0.75f)
                        )
                    }
                }
                Switch(
                    checked = isHudRunning,
                    onCheckedChange = {
                        if (!hasOverlayPermission && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                            val intent = android.content.Intent(
                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } else {
                            onToggleHud()
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BgDeepOled,
                        checkedTrackColor = cardAccent,
                        uncheckedThumbColor = TextTertiary,
                        uncheckedTrackColor = BgSurfaceLowest
                    )
                )
            }
        }
    }
}

// ============================================================
//  4. PERFORMANCE LAB — RECORDER CARD
// ============================================================

@Composable
fun LabRecordCard(
    isRecording: Boolean,
    onStartRecording: (String?) -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardAccent = AccentRed
    val context = LocalContext.current

    // Pulsing animation when recording
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    LynxCard(
        title = "Performance Lab — Frame Recorder",
        subtitle = "Perekam frame-pacing real-time untuk game & aplikasi aktif",
        icon = Icons.Default.Analytics,
        accentColor = cardAccent,
        modifier = modifier
    ) {
        // Status Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isRecording) cardAccent.copy(alpha = pulseAlpha) else AccentGreen)
                )
                Text(
                    text = if (isRecording) "SEDANG MEREKAM SESI AKTIF" else "Status: Standby (Siap Merekam)",
                    color = if (isRecording) cardAccent else TextSecondary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle)
            ) {
                Text(
                    text = "SurfaceFlinger BLAST",
                    color = TextTertiary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        Text(
            text = "Lynx Performance Lab mengukur frame presentation timestamps langsung dari SurfaceFlinger untuk mendeteksi micro-stutter, jank, variansi frame-time, serta korelasi drop frekuensi GPU dan thermal throttling secara presisi.",
            color = TextSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(bottom = 14.dp)
        )

        // Action Button
        Button(
            onClick = {
                if (isRecording) {
                    onStopRecording()
                    Toast.makeText(context, AppLocale.getString(context, R.string.toast_session_analyzing), Toast.LENGTH_SHORT).show()
                } else {
                    onStartRecording(null)
                    Toast.makeText(context, AppLocale.getString(context, R.string.toast_recording_started), Toast.LENGTH_SHORT).show()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRecording) AccentRed else AccentCyan
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp) // Touch target >= 48dp
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = BgDeepOled,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isRecording) "Hentikan Sesi & Analisis Frame" else "Mulai Perekaman Sesi Baru",
                color = BgDeepOled,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ============================================================
//  4. PERFORMANCE LAB — SESSION SUMMARY CARD
// ============================================================
