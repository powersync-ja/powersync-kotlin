package com.powersync.attachments

import com.powersync.PowerSyncException
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.cancellation.CancellationException

/**
 * Storage adapter for local storage
 */
public interface LocalStorageAdapter {
    /**
     * Saves a source of data bytes to a path.
     * @returns the bytesize of the file
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun saveFile(
        filePath: String,
        data: Flow<ByteArray>,
    ): Long

    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun readFile(
        filePath: String,
        mediaType: String? = null,
    ): Flow<ByteArray>

    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun deleteFile(filePath: String): Unit

    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun fileExists(filePath: String): Boolean

    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun makeDir(path: String): Unit

    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun rmDir(path: String): Unit

    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun copyFile(
        sourcePath: String,
        targetPath: String,
    ): Unit

    public fun getUserStorageDirectory(): String
}
