package com.noir.lynx.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.noir.lynx.R
import com.noir.lynx.core.locale.AppLocale
import com.noir.lynx.data.ModeToastData
import com.noir.lynx.data.ToastType
import com.noir.lynx.ui.JewelToastPill
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * LynxToastOverlayService — Transient, zero-latency floating Jewel HUD overlay.
 *
 * Renders the official cyberpunk Jewel Dynamic Island / Pill directly over running apps/games.
 * Non-intrusive (FLAG_NOT_FOCUSABLE), automatically dismisses and frees memory in ~2.5s.
 */
class LynxToastOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var floatView: View? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var dismissJob: Job? = null

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private val toastDataFlow = MutableStateFlow<ModeToastData?>(null)
    private val isVisibleFlow = MutableStateFlow(false)
    private var currentStyle: String = "jewel_top"

    companion object {
        const val ACTION_SHOW = "com.noir.lynx.service.SHOW_TRANSIENT_TOAST"
        const val EXTRA_PROFILE = "extra_profile"
        const val EXTRA_REASON = "extra_reason"
        const val EXTRA_STYLE = "extra_style"
        private const val NOTIF_CHANNEL_ID = "lynx_transient_toast_channel"
        private const val NOTIF_ID = 8802
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        try {
            startForeground(NOTIF_ID, buildSilentNotification())
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val profile = intent?.getStringExtra(EXTRA_PROFILE) ?: "balance"
        val style = intent?.getStringExtra(EXTRA_STYLE) ?: "jewel_top"
        currentStyle = style

        val (profileName, accentHex) = when (profile.lowercase()) {
            "extreme" -> AppLocale.getString(this, R.string.profile_extreme) to 0xFFFF3366L
            "performance" -> AppLocale.getString(this, R.string.profile_performance) to 0xFFFF9900L
            "powersave" -> AppLocale.getString(this, R.string.profile_powersave) to 0xFF00E676L
            else -> AppLocale.getString(this, R.string.profile_balance) to 0xFF00A3FFL
        }
        val label = AppLocale.getString(this, R.string.profile_mode_name_format, profileName)

        val toastData = ModeToastData(
            modeId = profile,
            modeLabel = label,
            accentColorHex = accentHex,
            statusTag = "AKTIF",
            type = ToastType.MODE
        )

        toastDataFlow.value = toastData

        if (floatView != null && style != currentStyle) {
            try {
                windowManager.removeView(floatView)
            } catch (_: Exception) {}
            floatView = null
        }
        currentStyle = style

        if (floatView == null) {
            createOverlayWindow(style)
        }

        // Trigger entrance animation
        isVisibleFlow.value = true

        // Schedule automatic dismissal
        dismissJob?.cancel()
        dismissJob = serviceScope.launch {
            delay(2200L) // visible duration
            isVisibleFlow.value = false // exit animation
            delay(250L) // wait for animation exit
            cleanUpAndStop()
        }

        return START_NOT_STICKY
    }

    private fun createOverlayWindow(style: String) {
        val isBottom = style == "jewel_bottom"

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = if (isBottom) (Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL) else (Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            val density = resources.displayMetrics.density
            y = if (isBottom) (80 * density).toInt() else (28 * density).toInt()
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@LynxToastOverlayService)
            setViewTreeSavedStateRegistryOwner(this@LynxToastOverlayService)
            setContent {
                val data by toastDataFlow.collectAsState()
                val isVisible by isVisibleFlow.collectAsState()

                Box(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    contentAlignment = if (isBottom) Alignment.BottomCenter else Alignment.TopCenter
                ) {
                    AnimatedVisibility(
                        visible = isVisible && data != null,
                        enter = if (isBottom) {
                            slideInVertically(
                                initialOffsetY = { it / 2 },
                                animationSpec = spring(dampingRatio = 0.78f, stiffness = 400f)
                            ) + fadeIn(animationSpec = tween(140))
                        } else {
                            slideInVertically(
                                initialOffsetY = { -it },
                                animationSpec = spring(dampingRatio = 0.75f, stiffness = 420f)
                            ) + fadeIn(animationSpec = tween(140))
                        },
                        exit = if (isBottom) {
                            slideOutVertically(
                                targetOffsetY = { it / 2 },
                                animationSpec = tween(150)
                            ) + fadeOut(animationSpec = tween(120))
                        } else {
                            slideOutVertically(
                                targetOffsetY = { -it },
                                animationSpec = tween(160)
                            ) + fadeOut(animationSpec = tween(120))
                        }
                    ) {
                        data?.let { toast ->
                            JewelToastPill(
                                toastData = toast,
                                onDismiss = { cleanUpAndStop() },
                                isCompactMini = isBottom
                            )
                        }
                    }
                }
            }
        }

        floatView = composeView
        try {
            windowManager.addView(composeView, layoutParams)
        } catch (_: Exception) {}
    }

    private fun cleanUpAndStop() {
        dismissJob?.cancel()
        floatView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
            floatView = null
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {}
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanUpAndStop()
        serviceScope.cancel()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                AppLocale.getString(this@LynxToastOverlayService, R.string.transient_feedback_channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildSilentNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIF_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("Lynx")
            .setContentText(AppLocale.getString(this@LynxToastOverlayService, R.string.transient_feedback_notification_text))
            .setSmallIcon(android.R.drawable.ic_notification_overlay)
            .setPriority(Notification.PRIORITY_MIN)
            .build()
    }
}
