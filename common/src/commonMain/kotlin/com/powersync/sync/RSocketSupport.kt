package com.powersync.sync

import com.powersync.bucket.PowerSyncControlArguments
import com.powersync.connectors.PowerSyncCredentials
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement

/**
 * Connects to the RSocket endpoint for receiving sync lines.
 *
 * Note that we reconstruct the transport layer for RSocket by opening a WebSocket connection
 * manually instead of using the high-level RSocket Ktor integration.
 * The reason is that every request to the sync service needs its own metadata and data payload
 * (e.g. to transmit the token), but the Ktor integration only supports setting a single payload for
 * the entire client.
 */
internal expect fun HttpClient.rSocketSyncStream(
    userAgent: String,
    req: JsonElement,
    credentials: PowerSyncCredentials,
): Flow<PowerSyncControlArguments>

/**
 * Thrown from [rSocketSyncStream] when the server closes the RSocket connection with a
 * PowerSync authorization error (PSYNC_S21xx) embedded in the transport-level error message.
 * Caught by [StreamingSync] to trigger credential invalidation.
 */
internal class RSocketCredentialsExpiredException(
    message: String,
    cause: Throwable,
) : RuntimeException(message, cause)

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
internal expect sealed class PowerSyncRSocketError : Throwable

internal expect fun PowerSyncRSocketError.indicatesInvalidCredentials(): Boolean
