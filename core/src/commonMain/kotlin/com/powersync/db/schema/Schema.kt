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
@OptIn(ExperimentalPowerSyncAPI::class)
public data class Schema internal constructor(
    val tables: List<Table>,
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

    /**
     * Secondary constructor to create a schema with a variable number of tables.
     */
    public constructor(vararg tables: Table) : this(tables.asList())

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

/**
 * A small note on the use of Serializable.
 * Using Serializable on public classes has an affect on the Object C headers for the Swift
 * SDK. The use causes:
 * An extra Objective-C Companion class for each of these types,
 * and Kotlin/Native having to export a bunch of the KotlinX Serialization classes and protocols.
 *
 * The actual requirements of serialization are quite standard and relatively small
 * in our use case. The implementation here declares a public data class for users to interact with
 * and an internal Serializable data class. Instances provided by consumers of the SDK are converted
 * to the serializable version then passed for serialization.
 *
 * An alternative would be to provide a custom serializer for each class. This approach has not been
 * implemented since we can use the built-in serialization methods with the internal serializable
 * classes.
 */
@Serializable
internal data class SerializableSchema(
    val tables: List<SerializableTable>,
    @SerialName("raw_tables")
    val rawTables: List<JsonElement>,
)

@OptIn(ExperimentalPowerSyncAPI::class)
internal fun Schema.toSerializable(): SerializableSchema =
    with(this) {
        SerializableSchema(
            tables = tables.map { it.toSerializable() },
            rawTables = rawTables.map { it.serialize() },
        )
    }
