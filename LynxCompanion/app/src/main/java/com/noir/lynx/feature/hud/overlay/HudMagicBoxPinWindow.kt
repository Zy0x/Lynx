package com.noir.lynx.feature.hud.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.ui.hudString
import com.noir.lynx.feature.hud.addins.HudGamingAddins
import com.noir.lynx.feature.hud.addins.HudMagicBox
import com.noir.lynx.feature.hud.addins.HudTapPoint
import com.noir.lynx.feature.hud.ui.HudBackground
import com.noir.lynx.feature.hud.ui.HudBlue
import com.noir.lynx.feature.hud.ui.HudBlueBadge
import com.noir.lynx.feature.hud.ui.HudBorder
import com.noir.lynx.feature.hud.ui.HudCard
import com.noir.lynx.feature.hud.ui.HudCyan
import com.noir.lynx.feature.hud.ui.HudElevated
import com.noir.lynx.feature.hud.ui.HudMuted
import com.noir.lynx.feature.hud.ui.HudRed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Interactive full-screen floating overlay for visual Magic Box point placement.
 * Allows gamers to drag visual pins or tap anywhere on top of games without guessing coordinates.
 */
internal class HudMagicBoxPinWindow(
    private val context: Context,
    scope: CoroutineScope,
    lifecycle: LifecycleOwner,
    savedState: SavedStateRegistryOwner,
    private val magic: HudMagicBox,
    private val onClosePlacer: (reopenPanel: Boolean) -> Unit
) {
    private val windows = context.getSystemService(WindowManager::class.java)
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT
    ).apply {
        title = "LynxMagicBoxPinOverlay"
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= 30) {
            setFitInsetsTypes(0)
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= 28) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private val host: FrameLayout = FrameLayout(context)
    private val compose = ComposeView(context)
    private val visibilityJob: Job

    init {
        host.setViewTreeLifecycleOwner(lifecycle)
        host.setViewTreeSavedStateRegistryOwner(savedState)
        compose.setContent {
            MagicBoxPinPlacerContent(
                magic = magic,
                onSave = { updatedPoints, startImmediately ->
                    magic.setPoints(updatedPoints)
                    magic.stopPlacing()
                    onClosePlacer(!startImmediately)
                    if (startImmediately) {
                        HudGamingAddins.current?.startMagic()
                    }
                },
                onCancel = {
                    magic.stopPlacing()
                    onClosePlacer(true)
                }
            )
        }
        host.addView(compose)
        host.visibility = View.GONE
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        runCatching { windows.addView(host, params) }

        visibilityJob = scope.launch {
            magic.isPlacing.collect { placing ->
                if (placing) {
                    params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                    runCatching { windows.updateViewLayout(host, params) }
                    host.visibility = View.VISIBLE
                } else {
                    host.visibility = View.GONE
                    params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    runCatching { windows.updateViewLayout(host, params) }
                }
            }
        }
    }

    fun close() {
        visibilityJob.cancel()
        runCatching { windows.removeView(host) }
        compose.disposeComposition()
    }
}

