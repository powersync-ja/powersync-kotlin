@file:OptIn(ExperimentalWasmJsInterop::class, InternalPowerSyncAPI::class)

package com.powersync.web

import androidx.sqlite.SQLiteStatement
import com.powersync.db.driver.SQLiteConnectionLease
import com.powersync.db.driver.SQLiteConnectionPool
import com.powersync.internal.InternalPowerSyncAPI

import kotlinx.coroutines.await
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.promise
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.JsBigInt
import kotlin.js.JsNumber
import kotlin.js.JsReference
import kotlin.js.JsString
import kotlin.js.get
import kotlin.js.js
import kotlin.js.length
import kotlin.js.set
import kotlin.js.toBoolean
import kotlin.js.toDouble
import kotlin.js.toInt
import kotlin.js.toJsArray
import kotlin.js.toJsBigInt
import kotlin.js.toJsNumber
import kotlin.js.toJsReference
import kotlin.js.toJsString
import kotlin.js.toList
import kotlin.js.toLong

internal class WorkerConnectionPool(
    private val factory: WebConnectionFactory,
    internal var streamUpdatesId: String,
    private val db: Database
): SQLiteConnectionPool {

    internal val updatesController = MutableSharedFlow<Set<String>>()

    override val updates: SharedFlow<Set<String>>
        get() = updatesController.asSharedFlow()

    private suspend fun <T> lock(callback: suspend (SQLiteConnectionLease) -> T): T {
        return coroutineScope {
            val promise = db.requestLock { token ->
                val id = token.toInt()
                promise {
                    callback(Lease(id))?.toJsReference()
                }
            }

            val result = promise.await() as JsReference<*>
            @Suppress("UNCHECKED_CAST")
            result.get() as T
        }
    }

    override suspend fun <T> read(callback: suspend (SQLiteConnectionLease) -> T): T {
        return lock(callback)
    }

    override suspend fun <T> write(callback: suspend (SQLiteConnectionLease) -> T): T {
        return lock(callback)
    }

    override suspend fun <R> withAllConnections(action: suspend (writer: SQLiteConnectionLease, readers: List<SQLiteConnectionLease>) -> R) {
        lock { action(it, emptyList()) }
    }

    override suspend fun close() {
        factory.markClosed(this)
        db.close().await()
    }

    private inner class Lease(val lockToken: Int): SQLiteConnectionLease {
        var inTransaction = false

        override suspend fun isInTransaction(): Boolean = inTransaction

        override suspend fun <R> usePreparedAsync(
            sql: String,
            block: suspend (SQLiteStatement) -> R
        ): R {
            val stmt = VirtualWorkerStatement { params ->
                val result = db.select(sql.toJsString(), databaseExecuteOptions(
                    params,
                    lockToken.toJsNumber(),
                    null,
                )).await()

                inTransaction = !result.autocommit.toBoolean()
                result.result
            }
            return block(stmt)
        }

        override suspend fun execSQL(sql: String) {
            val result = db.execute(sql.toJsString(), databaseExecuteOptions(
                JsArray(),
                lockToken.toJsNumber(),
                null
            )).await()
            inTransaction = !result.autocommit.toBoolean()
        }
    }
}

/**
 * A "pretend" statement implementation that will internally use a single select call to execute.
 *
 * We might replace this with a proper statement implementation in the future, but that would
 * require worker changes.
 */
