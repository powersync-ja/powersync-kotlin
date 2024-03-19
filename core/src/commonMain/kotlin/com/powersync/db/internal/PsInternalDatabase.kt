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
import app.cash.sqldelight.db.SqlPreparedStatement
import com.powersync.PsSqlDriver
import com.powersync.db.PsDatabase
import com.powersync.db.ReadQueries
import com.powersync.db.WriteQueries
import com.powersync.utils.JsonUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString


@OptIn(FlowPreview::class)
class PsInternalDatabase(val driver: PsSqlDriver, private val scope: CoroutineScope) :
    ReadQueries,
    WriteQueries {

    private val transactor: PsDatabase = PsDatabase(driver)
    val queries = transactor.powersyncQueries

    companion object {
        const val POWERSYNC_TABLE_MATCH = "(^ps_data__|^ps_data_local__)"
        const val DEFAULT_WATCH_THROTTLE_MS = 30L
    }

    init {
        scope.launch {
            val accumulatedUpdates = mutableSetOf<String>();
            tableUpdates()
//               Debounce will discard any events which occur inside the debounce window
//               This will accumulate those table updates
                .onEach { tables -> accumulatedUpdates.addAll(tables) }
                .debounce(DEFAULT_WATCH_THROTTLE_MS)
                .collect {
                    val dataTables = accumulatedUpdates.map { toFriendlyTableName(it) }.filter { it.isNotBlank() }
                    driver.notifyListeners(queryKeys = dataTables.toTypedArray());
                    accumulatedUpdates.clear();
                }
        }
    }

    override suspend fun execute(
        sql: String,
        parameters: List<Any>?
    ): Long {
        val numParams = parameters?.size ?: 0

        return createWriteQuery(
            sql,
            parameters = numParams,
            binders = getBindersFromParams(parameters)
        ).awaitAsOneOrNull() ?: 0L
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

        val tables = getSourceTables(sql, parameters).map { toFriendlyTableName(it) }
            .filter { it.isNotBlank() }.toSet()
        return watchQuery(
            query = sql,
            parameters = parameters?.size ?: 0,
            binders = getBindersFromParams(parameters),
            mapper = mapper,
            tables = tables
        ).asFlow().mapToList(scope.coroutineContext)
    }


    private fun createWriteQuery(
        query: String,
        parameters: Int = 0,
        binders: (SqlPreparedStatement.() -> Unit)? = null,
    ): ExecutableQuery<Long> {
        return object : ExecutableQuery<Long>(mapper = { cursor -> cursor.getLong(0)!! }) {
            override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> {
                return driver.execute(
                    identifier = null,
                    sql = query,
                    parameters,
                    binders
                ) as QueryResult<R>
            }
        }
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

    // Register callback for table updates
    private fun tableUpdates(): Flow<List<String>> {
        return driver.tableUpdates()
    }

    // Register callback for table updates on a specific table
    fun updatesOnTable(tableName: String): Flow<Unit> {
        return driver.updatesOnTable(tableName)
    }

    private fun toFriendlyTableName(tableName: String): String {
        val regex = POWERSYNC_TABLE_MATCH.toRegex()
        if (regex.containsMatchIn(tableName)) {
            return tableName.replace(regex, "")
        }
        return tableName
    }

    private fun getSourceTables(
        sql: String,
        parameters: List<Any>?,
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
        val params = listOf(JsonUtil.json.encodeToString(rootPages))
        val tableRows = createQuery(
            "SELECT tbl_name FROM sqlite_master WHERE rootpage IN (SELECT json_each.value FROM json_each(?))",
            parameters = params.size,
            binders = {
                bindString(0, params[0])
            }, mapper = { it.getString(0)!! }
        ).executeAsList()

        return tableRows.toSet()
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