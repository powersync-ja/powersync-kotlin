package com.powersync

import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.db.SqlSchema
import com.powersync.persistence.driver.JdbcPreparedStatement
import org.sqlite.SQLiteConnection
import java.nio.file.Path
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.util.Properties
import kotlin.io.path.absolutePathString

@Suppress("SqlNoDataSourceInspection", "SqlSourceToSinkFlow")
internal class PSJdbcSqliteDriver(
    url: String,
    properties: Properties = Properties(),
) : SqlDriver {
    private val listeners = linkedMapOf<String, MutableSet<Query.Listener>>()

    override fun addListener(
        vararg queryKeys: String,
        listener: Query.Listener,
    ) {
        synchronized(listeners) {
            queryKeys.forEach {
                listeners.getOrPut(it, { linkedSetOf() }).add(listener)
            }
        }
    }

    override fun removeListener(
        vararg queryKeys: String,
        listener: Query.Listener,
    ) {
        synchronized(listeners) {
            queryKeys.forEach {
                listeners[it]?.remove(listener)
            }
        }
    }

    override fun notifyListeners(vararg queryKeys: String) {
        val listenersToNotify = linkedSetOf<Query.Listener>()
        synchronized(listeners) {
            queryKeys.forEach { listeners[it]?.let(listenersToNotify::addAll) }
        }
        listenersToNotify.forEach(Query.Listener::queryResultsChanged)
    }

    private val connection: SQLiteConnection = DriverManager.getConnection(url, properties) as SQLiteConnection

    private var transaction: Transaction? = null

    private inner class Transaction(
        override val enclosingTransaction: Transaction?,
    ) : Transacter.Transaction() {
        init {
            connection.prepareStatement("BEGIN TRANSACTION").use(PreparedStatement::execute)
        }

        override fun endTransaction(successful: Boolean): QueryResult<Unit> {
            if (enclosingTransaction == null) {
                if (successful) {
                    connection.prepareStatement("END TRANSACTION").use(PreparedStatement::execute)
                } else {
                    connection.prepareStatement("ROLLBACK TRANSACTION").use(PreparedStatement::execute)
                }
            }
            transaction = enclosingTransaction
            return QueryResult.Unit
        }
    }

    override fun newTransaction(): QueryResult<Transacter.Transaction> {
        val newTransaction = Transaction(transaction)
        transaction = newTransaction
        return QueryResult.Value(newTransaction)
    }

    override fun close() {
        connection.close()
    }

    override fun currentTransaction(): Transacter.Transaction? = transaction

    @Synchronized
    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> =
        QueryResult.Value(
            connection.prepareStatement(sql).use {
                val stmt = JdbcPreparedStatement(it)
                binders?.invoke(stmt)
                stmt.execute()
            },
        )

    @Synchronized
    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> =
        connection.prepareStatement(sql).use {
            val stmt = JdbcPreparedStatement(it)
            binders?.invoke(stmt)
            stmt.executeQuery(mapper)
        }

    internal fun loadExtensions(vararg extensions: Pair<Path, String>) {
        connection.database.enable_load_extension(true)
        extensions.forEach { (path, entryPoint) ->
            val executed =
                connection.prepareStatement("SELECT load_extension(?, ?);").use { statement ->
                    statement.setString(1, path.absolutePathString())
                    statement.setString(2, entryPoint)
                    statement.execute()
                }
            check(executed) { "load_extension(\"${path.absolutePathString()}\", \"${entryPoint}\") failed" }
        }
        connection.database.enable_load_extension(false)
    }

    internal fun enableWriteAheadLogging() {
        val executed = connection.prepareStatement("PRAGMA journal_mode=WAL;").execute()
        check(executed) { "journal_mode=WAL failed" }
    }
}

internal fun PSJdbcSqliteDriver(
    url: String,
    properties: Properties = Properties(),
    schema: SqlSchema<QueryResult.Value<Unit>>,
    migrateEmptySchema: Boolean = false,
    vararg callbacks: AfterVersion,
): PSJdbcSqliteDriver {
    val driver = PSJdbcSqliteDriver(url, properties)
    val version = driver.getVersion()

    if (version == 0L && !migrateEmptySchema) {
        schema.create(driver).value
        driver.setVersion(schema.version)
    } else if (version < schema.version) {
        schema.migrate(driver, version, schema.version, *callbacks).value
        driver.setVersion(schema.version)
    }

    return driver
}

private fun PSJdbcSqliteDriver.getVersion(): Long {
    val mapper = { cursor: SqlCursor ->
        QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null)
    }
    return executeQuery(null, "PRAGMA user_version", mapper, 0, null).value ?: 0L
}

private fun PSJdbcSqliteDriver.setVersion(version: Long) {
    execute(null, "PRAGMA user_version = $version", 0, null).value
}
