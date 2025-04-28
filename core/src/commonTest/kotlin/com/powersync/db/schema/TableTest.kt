package com.powersync.db.schema

import com.powersync.utils.JsonUtil
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TableTest {
    @Test
    fun testTableInitialization() {
        val columns =
            listOf(
                Column("name", ColumnType.TEXT),
                Column("age", ColumnType.INTEGER),
            )
        val table = Table("users", columns)

        assertEquals("users", table.name)
        assertEquals(2, table.columns.size)
        assertEquals("ps_data__users", table.internalName)
    }

    @Test
    fun testLocalOnlyTable() {
        val columns = listOf(Column("content", ColumnType.TEXT))
        val table = Table.localOnly("notes", columns)

        assertTrue(table.internalName.startsWith("ps_data_local__"))
        assertEquals("notes", table.viewName)
    }

    @Test
    fun testInsertOnlyTable() {
        val columns = listOf(Column("event", ColumnType.TEXT))
        val table = Table.insertOnly("logs", columns)

        assertTrue(table.internalName.startsWith("ps_data__"))
        assertEquals("logs", table.viewName)
    }

    @Test
    fun testColumnRetrieval() {
        val columns =
            listOf(
                Column("name", ColumnType.TEXT),
                Column("age", ColumnType.INTEGER),
            )
        val table = Table("users", columns)

        assertEquals(ColumnType.TEXT, table["name"].type)
        assertEquals(ColumnType.INTEGER, table["age"].type)
    }

    @Test
    fun testValidTableName() {
        val columns = listOf(Column("name", ColumnType.TEXT))
        val table = Table("valid_table_name", columns)

        assertTrue(table.validName)
    }

    @Test
    fun testViewNameOverride() {
        val columns = listOf(Column("name", ColumnType.TEXT))
        val table = Table("valid_table_name", columns, viewNameOverride = "new_view_name")

        assertEquals(table.viewName, "new_view_name")
    }

    @Test
    fun testInvalidTableName() {
        val columns = listOf(Column("name", ColumnType.TEXT))
        val table = Table("#invalid-table-name", columns)

        assertFalse(table.validName)
    }

    @Test
    fun testValidation() {
        val columns =
            listOf(
                Column("name", ColumnType.TEXT),
                Column("age", ColumnType.INTEGER),
            )
        val table = Table("users", columns)

        // This should not throw an exception
        table.validate()
    }

    @Test
    fun testValidationFailsDuplicateColumn() {
        val columns =
            listOf(
                Column("name", ColumnType.TEXT),
                Column("name", ColumnType.TEXT),
            )
        val table = Table("users", columns)

        val exception =
            assertFailsWith<AssertionError> {
                table.validate()
            }
        assertEquals(exception.message, "Duplicate column users.name")
    }

    @Test
    fun testValidationFailsInvalidColumnName() {
        val columns = listOf(Column("#invalid-name", ColumnType.TEXT))
        val table = Table("users", columns)

        val exception =
            assertFailsWith<AssertionError> {
                table.validate()
            }
        assertEquals(exception.message, "Invalid characters in column name: users.#invalid-name")
    }

    @Test
    fun testValidationFailsTooManyColumns() {
        val columns = List(2000) { Column("column$it", ColumnType.TEXT) }
        val table = Table("users", columns)

        val exception =
            assertFailsWith<AssertionError> {
                table.validate()
            }
        assertEquals(exception.message, "Table users has more than 1999 columns, which is not supported")
    }

    @Test
    fun testValidationFailsInvalidIndexColumnReference() {
        val columns = listOf(Column("name", ColumnType.TEXT))
        val indexes = listOf(Index("idx_age", listOf(IndexedColumn("age"))))

        val exception =
            assertFailsWith<AssertionError> {
                Table("users", columns, indexes)
            }
        assertEquals(exception.message, "Could not find column definition for index idx_age:age")
    }

    @Test
    fun testValidationInvalidIndexColumnName() {
        val columns = listOf(Column("name", ColumnType.TEXT))
        val indexes = listOf(Index("#name_index", listOf(IndexedColumn("name"))))
        val table = Table("users", columns, indexes)

        val exception =
            assertFailsWith<AssertionError> {
                table.validate()
            }
        assertEquals(exception.message, "Invalid characters in index name: users.#name_index")
    }

    @Test
    fun testValidationFailsDuplicateIndexColumn() {
        val columns = listOf(Column("name", ColumnType.TEXT))
        val indexes = listOf(Index("name_index", listOf(IndexedColumn("name"))), Index("name_index", listOf(IndexedColumn("name"))))
        val table = Table("users", columns, indexes)

        val exception =
            assertFailsWith<AssertionError> {
                table.validate()
            }

        assertEquals(exception.message, "Duplicate index users.name_index")
    }

    @Test
    fun testValidationOfIdColumn() {
        val columns = listOf(Column("id", ColumnType.TEXT))
        val table = Table("users", columns)

        val exception =
            assertFailsWith<AssertionError> {
                table.validate()
            }

        assertEquals(exception.message, "users: id column is automatically added, custom id columns are not supported")
    }

    @Test
    fun testValidationLocalOnlyWithMetadata() {
        val table = Table("foo", listOf(Column.text("bar")), localOnly = true, includeMetadata = true)

        val exception = shouldThrow<IllegalStateException> { table.validate() }
        exception.message shouldBe "Can't track metadata for local-only tables."
    }

    @Test
    fun testValidationLocalOnlyWithIncludeOld() {
        val table = Table("foo", listOf(Column.text("bar")), localOnly = true, includeOld = IncludeOldOptions())

        val exception = shouldThrow<IllegalStateException> { table.validate() }
        exception.message shouldBe "Can't track old values for local-only tables."
    }

    @Test
    fun handlesOptions() {
        fun serialize(table: Table): JsonObject {
            return JsonUtil.json.encodeToJsonElement(serializer<SerializableTable>(), table.toSerializable()) as JsonObject
        }

        serialize(Table("foo", emptyList(), includeMetadata = true))["include_metadata"]!!.jsonPrimitive.boolean shouldBe true
        serialize(Table("foo", emptyList(), ignoreEmptyUpdate = true))["ignore_empty_update"]!!.jsonPrimitive.boolean shouldBe true

        serialize(Table("foo", emptyList(), includeOld = IncludeOldOptions())).let {
            it["include_old"]!!.jsonPrimitive.boolean shouldBe true
            it["include_old_only_when_changed"]!!.jsonPrimitive.boolean shouldBe false
        }

        serialize(Table("foo", emptyList(), includeOld = IncludeOldOptions(columnFilter = listOf("foo", "bar")))).let {
            it["include_old"]!!.jsonArray.map { e -> e.jsonPrimitive.content } shouldBe listOf("foo", "bar")
            it["include_old_only_when_changed"]!!.jsonPrimitive.boolean shouldBe false
        }

        serialize(Table("foo", emptyList(), includeOld = IncludeOldOptions(onlyWhenChanged = true))).let {
            it["include_old"]!!.jsonPrimitive.boolean shouldBe true
            it["include_old_only_when_changed"]!!.jsonPrimitive.boolean shouldBe true
        }
    }
}
