package com.noir.lynx.freeform.root

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
                               val death: IBinder.DeathRecipient, val packageName: String,
                               val userId: Int)
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
        val foreground = runCatching { access.tasks(0).firstOrNull()?.taskId }.getOrNull()
        runCatching { session.owner.unlinkToDeath(session.death, 0) }
        // Return the app's real task instead of deleting its tab/session state on close.
        runCatching { access.tasks(id).filter { access.userOf(it) == session.userId &&
            it.baseActivity?.packageName == session.packageName }.forEach {
                access.move(it.taskId, 0, bringToFront = false)
                android.util.Log.i("LynxFloatingRoot", "Returned task=${it.taskId} to display=0")
            } }.onFailure { android.util.Log.w("LynxFloatingRoot", "Task return failed; using display-removal migration", it) }
        try { session.display.release() } finally {
            session.surface.release()
            runCatching { access.restoreMainFocus(foreground) }
                .onFailure { android.util.Log.w("LynxFloatingRoot", "Main display focus restoration failed", it) }
        }
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
                val existing = RootTaskPolicy.reusable(access.tasks().map {
                    RootTaskPolicy.Task(it.taskId, access.userOf(it), access.displayOf(it), it.baseActivity?.packageName)
                }, target.packageName, userId, sessions.keys)
                val foreground = access.tasks(0).firstOrNull()?.taskId?.takeIf { it != existing?.id }
                stage = "display_creation"
                val ctor = DisplayManager::class.java.getDeclaredConstructor(android.content.Context::class.java)
                ctor.isAccessible = true
                val dm = ctor.newInstance(this@FloatingRootService)
                val (profile, vd) = RootDisplayProfiles.create(Build.VERSION.SDK_INT) { candidate ->
                    dm.createVirtualDisplay("LynxApp:${target.packageName}", width, height, density, surface, candidate.flags)
                        ?.also {
                            if (it.display.displayId <= 0 || !it.display.isValid) {
                                it.release(); throw IllegalArgumentException("Invalid display identity")
                            }
                        }
                }
                val id = vd.display.displayId
                val death = IBinder.DeathRecipient { synchronized(lock) { release(id) } }
                sessions[id] = Session(vd, surface, owner, death, target.packageName, userId)
                try {
                    owner.linkToDeath(death, 0)
                    val imeConfigured = access.configureIme(id)
                    val rotationLocked = access.lockOwnedRotation(id)
                    stage = "activity_launch"
                    if (existing != null) access.move(existing.id, id)
                    else {
                        val child = ProcessBuilder(RootLaunchCommand.create(component, userId, id))
                            .redirectErrorStream(true).start()
                        try {
                            check(child.waitFor(4, TimeUnit.SECONDS)) { "Activity start timeout" }
                            check(child.exitValue() == 0)
                        } finally { child.destroy(); child.inputStream.close() }
                    }
                    stage = "task_verification"
                    val deadline = SystemClock.uptimeMillis() + 2000
                    var task = access.tasks(id).firstOrNull { it.baseActivity?.packageName == target.packageName }
                    while (task == null && SystemClock.uptimeMillis() < deadline) {
                        Thread.sleep(100)
                        task = access.tasks(id).firstOrNull { it.baseActivity?.packageName == target.packageName }
                    }
                    check(task != null) { "No task on requested display" }
                    check(existing == null || task.taskId == existing.id) { "Existing task was not preserved" }
                    access.restoreMainFocus(foreground)
                    android.util.Log.i("LynxFloatingRoot", "Verified task=${task.taskId} display=$id reused=${existing != null} user=$userId")
                    Bundle().apply {
                        putBoolean("ok", true); putInt("displayId", id); putInt("taskId", task.taskId)
                        putInt("sdk", Build.VERSION.SDK_INT)
                        putString("compatibilityProfile", profile.name)
                        putBoolean("reusedTask", existing != null)
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
