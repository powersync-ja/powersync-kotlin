package com.powersync.internal.driver

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.NativeSQLiteConnection
import androidx.sqlite.throwSQLiteException
import cnames.structs.sqlite3
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import sqlite3.SQLITE_OPEN_CREATE
import sqlite3.SQLITE_OPEN_READONLY
import sqlite3.SQLITE_OPEN_READWRITE
import sqlite3.sqlite3_commit_hook
import sqlite3.sqlite3_open_v2
import sqlite3.sqlite3_rollback_hook
import sqlite3.sqlite3_update_hook

public class NativeDriver : PowerSyncDriver {
    override fun openDatabase(
        path: String,
        readOnly: Boolean,
        listener: ConnectionListener?,
    ): SQLiteConnection {
        val flags = if (readOnly) {
            SQLITE_OPEN_READONLY
        } else {
            SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE
        }

        return memScoped {
            val dbPointer = allocPointerTo<sqlite3>()
            val resultCode =
                sqlite3_open_v2(filename = path, ppDb = dbPointer.ptr, flags = flags, zVfs = null)

            if (resultCode != 0) {
                throwSQLiteException(resultCode, null)
            }

            ListenerConnection(dbPointer.value!!, listener)
        }
    }
}

private class ListenerConnection(
    sqlite: CPointer<sqlite3>,
    listener: ConnectionListener?
): SQLiteConnection {
    private val inner: NativeSQLiteConnection = NativeSQLiteConnection(sqlite)
    private val listener: StableRef<ConnectionListener>? = listener?.let { StableRef.create(it) }?.also {
        sqlite3_update_hook(sqlite, updateHook, it.asCPointer())
        sqlite3_commit_hook(sqlite, commitHook, it.asCPointer())
        sqlite3_rollback_hook(sqlite, rollbackHook, it.asCPointer())
    }

    override fun inTransaction(): Boolean {
        return inner.inTransaction()
    }

    override fun prepare(sql: String): SQLiteStatement {
        return inner.prepare(sql)
    }

    override fun close() {
        inner.close()
        listener?.dispose()
    }
}

private val commitHook =
    staticCFunction<COpaquePointer?, Int> {
        val listener = it!!.asStableRef<ConnectionListener>().get()
        listener.onCommit()
        0
    }

private val rollbackHook =
    staticCFunction<COpaquePointer?, Unit> {
        val listener = it!!.asStableRef<ConnectionListener>().get()
        listener.onRollback()
    }

private val updateHook =
    staticCFunction<
            COpaquePointer?,
            Int,
            CPointer<ByteVar>?,
            CPointer<ByteVar>?,
            Long,
            Unit,
            > { ctx, type, db, table, rowId ->
        val listener = ctx!!.asStableRef<ConnectionListener>().get()
        listener.onUpdate(
            type,
            db!!.toKString(),
            table!!.toKString(),
            rowId,
        )
    }
