package com.noir.lynx.feature.hud.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
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
import com.noir.lynx.feature.hud.ui.hudString
import com.noir.lynx.feature.hud.addins.HudGamingAddins
import com.noir.lynx.feature.hud.addins.HudMagicBox
import com.noir.lynx.feature.hud.addins.HudTapPoint
import com.noir.lynx.feature.hud.ui.HudCyan
import com.noir.lynx.feature.hud.ui.HudRed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Ultra-clean, high-precision visual Magic Box Combo Recorder & Pin Placer overlay.
 * Inspired by professional Game Space macro recorders with minimal, unobtrusive HUD.
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
            MagicBoxPinPlacerContent(
                magic = magic,
                onSetFocusable = ::setFocusable,
                onSave = { updatedPoints, updatedName ->
                    magic.setComboName(updatedName)
                    magic.setPoints(updatedPoints)
                    magic.stopPlacing()
                    onClosePlacer(true)
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
    onSetFocusable: (Boolean) -> Unit,
    onSave: (List<HudTapPoint>, String) -> Unit,
    onCancel: () -> Unit
) {
    val livePoints by magic.points.collectAsState()
    val liveName by magic.comboName.collectAsState()

    var draftPoints by remember(livePoints) { mutableStateOf(livePoints.ifEmpty { listOf(HudTapPoint()) }) }
    var comboName by remember(liveName) { mutableStateOf(liveName) }
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    var isRecording by rememberSaveable { mutableStateOf(false) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }

    val view = LocalView.current
    val density = LocalDensity.current
    val pinSizeDp = 42.dp
    val pinRadiusPx = with(density) { (pinSizeDp / 2).toPx() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .pointerInput(isRecording, selectedIndex, draftPoints.size) {
                detectTapGestures { tapOffset ->
                    val maxW = size.width.toFloat()
                    val maxH = size.height.toFloat()
                    if (maxW > 0f && maxH > 0f) {
                        val ratioX = (tapOffset.x / maxW).coerceIn(0.005f, 0.995f)
                        val ratioY = (tapOffset.y / maxH).coerceIn(0.005f, 0.995f)
                        val pctX = (ratioX * 100f).roundToInt().coerceIn(1, 99)
                        val pctY = (ratioY * 100f).roundToInt().coerceIn(1, 99)

                        if (isRecording) {
                            if (draftPoints.size < 10) {
                                val nextPt = HudTapPoint(pctX, pctY, ratioX, ratioY)
                                draftPoints = draftPoints + nextPt
                                selectedIndex = draftPoints.lastIndex
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            } else {
                                isRecording = false
                            }
                        } else {
                            if (selectedIndex in draftPoints.indices) {
                                draftPoints = draftPoints.toMutableList().also {
                                    it[selectedIndex] = HudTapPoint(pctX, pctY, ratioX, ratioY, it[selectedIndex].delayMs)
                                }
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
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

        // 1. Sleek C1..C10 Pins Layer
        draftPoints.forEachIndexed { index, pt ->
            val isSelected = index == selectedIndex
            val centerX = maxW * pt.xRatio
            val centerY = maxH * pt.yRatio
            val left = (centerX - pinRadiusPx).roundToInt()
            val top = (centerY - pinRadiusPx).roundToInt()

            val subscript = when (index) {
                0 -> "₁"
                1 -> "₂"
                2 -> "₃"
                3 -> "₄"
                4 -> "₅"
                5 -> "₆"
                6 -> "₇"
                7 -> "₈"
                8 -> "₉"
                9 -> "₁₀"
                else -> "${index + 1}"
            }

            Column(
                modifier = Modifier
                    .offset { IntOffset(left, top) }
                    .wrapContentSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier
                        .size(pinSizeDp)
                        .pointerInput(index, maxW, maxH) {
                            detectDragGestures(
                                onDragStart = {
                                    selectedIndex = index
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val curX = maxW * draftPoints[index].xRatio
                                    val curY = maxH * draftPoints[index].yRatio
                                    val newX = (curX + dragAmount.x).coerceIn(0f, maxW)
                                    val newY = (curY + dragAmount.y).coerceIn(0f, maxH)
                                    val newRatioX = (newX / maxW).coerceIn(0.005f, 0.995f)
                                    val newRatioY = (newY / maxH).coerceIn(0.005f, 0.995f)
                                    val newPctX = (newRatioX * 100f).roundToInt().coerceIn(1, 99)
                                    val newPctY = (newRatioY * 100f).roundToInt().coerceIn(1, 99)
                                    draftPoints = draftPoints.toMutableList().also {
                                        it[index] = HudTapPoint(newPctX, newPctY, newRatioX, newRatioY, it[index].delayMs)
                                    }
                                }
                            )
                        }
                        .clickable {
                            selectedIndex = index
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        },
                    shape = CircleShape,
                    color = Color(0xD9101014),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.2.dp,
                        color = if (isSelected) HudCyan else Color.White.copy(alpha = 0.40f)
                    ),
                    shadowElevation = if (isSelected) 8.dp else 3.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "C$subscript",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) HudCyan else Color.White
                        )
                    }
                }

                // Precision coordinate badge only visible when pin is selected
                if (isSelected) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xD90A0A0E))
                            .border(BorderStroke(0.7.dp, HudCyan), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "X:${(pt.xRatio * 100).roundToInt()}% · Y:${(pt.yRatio * 100).roundToInt()}%",
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = HudCyan
                        )
                    }
                }
            }
        }

        // 2. Minimalist Top Bar (Left: Close, Center: Name & Record, Right: Done)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = topPadding, start = sidePadding, end = sidePadding)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Close Button ( X )
            Surface(
                onClick = onCancel,
                shape = CircleShape,
                color = Color(0xD91C1C20),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                shadowElevation = 8.dp,
                modifier = Modifier.size(buttonSize)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = hudString(R.string.hud_magic_cancel),
                        tint = Color.White,
                        modifier = Modifier.size(if (isLandscape) 20.dp else 18.dp)
                    )
                }
            }

            // Center: Name Pill & Record Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(centerSpacing)
            ) {
                // Name Pill: "Nama: Kombo Cepat1 ✎"
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
                            text = comboName,
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
                            draftPoints = emptyList()
                            selectedIndex = -1
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
                            } else if (draftPoints.isEmpty()) {
                                hudString(R.string.hud_magic_record)
                            } else {
                                hudString(R.string.hud_magic_record_again)
                            },
                            fontSize = fontSize,
                            fontWeight = if (isRecording) FontWeight.Bold else FontWeight.Medium,
                            color = if (isRecording) HudRed else Color.White
                        )

                        if (isRecording) {
                            // Stop square
                            Box(
                                modifier = Modifier
                                    .size(if (isLandscape) 13.dp else 11.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(HudRed)
                            )
                        } else {
                            // Red record circle with white ring matching reference screenshot
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
                    onSave(draftPoints, comboName)
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

        // 3. Floating Hint Toast when recording
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
                        text = hudString(R.string.hud_magic_tap_record_hint),
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }
            }
        }

        // 4. Rename Dialog
        if (showRenameDialog) {
            var textInput by rememberSaveable { mutableStateOf(comboName) }
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
                                        comboName = textInput.trim()
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
