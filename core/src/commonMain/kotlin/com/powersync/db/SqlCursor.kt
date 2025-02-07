package com.powersync.db

import com.powersync.PowerSyncException

public interface SqlCursor {
    @Throws(PowerSyncException::class)
    public fun getBoolean(index: Int): Boolean?

    @Throws(PowerSyncException::class)
    public fun getBytes(index: Int): ByteArray?

    @Throws(PowerSyncException::class)
    public fun getDouble(index: Int): Double?

    @Throws(PowerSyncException::class)
    public fun getLong(index: Int): Long?

    @Throws(PowerSyncException::class)
    public fun getString(index: Int): String?

    @Throws(PowerSyncException::class)
    public fun columnName(index: Int): String?

    public val columnCount: Int

    public val columnNames: Map<String, Int>

    @Throws(PowerSyncException::class, IllegalArgumentException::class)
    public fun getBoolean(name: String): Boolean = getColumnValue(name) { getBoolean(it) }

    @Throws(PowerSyncException::class, IllegalArgumentException::class)
    public fun getBytes(name: String): ByteArray = getColumnValue(name) { getBytes(it) }

    @Throws(PowerSyncException::class, IllegalArgumentException::class)
    public fun getDouble(name: String): Double = getColumnValue(name) { getDouble(it) }

    @Throws(PowerSyncException::class, IllegalArgumentException::class)
    public fun getLong(name: String): Long = getColumnValue(name) { getLong(it) }

    @Throws(PowerSyncException::class, IllegalArgumentException::class)
    public fun getString(name: String): String = getColumnValue(name) { getString(it) }

    @Throws(PowerSyncException::class, IllegalArgumentException::class)
    public fun getBooleanOptional(name: String): Boolean? = getColumnValueOptional(name) { getBoolean(it) }

    @Throws(PowerSyncException::class, IllegalArgumentException::class)
    public fun getBytesOptional(name: String): ByteArray? = getColumnValueOptional(name) { getBytes(it) }

    @Throws(PowerSyncException::class, IllegalArgumentException::class)
    public fun getDoubleOptional(name: String): Double? = getColumnValueOptional(name) { getDouble(it) }

    @Throws(PowerSyncException::class, IllegalArgumentException::class)
    public fun getLongOptional(name: String): Long? = getColumnValueOptional(name) { getLong(it) }

    @Throws(PowerSyncException::class, IllegalArgumentException::class)
    public fun getStringOptional(name: String): String? = getColumnValueOptional(name) { getString(it) }
}

private inline fun <T> SqlCursor.getColumnValue(
    name: String,
    getValue: (Int) -> T?,
): T {
    val index = columnNames[name] ?: throw IllegalArgumentException("Column '$name' not found")
    return getValue(index) ?: throw IllegalArgumentException("Null value found for column '$name'")
}

private inline fun <T> SqlCursor.getColumnValueOptional(
    name: String,
    getValue: (Int) -> T?,
): T? = columnNames[name]?.let { getValue(it) }
