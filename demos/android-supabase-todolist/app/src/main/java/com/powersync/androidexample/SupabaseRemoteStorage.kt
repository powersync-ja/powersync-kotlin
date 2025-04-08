package com.powersync.androidexample

import com.powersync.attachments.Attachment
import com.powersync.attachments.RemoteStorage
import com.powersync.connector.supabase.SupabaseConnector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class SupabaseRemoteStorage(
    val connector: SupabaseConnector,
) : RemoteStorage {
    override suspend fun uploadFile(
        fileData: Flow<ByteArray>,
        attachment: Attachment,
    ) {
        // Supabase wants a single ByteArray
        val buffer = ByteArray(attachment.size!!.toInt())
        var position = 0
        fileData.collect {
            System.arraycopy(it, 0, buffer, position, it.size)
            position += it.size
        }

        connector.storageBucket().upload(attachment.filename, buffer)
    }

    override suspend fun downloadFile(attachment: Attachment): Flow<ByteArray> =
        flowOf(connector.storageBucket().downloadAuthenticated(attachment.filename))

    override suspend fun deleteFile(attachment: Attachment) {
        connector.storageBucket().delete(attachment.filename)
    }
}
