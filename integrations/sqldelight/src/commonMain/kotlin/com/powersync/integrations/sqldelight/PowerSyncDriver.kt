package com.powersync.integrations.sqldelight

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL
import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.PowerSyncDatabase
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A driver for SQLDelight that delegates queries to an opened PowerSync database.
 *
 * Writes made through SQLDelight will trigger entries in the CRUD queue for PowerSync, allowing
 * them to be uploaded.
 * Similarly, writes made on the PowerSync database (both locally and those made during syncing)
 * will update SQLDelight queries and flows.
 *
 * This driver implements [SqlDriver] and can be passed to constructors of your SQLDelight database.
 * Please see the readme of this library for more details to be aware of.
 */
@OptIn(ExperimentalPowerSyncAPI::class)
public class PowerSyncDriver(
    private val db: PowerSyncDatabase,
    private val scope: CoroutineScope,
): SynchronizedObject(), SqlDriver {

    private var transaction: PowerSyncTransaction? = null
    private var listeners: MutableMap<Query.Listener, Job> = mutableMapOf()

    private suspend inline fun <T> withConnection(body: (SQLiteConnection) -> T): T {
        transaction?.let { tx ->
            return body(tx.connection)
        }

        return db.leaseConnection(readOnly = false).use(body)
    }

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?
    ): QueryResult<R> {
        return QueryResult.AsyncValue {
            withConnection { connection ->
                connection.prepare(sql).use { stmt ->
                    val wrapper = StatementWrapper(stmt)
                    binders?.let { it(wrapper) }

                    mapper(wrapper).await()
                }
            }
        }
    }

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?
    ): QueryResult<Long> {
        return QueryResult.AsyncValue {
            withConnection { connection ->
                connection.prepare(sql).use { stmt ->
                    val wrapper = StatementWrapper(stmt)
                    binders?.let { it(wrapper) }

                    while (stmt.step()) {
                        // Keep stepping through statement
                    }

                    0L
                }
            }
        }
    }

    override fun newTransaction(): QueryResult<Transacter.Transaction> {
        return QueryResult.AsyncValue {
            val tx = transaction?.let { outerTx ->
                PowerSyncTransaction(outerTx)
            } ?: PowerSyncTransaction(db.leaseConnection(readOnly = false))

            tx.also {
                it.begin()
                transaction = it
            }
        }
    }

    override fun currentTransaction(): Transacter.Transaction? {
        return transaction
    }

    override fun addListener(
        vararg queryKeys: String,
        listener: Query.Listener
    ): Unit = synchronized(this) {
        val job = scope.launch {
            db.onChange(queryKeys.toSet(), triggerImmediately = false).collect {
                listener.queryResultsChanged()
            }
        }
        val previous = listeners.put(listener, job)
        previous?.cancel(CancellationException("Listener has been replaced"))
    }

    override fun removeListener(
        vararg queryKeys: String,
        listener: Query.Listener
    ): Unit = synchronized(this) {
        listeners[listener]?.cancel(CancellationException("Listener has been removed"))
    }

    override fun notifyListeners(vararg queryKeys: String) {
        // Not necessary, PowerSync uses update hooks to notify listeners.
    }

    override fun close() {}
}

private class PowerSyncTransaction(
    val connection: SQLiteConnection,
    val depth: Int = 0,
    override val enclosingTransaction: Transacter.Transaction? = null
) : Transacter.Transaction() {
    constructor(outer: PowerSyncTransaction) : this(
        outer.connection,
        outer.depth + 1,
        outer
    )

    fun begin() {
        if (depth == 0) {
            try {
                connection.execSQL("BEGIN EXCLUSIVE")
            } catch (e: Exception) {
                // Couldn't start transaction -> release connection
                connection.close()
                throw e
            }

        } else {
            connection.execSQL("SAVEPOINT s${depth}")
        }
    }

    fun commit() {
        if (depth == 0) {
            connection.execSQL("COMMIT")
            connection.close() // Return lease
        } else {
            connection.execSQL("RELEASE s${depth}")
        }
    }

    fun rollback() {
        if (depth == 0) {
            connection.execSQL("ROLLBACK")
            connection.close() // Return lease
        } else {
            connection.execSQL("ROLLBACK TRANSACTION TO SAVEPOINT s${depth}")
        }
    }

    override fun endTransaction(successful: Boolean): QueryResult<Unit> {
        if (successful) {
            commit()
        } else {
            rollback()
        }

        return QueryResult.Unit
    }
}

private class StatementWrapper(private val stmt: SQLiteStatement): SqlPreparedStatement, SqlCursor {
    private inline fun <T> bindNullable(index: Int, value: T?, bind: SQLiteStatement.(Int, T) -> Unit) {
        if (value == null) {
            stmt.bindNull(index + 1)
        } else {
            stmt.bind(index + 1, value)
        }
    }

    private inline fun <T> readNullable(index: Int, read: SQLiteStatement.(Int) -> T): T? {
        return if (stmt.isNull(index)) {
            null
        } else {
            stmt.read(index)
        }
    }

    override fun bindBytes(index: Int, bytes: ByteArray?) {
        bindNullable(index, bytes, SQLiteStatement::bindBlob)
    }

    override fun bindLong(index: Int, long: Long?) {
        bindNullable(index, long, SQLiteStatement::bindLong)
    }

    override fun bindDouble(index: Int, double: Double?) {
        bindNullable(index, double, SQLiteStatement::bindDouble)
    }

    override fun bindString(index: Int, string: String?) {
        bindNullable(index, string, SQLiteStatement::bindText)
    }

    override fun bindBoolean(index: Int, boolean: Boolean?) {
        bindNullable(index, boolean, SQLiteStatement::bindBoolean)
    }

    override fun next(): QueryResult<Boolean> {
        return QueryResult.Value(stmt.step())
    }

    override fun getString(index: Int): String? {
        return readNullable(index, SQLiteStatement::getText)
    }

    override fun getLong(index: Int): Long? {
        return readNullable(index, SQLiteStatement::getLong)
    }

    override fun getBytes(index: Int): ByteArray? {
        return readNullable(index, SQLiteStatement::getBlob)
    }

    override fun getDouble(index: Int): Double? {
        return readNullable(index, SQLiteStatement::getDouble)
    }

    override fun getBoolean(index: Int): Boolean? {
        return readNullable(index, SQLiteStatement::getBoolean)
    }
}
