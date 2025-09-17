package com.powersync.db.internal

import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.db.SqlCursor
import com.powersync.db.driver.SQLiteConnectionLease
import com.powersync.db.driver.SQLiteConnectionPool
import com.powersync.db.runWrapped
import com.powersync.utils.AtomicMutableSet
import com.powersync.utils.JsonUtil
import com.powersync.utils.throttle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalPowerSyncAPI::class)
internal class InternalDatabaseImpl(
    private val pool: SQLiteConnectionPool,
) : InternalDatabase {
    // Could be scope.coroutineContext, but the default is GlobalScope, which seems like a bad idea. To discuss.
    private val dbContext = Dispatchers.IO

    override suspend fun updateSchema(schemaJson: String) {
        withContext(dbContext) {
            runWrapped {
                pool.withAllConnections { writer, readers ->
                    writer.runTransaction { tx ->
                        tx.getOptional(
                            "SELECT powersync_replace_schema(?);",
                            listOf(schemaJson),
                        ) {}
                    }

                    // Update the schema on all read connections
                    for (readConnection in readers) {
                        readConnection.execSQL("pragma table_info('sqlite_master')")
                    }
                }
            }
        }
    }

    override fun onChange(
        tables: Set<String>,
        throttleMs: Long,
        triggerImmediately: Boolean,
    ): Flow<Set<String>> =
        channelFlow {
            // Match all possible internal table combinations
            val watchedTables =
                tables.flatMap { listOf(it, "ps_data__$it", "ps_data_local__$it") }.toSet()

            // Accumulate updates between throttles
            val batchedUpdates = AtomicMutableSet<String>()

            updatesOnTables()
                .onSubscription {
                    if (triggerImmediately) {
                        // Emit an initial event (if requested). No changes would be detected at this point
                        send(setOf())
                    }
                }.transform { updates ->
                    val intersection = updates.intersect(watchedTables)
                    if (intersection.isNotEmpty()) {
                        // Transform table names using friendlyTableName
                        val friendlyTableNames = intersection.map { friendlyTableName(it) }.toSet()
                        batchedUpdates.addAll(friendlyTableNames)
                        emit(Unit)
                    }
                }
                // Throttling here is a feature which prevents watch queries from spamming updates.
                // Throttling by design discards and delays events within the throttle window. Discarded events
                // still trigger a trailing edge update.
                // Backpressure is avoided on the throttling and consumer level by buffering the last upstream value.
                .throttle(throttleMs.milliseconds)
                .collect {
                    // Emit the transformed tables which have changed
                    val copy = batchedUpdates.toSetAndClear()
                    send(copy)
                }
        }

    override fun <RowType : Any> watch(
        sql: String,
        parameters: List<Any?>?,
        throttleMs: Long,
        mapper: (SqlCursor) -> RowType,
    ): Flow<List<RowType>> =
        // Use a channel flow here since we throttle (buffer used under the hood)
        // This causes some emissions to be from different scopes.
        channelFlow {
            // Fetch the tables asynchronously with getAll
            val tables =
                getSourceTables(sql, parameters)
                    .filter { it.isNotBlank() }
                    .toSet()

            updatesOnTables()
                // onSubscription here is very important.
                // This ensures that the initial result and all updates are emitted.
                .onSubscription {
                    send(getAll(sql, parameters = parameters, mapper = mapper))
                }.filter {
                    // Only trigger updates on relevant tables
                    it.intersect(tables).isNotEmpty()
                }
                // Throttling here is a feature which prevents watch queries from spamming updates.
                // Throttling by design discards and delays events within the throttle window. Discarded events
                // still trigger a trailing edge update.
                // Backpressure is avoided on the throttling and consumer level by buffering the last upstream value.
                // Note that the buffered upstream "value" only serves to trigger the getAll query. We don't buffer watch results.
                .throttle(throttleMs.milliseconds)
                .collect {
                    send(getAll(sql, parameters = parameters, mapper = mapper))
                }
        }

    override suspend fun <T> useConnection(
        readOnly: Boolean,
        block: suspend (SQLiteConnectionLease) -> T,
    ): T =
        withContext(dbContext) {
            if (readOnly) {
                pool.read(block)
            } else {
                pool.write(block)
            }
        }

    // Register callback for table updates on a specific table
    override fun updatesOnTables(): SharedFlow<Set<String>> = pool.updates

    private suspend fun getSourceTables(
        sql: String,
        parameters: List<Any?>?,
    ): Set<String> {
        val rows =
            getAll(
                "EXPLAIN $sql",
                parameters = parameters,
                mapper = {
                    ExplainQueryResult(
                        addr = it.getString(0)!!,
                        opcode = it.getString(1)!!,
                        p1 = it.getLong(2)!!,
                        p2 = it.getLong(3)!!,
                        p3 = it.getLong(4)!!,
                    )
                },
            )

        val rootPages = mutableListOf<Long>()
        for (row in rows) {
            if ((row.opcode == "OpenRead" || row.opcode == "OpenWrite") && row.p3 == 0L && row.p2 != 0L) {
                rootPages.add(row.p2)
            }
        }
        val params = listOf(JsonUtil.json.encodeToString(rootPages))
        val tableRows =
            getAll(
                "SELECT tbl_name FROM sqlite_master WHERE rootpage IN (SELECT json_each.value FROM json_each(?))",
                parameters = params,
                mapper = { it.getString(0)!! },
            )

        return tableRows.toSet()
    }

    override suspend fun close() {
        runWrapped {
            pool.close()
        }
    }

    internal data class ExplainQueryResult(
        val addr: String,
        val opcode: String,
        val p1: Long,
        val p2: Long,
        val p3: Long,
    )
}

/**
 * Converts internal table names (e.g., prefixed with "ps_data__" or "ps_data_local__")
 * to their original friendly names by removing the prefixes. If no prefix matches,
 * the original table name is returned.
 */
private fun friendlyTableName(table: String): String {
    val re = Regex("^ps_data__(.+)$")
    val re2 = Regex("^ps_data_local__(.+)$")
    val match = re.matchEntire(table) ?: re2.matchEntire(table)
    return match?.groupValues?.get(1) ?: table
}
