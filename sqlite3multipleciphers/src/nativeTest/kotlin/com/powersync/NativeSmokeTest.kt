package com.powersync

import com.powersync.encryption.Key
import com.powersync.encryption.NativeEncryptedDatabaseFactory
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class NativeSmokeTest {
    @Test
    fun linksSqlite3MultipleCiphers() {
        NativeEncryptedDatabaseFactory(key).openInMemoryConnection().use { db ->
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
