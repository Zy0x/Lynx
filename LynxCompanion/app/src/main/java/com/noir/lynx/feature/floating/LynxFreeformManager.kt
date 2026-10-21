package com.noir.lynx.feature.floating

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.noir.lynx.feature.floating.ui.FreeformMultiWindowOverlay
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * LynxFreeformManager — Advanced Root Freeform Window Engine (LMO-Freeform Architecture).
 * Manages real application tasks in Android Freeform mode with custom floating decor captions,
 * real-time corner resizing handles, minimize-to-bubble pill docks, and multi-app multitasking.
 */
@SuppressLint("StaticFieldLeak")
object LynxFreeformManager {
    private val mutableApps = MutableStateFlow<Map<Int, FreeformAppInstance>>(emptyMap())
    val activeApps = mutableApps.asStateFlow()

    private var appContext: Context? = null
    private var windowManager: WindowManager? = null
    private var coroutineScope: CoroutineScope? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var savedStateRegistryOwner: SavedStateRegistryOwner? = null

    private var decorView: ComposeView? = null
    private var decorParams: WindowManager.LayoutParams? = null

    fun init(
        context: Context,
        wm: WindowManager,
        scope: CoroutineScope,
        owner: LifecycleOwner,
        savedOwner: SavedStateRegistryOwner
    ) {
        appContext = context.applicationContext
        windowManager = wm
        coroutineScope = scope
        lifecycleOwner = owner
        savedStateRegistryOwner = savedOwner
    }

    fun destroy() {
        val wm = windowManager
        decorView?.let { view ->
            runCatching { wm?.removeView(view) }
            view.disposeComposition()
        }
        decorView = null
        decorParams = null
        mutableApps.value = emptyMap()
        appContext = null
        windowManager = null
        coroutineScope = null
        lifecycleOwner = null
        savedStateRegistryOwner = null
    }

    /**
     * Launch a REAL application in Freeform Mode with Root and attach Lynx decor frame.
     * Supports opening multiple real applications simultaneously.
     */
    fun launchRealApp(
        packageName: String,
        label: String,
        icon: Bitmap,
        widthPct: Int = 70,
        heightPct: Int = 65
    ) {
        val ctx = appContext ?: return
        val scope = coroutineScope ?: return

        scope.launch(Dispatchers.IO) {
            val dm = ctx.resources.displayMetrics
            val screenW = dm.widthPixels
            val screenH = dm.heightPixels

            val w = (screenW * (widthPct.coerceIn(35, 95) / 100f)).toInt()
            val h = (screenH * (heightPct.coerceIn(35, 90) / 100f)).toInt()

            // Cascade position slightly if other floating windows exist
            val count = mutableApps.value.size
            val offset = (count * 40).coerceAtMost(160)
            val left = (((screenW - w) / 2) + offset).coerceIn(0, (screenW - w).coerceAtLeast(0))
            val top = (((screenH - h) * 0.32f).toInt() + offset).coerceIn(80, (screenH - h).coerceAtLeast(80))
            val right = left + w
            val bottom = top + h
            val bounds = Rect(left, top, right, bottom)

            // 1. Ensure global OS freeform settings
            Shell.cmd("settings put global enable_freeform_support 1; settings put global force_resizable_activities 1").exec()

            // 2. Resolve target launcher activity component
            val resolveCmd = "cmd package resolve-activity --brief $packageName 2>/dev/null | tail -n 1"
            val resolved = Shell.cmd(resolveCmd).exec().out.joinToString("").trim()
            val targetCmp = if (resolved.isNotEmpty() && resolved.contains("/")) resolved else {
                val pm = ctx.packageManager
                val intent = pm.getLaunchIntentForPackage(packageName)
                intent?.component?.flattenToShortString() ?: packageName
            }

            val boundsStr = "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"

            // 3. Launch real application into freeform mode (windowingMode 5) via root
            val startCmd = "am start -n '$targetCmp' --windowingMode 5 --bounds '$boundsStr' -f 0x18000000"
            var startRes = Shell.cmd(startCmd).exec()
            if (!startRes.isSuccess) {
                // Secondary launch syntax
                Shell.cmd("cmd activity start-activity --windowingMode 5 -n '$targetCmp' --activity-multiple-task").exec()
            }

            // Brief wait for WindowManager task creation
            delay(350)

            // 4. Query Task ID from dumpsys
            val queryCmd = "dumpsys activity recents | grep -B 2 -A 8 '$packageName' | grep -m1 'taskId='"
            val taskIdLine = Shell.cmd(queryCmd).exec().out.joinToString("")
            val regex = Regex("""taskId=(\d+)""")
            val match = regex.find(taskIdLine)
            val taskId = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: System.currentTimeMillis().toInt()

            // 5. Force task resizable & set bounds via root
            Shell.cmd("cmd activity task resizeable $taskId 3; cmd activity task resize $taskId ${bounds.left} ${bounds.top} ${bounds.right} ${bounds.bottom}").exec()

            // 6. Register instance in reactive state
            val instance = FreeformAppInstance(
                taskId = taskId,
                packageName = packageName,
                component = targetCmp,
                label = label,
                icon = icon,
                bounds = bounds,
                isMinimized = false,
                isMaximized = false,
                savedBounds = bounds
            )

            withContext(Dispatchers.Main) {
                val current = mutableApps.value.toMutableMap()
                current[taskId] = instance
                mutableApps.value = current
                ensureDecorAttached()
            }
        }
    }

