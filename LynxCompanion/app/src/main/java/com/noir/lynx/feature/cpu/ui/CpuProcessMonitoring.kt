package com.noir.lynx.feature.cpu.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.data.*
import com.noir.lynx.designsystem.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private val appIconCache = java.util.concurrent.ConcurrentHashMap<String, ImageBitmap>()
private val appLabelCache = java.util.concurrent.ConcurrentHashMap<String, String>()
private val failedPkgs = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

@Composable
fun ProcessIcon(
    packageName: String,
    processName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cleanPkg = remember(packageName) {
        val base = if (packageName.contains(":")) packageName.substringBefore(":") else packageName
        when {
            base.contains("webview", ignoreCase = true) -> "com.android.chrome"
            base.contains("chrome", ignoreCase = true) -> "com.android.chrome"
            else -> base
        }
    }

    var cachedBitmap by remember(cleanPkg) {
        mutableStateOf(appIconCache[cleanPkg])
    }

    LaunchedEffect(cleanPkg) {
        if (cachedBitmap == null && cleanPkg.contains(".") && !failedPkgs.contains(cleanPkg)) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val pm = context.packageManager
                    val appInfo = pm.getApplicationInfo(cleanPkg, 0)
                    val drawable = pm.getApplicationIcon(appInfo)
                    val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth.coerceIn(48, 128) else 96
                    val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight.coerceIn(48, 128) else 96
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val canvas = AndroidCanvas(bmp)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    val imageBitmap = bmp.asImageBitmap()
                    appIconCache[cleanPkg] = imageBitmap
                    imageBitmap
                }.getOrNull()?.let {
                    cachedBitmap = it
                } ?: run {
                    failedPkgs.add(cleanPkg)
                }
            }
        }
    }

    if (cachedBitmap != null) {
        Image(
            bitmap = cachedBitmap!!,
            contentDescription = processName,
            modifier = modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
        )
    } else {
        val (fallbackIcon, fallbackTint) = when {
            processName.contains("media", ignoreCase = true) || processName.contains("codec", ignoreCase = true) || processName.contains("audio", ignoreCase = true) ->
                Pair(Icons.Default.VolumeUp, AccentCyan)
            processName.contains("surfaceflinger", ignoreCase = true) || processName.contains("render", ignoreCase = true) || processName.contains("composer", ignoreCase = true) ->
                Pair(Icons.Default.Layers, AccentPurple)
            processName.contains("camera", ignoreCase = true) ->
                Pair(Icons.Default.Videocam, AccentOrange)
            processName.contains("system_server", ignoreCase = true) || processName.contains("systemui", ignoreCase = true) || processName.contains("android", ignoreCase = true) ->
                Pair(Icons.Default.Android, AccentGreen)
            processName.contains("sh", ignoreCase = true) || processName.contains("top", ignoreCase = true) || processName.contains("su", ignoreCase = true) || processName.contains("shell", ignoreCase = true) ->
                Pair(Icons.Default.Terminal, AccentOrange)
            processName.contains("kworker", ignoreCase = true) || processName.contains("ksoftirqd", ignoreCase = true) || processName.contains("rcu", ignoreCase = true) ->
                Pair(Icons.Default.Memory, TextSecondary)
            else ->
                Pair(Icons.Default.Memory, AccentCyan)
        }

        Box(
            modifier = modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF141824))
                .border(0.6.dp, Color(0x18FFFFFF), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = processName,
                tint = fallbackTint,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

/**
 * TopProcessListCard — Human-friendly, uncluttered active process monitoring.
 *
 * Anti-AI-Slop & Ergonomics:
 * 1. Default to Top 5 prominent processes to prevent visual fatigue.
 * 2. Progressive disclosure toggle to expand up to 15 processes on demand.
 * 3. Human-friendly app labels and clean PID/package subtitles.
 * 4. Monospace tabular CPU load metrics with load-aware colors.
 */
@Composable
fun TopProcessListCard(
    procs: List<CpuProcessInfo>,
    clusters: List<CpuClusterInfo> = emptyList(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val pm = context.packageManager
    var selectedProcess by remember { mutableStateOf<Pair<CpuProcessInfo, String>?>(null) }
    var isExpanded by remember { mutableStateOf(false) }

    val activeProcs = if (procs.isNotEmpty()) procs else LynxRepository.getCachedTopProcesses()
    val displayedProcs = if (isExpanded) activeProcs.take(15) else activeProcs.take(5)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF10141F),
        border = BorderStroke(0.8.dp, Brush.verticalGradient(listOf(Color(0x28FFFFFF), Color(0x10FFFFFF)))),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header: Icon + Title + Process count badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AccentOrange.copy(alpha = 0.12f),
                        border = BorderStroke(0.5.dp, AccentOrange.copy(alpha = 0.35f)),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Terminal, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Proses CPU Aktif", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("Aplikasi dan thread dengan beban tertinggi", fontSize = 9.5.sp, color = TextSecondary.copy(alpha = 0.75f))
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF0C0F17),
                    border = BorderStroke(0.5.dp, Color(0x18FFFFFF))
                ) {
                    Text(
                        text = "${activeProcs.size} Thread",
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            if (activeProcs.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF0C0F17),
                    border = BorderStroke(0.6.dp, Color(0x15FFFFFF)),
                    modifier = Modifier.fillMaxWidth().height(60.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Seluruh thread CPU dalam batas normal", fontSize = 11.sp, color = TextSecondary)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    displayedProcs.forEach { p ->
                        val cleanPkg = if (p.packageName.contains(":")) p.packageName.substringBefore(":") else p.packageName
                        val displayName = remember(p.packageName, p.name) {
                            if (cleanPkg.contains(".") && !failedPkgs.contains(cleanPkg)) {
                                appLabelCache[cleanPkg] ?: runCatching {
                                    val appInfo = pm.getApplicationInfo(cleanPkg, 0)
                                    val label = pm.getApplicationLabel(appInfo).toString()
                                    appLabelCache[cleanPkg] = label
                                    label
                                }.getOrElse {
                                    failedPkgs.add(cleanPkg)
                                    appLabelCache[cleanPkg] = p.name
                                    p.name
                                }
                            } else {
                                p.name
                            }
                        }

                        val loadNormalized = (p.cpuPercent / 100f).coerceIn(0.04f, 1f)
                        val loadColor = when {
                            p.cpuPercent >= 50f -> AccentRed
                            p.cpuPercent >= 15f -> AccentOrange
                            else -> AccentCyan
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF0C0F17),
                            border = BorderStroke(0.6.dp, Color(0x15FFFFFF)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedProcess = Pair(p, displayName)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ProcessIcon(
                                        packageName = p.packageName,
                                        processName = p.name
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = displayName,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "PID ${p.pid} • ${cleanPkg.take(24)}",
                                            fontSize = 8.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = TextTertiary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = String.format(Locale.US, "%.1f%%", p.cpuPercent),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = loadColor
                                        )
                                        Spacer(Modifier.height(3.dp))
                                        Box(
                                            modifier = Modifier
                                                .width(48.dp)
                                                .height(3.dp)
                                                .clip(RoundedCornerShape(1.5.dp))
                                                .background(Color(0xFF141824))
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(fraction = loadNormalized)
                                                    .fillMaxHeight()
                                                    .background(loadColor)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = TextTertiary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Progressive Disclosure Toggle
                    if (activeProcs.size > 5) {
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isExpanded = !isExpanded
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF0C0F17),
                            border = BorderStroke(0.6.dp, Color(0x18FFFFFF)),
                            modifier = Modifier.fillMaxWidth().height(36.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isExpanded) "Tampilkan Ringkas (5 Teratas)" else "Lihat ${activeProcs.size.coerceAtMost(15)} Proses Aktif",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AccentOrange
                                )
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = AccentOrange,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedProcess?.let { (proc, resolvedLabel) ->
        ProcessInspectorBottomSheet(
            process = proc,
            displayName = resolvedLabel,
            clusters = clusters,
            onDismiss = { selectedProcess = null }
        )
    }
}
