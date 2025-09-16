package com.powersync

import app.cash.turbine.turbineScope
import co.touchlab.kermit.ExperimentalKermitApi
import com.powersync.attachments.Attachment
import com.powersync.attachments.AttachmentQueue
import com.powersync.attachments.AttachmentState
import com.powersync.attachments.RemoteStorage
import com.powersync.attachments.SyncErrorHandler
import com.powersync.attachments.WatchedAttachmentItem
import com.powersync.attachments.createAttachmentsTable
import com.powersync.db.getString
import com.powersync.db.schema.Schema
import com.powersync.db.schema.Table
import com.powersync.testutils.ActiveDatabaseTest
import com.powersync.testutils.MockedRemoteStorage
import com.powersync.testutils.UserRow
import com.powersync.testutils.databaseTest
import com.powersync.testutils.getTempDir
import com.powersync.testutils.waitFor
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.ArgMatchersScope
import dev.mokkery.matcher.any
import dev.mokkery.matcher.matching
import dev.mokkery.mock
import dev.mokkery.spy
import dev.mokkery.verifySuspend
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.io.files.Path
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalKermitApi::class)
class AttachmentsTest {
    fun watchAttachments(database: PowerSyncDatabase) =
        database.watch(
            // language=SQL
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
            WatchedAttachmentItem(
                id = it.getString("photo_id"),
                fileExtension = "jpg",
            )
        }

    private fun ActiveDatabaseTest.watchAttachmentsTable(): Flow<List<Attachment>> =
        database
            .watch("SELECT * FROM attachments -- test") {
                Attachment.fromCursor(it)
            }
            // Because tests run on slow machines, it's possible for a schedule like the following
            // to happen:
            //  1. the attachment is initially saved with QUEUED_DOWNLOAD, triggering a query.
            //  2. the attachment is downloaded fast, but the query flow is paused.
            //  3. only now is the query scheduled by 1 actually running, reporting SYNCED.
            //  4. we delete the attachment.
            //  5. thanks to 2, the query runs again, again reporting SYNCED.
            //  6. Our test now fails because the second event should be ARCHIVED.
            .distinctUntilChanged()
            .onEach { logger.i { "attachments table results: $it" } }

    suspend fun updateSchema(db: PowerSyncDatabase) {
        db.updateSchema(
            Schema(
                tables =
                    listOf<Table>(
                        UserRow.table,
                        createAttachmentsTable("attachments"),
                    ),
            ),
        )
    }

    fun getAttachmentsDir() = Path(getTempDir(), "attachments").toString()

