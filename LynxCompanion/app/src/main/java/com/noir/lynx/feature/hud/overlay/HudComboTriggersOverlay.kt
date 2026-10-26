package com.noir.lynx.feature.hud.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Region
import android.os.Build
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.combo.HudComboManager
import com.noir.lynx.feature.hud.combo.MacroCombo
import com.noir.lynx.feature.hud.ui.HudCyan
import com.noir.lynx.feature.hud.ui.HudRed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Universal In-Game Floating Macro Triggers Overlay.
 * Renders floating trigger buttons (C₁, C₂, etc.) over the game interface:
 * - Single Tap: Executes root input combo sequence instantly with a glowing cyan ripple
 * - Long-Press Drag: Repositions trigger buttons anywhere on the display
 * - Auto-Hides when HUD panel or Recorder Window is active
 */
internal class HudComboTriggersOverlay(
    private val context: Context,
    scope: CoroutineScope,
    lifecycle: LifecycleOwner,
    savedState: SavedStateRegistryOwner,
    private val comboManager: HudComboManager,
    private val isHudOpenFlow: kotlinx.coroutines.flow.StateFlow<Boolean>
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
        title = "LynxComboTriggersOverlay"
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
            ComboTriggersContent(comboManager = comboManager)
        }
        host.addView(compose)
        host.visibility = View.GONE
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        setupTouchableInsets(host)
        runCatching { windows.addView(host, params) }

        visibilityJob = scope.launch {
            combine(
                comboManager.combos,
                comboManager.editingComboId,
                isHudOpenFlow,
                comboManager.foreground
            ) { combos, editingId, hudOpen, pkg ->
                val hasEnabled = combos.any { it.enabled && it.taps.isNotEmpty() }
                val isGame = isGamePackage(pkg)
                val shouldShow = hasEnabled && editingId == null && !hudOpen && isGame
                shouldShow
            }.collect { visible ->
                if (visible) {
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

    private fun setupTouchableInsets(view: View) {
        try {
            val listenerClass = Class.forName("android.view.ViewTreeObserver\$OnComputeInternalInsetsListener")
            val addMethod = ViewTreeObserver::class.java.getMethod("addOnComputeInternalInsetsListener", listenerClass)
            val insetsInfoClass = Class.forName("android.view.ViewTreeObserver\$InternalInsetsInfo")
            val setTouchableInsets = insetsInfoClass.getMethod("setTouchableInsets", Int::class.javaPrimitiveType)
            val touchableRegionField = insetsInfoClass.getField("touchableRegion")

            val proxy = java.lang.reflect.Proxy.newProxyInstance(
                listenerClass.classLoader,
                arrayOf(listenerClass)
            ) { _, method, args ->
                if (method.name == "onComputeInternalInsets" && args != null && args.isNotEmpty()) {
                    val info = args[0]
                    setTouchableInsets.invoke(info, 3) // TOUCHABLE_INSETS_REGION
                    val region = touchableRegionField.get(info) as? Region ?: Region()
                    region.setEmpty()

                    val density = context.resources.displayMetrics.density
                    val btnSizePx = (56f * density).toInt()
                    val radiusPx = btnSizePx / 2
                    val w = host.width.toFloat().coerceAtLeast(1f)
                    val h = host.height.toFloat().coerceAtLeast(1f)

                    val activeCombos = comboManager.combos.value.filter { it.enabled && it.taps.isNotEmpty() }
                    activeCombos.forEach { combo ->
                        val cx = (w * combo.triggerXRatio).toInt()
                        val cy = (h * combo.triggerYRatio).toInt()
                        region.union(Rect(cx - radiusPx, cy - radiusPx, cx + radiusPx, cy + radiusPx))
                    }
                }
                null
            }
            addMethod.invoke(view.viewTreeObserver, proxy)
        } catch (_: Throwable) {}
    }

    private fun isGamePackage(pkg: String): Boolean =
        pkg.isNotBlank() && pkg !in setOf(
            "com.android.systemui", "com.android.settings", "com.android.permissioncontroller",
            "com.google.android.permissioncontroller", "me.bmax.apatch", "me.weishu.kernelsu",
            "com.topjohnwu.magisk", "com.noir.lynx"
        )

    fun close() {
        visibilityJob.cancel()
        runCatching { windows.removeView(host) }
        compose.disposeComposition()
    }
}

@Composable
private fun ComboTriggersContent(
    comboManager: HudComboManager
) {
    val combos by comboManager.combos.collectAsState()
    val activeRunningId by comboManager.activeRunning.collectAsState()
    val enabledCombos = remember(combos) { combos.filter { it.enabled && it.taps.isNotEmpty() } }

    val density = LocalDensity.current
    val buttonSizeDp = 48.dp
    val buttonRadiusPx = with(density) { (buttonSizeDp / 2).toPx() }
    val view = LocalView.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        val maxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val maxH = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        enabledCombos.forEach { combo ->
            val isRunning = activeRunningId == combo.id
            var curXRatio by remember(combo.triggerXRatio) { mutableFloatStateOf(combo.triggerXRatio) }
            var curYRatio by remember(combo.triggerYRatio) { mutableFloatStateOf(combo.triggerYRatio) }
            var isDragging by remember { mutableStateOf(false) }

            val pixelX = maxW * curXRatio
            val pixelY = maxH * curYRatio
            val left = (pixelX - buttonRadiusPx).roundToInt()
            val top = (pixelY - buttonRadiusPx).roundToInt()

            // Pulsing animation when running
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1.0f,
                targetValue = if (isRunning) 1.25f else 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale"
            )

            Box(
                modifier = Modifier
                    .offset { IntOffset(left, top) }
                    .size(buttonSizeDp)
                    .pointerInput(combo.id, maxW, maxH) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                isDragging = true
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val curX = maxW * curXRatio
                                val curY = maxH * curYRatio
                                val newX = (curX + dragAmount.x).coerceIn(0f, maxW)
                                val newY = (curY + dragAmount.y).coerceIn(0f, maxH)
                                curXRatio = (newX / maxW).coerceIn(0.01f, 0.99f)
                                curYRatio = (newY / maxH).coerceIn(0.01f, 0.99f)
                            },
                            onDragEnd = {
                                isDragging = false
                                comboManager.updateCombo(combo.copy(triggerXRatio = curXRatio, triggerYRatio = curYRatio))
                            },
                            onDragCancel = {
                                isDragging = false
                            }
                        )
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        comboManager.executeCombo(combo.id)
                    },
                contentAlignment = Alignment.Center
            ) {
                // Outer glow ring when executing
                if (isRunning) {
                    Box(
                        modifier = Modifier
                            .size(buttonSizeDp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .border(2.dp, HudCyan, CircleShape)
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = if (isRunning) Color(0xF0003322) else Color(0xD9101014),
                    border = BorderStroke(
                        width = if (isRunning) 2.dp else 1.2.dp,
                        color = if (isRunning) HudCyan else Color.White.copy(alpha = 0.50f)
                    ),
                    shadowElevation = if (isRunning) 10.dp else 4.dp,
                    modifier = Modifier.size(buttonSizeDp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = combo.displayBadge,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isRunning) HudCyan else Color.White
                        )
                    }
                }
            }
        }
    }
}
