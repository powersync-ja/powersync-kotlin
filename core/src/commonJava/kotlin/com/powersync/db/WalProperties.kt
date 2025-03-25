package com.powersync.db

import java.util.Properties

internal fun buildDefaultWalProperties(readOnly: Boolean = false): Properties {
    // WAL Mode properties
    val properties = Properties()
    properties.setProperty("journal_mode", "WAL")
    properties.setProperty("journal_size_limit", "${6 * 1024 * 1024}")
    properties.setProperty("busy_timeout", "30000")
    properties.setProperty("cache_size", "${50 * 1024}")

    if (readOnly) {
        properties.setProperty("open_mode", "1")
    }

    return properties
}
