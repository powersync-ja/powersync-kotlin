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
                    params.takeParameters(),
                    types = params.takeTypes(),
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
                types = ArrayBuffer(0),
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
    private val select: suspend (TypedParameters) -> ResultSet
): SQLiteStatement {
    private val parameters = TypedParameters()
    var resultSet: CopiedResultSet? = null

    override fun bindBlob(index: Int, value: ByteArray) {
        parameters.bindBlob(index, value)
    }

    override fun bindDouble(index: Int, value: Double) {
        parameters.bindDouble(index, value)
    }

    override fun bindInt(index: Int, value: Int) {
        parameters.bindInt(index, value)
    }

    override fun bindLong(index: Int, value: Long) {
        parameters.bindLong(index, value)
    }

    override fun bindText(index: Int, value: String) {
        parameters.bindText(index, value)
    }

    override fun bindNull(index: Int) = parameters.bindNull(index)

    private fun decodedValue(index: Int): Any? {
        val resultSet = requireNotNull(resultSet)
        return resultSet.kotlinValue(index)
    }

    override fun getBlob(index: Int): ByteArray {
        return decodedValue(index) as ByteArray
    }

    override fun getDouble(index: Int): Double {
        return decodedValue(index) as Double
    }

    override fun getLong(index: Int): Long {
        return decodedValue(index) as Long
    }

    override fun getInt(index: Int): Int {
        return getLong(index).toInt()
    }

    override fun getText(index: Int): String {
        return decodedValue(index) as String
    }

    override fun isNull(index: Int): Boolean = requireNotNull(resultSet).typeCode(index) == TypeCodes.NULL

    override fun getColumnCount(): Int {
        return requireNotNull(resultSet).columnNames.size
    }

    override fun getColumnName(index: Int): String {
        return requireNotNull(resultSet).columnNames[index]
    }

    override fun getColumnType(index: Int): Int {
        val transportType = requireNotNull(resultSet).typeCode(index)
        // For values, see https://sqlite.org/c3ref/c_blob.html
        return when (transportType) {
            TypeCodes.INTEGER, TypeCodes.BIG_INTEGER -> 1
            TypeCodes.FLOAT -> 2
            TypeCodes.TEXT -> 3
            TypeCodes.BLOB -> 4
            else -> 5
        }
    }

    override suspend fun step(): Boolean {
        resultSet?.let { rs ->
            return rs.step()
        }

        val rawResults = select(parameters)
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

private class CopiedResultSet(
    val columnNames: List<String>,
    val rows: Iterator<JsArray<JsAny?>>,
    val types: DataView,
) {
    var currentRow: JsArray<JsAny?>? = null
    var typeOffset = 0

    fun step(): Boolean {
        if (currentRow != null) {
            typeOffset += columnNames.size
        }

        if (rows.hasNext()) {
            currentRow = rows.next()
            return true
        }

        return false
    }

    fun typeCode(index: Int): Byte {
        return types.getInt8(typeOffset + index).toByte()
    }

    fun kotlinValue(index: Int): Any? {
        val row = requireNotNull(currentRow)
        return decodeTyped(row[index], typeCode(index))
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
            return CopiedResultSet(columnNames, rows, DataView(raw.types))
        }
    }
}

private fun databaseExecuteOptions(
    parameters: JsArray<JsAny?>,
    types: ArrayBuffer,
    token: JsNumber,
    abort: JsAny?
): DatabaseExecuteOptions = js("""({ parameters: parameters, types: types, token: token, abort: abort })""")
