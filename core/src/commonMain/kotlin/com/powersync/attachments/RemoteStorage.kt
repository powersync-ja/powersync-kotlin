package com.powersync.attachments

import kotlinx.coroutines.flow.Flow

/**
 * Adapter for interfacing with remote attachment storage.
 */
public interface RemoteStorage {
    /**
     * Uploads a file to remote storage.
     *
     * @param fileData The file data as a flow of byte arrays.
     * @param attachment The attachment record associated with the file.
     */
    public suspend fun uploadFile(
        fileData: Flow<ByteArray>,
        attachment: Attachment,
    ): Unit

    /**
     * Downloads a file from remote storage.
     *
     * @param attachment The attachment record associated with the file.
     * @return A flow of byte arrays representing the file data.
     */
    public suspend fun downloadFile(attachment: Attachment): Flow<ByteArray>

    /**
     * Deletes a file from remote storage.
     *
     * @param attachment The attachment record associated with the file.
     */
    public suspend fun deleteFile(attachment: Attachment)
}
