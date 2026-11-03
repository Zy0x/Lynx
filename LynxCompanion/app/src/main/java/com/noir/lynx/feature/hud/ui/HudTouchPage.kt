package com.noir.lynx.feature.hud.ui

import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.addins.HudGamingAddins
import com.noir.lynx.feature.hud.data.*
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class TouchPointVisual(
    val id: Long,
    val position: Offset,
    val history: List<Offset>
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun HudTouchPage(
    touchStatus: HudTouchStatus,
    busy: Boolean,
    onToggleTouchBoost: (Boolean) -> Unit
) {
    var touchSample by remember { mutableStateOf(HudTouchSample()) }
    var activeTouchPoints by remember { mutableStateOf<List<TouchPointVisual>>(emptyList()) }
    var showTouchesEnabled by remember { mutableStateOf(false) }
    var isScrollLocked by rememberSaveable { mutableStateOf(true) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val coroutineScope = rememberCoroutineScope()
    val view = LocalView.current

    DisposableEffect(view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.requestUnbufferedDispatch(MotionEvent.ACTION_MOVE)
        }
        onDispose { }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val out = Shell.cmd("settings get system show_touches").exec().out.firstOrNull()?.trim()
            showTouchesEnabled = out == "1"
        }
    }

    val runtime = HudGamingAddins.current
    val zoneTouch = runtime?.zoneTouch

    val esportsEnabled by zoneTouch?.esportsEnabled?.collectAsState() ?: remember { mutableStateOf(false) }
    val samplingBoost by zoneTouch?.samplingBoost?.collectAsState() ?: remember { mutableStateOf(false) }
    val touchSlop by zoneTouch?.touchSlop?.collectAsState() ?: remember { mutableStateOf(2) }

    val touchCapText = hudString(
        when (touchStatus.capability) {
            HudCapability.READY -> R.string.hud_cap_ready
            HudCapability.NEEDS_ROOT -> R.string.hud_cap_needs_root
            HudCapability.UNSUPPORTED -> R.string.hud_cap_unsupported
            HudCapability.CHECKING -> R.string.hud_cap_checking
        }
    )
    val touchCapColor = when (touchStatus.capability) {
        HudCapability.READY -> if (touchStatus.enabled) HudCyan else HudBlue
        HudCapability.NEEDS_ROOT -> HudOrange
        HudCapability.UNSUPPORTED -> HudMuted
        HudCapability.CHECKING -> HudBlue
    }

    val touchInfo = "${hudString(R.string.hud_lab_touch_desc)}\n\n" +
        "• Kernel CPU Touch Boost: Memaksa governor CPU melompat ke frekuensi tertinggi seketika jari menyentuh layar.\n\n" +
        "• Esports Low-Latency: Prioritas real-time SCHED_FIFO pada thread InputReader & InputDispatcher Android.\n\n" +
        "• Sampling Rate Boost: Memaksa digitizer IC hardware ke kecepatan baca maksimal (hingga 240Hz/480Hz).\n\n" +
        "• Ultra-Sensitive Touch Slop (2px): Responsivitas gesekan instan dengan meminimalkan deadzone pergerakan jari."

    // 2 Subpages: 0 = Setelan (Controls & Fine Tuning), 1 = Uji Sentuh (Spacious Test Pad)
    HudSubpages(listOf(R.string.hud_detail_controls, R.string.hud_detail_test)) { section ->
        if (section == 0) {
            HudSection(
                title = hudString(R.string.hud_lab_touch_title),
                badge = touchCapText,
                accentColor = touchCapColor,
                info = touchInfo
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // 1. Master Kernel Touch Boost Switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = hudString(R.string.hud_touch_boost_label),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            Text(
                                text = if (touchStatus.backendLabel.isNotBlank()) touchStatus.backendLabel else hudString(R.string.hud_lab_touch_desc),
                                fontSize = 10.5.sp,
                                fontFamily = if (touchStatus.backendLabel.isNotBlank()) FontFamily.Monospace else FontFamily.Default,
                                color = if (touchStatus.enabled) HudCyan else HudMuted
                            )
                        }
                        Switch(
                            checked = touchStatus.enabled,
                            onCheckedChange = { isChecked ->
                                onToggleTouchBoost(isChecked)
                                zoneTouch?.setEsportsEnabled(isChecked)
                            },
                            enabled = touchStatus.capability == HudCapability.READY && !busy,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = HudBlueBadge,
                                uncheckedThumbColor = HudMuted,
                                uncheckedTrackColor = HudElevated
                            )
                        )
                    }

                    // 2. Esports & Digitizer Fine-Tuning
                    if (zoneTouch != null) {
                        AddinToggle(
                            label = hudString(R.string.hud_zonetouch_esports),
                            checked = esportsEnabled,
                            info = "Memaksa InputReader ke prioritas real-time (RT) untuk memangkas latensi input.",
                            onChange = zoneTouch::setEsportsEnabled
                        )

                        if (esportsEnabled) {
                            AddinToggle(
                                label = hudString(R.string.hud_zonetouch_sampling),
                                checked = samplingBoost,
                                info = "Menaikkan touch reporting rate digitizer ke batas maksimal.",
                                onChange = zoneTouch::setSamplingBoost
                            )

                            AddinToggle(
                                label = hudString(R.string.hud_zonetouch_slop),
                                checked = touchSlop <= 2,
                                info = hudString(R.string.hud_zonetouch_slop_desc),
                                onChange = { ultra ->
                                    zoneTouch.setTouchSlop(if (ultra) 2 else 8)
                                }
                            )
                        }
                    }

                    // 3. System-Wide Show Touches Toggle
                    AddinToggle(
                        label = hudString(R.string.hud_touch_show_touches),
                        checked = showTouchesEnabled,
                        info = hudString(R.string.hud_touch_show_touches_desc),
                        onChange = { isChecked ->
                            showTouchesEnabled = isChecked
                            coroutineScope.launch(Dispatchers.IO) {
                                Shell.cmd("settings put system show_touches ${if (isChecked) 1 else 0}").exec()
                            }
                        }
                    )
                }
            }
        } else {
            // Uji Sentuh (Spacious Multi-Touch Pad with System Tap Indicator & Motion Trails)
            HudSection(
                title = hudString(R.string.hud_lab_touch_title),
                badge = if ((touchSample.rateHz ?: 0) > 0) "${touchSample.rateHz} Hz" else "Siap Uji",
                accentColor = if ((touchSample.rateHz ?: 0) > 0) HudCyan else HudBlue,
                info = "Pad Uji Sentuh Interaktif:\n\nSentuh, ketuk, atau geser beberapa jari bersamaan di dalam area pad untuk menguji responsivitas sampling layar (Hz), latensi interval (ms), dan kehalusan pelacakan posisi multi-touch secara real-time."
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Scroll Lock Toggle Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isScrollLocked) HudCyan.copy(alpha = 0.08f) else HudElevated)
                            .border(
                                width = 1.dp,
                                color = if (isScrollLocked) HudCyan.copy(alpha = 0.35f) else HudBorder,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { isScrollLocked = !isScrollLocked }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isScrollLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = if (isScrollLocked) HudCyan else HudOrange,
                                modifier = Modifier.size(16.dp)
                            )
                            Column {
                                Text(
                                    text = if (isScrollLocked) "Kunci Halaman Saat Uji" else "Scroll Halaman Aktif",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (isScrollLocked)
                                        "Gerakan sentuhan pad tidak akan menggeser halaman"
                                    else
                                        "Geser pad ke atas/bawah untuk menggulir halaman",
                                    fontSize = 9.5.sp,
                                    color = if (isScrollLocked) HudCyan.copy(alpha = 0.8f) else HudMuted
                                )
                            }
                        }
                        Switch(
                            checked = isScrollLocked,
                            onCheckedChange = { isScrollLocked = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = HudBlueBadge,
                                uncheckedThumbColor = HudMuted,
                                uncheckedTrackColor = HudCard
                            ),
                            modifier = Modifier.scale(0.8f)
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isLandscape) 135.dp else 170.dp)
                            .pointerInput(isScrollLocked) {
                                val timestampsNano = ArrayList<Long>(32)
                                var maxFingersCount = 0
                                val pointerHistories = HashMap<Long, ArrayList<Offset>>()

                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val pressed = event.changes.filter { it.pressed }

                                        if (isScrollLocked && pressed.isNotEmpty()) {
                                            event.changes.forEach { it.consume() }
                                        }

                                        if (pressed.isEmpty()) {
                                            timestampsNano.clear()
                                            pointerHistories.clear()
                                            activeTouchPoints = emptyList()
                                            if (touchSample.rateHz != null) {
                                                touchSample = touchSample.copy(activeFingers = 0)
                                            }
                                            continue
                                        }

                                        if (pressed.size > maxFingersCount) {
                                            maxFingersCount = pressed.size
                                        }

                                        val currentIds = pressed.map { it.id.value }.toSet()
                                        pointerHistories.keys.retainAll(currentIds)

                                        val visuals = ArrayList<TouchPointVisual>(pressed.size)
                                        for (change in pressed) {
                                            val id = change.id.value
                                            val hist = pointerHistories.getOrPut(id) { ArrayList() }
                                            for (h in change.historical) {
                                                hist.add(h.position)
                                            }
                                            hist.add(change.position)
                                            while (hist.size > 24) {
                                                hist.removeAt(0)
                                            }
                                            visuals.add(TouchPointVisual(id, change.position, hist.toList()))
                                        }
                                        activeTouchPoints = visuals

                                        val nowNano = SystemClock.elapsedRealtimeNanos()
                                        val primary = pressed.first()
                                        val histCount = primary.historical.size
                                        if (histCount > 0) {
                                            val prevNano = timestampsNano.lastOrNull() ?: (nowNano - (histCount * 5_000_000L))
                                            val span = (nowNano - prevNano).coerceAtLeast(1_000_000L)
                                            val step = span / (histCount + 1)
                                            for (h in 0 until histCount) {
                                                timestampsNano.add(prevNano + (h + 1) * step)
                                            }
                                        }
                                        timestampsNano.add(nowNano)

                                        while (timestampsNano.size > 32) {
                                            timestampsNano.removeAt(0)
                                        }

                                        val deltas = ArrayList<Double>(timestampsNano.size)
                                        for (i in 1 until timestampsNano.size) {
                                            val dtMs = (timestampsNano[i] - timestampsNano[i - 1]) / 1_000_000.0
                                            if (dtMs in 0.8..120.0) {
                                                deltas.add(dtMs)
                                            }
                                        }

                                        if (deltas.isNotEmpty() || pressed.isNotEmpty()) {
                                            touchSample = computeTouchSampleMetrics(deltas, pressed.size, maxFingersCount)
                                        }
                                    }
                                }
                            },
                        color = HudElevated,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            width = 1.5.dp,
                            color = if (activeTouchPoints.isNotEmpty()) HudCyan else HudBorder
                        )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            // 1. Center hint icon & description - smoothly dims when fingers are active on pad
                            val hintAlpha by animateFloatAsState(
                                targetValue = if (activeTouchPoints.isNotEmpty()) 0.08f else 1.0f,
                                label = "TouchHintAlpha"
                            )
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .padding(16.dp)
                                    .alpha(hintAlpha)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TouchApp,
                                    contentDescription = null,
                                    tint = if ((touchSample.rateHz ?: 0) > 0) HudCyan else HudMuted,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = if ((touchSample.rateHz ?: 0) > 0)
                                        "Mendeteksi Sentuhan Aktif (${touchSample.activeFingers} Jari)"
                                    else
                                        hudString(R.string.hud_touch_pad_hint),
                                    fontSize = 12.sp,
                                    fontWeight = if ((touchSample.rateHz ?: 0) > 0) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if ((touchSample.rateHz ?: 0) > 0) HudCyan else HudMuted,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Area luas untuk uji multi-finger swipe & tap gesture",
                                    fontSize = 10.5.sp,
                                    color = HudMuted.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center
                                )
                            }

                            // 2. High-Precision Real-Time Visual Touch Canvas (Show Taps & Motion Trails)
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                if (activeTouchPoints.isNotEmpty()) {
                                    // A. Draw subtle crosshair laser guidelines intersecting at each touch point
                                    for (tp in activeTouchPoints) {
                                        val pos = tp.position
                                        drawLine(
                                            color = HudCyan.copy(alpha = 0.12f),
                                            start = Offset(0f, pos.y),
                                            end = Offset(size.width, pos.y),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                        drawLine(
                                            color = HudCyan.copy(alpha = 0.12f),
                                            start = Offset(pos.x, 0f),
                                            end = Offset(pos.x, size.height),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                    }

                                    // B. Draw neon motion trails following each finger's path
                                    for (tp in activeTouchPoints) {
                                        if (tp.history.size > 1) {
                                            val trailPath = Path().apply {
                                                moveTo(tp.history[0].x, tp.history[0].y)
                                                for (i in 1 until tp.history.size) {
                                                    lineTo(tp.history[i].x, tp.history[i].y)
                                                }
                                            }
                                            drawPath(
                                                path = trailPath,
                                                color = HudCyan.copy(alpha = 0.45f),
                                                style = Stroke(
                                                    width = 3.dp.toPx(),
                                                    cap = StrokeCap.Round,
                                                    join = StrokeJoin.Round
                                                )
                                            )
                                        }
                                    }

                                    // C. Draw Android System Tap-inspired Touch Discs
                                    activeTouchPoints.forEachIndexed { index, tp ->
                                        val pos = tp.position

                                        // Outer radar glowing pulse ring
                                        drawCircle(
                                            color = HudCyan.copy(alpha = 0.28f),
                                            radius = 24.dp.toPx(),
                                            center = pos,
                                            style = Stroke(width = 1.5.dp.toPx())
                                        )

                                        // Middle translucent system tap disc (exact replica of Android "Show Taps" disc)
                                        drawCircle(
                                            color = Color.White.copy(alpha = 0.45f),
                                            radius = 16.dp.toPx(),
                                            center = pos
                                        )

                                        // Inner glowing cyan core
                                        drawCircle(
                                            color = HudCyan,
                                            radius = 7.dp.toPx(),
                                            center = pos
                                        )

                                        // Pinpoint precision center dot
                                        drawCircle(
                                            color = Color.White,
                                            radius = 2.5.dp.toPx(),
                                            center = pos
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Stat Grid Real-Time
                    HudStatGrid(listOf(
                        hudString(R.string.hud_touch_rate) to (touchSample.rateHz?.let { "$it Hz" } ?: "—"),
                        hudString(R.string.hud_touch_interval) to (touchSample.intervalMs?.let { String.format(java.util.Locale.US, "%.1f ms", it) } ?: "—"),
                        hudString(R.string.hud_touch_fingers) to (if (touchSample.maxFingers > 0) hudString(R.string.hud_touch_fingers_val, touchSample.maxFingers) else "—")
                    ))
                }
            }
        }
    }
}
