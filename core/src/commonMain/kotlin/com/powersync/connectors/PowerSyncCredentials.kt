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
data class PowerSyncCredentials(
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
    @SerialName("user_id") val userId: String?,
    /**
     * When the token expires. Only use for debugging purposes.
     */
    val expiresAt: Instant?
) {
    override fun toString(): String {
        return "PowerSyncCredentials<endpoint: $endpoint userId: $userId expiresAt: ${
            expiresAt?.toLocalDateTime(
                TimeZone.UTC
            )
        }>"
    }

    fun endpointUri(path: String): String {
        return "$endpoint/$path"
    }
}