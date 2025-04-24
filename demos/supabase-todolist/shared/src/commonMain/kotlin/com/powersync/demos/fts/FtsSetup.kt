/**
 * This file provides utility functions for setting up Full-Text Search (FTS)
 * using the FTS5 extension with PowerSync in a Kotlin Multiplatform project.
 * It mirrors the functionality of the fts_setup.dart file from the PowerSync
 * Flutter examples.
 *
 * Note: FTS5 support depends on the underlying SQLite engine used by the
 * PowerSync KMP SDK on each target platform. Ensure FTS5 is enabled/available.
 */
@file:JvmName("FtsSetupKt")

package com.powersync.demos.fts

import com.powersync.PowerSyncDatabase
import com.powersync.db.schema.Schema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import co.touchlab.kermit.Logger
import com.powersync.db.internal.PowerSyncTransaction
import kotlin.jvm.JvmName

/**
 * Defines the type of JSON extract operation needed, affecting the generated SQL.
 */
enum class ExtractType {
    /** Generates just the json_extract(...) expression. */
    COLUMN_ONLY,

    /** Generates 'column_name = json_extract(...)' for use in SET clauses. */
    COLUMN_IN_OPERATION
}

/**
 * Generates SQL JSON extract expressions for FTS triggers based on the ExtractType.
 * Matches the logic from the Dart helpers.dart example.
 *
 * @param type The type of extraction needed (COLUMN_ONLY or COLUMN_IN_OPERATION).
 * @param sourceColumn The JSON source column (e.g., 'data', 'NEW.data').
 * @param columns The list of column names to extract.
 * @return A comma-separated string of SQL expressions.
 */
internal fun generateJsonExtracts(
    type: ExtractType,
    sourceColumn: String,
    columns: List<String>
): String {
    // Helper function to generate the core json_extract part
    fun createExtract(jsonSource: String, columnName: String): String {
        // Quote the column name within the JSON path selector '$."columnName"'
        return "json_extract($jsonSource, '\$.\"$columnName\"')"
    }

    // Generate the SQL fragment for a single column based on the type
    fun generateSingleColumnSql(columnName: String): String {
        return when (type) {
            ExtractType.COLUMN_ONLY ->
                createExtract(sourceColumn, columnName)

            ExtractType.COLUMN_IN_OPERATION ->
                // Quote the target column name in the SET clause for safety: "columnName" = ...
                "\"$columnName\" = ${createExtract(sourceColumn, columnName)}"
        }
    }

    // Map each column to its corresponding SQL fragment and join them
    return columns.joinToString(", ") { columnName ->
        generateSingleColumnSql(columnName)
    }
}

/**
 * Generates the SQL statements required to set up an FTS5 virtual table
 * and corresponding triggers for a given PowerSync table. This function
 * mirrors the logic within the Dart `createFtsMigration` function.
 *
 * @param tableName The public name of the table to index (e.g., "lists", "todos").
 * @param columns The list of column names within the table to include in the FTS index.
 * @param schema The PowerSync Schema object to find the internal table name.
 * @param tokenizationMethod The FTS5 tokenization method (e.g., 'porter unicode61', 'unicode61').
 * @return A list of SQL statements to be executed, or null if the table is not found in the schema.
 */
