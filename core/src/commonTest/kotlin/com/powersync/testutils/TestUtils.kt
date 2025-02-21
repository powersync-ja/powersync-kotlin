package com.powersync.testutils

import com.powersync.DatabaseDriverFactory

expect annotation class IgnoreOnAndroid()

expect val factory: DatabaseDriverFactory

expect fun cleanup(path: String)
