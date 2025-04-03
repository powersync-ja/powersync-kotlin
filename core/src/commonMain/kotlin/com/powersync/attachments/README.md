# PowerSync Attachments

A [PowerSync](https://powersync.com) library to manage attachments in Kotlin Multiplatform apps.

This package is included in the PowerSync Core module.

## Usage

An `AttachmentQueue` is used to manage and sync attachments in your app. The attachments' state are
stored in a local only attachments table.

### Example

In this example, the user captures photos when checklist items are completed as part of an
inspection workflow.

The schema for the `checklist` table:

```kotlin
import com.powersync.attachments.AbstractAttachmentQueue
import com.powersync.attachments.createAttachmentsTable
import com.powersync.db.schema.Schema
import com.powersync.db.schema.Table

val checklists = Table(
    name = "checklists",
    columns =
        listOf(
            Column.text("description"),
            Column.integer("completed"),
            Column.text("photo_id"),
        ),
)

val schema = Schema(
    UserRow.table,
    // Includes the table which stores attachment states
    createAttachmentsTable("attachments")
)
```

The `createAttachmentsTable` function defines the local only attachment state storage table.

An attachments table definition can be created with the following options.

| Option              | Description                                                                     | Default                       |
|---------------------|---------------------------------------------------------------------------------|-------------------------------|
| `name`              | The name of the table                                                           | `attachments`                 |
| `additionalColumns` | An array of addition `Column` objects added to the default columns in the table | See below for default columns |

The default columns in `AttachmentTable`:

| Column Name  | Type      | Description                                                       |
|--------------|-----------|-------------------------------------------------------------------|
| `id`         | `TEXT`    | The ID of the attachment record                                   |
| `filename`   | `TEXT`    | The filename of the attachment                                    |
| `media_type` | `TEXT`    | The media type of the attachment                                  |
| `state`      | `INTEGER` | The state of the attachment, one of `AttachmentState` enum values |
| `timestamp`  | `INTEGER` | The timestamp of last update to the attachment record             |
| `size`       | `INTEGER` | The size of the attachment in bytes                               |

### Steps to implement

1. Create a new `AttachmentQueue` class that extends `AbstractAttachmentQueue` from
   `com.powersync.attachments`.

```kotlin

class AttachmentsQueue(
    db: PowerSyncDatabase,
    remoteStorage: RemoteStorageAdapter,
    attachmentDirectoryName: String,
) : AbstractAttachmentQueue(
    db,
    remoteStorage,
    attachmentDirectoryName = attachmentDirectoryName,
    // See the class definition for more options
) {

    // An example implementation
    override fun watchAttachments(): Flow<List<WatchedAttachmentItem>> =
        db.watch(
            sql =
                """
                SELECT
                    photo_id
                FROM
                    checklists
                WHERE
                    photo_id IS NOT NULL
                """,
        ) {
            WatchedAttachmentItem(id = it.getString("photo_id"), fileExtension = "jpg")
        }
}
```

2. Implement `watchAttachments`, which returns a `Flow` of `WatchedAttachmentItem`.
   The `WatchedAttachmentItem`s represent the attachments which should be present in the
   application. We recommend using `PowerSync`'s `watch` query as shown above. In this example we
   provide the `fileExtension` for all photos. This information could also be
   obtained from the query if necessary.

3. To instantiate an `AttachmentQueue`, one needs to provide an instance of
   `PowerSyncDatabase` from PowerSync and an instance of `RemoteStorageAdapter`. The remote storage
   is responsible for connecting to the attachments backend. See the `RemoteStorageAdapter`
   interface
   definition [here](https://github.com/powersync-ja/powersync-kotlin/blob/main/core/src/commonMain/kotlin/com.powersync/attachments/RemoteStorageAdapter.ts).


4. Implement a `RemoteStorageAdapter` which interfaces with a remote storage provider. This will be
   used for downloading, uploading and deleting attachments.

```kotlin
val remote = object : RemoteStorageAdapter() {
    override suspend fun uploadFile(
        filename: String,
        file: Flow<ByteArray>,
        mediaType: String?
    ) {
        TODO("Make a request to the backend")
    }

    override suspend fun downloadFile(filename: String): Flow<ByteArray> {
        TODO("Make a request to the backend")
    }

    override suspend fun deleteFile(filename: String) {
        TODO("Make a request to the backend")
    }

}

```

5. Instantiate a new `AttachmentQueue` and call `start()` to start syncing attachments.

```kotlin
    val queue =
    AttachmentsQueue(
        db = database,
        remoteStorage = remote,
    )

queue.start()
```

7. Finally, to create an attachment and add it to the queue, call `saveFile()`. This method will
   save the file to the local storage, create an attachment record which queues the file for upload
   to the remote storage and allows assigning the newly created attachment ID to a checklist item.

```kotlin
queue.saveFile(
    // The attachment's data flow
    data = flowOf(ByteArray(1)),
    mediaType = "image/jpg",
    fileExtension = "jpg",
) { tx, attachment ->
    // Set the photo_id of a checklist to the attachment id
    tx.execute(
        """
               UPDATE
                checklists 
               SET
                photo_id = ?
               WHERE 
                id = ?
        """,
        listOf(attachment.id, checklistId),
    )
}
```

# Implementation details

## Attachment State

The `AttachmentQueue` class manages attachments in your app by tracking their state.

The state of an attachment can be one of the following:

| State             | Description                                                                   |
|-------------------|-------------------------------------------------------------------------------|
| `QUEUED_UPLOAD`   | The attachment has been queued for upload to the cloud storage                |
| `QUEUED_DOWNLOAD` | The attachment has been queued for download from the cloud storage            |
| `SYNCED`          | The attachment has been synced                                                |
| `ARCHIVED`        | The attachment has been orphaned, i.e. the associated record has been deleted |

## Syncing attachments

The `AttachmentQueue` sets a watched query on the `attachments` table, for record in the
`QUEUED_UPLOAD` and `QUEUED_DOWNLOAD` state. An event loop triggers calls to the remote storage for
these operations.

In addition to watching for changes, the `AttachmentQueue` also triggers a sync periodically.
This will retry any failed uploads/downloads, in particular after the app was offline.

By default, this is every 30 seconds, but can be configured by setting `syncInterval` in the
`AttachmentQueue` constructor options, or disabled by setting the interval to `0`.

### Uploading

- An `AttachmentRecord` is created or updated with a state of `QUEUED_UPLOAD`.
- The `AttachmentQueue` picks this up and upon successful upload to the remote storage, sets the
  state to
  `SYNCED`.
- If the upload is not successful, the record remains in `QUEUED_UPLOAD` state and uploading will be
  retried when syncing triggers again. Retries can be stopped by providing an `errorHandler`.

### Downloading

- An `AttachmentRecord` is created or updated with `QUEUED_DOWNLOAD` state.
- The watched query from `watchAttachments` adds the attachment `id` into a queue of IDs to download
  and triggers the download process
- The watched query from `watchAttachments` adds the attachment `id` into a queue of IDs to download
  and triggers the download process
- If the photo is not on the device, it is downloaded from cloud storage.
- Writes file to the user's local storage.
- If this is successful, update the `AttachmentRecord` state to `SYNCED`.
- If any of these fail, the download is retried in the next sync trigger.

### Deleting attachments

When an attachment is deleted by a user action or cache expiration:

- Related `AttachmentRecord` is removed from attachments table.
- Local file (if exists) is deleted.
- File on cloud storage is deleted.

### Expire Cache

When PowerSync removes a record, as a result of coming back online or conflict resolution for
instance:

- Any associated `AttachmentRecord` is orphaned.
- On the next sync trigger, the `AttachmentQueue` sets all records that are orphaned to `ARCHIVED`
  state.
- By default, the `AttachmentQueue` only keeps the last `100` attachment records and then expires
  the rest.
- This can be configured by setting `cacheLimit` in the `AttachmentQueue` constructor options.
