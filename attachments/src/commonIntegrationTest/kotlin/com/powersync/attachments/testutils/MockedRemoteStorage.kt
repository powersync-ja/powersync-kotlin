package com.powersync.attachments.testutils

import com.powersync.attachments.RemoteStorageAdapter

class MockedRemoteStorage : RemoteStorageAdapter {
    override suspend fun uploadFile(
        filename: String,
        file: ByteArray,
        mediaType: String,
    ) {
    }

    override suspend fun downloadFile(filename: String): ByteArray = ByteArray(1)

    override suspend fun deleteFile(filename: String) {
    }
}
