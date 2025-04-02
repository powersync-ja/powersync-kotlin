package com.powersync.attachments

import com.powersync.attachments.storage.AbstractLocalStorageAdapter
import io.ktor.utils.io.core.remaining
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlin.math.min

/**
 * Storage adapter for local storage using the KotlinX IO library
 */
public class IOLocalStorageAdapter : AbstractLocalStorageAdapter() {
    public override suspend fun saveFile(
        filePath: String,
        data: Flow<ByteArray>,
    ): Long =
        withContext(Dispatchers.IO) {
            var totalSize = 0L
            SystemFileSystem.sink(Path(filePath)).use { sink ->
                // Copy to a buffer in order to write
                Buffer().use { buffer ->
                    data.collect { chunk ->
                        // Copy into a buffer in order to sink the chunk
                        buffer.write(chunk, 0)
                        val chunkSize = chunk.size.toLong()
                        totalSize += chunkSize
                        sink.write(buffer, chunkSize)
                    }
                }
                sink.flush()
                return@withContext totalSize
            }
        }

    public override suspend fun readFile(
        filePath: String,
        mediaType: String?,
    ): Flow<ByteArray> =
        flow {
            withContext(Dispatchers.IO) {
                SystemFileSystem.source(Path(filePath)).use { source ->
                    source.buffered().use { bufferedSource ->
                        val bufferSize = 8192L // Read in 8KB chunks
                        while (bufferedSource.remaining > 0) {
                            val byteCount =
                                min(
                                    bufferedSource.remaining,
                                    bufferSize,
                                )
                            val bytesRead = bufferedSource.readByteArray(byteCount.toInt())
                            emit(bytesRead)
                        }
                    }
                }
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
