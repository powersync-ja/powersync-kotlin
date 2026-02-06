package com.powersync.encryption.android

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.powersync.encryption.AndroidEncryptedDatabaseFactory
import com.powersync.encryption.Key

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import kotlin.use

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {
    @Test
    fun hasSqlite3Mc() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val factory = AndroidEncryptedDatabaseFactory(appContext, Key.Passphrase("foo"))
        factory.openInMemoryConnection().use { db ->
            db.prepare("PRAGMA cipher").use { stmt ->
                assertTrue(stmt.step())
                assertEquals("chacha20", stmt.getText(0))
            }
        }
    }
}
