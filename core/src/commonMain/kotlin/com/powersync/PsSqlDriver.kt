package com.powersync

import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import com.powersync.db.SqlCursor
import com.powersync.db.internal.ConnectionContext
import com.powersync.db.internal.getBindersFromParams
import com.powersync.db.internal.wrapperMapper
import com.powersync.db.runWrapped
import com.powersync.utils.AtomicMutableSet
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

internal class PsSqlDriver(
    private val driver: SqlDriver,
) : SqlDriver by driver,
    ConnectionContext {
    // MutableSharedFlow to emit batched table updates
    private val tableUpdatesFlow = MutableSharedFlow<Set<String>>(replay = 0)

    // In-memory buffer to store table names before flushing
    private val pendingUpdates = AtomicMutableSet<String>()

    fun updateTable(tableName: String) {
        pendingUpdates.add(tableName)
    }

    fun clearTableUpdates() {
        pendingUpdates.clear()
    }

    // Flows on any table change
    // This specifically returns a SharedFlow for downstream timing considerations
    fun updatesOnTables(): SharedFlow<Set<String>> =
        tableUpdatesFlow
            .asSharedFlow()

    suspend fun fireTableUpdates() {
        val updates = pendingUpdates.toSetAndClear()
        tableUpdatesFlow.emit(updates)
    }

    override fun execute(
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

    override fun <RowType : Any> get(
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

    override fun <RowType : Any> getAll(
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

    override fun <RowType : Any> getOptional(
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
}
