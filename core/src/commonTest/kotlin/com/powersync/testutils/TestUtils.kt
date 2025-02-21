package com.powersync.testutils

import com.powersync.DatabaseDriverFactory

expect val factory: DatabaseDriverFactory
expect fun cleanup(path: String)
