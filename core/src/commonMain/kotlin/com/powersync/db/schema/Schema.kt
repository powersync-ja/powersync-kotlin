package com.powersync.db.schema

import kotlinx.serialization.Serializable

public data class Schema(
    val tables: List<Table>,
) {
    init {
        validate()
    }

    public constructor(vararg tables: Table) : this(tables.asList())

    public fun validate() {
        val tableNames = mutableSetOf<String>()
        tables.forEach { table ->
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
)

internal fun Schema.toSerializable(): SerializableSchema =
    with(this) {
        SerializableSchema(tables.map { it.toSerializable() })
    }
