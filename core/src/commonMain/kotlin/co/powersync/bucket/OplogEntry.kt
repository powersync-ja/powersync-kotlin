package co.powersync.bucket

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class OplogEntry (
    @JsonNames("object_id") val rowId: String?,
    @JsonNames("object_type") val rowType: String?,
    @JsonNames("op_id") val opId: String,
    val op: OpType?,
    /**
     * Together with rowType and rowId, this uniquely identifies a source entry per bucket in the oplog.
     * There may be multiple source entries for a single "rowType + rowId" combination.
     */
    val subkey: String? = null,
    val checksum: Long,
    val data: MutableMap<String, String>?
) {

    companion object {
        fun fromRow (row: JsonObject): OplogEntry {

            var dataMap: MutableMap<String, String>? = null
            if(row["data"] !is JsonNull){
                dataMap= mutableMapOf()
                val dataObj = row["data"] as JsonObject
                dataObj.forEach{i -> dataMap[i.key] = (i.value as JsonPrimitive).content }
            }

            val opType: OpType = OpType.valueOf((row["op"] as JsonPrimitive).content)

            return OplogEntry(
                rowId = (row["object_id"] as JsonPrimitive).content,
                checksum = (row["checksum"] as JsonPrimitive).content.toLong(),
                rowType = (row["object_type"] as JsonPrimitive).content,
                opId = (row["op_id"] as JsonPrimitive).content,
                subkey = (row["subkey"] as JsonPrimitive).content,
                data = dataMap,
                op = opType
            )
        }
    }
}