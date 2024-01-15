package co.powersync.bucket

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OplogEntry (
    val checksum: Long,
    @SerialName("op_id") val opId: String,
    @SerialName("object_id") val rowId: String? = null,
    @SerialName("object_type") val rowType: String?= null,
    val op: OpType? = null,
    /**
     * Together with rowType and rowId, this uniquely identifies a source entry per bucket in the oplog.
     * There may be multiple source entries for a single "rowType + rowId" combination.
     */
    val subkey: String? = null,
    val data: Map<String, String>? = null
)