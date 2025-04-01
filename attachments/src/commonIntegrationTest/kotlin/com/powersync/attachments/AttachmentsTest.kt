package com.powersync.attachments

import app.cash.turbine.turbineScope
import co.touchlab.kermit.ExperimentalKermitApi
import com.powersync.PowerSyncDatabase
import com.powersync.attachments.testutils.MockedRemoteStorage
import com.powersync.attachments.testutils.TestAttachmentsQueue
import com.powersync.attachments.testutils.UserRow
import com.powersync.db.schema.Schema
import dev.mokkery.spy
import dev.mokkery.verifySuspend
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalKermitApi::class)
class AttachmentsTest {
    private lateinit var database: PowerSyncDatabase

    private fun openDB() =
        PowerSyncDatabase(
            factory = com.powersync.attachments.testutils.factory,
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
        com.powersync.attachments.testutils
            .cleanup("testdb")
    }

    @Test
    fun testLinksPowerSync() =
        runTest {
            database.get("SELECT powersync_rs_version();") { it.getString(0)!! }
        }

    @Test
    fun testAttachmentDownload() =
        runTest {
            turbineScope {
                val remote = spy<RemoteStorageAdapter>(MockedRemoteStorage())

                val queue =
                    TestAttachmentsQueue(db = database, remoteStorage = remote)

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

//                 The watched query should cause the attachment record to be pending download
                val afterInsert = attachmentQuery.awaitItem()

                assertEquals(
                    expected = 1,
                    actual = afterInsert.size,
                    "Should contain 1 attachment record",
                )

                val item = afterInsert.first()
                assertEquals(expected = AttachmentState.QUEUED_DOWNLOAD.ordinal, item.state)

                // A download should have been attempted for this file
                verifySuspend { remote.downloadFile(item.filename) }

                attachmentQuery.cancel()
                queue.close()
            }
        }
}
