package com.powersync.db.schema

import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.sync.SyncOptions
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
 *
 * Note that raw tables are only supported when [SyncOptions.newClientImplementation] is enabled.
 */
@ExperimentalPowerSyncAPI
public class RawTable(
    override val name: String,
    public val put: PendingStatement,
    /**
     * The statement to run when the sync client wants to delete a row.
     */
    public val delete: PendingStatement,
): BaseTable {
    override fun validate() {
        // We don't currently have any validation for raw tables
    }

    internal fun serialize(): JsonElement {
        return buildJsonObject {
            put("name", name)
            put("put", put.serialize())
            put("delete", delete.serialize())
        }
    }
}

@ExperimentalPowerSyncAPI
public class PendingStatement(
    public val sql: String,
    public val parameters: List<PendingStatementParameter>,
) {
    internal fun serialize(): JsonElement {
        return buildJsonObject {
            put("sql", sql)
            put("params", buildJsonArray {
                for (param in parameters) {
                    add(when(param) {
                        is PendingStatementParameter.Column -> buildJsonObject {
                            put("Column", param.name)
                        }
                        PendingStatementParameter.Id -> JsonPrimitive("Id")
                    })
                }
            })
        }
    }
}

/**
 * A parameter that can be used in a [PendingStatement].
 */
@ExperimentalPowerSyncAPI
public sealed interface PendingStatementParameter {
    /**
     * Resolves to the id of the affected row.
     */
    public object Id: PendingStatementParameter

    /**
     * Resolves to the value of a column in the added row.
     *
     * This is only available for [RawTable.put] - in [RawTable.delete] statements, only the [Id]
     * can be used as a value.
     */
    public class Column(public val name: String): PendingStatementParameter
}

