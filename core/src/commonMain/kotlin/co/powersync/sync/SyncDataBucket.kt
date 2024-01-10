package co.powersync.sync

import co.powersync.bucket.OplogEntry
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class SyncDataBucket (
    val bucket: String,
    val data: List<OplogEntry>,
    @JsonNames("has_more") val hasMore: Boolean = false,
    val after: String?,
    @JsonNames("next_after")val nextAfter: String?
){

    companion object {
        fun fromRow(row: JsonObject): SyncDataBucket {
            val data = (row["data"] as JsonArray).map { i -> OplogEntry.fromRow(i as JsonObject) }


            return SyncDataBucket(
                bucket = (row["bucket"] as JsonPrimitive).content,
                data = data,
                after = (row["after"] as JsonPrimitive).content,
                hasMore = (row["has_more"] as JsonPrimitive).content.toBoolean(),
                nextAfter = (row["next_after"] as JsonPrimitive).content
            )
        }
    }
}