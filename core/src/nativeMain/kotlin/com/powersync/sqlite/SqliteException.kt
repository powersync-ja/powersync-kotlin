package com.powersync.sqlite

import cnames.structs.sqlite3
import com.powersync.internal.sqlite3.sqlite3_errmsg
import com.powersync.internal.sqlite3.sqlite3_error_offset
import com.powersync.internal.sqlite3.sqlite3_errstr
import com.powersync.internal.sqlite3.sqlite3_extended_errcode
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.toKStringFromUtf8

internal class SqliteException(
    val code: Int,
    message: String,
    val extendedErrorCode: Int? = null,
    val offset: Int? = null,
    val dbMessage: String? = null,
    val sql: String? = null
): Exception(message) {

    override fun toString(): String {
        return buildString {
            append("SqliteException(")
            append(extendedErrorCode ?: code)
            append("): ")
            append(message)

            offset?.let {
                append(" at offset")
                append(it)
            }

            dbMessage?.let {
                append(", ")
                append(it)
            }

            sql?.let {
                append("for SQL: ")
                append(it)
            }
        }
    }

    companion object {
        fun createExceptionOutsideOfDatabase(code: Int): SqliteException {
            return SqliteException(code, sqlite3_errstr(code)!!.toKStringFromUtf8())
        }

        fun createExceptionInDatabase(code: Int, db: CPointer<sqlite3>, sql: String? = null): SqliteException {
            val extended = sqlite3_extended_errcode(db)
            val offset = sqlite3_error_offset(db).takeIf { it >= 0 }
            val dbMsg = sqlite3_errmsg(db)?.toKStringFromUtf8()
            val errStr = sqlite3_errstr(extended)!!.toKStringFromUtf8()

            return SqliteException(
                code,
                errStr,
                extended,
                offset,
                dbMsg,
                sql,
            )
        }
    }
}
