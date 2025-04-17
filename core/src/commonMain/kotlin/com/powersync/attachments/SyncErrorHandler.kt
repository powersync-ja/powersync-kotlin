package com.powersync.attachments

/**
 * Interface for handling errors during attachment operations.
 * Implementations determine whether failed operations should be retried.
 * Attachment records are archived if an operation fails and should not be retried.
 */
public interface SyncErrorHandler {
    /**
     * Determines whether the provided attachment download operation should be retried.
     *
     * @param attachment The attachment involved in the failed download operation.
     * @param exception The exception that caused the download failure.
     * @return `true` if the download operation should be retried, `false` otherwise.
     */
    public suspend fun onDownloadError(
        attachment: Attachment,
        exception: Exception,
    ): Boolean

    /**
     * Determines whether the provided attachment upload operation should be retried.
     *
     * @param attachment The attachment involved in the failed upload operation.
     * @param exception The exception that caused the upload failure.
     * @return `true` if the upload operation should be retried, `false` otherwise.
     */
    public suspend fun onUploadError(
        attachment: Attachment,
        exception: Exception,
    ): Boolean

    /**
     * Determines whether the provided attachment delete operation should be retried.
     *
     * @param attachment The attachment involved in the failed delete operation.
     * @param exception The exception that caused the delete failure.
     * @return `true` if the delete operation should be retried, `false` otherwise.
     */
    public suspend fun onDeleteError(
        attachment: Attachment,
        exception: Exception,
    ): Boolean
}
