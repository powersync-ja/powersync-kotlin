package com.powersync.internal.driver

import android.content.Context
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean

public class AndroidDriver(private val context: Context): JdbcDriver() {
    override fun addDefaultProperties(properties: Properties) {
        val isFirst = IS_FIRST_CONNECTION.getAndSet(false)
        if (isFirst) {
            // Make sure the temp_store_directory points towards a temporary directory we actually
            // have access to. Due to sandboxing, the default /tmp/ is inaccessible.
            // The temp_store_directory pragma is deprecated and not thread-safe, so we only set it
            // on the first connection (it sets a global field and will affect every connection
            // opened).
            val escapedPath = context.cacheDir.absolutePath.replace("\"", "\"\"")
            properties.setProperty("temp_store_directory", "\"$escapedPath\"")
        }
    }

    private companion object {
        val IS_FIRST_CONNECTION = AtomicBoolean(true)
    }
}
