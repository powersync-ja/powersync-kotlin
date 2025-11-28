package com.powersync.sync

import io.ktor.client.engine.HttpClientEngineFactory

internal expect fun userAgent(): String

/**
 * A fixed engine factory to replace the ktor default.
 *
 * On Apple platforms, we use a fork of `ktor-client-darwin` with better support for backpressure.
 */
internal expect fun overrideClientEngineFactory(): HttpClientEngineFactory<*>?
