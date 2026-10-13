package com.noir.lynx.feature.gpu.ui

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
    onSetMode: (Int) -> Unit,
    onSetStyle: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardAccent = AccentCyan
    val context = LocalContext.current
    val hasOverlayPermission = remember(isHudRunning) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.provider.Settings.canDrawOverlays(context)
        } else true
    }

    LynxCard(
        title = "Floating Game HUD (In-Game OSD)",
        subtitle = "Overlay performa mengambang real-time di atas game fullscreen",
        icon = Icons.Default.Layers,
        accentColor = cardAccent,
        action = {
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
        },
        modifier = modifier
    ) {
        if (!hasOverlayPermission) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = AccentOrange.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(18.dp))
                    Text(
                        "Izin Tampilkan di Atas Aplikasi Lain diperlukan agar overlay HUD dapat muncul saat Anda bermain game.",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // HUD Active Status Banner
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isHudRunning) cardAccent.copy(alpha = 0.1f) else BgElevated,
            border = BorderStroke(1.dp, if (isHudRunning) cardAccent.copy(alpha = 0.4f) else BorderSubtle),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    if (!hasOverlayPermission && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        val intent = android.content.Intent(
                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    } else {
                        onToggleHud()
                    }
                }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isHudRunning) cardAccent.copy(alpha = 0.2f) else TextTertiary.copy(alpha = 0.12f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isHudRunning) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = if (isHudRunning) cardAccent else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isHudRunning) "HUD Mengambang Aktif" else "HUD Nonaktif",
                        color = TextPrimary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isHudRunning) "Menampilkan FPS, Frame Time, GPU Load, & Suhu di layar" else "Ketuk sakelar untuk memunculkan OSD di game",
                        color = TextSecondary,
                        fontSize = 10.5.sp
                    )
                }
            }
        }

        AnimatedVisibility(visible = isHudRunning) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Layout Style Selector Chips
                Text("Gaya Tampilan Overlay (OSD Style)", color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        Triple(1, "RTSS Slim Pillar", "Vertikal"),
                        Triple(2, "Top Nano-Ribbon", "Horizontal"),
                        Triple(3, "Dual-Block", "Esport"),
                        Triple(4, "Quad-Tiles", "Grid"),
                        Triple(5, "Steam Deck", "Banner")
                    ).forEach { (styleId, title, badge) ->
                        val isSel = hudStyle == styleId
                        Surface(
                            onClick = { onSetStyle(styleId) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) cardAccent.copy(alpha = 0.22f) else BgElevated,
                            border = BorderStroke(1.dp, if (isSel) cardAccent else BorderGlass),
                            modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(title, color = if (isSel) cardAccent else TextPrimary, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium)
                                Text(badge, color = TextSecondary, fontSize = 9.sp)
                            }
                        }
                    }
                }
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
                    Toast.makeText(context, "Menganalisis sesi perekaman...", Toast.LENGTH_SHORT).show()
                } else {
                    onStartRecording(null)
                    Toast.makeText(context, "Perekaman dimulai. Buka game/aplikasi Anda.", Toast.LENGTH_SHORT).show()
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
