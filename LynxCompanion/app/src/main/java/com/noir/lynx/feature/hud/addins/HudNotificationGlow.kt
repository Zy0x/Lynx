package com.noir.lynx.feature.hud.addins

import android.app.Notification
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.hardware.input.InputManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal fun eligibleGlow(ownPackage: String, source: String, previous: Long?, postedAt: Long, now: Long, flags: Int): Boolean =
    source != ownPackage && previous != postedAt && now - postedAt in 0L..10_000L &&
        flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_GROUP_SUMMARY) == 0

internal fun glowWindowAlpha(maximum: Float) = maximum.coerceIn(0f, 1f) / 4f

/** No notification text or sender is read. Duplicate callbacks and reconnection replay are ignored. */
class HudNotificationGlowService : NotificationListenerService() {
    companion object {
        private val mutableConnected = MutableStateFlow(false)
        val connected = mutableConnected.asStateFlow()
    }
    private val seen = object : LinkedHashMap<String, Long>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > 128
    }

    override fun onListenerConnected() {
        seen.clear()
        runCatching { activeNotifications?.forEach { seen[it.key] = it.postTime } }
        mutableConnected.value = true
    }
    override fun onListenerDisconnected() { mutableConnected.value = false }
    override fun onDestroy() { mutableConnected.value = false; super.onDestroy() }
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val previous = seen.put(sbn.key, sbn.postTime)
        if (!eligibleGlow(packageName, sbn.packageName, previous, sbn.postTime, System.currentTimeMillis(), sbn.notification.flags)) return
        HudGamingAddins.current?.notificationArrived()
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { sbn?.let { seen.remove(it.key) } }
}

/** Four narrow, non-touchable windows; there is no full-screen input/occlusion surface. */
class HudNotificationGlow(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val windows = context.getSystemService(WindowManager::class.java)
    private val edges = mutableListOf<View>()
    private val remove = Runnable { edges.forEach { runCatching { windows.removeView(it) } }; edges.clear() }
    fun pulse(color: Int) = main.post {
        main.removeCallbacks(remove)
        remove.run()
        val tint = when (color) { 1 -> 0xff409cff.toInt(); 2 -> 0xffffb45c.toInt(); else -> 0xff00e6ba.toInt() }
        val thickness = (context.resources.displayMetrics.density * 6).toInt().coerceAtLeast(2)
        val maximum = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch
        } else .8f
        val opacity = glowWindowAlpha(maximum)
        if (opacity == 0f) return@post
        listOf(Gravity.TOP, Gravity.BOTTOM, Gravity.LEFT, Gravity.RIGHT).forEach { edge ->
            val horizontal = edge == Gravity.TOP || edge == Gravity.BOTTOM
            val view = View(context).apply {
                background = GradientDrawable(when (edge) {
                    Gravity.TOP -> GradientDrawable.Orientation.TOP_BOTTOM
                    Gravity.BOTTOM -> GradientDrawable.Orientation.BOTTOM_TOP
                    Gravity.LEFT -> GradientDrawable.Orientation.LEFT_RIGHT
                    else -> GradientDrawable.Orientation.RIGHT_LEFT
                }, intArrayOf(tint, 0x00000000))
                alpha = 0f
            }
            val params = WindowManager.LayoutParams(
                if (horizontal) -1 else thickness, if (horizontal) thickness else -1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = edge; alpha = opacity; setTitle("Lynx notification glow") }
            runCatching { windows.addView(view, params); edges.add(view) }
            view.animate().alpha(1f).setDuration(180).withEndAction {
                view.animate().alpha(0f).setStartDelay(500).setDuration(450).start()
            }.start()
        }
        main.postDelayed(remove, 1250)
    }
    fun close() { main.post { main.removeCallbacks(remove); remove.run() } }
}
