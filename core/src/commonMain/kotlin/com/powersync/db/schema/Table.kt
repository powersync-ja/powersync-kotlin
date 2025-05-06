package com.powersync.db.schema

import com.powersync.db.crud.CrudEntry
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray

private const val MAX_AMOUNT_OF_COLUMNS = 1999

/**
 * A single table in the schema.
 */
public data class Table(
    /**
     * The synced table name, matching sync rules.
     */
    var name: String,
    /**
     * List of columns.
     */
    var columns: List<Column>,
    /**
     * List of indexes.
     */
    var indexes: List<Index> = listOf(),
    /**
     * Whether the table only exists only.
     */
    val localOnly: Boolean = false,
    /**
     * Whether this is an insert-only table.
     */
    val insertOnly: Boolean = false,
    /**
     * Override the name for the view
     */
    private val viewNameOverride: String? = null,
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
                localOnly = true,
                insertOnly = false,
                viewNameOverride = viewName,
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
                localOnly = false,
                insertOnly = true,
                viewNameOverride = viewName,
                ignoreEmptyUpdates = ignoreEmptyUpdates,
                trackMetadata = trackMetadata,
                trackPreviousValues = trackPreviousValues,
            )
    }

    /**
     * Internal use only.
     *
     * Name of the table that stores the underlying data.
     */
    internal val internalName: String
        get() = if (localOnly) "ps_data_local__$name" else "ps_data__$name"

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

    /**
     * Check that there are no issues in the table definition.
     */
    public fun validate() {
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

        check(!localOnly || !trackMetadata) {
            "Can't track metadata for local-only tables."
        }
        check(!localOnly || trackPreviousValues == null) {
            "Can't track old values for local-only tables."
        }

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
 * Options to include old values in [CrudEntry.previousValue] for update statements.
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

@Serializable
internal data class SerializableTable(
    var name: String,
    var columns: List<SerializableColumn>,
    var indexes: List<SerializableIndex> = listOf(),
    @SerialName("local_only")
    val localOnly: Boolean = false,
    @SerialName("insert_only")
    val insertOnly: Boolean = false,
    @SerialName("view_name")
    val viewName: String? = null,
    @SerialName("ignore_empty_update")
    val ignoreEmptyUpdate: Boolean = false,
    @SerialName("include_metadata")
    val includeMetadata: Boolean = false,
    @SerialName("include_old")
    val includeOld: JsonElement = JsonPrimitive(false),
    @SerialName("include_old_only_when_changed")
    val includeOldOnlyWhenChanged: Boolean = false,
)

internal fun Table.toSerializable(): SerializableTable =
    with(this) {
        SerializableTable(
            name = name,
            columns = columns.map { it.toSerializable() },
            indexes = indexes.map { it.toSerializable() },
            localOnly = localOnly,
            insertOnly = insertOnly,
            viewName = viewName,
            ignoreEmptyUpdate = ignoreEmptyUpdates,
            includeMetadata = trackMetadata,
            includeOld =
                trackPreviousValues?.let {
                    if (it.columnFilter != null) {
                        buildJsonArray {
                            for (column in it.columnFilter) {
                                add(JsonPrimitive(column))
                            }
                        }
                    } else {
                        JsonPrimitive(true)
                    }
                } ?: JsonPrimitive(false),
            includeOldOnlyWhenChanged = trackPreviousValues?.onlyWhenChanged ?: false,
        )
    }
