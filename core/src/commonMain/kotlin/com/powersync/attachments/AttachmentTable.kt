package com.powersync.attachments

import com.powersync.db.schema.Column
import com.powersync.db.schema.ColumnType
import com.powersync.db.schema.Table

/**
 * Creates a PowerSync table for storing local attachment state
 */
public fun createAttachmentsTable(
    name: String,
    additionalColumns: List<Column>? = null,
): Table =
    Table(
        name = name,
        columns =
            listOf(
                Column("filename", ColumnType.TEXT),
                Column("local_uri", ColumnType.TEXT),
                Column("timestamp", ColumnType.INTEGER),
                Column("size", ColumnType.INTEGER),
                Column("media_type", ColumnType.TEXT),
                Column("state", ColumnType.INTEGER),
            ).plus(additionalColumns ?: emptyList()),
        localOnly = true,
    )
