package com.powersync.db.crud

import com.powersync.db.schema.Table
import com.powersync.utils.JsonUtil
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A single client-side change.
 */
@ConsistentCopyVisibility
public data class CrudEntry internal constructor(
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
    @Deprecated("Use data instead", replaceWith = ReplaceWith("data"))
    val opData: Map<String, String?>?,
    /**
     * Data associated with the change.
     *
     * For PUT, this is contains all non-null columns of the row.
     *
     * For PATCH, this is contains the columns that changed.
     *
     * For DELETE, this is null.
     */
    val data: Map<String, Any?>?,
    /**
     * Previous values before this change.
     *
     * These values can be tracked for `UPDATE` statements when [Table.trackPreviousValues] is
     * enabled.
     */
    @Deprecated("Use typedPreviousValues instead", replaceWith = ReplaceWith("typedPreviousValues"))
    val previousValues: Map<String, String?>? = null,
    /**
     * Previous values before this change.
     *
     * These values can be tracked for `UPDATE` statements when [Table.trackPreviousValues] is
     * enabled.
     */
    val typedPreviousValues: Map<String, Any?>? = null,
) {
    public companion object {
        public fun fromRow(row: CrudRow): CrudEntry {
            val data = JsonUtil.json.parseToJsonElement(row.data).jsonObject
            val opData = data["data"]?.asData()
            val previousValues = data["old"]?.asData()

            return CrudEntry(
                id = data["id"]!!.jsonPrimitive.content,
                clientId = row.id.toInt(),
                op = UpdateType.fromJsonChecked(data["op"]!!.jsonPrimitive.content),
                opData = opData?.toStringMap(),
                data = opData,
                table = data["type"]!!.jsonPrimitive.content,
                transactionId = row.txId,
                metadata = data["metadata"]?.jsonPrimitive?.content,
                typedPreviousValues = previousValues,
                previousValues = previousValues?.toStringMap(),
            )
        }

        private fun JsonElement.asData(): Map<String, Any?> =
            jsonObject.mapValues { (_, value) ->
                val primitive = value.jsonPrimitive
                if (primitive === JsonNull) {
                    null
                } else if (primitive.isString) {
                    primitive.content
                } else {
                    primitive.content.jsonNumberOrBoolean()
                }
            }

        private fun String.jsonNumberOrBoolean(): Any =
            when {
                this == "true" -> true
                this == "false" -> false
                this.any { char -> char == '.' || char == 'e' || char == 'E' } -> this.toDouble()
                else -> this.toInt()
            }

        private fun Map<String, Any?>.toStringMap(): Map<String, String> = mapValues { (_, v) -> v.toString() }
    }

    override fun toString(): String = "CrudEntry<$transactionId/$clientId ${op.toJson()} $table/$id $opData>"
}
