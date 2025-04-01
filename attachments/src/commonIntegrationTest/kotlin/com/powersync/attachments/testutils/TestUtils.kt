package com.powersync.attachments.testutils

import com.powersync.DatabaseDriverFactory

expect val factory: DatabaseDriverFactory

expect fun cleanup(path: String)
