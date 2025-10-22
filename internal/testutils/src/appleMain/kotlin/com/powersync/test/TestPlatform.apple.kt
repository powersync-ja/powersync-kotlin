package com.powersync.test

import com.powersync.DatabaseDriverFactory
import com.powersync.PersistentConnectionFactory

actual val factory: PersistentConnectionFactory = DatabaseDriverFactory()
