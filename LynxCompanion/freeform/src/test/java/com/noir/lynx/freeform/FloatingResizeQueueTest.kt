package com.noir.lynx.freeform

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class FloatingResizeQueueTest {
    @Test fun rapidResizesAreSerializedAndIntermediateRequestsCoalesced() = runBlocking {
        withTimeout(5000) {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val done = CompletableDeferred<Unit>()
            val calls = mutableListOf<Int>(); val commits = mutableListOf<Int>()
            var active = 0; var maxActive = 0
            val q = FloatingResizeQueue(this, 1, apply = { value ->
                active++; maxActive = maxOf(maxActive, active); calls.add(value)
                if (value == 2) { entered.complete(Unit); release.await() }
                active--; true
            }, settled = { value, ok ->
                assertTrue(ok); commits.add(value); done.complete(Unit)
            }, debounceMs = 0)
            q.request(2); entered.await()
            q.request(3); q.request(4); release.complete(Unit); done.await()
            assertEquals(listOf(2, 4), calls)
            assertEquals(listOf(4), commits)
            assertEquals(1, maxActive); assertFalse(q.busy)
            q.close()
        }
    }

    @Test fun closeDuringIpcSuppressesLateCommit() = runBlocking {
        withTimeout(5000) {
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val finished = CompletableDeferred<Unit>(); val commits = mutableListOf<Int>()
            val q = FloatingResizeQueue(this, 1, apply = { value ->
                entered.complete(Unit); release.await(); finished.complete(Unit); value == 2
            }, settled = { value, _ -> commits.add(value) }, debounceMs = 0)
            q.request(2); entered.await(); q.close(); release.complete(Unit); finished.await(); yield()
            assertTrue(commits.isEmpty())
        }
    }

    @Test fun supersededFailureDoesNotCloseNewerRequest() = runBlocking {
        withTimeout(5000) {
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val done = CompletableDeferred<Unit>(); val results = mutableListOf<Pair<Int, Boolean>>()
            val q = FloatingResizeQueue(this, 1, apply = { value ->
                if (value == 2) { entered.complete(Unit); release.await(); false } else true
            }, settled = { value, ok -> results.add(value to ok); done.complete(Unit) }, debounceMs = 0)
            q.request(2); entered.await(); q.request(3); release.complete(Unit); done.await()
            assertEquals(listOf(3 to true), results)
            q.close()
        }
    }
}
