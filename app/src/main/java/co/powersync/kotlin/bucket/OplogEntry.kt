package co.powersync.kotlin.bucket

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.reflect.jvm.internal.impl.load.kotlin.JvmType.Primitive

@Serializable
data class OplogEntryJSON (
    val op_id: String,
    val op: String,
    val object_type: String,
    val object_id: String,
    val checksum: Int,
    val data: Map<String, String>,
    val subkey: String
)

@Serializable
data class OplogEntry (
    val op_id: String,
    val op: OpType,
    val checksum: Int,
    val subkey: String,
    val object_type: String?,
    val object_id: String?,
    val data: Map<String, String>?
) {
    companion object {
        fun fromRow (row: OplogEntryJSON): OplogEntry {
            return OplogEntry(
                object_id = row.object_id,
                checksum = row.checksum,
                object_type = row.object_type,
                op_id = row.op_id,
                subkey = row.subkey,
                data = row.data,
                op = OpType(OpTypeEnum.valueOf(row.op))
            )
        }
    }
}