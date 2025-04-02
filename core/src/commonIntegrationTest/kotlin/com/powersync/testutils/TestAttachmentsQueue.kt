package com.powersync.testutils

import com.powersync.PowerSyncDatabase
import com.powersync.attachments.AbstractAttachmentQueue
import com.powersync.attachments.RemoteStorageAdapter
import com.powersync.attachments.WatchedAttachmentItem
import com.powersync.db.getString
import kotlinx.coroutines.flow.Flow

internal class TestAttachmentsQueue(
    db: PowerSyncDatabase,
    remoteStorage: RemoteStorageAdapter,
    attachmentDirectoryName: String,
) : AbstractAttachmentQueue(db, remoteStorage, attachmentDirectoryName = attachmentDirectoryName) {
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
}
