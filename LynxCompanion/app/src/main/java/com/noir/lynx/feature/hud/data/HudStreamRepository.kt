package com.noir.lynx.feature.hud.data

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/** Owns a read-only native collector; never starts the profile daemon or updates a module. */
class HudStreamRepository(private val context: Context) {
    private val mutable = MutableStateFlow(HudSnapshot())
    val snapshots = mutable.asStateFlow()
    @Volatile private var process: Process? = null

    suspend fun collect() = withContext(Dispatchers.IO) {
        val executable = File(context.applicationInfo.nativeLibraryDir, "liblynxhud.so")
        if (!executable.canExecute()) { unavailable("unsupported"); return@withContext }
        try {
            val child = ProcessBuilder("su", "-c", "exec '${executable.absolutePath}' hud stream")
                .redirectError(File("/dev/null")).start()
            process = child
            child.inputStream.bufferedReader().use { reader ->
                while (currentCoroutineContext().isActive) {
                    val line = reader.readLine() ?: break
                    if (line.length > 262144) continue
                    runCatching { HudSnapshot.parse(line) }.getOrNull()?.let { mutable.value = it }
                }
            }
            if (currentCoroutineContext().isActive) unavailable("permission_denied")
        } catch (_: Exception) { unavailable("permission_denied") }
        finally { stop() }
    }

    fun unavailable(status: String) {
        mutable.value = HudSnapshot(metrics = HudMetric.values().associate { it.key to HudReading(unit = it.unit, status = status) })
    }

    fun stop() {
        val child = process ?: return
        process = null
        runCatching { child.outputStream.write("stop\n".toByteArray()); child.outputStream.flush(); child.outputStream.close() }
        // Pipe EOF also stops the native process if the app is killed by Android.
        runCatching { child.inputStream.close() }
        child.destroy()
    }

    fun current(): HudSnapshot = snapshots.value.fresh(SystemClock.elapsedRealtime())
}
