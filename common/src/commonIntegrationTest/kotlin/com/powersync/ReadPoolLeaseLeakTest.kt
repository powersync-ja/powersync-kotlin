package com.powersync

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig
import co.touchlab.kermit.platformLogWriter
import com.powersync.db.schema.Schema
import com.powersync.test.factory
import com.powersync.test.getTempDir
import com.powersync.testutils.UserRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Regression test for https://github.com/powersync-ja/powersync-kotlin/issues/356
 *
 * A `read {}` (or the `withAllConnections` acquisition loop) cancelled during the channel
 * rendezvous used to silently drop its connection lease: the element was handed to the channel
 * but the cancelled receiver never ran the `done.complete(Unit)` in its `finally`, so the owning
 * read worker parked in `done.await()` forever and the connection was permanently lost from the
 * pool. After even one such leak, `ReadPool.withAllConnections` (used by `updateSchema`) can never
 * acquire all connections and deadlocks while holding the database mutex.
 *
 * This must run on real dispatchers - the cancellation race does not surface under the
 * single-threaded virtual-time test dispatcher.
 */
class ReadPoolLeaseLeakTest {
    @Test
    fun updateSchemaSurvivesConcurrentReadCancellation() =
        runBlocking(Dispatchers.Default) {
            val logger =
                Logger(
                    StaticConfig(
                        minSeverity = Severity.Warn,
                        logWriterList = listOf(platformLogWriter()),
                    ),
                )
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val schema = Schema(UserRow.table)

            val db =
                createPowerSyncDatabaseImpl(
                    factory = factory,
                    schema = schema,
                    dbFilename = "leak-test-${(0..1_000_000).random()}.db",
                    dbDirectory = getTempDir(),
                    logger = logger,
                    scope = scope,
                )

            try {
                // Force initialization so the read workers are up and offering connections.
                db.readLock { }

                // Hammer the read pool with reads that are cancelled at varied points, under
                // contention. A read cancelled during the channel rendezvous leaks its connection
                // lease permanently (pre-fix), after which withAllConnections() can never get all
                // connections.
                val attackers =
                    List(64) {
                        scope.launch(Dispatchers.Default) {
                            repeat(2_000) { i ->
                                // 0ms cancels essentially immediately (most likely to land in the
                                // handoff); 1ms/2ms spread the cancellation across other points.
                                withTimeoutOrNull((i % 3).milliseconds) { db.readLock { } }
                            }
                        }
                    }
                attackers.joinAll()

                // If any lease leaked, the pool is permanently short and this never returns.
                // PRE-FIX: times out. POST-FIX: completes in a few ms.
                withTimeout(10.seconds) {
                    db.updateSchema(schema)
                }
            } finally {
                // If a lease leaked, readPool.close() -> connections.joinAll() would itself block
                // forever on the worker parked in done.await(). Bound the cleanup so the test fails
                // fast on the updateSchema timeout instead of hanging; cancelling the scope tears
                // down any leaked worker either way.
                withTimeoutOrNull(5.seconds) { db.close() }
                scope.cancel()
            }
        }
}
