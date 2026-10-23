package com.noir.lynx.feature.floating

import android.content.Context
import android.graphics.Bitmap
import android.os.Process
import android.view.WindowManager
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import com.noir.lynx.R
import com.noir.lynx.core.locale.AppLocale
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*

/** Explicit ROM/LMO backend. Standalone apps use the separate APK-owned virtual display service. */
object LynxFreeformManager {
    private var context: Context? = null
    private var scope: CoroutineScope? = null
    private var sessionJob: Job? = null

    @Suppress("UNUSED_PARAMETER")
    fun init(context: Context, wm: WindowManager, parent: CoroutineScope,
             owner: LifecycleOwner, savedOwner: SavedStateRegistryOwner) {
        this.context = context.applicationContext
        sessionJob = SupervisorJob(parent.coroutineContext[Job])
        scope = CoroutineScope(parent.coroutineContext + sessionJob!!)
    }

    fun destroy() { sessionJob?.cancel(); sessionJob = null; scope = null; context = null }

    @Suppress("UNUSED_PARAMETER")
    fun launchRealApp(packageName: String, label: String, icon: Bitmap,
                      widthPct: Int = 70, heightPct: Int = 65, engine: String = "system_freeform") {
        val ctx = context ?: return
        scope?.launch {
            val cmp = ctx.packageManager.getLaunchIntentForPackage(packageName)?.component
            if (cmp == null) { report(ctx); return@launch }
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val user = Process.myUid() / 100000
                    val hasLmo = runCatching { ctx.packageManager.getApplicationInfo("com.libremobileos.freeform", 0) }.isSuccess
                    // Component strings originate from PackageManager and are still shell-quoted.
                    fun quote(s: String) = "'" + s.replace("'", "'\"'\"'") + "'"
                    val before = Shell.cmd("dumpsys activity activities").exec()
                    if (!before.isSuccess) return@runCatching false
                    val command = if (hasLmo) {
                        "am broadcast --user $user -p com.libremobileos.freeform -a com.libremobileos.freeform.START_FREEFORM " +
                            "--es packageName ${quote(packageName)} --es activityName ${quote(cmp.className)} --ei userId $user"
                    } else "am start --user $user -n ${quote(cmp.flattenToString())} --windowingMode 5"
                    if (!Shell.cmd(command).exec().isSuccess) return@runCatching false
                    delay(700)
                    val after = Shell.cmd("dumpsys activity activities").exec()
                    after.isSuccess && SystemFreeformEvidence.confirmed(before.out.joinToString("\n"),
                        after.out.joinToString("\n"), packageName, hasLmo)
                }.getOrDefault(false)
            }
            ensureActive()
            if (!ok) report(ctx)
        }
    }

    private fun report(ctx: Context) {
        Toast.makeText(ctx, AppLocale.getString(ctx, R.string.floating_root_unavailable), Toast.LENGTH_LONG).show()
    }
}
