package com.powersync.sqlite

import cnames.structs.sqlite3
import com.powersync.PowerSyncException
import com.powersync.internal.sqlite3.sqlite3_errmsg
import com.powersync.internal.sqlite3.sqlite3_error_offset
import com.powersync.internal.sqlite3.sqlite3_errstr
import com.powersync.internal.sqlite3.sqlite3_extended_errcode
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.toKStringFromUtf8

internal fun createExceptionInDatabase(
    db: CPointer<sqlite3>,
    sql: String? = null,
): PowerSyncException {
    val extended = sqlite3_extended_errcode(db)
    val offset = sqlite3_error_offset(db).takeIf { it >= 0 }
    val dbMsg = sqlite3_errmsg(db)?.toKStringFromUtf8()
    val errStr = sqlite3_errstr(extended)!!.toKStringFromUtf8()

    val message =
        buildString {
            append("SqliteException(")
            append(extended)
            append("): ")
            append(errStr)

            offset?.let {
                append(" at offset ")
                append(it)
            }

            dbMsg?.let {
                append(", ")
                append(it)
            }

            sql?.let {
                append(" for SQL: ")
                append(it)
            }
        }

    return PowerSyncException(message, null)
}
