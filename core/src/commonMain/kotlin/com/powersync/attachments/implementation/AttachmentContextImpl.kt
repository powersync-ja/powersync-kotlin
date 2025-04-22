package com.powersync.attachments.implementation

import co.touchlab.kermit.Logger
import com.powersync.PowerSyncDatabase
import com.powersync.attachments.Attachment
import com.powersync.attachments.AttachmentContext
import com.powersync.attachments.AttachmentState
import com.powersync.db.getString
import com.powersync.db.internal.ConnectionContext
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Default implementation of [AttachmentContext].
 *
 * This class provides the standard logic for managing attachments in a SQLite table.
 * Users can override this class if they need custom logic for handling columns or other
 * database operations related to attachments.
 */
public open class AttachmentContextImpl(
    public val db: PowerSyncDatabase,
    public val table: String,
    private val logger: Logger,
    private val maxArchivedCount: Long,
) : AttachmentContext {
    /**
     * Delete the attachment from the attachment queue.
     */
    public override suspend fun deleteAttachment(id: String) {
        db.execute("DELETE FROM $table WHERE id = ?", listOf(id))
    }

    /**
     * Set the state of the attachment to ignore.
     */
    public override suspend fun ignoreAttachment(id: String) {
        db.execute(
            "UPDATE $table SET state = ? WHERE id = ?",
            listOf(AttachmentState.ARCHIVED.ordinal, id),
        )
    }

    /**
     * Get the attachment from the attachment queue using an ID.
     */
    public override suspend fun getAttachment(id: String): Attachment? =
        db.getOptional("SELECT * FROM $table WHERE id = ?", listOf(id)) {
            Attachment.fromCursor(it)
        }

    /**
     * Save the attachment to the attachment queue.
     */
    public override suspend fun saveAttachment(attachment: Attachment): Attachment =
        db.writeLock { ctx ->
            upsertAttachment(attachment, ctx)
        }

    /**
     * Save the attachments to the attachment queue.
     */
    public override suspend fun saveAttachments(attachments: List<Attachment>) {
        if (attachments.isEmpty()) {
            return
        }

        db.writeTransaction { tx ->
            for (attachment in attachments) {
                upsertAttachment(attachment, tx)
            }
        }
    }

    /**
     * Get all the ID's of attachments in the attachment queue.
     */
    public override suspend fun getAttachmentIds(): List<String> =
        db.getAll(
            "SELECT id FROM $table WHERE id IS NOT NULL",
        ) { it.getString("name") }

    public override suspend fun getAttachments(): List<Attachment> =
        db.getAll(
            """
                    SELECT 
                        * 
                    FROM 
                        $table 
                    WHERE 
                        id IS NOT NULL
                    ORDER BY 
                        timestamp ASC
                """,
        ) { Attachment.fromCursor(it) }

    /**
     * Gets all the active attachments which require an operation to be performed.
     */
    public override suspend fun getActiveAttachments(): List<Attachment> =
        db.getAll(
            """
                SELECT 
                    *
                FROM
                    $table 
                WHERE 
                    state = ?
                    OR state = ?
                    OR state = ?
                ORDER BY 
                    timestamp ASC
                """,
            listOf(
                AttachmentState.QUEUED_UPLOAD.ordinal,
                AttachmentState.QUEUED_DOWNLOAD.ordinal,
                AttachmentState.QUEUED_DELETE.ordinal,
            ),
        ) { Attachment.fromCursor(it) }

    /**
     * Helper function to clear the attachment queue
     * Currently only used for testing purposes.
     */
    public override suspend fun clearQueue() {
        logger.i("Clearing attachment queue...")
        db.execute("DELETE FROM $table")
    }

    /**
     * Delete attachments which have been archived
     * @returns true if all items have been deleted. Returns false if there might be more archived
     * items remaining.
     */
    public override suspend fun deleteArchivedAttachments(callback: suspend (attachments: List<Attachment>) -> Unit): Boolean {
        // First fetch the attachments in order to allow other cleanup
        val limit = 1000
        val attachments =
            db.getAll(
                """
                    SELECT
                        * 
                    FROM 
                        $table
                    WHERE 
                        state = ?
                    ORDER BY
                        timestamp DESC
                    LIMIT ? OFFSET ?
                    """,
                listOf(
                    AttachmentState.ARCHIVED.ordinal,
                    limit,
                    maxArchivedCount,
                ),
            ) { Attachment.fromCursor(it) }
        callback(attachments)
        db.execute(
            "DELETE FROM $table WHERE id IN (SELECT value FROM json_each(?));",
            listOf(
                Json.encodeToString(attachments.map { it.id }),
            ),
        )
        return attachments.size < limit
    }

    /**
     * Upserts an attachment record synchronously given a database connection context.
     */
    public override fun upsertAttachment(
        attachment: Attachment,
        context: ConnectionContext,
    ): Attachment {
        val updatedRecord =
            attachment.copy(
                timestamp = Clock.System.now().toEpochMilliseconds(),
            )

        context.execute(
            """
                INSERT OR REPLACE INTO 
                    $table (id, timestamp, filename, local_uri, media_type, size, state, has_synced, meta_data) 
                VALUES
                    (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            listOf(
                updatedRecord.id,
                updatedRecord.timestamp,
                updatedRecord.filename,
                updatedRecord.localUri,
                updatedRecord.mediaType,
                updatedRecord.size,
                updatedRecord.state.ordinal,
                updatedRecord.hasSynced,
                updatedRecord.metaData,
            ),
        )

        return attachment
    }
}
