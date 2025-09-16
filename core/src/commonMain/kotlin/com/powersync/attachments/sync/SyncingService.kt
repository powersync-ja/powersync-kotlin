package com.powersync.attachments.sync

import co.touchlab.kermit.Logger
import com.powersync.PowerSyncException
import com.powersync.attachments.Attachment
import com.powersync.attachments.AttachmentContext
import com.powersync.attachments.AttachmentService
import com.powersync.attachments.AttachmentState
import com.powersync.attachments.LocalStorage
import com.powersync.attachments.RemoteStorage
import com.powersync.attachments.SyncErrorHandler
import com.powersync.utils.throttle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Service responsible for syncing attachments between local and remote storage.
 *
 * This service handles downloading, uploading, and deleting attachments, as well as
 * periodically syncing attachment states. It ensures proper lifecycle management
 * of sync operations and provides mechanisms for error handling and retries.
 *
 * The class provides a default implementation for syncing operations, which can be
 * overridden by subclasses to customize behavior as needed.
 *
 * @property remoteStorage The remote storage implementation for handling file operations.
 * @property localStorage The local storage implementation for managing files locally.
 * @property attachmentsService The service for managing attachment states and operations.
 * @property getLocalUri A function to resolve the local URI for a given filename.
 * @property errorHandler Optional error handler for managing sync-related errors.
 * @property logger Logger instance for logging sync operations and errors.
 * @property syncThrottle The minimum duration between consecutive sync operations.
 * @property scope The coroutine scope used for managing sync operations.
 */
