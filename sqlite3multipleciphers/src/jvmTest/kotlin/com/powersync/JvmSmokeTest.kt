package com.powersync

import com.powersync.encryption.JavaEncryptedDatabaseFactory
import com.powersync.encryption.Key
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.use

class JvmSmokeTest {
    @Test
    fun linksSqlite3MultipleCiphers() {
        JavaEncryptedDatabaseFactory(key).openInMemoryConnection().use { db ->
            db.prepare("PRAGMA cipher").use { stmt ->
                stmt.step() shouldBe true
                stmt.getText(0) shouldBe "chacha20"
            }
        }
    }

    private companion object Companion {
        val key = Key.Passphrase("test")
    }
}
