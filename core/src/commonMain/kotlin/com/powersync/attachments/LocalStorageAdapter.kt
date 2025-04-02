package com.powersync.attachments

import kotlinx.coroutines.flow.Flow

/**
 * Storage adapter for local storage
 */
public interface LocalStorageAdapter {
    /**
     * Saves a source of data bytes to a path.
     * @returns the bytesize of the file
     */
    public suspend fun saveFile(
        filePath: String,
        data: Flow<ByteArray>,
    ): Long

    public suspend fun readFile(
        filePath: String,
        mediaType: String? = null,
    ): Flow<ByteArray>

    public suspend fun deleteFile(filePath: String): Unit

    public suspend fun fileExists(filePath: String): Boolean

    public suspend fun makeDir(filePath: String): Unit

    public suspend fun copyFile(
        sourcePath: String,
        targetPath: String,
    ): Unit

    public fun getUserStorageDirectory(): String
}
