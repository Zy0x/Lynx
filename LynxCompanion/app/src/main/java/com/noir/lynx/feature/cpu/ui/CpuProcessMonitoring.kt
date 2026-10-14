package com.noir.lynx.feature.cpu.ui
import com.noir.lynx.feature.cpu.ui.CompactClusterRow

import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.designsystem.card.*
import com.noir.lynx.designsystem.controls.*
import com.noir.lynx.designsystem.tweak.*
import com.noir.lynx.designsystem.profile.*

import java.util.Locale
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.designsystem.controls.SafeSlider as Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.noir.lynx.data.*
import com.noir.lynx.feature.cpu.ui.CpuSetsTaskShieldCard

import com.noir.lynx.ui.*

private val appIconCache = java.util.concurrent.ConcurrentHashMap<String, ImageBitmap>()
private val appLabelCache = java.util.concurrent.ConcurrentHashMap<String, String>()

@OptIn(ExperimentalMaterial3Api::class)
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
        if (cachedBitmap == null && cleanPkg.contains(".")) {
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
                .background(BgElevated)
                .border(0.6.dp, BorderSubtle, RoundedCornerShape(8.dp)),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopProcessListCard(
    procs: List<CpuProcessInfo>,
    clusters: List<CpuClusterInfo> = emptyList(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val scrollState = rememberScrollState()
    var selectedProcess by remember { mutableStateOf<Pair<CpuProcessInfo, String>?>(null) }

    LynxCard(
        title = "PROSES CPU AKTIF (SNAPSHOT)",
        subtitle = "Daftar dipotret saat Monitor dibuka; grafik CPU tetap live",
        icon = Icons.Default.Terminal,
        accentColor = AccentOrange,
        modifier = modifier
    ) {
        if (procs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Menunggu snapshot proses awal...", fontSize = 11.sp, color = TextSecondary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                procs.forEach { p ->
                    val cleanPkg = if (p.packageName.contains(":")) p.packageName.substringBefore(":") else p.packageName
                    val displayName = remember(p.packageName, p.name) {
                        if (cleanPkg.contains(".")) {
                            appLabelCache[cleanPkg] ?: runCatching {
                                val appInfo = pm.getApplicationInfo(cleanPkg, 0)
                                val label = pm.getApplicationLabel(appInfo).toString()
                                appLabelCache[cleanPkg] = label
                                label
                            }.getOrNull() ?: p.name
                        } else {
                            p.name
                        }
                    }

                    val loadNormalized = (p.cpuPercent / 100f).coerceIn(0.04f, 1f)
                    val animatedLoad by animateFloatAsState(
                        targetValue = loadNormalized,
                        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                        label = "proc_load_${p.pid}"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(BgSurfaceLowest, RoundedCornerShape(10.dp))
                            .border(0.8.dp, BorderSubtle, RoundedCornerShape(10.dp))
                            .clickable { selectedProcess = Pair(p, displayName) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
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
                                    text = "PID ${p.pid} • ${p.packageName.take(24)}",
                                    fontSize = 9.sp,
                                    color = TextTertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.End) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (p.rawCpuPercent >= 80f) {
                                        val coreCountEstimate = p.rawCpuPercent / 100f
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = AccentOrange.copy(alpha = 0.15f),
                                            modifier = Modifier.padding(end = 6.dp)
                                        ) {
                                            Text(
                                                text = String.format(Locale.US, "%.1f Inti", coreCountEstimate),
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = AccentOrange,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = String.format(Locale.US, "%.1f%%", p.cpuPercent),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (p.cpuPercent >= 15f) AccentOrange else AccentCyan
                                    )
                                }
                                Spacer(Modifier.height(3.dp))
                                Box(
                                    modifier = Modifier
                                        .width(52.dp)
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(1.5.dp))
                                        .background(BgElevated)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(fraction = animatedLoad)
                                            .fillMaxHeight()
                                            .background(if (p.cpuPercent >= 15f) AccentOrange else AccentCyan)
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
