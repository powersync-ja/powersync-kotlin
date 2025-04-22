package com.powersync.attachments

import co.touchlab.kermit.Logger
import com.powersync.PowerSyncDatabase
import com.powersync.PowerSyncException
import com.powersync.attachments.implementation.AttachmentServiceImpl
import com.powersync.attachments.storage.IOLocalStorageAdapter
import com.powersync.attachments.sync.SyncingService
import com.powersync.db.getString
import com.powersync.db.internal.ConnectionContext
import com.powersync.db.runWrappedSuspending
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.files.Path
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A watched attachment record item.
 * This is usually returned from watching all relevant attachment IDs.
 */
public data class WatchedAttachmentItem(
    /**
     * Id for the attachment record.
     */
    public val id: String,
    /**
     * File extension used to determine an internal filename for storage if no [filename] is provided.
     */
    public val fileExtension: String? = null,
    /**
     * Filename to store the attachment with.
     */
    public val filename: String? = null,
    /**
     * Optional metadata for the attachment record.
     */
    public val metaData: String? = null,
) {
    init {
        require(fileExtension != null || filename != null) {
            "Either fileExtension or filename must be provided."
        }
    }
}

/**
 * Class used to implement the attachment queue.
 * Requires a PowerSyncDatabase, an implementation of
 * AbstractRemoteStorageAdapter, and an attachment directory name which will
 * determine which folder attachments are stored into.
 */
