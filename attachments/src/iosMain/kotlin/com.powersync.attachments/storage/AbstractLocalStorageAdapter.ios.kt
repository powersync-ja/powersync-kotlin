package com.powersync.attachments.storage

import com.powersync.attachments.LocalStorageAdapter
import platform.Foundation.NSHomeDirectory

public actual abstract class AbstractLocalStorageAdapter : LocalStorageAdapter {
    actual override fun getUserStorageDirectory(): String = NSHomeDirectory()
}
