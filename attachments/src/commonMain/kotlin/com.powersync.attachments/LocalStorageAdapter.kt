package com.powersync.attachments

/**
 * Storage adapter for local storage
 */
public interface LocalStorageAdapter {
    public suspend fun saveFile(
        filePath: String,
        data: ByteArray,
    ): Unit

    public suspend fun readFile(
        filePath: String,
        mediaType: String? = null,
    ): ByteArray

    public suspend fun deleteFile(filePath: String): Unit

    public suspend fun fileExists(filePath: String): Boolean

    public suspend fun makeDir(filePath: String): Unit

    public suspend fun copyFile(
        sourcePath: String,
        targetPath: String,
    ): Unit

    public fun getUserStorageDirectory(): String
}
