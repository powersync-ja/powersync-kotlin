package co.powersync.kotlin.bucket

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class SyncDataBucketJSON(
    val bucket: String,
    val has_more: Boolean, // TODO is this optional?
    val after: String, // TODO is this optional?
    val next_after: String,// TODO is this optional?
    val data: Array<OplogEntryJSON>
)

@Serializable
data class SyncDataBucket(

    val bucket: String,
    val data: Array<OplogEntry>,
    /**
     * True if the response does not contain all the data for this bucket, and another request must be made.
     */
    val has_more: Boolean = false,
    /**
     * The `after` specified in the request.
     */
    val after: String ?= null,
    /**
     * Use this for the next request.
     */
    val next_after: String ?= null


) {
    companion object {
        fun fromRow(row: JsonObject): SyncDataBucket {
            val data = (row["data"] as JsonArray).map { i -> OplogEntry.fromRow(i as JsonObject) }
                .toTypedArray()

            return SyncDataBucket(
                bucket = (row["bucket"] as JsonPrimitive).content,
                data = data,
                after = (row["after"] as JsonPrimitive).content,
                has_more = (row["has_more"] as JsonPrimitive).content.toBoolean(),
                next_after = (row["next_after"] as JsonPrimitive).content
            )
        }
    }
}