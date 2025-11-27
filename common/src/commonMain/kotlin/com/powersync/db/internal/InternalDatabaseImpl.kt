package com.powersync.db.internal

import co.touchlab.kermit.Logger
import com.powersync.DispatchFunction
import com.powersync.DispatchStrategy
import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.db.SqlCursor
import com.powersync.db.ThrowableLockCallback
import com.powersync.db.ThrowableTransactionCallback
import com.powersync.db.driver.SQLiteConnectionLease
import com.powersync.db.driver.SQLiteConnectionPool
import com.powersync.db.runWrapped
import com.powersync.utils.AtomicMutableSet
import com.powersync.utils.JsonUtil
import com.powersync.utils.throttle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.transform
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalPowerSyncAPI::class)
internal class InternalDatabaseImpl(
    private val pool: SQLiteConnectionPool,
    private val logger: Logger,
    dispatchStrategy: DispatchStrategy,
) : InternalDatabase {
    override suspend fun execute(
        sql: String,
        parameters: List<Any?>?,
    ): Long =
        writeLock { context ->
            context.execute(sql, parameters)
        }

    private val dispatch: DispatchFunction = dispatchStrategy.dispatchFunction

    override suspend fun updateSchema(schemaJson: String) {
        dispatch {
            runWrapped {
                pool.withAllConnections { writer, readers ->
                    writer.runTransaction { tx ->
                        tx.getOptional(
                            "SELECT powersync_replace_schema(?);",
                            listOf(schemaJson),
                        ) {}
                    }

                    // Update the schema on all read connections
                    for (readConnection in readers) {
                        readConnection.execSQL("pragma table_info('sqlite_master')")
                    }
                }
            }
        }
    }

    override suspend fun <RowType : Any> get(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType,
    ): RowType = readLock { connection -> connection.get(sql, parameters, mapper) }

    override suspend fun <RowType : Any> getAll(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType,
    ): List<RowType> = readLock { connection -> connection.getAll(sql, parameters, mapper) }

    override suspend fun <RowType : Any> getOptional(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType,
    ): RowType? = readLock { connection -> connection.getOptional(sql, parameters, mapper) }

    override fun onChange(
        tables: Set<String>,
        throttleMs: Long,
        triggerImmediately: Boolean,
    ): Flow<Set<String>> {
        // Match all possible internal table combinations
        val watchedTables =
            tables.flatMap { listOf(it, "ps_data__$it", "ps_data_local__$it") }.toSet()

        return rawChangedTables(watchedTables, throttleMs, triggerImmediately).map {
            it.mapTo(mutableSetOf(), ::friendlyTableName)
        }
    }

    override fun <RowType : Any> watch(
        sql: String,
        parameters: List<Any?>?,
        throttleMs: Long,
        mapper: (SqlCursor) -> RowType,
    ): Flow<List<RowType>> =
        flow {
            // Fetch the tables asynchronously with getAll
            val tables =
                getSourceTables(sql, parameters)
                    .filter { it.isNotBlank() }
                    .toSet()

            val queries =
                rawChangedTables(tables, throttleMs, triggerImmediately = true).map {
                    logger.v { "Fetching watch() query: $sql" }
                    val rows = getAll(sql, parameters = parameters, mapper = mapper)
                    logger.v { "watch query $sql done, emitting downstream" }
                    rows
                }
            emitAll(queries)
        }

    private fun rawChangedTables(
        tableNames: Set<String>,
        throttleMs: Long,
        triggerImmediately: Boolean,
    ): Flow<Set<String>> =
        flow {
            val batchedUpdates = AtomicMutableSet<String>()

            updatesOnTables()
                .onSubscription {
                    if (triggerImmediately) {
                        // Emit an initial event (if requested). No changes would be detected at this point
                        emit(initialUpdateSentinel)
                    }
                }.transform { updates ->
                    if (updates === initialUpdateSentinel) {
                        // This should always be emitted despite being empty and not intersecting with
                        // the tables we care about.
                        emit(Unit)
                    } else {
                        val intersection = updates.intersect(tableNames)
                        if (intersection.isNotEmpty()) {
                            batchedUpdates.addAll(intersection)
                            emit(Unit)
                        }
                    }
                }
                // Throttling here is a feature which prevents watch queries from spamming updates.
                // Throttling by design discards and delays events within the throttle window. Discarded events
                // still trigger a trailing edge update.
                // Backpressure is avoided on the throttling and consumer level by buffering the last upstream value.
                .throttle(throttleMs.milliseconds)
                .collect {
                    val entries = batchedUpdates.toSetAndClear()
                    emit(entries)
                }
        }

    override suspend fun <T> useConnection(
        readOnly: Boolean,
        block: suspend (SQLiteConnectionLease) -> T,
    ): T =
        if (readOnly) {
            pool.read(block)
        } else {
            pool.write(block)
        }

    /**
     * Creates a read lock while providing an internal transactor for transactions
     */
    @OptIn(ExperimentalPowerSyncAPI::class)
    private suspend fun <R> internalReadLock(callback: suspend (SQLiteConnectionLease) -> R): R =
        dispatch {
            runWrapped {
                useConnection(true) { connection ->
                    callback(connection)
                }
            }
        }

    override suspend fun <R> readLock(callback: ThrowableLockCallback<R>): R =
        internalReadLock {
            callback.execute(ConnectionContextImplementation(it))
        }

    override suspend fun <R> readTransaction(callback: ThrowableTransactionCallback<R>): R =
        internalReadLock {
            it.runTransaction { tx ->
                callback.execute(tx)
            }
        }

    @OptIn(ExperimentalPowerSyncAPI::class)
    private suspend fun <R> internalWriteLock(callback: suspend (SQLiteConnectionLease) -> R): R =
        dispatch {
            pool.write { writer ->
                runWrapped {
                    callback(writer)
                }
            }
        }

    override suspend fun <R> writeLock(callback: ThrowableLockCallback<R>): R =
        internalWriteLock {
            callback.execute(ConnectionContextImplementation(it))
        }

    override suspend fun <R> writeTransaction(callback: ThrowableTransactionCallback<R>): R =
        internalWriteLock {
            it.runTransaction { tx ->
                callback.execute(tx)
            }
        }

    // Register callback for table updates on a specific table
    override fun updatesOnTables(): SharedFlow<Set<String>> = pool.updates

    private suspend fun getSourceTables(
        sql: String,
        parameters: List<Any?>?,
    ): Set<String> {
        val rows =
            getAll(
                "EXPLAIN $sql",
                parameters = parameters,
                mapper = {
                    ExplainQueryResult(
                        addr = it.getString(0)!!,
                        opcode = it.getString(1)!!,
                        p1 = it.getLong(2)!!,
                        p2 = it.getLong(3)!!,
                        p3 = it.getLong(4)!!,
                    )
                },
            )

        val rootPages = mutableListOf<Long>()
        for (row in rows) {
            if ((row.opcode == "OpenRead" || row.opcode == "OpenWrite") && row.p3 == 0L && row.p2 != 0L) {
                rootPages.add(row.p2)
            }
        }
        val params = listOf(JsonUtil.json.encodeToString(rootPages))
        val tableRows =
            getAll(
                "SELECT tbl_name FROM sqlite_master WHERE rootpage IN (SELECT json_each.value FROM json_each(?))",
                parameters = params,
                mapper = { it.getString(0)!! },
            )

        return tableRows.toSet()
    }

    override suspend fun close() {
        runWrapped {
            pool.close()
        }
    }

    internal data class ExplainQueryResult(
        val addr: String,
        val opcode: String,
        val p1: Long,
        val p2: Long,
        val p3: Long,
    )

    private companion object {
        val initialUpdateSentinel = emptySet<String>()
    }
}

/**
 * Converts internal table names (e.g., prefixed with "ps_data__" or "ps_data_local__")
 * to their original friendly names by removing the prefixes. If no prefix matches,
 * the original table name is returned.
 */
private fun friendlyTableName(table: String): String {
    val re = Regex("^ps_data__(.+)$")
    val re2 = Regex("^ps_data_local__(.+)$")
    val match = re.matchEntire(table) ?: re2.matchEntire(table)
    return match?.groupValues?.get(1) ?: table
}
