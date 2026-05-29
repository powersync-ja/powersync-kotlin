package com.powersync.sync

import com.powersync.bucket.PowerSyncControlArguments
import com.powersync.connectors.PowerSyncCredentials
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonElement

internal actual fun HttpClient.rSocketSyncStream(
    userAgent: String,
    req: JsonElement,
    credentials: PowerSyncCredentials,
): Flow<PowerSyncControlArguments> =
    flow {
        throw UnsupportedOperationException("RSocket is not supported on web platforms")
    }

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
internal actual sealed class PowerSyncRSocketError : Throwable()

internal actual fun PowerSyncRSocketError.indicatesInvalidCredentials(): Boolean = false
