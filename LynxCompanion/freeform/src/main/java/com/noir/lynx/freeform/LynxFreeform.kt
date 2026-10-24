package com.noir.lynx.freeform

import android.content.*
import android.os.*
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.MainThread
import com.noir.lynx.freeform.root.FloatingRootService
import com.noir.lynx.freeform.root.IFloatingRoot
import com.topjohnwu.superuser.ipc.RootService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Reusable APK-owned engine. Call the public lifecycle methods on the main thread. */
class LynxFreeform(
    private val context: Context,
    parent: CoroutineScope,
    private val config: FreeformConfig = FreeformConfig()
) : AutoCloseable {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val job = SupervisorJob(parent.coroutineContext[Job])
    private val scope = CoroutineScope(parent.coroutineContext + job + Dispatchers.Main.immediate)
    private val mutableEvents = MutableSharedFlow<FreeformEvent>(extraBufferCapacity = 64)
    val events = mutableEvents.asSharedFlow()
    private var connection: ServiceConnection? = null
    private var pending: CompletableDeferred<IFloatingRoot>? = null
    private val windows = mutableMapOf<String, StandaloneAppWindow>()
    private val launching = mutableSetOf<String>()
    private var generation = 0
    private var closed = false
    private val parentCompletion = parent.coroutineContext[Job]?.invokeOnCompletion {
        Handler(Looper.getMainLooper()).post { close() }
    }

    private suspend fun connect(): IFloatingRoot {
        pending?.let { return withTimeoutOrNull(12000) { it.await() } ?: error("Root bind timeout") }
        val ready = CompletableDeferred<IFloatingRoot>()
        pending = ready
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                ready.complete(IFloatingRoot.Stub.asInterface(service))
            }
            override fun onServiceDisconnected(name: ComponentName) {
                closeWindows(); pending = null; connection = null
                ready.completeExceptionally(IllegalStateException("Root disconnected"))
            }
            override fun onNullBinding(name: ComponentName) {
                ready.completeExceptionally(IllegalStateException("Root binding rejected"))
            }
            override fun onBindingDied(name: ComponentName) = onServiceDisconnected(name)
        }
        connection = conn
        return try {
            RootService.bind(Intent(context, FloatingRootService::class.java), conn)
            withTimeoutOrNull(12000) { ready.await() } ?: error("Root bind timeout")
        } catch (e: Exception) {
            runCatching { RootService.unbind(conn) }
            if (pending === ready) { pending = null; connection = null }
            throw e
        }
    }

    @MainThread
    fun launch(packageName: String, label: String? = null, widthPercent: Int = 55, heightPercent: Int = 50) {
        check(!closed) { "Engine closed" }
        windows[packageName]?.let { it.reveal(); return }
        if (!launching.add(packageName)) return
        val epoch = generation
        scope.launch {
            try {
                if (Build.VERSION.SDK_INT < 29) { failed(packageName, "android_version"); return@launch }
                if (!Settings.canDrawOverlays(context)) { failed(packageName, "overlay_permission"); return@launch }
                if (windows.size >= config.maxWindows) { failed(packageName, "window_limit"); return@launch }
                val component = context.packageManager.getLaunchIntentForPackage(packageName)?.component
                    ?: error("No launcher activity")
                mutableEvents.tryEmit(FreeformEvent(packageName, FreeformStage.CONNECTING))
                val remote = connect()
                ensureActive()
                if (closed || epoch != generation) return@launch
                if (windows.size >= config.maxWindows) { failed(packageName, "window_limit"); return@launch }
                val appLabel = label ?: context.packageManager.getApplicationLabel(
                    context.packageManager.getApplicationInfo(packageName, 0)).toString()
                lateinit var window: StandaloneAppWindow
                window = StandaloneAppWindow(context, wm, scope, remote, appLabel,
                    component.flattenToString(), widthPercent, heightPercent, config,
                    { mutableEvents.tryEmit(it) }) { if (windows[packageName] === window) windows.remove(packageName) }
                windows[packageName] = window; window.show()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                failed(packageName, "connection_unavailable")
            } finally { launching.remove(packageName) }
        }
    }

    private fun failed(packageName: String, reason: String) {
        mutableEvents.tryEmit(FreeformEvent(packageName, FreeformStage.FAILED, reason))
        Toast.makeText(context, FloatingFailure.message(reason), Toast.LENGTH_LONG).show()
    }

    @MainThread fun viewportChanged() { windows.values.toList().forEach { it.clampToViewport() } }
    @MainThread fun closeWindows() { generation++; windows.values.toList().forEach { it.close() }; windows.clear() }

    @MainThread override fun close() {
        if (closed) return
        closed = true; closeWindows()
        connection?.let { runCatching { RootService.unbind(it) } }
        parentCompletion?.dispose(); job.cancel()
        pending?.cancel(); connection = null; pending = null
    }
}
