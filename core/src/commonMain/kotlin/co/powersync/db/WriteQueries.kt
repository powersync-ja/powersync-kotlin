package co.powersync.db

import app.cash.sqldelight.SuspendingTransactionWithReturn
import app.cash.sqldelight.SuspendingTransactionWithoutReturn

interface WriteQueries {

    /**
     * Execute a write query (INSERT, UPDATE, DELETE) and return the number of rows updated for an INSERT/DELETE/UPDATE.
     */
    suspend fun execute(sql: String, parameters: List<Any>? = listOf()): Long

    suspend fun writeTransaction(bodyNoReturn: suspend SuspendingTransactionWithoutReturn.() -> Unit)

}