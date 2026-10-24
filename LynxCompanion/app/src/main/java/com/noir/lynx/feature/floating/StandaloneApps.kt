package com.noir.lynx.feature.floating

import android.content.Context
import android.view.WindowManager
import com.noir.lynx.core.locale.AppLocale
import com.noir.lynx.feature.hud.HudApp
import com.noir.lynx.freeform.LynxFreeform
import kotlinx.coroutines.CoroutineScope

/** HUD adapter only. The reusable engine, resources and root bridge live in :freeform. */
object StandaloneApps {
    private var engine: LynxFreeform? = null
    fun init(context: Context, wm: WindowManager, parent: CoroutineScope) {
        engine?.close()
        engine = LynxFreeform(AppLocale.wrap(context), parent)
    }
    fun launch(app: HudApp, widthPct: Int, heightPct: Int) {
        engine?.launch(app.packageName, app.label, widthPct, heightPct)
    }
    fun viewportChanged() { engine?.viewportChanged() }
    fun closeWindows() { engine?.closeWindows() }
    fun destroy() { engine?.close(); engine = null }
}
