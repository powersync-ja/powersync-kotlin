package com.powersync.connectors

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    @Deprecated(
        message = "This property is no longer used.",
        replaceWith = ReplaceWith(""),
        level = DeprecationLevel.WARNING,
    )
    @SerialName("user_id")
    val userId: String?,
) {
    override fun toString(): String = "PowerSyncCredentials<endpoint: $endpoint userId: $userId>"

    public fun endpointUri(path: String): String = "$endpoint/$path"
}