public open class AttachmentQueue(
    /**
     * PowerSync database client.
     */
    public val db: PowerSyncDatabase,
    /**
     * Adapter which interfaces with the remote storage backend.
     */
    public val remoteStorage: RemoteStorage,
    /**
     * Directory name where attachment files will be written to disk.
     * This will be created if it does not exist.
     */
    private val attachmentsDirectory: String,
    /**
     * A flow generator for the current state of local attachments.
     * Example:
     * ```kotlin
     * watchAttachments = {
     *     db.watch(
     *         sql = """
     *             SELECT
     *                 photo_id as id,
     *                 'jpg' as fileExtension
     *             FROM
     *                 checklists
     *             WHERE
     *                 photo_id IS NOT NULL
     *         """,
     *     ) { cursor ->
     *         WatchedAttachmentItem(
     *             id = cursor.getString("id"),
     *             fileExtension = "jpg",
     *         )
     *     }
     * }
     * ```
     */
    private val watchAttachments: () -> Flow<List<WatchedAttachmentItem>>,
    /**
     * Provides access to local filesystem storage methods.
     */
    public val localStorage: LocalStorage = IOLocalStorageAdapter(),
    /**
     * SQLite table where attachment state will be recorded.
     */
    private val attachmentsQueueTableName: String = DEFAULT_TABLE_NAME,
    /**
     * Attachment operation error handler. Specifies if failed attachment operations
     * should be retried.
     */
    private val errorHandler: SyncErrorHandler? = null,
    /**
     * Periodic interval to trigger attachment sync operations.
     */
    private val syncInterval: Duration = 30.seconds,
    /**
     * Archived attachments can be used as a cache which can be restored if an attachment ID
     * reappears after being removed. This parameter defines how many archived records are retained.
     * Records are deleted once the number of items exceeds this value.
     */
    private val archivedCacheLimit: Long = 100,
    /**
     * Throttles remote sync operations triggering.
     */
    private val syncThrottleDuration: Duration = 1.seconds,
    /**
     * Creates a list of subdirectories in the [attachmentsDirectory] directory.
     */
    private val subdirectories: List<String>? = null,
    /**
     * Should attachments be downloaded.
     */
    private val downloadAttachments: Boolean = true,
    /**
     * Logging interface used for all log operations.
     */
    public val logger: Logger = Logger,
    /**
     * Optional scope to launch syncing jobs in.
     */
    private val coroutineScope: CoroutineScope? = null,
) {
    public companion object {
        /**
         * Default table name for attachments.
         */
        public const val DEFAULT_TABLE_NAME: String = "attachments"

        /**
         * Default directory name for attachments.
         */
        public const val DEFAULT_ATTACHMENTS_DIRECTORY_NAME: String = "attachments"
    }

    /**
     * Service which provides access to attachment records.
     * Use this to:
     *  - Query all current attachment records.
     *  - Create new attachment records for upload/download.
     */
    public val attachmentsService: AttachmentService =
        AttachmentServiceImpl(
            db,
            attachmentsQueueTableName,
            logger,
            maxArchivedCount = archivedCacheLimit,
        )

    // The syncScope is used to manage coroutines for syncing operations.
    // If a coroutineScope is provided, it reuses its context and adds Dispatchers.IO for IO-bound tasks,
    // ensuring proper cancellation propagation. If no coroutineScope is provided, a new CoroutineScope
    // is created with a SupervisorJob to isolate failures and Dispatchers.IO for efficient IO operations.
    private val syncScope =
        coroutineScope?.let {
            CoroutineScope(it.coroutineContext + Dispatchers.IO)
        } ?: CoroutineScope(CoroutineScope(Dispatchers.IO).coroutineContext + SupervisorJob())

    private var syncStatusJob: Job? = null
    private val mutex = Mutex()

    /**
     * Syncing service for this attachment queue.
     * This processes attachment records and performs relevant upload, download, and delete
     * operations.
     */
    private val syncingService =
        SyncingService(
            remoteStorage,
            localStorage,
            attachmentsService,
            ::getLocalUri,
            errorHandler,
            logger,
            syncScope,
            syncThrottleDuration,
        )

    public var closed: Boolean = false

    /**
     * Initialize the attachment queue by:
     * 1. Creating the attachments directory.
     * 2. Adding watches for uploads, downloads, and deletes.
     * 3. Adding a trigger to run uploads, downloads, and deletes when the device is online after being offline.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun startSync(): Unit =
        runWrappedSuspending {
            mutex.withLock {
                if (closed) {
                    throw Exception("Attachment queue has been closed")
                }

                stopSyncingInternal()

                // Ensure the directory where attachments are downloaded exists.
                localStorage.makeDir(attachmentsDirectory)

                subdirectories?.forEach { subdirectory ->
                    localStorage.makeDir(Path(attachmentsDirectory, subdirectory).toString())
                }

                attachmentsService.withContext { context ->
                    verifyAttachments(context)
                }

                syncingService.startSync(syncInterval)

                // Listen for connectivity changes.
                syncStatusJob =
                    syncScope.launch {
                        launch {
                            var previousConnected = db.currentStatus.connected
                            db.currentStatus.asFlow().collect { status ->
                                if (!previousConnected && status.connected) {
                                    syncingService.triggerSync()
                                }
                                previousConnected = status.connected
                            }
                        }

                        launch {
                            // Watch local attachment relationships and sync the attachment records.
                            watchAttachments().collect { items ->
                                processWatchedAttachments(items)
                            }
                        }
                    }
            }
        }

    /**
     * Stops syncing. Syncing may be resumed with [startSync].
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun stopSyncing(): Unit =
        mutex.withLock {
            stopSyncingInternal()
        }

    private suspend fun stopSyncingInternal(): Unit =
        runWrappedSuspending {
            if (closed) {
                return@runWrappedSuspending
            }

            syncStatusJob?.cancelAndJoin()
            syncStatusJob = null
            syncingService.stopSync()
        }

    /**
     * Closes the queue.
     * The queue cannot be used after closing.
     * A new queue should be created.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun close(): Unit =
        runWrappedSuspending {
            mutex.withLock {
                if (closed) {
                    return@runWrappedSuspending
                }

                syncStatusJob?.cancelAndJoin()
                syncingService.close()
                if (coroutineScope == null) {
                    // Only cancel the internal scope if we created it.
                    syncScope.coroutineContext[Job]?.takeIf { it.isActive }?.cancelAndJoin()
                }
                closed = true
            }
        }

    /**
     * Resolves the filename for new attachment items.
     * A new attachment from [watchAttachments] might not include a filename.
     * Concatenates the attachment ID and extension by default.
     * This method can be overridden for custom behavior.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public open suspend fun resolveNewAttachmentFilename(
        attachmentId: String,
        fileExtension: String?,
    ): String = "$attachmentId.$fileExtension"

    /**
     * Processes attachment items returned from [watchAttachments].
     * The default implementation asserts the items returned from [watchAttachments] as the definitive
     * state for local attachments.
     *
     * Records currently in the attachment queue which are not present in the items are deleted from
     * the queue.
     *
     * Received items which are not currently in the attachment queue are assumed scheduled for
     * download. This requires that locally created attachments should be created with [saveFile]
     * before assigning the attachment ID to the relevant watched tables.
     *
     * This method can be overridden for custom behavior.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public open suspend fun processWatchedAttachments(items: List<WatchedAttachmentItem>): Unit =
        runWrappedSuspending {
            /**
             * Use a lock here to prevent conflicting state updates.
             */
            attachmentsService.withContext { attachmentsContext ->
                /**
                 * Need to get all the attachments which are tracked in the DB.
                 * We might need to restore an archived attachment.
                 */
                val currentAttachments = attachmentsContext.getAttachments()
                val attachmentUpdates = mutableListOf<Attachment>()

                for (item in items) {
                    val existingQueueItem = currentAttachments.find { it.id == item.id }

                    if (existingQueueItem == null) {
                        if (!downloadAttachments) {
                            continue
                        }
                        // This item should be added to the queue.
                        // This item is assumed to be coming from an upstream sync.
                        // Locally created new items should be persisted using [saveFile] before
                        // this point.
                        val filename =
                            item.filename ?: resolveNewAttachmentFilename(
                                attachmentId = item.id,
                                fileExtension = item.fileExtension,
                            )

                        attachmentUpdates.add(
                            Attachment(
                                id = item.id,
                                filename = filename,
                                state = AttachmentState.QUEUED_DOWNLOAD,
                                metaData = item.metaData,
                            ),
                        )
                    } else if
                        (existingQueueItem.state == AttachmentState.ARCHIVED) {
                        // The attachment is present again. Need to queue it for sync.
                        // We might be able to optimize this in future.
                        if (existingQueueItem.hasSynced) {
                            // No remote action required, we can restore the record (avoids deletion).
                            attachmentUpdates.add(
                                existingQueueItem.copy(state = AttachmentState.SYNCED),
                            )
                        } else {
                            /**
                             * The localURI should be set if the record was meant to be downloaded
                             * and has been synced. If it's missing and hasSynced is false then
                             * it must be an upload operation.
                             */
                            attachmentUpdates.add(
                                existingQueueItem.copy(
                                    state =
                                        if (existingQueueItem.localUri == null) {
                                            AttachmentState.QUEUED_DOWNLOAD
                                        } else {
                                            AttachmentState.QUEUED_UPLOAD
                                        },
                                ),
                            )
                        }
                    }
                }

                /**
                 * Archive any items not specified in the watched items.
                 * For QUEUED_DELETE or QUEUED_UPLOAD states, archive only if hasSynced is true.
                 * For other states, archive if the record is not found in the items.
                 */
                currentAttachments
                    .filter { attachment ->
                        val notInWatchedItems =
                            items.find { update -> update.id == attachment.id } == null
                        if (notInWatchedItems) {
                            when (attachment.state) {
                                // Archive these record if they have synced
                                AttachmentState.QUEUED_DELETE, AttachmentState.QUEUED_UPLOAD -> attachment.hasSynced
                                // Other states, such as QUEUED_DOWNLOAD can be archived if they are not present in watched items
                                else -> true
                            }
                        } else {
                            // The record is present in watched items, no need to archive it
                            false
                        }
                    }.forEach {
                        attachmentUpdates.add(it.copy(state = AttachmentState.ARCHIVED))
                    }

                attachmentsContext.saveAttachments(attachmentUpdates)
            }
        }

    /**
     * A function which creates a new attachment locally. This new attachment is queued for upload
     * after creation.
     *
     * The filename is resolved using [resolveNewAttachmentFilename].
     *
     * A [updateHook] is provided which should be used when assigning relationships to the newly
     * created attachment. This hook is executed in the same write transaction which creates the
     * attachment record.
     *
     * This method can be overridden for custom behavior.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public open suspend fun saveFile(
        data: Flow<ByteArray>,
        mediaType: String,
        fileExtension: String? = null,
        metaData: String? = null,
        updateHook: (context: ConnectionContext, attachment: Attachment) -> Unit,
    ): Attachment =
        runWrappedSuspending {
            val id = db.get("SELECT uuid() as id") { it.getString("id") }
            val filename =
                resolveNewAttachmentFilename(attachmentId = id, fileExtension = fileExtension)
            val localUri = getLocalUri(filename)

            // Write the file to the filesystem.
            val fileSize = localStorage.saveFile(localUri, data)

            /**
             * Starts a write transaction. The attachment record and relevant local relationship
             * assignment should happen in the same transaction.
             */
            attachmentsService.withContext { attachmentContext ->
                db.writeTransaction { tx ->
                    val attachment =
                        Attachment(
                            id = id,
                            filename = filename,
                            size = fileSize,
                            mediaType = mediaType,
                            state = AttachmentState.QUEUED_UPLOAD,
                            localUri = localUri,
                            metaData = metaData,
                        )

                    /**
                     * Allow consumers to set relationships to this attachment ID.
                     */
                    updateHook.invoke(tx, attachment)

                    return@writeTransaction attachmentContext.upsertAttachment(
                        attachment,
                        tx,
                    )
                }
            }
        }

    /**
     * A function which creates an attachment delete operation locally. This operation is queued
     * for delete.
     * The default implementation assumes the attachment record already exists locally. An exception
     * is thrown if the record does not exist locally.
     * This method can be overridden for custom behavior.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public open suspend fun deleteFile(
        attachmentId: String,
        updateHook: (context: ConnectionContext, attachment: Attachment) -> Unit,
    ): Attachment =
        runWrappedSuspending {
            attachmentsService.withContext { attachmentContext ->
                val attachment =
                    attachmentContext.getAttachment(attachmentId)
                        ?: throw error("Attachment record with id $attachmentId was not found.")

                db.writeTransaction { tx ->
                    updateHook.invoke(tx, attachment)
                    return@writeTransaction attachmentContext.upsertAttachment(
                        attachment.copy(
                            state = AttachmentState.QUEUED_DELETE,
                            hasSynced = false,
                        ),
                        tx,
                    )
                }
            }
        }

    /**
     * Returns the user's storage directory with the attachment path used to load the file.
     * Example: filePath: "attachment-1.jpg" returns "/data/user/0/com.yourdomain.app/files/attachments/attachment-1.jpg".
     */
    public open fun getLocalUri(filename: String): String = Path(attachmentsDirectory, filename).toString()

    /**
     * Removes all archived items.
     */
    public suspend fun expireCache() {
        var done: Boolean
        attachmentsService.withContext { context ->
            do {
                done = syncingService.deleteArchivedAttachments(context)
            } while (!done)
        }
    }

    /**
     * Clears the attachment queue and deletes all attachment files.
     */
    public suspend fun clearQueue() {
        attachmentsService.withContext {
            it.clearQueue()
        }
        // Remove the attachments directory.
        localStorage.rmDir(attachmentsDirectory)
    }

    /**
     * Cleans up stale attachments.
     */
    private suspend fun verifyAttachments(context: AttachmentContext) {
        val attachments = context.getActiveAttachments()
        val updates = mutableListOf<Attachment>()

        for (attachment in attachments) {
            if (attachment.localUri == null) {
                continue
            }
            val exists = localStorage.fileExists(attachment.localUri)
            if (
                attachment.state == AttachmentState.SYNCED || attachment.state == AttachmentState.QUEUED_UPLOAD && !exists
            ) {
                updates.add(
                    attachment.copy(
                        state = AttachmentState.ARCHIVED,
                        localUri = null,
                    ),
                )
            }
        }

        context.saveAttachments(updates)
    }
}
