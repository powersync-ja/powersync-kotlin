package com.powersync.db.driver

import androidx.sqlite.SQLiteConnection
import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.PowerSyncException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/**
 * The read-part of a [SQLiteConnectionPool] backed by connections owned by the PowerSync SDK.
 */
@OptIn(ExperimentalPowerSyncAPI::class)
internal class ReadPool(
    factory: () -> SQLiteConnection,
    size: Int,
    private val scope: CoroutineScope,
) {
    private val available = Channel<Pair<SQLiteConnection, CompletableDeferred<Unit>>>()
    private val connections: List<Job> =
        List(size) {
            scope.launch {
                val driver = factory()
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
                    driver.close()
                }
            }
        }

    suspend fun <T> read(block: suspend (SQLiteConnectionLease) -> T): T {
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
            return block(RawConnectionLease(connection))
        } finally {
            done.complete(Unit)
        }
    }

    suspend fun <R> withAllConnections(action: suspend (connections: List<SQLiteConnection>) -> R): R {
        val obtainedConnections = mutableListOf<Pair<SQLiteConnection, CompletableDeferred<Unit>>>()

        try {
            /**
             * This attempts to receive (all) the number of available connections.
             * This creates a hold for each connection received. This means that subsequent
             * receive operations must return unique connections until all the available connections
             * have a hold.
             */
            repeat(connections.size) {
                try {
                    obtainedConnections.add(available.receive())
                } catch (e: PoolClosedException) {
                    throw PowerSyncException(
                        message = "Cannot process connection pool request",
                        cause = e,
                    )
                }
            }

            return action(obtainedConnections.map { it.first })
        } finally {
            obtainedConnections.forEach { it.second.complete(Unit) }
        }
    }

    suspend fun close() {
        available.cancel(PoolClosedException)
        connections.joinAll()
    }
}

internal object PoolClosedException : CancellationException("Pool is closed")
