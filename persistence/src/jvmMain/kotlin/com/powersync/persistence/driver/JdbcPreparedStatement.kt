package com.powersync.persistence.driver

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlPreparedStatement
import java.math.BigDecimal
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types

/**
 * Binds the parameter to [preparedStatement] by calling [bindString], [bindLong] or similar.
 * After binding, [execute] executes the query without a result, while [executeQuery] returns [JdbcCursor].
 */
public class JdbcPreparedStatement(
    private val preparedStatement: PreparedStatement,
) : SqlPreparedStatement {
    override fun bindBytes(index: Int, bytes: ByteArray?) {
        preparedStatement.setBytes(index + 1, bytes)
    }

    override fun bindBoolean(index: Int, boolean: Boolean?) {
        if (boolean == null) {
            preparedStatement.setNull(index + 1, Types.BOOLEAN)
        } else {
            preparedStatement.setBoolean(index + 1, boolean)
        }
    }

    public fun bindByte(index: Int, byte: Byte?) {
        if (byte == null) {
            preparedStatement.setNull(index + 1, Types.TINYINT)
        } else {
            preparedStatement.setByte(index + 1, byte)
        }
    }

    public fun bindShort(index: Int, short: Short?) {
        if (short == null) {
            preparedStatement.setNull(index + 1, Types.SMALLINT)
        } else {
            preparedStatement.setShort(index + 1, short)
        }
    }

    public fun bindInt(index: Int, int: Int?) {
        if (int == null) {
            preparedStatement.setNull(index + 1, Types.INTEGER)
        } else {
            preparedStatement.setInt(index + 1, int)
        }
    }

    override fun bindLong(index: Int, long: Long?) {
        if (long == null) {
            preparedStatement.setNull(index + 1, Types.BIGINT)
        } else {
            preparedStatement.setLong(index + 1, long)
        }
    }

    public fun bindFloat(index: Int, float: Float?) {
        if (float == null) {
            preparedStatement.setNull(index + 1, Types.REAL)
        } else {
            preparedStatement.setFloat(index + 1, float)
        }
    }

    override fun bindDouble(index: Int, double: Double?) {
        if (double == null) {
            preparedStatement.setNull(index + 1, Types.DOUBLE)
        } else {
            preparedStatement.setDouble(index + 1, double)
        }
    }

    public fun bindBigDecimal(index: Int, decimal: BigDecimal?) {
        preparedStatement.setBigDecimal(index + 1, decimal)
    }

    public fun bindObject(index: Int, obj: Any?) {
        if (obj == null) {
            preparedStatement.setNull(index + 1, Types.OTHER)
        } else {
            preparedStatement.setObject(index + 1, obj)
        }
    }

    public fun bindObject(index: Int, obj: Any?, type: Int) {
        if (obj == null) {
            preparedStatement.setNull(index + 1, type)
        } else {
            preparedStatement.setObject(index + 1, obj, type)
        }
    }

    override fun bindString(index: Int, string: String?) {
        preparedStatement.setString(index + 1, string)
    }

    public fun bindDate(index: Int, date: java.sql.Date?) {
        preparedStatement.setDate(index, date)
    }

    public fun bindTime(index: Int, date: java.sql.Time?) {
        preparedStatement.setTime(index, date)
    }

    public fun bindTimestamp(index: Int, timestamp: java.sql.Timestamp?) {
        preparedStatement.setTimestamp(index, timestamp)
    }

    public fun <R> executeQuery(mapper: (SqlCursor) -> R): R {
        try {
            return preparedStatement.executeQuery()
                .use { resultSet -> mapper(JdbcCursor(resultSet)) }
        } finally {
            preparedStatement.close()
        }
    }

    public fun execute(): Long {
        return if (preparedStatement.execute()) {
            // returned true so this is a result set return type.
            0L
        } else {
            preparedStatement.updateCount.toLong()
        }
    }
}

/**
 * Iterate each row in [resultSet] and map the columns to Kotlin classes by calling [getString], [getLong] etc.
 * Use [next] to retrieve the next row and [close] to close the connection.
 */
internal class JdbcCursor(val resultSet: ResultSet) : ColNamesSqlCursor {
    override fun getString(index: Int): String? = resultSet.getString(index + 1)
    override fun getBytes(index: Int): ByteArray? = resultSet.getBytes(index + 1)
    override fun getBoolean(index: Int): Boolean? = getAtIndex(index, resultSet::getBoolean)
    override fun columnName(index: Int): String? = resultSet.metaData.getColumnName(index + 1)
    override val columnCount: Int = resultSet.metaData.columnCount

    fun getByte(index: Int): Byte? = getAtIndex(index, resultSet::getByte)
    fun getShort(index: Int): Short? = getAtIndex(index, resultSet::getShort)
    fun getInt(index: Int): Int? = getAtIndex(index, resultSet::getInt)
    override fun getLong(index: Int): Long? = getAtIndex(index, resultSet::getLong)
    fun getFloat(index: Int): Float? = getAtIndex(index, resultSet::getFloat)
    override fun getDouble(index: Int): Double? = getAtIndex(index, resultSet::getDouble)
    fun getBigDecimal(index: Int): BigDecimal? = resultSet.getBigDecimal(index + 1)
    inline fun <reified T : Any> getObject(index: Int): T? = resultSet.getObject(index + 1, T::class.java)
    fun getDate(index: Int): java.sql.Date? = resultSet.getDate(index)
    fun getTime(index: Int): java.sql.Time? = resultSet.getTime(index)
    fun getTimestamp(index: Int): java.sql.Timestamp? = resultSet.getTimestamp(index)

    @Suppress("UNCHECKED_CAST")
    fun <T> getArray(index: Int) = getAtIndex(index, resultSet::getArray)?.array as Array<T>?

    private fun <T> getAtIndex(index: Int, converter: (Int) -> T): T? =
        converter(index + 1).takeUnless { resultSet.wasNull() }

    override fun next(): QueryResult.Value<Boolean> = QueryResult.Value(resultSet.next())
}
