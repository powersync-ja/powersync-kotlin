# PowerSync Attachments

A [PowerSync](https://powersync.com) library to manage attachments in Kotlin Multiplatform apps.

This package is included in the PowerSync Core module.

## Usage

An `AttachmentQueue` is used to manage and sync attachments in your app. The attachments' state are
stored in a local only attachments table.

### Key Assumptions

- Each attachment should be identifiable by a unique ID.
- Attachments are immutable.
- Relational data should contain a foreign key column that references the attachment ID.
- Relational data should reflect the holistic state of attachments at any given time. An existing
  local attachment will deleted locally if no relational data references it.

### Example

In this example, the user captures photos when checklist items are completed as part of an
inspection workflow.

The schema for the `checklist` table:

```kotlin
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

| Option | Description           | Default       |
|--------|-----------------------|---------------|
| `name` | The name of the table | `attachments` |

The default columns in `AttachmentTable`:

| Column Name  | Type      | Description                                                                                                        |
|--------------|-----------|--------------------------------------------------------------------------------------------------------------------|
| `id`         | `TEXT`    | The ID of the attachment record                                                                                    |
| `filename`   | `TEXT`    | The filename of the attachment                                                                                     |
| `media_type` | `TEXT`    | The media type of the attachment                                                                                   |
| `state`      | `INTEGER` | The state of the attachment, one of `AttachmentState` enum values                                                  |
| `timestamp`  | `INTEGER` | The timestamp of last update to the attachment record                                                              |
| `size`       | `INTEGER` | The size of the attachment in bytes                                                                                |
| `has_synced` | `INTEGER` | Internal tracker which tracks if the attachment has ever been synced. This is used for caching/archiving purposes. |
| `meta_data`  | `TEXT`    | Any extra meta data for the attachment. JSON is usually a good choice.                                             |

### Steps to implement

1. Create an instance of `AttachmentQueue` from `com.powersync.attachments`. This class provides
   default syncing utilities and implements a default sync strategy. This class is open and can be
   overridden for custom functionality.

```kotlin

val queue = AttachmentQueue(
    db = db,
    attachmentDirectory = attachmentDirectory,
    remoteStorage = SupabaseRemoteStorage(supabase),
    watchedAttachments = db.watch(
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
)
```

* The `attachmentDirectory`, specifies where local attachment
  files should be stored. This directory needs to be provided to the constructor. On Android
  `"${applicationContext.filesDir.canonicalPath}/attachments"` is a good choice.
* The `remoteStorage` is responsible for connecting to the attachments backend. See the
  `RemoteStorageAdapter` interface
  definition [here](https://github.com/powersync-ja/powersync-kotlin/blob/main/core/src/commonMain/kotlin/com.powersync/attachments/RemoteStorageAdapter.ts).
* `watchAttachments` is a `Flow` of `WatchedAttachmentItem`.
  The `WatchedAttachmentItem`s represent the attachments which should be present in the
  application. We recommend using `PowerSync`'s `watch` query as shown above. In this example we
  provide the `fileExtension` for all photos. This information could also be
  obtained from the query if necessary.

2. Implement a `RemoteStorageAdapter` which interfaces with a remote storage provider. This will be
   used for downloading, uploading and deleting attachments.

```kotlin
val remote = object : RemoteStorage() {
    override suspend fun uploadFile(
        fileData: Flow<ByteArray>,
        attachment: Attachment,
    ) {
        TODO("Make a request to the backend")
    }

    override suspend fun downloadFile(attachment: Attachment): Flow<ByteArray> {
        TODO("Make a request to the backend")
    }

    override suspend fun deleteFile(attachment: Attachment) {
        TODO("Make a request to the backend")
    }

}

```

3. Call `startSync()` to start syncing attachments.

```kotlin
queue.startSync()
```

4. Finally, to create an attachment and add it to the queue, call `saveFile()`. This method will
   save the file to the local storage, create an attachment record which queues the file for upload
   to the remote storage and allows assigning the newly created attachment ID to a checklist item.

```kotlin
queue.saveFile(
    // The attachment's data flow, this is just an example
    data = flowOf(ByteArray(1)),
    mediaType = "image/jpg",
    fileExtension = "jpg",
) { tx, attachment ->
    /**
     * This lambda is invoked in the same transaction which creates the attachment record.
     * Assignments of the newly created photo_id should be done in the same transaction for maximum efficiency.
     */
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

#### Handling Errors

The attachment queue automatically retries failed sync operations. Retries continue indefinitely
until success. A `SyncErrorHanlder` can be provided to the `AttachmentQueue` constructor. This
handler provides methods which are invoked on a remote sync exception. The handler can return a
Boolean which indicates if the attachment sync should be retried or archived.

```kotlin
val errorHandler = object : SyncErrorHandler {
    override suspend fun onDownloadError(
        attachment: Attachment,
        exception: Exception
    ): Boolean {
        TODO("Return if the attachment sync should be retried")
    }

    override suspend fun onUploadError(
        attachment: Attachment,
        exception: Exception
    ): Boolean {
        TODO("Return if the attachment sync should be retried")
    }

    override suspend fun onDeleteError(
        attachment: Attachment,
        exception: Exception
    ): Boolean {
        TODO("Return if the attachment sync should be retried")
    }

}

// Pass the handler to the queue constructor
val queue = AttachmentQueue(
//    ...,
    errorHandler = errorHandler,
//    ...
)
```

# Implementation details

## Attachment State

The `AttachmentQueue` class manages attachments in your app by tracking their state.

The state of an attachment can be one of the following:

| State             | Description                                                                   |
|-------------------|-------------------------------------------------------------------------------|
| `QUEUED_UPLOAD`   | The attachment has been queued for upload to the cloud storage                |
| `QUEUED_DELETE`   | The attachment has been queued for delete in the cloud storage (and locally)  |
| `QUEUED_DOWNLOAD` | The attachment has been queued for download from the cloud storage            |
| `SYNCED`          | The attachment has been synced                                                |
| `ARCHIVED`        | The attachment has been orphaned, i.e. the associated record has been deleted |

## Syncing attachments

The `AttachmentQueue` sets a watched query on the `attachments` table, for record in the
`QUEUED_UPLOAD`, `QUEUED_DELETE` and `QUEUED_DOWNLOAD` state. An event loop triggers calls to the
remote storage for these operations.

In addition to watching for changes, the `AttachmentQueue` also triggers a sync periodically.
This will retry any failed uploads/downloads, in particular after the app was offline. By default,
this is every 30 seconds, but can be configured by setting `syncInterval` in the
`AttachmentQueue` constructor options, or disabled by setting the interval to `0`.

### Watching State

The `watchedAttachments` flow provided to the `AttachmentQueue` constructor is used to reconcile the
local Attachment state. Each emission of the flow should represent the current attachment state. The
updated state is constantly compared to the current queue state. Items are queued based off the
difference.

* A new watched item which is not present in the current queue is treated as an upstream Attachment
  creation which needs to be downloaded.
    * An attachment record is create using the provided watched item. The filename will be inferred
      using a default filename resolver if it has not been provided in the watched item.
    * The syncing service will attempt to download the attachment from the remote storage.
    * The attachment will be saved to the local filesystem. The `local_uri` on the attachment record
      will be updated.
    * The attachment state will be updated to `SYNCED`
* Local attachments are archived if the watched state no longer includes the item. Archived items
  are cached and can be restored if the watched state includes them in future. The number of cached
  items is defined by the `archivedCacheLimit` parameter in the `AttachmentQueue` constructor. Items
  are deleted once the cache limit is reached.

### Uploading

The `saveFile` method provides a simple method for creating attachments which should be uploaded to
the backend. This method accepts the raw file content and meta data. This function:

* Persists the attachment to the local filesystem
* Creates an attachment record linked to the local attachment file.
* Queues the attachment for upload.
* Allows assigning the attachment to relational data.
    * It's important to assign the attachment to relational data since this data is constantly
      watched and should always represent the attachment queue state. Failure to assign the
      attachment could result in a failed upload. The attachment record will be archived.

The sync process after calling `saveFile` is:

- An `AttachmentRecord` is created or updated with a state of `QUEUED_UPLOAD`.
- The `RemoteStorage` `uploadFile` function is called with the `Attachment` record.
- The `AttachmentQueue` picks this up and upon successful upload to the remote storage, sets the
  state to `SYNCED`.
- If the upload is not successful, the record remains in `QUEUED_UPLOAD` state and uploading will be
  retried when syncing triggers again. Retries can be stopped by providing an `errorHandler`.

### Downloading

Attachments are schedules for download when the `watchedAttachments` flow emits a
`WatchedAttachmentItem` which is not present in the queue.

- An `AttachmentRecord` is created or updated with `QUEUED_DOWNLOAD` state.
- The `RemoteStorage` `downloadFile` function is called with the attachment record.
- The received data is persisted to the local filesystem.
- If this is successful, update the `AttachmentRecord` state to `SYNCED`.
- If any of these fail, the download is retried in the next sync trigger.

### Deleting attachments

Local attachments are archived and deleted (locally) if the `watchedAttachments` flow no longer
references them. Archived attachments are deleted locally after cache invalidation.

In some cases users might want to explicitly delete an attachment in the backend. The `deleteFile`
function provides a mechanism for this. This function:

* Deletes the attachment on the local filesystem
* Updates the record to the `QUEUED_DELETE` state
* Allows removing assignments to relational data.
    * It's important to unassign the attachment from relational data since this data is constantly
      watched and should always represent the attachment queue state. Failure to unassign the
      attachment could result in a failed delete.

### Expire Cache

When PowerSync removes a record, as a result of coming back online or conflict resolution for
instance:

- Any associated `AttachmentRecord` is orphaned.
- On the next sync trigger, the `AttachmentQueue` sets all records that are orphaned to `ARCHIVED`
  state.
- By default, the `AttachmentQueue` only keeps the last `100` attachment records and then expires
  the rest.
- In some cases these records (attachment ids) might be restored. An archived attachment will be
  restored if it is still in the cache. This can be configured by setting `cacheLimit` in the
  `AttachmentQueue` constructor options.
