package com.powersync.integrations.room

import com.powersync.PowerSyncDatabase
import com.powersync.db.schema.Schema
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.test.Test

class DriverTest {
    @Test
    fun usingRoomApis() = databaseTest { db ->
        val room = databaseBuilder().setDriver(PowerSyncRoomDriver(db)).build()

        room.todosDao().count() shouldBe 0
        room.todosDao().addEntry(TodoEntity(title="Title", content="content"))
        room.todosDao().count() shouldBe 1
        room.close()
    }
}

private fun databaseTest(body: suspend TestScope.(PowerSyncDatabase) -> Unit) {
    runTest {
        val dir = SystemTemporaryDirectory

        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        val suffix = CharArray(8) { allowedChars.random() }.concatToString()
        val databaseName = "db-$suffix"

        val db = PowerSyncDatabase(
            factory,
            schema = Schema(listOf(TodoEntity.TABLE)),
            dbFilename = databaseName,
            dbDirectory = dir.toString()
        )

        try {
            body(db)
        } finally {
            db.close()
            SystemFileSystem.delete(Path(dir, databaseName))
        }
    }
}
