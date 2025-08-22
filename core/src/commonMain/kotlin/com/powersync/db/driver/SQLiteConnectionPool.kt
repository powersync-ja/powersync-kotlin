package com.powersync.db.driver

import androidx.sqlite.SQLiteStatement
import com.powersync.ExperimentalPowerSyncAPI
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.runBlocking

@ExperimentalPowerSyncAPI()
public interface SQLiteConnectionPool {
    public suspend fun <T> read(callback: suspend (SQLiteConnectionLease) -> T): T
    public suspend fun <T> write(callback: suspend (SQLiteConnectionLease) -> T): T

    public suspend fun <R> withAllConnections(action: suspend (
        writer: SQLiteConnectionLease,
        readers: List<SQLiteConnectionLease>
    ) -> R)

    public val updates: SharedFlow<Set<String>>

    public suspend fun close()
}

@ExperimentalPowerSyncAPI
public interface SQLiteConnectionLease {
    /**
     * Queries the autocommit state on the connection.
     */
    public suspend fun isInTransaction(): Boolean

    public fun isInTransactionSync(): Boolean {
        return runBlocking { isInTransaction() }
    }

    /**
     * Prepares [sql] as statement and runs [block] with it.
     *
     * Block most only run on a single-thread. The statement must not be used once [block] returns.
     */
    public suspend fun <R> usePrepared(sql: String, block: (SQLiteStatement) -> R): R

    public fun <R> usePreparedSync(sql: String, block: (SQLiteStatement) -> R): R {
        return runBlocking {
            usePrepared(sql, block)
        }
    }

    public suspend fun execSQL(sql: String) {
        usePrepared(sql) {
            it.step()
        }
    }
}
