package com.powersync.bucket

import co.touchlab.kermit.Logger
import co.touchlab.stately.concurrency.AtomicBoolean
import com.powersync.db.SqlCursor
import com.powersync.db.crud.CrudEntry
import com.powersync.db.crud.CrudRow
import com.powersync.db.internal.InternalDatabase
import com.powersync.db.internal.InternalTable
import com.powersync.db.internal.PowerSyncTransaction
import com.powersync.sync.Instruction
import com.powersync.utils.JsonUtil

internal class BucketStorageImpl(
    private val db: InternalDatabase,
    private val logger: Logger,
) : BucketStorage {
    private var hasCompletedSync = AtomicBoolean(false)

    companion object {
        const val MAX_OP_ID = "9223372036854775807"
    }

    override fun getMaxOpId(): String = MAX_OP_ID

    override suspend fun getClientId(): String {
        val id =
            db.getOptional("SELECT powersync_client_id() as client_id") {
                it.getString(0)!!
            }
        return id ?: throw IllegalStateException("Client ID not found")
    }

    override suspend fun nextCrudItem(): CrudEntry? = db.getOptional(sql = nextCrudQuery, mapper = ::mapCrudEntry)

    override fun nextCrudItem(transaction: PowerSyncTransaction): CrudEntry? =
        transaction.getOptional(sql = nextCrudQuery, mapper = ::mapCrudEntry)

    private val nextCrudQuery = "SELECT id, tx_id, data FROM ${InternalTable.CRUD} ORDER BY id ASC LIMIT 1"

    override fun mapCrudEntry(row: SqlCursor): CrudEntry =
        CrudEntry.fromRow(
            CrudRow(
                id = row.getString(0)!!,
                txId = row.getString(1)?.toInt(),
                data = row.getString(2)!!,
            ),
        )

    override suspend fun hasCrud(): Boolean {
        val res = db.getOptional(sql = hasCrudQuery, mapper = hasCrudMapper)
        return res == 1L
    }

    override fun hasCrud(transaction: PowerSyncTransaction): Boolean {
        val res = transaction.getOptional(sql = hasCrudQuery, mapper = hasCrudMapper)
        return res == 1L
    }

    private val hasCrudQuery = "SELECT 1 FROM ps_crud LIMIT 1"
    private val hasCrudMapper: (SqlCursor) -> Long = {
        it.getLong(0)!!
    }

    override suspend fun updateLocalTarget(checkpointCallback: suspend () -> String): Boolean {
        db.getOptional(
            "SELECT target_op FROM ${InternalTable.BUCKETS} WHERE name = '\$local' AND target_op = ?",
            parameters = listOf(MAX_OP_ID),
            mapper = { cursor -> cursor.getLong(0)!! },
        )
            ?: // Nothing to update
            return false

        val seqBefore =
            db.getOptional("SELECT seq FROM main.sqlite_sequence WHERE name = '${InternalTable.CRUD}'") {
                it.getLong(0)!!
            } ?: // Nothing to update
                return false

        val opId = checkpointCallback()

        logger.i { "[updateLocalTarget] Updating target to checkpoint $opId" }

        return db.writeTransaction { tx ->
            if (hasCrud(tx)) {
                logger.w { "[updateLocalTarget] ps crud is not empty" }
                return@writeTransaction false
            }

            val seqAfter =
                tx.getOptional("SELECT seq FROM main.sqlite_sequence WHERE name = '${InternalTable.CRUD}'") {
                    it.getLong(0)!!
                }
                    ?: // assert isNotEmpty
                    throw AssertionError("Sqlite Sequence should not be empty")

            if (seqAfter != seqBefore) {
                logger.d("seqAfter != seqBefore seqAfter: $seqAfter seqBefore: $seqBefore")
                // New crud data may have been uploaded since we got the checkpoint. Abort.
                return@writeTransaction false
            }

            tx.execute(
                "UPDATE ${InternalTable.BUCKETS} SET target_op = CAST(? as INTEGER) WHERE name='\$local'",
                listOf(opId),
            )

            return@writeTransaction true
        }
    }

    override suspend fun hasCompletedSync(): Boolean {
        if (hasCompletedSync.value) {
            return true
        }

        val completedSync =
            db.getOptional(
                "SELECT powersync_last_synced_at()",
                mapper = { cursor ->
                    cursor.getString(0)!!
                },
            )

        return if (completedSync != null) {
            hasCompletedSync.value = true
            true
        } else {
            false
        }
    }

    private fun handleControlResult(cursor: SqlCursor): List<Instruction> {
        val result = cursor.getString(0)!!
        logger.v { "control result: $result" }

        return JsonUtil.json.decodeFromString<List<Instruction>>(result)
    }

    override suspend fun control(args: PowerSyncControlArguments): List<Instruction> =
        db.writeTransaction { tx ->
            logger.v { "powersync_control: $args" }

            val (op: String, data: Any?) = args.sqlArguments
            tx.get("SELECT powersync_control(?, ?) AS r", listOf(op, data), ::handleControlResult)
        }
}
