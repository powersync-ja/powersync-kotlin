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
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.util.Properties

@Suppress("SqlNoDataSourceInspection", "SqlSourceToSinkFlow")
internal class PSAndroidJdbcSqliteDriver(
    url: String,
    properties: Properties = Properties(),
) : SqlDriver {
    override fun addListener(
        vararg queryKeys: String,
        listener: Query.Listener,
    ) {
        // No Op, we don't currently use this
    }

    override fun removeListener(
        vararg queryKeys: String,
        listener: Query.Listener,
    ) {
        // No Op, we don't currently use this
    }

    override fun notifyListeners(vararg queryKeys: String) {
        // No Op, we don't currently use this
    }

    val connection: SQLiteConnection =
        DriverManager.getConnection(url, properties) as SQLiteConnection

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
                    connection
                        .prepareStatement("ROLLBACK TRANSACTION")
                        .use(PreparedStatement::execute)
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

    internal fun loadExtensions(vararg extensions: Pair<String, String>) {
        connection.database.enable_load_extension(true)
        extensions.forEach { (path, entryPoint) ->
            val executed =
                connection.prepareStatement("SELECT load_extension(?, ?);").use { statement ->
                    statement.setString(1, path)
                    statement.setString(2, entryPoint)
                    statement.execute()
                }
            check(executed) { "load_extension(\"${path}\", \"${entryPoint}\") failed" }
        }
        connection.database.enable_load_extension(false)
    }
}

internal fun PSAndroidJdbcSqliteDriver(
    url: String,
    properties: Properties = Properties(),
    schema: SqlSchema<QueryResult.Value<Unit>>,
    migrateEmptySchema: Boolean = false,
    vararg callbacks: AfterVersion,
): PSAndroidJdbcSqliteDriver {
    val driver = PSAndroidJdbcSqliteDriver(url, properties)
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

private fun PSAndroidJdbcSqliteDriver.getVersion(): Long {
    val mapper = { cursor: SqlCursor ->
        QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null)
    }
    return executeQuery(null, "PRAGMA user_version", mapper, 0, null).value ?: 0L
}

private fun PSAndroidJdbcSqliteDriver.setVersion(version: Long) {
    execute(null, "PRAGMA user_version = $version", 0, null).value
}
