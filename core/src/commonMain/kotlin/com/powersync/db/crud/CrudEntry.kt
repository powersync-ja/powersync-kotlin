package com.powersync.db.crud

import com.powersync.PowerSyncDatabase
import com.powersync.db.schema.Table
import com.powersync.utils.JsonUtil
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A single client-side change.
 */
public data class CrudEntry(
    /**
     * ID of the changed row.
     */
    val id: String,
    /**
     * Auto-incrementing client-side id.
     *
     * Reset whenever the database is re-created.
     */
    val clientId: Int,
    /**
     * Type of change.
     */
    val op: UpdateType,
    /**
     * Table that contained the change.
     */
    val table: String,
    /**
     * Auto-incrementing transaction id. This is the same for all operations
     * within the same transaction.
     *
     * Reset whenever the database is re-created.
     *
     * Currently, this is only present when [PowerSyncDatabase.writeTransaction] is used.
     * This may change in the future.
     */
    val transactionId: Int?,
    /**
     * User-defined metadata that can be attached to writes.
     *
     * This is the value the `_metadata` column had when the write to the database was made,
     * allowing backend connectors to e.g. identify a write and treat it specially.
     *
     * Note that the `_metadata` column is only available when [Table.trackMetadata] is enabled.
     */
    val metadata: String? = null,
    /**
     * Data associated with the change.
     *
     * For PUT, this is contains all non-null columns of the row.
     *
     * For PATCH, this is contains the columns that changed.
     *
     * For DELETE, this is null.
     */
    val opData: Map<String, String?>?,
    /**
     * Previous values before this change.
     *
     * These values can be tracked for `UPDATE` statements when [Table.trackPreviousValues] is
     * enabled.
     */
    val oldData: Map<String, String?>? = null,
) {
    public companion object {
        public fun fromRow(row: CrudRow): CrudEntry {
            val data = JsonUtil.json.parseToJsonElement(row.data).jsonObject
            return CrudEntry(
                id = data["id"]!!.jsonPrimitive.content,
                clientId = row.id.toInt(),
                op = UpdateType.fromJsonChecked(data["op"]!!.jsonPrimitive.content),
                opData =
                    data["data"]?.jsonObject?.mapValues { (_, value) ->
                        value.jsonPrimitive.contentOrNull
                    },
                table = data["type"]!!.jsonPrimitive.content,
                transactionId = row.txId,
                metadata = data["metadata"]?.jsonPrimitive?.content,
                oldData =
                    data["old"]?.jsonObject?.mapValues { (_, value) ->
                        value.jsonPrimitive.contentOrNull
                    },
            )
        }
    }

    override fun toString(): String = "CrudEntry<$transactionId/$clientId ${op.toJson()} $table/$id $opData>"
}
