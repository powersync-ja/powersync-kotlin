package com.powersync.db.internal

import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlPreparedStatement
import com.persistence.PowersyncQueries
import com.powersync.PowerSyncException
import com.powersync.PsSqlDriver
import com.powersync.db.SqlCursor
import com.powersync.db.runWrapped
import com.powersync.persistence.PsDatabase
import com.powersync.utils.JsonUtil
import com.powersync.utils.throttle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

@OptIn(FlowPreview::class)
internal class InternalDatabaseImpl(
    private val driver: PsSqlDriver,
    private val scope: CoroutineScope,
) : InternalDatabase {
    override val transactor: PsDatabase = PsDatabase(driver)
    override val queries: PowersyncQueries = transactor.powersyncQueries

    // Could be scope.coroutineContext, but the default is GlobalScope, which seems like a bad idea. To discuss.
    private val dbContext = Dispatchers.IO
    private val transaction =
        object : PowerSyncTransaction {
            override fun execute(
                sql: String,
                parameters: List<Any?>?,
            ): Long = this@InternalDatabaseImpl.executeSync(sql, parameters ?: emptyList())

            override fun <RowType : Any> get(
                sql: String,
                parameters: List<Any?>?,
                mapper: (SqlCursor) -> RowType,
            ): RowType = this@InternalDatabaseImpl.getSync(sql, parameters ?: emptyList(), mapper)

            override fun <RowType : Any> getAll(
                sql: String,
                parameters: List<Any?>?,
                mapper: (SqlCursor) -> RowType,
            ): List<RowType> = this@InternalDatabaseImpl.getAllSync(sql, parameters ?: emptyList(), mapper)

            override fun <RowType : Any> getOptional(
                sql: String,
                parameters: List<Any?>?,
                mapper: (SqlCursor) -> RowType,
            ): RowType? = this@InternalDatabaseImpl.getOptionalSync(sql, parameters ?: emptyList(), mapper)
        }

    companion object {
        const val DEFAULT_WATCH_THROTTLE_MS = 30L
    }

    override suspend fun execute(
        sql: String,
        parameters: List<Any?>?,
    ): Long =
        withContext(dbContext) {
            executeSync(sql, parameters)
        }.also {
            driver.fireTableUpdates()
        }

    private fun executeSync(
        sql: String,
        parameters: List<Any?>?,
    ): Long {
        val numParams = parameters?.size ?: 0

        return runWrapped {
            driver
                .execute(
                    identifier = null,
                    sql = sql,
                    parameters = numParams,
                    binders = getBindersFromParams(parameters),
                ).value
        }
    }

    override suspend fun <RowType : Any> get(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType,
    ): RowType = withContext(dbContext) { getSync(sql, parameters, mapper) }

    private fun <RowType : Any> getSync(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType,
    ): RowType {
        val result =
            this
                .createQuery(
                    query = sql,
                    parameters = parameters?.size ?: 0,
                    binders = getBindersFromParams(parameters),
                    mapper = mapper,
                ).executeAsOneOrNull()
        return requireNotNull(result) { "Query returned no result" }
    }

    override suspend fun <RowType : Any> getAll(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType,
    ): List<RowType> = withContext(dbContext) { getAllSync(sql, parameters, mapper) }

    private fun <RowType : Any> getAllSync(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType,
    ): List<RowType> =
        this
            .createQuery(
                query = sql,
                parameters = parameters?.size ?: 0,
                binders = getBindersFromParams(parameters),
                mapper = mapper,
            ).executeAsList()

    override suspend fun <RowType : Any> getOptional(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType,
    ): RowType? = withContext(dbContext) { getOptionalSync(sql, parameters, mapper) }

    private fun <RowType : Any> getOptionalSync(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType,
    ): RowType? =
        this
            .createQuery(
                query = sql,
                parameters = parameters?.size ?: 0,
                binders = getBindersFromParams(parameters),
                mapper = mapper,
            ).executeAsOneOrNull()

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
                }.throttle(throttleMs ?: DEFAULT_WATCH_THROTTLE_MS)
                .collect {
                    send(getAll(sql, parameters = parameters, mapper = mapper))
                }
        }

    private fun <T : Any> createQuery(
        query: String,
        mapper: (SqlCursor) -> T,
        parameters: Int = 0,
        binders: (SqlPreparedStatement.() -> Unit)? = null,
    ): ExecutableQuery<T> =
        object : ExecutableQuery<T>(wrapperMapper(mapper)) {
            override fun <R> execute(mapper: (app.cash.sqldelight.db.SqlCursor) -> QueryResult<R>): QueryResult<R> =
                runWrapped {
                    driver.executeQuery(null, query, mapper, parameters, binders)
                }
        }

    override suspend fun <R> readTransaction(callback: ThrowableTransactionCallback<R>): R =
        withContext(dbContext) {
            transactor.transactionWithResult(noEnclosing = true) {
                runWrapped {
                    val result = callback.execute(transaction)
                    if (result is PowerSyncException) {
                        throw result
                    }
                    result
                }
            }
        }

    override suspend fun <R> writeTransaction(callback: ThrowableTransactionCallback<R>): R =
        withContext(dbContext) {
            val r =
                transactor.transactionWithResult(noEnclosing = true) {
                    runWrapped {
                        val result = callback.execute(transaction)
                        if (result is PowerSyncException) {
                            throw result
                        }
                        result
                    }
                }
            // Trigger watched queries
            r
        }.also {
            driver.fireTableUpdates()
        }

    // Register callback for table updates on a specific table
    override fun updatesOnTables(): SharedFlow<Set<String>> = driver.updatesOnTables()

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
        runWrapped { this.driver.close() }
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

/**
 * Kotlin allows SAM (Single Abstract Method) interfaces to be treated like lambda expressions.
 */
public fun interface ThrowableTransactionCallback<R> {
    @Throws(PowerSyncException::class, CancellationException::class)
    public fun execute(transaction: PowerSyncTransaction): R
}
