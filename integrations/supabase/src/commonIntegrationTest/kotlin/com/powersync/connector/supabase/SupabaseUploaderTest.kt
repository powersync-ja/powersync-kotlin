package com.powersync.connector.supabase

import com.powersync.PowerSyncDatabase
import com.powersync.PowerSyncException
import com.powersync.db.crud.CrudEntry
import com.powersync.db.crud.CrudTransaction
import com.powersync.db.schema.Column
import com.powersync.db.schema.Schema
import com.powersync.db.schema.Table
import com.powersync.inMemory
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class SupabaseUploaderTest {
    @Test
    fun retriesFailedTransactionsAndCompletesSuccessfulUploads() =
        runTest {
            val db =
                PowerSyncDatabase.inMemory(
                    scope = this,
                    schema = Schema(Table("users", listOf(Column.text("name")))),
                )
            val client = createSupabaseClient("https://example.supabase.co", "test-key") { install(Postgrest) }
            try {
                db.execute("INSERT INTO users (id, name) VALUES ('user-id', 'Alice')")
                var shouldFail = true
                val uploaded = mutableListOf<CrudEntry>()
                val uploader =
                    object : SupabaseUploader(client) {
                        override suspend fun uploadCrudEntry(entry: CrudEntry) {
                            if (shouldFail) throw IllegalStateException("Temporary failure")
                            uploaded.add(entry)
                        }
                    }

                val error = assertFailsWith<PowerSyncException> { uploader.uploadMutations(db) }
                error.cause!!.message shouldBe "Temporary failure"
                db.getNextCrudTransaction()!!.crud shouldHaveSize 1

                shouldFail = false
                uploader.uploadMutations(db)
                uploaded shouldHaveSize 1
                uploaded.single().id shouldBe "user-id"
                db.getNextCrudTransaction() shouldBe null

                uploader.uploadMutations(db)
                uploaded shouldHaveSize 1
            } finally {
                db.close()
                client.close()
            }
        }

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

            val client = createSupabaseClient("https://example.supabase.co", "test-key") { install(Postgrest) }

            try {
                db.writeTransaction { tx ->
                    tx.execute("INSERT INTO users (id, name) VALUES (uuid(), ?)", listOf("a"))
                    tx.execute("INSERT INTO users (id, name) VALUES (uuid(), ?)", listOf("b"))
                    tx.execute("INSERT INTO users (id, name) VALUES (uuid(), ?)", listOf("c"))
                }

                var calledErrorHandler = false
                val connector =
                    object : SupabaseUploader(client) {
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

                connector.uploadMutations(db)
                calledErrorHandler shouldBe true
            } finally {
                db.close()
                client.close()
            }
        }
}
