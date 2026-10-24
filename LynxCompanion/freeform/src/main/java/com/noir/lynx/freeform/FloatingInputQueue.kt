package com.noir.lynx.freeform

import android.view.MotionEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.ArrayDeque

/** Bounded input queue. Only consecutive MOVE events are coalesced; pointer boundaries survive. */
internal class FloatingInputQueue(scope: CoroutineScope, private val inject: (MotionEvent) -> Boolean,
                                  private val failed: () -> Unit) {
    private val queue = ArrayDeque<MotionEvent>()
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private var stopped = false
    private val worker = scope.launch(Dispatchers.IO) {
        for (ignored in signal) {
            while (true) {
                val event = synchronized(queue) { queue.pollFirst() } ?: break
                val ok = try { inject(event) } catch (_: Exception) { false } finally { event.recycle() }
                if (!ok) {
                    stop()
                    withContext(Dispatchers.Main) { failed() }
                    return@launch
                }
            }
        }
    }

    fun offer(event: MotionEvent): Boolean = synchronized(queue) {
        if (stopped) return false
        if (event.actionMasked == MotionEvent.ACTION_MOVE && queue.peekLast()?.actionMasked == MotionEvent.ACTION_MOVE) {
            queue.removeLast().recycle()
        }
        if (queue.size >= 64) {
            // Abort the session rather than dropping UP/POINTER_UP and leaving a stuck gesture.
            return false
        }
        queue.addLast(MotionEvent.obtain(event))
        signal.trySend(Unit)
        true
    }

    private fun stop() = synchronized(queue) {
        stopped = true
        while (queue.isNotEmpty()) queue.removeFirst().recycle()
        signal.close()
    }
    fun close() { stop(); worker.cancel() }
}
