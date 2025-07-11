package com.powersync.db.internal

import app.cash.sqldelight.db.SqlPreparedStatement
import com.powersync.DatabaseDriverFactory
import com.powersync.PowerSyncException
import com.powersync.db.SqlCursor
import com.powersync.db.ThrowableLockCallback
import com.powersync.db.ThrowableTransactionCallback
import com.powersync.db.runWrapped
import com.powersync.utils.AtomicMutableSet
import com.powersync.utils.JsonUtil
import com.powersync.utils.throttle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

internal class InternalDatabaseImpl(
    private val factory: DatabaseDriverFactory,
    private val scope: CoroutineScope,
    private val dbFilename: String,
    private val dbDirectory: String?,
    private val writeLockMutex: Mutex,
) : InternalDatabase {
    private val writeConnection =
        TransactorDriver(
            factory.createDriver(
                scope = scope,
                dbFilename = dbFilename,
                dbDirectory = dbDirectory,
            ),
        )

    private val readPool =
        ConnectionPool(factory = {
            factory.createDriver(
                scope = scope,
                dbFilename = dbFilename,
                dbDirectory = dbDirectory,
                readOnly = true,
            )
        }, scope = scope)

    // Could be scope.coroutineContext, but the default is GlobalScope, which seems like a bad idea. To discuss.
    private val dbContext = Dispatchers.IO

    override suspend fun execute(
        sql: String,
        parameters: List<Any?>?,
    ): Long =
        writeLock { context ->
            context.execute(sql, parameters)
        }

    override suspend fun updateSchema(schemaJson: String) {
        withContext(dbContext) {
            runWrapped {
                // First get a lock on all read connections
                readPool.withAllConnections { readConnections ->
                    // Then get access to the write connection
                    writeTransaction { tx ->
                        tx.getOptional(
                            "SELECT powersync_replace_schema(?);",
                            listOf(schemaJson),
                        ) {}
                    }

                    // Update the schema on all read connections
                    readConnections.forEach { it.driver.getAll("pragma table_info('sqlite_master')") {} }
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
    ): Flow<Set<String>> =
        channelFlow {
            // Match all possible internal table combinations
            val watchedTables =
                tables.flatMap { listOf(it, "ps_data__$it", "ps_data_local__$it") }.toSet()

            // Accumulate updates between throttles
            val batchedUpdates = AtomicMutableSet<String>()

            updatesOnTables()
                .onSubscription {
                    if (triggerImmediately) {
                        // Emit an initial event (if requested). No changes would be detected at this point
                        send(setOf())
                    }
                }.transform { updates ->
                    val intersection = updates.intersect(watchedTables)
                    if (intersection.isNotEmpty()) {
                        // Transform table names using friendlyTableName
                        val friendlyTableNames = intersection.map { friendlyTableName(it) }.toSet()
                        batchedUpdates.addAll(friendlyTableNames)
                        emit(Unit)
                    }
                }
                // Throttling here is a feature which prevents watch queries from spamming updates.
                // Throttling by design discards and delays events within the throttle window. Discarded events
                // still trigger a trailing edge update.
                // Backpressure is avoided on the throttling and consumer level by buffering the last upstream value.
                .throttle(throttleMs.milliseconds)
                .collect {
                    // Emit the transformed tables which have changed
                    val copy = batchedUpdates.toSetAndClear()
                    send(copy)
                }
        }

    override fun <RowType : Any> watch(
        sql: String,
        parameters: List<Any?>?,
        throttleMs: Long,
        mapper: (SqlCursor) -> RowType,
    ): Flow<List<RowType>> =
        // Use a channel flow here since we throttle (buffer used under the hood)
        // This causes some emissions to be from different scopes.
        channelFlow {
            // Fetch the tables asynchronously with getAll
            val tables =
                getSourceTables(sql, parameters)
                    .filter { it.isNotBlank() }
                    .toSet()

            updatesOnTables()
                // onSubscription here is very important.
                // This ensures that the initial result and all updates are emitted.
                .onSubscription {
                    send(getAll(sql, parameters = parameters, mapper = mapper))
                }.filter {
                    // Only trigger updates on relevant tables
                    it.intersect(tables).isNotEmpty()
                }
                // Throttling here is a feature which prevents watch queries from spamming updates.
                // Throttling by design discards and delays events within the throttle window. Discarded events
                // still trigger a trailing edge update.
                // Backpressure is avoided on the throttling and consumer level by buffering the last upstream value.
                // Note that the buffered upstream "value" only serves to trigger the getAll query. We don't buffer watch results.
                .throttle(throttleMs.milliseconds)
                .collect {
                    send(getAll(sql, parameters = parameters, mapper = mapper))
                }
        }

    /**
     * Creates a read lock while providing an internal transactor for transactions
     */
    private suspend fun <R> internalReadLock(callback: (TransactorDriver) -> R): R =
        withContext(dbContext) {
            runWrapped {
                readPool.withConnection {
                    catchSwiftExceptions {
                        callback(it)
                    }
                }
            }
        }

    override suspend fun <R> readLock(callback: ThrowableLockCallback<R>): R =
        internalReadLock {
            callback.execute(it.driver)
        }

    override suspend fun <R> readTransaction(callback: ThrowableTransactionCallback<R>): R =
        internalReadLock {
            it.transactor.transactionWithResult(noEnclosing = true) {
                catchSwiftExceptions {
                    callback.execute(
                        PowerSyncTransactionImpl(
                            it.driver,
                        ),
                    )
                }
            }
        }

    private suspend fun <R> internalWriteLock(callback: (TransactorDriver) -> R): R =
        withContext(dbContext) {
            writeLockMutex.withLock {
                runWrapped {
                    catchSwiftExceptions {
                        callback(writeConnection)
                    }
                }.also {
                    // Trigger watched queries
                    // Fire updates inside the write lock
                    writeConnection.driver.fireTableUpdates()
                }
            }
        }

    override suspend fun <R> writeLock(callback: ThrowableLockCallback<R>): R =
        internalWriteLock {
            callback.execute(it.driver)
        }

    override suspend fun <R> writeTransaction(callback: ThrowableTransactionCallback<R>): R =
        internalWriteLock {
            it.transactor.transactionWithResult(noEnclosing = true) {
                // Need to catch Swift exceptions here for Rollback
                catchSwiftExceptions {
                    callback.execute(
                        PowerSyncTransactionImpl(
                            it.driver,
                        ),
                    )
                }
            }
        }

    // Register callback for table updates on a specific table
    override fun updatesOnTables(): SharedFlow<Set<String>> = writeConnection.driver.updatesOnTables()

    // Unfortunately Errors can't be thrown from Swift SDK callbacks.
    // These are currently returned and should be thrown here.
    private fun <R> catchSwiftExceptions(action: () -> R): R {
        val result = action()

        if (result is PowerSyncException) {
            throw result
        }
        return result
    }

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
            writeConnection.driver.close()
            readPool.close()
        }
    }

    internal data class ExplainQueryResult(
        val addr: String,
        val opcode: String,
        val p1: Long,
        val p2: Long,
        val p3: Long,
    )
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

internal fun getBindersFromParams(parameters: List<Any?>?): (SqlPreparedStatement.() -> Unit)? {
    if (parameters.isNullOrEmpty()) {
        return null
    }
    return {
        parameters.forEachIndexed { index, parameter ->
            when (parameter) {
                is Boolean -> bindBoolean(index, parameter)
                is String -> bindString(index, parameter)
                is Long -> bindLong(index, parameter)
                is Int -> bindLong(index, parameter.toLong())
                is Double -> bindDouble(index, parameter)
                is ByteArray -> bindBytes(index, parameter)
                else -> {
                    if (parameter != null) {
                        throw IllegalArgumentException("Unsupported parameter type: ${parameter::class}, at index $index")
                    }
                }
            }
        }
    }
}
