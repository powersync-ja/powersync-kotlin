package com.powersync

import app.cash.turbine.turbineScope
import co.touchlab.kermit.ExperimentalKermitApi
import com.powersync.attachments.Attachment
import com.powersync.attachments.AttachmentState
import com.powersync.attachments.RemoteStorageAdapter
import com.powersync.attachments.createAttachmentsTable
import com.powersync.db.schema.Schema
import com.powersync.testutils.MockedRemoteStorage
import com.powersync.testutils.TestAttachmentsQueue
import com.powersync.testutils.UserRow
import dev.mokkery.matcher.any
import dev.mokkery.spy
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalKermitApi::class)
class AttachmentsTest {
    private lateinit var database: PowerSyncDatabase

    private fun openDB() =
        PowerSyncDatabase(
            factory = com.powersync.testutils.factory,
            schema = Schema(UserRow.table, createAttachmentsTable("attachments")),
            dbFilename = "testdb",
        )

    @BeforeTest
    fun setupDatabase() {
        database = openDB()

        runBlocking {
            database.disconnectAndClear(true)
        }
    }

    @AfterTest
    fun tearDown() {
        runBlocking {
            if (!database.closed) {
                database.disconnectAndClear(true)
                database.close()
            }
        }
        com.powersync.testutils
            .cleanup("testdb")
    }

