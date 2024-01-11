package co.powersync.db.crud

/**
 * A transaction of client-side changes.
 */
data class CrudTransaction(
    /**
     * Unique transaction id.
     *
     * If null, this contains a list of changes recorded without an explicit transaction associated.
     */
    val transactionId: Int?,

    /**
     * List of client-side changes.
     */
    val crud: List<CrudEntry>,

    /**
     * Call to remove the changes from the local queue, once successfully uploaded.
     *
     * [writeCheckpoint] is optional.
     */
    val complete: (writeCheckpoint: String?) -> Unit
) {
    override fun toString() = "CrudTransaction<$transactionId, $crud>"
}