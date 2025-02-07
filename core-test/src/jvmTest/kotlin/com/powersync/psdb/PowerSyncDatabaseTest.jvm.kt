package com.powersync.psdb

import com.powersync.DatabaseDriverFactory
import java.io.File

actual abstract class RobolectricTest actual constructor()

actual val factory: DatabaseDriverFactory
    get() = DatabaseDriverFactory()

actual fun cleanupDb() {
    val testDbFile = File("testdb")
    if(testDbFile.exists()){
        testDbFile.delete()
    }
}