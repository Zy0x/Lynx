package com.noir.lynx.freeform.root

import android.app.ActivityManager
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

    fun tasks(displayId: Int): List<ActivityManager.RunningTaskInfo> {
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
            taskDisplayId == displayId
        }
    }

    fun remove(taskId: Int) {
        check(atmType.getMethod("removeTask", Int::class.javaPrimitiveType).invoke(atm, taskId) == true)
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
