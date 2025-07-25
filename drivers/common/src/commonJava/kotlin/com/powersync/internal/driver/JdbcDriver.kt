package com.powersync.internal.driver

import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import org.sqlite.SQLiteCommitListener
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteOpenMode
import org.sqlite.SQLiteUpdateListener
import org.sqlite.jdbc4.JDBC4Connection
import org.sqlite.jdbc4.JDBC4PreparedStatement
import org.sqlite.jdbc4.JDBC4ResultSet
import java.sql.Types
import java.util.Properties

public open class JdbcDriver : PowerSyncDriver {
    internal open fun addDefaultProperties(properties: Properties) {}

    override fun openDatabase(
        path: String,
        readOnly: Boolean,
        listener: ConnectionListener?,
    ): SQLiteConnection {
        val properties =
            Properties().also {
                it.setProperty(
                    SQLiteConfig.Pragma.OPEN_MODE.pragmaName,
                    if (readOnly) {
                        SQLiteOpenMode.READONLY.flag
                    } else {
                        SQLiteOpenMode.READWRITE.flag or SQLiteOpenMode.CREATE.flag
                    }.toString(),
                )
            }

        val inner = JDBC4Connection(path, path, properties)
        listener?.let {
            inner.addCommitListener(
                object : SQLiteCommitListener {
                    override fun onCommit() {
                        it.onCommit()
                    }

                    override fun onRollback() {
                        it.onRollback()
                    }
                },
            )

            inner.addUpdateListener { type, database, table, rowId ->
                val flags =
                    when (type) {
                        SQLiteUpdateListener.Type.INSERT -> SQLITE_INSERT
                        SQLiteUpdateListener.Type.DELETE -> SQLITE_DELETE
                        SQLiteUpdateListener.Type.UPDATE -> SQLITE_UPDATE
                    }

                it.onUpdate(flags, database, table, rowId)
            }
        }

        return JdbcConnection(inner)
    }

    private companion object {
        const val SQLITE_DELETE: Int = 9
        const val SQLITE_INSERT: Int = 18
        const val SQLITE_UPDATE: Int = 23
    }
}

public class JdbcConnection(
    public val connection: org.sqlite.SQLiteConnection,
) : SQLiteConnection {
    override fun inTransaction(): Boolean {
        // TODO: Unsupported with sqlite-jdbc?
        return true
    }

    override fun prepare(sql: String): SQLiteStatement = PowerSyncStatement(connection.prepareStatement(sql) as JDBC4PreparedStatement)

    override fun close() {
        connection.close()
    }
}

private class PowerSyncStatement(
    private val stmt: JDBC4PreparedStatement,
) : SQLiteStatement {
    private var currentCursor: JDBC4ResultSet? = null

    private val _columnCount: Int by lazy {
        // We have to call this manually because stmt.metadata.columnCount throws an exception when
        // a statement has zero columns.
        stmt.pointer.safeRunInt<Nothing> { db, ptr -> db.column_count(ptr) }
    }

    private fun requireCursor(): JDBC4ResultSet =
        requireNotNull(currentCursor) {
            "Illegal call which requires cursor, step() hasn't been called"
        }

    override fun bindBlob(
        index: Int,
        value: ByteArray,
    ) {
        stmt.setBytes(index, value)
    }

    override fun bindDouble(
        index: Int,
        value: Double,
    ) {
        stmt.setDouble(index, value)
    }

    override fun bindLong(
        index: Int,
        value: Long,
    ) {
        stmt.setLong(index, value)
    }

    override fun bindText(
        index: Int,
        value: String,
    ) {
        stmt.setString(index, value)
    }

    override fun bindNull(index: Int) {
        stmt.setNull(index, Types.NULL)
    }

    override fun getBlob(index: Int): ByteArray = requireCursor().getBytes(index + 1)

    override fun getDouble(index: Int): Double = requireCursor().getDouble(index + 1)

    override fun getLong(index: Int): Long = requireCursor().getLong(index + 1)

    override fun getText(index: Int): String = requireCursor().getString(index + 1)

    override fun isNull(index: Int): Boolean = getColumnType(index) == SQLITE_DATA_NULL

    override fun getColumnCount(): Int = _columnCount

    override fun getColumnName(index: Int): String = stmt.metaData.getColumnName(index + 1)

    override fun getColumnType(index: Int): Int = stmt.pointer.safeRunInt<Nothing> { db, ptr -> db.column_type(ptr, index) }

    override fun step(): Boolean {
        if (currentCursor == null) {
            if (_columnCount == 0) {
                // sqlite-jdbc refuses executeQuery calls for statements that don't return results
                stmt.execute()
                return false
            } else {
                currentCursor = stmt.executeQuery() as JDBC4ResultSet
            }
        }

        return currentCursor!!.next()
    }

    override fun reset() {
        currentCursor?.close()
        currentCursor = null
    }

    override fun clearBindings() {
        stmt.clearParameters()
    }

    override fun close() {
        currentCursor?.close()
        currentCursor = null
        stmt.close()
    }
}
