package com.powersync.db.crud

import com.powersync.utils.JsonUtil
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
     * Data associated with the change.
     *
     * For PUT, this is contains all non-null columns of the row.
     *
     * For PATCH, this is contains the columns that changed.
     *
     * For DELETE, this is null.
     */
    val opData: Map<String, String>?
) {
    public companion object {
        public fun fromRow(row: CrudRow): CrudEntry {
            val data = JsonUtil.json.parseToJsonElement(row.data).jsonObject
            return CrudEntry(
                id = data["id"]!!.jsonPrimitive.content,
                clientId = row.id.toInt(),
                op = UpdateType.fromJsonChecked(data["op"]!!.jsonPrimitive.content),
                opData = data["data"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content },
                table = data["type"]!!.jsonPrimitive.content,
                transactionId = row.txId,
            )
        }
    }

    override fun toString(): String {
        return "CrudEntry<$transactionId/$clientId ${op.toJson()} $table/$id $opData>"
    }

}