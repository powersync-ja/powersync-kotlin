package com.powersync.internal

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineConfig
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * A hook installed by the `:core` and `:internal:PowerSyncKotlin` projects.
 *
 * The hook is responsible for determining whether a given [HttpClientEngine] (expressed through
 * [HttpClientEngineConfig] because the former is not always public) is known not to support backpressure.
 * In particular, this is the case for the `Darwin` HTTP engine.
 *
 * When an engine is marked to not support backpressure handling, we will use a custom protocol with explicit
 * flow control instead of relying on HTTP response streams.
 */
@OptIn(ExperimentalAtomicApi::class)
@InternalPowerSyncAPI
public val httpClientIsKnownToNotSupportBackpressure: AtomicReference<((HttpClientEngineConfig) -> Boolean)?> = AtomicReference(null)

@OptIn(ExperimentalAtomicApi::class, InternalPowerSyncAPI::class)
internal val HttpClientEngineConfig.isKnownToNotSupportBackpressure: Boolean
    get() {
        val check = httpClientIsKnownToNotSupportBackpressure.load() ?: return false
        return check(this)
    }
