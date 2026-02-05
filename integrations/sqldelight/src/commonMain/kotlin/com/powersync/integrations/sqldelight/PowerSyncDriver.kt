package com.powersync.integrations.sqldelight

import androidx.sqlite.SQLiteStatement
import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.PowerSyncDatabase
import com.powersync.db.driver.SQLiteConnectionLease
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.locks.SynchronizedObject
import io.ktor.utils.io.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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
@OptIn(ExperimentalPowerSyncAPI::class, InternalAPI::class)
public class PowerSyncDriver(
    private val db: PowerSyncDatabase,
    private val scope: CoroutineScope,
) : SynchronizedObject(),
    SqlDriver {
    private var transaction: PowerSyncTransaction? = null
    private var listeners: MutableMap<Query.Listener, Job> = mutableMapOf()

    private suspend inline fun <T> withConnection(crossinline body: suspend (SQLiteConnectionLease) -> T): T {
        transaction?.let { tx ->
            return body(tx.connection)
        }

        return db.useConnection(readOnly = false) { body(it) }
    }

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> =
        QueryResult.AsyncValue {
            // Despite being a query, this is not guaranteed to be read-only: RETURNING statements
            // also use this.
            // So, always using the write connection is a safe default. In the future we may want to
            // analyze the statement to potentially route it to a read connection if possible.
            withConnection { connection ->
                connection.usePrepared(sql) { stmt ->
                    val wrapper = StatementWrapper(stmt)
                    binders?.let { it(wrapper) }

                    mapper(wrapper).value
                }
            }
        }

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> =
        QueryResult.AsyncValue {
            withConnection { connection ->
                connection.usePrepared(sql) { stmt ->
                    val wrapper = StatementWrapper(stmt)
                    binders?.let { it(wrapper) }

                    while (stmt.step()) {
                        // Keep stepping through statement
                    }
                }

                connection.usePrepared("SELECT changes()") {
                    check(it.step())
                    it.getLong(0)
                }
            }
        }

    override fun newTransaction(): QueryResult<Transacter.Transaction> =
        QueryResult.AsyncValue {
            val tx =
                transaction?.let { outerTx ->
                    PowerSyncTransaction(outerTx)
                } ?: newOutermostTransaction()

            tx.also {
                it.begin()
                transaction = it
            }
        }

    private suspend fun newOutermostTransaction(): PowerSyncTransaction {
        val connectionAvailable = CompletableDeferred<SQLiteConnectionLease>()
        val connectionDone = CompletableDeferred<Unit>()

        scope.launch {
            db.useConnection(readOnly = false) {
                connectionAvailable.complete(it)
                connectionDone.await()
            }
        }

        return PowerSyncTransaction(this, connectionAvailable.await(), connectionDone)
    }

    override fun currentTransaction(): Transacter.Transaction? = transaction

    internal fun endTransaction(transaction: PowerSyncTransaction) {
        check(this.transaction === transaction) {
            "Ending transaction that isn't the latest one"
        }
        this.transaction = transaction.outer
    }

    override fun addListener(
        vararg queryKeys: String,
        listener: Query.Listener,
    ): Unit =
        synchronized(this) {
            val job =
                scope.launch {
                    db.onChange(queryKeys.toSet(), triggerImmediately = false).collect {
                        listener.queryResultsChanged()
                    }
                }
            val previous = listeners.put(listener, job)
            previous?.cancel(CancellationException("Listener has been replaced"))
        }

    override fun removeListener(
        vararg queryKeys: String,
        listener: Query.Listener,
    ): Unit =
        synchronized(this) {
            listeners[listener]?.cancel(CancellationException("Listener has been removed"))
        }

    override fun notifyListeners(vararg queryKeys: String) {
        // Not necessary, PowerSync uses update hooks to notify listeners.
    }

    override fun close() {}
}

@OptIn(ExperimentalPowerSyncAPI::class)
internal class PowerSyncTransaction(
    val driver: PowerSyncDriver,
    val connection: SQLiteConnectionLease,
    private val returnLease: CompletableDeferred<Unit>,
    val depth: Int = 0,
    override val enclosingTransaction: PowerSyncTransaction? = null,
) : Transacter.Transaction() {
    val outer get() = enclosingTransaction

    constructor(outer: PowerSyncTransaction) : this(
        outer.driver,
        outer.connection,
        outer.returnLease,
        outer.depth + 1,
        outer,
    )

    fun end() {
        driver.endTransaction(this)
        if (depth == 0) {
            returnLease.complete(Unit)
        }
    }

    suspend fun begin() {
        if (depth == 0) {
            try {
                connection.execSQL("BEGIN EXCLUSIVE")
            } catch (e: Exception) {
                // Couldn't start transaction -> release connection
                end()
                throw e
            }
        } else {
            connection.execSQL("SAVEPOINT s$depth")
        }
    }

    suspend fun commit() {
        if (depth == 0) {
            connection.execSQL("COMMIT")
            end() // Return lease
        } else {
            connection.execSQL("RELEASE s$depth")
        }
    }

    suspend fun rollback() {
        if (depth == 0) {
            connection.execSQL("ROLLBACK")
            end() // Return lease
        } else {
            connection.execSQL("ROLLBACK TRANSACTION TO SAVEPOINT s$depth")
        }
    }

    override fun endTransaction(successful: Boolean): QueryResult<Unit> =
        QueryResult.AsyncValue {
            if (successful) {
                commit()
            } else {
                rollback()
            }
        }
}

private class StatementWrapper(
    private val stmt: SQLiteStatement,
) : SqlPreparedStatement,
    SqlCursor {
    private inline fun <T> bindNullable(
        index: Int,
        value: T?,
        bind: SQLiteStatement.(Int, T) -> Unit,
    ) {
        if (value == null) {
            stmt.bindNull(index + 1)
        } else {
            stmt.bind(index + 1, value)
        }
    }

    private inline fun <T> readNullable(
        index: Int,
        read: SQLiteStatement.(Int) -> T,
    ): T? =
        if (stmt.isNull(index)) {
            null
        } else {
            stmt.read(index)
        }

    override fun bindBytes(
        index: Int,
        bytes: ByteArray?,
    ) {
        bindNullable(index, bytes, SQLiteStatement::bindBlob)
    }

    override fun bindLong(
        index: Int,
        long: Long?,
    ) {
        bindNullable(index, long, SQLiteStatement::bindLong)
    }

    override fun bindDouble(
        index: Int,
        double: Double?,
    ) {
        bindNullable(index, double, SQLiteStatement::bindDouble)
    }

    override fun bindString(
        index: Int,
        string: String?,
    ) {
        bindNullable(index, string, SQLiteStatement::bindText)
    }

    override fun bindBoolean(
        index: Int,
        boolean: Boolean?,
    ) {
        bindNullable(index, boolean, SQLiteStatement::bindBoolean)
    }

    override fun next(): QueryResult<Boolean> = QueryResult.Value(stmt.step())

    override fun getString(index: Int): String? = readNullable(index, SQLiteStatement::getText)

    override fun getLong(index: Int): Long? = readNullable(index, SQLiteStatement::getLong)

    override fun getBytes(index: Int): ByteArray? = readNullable(index, SQLiteStatement::getBlob)

    override fun getDouble(index: Int): Double? = readNullable(index, SQLiteStatement::getDouble)

    override fun getBoolean(index: Int): Boolean? = readNullable(index, SQLiteStatement::getBoolean)
}
