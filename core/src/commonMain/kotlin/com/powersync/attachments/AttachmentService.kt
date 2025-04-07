package com.powersync.attachments

import com.powersync.db.internal.ConnectionContext
import kotlinx.coroutines.flow.Flow

/**
 * Context for performing Attachment operations.
 * This typically is provided through a locking/exclusivity method.
 */
public interface AttachmentContext {
    /**
     * Delete the attachment from the attachment queue.
     */
    public suspend fun deleteAttachment(id: String): Unit

    /**
     * Set the state of the attachment to ignore.
     */
    public suspend fun ignoreAttachment(id: String): Unit

    /**
     * Get the attachment from the attachment queue using an ID.
     */
    public suspend fun getAttachment(id: String): Attachment?

    /**
     * Save the attachment to the attachment queue.
     */
    public suspend fun saveAttachment(attachment: Attachment): Attachment

    /**
     * Save the attachments to the attachment queue.
     */
    public suspend fun saveAttachments(attachments: List<Attachment>): Unit

    /**
     * Get all the ID's of attachments in the attachment queue.
     */
    public suspend fun getAttachmentIds(): List<String>

    /**
     * Get all Attachment records present in the database.
     */
    public suspend fun getAttachments(): List<Attachment>

    /**
     * Gets all the active attachments which require an operation to be performed.
     */
    public suspend fun getActiveAttachments(): List<Attachment>

    /**
     * Helper function to clear the attachment queue
     * Currently only used for testing purposes.
     */
    public suspend fun clearQueue(): Unit

    /**
     * Delete attachments which have been archived
     * @returns true if all items have been deleted. Returns false if there might be more archived
     * items remaining.
     */
    public suspend fun deleteArchivedAttachments(callback: suspend (attachments: List<Attachment>) -> Unit): Boolean

    /**
     * Upserts an attachment record synchronously given a database connection context.
     */
    public fun upsertAttachment(
        attachment: Attachment,
        context: ConnectionContext,
    ): Attachment
}

/**
 * Service for interacting with the local attachment records.
 */
public interface AttachmentService {
    /**
     * Watcher for changes to attachments table.
     * Once a change is detected it will initiate a sync of the attachments
     */
    public fun watchActiveAttachments(): Flow<Unit>

    /**
     * Executes a callback with an exclusive lock on all attachment operations.
     * This helps prevent race conditions between different updates.
     */
    public suspend fun <R> withLock(action: suspend (context: AttachmentContext) -> R): R
}
