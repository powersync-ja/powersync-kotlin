package com.powersync.testutils

import com.powersync.PowerSyncDatabase
import com.powersync.attachments.AbstractAttachmentQueue
import com.powersync.attachments.RemoteStorageAdapter
import com.powersync.attachments.SyncErrorHandler
import com.powersync.attachments.WatchedAttachmentItem
import com.powersync.db.getString
import kotlinx.coroutines.flow.Flow

internal class TestAttachmentsQueue(
    db: PowerSyncDatabase,
    remoteStorage: RemoteStorageAdapter,
    archivedCacheLimit: Long,
    errorHandler: SyncErrorHandler? = null,
) : AbstractAttachmentQueue(
        db,
        remoteStorage,
        archivedCacheLimit = archivedCacheLimit,
        errorHandler = errorHandler,
    ) {
    override fun watchAttachments(): Flow<List<WatchedAttachmentItem>> =
        db.watch(
            sql =
                """
                SELECT
                    photo_id
                FROM
                    users
                WHERE
                    photo_id IS NOT NULL
                """,
        ) {
            WatchedAttachmentItem(id = it.getString("photo_id"), fileExtension = "jpg")
        }

    /**
     * For tests this uses a temporary directory. On iOS it uses the user storage directory
     */
    override fun getStorageDirectory(): String = getTempDir() ?: super.getStorageDirectory()
}
