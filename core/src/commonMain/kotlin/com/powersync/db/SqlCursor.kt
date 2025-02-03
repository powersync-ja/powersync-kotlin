package com.powersync.db

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

public fun SqlCursor.getBoolean(name: String): Boolean? = columnNames[name]?.let { getBoolean(it) }
public fun SqlCursor.getBytes(name: String): ByteArray? = columnNames[name]?.let { getBytes(it) }
public fun SqlCursor.getDouble(name: String): Double? = columnNames[name]?.let { getDouble(it) }
public fun SqlCursor.getLong(name: String): Long? = columnNames[name]?.let { getLong(it) }
public fun SqlCursor.getString(name: String): String? = columnNames[name]?.let { getString(it) }