package com.powersync.sqlite

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import cnames.structs.sqlite3
import cnames.structs.sqlite3_stmt
import com.powersync.PowerSyncException
import com.powersync.internal.sqlite3.sqlite3_close_v2
import com.powersync.internal.sqlite3.sqlite3_get_autocommit
import com.powersync.internal.sqlite3.sqlite3_initialize
import com.powersync.internal.sqlite3.sqlite3_open_v2
import com.powersync.internal.sqlite3.sqlite3_prepare_v3
import com.powersync.internal.sqlite3.sqlite3_db_config
import com.powersync.internal.sqlite3.sqlite3_free
import com.powersync.internal.sqlite3.sqlite3_load_extension
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKStringFromUtf8
import kotlinx.cinterop.utf16
import kotlinx.cinterop.value

internal class Database(private val ptr: CPointer<sqlite3>): SQLiteConnection {
    override fun inTransaction(): Boolean {
        // We're in a transaction if autocommit is disabled
        return sqlite3_get_autocommit(ptr) == 0
    }

    override fun prepare(sql: String): SQLiteStatement = memScoped {
        val stmtPtr = allocPointerTo<sqlite3_stmt>()
        val asUtf16 = sql.utf16
        sqlite3_prepare_v3(ptr, asUtf16.ptr.reinterpret(), asUtf16.size, 0u, stmtPtr.ptr, null).checkResult()

        Statement(sql, ptr, stmtPtr.value!!)
    }

    fun loadExtension(filename: String, entrypoint: String) = memScoped {
        val errorMessagePointer = alloc<CPointerVar<ByteVar>>()
        val resultCode = sqlite3_load_extension(ptr, filename, entrypoint, errorMessagePointer.ptr)

        if (resultCode != 0) {
            val errorMessage = errorMessagePointer.value?.toKStringFromUtf8()
            if (errorMessage != null) {
                sqlite3_free(errorMessagePointer.value)
            }

            throw SqliteException(resultCode, errorMessage ?: "unknown error")
        }
    }

    override fun close() {
        sqlite3_close_v2(ptr)
    }

    private fun Int.checkResult() {
        if (this != 0) {
            throw PowerSyncException("SQLite error", SqliteException.createExceptionInDatabase(this, ptr))
        }
    }

    companion object {
        fun open(path: String, flags: Int): Database = memScoped {
            var rc = sqlite3_initialize()
            if (rc != 0) {
                throw SqliteException.createExceptionOutsideOfDatabase(rc)
            }

            val encodedPath = path.cstr.getPointer(this)
            val ptr = allocPointerTo<sqlite3>()
            rc = sqlite3_open_v2(encodedPath, ptr.ptr, flags, null)
            if (rc != 0) {
                throw SqliteException.createExceptionOutsideOfDatabase(rc)
            }

            val db = ptr.value!!
            // Enable extensions via the C API
            sqlite3_db_config(db, DBCONFIG_ENABLE_LOAD_EXTENSION, 1, 0)

            Database(db)
        }

        private const val DBCONFIG_ENABLE_LOAD_EXTENSION = 1005
    }
}
