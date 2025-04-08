package com.powersync.attachments

import kotlinx.coroutines.flow.Flow

/**
 * Adapter for interfacing with remote attachment storage.
 */
public interface RemoteStorage {
    /**
     * Upload a file to remote storage
     */
    public suspend fun uploadFile(
        fileData: Flow<ByteArray>,
        attachment: Attachment,
    ): Unit

    /**
     * Download a file from remote storage
     */
    public suspend fun downloadFile(attachment: Attachment): Flow<ByteArray>

    /**
     * Delete a file from remote storage
     */
    public suspend fun deleteFile(attachment: Attachment)
}
