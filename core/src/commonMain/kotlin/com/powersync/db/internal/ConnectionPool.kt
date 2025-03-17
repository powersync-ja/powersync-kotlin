package com.powersync.db.internal

import co.touchlab.stately.concurrency.AtomicBoolean
import com.powersync.PowerSyncException
import com.powersync.PsSqlDriver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class WrappedConnection(
    val transactorDriver: TransactorDriver,
) {
    var busy: Boolean = false
}

internal data class DeferredAction<R>(
    val deferred: CompletableDeferred<R>,
    val action: suspend (connection: TransactorDriver) -> R,
)

internal class ConnectionPool(
    factory: () -> PsSqlDriver,
    size: Int = 5,
    private val scope: CoroutineScope,
) {
    val closed = AtomicBoolean(false)

    private val mutex = Mutex()
    private val connections = List(size) { WrappedConnection(TransactorDriver(factory())) }
    private val queue = mutableListOf<DeferredAction<Any?>>()
    private val activeOperations = mutableListOf<CompletableDeferred<Any?>>()

    @Suppress("UNCHECKED_CAST")
    suspend fun <R> withConnection(action: suspend (connection: TransactorDriver) -> R): R {
        if (closed.value) {
            throw PowerSyncException(
                message = "Cannot process connection pool request",
                cause = Exception("Pool is closed"),
            )
        }

        val wrappedDeferred = DeferredAction(CompletableDeferred<Any?>(), action)

        val connection =
            mutex.withLock {
                // check if any connections are free
                val freeConnection = connections.find { !it.busy }
                if (freeConnection == null) {
                    queue.add(wrappedDeferred)
                    return@withLock null
                }
                activeOperations.add(wrappedDeferred.deferred)
                freeConnection.busy = true
                return@withLock freeConnection
            }

        if (connection != null) {
            // Can process immediately
            scope.launch {
                processRequest(wrappedDeferred, connection)
            }
        }

        val result = wrappedDeferred.deferred.await()
        if (result != null) {
            val casted =
                result as? R
                    ?: throw PowerSyncException(
                        "Could not cast deferred result",
                        Exception("Casting error"),
                    )
            return casted
        }
        return null as R
    }

    private suspend fun processRequest(
        request: DeferredAction<Any?>,
        wrappedConnection: WrappedConnection,
    ) {
        try {
            val result = request.action(wrappedConnection.transactorDriver)
            request.deferred.complete(result)
        } catch (exception: Exception) {
            request.deferred.completeExceptionally(exception)
        } finally {
            mutex.withLock {
                wrappedConnection.busy = false
                activeOperations.remove(request.deferred)
            }
            scope.launch {
                processNext()
            }
        }
    }

    private suspend fun processNext() {
        val next: Pair<DeferredAction<Any?>, WrappedConnection> =
            mutex.withLock {
                if (queue.size == 0) {
                    return
                }
                // check if a connection is open
                val connection = connections.find { !it.busy }
                if (connection == null) {
                    return
                }
                val next = queue.first()
                queue.removeFirst()

                connection.busy = true
                return@withLock next to connection
            }

        processRequest(next.first, next.second)
    }

    suspend fun close() {
        if (closed.value) {
            return
        }

        closed.value = true
        val activeOperations =
            mutex.withLock {
                // These should all be pending
                for (item in queue) {
                    item.deferred.completeExceptionally(
                        PowerSyncException(
                            message = "Pending requests are aborted",
                            cause = Exception("Pool has been closed"),
                        ),
                    )
                }
                queue.clear()
                // Return a copy of active items in order to check them
                return@withLock activeOperations.toList()
            }

        // Wait for all pending operations
        // Alternatively we could cancel and ongoing jobs
        activeOperations.joinAll()

        // Close all connections
        for (connection in connections) {
            connection.transactorDriver.driver.close()
        }
    }
}
