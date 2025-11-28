package com.powersync.sync

import io.ktor.client.engine.HttpClientEngineFactory

internal actual fun overrideClientEngineFactory(): HttpClientEngineFactory<*>? = null
