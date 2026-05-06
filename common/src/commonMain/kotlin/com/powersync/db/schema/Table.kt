package com.powersync.db.schema

import com.powersync.db.crud.CrudEntry
import com.powersync.utils.OnlySerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.serializer

private const val MAX_AMOUNT_OF_COLUMNS = 1999

/**
 * A single table in the schema.
 */
@Serializable(with = TableSerializer::class)
public data class Table(
    /**
     * The synced table name, matching sync rules.
     */
    override var name: String,
    /**
     * List of columns.
     */
    var columns: List<Column>,
    /**
     * List of indexes.
     */
    var indexes: List<Index> = listOf(),
    /**
     * Common options for this table.
     */
    var options: TableOptions = TableOptions(),
    /**
     * Override the name for the view
     */
    internal val viewNameOverride: String? = null,
) : BaseTable {
    public constructor(
        name: String,
        columns: List<Column>,
        indexes: List<Index> = listOf(),
        localOnly: Boolean = false,
        insertOnly: Boolean = false,
        viewNameOverride: String? = null,
        trackMetadata: Boolean = false,
        trackPreviousValues: TrackPreviousValuesOptions? = null,
        ignoreEmptyUpdates: Boolean = false,
    ) : this(
        name = name,
        columns = columns,
        indexes = indexes,
        viewNameOverride = viewNameOverride,
        options =
            TableOptions(
                localOnly = localOnly,
                insertOnly = insertOnly,
                trackMetadata = trackMetadata,
                trackPreviousValues = trackPreviousValues,
                ignoreEmptyUpdates = ignoreEmptyUpdates,
            ),
    )

    init {
        /**
         * Need to set the column definition for each index column.
         * This is required for serialization
         */
        indexes.forEach { index ->
            index.columns.forEach {
                val matchingColumn =
                    columns.find { c -> c.name == it.column }
                        ?: throw AssertionError("Could not find column definition for index ${index.name}:${it.column}")
                it.setColumnDefinition(column = matchingColumn)
            }
        }
    }

    public companion object {
        /**
         * Create a table that only exists locally.
         *
         * This table does not record changes, and is not synchronized from the service.
         */
        public fun localOnly(
            name: String,
            columns: List<Column>,
            indexes: List<Index> = listOf(),
            viewName: String? = null,
        ): Table =
            Table(
                name,
                columns,
                indexes,
                viewNameOverride = viewName,
                options =
                    TableOptions(
                        localOnly = true,
                        insertOnly = false,
                    ),
            )

        /**
         * Create a table that only supports inserts.
         *
         * This table records INSERT statements, but does not persist data locally.
         *
         * SELECT queries on the table will always return 0 rows.
         */
        public fun insertOnly(
            name: String,
            columns: List<Column>,
            viewName: String? = null,
            ignoreEmptyUpdates: Boolean = false,
            trackMetadata: Boolean = false,
            trackPreviousValues: TrackPreviousValuesOptions? = null,
        ): Table =
            Table(
                name,
                columns,
                indexes = listOf(),
                viewNameOverride = viewName,
                options =
                    TableOptions(
                        localOnly = false,
                        insertOnly = true,
                        ignoreEmptyUpdates = ignoreEmptyUpdates,
                        trackMetadata = trackMetadata,
                        trackPreviousValues = trackPreviousValues,
                    ),
            )
    }

    /**
     * Internal use only.
     *
     * Name of the table that stores the underlying data.
     */
    internal val internalName: String
        get() = if (options.localOnly) "ps_data_local__$name" else "ps_data__$name"

    public operator fun get(columnName: String): Column = columns.first { it.name == columnName }

    /**
     * Whether this table name is valid.
     */
    val validName: Boolean
        get() =
            !invalidSqliteCharacters.containsMatchIn(name) &&
                (
                    viewNameOverride == null ||
                        !invalidSqliteCharacters.containsMatchIn(
                            viewNameOverride,
                        )
                )

    public override fun validate() {
        if (columns.size > MAX_AMOUNT_OF_COLUMNS) {
            throw AssertionError("Table $name has more than $MAX_AMOUNT_OF_COLUMNS columns, which is not supported")
        }

        if (invalidSqliteCharacters.containsMatchIn(name)) {
            throw AssertionError("Invalid characters in table name: $name")
        }

        if (viewNameOverride != null &&
            invalidSqliteCharacters.containsMatchIn(
                viewNameOverride,
            )
        ) {
            throw AssertionError("Invalid characters in view name: $viewNameOverride")
        }

        options.validate()

        val columnNames = mutableSetOf("id")
        for (column in columns) {
            when {
                column.name == "id" -> {
                    throw AssertionError("$name: id column is automatically added, custom id columns are not supported")
                }

                columnNames.contains(column.name) -> {
                    throw AssertionError("Duplicate column $name.${column.name}")
                }

                invalidSqliteCharacters.containsMatchIn(column.name) -> {
                    throw AssertionError("Invalid characters in column name: $name.${column.name}")
                }

                else -> columnNames.add(column.name)
            }
        }

        val indexNames = mutableSetOf<String>()
        for (index in indexes) {
            when {
                indexNames.contains(index.name) -> {
                    throw AssertionError("Duplicate index $name.${index.name}")
                }

                invalidSqliteCharacters.containsMatchIn(index.name) -> {
                    throw AssertionError("Invalid characters in index name: $name.${index.name}")
                }

                else -> {
                    for (column in index.columns) {
                        if (!columnNames.contains(column.column)) {
                            throw AssertionError("Column $name.${column.column} not found for index ${index.name}")
                        }
                    }
                    indexNames.add(index.name)
                }
            }
        }
    }

    /**
     * Name for the view, used for queries.
     * Defaults to the synced table name.
     */
    public val viewName: String
        get() = viewNameOverride ?: name
}

/**
 * Options to include old values in [CrudEntry.previousValues] for update statements.
 *
 * These options are enabled by passing them to a non-local [Table] constructor.
 */
public data class TrackPreviousValuesOptions(
    /**
     * A filter of column names for which updates should be tracked.
     *
     * When set to a non-null value, columns not included in this list will not appear in
     * [CrudEntry.previousValues]. By default, all columns are included.
     */
    val columnFilter: List<String>? = null,
    /**
     * Whether to only include old values when they were changed by an update, instead of always
     * including all old values,
     */
    val onlyWhenChanged: Boolean = false,
)

internal object TableSerializer : OnlySerializer<Table>() {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.powersync.db.schema.Table") {
            element<String>("name")
            element<List<Column>>("columns")
            element<List<Index>>("indexes")
            element<String?>("view_name")

            // Flatten common TableOptions fields into this serializer
            TableOptions.addFields(this)
        }

    override fun serialize(
        encoder: Encoder,
        value: Table,
    ) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.name)
            encodeSerializableElement(descriptor, 1, serializer<List<Column>>(), value.columns)
            encodeSerializableElement(descriptor, 2, serializer<List<Index>>(), value.indexes)
            encodeSerializableElement(descriptor, 3, serializer<String?>(), value.viewNameOverride)

            value.options.serialize(descriptor, 4, this)
        }
    }
}
