package com.powersync.db.internal

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import com.powersync.PowerSyncException
import com.powersync.db.SqlCursor
import com.powersync.db.StatementBasedCursor

public interface ConnectionContext {
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

internal class ConnectionContextImplementation(val connection: SQLiteConnection): ConnectionContext {
    override fun execute(
        sql: String,
        parameters: List<Any?>?
    ): Long {
        TODO("Not yet implemented")
    }

    override fun <RowType : Any> getOptional(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType
    ): RowType? {
        return getSequence(sql, parameters, mapper).firstOrNull()
    }

    override fun <RowType : Any> getAll(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType
    ): List<RowType> {
        return getSequence(sql, parameters, mapper).toList()
    }

    override fun <RowType : Any> get(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType
    ): RowType {
        return getOptional(sql, parameters, mapper) ?: throw PowerSyncException("get() called with query that returned no rows", null)
    }

    private fun <RowType : Any> getSequence(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType
    ): Sequence<RowType> = sequence {
        val stmt = prepareStmt(sql, parameters)
        val cursor = StatementBasedCursor(stmt)

        while (stmt.step()) {
            yield(mapper(cursor))
        }
    }

    private fun prepareStmt(sql: String, parameters: List<Any?>?): SQLiteStatement {
        return connection.prepare(sql).apply {
            try {
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
            } catch (e: Exception) {
                close()
                throw e
            }
        }
    }
}
