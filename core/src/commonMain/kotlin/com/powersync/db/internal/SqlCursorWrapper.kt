package com.powersync.db.internal

import app.cash.sqldelight.db.SqlCursor
import com.powersync.persistence.driver.ColNamesSqlCursor

internal class SqlCursorWrapper(val realCursor: ColNamesSqlCursor) : com.powersync.db.SqlCursor {
    override fun getBoolean(index: Int): Boolean? = realCursor.getBoolean(index)

    override fun getBytes(index: Int): ByteArray? = realCursor.getBytes(index)

    override fun getDouble(index: Int): Double? = realCursor.getDouble(index)

    override fun getLong(index: Int): Long? = realCursor.getLong(index)

    override fun getString(index: Int): String? = realCursor.getString(index)

    override fun columnName(index: Int): String? = realCursor.columnName(index)

    override val columnCount: Int
        get() = realCursor.columnCount

    override val columnNames: Map<String, Int> by lazy {
        val map = HashMap<String, Int>(this.columnCount)
        for (i in 0 until columnCount) {
            val key = columnName(i)
            if (key == null) {
                continue
            }
            if (map.containsKey(key)) {
                var index = 1
                val basicKey = "$key&JOIN"
                var finalKey = basicKey + index
                while (map.containsKey(finalKey)) {
                    finalKey = basicKey + ++index
                }
                map[finalKey] = i
            } else {
                map[key] = i
            }
        }
        map
    }
}

internal fun <T> wrapperMapper(mapper: (com.powersync.db.SqlCursor) -> T): (SqlCursor) -> T {
    return { realCursor -> mapper(SqlCursorWrapper(realCursor as ColNamesSqlCursor)) }
}