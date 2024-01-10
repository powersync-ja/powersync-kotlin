package co.powersync.sync

import co.powersync.bucket.BucketRequest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class StreamingSyncRequest(val buckets: List<BucketRequest>,
                                @JsonNames("include_checksum") val includeChecksum: Boolean = true,
                                ) {
    @JsonNames("raw_data") private val rawData: Boolean = true
}