    @Test
    fun testAttachmentDownload() =
        runTest(timeout = 5.minutes) {
            turbineScope(timeout = 5.minutes) {
                val remote = spy<RemoteStorageAdapter>(MockedRemoteStorage())

                val queue =
                    TestAttachmentsQueue(
                        db = database,
                        remoteStorage = remote,
                        /**
                         * Sets the cache limit to zero for this test. Archived records will
                         * immediately be deleted.
                         */
                        archivedCacheLimit = 0,
                    )

                queue.start()

                // Monitor the attachments table for testing
                val attachmentQuery =
                    database
                        .watch("SELECT * FROM attachments") { Attachment.fromCursor(it) }
                        .testIn(this)

                val result = attachmentQuery.awaitItem()

                // There should not be any attachment records here
                assertEquals(expected = 0, actual = result.size)

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

                var attachmentRecord = attachmentQuery.awaitItem().first()
                assertNotNull(
                    attachmentRecord,
                    """
                    An attachment record should be created after creating a user with a photo_id
                    "
                    """.trimIndent(),
                )

                /**
                 * The timing of the watched query resolving might differ slightly.
                 * We might get a watched query result where the attachment is QUEUED_DOWNLOAD
                 * or we could get the result once it has been DOWNLOADED.
                 * We should assert that the download happens eventually.
                 */

                if (attachmentRecord.state == AttachmentState.QUEUED_DOWNLOAD.ordinal) {
                    // Wait for the download to be triggered
                    attachmentRecord = attachmentQuery.awaitItem().first()
                }

                assertEquals(expected = AttachmentState.SYNCED.ordinal, attachmentRecord.state)

                // A download should have been attempted for this file
                verifySuspend { remote.downloadFile(attachmentRecord) }

                // A file should now exist
                val localUri = attachmentRecord.localUri!!
                assertTrue { queue.localStorage.fileExists(localUri) }

                // Now clear the user's photo_id. The attachment should be archived
                database.execute(
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
                if (nextRecord?.state == AttachmentState.ARCHIVED.ordinal) {
                    nextRecord = attachmentQuery.awaitItem().getOrNull(0)
                }

                // The record should have been deleted
                assertNull(nextRecord)

                // The file should have been deleted from storage
                assertEquals(expected = false, actual = queue.localStorage.fileExists(localUri))

                attachmentQuery.cancel()
                queue.close()
            }
        }

    @Test
    fun testAttachmentUpload() =
        runTest {
            turbineScope {
                val remote = spy<RemoteStorageAdapter>(MockedRemoteStorage())

                val queue =
                    TestAttachmentsQueue(
                        db = database,
                        remoteStorage = remote,
                        /**
                         * Sets the cache limit to zero for this test. Archived records will
                         * immediately be deleted.
                         */
                        archivedCacheLimit = 0,
                    )

                queue.start()

                // Monitor the attachments table for testing
                val attachmentQuery =
                    database
                        .watch("SELECT * FROM attachments") { Attachment.fromCursor(it) }
                        .testIn(this)

                val result = attachmentQuery.awaitItem()

                // There should not be any attachment records here
                assertEquals(expected = 0, actual = result.size)

                /**
                 * Creates an attachment given a flow of bytes (the file data) then assigns this to
                 * a newly created user.
                 */
                queue.saveFile(
                    data = flowOf(ByteArray(1)),
                    mediaType = "image/jpg",
                    fileExtension = "jpg",
                ) { tx, attachment ->
                    // Set the photo_id of a new user to the attachment id
                    tx.execute(
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
                assertNotNull(attachmentRecord)

                if (attachmentRecord.state == AttachmentState.QUEUED_UPLOAD.ordinal) {
                    // Wait for it to be synced
                    attachmentRecord = attachmentQuery.awaitItem().first()
                }

                assertEquals(
                    expected = AttachmentState.SYNCED.ordinal,
                    attachmentRecord.state,
                )

                // A download should have been attempted for this file
                verifySuspend {
                    remote.uploadFile(
                        any(),
                        attachmentRecord,
                    )
                }

                // A file should now exist
                val localUri = attachmentRecord.localUri!!
                assertTrue { queue.localStorage.fileExists(localUri) }

                // Now clear the user's photo_id. The attachment should be archived
                database.execute(
                    """
                            UPDATE
                                users
                            SET
                                photo_id = NULL
                         """,
                )

                var nextRecord: Attachment? = attachmentQuery.awaitItem().first()
                if (nextRecord?.state == AttachmentState.ARCHIVED.ordinal) {
                    nextRecord = attachmentQuery.awaitItem().getOrNull(0)
                }

                // The record should have been deleted
                assertNull(nextRecord)

                // The file should have been deleted from storage
                assertEquals(expected = false, actual = queue.localStorage.fileExists(localUri))

                attachmentQuery.cancel()
                queue.close()
            }
        }

    @Test
    fun testAttachmentCachedDownload() =
        runTest {
            turbineScope {
                val remote = spy<RemoteStorageAdapter>(MockedRemoteStorage())

                val queue =
                    TestAttachmentsQueue(
                        db = database,
                        remoteStorage = remote,
                        /**
                         * Keep some items in the cache
                         */
                        archivedCacheLimit = 10,
                    )

                queue.start()

                // Monitor the attachments table for testing
                val attachmentQuery =
                    database
                        .watch("SELECT * FROM attachments") { Attachment.fromCursor(it) }
                        .testIn(this)

                val result = attachmentQuery.awaitItem()

                // There should not be any attachment records here
                assertEquals(expected = 0, actual = result.size)

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

                var attachmentRecord = attachmentQuery.awaitItem().first()
                assertNotNull(
                    attachmentRecord,
                    """
                    An attachment record should be created after creating a user with a photo_id
                    "
                    """.trimIndent(),
                )

                /**
                 * The timing of the watched query resolving might differ slightly.
                 * We might get a watched query result where the attachment is QUEUED_DOWNLOAD
                 * or we could get the result once it has been DOWNLOADED.
                 * We should assert that the download happens eventually.
                 */

                if (attachmentRecord.state == AttachmentState.QUEUED_DOWNLOAD.ordinal) {
                    // Wait for the download to be triggered
                    attachmentRecord = attachmentQuery.awaitItem().first()
                }

                assertEquals(expected = AttachmentState.SYNCED.ordinal, attachmentRecord.state)

                // A download should have been attempted for this file
                verifySuspend { remote.downloadFile(attachmentRecord) }

                // A file should now exist
                val localUri = attachmentRecord.localUri!!
                assertTrue { queue.localStorage.fileExists(localUri) }

                // Now clear the user's photo_id. The attachment should be archived
                database.execute(
                    """
                            UPDATE
                                users
                            SET 
                                photo_id = NULL
                         """,
                )

                attachmentRecord = attachmentQuery.awaitItem().first()
                assertEquals(
                    expected = AttachmentState.ARCHIVED.ordinal,
                    actual = attachmentRecord.state,
                )

                // Now if we set the photo_id, the archived record should be restored
                database.execute(
                    """
                            UPDATE
                                users
                            SET 
                                photo_id = ?
                         """,
                    listOf(attachmentRecord.id),
                )

                attachmentRecord = attachmentQuery.awaitItem().first()
                assertEquals(
                    expected = AttachmentState.SYNCED.ordinal,
                    actual = attachmentRecord.state,
                )

                attachmentQuery.cancel()
                queue.close()
            }
        }
}
