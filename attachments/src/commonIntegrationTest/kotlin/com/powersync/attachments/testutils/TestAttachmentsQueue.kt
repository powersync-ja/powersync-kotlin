package com.powersync.attachments.testutils

import com.powersync.PowerSyncDatabase
import com.powersync.attachments.AbstractAttachmentQueue
import com.powersync.attachments.RemoteStorageAdapter
import com.powersync.attachments.WatchedAttachmentItem
import com.powersync.db.getString
import kotlinx.coroutines.flow.Flow

internal class TestAttachmentsQueue(
    db: PowerSyncDatabase,
    remoteStorage: RemoteStorageAdapter,
) : AbstractAttachmentQueue(db, remoteStorage) {
    override fun watchAttachments(): Flow<List<WatchedAttachmentItem>> =
        db.watch(
            sql =
                """
                SELECT
                    id,
                    photo_id
                FROM
                    users
                WHERE
                    photo_id IS NOT NULL
                """,
        ) {
            WatchedAttachmentItem(id = it.getString("id"), fileExtension = "jpg")
        }
}
