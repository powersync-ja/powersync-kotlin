package com.powersync.integrations.room

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.PowerSyncDatabase
import kotlinx.coroutines.runBlocking

/**
 * A [SQLiteDriver] that is backed by an existing [PowerSyncDatabase].
 *
 * In [open], the `fileName` parameter will always be ignored. Instead, all connections are
 * implemented by temporarily leasing a connection from the connection pool managed by PowerSync.
 */
public class PowerSyncRoomDriver(
    private val db: PowerSyncDatabase,
): SQLiteDriver {
    override val hasConnectionPool: Boolean
        // The PowerSync database has a connection pool internally, so Room shouldn't roll its own.
        get() = true

    override fun open(fileName: String): SQLiteConnection {
        return PowerSyncConnection(db)
    }
}

private class PowerSyncConnection(
    private val db: PowerSyncDatabase,
): SQLiteConnection {
    // We lazily request an underlying SQLite connection when necessary, and release it as quickly
    // as possible so that other concurrent PowerSync operations can run.
    private var currentLease: SQLiteConnection? = null
    private var inTransaction = false

    @OptIn(ExperimentalPowerSyncAPI::class)
    private fun obtainConnection(): SQLiteConnection {
        currentLease?.let { return it }

        return runBlocking { db.leaseConnection(readOnly = false) }.also {
            currentLease = it
        }
    }

    private fun returnConnection() {
        currentLease?.close()
        currentLease = null
    }

    override fun prepare(sql: String): SQLiteStatement {
        val lower = sql.lowercase()
        if (ignoredPragma.matches(lower)) {
            // PowerSync actually uses custom pragmas
            return FakeStatement(sql)
        }

        val connection = obtainConnection()
        // TODO: If we had a reliable way to get the autocommit state (we don't have it for
        //  sqlite-jdbc), we could remove this hacky check.
        if (lower.startsWith("begin")) {
            inTransaction = true
        }
        if (lower.startsWith("end transaction") || lower.startsWith("rollback")) {
            inTransaction = false
        }

        return CompletableStatement(connection.prepare(sql)) {
            if (!inTransaction) {
                returnConnection()
            }
        }
    }

    override fun close() {
        returnConnection()
    }

    private companion object {
        val ignoredPragma = Regex("pragma .*=.*")
    }
}

private class CompletableStatement(
    private val stmt: SQLiteStatement,
    private val onClose: () -> Unit,
): SQLiteStatement by stmt {
    override fun close() {
        stmt.close()
        onClose()
    }
}

private class FakeStatement(val sql: String): SQLiteStatement {
    private fun stub(): Nothing {
        throw UnsupportedOperationException("Fake statement: $sql")
    }

    override fun bindBlob(index: Int, value: ByteArray) {
    }

    override fun bindDouble(index: Int, value: Double) {

    }

    override fun bindLong(index: Int, value: Long) {
    }

    override fun bindText(index: Int, value: String) {
    }

    override fun bindNull(index: Int) {
    }

    override fun getBlob(index: Int): ByteArray {
        stub()
    }

    override fun getDouble(index: Int): Double {
        stub()
    }

    override fun getLong(index: Int): Long {
        // make pragma user_version return 1 so that room doesn't try to migrate.
        return 1L
    }

    override fun getText(index: Int): String {
        stub()
    }

    override fun isNull(index: Int): Boolean {
        stub()
    }

    override fun getColumnCount(): Int {
        stub()
    }

    override fun getColumnName(index: Int): String {
        stub()
    }

    override fun getColumnType(index: Int): Int {
        stub()
    }

    override fun step(): Boolean {
        return false
    }

    override fun reset() {

    }

    override fun clearBindings() {

    }

    override fun close() {

    }
}
