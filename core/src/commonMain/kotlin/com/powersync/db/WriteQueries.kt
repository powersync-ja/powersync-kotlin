package com.powersync.db

import app.cash.sqldelight.SuspendingTransactionWithReturn

interface WriteQueries {

    /**
     * Execute a write query (INSERT, UPDATE, DELETE) and return the number of rows updated for an INSERT/DELETE/UPDATE.
     */
    suspend fun execute(sql: String, parameters: List<Any>? = listOf()): Long

    suspend fun executeWrite(sql: String, parameters: List<Any>?): Long

    suspend fun <R> writeTransaction(body: suspend SuspendingTransactionWithReturn<R>.() -> R): R

}