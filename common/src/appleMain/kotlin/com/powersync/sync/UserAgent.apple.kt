package com.powersync.sync

import com.powersync.http.AppleHttpClientEngineFactory
import io.ktor.client.engine.HttpClientEngineFactory

internal actual fun overrideClientEngineFactory(): HttpClientEngineFactory<*>? {
    return AppleHttpClientEngineFactory
}
