package com.powersync.connector.supabase

import com.powersync.PowerSyncDatabase
import com.powersync.db.crud.CrudEntry
import com.powersync.db.crud.CrudTransaction
import com.powersync.db.schema.Column
import com.powersync.db.schema.Schema
import com.powersync.db.schema.Table
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SupabaseConnectorTest {
    @Test
    fun errorHandling() =
        runTest {
            val db =
                PowerSyncDatabase.inMemory(
                    scope = this,
                    schema =
                        Schema(
                            Table(
                                "users",
                                listOf(
                                    Column.text("name"),
                                ),
                            ),
                        ),
                )

            try {
                db.writeTransaction { tx ->
                    tx.execute("INSERT INTO users (id, name) VALUES (uuid(), ?)", listOf("a"))
                    tx.execute("INSERT INTO users (id, name) VALUES (uuid(), ?)", listOf("b"))
                    tx.execute("INSERT INTO users (id, name) VALUES (uuid(), ?)", listOf("c"))
                }

                var calledErrorHandler = false
                val connector =
                    object : SupabaseConnector("", "", "") {
                        override suspend fun uploadCrudEntry(entry: CrudEntry): Unit =
                            throw Exception("Expected exception, failing in uploadCrudEntry")

                        override suspend fun handleError(
                            tx: CrudTransaction,
                            entry: CrudEntry,
                            exception: Exception,
                            errorCode: String?,
                        ) {
                            calledErrorHandler = true

                            tx.crud shouldHaveSize 3
                            entry shouldBeEqual tx.crud[0]
                            exception.message shouldBe "Expected exception, failing in uploadCrudEntry"
                            tx.complete(null)
                        }
                    }

                connector.uploadData(db)
                calledErrorHandler shouldBe true
            } finally {
                db.close()
            }
        }
}