public open class SyncingService(
    private val remoteStorage: RemoteStorage,
    private val localStorage: LocalStorage,
    private val attachmentsService: AttachmentService,
    private val getLocalUri: suspend (String) -> String,
    private val errorHandler: SyncErrorHandler?,
    private val logger: Logger,
    private val scope: CoroutineScope,
    private val syncThrottle: Duration = 5.seconds,
) {
    private val mutex = Mutex()
    private var syncJob: Job? = null

    /**
     * Used to trigger the sync process either manually or periodically
     */
    private val syncTriggerFlow = MutableSharedFlow<Unit>(replay = 0)

    /**
     * Starts the syncing process, including periodic and event-driven sync operations.
     *
     * @param period The interval at which periodic sync operations are triggered.
     */
    public suspend fun startSync(period: Duration = 30.seconds): Unit =
        mutex.withLock {
            syncJob?.cancelAndJoin()

            syncJob =
                scope.launch {
                    launch {
                        merge(
                            // Handles manual triggers for sync events
                            syncTriggerFlow.asSharedFlow(),
                            // Triggers the sync process whenever an underlying change to the
                            // attachments table happens
                            attachmentsService
                                .watchActiveAttachments(),
                        )
                            // We only use these flows to trigger the process. We can skip multiple invocations
                            // while we are processing. We will always process on the trailing edge.
                            // This buffer operation should automatically be applied to all merged sources.
                            .buffer(1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
                            .throttle(syncThrottle)
                            .collect {
                                attachmentsService.withContext { context ->
                                    /**
                                     * Gets and performs the operations for active attachments which are
                                     * pending download, upload, or delete.
                                     */
                                    try {
                                        val attachments = context.getActiveAttachments()
                                        logger.v { "Processing active attachments: $attachments" }

                                        // Performs pending operations and updates attachment states
                                        handleSync(attachments, context)

                                        // Cleanup archived attachments
                                        deleteArchivedAttachments(context)
                                    } catch (ex: Exception) {
                                        if (ex is CancellationException) {
                                            throw ex
                                        }
                                        // Rare exceptions caught here will be swallowed and retried on the
                                        // next tick.
                                        logger.e("Caught exception when processing attachments $ex")
                                    }
                                }
                            }
                    }

                    launch {
                        logger.i("Periodically syncing attachments")
                        while (true) {
                            syncTriggerFlow.emit(Unit)
                            delay(period)
                        }
                    }
                }
        }

    /**
     * Enqueues a sync operation
     */
    public suspend fun triggerSync() {
        syncTriggerFlow.emit(Unit)
    }

    /**
     * Stops all ongoing sync operations.
     */
    public suspend fun stopSync(): Unit =
        mutex.withLock {
            syncJob?.cancelAndJoin()
        }

    /**
     * Closes the syncing service, stopping all operations and releasing resources.
     */
    public suspend fun close() {
        stopSync()
    }

    /**
     * Handles syncing operations for a list of attachments, including downloading,
     * uploading, and deleting files based on their states.
     *
     * @param attachments The list of attachments to process.
     * @param context The attachment context used for managing attachment states.
     */
    private suspend fun handleSync(
        attachments: List<Attachment>,
        context: AttachmentContext,
    ) {
        val updatedAttachments = mutableListOf<Attachment>()
        try {
            for (attachment in attachments) {
                when (attachment.state) {
                    AttachmentState.QUEUED_DOWNLOAD -> {
                        logger.i("Downloading ${attachment.filename}")
                        updatedAttachments.add(downloadAttachment(attachment))
                    }

                    AttachmentState.QUEUED_UPLOAD -> {
                        logger.i("Uploading ${attachment.filename}")
                        updatedAttachments.add(uploadAttachment(attachment))
                    }

                    AttachmentState.QUEUED_DELETE -> {
                        logger.i("Deleting ${attachment.filename}")
                        updatedAttachments.add(deleteAttachment(attachment))
                    }

                    AttachmentState.SYNCED -> {}
                    AttachmentState.ARCHIVED -> {}
                }
            }

            // Update the state of processed attachments
            context.saveAttachments(updatedAttachments)
        } catch (error: Exception) {
            // We retry, on the next invocation, whenever there are errors on this level
            logger.e("Error during sync: ${error.message}")
        }
    }

    /**
     * Uploads an attachment from local storage to remote storage.
     *
     * @param attachment The attachment to upload.
     * @return The updated attachment with its new state.
     */
    private suspend fun uploadAttachment(attachment: Attachment): Attachment {
        try {
            if (attachment.localUri == null) {
                throw PowerSyncException(
                    "No localUri for attachment $attachment",
                    cause = Exception("attachment.localUri == null"),
                )
            }

            remoteStorage.uploadFile(
                localStorage.readFile(attachment.localUri),
                attachment,
            )
            logger.i("Uploaded attachment \"${attachment.id}\" to Cloud Storage")
            return attachment.copy(state = AttachmentState.SYNCED, hasSynced = true)
        } catch (e: Exception) {
            logger.e("Upload attachment error for attachment $attachment: ${e.message}")
            if (errorHandler != null) {
                val shouldRetry = errorHandler.onUploadError(attachment, e)
                if (!shouldRetry) {
                    logger.i("Attachment with ID ${attachment.id} has been archived")
                    return attachment.copy(state = AttachmentState.ARCHIVED)
                }
            }

            // Retry the upload (same state)
            return attachment
        }
    }

    /**
     * Downloads an attachment from remote storage and saves it to local storage.
     *
     * @param attachment The attachment to download.
     * @return The updated attachment with its new state.
     */
    private suspend fun downloadAttachment(attachment: Attachment): Attachment {
        /**
         * When downloading an attachment we take the filename and resolve
         * the local_uri where the file will be stored
         */
        val attachmentPath = getLocalUri(attachment.filename)

        try {
            val fileFlow = remoteStorage.downloadFile(attachment)
            localStorage.saveFile(attachmentPath, fileFlow)
            logger.i("Downloaded file \"${attachment.id}\"")

            // The attachment has been downloaded locally
            return attachment.copy(
                localUri = attachmentPath,
                state = AttachmentState.SYNCED,
                hasSynced = true,
            )
        } catch (e: Exception) {
            if (errorHandler != null) {
                val shouldRetry = errorHandler.onDownloadError(attachment, e)
                if (!shouldRetry) {
                    logger.i("Attachment with ID ${attachment.id} has been archived")
                    return attachment.copy(state = AttachmentState.ARCHIVED)
                }
            }

            logger.e("Download attachment error for attachment $attachment: ${e.message}")
            // Return the same state, this will cause a retry
            return attachment
        }
    }

    /**
     * Deletes an attachment from remote and local storage, and removes it from the queue.
     *
     * @param attachment The attachment to delete.
     * @return The updated attachment with its new state.
     */
    private suspend fun deleteAttachment(attachment: Attachment): Attachment {
        try {
            remoteStorage.deleteFile(attachment)
            if (attachment.localUri != null && localStorage.fileExists(attachment.localUri)) {
                localStorage.deleteFile(attachment.localUri)
            }
            return attachment.copy(state = AttachmentState.ARCHIVED)
        } catch (e: Exception) {
            if (errorHandler != null) {
                val shouldRetry = errorHandler.onDeleteError(attachment, e)
                if (!shouldRetry) {
                    logger.i("Attachment with ID ${attachment.id} has been archived")
                    return attachment.copy(state = AttachmentState.ARCHIVED)
                }
            }
            // We'll retry this
            logger.e("Error deleting attachment: ${e.message}")
            return attachment
        }
    }

    /**
     * Deletes archived attachments from local storage.
     *
     * @param context The attachment context used to retrieve and manage archived attachments.
     * @return `true` if all archived attachments were successfully deleted, `false` otherwise.
     */
    public suspend fun deleteArchivedAttachments(context: AttachmentContext): Boolean =
        context.deleteArchivedAttachments { pendingDelete ->
            logger.v { "Deleting archived attachments: $pendingDelete" }

            for (attachment in pendingDelete) {
                if (attachment.localUri == null) {
                    continue
                }
                if (!localStorage.fileExists(attachment.localUri)) {
                    continue
                }
                localStorage.deleteFile(attachment.localUri)
            }
        }
}