private class VirtualWorkerStatement(
    private val select: suspend (JsArray<JsAny?>) -> ResultSet
): SQLiteStatement {
    private val parameters = mutableListOf<JsAny?>()
    var resultSet: CopiedResultSet? = null

    private fun ensureParameterCapacity(index: Int) {
        check(index > 0) // 1-based index

        if (parameters.size < index) {
            repeat(index - parameters.size) {
                parameters.add(null)
            }
        }
    }

    override fun bindBlob(index: Int, value: ByteArray) {
        ensureParameterCapacity(index)
        val bytes = newUint8Array(value.size.toJsNumber())
        value.forEachIndexed { i, byte -> bytes[i] = byte.toInt().toJsNumber() }

        parameters[index - 1] = bytes
    }

    override fun bindDouble(index: Int, value: Double) {
        ensureParameterCapacity(index)
        parameters[index - 1] = value.toJsNumber()
    }

    override fun bindInt(index: Int, value: Int) {
        ensureParameterCapacity(index)
        parameters[index - 1] = bigInt(value.toJsNumber())
    }

    override fun bindLong(index: Int, value: Long) {
        ensureParameterCapacity(index)
        parameters[index - 1] = value.toSuitableJavaScriptRepresentation()
    }

    override fun bindText(index: Int, value: String) {
        ensureParameterCapacity(index)
        parameters[index - 1] = value.toJsString()
    }

    override fun bindNull(index: Int) {
        ensureParameterCapacity(index)
        parameters[index - 1] = null
    }

    private fun requireRow() = requireNotNull(requireNotNull(resultSet).currentRow)

    private fun rawValue(index: Int): JsAny? {
        return requireRow()[index]
    }

    override fun getBlob(index: Int): ByteArray {
        TODO("Not yet implemented")
    }

    override fun getDouble(index: Int): Double {
        return number(rawValue(index)).toDouble()
    }

    override fun getLong(index: Int): Long {
        return bigInt(rawValue(index)).toLong()
    }

    override fun getInt(index: Int): Int {
        return number(rawValue(index)).toInt()
    }

    override fun getText(index: Int): String {
        return string(rawValue(index)).toString()
    }

    override fun isNull(index: Int): Boolean = rawValue(index) == null

    override fun getColumnCount(): Int {
        return requireNotNull(resultSet).columnNames.size
    }

    override fun getColumnName(index: Int): String {
        return requireNotNull(resultSet).columnNames[index]
    }

    override fun getColumnType(index: Int): Int {
        return columnType(rawValue(index)).toInt()
    }

    override suspend fun step(): Boolean {
        resultSet?.let { rs ->
            return rs.step()
        }

        val rawResults = select(parameters.toJsArray())
        val results = CopiedResultSet.fromRaw(rawResults)
        resultSet = results
        return results.step()
    }

    override fun reset() {
        resultSet = null
    }

    override fun clearBindings() {
        parameters.clear()
    }

    override fun close() {
        // Not a real statement, nothing to close
    }
}

private class CopiedResultSet(val columnNames: List<String>, val rows: Iterator<JsArray<JsAny?>>) {
    var currentRow: JsArray<JsAny?>? = null

    fun step(): Boolean {
        if (rows.hasNext()) {
            currentRow = rows.next()
            return true
        }

        return false
    }

    companion object {
        fun fromRaw(raw: ResultSet): CopiedResultSet {
            val numColumns = raw.columnNames.length
            val columnNames = buildList(numColumns) {
                for (i in 0..< numColumns) {
                    add(raw.columnNames[i].toString())
                }
            }

            val rows = raw.rows.toList().iterator()
            return CopiedResultSet(columnNames, rows)
        }
    }
}

private fun newUint8Array(length: JsNumber): JsArray<JsNumber> = js("new Uint8Array(length)")

private fun string(content: JsAny?): JsString = js("String(content)")

private fun bigInt(content: JsAny?): JsBigInt = js("BigInt(content)")

private fun number(content: JsAny?): JsNumber = js("Number(content)")

// For type codes, see https://sqlite.org/c3ref/c_blob.html
private fun columnType(content: JsAny?): JsNumber = js("""{
  if (content == null) return 5;
  const type = typeof content;
  switch (type) {
    case "bigint": return 1;
    case "number": return 2;
    case "string": return 3;
    default: return 4;
  }
}""")

private fun databaseExecuteOptions(
    parameters: JsArray<JsAny?>,
    token: JsNumber,
    abort: JsAny?
): DatabaseExecuteOptions = js("""({ parameters: parameters, token: token, abort: abort })""")
