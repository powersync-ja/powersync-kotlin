package co.powersync.kotlin.bucket

import kotlinx.serialization.Serializable

@Serializable
data class OplogEntry (
    val op_id: String,
    val op: OpType,
    val checksum: Integer,
    val subkey: String,
    val object_type: String?,
    val object_id: String?,
    val data: Map<String, String>? // TODO figure out type here, value should be Any, not String but that causes compiler to complain about Serializable
)