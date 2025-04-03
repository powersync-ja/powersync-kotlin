package com.powersync.testutils

import com.powersync.attachments.Attachment
import com.powersync.attachments.RemoteStorageAdapter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MockedRemoteStorage : RemoteStorageAdapter {
    override suspend fun uploadFile(
        fileData: Flow<ByteArray>,
        attachment: Attachment,
    ) {
        // No op
    }

    override suspend fun downloadFile(attachment: Attachment): Flow<ByteArray> =
        flow {
            emit(ByteArray(1))
        }

    override suspend fun deleteFile(attachment: Attachment) {
        // No op
    }
}
