package com.powersync.attachments

import com.powersync.attachments.storage.AbstractLocalStorageAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

/**
 * Storage adapter for local storage using the KotlinX IO library
 */
public class IOLocalStorageAdapter : AbstractLocalStorageAdapter() {
    public override suspend fun saveFile(
        filePath: String,
        data: ByteArray,
    ): Unit =
        withContext(Dispatchers.IO) {
            SystemFileSystem.sink(Path(filePath)).use {
                // Copy to a buffer in order to write
                val buffer = Buffer()
                buffer.write(data)
                it.write(buffer, buffer.size)
                it.flush()
            }
        }

    public override suspend fun readFile(
        filePath: String,
        mediaType: String?,
    ): ByteArray =
        withContext(Dispatchers.IO) {
            SystemFileSystem.source(Path(filePath)).use {
                it.buffered().readByteArray()
            }
        }

    public override suspend fun deleteFile(filePath: String): Unit =
        withContext(Dispatchers.IO) {
            SystemFileSystem.delete(Path(filePath))
        }

    public override suspend fun fileExists(filePath: String): Boolean =
        withContext(Dispatchers.IO) {
            SystemFileSystem.exists(Path(filePath))
        }

    public override suspend fun makeDir(filePath: String): Unit =
        withContext(Dispatchers.IO) {
            SystemFileSystem.createDirectories(Path(filePath))
        }

    public override suspend fun copyFile(
        sourcePath: String,
        targetPath: String,
    ): Unit =
        withContext(Dispatchers.IO) {
            SystemFileSystem.source(Path(sourcePath)).use { source ->
                SystemFileSystem.sink(Path(targetPath)).use { sink ->
                    source.buffered().transferTo(sink.buffered())
                }
            }
        }
}
