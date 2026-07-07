package com.powersync.attachments

import com.powersync.PowerSyncException
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.cancellation.CancellationException

/**
 * Provides access to local storage on a device.
 */
public interface LocalStorage {
    /**
     * Saves a source of data bytes to a path.
     *
     * @param filePath The path where the file will be saved.
     * @param data A [Flow] of [ByteArray] representing the file data.
     * @return The byte size of the saved file.
     * @throws PowerSyncException If an error occurs during the save operation.
     * @throws CancellationException If the operation is cancelled.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun saveFile(
        filePath: String,
        data: Flow<ByteArray>,
    ): Long

    /**
     * Reads a file from the specified path.
     *
     * @param filePath The path of the file to read.
     * @param mediaType Optional media type of the file.
     * @return A [Flow] of [ByteArray] representing the file data.
     * @throws PowerSyncException If an error occurs during the read operation.
     * @throws CancellationException If the operation is cancelled.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun readFile(
        filePath: String,
        mediaType: String? = null,
    ): Flow<ByteArray>

    /**
     * Deletes a file at the specified path.
     *
     * @param filePath The path of the file to delete.
     * @throws PowerSyncException If an error occurs during the delete operation.
     * @throws CancellationException If the operation is cancelled.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun deleteFile(filePath: String): Unit

    /**
     * Checks if a file exists at the specified path.
     *
     * @param filePath The path of the file to check.
     * @return `true` if the file exists, `false` otherwise.
     * @throws PowerSyncException If an error occurs during the check.
     * @throws CancellationException If the operation is cancelled.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun fileExists(filePath: String): Boolean

    /**
     * Creates a directory at the specified path.
     *
     * @param path The path of the directory to create.
     * @throws PowerSyncException If an error occurs during the directory creation.
     * @throws CancellationException If the operation is cancelled.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun makeDir(path: String): Unit

    /**
     * Removes a directory at the specified path.
     *
     * @param path The path of the directory to remove.
     * @throws PowerSyncException If an error occurs during the directory removal.
     * @throws CancellationException If the operation is cancelled.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun rmDir(path: String): Unit

    /**
     * Copies a file from the source path to the target path.
     *
     * @param sourcePath The path of the source file.
     * @param targetPath The path where the file will be copied to.
     * @throws PowerSyncException If an error occurs during the copy operation.
     * @throws CancellationException If the operation is cancelled.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun copyFile(
        sourcePath: String,
        targetPath: String,
    ): Unit
}
