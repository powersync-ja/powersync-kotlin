package com.powersync.attachments

import co.touchlab.kermit.Logger
import com.powersync.PowerSyncDatabase
import com.powersync.PowerSyncException
import com.powersync.attachments.sync.SyncingService
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
import kotlin.time.Duration.Companion.minutes

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
    private val db: PowerSyncDatabase,
    /**
     * Adapter which interfaces with the remote storage backend
     */
    private val remoteStorage: RemoteStorageAdapter,
    /**
     * Provides access to local filesystem storage methods
     */
    private val localStorage: LocalStorageAdapter = IOLocalStorageAdapter(),
    /**
     * Directory where attachment files will be written to disk
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
    private val errorHandler: SyncErrorHandler?,
    /**
     * Periodic interval to trigger attachment sync operations
     */
    private val syncInterval: Duration = 5.minutes,
    /**
     * Creates a list of subdirectories in the {attachmentDirectoryName} directory
     */
    private val subdirectories: List<String>? = null,
    /**
     * Logging interface used for all log operations
     */
    private val logger: Logger = Logger,
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
        AttachmentService(db, attachmentsQueueTableName, logger)

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
    public suspend fun start(): Unit =
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

                // Start watching for changes
                val scope = CoroutineScope(Dispatchers.IO)

                syncingService.startPeriodicSync(syncInterval)

                // Listen for connectivity changes
                syncStatusJob =
                    scope.launch {
                        scope.launch {
                            db.currentStatus.asFlow().collect { status ->
                                if (status.connected) {
                                    syncingService.triggerSync()
                                }
                            }
                        }

                        scope.launch {
                            // Watch local attachment relationships and sync the attachment records
                            watchAttachments().collect { items ->
                                processWatchedAttachments(items)
                            }
                        }
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
    public suspend fun resolveNewAttachmentFilename(
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
    public suspend fun processWatchedAttachments(items: List<WatchedAttachmentItem>): Unit =
        runWrappedSuspending {
            val currentAttachments = attachmentsService.getAttachments()
            val attachmentUpdates = mutableListOf<Attachment>()

            for (item in items) {
                val existingQueueItem = currentAttachments.find { it.id == item.id }

                if (existingQueueItem == null) {
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
                }
            }

            // Remove any items not specified in the items
            currentAttachments
                .filter { null == items.find { update -> update.id == it.id } }
                .forEach {
                    attachmentUpdates.add(it.copy(state = AttachmentState.ARCHIVED.ordinal))
                }
        }

    /**
     * A function which creates a new attachment locally. This new attachment is queued for upload
     * after creation.
     * The relevant attachment file should be persisted to disk before calling this method.
     * The default implementation assumes the attachment file has been written to the path
     *  ```kotlin
     *      val path = getLocalFilePathSuffix(
     *          resolveNewAttachmentFilename(
     *              attachmentId = attachmentId,
     *              fileExtension = fileExtension,
     *      ))
     *  )
     *  ```
     *  This method can be overriden for custom behaviour.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun saveFile(
        attachmentId: String,
        size: Long,
        mediaType: String,
        fileExtension: String?,
    ): Attachment =
        runWrappedSuspending {
            val filename =
                resolveNewAttachmentFilename(
                    attachmentId = attachmentId,
                    fileExtension = fileExtension,
                )

            return@runWrappedSuspending attachmentsService.saveAttachment(
                Attachment(
                    id = attachmentId,
                    filename = filename,
                    size = size,
                    mediaType = mediaType,
                    state = AttachmentState.QUEUED_UPLOAD.ordinal,
                    localUri = getLocalFilePathSuffix(filename).toString(),
                ),
            )
        }

    /**
     * A function which creates an attachment delete operation locally. This operation is queued
     * for delete after creating.
     * The default implementation assumes the attachment record already exists locally. An exception
     * is thrown if the record does not exist locally.
     * This method can be overriden for custom behaviour.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun deleteFile(attachmentId: String): Attachment =
        runWrappedSuspending {
            val attachment =
                attachmentsService.getAttachment(attachmentId)
                    ?: throw Exception("Attachment record with id $attachmentId was not found.")

            return@runWrappedSuspending attachmentsService.saveAttachment(
                attachment.copy(state = AttachmentState.QUEUED_DELETE.ordinal),
            )
        }

    /**
     * Returns the local file path for the given filename, used to store in the database.
     * Example: filename: "attachment-1.jpg" returns "attachments/attachment-1.jpg"
     */
    public fun getLocalFilePathSuffix(filename: String): String = Path(attachmentDirectoryName, filename).toString()

    /**
     * Returns the directory where attachments are stored on the device, used to make dir
     * Example: "/data/user/0/com.yourdomain.app/files/attachments/"
     */
    public fun getStorageDirectory(): String {
        val userStorageDirectory = localStorage.getUserStorageDirectory()
        return Path(userStorageDirectory, attachmentDirectoryName).toString()
    }

    /**
     * Return users storage directory with the attachmentPath use to load the file.
     * Example: filePath: "attachments/attachment-1.jpg" returns "/data/user/0/com.yourdomain.app/files/attachments/attachment-1.jpg"
     */
    public fun getLocalUri(filePath: String): String {
        val storageDirectory = getStorageDirectory()
        return Path(storageDirectory, filePath).toString()
    }
}
