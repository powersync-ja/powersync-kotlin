package com.powersync.attachments

import co.touchlab.kermit.ExperimentalKermitApi
import com.powersync.PowerSyncDatabase
import com.powersync.attachments.testutils.UserRow
import com.powersync.db.schema.Schema
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalKermitApi::class)
class AttachmentsTest {
    private lateinit var database: PowerSyncDatabase

    private fun openDB() =
        PowerSyncDatabase(
            factory = com.powersync.attachments.testutils.factory,
            schema = Schema(UserRow.table),
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
}
