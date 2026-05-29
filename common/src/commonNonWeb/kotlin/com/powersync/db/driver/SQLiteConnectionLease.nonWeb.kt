package com.powersync.db.driver

import androidx.sqlite.SQLiteStatement
import kotlinx.coroutines.runBlocking

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual interface SQLiteConnectionLease {
    /**
     * Queries the autocommit state on the connection.
     */
    public actual suspend fun isInTransaction(): Boolean

    /**
     * Prepares [sql] as statement and runs [block] with it.
     *
     * Block most only run on a single-thread. The statement must not be used once [block] returns.
     */
    public suspend fun <R> usePrepared(
        sql: String,
        block: (SQLiteStatement) -> R,
    ): R

    public actual suspend fun <R> usePreparedAsync(
        sql: String,
        block: suspend (SQLiteStatement) -> R,
    ): R

    public fun isInTransactionSync(): Boolean = runBlocking { isInTransaction() }

    public fun <R> usePreparedSync(
        sql: String,
        block: (SQLiteStatement) -> R,
    ): R =
        runBlocking {
            usePrepared(sql, block)
        }

    public actual suspend fun execSQL(sql: String): Unit =
        usePrepared(sql) {
            it.step()
        }
}
