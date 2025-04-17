package com.powersync.attachments.implementation

import co.touchlab.kermit.Logger
import com.powersync.PowerSyncDatabase
import com.powersync.attachments.AttachmentContext
import com.powersync.attachments.AttachmentService
import com.powersync.attachments.AttachmentState
import com.powersync.db.getString
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

    public override suspend fun <R> withContext(action: suspend (AttachmentContext) -> R): R = mutex.withLock { action(context) }

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
            ) { it.getString("id") }
            // We only use changes here to trigger a sync consolidation
            .map { Unit }
    }
}
