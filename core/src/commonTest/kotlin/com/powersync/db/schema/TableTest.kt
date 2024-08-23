package com.powersync.db.schema

import kotlin.test.*

class TableTest {

    @Test
    fun testTableInitialization() {
        val columns = listOf(
            Column("name", ColumnType.TEXT),
            Column("age", ColumnType.INTEGER)
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
        val columns = listOf(
            Column("name", ColumnType.TEXT),
            Column("age", ColumnType.INTEGER)
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
    fun testInvalidTableName() {
        val columns = listOf(Column("name", ColumnType.TEXT))
        val table = Table("#invalid-table-name", columns)

        assertFalse(table.validName)
    }

    @Test
    fun testValidation() {
        val columns = listOf(
            Column("name", ColumnType.TEXT),
            Column("age", ColumnType.INTEGER)
        )
        val table = Table("users", columns)

        // This should not throw an exception
        table.validate()
    }

    @Test
    fun testValidationFailsDuplicateColumn() {
        val columns = listOf(
            Column("name", ColumnType.TEXT),
            Column("name", ColumnType.TEXT)
        )
        val table = Table("users", columns)

        val exception = assertFailsWith<AssertionError> {
            table.validate()
        }
        assertEquals(exception.message, "Duplicate column users.name")
    }

    @Test
    fun testValidationFailsInvalidColumnName() {
        val columns = listOf(Column("#invalid-name", ColumnType.TEXT))
        val table = Table("users", columns)

        val exception = assertFailsWith<AssertionError> {
            table.validate()
        }
        assertEquals(exception.message, "Invalid characters in column name: users.#invalid-name")
    }

    @Test
    fun testValidationFailsTooManyColumns() {
        val columns = List(64) { Column("column$it", ColumnType.TEXT) }
        val table = Table("users", columns)

        val exception = assertFailsWith<AssertionError> {
            table.validate()
        }
        assertEquals(exception.message,"Table users has more than 63 columns, which is not supported")
    }

    @Test
    fun testValidationFailsInvalidIndexColumnReference() {
        val columns = listOf(Column("name", ColumnType.TEXT))
        val indexes = listOf(Index("idx_age", listOf(IndexedColumn("age"))))

        val exception = assertFailsWith<AssertionError> {
            Table("users", columns, indexes)
        }
        assertEquals(exception.message, "Could not find column definition for index idx_age:age")
    }

    @Test
    fun testValidationInvalidIndexColumnName() {
        val columns = listOf(Column("name", ColumnType.TEXT))
        val indexes = listOf(Index("#name_index", listOf(IndexedColumn("name"))))
        val table = Table("users", columns, indexes)

        val exception = assertFailsWith<AssertionError> {
            table.validate()
        }
        assertEquals(exception.message,"Invalid characters in index name: users.#name_index")
    }

    @Test
    fun testValidationFailsDuplicateIndexColumn() {
        val columns = listOf(Column("name", ColumnType.TEXT))
        val indexes = listOf(Index("name_index", listOf(IndexedColumn("name"))), Index("name_index", listOf(IndexedColumn("name"))))
        val table = Table("users", columns, indexes)

        val exception = assertFailsWith<AssertionError> {
            table.validate()
        }

        assertEquals(exception.message,"Duplicate index users.name_index")
    }
}