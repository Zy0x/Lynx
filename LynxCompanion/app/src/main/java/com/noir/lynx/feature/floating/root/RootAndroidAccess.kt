package com.noir.lynx.feature.floating.root

import android.app.ActivityManager
import android.content.Context
import android.hardware.input.InputManager
import android.os.Build
import android.view.InputEvent

/** Framework reflection stays in the privileged process; no private transaction numbers. */
internal class RootAndroidAccess(context: Context) {
    private val atmType = Class.forName("android.app.IActivityTaskManager")
    private val atm = Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null)
    private val input = context.getSystemService(InputManager::class.java)
    private val setDisplay = InputEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType)
    private val inject = InputManager::class.java.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)

    fun inject(event: InputEvent, displayId: Int): Boolean {
        setDisplay.invoke(event, displayId)
        return inject.invoke(input, event, 0) == true
    }

    fun tasks(displayId: Int): List<ActivityManager.RunningTaskInfo> {
        val method = atmType.methods.single { it.name == "getTasks" }
        val args = method.parameterTypes.mapIndexed { index, type ->
            when (type) {
                Int::class.javaPrimitiveType -> if (index == 0) 100 else displayId
                Boolean::class.javaPrimitiveType -> false
                else -> error("Unsupported getTasks signature")
            }
        }.toTypedArray()
        @Suppress("UNCHECKED_CAST")
        return (method.invoke(atm, *args) as List<ActivityManager.RunningTaskInfo>).filter { it.displayId == displayId }
    }

    fun remove(taskId: Int) {
        check(atmType.getMethod("removeTask", Int::class.javaPrimitiveType).invoke(atm, taskId) == true)
    }

    fun configureIme(displayId: Int) {
        if (Build.VERSION.SDK_INT < 31) return
        val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java).invoke(null, "window")
        val type = Class.forName("android.view.IWindowManager")
        val wm = Class.forName("android.view.IWindowManager\$Stub")
            .getMethod("asInterface", android.os.IBinder::class.java).invoke(null, binder)
        // FALLBACK_DISPLAY: show the keyboard on the physical display.
        type.getMethod("setDisplayImePolicy", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).invoke(wm, displayId, 1)
    }
}
