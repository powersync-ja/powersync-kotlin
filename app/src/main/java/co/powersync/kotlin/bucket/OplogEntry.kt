package co.powersync.kotlin.bucket

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class OplogEntryJSON (
    val op_id: String,
    val op: String,
    val object_type: String,
    val object_id: String,
    val checksum: Int,
    val data: JsonObject,
    val subkey: String
)

@Serializable
data class OplogEntry (
    val op_id: String,
    val op: OpType,
    val checksum: Long,
    val subkey: String,
    val object_type: String?,
    val object_id: String?,
    val data: MutableMap<String, String>?
) {
    companion object {
        fun fromRow (row: JsonObject): OplogEntry {

            val dataObj = row["data"] as JsonObject
            val dataMap = mutableMapOf<String, String>()
            dataObj.forEach{i -> dataMap[i.key] = (i.value as JsonPrimitive).content }

            val opType: OpTypeEnum = OpTypeEnum.valueOf((row["op"] as JsonPrimitive).content);
            val op = OpType(opType);

            return OplogEntry(
                object_id = (row["object_id"] as JsonPrimitive).content,
                checksum = (row["checksum"] as JsonPrimitive).content.toLong(),
                object_type = (row["object_type"] as JsonPrimitive).content,
                op_id = (row["op_id"] as JsonPrimitive).content,
                subkey = (row["subkey"] as JsonPrimitive).content,
                data = dataMap,
                op = op
            )
        }
    }
}