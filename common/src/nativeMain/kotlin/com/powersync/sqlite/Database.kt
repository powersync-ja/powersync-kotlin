package com.powersync.sqlite

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import cnames.structs.sqlite3
import cnames.structs.sqlite3_stmt
import com.powersync.PowerSyncException
import com.powersync.internal.sqlite3.sqlite3_close_v2
import com.powersync.internal.sqlite3.sqlite3_db_config
import com.powersync.internal.sqlite3.sqlite3_extended_result_codes
import com.powersync.internal.sqlite3.sqlite3_free
import com.powersync.internal.sqlite3.sqlite3_get_autocommit
import com.powersync.internal.sqlite3.sqlite3_initialize
import com.powersync.internal.sqlite3.sqlite3_load_extension
import com.powersync.internal.sqlite3.sqlite3_open_v2
import com.powersync.internal.sqlite3.sqlite3_prepare16_v3
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKStringFromUtf8
import kotlinx.cinterop.utf16
import kotlinx.cinterop.value

/**
 * A simple implementation of the [SQLiteConnection] interface backed by a synchronous `sqlite3*`
 * database pointer and the SQLite C APIs called via cinterop.
 *
 * Multiple instances of this class are bundled into an
 * [com.powersync.db.driver.InternalConnectionPool] and called from [kotlinx.coroutines.Dispatchers.IO]
 * to make these APIs asynchronous.
 */
public class Database(
    private val ptr: CPointer<sqlite3>,
) : SQLiteConnection {
    override fun inTransaction(): Boolean {
        // We're in a transaction if autocommit is disabled
        return sqlite3_get_autocommit(ptr) == 0
    }

    override fun prepare(sql: String): SQLiteStatement =
        memScoped {
            val stmtPtr = allocPointerTo<sqlite3_stmt>()
            val asUtf16 = sql.utf16
            sqlite3_prepare16_v3(ptr, asUtf16.ptr, asUtf16.size, 0u, stmtPtr.ptr, null)
                .checkResult(sql)

            Statement(sql, ptr, stmtPtr.value!!)
        }

    public fun loadExtension(
        filename: String,
        entrypoint: String,
    ): Unit = memScoped {
        val errorMessagePointer = alloc<CPointerVar<ByteVar>>()
        val resultCode = sqlite3_load_extension(ptr, filename, entrypoint, errorMessagePointer.ptr)

        if (resultCode != 0) {
            val errorMessage = errorMessagePointer.value?.toKStringFromUtf8()
            if (errorMessage != null) {
                sqlite3_free(errorMessagePointer.value)
            }

            throw PowerSyncException("Could not load extension ($resultCode): ${errorMessage ?: "unknown error"}", null)
        }
    }

    override fun close() {
        sqlite3_close_v2(ptr)
    }

    private fun Int.checkResult(stmt: String? = null) {
        if (this != 0) {
            throw createExceptionInDatabase(ptr, stmt)
        }
    }

    public companion object {
        public fun open(
            path: String,
            flags: Int,
        ): Database =
            memScoped {
                var rc = sqlite3_initialize()
                if (rc != 0) {
                    throw PowerSyncException("sqlite3_initialize() failed", null)
                }

                val encodedPath = path.cstr.getPointer(this)
                val ptr = allocPointerTo<sqlite3>()
                rc = sqlite3_open_v2(encodedPath, ptr.ptr, flags, null)
                if (rc != 0) {
                    throw PowerSyncException("Could not open database $path with $flags", null)
                }

                val db = ptr.value!!

                // Enable extended error codes.
                sqlite3_extended_result_codes(db, 1)

                // Enable extensions via the C API
                sqlite3_db_config(db, DBCONFIG_ENABLE_LOAD_EXTENSION, 1, 0)

                Database(db)
            }

        private const val DBCONFIG_ENABLE_LOAD_EXTENSION = 1005
    }
}
