package com.powersync.attachments

import com.powersync.db.schema.Column
import com.powersync.db.schema.ColumnType
import com.powersync.db.schema.Table

/**
 * Creates a PowerSync table for storing local attachment state.
 *
 * @param name The name of the table.
 * @return A [Table] object configured for storing attachment data.
 */
public fun createAttachmentsTable(name: String): Table =
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
                Column("has_synced", ColumnType.INTEGER),
                Column("meta_data", ColumnType.TEXT),
            ),
        localOnly = true,
    )
