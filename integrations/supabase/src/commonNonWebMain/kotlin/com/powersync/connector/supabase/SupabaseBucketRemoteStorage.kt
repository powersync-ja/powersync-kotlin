package com.powersync.connector.supabase

import com.powersync.attachments.Attachment
import com.powersync.attachments.RemoteStorage
import io.github.jan.supabase.storage.BucketApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Implementation of [RemoteStorage] that uses Supabase as the backend storage provider.
 *
 * @property bucket The Supabase storage bucket used for attachments.
 */
public class SupabaseBucketRemoteStorage(
    public val bucket: BucketApi,
) : RemoteStorage {
    /**
     * Uploads a file to the Supabase storage bucket.
     *
     * @param fileData A [Flow] of [ByteArray] representing the file data to be uploaded.
     * @param attachment The [Attachment] metadata associated with the file.
     * @throws IllegalStateException If the attachment size is not specified.
     */
    override suspend fun uploadFile(
        fileData: Flow<ByteArray>,
        attachment: Attachment,
    ) {
        val byteSize =
            attachment.size?.toInt() ?: error("Cannot upload a file with no byte size specified")
        // Supabase wants a single ByteArray
        val buffer = ByteArray(byteSize)
        var position = 0
        fileData.collect {
            it.copyInto(buffer, destinationOffset = position)
            position += it.size
        }

        bucket.upload(attachment.filename, buffer)
    }

    /**
     * Downloads a file from the Supabase storage bucket.
     *
     * @param attachment The [Attachment] record associated with the file to be downloaded.
     * @return A [Flow] of [ByteArray] representing the file data.
     */
    override suspend fun downloadFile(attachment: Attachment): Flow<ByteArray> = flowOf(bucket.downloadAuthenticated(attachment.filename))

    /**
     * Deletes a file from the Supabase storage bucket.
     *
     * @param attachment The [Attachment] record associated with the file to be deleted.
     */
    override suspend fun deleteFile(attachment: Attachment) {
        bucket.delete(attachment.filename)
    }
}
