package com.powersync.db.internal

import app.cash.sqldelight.db.SqlCursor

internal class SqlCursorWrapper(val realCursor: SqlCursor):com.powersync.db.SqlCursor {
    override fun getBoolean(index: Int): Boolean? = realCursor.getBoolean(index)

    override fun getBytes(index: Int): ByteArray? = realCursor.getBytes(index)

    override fun getDouble(index: Int): Double? = realCursor.getDouble(index)

    override fun getLong(index: Int): Long? = realCursor.getLong(index)

    override fun getString(index: Int): String? = realCursor.getString(index)
}

internal fun <T> wrapperMapper(mapper:(com.powersync.db.SqlCursor)->T):(SqlCursor)->T{
    return {realCursor -> mapper(SqlCursorWrapper(realCursor))}
}