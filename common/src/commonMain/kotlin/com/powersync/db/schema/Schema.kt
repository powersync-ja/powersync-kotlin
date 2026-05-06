package com.powersync.db.schema

import com.powersync.ExperimentalPowerSyncAPI
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * The schema used by the database.
 *
 * The implementation uses the schema as a "VIEW" on top of JSON data.
 * No migrations are required on the client.
 */
@ConsistentCopyVisibility
@OptIn(ExperimentalPowerSyncAPI::class)
@Serializable
public data class Schema internal constructor(
    val tables: List<Table>,
    @SerialName("raw_tables")
    val rawTables: List<RawTable>,
) {
    public constructor(tables: List<BaseTable>) : this(
        tables.filterIsInstance<Table>(),
        tables.filterIsInstance<RawTable>(),
    )

    init {
        validate()
    }

    internal val allTables: Sequence<BaseTable> get() =
        sequence {
            yieldAll(tables)
            yieldAll(rawTables)
        }

    // Kept for binary compatibility, the new constructor taking a BaseTable vararg will be used when recompiling.
    @Deprecated(level = DeprecationLevel.HIDDEN, message = "Use constructor taking BaseTable args instead")
    public constructor(vararg tables: Table) : this(tables.asList())

    /**
     * Secondary constructor to create a schema with a variable number of tables.
     */
    public constructor(vararg tables: BaseTable) : this(tables.asList())

    /**
     * Validates the schema by ensuring there are no duplicate table names
     * and that each table is valid.
     *
     * @throws AssertionError if duplicate table names are found.
     */
    public fun validate() {
        val tableNames = mutableSetOf<String>()
        allTables.forEach { table ->
            if (!tableNames.add(table.name)) {
                throw AssertionError("Duplicate table name: ${table.name}")
            }
            table.validate()
        }
    }
}
