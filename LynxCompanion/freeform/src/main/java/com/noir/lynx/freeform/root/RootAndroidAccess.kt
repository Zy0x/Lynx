package com.noir.lynx.freeform.root

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Context
import android.hardware.input.InputManager
import android.os.Build
import android.view.InputEvent

/** Framework reflection stays in the privileged process; no private transaction numbers. */
internal class RootAndroidAccess(context: Context) {
    private val atmType = Class.forName("android.app.IActivityTaskManager")
    private val atm = Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null)
    private val taskMethod by lazy { atmType.methods.filter { it.name == "getTasks" }.let { methods ->
        val index = RootFrameworkPolicy.taskSignature(methods.map { m -> m.parameterTypes.map { it.name } })
            ?: error("Unsupported getTasks signature")
        methods[index]
    } }
    private val input = context.getSystemService(InputManager::class.java)
    private val setDisplay = InputEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType)
    private val inject = InputManager::class.java.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)

    fun inject(event: InputEvent, displayId: Int): Boolean {
        setDisplay.invoke(event, displayId)
        return inject.invoke(input, event, 0) == true
    }

    fun tasks(displayId: Int = -1): List<ActivityManager.RunningTaskInfo> {
        val method = taskMethod
        val args = method.parameterTypes.mapIndexed { index, type ->
            when (type) {
                Int::class.javaPrimitiveType -> if (index == 0) 100 else displayId
                Boolean::class.javaPrimitiveType -> false
                else -> error("Unsupported getTasks signature")
            }
        }.toTypedArray()
        @Suppress("UNCHECKED_CAST")
        return (method.invoke(atm, *args) as List<ActivityManager.RunningTaskInfo>).filter {
            val taskDisplayId = runCatching {
                it.javaClass.getField("displayId").getInt(it)
            }.getOrNull()
            if (displayId < 0) taskDisplayId != null else taskDisplayId == displayId
        }
    }

    fun displayOf(task: ActivityManager.RunningTaskInfo): Int = task.javaClass.getField("displayId").getInt(task)
    fun userOf(task: ActivityManager.RunningTaskInfo): Int = task.javaClass.getField("userId").getInt(task)

    fun restoreMainFocus(taskId: Int?) {
        if (taskId == null || tasks(0).none { it.taskId == taskId }) return
        atmType.getMethod("setFocusedTask", Int::class.javaPrimitiveType).invoke(atm, taskId)
    }

    fun move(taskId: Int, displayId: Int, bringToFront: Boolean = true) {
        require(taskId >= 0 && displayId >= 0)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
        ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
            .invoke(options, 1)
        if (!bringToFront) ActivityOptions::class.java.getMethod("setAvoidMoveToFront").invoke(options)
        val result = atmType.getMethod("startActivityFromRecents", Int::class.javaPrimitiveType, android.os.Bundle::class.java)
            .invoke(atm, taskId, options.toBundle()) as Int
        check(result >= 0) { "Task move rejected: $result" }
        val deadline = android.os.SystemClock.uptimeMillis() + 1000
        while (tasks(displayId).none { it.taskId == taskId }) {
            check(android.os.SystemClock.uptimeMillis() < deadline) { "Task move was not verified" }
            Thread.sleep(50)
        }
    }

    private val windowType by lazy { Class.forName("android.view.IWindowManager") }
    private val window by lazy {
        val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java).invoke(null, "window")
        Class.forName("android.view.IWindowManager\$Stub")
            .getMethod("asInterface", android.os.IBinder::class.java).invoke(null, binder)
    }

    fun configureIme(displayId: Int): Boolean = runCatching {
        // Android 10/11 route a secondary display without a local IME to the default display.
        if (Build.VERSION.SDK_INT < 31) return@runCatching true
        // FALLBACK_DISPLAY: show the keyboard on the physical display.
        windowType.getMethod("setDisplayImePolicy", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .invoke(window, displayId, 1)
        windowType.getMethod("getDisplayImePolicy", Int::class.javaPrimitiveType).invoke(window, displayId) == 1
    }.getOrDefault(false)

    fun lockOwnedRotation(displayId: Int): Boolean = runCatching {
        check(displayId > 0)
        // Only this owned virtual display. Never fall back to freezeRotation(default display).
        val p = Int::class.javaPrimitiveType
        val method = runCatching { windowType.getMethod("freezeDisplayRotation", p, p, String::class.java) }
            .getOrElse { windowType.getMethod("freezeDisplayRotation", p, p) }
        if (method.parameterCount == 3) method.invoke(window, displayId, 0, "LynxVirtual")
        else method.invoke(window, displayId, 0)
        true
    }.getOrDefault(false)
}
