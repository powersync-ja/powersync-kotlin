package com.powersync.attachments.sync

import co.touchlab.kermit.Logger
import com.powersync.PowerSyncException
import com.powersync.attachments.Attachment
import com.powersync.attachments.AttachmentService
import com.powersync.attachments.AttachmentState
import com.powersync.attachments.LocalStorageAdapter
import com.powersync.attachments.RemoteStorageAdapter
import com.powersync.attachments.SyncErrorHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
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

/**
 * Service used to sync attachments between local and remote storage
 */
internal class SyncingService(
    private val remoteStorage: RemoteStorageAdapter,
    private val localStorage: LocalStorageAdapter,
    private val attachmentsService: AttachmentService,
    private val getLocalUri: suspend (String) -> String,
    private val errorHandler: SyncErrorHandler?,
    private val logger: Logger,
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val mutex = Mutex()
    private val syncJob: Job
    private var periodicSyncTrigger: Job? = null

    /**
     * Used to trigger the sync process either manually or periodically
     */
    private val syncTriggerFlow = MutableSharedFlow<Unit>(replay = 0)

    init {
        syncJob =
            scope.launch {
                merge(
                    // Handles manual triggers for sync events
                    syncTriggerFlow.asSharedFlow(),
                    // Triggers the sync process whenever an underlaying change to the
                    // attachments table happens
                    attachmentsService
                        .watchActiveAttachments(),
                )
                    // We only use these flows to trigger the process. We can skip multiple invocations
                    // while we are processing. We will always process on the trailing edge.
                    // This buffer operation should automatically be applied to all merged sources.
                    .buffer(1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
                    .collect {
                        /**
                         * Gets and performs the operations for active attachments which are
                         * pending download, upload, or delete.
                         */
                        try {
                            val attachments = attachmentsService.getActiveAttachments()
                            // Performs pending operations and updates attachment states
                            handleSync(attachments)

                            // Cleanup archived attachments
                            deleteArchivedAttachments()
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

    /**
     * Periodically sync attachments and delete archived attachments
     */
    suspend fun startPeriodicSync(period: Duration): Unit =
        mutex.withLock {
            periodicSyncTrigger?.cancel()

            periodicSyncTrigger =
                scope.launch {
                    logger.i("Periodically syncing attachments")
                    syncTriggerFlow.emit(Unit)
                    delay(period)
                }
        }

    /**
     * Enqueues a sync operation
     */
    suspend fun triggerSync() {
        syncTriggerFlow.emit(Unit)
    }

    suspend fun close(): Unit =
        mutex.withLock {
            periodicSyncTrigger?.cancel()
            periodicSyncTrigger?.join()
            syncJob.cancel()
            syncJob.join()
        }

    suspend fun deleteArchivedAttachments() =
        attachmentsService.deleteArchivedAttachments { pendingDelete ->
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

    /**
     * Handle downloading, uploading or deleting of attachments
     */
    private suspend fun handleSync(attachments: List<Attachment>) {
        val updatedAttachments = mutableListOf<Attachment>()
        try {
            for (attachment in attachments) {
                when (attachment.state) {
                    AttachmentState.QUEUED_DOWNLOAD.ordinal -> {
                        logger.i("Downloading ${attachment.filename}")
                        updatedAttachments.add(downloadAttachment(attachment))
                    }

                    AttachmentState.QUEUED_UPLOAD.ordinal -> {
                        logger.i("Uploading ${attachment.filename}")
                        updatedAttachments.add(uploadAttachment(attachment))
                    }

                    AttachmentState.QUEUED_DELETE.ordinal -> {
                        logger.i("Deleting ${attachment.filename}")
                        updatedAttachments.add(deleteAttachment(attachment))
                    }
                }
            }

            // Update the state of processed attachments
            attachmentsService.saveAttachments(updatedAttachments)
        } catch (error: Exception) {
            // We retry, on the next invocation, whenever there are errors on this level
            logger.e("Error during sync: ${error.message}")
        }
    }

    /**
     * Upload attachment from local storage to remote storage.
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
            return attachment.copy(state = AttachmentState.SYNCED.ordinal, hasSynced = 1)
        } catch (e: Exception) {
            logger.e("Upload attachment error for attachment $attachment: ${e.message}")
            if (errorHandler != null) {
                val shouldRetry = errorHandler.onUploadError(attachment, e)
                if (!shouldRetry) {
                    logger.i("Attachment with ID ${attachment.id} has been archived")
                    return attachment.copy(state = AttachmentState.ARCHIVED.ordinal)
                }
            }

            // Retry the upload (same state)
            return attachment
        }
    }

    /**
     * Download attachment from remote storage and save it to local storage.
     * Returns the updated state of the attachment.
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
                state = AttachmentState.SYNCED.ordinal,
                hasSynced = 1,
            )
        } catch (e: Exception) {
            if (errorHandler != null) {
                val shouldRetry = errorHandler.onDownloadError(attachment, e)
                if (!shouldRetry) {
                    logger.i("Attachment with ID ${attachment.id} has been archived")
                    return attachment.copy(state = AttachmentState.ARCHIVED.ordinal)
                }
            }

            logger.e("Download attachment error for attachment $attachment: ${e.message}")
            // Return the same state, this will cause a retry
            return attachment
        }
    }

    /**
     * Delete attachment from remote, local storage and then remove it from the queue.
     */
    private suspend fun deleteAttachment(attachment: Attachment): Attachment {
        try {
            remoteStorage.deleteFile(attachment)
            if (attachment.localUri != null) {
                localStorage.deleteFile(attachment.localUri)
            }
            return attachment.copy(state = AttachmentState.ARCHIVED.ordinal)
        } catch (e: Exception) {
            if (errorHandler != null) {
                val shouldRetry = errorHandler.onDeleteError(attachment, e)
                if (!shouldRetry) {
                    logger.i("Attachment with ID ${attachment.id} has been archived")
                    return attachment.copy(state = AttachmentState.ARCHIVED.ordinal)
                }
            }
            // We'll retry this
            logger.e("Error deleting attachment: ${e.message}")
            return attachment
        }
    }
}
