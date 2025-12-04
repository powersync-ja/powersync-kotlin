package com.powersync

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.sqlite.SQLiteException
import androidx.sqlite.execSQL
import app.cash.turbine.turbineScope
import com.powersync.db.schema.Schema
import com.powersync.encryption.AndroidEncryptedDatabaseFactory
import com.powersync.encryption.Key
import com.powersync.testutils.UserRow
import kotlinx.coroutines.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseTest {

    @Test
    fun testEncryptedDatabase()  =
        runTest {
            val context = InstrumentationRegistry.getInstrumentation().targetContext

            val database = PowerSyncDatabase(
                factory = AndroidEncryptedDatabaseFactory(
                    context,
                    Key.Passphrase("mykey")
                ),
                schema = Schema(UserRow.table),
                dbFilename = "encrypted_test",
            )

            assertEquals("chacha20", database.get("PRAGMA cipher") { it.getString(0)!! })

            database.execute(
                "INSERT INTO users (id, name, email) VALUES (uuid(), ?, ?)",
                listOf("Test", "test@example.org"),
            )
            database.close()

            val unencryptedFactory = DatabaseDriverFactory(context)
            val unencrypted = unencryptedFactory.openConnection("encrypted_test", null, false)

            try {
                unencrypted.execSQL("SELECT * FROM sqlite_schema")
                throw IllegalStateException("Was able to read schema from encrypted database without supplying a key")
            } catch (_: SQLiteException) {
                // Expected
            }
            unencrypted.close()
        }
}
