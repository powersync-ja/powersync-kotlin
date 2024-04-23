package com.powersync.db

import app.cash.sqldelight.SuspendingTransactionWithReturn

public interface WriteQueries {

    /**
     * Execute a write query (INSERT, UPDATE, DELETE) and return the number of rows updated for an INSERT/DELETE/UPDATE.
     */
    public suspend fun execute(sql: String, parameters: List<Any?>? = listOf()): Long

    public suspend fun <R> writeTransaction(body: suspend SuspendingTransactionWithReturn<R>.() -> R): R

}