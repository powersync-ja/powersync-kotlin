package com.powersync.db.internal

import androidx.sqlite.SQLiteStatement
import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.PowerSyncException
import com.powersync.db.QueryRunner
import com.powersync.db.SqlCursor
import com.powersync.db.StatementBasedCursor
import com.powersync.db.driver.SQLiteConnectionLease
import kotlinx.coroutines.runBlocking

public interface ConnectionContext {
    // TODO (breaking): Make asynchronous, create shared superinterface with Queries

    public val async: QueryRunner

    @Throws(PowerSyncException::class)
    public fun execute(
        sql: String,
        parameters: List<Any?>? = listOf(),
    ): Long

    @Throws(PowerSyncException::class)
    public fun <RowType : Any> getOptional(
        sql: String,
        parameters: List<Any?>? = listOf(),
        mapper: (SqlCursor) -> RowType,
    ): RowType?

    @Throws(PowerSyncException::class)
    public fun <RowType : Any> getAll(
        sql: String,
        parameters: List<Any?>? = listOf(),
        mapper: (SqlCursor) -> RowType,
    ): List<RowType>

    @Throws(PowerSyncException::class)
    public fun <RowType : Any> get(
        sql: String,
        parameters: List<Any?>? = listOf(),
        mapper: (SqlCursor) -> RowType,
    ): RowType
}

/**
 * An implementation of a [ConnectionContext] that delegates to a [QueryRunner] via [runBlocking].
 */
internal abstract class BaseConnectionContextImplementation(): ConnectionContext {
    override fun execute(
        sql: String,
        parameters: List<Any?>?,
    ): Long = runBlocking { async.execute(sql, parameters) }

    override fun <RowType : Any> getOptional(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType,
    ): RowType? = runBlocking {
        async.getOptional(sql, parameters, mapper)
    }

    override fun <RowType : Any> getAll(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType,
    ): List<RowType> =
        runBlocking {
            async.getAll(sql, parameters, mapper)
        }

    override fun <RowType : Any> get(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType,
    ): RowType = runBlocking {
        async.get(sql, parameters, mapper)
    }
}

@OptIn(ExperimentalPowerSyncAPI::class)
internal class ConnectionContextImplementation(lease: SQLiteConnectionLease): BaseConnectionContextImplementation() {
    override val async = ContextQueryRunner(lease)
}

@OptIn(ExperimentalPowerSyncAPI::class)
internal class ContextQueryRunner(
    private val rawConnection: SQLiteConnectionLease
): QueryRunner {
    override suspend fun execute(
        sql: String,
        parameters: List<Any?>?
    ): Long {
        withStatement(sql, parameters) {
            while (it.step()) {
                // Iterate through the statement
            }
        }

        return withStatement("SELECT changes()", null) {
            check(it.step())
            it.getLong(0)
        }
    }

    override suspend fun <RowType : Any> get(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType
    ): RowType = getOptional(sql, parameters, mapper) ?: throw PowerSyncException("get() called with query that returned no rows", null)

    override suspend fun <RowType : Any> getAll(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType
    ): List<RowType> = withStatement(sql, parameters) { stmt ->
        buildList {
            val cursor = StatementBasedCursor(stmt)
            while (stmt.step()) {
                add(mapper(cursor))
            }
        }
    }

    override suspend fun <RowType : Any> getOptional(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType
    ): RowType? = withStatement(sql, parameters) { stmt ->
        if (stmt.step()) {
            mapper(StatementBasedCursor(stmt))
        } else {
            null
        }
    }

    private suspend inline fun <T> withStatement(
        sql: String,
        parameters: List<Any?>?,
        crossinline block: (SQLiteStatement) -> T,
    ): T {
        return rawConnection.usePrepared(sql) { stmt ->
            stmt.bind(parameters)
            block(stmt)
        }
    }
}

internal fun SQLiteStatement.bind(parameters: List<Any?>?) {
    parameters?.forEachIndexed { i, parameter ->
        // SQLite parameters are 1-indexed
        val index = i + 1

        when (parameter) {
            is Boolean -> bindBoolean(index, parameter)
            is String -> bindText(index, parameter)
            is Long -> bindLong(index, parameter)
            is Int -> bindLong(index, parameter.toLong())
            is Double -> bindDouble(index, parameter)
            is ByteArray -> bindBlob(index, parameter)
            else -> {
                if (parameter != null) {
                    throw IllegalArgumentException("Unsupported parameter type: ${parameter::class}, at index $index")
                }
            }
        }
    }
}
