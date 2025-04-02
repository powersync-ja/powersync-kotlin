package com.powersync.testutils

import com.powersync.attachments.RemoteStorageAdapter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MockedRemoteStorage : RemoteStorageAdapter {
    override suspend fun uploadFile(
        filename: String,
        file: Flow<ByteArray>,
        mediaType: String?,
    ) {
        // No op
    }

    override suspend fun downloadFile(filename: String): Flow<ByteArray> =
        flow {
            emit(ByteArray(1))
        }

    override suspend fun deleteFile(filename: String) {
        // No op
    }
}
