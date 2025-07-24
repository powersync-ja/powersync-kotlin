package com.powersync.db

import androidx.sqlite.SQLiteStatement
import co.touchlab.skie.configuration.annotations.FunctionInterop
import com.powersync.PowerSyncException
import kotlin.collections.set

public interface SqlCursor {
    public fun getBoolean(index: Int): Boolean?

    public fun getBytes(index: Int): ByteArray?

    public fun getDouble(index: Int): Double?

    public fun getLong(index: Int): Long?

    public fun getString(index: Int): String?

    public fun columnName(index: Int): String?

    public val columnCount: Int

    public val columnNames: Map<String, Int>
}

private inline fun <T> SqlCursor.getColumnValue(
    name: String,
    getValue: (Int) -> T?,
): T {
    val index = columnNames[name] ?: throw IllegalArgumentException("Column '$name' not found")
    return getValue(index) ?: throw IllegalArgumentException("Null value found for column '$name'")
}

internal class StatementBasedCursor(private val stmt: SQLiteStatement): SqlCursor {
    override fun getBoolean(index: Int): Boolean? {
        return getNullable(index) { index -> stmt.getLong(index) != 0L }
    }

    override fun getBytes(index: Int): ByteArray? {
        return getNullable(index, SQLiteStatement::getBlob)
    }

    override fun getDouble(index: Int): Double? {
        return getNullable(index, SQLiteStatement::getDouble)
    }

    override fun getLong(index: Int): Long? {
        return getNullable(index, SQLiteStatement::getLong)
    }

    override fun getString(index: Int): String? {
        return getNullable(index, SQLiteStatement::getText)
    }

    private inline fun <T> getNullable(index: Int, read: SQLiteStatement.(Int) -> T): T? {
        return if (stmt.isNull(index)) {
            null
        } else {
            stmt.read(index)
        }
    }

    override fun columnName(index: Int): String? {
        return stmt.getColumnName(index)
    }

    override val columnCount: Int
        get() = stmt.getColumnCount()

    override val columnNames: Map<String, Int> by lazy {
        buildMap {
            stmt.getColumnNames().forEachIndexed { index, key ->
               val finalKey = if (containsKey(key)) {
                    var index = 1
                    val basicKey = "$key&JOIN"
                    var finalKey = basicKey + index
                    while (containsKey(finalKey)) {
                        finalKey = basicKey + ++index
                    }
                    finalKey
                } else {
                    key
                }

                put(finalKey, index)
            }
        }
    }

}

private inline fun <T> SqlCursor.getColumnValueOptional(
    name: String,
    getValue: (Int) -> T?,
): T? = columnNames[name]?.let { getValue(it) }

// This causes a collision the functions created in Swift and there we need to disable this conversion
@FunctionInterop.FileScopeConversion.Disabled
@Throws(PowerSyncException::class, IllegalArgumentException::class)
public fun SqlCursor.getBoolean(name: String): Boolean = getColumnValue(name) { getBoolean(it) }

@FunctionInterop.FileScopeConversion.Disabled
@Throws(PowerSyncException::class, IllegalArgumentException::class)
public fun SqlCursor.getBytes(name: String): ByteArray = getColumnValue(name) { getBytes(it) }

@FunctionInterop.FileScopeConversion.Disabled
@Throws(PowerSyncException::class, IllegalArgumentException::class)
public fun SqlCursor.getDouble(name: String): Double = getColumnValue(name) { getDouble(it) }

@FunctionInterop.FileScopeConversion.Disabled
@Throws(PowerSyncException::class, IllegalArgumentException::class)
public fun SqlCursor.getLong(name: String): Long = getColumnValue(name) { getLong(it) }

@FunctionInterop.FileScopeConversion.Disabled
@Throws(PowerSyncException::class, IllegalArgumentException::class)
public fun SqlCursor.getString(name: String): String = getColumnValue(name) { getString(it) }

@Throws(PowerSyncException::class, IllegalArgumentException::class)
public fun SqlCursor.getBooleanOptional(name: String): Boolean? = getColumnValueOptional(name) { getBoolean(it) }

@Throws(PowerSyncException::class, IllegalArgumentException::class)
public fun SqlCursor.getBytesOptional(name: String): ByteArray? = getColumnValueOptional(name) { getBytes(it) }

@Throws(PowerSyncException::class, IllegalArgumentException::class)
public fun SqlCursor.getDoubleOptional(name: String): Double? = getColumnValueOptional(name) { getDouble(it) }

@Throws(PowerSyncException::class, IllegalArgumentException::class)
public fun SqlCursor.getLongOptional(name: String): Long? = getColumnValueOptional(name) { getLong(it) }

@FunctionInterop.FileScopeConversion.Disabled
@Throws(PowerSyncException::class, IllegalArgumentException::class)
public fun SqlCursor.getStringOptional(name: String): String? = getColumnValueOptional(name) { getString(it) }
