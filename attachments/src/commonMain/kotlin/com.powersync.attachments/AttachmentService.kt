package com.powersync.attachments

import co.touchlab.kermit.Logger
import com.powersync.PowerSyncDatabase
import com.powersync.db.internal.ConnectionContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Service for interacting with the local attachment records.
 */
public class AttachmentService(
    private val db: PowerSyncDatabase,
    private val tableName: String,
    private val logger: Logger,
) {
    /**
     * Table used for storing attachments in the attachment queue.
     */
    private val table: String
        get() = tableName

    /**
     * Delete the attachment from the attachment queue.
     */
    public suspend fun deleteAttachment(id: String) {
        db.execute("DELETE FROM $table WHERE id = ?", listOf(id))
    }

    /**
     * Set the state of the attachment to ignore.
     */
    public suspend fun ignoreAttachment(id: String) {
        db.execute(
            "UPDATE $table SET state = ? WHERE id = ?",
            listOf(AttachmentState.ARCHIVED.ordinal, id),
        )
    }

    /**
     * Get the attachment from the attachment queue using an ID.
     */
    public suspend fun getAttachment(id: String): Attachment? =
        db.getOptional("SELECT * FROM $table WHERE id = ?", listOf(id)) {
            Attachment.fromCursor(it)
        }

    /**
     * Save the attachment to the attachment queue.
     */
    public suspend fun saveAttachment(attachment: Attachment): Attachment =
        db.writeLock { ctx ->
            upsertAttachment(attachment, ctx)
        }

    /**
     * Save the attachments to the attachment queue.
     */
    public suspend fun saveAttachments(attachments: List<Attachment>) {
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
    public suspend fun getAttachmentIds(): List<String> =
        db.getAll(
            "SELECT id FROM $table WHERE id IS NOT NULL",
        ) { it.getString(0)!! }

    public suspend fun getAttachments(): List<Attachment> =
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
    public suspend fun getActiveAttachments(): List<Attachment> =
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
     * Watcher for changes to attachments table.
     * Once a change is detected it will initiate a sync of the attachments
     */
    public fun watchActiveAttachments(): Flow<Unit> {
        logger.i("Watching attachments...")
        return db
            .watch(
                """
                SELECT 
                    id 
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
            ) { it.getString(0)!! }
            // We only use changes here to trigger a sync consolidation
            .map { Unit }
    }

    /**
     * Helper function to clear the attachment queue
     * Currently only used for testing purposes.
     */
    public suspend fun clearQueue() {
        logger.i("Clearing attachment queue...")
        db.execute("DELETE FROM $table")
    }

    /**
     * Delete attachments which have been archived
     */
    public suspend fun deleteArchivedAttachments(callback: suspend (attachments: List<Attachment>) -> Unit) {
        // First fetch the attachments in order to allow other cleanup
        val attachments =
            db.getAll(
                "SELECT * FROM $table WHERE state = ?",
                listOf(AttachmentState.ARCHIVED.ordinal),
            ) { Attachment.fromCursor(it) }
        callback(attachments)
        db.execute(
            "DELETE FROM $table WHERE id IN (SELECT value FROM json_each(?));",
            listOf(
                Json.encodeToString(attachments.map { it.id }),
            ),
        )
    }

    /**
     * Upserts an attachment record synchronously given a database connection context.
     */
    public fun upsertAttachment(
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
                    $table (id, timestamp, filename, local_uri, media_type, size, state) 
                VALUES
                    (?, ?, ?, ?, ?, ?, ?)
            """,
            listOf(
                updatedRecord.id,
                updatedRecord.timestamp,
                updatedRecord.filename,
                updatedRecord.localUri,
                updatedRecord.mediaType,
                updatedRecord.size,
                updatedRecord.state,
            ),
        )

        return attachment
    }
}
