package com.powersync.pool

import cnames.structs.sqlite3
import cnames.structs.sqlite3_changeset_iter
import cnames.structs.sqlite3_session
import com.powersync.PowerSyncException
import com.powersync.db.runWrapped
import com.powersync.internal.sqlite3.sqlite3_free
import com.powersync.internal.sqlite3.sqlite3changeset_finalize
import com.powersync.internal.sqlite3.sqlite3changeset_next
import com.powersync.internal.sqlite3.sqlite3changeset_op
import com.powersync.internal.sqlite3.sqlite3changeset_start
import com.powersync.internal.sqlite3.sqlite3session_attach
import com.powersync.internal.sqlite3.sqlite3session_changeset
import com.powersync.internal.sqlite3.sqlite3session_create
import com.powersync.internal.sqlite3.sqlite3session_delete
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value

/**
 * We typically have a few options for table update hooks:
 * 1.) Registering a hook with SQLite
 * 2.) Using our Rust core to register update hooks
 * 3.) Receiving updates from an external API
 *
 * In some cases, particularly in the case of GRDB, none of these options are viable.
 * GRDB dynamically registers (and unregisters) its own update hooks and its update hook logic
 * does not report changes for operations made outside of its own APIs.
 *
 * 1.) We can't register our own hooks since GRDB might override it or our hook could conflict with GRDB's
 * 2.) We can't register hooks due to above
 * 3.) The GRDB APIs only report changes if made with their SQLite execution APIs. It's not trivial to implement [com.powersync.db.driver.SQLiteConnectionLease] with their APIs.
 *
 * This function provides an alternative method of obtaining table changes by using SQLite sessions.
 * https://www.sqlite.org/sessionintro.html
 *
 * We start a session, execute a block of code, and then extract the changeset from the session.
 * We then parse the changeset to extract the table names that were modified.
 * This approach is more heavyweight than using update hooks, but it works in scenarios where
 * update hooks are not currently feasible.
 */
@Throws(PowerSyncException::class)
public fun withSession(
    db: CPointer<sqlite3>,
    block: () -> Unit,
): Set<String> =
    runWrapped {
        memScoped {
            val sessionPtr = alloc<CPointerVar<sqlite3_session>>()

            val rc =
                sqlite3session_create(
                    db,
                    "main",
                    sessionPtr.ptr,
                ).checkResult("Could not create SQLite session")

            val session =
                sessionPtr.value ?: throw PowerSyncException(
                    "Could not create SQLite session",
                    cause = Error(),
                )

            try {
                // Attach all tables to track changes
                sqlite3session_attach(
                    session,
                    null,
                ).checkResult("Could not attach all tables to session") // null means all tables

                // Execute the block where changes happen
                block()

                // Get the changeset
                val changesetSizePtr = alloc<IntVar>()
                val changesetPtr = alloc<COpaquePointerVar>()

                val changesetRc =
                    sqlite3session_changeset(
                        session,
                        changesetSizePtr.ptr,
                        changesetPtr.ptr,
                    ).checkResult("Could not get changeset from session")

                val changesetSize = changesetSizePtr.value
                val changeset = changesetPtr.value

                if (changesetSize == 0 || changeset == null) {
                    return@memScoped emptySet<String>()
                }

                // Parse the changeset to extract table names
                val changedTables = mutableSetOf<String>()
                val iterPtr = alloc<CPointerVar<sqlite3_changeset_iter>>()

                val startRc =
                    sqlite3changeset_start(
                        iterPtr.ptr,
                        changesetSize,
                        changeset,
                    ).checkResult("Could not start changeset iterator")

                val iter = iterPtr.value

                if (iter == null) {
                    return@memScoped emptySet<String>()
                }

                try {
                    // Iterate through all changes
                    while (sqlite3changeset_next(iter) == 100) {
                        val tableNamePtr = alloc<CPointerVar<ByteVar>>()
                        val nColPtr = alloc<IntVar>()
                        val opPtr = alloc<IntVar>()
                        val indirectPtr = alloc<IntVar>()

                        val opRc =
                            sqlite3changeset_op(
                                iter,
                                tableNamePtr.ptr,
                                nColPtr.ptr,
                                opPtr.ptr,
                                indirectPtr.ptr,
                            )

                        if (opRc == 0) {
                            val tableNameCPtr = tableNamePtr.value
                            if (tableNameCPtr != null) {
                                val tableName = tableNameCPtr.toKString()
                                changedTables.add(tableName)
                            }
                        }
                    }
                } finally {
                    sqlite3changeset_finalize(iter)
                    // Free the changeset memory
                    sqlite3_free(changeset)
                }

                return@memScoped changedTables.toSet()
            } finally {
                // Clean up the session
                sqlite3session_delete(session)
            }
        }
    }

private fun Int.checkResult(message: String) {
    if (this != 0) {
        throw PowerSyncException("SQLite error code: $this", cause = Error(message))
    }
}
