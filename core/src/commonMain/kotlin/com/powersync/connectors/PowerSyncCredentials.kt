package com.powersync.connectors

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * Temporary credentials to connect to the PowerSync service.
 */
@Serializable
public data class PowerSyncCredentials(
    /**
     * PowerSync endpoint, e.g. "https://myinstance.powersync.co".
     */
    val endpoint: String,
    /**
     * Temporary token to authenticate against the service.
     */
    val token: String,
    /**
     * User ID.
     */
    @SerialName("user_id") val userId: String?
) {
    override fun toString(): String {
        return "PowerSyncCredentials<endpoint: $endpoint userId: $userId>"
    }

    public fun endpointUri(path: String): String {
        return "$endpoint/$path"
    }
}