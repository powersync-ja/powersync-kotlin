package com.powersync.db.driver

import androidx.sqlite.SQLiteStatement

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual interface SQLiteConnectionLease {
    public actual suspend fun isInTransaction(): Boolean

    public actual suspend fun <R> usePreparedAsync(
        sql: String,
        block: suspend (SQLiteStatement) -> R,
    ): R

    public actual suspend fun execSQL(sql: String) {
        usePreparedAsync(sql) {
            it.step()
        }
    }
}
