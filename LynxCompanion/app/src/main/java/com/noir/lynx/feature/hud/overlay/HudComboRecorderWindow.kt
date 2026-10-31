package com.noir.lynx.feature.hud.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.combo.HudComboManager
import com.noir.lynx.feature.hud.combo.MacroCombo
import com.noir.lynx.feature.hud.combo.RecordedTouch
import com.noir.lynx.feature.hud.ui.HudCyan
import com.noir.lynx.feature.hud.ui.HudRed
import com.noir.lynx.feature.hud.ui.hudString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Universal Precision Combo Macro Recorder Window.
 * 100% transparent visual macro editor adhering to the XArena-style reference design:
 * - Top Bar: [ ( X ) ]  [ Nama: Kombo Cepat 1 ✎ ]  [ Rekam Lagi 🔴 ]  [ ( ✓ ) ]
 * - Draggable Trigger Marker C₁ (allows placing where the floating in-game trigger appears)
 * - Sequential Numbered Tap Markers ①, ②, ③... with sub-pixel ratio accuracy & delta timing
 */
internal class HudComboRecorderWindow(
    private val context: Context,
    scope: CoroutineScope,
    lifecycle: LifecycleOwner,
    savedState: SavedStateRegistryOwner,
    private val comboManager: HudComboManager,
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
        title = "LynxComboRecorderOverlay"
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

    fun setFocusable(focusable: Boolean) {
        if (focusable) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        runCatching { windows.updateViewLayout(host, params) }
    }

    init {
        host.setViewTreeLifecycleOwner(lifecycle)
        host.setViewTreeSavedStateRegistryOwner(savedState)
        compose.setContent {
            ComboRecorderContent(
                comboManager = comboManager,
                onSetFocusable = ::setFocusable,
                onSave = { updatedCombo ->
                    comboManager.updateCombo(updatedCombo)
                    comboManager.setEditing(null)
                    onClosePlacer(true)
                },
                onCancel = {
                    comboManager.setEditing(null)
                    onClosePlacer(true)
                }
            )
        }
        host.addView(compose)
        host.visibility = View.GONE
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        runCatching { windows.addView(host, params) }

        visibilityJob = scope.launch {
            comboManager.editingComboId.collect { editingId ->
                if (editingId != null) {
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
private fun ComboRecorderContent(
    comboManager: HudComboManager,
    onSetFocusable: (Boolean) -> Unit,
    onSave: (MacroCombo) -> Unit,
    onCancel: () -> Unit
) {
    val editingId by comboManager.editingComboId.collectAsState()
    val allCombos by comboManager.combos.collectAsState()
    val currentCombo = remember(editingId, allCombos) {
        allCombos.find { it.id == editingId } ?: MacroCombo()
    }

    var draftName by remember(currentCombo) { mutableStateOf(currentCombo.name) }
    var draftTaps by remember(currentCombo) { mutableStateOf(currentCombo.taps) }
    var triggerXRatio by remember(currentCombo) { mutableFloatStateOf(currentCombo.triggerXRatio) }
    var triggerYRatio by remember(currentCombo) { mutableFloatStateOf(currentCombo.triggerYRatio) }

    var isRecording by rememberSaveable { mutableStateOf(false) }
    var lastTapTimeMs by remember { mutableLongStateOf(0L) }
    var selectedTapIndex by rememberSaveable { mutableIntStateOf(-1) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }

    // Safety watchdog: auto-cancel recorder overlay after 40s of inactivity to prevent screen lockouts
    LaunchedEffect(isRecording, draftTaps.size) {
        if (!isRecording) {
            delay(40_000L)
            onCancel()
        }
    }

    val view = LocalView.current
    val density = LocalDensity.current
    val badgeSizeDp = 44.dp
    val badgeRadiusPx = with(density) { (badgeSizeDp / 2).toPx() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .pointerInput(isRecording, draftTaps.size) {
                detectTapGestures { tapOffset ->
                    val maxW = size.width.toFloat()
                    val maxH = size.height.toFloat()
                    if (maxW > 0f && maxH > 0f) {
                        val ratioX = (tapOffset.x / maxW).coerceIn(0.005f, 0.995f)
                        val ratioY = (tapOffset.y / maxH).coerceIn(0.005f, 0.995f)

                        if (isRecording) {
                            if (draftTaps.size < 25) {
                                val now = SystemClock.uptimeMillis()
                                val delayMs = if (lastTapTimeMs == 0L || draftTaps.isEmpty()) 0L else (now - lastTapTimeMs).coerceIn(10L, 5000L)
                                lastTapTimeMs = now

                                val newTap = RecordedTouch(ratioX, ratioY, delayMs)
                                draftTaps = draftTaps + newTap
                                selectedTapIndex = draftTaps.lastIndex
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            } else {
                                isRecording = false
                            }
                        } else {
                            // Tap outside when not recording deselects
                            selectedTapIndex = -1
                        }
                    }
                }
            }
    ) {
        val maxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val maxH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val isLandscape = maxW > maxH

        val topPadding = if (isLandscape) 18.dp else 38.dp
        val sidePadding = if (isLandscape) 28.dp else 12.dp
        val buttonSize = if (isLandscape) 46.dp else 42.dp
        val pillHeight = if (isLandscape) 46.dp else 42.dp
        val pillPaddingH = if (isLandscape) 14.dp else 9.dp
        val centerSpacing = if (isLandscape) 10.dp else 6.dp
        val nameTextMaxW = if (isLandscape) 160.dp else 78.dp
        val fontSize = if (isLandscape) 13.sp else 11.5.sp

        // 1. In-Game Trigger Button Placement Marker (C₁, C₂, etc.)
        val triggerPixelX = maxW * triggerXRatio
        val triggerPixelY = maxH * triggerYRatio
        val triggerLeft = (triggerPixelX - badgeRadiusPx).roundToInt()
        val triggerTop = (triggerPixelY - badgeRadiusPx).roundToInt()

        Column(
            modifier = Modifier
                .offset { IntOffset(triggerLeft, triggerTop) }
                .wrapContentSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier
                    .size(badgeSizeDp)
                    .pointerInput(maxW, maxH) {
                        detectDragGestures(
                            onDragStart = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val curX = maxW * triggerXRatio
                                val curY = maxH * triggerYRatio
                                val newX = (curX + dragAmount.x).coerceIn(0f, maxW)
                                val newY = (curY + dragAmount.y).coerceIn(0f, maxH)
                                triggerXRatio = (newX / maxW).coerceIn(0.01f, 0.99f)
                                triggerYRatio = (newY / maxH).coerceIn(0.01f, 0.99f)
                            }
                        )
                    },
                shape = CircleShape,
                color = Color(0xEB121216),
                border = BorderStroke(2.dp, HudCyan),
                shadowElevation = 10.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = currentCombo.displayBadge,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = HudCyan
                    )
                }
            }

            Spacer(Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xD90A0A0E))
                    .border(BorderStroke(0.7.dp, HudCyan), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Tombol Pemicu",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HudCyan
                )
            }
        }

        // 2. Sequential Numbered Tap Badges (①, ②, ③...)
        draftTaps.forEachIndexed { index, touch ->
            val isSelected = index == selectedTapIndex
            val centerX = maxW * touch.xRatio
            val centerY = maxH * touch.yRatio
            val tapLeft = (centerX - badgeRadiusPx * 0.8f).roundToInt()
            val tapTop = (centerY - badgeRadiusPx * 0.8f).roundToInt()

            Column(
                modifier = Modifier
                    .offset { IntOffset(tapLeft, tapTop) }
                    .wrapContentSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier
                        .size(badgeSizeDp * 0.8f)
                        .pointerInput(index, maxW, maxH) {
                            detectDragGestures(
                                onDragStart = {
                                    selectedTapIndex = index
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val curX = maxW * draftTaps[index].xRatio
                                    val curY = maxH * draftTaps[index].yRatio
                                    val newX = (curX + dragAmount.x).coerceIn(0f, maxW)
                                    val newY = (curY + dragAmount.y).coerceIn(0f, maxH)
                                    val newRatioX = (newX / maxW).coerceIn(0.005f, 0.995f)
                                    val newRatioY = (newY / maxH).coerceIn(0.005f, 0.995f)
                                    draftTaps = draftTaps.toMutableList().also {
                                        it[index] = it[index].copy(xRatio = newRatioX, yRatio = newRatioY)
                                    }
                                }
                            )
                        }
                        .clickable {
                            selectedTapIndex = index
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        },
                    shape = CircleShape,
                    color = Color(0xD91B1B20),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.2.dp,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.45f)
                    ),
                    shadowElevation = if (isSelected) 8.dp else 3.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${index + 1}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                if (touch.delayBeforeMs > 0L) {
                    Spacer(Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xD90A0A0E))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "+${touch.delayBeforeMs}ms",
                            fontSize = 8.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFAAAAAA)
                        )
                    }
                }
            }
        }

        // 3. Minimalist Top Bar matching reference screenshot
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = topPadding, start = sidePadding, end = sidePadding)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Close Button ( X ) with high-visibility safety styling
            Surface(
                onClick = onCancel,
                shape = CircleShape,
                color = HudRed.copy(alpha = 0.25f),
                border = BorderStroke(1.2.dp, HudRed.copy(alpha = 0.8f)),
                shadowElevation = 8.dp,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = hudString(R.string.hud_magic_cancel),
                        tint = HudRed,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Center: Name Pill & Record Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(centerSpacing)
            ) {
                // Name Pill: "Nama: Kombo Cepat 1 ✎"
                Surface(
                    onClick = {
                        showRenameDialog = true
                        onSetFocusable(true)
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xD91C1C20),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                    shadowElevation = 8.dp,
                    modifier = Modifier.height(pillHeight)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = pillPaddingH),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Nama: ",
                            fontSize = fontSize,
                            color = Color(0xFFAAAAAA)
                        )
                        Text(
                            text = draftName,
                            fontSize = fontSize,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = nameTextMaxW)
                        )
                        Spacer(Modifier.width(1.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = Color(0xFF8E8E93),
                            modifier = Modifier.size(if (isLandscape) 13.dp else 11.dp)
                        )
                    }
                }

                // Record Pill: "Rekam Lagi 🔴" / "Merekam... ⏹"
                Surface(
                    onClick = {
                        if (isRecording) {
                            isRecording = false
                        } else {
                            draftTaps = emptyList()
                            selectedTapIndex = -1
                            lastTapTimeMs = 0L
                            isRecording = true
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xD91C1C20),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isRecording) HudRed else Color.White.copy(alpha = 0.20f)
                    ),
                    shadowElevation = 8.dp,
                    modifier = Modifier.height(pillHeight)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = pillPaddingH),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isRecording) {
                                hudString(R.string.hud_magic_recording)
                            } else if (draftTaps.isEmpty()) {
                                hudString(R.string.hud_magic_record)
                            } else {
                                hudString(R.string.hud_magic_record_again)
                            },
                            fontSize = fontSize,
                            fontWeight = if (isRecording) FontWeight.Bold else FontWeight.Medium,
                            color = if (isRecording) HudRed else Color.White
                        )

                        if (isRecording) {
                            Box(
                                modifier = Modifier
                                    .size(if (isLandscape) 13.dp else 11.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(HudRed)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(if (isLandscape) 18.dp else 15.dp)
                                    .clip(CircleShape)
                                    .border(1.2.dp, Color.White, CircleShape)
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF3B30))
                            )
                        }
                    }
                }
            }

            // Right: Save Button ( ✓ )
            Surface(
                onClick = {
                    onSave(
                        currentCombo.copy(
                            name = draftName,
                            triggerXRatio = triggerXRatio,
                            triggerYRatio = triggerYRatio,
                            taps = draftTaps
                        )
                    )
                },
                shape = CircleShape,
                color = Color(0xD91C1C20),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                shadowElevation = 8.dp,
                modifier = Modifier.size(buttonSize)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = hudString(R.string.hud_magic_save_only),
                        tint = Color.White,
                        modifier = Modifier.size(if (isLandscape) 22.dp else 19.dp)
                    )
                }
            }
        }

        // 4. Floating Action Pill if a tap is selected (Delete Tap)
        if (!isRecording && selectedTapIndex in draftTaps.indices) {
            Surface(
                onClick = {
                    draftTaps = draftTaps.filterIndexed { i, _ -> i != selectedTapIndex }
                    selectedTapIndex = -1
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                },
                shape = RoundedCornerShape(20.dp),
                color = Color(0xF018181C),
                border = BorderStroke(1.dp, HudRed.copy(alpha = 0.6f)),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 36.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = HudRed, modifier = Modifier.size(16.dp))
                    Text(
                        text = "Hapus Titik Sentuh ${selectedTapIndex + 1}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }
        }

        // 5. Toast Hint when recording
        AnimatedVisibility(
            visible = isRecording,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xF0121216),
                border = BorderStroke(1.dp, HudRed.copy(alpha = 0.5f)),
                shadowElevation = 10.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(HudRed)
                    )
                    Text(
                        text = "Ketuk layar secara alami untuk merekam urutan & jeda waktu kombo",
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }
            }
        }

        // 6. Rename Dialog
        if (showRenameDialog) {
            var textInput by rememberSaveable { mutableStateOf(draftName) }
            Dialog(
                onDismissRequest = {
                    showRenameDialog = false
                    onSetFocusable(false)
                }
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xF216161A),
                    border = BorderStroke(1.dp, HudCyan.copy(alpha = 0.45f)),
                    shadowElevation = 16.dp,
                    modifier = Modifier.fillMaxWidth(0.90f)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = hudString(R.string.hud_magic_edit_name_title),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it.take(30) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = HudCyan,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                                cursorColor = HudCyan
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    showRenameDialog = false
                                    onSetFocusable(false)
                                }
                            ) {
                                Text(hudString(R.string.hud_magic_cancel), color = Color(0xFFAAAAAA))
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (textInput.isNotBlank()) {
                                        draftName = textInput.trim()
                                    }
                                    showRenameDialog = false
                                    onSetFocusable(false)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = HudCyan,
                                    contentColor = Color.Black
                                )
                            ) {
                                Text(hudString(R.string.hud_magic_save_only), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