@Composable
private fun MagicBoxPinPlacerContent(
    magic: HudMagicBox,
    onSave: (List<HudTapPoint>, Boolean) -> Unit,
    onCancel: () -> Unit
) {
    val livePoints by magic.points.collectAsState()
    var draftPoints by remember(livePoints) { mutableStateOf(livePoints.ifEmpty { listOf(HudTapPoint()) }) }
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    if (selectedIndex !in draftPoints.indices) {
        selectedIndex = (draftPoints.size - 1).coerceAtLeast(0)
    }

    val density = LocalDensity.current
    val pinSizeDp = 56.dp
    val pinSizePx = with(density) { pinSizeDp.toPx() }
    val extraWidthDp = 44.dp
    val totalWidthPx = with(density) { (pinSizeDp + extraWidthDp).toPx() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.20f))
            .pointerInput(selectedIndex, draftPoints.size) {
                detectTapGestures { tapOffset ->
                    val maxW = size.width.toFloat()
                    val maxH = size.height.toFloat()
                    if (maxW > 0f && maxH > 0f && selectedIndex in draftPoints.indices) {
                        val pctX = ((tapOffset.x / maxW) * 100).roundToInt().coerceIn(1, 99)
                        val pctY = ((tapOffset.y / maxH) * 100).roundToInt().coerceIn(1, 99)
                        draftPoints = draftPoints.toMutableList().also {
                            it[selectedIndex] = HudTapPoint(pctX, pctY)
                        }
                    }
                }
            }
    ) {
        val maxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val maxH = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        // 1. Interactive Pins Layer
        draftPoints.forEachIndexed { index, pt ->
            val isSelected = index == selectedIndex
            val centerX = maxW * pt.x / 100f
            val centerY = maxH * pt.y / 100f
            val left = (centerX - totalWidthPx / 2f).roundToInt()
            val top = (centerY - pinSizePx / 2f).roundToInt()

            Column(
                modifier = Modifier
                    .offset { IntOffset(left, top) }
                    .width(pinSizeDp + extraWidthDp)
                    .wrapContentHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Pin Target Reticle Ring
                Surface(
                    modifier = Modifier
                        .size(pinSizeDp)
                        .pointerInput(index, maxW, maxH) {
                            detectDragGestures(
                                onDragStart = { selectedIndex = index },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val curX = maxW * draftPoints[index].x / 100f
                                    val curY = maxH * draftPoints[index].y / 100f
                                    val newX = (curX + dragAmount.x).coerceIn(0f, maxW)
                                    val newY = (curY + dragAmount.y).coerceIn(0f, maxH)
                                    val newPctX = ((newX / maxW) * 100).roundToInt().coerceIn(1, 99)
                                    val newPctY = ((newY / maxH) * 100).roundToInt().coerceIn(1, 99)
                                    draftPoints = draftPoints.toMutableList().also {
                                        it[index] = HudTapPoint(newPctX, newPctY)
                                    }
                                }
                            )
                        }
                        .clickable { selectedIndex = index },
                    shape = CircleShape,
                    color = HudCard.copy(alpha = 0.90f),
                    border = BorderStroke(
                        width = if (isSelected) 2.5.dp else 1.5.dp,
                        color = if (isSelected) HudCyan else HudCyan.copy(alpha = 0.55f)
                    ),
                    shadowElevation = if (isSelected) 10.dp else 3.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // Center Crosshair Lines
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val c = size.width / 2f
                            val arm = size.width * 0.20f
                            val lineColor = if (isSelected) HudCyan else Color.White.copy(alpha = 0.5f)
                            drawLine(lineColor, Offset(c - arm, c), Offset(c + arm, c), strokeWidth = 2.5f)
                            drawLine(lineColor, Offset(c, c - arm), Offset(c, c + arm), strokeWidth = 2.5f)
                        }
                        Text(
                            text = "${index + 1}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isSelected) HudCyan else Color.White
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Coordinate Pill below pin
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(HudBackground.copy(alpha = 0.92f))
                        .border(BorderStroke(0.6.dp, if (isSelected) HudCyan else HudBorder), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "X:${pt.x}% · Y:${pt.y}%",
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        color = if (isSelected) HudCyan else HudMuted
                    )
                }
            }
        }

        // 2. Floating Top Control Island
        Surface(
            onClick = {},
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp, start = 12.dp, end = 12.dp)
                .fillMaxWidth(0.96f)
                .wrapContentHeight(),
            color = HudBackground.copy(alpha = 0.95f),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, HudCyan.copy(alpha = 0.45f)),
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(HudCyan.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = null,
                                tint = HudCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Column {
                            Text(
                                text = hudString(R.string.hud_magic_placing_title),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = hudString(R.string.hud_magic_placing_desc),
                                fontSize = 10.5.sp,
                                color = HudMuted
                            )
                        }
                    }

                    // Cancel 'X' Button
                    Surface(
                        onClick = onCancel,
                        shape = CircleShape,
                        color = HudElevated,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = hudString(R.string.hud_magic_cancel),
                                tint = HudMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Point Chips & Add/Remove Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    draftPoints.forEachIndexed { index, _ ->
                        val active = selectedIndex == index
                        FilterChip(
                            selected = active,
                            onClick = { selectedIndex = index },
                            label = {
                                Text(
                                    text = hudString(R.string.hud_magic_point, index + 1),
                                    fontSize = 11.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = HudElevated,
                                labelColor = HudMuted,
                                selectedContainerColor = HudBlueBadge.copy(alpha = 0.45f),
                                selectedLabelColor = Color.White
                            ),
                            border = BorderStroke(1.dp, if (active) HudCyan else HudBorder),
                            modifier = Modifier.heightIn(min = 36.dp)
                        )
                    }

                    // Add Point Chip
                    if (draftPoints.size < 5) {
                        Surface(
                            onClick = {
                                val next = draftPoints + HudTapPoint()
                                draftPoints = next
                                selectedIndex = next.lastIndex
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = HudElevated,
                            border = BorderStroke(1.dp, HudBorder),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Add, null, tint = HudCyan, modifier = Modifier.size(14.dp))
                                Text(hudString(R.string.hud_magic_add), fontSize = 11.sp, color = HudCyan)
                            }
                        }
                    }

                    // Remove Point Chip
                    if (draftPoints.size > 1) {
                        Surface(
                            onClick = {
                                val removeIdx = selectedIndex
                                val next = draftPoints.filterIndexed { i, _ -> i != removeIdx }
                                draftPoints = next
                                selectedIndex = (removeIdx - 1).coerceAtLeast(0)
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = HudElevated,
                            border = BorderStroke(1.dp, HudRed.copy(alpha = 0.4f)),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Delete, null, tint = HudRed, modifier = Modifier.size(14.dp))
                                Text(hudString(R.string.hud_magic_remove), fontSize = 11.sp, color = HudRed)
                            }
                        }
                    }
                }

                // Action Confirm Buttons Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Save Button
                    Surface(
                        onClick = { onSave(draftPoints, false) },
                        shape = RoundedCornerShape(12.dp),
                        color = HudElevated,
                        border = BorderStroke(1.dp, HudBlue.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Check, null, tint = HudBlue, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(hudString(R.string.hud_magic_save_only), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }

                    // Save & Run Immediately Button
                    Surface(
                        onClick = { onSave(draftPoints, true) },
                        shape = RoundedCornerShape(12.dp),
                        color = HudCyan,
                        modifier = Modifier
                            .weight(1.3f)
                            .fillMaxHeight()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(hudString(R.string.hud_magic_save_and_run), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }
            }
        }
    }
}
