package com.powersync.androidexample

import com.powersync.PowerSyncDatabase
import com.powersync.attachments.AbstractAttachmentQueue
import com.powersync.attachments.RemoteStorageAdapter
import com.powersync.attachments.WatchedAttachmentItem
import com.powersync.db.getString
import kotlinx.coroutines.flow.Flow

class AttachmentsQueue(db: PowerSyncDatabase,
                       remoteStorage: RemoteStorageAdapter
) : AbstractAttachmentQueue(db, remoteStorage, archivedCacheLimit = 0) {
    override fun watchAttachments(): Flow<List<WatchedAttachmentItem>> {
        return db.watch(
            "SELECT photo_id from todos WHERE photo_id IS NOT NULL"
        ) {
            WatchedAttachmentItem(
                id = it.getString("photo_id"),
                fileExtension = "jpg"
            )
        }
    }
}