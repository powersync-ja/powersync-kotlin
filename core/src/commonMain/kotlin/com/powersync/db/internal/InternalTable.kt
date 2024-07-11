package com.powersync.db.internal

internal enum class InternalTable(private val tableName: String) {
    DATA("ps_data"),
    CRUD("ps_crud"),
    BUCKETS("ps_buckets"),
    OPLOG("ps_oplog"),
    UNTYPED("ps_untyped");


    override fun toString(): String {
        return tableName
    }
}