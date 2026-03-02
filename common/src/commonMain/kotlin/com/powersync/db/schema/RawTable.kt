package com.powersync.db.schema

import com.powersync.sync.SyncOptions
import com.powersync.utils.OnlySerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.serializer

/**
 * A table that is managed by the user instead of being auto-created and migrated by the PowerSync
 * SDK.
 *
 * These tables give application developers full control over the table (including table and
 * column constraints). The [put] and [delete] statements the sync client uses to apply operations
 * to the local database also need to be set explicitly.
 *
 * A main benefit of raw tables is that, since they're not backed by JSON views, complex queries on
 * them can be much more efficient.
 * However, it's the responsibility of the developer to create these raw tables, migrate them when necessary and to
 * write triggers detecting local writes. For more information, see
 * [the documentation page](https://docs.powersync.com/usage/use-case-examples/raw-tables).
 *
 * Note that raw tables are only supported when [SyncOptions.newClientImplementation] is enabled.
 */
public class RawTable private constructor(
    override val name: String,
    public val put: PendingStatement?,
    /**
     * The statement to run when the sync client wants to delete a row.
     */
    public val delete: PendingStatement?,
    /**
     * An optional statement to run when [com.powersync.PowerSyncDatabase.disconnectAndClear] is called on the database.
     */
    public val clear: String? = null,
    public val schema: RawTableSchema? = null,
) : BaseTable {
    /**
     * Creates a raw table from [put] and [delete] statements.
     *
     * Alternatively, raw tables can be constructed by passing a [RawTableSchema]. This makes put
     * and delete statements optional.
     */
    public constructor(
        name: String,
        put: PendingStatement,
        delete: PendingStatement,
        clear: String? = null,
    ) : this(name, put, delete, clear, schema = null)

    /**
     * Creates a raw table that will infer [put] and [delete] statements from the given [schema].
     *
     * Statements can still be passed if they need to be customized.
     */
    public constructor(
        name: String,
        schema: RawTableSchema,
        put: PendingStatement? = null,
        delete: PendingStatement? = null,
        clear: String? = null,
    ) : this(name, put, delete, clear, schema)

    override fun validate() {
        this.schema?.options?.validate()
    }

    /**
     * A JSON-serialized representation of this raw table.
     *
     * The output of this can be passed to the `powersync_create_raw_table_crud_trigger` SQL
     * function to define triggers for this table.
     */
    public fun jsonDescription(): String = Json.encodeToString(RawTableSerializer, this)
}

/**
 * The schema of a [RawTable] in the local database.
 *
 * This information is optional when declaring raw tables. However, providing it allows the sync
 * client to infer [RawTable.put] and [RawTable.delete] statements automatically.
 */
public class RawTableSchema(
    /**
     * The actual name of the raw table in the local schema.
     *
     * Unlike [RawTable.name], which describes the name of synced tables to match, this reflects
     * the SQLite table name. This is used to infer [RawTable.put] and [RawTable.delete] statements
     * for the sync client. It can also be used to auto-generate triggers forwarding writes on raw
     * tables into the CRUD upload queue (using the `powersync_create_raw_table_crud_trigger` SQL
     * function).
     *
     * When set to null, it defaults to [RawTable.name] as these are commonly the same.
     */
    public val tableName: String? = null,
    /**
     * An optional filter of columns that should be synced.
     *
     * By default, all columns in a raw table are considered for sync. If a filter is specified,
     * PowerSync treats unmatched columns as local-only and will not attempt to sync them.
     */
    public val syncedColumns: List<String>? = null,
    /**
     * Common options affecting how the `powersync_create_raw_table_crud_trigger` SQL function
     * generates triggers.
     */
    public val options: TableOptions = TableOptions(),
)

public class PendingStatement(
    public val sql: String,
    public val parameters: List<PendingStatementParameter>,
)

/**
 * A parameter that can be used in a [PendingStatement].
 */
public sealed interface PendingStatementParameter {
    /**
     * Resolves to the id of the affected row.
     */
    public object Id : PendingStatementParameter

    /**
     * Resolves to the value of a column in the added row.
     *
     * This is only available for [RawTable.put] - in [RawTable.delete] statements, only the [Id]
     * can be used as a value.
     */
    public class Column(
        public val name: String,
    ) : PendingStatementParameter

    /**
     * Resolves to a JSON object containing all columns from the synced row that haven't been
     * matched by a [Column] value in the same statement.
     */
    public object Rest : PendingStatementParameter
}

internal typealias SerializableRawTable =
    @Serializable(RawTableSerializer::class)
    RawTable

internal object RawTableSerializer : KSerializer<RawTable> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.powersync.db.schema.RawTable") {
            element<String>("name")
            element("put", PendingStatementSerializer.descriptor, isOptional = true)
            element("delete", PendingStatementSerializer.descriptor, isOptional = true)
            element<String>("clear", isOptional = true)

            // RawTableSchema is flattened into this structure
            element<String>("table_name", isOptional = true)
            element<List<String>>("synced_columns", isOptional = true)
            TableOptions.addFields(this)
        }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(
        encoder: Encoder,
        value: RawTable,
    ) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.name)
            encodeNullableSerializableElement(descriptor, 1, PendingStatementSerializer, value.put)
            encodeNullableSerializableElement(descriptor, 2, PendingStatementSerializer, value.delete)
            value.clear?.let { clear -> encodeStringElement(descriptor, 3, clear) }

            value.schema?.let { schema ->
                encodeStringElement(descriptor, 4, schema.tableName ?: value.name)
                schema.syncedColumns?.let { filter -> encodeSerializableElement(descriptor, 5, serializer<List<String>>(), filter) }

                schema.options.serialize(descriptor, 6, this)
            }
        }
    }

    override fun deserialize(decoder: Decoder): RawTable {
        // We'll only ever serialize tables
        throw UnsupportedOperationException("serializing tables")
    }
}

internal object PendingStatementSerializer : OnlySerializer<PendingStatement>() {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.powersync.db.schema.PendingStatement") {
            element<String>("sql")
            element<List<JsonElement>>("params")
        }

    override fun serialize(
        encoder: Encoder,
        value: PendingStatement,
    ) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.sql)
            encodeSerializableElement(
                descriptor,
                1,
                serializer<List<JsonElement>>(),
                value.parameters.map { p ->
                    when (p) {
                        is PendingStatementParameter.Column ->
                            buildJsonObject {
                                put("Column", JsonPrimitive(p.name))
                            }
                        PendingStatementParameter.Id -> JsonPrimitive("Id")
                        PendingStatementParameter.Rest -> JsonPrimitive("Rest")
                    }
                },
            )
        }
    }
}
