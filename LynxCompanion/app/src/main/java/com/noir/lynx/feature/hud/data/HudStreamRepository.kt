package com.noir.lynx.feature.hud.data

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
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

    private fun resolveCollectorBinary(): String? {
        // Tier 1: Bundled native .so in APK nativeLibraryDir
        val bundled = File(context.applicationInfo.nativeLibraryDir, "liblynxhud.so")
        if (bundled.exists()) return bundled.absolutePath

        // Tier 2: Magisk/KernelSU/APatch installed module binary
        val modLynxd = File("/data/adb/modules/Lynx/system/bin/lynxd")
        if (modLynxd.exists()) return modLynxd.absolutePath

        // Tier 3: Standalone root binary in /data/local/tmp/lynxd
        val tmpLynxd = File("/data/local/tmp/lynxd")
        if (tmpLynxd.exists()) return tmpLynxd.absolutePath

        return if (bundled.absolutePath.isNotBlank()) bundled.absolutePath else null
    }

    suspend fun collect() = withContext(Dispatchers.IO) {
        while (currentCoroutineContext().isActive) {
            val binPath = resolveCollectorBinary()
            if (binPath == null) {
                unavailable("unsupported")
                delay(2000)
                continue
            }

            var child: Process? = null
            try {
                // Execute unquoted path to avoid single/double quote nesting parsing bugs in Android sh/toybox
                child = ProcessBuilder("su", "-c", "exec $binPath hud stream")
                    .redirectError(File("/dev/null"))
                    .start()
                process = child

                var receivedAny = false
                child.inputStream.bufferedReader().use { reader ->
                    while (currentCoroutineContext().isActive) {
                        val line = reader.readLine() ?: break
                        if (line.length > 262144) continue
                        val snapshot = runCatching { HudSnapshot.parse(line) }.getOrNull()
                        if (snapshot != null) {
                            receivedAny = true
                            mutable.value = snapshot
                        }
                    }
                }
                if (currentCoroutineContext().isActive && !receivedAny) {
                    unavailable("permission_denied")
                }
            } catch (e: Exception) {
                Log.w("HudStream", "Collector stream execution failed: ${e.message}")
                if (currentCoroutineContext().isActive) {
                    unavailable("permission_denied")
                }
            } finally {
                stop()
            }

            // Brief backoff before auto-reconnecting stream
            if (currentCoroutineContext().isActive) {
                delay(1500)
            }
        }
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
