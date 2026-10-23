package com.noir.lynx.feature.media.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.safety.LynxMediaSafeguardManager
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MediaCodecDiagnosticCard - Real-time diagnostic tool for MediaCodec/ExoPlayer video decoding
 * and One-Click Self-Heal for Twitter/Chrome video playback errors.
 */
@Composable
fun MediaCodecDiagnosticCard(
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var isHealing by remember { mutableStateOf(false) }
    var healStatus by remember { mutableStateOf<String?>(null) }
    var recentLogs by remember { mutableStateOf<List<String>>(emptyList()) }
    var ccodecProp by remember { mutableStateOf("1") }
    var swapIntervalProp by remember { mutableStateOf("1") }

    val isSafeguardActive by LynxMediaSafeguardManager.isSafeguardEngaged.collectAsState()
    val protectedApp by LynxMediaSafeguardManager.activeProtectedPackage.collectAsState()

    fun refreshDiagnostics() {
        coroutineScope.launch(Dispatchers.IO) {
            ccodecProp = Shell.cmd("getprop debug.stagefright.ccodec").exec().out.firstOrNull()?.trim() ?: "default"
            swapIntervalProp = Shell.cmd("getprop debug.egl.swapinterval").exec().out.firstOrNull()?.trim() ?: "1"

            val logRes = Shell.cmd(
                "logcat -d -t 50 -s MediaCodec:E ExoPlayer:E CCodec:E Stagefright:E 2>/dev/null | tail -n 4"
            ).exec().out.filter { it.isNotBlank() }

            withContext(Dispatchers.Main) {
                recentLogs = if (logRes.isNotEmpty()) logRes else listOf("Tidak ada error kritis MediaCodec / ExoPlayer terdeteksi.")
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshDiagnostics()
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = BgSurfaceLowest,
        border = BorderStroke(1.dp, BorderGlass),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── HEADER ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(AccentCyan.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Media & Codec Health Inspector",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Diagnostik ExoPlayer & pemulihan video Twitter/Browser",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                IconButton(onClick = { refreshDiagnostics() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                }
            }

            HorizontalDivider(color = BorderGlass)

            // ── STATUS BADGES ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Codec2 status
                val isCodec2Healthy = ccodecProp != "0"
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isCodec2Healthy) AccentGreen.copy(alpha = 0.15f) else AccentOrange.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, if (isCodec2Healthy) AccentGreen.copy(alpha = 0.4f) else AccentOrange.copy(alpha = 0.4f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Codec2 Backend", fontSize = 10.sp, color = TextSecondary)
                        Text(
                            text = if (isCodec2Healthy) "Codec2 (Sehat)" else "Legacy OMX (Risiko)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isCodec2Healthy) AccentGreen else AccentOrange
                        )
                    }
                }

                // VSync Interop status
                val isVsyncSafe = swapIntervalProp == "1" || isSafeguardActive
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isVsyncSafe) AccentCyan.copy(alpha = 0.15f) else AccentOrange.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, if (isVsyncSafe) AccentCyan.copy(alpha = 0.4f) else AccentOrange.copy(alpha = 0.4f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("VSync Interop", fontSize = 10.sp, color = TextSecondary)
                        Text(
                            text = if (isVsyncSafe) "Sinkron (Aman)" else "Bypass (Rawan Error)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isVsyncSafe) AccentCyan else AccentOrange
                        )
                    }
                }
            }

            // ── SAFEGUARD INDICATOR ──
            if (isSafeguardActive && protectedApp != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentGreen.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Lynx Safety Shield Aktif mengamankan video untuk $protectedApp",
                            fontSize = 11.sp,
                            color = AccentGreen,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // ── ERROR LOGCAT PREVIEW ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF090B10), RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Log Diagnostik Decoder Terakhir:", fontSize = 10.sp, color = TextSecondary)
                recentLogs.forEach { line ->
                    Text(
                        text = line,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (line.contains("error", ignoreCase = true) || line.contains("fail", ignoreCase = true)) AccentRed else TextSecondary,
                        maxLines = 2
                    )
                }
            }

            healStatus?.let { msg ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentGreen.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg,
                        fontSize = 11.sp,
                        color = AccentGreen,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            // ── ONE-CLICK SELF-HEAL BUTTON ──
            Button(
                onClick = {
                    isHealing = true
                    healStatus = null
                    coroutineScope.launch(Dispatchers.IO) {
                        // Reset tampered props
                        Shell.cmd(
                            "resetprop -d debug.stagefright.ccodec 2>/dev/null",
                            "resetprop -d debug.stagefright.omx_default_rank 2>/dev/null",
                            "resetprop -d debug.stagefright.omx_default_rank.sw-other 2>/dev/null",
                            "resetprop -d media.stagefright.thumbnail.prefer_hw_codecs 2>/dev/null",
                            "setprop media.stagefright.thumbnail.prefer_hw_codecs false",
                            "setprop debug.stagefright.ccodec 1",
                            "setprop debug.egl.swapinterval 1",
                            "pkill -f mediaserver 2>/dev/null",
                            "pkill -f cameraserver 2>/dev/null"
                        ).exec()

                        kotlinx.coroutines.delay(600)
                        refreshDiagnostics()

                        withContext(Dispatchers.Main) {
                            isHealing = false
                            healStatus = "✅ Decoder video berhasil dipulihkan! Mediaserver telah di-restart bersih tanpa reboot."
                        }
                    }
                },
                enabled = !isHealing,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Icon(Icons.Default.Healing, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isHealing) "Memulihkan Decoder..." else "Pulihkan Decoder (One-Click Self-Heal)",
                    color = Color.Black,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
