@file:OptIn(ExperimentalWasmJsInterop::class, InternalPowerSyncAPI::class)

package com.powersync.web

import androidx.sqlite.SQLiteStatement
import com.powersync.db.driver.SQLiteConnectionLease
import com.powersync.db.driver.SQLiteConnectionPool
import com.powersync.internal.InternalPowerSyncAPI
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.promise
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.JsNumber
import kotlin.js.JsReference
import kotlin.js.get
import kotlin.js.js
import kotlin.js.length
import kotlin.js.toBoolean
import kotlin.js.toInt
import kotlin.js.toJsNumber
import kotlin.js.toJsReference
import kotlin.js.toJsString
import kotlin.js.toList

/**
 * A worker "connection pool" implemented by a single connection to a worker hosting a SQLite
 * database.
 *
 * In the future, we may want to scale this to multiple workers after the Dart SDK supports that.
 */
internal class DartWorkerDatabase(
    private val factory: WebConnectionFactory,
    /**
     * An obtained id for table notifications that the worker sends to clients.
     */
    internal var streamUpdatesId: String,
    /**
     * The underlying database, managed by the `sqlite3_web` npm package.
     */
    private val db: Database,
) : SQLiteConnectionPool {
    internal val updatesController = MutableSharedFlow<Set<String>>()

    override val updates: SharedFlow<Set<String>>
        get() = updatesController.asSharedFlow()

    private suspend fun <T> lock(callback: suspend (SQLiteConnectionLease) -> T): T =
        withAbortSignal { signal ->
            val promise =
                db.requestLock({ token ->
                    val id = token.toInt()
                    promise {
                        callback(Lease(id))?.toJsReference()
                    }
                }, requestLockOptions(signal))

            val result = promise.await() as JsReference<*>
            @Suppress("UNCHECKED_CAST")
            result.get() as T
        }

    override suspend fun <T> read(callback: suspend (SQLiteConnectionLease) -> T): T = lock(callback)

    override suspend fun <T> write(callback: suspend (SQLiteConnectionLease) -> T): T = lock(callback)

    override suspend fun <R> withAllConnections(
        action: suspend (writer: SQLiteConnectionLease, readers: List<SQLiteConnectionLease>) -> R,
    ) {
        lock { action(it, emptyList()) }
    }

    override suspend fun close() {
        factory.markClosed(this)
        db.close().await()
    }

    private inner class Lease(
        val lockToken: Int,
    ) : SQLiteConnectionLease {
        /**
         * Whether we're currently in a transaction.
         *
         * This can only be changed by statements, so we query its value after every statement.
         */
        var inTransaction = false

        override suspend fun isInTransaction(): Boolean = inTransaction

        override suspend fun <R> usePreparedAsync(
            sql: String,
            block: suspend (SQLiteStatement) -> R,
        ): R {
            val stmt =
                VirtualWorkerStatement { params ->
                    val result =
                        withAbortSignal { signal ->
                            db
                                .select(
                                    sql.toJsString(),
                                    databaseExecuteOptions(
                                        params.takeParameters(),
                                        types = params.takeTypes(),
                                        token = lockToken.toJsNumber(),
                                        abort = signal,
                                    ),
                                ).awaitSafe()
                        }

                    inTransaction = !result.autocommit.toBoolean()
                    result.result
                }
            return block(stmt)
        }

        override suspend fun execSQL(sql: String) {
            val result =
                withAbortSignal { signal ->
                    db
                        .execute(
                            sql.toJsString(),
                            databaseExecuteOptions(
                                JsArray(),
                                types = ArrayBuffer(0),
                                token = lockToken.toJsNumber(),
                                abort = signal,
                            ),
                        ).awaitSafe()
                }

            inTransaction = !result.autocommit.toBoolean()
        }
    }
}

/**
 * A virtual statement implementation that will internally use a single select call to fetch
 * results instead of invoking the worker once per call to [step].
 *
 * We might replace this with a proper statement implementation in the future, but that would
 * require worker changes.
 */
private class VirtualWorkerStatement(
    private val select: suspend (TypedParameters) -> ResultSet,
) : SQLiteStatement {
    private val parameters = TypedParameters()
    var resultSet: CopiedResultSet? = null

    override fun bindBlob(
        index: Int,
        value: ByteArray,
    ) = parameters.bindBlob(index, value)

    override fun bindDouble(
        index: Int,
        value: Double,
    ) = parameters.bindDouble(index, value)

    override fun bindInt(
        index: Int,
        value: Int,
    ) = parameters.bindInt(index, value)

    override fun bindLong(
        index: Int,
        value: Long,
    ) = parameters.bindLong(index, value)

    override fun bindText(
        index: Int,
        value: String,
    ) = parameters.bindText(index, value)

    override fun bindNull(index: Int) = parameters.bindNull(index)

    private fun decodedValue(index: Int): Any? {
        val resultSet = requireNotNull(resultSet)
        return resultSet.kotlinValue(index)
    }

    override fun getBlob(index: Int): ByteArray = decodedValue(index) as ByteArray

    override fun getDouble(index: Int): Double = decodedValue(index) as Double

    override fun getLong(index: Int): Long = decodedValue(index) as Long

    override fun getInt(index: Int): Int = getLong(index).toInt()

    override fun getText(index: Int): String = decodedValue(index) as String

    override fun isNull(index: Int): Boolean = requireNotNull(resultSet).typeCode(index) == TypeCodes.NULL

    override fun getColumnCount(): Int = requireNotNull(resultSet).columnNames.size

    override fun getColumnName(index: Int): String = requireNotNull(resultSet).columnNames[index]

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

/**
 * A decoded result set from a worker message where we iterate through rows as a JS array.
 */
private class CopiedResultSet(
    val columnNames: List<String>,
    val rows: Iterator<JsArray<JsAny?>>,
    /**
     * A data view containing one byte per value in the result set.
     *
     * This describes the JavaScript encoding of SQLite types (an integer `3` and a double `3.0`
     * would both encode to the same JavaScript number for example, this lets us keep them apart).
     */
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

    fun typeCode(index: Int): Byte = types.getInt8(typeOffset + index).toByte()

    fun kotlinValue(index: Int): Any? {
        val row = requireNotNull(currentRow)
        return decodeTyped(row[index], typeCode(index))
    }

    companion object {
        fun fromRaw(raw: ResultSet): CopiedResultSet {
            val numColumns = raw.columnNames.length
            val columnNames =
                buildList(numColumns) {
                    for (i in 0..<numColumns) {
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
    abort: JsAny?,
): DatabaseExecuteOptions = js("""({ parameters: parameters, types: types, token: token, abort: abort })""")

private fun requestLockOptions(abort: JsAny): JsAny = js("({ abort: abort })")
