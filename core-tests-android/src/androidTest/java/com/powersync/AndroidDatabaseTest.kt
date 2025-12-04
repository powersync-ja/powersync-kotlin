package com.powersync

import com.powersync.testutils.IntegrationTestHelpers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidDatabaseTest {
    private val helpers = IntegrationTestHelpers(
        InstrumentationRegistry.getInstrumentation().targetContext
    )

    @Before
    fun setupDatabase() {
        helpers.setup()
    }

    @After
    fun tearDown() {
        helpers.tearDown()
    }

    @Test
    fun testLinksPowerSync() = helpers.testLinksPowerSync()

    @Test
    fun testTableUpdates() = helpers.testTableUpdates()

    @Test
    fun testConcurrentReads() = helpers.testConcurrentReads()

    @Test
    fun transactionReads() = helpers.transactionReads()

    @Test
    fun openDBWithDirectory() = helpers.openDBWithDirectory()

    @Test
    fun readConnectionsReadOnly() = helpers.readConnectionsReadOnly()

    @Test
    fun canUseTempStore() = helpers.canUseTempStore()

    @Test
    fun testEncryptedDatabase() = helpers.testEncryptedDatabase()
}
