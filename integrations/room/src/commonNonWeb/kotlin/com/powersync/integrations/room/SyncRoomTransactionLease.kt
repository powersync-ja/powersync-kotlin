package com.powersync.integrations.room

import androidx.room3.Transactor
import androidx.sqlite.SQLiteStatement
import com.powersync.db.driver.SQLiteConnectionLease
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext

internal actual suspend fun Transactor.asConnectionLease(): SQLiteConnectionLease =
    SyncRoomTransactionLease(this, currentCoroutineContext())

private class SyncRoomTransactionLease(
    transactor: Transactor,
    private val context: CoroutineContext,
) : RoomTransactionLease(transactor) {
    override suspend fun <R> usePrepared(
        sql: String,
        block: (SQLiteStatement) -> R,
    ): R = transactor.usePrepared(sql, block)

    override fun isInTransactionSync(): Boolean =
        runBlocking(context) {
            isInTransaction()
        }

    override fun <R> usePreparedSync(
        sql: String,
        block: (SQLiteStatement) -> R,
    ): R =
        runBlocking(context) {
            usePrepared(sql, block)
        }
}
