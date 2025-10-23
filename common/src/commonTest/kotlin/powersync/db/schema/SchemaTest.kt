package powersync.db.schema

import com.powersync.db.schema.Column
import com.powersync.db.schema.ColumnType
import com.powersync.db.schema.Schema
import com.powersync.db.schema.Table
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SchemaTest {
    @Test
    fun schemaConstructionWithValidTablesShouldSucceed() {
        val table1 = Table("table1", listOf(Column("column1", ColumnType.TEXT)))
        val table2 = Table("table2", listOf(Column("column2", ColumnType.INTEGER)))

        val schema = Schema(table1, table2)

        assertEquals(2, schema.tables.size)
        assertEquals("table1", schema.tables[0].name)
        assertEquals("table2", schema.tables[1].name)
    }

    @Test
    fun schemaConstructionWithInvalidTableShouldThrowException() {
        val validTable = Table("validTable", listOf(Column("column1", ColumnType.TEXT)))
        val invalidTable = Table("#invalid-table", listOf(Column("column1", ColumnType.TEXT)))

        val exception =
            assertFailsWith<AssertionError> {
                Schema(validTable, invalidTable)
            }
        assertEquals(exception.message, "Invalid characters in table name: #invalid-table")
    }

    @Test
    fun schemaShouldDetectDuplicateTableNames() {
        val table1 = Table("table1", listOf(Column("column1", ColumnType.TEXT)))
        val table2 = Table("table1", listOf(Column("column2", ColumnType.INTEGER)))

        val exception =
            assertFailsWith<AssertionError> {
                Schema(table1, table2)
            }
        assertEquals(exception.message, "Duplicate table name: table1")
    }
}
