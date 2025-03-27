package com.powersync.db.internal

import com.powersync.PowerSyncException
import com.powersync.PsSqlDriver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

internal class ConnectionPool(
    factory: () -> PsSqlDriver,
    val size: Int = 5,
    private val scope: CoroutineScope,
) {
    private val available = Channel<Pair<TransactorDriver, CompletableDeferred<Unit>>>()
    private val connections: List<Job> =
        List(size) {
            scope.launch {
                val driver = TransactorDriver(factory())
                try {
                    while (true) {
                        val done = CompletableDeferred<Unit>()
                        try {
                            available.send(driver to done)
                        } catch (_: ClosedSendChannelException) {
                            break // Pool closed
                        }

                        done.await()
                    }
                } finally {
                    driver.driver.close()
                }
            }
        }

    suspend fun <R> withConnection(action: suspend (connection: TransactorDriver) -> R): R {
        val (connection, done) =
            try {
                available.receive()
            } catch (e: PoolClosedException) {
                throw PowerSyncException(
                    message = "Cannot process connection pool request",
                    cause = e,
                )
            }

        try {
            return action(connection)
        } finally {
            done.complete(Unit)
        }
    }

    suspend fun <R> withAllConnections(action: suspend (connections: List<TransactorDriver>) -> R): R {
        val connections = mutableListOf<Pair<TransactorDriver, CompletableDeferred<Unit>>>()

        // Try and get all the connections
        for (i in 1..connections.size) {
            try {
                connections.add(available.receive())
            } catch (e: PoolClosedException) {
                // Free any connections we already received
                connections.forEach { it.second.complete(Unit) }
                throw PowerSyncException(
                    message = "Cannot process connection pool request",
                    cause = e,
                )
            }
        }

        try {
            return action(connections.map { it.first })
        } finally {
            connections.forEach { it.second.complete(Unit) }
        }
    }

    suspend fun close() {
        available.cancel(PoolClosedException)
        connections.joinAll()
    }
}

internal object PoolClosedException : CancellationException("Pool is closed")
