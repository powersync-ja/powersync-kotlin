package com.powersync.sqlite

import androidx.sqlite.SQLiteStatement
import cnames.structs.sqlite3
import cnames.structs.sqlite3_stmt
import com.powersync.internal.sqlite3.sqlite3_bind_blob64
import com.powersync.internal.sqlite3.sqlite3_bind_double
import com.powersync.internal.sqlite3.sqlite3_bind_int64
import com.powersync.internal.sqlite3.sqlite3_bind_null
import com.powersync.internal.sqlite3.sqlite3_bind_text16
import com.powersync.internal.sqlite3.sqlite3_clear_bindings
import com.powersync.internal.sqlite3.sqlite3_column_blob
import com.powersync.internal.sqlite3.sqlite3_column_bytes
import com.powersync.internal.sqlite3.sqlite3_column_count
import com.powersync.internal.sqlite3.sqlite3_column_double
import com.powersync.internal.sqlite3.sqlite3_column_int64
import com.powersync.internal.sqlite3.sqlite3_column_name
import com.powersync.internal.sqlite3.sqlite3_column_text16
import com.powersync.internal.sqlite3.sqlite3_column_type
import com.powersync.internal.sqlite3.sqlite3_finalize
import com.powersync.internal.sqlite3.sqlite3_reset
import com.powersync.internal.sqlite3.sqlite3_step
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toKStringFromUtf16
import kotlinx.cinterop.toKStringFromUtf8
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.utf16

@OptIn(ExperimentalForeignApi::class)
internal class Statement(
    private val sql: String,
    private val db: CPointer<sqlite3>,
    private val ptr: CPointer<sqlite3_stmt>,
) : SQLiteStatement {
    override fun bindBlob(
        index: Int,
        value: ByteArray,
    ) {
        value.usePinned { pinned ->
            val valuePtr = pinned.addressOf(0)
            sqlite3_bind_blob64(
                ptr,
                index,
                valuePtr,
                value.size.toULong(),
                DESTRUCTOR_TRANSIENT,
            ).checkResult()
        }
    }

    override fun bindDouble(
        index: Int,
        value: Double,
    ) {
        sqlite3_bind_double(ptr, index, value).checkResult()
    }

    override fun bindLong(
        index: Int,
        value: Long,
    ) {
        sqlite3_bind_int64(ptr, index, value).checkResult()
    }

    override fun bindText(
        index: Int,
        value: String,
    ) {
        memScoped {
            val utf16 = value.utf16
            sqlite3_bind_text16(ptr, index, utf16.ptr.reinterpret(), utf16.size - 1, DESTRUCTOR_TRANSIENT).checkResult()
        }
    }

    override fun bindNull(index: Int) {
        sqlite3_bind_null(ptr, index).checkResult()
    }

    override fun getBlob(index: Int): ByteArray {
        val len = sqlite3_column_bytes(ptr, index.columnIndex())
        if (len == 0) {
            return byteArrayOf()
        }

        val buf = sqlite3_column_blob(ptr, index)!!
        return buf.reinterpret<ByteVar>().readBytes(len) // Note: this copies
    }

    override fun getDouble(index: Int): Double = sqlite3_column_double(ptr, index.columnIndex())

    override fun getLong(index: Int): Long = sqlite3_column_int64(ptr, index.columnIndex())

    override fun getText(index: Int): String {
        val value = sqlite3_column_text16(ptr, index.columnIndex())
        if (value == null) {
            return ""
        }

        return value.reinterpret<UShortVar>().toKStringFromUtf16()
    }

    override fun isNull(index: Int): Boolean = sqlite3_column_type(ptr, index.columnIndex()) == SQLITE_NULL

    override fun getColumnCount(): Int = sqlite3_column_count(ptr)

    override fun getColumnName(index: Int): String = sqlite3_column_name(ptr, index.columnIndex())!!.toKStringFromUtf8()

    override fun getColumnType(index: Int): Int = sqlite3_column_type(ptr, index.columnIndex())

    override fun step(): Boolean =
        when (val rc = sqlite3_step(ptr)) {
            SQLITE_ROW -> true
            SQLITE_DONE -> false
            else -> throwException(rc)
        }

    override fun reset() {
        sqlite3_reset(ptr).checkResult()
    }

    override fun clearBindings() {
        sqlite3_clear_bindings(ptr).checkResult()
    }

    override fun close() {
        sqlite3_finalize(ptr).checkResult()
    }

    private fun Int.checkResult() {
        if (this != 0) {
            throwException(this)
        }
    }

    private fun Int.columnIndex(): Int {
        if (this < 0 || this >= getColumnCount()) {
            throw IllegalArgumentException("Invalid column index: $this")
        }

        return this
    }

    private fun throwException(errorCode: Int): Nothing = throw createExceptionInDatabase(db, sql)

    internal companion object {
        const val SQLITE_INTEGER = 1
        const val SQLITE_FLOAT = 2
        const val SQLITE_TEXT = 3
        const val SQLITE_BLOB = 4
        const val SQLITE_NULL = 5

        const val SQLITE_ROW = 100
        const val SQLITE_DONE = 101

        val DESTRUCTOR_TRANSIENT: COpaquePointer = (-1L).toCPointer<CPointed>()!!
    }
}
