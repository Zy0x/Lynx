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

    /**
     * Last confirmed profile from broadcast sync (LynxProfileSyncBus).
     * Acts as an optimistic UI update when the user taps a profile,
     * but auto-expires after 2.5 seconds or once the native stream catches up,
     * preventing the HUD from getting stuck in "Kinerja" when lynxd switches to "Balance".
     */
    @Volatile var lastConfirmedProfile: String? = null
    @Volatile private var lastConfirmedProfileTimestamp: Long = 0L

    private fun resolveCollectorBinary(): String? {
        // Tier 1: Bundled native .so in APK nativeLibraryDir
        val bundled = File(context.applicationInfo.nativeLibraryDir, "liblynxhud.so")
        if (bundled.exists() && bundled.canExecute()) return bundled.absolutePath

        // Tier 2: Magisk/KernelSU/APatch installed module binary
        val modLynxd = File("/data/adb/modules/Lynx/system/bin/lynxd")
        if (modLynxd.exists() && modLynxd.canExecute()) return modLynxd.absolutePath

        // Tier 3: Standalone root binary in /data/local/tmp/lynxd
        val tmpLynxd = File("/data/local/tmp/lynxd")
        if (tmpLynxd.exists() && tmpLynxd.canExecute()) return tmpLynxd.absolutePath

        if (bundled.exists()) return bundled.absolutePath
        if (tmpLynxd.exists()) return tmpLynxd.absolutePath
        return null
    }

    suspend fun collect() = withContext(Dispatchers.IO) {
        while (currentCoroutineContext().isActive) {
            val binPath = resolveCollectorBinary()
            if (binPath == null) {
                if (mutable.value.metrics.isEmpty()) unavailable("unsupported")
                delay(2000)
                continue
            }

            var child: Process? = null
            try {
                // Execute unquoted path to avoid single/double quote nesting parsing bugs in Android sh/toybox
                val suBin = com.noir.lynx.data.LynxRepository.getSuBinary()
                child = ProcessBuilder(suBin, "-c", "exec $binPath hud stream")
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
                            // Apply lastConfirmedProfile optimistic override: prevents visual flicker
                            // immediately after user tap, but auto-clears once stream catches up or after 2.5s.
                            val confirmed = lastConfirmedProfile
                            val elapsedSinceConfirmed = SystemClock.elapsedRealtime() - lastConfirmedProfileTimestamp
                            val merged = if (confirmed != null && confirmed.isNotBlank() && elapsedSinceConfirmed < 2500L && snapshot.profile != confirmed) {
                                snapshot.copy(profile = confirmed)
                            } else {
                                if (confirmed != null && (snapshot.profile == confirmed || elapsedSinceConfirmed >= 2500L)) {
                                    lastConfirmedProfile = null
                                }
                                snapshot
                            }
                            mutable.value = merged
                        }
                    }
                }
                // If stream was interrupted or briefly reconnects, retain latest metrics.
                // Only mark permission_denied if we never received any valid snapshot at all.
                if (currentCoroutineContext().isActive && !receivedAny && mutable.value.metrics.isEmpty()) {
                    unavailable("permission_denied")
                }
            } catch (e: Exception) {
                Log.w("HudStream", "Collector stream execution failed: ${e.message}")
                if (currentCoroutineContext().isActive && mutable.value.metrics.isEmpty()) {
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

    fun updateProfileDirect(profile: String) {
        val norm = profile.trim().lowercase()
        // Update lastConfirmedProfile as optimistic ground truth from sync bus
        lastConfirmedProfile = norm
        lastConfirmedProfileTimestamp = SystemClock.elapsedRealtime()
        // Immediately patch the live snapshot so HUD shows the correct profile
        // without waiting for the next stream frame (zero-latency visual update)
        mutable.value = mutable.value.copy(profile = norm)
    }

    fun current(): HudSnapshot = snapshots.value.fresh(SystemClock.elapsedRealtime())
}
