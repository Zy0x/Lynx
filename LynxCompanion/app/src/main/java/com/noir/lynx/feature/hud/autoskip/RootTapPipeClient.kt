package com.noir.lynx.feature.hud.autoskip

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Dedicated synchronous root shell pipe for synthetic touch injection.
 * Isolates tap execution from shared `libsu` queues and guarantees zero-backlog delivery.
 */
internal class RootTapPipeClient(private val scope: CoroutineScope) {
    companion object {
        private const val TAP_SENTINEL = "__LYNX_TAP_OK__"
    }

    private val tapLock = Any()
    private var tapProc: Process? = null
    private var tapWriter: BufferedWriter? = null
    private var tapReader: BufferedReader? = null

    @Volatile
    var tapInFlight: Boolean = false
        private set

    @Volatile
    var lastTapStartedMs: Long = 0L
        private set

    @Volatile
    var lastTapEndedMs: Long = 0L
        private set

    private fun ensureTapPipe(): Boolean {
        synchronized(tapLock) {
            val existing = tapProc
            if (existing != null && existing.isAlive && tapWriter != null && tapReader != null) {
                return true
            }
            closeLocked()
            return try {
                val suBin = com.noir.lynx.data.LynxRepository.getSuBinary()
                val proc = ProcessBuilder(suBin).redirectErrorStream(true).start()
                tapProc = proc
                tapWriter = BufferedWriter(OutputStreamWriter(proc.outputStream))
                tapReader = BufferedReader(InputStreamReader(proc.inputStream))
                true
            } catch (_: Throwable) {
                closeLocked()
                false
            }
        }
    }

    private fun closeLocked() {
        runCatching { tapWriter?.close() }
        runCatching { tapReader?.close() }
        runCatching { tapProc?.destroy() }
        tapWriter = null
        tapReader = null
        tapProc = null
    }

    fun closeAsync() {
        scope.launch(Dispatchers.IO) {
            synchronized(tapLock) { closeLocked() }
        }
    }

    fun executeSingleTapSync(tapBin: String, x: Int, y: Int): Long {
        val start = SystemClock.uptimeMillis()
        lastTapStartedMs = start
        tapInFlight = true
        try {
            if (!ensureTapPipe()) return 0L
            synchronized(tapLock) {
                val writer = tapWriter ?: return 0L
                val reader = tapReader ?: return 0L
                writer.write("$tapBin $x $y >/dev/null 2>&1; echo $TAP_SENTINEL\n")
                writer.flush()
                while (true) {
                    val line = reader.readLine() ?: run {
                        closeLocked()
                        return 0L
                    }
                    if (line.contains(TAP_SENTINEL)) break
                }
            }
        } catch (_: Throwable) {
            synchronized(tapLock) { closeLocked() }
        } finally {
            tapInFlight = false
            lastTapEndedMs = SystemClock.uptimeMillis()
        }
        return (lastTapEndedMs - start).coerceAtLeast(0L)
    }
}
