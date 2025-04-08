package com.powersync.attachments

/**
 * Handles attachment operation errors.
 * The handlers here specify if the corresponding operations should be retried.
 * Attachment records are archived if an operation failed and should not be retried.
 */
public interface SyncErrorHandler {
    /**
     * @returns if the provided attachment download operation should be retried
     */
    public suspend fun onDownloadError(
        attachment: Attachment,
        exception: Exception,
    ): Boolean

    /**
     * @returns if the provided attachment upload operation should be retried
     */
    public suspend fun onUploadError(
        attachment: Attachment,
        exception: Exception,
    ): Boolean

    /**
     * @returns if the provided attachment delete operation should be retried
     */
    public suspend fun onDeleteError(
        attachment: Attachment,
        exception: Exception,
    ): Boolean
}
