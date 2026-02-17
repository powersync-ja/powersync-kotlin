package com.powersync.db.schema

import com.powersync.db.crud.CrudEntry
import kotlinx.serialization.descriptors.ClassSerialDescriptorBuilder
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.serializer

public data class TableOptions(
    /**
     * Whether the table only exists locally.
     */
    val localOnly: Boolean = false,
    /**
     * Whether this is an insert-only table.
     */
    val insertOnly: Boolean = false,
    /**
     *  Whether to add a hidden `_metadata` column that will be enabled for updates to attach custom
     *  information about writes that will be reported through [CrudEntry.metadata].
     */
    val trackMetadata: Boolean = false,
    /**
     * When set to a non-null value, track old values of columns for [CrudEntry.previousValue].
     *
     * See [TrackPreviousValuesOptions] for details.
     */
    val trackPreviousValues: TrackPreviousValuesOptions? = null,
    /**
     * Whether an `UPDATE` statement that doesn't change any values should be ignored when creating
     * CRUD entries.
     */
    val ignoreEmptyUpdates: Boolean = false,
) {
    internal fun validate() {
        check(!localOnly || !trackMetadata) {
            "Can't track metadata for local-only tables."
        }
        check(!localOnly || trackPreviousValues == null) {
            "Can't track old values for local-only tables."
        }
    }

    /**
     * Serializes table options into an outer serializer.
     *
     * @param index The index of the serial descriptor at which [addFields] have been added.
     */
    internal fun serialize(
        descriptor: SerialDescriptor,
        index: Int,
        writer: CompositeEncoder,
    ) {
        writer.encodeBooleanElement(descriptor, index, localOnly)
        writer.encodeBooleanElement(descriptor, index + 1, insertOnly)
        writer.encodeBooleanElement(descriptor, index + 2, ignoreEmptyUpdates)
        writer.encodeBooleanElement(descriptor, index + 3, trackMetadata)
        trackPreviousValues?.let {
            val serializedColumnFilter =
                it.columnFilter?.let { filter ->
                    buildJsonArray {
                        for (column in filter) {
                            add(JsonPrimitive(column))
                        }
                    }
                } ?: JsonPrimitive(true)

            writer.encodeSerializableElement(descriptor, index + 4, serializer<JsonElement>(), serializedColumnFilter)
            writer.encodeBooleanElement(descriptor, index + 5, it.onlyWhenChanged)
        }
    }

    internal companion object {
        fun addFields(descriptor: ClassSerialDescriptorBuilder) {
            with(descriptor) {
                element<Boolean>("local_only", isOptional = true)
                element<Boolean>("insert_only", isOptional = true)
                element<Boolean>("ignore_empty_update", isOptional = true)
                element<Boolean>("include_metadata", isOptional = true)
                element<JsonElement>("include_old", isOptional = true)
                element<Boolean>("include_old_only_when_changed", isOptional = true)
            }
        }
    }
}
