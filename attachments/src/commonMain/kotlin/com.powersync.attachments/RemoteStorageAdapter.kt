package com.powersync.attachments

/**
 * Adapter for interfacing with remote attachment storage.
 */
public interface RemoteStorageAdapter {
    /**
     * Upload a file to remote storage
     */
    public suspend fun uploadFile(
        filename: String,
        file: ByteArray,
        mediaType: String,
    ): Unit

    /**
     * Download a file from remote storage
     */
    public suspend fun downloadFile(filename: String): ByteArray

    /**
     * Delete a file from remote storage
     */
    public suspend fun deleteFile(filename: String)
}
