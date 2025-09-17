package com.powersync.integrations.sqldelight

import com.powersync.DatabaseDriverFactory

actual fun databaseDriverFactory(): DatabaseDriverFactory = DatabaseDriverFactory()
