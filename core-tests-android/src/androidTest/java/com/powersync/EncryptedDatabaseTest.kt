package com.powersync

import com.powersync.testutils.IntegrationTestHelpers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseTest {
    private val helpers = IntegrationTestHelpers(
        InstrumentationRegistry.getInstrumentation().targetContext
    )
    @Test
    fun testEncryptedDatabase() = helpers.testEncryptedDatabase()
}
