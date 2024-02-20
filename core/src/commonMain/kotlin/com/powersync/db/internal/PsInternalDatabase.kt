package com.powersync.db.internal

import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.Query
import app.cash.sqldelight.SuspendingTransactionWithReturn
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import com.powersync.db.PsDatabase
import com.powersync.db.ReadQueries
import com.powersync.db.WriteQueries
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PsInternalDatabase(val driver: SqlDriver, private val scope: CoroutineScope) :
    ReadQueries,
    WriteQueries {

    private val transactor: PsDatabase = PsDatabase(driver)
    val queries = transactor.powersyncQueries

    companion object {
        const val POWERSYNC_TABLE_MATCH = "(^ps_data__|^ps_data_local__)"
    }

    override suspend fun execute(
        sql: String,
        parameters: List<Any>?
    ): Long {
        val numParams = parameters?.size ?: 0

        val result = createQuery(
            sql,
            parameters = numParams,
            binders = getBindersFromParams(parameters)
        ).awaitAsOneOrNull() ?: 0

        val tables = getSourceTables(sql, parameters, transformer = ::dataTableToFriendlyName)
        if (tables.isNotEmpty()) {
            driver.notifyListeners(queryKeys = tables.toTypedArray())
        }
        return result
    }

    override suspend fun <RowType : Any> get(
        sql: String,
        parameters: List<Any>?,
        mapper: (SqlCursor) -> RowType
    ): RowType {
        return this.createQuery(
            query = sql,
            parameters = parameters?.size ?: 0,
            binders = getBindersFromParams(parameters),
            mapper = mapper
        ).awaitAsOneOrNull()!!
    }

    override suspend fun <RowType : Any> getAll(
        sql: String,
        parameters: List<Any>?,
        mapper: (SqlCursor) -> RowType
    ): List<RowType> {
        return this.createQuery(
            query = sql,
            parameters = parameters?.size ?: 0,
            binders = getBindersFromParams(parameters),
            mapper = mapper
        ).awaitAsList()
    }

    override suspend fun <RowType : Any> getOptional(
        sql: String,
        parameters: List<Any>?,
        mapper: (SqlCursor) -> RowType
    ): RowType? {
        return this.createQuery(
            query = sql,
            parameters = parameters?.size ?: 0,
            binders = getBindersFromParams(parameters),
            mapper = mapper
        ).awaitAsOneOrNull()
    }

    override fun <RowType : Any> watch(
        sql: String,
        parameters: List<Any>?,
        mapper: (SqlCursor) -> RowType
    ): Flow<List<RowType>> {

        val tables = getSourceTables(sql, parameters, transformer = ::dataTableToFriendlyName)

        return watchQuery(
            query = sql,
            parameters = parameters?.size ?: 0,
            binders = getBindersFromParams(parameters),
            mapper = mapper,
            tables = tables
        ).asFlow().mapToList(scope.coroutineContext)
    }


    private fun createQuery(
        query: String,
        parameters: Int = 0,
        binders: (SqlPreparedStatement.() -> Unit)? = null,
    ): ExecutableQuery<Long> {
        return createQuery(query, { cursor -> cursor.getLong(0)!! }, parameters, binders)
    }

    private fun <T : Any> createQuery(
        query: String,
        mapper: (SqlCursor) -> T,
        parameters: Int = 0,
        binders: (SqlPreparedStatement.() -> Unit)? = null,
    ): ExecutableQuery<T> {
        return object : ExecutableQuery<T>(mapper) {
            override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> {
                return driver.executeQuery(null, query, mapper, parameters, binders)
            }
        }
    }

    private fun <T : Any> watchQuery(
        query: String,
        mapper: (SqlCursor) -> T,
        parameters: Int = 0,
        binders: (SqlPreparedStatement.() -> Unit)? = null,
        tables: Set<String> = setOf()
    ): Query<T> {

        return object : Query<T>(mapper) {
            override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> {
                return driver.executeQuery(null, query, mapper, parameters, binders);
            }

            override fun addListener(listener: Listener) {
                driver.addListener(queryKeys = tables.toTypedArray(), listener = listener)
            }

            override fun removeListener(listener: Listener) {
                driver.removeListener(queryKeys = tables.toTypedArray(), listener = listener)
            }
        }
    }

    override suspend fun <R> readTransaction(body: suspend SuspendingTransactionWithReturn<R>.() -> R): R {
        return transactor.transactionWithResult(noEnclosing = true, body)
    }

    override suspend fun <R> writeTransaction(body: suspend SuspendingTransactionWithReturn<R>.() -> R): R {
        return transactor.transactionWithResult(noEnclosing = true, body)
    }

    private fun getSourceTables(
        sql: String,
        parameters: List<Any>?,
        transformer: ((value: String) -> String)?
    ): Set<String> {
        val rows = createQuery(
            query = "EXPLAIN $sql",
            parameters = parameters?.size ?: 0,
            binders = getBindersFromParams(parameters),
            mapper = {
                ExplainQueryResult(
                    addr = it.getString(0)!!,
                    opcode = it.getString(1)!!,
                    p1 = it.getLong(2)!!,
                    p2 = it.getLong(3)!!,
                    p3 = it.getLong(4)!!
                )
            }
        ).executeAsList()

        val rootPages = mutableListOf<Long>()
        for (row in rows) {
            if ((row.opcode == "OpenRead" || row.opcode == "OpenWrite") && row.p3 == 0L && row.p2 != 0L) {
                rootPages.add(row.p2)
            }
        }
        val params = listOf(Json.encodeToString(rootPages))
        val tableRows = createQuery(
            "SELECT tbl_name FROM sqlite_master WHERE rootpage IN (SELECT json_each.value FROM json_each(?))",
            parameters = params.size,
            binders = {
                bindString(0, params[0])
            }, mapper = { it.getString(0)!! }
        ).executeAsList()

        return tableRows.map { transformer?.invoke(it) ?: it }.filter { it.isNotEmpty() }.toSet()
    }

    private fun dataTableToFriendlyName(table: String): String {
        // Return table name that match the regex and replace it with empty string
        val regex = POWERSYNC_TABLE_MATCH.toRegex()
        if (regex.containsMatchIn(table)) {
            return table.replace(regex, "")
        }
        return ""
    }

    internal data class ExplainQueryResult(
        val addr: String,
        val opcode: String,
        val p1: Long,
        val p2: Long,
        val p3: Long,
    )
}

fun getBindersFromParams(parameters: List<Any>?): (SqlPreparedStatement.() -> Unit)? {
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
                else -> throw IllegalArgumentException("Unsupported parameter type: ${parameter::class}, at index $index")
            }
        }
    }
}