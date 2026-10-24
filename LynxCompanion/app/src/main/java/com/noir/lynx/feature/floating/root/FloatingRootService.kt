package com.noir.lynx.feature.floating.root

import android.content.ComponentName
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.*
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import com.topjohnwu.superuser.ipc.RootService
import java.util.concurrent.TimeUnit

/** APK-owned, non-daemon root service. It neither installs a module nor edits global settings. */
class FloatingRootService : RootService() {
    private data class Session(val display: VirtualDisplay, val surface: Surface, val owner: IBinder,
                               val death: IBinder.DeathRecipient)
    private val sessions = java.util.concurrent.ConcurrentHashMap<Int, Session>()
    private val lock = Any()
    private lateinit var access: RootAndroidAccess
    private var clientUid = -1

    override fun onCreate() {
        super.onCreate()
        clientUid = applicationInfo.uid
        access = RootAndroidAccess(this)
    }

    private fun <T> privileged(serialized: Boolean = true, block: () -> T): T {
        check(Binder.getCallingUid() == clientUid) { "Unexpected client UID" }
        val token = Binder.clearCallingIdentity()
        return try { if (serialized) synchronized(lock, block) else block() }
        finally { Binder.restoreCallingIdentity(token) }
    }

    private fun release(id: Int) {
        val session = sessions.remove(id) ?: return
        runCatching { session.owner.unlinkToDeath(session.death, 0) }
        runCatching { access.tasks(id).forEach { access.remove(it.taskId) } }
        try { session.display.release() } finally { session.surface.release() }
    }

    private fun result(stage: () -> String = { "capability_unavailable" }, block: () -> Bundle): Bundle = try { block() } catch (e: Exception) {
        android.util.Log.w("LynxFloatingRoot", "Privileged operation failed", e)
        val cause = if (e is java.lang.reflect.InvocationTargetException) e.targetException else e
        Bundle().apply {
            putBoolean("ok", false)
            putString("reason", if (cause is SecurityException) "permission_denied" else stage())
        }
    }

    private val binder = object : IFloatingRoot.Stub() {
        override fun open(owner: IBinder, surface: Surface, component: String, userId: Int,
                          width: Int, height: Int, density: Int): Bundle = privileged {
            var stage = "framework_api"
            result({ stage }) {
                check(Build.VERSION.SDK_INT >= 29 && Process.myUid() == 0)
                check(userId == clientUid / 100000 && owner.isBinderAlive && surface.isValid)
                check(sessions.size < 4)
                check(FloatingDisplayPolicy.validSize(width, height, density))
                val target = ComponentName.unflattenFromString(component) ?: error("Invalid component")
                check(packageManager.getActivityInfo(target, 0).exported)
                stage = "display_creation"
                val flags = RootFrameworkPolicy.displayFlags(Build.VERSION.SDK_INT)
                val ctor = DisplayManager::class.java.getDeclaredConstructor(android.content.Context::class.java)
                ctor.isAccessible = true
                val dm = ctor.newInstance(this@FloatingRootService)
                val vd = dm.createVirtualDisplay("LynxApp:${target.packageName}", width, height, density, surface, flags)
                    ?: error("Display creation rejected")
                val id = vd.display.displayId
                val death = IBinder.DeathRecipient { synchronized(lock) { release(id) } }
                sessions[id] = Session(vd, surface, owner, death)
                try {
                    owner.linkToDeath(death, 0)
                    val imeConfigured = access.configureIme(id)
                    val rotationLocked = access.lockOwnedRotation(id)
                    stage = "activity_launch"
                    val child = ProcessBuilder(RootLaunchCommand.create(component, userId, id))
                        .redirectErrorStream(true).start()
                    try {
                        check(child.waitFor(4, TimeUnit.SECONDS)) { "Activity start timeout" }
                        check(child.exitValue() == 0)
                    } finally { child.destroy(); child.inputStream.close() }
                    stage = "task_verification"
                    val deadline = SystemClock.uptimeMillis() + 2000
                    var task = access.tasks(id).firstOrNull { it.baseActivity?.packageName == target.packageName }
                    while (task == null && SystemClock.uptimeMillis() < deadline) {
                        Thread.sleep(100)
                        task = access.tasks(id).firstOrNull { it.baseActivity?.packageName == target.packageName }
                    }
                    check(task != null) { "No task on requested display" }
                    Bundle().apply {
                        putBoolean("ok", true); putInt("displayId", id); putInt("taskId", task.taskId)
                        putInt("sdk", Build.VERSION.SDK_INT)
                        putBoolean("imeConfigured", imeConfigured); putBoolean("rotationLocked", rotationLocked)
                    }
                } catch (e: Exception) { release(id); throw e }
            }.also { if (!it.getBoolean("ok")) surface.release() }
        }

        override fun resize(displayId: Int, width: Int, height: Int, density: Int): Bundle = privileged {
            result({ "resize_rejected" }) {
                check(FloatingDisplayPolicy.validSize(width, height, density))
                val s = sessions[displayId] ?: error("Unknown session")
                s.display.resize(width, height, density)
                val metrics = android.util.DisplayMetrics()
                val deadline = SystemClock.uptimeMillis() + 700
                do {
                    @Suppress("DEPRECATION")
                    s.display.display.getRealMetrics(metrics)
                    if (metrics.widthPixels == width && metrics.heightPixels == height && metrics.densityDpi == density) break
                    Thread.sleep(50)
                } while (SystemClock.uptimeMillis() < deadline)
                Bundle().apply {
                    putBoolean("ok", metrics.widthPixels == width && metrics.heightPixels == height && metrics.densityDpi == density)
                    putInt("width", metrics.widthPixels); putInt("height", metrics.heightPixels); putInt("density", metrics.densityDpi)
                }
            }
        }

        override fun touch(displayId: Int, event: MotionEvent): Boolean = privileged(false) {
            try { sessions.containsKey(displayId) && access.inject(event, displayId) }
            catch (_: Exception) { false } finally { event.recycle() }
        }

        override fun back(displayId: Int): Boolean = privileged(false) {
            if (!sessions.containsKey(displayId)) return@privileged false
            runCatching {
                val now = SystemClock.uptimeMillis()
                val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0).apply { source = InputDevice.SOURCE_KEYBOARD }
                val up = KeyEvent.changeAction(down, KeyEvent.ACTION_UP)
                access.inject(down, displayId) && access.inject(up, displayId)
            }.getOrDefault(false)
        }

        override fun visible(displayId: Int, visible: Boolean): Boolean = privileged {
            runCatching {
                val s = sessions[displayId] ?: return@runCatching false
                s.display.surface = if (visible) s.surface else null
                true
            }.getOrDefault(false)
        }

        override fun close(displayId: Int) = privileged { release(displayId) }
    }

    override fun onBind(intent: Intent): IBinder = binder
    override fun onDestroy() {
        synchronized(lock) { sessions.keys.toList().forEach(::release) }
        super.onDestroy()
    }
}