    @Test
    fun testAttachmentDownload() =
        databaseTest {
            turbineScope {
                updateSchema(database)

                val remote = spy<RemoteStorage>(MockedRemoteStorage())

                // Monitor the attachments table for testing
                val attachmentQuery = watchAttachmentsTable().testIn(this)

                val queue =
                    AttachmentQueue(
                        db = database,
                        remoteStorage = remote,
                        attachmentsDirectory = getAttachmentsDir(),
                        watchAttachments = { watchAttachments(database) },
                        /**
                         * Sets the cache limit to zero for this test. Archived records will
                         * immediately be deleted.
                         */
                        archivedCacheLimit = 0,
                        logger = logger,
                    )

                doOnCleanup {
                    attachmentQuery.cancel()
                    queue.stopSyncing()
                    queue.clearQueue()
                    queue.close()
                }

                queue.startSync()

                val result = attachmentQuery.awaitItem()

                // There should not be any attachment records here
                result.size shouldBe 0

                // Create a user with a photo_id specified.
                // This code did not save an attachment before assigning a photo_id.
                // This is equivalent to requiring an attachment download
                database.execute(
                    // language=SQL
                    """
                        INSERT INTO
                            users (id, name, email, photo_id)
                        VALUES
                            (uuid(), "steven", "steven@journeyapps.com", uuid())
                    """,
                )

                var attachmentRecord = attachmentQuery.awaitItem().first()
                attachmentRecord shouldNotBe null

                /**
                 * The timing of the watched query resolving might differ slightly.
                 * We might get a watched query result where the attachment is QUEUED_DOWNLOAD
                 * or we could get the result once it has been DOWNLOADED.
                 * We should assert that the download happens eventually.
                 */

                if (attachmentRecord.state == AttachmentState.QUEUED_DOWNLOAD) {
                    // Wait for the download to be triggered
                    attachmentRecord = attachmentQuery.awaitItem().first()
                }

                attachmentRecord.state shouldBe AttachmentState.SYNCED

                // A download should have been attempted for this file
                verifySuspend { remote.downloadFile(attachmentMatcher(attachmentRecord)) }

                // A file should now exist
                val localUri = attachmentRecord.localUri!!
                queue.localStorage.fileExists(localUri) shouldBe true

                // Now clear the user's photo_id. The attachment should be archived
                database.execute(
                    // language=SQL
                    """
                            UPDATE
                                users
                            SET 
                                photo_id = NULL
                         """,
                )

                /**
                 * The timing of the watched query resolving might differ slightly.
                 * We might get a watched query result where the attachment is ARCHIVED
                 * or we could get the result once it has been deleted.
                 * The file should be deleted eventually
                 */
                var nextRecord: Attachment? = attachmentQuery.awaitItem().first()
                if (nextRecord?.state == AttachmentState.ARCHIVED) {
                    nextRecord = attachmentQuery.awaitItem().getOrNull(0)
                }

                // The record should have been deleted
                nextRecord shouldBe null

                // The file should have been deleted from storage
                val exists = queue.localStorage.fileExists(localUri)
                exists shouldBe false

                attachmentQuery.cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun testAttachmentUpload() =
        databaseTest {
            turbineScope {
                updateSchema(database)
                val remote = spy<RemoteStorage>(MockedRemoteStorage())

                // Monitor the attachments table for testing
                val attachmentQuery = watchAttachmentsTable().testIn(this)

                val queue =
                    AttachmentQueue(
                        db = database,
                        remoteStorage = remote,
                        attachmentsDirectory = getAttachmentsDir(),
                        watchAttachments = { watchAttachments(database) },
                        /**
                         * Sets the cache limit to zero for this test. Archived records will
                         * immediately be deleted.
                         */
                        archivedCacheLimit = 0,
                        logger = logger,
                    )

                doOnCleanup {
                    attachmentQuery.cancel()
                    queue.stopSyncing()
                    queue.clearQueue()
                    queue.close()
                }

                queue.startSync()

                val result = attachmentQuery.awaitItem()

                // There should not be any attachment records here
                result.size shouldBe 0

                /**
                 * Creates an attachment given a flow of bytes (the file data) then assigns this to
                 * a newly created user.
                 */
                val record =
                    queue.saveFile(
                        data = flowOf(ByteArray(1)),
                        mediaType = "image/jpg",
                        fileExtension = "jpg",
                    ) { tx, attachment ->
                        // Set the photo_id of a new user to the attachment id
                        tx.execute(
                            // language=SQL
                            """
                            INSERT INTO
                                users (id, name, email, photo_id)
                            VALUES
                                (uuid(), "steven", "steven@steven.com", ?)
                            """,
                            listOf(attachment.id),
                        )
                    }

                var attachmentRecord = attachmentQuery.awaitItem().first()
                attachmentRecord shouldNotBe null

                if (attachmentRecord.state == AttachmentState.QUEUED_UPLOAD) {
                    // Wait for it to be synced
                    attachmentRecord = attachmentQuery.awaitItem().first()
                }

                attachmentRecord.state shouldBe AttachmentState.SYNCED

                // A download should have been attempted for this file
                verifySuspend {
                    remote.uploadFile(
                        any(),
                        attachmentMatcher(attachmentRecord),
                    )
                }

                val localUri = attachmentRecord.localUri!!
                queue.localStorage.fileExists(localUri) shouldBe true

                // Now clear the user's photo_id. The attachment should be archived
                database.execute(
                    // language=SQL
                    """
                        UPDATE
                            users
                        SET
                            photo_id = NULL
                         """,
                )

                waitFor {
                    var nextRecord: Attachment? = attachmentQuery.awaitItem().firstOrNull()
                    // The record should have been deleted
                    nextRecord shouldBe null
                }

                // The file should have been deleted from storage
                queue.localStorage.fileExists(localUri) shouldBe false

                attachmentQuery.cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun testAttachmentDelete() =
        databaseTest {
            turbineScope {
                updateSchema(database)
                val remote = spy<RemoteStorage>(MockedRemoteStorage())

                // Monitor the attachments table for testing
                val attachmentQuery = watchAttachmentsTable().testIn(this)

                val queue =
                    AttachmentQueue(
                        db = database,
                        remoteStorage = remote,
                        attachmentsDirectory = getAttachmentsDir(),
                        watchAttachments = { watchAttachments(database) },
                        /**
                         * Sets the cache limit to zero for this test. Archived records will
                         * immediately be deleted.
                         */
                        archivedCacheLimit = 0,
                        syncThrottleDuration = 0.seconds,
                        logger = logger,
                    )

                doOnCleanup {
                    queue.stopSyncing()
                    queue.clearQueue()
                    queue.close()
                    attachmentQuery.cancel()
                }

                queue.startSync()

                val result = attachmentQuery.awaitItem()

                // There should not be any attachment records here
                result.size shouldBe 0

                // Create an attachment (simulates a download)
                database.execute(
                    // language=SQL
                    """
                    INSERT INTO
                        users (id, name, email, photo_id)
                    VALUES
                         (uuid(), "steven", "steven@steven.com", uuid())
                    """,
                )

                // language=SQL
                val attachmentID =
                    database.get("SELECT photo_id FROM users") { it.getString("photo_id") }

                // Wait for the record to be synced (mocked backend will allow it)
                waitFor {
                    val record = attachmentQuery.awaitItem().first()
                    record shouldNotBe null
                    record.state shouldBe AttachmentState.SYNCED
                }

                queue.deleteFile(
                    attachmentId = attachmentID,
                ) { tx, attachment ->
                    tx.execute(
                        // language=SQL
                        """
                        UPDATE 
                            users
                        SET
                            photo_id = NULL
                        WHERE 
                            photo_id = ?
                        """.trimIndent(),
                        listOf(attachment.id),
                    )
                }

                waitFor {
                    // Record should be deleted
                    val record = attachmentQuery.awaitItem().firstOrNull()
                    record shouldBe null
                }

                // A delete should have been attempted for this file
                verifySuspend {
                    remote.deleteFile(
                        any(),
                    )
                }

                attachmentQuery.cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun testAttachmentCachedDownload() =
        databaseTest {
            turbineScope {
                updateSchema(database)

                val remote = spy<RemoteStorage>(MockedRemoteStorage())

                // Monitor the attachments table for testing
                val attachmentQuery = watchAttachmentsTable().testIn(this)

                val queue =
                    AttachmentQueue(
                        db = database,
                        remoteStorage = remote,
                        attachmentsDirectory = getAttachmentsDir(),
                        watchAttachments = { watchAttachments(database) },
                        /**
                         * Keep some items in the cache
                         */
                        archivedCacheLimit = 10,
                        logger = logger,
                    )

                doOnCleanup {
                    queue.stopSyncing()
                    queue.clearQueue()
                    queue.close()
                    attachmentQuery.cancel()
                }

                queue.startSync()

                val result = attachmentQuery.awaitItem()

                // There should not be any attachment records here
                result.size shouldBe 0

                // Create a user with a photo_id specified.
                // This code did not save an attachment before assigning a photo_id.
                // This is equivalent to requiring an attachment download
                database.execute(
                    // language=SQL
                    """
                        INSERT INTO
                            users (id, name, email, photo_id)
                        VALUES
                            (uuid(), "steven", "steven@journeyapps.com", uuid())
                    """,
                )

                var attachmentRecord = attachmentQuery.awaitItem().first()
                attachmentRecord shouldNotBe null

                /**
                 * The timing of the watched query resolving might differ slightly.
                 * We might get a watched query result where the attachment is QUEUED_DOWNLOAD
                 * or we could get the result once it has been DOWNLOADED.
                 * We should assert that the download happens eventually.
                 */

                if (attachmentRecord.state == AttachmentState.QUEUED_DOWNLOAD) {
                    // Wait for the download to be triggered
                    attachmentRecord = attachmentQuery.awaitItem().first()
                }

                attachmentRecord.state shouldBe AttachmentState.SYNCED

                // A download should have been attempted for this file
                verifySuspend { remote.downloadFile(attachmentMatcher(attachmentRecord)) }

                // A file should now exist
                val localUri = attachmentRecord.localUri!!
                queue.localStorage.fileExists(localUri) shouldBe true

                // Now clear the user's photo_id. The attachment should be archived
                database.execute(
                    // language=SQL
                    """
                        UPDATE
                            users
                        SET 
                            photo_id = NULL
                         """,
                )

                attachmentRecord = attachmentQuery.awaitItem().first()
                attachmentRecord.state shouldBe AttachmentState.ARCHIVED

                // Now if we set the photo_id, the archived record should be restored
                database.execute(
                    // language=SQL
                    """
                        UPDATE
                            users
                        SET 
                            photo_id = ?
                         """,
                    listOf(attachmentRecord.id),
                )

                attachmentRecord = attachmentQuery.awaitItem().first()
                attachmentRecord.state shouldBe AttachmentState.SYNCED

                attachmentQuery.cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun testSkipFailedDownload() =
        databaseTest {
            turbineScope {
                updateSchema(database)

                val remote =
                    mock<RemoteStorage> {
                        everySuspend { downloadFile(any()) } throws (Exception("Test error"))
                    }

                // Monitor the attachments table for testing
                val attachmentQuery = watchAttachmentsTable().testIn(this)

                val queue =
                    AttachmentQueue(
                        db = database,
                        remoteStorage = remote,
                        attachmentsDirectory = getAttachmentsDir(),
                        watchAttachments = { watchAttachments(database) },
                        errorHandler =
                            object : SyncErrorHandler {
                                override suspend fun onDownloadError(
                                    attachment: Attachment,
                                    exception: Exception,
                                ): Boolean = false

                                override suspend fun onUploadError(
                                    attachment: Attachment,
                                    exception: Exception,
                                ): Boolean = false

                                override suspend fun onDeleteError(
                                    attachment: Attachment,
                                    exception: Exception,
                                ): Boolean = false
                            },
                        logger = logger,
                    )
                doOnCleanup {
                    queue.stopSyncing()
                    queue.clearQueue()
                    queue.close()
                    attachmentQuery.cancel()
                }

                queue.startSync()

                val result = attachmentQuery.awaitItem()

                // There should not be any attachment records here
                result.size shouldBe 0

                // Create a user with a photo_id specified.
                // This code did not save an attachment before assigning a photo_id.
                // This is equivalent to requiring an attachment download
                database.execute(
                    """
                        INSERT INTO
                            users (id, name, email, photo_id)
                        VALUES
                            (uuid(), "steven", "steven@journeyapps.com", uuid())
                    """,
                )

                // Depending on when the query updates, we'll see the attachment as queued for
                // download or archived.
                var attachmentRecord = attachmentQuery.awaitItem().first()
                attachmentRecord shouldNotBe null

                if (attachmentRecord.state == AttachmentState.QUEUED_DOWNLOAD) {
                    attachmentRecord = attachmentQuery.awaitItem().first()
                }

                // The download should fail. We don't specify a retry. The record should be archived.
                attachmentRecord.state shouldBe AttachmentState.ARCHIVED

                attachmentQuery.cancelAndIgnoreRemainingEvents()
            }
        }
}

fun ArgMatchersScope.attachmentMatcher(attachment: Attachment): Attachment =
    matching(toString = { "attachment($attachment)" }, predicate = { it.id == attachment.id })
