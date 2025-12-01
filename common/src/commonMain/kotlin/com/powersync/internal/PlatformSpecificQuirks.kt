package com.powersync.internal

import io.ktor.client.engine.HttpClientEngineConfig
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
@InternalPowerSyncAPI
public val httpClientIsKnownToNotSupportBackpressure: AtomicReference<((HttpClientEngineConfig) -> Boolean)?> = AtomicReference(null)

@OptIn(ExperimentalAtomicApi::class, InternalPowerSyncAPI::class)
internal val HttpClientEngineConfig.isKnownToNotSupportBackpressure: Boolean
    get() {
        val check = httpClientIsKnownToNotSupportBackpressure.load() ?: return false
        return check(this)
    }
