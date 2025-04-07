package com.powersync.attachments.implementation

import co.touchlab.kermit.Logger
import com.powersync.PowerSyncDatabase
import com.powersync.attachments.Attachment
import com.powersync.attachments.AttachmentContext
import com.powersync.attachments.AttachmentService
import com.powersync.attachments.AttachmentState
import com.powersync.db.internal.ConnectionContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Service for interacting with the local attachment records.
 */
public open class AttachmentServiceImpl(
    private val db: PowerSyncDatabase,
    private val tableName: String,
    private val logger: Logger,
    private val maxArchivedCount: Long,
) : AttachmentService {
    /**
     * Table used for storing attachments in the attachment queue.
     */
    private val table: String
        get() = tableName

    private val mutex = Mutex()

    private val context: AttachmentContext =
        AttachmentContextImpl(
            db = db,
            table = table,
            logger = logger,
            maxArchivedCount = maxArchivedCount,
        )

    public override suspend fun <R> withLock(action: suspend (AttachmentContext) -> R): R = mutex.withLock { action(context) }

    /**
     * Watcher for changes to attachments table.
     * Once a change is detected it will initiate a sync of the attachments
     */
    public override fun watchActiveAttachments(): Flow<Unit> {
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

    override fun upsertAttachment(
        attachment: Attachment,
        context: ConnectionContext,
    ): Attachment = this.context.upsertAttachment(attachment = attachment, context = context)

    override suspend fun deleteAttachment(id: String): Unit = withLock { it.deleteAttachment(id) }

    override suspend fun ignoreAttachment(id: String): Unit = withLock { it.ignoreAttachment(id) }

    override suspend fun getAttachment(id: String): Attachment? = withLock { it.getAttachment(id) }

    override suspend fun saveAttachment(attachment: Attachment): Attachment = withLock { it.saveAttachment(attachment) }

    override suspend fun saveAttachments(attachments: List<Attachment>): Unit = withLock { it.saveAttachments(attachments) }

    override suspend fun getAttachmentIds(): List<String> = withLock { it.getAttachmentIds() }

    override suspend fun getAttachments(): List<Attachment> = withLock { it.getAttachments() }

    override suspend fun getActiveAttachments(): List<Attachment> = withLock { it.getActiveAttachments() }

    override suspend fun clearQueue(): Unit = withLock { it.clearQueue() }

    override suspend fun deleteArchivedAttachments(callback: suspend (List<Attachment>) -> Unit): Boolean =
        withLock { it.deleteArchivedAttachments(callback) }
}
