package com.noir.lynx.feature.floating

import android.content.*
import android.os.IBinder
import android.view.WindowManager
import android.widget.Toast
import com.noir.lynx.R
import com.noir.lynx.core.locale.AppLocale
import com.noir.lynx.feature.floating.root.FloatingRootService
import com.noir.lynx.feature.floating.root.IFloatingRoot
import com.noir.lynx.feature.hud.HudApp
import com.topjohnwu.superuser.ipc.RootService
import kotlinx.coroutines.*

/** Connects only on user launch; the root service is owned by the HUD session. */
object StandaloneApps {
    private var context: Context? = null
    private var wm: WindowManager? = null
    private var scope: CoroutineScope? = null
    private var job: Job? = null
    private var connection: ServiceConnection? = null
    private var pending: CompletableDeferred<IFloatingRoot>? = null
    private val windows = mutableSetOf<StandaloneAppWindow>()
    private var generation = 0

    fun init(context: Context, wm: WindowManager, parent: CoroutineScope) {
        this.context = context
        generation++
        this.wm = wm
        job = SupervisorJob(parent.coroutineContext[Job])
        scope = CoroutineScope(parent.coroutineContext + job!!)
    }

    private suspend fun connect(ctx: Context): IFloatingRoot {
        pending?.let { return withTimeoutOrNull(12000) { it.await() } ?: error("Root bind timed out") }
        val ready = CompletableDeferred<IFloatingRoot>()
        pending = ready
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                ready.complete(IFloatingRoot.Stub.asInterface(service))
            }
            override fun onServiceDisconnected(name: ComponentName) {
                windows.toList().forEach { it.close() }
                windows.clear()
                pending = null
                connection = null
                ready.completeExceptionally(IllegalStateException("Root service disconnected"))
            }
        }
        connection = conn
        RootService.bind(Intent(ctx, FloatingRootService::class.java), conn)
        return try { withTimeoutOrNull(12000) { ready.await() } ?: error("Root bind timed out") } catch (e: Exception) {
            runCatching { RootService.unbind(conn) }
            if (pending === ready) { pending = null; connection = null }
            throw e
        }
    }

    fun launch(app: HudApp, widthPct: Int, heightPct: Int) {
        val ctx = context ?: return
        val manager = wm ?: return
        val sessionScope = scope ?: return
        val epoch = generation
        sessionScope.launch {
            try {
                check(windows.size < 4)
                val component = ctx.packageManager.getLaunchIntentForPackage(app.packageName)?.component
                    ?: error("No launcher activity")
                val remote = connect(ctx)
                ensureActive()
                if (epoch != generation) return@launch
                check(windows.size < 4)
                lateinit var window: StandaloneAppWindow
                window = StandaloneAppWindow(AppLocale.wrap(ctx), manager, sessionScope, remote, app.label,
                    component.flattenToString(), widthPct, heightPct) { windows.remove(window) }
                windows.add(window)
                window.show()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { Toast.makeText(ctx, AppLocale.getString(ctx, R.string.floating_root_unavailable), Toast.LENGTH_LONG).show() }
        }
    }

    fun viewportChanged() { windows.toList().forEach { it.clampToViewport() } }
    fun closeWindows() { generation++; windows.toList().forEach { it.close() }; windows.clear() }

    fun destroy() {
        closeWindows()
        connection?.let { runCatching { RootService.unbind(it) } }
        job?.cancel()
        job = null; scope = null; connection = null; pending = null; wm = null; context = null
    }
}
