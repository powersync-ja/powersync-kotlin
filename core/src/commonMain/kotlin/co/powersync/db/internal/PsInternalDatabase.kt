package co.powersync.db.internal

import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.Query
import app.cash.sqldelight.SuspendingTransactionWithReturn
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import co.powersync.db.PsDatabase
import co.powersync.db.ReadQueries
import co.powersync.db.WriteQueries
import kotlinx.coroutines.flow.Flow

class PsInternalDatabase(val driver: SqlDriver) :
    ReadQueries,
    WriteQueries {

    private val transactor: PsDatabase = PsDatabase(driver)
    val queries = transactor.powersyncQueries

    override suspend fun execute(
        sql: String,
        parameters: List<Any>?
    ): Long {
        val numParams = parameters?.size ?: 0
        return createQuery(
            sql,
            parameters = numParams,
            binders = getBindersFromParams(parameters)
        ).awaitAsOneOrNull() ?: 0
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

    override suspend fun <RowType : Any> watch(
        sql: String,
        parameters: List<Any>?,
        mapper: (SqlCursor) -> RowType
    ): Flow<RowType> {
        TODO("Not yet implemented")
    }


    fun createQuery(
        query: String,
        parameters: Int = 0,
        binders: (SqlPreparedStatement.() -> Unit)? = null,
    ): ExecutableQuery<Long> {
        return createQuery(query, { cursor -> cursor.getLong(0)!! }, parameters, binders)
    }

    fun <T : Any> createQuery(
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

    fun <T : Any> watchQuery(
        key: String, query: String,
        mapper: (SqlCursor) -> T,
        parameters: Int = 0,
        binders: (SqlPreparedStatement.() -> Unit)? = null,
    ): Query<T> {
        return object : Query<T>(mapper) {
            override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> {
                return driver.executeQuery(null, query, mapper, parameters, binders);
            }

            override fun addListener(listener: Listener) {
                driver.addListener(key, listener = listener)
            }

            override fun removeListener(listener: Listener) {
                driver.removeListener(key, listener = listener)
            }
        }
    }

    override suspend fun <R> readTransaction(body: suspend SuspendingTransactionWithReturn<R>.() -> R): R {
        return transactor.transactionWithResult(noEnclosing = true, body)
    }

    override suspend fun <R> writeTransaction(body: suspend SuspendingTransactionWithReturn<R>.() -> R): R {
        return transactor.transactionWithResult(noEnclosing = true, body)
    }
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