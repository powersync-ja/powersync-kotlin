package com.powersync.attachments.storage

import com.powersync.attachments.LocalStorageAdapter

public actual abstract class AbstractLocalStorageAdapter : LocalStorageAdapter {
    actual override fun getUserStorageDirectory(): String = System.getProperty("user.home")
}
