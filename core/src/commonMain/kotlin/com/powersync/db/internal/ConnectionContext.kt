package com.powersync.db.internal

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import com.powersync.PowerSyncException
import com.powersync.db.SqlCursor
import com.powersync.db.StatementBasedCursor
import kotlin.native.HiddenFromObjC

public interface ConnectionContext {
    @HiddenFromObjC
    public val rawConnection: SQLiteConnection

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

internal class ConnectionContextImplementation(
    override val rawConnection: SQLiteConnection,
) : ConnectionContext {
    override fun execute(
        sql: String,
        parameters: List<Any?>?,
    ): Long {
        withStatement(sql, parameters) {
            while (it.step()) {
                // Iterate through the statement
            }

            // TODO: What is this even supposed to return
            return 0L
        }
    }

    override fun <RowType : Any> getOptional(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType,
    ): RowType? =
        withStatement(sql, parameters) { stmt ->
            if (stmt.step()) {
                mapper(StatementBasedCursor(stmt))
            } else {
                null
            }
        }

    override fun <RowType : Any> getAll(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType,
    ): List<RowType> =
        withStatement(sql, parameters) { stmt ->
            buildList {
                val cursor = StatementBasedCursor(stmt)
                while (stmt.step()) {
                    add(mapper(cursor))
                }
            }
        }

    override fun <RowType : Any> get(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType,
    ): RowType = getOptional(sql, parameters, mapper) ?: throw PowerSyncException("get() called with query that returned no rows", null)

    private inline fun <T> withStatement(
        sql: String,
        parameters: List<Any?>?,
        block: (SQLiteStatement) -> T,
    ): T = prepareStmt(sql, parameters).use(block)

    private fun prepareStmt(
        sql: String,
        parameters: List<Any?>?,
    ): SQLiteStatement =
        rawConnection.prepare(sql).apply {
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
