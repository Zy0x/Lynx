package com.noir.lynx.feature.floating

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** One IPC worker; cancellation never starts a competing synchronous Binder resize. */
internal class FloatingResizeQueue<T>(
    scope: CoroutineScope,
    initial: T,
    private val apply: suspend (T) -> Boolean,
    private val settled: (T, Boolean) -> Unit,
    private val debounceMs: Long = 150,
    private val busyChanged: (Boolean) -> Unit = {}
) {
    private val requests = Channel<T>(Channel.CONFLATED)
    private var desired = initial
    private var applied = initial
    private var stopped = false
    var busy = false
        private set
    private val worker = scope.launch {
        for (request in requests) {
            delay(debounceMs)
            var target = request
            while (true) target = requests.tryReceive().getOrNull() ?: break
            val ok = if (target == applied) true else {
                withContext(NonCancellable) { try { apply(target) } catch (_: Exception) { false } }
            }
            if (stopped) break
            if (ok) applied = target
            if (desired == target) {
                busy = false; busyChanged(false)
                settled(target, ok)
            }
        }
    }

    fun request(target: T) {
        if (stopped || target == desired) return
        desired = target
        if (!busy) { busy = true; busyChanged(true) }
        requests.trySend(target)
    }

    fun close() { stopped = true; requests.close(); worker.cancel() }
}