internal fun getFtsSetupSqlStatements(
    tableName: String,
    columns: List<String>,
    schema: Schema,
    tokenizationMethod: String = "unicode61"
): List<String>? {
    // Find the internal name (PowerSync uses prefixed names internally)
    val internalName = schema.tables.find { it.name == tableName }?.internalName
        ?: run {
            Logger.w { "Table '$tableName' not found in schema. Skipping FTS setup for this table." }
            return null
        }

    val ftsTableName = "fts_$tableName"

    // Quote column names for use in CREATE VIRTUAL TABLE definition (e.g., "name", "description")
    val stringColumnsForCreate = columns.joinToString(", ") { "\"$it\"" }
    // Quote column names for use in INSERT INTO statement's column list
    val stringColumnsForInsertList = columns.joinToString(", ") { "\"$it\"" }

    val sqlStatements = mutableListOf<String>()

    // --- SQL Statement Generation (Matches Dart logic) ---

    // 1. Create the FTS5 Virtual Table
    // Example: CREATE VIRTUAL TABLE IF NOT EXISTS fts_lists USING fts5(id UNINDEXED, "name", tokenize='porter unicode61');
    sqlStatements.add(
        """
        CREATE VIRTUAL TABLE IF NOT EXISTS $ftsTableName
        USING fts5(id UNINDEXED, $stringColumnsForCreate, tokenize='$tokenizationMethod');
    """.trimIndent()
    )

    // 2. Copy existing data from the main table to the FTS table
    // Example: INSERT INTO fts_lists(rowid, id, "name") SELECT rowid, id, json_extract(data, '$."name"') FROM ps_data_lists;
    sqlStatements.add(
        """
        INSERT INTO $ftsTableName(rowid, id, $stringColumnsForInsertList)
        SELECT rowid, id, ${generateJsonExtracts(ExtractType.COLUMN_ONLY, "data", columns)}
        FROM $internalName;
    """.trimIndent()
    )

    // 3. Create INSERT Trigger: Keep FTS table updated when new rows are inserted into the main table
    // Example: CREATE TRIGGER IF NOT EXISTS fts_insert_trigger_lists AFTER INSERT ON ps_data_lists BEGIN INSERT INTO fts_lists(rowid, id, "name") VALUES ( NEW.rowid, NEW.id, json_extract(NEW.data, '$."name"') ); END;
    sqlStatements.add(
        """
        CREATE TRIGGER IF NOT EXISTS fts_insert_trigger_$tableName AFTER INSERT ON $internalName
        BEGIN
            INSERT INTO $ftsTableName(rowid, id, $stringColumnsForInsertList)
            VALUES (
                NEW.rowid,
                NEW.id,
                ${generateJsonExtracts(ExtractType.COLUMN_ONLY, "NEW.data", columns)}
            );
        END;
    """.trimIndent()
    )

    // 4. Create UPDATE Trigger: Keep FTS table updated when rows are updated in the main table
    // Example: CREATE TRIGGER IF NOT EXISTS fts_update_trigger_lists AFTER UPDATE ON ps_data_lists BEGIN UPDATE fts_lists SET "name" = json_extract(NEW.data, '$."name"') WHERE rowid = NEW.rowid; END;
    sqlStatements.add(
        """
        CREATE TRIGGER IF NOT EXISTS fts_update_trigger_$tableName AFTER UPDATE ON $internalName
        BEGIN
            UPDATE $ftsTableName
            SET ${generateJsonExtracts(ExtractType.COLUMN_IN_OPERATION, "NEW.data", columns)}
            WHERE rowid = NEW.rowid;
        END;
    """.trimIndent()
    )

    // 5. Create DELETE Trigger: Keep FTS table updated when rows are deleted from the main table
    // Example: CREATE TRIGGER IF NOT EXISTS fts_delete_trigger_lists AFTER DELETE ON ps_data_lists BEGIN DELETE FROM fts_lists WHERE rowid = OLD.rowid; END;
    sqlStatements.add(
        """
        CREATE TRIGGER IF NOT EXISTS fts_delete_trigger_$tableName AFTER DELETE ON $internalName
        BEGIN
            DELETE FROM $ftsTableName WHERE rowid = OLD.rowid;
        END;
    """.trimIndent()
    )

    return sqlStatements
}


/**
 * Configures Full-Text Search (FTS) tables and triggers for specified tables
 * within the PowerSync database. It generates the necessary SQL and executes it
 * within a single transaction. Call this function during your database initialization.
 * This function mirrors the intent of the Dart `configureFts` function.
 *
 * @param db The initialized PowerSyncDatabase instance.
 * @param schema The PowerSync Schema instance matching the database.
 */
suspend fun configureFts(db: PowerSyncDatabase, schema: Schema) {
    Logger.i { "[FTS] Starting FTS configuration..." }
    val allSqlStatements = mutableListOf<String>()

    // --- Define FTS configurations for each table ---

    // Configure FTS for the 'lists' table
    getFtsSetupSqlStatements(
        tableName = "lists",
        columns = listOf("name"),
        schema = schema,
        tokenizationMethod = "porter unicode61"
    )?.let {
        Logger.d { "[FTS] Generated ${it.size} SQL statements for 'lists' table." }
        allSqlStatements.addAll(it)
    }

    // Configure FTS for the 'todos' table
    getFtsSetupSqlStatements(
        tableName = "todos",
        columns = listOf("description", "list_id"), // Index multiple columns
        schema = schema
        // Uses default tokenizationMethod = "unicode61"
    )?.let {
        Logger.d { "[FTS] Generated ${it.size} SQL statements for 'todos' table." }
        allSqlStatements.addAll(it)
    }

    // --- Execute all generated SQL statements ---

    if (allSqlStatements.isNotEmpty()) {
        try {
            // Execute all setup statements within a single database transaction
            // Using Dispatchers.Default as DB operations might be CPU-bound or offloaded by the driver
            withContext(Dispatchers.Default) { // Adjust dispatcher if needed (e.g., Dispatchers.IO)
                Logger.i { "[FTS] Executing ${allSqlStatements.size} SQL statements in a transaction..." }
                db.writeTransaction { tx: PowerSyncTransaction ->
                    allSqlStatements.forEach { sql ->
                        // Log SQL execution - consider reducing verbosity in production
                        Logger.v { "[FTS] Executing SQL:\n$sql" }
                        tx.execute(sql) // Execute each statement
                    }
                }
            }
            Logger.i { "[FTS] Configuration completed successfully." }
        } catch (e: Exception) {
            // Log detailed error information
            Logger.e("[FTS] Error during FTS setup SQL execution: ${e.message}", throwable = e)
            // Depending on requirements, you might want to re-throw, clear FTS tables, or handle differently
        }
    } else {
        Logger.w { "[FTS] No FTS SQL statements were generated. Check table names and schema definition." }
    }
}