    fun dragTask(taskId: Int, dx: Float, dy: Float) {
        val ctx = appContext ?: return
        val current = mutableApps.value[taskId] ?: return
        val dm = ctx.resources.displayMetrics
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels

        val newLeft = (current.bounds.left + dx.toInt()).coerceIn(0, screenW - 120)
        val newTop = (current.bounds.top + dy.toInt()).coerceIn(60, screenH - 120)
        val newRight = newLeft + current.bounds.width()
        val newBottom = newTop + current.bounds.height()
        val nextBounds = Rect(newLeft, newTop, newRight, newBottom)

        val updated = current.copy(bounds = nextBounds, savedBounds = nextBounds)
        val map = mutableApps.value.toMutableMap()
        map[taskId] = updated
        mutableApps.value = map

        coroutineScope?.launch(Dispatchers.IO) {
            Shell.cmd("cmd activity task resize $taskId $newLeft $newTop $newRight $newBottom").exec()
        }
    }

    fun resizeTask(taskId: Int, dw: Float, dh: Float) {
        val ctx = appContext ?: return
        val current = mutableApps.value[taskId] ?: return
        val dm = ctx.resources.displayMetrics
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels

        val minW = (240 * dm.density).toInt()
        val minH = (200 * dm.density).toInt()

        val newRight = (current.bounds.right + dw.toInt()).coerceIn(current.bounds.left + minW, screenW)
        val newBottom = (current.bounds.bottom + dh.toInt()).coerceIn(current.bounds.top + minH, screenH)
        val nextBounds = Rect(current.bounds.left, current.bounds.top, newRight, newBottom)

        val updated = current.copy(bounds = nextBounds, savedBounds = nextBounds)
        val map = mutableApps.value.toMutableMap()
        map[taskId] = updated
        mutableApps.value = map

        coroutineScope?.launch(Dispatchers.IO) {
            Shell.cmd("cmd activity task resize $taskId ${nextBounds.left} ${nextBounds.top} $newRight $newBottom").exec()
        }
    }

    fun minimizeTask(taskId: Int) {
        val current = mutableApps.value[taskId] ?: return
        val updated = current.copy(isMinimized = true)
        val map = mutableApps.value.toMutableMap()
        map[taskId] = updated
        mutableApps.value = map

        coroutineScope?.launch(Dispatchers.IO) {
            // Shrink task out of screen or move to back
            Shell.cmd("cmd activity task resize $taskId 0 0 1 1").exec()
        }
    }

    fun restoreTask(taskId: Int) {
        val current = mutableApps.value[taskId] ?: return
        val bounds = current.savedBounds
        val updated = current.copy(isMinimized = false, bounds = bounds)
        val map = mutableApps.value.toMutableMap()
        map[taskId] = updated
        mutableApps.value = map

        coroutineScope?.launch(Dispatchers.IO) {
            Shell.cmd("cmd activity task resize $taskId ${bounds.left} ${bounds.top} ${bounds.right} ${bounds.bottom}").exec()
            Shell.cmd("am start -n '${current.component}' --windowingMode 5").exec()
        }
    }

    fun toggleMaximize(taskId: Int) {
        val ctx = appContext ?: return
        val current = mutableApps.value[taskId] ?: return
        val dm = ctx.resources.displayMetrics

        val isMax = !current.isMaximized
        val nextBounds = if (isMax) {
            Rect(0, 0, dm.widthPixels, dm.heightPixels)
        } else {
            current.savedBounds
        }

        val updated = current.copy(isMaximized = isMax, bounds = nextBounds)
        val map = mutableApps.value.toMutableMap()
        map[taskId] = updated
        mutableApps.value = map

        coroutineScope?.launch(Dispatchers.IO) {
            Shell.cmd("cmd activity task resize $taskId ${nextBounds.left} ${nextBounds.top} ${nextBounds.right} ${nextBounds.bottom}").exec()
        }
    }

    fun closeTask(taskId: Int) {
        val current = mutableApps.value[taskId]
        val map = mutableApps.value.toMutableMap()
        map.remove(taskId)
        mutableApps.value = map

        if (map.isEmpty()) {
            decorView?.visibility = View.GONE
        }

        current?.let { app ->
            coroutineScope?.launch(Dispatchers.IO) {
                Shell.cmd("cmd activity task kill $taskId; am force-stop ${app.packageName}").exec()
            }
        }
    }

    private fun ensureDecorAttached() {
        val ctx = appContext ?: return
        val wm = windowManager ?: return
        val owner = lifecycleOwner ?: return
        val savedOwner = savedStateRegistryOwner ?: return

        if (decorView == null) {
            val flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

            decorParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                title = "LynxFreeformDecorOverlay"
            }

            val composeView = ComposeView(ctx).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeSavedStateRegistryOwner(savedOwner)
                setContent {
                    val appsMap by activeApps.collectAsState()
                    FreeformMultiWindowOverlay(
                        apps = appsMap.values.toList(),
                        onDrag = ::dragTask,
                        onResize = ::resizeTask,
                        onMinimize = ::minimizeTask,
                        onRestore = ::restoreTask,
                        onToggleMaximize = ::toggleMaximize,
                        onClose = ::closeTask
                    )
                }
            }

            decorView = composeView
            runCatching { wm.addView(composeView, decorParams) }
        } else {
            decorView?.visibility = View.VISIBLE
        }
    }
}
