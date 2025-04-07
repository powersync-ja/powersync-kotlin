package com.powersync.attachments.storage

import com.powersync.attachments.LocalStorage
import com.powersync.db.runWrappedSuspending
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.core.remaining
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * Storage adapter for local storage using the KotlinX IO library
 */
public class IOLocalStorageAdapter : LocalStorage {
    public override suspend fun saveFile(
        filePath: String,
        data: Flow<ByteArray>,
    ): Long =
        runWrappedSuspending {
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
        }

    public override suspend fun readFile(
        filePath: String,
        mediaType: String?,
    ): Flow<ByteArray> =
        flow {
            SystemFileSystem.source(Path(filePath)).use { source ->
                source.buffered().use { bufferedSource ->
                    var remaining = 0L
                    val bufferSize = 8192L
                    do {
                        bufferedSource.request(bufferSize)
                        remaining = bufferedSource.remaining
                        emit(bufferedSource.readBytes(remaining.toInt()))
                    } while (remaining > 0)
                }
            }
        }.flowOn(Dispatchers.IO)

    public override suspend fun deleteFile(filePath: String): Unit =
        runWrappedSuspending {
            withContext(Dispatchers.IO) {
                SystemFileSystem.delete(Path(filePath))
            }
        }

    public override suspend fun fileExists(filePath: String): Boolean =
        runWrappedSuspending {
            withContext(Dispatchers.IO) {
                SystemFileSystem.exists(Path(filePath))
            }
        }

    public override suspend fun makeDir(path: String): Unit =
        runWrappedSuspending {
            withContext(Dispatchers.IO) {
                SystemFileSystem.createDirectories(Path(path))
            }
        }

    public override suspend fun rmDir(path: String): Unit =
        runWrappedSuspending {
            withContext(Dispatchers.IO) {
                for (item in SystemFileSystem.list(Path(path))) {
                    // Can't delete directories with files in them. Need to go down the file tree
                    // and clear the directory.
                    val meta = SystemFileSystem.metadataOrNull(item)
                    if (meta?.isDirectory == true) {
                        rmDir(item.toString())
                    } else if (meta?.isRegularFile == true) {
                        SystemFileSystem.delete(item)
                    }
                }
            }
        }

    public override suspend fun copyFile(
        sourcePath: String,
        targetPath: String,
    ): Unit =
        runWrappedSuspending {
            withContext(Dispatchers.IO) {
                SystemFileSystem.source(Path(sourcePath)).use { source ->
                    SystemFileSystem.sink(Path(targetPath)).use { sink ->
                        source.buffered().transferTo(sink.buffered())
                    }
                }
            }
        }
}
