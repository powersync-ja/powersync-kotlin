package com.powersync.db.internal

enum class PsInternalTable(private val tableName: String) {
    DATA("ps_data"),
    CRUD("ps_crud"),
    BUCKETS("ps_buckets"),
    OPLOG("ps_oplog");


    override fun toString(): String {
        return tableName
    }
}