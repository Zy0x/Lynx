package com.noir.lynx.feature.floating

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.noir.lynx.feature.floating.ui.FreeformCornerGrip
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * LynxFreeformManager — High-Performance Root Freeform Engine with Tactical Corner Anchors.
 * Each floating app task is assigned a non-intrusive, isolated Corner Grip window.
 * The underlying application retains 100% native touch responsiveness with zero fullscreen blocking.
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

    private data class GripViewHolder(
        val view: ComposeView,
        val params: WindowManager.LayoutParams,
        var currentBounds: Rect
    )

    private val activeGrips = mutableMapOf<Int, GripViewHolder>()
    private var resizeJob: Job? = null
    private var pendingResize: Pair<Int, Rect>? = null

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
        activeGrips.values.forEach { holder ->
            runCatching { wm?.removeView(holder.view) }
            holder.view.disposeComposition()
        }
        activeGrips.clear()
        mutableApps.value = emptyMap()
        resizeJob?.cancel()
        resizeJob = null
        pendingResize = null
        appContext = null
        windowManager = null
        coroutineScope = null
        lifecycleOwner = null
        savedStateRegistryOwner = null
    }

    /**
     * Launch a REAL application in Freeform Mode with Root and attach the tactical corner grip.
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

            val count = activeApps.value.size
            val offset = (count * 45).coerceAtMost(180)
            val left = (((screenW - w) / 2) + offset).coerceIn(40, (screenW - w).coerceAtLeast(40))
            val top = (((screenH - h) * 0.32f).toInt() + offset).coerceIn(100, (screenH - h).coerceAtLeast(100))
            val right = left + w
            val bottom = top + h
            val initialBounds = Rect(left, top, right, bottom)

            // 1. Enforce global freeform capabilities
            Shell.cmd("settings put global enable_freeform_support 1; settings put global force_resizable_activities 1").exec()

            // 2. Resolve target launcher component
            val resolveCmd = "cmd package resolve-activity --brief $packageName 2>/dev/null | tail -n 1"
            val resolved = Shell.cmd(resolveCmd).exec().out.joinToString("").trim()
            val targetCmp = if (resolved.isNotEmpty() && resolved.contains("/")) resolved else {
                val pm = ctx.packageManager
                val intent = pm.getLaunchIntentForPackage(packageName)
                intent?.component?.flattenToShortString() ?: packageName
            }

            // 3. Start Activity in Freeform Windowing Mode (5)
            Shell.cmd("cmd activity start-activity --windowingMode 5 -n '$targetCmp'").exec()

            // Brief wait for task registration
            delay(250)

            // 4. Query exact freeform task ID
            val queryCmd = "dumpsys activity activities | grep -E 'Task\\{.*$packageName.*mode=freeform' | head -n 1"
            var taskLine = Shell.cmd(queryCmd).exec().out.joinToString("")
            var taskIdMatch = Regex("""#(\d+)""").find(taskLine)
            var taskId = taskIdMatch?.groupValues?.getOrNull(1)?.toIntOrNull()

            if (taskId == null) {
                // Fallback query from recents
                val recentsCmd = "dumpsys activity recents | grep -B 2 -A 8 '$packageName' | grep -m1 'taskId='"
                val recentsLine = Shell.cmd(recentsCmd).exec().out.joinToString("")
                taskId = Regex("""taskId=(\d+)""").find(recentsLine)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ?: System.currentTimeMillis().toInt()
            }

            // 5. Force task resizable & immediately apply preset bounds
            Shell.cmd("cmd activity task resizeable $taskId 3; cmd activity task resize $taskId $left $top $right $bottom").exec()

            val instance = FreeformAppInstance(
                taskId = taskId,
                packageName = packageName,
                component = targetCmp,
                label = label,
                icon = icon,
                bounds = initialBounds,
                isMinimized = false,
                isMaximized = false,
                savedBounds = initialBounds
            )

            withContext(Dispatchers.Main) {
                val current = mutableApps.value.toMutableMap()
                current[taskId] = instance
                mutableApps.value = current
                attachOrUpdateGrip(instance)
            }
        }
    }

    private fun attachOrUpdateGrip(app: FreeformAppInstance) {
        val ctx = appContext ?: return
        val wm = windowManager ?: return
        val owner = lifecycleOwner ?: return
        val savedOwner = savedStateRegistryOwner ?: return

        val dm = ctx.resources.displayMetrics
        val gripSize = (48 * dm.density).toInt()

        val existing = activeGrips[app.taskId]
        if (existing != null) {
            existing.currentBounds = app.bounds
            existing.params.x = if (app.isMinimized) dm.widthPixels - gripSize else (app.bounds.right - gripSize).coerceAtLeast(0)
            existing.params.y = if (app.isMinimized) (dm.heightPixels * 0.45f).toInt() else (app.bounds.bottom - gripSize).coerceAtLeast(0)
            runCatching { wm.updateViewLayout(existing.view, existing.params) }
            return
        }

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (app.bounds.right - gripSize).coerceAtLeast(0)
            y = (app.bounds.bottom - gripSize).coerceAtLeast(0)
            title = "LynxCornerGrip_${app.taskId}"
        }

        val composeView = ComposeView(ctx).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(savedOwner)
            setContent {
                val appsMap by activeApps.collectAsState()
                val currentApp = appsMap[app.taskId] ?: app
                FreeformCornerGrip(
                    app = currentApp,
                    onResizeDelta = { dw, dh -> handleResizeDelta(app.taskId, dw, dh) },
                    onResizeCommit = { handleResizeCommit(app.taskId) },
                    onApplyPreset = { wPct, hPct -> applyPreset(app.taskId, wPct, hPct) },
                    onToggleMaximize = { toggleMaximize(app.taskId) },
                    onMinimize = { minimizeTask(app.taskId) },
                    onRestore = { restoreTask(app.taskId) },
                    onClose = { closeTask(app.taskId) }
                )
            }
        }

        val holder = GripViewHolder(composeView, params, app.bounds)
        activeGrips[app.taskId] = holder
        runCatching { wm.addView(composeView, params) }
    }

    private fun handleResizeDelta(taskId: Int, dw: Float, dh: Float) {
        val ctx = appContext ?: return
        val wm = windowManager ?: return
        val holder = activeGrips[taskId] ?: return
        val dm = ctx.resources.displayMetrics
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels

        val minW = (240 * dm.density).toInt()
        val minH = (200 * dm.density).toInt()

        val newRight = (holder.currentBounds.right + dw.toInt()).coerceIn(holder.currentBounds.left + minW, screenW)
        val newBottom = (holder.currentBounds.bottom + dh.toInt()).coerceIn(holder.currentBounds.top + minH, screenH)
        val nextBounds = Rect(holder.currentBounds.left, holder.currentBounds.top, newRight, newBottom)
        holder.currentBounds = nextBounds

        // Update grip window position immediately for 60fps tracking
        val gripSize = (48 * dm.density).toInt()
        holder.params.x = (newRight - gripSize).coerceAtLeast(0)
        holder.params.y = (newBottom - gripSize).coerceAtLeast(0)
        runCatching { wm.updateViewLayout(holder.view, holder.params) }

        // Throttle root command to ~30 FPS to prevent I/O choke
        scheduleResize(taskId, nextBounds)
    }

    private fun handleResizeCommit(taskId: Int) {
        val holder = activeGrips[taskId] ?: return
        val b = holder.currentBounds

        resizeJob?.cancel()
        resizeJob = null
        pendingResize = null

        coroutineScope?.launch(Dispatchers.IO) {
            Shell.cmd("cmd activity task resize $taskId ${b.left} ${b.top} ${b.right} ${b.bottom}").exec()
        }

        val current = mutableApps.value[taskId] ?: return
        val map = mutableApps.value.toMutableMap()
        map[taskId] = current.copy(bounds = b, savedBounds = b)
        mutableApps.value = map
    }

    private fun scheduleResize(taskId: Int, bounds: Rect) {
        pendingResize = taskId to bounds
        if (resizeJob?.isActive == true) return
        resizeJob = coroutineScope?.launch(Dispatchers.IO) {
            delay(35) // 30 FPS throttle
            val pending = pendingResize ?: return@launch
            val (id, b) = pending
            Shell.cmd("cmd activity task resize $id ${b.left} ${b.top} ${b.right} ${b.bottom}").exec()
            resizeJob = null
        }
    }

    fun applyPreset(taskId: Int, widthPct: Int, heightPct: Int) {
        val ctx = appContext ?: return
        val wm = windowManager ?: return
        val holder = activeGrips[taskId] ?: return
        val dm = ctx.resources.displayMetrics
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels

        val w = (screenW * (widthPct.coerceIn(35, 95) / 100f)).toInt()
        val h = (screenH * (heightPct.coerceIn(35, 90) / 100f)).toInt()

        val left = holder.currentBounds.left.coerceIn(20, (screenW - w).coerceAtLeast(20))
        val top = holder.currentBounds.top.coerceIn(80, (screenH - h).coerceAtLeast(80))
        val nextBounds = Rect(left, top, left + w, top + h)
        holder.currentBounds = nextBounds

        val gripSize = (48 * dm.density).toInt()
        holder.params.x = (nextBounds.right - gripSize).coerceAtLeast(0)
        holder.params.y = (nextBounds.bottom - gripSize).coerceAtLeast(0)
        runCatching { wm.updateViewLayout(holder.view, holder.params) }

        coroutineScope?.launch(Dispatchers.IO) {
            Shell.cmd("cmd activity task resize $taskId ${nextBounds.left} ${nextBounds.top} ${nextBounds.right} ${nextBounds.bottom}").exec()
        }

        val current = mutableApps.value[taskId] ?: return
        val map = mutableApps.value.toMutableMap()
        map[taskId] = current.copy(bounds = nextBounds, savedBounds = nextBounds)
        mutableApps.value = map
    }

    fun toggleMaximize(taskId: Int) {
        val ctx = appContext ?: return
        val wm = windowManager ?: return
        val holder = activeGrips[taskId] ?: return
        val current = mutableApps.value[taskId] ?: return
        val dm = ctx.resources.displayMetrics

        val isMax = !current.isMaximized
        val nextBounds = if (isMax) Rect(0, 0, dm.widthPixels, dm.heightPixels) else current.savedBounds
        holder.currentBounds = nextBounds

        val gripSize = (48 * dm.density).toInt()
        holder.params.x = (nextBounds.right - gripSize).coerceAtLeast(0)
        holder.params.y = (nextBounds.bottom - gripSize).coerceAtLeast(0)
        runCatching { wm.updateViewLayout(holder.view, holder.params) }

        coroutineScope?.launch(Dispatchers.IO) {
            Shell.cmd("cmd activity task resize $taskId ${nextBounds.left} ${nextBounds.top} ${nextBounds.right} ${nextBounds.bottom}").exec()
        }

        val map = mutableApps.value.toMutableMap()
        map[taskId] = current.copy(isMaximized = isMax, bounds = nextBounds)
        mutableApps.value = map
    }

    fun minimizeTask(taskId: Int) {
        val ctx = appContext ?: return
        val wm = windowManager ?: return
        val holder = activeGrips[taskId] ?: return
        val current = mutableApps.value[taskId] ?: return
        val dm = ctx.resources.displayMetrics

        val gripSize = (48 * dm.density).toInt()
        holder.params.x = dm.widthPixels - gripSize - 10
        holder.params.y = (dm.heightPixels * 0.45f).toInt()
        runCatching { wm.updateViewLayout(holder.view, holder.params) }

        coroutineScope?.launch(Dispatchers.IO) {
            Shell.cmd("cmd activity task resize $taskId 0 0 1 1").exec()
        }

        val map = mutableApps.value.toMutableMap()
        map[taskId] = current.copy(isMinimized = true)
        mutableApps.value = map
    }

    fun restoreTask(taskId: Int) {
        val ctx = appContext ?: return
        val wm = windowManager ?: return
        val holder = activeGrips[taskId] ?: return
        val current = mutableApps.value[taskId] ?: return
        val dm = ctx.resources.displayMetrics
        val b = current.savedBounds

        holder.currentBounds = b
        val gripSize = (48 * dm.density).toInt()
        holder.params.x = (b.right - gripSize).coerceAtLeast(0)
        holder.params.y = (b.bottom - gripSize).coerceAtLeast(0)
        runCatching { wm.updateViewLayout(holder.view, holder.params) }

        coroutineScope?.launch(Dispatchers.IO) {
            Shell.cmd("cmd activity task resize $taskId ${b.left} ${b.top} ${b.right} ${b.bottom}").exec()
            Shell.cmd("am start -n '${current.component}' --windowingMode 5").exec()
        }

        val map = mutableApps.value.toMutableMap()
        map[taskId] = current.copy(isMinimized = false, bounds = b)
        mutableApps.value = map
    }

    fun closeTask(taskId: Int) {
        val wm = windowManager
        val holder = activeGrips.remove(taskId)
        holder?.let {
            runCatching { wm?.removeView(it.view) }
            it.view.disposeComposition()
        }

        val current = mutableApps.value[taskId]
        val map = mutableApps.value.toMutableMap()
        map.remove(taskId)
        mutableApps.value = map

        current?.let { app ->
            coroutineScope?.launch(Dispatchers.IO) {
                Shell.cmd("cmd activity task kill $taskId; am force-stop ${app.packageName}").exec()
            }
        }
    }
}
