package com.powersync.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SyncLocalDatabaseResult(
    var ready: Boolean = true,
    @SerialName("valid") val checkpointValid: Boolean = true,
    @SerialName("failed_buckets") val checkpointFailures: List<String>? = null
) {
    override fun toString() =
        "SyncLocalDatabaseResult<ready=$ready, checkpointValid=$checkpointValid, failures=$checkpointFailures>"
}