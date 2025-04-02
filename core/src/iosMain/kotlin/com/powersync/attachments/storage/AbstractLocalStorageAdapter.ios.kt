package com.powersync.attachments.storage

import com.powersync.attachments.LocalStorageAdapter
import platform.Foundation.NSHomeDirectory

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual abstract class AbstractLocalStorageAdapter : LocalStorageAdapter {
    actual override fun getUserStorageDirectory(): String = NSHomeDirectory()
}
