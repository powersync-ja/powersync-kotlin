package com.powersync.attachments

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class FIFOLock {
    private val mutex = Mutex()
    private val queue = mutableListOf<CompletableDeferred<Unit>>()

    suspend fun lock() {
        val deferred = CompletableDeferred<Unit>()

        mutex.withLock {
            queue.add(deferred)
            // If this is the only request, grant the lock immediately
            if (queue.size == 1) {
                deferred.complete(Unit)
            }
        }

        // Suspend until the lock is granted
        deferred.await()
    }

    suspend fun <R> withLock(action: suspend () -> R): R {
        lock()
        return try {
            action()
        } finally {
            unlock()
        }
    }

    suspend fun unlock() {
        mutex.withLock {
            // Remove the current lock holder
            queue.removeAt(0)
            // Grant the lock to the next request in the queue, if any
            queue.firstOrNull()?.complete(Unit)
        }
    }
}
