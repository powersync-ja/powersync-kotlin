package com.powersync.db.crud

/**
 * A batch of client-side changes.
 */
data class CrudBatch(
    /**
     * List of client-side changes.
     */
    val crud: List<CrudEntry>,

    /**
     * true if there are more changes in the local queue
     */
    val hasMore: Boolean,

    /**
     * Call to remove the changes from the local queue, once successfully uploaded.
     *
     * [writeCheckpoint] is optional.
     */
    val complete: suspend (writeCheckpoint: String?) -> Unit
)