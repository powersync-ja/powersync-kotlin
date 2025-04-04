package com.powersync.attachments

import co.touchlab.kermit.Logger
import com.powersync.PowerSyncDatabase
import com.powersync.PowerSyncException
import com.powersync.attachments.storage.IOLocalStorageAdapter
import com.powersync.attachments.sync.SyncingService
import com.powersync.db.internal.ConnectionContext
import com.powersync.db.runWrappedSuspending
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
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
     * Id for the attachment record
     */
    public val id: String,
    /**
     * File extension used to determine an internal filename for storage if no [filename] is provided
     */
    public val fileExtension: String? = null,
    /**
     * Filename to store the attachment with
     */
    public val filename: String? = null,
) {
    init {
        require(fileExtension != null || filename != null) {
            "Either fileExtension or filename must be provided."
        }
    }
}

/**
 * Abstract class used to implement the attachment queue
 * Requires a PowerSyncDatabase, an implementation of
 * AbstractRemoteStorageAdapter and an attachment directory name which will
 * determine which folder attachments are stored into.
 */
public abstract class AbstractAttachmentQueue(
    /**
     * PowerSync database client
     */
    public val db: PowerSyncDatabase,
    /**
     * Adapter which interfaces with the remote storage backend
     */
    public val remoteStorage: RemoteStorageAdapter,
    /**
     * Provides access to local filesystem storage methods
     */
    public val localStorage: LocalStorageAdapter = IOLocalStorageAdapter(),
    /**
     * Directory name where attachment files will be written to disk.
     * This will be created under the directory returned from [getStorageDirectory]
     */
    private val attachmentDirectoryName: String = DEFAULT_ATTACHMENTS_DIRECTORY_NAME,
    /**
     * SQLite table where attachment state will be recorded
     */
    private val attachmentsQueueTableName: String = DEFAULT_TABLE_NAME,
    /**
     * Attachment operation error handler. This specified if failed attachment operations
     * should be retried.
     */
    private val errorHandler: SyncErrorHandler? = null,
    /**
     * Periodic interval to trigger attachment sync operations
     */
    private val syncInterval: Duration = 30.seconds,
    /**
     * Archived attachments can be used as a cache which can be restored if an attachment id
     * reappears after being removed. This parameter defines how many archived records are retained.
     * Records are deleted once the number of items exceeds this value.
     */
    private val archivedCacheLimit: Long = 100,
    /**
     * Throttles remote sync operations triggering
     */
    private val syncThrottleDuration: Duration = 1.seconds,
    /**
     * Creates a list of subdirectories in the {attachmentDirectoryName} directory
     */
    private val subdirectories: List<String>? = null,
    /**
     * Should attachments be downloaded
     */
    private val downloadAttachments: Boolean = true,
    /**
     * Logging interface used for all log operations
     */
    public val logger: Logger = Logger,
) {
    public companion object {
        public const val DEFAULT_TABLE_NAME: String = "attachments"
        public const val DEFAULT_ATTACHMENTS_DIRECTORY_NAME: String = "attachments"
    }

    /**
     * Service which provides access to attachment records.
     * Use this to:
     *  - Query all current attachment records
     *  - Create new attachment records for upload/download
     */
    public val attachmentsService: AttachmentService =
        AttachmentService(
            db,
            attachmentsQueueTableName,
            logger,
            maxArchivedCount = archivedCacheLimit,
        )

    /**
     * Syncing service for this attachment queue.
     * This processes attachment records and performs relevant upload, download and delete
     * operations.
     */
    private val syncingService: SyncingService =
        SyncingService(
            remoteStorage,
            localStorage,
            attachmentsService,
            ::getLocalUri,
            errorHandler,
            logger,
            syncThrottleDuration,
        )

    private var syncStatusJob: Job? = null
    private val mutex = Mutex()

    public var closed: Boolean = false

    /**
     * Initialize the attachment queue by
     * 1. Creating attachments directory
     * 2. Adding watches for uploads, downloads, and deletes
     * 3. Adding trigger to run uploads, downloads, and deletes when device is online after being offline
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun startSync(): Unit =
        runWrappedSuspending {
            mutex.withLock {
                if (closed) {
                    throw Exception("Attachment queue has been closed")
                }
                // Ensure the directory where attachments are downloaded, exists
                localStorage.makeDir(getStorageDirectory())

                subdirectories?.forEach { subdirectory ->
                    localStorage.makeDir(Path(getStorageDirectory(), subdirectory).toString())
                }

                val scope = CoroutineScope(Dispatchers.IO)

                syncingService.startPeriodicSync(syncInterval)

                // Listen for connectivity changes
                syncStatusJob =
                    scope.launch {
                        val statusJob =
                            launch {
                                var previousConnected = db.currentStatus.connected
                                db.currentStatus.asFlow().collect { status ->
                                    if (!previousConnected && status.connected) {
                                        syncingService.triggerSync()
                                    }
                                    previousConnected = status.connected
                                }
                            }

                        val watchJob =
                            launch {
                                // Watch local attachment relationships and sync the attachment records
                                watchAttachments().collect { items ->
                                    processWatchedAttachments(items)
                                }
                            }

                        statusJob.join()
                        watchJob.join()
                    }
            }
        }

    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun close(): Unit =
        runWrappedSuspending {
            mutex.withLock {
                if (closed) {
                    return@runWrappedSuspending
                }

                syncStatusJob?.cancel()
                syncStatusJob?.join()
                syncingService.close()

                closed = true
            }
        }

    /**
     * Creates a watcher for the current state of local attachments
     * ```kotlin
     *      public fun watchAttachments(): Flow<List<WatchedAttachmentItem>> =
     *         db.watch(
     *             sql =
     *                 """
     *                 SELECT
     *                     photo_id as id
     *                 FROM
     *                     checklists
     *                 WHERE
     *                     photo_id IS NOT NULL
     *                 """,
     *         ) { cursor ->
     *             WatchedAttachmentItem(
     *                 id = cursor.getString("id"),
     *                 fileExtension = "jpg",
     *             )
     *         }
     * ```
     */
    @Throws(PowerSyncException::class)
    public abstract fun watchAttachments(): Flow<List<WatchedAttachmentItem>>

    /**
     * Resolves the filename for new attachment items.
     * A new attachment from [watchAttachments] might not include a filename.
     * Concatenates the attachment ID an extension by default.
     * This method can be overriden for custom behaviour.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public open suspend fun resolveNewAttachmentFilename(
        attachmentId: String,
        fileExtension: String?,
    ): String = "$attachmentId.$fileExtension"

    /**
     * Processes attachment items returned from [watchAttachments].
     * The default implementation assets the items returned from [watchAttachments] as the definitive
     * state for local attachments.
     *
     * Records currently in the attachment queue which are not present in the items are deleted from
     * the queue.
     *
     * Received items which are not currently in the attachment queue are assumed scheduled for
     * download. This requires that locally created attachments should be created with [saveFile]
     * before assigning the attachment ID to the relevant watched tables.
     *
     * This method can be overriden for custom behaviour.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public open suspend fun processWatchedAttachments(items: List<WatchedAttachmentItem>): Unit =
        runWrappedSuspending {
            /**
             * Need to get all the attachments which are tracked in the DB.
             * We might need to restore an archived attachment.
             */
            val currentAttachments = attachmentsService.getAttachments()
            val attachmentUpdates = mutableListOf<Attachment>()

            for (item in items) {
                val existingQueueItem = currentAttachments.find { it.id == item.id }

                if (existingQueueItem == null) {
                    if (!downloadAttachments) {
                        continue
                    }
                    // This item should be added to the queue
                    // This item is assumed to be coming from an upstream sync
                    // Locally created new items should be persisted using [saveFile] before
                    // this point.
                    val filename =
                        resolveNewAttachmentFilename(
                            attachmentId = item.id,
                            fileExtension = item.fileExtension,
                        )

                    attachmentUpdates.add(
                        Attachment(
                            id = item.id,
                            filename = filename,
                            state = AttachmentState.QUEUED_DOWNLOAD.ordinal,
                        ),
                    )
                } else if
                    (existingQueueItem.state == AttachmentState.ARCHIVED.ordinal) {
                    // The attachment is present again. Need to queue it for sync.
                    // We might be able to optimize this in future
                    if (existingQueueItem.hasSynced == 1) {
                        // No remote action required, we can restore the record (avoids deletion)
                        attachmentUpdates.add(
                            existingQueueItem.copy(state = AttachmentState.SYNCED.ordinal),
                        )
                    } else {
                        /**
                         * The localURI should be set if the record was meant to be downloaded
                         * and has been synced. If it's missing and hasSynced is false then
                         * it must be an upload operation
                         */
                        attachmentUpdates.add(
                            existingQueueItem.copy(
                                state =
                                    if (existingQueueItem.localUri == null) {
                                        AttachmentState.QUEUED_DOWNLOAD.ordinal
                                    } else {
                                        AttachmentState.QUEUED_UPLOAD.ordinal
                                    },
                            ),
                        )
                    }
                }
            }

            /**
             * Archive any items not specified in the watched items except for items pending delete.
             */
            currentAttachments
                .filter {
                    it.state != AttachmentState.QUEUED_DELETE.ordinal &&
                        null == items.find { update -> update.id == it.id }
                }.forEach {
                    attachmentUpdates.add(it.copy(state = AttachmentState.ARCHIVED.ordinal))
                }

            attachmentsService.saveAttachments(attachmentUpdates)
        }

    /**
     * A function which creates a new attachment locally. This new attachment is queued for upload
     * after creation.
     *
     * The filename is resolved using [resolveNewAttachmentFilename].
     *
     * A [updateHook] is provided which should be used when assigning relationships to the newly
     * created attachment. This hook is executed in the same writeTransaction which creates the
     * attachment record.
     *
     * This method can be overriden for custom behaviour.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public open suspend fun saveFile(
        data: Flow<ByteArray>,
        mediaType: String,
        fileExtension: String?,
        updateHook: ((context: ConnectionContext, attachment: Attachment) -> Unit)? = null,
    ): Attachment =
        runWrappedSuspending {
            val id = db.get("SELECT uuid()") { it.getString(0)!! }
            val filename =
                resolveNewAttachmentFilename(attachmentId = id, fileExtension = fileExtension)
            val localUri = getLocalUri(filename)

            // write the file to the filesystem
            val fileSize = localStorage.saveFile(localUri, data)

            /**
             * Starts a write transaction. The attachment record and relevant local relationship
             * assignment should happen in the same transaction.
             */
            db.writeTransaction { tx ->
                val attachment =  Attachment(
                    id = id,
                    filename = filename,
                    size = fileSize,
                    mediaType = mediaType,
                    state = AttachmentState.QUEUED_UPLOAD.ordinal,
                    localUri = localUri,
                )

                /**
                 * Allow consumers to set relationships to this attachment id
                  */
                updateHook?.invoke(tx, attachment)

                return@writeTransaction attachmentsService.upsertAttachment(
                    attachment,
                    tx,
                )
            }
        }

    /**
     * A function which creates an attachment delete operation locally. This operation is queued
     * for delete.
     * The default implementation assumes the attachment record already exists locally. An exception
     * is thrown if the record does not exist locally.
     * This method can be overriden for custom behaviour.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public open suspend fun deleteFile(attachmentId: String,
                                       updateHook: ((context: ConnectionContext, attachment: Attachment) -> Unit)? = null,
                                       ): Attachment =
        runWrappedSuspending {
            val attachment =
                attachmentsService.getAttachment(attachmentId)
                    ?: throw Exception("Attachment record with id $attachmentId was not found.")

            db.writeTransaction { tx ->
                updateHook?.invoke(tx, attachment)
                return@writeTransaction attachmentsService.upsertAttachment(
                    attachment.copy(state = AttachmentState.QUEUED_DELETE.ordinal),
                    tx,
                )
            }
        }

    /**
     * Returns the local file path for the given filename, used to store in the database.
     * Example: filename: "attachment-1.jpg" returns "attachments/attachment-1.jpg"
     */
    public open fun getLocalFilePathSuffix(filename: String): String = Path(attachmentDirectoryName, filename).toString()

    /**
     * Returns the directory where attachments are stored on the device, used to make dir
     * Example: "/data/user/0/com.yourdomain.app/files/attachments/"
     */
    public open fun getStorageDirectory(): String {
        val userStorageDirectory = localStorage.getUserStorageDirectory()
        return Path(userStorageDirectory, attachmentDirectoryName).toString()
    }

    /**
     * Return users storage directory with the attachmentPath use to load the file.
     * Example: filePath: "attachments/attachment-1.jpg" returns "/data/user/0/com.yourdomain.app/files/attachments/attachment-1.jpg"
     */
    public open fun getLocalUri(filename: String): String {
        val storageDirectory = getStorageDirectory()
        return Path(storageDirectory, filename).toString()
    }

    /**
     * Removes all archived items
     */
    public suspend fun expireCache() {
        var done: Boolean
        do {
            done = syncingService.deleteArchivedAttachments()
        } while (!done)
    }

    /**
     * Clears the attachment queue and deletes all attachment files
     */
    public suspend fun clearQueue() {
        attachmentsService.clearQueue()
        // Remove the attachments directory
        localStorage.rmDir(getStorageDirectory())
    }
}
