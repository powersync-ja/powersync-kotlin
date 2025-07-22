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

public open class JdbcDriver: PowerSyncDriver {
    internal open fun addDefaultProperties(properties: Properties) {}

    override fun openDatabase(
        path: String,
        readOnly: Boolean,
        listener: ConnectionListener?
    ): SQLiteConnection {
        val properties = Properties().also {
            it.setProperty(SQLiteConfig.Pragma.OPEN_MODE.pragmaName, if (readOnly) {
                SQLiteOpenMode.READONLY.flag
            } else {
                SQLiteOpenMode.READWRITE.flag or SQLiteOpenMode.CREATE.flag
            }.toString())
        }

        val inner = JDBC4Connection(path, path, properties)
        listener?.let {
            inner.addCommitListener(object: SQLiteCommitListener {
                override fun onCommit() {
                    it.onCommit()
                }

                override fun onRollback() {
                    it.onRollback()
                }
            })

            inner.addUpdateListener { type, database, table, rowId ->
                val flags = when (type) {
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

public class JdbcConnection(public val connection: org.sqlite.SQLiteConnection): SQLiteConnection {
    override fun inTransaction(): Boolean {
        return !connection.autoCommit
    }

    override fun prepare(sql: String): SQLiteStatement {
        return PowerSyncStatement(connection.prepareStatement(sql) as JDBC4PreparedStatement)
    }

    override fun close() {
        connection.close()
    }
}

private class PowerSyncStatement(
    private val stmt: JDBC4PreparedStatement,
): SQLiteStatement {
    private var currentCursor: JDBC4ResultSet? = null

    private fun requireCursor(): JDBC4ResultSet {
        return requireNotNull(currentCursor) {
            "Illegal call which requires cursor, step() hasn't been called"
        }
    }

    override fun bindBlob(index: Int, value: ByteArray) {
        stmt.setBytes(index , value)
    }

    override fun bindDouble(index: Int, value: Double) {
        stmt.setDouble(index, value)
    }

    override fun bindLong(index: Int, value: Long) {
        stmt.setLong(index, value)
    }

    override fun bindText(index: Int, value: String) {
        stmt.setString(index, value)
    }

    override fun bindNull(index: Int) {
        stmt.setNull(index, Types.NULL)
    }

    override fun getBlob(index: Int): ByteArray {
        return requireCursor().getBytes(index)
    }

    override fun getDouble(index: Int): Double {
        return requireCursor().getDouble(index)
    }

    override fun getLong(index: Int): Long {
        return requireCursor().getLong(index)
    }

    override fun getText(index: Int): String {
        return requireCursor().getString(index  )
    }

    override fun isNull(index: Int): Boolean {
        return getColumnType(index) == SQLITE_DATA_NULL
    }

    override fun getColumnCount(): Int {
        return currentCursor!!.metaData.columnCount
    }

    override fun getColumnName(index: Int): String {
        return stmt.metaData.getColumnName(index)
    }

    override fun getColumnType(index: Int): Int {
        return stmt.pointer.safeRunInt<Nothing> { db, ptr -> db.column_type(ptr, index  ) }
    }

    override fun step(): Boolean {
        if (currentCursor == null) {
            currentCursor = stmt.executeQuery() as JDBC4ResultSet
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
        stmt.close()
    }
}
