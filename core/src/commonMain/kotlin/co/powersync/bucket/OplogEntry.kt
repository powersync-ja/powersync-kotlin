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
    val checksum: Long,
    @JsonNames("op_id") val opId: String,
    @JsonNames("object_id") val rowId: String? = null,
    @JsonNames("object_type") val rowType: String?= null,
    val op: OpType? = null,
    /**
     * Together with rowType and rowId, this uniquely identifies a source entry per bucket in the oplog.
     * There may be multiple source entries for a single "rowType + rowId" combination.
     */
    val subkey: String? = null,
    val data: Map<String, String>? = null
) {
}