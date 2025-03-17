package com.powersync.db.internal

import app.cash.sqldelight.db.SqlPreparedStatement
import com.powersync.DatabaseDriverFactory
import com.powersync.PowerSyncException
import com.powersync.db.SqlCursor
import com.powersync.db.ThrowableLockCallback
import com.powersync.db.ThrowableTransactionCallback
import com.powersync.db.runWrapped
import com.powersync.db.runWrappedSuspending
import com.powersync.persistence.PsDatabase
import com.powersync.utils.JsonUtil
import com.powersync.utils.throttle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

@OptIn(FlowPreview::class)
internal class InternalDatabaseImpl(
    private val factory: DatabaseDriverFactory,
    private val scope: CoroutineScope,
    private val dbFilename: String,
    private val dbDirectory: String?,
) : InternalDatabase {
    private val writeConnection =
        factory.createDriver(scope = scope, dbFilename = dbFilename, dbDirectory = dbDirectory)
    private val writeTransactor = PsDatabase(writeConnection)

    private val dbIdentifier = dbFilename + dbDirectory

    private val readPool =
        ConnectionPool(factory = {
            factory.createDriver(
                scope = scope,
                dbFilename = dbFilename,
                dbDirectory = dbDirectory,
            )
        }, scope = scope)

    // Could be scope.coroutineContext, but the default is GlobalScope, which seems like a bad idea. To discuss.
    private val dbContext = Dispatchers.IO

    companion object {
        const val DEFAULT_WATCH_THROTTLE_MS = 30L

        // A meta mutex for protecting mutex operations
        private val globalLock = Mutex()

        // Static mutex max which globally shares write locks
        private val mutexMap = mutableMapOf<String, Mutex>()

        // Run an action inside a global shared mutex
        private suspend fun <T> withSharedMutex(
            key: String,
            action: suspend () -> T,
        ): T {
            val mutex = globalLock.withLock { mutexMap.getOrPut(key) { Mutex() } }
            return mutex.withLock { action() }
        }
    }

    override suspend fun execute(
        sql: String,
        parameters: List<Any?>?,
    ): Long =
        writeLock { context ->
            context.execute(sql, parameters)
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

    override fun <RowType : Any> watch(
        sql: String,
        parameters: List<Any?>?,
        throttleMs: Long?,
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
                .throttle(throttleMs ?: DEFAULT_WATCH_THROTTLE_MS)
                .collect {
                    send(getAll(sql, parameters = parameters, mapper = mapper))
                }
        }

    override suspend fun <R> readLock(callback: ThrowableLockCallback<R>): R =
        withContext(dbContext) {
            runWrappedSuspending {
                readPool.withConnection { callback.execute(it) }
            }
        }

    override suspend fun <R> readTransaction(callback: ThrowableTransactionCallback<R>): R =
        readLock { connection ->
            internalTransaction(connection, readOnly = true) {
                callback.execute(
                    PowerSyncTransactionImpl(
                        connection,
                    ),
                )
            }
        }

    override suspend fun <R> writeLock(callback: ThrowableLockCallback<R>): R =
        withContext(dbContext) {
            withSharedMutex(dbIdentifier) {
                runWrapped {
                    callback.execute(writeConnection)
                }.also {
                    // Trigger watched queries
                    // Fire updates inside the write lock
                    writeConnection.fireTableUpdates()
                }
            }
        }

    override suspend fun <R> writeTransaction(callback: ThrowableTransactionCallback<R>): R =
        writeLock { connection ->
            // Some drivers (mainly iOS) require starting a transaction with the driver in order to
            // access the internal write connection
            writeTransactor.transactionWithResult(noEnclosing = true) {
                callback.execute(
                    PowerSyncTransactionImpl(
                        connection,
                    ),
                )
            }
        }

    // Register callback for table updates on a specific table
    override fun updatesOnTables(): SharedFlow<Set<String>> = writeConnection.updatesOnTables()

    private fun <R> internalTransaction(
        context: ConnectionContext,
        readOnly: Boolean,
        action: () -> R,
    ): R {
        try {
            context.execute(
                "BEGIN ${
                    if (readOnly) {
                        ""
                    } else {
                        "IMMEDIATE"
                    }
                }",
            )
            val result = catchSwiftExceptions { action() }
            context.execute("COMMIT")
            return result
        } catch (error: Exception) {
            try {
                context.execute("ROLLBACK")
            } catch (_: Exception) {
                // This can fail safely under some circumstances
            }
            throw error
        }
    }

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

    override fun close() {
        runWrapped {
            writeConnection.close()
            runBlocking {
                readPool.close()
            }
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
