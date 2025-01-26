package com.powersync.db

public interface SqlCursor {
    public fun getBoolean(index: Int): Boolean?

    public fun getBytes(index: Int): ByteArray?

    public fun getDouble(index: Int): Double?

    public fun getLong(index: Int): Long?

    public fun getString(index: Int): String?
}