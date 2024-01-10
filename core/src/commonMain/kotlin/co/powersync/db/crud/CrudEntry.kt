package co.powersync.db.crud

/**
 * A single client-side change.
 */
data class CrudEntry (

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
    val opData: Map<String, Any>?
){
    companion object {
        fun fromRow(row: HashMap<String, Any>): CrudEntry {
            val data = row["data"] as? Map<String, Any>
            return CrudEntry(
                id = row["id"] as String,
                clientId = row["op_id"] as Int,
                op = UpdateType.fromJsonChecked(row["op"] as String),
                table = row["table"] as String,
                transactionId = row["tx_id"] as? Int,
                opData = data?.get("data") as? Map<String, Any>
            )
        }
    }

    override fun toString(): String {
        return "CrudEntry<$transactionId/$clientId ${op.toJson()} $table/$id $opData>"
    }

}